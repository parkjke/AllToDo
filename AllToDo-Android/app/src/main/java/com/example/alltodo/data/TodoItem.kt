package com.example.alltodo.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "todo_items")
data class TodoItem(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val text: String,
    val completed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val source: String = "local", // "local" or "external"
    val latitude: Double? = null,
    val longitude: Double? = null
)
