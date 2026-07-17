package io.github.seijikohara.femto.data.location

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
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
        // (trip_id, time_ms) serves per-trip reads for the future visualization
        // AND is unique so a crash-restart that replays the same getLastKnownLocation
        // seed (identical trip/time) is ignored on insert rather than duplicated.
        Index(value = ["trip_id", "time_ms"], unique = true),
        // The bare time_ms index serves the retention prune's range delete.
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
    // IGNORE, paired with the unique (trip_id, time_ms) index, drops a replayed
    // seed without aborting the rest of the batch.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(points: List<TrackPointEntity>)

    @Query("DELETE FROM track_points WHERE time_ms < :cutoffEpochMs")
    suspend fun deleteOlderThan(cutoffEpochMs: Long)

    @Query("DELETE FROM track_points")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM track_points")
    suspend fun count(): Long

    @Query("SELECT MAX(time_ms) FROM track_points")
    suspend fun newestTimeMs(): Long?

    // Keyset page in insert order (the single-writer recorder inserts
    // chronologically, so id order is trip/time order); the export streams
    // pages so a season's track never sits in memory at once.
    @Query("SELECT * FROM track_points WHERE id > :afterId ORDER BY id LIMIT :limit")
    suspend fun pageAfter(
        afterId: Long,
        limit: Int,
    ): List<TrackPointEntity>

    // One indexed GROUP BY pass yields each trip's start time, point count, and
    // lat/lon bounds for the trip-selector list. Distance is NOT derivable in SQL
    // (haversine over consecutive points); the visualization computes it in Kotlin
    // while loading the trip's points.
    @Query(
        "SELECT trip_id AS tripId, MIN(time_ms) AS startMs, MAX(time_ms) AS endMs, " +
            "COUNT(*) AS pointCount, MIN(latitude) AS minLat, MAX(latitude) AS maxLat, " +
            "MIN(longitude) AS minLon, MAX(longitude) AS maxLon, " +
            "MIN(altitude_m) AS minAltitude, MAX(altitude_m) AS maxAltitude " +
            "FROM track_points GROUP BY trip_id ORDER BY startMs DESC",
    )
    suspend fun tripSummaries(): List<TripSummaryRow>

    // A whole trip's points in chronological order, served by the unique
    // (trip_id, time_ms) index. Bounded per-trip (~3600 rows/driving-hour at 1 Hz);
    // the visualization downsamples pathological trips before rendering.
    @Query("SELECT * FROM track_points WHERE trip_id = :tripId ORDER BY time_ms")
    suspend fun pointsForTrip(tripId: Long): List<TrackPointEntity>
}

/** Aggregate row for one trip in the visualization's trip selector. */
internal data class TripSummaryRow(
    val tripId: Long,
    val startMs: Long,
    val endMs: Long,
    val pointCount: Int,
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double,
    // Null when no point in the trip carried an altitude reading.
    val minAltitude: Double?,
    val maxAltitude: Double?,
)

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
