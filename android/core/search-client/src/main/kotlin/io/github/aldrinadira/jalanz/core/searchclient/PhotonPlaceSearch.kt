package io.github.aldrinadira.jalanz.core.searchclient

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

/** Fetches a URL's body as text. A seam so tests never touch the network. */
fun interface HttpGet {
    @Throws(IOException::class)
    fun get(url: String): String
}

/**
 * [PlaceSearch] backed by the public Photon geocoder by komoot (spec 05 DS-16): free, no key, fair use only.
 * Results are limited to Peninsular Malaysia (spec 00 coverage) and transit stops are filtered out, because
 * otherwise they crowd out the place people mean (e.g. three "KLCC" bus stops).
 * Search text is never logged (NFR-13).
 */
class PhotonPlaceSearch(
    private val http: HttpGet = UrlConnectionHttpGet(),
    private val baseUrl: String = "https://photon.komoot.io",
) : PlaceSearch {

    override suspend fun search(query: String, near: Place?): List<Place> {
        val text = query.trim()
        if (text.isEmpty()) return emptyList()
        val body = withContext(Dispatchers.IO) { http.get(url(text, near)) }
        return parse(body)
    }

    internal fun url(query: String, near: Place?): String = buildString {
        append(baseUrl).append("/api/?q=").append(URLEncoder.encode(query, "UTF-8"))
        append("&limit=").append(LIMIT)
        append("&bbox=").append(PENINSULAR_MALAYSIA_BBOX)
        near?.let { append("&lat=").append(it.lat).append("&lon=").append(it.lon) }
        EXCLUDED_TAGS.forEach { append("&osm_tag=!").append(it) }
    }

    internal companion object {
        const val LIMIT = 15
        /** minLon,minLat,maxLon,maxLat around Peninsular Malaysia, incl. Langkawi and the Johor coast. */
        const val PENINSULAR_MALAYSIA_BBOX = "99.6,1.2,104.6,6.8"
        val EXCLUDED_TAGS = listOf(
            "highway:bus_stop",
            "public_transport:stop_position",
            "public_transport:platform",
            "railway:stop",
        )

        /** Photon GeoJSON -> places; drops unnamed features and duplicates with the same name and detail. */
        fun parse(body: String): List<Place> {
            val features = Json.parseToJsonElement(body).jsonObject["features"]?.jsonArray.orEmpty()
            return features.mapNotNull { feature ->
                val obj = feature.jsonObject
                val props = obj["properties"]?.jsonObject ?: return@mapNotNull null
                fun prop(key: String) = props[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                val street = listOfNotNull(prop("housenumber"), prop("street")).joinToString(" ").ifBlank { null }
                val name = prop("name") ?: street ?: return@mapNotNull null
                val detail = listOfNotNull(street, prop("district"), prop("city"), prop("state"))
                    .distinct()
                    .filter { it != name }
                    .joinToString(", ")
                val coords = obj["geometry"]?.jsonObject?.get("coordinates")?.jsonArray ?: return@mapNotNull null
                Place(name, detail, lat = coords[1].jsonPrimitive.double, lon = coords[0].jsonPrimitive.double)
            }.distinctBy { it.name to it.detail }
        }
    }
}

/** Plain HttpURLConnection: MapLibre already ships OkHttp 4, so we avoid a second HTTP stack. */
class UrlConnectionHttpGet(private val timeoutMs: Int = 10_000) : HttpGet {
    override fun get(url: String): String {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            // Public services ask for an identifying User-Agent (fair use, spec 10 §1.1).
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("HTTP ${connection.responseCode}")
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val USER_AGENT = "Jalanz/0.0.1 (+https://github.com/aldrin-adira/jalanz)"
    }
}
