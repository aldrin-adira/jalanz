package io.github.aldrinadira.jalanz

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import io.github.aldrinadira.jalanz.core.map.JalanzMap
import io.github.aldrinadira.jalanz.core.searchclient.Place
import io.github.aldrinadira.jalanz.core.searchclient.SharedLocation
import io.github.aldrinadira.jalanz.core.searchclient.formatCoordinates
import io.github.aldrinadira.jalanz.core.searchclient.parseSharedLocation
import io.github.aldrinadira.jalanz.feature.routepreview.RoutePreviewRoute
import io.github.aldrinadira.jalanz.feature.routepreview.RoutePreviewViewModel
import io.github.aldrinadira.jalanz.feature.search.SearchRoute
import io.github.aldrinadira.jalanz.feature.settings.AttributionScreen
import kotlinx.coroutines.launch
import org.maplibre.android.geometry.LatLng
import io.github.aldrinadira.jalanz.core.designsystem.R as DesignR

private enum class Screen { MAP, SEARCH, ROUTES, PICK_ORIGIN, ATTRIBUTION }

private enum class ShareProblem { NEEDS_COORDINATES, NOT_FOUND }

private val PlaceSaver = Saver<Place?, List<Any>>(
    save = { place -> place?.let { listOf(it.name, it.detail, it.lat, it.lon) } ?: emptyList() },
    restore = { saved -> if (saved.isEmpty()) null else Place(saved[0] as String, saved[1] as String, saved[2] as Double, saved[3] as Double) },
)

/**
 * Top-level phone UI. A handful of screens, so plain state instead of a navigation library.
 * [sharedInput] is text or a link another app shared with Jalanz (FR-17); it is shown as a pin once handled.
 */
@Composable
fun JalanzApp(sharedInput: String? = null, onSharedInputHandled: () -> Unit = {}) {
    var screen by rememberSaveable { mutableStateOf(Screen.MAP) }
    var selected by rememberSaveable(stateSaver = PlaceSaver) { mutableStateOf<Place?>(null) }
    var shareProblem by rememberSaveable { mutableStateOf<ShareProblem?>(null) }
    // Activity-scoped, so the route screen and the start-point search share it.
    val routes: RoutePreviewViewModel = hiltViewModel()

    val sharedName = stringResource(R.string.shared_location)
    LaunchedEffect(sharedInput) {
        if (sharedInput == null) return@LaunchedEffect
        when (val location = parseSharedLocation(sharedInput)) {
            is SharedLocation.Found -> {
                selected = Place(location.label ?: sharedName, formatCoordinates(location.lat, location.lon), location.lat, location.lon)
                screen = Screen.MAP
            }
            SharedLocation.NeedsCoordinates -> shareProblem = ShareProblem.NEEDS_COORDINATES
            SharedLocation.NotFound -> shareProblem = ShareProblem.NOT_FOUND
        }
        onSharedInputHandled()
    }

    if (screen != Screen.MAP) BackHandler { screen = if (screen == Screen.PICK_ORIGIN) Screen.ROUTES else Screen.MAP }
    when (screen) {
        Screen.MAP -> MapHome(
            selected = selected,
            onSelect = { selected = it },
            onSearch = { screen = Screen.SEARCH },
            onDirections = { place ->
                routes.planTo(place)
                screen = Screen.ROUTES
            },
            onShowAttribution = { screen = Screen.ATTRIBUTION },
        )
        Screen.SEARCH -> SearchRoute(
            onPlaceSelected = { place ->
                selected = place
                screen = Screen.MAP
            },
            onBack = { screen = Screen.MAP },
        )
        Screen.ROUTES -> RoutePreviewRoute(
            onBack = { screen = Screen.MAP },
            onPickOrigin = { screen = Screen.PICK_ORIGIN },
            viewModel = routes,
        )
        Screen.PICK_ORIGIN -> SearchRoute(
            onPlaceSelected = { place ->
                routes.setOrigin(place)
                screen = Screen.ROUTES
            },
            onBack = { screen = Screen.ROUTES },
            placeholder = stringResource(R.string.choose_start),
        )
        Screen.ATTRIBUTION -> AttributionScreen(onBack = { screen = Screen.MAP })
    }

    shareProblem?.let { problem ->
        AlertDialog(
            onDismissRequest = { shareProblem = null },
            confirmButton = { TextButton(onClick = { shareProblem = null }) { Text(stringResource(R.string.action_ok)) } },
            title = {
                Text(
                    stringResource(
                        if (problem == ShareProblem.NEEDS_COORDINATES) R.string.share_needs_coordinates_title else R.string.share_not_found_title,
                    ),
                )
            },
            text = {
                Text(
                    stringResource(
                        if (problem == ShareProblem.NEEDS_COORDINATES) R.string.share_needs_coordinates else R.string.share_not_found,
                    ),
                )
            },
        )
    }
}

@Composable
private fun MapHome(
    selected: Place?,
    onSelect: (Place?) -> Unit,
    onSearch: () -> Unit,
    onDirections: (Place) -> Unit,
    onShowAttribution: () -> Unit,
    viewModel: MapHomeViewModel = hiltViewModel(),
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf<Place?>(null) }
    val droppedPinName = stringResource(R.string.dropped_pin)
    val savedMessage = stringResource(R.string.place_saved)

    if (selected != null) BackHandler { onSelect(null) }
    Box(Modifier.fillMaxSize()) {
        JalanzMap(
            modifier = Modifier.fillMaxSize(),
            pin = selected?.let { LatLng(it.lat, it.lon) },
            onLongPress = { point ->
                onSelect(Place(droppedPinName, formatCoordinates(point.latitude, point.longitude), point.latitude, point.longitude))
            },
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .safeDrawingPadding()
                .padding(12.dp),
        ) {
            SearchBox(onSearch, Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            SmallFloatingActionButton(onClick = onShowAttribution) {
                Icon(painterResource(DesignR.drawable.ic_info), stringResource(R.string.action_data_sources))
            }
        }
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(12.dp),
        ) {
            SnackbarHost(snackbar)
            if (selected != null) {
                SelectedPlaceCard(
                    place = selected,
                    onDirections = { onDirections(selected) },
                    onSave = { saving = selected },
                    onClose = { onSelect(null) },
                )
            }
        }
    }

    saving?.let { place ->
        SavePlaceDialog(
            initialName = place.name,
            onDismiss = { saving = null },
            onSave = { name ->
                val named = place.copy(name = name)
                viewModel.save(named)
                onSelect(named)
                saving = null
                scope.launch { snackbar.showSnackbar(savedMessage) }
            },
        )
    }
}

/** Looks like a text field but only opens the search screen, so the keyboard never covers the map here. */
@Composable
private fun SearchBox(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 3.dp,
        shadowElevation = 3.dp,
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Icon(painterResource(DesignR.drawable.ic_search), contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.search_places), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SelectedPlaceCard(place: Place, onDirections: () -> Unit, onSave: () -> Unit, onClose: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(place.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (place.detail.isNotEmpty()) {
                        Text(place.detail, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                IconButton(onClick = onClose) {
                    Icon(painterResource(DesignR.drawable.ic_close), stringResource(R.string.action_close))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Button(onClick = onDirections) { Text(stringResource(R.string.action_directions)) }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onSave) { Text(stringResource(R.string.action_save)) }
            }
        }
    }
}

@Composable
private fun SavePlaceDialog(initialName: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    // Whole name selected, so typing replaces "Dropped pin" instead of adding to it.
    var name by remember { mutableStateOf(TextFieldValue(initialName, TextRange(0, initialName.length))) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.save_place_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.save_place_name)) },
                singleLine = true,
                modifier = Modifier.focusRequester(focusRequester),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.text.trim()) }, enabled = name.text.isNotBlank()) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
