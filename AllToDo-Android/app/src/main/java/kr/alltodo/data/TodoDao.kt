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

    // Path Operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPath(path: PathItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPaths(paths: List<PathItem>)

    @Query("SELECT * FROM paths WHERE todo_id = :todoId ORDER BY path_id ASC")
    fun getPathsForTodo(todoId: String): Flow<List<PathItem>>
}
