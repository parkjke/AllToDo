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
    clusteredItems: List<kr.alltodo.ui.TodoViewModel.PinClusterItem>,
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
    contentPaddingBottom: Int = 0 
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

    LaunchedEffect(naverMap, visibleClusters) {
        val map = naverMap ?: return@LaunchedEffect
        
        // Remove old markers
        currentMarkers.forEach { it.map = null }
        currentMarkers.clear()
        
        visibleClusters.forEach { cluster ->
            val isSingle = cluster.count == 1
            val firstItem = cluster.items.firstOrNull()
            
            val position = LatLng(cluster.latitude, cluster.longitude)
            val marker = Marker()
            marker.position = position
            
            // Icon Generation
            val (bitmap, anchorX, anchorY) = if (isSingle && firstItem != null) {
                val resId = firstItem.getPinResId()
                // Use createNaverPinBitmap (Scale 1.0)
                val b = kr.alltodo.ui.createNaverPinBitmap(context, 0, resId, android.graphics.Color.TRANSPARENT)
                Triple(b, 0.5f, 1.0f)
            } else {
                 var hasUserLocation = false
                 var hasHistory = false
                 var hasServerTodo = false
                 var hasUserTodo = false
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
                     hasUserLocation -> kr.alltodo.R.drawable.pin_current to android.graphics.Color.RED
                     hasUserTodo -> kr.alltodo.R.drawable.pin_todo_ready to android.graphics.Color.parseColor("#00AA00")
                     hasServerTodo -> kr.alltodo.R.drawable.pin_receive_ready to android.graphics.Color.BLUE
                     else -> kr.alltodo.R.drawable.pin_history to android.graphics.Color.RED
                 }
                 // Count > 0 renders badge
                 val b = kr.alltodo.ui.createNaverPinBitmap(context, cluster.count, resId, badgeColor)
                 // [FIX] Anchor for Cluster (matches Kakao/Google logic)
                 Triple(b, 0.33f, 1.0f)
            }
            
            if (bitmap != null) {
                marker.icon = OverlayImage.fromBitmap(bitmap)
                
                // [FIX] Anchor for Cluster (matches expanded PinImageManager canvas)
                // Single pin: standard 0.5, 1.0
                // Cluster: Pin is drawn at (0, padding) with size (pinW, pinH) inside (pinW+padding, pinH+padding)
                val finalAnchorX = if (isSingle) 0.5f else {
                    val density = context.resources.displayMetrics.density
                    val scale = 1.0f // [FIX] Matches createNaverPinBitmap (1.0)
                    val pinW = (40 * density * scale)
                    val badgeRadius = 10f * density * scale
                    val padding = (badgeRadius * 1.2f)
                    val canvasW = pinW + padding
                    (pinW / 2f) / canvasW
                }
                marker.anchor = android.graphics.PointF(finalAnchorX, 1.0f)
                
                // Click Listener
                marker.setOnClickListener { overlay ->
                    val proj = map.projection
                    val screenPt = proj.toScreenLocation(position)
                    
                    if (isSingle && firstItem != null) {
                         onItemClickWithCoords(firstItem, screenPt.x.toFloat(), screenPt.y.toFloat())
                    } else {
                         onClusterClickWithCoords(cluster.items, screenPt.x.toFloat(), screenPt.y.toFloat())
                    }
                    true
                }
                
                marker.map = map
                currentMarkers.add(marker)
            }
        }
        
        // [NEW] Show Creating Todo Pin (Green)
        if (creatingTodoLocation != null) {
            val marker = Marker()
            marker.position = creatingTodoLocation
            val b = kr.alltodo.ui.createNaverPinBitmap(context, 0, kr.alltodo.R.drawable.pin_todo_ready, android.graphics.Color.TRANSPARENT)
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
            currentSpanLon = (Math.abs(dLon) * 100000).toInt()
            currentSpanLat = (Math.abs(dLat) * 100000).toInt()
            
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
