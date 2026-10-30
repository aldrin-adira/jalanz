package io.github.aldrinadira.jalanz.core.toll.fixtures

import io.github.aldrinadira.jalanz.core.toll.Confidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Checks the fixture files themselves, independent of any engine, so a typo cannot hide inside the contract. */
class FixtureFilesTest {
    private val fixtures = TollFixtures.loadAll()
    private val allCases = fixtures.flatMap { it.cases }

    @Test
    fun `every fixture from spec 04 section 9 exists`() {
        assertEquals((1..10).map { "F$it" }, fixtures.map { it.id })
        val caseIds = allCases.map { it.id }.toSet()
        for (id in listOf("F2a", "F2b", "F2c", "F2d", "F5", "F6", "F7", "F8", "F9", "F10")) {
            assertTrue("missing case $id", id in caseIds)
        }
    }

    @Test
    fun `case ids are unique`() {
        val ids = allCases.map { it.id }
        assertEquals(ids.distinct(), ids)
    }

    @Test
    fun `real fixtures cite their source and synthetic ones use TEST ids`() {
        for (fixture in fixtures) {
            when (fixture.kind) {
                "real" -> {
                    assertNotNull("${fixture.id} retrieved_at", fixture.retrievedAt)
                    assertNotNull("${fixture.id} source", fixture.source)
                }
                "synthetic" -> fixture.dataset.plazas.forEach {
                    assertTrue("${fixture.id}: ${it.id.value}", it.id.value.startsWith("TEST-"))
                }
                else -> error("${fixture.id}: unknown kind ${fixture.kind}")
            }
        }
    }

    @Test
    fun `expected totals add up`() {
        for (case in allCases) {
            val e = case.expected
            val min = e.items.sumOf { it.chargedSen ?: it.rangeSen!![0] }
            val max = e.items.sumOf { it.chargedSen ?: it.rangeSen!![1] }
            if (e.rangeSen == null) {
                assertEquals("${case.id} total", max, e.totalSen)
                assertTrue("${case.id}: items without a fare need a range", e.items.all { it.chargedSen != null })
            } else {
                assertEquals("${case.id} range", listOf(min, max), e.rangeSen)
                assertEquals("${case.id}: total is the upper end of the range", max, e.totalSen)
                assertEquals("${case.id}: a range means LOW", Confidence.LOW, e.confidence)
            }
        }
    }

    @Test
    fun `expected items refer to plazas and discounts in the same dataset`() {
        for (fixture in fixtures) {
            val plazaIds = fixture.dataset.plazas.map { it.id.value }.toSet()
            val discountIds = fixture.dataset.discountEvents.map { it.id.value }.toSet()
            for (item in fixture.cases.flatMap { it.expected.items }) {
                listOfNotNull(item.plazaId, item.entry, item.exit).forEach {
                    assertTrue("${fixture.id}: unknown plaza $it", it in plazaIds)
                }
                item.discountEventId?.let { assertTrue("${fixture.id}: unknown discount $it", it in discountIds) }
            }
        }
    }

    @Test
    fun `path times never go backwards`() {
        for (case in allCases) {
            val times = case.path.points.map { it.at }
            assertFalse("${case.id}: times go backwards", times.zipWithNext().any { (a, b) -> b.isBefore(a) })
        }
    }
}
