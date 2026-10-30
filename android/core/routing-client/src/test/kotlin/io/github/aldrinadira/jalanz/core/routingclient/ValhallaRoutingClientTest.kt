package io.github.aldrinadira.jalanz.core.routingclient

import io.github.aldrinadira.jalanz.core.searchclient.Place
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder

class ValhallaRoutingClientTest {
    private val kl = Place("KL", "", 3.139, 101.6869)
    private val seremban = Place("Seremban", "", 2.7258, 101.9424)

    @Test
    fun `decodes polylines at precision 5 and 6`() {
        // Google's documented example, precision 5.
        assertEquals(
            listOf(LatLon(38.5, -120.2), LatLon(40.7, -120.95), LatLon(43.252, -126.453)),
            decodePolyline("_p~iF~ps|U_ulLnnqC_mqNvxq`@", precision = 5),
        )
        assertEquals(
            listOf(LatLon(3.139, 101.6869), LatLon(3.1, 101.7), LatLon(2.7258, 101.9424)),
            decodePolyline("ozq~Dgfn}`EndkAwqXnzyU_mxM"),
        )
    }

    /** Shape of a real FOSSGIS response (30 Oct 2026), trimmed: one main trip and one alternate. */
    private val response = """
        {"trip":{"legs":[{"shape":"ozq~Dgfn}`EndkAwqXnzyU_mxM"}],
                 "summary":{"has_toll":true,"has_ferry":false,"time":3668.5,"length":64.412}},
         "alternates":[{"trip":{"legs":[{"shape":"ozq~Dgfn}`EndkAwqXnzyU_mxM"}],
                 "summary":{"has_toll":false,"time":4680.2,"length":73.7}}}]}
    """.trimIndent()

    @Test
    fun `parses main trip and alternates, fastest first as Valhalla returns them`() {
        val routes = ValhallaRoutingClient.parse(response)
        assertEquals(2, routes.size)
        assertEquals(64_412L, routes[0].distanceMeters)
        assertEquals(3_669L, routes[0].durationSeconds)
        assertTrue(routes[0].hasToll)
        assertFalse(routes[1].hasToll)
        assertEquals(LatLon(2.7258, 101.9424), routes[1].points.last())
    }

    @Test
    fun `avoid tolls excludes toll roads instead of only discouraging them`() {
        fun request(avoid: Boolean) = Json.parseToJsonElement(
            URLDecoder.decode(
                ValhallaRoutingClient().url(kl, seremban, avoid).substringAfter("json="),
                "UTF-8",
            ),
        ).jsonObject.toString()

        assertTrue(request(avoid = true).contains(""""exclude_tolls":true"""))
        assertTrue(request(avoid = true).contains(""""alternates":0"""))
        assertTrue(request(avoid = false).contains(""""use_tolls":0.5"""))
        assertTrue(request(avoid = false).contains(""""alternates":2"""))
        assertTrue(request(avoid = false).contains(""""lat":3.139,"lon":101.6869"""))
    }
}
