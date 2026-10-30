package io.github.aldrinadira.jalanz.feature.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.aldrinadira.jalanz.core.data.SavedPlaces
import io.github.aldrinadira.jalanz.core.searchclient.Place
import io.github.aldrinadira.jalanz.core.searchclient.PlaceSearch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SearchResults {
    data object Idle : SearchResults
    data object Loading : SearchResults
    data class Loaded(val places: List<Place>) : SearchResults
    data object Failed : SearchResults
}

data class SearchUiState(
    val results: SearchResults = SearchResults.Idle,
    val home: Place? = null,
    val work: Place? = null,
    val recents: List<Place> = emptyList(),
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    placeSearch: PlaceSearch,
    private val savedPlaces: SavedPlaces,
) : ViewModel() {
    /**
     * The text in the search box. Compose state, not a Flow, so the text field always reads back exactly what was
     * typed; an asynchronous round trip makes the cursor jump while typing fast.
     */
    var query by mutableStateOf("")
        private set

    val state: StateFlow<SearchUiState> = combine(
        searchResults(snapshotFlow { query }, placeSearch),
        savedPlaces.home,
        savedPlaces.work,
        savedPlaces.recents,
    ) { results, home, work, recents ->
        SearchUiState(results, home, work, recents)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    fun onQueryChange(text: String) {
        query = text
    }

    /** Remembers [place] in recents; the caller then shows it on the map. */
    fun onPlaceSelected(place: Place) {
        viewModelScope.launch { savedPlaces.addRecent(place) }
    }

    fun setHome(place: Place) = launch { savedPlaces.setHome(place) }
    fun setWork(place: Place) = launch { savedPlaces.setWork(place) }
    fun clearHome() = launch { savedPlaces.clearHome() }
    fun clearWork() = launch { savedPlaces.clearWork() }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}

/** Results bias towards KL until the drive service can supply the phone's position (WP 2.1). */
internal val SEARCH_BIAS = Place("Kuala Lumpur", "", 3.1390, 101.6869)

internal const val SEARCH_DEBOUNCE_MS = 300L
internal const val MIN_QUERY_LENGTH = 2

/** Search-as-you-type: waits for a pause in typing, and drops results of queries the user has moved past. */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
internal fun searchResults(queries: Flow<String>, placeSearch: PlaceSearch): Flow<SearchResults> =
    queries
        .map { it.trim() }
        .distinctUntilChanged()
        .debounce { if (it.length < MIN_QUERY_LENGTH) 0L else SEARCH_DEBOUNCE_MS }
        .flatMapLatest { text ->
            if (text.length < MIN_QUERY_LENGTH) {
                flowOf<SearchResults>(SearchResults.Idle)
            } else {
                flow<SearchResults> {
                    emit(SearchResults.Loading)
                    emit(
                        try {
                            SearchResults.Loaded(placeSearch.search(text, SEARCH_BIAS))
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            // Network or parse failure. Never log the query (NFR-13).
                            SearchResults.Failed
                        },
                    )
                }
            }
        }
