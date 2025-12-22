package kr.alltodo.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import androidx.room.ForeignKey

@Entity(
    tableName = "contacts",
    foreignKeys = [
        ForeignKey(
            entity = TodoItem::class,
            parentColumns = ["todo_id"],
            childColumns = ["todo_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ContactItem(
    @PrimaryKey
    @ColumnInfo(name = "todo_id")
    val todo_id: String,
    
    @ColumnInfo(name = "address_id")
    val address_id: String? = null,
    
    @ColumnInfo(name = "name")
    val name: String,
    
    @ColumnInfo(name = "p_name")
    val p_name: String? = null,
    
    @ColumnInfo(name = "int_long")
    val int_long: Int? = null,
    
    @ColumnInfo(name = "int_lat")
    val int_lat: Int? = null
)
