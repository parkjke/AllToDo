package kr.alltodo.ui.components

import android.location.Location
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kr.alltodo.ui.UnifiedItem
import kr.alltodo.ui.PinClusterItem
import kr.alltodo.utils.GeomUtils

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
    clusteredItems: List<PinClusterItem>,
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
    
    SideEffect {
    }
    
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
    LaunchedEffect(isMapReady, initialAnimationDone) {
        if (isMapReady && !initialAnimationDone) {
            currentOnMove(currentBeforeLocation.latitude, currentBeforeLocation.longitude, 15.0f, false)
        }
    }

    // [Stage 2 & 3] Data-Driven Fit Bounds & Final Focus
    // [FIX] Use snapshotFlow to wait for initial items but prevent restarts on subsequent updates.
    val currentClusteredItemsState by rememberUpdatedState(clusteredItems)
    
    // [Stage 2 & 3] Data-Driven Fit Bounds & Final Focus
    LaunchedEffect(isMapReady, initialAnimationDone) {
        if (!isMapReady || initialAnimationDone) return@LaunchedEffect
        
        // [Stage 2.0] Wait for initial clustered items if not already present
        // [FIX] Use currentClusteredItemsState to observe latest items without restarting effect
        if (currentClusteredItemsState.isEmpty()) {
            snapshotFlow { currentClusteredItemsState }
                .filter { it.isNotEmpty() }
                .first()
        } else {
        }

        // [Interruption] Stop any ongoing Stage 1 or previous animations
        currentOnStop()
        
        // 1. Prepare Points (Integer-based for speed)
        // Use a snapshot of current items to avoid mid-sequence calculation drifts
        val currentItems = clusteredItems.toList() 
        val pinPoints = currentItems.map { it.latitude to it.longitude }.toMutableList()
        currentMyLocation?.let { 
            if (it.latitude != 0.0 || it.longitude != 0.0) pinPoints.add(it.latitude to it.longitude)
        }

        if (pinPoints.isNotEmpty()) {
            // [STAGE 2] Fit Bounds to show all pins
            // Use GeomUtils to calculate the bounding box.
            // minDelta = 2000 (~2.2km) ensures the view is wide enough for Stage 2 (Zoom ~14-15)
            // without needing explicit MaxZoom locks.
            val intPoints = pinPoints.map { (it.first * GeomUtils.PRECISION).toInt() to (it.second * GeomUtils.PRECISION).toInt() }
            val intRect = GeomUtils.calculateIntBoundingBox(intPoints, paddingPercent = 0, minDelta = 2000)
            
            val precision = GeomUtils.PRECISION
            
            if (intRect.minLat == intRect.maxLat && intRect.minLon == intRect.maxLon) {
                // Single-point: Move to center at zoom 15
                currentOnMove(intRect.minLat / precision, intRect.minLon / precision, 15.0f, true)
            } else {
                // Multi-point fit bounds
                val boundsPoints = listOf(
                    intRect.minLat / precision to intRect.minLon / precision,
                    intRect.maxLat / precision to intRect.maxLon / precision
                )
                currentOnFitBounds(boundsPoints, 100, 15.0f)
            }
        }

        // [Stage 3] Final Focus After 3 Seconds
        delay(3000)
        
        // Final Focus on Current Location
        currentOnEnableClustering()
        val targetLoc = currentMyLocation ?: currentBeforeLocation
        currentOnMove(targetLoc.latitude, targetLoc.longitude, 18.0f, true)
        
        currentOnInitialAnimationDone()
    }
}
