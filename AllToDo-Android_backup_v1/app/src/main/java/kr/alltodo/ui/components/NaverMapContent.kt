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
import com.naver.maps.map.CameraUpdate
import com.naver.maps.map.MapView
import com.naver.maps.map.NaverMap
import com.naver.maps.map.overlay.Marker
import com.naver.maps.map.overlay.OverlayImage
import com.naver.maps.map.overlay.PathOverlay
import kotlinx.coroutines.delay

@Composable
fun NaverMapContent(
    modifier: Modifier = Modifier,
    clusteredItems: List<kr.alltodo.ui.TodoViewModel.PinClusterItem>,
    currentLocation: android.location.Location?,
    onMapReady: (NaverMap) -> Unit,
    onClusterClickWithCoords: (List<UnifiedItem>, Float, Float) -> Unit,
    onItemClickWithCoords: (UnifiedItem, Float, Float) -> Unit,
    onCameraRotate: (Float) -> Unit,
    initialAnimationDone: Boolean,
    onInitialAnimationDone: () -> Unit,
    onFarItemsDetected: (Int) -> Unit = {},
    onZoomChange: (Float) -> Unit,
    onEnableClustering: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var naverMap by remember { mutableStateOf<NaverMap?>(null) }
    var isMapReady by remember { mutableStateOf(false) }

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
                // Use createKakaoPinBitmap logic but adapted for Naver (or generic)
                // We'll reuse createKakaoPinBitmap as it returns a scaled Bitmap (Count=0 for single)
                val b = kr.alltodo.ui.createKakaoPinBitmap(context, 0, resId, android.graphics.Color.TRANSPARENT)
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
                     hasHistory -> kr.alltodo.R.drawable.pin_history to android.graphics.Color.RED
                     hasServerTodo -> kr.alltodo.R.drawable.pin_receive_ready to android.graphics.Color.BLUE
                     else -> kr.alltodo.R.drawable.pin_todo_ready to android.graphics.Color.parseColor("#00AA00")
                 }
                 // Count > 0 renders badge
                 val b = kr.alltodo.ui.createKakaoPinBitmap(context, cluster.count, resId, badgeColor)
                 // [FIX] Anchor for Cluster (matches Kakao/Google logic)
                 Triple(b, 0.33f, 1.0f)
            }
            
            if (bitmap != null) {
                marker.icon = OverlayImage.fromBitmap(bitmap)
                marker.anchor = android.graphics.PointF(anchorX, anchorY)
                
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
    }

    // Zoom Polling
    LaunchedEffect(naverMap) {
        val map = naverMap ?: return@LaunchedEffect
        var lastZoom = 0.0
        while (true) {
            val z = map.cameraPosition.zoom
            if (Math.abs(z - lastZoom) > 0.1) {
                lastZoom = z
                onZoomChange(z.toFloat())
            }
            delay(300)
        }
    }

    // Camera Init Logic
    val currentLocState = rememberUpdatedState(currentLocation)
    LaunchedEffect(isMapReady) {
        if (isMapReady) {
             // Force Enable Clustering after delay (Safety net)
             delay(3000)
             onEnableClustering()
        }
    }
    
    // Initial Animation Logic
    LaunchedEffect(isMapReady) {
        if (!isMapReady || initialAnimationDone) return@LaunchedEffect
        val map = naverMap ?: return@LaunchedEffect
        
        // Wait for data
        delay(500)
        
        val items = clusteredItems
        val userLoc = currentLocState.value
        
        val boundsBuilder = LatLngBounds.Builder()
        var validPoints = 0
        
        items.forEach { 
            if(it.latitude != 0.0) {
                boundsBuilder.include(LatLng(it.latitude, it.longitude))
                validPoints++
            }
        }
        
        if (userLoc != null && userLoc.latitude != 0.0) {
            boundsBuilder.include(LatLng(userLoc.latitude, userLoc.longitude))
            validPoints++
        }
        
        if (validPoints > 1) {
            val bounds = boundsBuilder.build()
            // Fit bounds with padding
            val cameraUpdate = CameraUpdate.fitBounds(bounds, 100)
            map.moveCamera(cameraUpdate)
            
            delay(3000)
        } else if (userLoc != null) {
            val cameraUpdate = CameraUpdate.scrollAndZoomTo(LatLng(userLoc.latitude, userLoc.longitude), 15.0)
            map.moveCamera(cameraUpdate)
             delay(1000)
        }
        
        onInitialAnimationDone()
    }

    AndroidView(
        factory = { ctx ->
            val contextThemeWrapper = android.view.ContextThemeWrapper(ctx, androidx.appcompat.R.style.Theme_AppCompat_Light_NoActionBar)
            MapView(contextThemeWrapper).apply {
                mapView = this
                getMapAsync { nMap ->
                    naverMap = nMap
                    isMapReady = true
                    onMapReady(nMap)
                    
                    // Basic Settings
                    nMap.uiSettings.isZoomControlEnabled = false
                    nMap.uiSettings.isLocationButtonEnabled = false
                    
                    // Rotation Listener
                    nMap.addOnCameraChangeListener { reason, animated ->
                         onCameraRotate(nMap.cameraPosition.bearing.toFloat())
                    }
                    
                    // Map Click
                    nMap.setOnMapClickListener { _, coord ->
                         // Clear selection handled by parent if needed, but Naver consumes click if marker handled it
                    }
                }
            }
        },
        modifier = modifier
    )
}
