package kr.alltodo.tp.data

import androidx.room.*
import java.util.UUID

@Entity(tableName = "trajectories")
data class TrajectoryEntity(
    @PrimaryKey val todo_id: String,
    val begin_time: Long,
    val end_time: Long?,
    val no_of_path: Int,
    val created_at: Long,
    val begin_power: Int,
    val end_power: Int,
    val usage: Int
)

@Entity(
    tableName = "paths",
    foreignKeys = [
        ForeignKey(
            entity = TrajectoryEntity::class,
            parentColumns = ["todo_id"],
            childColumns = ["todo_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["todo_id"])]
)
data class PathEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val todo_id: String,
    val time: Long,
    val int_lat: Int,
    val int_long: Int
)

@Dao
interface TrajectoryDao {
    @Insert
    suspend fun insertTrajectory(trajectory: TrajectoryEntity)

    @Update
    suspend fun updateTrajectory(trajectory: TrajectoryEntity)

    @Insert
    suspend fun insertPathPoint(pathPoint: PathEntity)

    @Transaction
    @Query("SELECT * FROM trajectories ORDER BY created_at DESC")
    suspend fun getAllTrajectories(): List<TrajectoryEntity>

    @Query("SELECT * FROM paths WHERE todo_id = :todoId ORDER BY time ASC")
    suspend fun getPathsForTrajectory(todoId: String): List<PathEntity>

    @Delete
    suspend fun deleteTrajectory(trajectory: TrajectoryEntity)
}

@Database(entities = [TrajectoryEntity::class, PathEntity::class], version = 1)
abstract class TrajectoryDatabase : RoomDatabase() {
    abstract fun trajectoryDao(): TrajectoryDao

    companion object {
        @Volatile
        private var INSTANCE: TrajectoryDatabase? = null

        fun getDatabase(context: android.content.Context): TrajectoryDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TrajectoryDatabase::class.java,
                    "trajectory_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
