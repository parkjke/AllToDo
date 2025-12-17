package com.example.alltodo.ui.components

import android.graphics.Bitmap
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
    // [FIX] Capture callback to avoid name shadowing in KakaoMapReadyCallback
    val activeOnMapReady = onMapReady
    
    // [FIX] Capture latest location for Animation Coroutine
    val currentLocState = rememberUpdatedState(currentLocation)
    
    val context = LocalContext.current
    var kakaoMap by remember { mutableStateOf<KakaoMap?>(null) }
    var isMapReady by remember { mutableStateOf(false) }
    
    // [FIX] Local Guard: Ensure animation runs at least once for THIS composition
    var hasLocalAnimationRun by remember { mutableStateOf(false) }

    // 500km Filter Logic
    var isDistanceFilterEnabled by remember { mutableStateOf(true) } // [FIX] Dynamic Filter State
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
            clusteredItems // Show ALL when filter disabled
        }
    }
    
    // [FIX] Capture latest clusters for LaunchedEffect (Since list is immutable)
    val latestVisibleClusters = rememberUpdatedState(visibleClusters)

    // Trigger Launch Animation when map is ready and not done yet
    if (isMapReady) {
         // Log removed
    }

    // [FIX] Changed Key to isMapReady to prevent cancellation when initialAnimationDone changes mid-flight
    LaunchedEffect(isMapReady) {
        // [FIX] Run if Global is False OR Local hasn't run yet (Force one run per load)
        // Check local flag again to prevent duplicate run
        if (hasLocalAnimationRun) return@LaunchedEffect
        
        // Wait until map is truly ready
        if (!isMapReady) return@LaunchedEffect
        
        // If global is already done AND local hasn't run, we still run it once (Local Guard)
        // logic: shouldRun = !initialAnimationDone || !hasLocalAnimationRun
        // implicit: we are here because isMapReady is true
        
        val map = kakaoMap ?: return@LaunchedEffect
        
        // Now we commit to running
        hasLocalAnimationRun = true
        
        // [FIX] Anti-Dalian: Move to known location immediately just in case data takes time
        val knownLoc = currentLocState.value
        
        if (knownLoc != null && knownLoc.latitude != 0.0) {
             try {
                 map.moveCamera(CameraUpdateFactory.newCenterPosition(
                     com.kakao.vectormap.LatLng.from(knownLoc.latitude, knownLoc.longitude), 14
                 ))
             } catch(e: Exception) {}
        } else {
             // [FIX] If location is null, move to Default (Gwanghwamun) to avoid Dalian
             try {
                 map.moveCamera(CameraUpdateFactory.newCenterPosition(
                     com.kakao.vectormap.LatLng.from(37.5759, 126.9768), 15
                 ))
             } catch(e: Exception) {}
        }
        
        // [MapStep 1] Map Initialized
        // [MapStep 1] Map Initialized
        android.util.Log.e("MapStep", "1. Map Initialized")
        System.out.println(">>> MapStep 1. Map Initialized")

        // Data Polling (Max 3s)
        val start = System.currentTimeMillis()
        var hasValidData = false
        
        while (System.currentTimeMillis() - start < 3000) {
            val items = latestVisibleClusters.value
             if (items.isNotEmpty()) {
                 hasValidData = true
                 // [MapStep 2] All Pins Loaded
                  // [MapStep 2] All Pins Loaded
                  android.util.Log.e("MapStep", "2. Data Loaded (Count=${items.size})")
                  System.out.println(">>> MapStep 2. Data Loaded (Count=${items.size})")
                 break
             }
             delay(200)
        }
        
        // Re-read latest items
        val visibleClusters = latestVisibleClusters.value
        
        if (!hasValidData) android.util.Log.e("MapStep", "2. Data Load Timeout (Empty)")
        
        try {
            // Step 1: Center + Zoom 14 (Instead of FitBounds)
            // Calculate Min/Max
            var minLat = 90.0; var maxLat = -90.0
            var minLon = 180.0; var maxLon = -180.0
            var validPoints = 0
            
            // Add visible items
            visibleClusters.forEach {
                if (it.latitude != 0.0 && it.longitude != 0.0) {
                    if (it.latitude < minLat) minLat = it.latitude
                    if (it.latitude > maxLat) maxLat = it.latitude
                    if (it.longitude < minLon) minLon = it.longitude
                    if (it.longitude > maxLon) maxLon = it.longitude
                    validPoints++
                }
            }
            // Add User Location
            val loc = currentLocState.value
            if (loc != null && loc.latitude != 0.0) {
                if (loc.latitude < minLat) minLat = loc.latitude
                if (loc.latitude > maxLat) maxLat = loc.latitude
                if (loc.longitude < minLon) minLon = loc.longitude
                if (loc.longitude > maxLon) maxLon = loc.longitude
                validPoints++
            }
                    
            // [MapStep 3] Calculation Done (Filter 500km + Add Current Loc)
            // [MapStep 3] Calculation Done (Filter 500km + Add Current Loc)
            android.util.Log.e("MapStep", "3. Filtered & Calculated (ValidPoints=$validPoints)")
            System.out.println(">>> MapStep 3. Filtered & Calculated (ValidPoints=$validPoints)")

            if (validPoints > 0) {
                 // [MapStep 4] Change Zoom to Show All
                 // [MapStep 4] Change Zoom to Show All
                 android.util.Log.e("MapStep", "4. FitBounds (Show All)")
                 System.out.println(">>> MapStep 4. FitBounds (Show All)")
                // [FIX] Restore FitBounds with Min Span (0.05)
                val safeBounds = com.example.alltodo.utils.SmartLocationManager.ensureMinSpan(
                    com.google.android.gms.maps.model.LatLngBounds(
                        com.google.android.gms.maps.model.LatLng(minLat, minLon),
                        com.google.android.gms.maps.model.LatLng(maxLat, maxLon)
                    ),
                    0.05
                )
                
                // [FIX] Try fitMapPoints first, fallback to Center+Zoom14 if it fails
                try {
                    val sw = LatLng.from(safeBounds.southwest.latitude, safeBounds.southwest.longitude)
                    val ne = LatLng.from(safeBounds.northeast.latitude, safeBounds.northeast.longitude)
                    
                    // Use larger padding (100 -> 150)
                    val update = CameraUpdateFactory.fitMapPoints(arrayOf(sw, ne), 150)
                    map.moveCamera(update, CameraAnimation.from(1000, true, true))
                } catch (e: Exception) {
                    val centerLat = safeBounds.center.latitude
                    val centerLon = safeBounds.center.longitude
                
                    val update = CameraUpdateFactory.newCenterPosition(
                        com.kakao.vectormap.LatLng.from(centerLat, centerLon), 
                        14
                    )
                    map.moveCamera(update, CameraAnimation.from(1000, true, true))
                }
                
                delay(3000)
            } else {
                delay(500)
            }

            // Step 2: Move to User Location (Zoom 18)
            // Poll for location (Max 10s)
            var attempts = 0
            while (attempts < 20) {
                // Check captured state or helper
                val latestLoc = currentLocState.value
                if (latestLoc != null && latestLoc.latitude != 0.0) {
                     break
                }
                delay(500)
                attempts++
            }
            
            val latestLoc = currentLocState.value
            if (latestLoc != null && latestLoc.latitude != 0.0) {
                val lat = latestLoc.latitude
                val lon = latestLoc.longitude
                
                val finalUpdate = CameraUpdateFactory.newCenterPosition(
                    com.kakao.vectormap.LatLng.from(lat, lon), 18
                )
                  // [MapStep 5] Move to Current Location
                  android.util.Log.e("MapStep", "5. Move to Current Location")
                  System.out.println(">>> MapStep 5. Move to Current Location")
                  
                  // [FIX] Disable Filter to show ALL pins (including Beijing) now
                  isDistanceFilterEnabled = false
                  
                  val anim = CameraAnimation.from(500, true, true)
                  map.moveCamera(finalUpdate, anim) 
            } else {
                 android.util.Log.e("MapStep", "5. Current Location Unknown (Skip)")
                 System.out.println(">>> MapStep 5. Current Location Unknown (Skip)")
            }
            onInitialAnimationDone()
            // hasLocalAnimationRun = true // Already set at start
        } catch (e: Exception) { e.printStackTrace() }

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
                            com.example.alltodo.ui.createKakaoPinBitmap(context, 1, resId, android.graphics.Color.TRANSPARENT)
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
                    val b = com.example.alltodo.ui.createKakaoPinBitmap(context, cluster.count, resId, badgeColor) // Scale handled internally
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

    // [MapStep Event] Monitor OS Location Updates
    LaunchedEffect(currentLocation) {
        if (currentLocation != null) {
            val msg = ">>> [MapStep Event] OS Location Received: ${currentLocation.latitude}, ${currentLocation.longitude}"
            android.util.Log.e("MapStep", msg)
            System.out.println(msg)
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
                        // onMapReady, getPosition, getZoomLevel이 모두 이 블록 안에 있어야 합니다.
                        override fun onMapReady(kMap: KakaoMap) {
                            kakaoMap = kMap
                            isMapReady = true
                            activeOnMapReady(kMap) // Call captured parent callback with Map instance

                            // Set Global Listener
                            kMap.setOnLabelClickListener { _, _, label ->
                                val pos = label.position
                                // Find Cluster by Position (Approx)
                                val clicked = visibleClusters.find { 
                                    Math.abs(it.latitude - pos.latitude) < 0.0001 && Math.abs(it.longitude - pos.longitude) < 0.0001
                                }
                                
                                if (clicked != null) {
                                    val screenPt = kMap.toScreenPoint(pos)
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
                        }

                        override fun getPosition(): LatLng {
                             return LatLng.from(37.5665, 126.9780)
                        }

                        override fun getZoomLevel(): Int {
                             return 18
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
