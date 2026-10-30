package io.github.aldrinadira.jalanz.core.toll

import java.time.Instant

/**
 * One point of a route (04 §5). [wayId] is the OSM way travelled to reach this point, so a plaza crossing is
 * (this point's way, its node, the next point's way). [at] is the predicted time at the point.
 */
data class PathPoint(
    val nodeId: OsmNodeId?,
    val wayId: OsmWayId,
    val location: GeoPoint,
    val at: Instant,
)

data class RoutePath(val points: List<PathPoint>) {
    init {
        require(points.isNotEmpty()) { "a route path needs at least one point" }
    }
}

enum class Confidence { LOW, MEDIUM, HIGH }

data class SenRange(val min: Sen, val max: Sen) {
    init {
        require(min <= max) { "range min $min is above max $max" }
    }
}

/** One line of the per-plaza breakdown (04 §8). [charged] is null when only a range is known. */
sealed interface TollItem {
    val charged: Sen?
    val discountEventId: DiscountEventId?

    data class Open(
        val plazaId: PlazaId,
        val at: Instant,
        val vehicleClass: VehicleClass,
        val base: Sen,
        override val charged: Sen,
        override val discountEventId: DiscountEventId?,
    ) : TollItem

    data class ClosedPair(
        val networkId: NetworkId,
        val entry: PlazaId,
        val exit: PlazaId,
        val exitAt: Instant,
        val vehicleClass: VehicleClass,
        val base: Sen,
        override val charged: Sen,
        override val discountEventId: DiscountEventId?,
        val distanceKm: Double?,
    ) : TollItem

    /** The vehicle's class is not charged at this plaza, e.g. motorcycles on LLM 1–5 highways (04 §6). */
    data class NotCharged(val plazaId: PlazaId, val at: Instant) : TollItem {
        override val charged: Sen get() = Sen.ZERO
        override val discountEventId: DiscountEventId? get() = null
    }

    /** A charging plaza with no fare (or no class mapping) in the dataset: unknown, never zero (04 §1). */
    data class MissingFare(val plazaId: PlazaId, val at: Instant, val entry: PlazaId?) : TollItem {
        override val charged: Sen? get() = null
        override val discountEventId: DiscountEventId? get() = null
    }

    /** Route starts inside a closed network: only the range of fares to [exit] is known (04 §7 edge case 3). */
    data class ClosedUnknownEntry(
        val networkId: NetworkId,
        val exit: PlazaId,
        val exitAt: Instant,
        val range: SenRange,
    ) : TollItem {
        override val charged: Sen? get() = null
        override val discountEventId: DiscountEventId? get() = null
    }
}

/**
 * Result for one route (04 §8). With confidence LOW and a [range], [total] is the upper end of the range so a
 * route total never under-states the toll.
 */
data class TollEstimate(
    val total: Sen,
    val range: SenRange?,
    val confidence: Confidence,
    val items: List<TollItem>,
    val warnings: List<String>,
    val datasetVersion: String,
    val verifiedAt: Instant?,
)

/** Computes the toll for a route path from a [TollDataset] (04 §5–6). Fixtures F1–F10 are its contract. */
class TollEstimator(private val dataset: TollDataset) {
    private val detector = PlazaDetector(dataset)

    /** [now] is when the estimate is made; it drives the verified_at age rule (F10). */
    fun estimate(path: RoutePath, profile: VehicleProfile, now: Instant): TollEstimate {
        val trip = Trip(profile)
        detector.detect(path).forEach(trip::pass)
        trip.finish()
        return TollEstimate(
            total = trip.items.mapNotNull { it.charged }.sum(),
            range = null,
            confidence = trip.confidence,
            items = trip.items,
            warnings = trip.warnings,
            datasetVersion = dataset.version,
            verifiedAt = trip.verifiedAt.minOrNull(),
        )
    }

    /** State while walking one route's crossings in order (04 §6 pseudo-code). */
    private inner class Trip(private val profile: VehicleProfile) {
        val items = mutableListOf<TollItem>()
        val warnings = mutableListOf<String>()
        val verifiedAt = mutableListOf<Instant>()
        var confidence = Confidence.HIGH
            private set

        /** Open closed-system trips, one per network, so two networks never mix (F7, edge case 4). */
        private val openEntries = LinkedHashMap<NetworkId, PlazaCrossing>()

        fun pass(crossing: PlazaCrossing) {
            if (crossing.method == DetectionMethod.GEOMETRIC) lower(Confidence.MEDIUM)
            val role = dataset.plazaRoleAt(crossing.plazaId, crossing.inWayId, crossing.at)
            if (role == null) {
                warn("no role for ${crossing.plazaId.value} at ${crossing.at}")
                lower(Confidence.LOW)
                return
            }
            when (role.role) {
                Role.OPEN_NO_CHARGE -> Unit
                Role.OPEN_CHARGE -> chargeOpen(crossing)
                Role.CLOSED_ENTRY -> enter(role.networkId!!, crossing)
                Role.CLOSED_EXIT -> exit(role.networkId!!, crossing)
                // Mainline barrier at a network boundary: exit if we are inside, otherwise entry.
                Role.CLOSED_TOGGLE ->
                    if (role.networkId!! in openEntries) exit(role.networkId, crossing) else enter(role.networkId, crossing)
            }
        }

        fun finish() {
            for (network in openEntries.keys) {
                warn("route ends inside closed network ${network.value}")
                lower(Confidence.LOW)
            }
        }

        private fun chargeOpen(crossing: PlazaCrossing) {
            val vehicleClass = chargedClass(crossing, entry = null) ?: return
            val fare = dataset.openFareAt(crossing.plazaId, crossing.inWayId, vehicleClass, crossing.at)
            if (fare == null) return missingFare(crossing, entry = null)
            verifiedAt += fare.verifiedAt
            items += TollItem.Open(crossing.plazaId, crossing.at, vehicleClass, fare.amount, fare.amount, null)
        }

        private fun enter(network: NetworkId, crossing: PlazaCrossing) {
            if (network in openEntries) {
                warn("re-entered ${network.value} at ${crossing.plazaId.value} without an exit")
                lower(Confidence.MEDIUM)
            }
            openEntries[network] = crossing
        }

        /** Closed trips are priced at the exit plaza and exit time (transaction time, 04 §6, F8). */
        private fun exit(network: NetworkId, crossing: PlazaCrossing) {
            val entry = openEntries.remove(network)
            if (entry == null) {
                // Started inside the network (edge case 3). The LOW-confidence range arrives in WP 1.5.
                warn("no entry plaza known for exit ${crossing.plazaId.value}")
                return missingFare(crossing, entry = null)
            }
            val vehicleClass = chargedClass(crossing, entry.plazaId) ?: return
            val fare = dataset.closedFareAt(network, entry.plazaId, crossing.plazaId, vehicleClass, crossing.at)
            if (fare == null) return missingFare(crossing, entry.plazaId)
            verifiedAt += fare.verifiedAt
            items += TollItem.ClosedPair(
                networkId = network,
                entry = entry.plazaId,
                exit = crossing.plazaId,
                exitAt = crossing.at,
                vehicleClass = vehicleClass,
                base = fare.amount,
                charged = fare.amount,
                discountEventId = null,
                distanceKm = fare.distanceKm,
            )
        }

        /** Class at the charging plaza's scheme; records a NotCharged or MissingFare item when there is none. */
        private fun chargedClass(crossing: PlazaCrossing, entry: PlazaId?): VehicleClass? {
            val scheme = dataset.plaza(crossing.plazaId)!!.classScheme
            return when (val assignment = dataset.classFor(profile, scheme)) {
                is ClassAssignment.Charged -> assignment.vehicleClass
                ClassAssignment.NotCharged -> {
                    items += TollItem.NotCharged(crossing.plazaId, crossing.at)
                    null
                }
                null -> {
                    warn("no class for profile ${profile.id} in scheme ${scheme.value}")
                    missingFare(crossing, entry)
                    null
                }
            }
        }

        private fun missingFare(crossing: PlazaCrossing, entry: PlazaId?) {
            items += TollItem.MissingFare(crossing.plazaId, crossing.at, entry)
            lower(Confidence.LOW)
        }

        private fun warn(message: String) {
            warnings += message
        }

        private fun lower(to: Confidence) {
            confidence = minOf(confidence, to)
        }
    }
}
