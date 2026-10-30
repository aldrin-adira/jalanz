package io.github.aldrinadira.jalanz.core.toll

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SenTest {
    @Test
    fun `arithmetic stays in whole sen`() {
        assertEquals(Sen(1525), Sen(1000) + Sen(525))
        assertEquals(Sen(-200), Sen(1325) - Sen(1525))
        assertEquals(Sen(3050), Sen(1525) * 2)
    }

    @Test
    fun `sum of an empty list is zero`() {
        assertEquals(Sen.ZERO, emptyList<Sen>().sum())
        assertEquals(Sen(2290), listOf(Sen(1525), Sen(765)).sum())
    }

    @Test
    fun `orders by amount`() {
        assertTrue(Sen(762) < Sen(763))
        assertEquals(Sen(1525), listOf(Sen(763), Sen(1525), Sen(0)).max())
    }

    @Test
    fun `formats as ringgit for logs and debugging`() {
        assertEquals("RM15.25", Sen(1525).toRinggitString())
        assertEquals("RM0.05", Sen(5).toRinggitString())
        assertEquals("-RM2.00", Sen(-200).toRinggitString())
    }
}
