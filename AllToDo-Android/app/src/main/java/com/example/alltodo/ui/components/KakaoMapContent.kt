package com.example.alltodo.ui.components

import android.graphics.Bitmap
import android.util.Log
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.alltodo.ui.UnifiedItem
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView
import com.kakao.vectormap.camera.CameraAnimation
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.label.LabelLayerOptions
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelStyle
import com.kakao.vectormap.label.LabelStyles
import kotlinx.coroutines.delay

@Composable
fun KakaoMapContent(
    modifier: Modifier = Modifier,
    isSdkInitialized: Boolean,
    clusteredItems: List<com.example.alltodo.ui.TodoViewModel.PinClusterItem>,
    currentLocation: android.location.Location?,
    onMapReady: (KakaoMap) -> Unit,
    onClusterClickWithCoords: (List<UnifiedItem>, Float, Float) -> Unit,
    onItemClickWithCoords: (UnifiedItem, Float, Float) -> Unit,
    onCameraRotate: (Float) -> Unit,
    initialAnimationDone: Boolean,
    onInitialAnimationDone: () -> Unit,
    onFarItemsDetected: (Int) -> Unit = {}
) {
    val context = LocalContext.current
    var kakaoMap by remember { mutableStateOf<KakaoMap?>(null) }
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
        val map = kakaoMap ?: return@LaunchedEffect
        
        // Wait for data
        val start = System.currentTimeMillis()
        var validCenter: LatLng? = null
        
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

            // Calculate Center of Bounds (Simple Average)
            var latSum = 0.0
            var lonSum = 0.0
            var count = 0
            
            items.forEach { 
                if (it.latitude != 0.0 && it.longitude != 0.0) {
                    latSum += it.latitude
                    lonSum += it.longitude
                    count++
                }
            }
            if (loc != null && loc.latitude != 0.0) {
                latSum += loc.latitude
                lonSum += loc.longitude
                count++
            }
            
            if (count > 0) {
                validCenter = LatLng.from(latSum / count, lonSum / count)
                break
            }
            delay(500)
        }
        
        if (validCenter != null) {
            try {
                // Step 1: Whole View (Zoom 10 around Center)
                val update = CameraUpdateFactory.newCenterPosition(validCenter, 10)
                map.moveCamera(update, CameraAnimation.from(1000, true, true))
                
                // Step 2: 3s Delay -> Zoom to User
                delay(3000)
                
                if (currentLocation != null) {
                    val finalUpdate = CameraUpdateFactory.newCenterPosition(
                        LatLng.from(currentLocation.latitude, currentLocation.longitude), 18
                    )
                    map.moveCamera(finalUpdate, CameraAnimation.from(1500, true, true))
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
        onInitialAnimationDone()
    }
    
    // Rendering Logic
    LaunchedEffect(kakaoMap, visibleClusters) {
        val map = kakaoMap ?: return@LaunchedEffect
        val labelManager = map.labelManager ?: return@LaunchedEffect
        val layer = labelManager.getLayer("mainLayer") ?: labelManager.addLayer(LabelLayerOptions.from("mainLayer"))
        layer?.removeAll()
        
        visibleClusters.forEach { cluster ->
             // Standard Pin Logic
            val isSingle = cluster.count == 1
            val firstItem = cluster.items.firstOrNull()
            
             // Determine Icon & Style
            val styleId = "cluster_${cluster.count}_${cluster.latitude}" // Unique ID
            var styles = labelManager.getLabelStyles(styleId)
            
            if (styles == null) {
                val (bitmap, anchorX, anchorY) = if (isSingle && firstItem != null) {
                    val resId = firstItem.getPinResId()
                    val b = com.example.alltodo.ui.PinImageManager.getPinBitmap(resId) ?: 
                            com.example.alltodo.ui.createClusterBitmapInternal(context, 1, resId, android.graphics.Color.TRANSPARENT)
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
                        hasUserLocation -> com.example.alltodo.R.drawable.pin_current to android.graphics.Color.RED
                        hasHistory -> com.example.alltodo.R.drawable.pin_history to android.graphics.Color.RED
                        hasServerTodo -> com.example.alltodo.R.drawable.pin_receive_ready to android.graphics.Color.BLUE
                        else -> com.example.alltodo.R.drawable.pin_todo_ready to android.graphics.Color.parseColor("#00AA00")
                    }
                    val b = com.example.alltodo.ui.createClusterBitmapInternal(context, cluster.count, resId, badgeColor)
                    Triple(b, 0.4f, 1.0f)
                }
                
                if (bitmap != null) {
                    styles = labelManager.addLabelStyles(LabelStyles.from(styleId, LabelStyle.from(bitmap).setAnchorPoint(anchorX, anchorY)))
                }
            }
            
            if (styles != null) {
                 val options = LabelOptions.from(LatLng.from(cluster.latitude, cluster.longitude))
                        .setStyles(styles)
                        .setClickable(true)
                 
                 val label = layer?.addLabel(options)
                 
                 // How to handle click? 
                 // KakaoMap Label Click Listener is Global (set on Map).
                 // Logic must be inside AndroidView 'start' block.
                 // We need to map Label -> Cluster.
                 // Storing map in Tag? Label doesn't support SetTag easily.
                 // We can use Position matching or global mapping.
                 // Step 1311 used "Find by Position". I will stick to that or use External Map.
            }
        }
    }

    if (isSdkInitialized) {
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    start(object : MapLifeCycleCallback() {
                        override fun onMapDestroy() {}
                        override fun onMapError(e: Exception?) {}
                    }, object : KakaoMapReadyCallback() {
                        override fun onMapReady(map: KakaoMap) {
                            kakaoMap = map
                            isMapReady = true
                            onMapReady(map) // Call parent

                            // Set Global Listener
                            map.setOnLabelClickListener { _, _, label ->
                                val pos = label.position
                                // Find Cluster by Position (Approx)
                                val clicked = visibleClusters.find { 
                                    Math.abs(it.latitude - pos.latitude) < 0.0001 && Math.abs(it.longitude - pos.longitude) < 0.0001
                                }
                                
                                if (clicked != null) {
                                    val screenPt = map.toScreenPoint(pos)
                                    val scrollX = if (screenPt != null) screenPt.x.toFloat() else 0f
                                    val scrollY = if (screenPt != null) screenPt.y.toFloat() else 0f
                                    
                                    if (clicked.count == 1 && clicked.items.isNotEmpty()) {
                                         onItemClickWithCoords(clicked.items.first(), scrollX, scrollY)
                                    } else {
                                         onClusterClickWithCoords(clicked.items, scrollX, scrollY)
                                    }
                                }
                                true
                            }
                            
                            map.addOnCameraChangeListener { _, _ ->
                                onCameraRotate(map.cameraPosition.bearing.toFloat())
                            }
                        }
                        override fun getPosition(): LatLng {
                             return LatLng.from(37.5665, 126.9780)
                        }
                        override fun getZoomLevel(): Int {
                             return 15
                        }
                    })
                }
            },
            modifier = modifier
        )
    } else {
        Box(modifier.background(androidx.compose.ui.graphics.Color.White), contentAlignment = Alignment.Center) {
            Text("Initializing Map...")
        }
    }
}
