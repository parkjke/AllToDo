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
        calendar.add(Calendar.HOUR, -24)
        val minTime = calendar.timeInMillis
        
        calendar.time = centerDate
        calendar.add(Calendar.HOUR, 24 + 24) // Restore +24h (Total +48h window was iOS logic? iOS code says +/- 24h)
        // iOS: min = center - 24h, max = center + 24h
        // Let's stick to iOS Logic strictly:
        calendar.time = centerDate
        calendar.add(Calendar.HOUR, 24)
        val maxTime = calendar.timeInMillis
        
        // 1. Path Existence Filter
        val withPath = allItems.filter { it.is_exist_location_path }
        
        // 2. Time Window Filter (±24h)
        val timeFiltered = withPath.filter { item ->
            // Use begin_time or date_time(parsed) or created_at
            // iOS Logic used Date comparison
            val itemTime = item.begin_time ?: item.created_at // specific logic might be needed for string date_time
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
    
    fun isInKorea(lat: Double, lon: Double): Boolean {
        return lat in 32.0..44.0 && lon in 123.0..133.0
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
            
            if (isInKorea(item.latitude, item.longitude)) {
                near.add(item)
            } else {
                farCount++
            }
        }
        
        return PartitionResult(near, farCount)
    }
    
    // MARK: - Cluster Styling
    
    data class ClusterStyle(
        val baseResId: Int,
        val color: Int,
        val count: Int
    )
    
    fun resolveClusterStyle(items: List<UnifiedItem>): ClusterStyle {
        var userLocationFound = false
        var blueCount = 0   // Server 20
        var greenCount = 0  // Local 10
        var redCount = 0    // History 00 or User
        
        for (item in items) {
            when (item) {
                is UnifiedItem.CurrentLocation -> userLocationFound = true
                is UnifiedItem.Todo -> {
                    if (item.item.source != "local") blueCount++ // Server
                    else greenCount++ // Local
                }
                is UnifiedItem.History -> redCount++
            }
        }
        
        var baseResId = R.drawable.pin_todo_ready
        
        if (userLocationFound) {
            baseResId = R.drawable.pin_current
        } else {
            // Priority: Blue > Green > Red (History)
            // Logic copied from iOS:
            // counts = [("PinReceiveReady", blue), ("PinTodoReady", green), ("PinHistory", red)]
            // maxItem = counts.max
            
            if (blueCount >= greenCount && blueCount >= redCount && blueCount > 0) {
                 baseResId = R.drawable.pin_receive_ready
            } else if (greenCount >= redCount && greenCount > 0) {
                 baseResId = R.drawable.pin_todo_ready
            } else if (redCount > 0) {
                 baseResId = R.drawable.pin_history
            }
        }
        
        // Color Resolution
        val color = when (baseResId) {
            R.drawable.pin_history, R.drawable.pin_current -> Color.RED
            R.drawable.pin_receive_ready -> Color.BLUE // System Blue-ish
            else -> Color.parseColor("#00C7BE") // AllToDo Green (Teal)
        }
        
        return ClusterStyle(baseResId, color, items.size)
    }
}
