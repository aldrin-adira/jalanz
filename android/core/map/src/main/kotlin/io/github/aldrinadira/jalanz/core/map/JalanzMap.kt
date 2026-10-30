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
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.FeatureCollection
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

/**
 * Full-screen MapLibre map that follows the system light/dark setting. Shared later by phone and car (06 §2).
 * When [pin] is set the map shows a marker there and moves the camera to it.
 */
@Composable
fun JalanzMap(
    modifier: Modifier = Modifier,
    pin: LatLng? = null,
    darkTheme: Boolean = isSystemInDarkTheme(),
) {
    val context = LocalContext.current
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply {
            onCreate(null)
            getMapAsync { it.cameraPosition = DEFAULT_CAMERA }
        }
    }
    MapViewLifecycle(mapView)

    val currentPin by rememberUpdatedState(pin)
    val styleUrl = if (darkTheme) MapStyles.DARK else MapStyles.LIGHT
    LaunchedEffect(mapView, styleUrl) {
        // A new style drops our layers, so the pin is drawn again once it has loaded.
        mapView.getMapAsync { map -> map.setStyle(styleUrl) { style -> style.showPin(currentPin) } }
    }
    LaunchedEffect(mapView, pin) {
        mapView.getMapAsync { map ->
            map.style?.takeIf { it.isFullyLoaded }?.showPin(pin)
            if (pin != null) map.animateCamera(CameraUpdateFactory.newLatLngZoom(pin, PIN_ZOOM))
        }
    }
    AndroidView(factory = { mapView }, modifier = modifier)
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
    source.setGeoJson(FeatureCollection.fromFeatures(points.map { org.maplibre.geojson.Feature.fromGeometry(it) }))
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
