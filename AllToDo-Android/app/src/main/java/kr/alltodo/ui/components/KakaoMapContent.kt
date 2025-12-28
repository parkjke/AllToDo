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
import androidx.compose.ui.platform.LocalDensity
import java.util.Arrays
import kr.alltodo.ui.UnifiedItem
import kr.alltodo.ui.PinClusterItem
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
import com.kakao.vectormap.route.RouteLineOptions
import com.kakao.vectormap.route.RouteLineSegment
import com.kakao.vectormap.route.RouteLineStyle
import com.kakao.vectormap.route.RouteLineStyles



@Composable
fun KakaoMapContent(
    modifier: Modifier = Modifier,
    isSdkInitialized: Boolean,
    clusteredItems: List<PinClusterItem>,
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
    contentPaddingBottom: Int = 0,
    activePoints: List<kr.alltodo.data.GpsAuthPoint> = emptyList(),
    showActivePath: Boolean = true
) {
    val context = LocalContext.current
    
    // --- State Variables (Unified at top) ---
    var kakaoMap by remember { mutableStateOf<KakaoMap?>(null) }
    var isMapReady by remember { mutableStateOf(false) }
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var hasLocalAnimationRun by remember { mutableStateOf(false) }
    var isDistanceFilterEnabled by remember { mutableStateOf(true) }
    
    // Smart Tethering State
    var currentSpanLon by remember { mutableStateOf(0) }
    var currentSpanLat by remember { mutableStateOf(0) }
    var moveLocation by remember { mutableStateOf<kr.alltodo.utils.SmartLocationManager.IntLocation?>(null) }

    // [FIX] Capture callback to avoid name shadowing in KakaoMapReadyCallback
    val activeOnMapReady = onMapReady
    
    val currentLocState = rememberUpdatedState(currentLocation)
    
    // 500km Filter Logic
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
        onInitialAnimationDone = {
            // Set Initial Tethering Anchor
            currentLocation?.let { moveLocation = kr.alltodo.utils.SmartLocationManager.toIntLocation(it) }
            onInitialAnimationDone()
        },
        onResetAnimationDone = onResetAnimationDone, // [NEW]
        onEnableClustering = onEnableClustering,
        onMove = { lat, lon, zoom, animate ->
            kakaoMap?.let { map ->
                // [FIX] Release Stage 2 constraint for Stage 3 Focus
                map.setCameraMinLevel(1)
                map.setCameraMaxLevel(21)
                
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
    
    
    val density = LocalDensity.current.density
    
    // [NEW] Active Path Rendering
    LaunchedEffect(kakaoMap, activePoints, showActivePath, density) {
        val map = kakaoMap ?: return@LaunchedEffect
        val manager = map.routeLineManager ?: return@LaunchedEffect
        val layer = manager.getLayer("activePathLayer") ?: manager.addLayer("activePathLayer", 2000)
        
        // Remove old trail
        layer.removeAll()
        
        if (showActivePath && activePoints.size >= 2) {
            val points = activePoints.map { LatLng.from(it.latitude, it.longitude) }
            val style = RouteLineStyles.from(
                RouteLineStyle.from(2.5f * density, android.graphics.Color.parseColor("#FF5722"))
            )



            val segment = RouteLineSegment.from(points, style)
            layer.addRouteLine(RouteLineOptions.from(Arrays.asList(segment)))
        }

    }




    
    // [FIX] We need a persistent state for the User Label to reuse it across recompositions
    val userLabelState = remember { mutableStateOf<com.kakao.vectormap.label.Label?>(null) }
    
    LaunchedEffect(kakaoMap, visibleClusters) {
        val map = kakaoMap ?: return@LaunchedEffect
        val labelManager = map.labelManager ?: return@LaunchedEffect
        val layer = labelManager.getLayer("mainLayer") ?: labelManager.addLayer(LabelLayerOptions.from("mainLayer"))
        
        // [FIX] instead of removeAll(), we will remove all EXCEPT the user label if we can find it?
        // Actually, easiest is to keep a reference.
        
        // 1. Identify New User Data
        var newUserPos: LatLng? = null
        var newUserBitmap: Bitmap? = null
        var newUserAnchor: Pair<Float, Float>? = null
        var newUserItems: List<UnifiedItem>? = null
        var newUserClusterIndex = -1
        
        visibleClusters.forEachIndexed { index, cluster ->
             if (cluster.items.any { it is UnifiedItem.CurrentLocation }) {
                 newUserClusterIndex = index
                 newUserItems = cluster.items
                 newUserPos = LatLng.from(cluster.latitude, cluster.longitude)
                 
                 val isSingle = cluster.count == 1
                 val firstItem = cluster.items.first()
                 
                 val (b, ax, ay) = if (isSingle) {
                     Triple(kr.alltodo.ui.createKakaoPinBitmap(context, 0, kr.alltodo.R.drawable.pin_current, android.graphics.Color.TRANSPARENT), 0.5f, 0.5f)
                 } else {
                     Triple(kr.alltodo.ui.createKakaoPinBitmap(context, cluster.count, kr.alltodo.R.drawable.pin_current, android.graphics.Color.RED), 0.33f, 1.0f)
                 }
                 newUserBitmap = b
                 newUserAnchor = Pair(ax, ay)
             }
        }
        
        // 2. Clear Non-User Labels
        // We can't selectively remove easily without tracking ALL. 
        // Strategy: removeAll(), but if userLabelState is valid, does removeAll() invalidate it?
        // YES. layer.removeAll() destroys all labels.
        
        // CHANGE STRATEGY: We must track ALL labels to do proper diffing, OR
        // just accept that we clear non-user labels.
        // But to reuse User Label, we must NOT call removeAll().
        
        // So we need to iterate internal list? No access.
        // We MUST maintain our own list of labels.
        
        // [Refined Plan]
        // 1. Clear everything (Performance hit? No, logic is simpler).
        // 2. BUT to support smooth movement, we need to SAVE the user label instance.
        // 3. Issue: If we call layer.removeAll(), the user label is dead.
        // 4. Solution: Don't call removeAll(). Call remove(label) for everyone else.
        
        // We need a persistent list of labels.
    }
    
    // [FIX] Persistent State for Labels
    val currentLabels = remember { mutableListOf<com.kakao.vectormap.label.Label>() }
    
    LaunchedEffect(kakaoMap, visibleClusters) {
        val map = kakaoMap ?: return@LaunchedEffect
        val labelManager = map.labelManager ?: return@LaunchedEffect
        val layer = labelManager.getLayer("mainLayer") ?: labelManager.addLayer(LabelLayerOptions.from("mainLayer"))
        
        // 1. Identify User in New Data
        var newUserClusterIndex = -1
        visibleClusters.forEachIndexed { index, cluster ->
            if (cluster.items.any { it is UnifiedItem.CurrentLocation }) {
                newUserClusterIndex = index
            }
        }
        
        // 2. Find Existing User Label
        var reusedUserLabel: com.kakao.vectormap.label.Label? = null
        val oldUserLabel = currentLabels.find { (it.tag as? PinClusterItem)?.items?.any { item -> item is UnifiedItem.CurrentLocation } == true }
        
        // 3. Render New Labels
        val newLabels = mutableListOf<com.kakao.vectormap.label.Label>()
        
        visibleClusters.forEachIndexed { index, cluster ->
            val isUserCluster = (index == newUserClusterIndex)
            val currentCluster = cluster // Explicit parameter name for better readability
            
            if (isUserCluster && oldUserLabel != null) {
                // Reuse!
                reusedUserLabel = oldUserLabel
                val pos = LatLng.from(currentCluster.latitude, currentCluster.longitude)
                oldUserLabel.moveTo(pos, 500) // Smooth Move!
                
                // Update Style (Icon/Anchor)
                // Kakao Label doesn't support changing icon easily without styles.
                // But we can add new style and set it?
                // Or just assume style is same (Blue Dot)?
                // Actually changing styles is possible: label.setStyles(styles)
                
                val isSingle = cluster.count == 1
                val firstItem = cluster.items.first()
                val styleId = "cluster_${cluster.count}_User"
                
                var styles = labelManager.getLabelStyles(styleId)
                if (styles == null) {
                    val (b, ax, ay) = if (isSingle) {
                        Triple(kr.alltodo.ui.createKakaoPinBitmap(context, 0, kr.alltodo.R.drawable.pin_current, android.graphics.Color.TRANSPARENT), 0.5f, 0.5f)
                    } else {
                        Triple(kr.alltodo.ui.createKakaoPinBitmap(context, cluster.count, kr.alltodo.R.drawable.pin_current, android.graphics.Color.RED), 0.33f, 1.0f)
                    }
                    if (b != null) {
                         styles = labelManager.addLabelStyles(LabelStyles.from(styleId, LabelStyle.from(b).setAnchorPoint(ax, ay)))
                    }
                }
                
                if (styles != null) oldUserLabel.styles = styles
                
                // Update Tag
                oldUserLabel.tag = cluster
                newLabels.add(oldUserLabel)
                
            } else {
                // Create New
                val isSingle = cluster.count == 1
                val firstItem = cluster.items.firstOrNull() ?: return@forEachIndexed
                
                val styleId = "cluster_${cluster.count}_${cluster.latitude}_${cluster.longitude}" // Unique per pos
                var styles = labelManager.getLabelStyles(styleId)
                
                    if (styles == null) {
                    val (bitmap, anchorX, anchorY) = if (isSingle) {
                        val resId = firstItem.getPinResId()
                        val b = kr.alltodo.ui.createKakaoPinBitmap(context, 0, resId, android.graphics.Color.TRANSPARENT)
                        // [FIX] Update Anchor for Single Pin too (it has 0 padding in logic? No, PinImageManager adds padding even for 0 count? 
                        // Let's check PinImageManager. createClusterPin always adds padding.
                        // So 0.35f is correct for Single too if it goes through createClusterPin.
                        Triple(b, 0.35f, 1.0f)
                    } else {
                         // (Color Logic...)
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
                        val b = kr.alltodo.ui.createKakaoPinBitmap(context, cluster.count, resId, badgeColor) 
                        // [FIX] Anchor 0.35f aligns with new Padding logic for Scale 0.7
                        Triple(b, 0.35f, 1.0f)
                    }
                    
                    if (bitmap != null) {
                        styles = labelManager.addLabelStyles(LabelStyles.from(styleId, LabelStyle.from(bitmap).setAnchorPoint(anchorX, anchorY)))
                    }
                }
                
                if (styles != null) {
                     val options = LabelOptions.from(LatLng.from(cluster.latitude, cluster.longitude))
                            .setStyles(styles)
                            .setClickable(true)
                            
                     // [FIX] Explicit Z-Order (Rank) Logic
                     // User: 3000, Cluster: 2000, Single: 1000
                     // Note: setRank takes Long. Higher is on top.
                     val rank = if (cluster.count > 1) 2000L else 1000L
                     options.setRank(rank)
                     
                     val label = layer?.addLabel(options)
                     if (label != null) {
                         label.tag = cluster
                         newLabels.add(label)
                     }
                }
            }
        }
        
        // 4. Cleanup Old Labels
        currentLabels.forEach { old ->
             if (old != reusedUserLabel) {
                 layer?.remove(old)
             }
        }
        currentLabels.clear()
        currentLabels.addAll(newLabels)

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
                
                // 2. Rotation Change
                val rotateRad = map.cameraPosition?.rotationAngle ?: 0f
                val rotateDeg = Math.toDegrees(rotateRad.toDouble()).toFloat()
                if (Math.abs(rotateDeg - lastRotate) > 1.0f) {
                    lastRotate = rotateDeg
                    onCameraRotate(rotateDeg)
                }
                
                // [NEW] 3. Span Calculation via Projection
                val width = mapView?.width ?: 0
                val height = mapView?.height ?: 0
                if (width > 0 && height > 0) {
                    val center = map.cameraPosition?.getPosition()
                    
                    if (center != null) {
                        val left = map.fromScreenPoint(0, height / 2)
                        val top = map.fromScreenPoint(width / 2, 0)
                        
                        if (left != null && top != null) {
                            val dLon = Math.abs(center.longitude - left.longitude) * 2
                            val dLat = Math.abs(center.latitude - top.latitude) * 2
                            val newSpanLon = (dLon * 100000).toInt()
                            val newSpanLat = (dLat * 100000).toInt()
                            
                            if (newSpanLon > 0) currentSpanLon = newSpanLon
                            if (newSpanLat > 0) currentSpanLat = newSpanLat
                        }
                    }
                }
                
                delay(300) 
            }
        }
    }

    // [NEW] Smart Tethering Reaction
    LaunchedEffect(currentLocation, currentSpanLon, currentSpanLat, initialAnimationDone) {
        if (!initialAnimationDone) return@LaunchedEffect
        val map = kakaoMap ?: return@LaunchedEffect
        val loc = currentLocation ?: return@LaunchedEffect
        
        val userInt = kr.alltodo.utils.SmartLocationManager.toIntLocation(loc)
        
        // Use persistent anchor instead of map center for Exploration Friendliness
        val anchor = moveLocation ?: run {
            moveLocation = userInt
            return@LaunchedEffect
        }
        
        if (kr.alltodo.utils.SmartLocationManager.needsCentering(userInt, anchor, currentSpanLon, currentSpanLat)) {
            map.moveCamera(com.kakao.vectormap.camera.CameraUpdateFactory.newCenterPosition(LatLng.from(loc.latitude, loc.longitude)), com.kakao.vectormap.camera.CameraAnimation.from(500, true, true))
            moveLocation = userInt // Update Anchor
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
                                // [FIX] Use tag for 100% reliable identification
                                val clicked = label.tag as? PinClusterItem
                                
                                if (clicked != null) {
                                    val pos = label.position
                                    val screenPt = kMap.toScreenPoint(pos)
                                    val scrollX = screenPt?.x?.toFloat() ?: 0f
                                    val scrollY = screenPt?.y?.toFloat() ?: 0f
                                    
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
