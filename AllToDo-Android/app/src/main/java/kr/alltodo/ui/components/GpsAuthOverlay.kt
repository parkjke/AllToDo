package kr.alltodo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kr.alltodo.ui.GpsAuthViewModel
import kr.alltodo.ui.MapProvider
import kr.alltodo.data.GpsAuthPoint
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.zIndex
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.ui.text.font.FontWeight
import com.google.android.gms.maps.model.CameraPosition as GoogleCameraPosition
import com.google.android.gms.maps.model.LatLng as GoogleLatLng
import com.google.maps.android.compose.*
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView as KakaoMapView
import com.kakao.vectormap.camera.CameraUpdateFactory as KakaoCameraUpdateFactory
import com.naver.maps.map.MapView as NaverMapView
import com.naver.maps.map.NaverMap
import com.naver.maps.map.overlay.Marker as NaverMarker
import com.naver.maps.map.overlay.OverlayImage as NaverOverlayImage
import com.naver.maps.geometry.LatLng as NaverLatLng
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color as AndroidColor
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GpsAuthOverlay(
    viewModel: GpsAuthViewModel,
    currentLocation: android.location.Location?,
    mapProvider: MapProvider,
    onDismiss: () -> Unit,
    onStartTracking: () -> Unit = {}
) {
    // --- Local Test Logic States ---
    var localSelectedTrack by remember { mutableStateOf<kr.alltodo.data.GpsAuthTrack?>(null) }
    var localPoints by remember { mutableStateOf<List<kr.alltodo.data.GpsAuthPoint>>(emptyList()) }
    var tmIndex by remember { mutableStateOf(-1) }
    var isPlaying by remember { mutableStateOf(false) }
    var tmSpeed by remember { mutableStateOf(1) }
    var useFixedInterval by remember { mutableStateOf(false) }
    
    var isOriginalVisible by remember { mutableStateOf(true) }
    var isStage1Visible by remember { mutableStateOf(false) }
    var isStage2Visible by remember { mutableStateOf(false) }
    var stage1Points by remember { mutableStateOf<List<List<kr.alltodo.data.GpsAuthPoint>>>(emptyList()) }
    var stage2Points by remember { mutableStateOf<List<List<kr.alltodo.data.GpsAuthPoint>>>(emptyList()) }
    var isSimplifying by remember { mutableStateOf(false) }

    val pointsData by viewModel.points.collectAsState() // For live tracking
    val isTracking by viewModel.isTracking.collectAsState()
    val savedTracks by viewModel.savedTracks.collectAsState()
    val pointSize by viewModel.pointSize.collectAsState()

    // Resolve which points to show
    val points = if (isTracking) pointsData else localPoints
    val selectedTrack = localSelectedTrack

    val scope = rememberCoroutineScope()

    // Helper: Map WASM result back to points
    fun mapWasmResult(wasmResult: List<Int>, ref: List<kr.alltodo.data.GpsAuthPoint>): List<kr.alltodo.data.GpsAuthPoint> {
        val res = mutableListOf<kr.alltodo.data.GpsAuthPoint>()
        for (i in 0 until wasmResult.size step 2) {
            val lat = wasmResult[i]
            val lng = wasmResult[i+1]
            res.add(ref.find { it.intLat == lat && it.intLng == lng } ?: kr.alltodo.data.GpsAuthPoint(lat, lng, 0, 0))
        }
        return res
    }

    // Pruning Logic
    fun prune1(targetPoints: List<GpsAuthPoint>) {
        if (targetPoints.size < 3) return
        scope.launch {
            isSimplifying = true
            try {
                val input = targetPoints.flatMap { listOf(it.intLat, it.intLng) }
                val simplified = viewModel.wasmManager.compress(input, minDist = 3)
                val result = mapWasmResult(simplified, targetPoints)
                stage1Points = stage1Points + listOf(result)
            } finally { isSimplifying = false }
        }
    }

    fun prune2(targetPoints: List<GpsAuthPoint>) {
        if (targetPoints.size < 3) return
        scope.launch {
            isSimplifying = true
            try {
                val input = targetPoints.flatMap { listOf(it.intLat, it.intLng) }
                val simplified = viewModel.wasmManager.compress(input, minDist = 3)
                val result = mapWasmResult(simplified, targetPoints)
                stage2Points = stage2Points + listOf(result)
            } finally { isSimplifying = false }
        }
    }

    // [V3] Filtering for sequential rendering
    val filterTimestamp = remember(selectedTrack, tmIndex, points) {
        if (selectedTrack != null && tmIndex >= 0 && points.isNotEmpty()) {
            points[tmIndex.coerceAtMost(points.size - 1)].timestamp
        } else {
            Long.MAX_VALUE
        }
    }

    // [FIX] Incremental Path Logic
    val displayPath = remember(stage1Points, tmIndex, points, selectedTrack) {
        val latestS1 = stage1Points.lastOrNull() ?: emptyList()
        if (selectedTrack != null && tmIndex >= 0 && points.isNotEmpty()) {
            val head = points[tmIndex.coerceAtMost(points.size - 1)]
            val list = latestS1.filter { it.timestamp <= head.timestamp }.toMutableList()
            if (list.isEmpty() || list.last().timestamp < head.timestamp) {
                list.add(head)
            }
            list
        } else {
            latestS1
        }
    }
    
    // Time Machine Replay
    LaunchedEffect(isPlaying, tmIndex, tmSpeed, useFixedInterval) {
        if (isPlaying && selectedTrack != null) {
            val nextIdx = tmIndex + 1
            if (nextIdx < points.size) {
                val currentP = points[tmIndex.coerceAtLeast(0)]
                val nextP = points[nextIdx]
                val realDiff = (nextP.timestamp - currentP.timestamp).coerceAtLeast(0L)
                
                val delayMs = if (useFixedInterval) 100L else {
                    val multi = when(tmSpeed) { 1->2.0; 2->10.0; 3->40.0; else->2.0 }
                    (realDiff / multi).toLong().coerceIn(10L, 1000L)
                }
                delay(delayMs)
                tmIndex = nextIdx
            } else {
                if (useFixedInterval && stage2Points.isEmpty()) {
                    prune2(stage1Points.lastOrNull() ?: emptyList())
                }
                isPlaying = false
                tmIndex = -1
                useFixedInterval = false
            }
        }
    }
    
    // 1. Google Camera State
    val googleCameraState = rememberCameraPositionState {
        currentLocation?.let {
            position = GoogleCameraPosition.fromLatLngZoom(GoogleLatLng(it.latitude, it.longitude), 18f)
        }
    }
    
    // 2. Google Map Instance
    var googleMapInstance by remember { mutableStateOf<com.google.android.gms.maps.GoogleMap?>(null) }

    // 3. Kakao Map State
    var kakaoMapInstance by remember { mutableStateOf<KakaoMap?>(null) }
    
    // 3. Naver Map State
    var naverMapInstance by remember { mutableStateOf<NaverMap?>(null) }


    // Auto-follow Time Machine
    LaunchedEffect(tmIndex) {
        if (tmIndex >= 0 && tmIndex < points.size) {
            val p = points[tmIndex]
            when (mapProvider) {
                MapProvider.Google -> {
                    googleCameraState.animate(
                        com.google.android.gms.maps.CameraUpdateFactory.newLatLng(GoogleLatLng(p.latitude, p.longitude))
                    )
                }
                MapProvider.Kakao -> {
                    kakaoMapInstance?.moveCamera(
                        com.kakao.vectormap.camera.CameraUpdateFactory.newCenterPosition(com.kakao.vectormap.LatLng.from(p.latitude, p.longitude))
                    )
                }
                MapProvider.Naver -> {
                    val update = com.naver.maps.map.CameraUpdate.scrollTo(NaverLatLng(p.latitude, p.longitude))
                        .animate(com.naver.maps.map.CameraAnimation.Easing)
                    naverMapInstance?.moveCamera(update)
                }
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.White)) {
        val viewWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) { constraints.maxWidth }
        val viewHeightPx = with(androidx.compose.ui.platform.LocalDensity.current) { constraints.maxHeight }

        // Map Engine Selection
        when (mapProvider) {
            MapProvider.Google -> {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = googleCameraState,
                    uiSettings = MapUiSettings(zoomControlsEnabled = false, compassEnabled = false)
                ) {
                    MapEffect(Unit) { map ->
                        googleMapInstance = map
                    }
                    val map = googleMapInstance ?: return@GoogleMap
                    val proj = map.projection
                    
                    // [NEW] Calculate MPP for fixed pixel size dots
                    val centerPt = android.graphics.Point(viewWidthPx / 2, viewHeightPx / 2)
                    val ptR = android.graphics.Point(viewWidthPx / 2 + 100, viewHeightPx / 2)
                    val latLngL = proj.fromScreenLocation(centerPt)
                    val latLngR = proj.fromScreenLocation(ptR)
                    val results = FloatArray(1)
                    android.location.Location.distanceBetween(latLngL.latitude, latLngL.longitude, latLngR.latitude, latLngR.longitude, results)
                    val mpp = results[0] / 100.0
                    
                    // [V4] Independent Layers
                    val layers = mutableListOf<Pair<List<kr.alltodo.data.GpsAuthPoint>, Int>>()
                    if (isOriginalVisible) layers.add(points to 0)
                    if (isStage1Visible) layers.add((stage1Points.lastOrNull() ?: emptyList()) to 1)
                    if (isStage2Visible) layers.add((stage2Points.lastOrNull() ?: emptyList()) to 2)

                    layers.forEach { (layerPoints, stage) ->
                        val color = when (stage) {
                            1 -> Color(0xFF2E7D32) // Stage 1 (Dark Green)
                            2 -> Color.Red         // Stage 2 (Red)
                            else -> if (selectedTrack != null) Color.Blue else getPointColor(layerPoints.firstOrNull()?.status ?: 0)
                        }
                        val pixelRadius = when (stage) {
                            1 -> 4.0
                            2 -> 5.0
                            else -> if (selectedTrack != null) 3.0 else getPointRadius(pointSize)
                        }
                        
                        // [V3] Sequential Rendering Filter
                        layerPoints.forEach { p ->
                            if (p.timestamp <= filterTimestamp) {
                                Circle(
                                    center = GoogleLatLng(p.latitude, p.longitude),
                                    radius = pixelRadius * mpp, // Fixed pixel size on screen
                                    fillColor = color.copy(alpha = 0.8f),
                                    strokeColor = color,
                                    strokeWidth = 1f,
                                    zIndex = when(stage) {
                                        1 -> 60f
                                        2 -> 70f
                                        else -> 50f
                                    }
                                )
                            }
                        }
                    }
                    
                    // [NEW] Current Location marker & Accuracy circle
                    currentLocation?.let { loc ->
                        val pos = GoogleLatLng(loc.latitude, loc.longitude)
                        Circle(
                            center = pos,
                            radius = loc.accuracy.toDouble(),
                            fillColor = Color(0x224285F4),
                            strokeColor = Color(0x664285F4),
                            strokeWidth = 2f
                        )
                        Marker(
                            state = MarkerState(position = pos),
                            icon = com.google.android.gms.maps.model.BitmapDescriptorFactory.fromBitmap(createDotBitmap(Color(0xFF4285F4), 40)),
                            anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f)
                        )
                    }

                    // [FIX] Incremental Simplified Path
                    if (displayPath.isNotEmpty()) {
                        Polyline(
                            points = displayPath.map { GoogleLatLng(it.latitude, it.longitude) },
                            color = Color.Blue,
                            width = 12f,
                            jointType = com.google.android.gms.maps.model.JointType.ROUND,
                            startCap = com.google.android.gms.maps.model.RoundCap(),
                            endCap = com.google.android.gms.maps.model.RoundCap()
                        )
                    }
                }
            }
            MapProvider.Kakao -> {
                AndroidView(
                    factory = { ctx ->
                        KakaoMapView(ctx).apply {
                            start(object : MapLifeCycleCallback() {
                                override fun onMapDestroy() {}
                                override fun onMapError(e: Exception?) {}
                            }, object : KakaoMapReadyCallback() {
                                override fun onMapReady(map: KakaoMap) {
                                    kakaoMapInstance = map
                                    currentLocation?.let {
                                        map.moveCamera(com.kakao.vectormap.camera.CameraUpdateFactory.newCenterPosition(
                                            com.kakao.vectormap.LatLng.from(it.latitude, it.longitude), 18
                                        ))
                                    }
                                }
                                override fun getPosition(): com.kakao.vectormap.LatLng = com.kakao.vectormap.LatLng.from(currentLocation?.latitude ?: 37.5, currentLocation?.longitude ?: 127.0)
                            })
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                
                // Efficient Label Management
                LaunchedEffect(kakaoMapInstance, points, tmIndex, pointSize, displayPath, isOriginalVisible, isStage1Visible, isStage2Visible, stage1Points, stage2Points) {
                    val map = kakaoMapInstance ?: return@LaunchedEffect
                    val layer = map.labelManager?.getLayer("gpsLayer") ?: map.labelManager?.addLayer(com.kakao.vectormap.label.LabelLayerOptions.from("gpsLayer"))
                    val routeLayer = map.routeLineManager?.getLayer("pathLayer") ?: map.routeLineManager?.addLayer("pathLayer", 1000)
                    
                    layer?.removeAll()
                    routeLayer?.removeAll()
                    
                    // [V4] Independent Layers for Kakao
                    val layers = mutableListOf<Pair<List<kr.alltodo.data.GpsAuthPoint>, Int>>()
                    if (isOriginalVisible) layers.add(points to 0)
                    if (isStage1Visible) layers.add((stage1Points.lastOrNull() ?: emptyList()) to 1)
                    if (isStage2Visible) layers.add((stage2Points.lastOrNull() ?: emptyList()) to 2)

                    val dotBitmaps = mutableMapOf<Int, Bitmap>()
                    layers.forEach { (layerPoints, stage) ->
                        val color = when (stage) {
                            1 -> Color(0xFF2E7D32) // Dark Green
                            2 -> Color.Red
                            else -> if (selectedTrack != null) Color.Blue else getPointColor(layerPoints.firstOrNull()?.status ?: 0)
                        }
                        val radiusSize = when (stage) {
                            1 -> 4.0
                            2 -> 5.0
                            else -> if (selectedTrack != null) 3.0 else getPointRadius(pointSize)
                        }
                        val sizePx = (radiusSize * 10).toInt().coerceAtLeast(10)
                        
                        layerPoints.forEach { p ->
                            if (p.timestamp <= filterTimestamp) {
                                val styleKey = color.toArgb() * 31 + sizePx
                                val bitmap = dotBitmaps.getOrPut(styleKey) { createDotBitmap(color, sizePx) }
                                val style = com.kakao.vectormap.label.LabelStyle.from(bitmap).setAnchorPoint(0.5f, 0.5f)
                                val styles = map.labelManager?.addLabelStyles(com.kakao.vectormap.label.LabelStyles.from(style))
                                if (styles != null) {
                                    layer?.addLabel(com.kakao.vectormap.label.LabelOptions.from(com.kakao.vectormap.LatLng.from(p.latitude, p.longitude)).setStyles(styles).setRank(
                                        when(stage) {
                                            1 -> 60
                                            2 -> 70
                                            else -> 50
                                        }
                                    ))
                                }
                            }
                        }
                    }

                    // [FIX] Incremental Simplified Path for Kakao
                    if (displayPath.size >= 2) {
                        val latLngs = displayPath.map { com.kakao.vectormap.LatLng.from(it.latitude, it.longitude) }
                        val routeStyle = com.kakao.vectormap.route.RouteLineStyles.from(com.kakao.vectormap.route.RouteLineStyle.from(12f, AndroidColor.BLUE))
                        val segment = com.kakao.vectormap.route.RouteLineSegment.from(latLngs, routeStyle)
                        routeLayer?.addRouteLine(com.kakao.vectormap.route.RouteLineOptions.from(segment))
                    }
                    
                    // [NEW] Current Location
                    currentLocation?.let { loc ->
                         val color = Color(0xFF4285F4)
                         val bitmap = createDotBitmap(color, 40)
                         val style = com.kakao.vectormap.label.LabelStyle.from(bitmap).setAnchorPoint(0.5f, 0.5f)
                         val styles = map.labelManager?.addLabelStyles(com.kakao.vectormap.label.LabelStyles.from(style))
                         if (styles != null) {
                             layer?.addLabel(com.kakao.vectormap.label.LabelOptions.from(com.kakao.vectormap.LatLng.from(loc.latitude, loc.longitude))
                                .setStyles(styles)
                                .setTag("current_loc"))
                         }
                    }
                }
            }
            MapProvider.Naver -> {
                val naverMarkers = remember { mutableListOf<NaverMarker>() }
                val naverCircle = remember { mutableStateOf<com.naver.maps.map.overlay.CircleOverlay?>(null) }
                AndroidView(
                    factory = { ctx ->
                        NaverMapView(ctx).apply {
                            getMapAsync { map ->
                                naverMapInstance = map
                                map.uiSettings.isZoomControlEnabled = false
                                currentLocation?.let {
                                    map.moveCamera(com.naver.maps.map.CameraUpdate.scrollAndZoomTo(NaverLatLng(it.latitude, it.longitude), 18.0))
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                
                LaunchedEffect(naverMapInstance, points, tmIndex, pointSize, currentLocation, displayPath, isOriginalVisible, isStage1Visible, isStage2Visible, stage1Points, stage2Points) {
                    val map = naverMapInstance ?: return@LaunchedEffect
                    naverMarkers.forEach { it.map = null }
                    naverMarkers.clear()
                    naverCircle.value?.map = null
                    naverCircle.value = null
                    
                    // [V4] Independent Layers for Naver
                    val layers = mutableListOf<Pair<List<kr.alltodo.data.GpsAuthPoint>, Int>>()
                    if (isOriginalVisible) layers.add(points to 0)
                    if (isStage1Visible) layers.add((stage1Points.lastOrNull() ?: emptyList()) to 1)
                    if (isStage2Visible) layers.add((stage2Points.lastOrNull() ?: emptyList()) to 2)

                    val dotImages = mutableMapOf<Int, NaverOverlayImage>()
                    layers.forEach { (layerPoints, stage) ->
                        val color = when (stage) {
                            1 -> Color(0xFF2E7D32) // Dark Green
                            2 -> Color.Red
                            else -> if (selectedTrack != null) Color.Blue else getPointColor(layerPoints.firstOrNull()?.status ?: 0)
                        }
                        val radiusSize = when (stage) {
                            1 -> 4.0
                            2 -> 5.0
                            else -> if (selectedTrack != null) 3.0 else getPointRadius(pointSize)
                        }
                        val sizePx = (radiusSize * 10).toInt().coerceAtLeast(10)
                        
                        layerPoints.forEach { p ->
                            if (p.timestamp <= filterTimestamp) {
                                val styleKey = color.toArgb() * 31 + sizePx
                                val overlayImage = dotImages.getOrPut(styleKey) { NaverOverlayImage.fromBitmap(createDotBitmap(color, sizePx)) }
                                
                                val marker = NaverMarker().apply {
                                    position = NaverLatLng(p.latitude, p.longitude)
                                    icon = overlayImage
                                    anchor = android.graphics.PointF(0.5f, 0.5f)
                                    zIndex = when(stage) {
                                        1 -> 60
                                        2 -> 70
                                        else -> 50
                                    }
                                }
                                marker.map = map
                                naverMarkers.add(marker)
                            }
                        }
                    }

                    // [FIX] Incremental Simplified Path for Naver
                    if (displayPath.size >= 2) {
                        val path = com.naver.maps.map.overlay.PathOverlay()
                        path.coords = displayPath.map { NaverLatLng(it.latitude, it.longitude) }
                        path.width = 15
                        path.color = AndroidColor.BLUE
                        path.map = map
                    }
                    
                    // [NEW] Current Location Marker & Accuracy Circle for Naver
                    currentLocation?.let { loc ->
                        val pos = NaverLatLng(loc.latitude, loc.longitude)
                        
                        // Blue Dot Marker
                        val currentMarker = NaverMarker()
                        currentMarker.position = pos
                        currentMarker.icon = NaverOverlayImage.fromBitmap(createDotBitmap(Color(0xFF4285F4), 40))
                        currentMarker.anchor = android.graphics.PointF(0.5f, 0.5f)
                        currentMarker.map = map
                        naverMarkers.add(currentMarker)
                        
                        // Accuracy Circle
                        val circle = com.naver.maps.map.overlay.CircleOverlay()
                        circle.center = pos
                        circle.radius = loc.accuracy.toDouble()
                        circle.color = AndroidColor.argb(34, 66, 133, 244)
                        circle.outlineWidth = 2
                        circle.outlineColor = AndroidColor.argb(102, 66, 133, 244)
                        circle.map = map
                        naverCircle.value = circle
                    }
                }
            }
        }

        // Saved Tracks List Overlay (Full Screen to block interactions)
        if (!isTracking && selectedTrack == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .zIndex(100f), // Very high zIndex
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.85f)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("저장된 기록", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, "닫기", tint = Color.Black)
                            }
                        }
                        
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                        
                        if (savedTracks.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("저장된 기록이 없습니다.", color = Color.Gray)
                            }
                        } else {
                             LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                 items(savedTracks) { track ->
                                     Card(
                                         onClick = { 
                                             localSelectedTrack = track
                                             localPoints = track.points
                                             stage1Points = emptyList()
                                             stage2Points = emptyList()
                                             tmIndex = 0
                                             isPlaying = true
                                             isOriginalVisible = true
                                             isStage1Visible = false
                                             isStage2Visible = false
                                         },
                                         modifier = Modifier.fillMaxWidth(),
                                         colors = CardDefaults.cardColors(containerColor = Color(0xFFF9F9F9)),
                                         shape = RoundedCornerShape(4.dp)
                                     ) {
                                         Row(
                                             modifier = Modifier.padding(10.dp).fillMaxWidth(),
                                             verticalAlignment = Alignment.CenterVertically,
                                             horizontalArrangement = Arrangement.spacedBy(10.dp)
                                         ) {
                                             // Time (Black, Bold) - Clickable for Full Pruning
                                             Text(
                                                 SimpleDateFormat("MM/dd HH:mm:ss", Locale.getDefault()).format(Date(track.startTime)), 
                                                 fontSize = 15.sp, 
                                                 color = Color.Black, 
                                                 fontWeight = FontWeight.Bold,
                                                 modifier = Modifier.clickable { 
                                                     localSelectedTrack = track
                                                     localPoints = track.points
                                                     isOriginalVisible = true
                                                     isStage1Visible = true
                                                     isStage2Visible = true
                                                     useFixedInterval = true
                                                     tmIndex = 0
                                                     isPlaying = true
                                                     
                                                     scope.launch {
                                                         if (stage1Points.isEmpty()) {
                                                             prune1(track.points)
                                                             while (isSimplifying) { delay(50) }
                                                         }
                                                     }
                                                 }
                                             )
                                             
                                             Text(">", fontSize = 13.sp, color = Color.Gray)
                                             
                                             // Duration (Dark Gray)
                                             Text(formatDuration(track.durationSeconds), fontSize = 15.sp, color = Color.DarkGray, fontWeight = FontWeight.Medium)
                                             
                                             Spacer(modifier = Modifier.weight(1f))
                                             
                                             // Positions (Green)
                                             Text(String.format("%,d", track.totalPoints), fontSize = 15.sp, color = Color(0xFF006400), fontWeight = FontWeight.ExtraBold)
                                             
                                             // Jumpy (Orange)
                                             Text(String.format("%d", track.jumpyCount), fontSize = 15.sp, color = Color(0xFFCC8400), fontWeight = FontWeight.ExtraBold)
                                             
                                             // Impossible (Red)
                                             Text(String.format("%d", track.impossibleCount), fontSize = 15.sp, color = Color.Red, fontWeight = FontWeight.ExtraBold)
                                             
                                             // [NEW] Delete Button
                                             IconButton(
                                                 onClick = { viewModel.deleteTrack(track.id) },
                                                 modifier = Modifier.size(32.dp)
                                             ) {
                                                 Icon(
                                                     imageVector = Icons.Default.Delete,
                                                     contentDescription = "삭제",
                                                     tint = Color.Gray,
                                                     modifier = Modifier.size(18.dp)
                                                 )
                                             }
                                         }
                                     }
                                 }
                             }
                        }
                    }
                }
            }
        }

        // Top Controls Order: [Pin S|M|L] [Clock N|F|S] [Close]
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd) // Align to top right
                .padding(top = 40.dp, end = 16.dp) // [FIX] Down by 24pt (16+24=40)
                .zIndex(50f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Pin Size: [Icon | S | M | L]
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(8.dp),
                shadowElevation = 4.dp,
                border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                    Icon(Icons.Default.PushPin, null, modifier = Modifier.size(18.dp).padding(start = 4.dp), tint = Color.Gray)
                    listOf("S", "M", "L").forEachIndexed { index, label ->
                        TextButton(
                            onClick = { viewModel.setPointSize(index) },
                            modifier = Modifier.size(36.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                label,
                                fontSize = 15.sp,
                                fontWeight = if(pointSize == index) FontWeight.ExtraBold else FontWeight.Normal,
                                color = if(pointSize == index) Color(0xFF4285F4) else Color.Gray
                            )
                        }
                    }
                }
            }

            // 2. Time Machine: [Icon | N | F | S]
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(8.dp),
                shadowElevation = 4.dp,
                border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                    IconButton(
                        onClick = { 
                            if (localPoints.isNotEmpty()) {
                                isPlaying = !isPlaying 
                                if (isPlaying && tmIndex < 0) tmIndex = 0
                            }
                        }, 
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if(isPlaying) Icons.Default.StopCircle else Icons.Default.Timer,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = if(isPlaying) Color.Red else Color.Gray
                        )
                    }
                    listOf("N", "F", "S").forEachIndexed { index, label ->
                        val speedIdx = index + 1
                        TextButton(
                            onClick = { tmSpeed = speedIdx },
                            modifier = Modifier.size(36.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                label,
                                fontSize = 15.sp,
                                fontWeight = if(tmSpeed == speedIdx) FontWeight.ExtraBold else FontWeight.Normal,
                                color = if(tmSpeed == speedIdx) Color(0xFF4CAF50) else Color.Gray
                            )
                        }
                    }
                }
            }

            // 3. Close Button
            FloatingActionButton(
                onClick = onDismiss,
                containerColor = Color.White,
                contentColor = Color.Black,
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(8.dp),
                elevation = FloatingActionButtonDefaults.elevation(4.dp)
            ) {
                Icon(Icons.Default.Close, "닫기", modifier = Modifier.size(24.dp))
            }
        }

        // Right Side Controls: [닫기] [현재 위치] [확대] [축소] [나침판]
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FloatingActionButton(
                onClick = {
                    currentLocation?.let {
                        scope.launch {
                             when(mapProvider) {
                                 MapProvider.Google -> googleCameraState.animate(com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(GoogleLatLng(it.latitude, it.longitude), 18f))
                                 MapProvider.Kakao -> kakaoMapInstance?.moveCamera(com.kakao.vectormap.camera.CameraUpdateFactory.newCenterPosition(com.kakao.vectormap.LatLng.from(it.latitude, it.longitude), 18), com.kakao.vectormap.camera.CameraAnimation.from(500, true, true))
                                 MapProvider.Naver -> naverMapInstance?.moveCamera(com.naver.maps.map.CameraUpdate.scrollAndZoomTo(NaverLatLng(it.latitude, it.longitude), 18.0).animate(com.naver.maps.map.CameraAnimation.Easing))
                             }
                        }
                    }
                },
                containerColor = Color.White,
                contentColor = Color(0xFF4285F4),
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Default.MyLocation, "현재 위치")
            }
            
            FloatingActionButton(
                onClick = {
                    scope.launch {
                        when(mapProvider) {
                            MapProvider.Google -> googleCameraState.animate(com.google.android.gms.maps.CameraUpdateFactory.zoomIn())
                            MapProvider.Kakao -> kakaoMapInstance?.moveCamera(com.kakao.vectormap.camera.CameraUpdateFactory.zoomIn())
                            MapProvider.Naver -> naverMapInstance?.moveCamera(com.naver.maps.map.CameraUpdate.zoomIn())
                        }
                    }
                },
                containerColor = Color.White,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Default.Add, "확대")
            }
            
            FloatingActionButton(
                onClick = {
                    scope.launch {
                         when(mapProvider) {
                            MapProvider.Google -> googleCameraState.animate(com.google.android.gms.maps.CameraUpdateFactory.zoomOut())
                            MapProvider.Kakao -> kakaoMapInstance?.moveCamera(com.kakao.vectormap.camera.CameraUpdateFactory.zoomOut())
                            MapProvider.Naver -> naverMapInstance?.moveCamera(com.naver.maps.map.CameraUpdate.zoomOut())
                        }
                    }
                },
                containerColor = Color.White,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Default.Remove, "축소")
            }
            
            FloatingActionButton(
                onClick = {
                    scope.launch {
                        when(mapProvider) {
                            MapProvider.Google -> {
                                val current = googleCameraState.position
                                googleCameraState.animate(com.google.android.gms.maps.CameraUpdateFactory.newCameraPosition(GoogleCameraPosition.builder(current).bearing(0f).tilt(0f).build()))
                            }
                            MapProvider.Kakao -> {
                                kakaoMapInstance?.moveCamera(com.kakao.vectormap.camera.CameraUpdateFactory.rotateTo(0.0), com.kakao.vectormap.camera.CameraAnimation.from(500, true, true))
                            }
                            MapProvider.Naver -> {
                                naverMapInstance?.moveCamera(com.naver.maps.map.CameraUpdate.withParams(com.naver.maps.map.CameraUpdateParams().rotateTo(0.0)).animate(com.naver.maps.map.CameraAnimation.Easing))
                            }
                        }
                    }
                },
                containerColor = Color.White,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Default.Explore, "나침반")
            }
        }
        
        // [V3] START Button - Visible ONLY in Record List side (selectedTrack == null AND !isTracking)
        if (selectedTrack == null && !isTracking) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp) 
                    .zIndex(200f)
            ) {
                Surface(
                    onClick = { onStartTracking() },
                    modifier = Modifier.size(90.dp),
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = Color.Red,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(text = "시작", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }

        // [V3] END Button - Visible ONLY when Tracking is active
        if (isTracking) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp)
                    .zIndex(200f)
            ) {
                Surface(
                    onClick = { viewModel.stopTrackingAndSave() },
                    modifier = Modifier.size(90.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Red,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(text = "끝", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }

        // [NEW] Orbit Pruning Control Bar (Visible when a track is selected)
        if (selectedTrack != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp)
                    .zIndex(210f)
            ) {
                Surface(
                    color = Color.White.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(16.dp),
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth(0.9f).height(70.dp),
                    border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("궤적 가지치기", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                             // Stage 0 Button [0] - Original
                             Surface(
                                 onClick = { isOriginalVisible = !isOriginalVisible },
                                 color = if (isOriginalVisible) Color.Blue else Color(0xFFF0F0F0),
                                 shape = RoundedCornerShape(8.dp),
                                 modifier = Modifier.size(width = 44.dp, height = 40.dp)
                             ) {
                                 Box(contentAlignment = Alignment.Center) {
                                     Text("0", color = if (isOriginalVisible) Color.White else Color.Black, fontWeight = FontWeight.Bold)
                                 }
                             }

                             // Stage 1 Button [1]
                             Surface(
                                 onClick = { 
                                     isStage1Visible = !isStage1Visible 
                                     if (isStage1Visible && stage1Points.isEmpty()) {
                                         prune1(points)
                                     }
                                 },
                                 color = if (isStage1Visible) Color(0xFF2E7D32) else Color(0xFFF0F0F0),
                                 shape = RoundedCornerShape(8.dp),
                                 modifier = Modifier.size(width = 44.dp, height = 40.dp)
                             ) {
                                 Box(contentAlignment = Alignment.Center) {
                                     Text("1", color = if (isStage1Visible) Color.White else Color.Black, fontWeight = FontWeight.Bold)
                                 }
                             }

                             // Stage 2 Button [2]
                             Surface(
                                 onClick = { 
                                     isStage2Visible = !isStage2Visible 
                                     if (isStage2Visible && stage2Points.isEmpty()) {
                                         scope.launch {
                                             if (stage1Points.isEmpty()) {
                                                 prune1(points)
                                                 while (isSimplifying) { delay(50) }
                                             }
                                             prune2(stage1Points.lastOrNull() ?: emptyList())
                                         }
                                     }
                                 },
                                 color = if (isStage2Visible) Color.Red else Color(0xFFF0F0F0),
                                 shape = RoundedCornerShape(8.dp),
                                 modifier = Modifier.size(width = 44.dp, height = 40.dp)
                             ) {
                                 Box(contentAlignment = Alignment.Center) {
                                     Text("2", color = if (isStage2Visible) Color.White else Color.Black, fontWeight = FontWeight.Bold)
                                 }
                             }

                             Spacer(modifier = Modifier.width(8.dp))

                             // Close Button [X]
                             IconButton(
                                 onClick = { 
                                     localSelectedTrack = null
                                     localPoints = emptyList()
                                     tmIndex = -1
                                     isPlaying = false
                                     useFixedInterval = false
                                 },
                                 modifier = Modifier.size(40.dp).background(Color(0xFFEEEEEE), RoundedCornerShape(20.dp))
                             ) {
                                 Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Black, modifier = Modifier.size(20.dp))
                             }
                        }
                    }
                }
            }
        }
    }
}

private fun getPointColor(status: Int): Color {
    return when (status) {
        1 -> Color(0xFFFFA500) // Orange (Bounced)
        2 -> Color.Red         // Red (Impossible)
        else -> Color(0xFF228B22) // ForestGreen (Normal)
    }
}

private fun getPointRadius(pointSize: Int): Double {
    return when(pointSize) {
        0 -> 0.4
        1 -> 0.8
        2 -> 2.0
        else -> 0.8
    }
}

private fun createDotBitmap(color: Color, size: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint().apply {
        this.color = color.toArgb()
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
    return bitmap
}

private fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) {
        String.format("%02d:%02d:%02d", h, m, s)
    } else {
        String.format("%02d:%02d", m, s)
    }
}
