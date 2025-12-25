package kr.alltodo.ui.components

import androidx.compose.foundation.background
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
    onDismiss: () -> Unit
) {
    val points by viewModel.points.collectAsState()
    val pointSize by viewModel.pointSize.collectAsState()
    val speed by viewModel.timeMachineSpeed.collectAsState()
    val tmIndex by viewModel.timeMachineIndex.collectAsState()
    val isPlaying by viewModel.isTimeMachinePlaying.collectAsState()
    
    val isTracking by viewModel.isTracking.collectAsState()
    val savedTracks by viewModel.savedTracks.collectAsState()
    val selectedTrack by viewModel.selectedTrack.collectAsState()
    val simplifiedPoints by viewModel.simplifiedPoints.collectAsState()
    val isSimplifying by viewModel.isSimplifying.collectAsState()
    
    val scope = rememberCoroutineScope()
    
    // [FIX] Incremental Path Logic: Grow path as playback moves
    val displayPath = remember(simplifiedPoints, tmIndex, points) {
        if (tmIndex >= 0 && points.isNotEmpty()) {
            val head = if (tmIndex < points.size) points[tmIndex] else points.last()
            val list = simplifiedPoints.filter { it.timestamp <= head.timestamp }.toMutableList()
            // Always append current head to close the gap between simplified segments
            if (list.isEmpty() || list.last().timestamp < head.timestamp) {
                list.add(head)
            }
            list
        } else {
            simplifiedPoints
        }
    }
    
    // 1. Google Camera State
    val googleCameraState = rememberCameraPositionState {
        currentLocation?.let {
            position = GoogleCameraPosition.fromLatLngZoom(GoogleLatLng(it.latitude, it.longitude), 18f)
        }
    }
    
    // 2. Kakao Map State
    var kakaoMapInstance by remember { mutableStateOf<KakaoMap?>(null) }
    
    // 3. Naver Map State
    var naverMapInstance by remember { mutableStateOf<NaverMap?>(null) }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

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

    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
        // Map Engine Selection
        when (mapProvider) {
            MapProvider.Google -> {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = googleCameraState,
                    uiSettings = MapUiSettings(zoomControlsEnabled = false, compassEnabled = false)
                ) {
                    // [OPTIMIZATION] Past Points (Sparse)
                    val displayPoints = if (tmIndex >= 0) points.take(tmIndex + 1) else points
                    // To prevent engine lag, only draw dots every 5th point if we have too many
                    val step = (displayPoints.size / 500).coerceAtLeast(1)
                    displayPoints.forEachIndexed { idx, p ->
                        if (idx % step == 0 || idx == displayPoints.size - 1) {
                            val color = getPointColor(p.status)
                            val radius = getPointRadius(pointSize)
                            Circle(
                                center = GoogleLatLng(p.latitude, p.longitude),
                                radius = radius,
                                fillColor = color.copy(alpha = 0.8f),
                                strokeColor = color,
                                strokeWidth = 1f
                            )
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
                LaunchedEffect(kakaoMapInstance, points, tmIndex, pointSize, displayPath) {
                    val map = kakaoMapInstance ?: return@LaunchedEffect
                    val layer = map.labelManager?.getLayer("gpsLayer") ?: map.labelManager?.addLayer(com.kakao.vectormap.label.LabelLayerOptions.from("gpsLayer"))
                    val routeLayer = map.routeLineManager?.getLayer("pathLayer") ?: map.routeLineManager?.addLayer("pathLayer", 1000)
                    
                    layer?.removeAll()
                    routeLayer?.removeAll()
                    
                    val displayPoints = if (tmIndex >= 0) points.take(tmIndex + 1) else points
                    // [OPTIMIZATION] Sparse dots for Kakao
                    val step = (displayPoints.size / 500).coerceAtLeast(1)
                    val dotBitmaps = mutableMapOf<Int, Bitmap>()
                    
                    displayPoints.forEachIndexed { idx, p ->
                         if (idx % step != 0 && idx != displayPoints.size - 1) return@forEachIndexed
                         
                         val color = getPointColor(p.status)
                         val radius = getPointRadius(pointSize)
                         val sizePx = (radius * 10).toInt().coerceAtLeast(10)
                         val styleKey = color.toArgb() * 31 + sizePx
                         
                         val bitmap = dotBitmaps.getOrPut(styleKey) { createDotBitmap(color, sizePx) }
                         val style = com.kakao.vectormap.label.LabelStyle.from(bitmap).setAnchorPoint(0.5f, 0.5f)
                         val styles = map.labelManager?.addLabelStyles(com.kakao.vectormap.label.LabelStyles.from(style))
                         
                         layer?.addLabel(com.kakao.vectormap.label.LabelOptions.from(com.kakao.vectormap.LatLng.from(p.latitude, p.longitude)).setStyles(styles))
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
                         layer?.addLabel(com.kakao.vectormap.label.LabelOptions.from(com.kakao.vectormap.LatLng.from(loc.latitude, loc.longitude))
                            .setStyles(styles)
                            .setTag("current_loc"))
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
                
                LaunchedEffect(naverMapInstance, points, tmIndex, pointSize, currentLocation, displayPath) {
                    val map = naverMapInstance ?: return@LaunchedEffect
                    naverMarkers.forEach { it.map = null }
                    naverMarkers.clear()
                    naverCircle.value?.map = null
                    naverCircle.value = null
                    
                    val displayPoints = if (tmIndex >= 0) points.take(tmIndex + 1) else points
                    val step = (displayPoints.size / 500).coerceAtLeast(1)
                    val dotImages = mutableMapOf<Int, NaverOverlayImage>()
                    
                    displayPoints.forEachIndexed { idx, p ->
                        if (idx % step != 0 && idx != displayPoints.size - 1) return@forEachIndexed
                        
                        val color = getPointColor(p.status)
                        val radius = getPointRadius(pointSize)
                        val sizePx = (radius * 16).toInt().coerceAtLeast(16)
                        val styleKey = color.toArgb() * 31 + sizePx
                        
                        val image = dotImages.getOrPut(styleKey) { NaverOverlayImage.fromBitmap(createDotBitmap(color, sizePx)) }
                        
                        val marker = NaverMarker()
                        marker.position = NaverLatLng(p.latitude, p.longitude)
                        marker.icon = image
                        marker.anchor = android.graphics.PointF(0.5f, 0.5f)
                        marker.map = map
                        naverMarkers.add(marker)
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
                                     val sdf = SimpleDateFormat("MM/dd HH:mm:ss", Locale.getDefault())
                                     val durationStr = String.format("%02d:%02d:%02d", track.durationSeconds / 3600, (track.durationSeconds % 3600) / 60, track.durationSeconds % 60)
                                     
                                     Card(
                                         onClick = { viewModel.selectTrack(track) },
                                         modifier = Modifier.fillMaxWidth(),
                                         colors = CardDefaults.cardColors(containerColor = Color(0xFFF9F9F9)),
                                         shape = RoundedCornerShape(4.dp)
                                     ) {
                                         Row(
                                             modifier = Modifier.padding(10.dp).fillMaxWidth(),
                                             verticalAlignment = Alignment.CenterVertically,
                                             horizontalArrangement = Arrangement.spacedBy(10.dp)
                                         ) {
                                             // Time (Black, Bold)
                                             Text(SimpleDateFormat("MM/dd HH:mm:ss", Locale.getDefault()).format(Date(track.startTime)), fontSize = 15.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                                             
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
                    IconButton(onClick = { viewModel.toggleTimeMachine() }, modifier = Modifier.size(32.dp)) {
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
                            onClick = { viewModel.setTimeMachineSpeed(speedIdx) },
                            modifier = Modifier.size(36.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                label,
                                fontSize = 15.sp,
                                fontWeight = if(speed == speedIdx) FontWeight.ExtraBold else FontWeight.Normal,
                                color = if(speed == speedIdx) Color(0xFF4CAF50) else Color.Gray
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
        
        // Start/End Buttons fixed at bottom with high zIndex
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .zIndex(200f) // Priority over List Overlay
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedButton(
                    onClick = { 
                        if (selectedTrack != null) {
                            viewModel.simplifyCurrentTrack()
                        } else {
                            viewModel.startTracking() 
                        }
                    },
                    enabled = !isTracking,
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if(!isTracking) (if(selectedTrack != null) Color(0xFF2196F3) else Color(0xFF4CAF50)) else Color.Transparent,
                        contentColor = if(!isTracking) Color.White else Color.Gray.copy(alpha = 0.5f),
                        disabledContentColor = Color.Gray.copy(alpha = 0.5f)
                    ),
                    border = BorderStroke(2.dp, if(!isTracking) (if(selectedTrack != null) Color(0xFF2196F3) else Color(0xFF4CAF50)) else Color.Gray.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.width(130.dp).height(56.dp)
                ) {
                    if (isSimplifying) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text(if (selectedTrack != null) "경로보기" else "시작", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
                
                OutlinedButton(
                    onClick = { 
                        if (selectedTrack != null) {
                            viewModel.selectTrack(null)
                        } else {
                            viewModel.stopTrackingAndSave() 
                        }
                    },
                    enabled = isTracking || selectedTrack != null,
                    colors = ButtonDefaults.outlinedButtonColors(
                        // [FIX] Background Gray 1, Text Gray 6
                        containerColor = if(isTracking || selectedTrack != null) Color(0xFFF5F5F5) else Color.Transparent,
                        contentColor = if(isTracking || selectedTrack != null) Color(0xFF666666) else Color.Gray.copy(alpha = 0.5f),
                        disabledContentColor = Color.Gray.copy(alpha = 0.5f)
                    ),
                    border = BorderStroke(2.dp, if(isTracking || selectedTrack != null) Color(0xFFCCCCCC) else Color.Gray.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.width(130.dp).height(56.dp)
                ) {
                    Text(if (selectedTrack != null) "목록으로" else "끝", fontSize = 18.sp, fontWeight = FontWeight.Bold)
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
