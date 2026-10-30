package io.github.aldrinadira.jalanz.core.searchclient

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedLocationTest {
    private fun found(lat: Double, lon: Double, label: String? = null) = SharedLocation.Found(lat, lon, label)

    @Test
    fun `plain coordinates as Google Maps shows them after a long-press`() {
        assertEquals(found(3.139, 101.6869), parseSharedLocation("3.13900, 101.68690"))
        assertEquals(found(3.139, 101.6869), parseSharedLocation(" 3.139,101.6869 "))
        assertEquals(found(-6.2, 106.8), parseSharedLocation("(-6.2, 106.8)"))
    }

    @Test
    fun `geo uris, with and without a label`() {
        assertEquals(found(3.139, 101.6869), parseSharedLocation("geo:3.139,101.6869"))
        assertEquals(found(3.139, 101.6869), parseSharedLocation("geo:3.139,101.6869?z=17"))
        assertEquals(
            found(3.0724, 101.6066, "Sunway Pyramid"),
            parseSharedLocation("geo:0,0?q=3.0724,101.6066(Sunway%20Pyramid)"),
        )
    }

    @Test
    fun `links that carry coordinates literally`() {
        assertEquals(
            found(3.1176, 101.6774, "Mid Valley Megamall"),
            parseSharedLocation("Mid Valley Megamall\nhttps://www.google.com/maps/place/Mid+Valley/@3.1176,101.6774,17z"),
        )
        assertEquals(found(3.1, 101.6), parseSharedLocation("https://maps.google.com/maps?q=3.1,101.6"))
        assertEquals(found(3.1, 101.6), parseSharedLocation("https://www.google.com/maps/search/?api=1&query=3.1%2C101.6"))
        assertEquals(found(3.1, 101.6), parseSharedLocation("https://waze.com/ul?ll=3.1,101.6&navigate=yes"))
    }

    @Test
    fun `short links hide coordinates and are never expanded`() {
        assertEquals(
            SharedLocation.NeedsCoordinates,
            parseSharedLocation("Beary Potter Studio\nhttps://maps.app.goo.gl/AbCdEf123"),
        )
        assertEquals(SharedLocation.NeedsCoordinates, parseSharedLocation("https://goo.gl/maps/xyz"))
    }

    @Test
    fun `text without a location`() {
        assertEquals(SharedLocation.NotFound, parseSharedLocation("Jalan 2/10b, Batu Caves"))
        assertEquals(SharedLocation.NotFound, parseSharedLocation("91.0, 200.0"))
        assertEquals(SharedLocation.NotFound, parseSharedLocation("   "))
    }

    @Test
    fun `search box coordinates must be the whole text`() {
        assertEquals(3.139 to 101.6869, parseCoordinates("3.139, 101.6869"))
        assertNull(parseCoordinates("Lot 3, 101 Jalan Ampang"))
        assertNull(parseCoordinates("mid valley"))
    }

    @Test
    fun `coordinates are formatted lat first with five decimals`() {
        assertEquals("3.13900, 101.68690", formatCoordinates(3.139, 101.6869))
    }
}
