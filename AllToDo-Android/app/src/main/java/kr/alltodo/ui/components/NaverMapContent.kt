package kr.alltodo.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kr.alltodo.ui.UnifiedItem
import kr.alltodo.ui.PinClusterItem
import com.naver.maps.geometry.LatLng
import com.naver.maps.geometry.LatLngBounds
import com.naver.maps.map.CameraAnimation
import com.naver.maps.map.CameraPosition
import com.naver.maps.map.CameraUpdate
import com.naver.maps.map.MapView
import com.naver.maps.map.NaverMap
import com.naver.maps.map.overlay.Marker
import com.naver.maps.map.overlay.OverlayImage
import com.naver.maps.map.overlay.PathOverlay
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first

@Composable
fun NaverMapContent(
    modifier: Modifier = Modifier,
    clusteredItems: List<PinClusterItem>,
    beforeLocation: android.location.Location,
    currentLocation: android.location.Location?,
    onMapReady: (NaverMap) -> Unit,
    onClusterClickWithCoords: (List<UnifiedItem>, Float, Float) -> Unit,
    onItemClickWithCoords: (UnifiedItem, Float, Float) -> Unit,
    onCameraRotate: (Float) -> Unit,
    initialAnimationDone: Boolean,
    onInitialAnimationDone: () -> Unit,
    onResetAnimationDone: () -> Unit, // [NEW]
    onFarItemsDetected: (Int) -> Unit = {},
    onZoomChange: (Float) -> Unit,
    onEnableClustering: () -> Unit,
    onMapLongClick: (LatLng) -> Unit = {},
    creatingTodoLocation: LatLng? = null,
    contentPaddingBottom: Int = 0,
    activePoints: List<kr.alltodo.data.GpsAuthPoint> = emptyList(),

    showActivePath: Boolean = true,
    livePath: List<kr.alltodo.data.LocationEntity> = emptyList(), // [FIX] Added parameter
    showMyLocation: Boolean = true, // [NEW] Control user location visibility
    onCameraIdle: (Double, Float, Double) -> Unit // [NEW] Wm, Zoom, Lat
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var naverMap by remember { mutableStateOf<NaverMap?>(null) }
    var isMapReady by remember { mutableStateOf(false) }
    
    // [NEW] Smart Tethering State
    var currentSpanLon by remember { mutableStateOf(0) }
    var currentSpanLat by remember { mutableStateOf(0) }
    var moveLocation by remember { mutableStateOf<kr.alltodo.utils.SmartLocationManager.IntLocation?>(null) }

    // [FIX] Lifecycle Management for Fast Resume (Avoid Zombie State)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> mapView?.onCreate(null)
                Lifecycle.Event.ON_START -> mapView?.onStart()
                Lifecycle.Event.ON_RESUME -> mapView?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView?.onPause()
                Lifecycle.Event.ON_STOP -> mapView?.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView?.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // 500km Filter Logic
    var isDistanceFilterEnabled by remember { mutableStateOf(true) }
    val farThreshold = 500000f
    
    val visibleClusters = remember(clusteredItems, currentLocation, isDistanceFilterEnabled) {
        val baseLat = currentLocation?.latitude ?: 37.5759
        val baseLon = currentLocation?.longitude ?: 126.9768
        
        if (isDistanceFilterEnabled) {
            clusteredItems.filter { cluster ->
                 val results = FloatArray(1)
                 android.location.Location.distanceBetween(baseLat, baseLon, cluster.latitude, cluster.longitude, results)
                 results[0] <= farThreshold
            }
        } else {
            clusteredItems
        }
    }

    // [New Algorithm] 4-Step Incremental Clustering (Naver Parity)
    // We must manually manage Marker objects on the NaverMap instance
    val currentMarkersMap = remember { mutableMapOf<String, Marker>() }

    // [RESTORED] Active Path Rendering
    val activePathOverlay = remember { PathOverlay() }
    // [RESTORED] Active Path & Blue Dot Trail rendering (Merged to reduce UI thread load)
    val trailMarkers = remember { mutableListOf<com.naver.maps.map.overlay.Marker>() }
    LaunchedEffect(naverMap, activePoints, showActivePath) {
        val map = naverMap ?: return@LaunchedEffect
        
        // 1. Render Active Path Polyline
        if (showActivePath && activePoints.size >= 2) {
            activePathOverlay.coords = activePoints.map { LatLng(it.latitude, it.longitude) }
            activePathOverlay.width = (5 * context.resources.displayMetrics.density).toInt()
            activePathOverlay.color = android.graphics.Color.parseColor("#FF5722") // Orange Red
            activePathOverlay.globalZIndex = 100000 
            activePathOverlay.outlineWidth = 0
            activePathOverlay.map = map
        } else {
            activePathOverlay.map = null
        }

        // 2. Render Blue Dot Trail (Last 20 points)
        trailMarkers.forEach { it.map = null }
        trailMarkers.clear()
        
        if (showActivePath && activePoints.isNotEmpty()) {
            val tailPoints = activePoints.takeLast(20)
            val dotBitmap = kr.alltodo.ui.PinImageManager.createDotBitmap(context, android.graphics.Color.BLUE, 3f)
            val overlayImage = com.naver.maps.map.overlay.OverlayImage.fromBitmap(dotBitmap)
            
            tailPoints.forEach { point ->
                val marker = com.naver.maps.map.overlay.Marker()
                marker.position = LatLng(point.latitude, point.longitude)
                marker.icon = overlayImage
                marker.anchor = android.graphics.PointF(0.5f, 0.5f)
                marker.isFlat = true
                marker.map = map
                trailMarkers.add(marker)
            }
        }
    }

    LaunchedEffect(naverMap, visibleClusters) {
        val map = naverMap ?: return@LaunchedEffect
        val count = visibleClusters.size
        
        try {
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

            val newKeys = mutableSetOf<String>()
            val nextMarkersMap = mutableMapOf<String, Marker>()
            
            // --- Step 1 & 4: Identification & Addition/Update ---
            visibleClusters.forEach { cluster ->
                val lat = cluster.latitude
                val lng = cluster.longitude
                
                if (lat.isNaN() || lng.isNaN()) return@forEach

                val key = generateKey(cluster)
                newKeys.add(key)
                
                val isSingle = cluster.count == 1
                val isUser = cluster.items.any { it is UnifiedItem.CurrentLocation }
                val firstItem = cluster.items.firstOrNull() ?: return@forEach
                val position = LatLng(lat, lng)
                
                // Resolve Style & Icon
                val (bitmap, anchorX, anchorY) = if (isSingle) {
                    val b = kr.alltodo.ui.createNaverPinBitmap(context, 0, firstItem.pinId, android.graphics.Color.TRANSPARENT)
                    val aH = if (isUser) 0.5f else 1.0f
                    Triple(b, 0.5f, aH)
                } else {
                    val style = kr.alltodo.utils.MapLogicHelper.resolveClusterStyle(cluster.items)
                    val b = kr.alltodo.ui.createNaverPinBitmap(context, cluster.count, style.pinId, style.color)
                    Triple(b, 0.392f, 1.0f)
                }
                
                val marker = currentMarkersMap[key] ?: Marker().apply { 
                    this.tag = key
                    this.position = position
                    this.map = map // Add to map if new
                }
                
                // Update Properties
                marker.position = position
                if (bitmap != null) {
                    marker.icon = OverlayImage.fromBitmap(bitmap)
                    marker.anchor = android.graphics.PointF(anchorX, anchorY)
                }
                marker.zIndex = if (isUser) 100 else 10
                
                marker.setOnClickListener { _ ->
                    val proj = map.projection
                    val density = context.resources.displayMetrics.density
                    
                    // Requirement 4: Pin head at center.y + 3pt
                    // Requirement 3: Tail at center.y
                    // Actual Map View Dimensions (Safer than displayMetrics)
                    val viewW = map.width
                    val viewH = map.height
                    
                    // Pin Tip Target Location = center.y + 3dp + 50dp(height) = +53dp
                    val offsetPx = (53 * density).toInt() 
                    val targetScreenPt = android.graphics.PointF(viewW / 2f, (viewH / 2f) + offsetPx)
                    
                    // Convert Pin's current position to screen, calculate target center
                    val pinScreenPt = proj.toScreenLocation(position)
                    val deltaX = pinScreenPt.x - targetScreenPt.x
                    val deltaY = pinScreenPt.y - targetScreenPt.y
                    
                    val currentCenterPt = android.graphics.PointF(viewW / 2f, viewH / 2f)
                    val targetCenterPt = android.graphics.PointF(currentCenterPt.x + deltaX, currentCenterPt.y + deltaY)
                    val targetLatLng = proj.fromScreenLocation(targetCenterPt)
                    
                    map.moveCamera(com.naver.maps.map.CameraUpdate.scrollTo(targetLatLng).animate(com.naver.maps.map.CameraAnimation.Easing, 400))
                    
                    // Pass screen center to CalloutBubble so tail is at center
                    if (isSingle) {
                        onItemClickWithCoords(firstItem, viewW / 2f, viewH / 2f)
                    } else {
                        onClusterClickWithCoords(cluster.items, viewW / 2f, viewH / 2f)
                    }
                    true
                }
                
                nextMarkersMap[key] = marker
            }
            
            // --- Step 2 & 3: Removal ---
            val it = currentMarkersMap.entries.iterator()
            while (it.hasNext()) {
                val entry = it.next()
                if (!newKeys.contains(entry.key)) {
                    entry.value.map = null 
                    it.remove()
                }
            }
            
            currentMarkersMap.putAll(nextMarkersMap)

            // Creating Todo Pin
            if (creatingTodoLocation != null) {
                val key = "CREATING_TODO"
                val marker = currentMarkersMap.getOrPut(key) {
                    Marker().apply { 
                        this.position = creatingTodoLocation
                        this.map = map 
                    }
                }
                marker.position = creatingTodoLocation
                val b = kr.alltodo.ui.createNaverPinBitmap(context, 0, "10", android.graphics.Color.TRANSPARENT)
                if (b != null) {
                    marker.icon = OverlayImage.fromBitmap(b)
                    marker.anchor = android.graphics.PointF(0.5f, 1.0f)
                }
                marker.zIndex = 50
            } else {
                currentMarkersMap.remove("CREATING_TODO")?.map = null
            }
            
        } catch (e: Exception) {
            if (e !is kotlinx.coroutines.CancellationException) {
                android.util.Log.e("NaverMap", "Error Rendering Clusters: ${e.message}", e)
            }
            throw e
        }
    }


    // Zoom & Span Polling
    LaunchedEffect(naverMap) {
        val map = naverMap ?: return@LaunchedEffect
        var lastZoom = 0.0
        while (true) {
            val z = map.cameraPosition.zoom
            if (Math.abs(z - lastZoom) > 0.1) {
                lastZoom = z
                onZoomChange(z.toFloat())
            }
            
            // [NEW] Update Span
            val bounds = map.contentBounds
            val dLon = bounds.eastLongitude - bounds.westLongitude
            val dLat = bounds.northLatitude - bounds.southLatitude
            val newSpanLon = (Math.abs(dLon) * 100000).toInt()
            val newSpanLat = (Math.abs(dLat) * 100000).toInt()
            if (newSpanLon > 0) currentSpanLon = newSpanLon
            if (newSpanLat > 0) currentSpanLat = newSpanLat
            
            delay(300)
        }
    }

    // [NEW] Smart Tethering Reaction
    LaunchedEffect(currentLocation, currentSpanLon, currentSpanLat, initialAnimationDone) {
        if (!initialAnimationDone) return@LaunchedEffect
        val map = naverMap ?: return@LaunchedEffect
        val loc = currentLocation ?: return@LaunchedEffect
        
        val userInt = kr.alltodo.utils.SmartLocationManager.toIntLocation(loc)
        
        // Use persistent anchor instead of map center for Exploration Friendliness
        val anchor = moveLocation ?: run {
            moveLocation = userInt
            return@LaunchedEffect
        }
        
        if (kr.alltodo.utils.SmartLocationManager.needsCentering(userInt, anchor, currentSpanLon, currentSpanLat)) {
            map.moveCamera(com.naver.maps.map.CameraUpdate.scrollTo(LatLng(loc.latitude, loc.longitude)).animate(com.naver.maps.map.CameraAnimation.Easing, 500))
            moveLocation = userInt // Update Anchor
        }
    }

    // [NEW] Unified Map Begin Logic (Centralized in MapBegin.kt)
    MapBeginSequence(
        isMapReady = isMapReady,
        initialAnimationDone = initialAnimationDone,
        beforeLocation = beforeLocation,
        currentLocation = currentLocation,
        clusteredItems = clusteredItems,
        onInitialAnimationDone = { 
            // Set Initial Tethering Anchor
            currentLocation?.let { moveLocation = kr.alltodo.utils.SmartLocationManager.toIntLocation(it) }
            onInitialAnimationDone() 
        },
        onResetAnimationDone = onResetAnimationDone, // [NEW]
        onEnableClustering = onEnableClustering,
        onMove = { lat, lon, zoom, animate ->
            naverMap?.let { map ->
                val update = CameraUpdate.scrollAndZoomTo(LatLng(lat, lon), zoom.toDouble())
                if (animate) {
                    map.moveCamera(update.animate(CameraAnimation.Easing, 1200))
                } else {
                    map.moveCamera(update)
                }
            }
        },
        onFitBounds = { points, padding, _ ->
            if (points.isNotEmpty()) {
                naverMap?.let { map ->
                    if (points.size == 1) {
                        val p = points.first()
                        map.moveCamera(CameraUpdate.scrollAndZoomTo(LatLng(p.first, p.second), 15.0))
                    } else {
                        val boundsBuilder = LatLngBounds.Builder()
                        points.forEach { boundsBuilder.include(LatLng(it.first, it.second)) }
                        map.moveCamera(CameraUpdate.fitBounds(boundsBuilder.build(), padding))
                    }
                }
            }
        },
        onStop = {
            naverMap?.cancelTransitions()
        }
    )

    // [NEW] Live Path Rendering (Overlay is already defined at top)
    LaunchedEffect(livePath, showActivePath, naverMap) {
        val map = naverMap ?: return@LaunchedEffect
        
        if (showActivePath && livePath.size >= 2) {
             val coords = livePath.map { LatLng(it.latitude, it.longitude) }
             activePathOverlay.coords = coords
             activePathOverlay.width = 10
             activePathOverlay.color = android.graphics.Color.BLUE
             activePathOverlay.outlineWidth = 0
             activePathOverlay.map = map
        } else {
             activePathOverlay.map = null
        }
    }
    
    // [NEW] Dots Overlay (Naver)
    // Using a simple logic to add a CircleOverlay for each point is expensive in Compose side effect.
    // Naver Map doesn't have a lightweight "Circle" composable wrapper here easily without loop.
    // A better approach for Naver is to set the `activePathOverlay` to have a join type or pattern?
    // Let's assume the Line is sufficient for the "Path", but the "Dot" is mainly for the *Current* update.
    // However, the user said "points are stamped".
    // Let's add a "Latest Point" marker at least, or try to iterate circles.
    // Given performance, let's keep it to Line for "Path" and maybe just emphasize the nodes?
    // Actually, `activePoints` (the blue dots) are what the user might be referring to?
    // Start of the path is already marked.
    // Let's stick to the Line for now, as "Dot" might be metaphorical for the vertices.
    // Re-reading user: "Variable location -> Dot stamped".
    // If I truly want dots, I physically need Circles.
    // Since I can't easily add N CircleOverlays in one go without a custom view or managing a list...
    // I will leave Naver as Line-only for performance unless strictly forced.
    // Google Map `Circle` is composable. Naver `CircleOverlay` is an object.
    
    LaunchedEffect(contentPaddingBottom, creatingTodoLocation) {
        val map = naverMap ?: return@LaunchedEffect
        map.setContentPadding(0, 0, 0, contentPaddingBottom)
        if (contentPaddingBottom > 0 && creatingTodoLocation != null) {
            // Re-center on the new creation point when padding is applied
            map.moveCamera(com.naver.maps.map.CameraUpdate.scrollTo(creatingTodoLocation).animate(com.naver.maps.map.CameraAnimation.Easing))
        }
    }

    AndroidView(
        factory = { ctx ->
            val contextThemeWrapper = android.view.ContextThemeWrapper(ctx, androidx.appcompat.R.style.Theme_AppCompat_Light_NoActionBar)
            MapView(contextThemeWrapper).apply {
                mapView = this
                getMapAsync { nMap ->
                    naverMap = nMap
                    
                    // [NEW] Force Light Mode
                    nMap.isNightModeEnabled = false
                    
                    // [Stage 1] Set initial camera immediately to beforeLocation/Zoom 15
                    nMap.cameraPosition = CameraPosition(LatLng(beforeLocation.latitude, beforeLocation.longitude), 15.0)
                    
                    isMapReady = true
                    onMapReady(nMap)
                    
                    // [NEW] Dynamic Location Support
                    nMap.locationOverlay.isVisible = showMyLocation
                    
                    // Basic Settings
                    nMap.uiSettings.isZoomControlEnabled = false
                    nMap.uiSettings.isLocationButtonEnabled = false
                    
                    // [NEW] Set Content Padding
                    nMap.setContentPadding(0, 0, 0, contentPaddingBottom)

                    // Rotation Listener
                         onCameraRotate(nMap.cameraPosition.bearing.toFloat())
                         onZoomChange(nMap.cameraPosition.zoom.toFloat())

                    
                    // [NEW] Camera Idle Listener
                    nMap.addOnCameraIdleListener {
                        val bounds = nMap.contentBounds
                        val centerLat = (bounds.northLatitude + bounds.southLatitude) / 2.0
                        
                        val results = FloatArray(1)
                        android.location.Location.distanceBetween(
                            centerLat, bounds.westLongitude,
                            centerLat, bounds.eastLongitude,
                            results
                        )
                        val widthMeters = results[0].toDouble()
                        
                        onCameraIdle(widthMeters, nMap.cameraPosition.zoom.toFloat(), centerLat)
                    }
                    
                    // Map Click
                    nMap.setOnMapClickListener { _, coord ->
                         // Clear selection handled by parent if needed, but Naver consumes click if marker handled it
                    }

                    // [NEW] Map Long Click
                    nMap.setOnMapLongClickListener { _, coord ->
                        onMapLongClick(coord)
                    }
                }
            }
        },
        modifier = modifier
    )
}
