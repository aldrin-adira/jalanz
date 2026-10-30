package io.github.aldrinadira.jalanz.feature.routepreview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.aldrinadira.jalanz.core.data.SavedPlaces
import io.github.aldrinadira.jalanz.core.routingclient.RouteOption
import io.github.aldrinadira.jalanz.core.routingclient.RoutingClient
import io.github.aldrinadira.jalanz.core.searchclient.Place
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface RoutesResult {
    /** No starting point yet: the phone's position is not available until the drive service exists (WP 2.1). */
    data object NeedsOrigin : RoutesResult
    data object Loading : RoutesResult
    data class Loaded(val routes: List<RouteOption>) : RoutesResult
    data object Failed : RoutesResult
}

data class RoutePreviewState(
    val from: Place? = null,
    val to: Place? = null,
    val avoidTolls: Boolean = false,
    val result: RoutesResult = RoutesResult.NeedsOrigin,
    val selected: Int = 0,
)

@HiltViewModel
class RoutePreviewViewModel @Inject constructor(
    private val routing: RoutingClient,
    private val savedPlaces: SavedPlaces,
) : ViewModel() {
    private val _state = MutableStateFlow(RoutePreviewState())
    val state: StateFlow<RoutePreviewState> = _state.asStateFlow()
    private var request: Job? = null

    /** Plans to [to]. Keeps the current start if there is one, otherwise starts from Home when it is set. */
    fun planTo(to: Place) {
        viewModelScope.launch {
            val from = _state.value.from ?: savedPlaces.home.first()
            _state.update { it.copy(from = from, to = to, selected = 0) }
            reload()
        }
    }

    fun setOrigin(from: Place) {
        _state.update { it.copy(from = from, selected = 0) }
        reload()
    }

    fun setAvoidTolls(avoid: Boolean) {
        _state.update { it.copy(avoidTolls = avoid, selected = 0) }
        reload()
    }

    fun select(index: Int) = _state.update { it.copy(selected = index) }

    fun retry() = reload()

    private fun reload() {
        val (from, to, avoidTolls) = _state.value.let { Triple(it.from, it.to, it.avoidTolls) }
        request?.cancel()
        if (from == null || to == null) {
            _state.update { it.copy(result = RoutesResult.NeedsOrigin) }
            return
        }
        _state.update { it.copy(result = RoutesResult.Loading) }
        request = viewModelScope.launch {
            val result = try {
                val routes = routing.routes(from, to, avoidTolls)
                if (routes.isEmpty()) RoutesResult.Failed else RoutesResult.Loaded(routes)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Network failure or "no route" from the server. Never log the endpoints (NFR-13).
                RoutesResult.Failed
            }
            _state.update { it.copy(result = result) }
        }
    }
}
