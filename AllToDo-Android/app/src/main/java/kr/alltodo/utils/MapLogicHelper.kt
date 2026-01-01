package kr.alltodo.utils

import android.location.Location
import kr.alltodo.data.TodoItem
import kr.alltodo.ui.UnifiedItem
import java.util.Calendar
import java.util.Date
import android.graphics.Color
import kr.alltodo.R

object MapLogicHelper {

    // MARK: - Core Filtering Logic
    
    fun filterAndTransformItems(
        allItems: List<TodoItem>,
        currentLocation: Location?,
        showHistoryMode: Boolean,
        anchorDate: Date,
        selectedDate: Date
    ): List<UnifiedItem> {
        
        val centerDate = if (showHistoryMode) selectedDate else anchorDate
        val calendar = Calendar.getInstance()
        calendar.time = centerDate
        // iOS Logic: +/- 24 hours from enter date
        calendar.add(Calendar.HOUR, -24)
        val minTime = calendar.timeInMillis
        
        calendar.time = centerDate
        calendar.add(Calendar.HOUR, 24)
        val maxTime = calendar.timeInMillis
        
        // 1. Path Existence Filter (Strict Requirement: no_of_path > 0)
        val withLocation = allItems.filter { 
            it.no_of_path > 0
        }
        
        // 2. Time Window Filter (±24h)
        val timeFiltered = withLocation.filter { item ->
            // Use begin_time or date_time(parsed) or created_at
            val itemTime = item.begin_time ?: item.created_at
            itemTime in minTime..maxTime
        }
        
        val results = mutableListOf<UnifiedItem>()
        
        // 3. User Location Virtual Item
        if (currentLocation != null) {
            results.add(UnifiedItem.CurrentLocation(currentLocation.latitude, currentLocation.longitude))
        }
        
        // 4. Transform to UnifiedMapItem
        for (item in timeFiltered) {
            if (item.type.startsWith("0")) {
                results.add(UnifiedItem.History(item))
            } else {
                results.add(UnifiedItem.Todo(item))
            }
        }
        
        return results
    }

    // MARK: - Geo Partitioning (Korea Rule)
    
    fun isInKorea(intLat: Int, intLng: Int): Boolean {
        // Bounds: Lat 32.0..44.0, Lon 123.0..133.0
        return intLat in 3200000..4400000 && intLng in 12300000..13300000
    }
    
    data class PartitionResult(
        val nearItems: List<UnifiedItem>,
        val farCount: Int
    )

    fun partitionItemsByKorea(items: List<UnifiedItem>): PartitionResult {
        val near = mutableListOf<UnifiedItem>()
        var farCount = 0
        
        for (item in items) {
            // Always include User Location
            if (item is UnifiedItem.CurrentLocation) {
                near.add(item)
                continue
            }
            
            if (isInKorea(item.intLat, item.intLng)) {
                near.add(item)
            } else {
                farCount++
            }
        }
        
        return PartitionResult(near, farCount)
    }
    
    // MARK: - Cluster Styling (Static Bitmaps)
    
    data class ClusterStyle(
        val pinId: String,
        val color: Int,
        val count: Int
    )
    
    fun resolveClusterStyle(items: List<UnifiedItem>): ClusterStyle {
        var userLocationFound = false
        var blueCount = 0   // Server 20
        var greenCount = 0  // Local 10
        var redCount = 0    // History 00 / History 01
        
        for (item in items) {
            when (item) {
                is UnifiedItem.CurrentLocation -> userLocationFound = true
                is UnifiedItem.Todo -> {
                    if (item.item.source != "local") blueCount++ // Server
                    else if (item.item.type == "00") redCount++
                    else greenCount++ // Local (10)
                }
                is UnifiedItem.History -> redCount++
            }
        }
        
        var pinId = "10"
        var color = Color.parseColor("#00C7BE") // AllToDo Green
        
        if (userLocationFound) {
            pinId = "00"
            color = Color.RED
        } else {
            // Priority: Blue > Green > Red based on COUNT
            if (blueCount >= greenCount && blueCount >= redCount && blueCount > 0) {
                 pinId = "20"
                 color = Color.BLUE
            } else if (redCount > greenCount && redCount > 0) {
                 pinId = "01"
                 color = Color.RED
            } else {
                 // Default Green
                 pinId = "10" 
                 color = Color.parseColor("#00C7BE")
            }
        }
        
        return ClusterStyle(pinId, color, items.size)
    }
}
