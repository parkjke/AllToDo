package com.example.alltodo.ui.components

import android.os.Bundle
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.alltodo.ui.UnifiedItem
import com.naver.maps.geometry.LatLng
import com.naver.maps.geometry.LatLngBounds
import com.naver.maps.map.CameraUpdate
import com.naver.maps.map.MapView
import com.naver.maps.map.NaverMap
import com.naver.maps.map.overlay.Marker
import com.naver.maps.map.overlay.OverlayImage
import kotlinx.coroutines.delay

@Composable
fun NaverMapContent(
    modifier: Modifier = Modifier,
    clusteredItems: List<com.example.alltodo.ui.TodoViewModel.PinClusterItem>,
    currentLocation: android.location.Location?,
    onMapReady: (NaverMap) -> Unit = {},
    onItemClickWithCoords: (UnifiedItem, Float, Float) -> Unit,
    onClusterClickWithCoords: (List<UnifiedItem>, Float, Float) -> Unit,
    onRotationChange: (Float) -> Unit = {},
    initialAnimationDone: Boolean,
    onInitialAnimationDone: () -> Unit,
    onFarItemsDetected: (Int) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember { MapView(context) }
    
    // Lifecycle
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> mapView.onCreate(Bundle())
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var naverMap by remember { mutableStateOf<NaverMap?>(null) }
    val currentMarkers = remember { mutableListOf<Marker>() }
    var isMapReady by remember { mutableStateOf(false) }

    // 500km Filter Logic
    val farThreshold = 500000f
    val visibleClusters = remember(clusteredItems, currentLocation) {
        if (currentLocation == null) clusteredItems
        else clusteredItems.filter { cluster ->
             val results = FloatArray(1)
             android.location.Location.distanceBetween(currentLocation.latitude, currentLocation.longitude, cluster.latitude, cluster.longitude, results)
             results[0] <= farThreshold
        }
    }

    // Launch Animation
    LaunchedEffect(isMapReady, initialAnimationDone) {
        if (initialAnimationDone || !isMapReady) return@LaunchedEffect
        val map = naverMap ?: return@LaunchedEffect
        
        // Wait for data
        val start = System.currentTimeMillis()
        var validBounds: LatLngBounds? = null
        
        while (System.currentTimeMillis() - start < 5000) {
            val items = visibleClusters
            val loc = currentLocation
            
            // Check Far Items
            val farCount = clusteredItems.sumOf { cluster ->
                 val results = FloatArray(1)
                 if (loc != null) {
                     android.location.Location.distanceBetween(loc.latitude, loc.longitude, cluster.latitude, cluster.longitude, results)
                     if (results[0] > farThreshold) cluster.count else 0
                 } else 0
            }
            if (farCount > 0) onFarItemsDetected(farCount)

            val builder = LatLngBounds.Builder()
            var hasPoint = false
            items.forEach { 
                if (it.latitude != 0.0 && it.longitude != 0.0) {
                    builder.include(LatLng(it.latitude, it.longitude))
                    hasPoint = true
                }
            }
            if (loc != null && loc.latitude != 0.0) {
                builder.include(LatLng(loc.latitude, loc.longitude))
                hasPoint = true
            }
            
            if (hasPoint) {
                validBounds = builder.build()
                break
            }
            delay(500)
        }
        
        if (validBounds != null) {
            try {
                // Step 1: Fit Bounds
                val update = CameraUpdate.fitBounds(validBounds!!, 100)
                update.animate(com.naver.maps.map.CameraAnimation.Easing, 1000)
                map.moveCamera(update)
                
                // Step 2: 3s Delay -> Zoom to User
                delay(3000) // Wait for animation + User viewing time (Total ~4s?)
                // Actually animation takes 1s, wait 3s = 4s total.
                // Google was: Animate(1s) -> Delay(3s).
                // Let's make it simple.
                
                if (currentLocation != null) {
                    val finalUpdate = CameraUpdate.scrollAndZoomTo(
                        LatLng(currentLocation.latitude, currentLocation.longitude), 18.0
                    )
                    finalUpdate.animate(com.naver.maps.map.CameraAnimation.Easing, 1500)
                    map.moveCamera(finalUpdate)
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
        onInitialAnimationDone()
    }


    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { mapView },
        update = { view ->
            view.getMapAsync { map ->
                if (naverMap == null) {
                    naverMap = map
                    isMapReady = true
                    map.uiSettings.isLocationButtonEnabled = false
                    map.uiSettings.isZoomControlEnabled = false
                    
                    map.addOnCameraChangeListener { _, _ ->
                        onRotationChange(map.cameraPosition.bearing.toFloat())
                    }
                    onMapReady(map)
                }
            }
        }
    )

    // Render Markers
    LaunchedEffect(naverMap, visibleClusters) { // Use visibleClusters
        val map = naverMap ?: return@LaunchedEffect
        
        currentMarkers.forEach { it.map = null }
        currentMarkers.clear()
        
        visibleClusters.forEach { cluster ->
            val marker = Marker()
            marker.position = LatLng(cluster.latitude, cluster.longitude)
            marker.map = map
            
            // Standard Pin Logic
            val isSingle = cluster.count == 1
            val firstItem = cluster.items.firstOrNull()
            
             // Determine Icon
            val (bitmap, anchor) = if (isSingle && firstItem != null) {
                val resId = firstItem.getPinResId()
                // Use Google Map helper? No, need Bitmap.
                // MapCommon.createClusterBitmapInternal is for clusters.
                // For single pin, we need to load Drawable as Bitmap.
                // GoogleMapContent used `bitmapDescriptorFromVector`.
                // Naver needs `Bitmap`.
                // PinImageManager gives Bitmap.
                val b = com.example.alltodo.ui.PinImageManager.getPinBitmap(resId) ?: 
                        com.example.alltodo.ui.createClusterBitmapInternal(context, 1, resId, android.graphics.Color.TRANSPARENT) // Fallback (Hack)
                // Actually, let's just use createClusterBitmapInternal with count=0 or similar if we want standard? 
                // Or best: `PinImageManager` returns raw bitmap.
                // Let's use `com.example.alltodo.ui.PinImageManager.getPinBitmap(resId)` and if null load logic.
                // But Naver expects Bitmap.
                b to android.graphics.PointF(0.5f, 1.0f)
            } else {
                // Cluster
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
                    hasUserLocation -> com.example.alltodo.R.drawable.pin_current to android.graphics.Color.RED
                    hasHistory -> com.example.alltodo.R.drawable.pin_history to android.graphics.Color.RED
                    hasServerTodo -> com.example.alltodo.R.drawable.pin_receive_ready to android.graphics.Color.BLUE
                    else -> com.example.alltodo.R.drawable.pin_todo_ready to android.graphics.Color.parseColor("#00AA00")
                }
                
                val b = com.example.alltodo.ui.createClusterBitmapInternal(context, cluster.count, resId, badgeColor)
                b to android.graphics.PointF(0.4f, 1.0f) // Adjusted anchor
            }
            
            if (bitmap != null) {
                marker.icon = OverlayImage.fromBitmap(bitmap)
                marker.anchor = anchor
            }
            
            marker.setOnClickListener {
                val projection = map.projection
                val screenPt = projection.toScreenLocation(marker.position)
                if (isSingle && firstItem != null) {
                    onItemClickWithCoords(firstItem, screenPt.x.toFloat(), screenPt.y.toFloat())
                } else {
                    onClusterClickWithCoords(cluster.items, screenPt.x.toFloat(), screenPt.y.toFloat())
                }
                true
            }
            currentMarkers.add(marker)
        }
    }
}
