package io.github.aldrinadira.jalanz.feature.routepreview

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aldrinadira.jalanz.core.map.JalanzMap
import io.github.aldrinadira.jalanz.core.map.MapInsets
import io.github.aldrinadira.jalanz.core.routingclient.RouteOption
import io.github.aldrinadira.jalanz.core.searchclient.Place
import org.maplibre.android.geometry.LatLng
import io.github.aldrinadira.jalanz.core.designsystem.R as DesignR

/**
 * Route preview (FR-05): up to three routes with time, distance and whether they use tolls, plus avoid tolls.
 * Shares one [RoutePreviewViewModel] with the caller, who sets the destination with [RoutePreviewViewModel.planTo].
 */
@Composable
fun RoutePreviewRoute(
    onBack: () -> Unit,
    onPickOrigin: () -> Unit,
    viewModel: RoutePreviewViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RoutePreviewScreen(
        state = state,
        onBack = onBack,
        onPickOrigin = onPickOrigin,
        onSelect = viewModel::select,
        onAvoidTolls = viewModel::setAvoidTolls,
        onRetry = viewModel::retry,
    )
}

@Composable
fun RoutePreviewScreen(
    state: RoutePreviewState,
    onBack: () -> Unit,
    onPickOrigin: () -> Unit,
    onSelect: (Int) -> Unit,
    onAvoidTolls: (Boolean) -> Unit,
    onRetry: () -> Unit,
) {
    val routes = (state.result as? RoutesResult.Loaded)?.routes.orEmpty()
    val lines = remember(routes) { routes.map { route -> route.points.map { LatLng(it.lat, it.lon) } } }
    val density = LocalDensity.current

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val screenHeight = maxHeight
        // Routes are fitted between the From/To panel and the route list.
        val insets = with(density) {
            MapInsets(left = 40.dp.roundToPx(), top = 190.dp.roundToPx(), right = 40.dp.roundToPx(), bottom = (screenHeight * 0.45f).roundToPx())
        }
        JalanzMap(
            modifier = Modifier.fillMaxSize(),
            pin = state.to?.let { LatLng(it.lat, it.lon) },
            routes = lines,
            selectedRoute = state.selected,
            routeInsets = insets,
        )
        FromToPanel(state.from, state.to, onBack, onPickOrigin, Modifier.align(Alignment.TopCenter))
        Surface(
            shape = MaterialTheme.shapes.extraLarge.copy(bottomStart = MaterialTheme.shapes.extraSmall.bottomStart, bottomEnd = MaterialTheme.shapes.extraSmall.bottomEnd),
            tonalElevation = 3.dp,
            shadowElevation = 8.dp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = screenHeight * 0.45f),
        ) {
            Column(Modifier.navigationBarsPadding().padding(top = 12.dp, bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 20.dp)) {
                    Text(stringResource(R.string.route_avoid_tolls), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Switch(checked = state.avoidTolls, onCheckedChange = onAvoidTolls)
                }
                Text(
                    stringResource(R.string.route_no_live_traffic),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
                when (val result = state.result) {
                    RoutesResult.NeedsOrigin -> Prompt(stringResource(R.string.route_needs_origin), stringResource(R.string.route_choose_start), onPickOrigin)
                    RoutesResult.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(20.dp))
                    RoutesResult.Failed -> Prompt(stringResource(R.string.route_failed), stringResource(R.string.route_retry), onRetry)
                    is RoutesResult.Loaded -> LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        itemsIndexed(result.routes) { index, route ->
                            RouteCard(route, fastest = index == 0 && result.routes.size > 1, selected = index == state.selected, onClick = { onSelect(index) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FromToPanel(from: Place?, to: Place?, onBack: () -> Unit, onPickOrigin: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.large,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
        modifier = modifier
            .statusBarsPadding()
            .padding(12.dp)
            .fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
            IconButton(onClick = onBack) {
                Icon(painterResource(DesignR.drawable.ic_arrow_back), stringResource(R.string.route_back))
            }
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                EndpointRow(
                    label = stringResource(R.string.route_from),
                    value = from?.name ?: stringResource(R.string.route_choose_start),
                    emphasised = from == null,
                    modifier = Modifier.clickable(onClick = onPickOrigin),
                )
                EndpointRow(label = stringResource(R.string.route_to), value = to?.name.orEmpty(), emphasised = false)
            }
        }
    }
}

@Composable
private fun EndpointRow(label: String, value: String, emphasised: Boolean, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(56.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            color = if (emphasised) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RouteCard(route: RouteOption, fastest: Boolean, selected: Boolean, onClick: () -> Unit) {
    val (hours, minutes) = hoursAndMinutes(route.durationSeconds)
    val duration = if (hours > 0) stringResource(R.string.route_duration_hm, hours, minutes) else stringResource(R.string.route_duration_m, minutes)
    val locale = LocalConfiguration.current.locales[0]
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        tonalElevation = if (selected) 6.dp else 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(duration, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    formatDistance(route.distanceMeters, locale) + " · " +
                        stringResource(if (route.hasToll) R.string.route_tolls_unknown else R.string.route_no_tolls),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (fastest) {
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.route_fastest), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun Prompt(message: String, action: String, onAction: () -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text(message, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.width(8.dp))
        Button(onClick = onAction, modifier = Modifier.padding(top = 12.dp)) { Text(action) }
    }
}
