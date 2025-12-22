package kr.alltodo.ui.components

import android.graphics.Bitmap
import android.graphics.PointF
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
import kr.alltodo.ui.UnifiedItem
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
    clusteredItems: List<kr.alltodo.ui.TodoViewModel.PinClusterItem>,
    beforeLocation: android.location.Location,
    currentLocation: android.location.Location?,
    onMapReady: (KakaoMap) -> Unit,
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

    // [NEW] Unified Map Begin Logic (Centralized in MapBegin.kt)
    MapBeginSequence(
        isMapReady = isMapReady,
        initialAnimationDone = initialAnimationDone,
        beforeLocation = beforeLocation,
        currentLocation = currentLocation,
        clusteredItems = clusteredItems,
        onInitialAnimationDone = onInitialAnimationDone,
        onResetAnimationDone = onResetAnimationDone, // [NEW]
        onEnableClustering = onEnableClustering,
        onMove = { lat, lon, zoom, animate ->
            kakaoMap?.let { map ->
                // [FIX] Release Stage 2 constraint for Stage 3 Focus
                if (zoom > 15f) {
                    map.setCameraMinLevel(1)
                    map.setCameraMaxLevel(21)
                }
                
                val update = CameraUpdateFactory.newCenterPosition(LatLng.from(lat, lon), zoom.toInt())
                if (animate) {
                    map.moveCamera(update, com.kakao.vectormap.camera.CameraAnimation.from(1200, true, true))
                } else {
                    map.moveCamera(update)
                }
            }
        },
        onFitBounds = { points, padding, _ ->
            kakaoMap?.let { map ->
                if (points.size == 1) {
                    val p = points.first()
                    map.moveCamera(CameraUpdateFactory.newCenterPosition(LatLng.from(p.first, p.second), 15))
                } else if (points.isNotEmpty()) {
                    val kakaoPoints = points.map { LatLng.from(it.first, it.second) }.toTypedArray()
                    
                    // [FIX] Lock max zoom to 15.0 for Stage 2. 
                    // Use correct SDK methods: setCameraMinLevel/setCameraMaxLevel
                    map.setCameraMinLevel(1)
                    map.setCameraMaxLevel(15)
                    
                    map.moveCamera(
                        CameraUpdateFactory.fitMapPoints(kakaoPoints, padding),
                        com.kakao.vectormap.camera.CameraAnimation.from(1000, true, true)
                    )
                }
            }
        },
        onStop = {
            // Kakao doesn't have an explicit cancel, but moving camera again stops previous.
        }
    )
    
    
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
                    // Use createKakaoPinBitmap with count=0 for Single Pin (No Badge, Scaled 0.7)
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
                    val b = kr.alltodo.ui.createKakaoPinBitmap(context, cluster.count, resId, badgeColor) // Scale handled internally
                    // [FIX] Adjusted Anchor X from 0.4 to 0.33 to match new padding ratio (16 / 48)
                    Triple(b, 0.33f, 1.0f)
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
            }
        }

        // [NEW] Show Creating Todo Pin (Green)
        if (creatingTodoLocation != null) {
            val styleId = "creating_todo"
            var styles = labelManager.getLabelStyles(styleId)
            if (styles == null) {
                val b = kr.alltodo.ui.createKakaoPinBitmap(context, 0, kr.alltodo.R.drawable.pin_todo_ready, android.graphics.Color.TRANSPARENT)
                if (b != null) {
                    styles = labelManager.addLabelStyles(LabelStyles.from(styleId, LabelStyle.from(b).setAnchorPoint(0.5f, 1.0f)))
                }
            }
            if (styles != null) {
                layer?.addLabel(LabelOptions.from(creatingTodoLocation).setStyles(styles))
            }
        }
    }



    // [FIX] Fallback Zoom & Rotation Polling
    LaunchedEffect(kakaoMap) {
        val map = kakaoMap
        if (map != null) {
            var lastZoom = 0
            var lastRotate = 0f
            while (true) {
                // 1. Zoom Change
                val z = map.zoomLevel
                if (z != lastZoom) {
                    lastZoom = z
                    onZoomChange(z.toFloat())
                }
                
                // 2. Rotation Change (Kakao rotate is in Radians, CW from North)
                // Convert to Degrees for our UI
                val rotateRad = map.cameraPosition?.rotationAngle ?: 0f
                val rotateDeg = Math.toDegrees(rotateRad.toDouble()).toFloat()
                if (Math.abs(rotateDeg - lastRotate) > 1.0f) { // 1 degree threshold
                    lastRotate = rotateDeg
                    onCameraRotate(rotateDeg)
                }
                
                delay(200) // Poll every 200ms for better responsiveness
            }
        }
    }

    // [MapStep Event] Monitor OS Location Updates
    LaunchedEffect(currentLocation) {
        if (currentLocation != null) {
            val msg = ">>> [MapStep Event] OS Location Received: ${currentLocation.latitude}, ${currentLocation.longitude}"
        }
    }
    
    // [FIX] Lifecycle Management for Fast Resume (Avoid Zombie State)
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    var mapView by remember { mutableStateOf<MapView?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> mapView?.resume()
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> mapView?.pause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    // [NEW] Dynamic Padding & Re-centering Reaction
    LaunchedEffect(contentPaddingBottom, creatingTodoLocation) {
        val map = kakaoMap ?: return@LaunchedEffect
        map.setPadding(0, 0, 0, contentPaddingBottom)
        if (contentPaddingBottom > 0 && creatingTodoLocation != null) {
            map.moveCamera(com.kakao.vectormap.camera.CameraUpdateFactory.newCenterPosition(creatingTodoLocation), com.kakao.vectormap.camera.CameraAnimation.from(500))
        }
    }

    if (isSdkInitialized) {
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    mapView = this // [FIX] Capture Reference
                    start(object : MapLifeCycleCallback() {
                        override fun onMapDestroy() {}
                        override fun onMapError(e: Exception?) {}
                    }, object : KakaoMapReadyCallback() {
                        // onMapReady, getPosition, getZoomLevel이 모두 이 블록 안에 있어야 합니다.
                        override fun onMapReady(kMap: KakaoMap) {
                            kakaoMap = kMap
                            isMapReady = true
                            activeOnMapReady(kMap) // Call captured parent callback with Map instance
                            
                            // [NEW] Apply Padding
                            kMap.setPadding(0, 0, 0, contentPaddingBottom)

                            // [NEW] Map Long Click (Terrain)
                            kMap.setOnTerrainLongClickListener(object : KakaoMap.OnTerrainLongClickListener {
                                override fun onTerrainLongClicked(kakaoMap: KakaoMap, latLng: LatLng, screenPoint: PointF) {
                                    onMapLongClick(latLng)
                                }
                            })

                            // [FIX] Polling for Zoom Change (since Listener API is uncertain/failed)
                            // This ensures we catch Zoom changes even if listeners are tricky
                            // We will handle this in a LaunchedEffect outside, or basic polling here?
                            // LaunchedEffect is better.
                            
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
                             return LatLng.from(beforeLocation.latitude, beforeLocation.longitude)
                        }

                        override fun getZoomLevel(): Int {
                             return 15
                        }
                    })
                    
                    // [FIX] Black Screen Fix: Force Resume if Activity is already Resumed
                    if (lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
                        this.resume()
                    }
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
