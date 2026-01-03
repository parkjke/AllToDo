package kr.alltodo.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoDao {
    @Query("SELECT * FROM todo_items ORDER BY created_at DESC")
    fun getAll(): Flow<List<TodoItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: TodoItem)

    @Update
    suspend fun update(item: TodoItem)

    @Delete
    suspend fun delete(item: TodoItem)

    // Contact Operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: ContactItem)

    @Query("SELECT * FROM contacts WHERE todo_id = :todoId")
    suspend fun getContactForTodo(todoId: String): ContactItem?

    // Current Location (0-th History) Operations
    @Query("UPDATE todo_items SET int_lat = :lat, int_long = :lng, created_at = :time WHERE todo_id = 'CURRENT_LOCATION'")
    suspend fun updateCurrentLocation(lat: Int, lng: Int, time: Long = System.currentTimeMillis())

    @Query("SELECT * FROM todo_items WHERE todo_id = 'CURRENT_LOCATION' LIMIT 1")
    suspend fun getCurrentLocation(): TodoItem?

    @Query("SELECT * FROM todo_items WHERE todo_id = 'CURRENT_LOCATION' LIMIT 1")
    fun observeCurrentLocation(): Flow<TodoItem?>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertInitialCurrentLocation(item: TodoItem)

    // History Session Operations
    @Query("SELECT * FROM todo_items WHERE type = '00' AND todo_id != 'CURRENT_LOCATION' ORDER BY created_at DESC LIMIT 1")
    suspend fun getLatestActiveHistory(): TodoItem?

    @Query("UPDATE todo_items SET end_time = :endTime, no_of_path = :noOfPath WHERE todo_id = :todoId")
    suspend fun finalizeHistory(todoId: String, endTime: Long, noOfPath: Int)

    // Path Operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPath(path: PathItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPaths(paths: List<PathItem>)

    @Query("SELECT * FROM paths WHERE todo_id = :todoId ORDER BY path_id ASC")
    fun getPathsForTodo(todoId: String): Flow<List<PathItem>>
}
