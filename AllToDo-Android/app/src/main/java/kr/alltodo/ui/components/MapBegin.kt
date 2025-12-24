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

    // [FIX] Use SmartLocationManager constants for extreme fallback
    val defaultLat = kr.alltodo.utils.SmartLocationManager.GWANGHWAMUN_LAT
    val defaultLon = kr.alltodo.utils.SmartLocationManager.GWANGHWAMUN_LON

    // [Stage 1] Fast Display : Jump immediately when map is ready
    LaunchedEffect(isMapReady) {
        if (isMapReady && !initialAnimationDone) {
            currentOnMove(currentBeforeLocation.latitude, currentBeforeLocation.longitude, 15.0f, false)
        }
    }

    // [Stage 2 & 3] Data-Driven Fit Bounds & Final Focus
    LaunchedEffect(isMapReady, clusteredItems, initialAnimationDone) {
        if (!isMapReady || initialAnimationDone || clusteredItems.isEmpty()) return@LaunchedEffect
        
        // [Interruption] Stop any ongoing Stage 1 or previous animations
        currentOnStop()
        
        // 1. Prepare Points (Integer-based for speed)
        val pinPoints = clusteredItems.map { it.latitude to it.longitude }.toMutableList()
        currentMyLocation?.let { 
            if (it.latitude != 0.0 || it.longitude != 0.0) pinPoints.add(it.latitude to it.longitude)
        }

        if (pinPoints.isNotEmpty()) {
            val precision = 100000.0
            var minLatInt = Int.MAX_VALUE; var maxLatInt = Int.MIN_VALUE
            var minLonInt = Int.MAX_VALUE; var maxLonInt = Int.MIN_VALUE
            
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
            
            // Zoom 15 Span Threshold: ~1500 units (~1.5km)
            val zoom15Threshold = 1500
            
            if (width < zoom15Threshold && height < zoom15Threshold) {
                val centerLat = ((minLatInt + maxLatInt) / 2.0) / precision
                val centerLon = ((minLonInt + maxLonInt) / 2.0) / precision
                currentOnMove(centerLat, centerLon, 15.0f, true)
            } else {
                // Fit bounds with 1/4 padding logic built into SDK animate (usually) or manual points
                val boundsPoints = listOf(
                    minLatInt / precision to minLonInt / precision,
                    maxLatInt / precision to maxLonInt / precision
                )
                currentOnFitBounds(boundsPoints, 60, 15.0f)
            }
        }

        // [Stage 3] Final Focus After 3 Seconds
        delay(3000)
        
        // Re-check if we are still the active effect (interruption check)
        currentOnEnableClustering()
        val targetLoc = currentMyLocation ?: currentBeforeLocation
        currentOnMove(targetLoc.latitude, targetLoc.longitude, 18.0f, true)
        
        onInitialAnimationDone()
    }
}
