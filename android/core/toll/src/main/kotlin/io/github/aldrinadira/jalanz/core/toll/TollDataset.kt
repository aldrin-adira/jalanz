package io.github.aldrinadira.jalanz.core.toll

import java.time.Instant

/**
 * One released toll dataset (04 §4.4), held in memory with lookups by key and time.
 * Every `*At` lookup returns the row effective at `t`, or null when the dataset has none:
 * callers must treat null as "unknown", never as zero (04 §1 principle 1).
 */
class TollDataset(
    val version: String,
    plazas: List<Plaza>,
    val osmRefs: List<PlazaOsmRef> = emptyList(),
    roles: List<PlazaRole> = emptyList(),
    openFares: List<OpenFare> = emptyList(),
    closedFares: List<ClosedFare> = emptyList(),
    systemPeriods: List<HighwaySystemPeriod> = emptyList(),
    val discountEvents: List<DiscountEvent> = emptyList(),
    classMappings: List<ClassMapping> = emptyList(),
) {
    private val plazasById = plazas.associateBy { it.id }
    private val rolesByPlaza = roles.groupBy { it.plazaId }
    private val openFaresByKey = openFares.groupBy { OpenFareKey(it.plazaId, it.vehicleClass) }
    private val closedFaresByKey = closedFares.groupBy {
        ClosedFareKey(it.networkId, it.entryPlazaId, it.exitPlazaId, it.vehicleClass)
    }
    private val systemByHighway = systemPeriods.groupBy { it.highwayId }
    private val classByProfile = classMappings.associateBy({ it.profile to it.scheme }, { it.assignment })

    val plazas: Collection<Plaza> get() = plazasById.values

    fun plaza(id: PlazaId): Plaza? = plazasById[id]

    /** Role for traffic arriving on [inWayId]; a row for that exact direction wins over an any-direction row. */
    fun plazaRoleAt(plazaId: PlazaId, inWayId: OsmWayId?, t: Instant): PlazaRole? =
        rolesByPlaza[plazaId].orEmpty().forDirection(inWayId, t) { it.inWayId }

    fun openFareAt(plazaId: PlazaId, inWayId: OsmWayId?, vehicleClass: VehicleClass, t: Instant): OpenFare? =
        openFaresByKey[OpenFareKey(plazaId, vehicleClass)].orEmpty().forDirection(inWayId, t) { it.inWayId }

    fun closedFareAt(
        networkId: NetworkId,
        entry: PlazaId,
        exit: PlazaId,
        vehicleClass: VehicleClass,
        t: Instant,
    ): ClosedFare? = closedFaresByKey[ClosedFareKey(networkId, entry, exit, vehicleClass)].orEmpty().effectiveAt(t)

    fun systemAt(highwayId: HighwayId, t: Instant): HighwaySystemPeriod? =
        systemByHighway[highwayId].orEmpty().effectiveAt(t)

    /** Null when no mapping exists for this profile and scheme (unknown, not "free"). */
    fun classFor(profile: VehicleProfile, scheme: ClassSchemeId): ClassAssignment? = classByProfile[profile to scheme]

    private data class OpenFareKey(val plazaId: PlazaId, val vehicleClass: VehicleClass)

    private data class ClosedFareKey(
        val networkId: NetworkId,
        val entry: PlazaId,
        val exit: PlazaId,
        val vehicleClass: VehicleClass,
    )
}

/** Direction-specific rows first; rows with a null direction apply to any direction (04 §3 `in_way_id`). */
private fun <T : EffectiveDated> List<T>.forDirection(inWayId: OsmWayId?, t: Instant, direction: (T) -> OsmWayId?): T? {
    if (inWayId != null) {
        filter { direction(it) == inWayId }.effectiveAt(t)?.let { return it }
    }
    return filter { direction(it) == null }.effectiveAt(t)
}
