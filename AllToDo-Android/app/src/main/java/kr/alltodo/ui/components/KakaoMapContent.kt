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
// [FIX] Removed invalid import



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

    showActivePath: Boolean = true,
    livePath: List<kr.alltodo.data.LocationEntity> = emptyList(), // [FIX] Added parameter
    onCameraIdle: (Double, Float, Double) -> Unit // [NEW] Wm, Zoom, Lat
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
            val routeLineManager = kakaoMap!!.getRouteLineManager()!!
            val style = RouteLineStyles.from(
                RouteLineStyle.from(5f * density, android.graphics.Color.parseColor("#FF5722"))
            )
            val segment = RouteLineSegment.from(points, style)
            layer.addRouteLine(RouteLineOptions.from(Arrays.asList(segment)))
        }

    }

    // [NEW] Active Path Blue Dot Trail
    LaunchedEffect(kakaoMap, activePoints, showActivePath) {
        val map = kakaoMap ?: return@LaunchedEffect
        val labelManager = map.labelManager ?: return@LaunchedEffect
        val layer = labelManager.getLayer("activeDotsLayer") ?: labelManager.addLayer(LabelLayerOptions.from("activeDotsLayer").setZOrder(2100))
        
        layer?.removeAll()
        
        if (showActivePath && activePoints.isNotEmpty()) {
            val tailPoints = activePoints.takeLast(20)
            val dotBitmap = kr.alltodo.ui.PinImageManager.createDotBitmap(context, android.graphics.Color.BLUE, 3f)
            
            val styleId = "blue_dot_style"
            var style = labelManager.getLabelStyles(styleId)
            if (style == null) {
                style = labelManager.addLabelStyles(LabelStyles.from(styleId, LabelStyle.from(dotBitmap).setAnchorPoint(0.5f, 0.5f)))
            }
            
            if (style != null) {
                tailPoints.forEach { point ->
                   layer?.addLabel(LabelOptions.from(LatLng.from(point.latitude, point.longitude)).setStyles(style))
                }
            }
        }
    }




    
    // [FIX] We need a persistent state for the User Label to reuse it across recompositions
    val userLabelState = remember { mutableStateOf<com.kakao.vectormap.label.Label?>(null) }
    

    
    // [FIX] Persistent State for Labels
    val currentLabels = remember { mutableListOf<com.kakao.vectormap.label.Label>() }
    
    LaunchedEffect(kakaoMap, visibleClusters) {
        val map = kakaoMap ?: return@LaunchedEffect
        val labelManager = map.labelManager ?: return@LaunchedEffect
        val layer = labelManager.getLayer("mainLayer") ?: labelManager.addLayer(LabelLayerOptions.from("mainLayer"))
        
        // [New Algorithm] 4-Step Incremental Clustering
        // We use stable keys based on item IDs to track identity across re-clustering.
        fun generateKey(cluster: kr.alltodo.ui.PinClusterItem): String {
            val itemIds = cluster.items.map { 
                when(it) {
                    is kr.alltodo.ui.UnifiedItem.Todo -> "T_${it.item.todo_id}"
                    is kr.alltodo.ui.UnifiedItem.History -> "H_${it.item.todo_id}"
                    is kr.alltodo.ui.UnifiedItem.CurrentLocation -> "U"
                }
            }.sorted().joinToString("|")
            return "k_${cluster.count}_$itemIds"
        }

        val oldLabelsByKey = currentLabels.associateBy { (it.tag as? kr.alltodo.ui.PinClusterItem)?.let { generateKey(it) } ?: "none" }
        val newLabels = mutableListOf<com.kakao.vectormap.label.Label>()
        
        // --- Step 1 & 4: Identification & Addition ---
        visibleClusters.forEach { cluster ->
            val lat = cluster.latitude
            val lng = cluster.longitude
            if (lat.isNaN() || lng.isNaN()) return@forEach

            val key = generateKey(cluster)
            val existing = oldLabelsByKey[key]
            
            if (existing != null) {
                // [REUSE] Identical cluster content found, just update position/tag
                val pos = LatLng.from(cluster.latitude, cluster.longitude)
                
                // Smooth move for User Pin, immediate for others to prevent 'lagging' trail
                val isUserVisible = cluster.items.any { it is kr.alltodo.ui.UnifiedItem.CurrentLocation }
                if (isUserVisible) {
                    existing.moveTo(pos, 500)
                } else {
                    existing.moveTo(pos, 0)
                }
                
                existing.tag = cluster
                newLabels.add(existing)
            } else {
                // [ADD] New pin or cluster - Step 1/4
                val isSingle = cluster.count == 1
                val isUser = cluster.items.any { it is kr.alltodo.ui.UnifiedItem.CurrentLocation }
                val firstItem = cluster.items.firstOrNull() ?: return@forEach
                
                val styleId = "s_${cluster.count}_${if(isUser) "U" else firstItem.hashCode()}"
                var styles = labelManager.getLabelStyles(styleId)
                
                if (styles == null) {
                    val (bitmap, ax, ay) = if (isSingle) {
                        val b = kr.alltodo.ui.createKakaoPinBitmap(context, 0, firstItem.pinId, android.graphics.Color.TRANSPARENT)
                        Triple(b, 0.5f, 1.0f)
                    } else {
                        val style = kr.alltodo.utils.MapLogicHelper.resolveClusterStyle(cluster.items)
                        val b = kr.alltodo.ui.createKakaoPinBitmap(context, cluster.count, style.pinId, style.color)
                        
                        val density = context.resources.displayMetrics.density
                        val scale = 0.7f
                        val pinW = (40 * density * scale)
                        val badgeRadius = 10f * density * scale
                        val padding = (badgeRadius * 1.5f)
                        val totalW = pinW + padding
                        val finalAnchorX = (pinW / 2f) / totalW
                        
                        Triple(b, finalAnchorX, 1.0f)
                    }
                    
                    if (bitmap != null) {
                        styles = labelManager.addLabelStyles(LabelStyles.from(styleId, LabelStyle.from(bitmap).setAnchorPoint(ax, ay)))
                    }
                }
                
                if (styles != null) {
                    val options = LabelOptions.from(LatLng.from(cluster.latitude, cluster.longitude))
                        .setStyles(styles)
                        .setClickable(true)
                    
                    val rank = if (isUser) 3000L else (if (cluster.count > 1) 2000L else 1000L)
                    options.setRank(rank)
                    
                    layer?.addLabel(options)?.let { label ->
                        label.tag = cluster
                        newLabels.add(label)
                    }
                }
            }
        }
        
        // --- Step 2 & 3: Removal ---
        val newLabelIds = newLabels.map { it.labelId }.toSet()
        currentLabels.forEach { old ->
            if (!newLabelIds.contains(old.labelId)) {
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
                val b = kr.alltodo.ui.createKakaoPinBitmap(context, 0, "10", android.graphics.Color.TRANSPARENT)
                
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
    
    // [NEW] Live Path Rendering (RouteLine)
    LaunchedEffect(livePath, showActivePath, kakaoMap) {
        val map = kakaoMap ?: return@LaunchedEffect
        val manager = map.routeLineManager
        val layer = manager?.getLayer("livePathLayer") ?: manager?.addLayer("livePathLayer", 2000)
        
        if (showActivePath && livePath.size >= 2) {
             val points = livePath.map { LatLng.from(it.latitude, it.longitude) }
             // [FIX] Explicitly pass List as Arrays.asList or strict type to resolve ambiguity
             val segment = RouteLineSegment.from(points, RouteLineStyle.from(20f, android.graphics.Color.BLUE))
             val options = RouteLineOptions.from(segment)
             
             // Clear old and add new (Simpler than updating for now)
             layer?.removeAll()
             layer?.addRouteLine(options)
             
             // [NEW] Dots for Kakao?
             // Kakao RouteLine supports styles, but not explicit dots at vertices easily without LabelLayer.
             // Adding Labels for every point is very heavy.
             // We will stick to the Line for performance.
        } else {
             layer?.removeAll()
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
                            
                            // [NEW] Camera Idle Listener
                            // [NEW] Camera Idle Listener
                            // [NEW] Camera Idle Listener
                            // Using setOnCameraMoveEndListener directly if unified listener is missing
                            kMap.setOnCameraMoveEndListener { kakaoMap, position, gestureType ->
                                    val width = mapView?.width ?: 0
                                    val height = mapView?.height ?: 0
                                    if (width > 0 && height > 0) {
                                        val left = kakaoMap.fromScreenPoint(0, height / 2)
                                        val right = kakaoMap.fromScreenPoint(width, height / 2)
                                        
                                        if (left != null && right != null) {
                                            val results = FloatArray(1)
                                            android.location.Location.distanceBetween(
                                                position.position.latitude, left.longitude,
                                                position.position.latitude, right.longitude,
                                                results
                                            )
                                            val widthMeters = results[0].toDouble()
                                            
                                            // Report to ViewModel
                                            onCameraIdle(widthMeters, position.zoomLevel.toFloat(), position.position.latitude)
                                        }
                                    }
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
