package io.github.aldrinadira.jalanz.core.toll

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rules from 04 §6 that fixtures F1–F10 do not cover. Synthetic data only. */
class TollEstimatorTest {
    private val since2020 = EffectivePeriod(malaysiaTime(2020, 1, 1, 0, 0), null)
    private val now = malaysiaTime(2026, 6, 1, 9, 0)
    private val net = NetworkId("TEST-NET")
    private val class1 = VehicleClass("1")

    private fun plaza(id: String, node: Long) = Plaza(
        PlazaId(id), HighwayId("TEST-HWY"), id, null, PlazaKind.BOOTH, ClassSchemeId.LLM_1_5,
        GeoPoint(3.0 + node / 100.0, 101.0), since2020,
    )

    private fun role(id: String, role: Role, network: NetworkId? = net) = PlazaRole(PlazaId(id), null, role, network, since2020)

    private fun closedFare(entry: String, exit: String, sen: Long) =
        ClosedFare(net, PlazaId(entry), PlazaId(exit), class1, Sen(sen), null, since2020, SourceId("test"), now)

    /** Plazas TEST-1..TEST-n sit on nodes 1..n along one northbound path. */
    private fun path(vararg nodes: Long) = RoutePath(
        nodes.mapIndexed { i, node ->
            PathPoint(OsmNodeId(node), OsmWayId(100 + i.toLong()), GeoPoint(3.0 + node / 100.0, 101.0), now.plusSeconds(3600L + i * 60))
        },
    )

    private fun dataset(
        roles: List<PlazaRole>,
        closedFares: List<ClosedFare> = emptyList(),
        openFares: List<OpenFare> = emptyList(),
        mappings: List<ClassMapping> = listOf(
            ClassMapping(VehicleProfile.CAR, ClassSchemeId.LLM_1_5, ClassAssignment.Charged(class1)),
            ClassMapping(VehicleProfile.MOTORCYCLE, ClassSchemeId.LLM_1_5, ClassAssignment.NotCharged),
        ),
    ): TollDataset {
        val ids = roles.map { it.plazaId.value }.distinct()
        return TollDataset(
            version = "t",
            plazas = ids.map { plaza(it, it.removePrefix("TEST-").toLong()) },
            osmRefs = ids.map { PlazaOsmRef(PlazaId(it), OsmNodeId(it.removePrefix("TEST-").toLong()), null, null, null) },
            roles = roles,
            openFares = openFares,
            closedFares = closedFares,
            classMappings = mappings,
        )
    }

    private fun estimate(dataset: TollDataset, path: RoutePath, profile: VehicleProfile = VehicleProfile.CAR) =
        TollEstimator(dataset).estimate(path, profile, now)

    @Test
    fun `toggle plaza enters a network, then exits it`() {
        val data = dataset(
            roles = listOf(role("TEST-1", Role.CLOSED_TOGGLE), role("TEST-2", Role.CLOSED_TOGGLE)),
            closedFares = listOf(closedFare("TEST-1", "TEST-2", 420)),
        )
        val result = estimate(data, path(1, 2))
        assertEquals(Sen(420), result.total)
        assertEquals(Confidence.HIGH, result.confidence)
    }

    @Test
    fun `not charged class adds a zero item and keeps confidence`() {
        val data = dataset(
            roles = listOf(role("TEST-1", Role.OPEN_CHARGE, null)),
            openFares = listOf(OpenFare(PlazaId("TEST-1"), null, class1, Sen(300), since2020, SourceId("test"), now)),
        )
        val result = estimate(data, path(1), VehicleProfile.MOTORCYCLE)
        assertEquals(listOf<TollItem>(TollItem.NotCharged(PlazaId("TEST-1"), now.plusSeconds(3600))), result.items)
        assertEquals(Sen.ZERO, result.total)
        assertEquals(Confidence.HIGH, result.confidence)
    }

    @Test
    fun `missing fare is unknown, never zero`() {
        val data = dataset(roles = listOf(role("TEST-1", Role.CLOSED_ENTRY), role("TEST-2", Role.CLOSED_EXIT)))
        val result = estimate(data, path(1, 2))
        assertTrue(result.items.single() is TollItem.MissingFare)
        assertEquals(Confidence.LOW, result.confidence)
    }

    @Test
    fun `unknown vehicle class mapping lowers confidence to LOW`() {
        val data = dataset(
            roles = listOf(role("TEST-1", Role.OPEN_CHARGE, null)),
            mappings = emptyList(),
        )
        val result = estimate(data, path(1))
        assertEquals(Confidence.LOW, result.confidence)
        assertTrue(result.warnings.single().contains("no class"))
    }

    @Test
    fun `route ending inside a closed network is LOW with a warning`() {
        val data = dataset(roles = listOf(role("TEST-1", Role.CLOSED_ENTRY)))
        val result = estimate(data, path(1))
        assertEquals(Confidence.LOW, result.confidence)
        assertTrue(result.warnings.single().contains("ends inside"))
    }

    @Test
    fun `re-entry without exit lowers confidence to MEDIUM and prices from the last entry`() {
        val data = dataset(
            roles = listOf(role("TEST-1", Role.CLOSED_ENTRY), role("TEST-2", Role.CLOSED_ENTRY), role("TEST-3", Role.CLOSED_EXIT)),
            closedFares = listOf(closedFare("TEST-1", "TEST-3", 900), closedFare("TEST-2", "TEST-3", 500)),
        )
        val result = estimate(data, path(1, 2, 3))
        assertEquals(Sen(500), result.total)
        assertEquals(Confidence.MEDIUM, result.confidence)
    }

    @Test
    fun `plaza with no role is LOW, not free`() {
        val data = dataset(roles = listOf(role("TEST-1", Role.OPEN_NO_CHARGE, null)))
        val noRole = TollDataset(
            version = "t",
            plazas = data.plazas.toList() + plaza("TEST-2", 2),
            osmRefs = data.osmRefs + PlazaOsmRef(PlazaId("TEST-2"), OsmNodeId(2), null, null, null),
            roles = listOf(role("TEST-1", Role.OPEN_NO_CHARGE, null)),
        )
        assertEquals(Confidence.LOW, estimate(noRole, path(1, 2)).confidence)
    }

    @Test
    fun `estimate reports the oldest verified_at and the dataset version`() {
        val older = malaysiaTime(2026, 5, 20, 0, 0)
        val data = dataset(
            roles = listOf(role("TEST-1", Role.OPEN_CHARGE, null), role("TEST-2", Role.OPEN_CHARGE, null)),
            openFares = listOf(
                OpenFare(PlazaId("TEST-1"), null, class1, Sen(100), since2020, SourceId("test"), now),
                OpenFare(PlazaId("TEST-2"), null, class1, Sen(200), since2020, SourceId("test"), older),
            ),
        )
        val result = estimate(data, path(1, 2))
        assertEquals(older, result.verifiedAt)
        assertEquals("t", result.datasetVersion)
        assertEquals(Sen(300), result.total)
    }
}
