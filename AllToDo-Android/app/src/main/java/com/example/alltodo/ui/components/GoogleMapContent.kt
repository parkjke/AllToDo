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
    onInitialAnimationDone: () -> Unit
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



    // 2. Launch Animation & History Mode Handler
    // Add currentLocation to keys to handle "No Pins -> Zoom 15" when location arrives
    // [FIX] Add isMapReady to prevent crash on re-entry (trying to animate before map loads)
    // 2. Launch Animation & History Mode Handler
    // 2. Launch Animation & History Mode Handler
    // 2. iPhone-like Launch Animation (Fit Bounds with Max Zoom 9)
    // 2. iPhone-like Launch Animation (Fit Bounds with Max Zoom 11)
    // [FIX] Loop prevention: animate once, never restart.
    // Depend on Unit so it doesn't restart on data change.
    // [FIX] Reactive Launch Animation (No Polling Loop)
    // [FIX] Reactive Launch Animation (Run Once)
    // Removed 'clusteredItems' from keys to prevent restart on update
    LaunchedEffect(isMapReady) {
        if (initialAnimationDone || !isMapReady) return@LaunchedEffect
        
        // Wait for valid data (snapshotFlow)
        // We wait for either clusteredItems to be populated OR a timeout?
        // Let's wait for clusteredItems to have content OR current location
        
        var validPoints: List<LatLng> = emptyList()
        
        // Wait until we have data or location
        // Use a simple loop with timeout or snapshotFlow
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < 5000) { // Max 5 sec wait
             val items = clusteredItems // Capture current state
             val loc = currentLocation
             
             val points = mutableListOf<LatLng>()
             val itemPoints = items.flatMap { it.items }
                .filter { (it is UnifiedItem.Todo || it is UnifiedItem.History) }
                .map { item -> LatLng(item.latitude, item.longitude) }
             points.addAll(itemPoints)
             
             if (loc != null && loc.latitude != 0.0) {
                 points.add(LatLng(loc.latitude, loc.longitude))
             }
             
             if (points.isNotEmpty()) {
                 validPoints = points
                 break
             }
             delay(500) // Poll every 500ms
        }
        
        if (validPoints.isNotEmpty()) {
             // Run Animation Sequence
             val boundsBuilder = LatLngBounds.builder()
             validPoints.forEach { boundsBuilder.include(it) }
             val bounds = boundsBuilder.build()
             val center = bounds.center

             val MIN_SPAN = 0.4 
             val ne = bounds.northeast
             val sw = bounds.southwest
             var latSpan = ne.latitude - sw.latitude
             var lngSpan = ne.longitude - sw.longitude
             
             if (latSpan < MIN_SPAN) latSpan = MIN_SPAN
             if (lngSpan < MIN_SPAN) lngSpan = MIN_SPAN
             
             val expandedBounds = LatLngBounds(
                 LatLng(center.latitude - latSpan / 2, center.longitude - lngSpan / 2),
                 LatLng(center.latitude + latSpan / 2, center.longitude + lngSpan / 2)
             )

             try {
                 cameraPositionState.animate(
                     CameraUpdateFactory.newLatLngBounds(expandedBounds, 100),
                     1500
                 )
                 delay(2000)
                 if (currentLocation != null) {
                      cameraPositionState.animate(
                          CameraUpdateFactory.newLatLngZoom(
                              LatLng(currentLocation.latitude, currentLocation.longitude), 
                              15f
                          ),
                          1000 
                      )
                 }
                 onInitialAnimationDone()
             } catch (e: Exception) { e.printStackTrace() }
        } else {
             // Fallback if no data found in 5 sec
             onInitialAnimationDone()
        }
    }

    // [FIX] Projection State
    var mapProjection by remember { mutableStateOf<com.google.android.gms.maps.Projection?>(null) }

    GoogleMap(
        modifier = modifier.fillMaxSize(),
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
        MapEffect(Unit) { map ->
            map.setOnCameraMoveListener {
                mapProjection = map.projection
                onRotationChange(map.cameraPosition.bearing)
            }
            map.setOnCameraIdleListener {
                mapProjection = map.projection
                onRotationChange(map.cameraPosition.bearing)
            }
            mapProjection = map.projection
            onRotationChange(map.cameraPosition.bearing) // Initial set
        }
        
        // [FIX] Render Clustered Items
        clusteredItems.forEach { cluster ->
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

