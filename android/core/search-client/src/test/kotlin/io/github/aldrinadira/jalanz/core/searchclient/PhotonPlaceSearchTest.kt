package io.github.aldrinadira.jalanz.core.searchclient

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotonPlaceSearchTest {
    /** Trimmed shape of a real Photon response (30 Oct 2026), with one duplicate and one unnamed feature. */
    private val response = """
        {"type":"FeatureCollection","features":[
          {"type":"Feature","geometry":{"type":"Point","coordinates":[101.6773741,3.1176552]},
           "properties":{"osm_type":"W","osm_key":"shop","osm_value":"mall","name":"Mid Valley Megamall",
             "street":"Lingkaran Syed Putra","city":"Kuala Lumpur","country":"Malaysia"}},
          {"type":"Feature","geometry":{"type":"Point","coordinates":[101.6780000,3.1180000]},
           "properties":{"name":"Mid Valley Megamall","street":"Lingkaran Syed Putra","city":"Kuala Lumpur"}},
          {"type":"Feature","geometry":{"type":"Point","coordinates":[101.0901079,4.5960075]},
           "properties":{"name":"Ipoh Parade","street":"Jalan Sultan Abdul Jalil","city":"Ipoh","state":"Perak"}},
          {"type":"Feature","geometry":{"type":"Point","coordinates":[101.60,3.07]},
           "properties":{"housenumber":"12","street":"Jalan PJS 11/15","city":"Subang Jaya","state":"Selangor"}},
          {"type":"Feature","geometry":{"type":"Point","coordinates":[101.0,3.0]},"properties":{"osm_value":"yes"}}
        ]}
    """.trimIndent()

    @Test
    fun `parses names, address detail and coordinates`() {
        val places = PhotonPlaceSearch.parse(response)
        assertEquals(
            listOf(
                Place("Mid Valley Megamall", "Lingkaran Syed Putra, Kuala Lumpur", 3.1176552, 101.6773741),
                Place("Ipoh Parade", "Jalan Sultan Abdul Jalil, Ipoh, Perak", 4.5960075, 101.0901079),
                Place("12 Jalan PJS 11/15", "Subang Jaya, Selangor", 3.07, 101.60),
            ),
            places,
        )
    }

    @Test
    fun `request is limited to peninsular malaysia, biased to the given place and skips transit stops`() {
        val urls = mutableListOf<String>()
        val search = PhotonPlaceSearch(http = { url -> urls += url; """{"features":[]}""" })
        runTest { search.search("  pasar seni ", near = Place("KL", "", 3.139, 101.687)) }

        val url = urls.single()
        assertTrue(url, url.startsWith("https://photon.komoot.io/api/?q=pasar+seni&"))
        assertTrue(url, "&bbox=99.6,1.2,104.6,6.8" in url)
        assertTrue(url, "&lat=3.139&lon=101.687" in url)
        assertTrue(url, "&osm_tag=!highway:bus_stop" in url)
    }

    @Test
    fun `blank query never calls the network`() = runTest {
        val search = PhotonPlaceSearch(http = { error("must not be called") })
        assertEquals(emptyList<Place>(), search.search("   "))
    }
}
