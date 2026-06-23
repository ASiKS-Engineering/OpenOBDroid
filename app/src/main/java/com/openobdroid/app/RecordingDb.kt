package com.openobdroid.app

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "recordings")
data class RecordingSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,
    val endTime: Long? = null,
    val name: String,
    val dtcsAtStart: String? = null,
    val dtcsAtEnd: String? = null
)

@Entity(
    tableName = "sensor_data",
    foreignKeys = [
        ForeignKey(
            entity = RecordingSession::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId")]
)
data class SensorDataPoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val timestamp: Long,
    val pid: String,
    val name: String,
    val value: Float
)

@Dao
interface RecordingDao {
    @Insert
    suspend fun insertSession(session: RecordingSession): Long

    @Update
    suspend fun updateSession(session: RecordingSession)

    @Insert
    suspend fun insertDataPoints(dataPoints: List<SensorDataPoint>)

    @Query("SELECT * FROM recordings ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<RecordingSession>>

    @Query("SELECT * FROM sensor_data WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getDataForSession(sessionId: Long): List<SensorDataPoint>

    @Query("SELECT * FROM recordings WHERE id = :id")
    suspend fun getSessionById(id: Long): RecordingSession?

    @Delete
    suspend fun deleteSession(session: RecordingSession)
}

@Database(entities = [RecordingSession::class, SensorDataPoint::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recordingDao(): RecordingDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "obd_recordings.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
