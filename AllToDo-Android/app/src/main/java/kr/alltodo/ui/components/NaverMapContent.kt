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
    showActivePath: Boolean = true
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

    // [FIX] Manage Markers (Recomposition Optimization)
    // We must manually manage Marker objects on the NaverMap instance
    val currentMarkers = remember { mutableListOf<Marker>() }

    // [NEW] Active Path Rendering
    val activePathOverlay = remember { PathOverlay() }
    LaunchedEffect(naverMap, activePoints, showActivePath) {
        val map = naverMap ?: return@LaunchedEffect
        if (showActivePath && activePoints.size >= 2) {
            activePathOverlay.coords = activePoints.map { LatLng(it.latitude, it.longitude) }
            activePathOverlay.width = (2.5 * context.resources.displayMetrics.density).toInt() // Thinned from 4

            activePathOverlay.color = android.graphics.Color.parseColor("#FF5722") // Orange Red

            activePathOverlay.outlineWidth = 0
            activePathOverlay.map = map
        } else {
            activePathOverlay.map = null
        }
    }

    LaunchedEffect(naverMap, visibleClusters) {
        val map = naverMap ?: return@LaunchedEffect
        
        // [VISUAL DIFFING ALGORITHM]
        // 1. Identify User Pin in New Data
        var newUserMarkerData: Triple<LatLng, com.naver.maps.map.overlay.OverlayImage, android.graphics.PointF>? = null
        var newUserItems: List<UnifiedItem>? = null
        var newUserClusterIndex = -1
        
        visibleClusters.forEachIndexed { index, cluster ->
            val isUserFn = { item: UnifiedItem -> item is UnifiedItem.CurrentLocation }
            if (cluster.items.any(isUserFn)) {
                // This cluster contains User Location
                newUserClusterIndex = index
                newUserItems = cluster.items
                
                // Resolve Style
                val isSingle = cluster.count == 1
                val firstItem = cluster.items.first()
                val position = LatLng(cluster.latitude, cluster.longitude)
                
                // (Logic copied from previous implementation for style resolution)
                // (Logic copied from previous implementation for style resolution)
                val (bitmap, anchorX, anchorY) = if (isSingle) { // User Single
                     val shieldId = kr.alltodo.ui.PinImageManager.getResourceId(context, firstItem.shieldName)
                     val markId = kr.alltodo.ui.PinImageManager.getResourceId(context, firstItem.markName)
                     Triple(kr.alltodo.ui.createNaverPinBitmap(context, 0, shieldId, markId, android.graphics.Color.TRANSPARENT), 0.5f, 0.5f) 
                } else { // User Cluster
                     val style = kr.alltodo.utils.MapLogicHelper.resolveClusterStyle(cluster.items)
                     val shieldId = kr.alltodo.ui.PinImageManager.getResourceId(context, style.shieldName)
                     val markId = kr.alltodo.ui.PinImageManager.getResourceId(context, style.markName)
                     
                     val b = kr.alltodo.ui.createNaverPinBitmap(context, cluster.count, shieldId, markId, style.color)
                     // Cluster anchor logic
                     val density = context.resources.displayMetrics.density
                     val scale = 1.0f 
                     val pinW = (40 * density * scale)
                     val badgeRadius = 10f * density * scale
                     val padding = (badgeRadius * 1.2f)
                     val canvasW = pinW + padding
                     val finalAnchorX = (pinW / 2f) / canvasW
                     Triple(b, finalAnchorX, 1.0f)
                }
                
                if (bitmap != null) {
                    newUserMarkerData = Triple(position, OverlayImage.fromBitmap(bitmap), android.graphics.PointF(anchorX, anchorY))
                }
            }
        }
        
        // 2. Find Existing User Marker to Reuse
        var reusedMarker: Marker? = null
        val oldUserMarker = currentMarkers.find { it.tag == "UserPin" }
        
        if (newUserMarkerData != null && oldUserMarker != null) {
            // REUSE
            reusedMarker = oldUserMarker
            val (pos, icon, anchor) = newUserMarkerData!!
            
            oldUserMarker.position = pos
            oldUserMarker.icon = icon
            oldUserMarker.anchor = anchor
            oldUserMarker.zIndex = 100 // Keep user on top
            
            // Update Click Listener with new Items
            oldUserMarker.setOnClickListener { _ ->
                val proj = map.projection
                val screenPt = proj.toScreenLocation(pos)
                if (newUserItems!!.size == 1) {
                     onItemClickWithCoords(newUserItems!![0], screenPt.x.toFloat(), screenPt.y.toFloat())
                } else {
                     onClusterClickWithCoords(newUserItems!!, screenPt.x.toFloat(), screenPt.y.toFloat())
                }
                true
            }
        }
        
        // 3. Prepare New Marker List
        val newMarkersList = mutableListOf<Marker>()
        if (reusedMarker != null) newMarkersList.add(reusedMarker)
        
        // 4. Create New Markers (Skip User if Reused)
        visibleClusters.forEachIndexed { index, cluster ->
            if (index == newUserClusterIndex && reusedMarker != null) return@forEachIndexed
            
            val isSingle = cluster.count == 1
            val firstItem = cluster.items.firstOrNull() ?: return@forEachIndexed
            val position = LatLng(cluster.latitude, cluster.longitude)
            
            val marker = Marker()
            marker.position = position
            
            // Mark as User Pin if applicable (for future reuse)
            val isUserCluster = index == newUserClusterIndex
            if (isUserCluster) marker.tag = "UserPin"
            
            val (bitmap, anchorX, anchorY) = if (isSingle) {
                val shieldId = kr.alltodo.ui.PinImageManager.getResourceId(context, firstItem.shieldName)
                val markId = kr.alltodo.ui.PinImageManager.getResourceId(context, firstItem.markName)
                val b = kr.alltodo.ui.createNaverPinBitmap(context, 0, shieldId, markId, android.graphics.Color.TRANSPARENT)
                // [FIX] Current Location Single Pin center is 0.5, 0.5. Others are 0.5, 1.0
                val aH = if (firstItem is UnifiedItem.CurrentLocation) 0.5f else 1.0f
                Triple(b, 0.5f, aH)
            } else {
                 val style = kr.alltodo.utils.MapLogicHelper.resolveClusterStyle(cluster.items)
                 val shieldId = kr.alltodo.ui.PinImageManager.getResourceId(context, style.shieldName)
                 val markId = kr.alltodo.ui.PinImageManager.getResourceId(context, style.markName)
                 val b = kr.alltodo.ui.createNaverPinBitmap(context, cluster.count, shieldId, markId, style.color)
                 
                 val density = context.resources.displayMetrics.density
                 val scale = 1.0f
                 val pinW = (40 * density * scale)
                 val badgeRadius = 10f * density * scale
                 val padding = (badgeRadius * 1.2f)
                 val canvasW = pinW + padding
                 val finalAnchorX = (pinW / 2f) / canvasW
                 
                 Triple(b, finalAnchorX, 1.0f)
            }
            
            if (bitmap != null) {
                marker.icon = OverlayImage.fromBitmap(bitmap)
                marker.anchor = android.graphics.PointF(anchorX, anchorY)
                marker.zIndex = if (isUserCluster) 100 else 10
                
                marker.setOnClickListener { _ ->
                    val proj = map.projection
                    val screenPt = proj.toScreenLocation(position)
                    if (isSingle) {
                         onItemClickWithCoords(firstItem, screenPt.x.toFloat(), screenPt.y.toFloat())
                    } else {
                         onClusterClickWithCoords(cluster.items, screenPt.x.toFloat(), screenPt.y.toFloat())
                    }
                    true
                }
                
                marker.map = map
                newMarkersList.add(marker)
            }
        }
        
        // 5. Cleanup Old Markers
        currentMarkers.forEach { old ->
            if (old != reusedMarker) {
                old.map = null
            }
        }
        currentMarkers.clear()
        currentMarkers.addAll(newMarkersList)
        
        // [NEW] Show Creating Todo Pin (Green)
        if (creatingTodoLocation != null) {
            val marker = Marker()
            marker.position = creatingTodoLocation
            val shieldId = kr.alltodo.ui.PinImageManager.getResourceId(context, "pin_shield_1x")
            val markId = kr.alltodo.ui.PinImageManager.getResourceId(context, "pin_mark_10")
            val b = kr.alltodo.ui.createNaverPinBitmap(context, 0, shieldId, markId, android.graphics.Color.TRANSPARENT)
            if (b != null) {
                marker.icon = OverlayImage.fromBitmap(b)
                marker.anchor = android.graphics.PointF(0.5f, 1.0f)
                marker.map = map
                currentMarkers.add(marker)
            }
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
                // [FIX] Release Stage 2 constraint if we are targeting a higher zoom (Stage 3)
                if (zoom > 15f) map.maxZoom = 21.0
                
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
                        val bounds = boundsBuilder.build()
                        
                        // [FIX] Lock max zoom to 15.0 for Stage 2. 
                        // It will be released in Stage 3 (onMove) or when user interacts.
                        map.maxZoom = 15.0
                        map.moveCamera(CameraUpdate.fitBounds(bounds, padding))
                    }
                }
            }
        },
        onStop = {
            naverMap?.cancelTransitions()
        }
    )

    // [NEW] Dynamic Padding & Re-centering Reaction
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
                    
                    // [Stage 1] Set initial camera immediately to beforeLocation/Zoom 15
                    nMap.cameraPosition = CameraPosition(LatLng(beforeLocation.latitude, beforeLocation.longitude), 15.0)
                    
                    isMapReady = true
                    onMapReady(nMap)
                    
                    // Basic Settings
                    nMap.uiSettings.isZoomControlEnabled = false
                    nMap.uiSettings.isLocationButtonEnabled = false
                    
                    // [NEW] Set Content Padding
                    nMap.setContentPadding(0, 0, 0, contentPaddingBottom)

                    // Rotation Listener
                    nMap.addOnCameraChangeListener { reason, animated ->
                         onCameraRotate(nMap.cameraPosition.bearing.toFloat())
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
