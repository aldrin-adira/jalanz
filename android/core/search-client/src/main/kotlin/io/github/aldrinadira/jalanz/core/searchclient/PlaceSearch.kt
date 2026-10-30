package io.github.aldrinadira.jalanz.core.searchclient

/** A place the user can pick: a search result, a recent place, Home or Work. */
data class Place(
    val name: String,
    /** One-line address context, e.g. "Jalan Ampang, Kuala Lumpur". May be empty. */
    val detail: String,
    val lat: Double,
    val lon: Double,
)

/**
 * Finds places by text (FR-01). Implemented by Photon on the personal-use track (spec 10 §1.1); our own
 * `GET /v1/search` (WP 1.11) can replace it without touching callers.
 */
interface PlaceSearch {
    /** Results for [query], nearest to [near] first where the backend supports it. Blank queries return nothing. */
    suspend fun search(query: String, near: Place? = null): List<Place>
}
