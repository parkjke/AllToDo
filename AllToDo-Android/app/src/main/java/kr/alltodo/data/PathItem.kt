package kr.alltodo.data

import androidx.room.*

@Entity(
    tableName = "paths",
    foreignKeys = [
        ForeignKey(
            entity = TodoItem::class,
            parentColumns = ["todo_id"],
            childColumns = ["todo_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["todo_id"])]
)
data class PathItem(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "path_id")
    val path_id: Long = 0,
    
    @ColumnInfo(name = "todo_id")
    val todo_id: String,
    
    @ColumnInfo(name = "int_long")
    val int_long: Int,
    
    @ColumnInfo(name = "int_lat")
    val int_lat: Int
)
