package io.github.aldrinadira.jalanz.core.toll

import io.github.aldrinadira.jalanz.core.toll.fixtures.TollFixture
import io.github.aldrinadira.jalanz.core.toll.fixtures.TollFixtures
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class PlazaDetectorTest {
    private fun fixture(id: String): TollFixture = TollFixtures.load(File(TollFixtures.directory, "$id.json"))

    private fun detect(fixtureId: String, caseId: String = fixtureId): List<PlazaCrossing> {
        val f = fixture(fixtureId)
        return PlazaDetector(f.dataset).detect(f.cases.first { it.id == caseId }.path)
    }

    @Test
    fun `F1 finds entry and exit by node, in path order`() {
        val crossings = detect("F1", "F1-class1")
        assertEquals(listOf("E1-SIMPANG-PULAI", "E1-JURU"), crossings.map { it.plazaId.value })
        assertEquals(listOf(OsmWayId(1001), OsmWayId(1003)), crossings.map { it.inWayId })
        assertEquals(setOf(DetectionMethod.NODE), crossings.map { it.method }.toSet())
    }

    @Test
    fun `F4 records the arrival way so direction rules can apply`() {
        assertEquals(listOf(OsmWayId(4001)), detect("F4", "F4-eastbound").map { it.inWayId })
        assertEquals(listOf(OsmWayId(4011)), detect("F4", "F4-westbound").map { it.inWayId })
    }

    @Test
    fun `F6 mainline beside a ramp plaza crosses nothing`() {
        assertEquals(emptyList<PlazaCrossing>(), detect("F6"))
    }

    @Test
    fun `F9 plaza off the routing graph is found geometrically`() {
        val crossing = detect("F9").single()
        assertEquals(PlazaId("TEST-GEO"), crossing.plazaId)
        assertEquals(DetectionMethod.GEOMETRIC, crossing.method)
        assertEquals(OsmWayId(9001), crossing.inWayId)
        // Plaza is half-way between the nodes at 10:00:20 and 10:01:00.
        assertEquals(malaysiaTime(2026, 6, 1, 10, 0).plusSeconds(40), crossing.at)
    }

    @Test
    fun `F7 keeps all five plazas in order`() {
        assertEquals(
            listOf("TEST-A-IN", "TEST-A-OUT", "TEST-MID", "TEST-B-IN", "TEST-B-OUT"),
            detect("F7").map { it.plazaId.value },
        )
    }

    // Synthetic cases not covered by the fixtures.

    private val since2020 = EffectivePeriod(malaysiaTime(2020, 1, 1, 0, 0), null)

    private fun plaza(id: String, lat: Double, lon: Double, active: EffectivePeriod = since2020) = Plaza(
        PlazaId(id), HighwayId("TEST-HWY"), id, null, PlazaKind.BOOTH, ClassSchemeId.LLM_1_5, GeoPoint(lat, lon), active,
    )

    private fun point(node: Long?, way: Long, lat: Double, minute: Int) =
        PathPoint(node?.let(::OsmNodeId), OsmWayId(way), GeoPoint(lat, 101.0), malaysiaTime(2026, 6, 1, 10, minute))

    private val northbound = RoutePath(
        listOf(point(null, 1, 2.99, 0), point(1, 1, 3.00, 1), point(2, 2, 3.01, 2), point(null, 2, 3.02, 3)),
    )

    @Test
    fun `node on the path in the wrong direction is neither node nor geometric match`() {
        val dataset = TollDataset(
            version = "t",
            plazas = listOf(plaza("TEST-ONEWAY", 3.00, 101.0)),
            osmRefs = listOf(PlazaOsmRef(PlazaId("TEST-ONEWAY"), OsmNodeId(1), OsmWayId(99), null, null)),
        )
        assertEquals(emptyList<PlazaCrossing>(), PlazaDetector(dataset).detect(northbound))
    }

    @Test
    fun `plaza without refs or bearing matches on distance alone`() {
        val dataset = TollDataset(version = "t", plazas = listOf(plaza("TEST-NEW", 3.005, 101.0)))
        val crossing = PlazaDetector(dataset).detect(northbound).single()
        assertEquals(DetectionMethod.GEOMETRIC, crossing.method)
        assertEquals(OsmWayId(2), crossing.inWayId)
    }

    @Test
    fun `plaza more than 15 m away is ignored`() {
        // 0.0002 deg of longitude at 3 deg N is about 22 m.
        val dataset = TollDataset(version = "t", plazas = listOf(plaza("TEST-FAR", 3.005, 101.0002)))
        assertEquals(emptyList<PlazaCrossing>(), PlazaDetector(dataset).detect(northbound))
    }

    @Test
    fun `plaza not active at crossing time is ignored`() {
        val closed = EffectivePeriod(malaysiaTime(2020, 1, 1, 0, 0), malaysiaTime(2026, 1, 1, 0, 0))
        val dataset = TollDataset(
            version = "t",
            plazas = listOf(plaza("TEST-OLD", 3.00, 101.0, closed)),
            osmRefs = listOf(PlazaOsmRef(PlazaId("TEST-OLD"), OsmNodeId(1), null, null, null)),
        )
        assertEquals(emptyList<PlazaCrossing>(), PlazaDetector(dataset).detect(northbound))
    }
}
