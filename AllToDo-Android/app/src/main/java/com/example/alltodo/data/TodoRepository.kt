package com.example.alltodo.data

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
}
