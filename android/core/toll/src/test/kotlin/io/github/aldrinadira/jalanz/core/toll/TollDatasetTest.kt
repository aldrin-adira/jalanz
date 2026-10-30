package io.github.aldrinadira.jalanz.core.toll

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Synthetic data only (TEST- prefix, 04 §9); real fares arrive with the fixtures in WP 1.2. */
class TollDatasetTest {
    private val since2020 = EffectivePeriod(malaysiaTime(2020, 1, 1, 0, 0), null)
    private val newYear2027 = malaysiaTime(2027, 1, 1, 0, 0)
    private val verified = malaysiaTime(2026, 10, 23, 2, 10)
    private val source = SourceId("test")

    private val network = NetworkId("TEST-NET")
    private val entry = PlazaId("TEST-ENTRY")
    private val exit = PlazaId("TEST-EXIT")
    private val bridge = PlazaId("TEST-BRIDGE")
    private val bridgeEastbound = OsmWayId(1001)
    private val bridgeWestbound = OsmWayId(1002)
    private val class1 = VehicleClass("1")

    private fun plaza(id: PlazaId, scheme: ClassSchemeId = ClassSchemeId.LLM_1_5) = Plaza(
        id = id,
        highwayId = HighwayId("TEST-HWY"),
        nameMs = id.value,
        nameEn = null,
        kind = PlazaKind.BOOTH,
        classScheme = scheme,
        location = GeoPoint(lat = 3.0, lon = 101.0),
        active = since2020,
    )

    private val dataset = TollDataset(
        version = "test-1",
        plazas = listOf(plaza(entry), plaza(exit), plaza(bridge, ClassSchemeId("PENANG_BRIDGE"))),
        roles = listOf(
            PlazaRole(entry, null, Role.CLOSED_ENTRY, network, since2020),
            PlazaRole(exit, null, Role.CLOSED_EXIT, network, since2020),
            // Edge case 1 (04 §7) / fixture F4: charged one direction only.
            PlazaRole(bridge, null, Role.OPEN_NO_CHARGE, null, since2020),
            PlazaRole(bridge, bridgeEastbound, Role.OPEN_CHARGE, null, since2020),
        ),
        openFares = listOf(
            OpenFare(bridge, bridgeEastbound, class1, Sen(700), since2020, source, verified),
        ),
        closedFares = listOf(
            ClosedFare(
                network, entry, exit, class1, Sen(1500), distanceKm = 100.0,
                period = EffectivePeriod(malaysiaTime(2020, 1, 1, 0, 0), newYear2027),
                sourceId = source, verifiedAt = verified,
            ),
            ClosedFare(
                network, entry, exit, class1, Sen(1600), distanceKm = 100.0,
                period = EffectivePeriod(newYear2027, null),
                sourceId = source, verifiedAt = verified,
            ),
        ),
        systemPeriods = listOf(
            HighwaySystemPeriod(HighwayId("TEST-HWY"), SystemType.CLOSED, network,
                EffectivePeriod(malaysiaTime(2020, 1, 1, 0, 0), newYear2027)),
            HighwaySystemPeriod(HighwayId("TEST-HWY"), SystemType.OPEN, null, EffectivePeriod(newYear2027, null)),
        ),
        classMappings = listOf(
            ClassMapping(VehicleProfile.CAR, ClassSchemeId.LLM_1_5, ClassAssignment.Charged(class1)),
            ClassMapping(VehicleProfile.MOTORCYCLE, ClassSchemeId.LLM_1_5, ClassAssignment.NotCharged),
        ),
    )

    private val midYear2026 = malaysiaTime(2026, 6, 1, 12, 0)

    @Test
    fun `finds plazas by id`() {
        assertEquals("TEST-EXIT", dataset.plaza(exit)?.nameMs)
        assertNull(dataset.plaza(PlazaId("TEST-MISSING")))
    }

    @Test
    fun `direction specific role wins over any-direction role`() {
        assertEquals(Role.OPEN_CHARGE, dataset.plazaRoleAt(bridge, bridgeEastbound, midYear2026)?.role)
        assertEquals(Role.OPEN_NO_CHARGE, dataset.plazaRoleAt(bridge, bridgeWestbound, midYear2026)?.role)
        assertEquals(Role.OPEN_NO_CHARGE, dataset.plazaRoleAt(bridge, null, midYear2026)?.role)
    }

    @Test
    fun `open fare is looked up per direction and class`() {
        assertEquals(Sen(700), dataset.openFareAt(bridge, bridgeEastbound, class1, midYear2026)?.amount)
        assertNull(dataset.openFareAt(bridge, bridgeWestbound, class1, midYear2026))
        assertNull(dataset.openFareAt(bridge, bridgeEastbound, VehicleClass("2"), midYear2026))
    }

    @Test
    fun `closed fare uses the rate effective at exit time`() {
        // Fixture F8 timing: depart before the change, exit after it.
        val beforeChange = malaysiaTime(2026, 12, 31, 23, 50)
        val afterChange = malaysiaTime(2027, 1, 1, 0, 20)
        assertEquals(Sen(1500), dataset.closedFareAt(network, entry, exit, class1, beforeChange)?.amount)
        assertEquals(Sen(1600), dataset.closedFareAt(network, entry, exit, class1, afterChange)?.amount)
        assertNull(dataset.closedFareAt(network, exit, entry, class1, afterChange))
    }

    @Test
    fun `highway system type is effective dated`() {
        val hwy = HighwayId("TEST-HWY")
        assertEquals(SystemType.CLOSED, dataset.systemAt(hwy, malaysiaTime(2026, 12, 31, 23, 59))?.systemType)
        assertEquals(SystemType.OPEN, dataset.systemAt(hwy, newYear2027)?.systemType)
    }

    @Test
    fun `maps vehicle profile to class per scheme`() {
        assertEquals(ClassAssignment.Charged(class1), dataset.classFor(VehicleProfile.CAR, ClassSchemeId.LLM_1_5))
        assertEquals(ClassAssignment.NotCharged, dataset.classFor(VehicleProfile.MOTORCYCLE, ClassSchemeId.LLM_1_5))
        assertNull(dataset.classFor(VehicleProfile.CAR, ClassSchemeId("PENANG_BRIDGE")))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `fares can never be negative`() {
        OpenFare(bridge, null, class1, Sen(-1), since2020, source, verified)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `closed roles need a network`() {
        PlazaRole(entry, null, Role.CLOSED_ENTRY, null, since2020)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `closed highway periods need a network`() {
        HighwaySystemPeriod(HighwayId("TEST-HWY"), SystemType.CLOSED, null, since2020)
    }
}
