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
import com.naver.maps.map.CameraUpdate
import com.naver.maps.map.MapView
import com.naver.maps.map.NaverMap
import com.naver.maps.map.overlay.Marker
import com.naver.maps.map.overlay.OverlayImage
import com.naver.maps.map.util.FusedLocationSource

@Composable
fun NaverMapContent(
    modifier: Modifier = Modifier,
    items: List<UnifiedItem>,
    currentLocation: android.location.Location?,
    onMapReady: (NaverMap) -> Unit = {},
    onItemClick: (UnifiedItem) -> Unit
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
    
    // Camera Move Logic (Follow User)
    LaunchedEffect(currentLocation, naverMap) {
        val map = naverMap ?: return@LaunchedEffect
        currentLocation?.let {
            // Only move if needed? For now, we follow like other maps
             val cameraUpdate = CameraUpdate.scrollTo(LatLng(it.latitude, it.longitude))
             map.moveCamera(cameraUpdate)
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { mapView },
        update = { view ->
            view.getMapAsync { map ->
                if (naverMap == null) {
                    naverMap = map
                    map.uiSettings.isLocationButtonEnabled = false
                    map.uiSettings.isZoomControlEnabled = false
                    onMapReady(map)
                }
            }
        }
    )

    // Marker Update Logic
    LaunchedEffect(naverMap, items) {
        val map = naverMap ?: return@LaunchedEffect
        
        // Clear old
        currentMarkers.forEach { it.map = null }
        currentMarkers.clear()
        
        // Add new
        items.forEach { item ->
            val position = when (item) {
                is UnifiedItem.Todo -> LatLng(item.item.latitude ?: 0.0, item.item.longitude ?: 0.0)
                is UnifiedItem.History -> LatLng(item.log.latitude, item.log.longitude)
                is UnifiedItem.CurrentLocation -> LatLng(item.lat, item.lon)
            }
            
            // Skip invalid positions
            if (position.latitude == 0.0 && position.longitude == 0.0) return@forEach

            val marker = Marker()
            marker.position = position
            marker.map = map
            
            // Color/Icon Logic
            // Color/Icon Logic
            marker.icon = OverlayImage.fromResource(item.getPinResId())
            
            if (item is UnifiedItem.Todo) {
                marker.captionText = item.item.text
            } else if (item is UnifiedItem.CurrentLocation) {
                marker.captionText = "Me"
            }
            
            marker.setOnClickListener {
                onItemClick(item)
                true
            }
            currentMarkers.add(marker)
        }
    }
}
