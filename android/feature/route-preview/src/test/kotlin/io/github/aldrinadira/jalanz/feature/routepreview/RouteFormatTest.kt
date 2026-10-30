package io.github.aldrinadira.jalanz.feature.routepreview

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class RouteFormatTest {
    @Test
    fun `durations round to the nearest minute`() {
        assertEquals(1L to 1L, hoursAndMinutes(3668))
        assertEquals(0L to 1L, hoursAndMinutes(10))
        assertEquals(0L to 59L, hoursAndMinutes(3539))
        assertEquals(1L to 0L, hoursAndMinutes(3570))
    }

    @Test
    fun `distances in km with one decimal, metres when short`() {
        assertEquals("64.4 km", formatDistance(64_412, Locale.ENGLISH))
        assertEquals("850 m", formatDistance(850, Locale.ENGLISH))
        assertEquals("1.0 km", formatDistance(1_000, Locale.ENGLISH))
    }
}
