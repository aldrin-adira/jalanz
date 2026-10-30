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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.aldrinadira.jalanz.core.map.JalanzMap
import io.github.aldrinadira.jalanz.core.searchclient.Place
import io.github.aldrinadira.jalanz.feature.search.SearchRoute
import io.github.aldrinadira.jalanz.feature.settings.AttributionScreen
import org.maplibre.android.geometry.LatLng
import io.github.aldrinadira.jalanz.core.designsystem.R as DesignR

private enum class Screen { MAP, SEARCH, ATTRIBUTION }

private val PlaceSaver = Saver<Place?, List<Any>>(
    save = { place -> place?.let { listOf(it.name, it.detail, it.lat, it.lon) } ?: emptyList() },
    restore = { saved -> if (saved.isEmpty()) null else Place(saved[0] as String, saved[1] as String, saved[2] as Double, saved[3] as Double) },
)

/** Top-level phone UI. Three screens for now, so plain state instead of a navigation library. */
@Composable
fun JalanzApp() {
    var screen by rememberSaveable { mutableStateOf(Screen.MAP) }
    var selected by rememberSaveable(stateSaver = PlaceSaver) { mutableStateOf<Place?>(null) }

    if (screen != Screen.MAP) BackHandler { screen = Screen.MAP }
    when (screen) {
        Screen.MAP -> MapHome(
            selected = selected,
            onSearch = { screen = Screen.SEARCH },
            onClearSelection = { selected = null },
            onShowAttribution = { screen = Screen.ATTRIBUTION },
        )
        Screen.SEARCH -> SearchRoute(
            onPlaceSelected = { place ->
                selected = place
                screen = Screen.MAP
            },
            onBack = { screen = Screen.MAP },
        )
        Screen.ATTRIBUTION -> AttributionScreen(onBack = { screen = Screen.MAP })
    }
}

@Composable
private fun MapHome(
    selected: Place?,
    onSearch: () -> Unit,
    onClearSelection: () -> Unit,
    onShowAttribution: () -> Unit,
) {
    if (selected != null) BackHandler(onBack = onClearSelection)
    Box(Modifier.fillMaxSize()) {
        JalanzMap(Modifier.fillMaxSize(), pin = selected?.let { LatLng(it.lat, it.lon) })
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
        if (selected != null) {
            SelectedPlaceCard(
                place = selected,
                onClose = onClearSelection,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(12.dp),
            )
        }
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
private fun SelectedPlaceCard(place: Place, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.large,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp)) {
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
    }
}
