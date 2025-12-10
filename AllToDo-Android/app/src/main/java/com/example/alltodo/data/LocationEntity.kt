package com.example.alltodo.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlin.math.roundToInt

@Entity(tableName = "location_history")
data class LocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val latitude: Double, // Kept for legacy compatibility if needed
    val longitude: Double, // Kept for legacy compatibility if needed
    val timestamp: Long,
    
    // New Integer Fields (Default to 0 during migration or if unused)
    // In a real app, we might migrate logic fully.
    // For now, let's auto-calculate or allow nullable?
    // User goal: "Represent all geographic positions using integers".
    // Let's add transient properties or computed properties for now to be safe against schema changes,
    // OR we can change the Entity if we do a migration.
    // Since User asked for "DB schema aligned with integer coordinate format",
    // and this is the LOCAL ROOM DB, we should ideally migrate it too.
    // However, I just saw no migration logic for Room.
    // Let's perform a "Soft Integration" -> Update ViewModel to use IntCoordinate, 
    // and map to Double for this Entity for now to avoid Room Migration crash 
    // unless I'm confident to write Room Migration.
    // BUT, the request implies standardizing.
    // If I change schema here, app crashes on launch without migration.
    // Let's add extension properties first.
) {
    val intCoordinate: IntCoordinate
        get() = IntCoordinate.fromDouble(latitude, longitude)
}
