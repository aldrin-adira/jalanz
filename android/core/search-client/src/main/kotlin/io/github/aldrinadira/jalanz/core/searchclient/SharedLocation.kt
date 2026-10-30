package io.github.aldrinadira.jalanz.core.searchclient

import java.net.URLDecoder
import java.util.Locale

/** What Jalanz could read from text or a link another app shared (FR-17, spec 05 §2). */
sealed interface SharedLocation {
    /** Coordinates found. [label] is a place name when the share carried one. */
    data class Found(val lat: Double, val lon: Double, val label: String?) : SharedLocation

    /**
     * A short link (e.g. maps.app.goo.gl) that hides its coordinates. Jalanz never calls Google to expand it
     * (spec 05 §2, decision D2), so the user is told how to copy the coordinates instead.
     */
    data object NeedsCoordinates : SharedLocation

    data object NotFound : SharedLocation
}

/** "3.13900, 101.68690" — the (lat, lon) order used in UI strings (spec 00 conventions). */
fun formatCoordinates(lat: Double, lon: Double): String = String.format(Locale.ROOT, "%.5f, %.5f", lat, lon)

private const val NUMBER = """-?\d{1,3}(?:\.\d+)?"""
private const val PAIR = """($NUMBER)\s*,\s*($NUMBER)"""

/** The whole text is a coordinate pair, as Google Maps shows after a long-press: "3.13900, 101.68690". */
private val PLAIN_PAIR = Regex("""^\(?\s*$PAIR\s*\)?$""")
private val GEO_URI = Regex("""^geo:$PAIR""", RegexOption.IGNORE_CASE)
private val GEO_QUERY_PAIR = Regex("""[?&]q=$PAIR(?:\s*\((.*)\))?""")

/** Link forms that carry coordinates literally: Google Maps @lat,lon and q/query/ll/destination params, Waze ll. */
private val LINK_PATTERNS = listOf(
    Regex("""@$PAIR"""),
    Regex("""[?&](?:q|query|ll|destination|daddr|center)=$PAIR"""),
)

private val SHORT_LINK_HOSTS = listOf("maps.app.goo.gl", "goo.gl/maps", "g.co/kgs", "waze.com/ul/h")

/**
 * Reads a location from shared text: a `geo:` URI, a link with coordinates in it, or a plain "lat, lon" pair.
 * Only coordinates that appear literally are used; nothing is fetched. Shared text is never logged (NFR-13).
 */
fun parseSharedLocation(shared: String): SharedLocation {
    val text = shared.trim()
    if (text.isEmpty()) return SharedLocation.NotFound

    parseCoordinates(text)?.let { (lat, lon) -> return SharedLocation.Found(lat, lon, label = null) }

    val decoded = runCatching { URLDecoder.decode(text, "UTF-8") }.getOrDefault(text)
    GEO_URI.find(decoded)?.let { geo ->
        val query = GEO_QUERY_PAIR.find(decoded)
        // geo:0,0?q=lat,lon(Label) puts the real position in the query.
        val (lat, lon) = query?.let { it.coordinates() } ?: geo.coordinates() ?: return@let
        if (lat == 0.0 && lon == 0.0) return@let
        return SharedLocation.Found(lat, lon, query?.groupValues?.getOrNull(3)?.trim()?.ifEmpty { null })
    }
    for (pattern in LINK_PATTERNS) {
        val match = pattern.find(decoded) ?: continue
        val (lat, lon) = match.coordinates() ?: continue
        return SharedLocation.Found(lat, lon, label = nameBeforeLink(text))
    }
    if (SHORT_LINK_HOSTS.any { it in text }) return SharedLocation.NeedsCoordinates
    return SharedLocation.NotFound
}

/** A coordinate pair typed or pasted as the whole text, e.g. into the search box. */
fun parseCoordinates(text: String): Pair<Double, Double>? = PLAIN_PAIR.find(text.trim())?.coordinates()

private fun MatchResult.coordinates(): Pair<Double, Double>? {
    val lat = groupValues[1].toDoubleOrNull() ?: return null
    val lon = groupValues[2].toDoubleOrNull() ?: return null
    if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
    return lat to lon
}

/** Apps often share "Place name\nhttps://…": the first line that is not a link is the name. */
private fun nameBeforeLink(text: String): String? =
    text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() && "://" !in it }
