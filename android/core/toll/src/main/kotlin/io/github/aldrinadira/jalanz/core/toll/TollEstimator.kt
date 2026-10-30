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
    /** [now] is when the estimate is made; it drives the verified_at age rule (F10). */
    fun estimate(path: RoutePath, profile: VehicleProfile, now: Instant): TollEstimate =
        TODO("Plaza detection, fares and discounts arrive in WP 1.3-1.5 (spec 10 §5); dataset ${dataset.version}")
}
