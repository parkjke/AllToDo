package com.example.alltodo.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import com.example.alltodo.ui.UnifiedItem
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@OptIn(MapsComposeExperimentalApi::class)
@Composable
fun GoogleMapContent(
    modifier: Modifier = Modifier,
    clusteredItems: List<com.example.alltodo.ui.TodoViewModel.PinClusterItem>, // [FIX] Use Clustered Items
    currentLocation: android.location.Location?,
    cameraPositionState: CameraPositionState,
    onMapClick: (com.kakao.vectormap.LatLng) -> Unit,
    onMapLongClick: (com.kakao.vectormap.LatLng) -> Unit, 
    onItemClick: (UnifiedItem) -> Unit,
    onItemClickWithCoords: (UnifiedItem, Float, Float) -> Unit, 
    onClusterClickWithCoords: (List<UnifiedItem>, Float, Float) -> Unit,
    onRotationChange: (Float) -> Unit,
    isMapReady: Boolean,
    onMapLoaded: () -> Unit,
    showHistoryMode: Boolean,
    initialAnimationDone: Boolean,
    showHistoryMode: Boolean,
    initialAnimationDone: Boolean,
    onInitialAnimationDone: () -> Unit,
    onFarItemsDetected: (Int) -> Unit = {} // [NEW] Callback for Far Items
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // 1. UI Settings (Disable Toolbar & Zoom)
    val uiSettings = remember {
        MapUiSettings(
            zoomControlsEnabled = false,
            compassEnabled = false,
            myLocationButtonEnabled = false,
            mapToolbarEnabled = false // [FIX] Hide Google Map Button
        )
    }

    val properties = remember {
        MapProperties(
            isMyLocationEnabled = false // [FIX] Disable native blue dot to avoid overlap with custom marker
        )
    }

    // [FIX] Far Item Logic (500km Filter)
    val farThreshold = 500000f // 500km in meters
    val visibleClusters = remember(clusteredItems, currentLocation) {
        if (currentLocation == null) clusteredItems
        else clusteredItems.filter { cluster ->
             val results = FloatArray(1)
             android.location.Location.distanceBetween(currentLocation.latitude, currentLocation.longitude, cluster.latitude, cluster.longitude, results)
             results[0] <= farThreshold // Only show if <= 500km
        }
    }
    
    // [FIX] Removed internal farItemMessage handling. Handled by MainScreen via callback.

    // [FIX] Reactive Launch Animation: Trigger on mapReady AND reset of animation flag
    LaunchedEffect(isMapReady, initialAnimationDone) {
        if (initialAnimationDone || !isMapReady) return@LaunchedEffect
        
        // Wait for valid data (snapshotFlow)
        var validPoints: List<LatLng> = emptyList()
        
        // Wait until we have data or location
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < 5000) { // Max 5 sec wait
             val items = visibleClusters // [FIX] Use filtered clusters for bounds
             val loc = currentLocation
             
             // [NEW] Calculate invisible far items count for notification
             val farCount = clusteredItems.sumOf { cluster ->
                 val results = FloatArray(1)
                 if (loc != null) {
                     android.location.Location.distanceBetween(loc.latitude, loc.longitude, cluster.latitude, cluster.longitude, results)
                     if (results[0] > farThreshold) cluster.count else 0
                 } else 0
             }
             if (farCount > 0) {
                 onFarItemsDetected(farCount)
             }
             
             val points = mutableListOf<LatLng>()
             val itemPoints = items.flatMap { it.items }
                .filter { (it is UnifiedItem.Todo || it is UnifiedItem.History) }
                .filter { it.latitude != 0.0 && it.longitude != 0.0 } 
                .map { item -> LatLng(item.latitude, item.longitude) }
             points.addAll(itemPoints)
             
             if (loc != null && loc.latitude != 0.0) {
                 points.add(LatLng(loc.latitude, loc.longitude))
             }
             
             if (points.isNotEmpty()) {
                 validPoints = points
                 break
             }
             delay(500) 
        }
        
        if (validPoints.isNotEmpty()) {
             // Run Animation Sequence
             val boundsBuilder = LatLngBounds.builder()
             validPoints.forEach { boundsBuilder.include(it) }
             val bounds = boundsBuilder.build()

             try {
                     // Step 1: Fit Bounds
                     cameraPositionState.animate(
                         CameraUpdateFactory.newLatLngBounds(bounds, 100),
                         1000
                     )

                     // Step 2: Enforce Min Zoom 15 (Don't zoom out too far)
                     if (cameraPositionState.position.zoom < 15f) {
                         cameraPositionState.animate(
                             CameraUpdateFactory.zoomTo(15f),
                             500
                         )
                     }

                     // Step 3: Wait 3 seconds
                     delay(3000)
                     
                     // Step 4: Zoom to Current Location
                     if (currentLocation != null) {
                          cameraPositionState.animate(
                              CameraUpdateFactory.newLatLngZoom(
                                  LatLng(currentLocation.latitude, currentLocation.longitude), 
                                  18f
                              ),
                              1500 
                          )
                     }
                 onInitialAnimationDone()
             } catch (e: Exception) { e.printStackTrace() }
        } else {
             onInitialAnimationDone()
        }
    }

    // [FIX] Projection State
    var mapProjection by remember { mutableStateOf<com.google.android.gms.maps.Projection?>(null) }

    Box(modifier = modifier) {
    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        properties = properties,
        uiSettings = uiSettings,
        onMapClick = { latLng ->
            onMapClick(com.kakao.vectormap.LatLng.from(latLng.latitude, latLng.longitude))
        },
        onMapLongClick = { latLng ->
             onMapLongClick(com.kakao.vectormap.LatLng.from(latLng.latitude, latLng.longitude))
        },
        onMapLoaded = onMapLoaded
    ) {
        // [FIX] Use SideEffect/LaunchedEffect to track projection/rotation without breaking internal listeners
        LaunchedEffect(cameraPositionState.isMoving, cameraPositionState.position) {
             // Update projection and rotation whenever camera moves
             // We need access to the GoogleMap object?
             // Accessing map inside LaunchedEffect is tricky if it's not state.
             // But we have `onMapLoaded`? No.
             // Wait, without `MapEffect`, we don't have the `GoogleMap` instance easily unless we capture it?
        }
        
        // Wait, standard way is using `MapEffect` to capture map instance, but NOT setting listeners.
        // We can set projection in OnMapLoaded or use a snapshotFlow on the map object if exposed?
        // Actually, we can use `cameraPositionState.projection`? No, it doesn't expose projection.
        
        // Alternative: Use `MapEffect` to capture `map` instance into a state variable.
        var googleMapInstance by remember { mutableStateOf<com.google.android.gms.maps.GoogleMap?>(null) }
        
        MapEffect(Unit) { map ->
            googleMapInstance = map
        }
        
        // Now observe state changes and update projection from instance
        LaunchedEffect(cameraPositionState.isMoving) {
            val map = googleMapInstance ?: return@LaunchedEffect
            snapshotFlow { cameraPositionState.position }
                .collectLatest { 
                    mapProjection = map.projection
                    onRotationChange(it.bearing)
                }
        }
        
        // [FIX] Render Clustered Items (Filtered)
        visibleClusters.forEach { cluster ->
            val position = LatLng(cluster.latitude, cluster.longitude)
            val isSingle = cluster.count == 1
            val firstItem = cluster.items.firstOrNull()
            
            // Determine Icon
            val iconDescriptor = if (isSingle && firstItem != null) {
                // Formatting Single Item
                bitmapDescriptorFromVector(context, firstItem.getPinResId(), 40)
            } else {
                // Cluster Item
                // [FIX] Priority Logic: UserLocation > History > Server(Blue) > User(Green)
                var hasUserLocation = false
                var hasHistory = false
                var hasServerTodo = false // Blue
                var hasUserTodo = false   // Green
                
                cluster.items.forEach { 
                    when(it) {
                        is UnifiedItem.CurrentLocation -> hasUserLocation = true
                        is UnifiedItem.History -> hasHistory = true
                        is UnifiedItem.Todo -> {
                            if (it.item.source != "local") hasServerTodo = true
                            else hasUserTodo = true
                        }
                    }
                }
                
                val (resId, badgeColor) = when {
                    hasUserLocation -> com.example.alltodo.R.drawable.pin_current to android.graphics.Color.RED
                    hasHistory -> com.example.alltodo.R.drawable.pin_history to android.graphics.Color.RED
                    hasServerTodo -> com.example.alltodo.R.drawable.pin_receive_ready to android.graphics.Color.BLUE // Needs accurate Blue
                    else -> com.example.alltodo.R.drawable.pin_todo_ready to android.graphics.Color.parseColor("#00AA00") // Green
                }
                // [FIX] Use Cached Implementation from MapCommon.kt
                com.example.alltodo.ui.getCachedClusterBitmap(context, cluster.count, resId, badgeColor)
            }

            Marker(
                state = MarkerState(position = position),
                // [FIX] Adjust anchor for cluster (offset due to badge overhang)
                anchor = if (isSingle && firstItem != null) Offset(0.5f, 1.0f) else Offset(0.4f, 1.0f),
                icon = iconDescriptor,
                onClick = {
                    val point = mapProjection?.toScreenLocation(position)
                    if (point != null) {
                        if (isSingle && firstItem != null) {
                            onItemClickWithCoords(firstItem, point.x.toFloat(), point.y.toFloat())
                        } else {
                            onClusterClickWithCoords(cluster.items, point.x.toFloat(), point.y.toFloat())
                        }
                    }
                    true
                }
            )
        }
        
        // [REMOVED] Standalone Current Location Marker (Now handled in clusters)
    }

    // [FIX] Removed Internal Overlay
    } // Close Box
}


// [FIX] Safe Vector Drawable Loader with Scaling
fun bitmapDescriptorFromVector(
    context: android.content.Context,
    @androidx.annotation.DrawableRes vectorResId: Int,
    targetSizeDp: Int = 40 
): com.google.android.gms.maps.model.BitmapDescriptor? {
    return try {
        // [Optimization] Use Pre-loaded Bitmap from PinImageManager
        val cached = com.example.alltodo.ui.PinImageManager.getPinBitmap(vectorResId)
        if (cached != null) {
            return com.google.android.gms.maps.model.BitmapDescriptorFactory.fromBitmap(cached)
        }
        
        val vectorDrawable = androidx.core.content.ContextCompat.getDrawable(context, vectorResId) ?: return null
        
        val density = context.resources.displayMetrics.density
        val sizePx = (targetSizeDp * density).toInt()

        // Maintain aspect ratio if intrinsic dimensions exist
        val w = vectorDrawable.intrinsicWidth
        val h = vectorDrawable.intrinsicHeight
        
        val finalW: Int
        val finalH: Int
        
        if (w > 0 && h > 0) {
            val aspect = w.toFloat() / h.toFloat()
            if (w > h) {
                finalW = sizePx
                finalH = (sizePx / aspect).toInt()
            } else {
                finalH = sizePx
                finalW = (sizePx * aspect).toInt()
            }
        } else {
            finalW = sizePx
            finalH = sizePx
        }

        vectorDrawable.setBounds(0, 0, finalW, finalH)
        val bitmap = android.graphics.Bitmap.createBitmap(
            finalW,
            finalH,
            android.graphics.Bitmap.Config.ARGB_8888
        )
        val canvas = android.graphics.Canvas(bitmap)
        vectorDrawable.draw(canvas)
        com.google.android.gms.maps.model.BitmapDescriptorFactory.fromBitmap(bitmap)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

