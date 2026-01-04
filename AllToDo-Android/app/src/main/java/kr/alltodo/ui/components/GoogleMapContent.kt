package kr.alltodo.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity

import androidx.compose.ui.geometry.Offset
import kr.alltodo.ui.UnifiedItem
import kr.alltodo.ui.PinClusterItem
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.*
import com.google.android.gms.maps.model.MapStyleOptions // [NEW]
import kr.alltodo.R // [NEW]
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    clusteredItems: List<PinClusterItem>,
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
    contentPaddingBottom: Int = 0, // px
    activePoints: List<kr.alltodo.data.GpsAuthPoint> = emptyList(),

    showActivePath: Boolean = true,
    livePath: List<kr.alltodo.data.LocationEntity> = emptyList(), // [FIX] Added parameter
    onCameraIdle: (Double, Float, Double) -> Unit // [NEW] Wm, Zoom, Lat
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    
    androidx.compose.foundation.layout.BoxWithConstraints(modifier = modifier) {
        val viewWidthPx = with(density) { constraints.maxWidth }
        val viewHeightPx = with(density) { constraints.maxHeight }
        
        // 1. UI Settings (Disable Toolbar & Zoom)
    val uiSettings = remember {
        MapUiSettings(
            zoomControlsEnabled = false,
            compassEnabled = false,
            myLocationButtonEnabled = false,
            mapToolbarEnabled = false,
            scrollGesturesEnabled = true, // [FIX] Ensure gestures are enabled
            zoomGesturesEnabled = true    // [FIX] Ensure gestures are enabled
        )
    }
    
    // [DEBUG] Entry Log

    val properties = remember(androidx.compose.foundation.isSystemInDarkTheme()) {
        MapProperties(
            isMyLocationEnabled = false,
            mapStyleOptions = if (androidx.compose.foundation.isSystemInDarkTheme()) {
                MapStyleOptions.loadRawResourceStyle(context, R.raw.google_map_dark_style)
            } else {
                null
            }
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
                val builder = LatLngBounds.Builder()
                points.forEach { builder.include(LatLng(it.first, it.second)) }
                cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(builder.build(), padding), 1000)
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
        onMapLoaded = {
            onMapLoaded()
        },
        contentPadding = PaddingValues(bottom = (contentPaddingBottom / context.resources.displayMetrics.density).dp)
    ) {
        // [FIX] Use SideEffect/LaunchedEffect to track projection/rotation without breaking internal listeners
        
        MapEffect(Unit) { map ->
            googleMapInstance = map
        }

        
        LaunchedEffect(googleMapInstance, cameraPositionState.isMoving) {
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
                    
                    val newSpanLon = (Math.abs(dLon) * 100000).toInt()
                    val newSpanLat = (Math.abs(dLat) * 100000).toInt()
                    
                    // [FIX] Ensure we never use 0 to avoid jittery re-centers
                    if (newSpanLon > 0) currentSpanLon = newSpanLon
                    if (newSpanLat > 0) currentSpanLat = newSpanLat
                }

        }
        
        // [NEW] Camera Idle Detection & Reporting
        LaunchedEffect(cameraPositionState.isMoving) {
            if (!cameraPositionState.isMoving) {
                // Map stopped moving -> Idle
                val map = googleMapInstance
                if (map != null) {
                    val bounds = map.projection.visibleRegion.latLngBounds
                    val centerLat = bounds.center.latitude
                    
                    val results = FloatArray(1)
                    android.location.Location.distanceBetween(
                        centerLat, bounds.southwest.longitude,
                        centerLat, bounds.northeast.longitude,
                        results
                    )
                    val widthMeters = results[0].toDouble()
                    
                    // Trigger Logic
                    onCameraIdle(widthMeters, cameraPositionState.position.zoom, centerLat)
                }
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
        
        // [NEW] Show Creating Todo Pin (Static Bitmap 10)
        if (creatingTodoLocation != null) {
            val bitmap = kr.alltodo.ui.createGooglePinBitmap(context, 0, "10", android.graphics.Color.TRANSPARENT)
            Marker(
                state = MarkerState(position = creatingTodoLocation),
                icon = com.google.android.gms.maps.model.BitmapDescriptorFactory.fromBitmap(bitmap),
                anchor = Offset(0.5f, 1.0f)
            )
        }
        
        // [New Algorithm] 4-Step Incremental Clustering (Google Parity)
        fun generateKey(cluster: PinClusterItem): String {
            val itemIds = cluster.items.map { 
                when(it) {
                    is UnifiedItem.Todo -> "T_${it.item.todo_id}"
                    is UnifiedItem.History -> "H_${it.item.todo_id}"
                    is UnifiedItem.CurrentLocation -> "U"
                }
            }.sorted().joinToString("|")
            return "k_${cluster.count}_$itemIds"
        }

        // Maintain stable MarkerState objects to enable smooth updates
        val markerStates = remember { mutableStateMapOf<String, com.google.maps.android.compose.MarkerState>() }
        val currentKeys = mutableSetOf<String>()
        
        filteredItems.forEach { cluster ->
            val lat = cluster.latitude
            val lng = cluster.longitude
            if (lat.isNaN() || lng.isNaN()) return@forEach

            val stableId = generateKey(cluster)
            currentKeys.add(stableId)
            
            val isSingle = cluster.count == 1
            val isUser = cluster.items.any { it is UnifiedItem.CurrentLocation }
            val firstItem = cluster.items.firstOrNull()
            
            // Get or Create State
            val position = LatLng(lat, lng)
            val state = markerStates.getOrPut(stableId) {
                MarkerState(position = position)
            }
            
            // For smooth user movement, we allow MarkerState to handle the position update
            state.position = position
            
            // Determine Icon
            val style = kr.alltodo.utils.MapLogicHelper.resolveClusterStyle(cluster.items)
            val iconDescriptor = if (isSingle && firstItem != null) {
                val bitmap = kr.alltodo.ui.createGooglePinBitmap(context, 0, firstItem.pinId, android.graphics.Color.TRANSPARENT)
                com.google.android.gms.maps.model.BitmapDescriptorFactory.fromBitmap(bitmap)
            } else {
                kr.alltodo.ui.getCachedClusterBitmap(context, cluster.count, style.pinId, style.color)
            }
            
            key(stableId) {
                Marker(
                    state = state,
                    icon = iconDescriptor,
                    // [FIX] Adjust anchor for cluster (Unified Standard: 0.392f)
                    anchor = if (isSingle) Offset(0.5f, 1.0f) else Offset(0.392f, 1.0f),
                    onClick = {
                        val map = googleMapInstance ?: return@Marker false
                        val density = context.resources.displayMetrics.density
                        
                        // Requirement 4: Pin head at center.y + 3pt
                        // Pin Tip Target Location = center.y + 3dp + 50dp(height) = +53dp
                        val offsetPx = (53 * density).toInt() 
                        
                        // Use projection to accurately calculate relative move
                        val proj = map.projection
                        val currentPinLatLng = LatLng(cluster.latitude, cluster.longitude)
                        val pinScreenPt = proj.toScreenLocation(currentPinLatLng)
                        
                        // Target screen point for the pin tip (bottom)
                        val targetScreenPt = android.graphics.Point(viewWidthPx / 2, (viewHeightPx / 2) + offsetPx)
                        
                        val deltaX = pinScreenPt.x - targetScreenPt.x
                        val deltaY = pinScreenPt.y - targetScreenPt.y
                        
                        val currentCenterPt = android.graphics.Point(viewWidthPx / 2, viewHeightPx / 2)
                        val targetCenterPt = android.graphics.Point(currentCenterPt.x + deltaX, currentCenterPt.y + deltaY)
                        val targetLatLng = proj.fromScreenLocation(targetCenterPt)
                        
                        scope.launch {
                            cameraPositionState.animate(com.google.android.gms.maps.CameraUpdateFactory.newLatLng(targetLatLng), 400)
                        }
                        
                        // Pass screen center to CalloutBubble so tail is at center
                        if (isSingle && firstItem != null) {
                            onItemClickWithCoords(firstItem, viewWidthPx / 2f, viewHeightPx / 2f)
                        } else {
                            onClusterClickWithCoords(cluster.items, viewWidthPx / 2f, viewHeightPx / 2f)
                        }
                        true
                    },
                    zIndex = if (isUser) 100f else 1.0f
                )
            }
        }
        
        // Cleanup old states
        val it = markerStates.iterator()
        while (it.hasNext()) {
            val entry = it.next()
            if (!currentKeys.contains(entry.key)) {
                it.remove()
            }
        }

        
        // [NEW] Active Recording Path Polyline
        if (showActivePath && activePoints.size >= 2) {
            val polylinePoints = activePoints.map { com.google.android.gms.maps.model.LatLng(it.latitude, it.longitude) }
            val density = LocalDensity.current.density
            Polyline(
                points = polylinePoints,
                color = Color(0xFFFF5722), // Orange Red for active trail
                width = 8f * density, // Increased to 8dp for better visibility
                zIndex = 100f,
                jointType = com.google.android.gms.maps.model.JointType.ROUND,
                startCap = com.google.android.gms.maps.model.RoundCap(),
                endCap = com.google.android.gms.maps.model.RoundCap()
            )
        }
        
        // [NEW] Active Path Blue Dot Trail (Immediate Feedback)
        if (showActivePath && activePoints.isNotEmpty()) {
            val density = LocalDensity.current.density
            val dotRadius = 3.0 // meters
            
            activePoints.forEach { point -> // Show ALL points for full trail visibility
                Circle(
                    center = LatLng(point.latitude, point.longitude),
                    radius = dotRadius, 
                    fillColor = Color.Blue.copy(alpha = 0.6f),
                    strokeColor = Color.Transparent,
                    strokeWidth = 0f,
                    zIndex = 101f
                )
            }
        }

        // [REMOVED] Standalone Current Location Marker (Now handled in clusters)

        // [NEW] Live Path Rendering (Moved INSIDE GoogleMap scope)
        if (showActivePath && livePath.size >= 2) {
            val points = livePath.map { com.google.android.gms.maps.model.LatLng(it.latitude, it.longitude) }
            Polyline(
                points = points,
                color = Color.Blue,
                width = 10f,
                zIndex = 200f
            )
        }
        }
    }
}
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
        val cached = kr.alltodo.ui.PinImageManager.fetchStaticPin(context, vectorResId.toString())
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

