package kr.alltodo.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import kr.alltodo.ui.UnifiedItem
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
    clusteredItems: List<kr.alltodo.ui.TodoViewModel.PinClusterItem>,
    beforeLocation: android.location.Location,
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
    onInitialAnimationDone: () -> Unit,
    onResetAnimationDone: () -> Unit, // [NEW]
    onEnableClustering: () -> Unit,
    onFarItemsDetected: (Int) -> Unit = {},
    creatingTodoLocation: com.google.android.gms.maps.model.LatLng? = null,
    contentPaddingBottom: Int = 0 // px
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

    // [FIX] Dynamic Filter State
    var isDistanceFilterEnabled by remember { mutableStateOf(true) }
    
    // [FIX] State needed for MapBeginSequence
    var googleMapInstance by remember { mutableStateOf<com.google.android.gms.maps.GoogleMap?>(null) }
    var mapProjection by remember { mutableStateOf<com.google.android.gms.maps.Projection?>(null) }
    var currentSpanLon by remember { mutableStateOf(0) }
    var currentSpanLat by remember { mutableStateOf(0) }
    var moveLocationAlias by remember { mutableStateOf<kr.alltodo.utils.SmartLocationManager.IntLocation?>(null) }
    
    // [FIX] Filter Items
    val filteredItems = remember(clusteredItems, currentLocation, isDistanceFilterEnabled) {
         val items = clusteredItems
         val loc = currentLocation ?: android.location.Location("default").apply { latitude=37.5759; longitude=126.9768 }
         
         if (isDistanceFilterEnabled) {
             items.filter { item ->
                 val results = FloatArray(1)
                 android.location.Location.distanceBetween(loc.latitude, loc.longitude, item.latitude, item.longitude, results)
                 results[0] <= 500000f
             }
         } else {
             items // Show All
         }
    }
    
    // [FIX] Removed internal farItemMessage handling. Handled by MainScreen via callback.

    // [NEW] Unified Map Begin Logic (Centralized in MapBegin.kt)
    MapBeginSequence(
        isMapReady = isMapReady,
        initialAnimationDone = initialAnimationDone,
        beforeLocation = beforeLocation,
        currentLocation = currentLocation,
        clusteredItems = clusteredItems,
        onInitialAnimationDone = {
            // Set Initial Tethering Anchor
            currentLocation?.let { moveLocationAlias = kr.alltodo.utils.SmartLocationManager.toIntLocation(it) }
            onInitialAnimationDone()
        },
        onResetAnimationDone = onResetAnimationDone, // [NEW]
        onEnableClustering = onEnableClustering,
        onMove = { lat, lon, zoom, animate ->
            val update = CameraUpdateFactory.newLatLngZoom(LatLng(lat, lon), zoom)
            // [FIX] Release Stage 2 constraint if targeting higher zoom
            if (zoom > 15f) googleMapInstance?.resetMinMaxZoomPreference()
            
            if (animate) {
                cameraPositionState.animate(update, 1200)
            } else {
                cameraPositionState.move(update)
            }
        },
        onFitBounds = { points, padding, _ ->
            if (points.size == 1) {
                val p = points.first()
                cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(LatLng(p.first, p.second), 15f), 1000)
            } else if (points.isNotEmpty()) {
                val map = googleMapInstance
                val builder = LatLngBounds.Builder()
                points.forEach { builder.include(LatLng(it.first, it.second)) }
                val bounds = builder.build()
                
                // [FIX] Lock max zoom to 15.0 for Stage 2. 
                // Released in Stage 3 (onMove) or interaction.
                map?.setMaxZoomPreference(15.0f)
                cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(bounds, padding), 1000)
            }
        },
        onStop = {
            // Google Map animate can be interrupted by new move/animate calls
        }
    )

    // [NEW] Dynamic Padding & Re-centering Reaction
    LaunchedEffect(contentPaddingBottom, creatingTodoLocation) {
        if (contentPaddingBottom > 0 && creatingTodoLocation != null) {
            cameraPositionState.animate(com.google.android.gms.maps.CameraUpdateFactory.newLatLng(creatingTodoLocation), 500)
        }
    }

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
        onMapLoaded = onMapLoaded,
        contentPadding = PaddingValues(bottom = (contentPaddingBottom / context.resources.displayMetrics.density).dp)
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
        
        MapEffect(Unit) { map ->
            googleMapInstance = map
        }

        
        LaunchedEffect(cameraPositionState.isMoving) {
            val map = googleMapInstance ?: return@LaunchedEffect
            snapshotFlow { cameraPositionState.position }
                .collectLatest { 
                    mapProjection = map.projection
                    onRotationChange(it.bearing)
                    
                    // [NEW] Update Span (Distance) using Projection
                    val visibleRegion = map.projection.visibleRegion
                    val bounds = visibleRegion.latLngBounds
                    val dLon = bounds.northeast.longitude - bounds.southwest.longitude
                    val dLat = bounds.northeast.latitude - bounds.southwest.latitude
                    currentSpanLon = (Math.abs(dLon) * 100000).toInt()
                    currentSpanLat = (Math.abs(dLat) * 100000).toInt()
                }
        }
        
        // [NEW] Smart Tethering Logic
        LaunchedEffect(currentLocation, currentSpanLon, currentSpanLat, initialAnimationDone) {
             if (!initialAnimationDone) return@LaunchedEffect
             val loc = currentLocation ?: return@LaunchedEffect
             
             val userInt = kr.alltodo.utils.SmartLocationManager.toIntLocation(loc)
             
             // [NEW] Use persistent anchor instead of map center for Exploration Friendliness
             val anchor = moveLocationAlias ?: run {
                 moveLocationAlias = userInt
                 return@LaunchedEffect
             }
             
             if (kr.alltodo.utils.SmartLocationManager.needsCentering(userInt, anchor, currentSpanLon, currentSpanLat)) {
                 cameraPositionState.animate(
                     CameraUpdateFactory.newLatLng(LatLng(loc.latitude, loc.longitude)),
                     500 
                 )
                 moveLocationAlias = userInt // Update Anchor
             }
        }
        
        // [NEW] Show Creating Todo Pin (Green)
        if (creatingTodoLocation != null) {
            val icon = bitmapDescriptorFromVector(context, kr.alltodo.R.drawable.pin_todo_ready, 40)
            Marker(
                state = MarkerState(position = creatingTodoLocation),
                icon = icon,
                anchor = Offset(0.5f, 1.0f)
            )
        }
        
        // [FIX] Render Clustered Items (Filtered)
        filteredItems.forEach { cluster ->
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
                
                cluster.items.forEach { item ->
                    when(item) {
                        is UnifiedItem.CurrentLocation -> hasUserLocation = true
                        is UnifiedItem.History -> hasHistory = true
                        is UnifiedItem.Todo -> {
                            if (item.item.source != "local") hasServerTodo = true
                            else hasUserTodo = true
                        }
                    }
                }
                
                val (resId, badgeColor) = when {
                    hasUserLocation -> kr.alltodo.R.drawable.pin_current to android.graphics.Color.RED
                    hasHistory -> kr.alltodo.R.drawable.pin_history to android.graphics.Color.RED
                    hasServerTodo -> kr.alltodo.R.drawable.pin_receive_ready to android.graphics.Color.BLUE 
                    else -> kr.alltodo.R.drawable.pin_todo_ready to android.graphics.Color.parseColor("#00AA00") 
                }
                // [FIX] Use Cached Cluster Bitmap to prevent flickering & show Badge
                kr.alltodo.ui.getCachedClusterBitmap(context, cluster.count, resId, badgeColor)
            }
            
            // [FIX] Add Marker
            Marker(
                state = MarkerState(position = position),
                icon = iconDescriptor,
                // [FIX] Adjust anchor for cluster (offset due to badge overhang)
                anchor = if (isSingle && firstItem != null) Offset(0.5f, 1.0f) else Offset(0.4f, 1.0f),
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

// [NEW] Helper for Bounds
fun calculateVisibleBounds(region: com.google.android.gms.maps.model.VisibleRegion): LatLngBounds {
    val builder = LatLngBounds.builder()
    builder.include(region.nearLeft)
    builder.include(region.nearRight)
    builder.include(region.farLeft)
    builder.include(region.farRight)
    return builder.build()
}


// [FIX] Safe Vector Drawable Loader with Scaling
fun bitmapDescriptorFromVector(
    context: android.content.Context,
    @androidx.annotation.DrawableRes vectorResId: Int,
    targetSizeDp: Int = 40 
): com.google.android.gms.maps.model.BitmapDescriptor? {
    return try {
        // [Optimization] Use Pre-loaded Bitmap from PinImageManager
        val cached = kr.alltodo.ui.PinImageManager.getPinBitmap(vectorResId)
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

