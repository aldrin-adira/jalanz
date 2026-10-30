package io.github.aldrinadira.jalanz.core.toll

import java.time.Instant

// Toll domain model, mirroring the tables in spec 04 §3. Provenance tables (source_snapshot, change_set,
// dataset_release) stay server-side; the device only needs source IDs and verified_at.

@JvmInline value class PlazaId(val value: String)
@JvmInline value class HighwayId(val value: String)
@JvmInline value class NetworkId(val value: String)
@JvmInline value class SourceId(val value: String)
@JvmInline value class DiscountEventId(val value: String)
@JvmInline value class OsmWayId(val value: Long)
@JvmInline value class OsmNodeId(val value: Long)

/** Class codes are only meaningful inside a [ClassSchemeId], e.g. "1".."5" for LLM_1_5. */
@JvmInline value class VehicleClass(val code: String)

@JvmInline
value class ClassSchemeId(val value: String) {
    companion object {
        val LLM_1_5 = ClassSchemeId("LLM_1_5")
    }
}

@JvmInline
value class VehicleProfile(val id: String) {
    companion object {
        val CAR = VehicleProfile("car")
        val TAXI = VehicleProfile("taxi")
        val MOTORCYCLE = VehicleProfile("motorcycle")
    }
}

/** WGS84 point. Not money, so floating point is fine. */
data class GeoPoint(val lat: Double, val lon: Double)

enum class SystemType { OPEN, CLOSED, FREE }

enum class PlazaKind { BOOTH, GANTRY }

enum class Role(val isClosed: Boolean) {
    OPEN_CHARGE(false),
    OPEN_NO_CHARGE(false),
    CLOSED_ENTRY(true),
    CLOSED_EXIT(true),
    CLOSED_TOGGLE(true),
}

data class Highway(
    val id: HighwayId,
    val nameMs: String,
    val nameEn: String?,
    val shortName: String?,
)

data class HighwaySystemPeriod(
    val highwayId: HighwayId,
    val systemType: SystemType,
    val networkId: NetworkId?,
    override val period: EffectivePeriod,
) : EffectiveDated {
    init {
        require(systemType != SystemType.CLOSED || networkId != null) { "closed highway ${highwayId.value} needs a network" }
    }
}

data class Plaza(
    val id: PlazaId,
    val highwayId: HighwayId,
    val nameMs: String,
    val nameEn: String?,
    val kind: PlazaKind,
    val classScheme: ClassSchemeId,
    val location: GeoPoint,
    val active: EffectivePeriod,
)

/** OSM node of a booth/gantry; [inWayId] fixes the direction of travel (04 §5). */
data class PlazaOsmRef(
    val plazaId: PlazaId,
    val osmNodeId: OsmNodeId,
    val inWayId: OsmWayId?,
    val outWayId: OsmWayId?,
    val bearingDeg: Double?,
)

/** What a plaza does for traffic arriving on [inWayId] (null = any direction). */
data class PlazaRole(
    val plazaId: PlazaId,
    val inWayId: OsmWayId?,
    val role: Role,
    val networkId: NetworkId?,
    override val period: EffectivePeriod,
) : EffectiveDated {
    init {
        require(!role.isClosed || networkId != null) { "closed role $role at ${plazaId.value} needs a network" }
    }
}

data class OpenFare(
    val plazaId: PlazaId,
    val inWayId: OsmWayId?,
    val vehicleClass: VehicleClass,
    val amount: Sen,
    override val period: EffectivePeriod,
    val sourceId: SourceId,
    val verifiedAt: Instant,
) : EffectiveDated {
    init {
        require(amount >= Sen.ZERO) { "negative fare at ${plazaId.value}" }
    }
}

data class ClosedFare(
    val networkId: NetworkId,
    val entryPlazaId: PlazaId,
    val exitPlazaId: PlazaId,
    val vehicleClass: VehicleClass,
    val amount: Sen,
    val distanceKm: Double?,
    override val period: EffectivePeriod,
    val sourceId: SourceId,
    val verifiedAt: Instant,
) : EffectiveDated {
    init {
        require(amount >= Sen.ZERO) { "negative fare ${entryPlazaId.value}->${exitPlazaId.value}" }
    }
}

/** How a discount changes a fare. Percentages in basis points (5000 = 50 %) to avoid floating point. */
sealed interface DiscountAmount {
    data class PercentOff(val basisPoints: Int) : DiscountAmount {
        init {
            require(basisPoints in 1..10_000) { "percent off out of range: $basisPoints bp" }
        }
    }

    data class FixedAmountOff(val amount: Sen) : DiscountAmount
    data object Free : DiscountAmount
}

/** How a discounted fare is rounded to whole sen. `[VERIFY]` per announcement (04 §3). */
enum class DiscountRounding { HALF_UP_SEN, DOWN_SEN, UP_SEN }

enum class DiscountScopeKind { ALL, HIGHWAY, NETWORK, PLAZA }

/** What a discount event includes; [scopeId] is null for [DiscountScopeKind.ALL]. */
data class DiscountScope(
    val kind: DiscountScopeKind,
    val scopeId: String?,
    val classScheme: ClassSchemeId?,
    val vehicleClass: VehicleClass?,
)

/** E.g. a festive discount. [window] is when it applies, judged at transaction time (04 §6.1). */
data class DiscountEvent(
    val id: DiscountEventId,
    val name: String,
    val window: EffectivePeriod,
    val amount: DiscountAmount,
    val rounding: DiscountRounding,
    val scopes: List<DiscountScope>,
    val excludedPlazas: Set<PlazaId>,
    val sourceUrl: String,
) {
    init {
        require(window.to != null) { "discount ${id.value} needs an end time" }
    }
}

/** Result of mapping a vehicle profile to a plaza's class scheme. */
sealed interface ClassAssignment {
    data class Charged(val vehicleClass: VehicleClass) : ClassAssignment
    data object NotCharged : ClassAssignment
}

data class ClassMapping(
    val profile: VehicleProfile,
    val scheme: ClassSchemeId,
    val assignment: ClassAssignment,
)
