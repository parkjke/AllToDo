package kr.alltodo.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import java.util.UUID

@Entity(tableName = "todo_items")
data class TodoItem(
    @PrimaryKey 
    @ColumnInfo(name = "todo_id")
    val todo_id: String = UUID.randomUUID().toString(),
    
    @ColumnInfo(name = "todo_name")
    val todo_name: String,
    
    @ColumnInfo(name = "is_exist_person")
    val is_exist_person: Boolean = false,
    
    @ColumnInfo(name = "date_time")
    val date_time: String? = null, // Combining date and time for now as per schema
    
    @ColumnInfo(name = "memo")
    val memo: String? = null,
    
    @ColumnInfo(name = "no_of_path")
    val no_of_path: Int = 0,
    
    @ColumnInfo(name = "begin_time")
    val begin_time: Long? = null,
    
    @ColumnInfo(name = "end_time")
    val end_time: Long? = null,
    
    @ColumnInfo(name = "type")
    val type: String = "10", // 00: History, 10: To-do, 20: Server
    
    @ColumnInfo(name = "created_at")
    val created_at: Long = System.currentTimeMillis(),
    
    // Additional fields for app logic
    @ColumnInfo(name = "completed")
    val completed: Boolean = false,
    
    @ColumnInfo(name = "int_lat")
    val int_lat: Int? = null,
    
    @ColumnInfo(name = "int_long")
    val int_long: Int? = null,
    
    @ColumnInfo(name = "latitude")
    val latitude: Double? = null,
    
    @ColumnInfo(name = "longitude")
    val longitude: Double? = null,
    
    @ColumnInfo(name = "person")
    val person: String? = null,
    
    @ColumnInfo(name = "source")
    val source: String = "local"
)
