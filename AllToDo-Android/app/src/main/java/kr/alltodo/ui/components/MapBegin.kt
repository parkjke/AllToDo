package kr.alltodo.ui.components

import android.location.Location
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kr.alltodo.ui.TodoViewModel

/**
 * MapBegin: Centralized 3-Stage Initialization Sequence
 * Follows docs/map_begin_logic.md
 */
@Composable
fun MapBeginSequence(
    isMapReady: Boolean,
    initialAnimationDone: Boolean,
    beforeLocation: Location,
    currentLocation: Location?,
    clusteredItems: List<TodoViewModel.PinClusterItem>,
    onInitialAnimationDone: () -> Unit,
    onResetAnimationDone: () -> Unit, // [NEW] Added for 5s Background Resume
    onEnableClustering: () -> Unit,
    // Provide abstract camera control to bridge different map engines
    onMove: suspend (lat: Double, lon: Double, zoom: Float, animate: Boolean) -> Unit,
    onFitBounds: suspend (points: List<Pair<Double, Double>>, padding: Int, maxZoom: Float?) -> Unit,
    onStop: () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var lastBackgroundTime by remember { mutableStateOf(0L) }
    
    // [FIX] Use rememberUpdatedState to ensure the LaunchedEffect closure sees the latest values
    val currentOnInitialAnimationDone by rememberUpdatedState(onInitialAnimationDone)
    val currentOnEnableClustering by rememberUpdatedState(onEnableClustering)
    val currentOnMove by rememberUpdatedState(onMove)
    val currentOnFitBounds by rememberUpdatedState(onFitBounds)
    val currentOnStop by rememberUpdatedState(onStop)
    
    // [FIX] Also wrap location data to avoid stale captures in LaunchedEffect
    val currentBeforeLocation by rememberUpdatedState(beforeLocation)
    val currentMyLocation by rememberUpdatedState(currentLocation)

    // [Item 5] Continuous Snapshot: Keep points in memory for Instant Resume
    var lastKnownBoundsPoints by remember { mutableStateOf<List<Pair<Double, Double>>>(emptyList()) }
    
    // Sync points while in foreground
    LaunchedEffect(clusteredItems) {
        if (clusteredItems.isNotEmpty()) {
            lastKnownBoundsPoints = clusteredItems.map { it.latitude to it.longitude }
        }
    }

    // [Item 4] Lifecycle Observer for 5s Background Resume
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    lastBackgroundTime = System.currentTimeMillis()
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (lastBackgroundTime > 0) {
                        val elapsed = System.currentTimeMillis() - lastBackgroundTime
                        if (elapsed >= 5000) {
                            onResetAnimationDone() // Re-trigger Step 1
                        }
                    }
                    lastBackgroundTime = 0
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // [FIX] Removed initialAnimationDone from keys to prevent cancellation when flag is set
    LaunchedEffect(isMapReady) {
        if (!isMapReady || initialAnimationDone) return@LaunchedEffect
        
        // [Stage 1] Fast Display : Use beforeLocation at Zoom 15
        currentOnMove(currentBeforeLocation.latitude, currentBeforeLocation.longitude, 15.0f, false)

        // [Stage 2] Fit-Bounds Display
        currentOnStop() // Stop Stage 1 movements
        
        // [Item 5] Fast Resume: Use cached points if available, otherwise wait for flow
        val pinPoints = if (lastKnownBoundsPoints.isNotEmpty()) {
            lastKnownBoundsPoints.toMutableList()
        } else if (clusteredItems.isNotEmpty()) {
            clusteredItems.map { it.latitude to it.longitude }.toMutableList()
        } else {
            // Cold Start case: Wait for first data emission
            snapshotFlow { clusteredItems }
                .filter { it.isNotEmpty() }
                .first()
                .map { it.latitude to it.longitude }.toMutableList()
        }
        
        // 2.3 Include current location if available
        currentMyLocation?.let { 
            if (it.latitude != 0.0 || it.longitude != 0.0) {
                pinPoints.add(it.latitude to it.longitude)
            }
        }

        if (pinPoints.isNotEmpty()) {
            // [Item 3] Integer-based Fast Bounds Calculation
            // Precision: 100,000 (as requested)
            val precision = 100000.0
            
            var minLatInt = Int.MAX_VALUE
            var maxLatInt = Int.MIN_VALUE
            var minLonInt = Int.MAX_VALUE
            var maxLonInt = Int.MIN_VALUE
            
            pinPoints.forEach { (lat, lon) ->
                val latI = (lat * precision).toInt()
                val lonI = (lon * precision).toInt()
                if (latI < minLatInt) minLatInt = latI
                if (latI > maxLatInt) maxLatInt = latI
                if (lonI < minLonInt) minLonInt = lonI
                if (lonI > maxLonInt) maxLonInt = lonI
            }
            
            val width = maxLonInt - minLonInt
            val height = maxLatInt - minLatInt
            
            // Add 1/4 (25%) margin to each side
            val padLon = width / 4
            val padLat = height / 4
            
            val finalMinLat = (minLatInt - padLat) / precision
            val finalMaxLat = (maxLatInt + padLat) / precision
            val finalMinLon = (minLonInt - padLon) / precision
            val finalMaxLon = (maxLonInt + padLon) / precision
            
            // Zoom 15 Span Threshold: ~1500 units (approx 1.5km at 100,000 scale)
            val zoom15Threshold = 1500
            val totalWidthWithPad = width + (2 * padLon)
            val totalHeightWithPad = height + (2 * padLat)
            
            if (totalWidthWithPad < zoom15Threshold && totalHeightWithPad < zoom15Threshold) {
                // Step 2.5: If area is small, just jump to Zoom 15 at center
                val centerLat = ((minLatInt + maxLatInt) / 2.0) / precision
                val centerLon = ((minLonInt + maxLonInt) / 2.0) / precision
                currentOnMove(centerLat, centerLon, 15.0f, true)
            } else {
                // Step 2.4: Fit bounds with calculated padding points
                val boundsPoints = listOf(
                    finalMinLat to finalMinLon,
                    finalMaxLat to finalMaxLon
                )
                currentOnFitBounds(boundsPoints, 20, 15.0f)
            }
        }

        // [Stage 3] Final Focus : After 3 seconds (as requested)
        delay(3000)
        
        currentOnEnableClustering()
        
        val targetLoc = currentMyLocation ?: currentBeforeLocation
        currentOnMove(targetLoc.latitude, targetLoc.longitude, 18.0f, true)
        
        // Flag completion only after Stage 3 is safely initiated or done
        currentOnInitialAnimationDone()
    }
}
