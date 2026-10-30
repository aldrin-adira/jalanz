package io.github.aldrinadira.jalanz.feature.search

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aldrinadira.jalanz.core.designsystem.JalanzTheme
import io.github.aldrinadira.jalanz.core.searchclient.Place
import io.github.aldrinadira.jalanz.core.designsystem.R as DesignR

/**
 * Search screen wired to its ViewModel. [onPlaceSelected] gets the chosen place after it is saved to recents.
 * [placeholder] replaces "Search places", e.g. when picking a starting point.
 */
@Composable
fun SearchRoute(
    onPlaceSelected: (Place) -> Unit,
    onBack: () -> Unit,
    placeholder: String? = null,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SearchScreen(
        query = viewModel.query,
        placeholder = placeholder,
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onSelect = { place ->
            viewModel.onPlaceSelected(place)
            onPlaceSelected(place)
        },
        onSetHome = viewModel::setHome,
        onSetWork = viewModel::setWork,
        onClearHome = viewModel::clearHome,
        onClearWork = viewModel::clearWork,
        onRemoveSaved = viewModel::removeSaved,
        onBack = onBack,
    )
}

@Composable
fun SearchScreen(
    query: String,
    placeholder: String? = null,
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onSelect: (Place) -> Unit,
    onSetHome: (Place) -> Unit,
    onSetWork: (Place) -> Unit,
    onClearHome: () -> Unit,
    onClearWork: () -> Unit,
    onRemoveSaved: (Place) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = { SearchBar(query, placeholder, onQueryChange, onBack) },
    ) { innerPadding ->
        val contentPadding = PaddingValues(
            top = innerPadding.calculateTopPadding(),
            bottom = innerPadding.calculateBottomPadding() + 8.dp,
        )
        val placeActions = PlaceActions(onSetHome, onSetWork)
        when (val results = state.results) {
            SearchResults.Idle -> LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
                item {
                    FavouriteRow(DesignR.drawable.ic_home, stringResource(R.string.search_home), state.home, onSelect, onClearHome)
                }
                item {
                    FavouriteRow(DesignR.drawable.ic_work, stringResource(R.string.search_work), state.work, onSelect, onClearWork)
                }
                if (state.saved.isNotEmpty()) {
                    item { HorizontalDivider() }
                    item { SectionHeader(stringResource(R.string.search_saved)) }
                    items(state.saved) { place -> SavedRow(place, onSelect, onRemove = { onRemoveSaved(place) }) }
                }
                if (state.recents.isNotEmpty()) {
                    item { HorizontalDivider() }
                    item { SectionHeader(stringResource(R.string.search_recent)) }
                    items(state.recents) { place -> PlaceRow(DesignR.drawable.ic_history, place, onSelect, placeActions) }
                }
            }
            SearchResults.Loading -> Box(Modifier.padding(contentPadding)) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            SearchResults.Failed -> Message(stringResource(R.string.search_failed), contentPadding)
            is SearchResults.Loaded ->
                if (results.places.isEmpty()) {
                    Message(stringResource(R.string.search_no_results), contentPadding)
                } else {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
                        items(results.places) { place -> PlaceRow(DesignR.drawable.ic_place, place, onSelect, placeActions) }
                    }
                }
        }
    }
}

@Composable
private fun SearchBar(query: String, placeholder: String?, onQueryChange: (String) -> Unit, onBack: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 8.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(painterResource(DesignR.drawable.ic_arrow_back), stringResource(R.string.search_back))
        }
        TextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text(placeholder ?: stringResource(R.string.search_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(painterResource(DesignR.drawable.ic_close), stringResource(R.string.search_clear))
                    }
                }
            },
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
                .focusRequester(focusRequester),
        )
    }
}

private class PlaceActions(val setHome: (Place) -> Unit, val setWork: (Place) -> Unit)

@Composable
private fun PlaceRow(@DrawableRes icon: Int, place: Place, onSelect: (Place) -> Unit, actions: PlaceActions) {
    var menuOpen by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(place.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = place.detail.takeIf { it.isNotEmpty() }?.let {
            { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        },
        leadingContent = { Icon(painterResource(icon), contentDescription = null) },
        trailingContent = {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(painterResource(DesignR.drawable.ic_more_vert), stringResource(R.string.search_more))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.search_set_home)) },
                        onClick = { menuOpen = false; actions.setHome(place) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.search_set_work)) },
                        onClick = { menuOpen = false; actions.setWork(place) },
                    )
                }
            }
        },
        modifier = Modifier.clickable { onSelect(place) },
    )
}

@Composable
private fun FavouriteRow(@DrawableRes icon: Int, label: String, place: Place?, onSelect: (Place) -> Unit, onClear: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = {
            Text(place?.name ?: stringResource(R.string.search_not_set), maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        leadingContent = { Icon(painterResource(icon), contentDescription = null) },
        trailingContent = place?.let {
            {
                IconButton(onClick = onClear) {
                    Icon(painterResource(DesignR.drawable.ic_close), stringResource(R.string.search_clear_saved, label))
                }
            }
        },
        modifier = if (place != null) Modifier.clickable { onSelect(place) } else Modifier,
    )
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SavedRow(place: Place, onSelect: (Place) -> Unit, onRemove: () -> Unit) {
    ListItem(
        headlineContent = { Text(place.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = place.detail.takeIf { it.isNotEmpty() }?.let {
            { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        },
        leadingContent = { Icon(painterResource(DesignR.drawable.ic_place), contentDescription = null) },
        trailingContent = {
            IconButton(onClick = onRemove) {
                Icon(painterResource(DesignR.drawable.ic_close), stringResource(R.string.search_clear_saved, place.name))
            }
        },
        modifier = Modifier.clickable { onSelect(place) },
    )
}

@Composable
private fun Message(text: String, padding: PaddingValues) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .padding(padding)
            .padding(24.dp),
    )
}

@Preview(showBackground = true)
@Preview(showBackground = true, locale = "ms")
@Composable
private fun SearchScreenPreview() {
    val midValley = Place("Mid Valley Megamall", "Lingkaran Syed Putra, Kuala Lumpur", 3.1177, 101.6774)
    JalanzTheme {
        SearchScreen(
            query = "",
            state = SearchUiState(home = midValley, recents = listOf(midValley)),
            onQueryChange = {}, onSelect = {}, onSetHome = {}, onSetWork = {},
            onClearHome = {}, onClearWork = {}, onRemoveSaved = {}, onBack = {},
        )
    }
}
