package io.github.aldrinadira.jalanz.core.map

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/**
 * Base map styles. Personal-use track: OpenFreeMap's public styles and tiles, no key (spec 10 §1.1, 05 DS-15).
 * Replace with our own PMTiles style (WP 0.7) before anyone else uses the app.
 */
object MapStyles {
    const val LIGHT = "https://tiles.openfreemap.org/styles/liberty"
    const val DARK = "https://tiles.openfreemap.org/styles/dark"
}

/** Where the map opens until the drive service can supply the phone's location (WP 2.1). */
val DEFAULT_CAMERA: CameraPosition = CameraPosition.Builder()
    .target(LatLng(3.1390, 101.6869)) // Kuala Lumpur
    .zoom(10.0)
    .build()

private const val PIN_SOURCE = "jalanz-pin"
private const val PIN_LAYER = "jalanz-pin"
private const val PIN_ZOOM = 15.0

private const val ROUTE_SOURCE = "jalanz-routes"
private const val ROUTE_ALTERNATE_LAYER = "jalanz-routes-alternate"
private const val ROUTE_SELECTED_LAYER = "jalanz-routes-selected"
private const val ROUTE_START_LAYER = "jalanz-routes-start"
private const val SELECTED = "selected"

/** Space kept clear around routes when the camera fits them, so panels on top don't hide them. */
data class MapInsets(val left: Int = 0, val top: Int = 0, val right: Int = 0, val bottom: Int = 0)

/**
 * Full-screen MapLibre map that follows the system light/dark setting. Shared later by phone and car (06 §2).
 * When [pin] is set the map shows a marker there and moves the camera to it. [onLongPress] gets the pressed point.
 * [routes] are drawn with [selectedRoute] highlighted, and the camera fits them inside [routeInsets] (pixels).
 */
@Composable
fun JalanzMap(
    modifier: Modifier = Modifier,
    pin: LatLng? = null,
    onLongPress: (LatLng) -> Unit = {},
    routes: List<List<LatLng>> = emptyList(),
    selectedRoute: Int = 0,
    routeInsets: MapInsets = MapInsets(),
    darkTheme: Boolean = isSystemInDarkTheme(),
) {
    val context = LocalContext.current
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply {
            onCreate(null)
            getMapAsync { map ->
                map.cameraPosition = DEFAULT_CAMERA
                map.addOnMapLongClickListener { point ->
                    currentOnLongPress(point)
                    true
                }
            }
        }
    }
    MapViewLifecycle(mapView)

    val currentPin by rememberUpdatedState(pin)
    val currentRoutes by rememberUpdatedState(routes)
    val currentSelected by rememberUpdatedState(selectedRoute)
    val styleUrl = if (darkTheme) MapStyles.DARK else MapStyles.LIGHT
    LaunchedEffect(mapView, styleUrl) {
        // A new style drops our layers, so routes and pin are drawn again once it has loaded.
        mapView.getMapAsync { map ->
            map.setStyle(styleUrl) { style ->
                style.showRoutes(currentRoutes, currentSelected)
                style.showPin(currentPin)
            }
        }
    }
    LaunchedEffect(mapView, pin) {
        mapView.getMapAsync { map ->
            map.style?.takeIf { it.isFullyLoaded }?.showPin(pin)
            if (pin != null && currentRoutes.isEmpty()) map.animateCamera(CameraUpdateFactory.newLatLngZoom(pin, PIN_ZOOM))
        }
    }
    LaunchedEffect(mapView, routes, selectedRoute) {
        mapView.getMapAsync { map -> map.style?.takeIf { it.isFullyLoaded }?.showRoutes(routes, selectedRoute) }
    }
    LaunchedEffect(mapView, routes, routeInsets) {
        val all = routes.flatten()
        if (all.size < 2) return@LaunchedEffect
        val bounds = LatLngBounds.Builder().includes(all).build()
        mapView.getMapAsync { map ->
            map.animateCamera(
                CameraUpdateFactory.newLatLngBounds(bounds, routeInsets.left, routeInsets.top, routeInsets.right, routeInsets.bottom),
            )
        }
    }
    AndroidView(factory = { mapView }, modifier = modifier)
}

/** Alternates in grey under the selected route in Jalanz blue, plus a small dot where the route starts. */
private fun Style.showRoutes(routes: List<List<LatLng>>, selected: Int) {
    val source = getSourceAs<GeoJsonSource>(ROUTE_SOURCE) ?: GeoJsonSource(ROUTE_SOURCE).also { source ->
        addSource(source)
        val isSelected = Expression.eq(Expression.get(SELECTED), Expression.literal(true))
        val layers = listOf(
            LineLayer(ROUTE_ALTERNATE_LAYER, ROUTE_SOURCE).withProperties(
                lineColor("#8A9AA5"), lineWidth(6f), lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
            ).withFilter(Expression.all(Expression.eq(Expression.geometryType(), "LineString"), Expression.not(isSelected))),
            LineLayer(ROUTE_SELECTED_LAYER, ROUTE_SOURCE).withProperties(
                lineColor("#00658F"), lineWidth(8f), lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
            ).withFilter(Expression.all(Expression.eq(Expression.geometryType(), "LineString"), isSelected)),
            CircleLayer(ROUTE_START_LAYER, ROUTE_SOURCE).withProperties(
                circleRadius(6f), circleColor("#FFFFFF"), circleStrokeWidth(3f), circleStrokeColor("#00658F"),
            ).withFilter(Expression.eq(Expression.geometryType(), "Point")),
        )
        // Keep the destination pin on top when it already exists.
        layers.forEach { layer -> if (getLayer(PIN_LAYER) != null) addLayerBelow(layer, PIN_LAYER) else addLayer(layer) }
    }
    // Selected route last so it is drawn above the alternates where they overlap.
    val order = routes.indices.sortedBy { it == selected }
    val lines = order.map { i ->
        Feature.fromGeometry(LineString.fromLngLats(routes[i].map { Point.fromLngLat(it.longitude, it.latitude) }))
            .apply { addBooleanProperty(SELECTED, i == selected) }
    }
    val start = routes.getOrNull(selected)?.firstOrNull()?.let { Feature.fromGeometry(Point.fromLngLat(it.longitude, it.latitude)) }
    source.setGeoJson(FeatureCollection.fromFeatures(lines + listOfNotNull(start)))
}

private fun Style.showPin(pin: LatLng?) {
    val source = getSourceAs<GeoJsonSource>(PIN_SOURCE) ?: GeoJsonSource(PIN_SOURCE).also {
        addSource(it)
        addLayer(
            CircleLayer(PIN_LAYER, PIN_SOURCE).withProperties(
                circleRadius(9f),
                circleColor("#00658F"),
                circleStrokeWidth(3f),
                circleStrokeColor("#FFFFFF"),
            ),
        )
    }
    val points = listOfNotNull(pin?.let { Point.fromLngLat(it.longitude, it.latitude) })
    source.setGeoJson(FeatureCollection.fromFeatures(points.map { Feature.fromGeometry(it) }))
}

/** Forwards the screen's lifecycle to [mapView], and tears it down when it leaves the composition. */
@Composable
private fun MapViewLifecycle(mapView: MapView) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            val state = lifecycle.currentState
            if (state.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
            if (state.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
            mapView.onDestroy()
        }
    }
}
