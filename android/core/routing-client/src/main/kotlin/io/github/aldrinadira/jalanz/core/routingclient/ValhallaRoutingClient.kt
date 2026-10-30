package io.github.aldrinadira.jalanz.core.routingclient

import io.github.aldrinadira.jalanz.core.searchclient.HttpGet
import io.github.aldrinadira.jalanz.core.searchclient.Place
import io.github.aldrinadira.jalanz.core.searchclient.UrlConnectionHttpGet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.net.URLEncoder
import kotlin.math.roundToLong

/**
 * [RoutingClient] backed by Valhalla on the FOSSGIS public server (spec 05 DS-17): free, no key, fair use only.
 * Valhalla is also the engine the spec picked for our own server (D3), so requests carry over unchanged.
 * Start and end points are never logged (NFR-13).
 */
class ValhallaRoutingClient(
    private val http: HttpGet = UrlConnectionHttpGet(),
    private val baseUrl: String = "https://valhalla1.openstreetmap.de",
) : RoutingClient {

    override suspend fun routes(from: Place, to: Place, avoidTolls: Boolean): List<RouteOption> {
        val body = withContext(Dispatchers.IO) { http.get(url(from, to, avoidTolls)) }
        return parse(body)
    }

    internal fun url(from: Place, to: Place, avoidTolls: Boolean): String =
        "$baseUrl/route?json=" + URLEncoder.encode(request(from, to, avoidTolls).toString(), "UTF-8")

    internal companion object {
        const val ALTERNATES = 2

        fun request(from: Place, to: Place, avoidTolls: Boolean): JsonObject = buildJsonObject {
            put(
                "locations",
                buildJsonArray {
                    add(buildJsonObject { put("lat", from.lat); put("lon", from.lon) })
                    add(buildJsonObject { put("lat", to.lat); put("lon", to.lon) })
                },
            )
            put("costing", "auto")
            putJsonObject("costing_options") {
                putJsonObject("auto") {
                    // use_tolls = 0 only discourages tolls; exclude_tolls removes them (checked 30 Oct 2026).
                    if (avoidTolls) put("exclude_tolls", true) else put("use_tolls", 0.5)
                }
            }
            put("alternates", if (avoidTolls) 0 else ALTERNATES)
            put("units", "kilometers")
            // Turn-by-turn instructions come with navigation (Phase 2); the preview needs only shape and summary.
            put("directions_type", "none")
        }

        fun parse(body: String): List<RouteOption> {
            val root = Json.parseToJsonElement(body).jsonObject
            val trips = listOfNotNull(root["trip"]) + root["alternates"]?.jsonArray.orEmpty().mapNotNull { it.jsonObject["trip"] }
            return trips.map { tripElement ->
                val trip = tripElement.jsonObject
                val summary = trip.getValue("summary").jsonObject
                RouteOption(
                    points = trip.getValue("legs").jsonArray.flatMap { leg ->
                        decodePolyline(leg.jsonObject.getValue("shape").jsonPrimitive.content)
                    },
                    distanceMeters = (summary.getValue("length").jsonPrimitive.double * 1000).roundToLong(),
                    durationSeconds = summary.getValue("time").jsonPrimitive.double.roundToLong(),
                    hasToll = summary["has_toll"]?.jsonPrimitive?.boolean ?: false,
                    hasFerry = summary["has_ferry"]?.jsonPrimitive?.boolean ?: false,
                )
            }
        }
    }
}

/** Decodes an encoded polyline; Valhalla uses 6 decimal places ("polyline6"). */
fun decodePolyline(encoded: String, precision: Int = 6): List<LatLon> {
    val factor = Math.pow(10.0, precision.toDouble())
    val points = mutableListOf<LatLon>()
    var index = 0
    var lat = 0L
    var lon = 0L
    while (index < encoded.length) {
        val values = LongArray(2)
        for (i in 0..1) {
            var shift = 0
            var result = 0L
            var byte: Int
            do {
                byte = encoded[index++].code - 63
                result = result or ((byte and 0x1f).toLong() shl shift)
                shift += 5
            } while (byte >= 0x20)
            values[i] = if (result and 1L != 0L) (result shr 1).inv() else result shr 1
        }
        lat += values[0]
        lon += values[1]
        points += LatLon(lat / factor, lon / factor)
    }
    return points
}
