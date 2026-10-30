package io.github.aldrinadira.jalanz.core.data

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.aldrinadira.jalanz.core.searchclient.Place
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Home, Work, named saved places and recent places, stored only on this phone (FR-01, NFR-02). */
@Singleton
class SavedPlaces @Inject constructor(private val dao: SavedPlaceDao) {
    val home: Flow<Place?> = dao.observeKind(PlaceKind.HOME).map { it?.toPlace() }
    val work: Flow<Place?> = dao.observeKind(PlaceKind.WORK).map { it?.toPlace() }
    val recents: Flow<List<Place>> = dao.observeRecents(RECENTS_SHOWN).map { list -> list.map { it.toPlace() } }

    /** Places the user named and saved, e.g. a shop missing from the map data, A to Z. */
    val saved: Flow<List<Place>> = dao.observeAllOfKind(PlaceKind.SAVED).map { list -> list.map { it.toPlace() } }

    suspend fun setHome(place: Place) = dao.replaceKind(place.toEntity(PlaceKind.HOME))
    suspend fun setWork(place: Place) = dao.replaceKind(place.toEntity(PlaceKind.WORK))
    suspend fun clearHome() = dao.deleteKind(PlaceKind.HOME)
    suspend fun clearWork() = dao.deleteKind(PlaceKind.WORK)
    suspend fun addRecent(place: Place) = dao.pushRecent(place.toEntity(PlaceKind.RECENT), RECENTS_KEPT)
    suspend fun clearRecents() = dao.deleteKind(PlaceKind.RECENT)
    suspend fun save(place: Place) = dao.insert(place.toEntity(PlaceKind.SAVED))
    suspend fun removeSaved(place: Place) = dao.deleteOne(PlaceKind.SAVED, place.name, place.lat, place.lon)

    private companion object {
        const val RECENTS_SHOWN = 10
        const val RECENTS_KEPT = 20
    }
}

private fun SavedPlaceEntity.toPlace() = Place(name, detail, lat, lon)

private fun Place.toEntity(kind: String) =
    SavedPlaceEntity(kind = kind, name = name, detail = detail, lat = lat, lon = lon, usedAt = System.currentTimeMillis())

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): SavedPlacesDatabase =
        Room.databaseBuilder(context, SavedPlacesDatabase::class.java, "jalanz.db").build()

    @Provides
    fun savedPlaceDao(database: SavedPlacesDatabase): SavedPlaceDao = database.savedPlaceDao()
}
