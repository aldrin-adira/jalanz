package io.github.aldrinadira.jalanz.core.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** Kinds of saved place. Stored as text so the table stays readable in a database export (FR-18). */
object PlaceKind {
    const val HOME = "home"
    const val WORK = "work"
    const val RECENT = "recent"
    const val SAVED = "saved"
}

/** A place kept on this phone only (NFR-02; backup and device transfer are off). */
@Entity(tableName = "saved_place")
data class SavedPlaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val name: String,
    val detail: String,
    val lat: Double,
    val lon: Double,
    /** Epoch milliseconds of the last time the place was picked or saved. */
    val usedAt: Long,
)

@Dao
interface SavedPlaceDao {
    @Query("SELECT * FROM saved_place WHERE kind = :kind LIMIT 1")
    fun observeKind(kind: String): Flow<SavedPlaceEntity?>

    @Query("SELECT * FROM saved_place WHERE kind = 'recent' ORDER BY usedAt DESC LIMIT :limit")
    fun observeRecents(limit: Int): Flow<List<SavedPlaceEntity>>

    @Query("SELECT * FROM saved_place WHERE kind = :kind ORDER BY name COLLATE NOCASE")
    fun observeAllOfKind(kind: String): Flow<List<SavedPlaceEntity>>

    @Query("DELETE FROM saved_place WHERE kind = :kind AND name = :name AND lat = :lat AND lon = :lon")
    suspend fun deleteOne(kind: String, name: String, lat: Double, lon: Double)

    @Insert
    suspend fun insert(place: SavedPlaceEntity)

    @Query("DELETE FROM saved_place WHERE kind = :kind")
    suspend fun deleteKind(kind: String)

    @Query("DELETE FROM saved_place WHERE kind = 'recent' AND name = :name AND lat = :lat AND lon = :lon")
    suspend fun deleteRecent(name: String, lat: Double, lon: Double)

    @Query(
        "DELETE FROM saved_place WHERE kind = 'recent' AND id NOT IN " +
            "(SELECT id FROM saved_place WHERE kind = 'recent' ORDER BY usedAt DESC LIMIT :keep)",
    )
    suspend fun trimRecents(keep: Int)

    /** Home and Work hold at most one place each. */
    @Transaction
    suspend fun replaceKind(place: SavedPlaceEntity) {
        deleteKind(place.kind)
        insert(place)
    }

    /** Moves a picked place to the top of recents, keeping [keep] at most. */
    @Transaction
    suspend fun pushRecent(place: SavedPlaceEntity, keep: Int) {
        deleteRecent(place.name, place.lat, place.lon)
        insert(place)
        trimRecents(keep)
    }
}

@Database(entities = [SavedPlaceEntity::class], version = 1, exportSchema = true)
abstract class SavedPlacesDatabase : RoomDatabase() {
    abstract fun savedPlaceDao(): SavedPlaceDao
}
