package io.github.seijikohara.femto.data.location

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * One recorded GPS fix. `time_ms` is the fix's wall-clock time
 * ([android.location.Location.time]) — the visualization axis — never the
 * boot-relative `elapsedRealtimeNanos`, which is meaningless across reboots.
 * Optional readings the chip did not supply stay null rather than fake zeros.
 */
@Entity(
    tableName = "track_points",
    indices = [
        // (trip_id, time_ms) serves per-trip reads for the future visualization;
        // the bare time_ms index serves the retention prune's range delete.
        Index("trip_id", "time_ms"),
        Index("time_ms"),
    ],
)
internal data class TrackPointEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,
    @ColumnInfo(name = "trip_id")
    val tripId: Long,
    @ColumnInfo(name = "time_ms")
    val timeMs: Long,
    @ColumnInfo(name = "latitude")
    val latitude: Double,
    @ColumnInfo(name = "longitude")
    val longitude: Double,
    @ColumnInfo(name = "speed_mps")
    val speedMps: Float?,
    @ColumnInfo(name = "bearing_deg")
    val bearingDeg: Float?,
    @ColumnInfo(name = "altitude_m")
    val altitudeM: Double?,
    @ColumnInfo(name = "accuracy_m")
    val accuracyM: Float?,
)

@Dao
internal interface TrackPointDao {
    @Insert
    suspend fun insertAll(points: List<TrackPointEntity>)

    @Query("DELETE FROM track_points WHERE time_ms < :cutoffEpochMs")
    suspend fun deleteOlderThan(cutoffEpochMs: Long)

    @Query("DELETE FROM track_points")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM track_points")
    suspend fun count(): Long

    // Keyset page in insert order (the single-writer recorder inserts
    // chronologically, so id order is trip/time order); the export streams
    // pages so a season's track never sits in memory at once.
    @Query("SELECT * FROM track_points WHERE id > :afterId ORDER BY id LIMIT :limit")
    suspend fun pageAfter(
        afterId: Long,
        limit: Int,
    ): List<TrackPointEntity>
}

@Database(entities = [TrackPointEntity::class], version = 1)
internal abstract class TrackLogDatabase : RoomDatabase() {
    abstract fun trackPointDao(): TrackPointDao

    companion object {
        @Volatile
        private var instance: TrackLogDatabase? = null

        // databaseBuilder().build() is lazy — the file opens on first use, on the
        // recorder's IO dispatcher, so calling get() during LocationGraph
        // construction never touches disk on the cold-start path.
        fun get(context: Context): TrackLogDatabase =
            instance ?: synchronized(this) {
                instance ?: Room
                    .databaseBuilder(context.applicationContext, TrackLogDatabase::class.java, "track_log.db")
                    .build()
                    .also { instance = it }
            }
    }
}
