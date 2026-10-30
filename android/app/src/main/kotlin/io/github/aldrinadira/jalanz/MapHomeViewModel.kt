package io.github.aldrinadira.jalanz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.aldrinadira.jalanz.core.data.SavedPlaces
import io.github.aldrinadira.jalanz.core.searchclient.Place
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MapHomeViewModel @Inject constructor(private val savedPlaces: SavedPlaces) : ViewModel() {
    /** Saves [place] under the user's chosen name, e.g. a shop that no map data source knows. */
    fun save(place: Place) {
        viewModelScope.launch { savedPlaces.save(place) }
    }
}
