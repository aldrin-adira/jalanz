package io.github.aldrinadira.jalanz.core.toll

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class EffectivePeriodTest {
    private val newYear2027 = malaysiaTime(2027, 1, 1, 0, 0)

    @Test
    fun `malaysia time is utc plus eight`() {
        assertEquals(Instant.parse("2026-12-31T16:00:00Z"), newYear2027)
    }

    @Test
    fun `period is half open - from is inside, to is outside`() {
        val period = EffectivePeriod(malaysiaTime(2026, 1, 1, 0, 0), newYear2027)
        assertTrue(period.contains(malaysiaTime(2026, 1, 1, 0, 0)))
        assertTrue(period.contains(newYear2027.minusSeconds(1)))
        assertFalse(period.contains(newYear2027))
        assertFalse(period.contains(malaysiaTime(2025, 12, 31, 23, 59)))
    }

    @Test
    fun `open ended period has no end`() {
        val period = EffectivePeriod(newYear2027, to = null)
        assertTrue(period.contains(malaysiaTime(2099, 1, 1, 0, 0)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `end must be after start`() {
        EffectivePeriod(newYear2027, newYear2027)
    }

    private data class Row(val label: String, override val period: EffectivePeriod) : EffectiveDated

    // Fixture F8 boundary: old rate until 2027-01-01 00:00 MYT, new rate from then.
    private val oldRate = Row("old", EffectivePeriod(malaysiaTime(2020, 1, 1, 0, 0), newYear2027))
    private val newRate = Row("new", EffectivePeriod(newYear2027, null))

    @Test
    fun `selects the row effective at the given time`() {
        val rows = listOf(oldRate, newRate)
        assertEquals("old", rows.effectiveAt(malaysiaTime(2026, 12, 31, 23, 50))?.label)
        assertEquals("new", rows.effectiveAt(malaysiaTime(2027, 1, 1, 0, 20))?.label)
        assertEquals("new", rows.effectiveAt(newYear2027)?.label)
    }

    @Test
    fun `returns null when nothing is effective`() {
        assertNull(listOf(newRate).effectiveAt(malaysiaTime(2026, 6, 1, 0, 0)))
    }

    @Test(expected = OverlappingPeriodsException::class)
    fun `overlapping rows are a data error, never a guess`() {
        val overlapping = Row("overlap", EffectivePeriod(malaysiaTime(2026, 6, 1, 0, 0), null))
        listOf(oldRate, overlapping).effectiveAt(malaysiaTime(2026, 7, 1, 0, 0))
    }
}
