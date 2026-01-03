package kr.alltodo.data

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class TodoRepository @Inject constructor(private val todoDao: TodoDao) {
    val allTodos: Flow<List<TodoItem>> = todoDao.getAll()

    suspend fun insert(todo: TodoItem) {
        todoDao.insert(todo)
    }

    suspend fun update(todo: TodoItem) {
        todoDao.update(todo)
    }

    suspend fun delete(todo: TodoItem) {
        todoDao.delete(todo)
    }

    suspend fun insertPaths(paths: List<PathItem>) {
        todoDao.insertPaths(paths)
    }

    suspend fun insertPath(path: PathItem) {
        todoDao.insertPath(path)
    }

    fun getPathsForTodo(todoId: String): Flow<List<PathItem>> {
        return todoDao.getPathsForTodo(todoId)
    }

    // Current Location & History Session
    suspend fun updateCurrentLocation(lat: Double, lon: Double) {
        val iLat = (lat * 100_000).toInt()
        val iLng = (lon * 100_000).toInt()
        todoDao.updateCurrentLocation(iLat, iLng)
    }

    fun observeCurrentLocation(): Flow<TodoItem?> = todoDao.observeCurrentLocation()

    suspend fun ensureCurrentLocationExists() {
        if (todoDao.getCurrentLocation() == null) {
            val initial = TodoItem(
                todo_id = "CURRENT_LOCATION",
                todo_name = "현재 위치",
                type = "00",
                no_of_path = 0,
                int_lat = 37575900, // Gwanghwamun default (scaled)
                int_long = 126976800,
                created_at = System.currentTimeMillis()
            )
            todoDao.insertInitialCurrentLocation(initial)
        }
    }

    suspend fun getLatestActiveHistory(): TodoItem? {
        return todoDao.getLatestActiveHistory()
    }

    suspend fun finalizeHistory(todoId: String, endTime: Long, pointsCount: Int) {
        todoDao.finalizeHistory(todoId, endTime, pointsCount)
    }
}
