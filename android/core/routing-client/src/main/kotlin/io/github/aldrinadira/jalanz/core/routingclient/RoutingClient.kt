package io.github.aldrinadira.jalanz.core.routingclient

import io.github.aldrinadira.jalanz.core.searchclient.Place

data class LatLon(val lat: Double, val lon: Double)

/** One way to drive from A to B (FR-05). Times use typical speeds: there is no live traffic yet (FR-04, Phase 5). */
data class RouteOption(
    val points: List<LatLon>,
    val distanceMeters: Long,
    val durationSeconds: Long,
    /** The routing graph says the route uses at least one toll road or plaza. */
    val hasToll: Boolean,
    val hasFerry: Boolean,
)

/**
 * Plans routes. Implemented by the public FOSSGIS Valhalla server on the personal-use track (spec 10 §1.1);
 * our own `POST /v1/routes` (WP 1.12) can replace it without touching callers.
 */
interface RoutingClient {
    /** Up to three options, fastest first. With [avoidTolls], only toll-free routes (usually one). */
    suspend fun routes(from: Place, to: Place, avoidTolls: Boolean): List<RouteOption>
}
