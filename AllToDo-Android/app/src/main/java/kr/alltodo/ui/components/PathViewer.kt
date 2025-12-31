package kr.alltodo.ui.components

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import java.util.Arrays

import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.LatLng as KakaoLatLng
import com.kakao.vectormap.label.LabelLayerOptions
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelStyle
import com.kakao.vectormap.label.LabelStyles
import com.kakao.vectormap.route.RouteLineOptions
import com.kakao.vectormap.route.RouteLineSegment
import com.kakao.vectormap.route.RouteLineStyle
import com.kakao.vectormap.route.RouteLineStyles
import kotlinx.coroutines.delay
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.CameraUpdate
import com.naver.maps.map.compose.ExperimentalNaverMapApi
import kr.alltodo.ui.MapProvider
import kr.alltodo.ui.theme.AllToDoGreen
import com.google.android.gms.maps.model.LatLng as GoogleLatLng
import com.google.android.gms.maps.model.LatLngBounds as GoogleLatLngBounds

@Composable
fun PathViewer(
    todo: kr.alltodo.data.TodoItem,
    pathData: List<kr.alltodo.data.PathItem>,
    mapProvider: MapProvider,
    onClose: () -> Unit
) {
    var lineColor by remember { mutableStateOf(Color.Red) }
    var lineThickness by remember { mutableStateOf(2.5.dp) }
    val density = androidx.compose.ui.platform.LocalDensity.current.density



    val pathPoints = remember(pathData) {
        pathData.map { LatLng(it.int_lat / 100_000.0, it.int_long / 100_000.0) }
    }

    // [ABSOLUTE ROOT] zIndex 9999
    Box(modifier = Modifier.fillMaxSize().zIndex(9999f)) {
        
        // 1. Scrim Layer (Click to close)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable { 
                    Log.d("PathViewer", ">>> Background Scrim Clicked")
                    Log.e("PathViewer", ">>> Background Scrim Clicked (ERROR LEVEL)")
                    onClose() 
                }
        )

        // 2. Main Map Container
        Card(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Map View
                Box(modifier = Modifier.fillMaxSize().zIndex(1f)) {
                    when (mapProvider) {
                        MapProvider.Naver -> NaverPathMap(todo, pathPoints, lineColor, lineThickness)
                        MapProvider.Google -> GooglePathMap(todo, pathPoints, lineColor, lineThickness)
                        MapProvider.Kakao -> KakaoPathMap(todo, pathPoints, lineColor, lineThickness)

                    }
                }
                
                // Bottom Settings
                Card(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).zIndex(10f),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("색상", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(40.dp))
                            listOf(Color.Red, AllToDoGreen, Color.Blue, Color.Black).forEach { color ->
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .padding(4.dp)
                                        .background(color, CircleShape)
                                        .let { if (lineColor == color) it.background(color.copy(alpha = 0.3f), CircleShape).padding(2.dp).background(color, CircleShape) else it }
                                        .clickable { lineColor = color }
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("두께", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(40.dp))
                            Slider(
                                value = lineThickness.value,
                                onValueChange = { lineThickness = it.dp },
                                valueRange = 1f..10f,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // 3. [최종 병기] 닫기 버튼 - 무조건 클릭되는 48dp 터치 영역
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(24.dp)
                .size(48.dp) // 표준 터치 타겟 크기
                .zIndex(999999f) // 최상위 zIndex
                .background(Color.White.copy(alpha = 0.9f), androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                .clickable {
                    Log.d("PathViewer", ">>> [TOUCH] 경로 닫기 클릭됨")
                    onClose()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "닫기",
                tint = Color.Black,
                modifier = Modifier.size(30.dp)
            )
        }
    }
}

@OptIn(ExperimentalNaverMapApi::class)
@Composable
fun NaverPathMap(todo: kr.alltodo.data.TodoItem, points: List<LatLng>, color: Color, width: androidx.compose.ui.unit.Dp) {
    val initialCenter = LatLng((todo.int_lat ?: 0) / 100_000.0, (todo.int_long ?: 0) / 100_000.0)
    val cameraPositionState = com.naver.maps.map.compose.rememberCameraPositionState {
        position = com.naver.maps.map.CameraPosition(initialCenter, 15.0)
    }
    LaunchedEffect(points) {
        if (points.isNotEmpty()) {
            Log.d("PathViewer", ">>> NaverPathMap: Drawing ${points.size} points. First: ${points.firstOrNull()}")
            if (points.size >= 2) {
                val builder = com.naver.maps.geometry.LatLngBounds.Builder()
                points.forEach { builder.include(it) }
                cameraPositionState.animate(CameraUpdate.fitBounds(builder.build(), 100))
            } else {
                cameraPositionState.animate(CameraUpdate.scrollAndZoomTo(points.first(), 15.0))
            }
        } else {
            Log.d("PathViewer", ">>> NaverPathMap: Points list is EMPTY")
        }
    }
    com.naver.maps.map.compose.NaverMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        uiSettings = com.naver.maps.map.compose.MapUiSettings(isZoomControlEnabled = false)
    ) {
        if (points.size >= 2) {
            com.naver.maps.map.compose.PathOverlay(coords = points, color = color, width = width, outlineWidth = 0.dp)
            
            // Start/End Markers
            com.naver.maps.map.compose.Marker(
                state = com.naver.maps.map.compose.rememberMarkerState(position = points.first()),
                icon = com.naver.maps.map.overlay.OverlayImage.fromResource(kr.alltodo.R.drawable.map_pin_01),
                width = 24.dp, height = 30.dp
            )
            com.naver.maps.map.compose.Marker(
                state = com.naver.maps.map.compose.rememberMarkerState(position = points.last()),
                icon = com.naver.maps.map.overlay.OverlayImage.fromResource(kr.alltodo.R.drawable.map_pin_02),
                width = 24.dp, height = 30.dp
            )
        }
    }
}

@Composable
fun GooglePathMap(todo: kr.alltodo.data.TodoItem, points: List<LatLng>, color: Color, width: androidx.compose.ui.unit.Dp) {
    val initialCenter = GoogleLatLng((todo.int_lat ?: 0) / 100_000.0, (todo.int_long ?: 0) / 100_000.0)
    val gPoints = remember(points) { points.map { GoogleLatLng(it.latitude, it.longitude) } }
    val cameraPositionState = com.google.maps.android.compose.rememberCameraPositionState {
        position = com.google.android.gms.maps.model.CameraPosition.fromLatLngZoom(initialCenter, 15f)
    }
    LaunchedEffect(gPoints) {
        if (gPoints.isNotEmpty()) {
            try {
                if (gPoints.size >= 2) {
                    val builder = GoogleLatLngBounds.builder()
                    gPoints.forEach { builder.include(it) }
                    cameraPositionState.animate(com.google.android.gms.maps.CameraUpdateFactory.newLatLngBounds(builder.build(), 100))
                } else {
                    cameraPositionState.move(com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(gPoints.first(), 15f))
                }
            } catch (e: Exception) {
                cameraPositionState.move(com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(gPoints.first(), 15f))
            }
        }
    }
    com.google.maps.android.compose.GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        uiSettings = com.google.maps.android.compose.MapUiSettings(zoomControlsEnabled = false)
    ) {
        if (gPoints.size >= 2) {
            val px = width.value * androidx.compose.ui.platform.LocalDensity.current.density
            com.google.maps.android.compose.Polyline(points = gPoints, color = color, width = px, zIndex = 5f)
            
            // Start/End Markers
            com.google.maps.android.compose.Marker(
                state = com.google.maps.android.compose.rememberMarkerState(position = gPoints.first()),
                icon = com.google.android.gms.maps.model.BitmapDescriptorFactory.fromResource(kr.alltodo.R.drawable.map_pin_01)
            )
            com.google.maps.android.compose.Marker(
                state = com.google.maps.android.compose.rememberMarkerState(position = gPoints.last()),
                icon = com.google.android.gms.maps.model.BitmapDescriptorFactory.fromResource(kr.alltodo.R.drawable.map_pin_02)
            )
        }
    }
}

@Composable
fun KakaoPathMap(todo: kr.alltodo.data.TodoItem, points: List<LatLng>, color: Color, width: androidx.compose.ui.unit.Dp) {
    val initialCenter = KakaoLatLng.from((todo.int_lat ?: 0) / 100_000.0, (todo.int_long ?: 0) / 100_000.0)
    val kPoints = remember(points) { points.map { KakaoLatLng.from(it.latitude, it.longitude) } }
    var kakaoMap by remember { mutableStateOf<KakaoMap?>(null) }
    val colorInt = android.graphics.Color.argb((color.alpha * 255).toInt(), (color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt())
    val density = LocalDensity.current.density

    LaunchedEffect(kakaoMap, kPoints, colorInt, width, density) {
        val map = kakaoMap ?: return@LaunchedEffect
        if (kPoints.isEmpty()) return@LaunchedEffect
        map.routeLineManager?.getLayer("pathLayer")?.removeAll()
        if (kPoints.size >= 2) {
            val manager = map.routeLineManager
            val layer = manager?.getLayer("pathLayer") ?: manager?.addLayer("pathLayer", 1000)
            val px = width.value * density
            val segment = RouteLineSegment.from(kPoints, RouteLineStyles.from(RouteLineStyle.from(px, colorInt)))
            layer?.addRouteLine(RouteLineOptions.from(Arrays.asList(segment)))

            // Start/End Markers (Labels in Kakao)
            val labelManager = map.labelManager
            val labelLayer = labelManager?.getLayer("markerLayer") ?: labelManager?.addLayer(LabelLayerOptions.from("markerLayer"))
            
            labelLayer?.removeAll()
            labelLayer?.addLabel(LabelOptions.from("start", kPoints.first()).setStyles(LabelStyles.from(LabelStyle.from(kr.alltodo.R.drawable.map_pin_01).setAnchorPoint(0.5f, 1f))))
            labelLayer?.addLabel(LabelOptions.from("end", kPoints.last()).setStyles(LabelStyles.from(LabelStyle.from(kr.alltodo.R.drawable.map_pin_02).setAnchorPoint(0.5f, 1f))))

            // Refined Fit Bounds with Delay & Animation
            delay(300)
            map.moveCamera(CameraUpdateFactory.fitMapPoints(kPoints.toTypedArray(), 120), com.kakao.vectormap.camera.CameraAnimation.from(1000, true, true))

        } else {
            map.moveCamera(CameraUpdateFactory.newCenterPosition(kPoints.first(), 15))
        }
    }

    AndroidView(
        factory = { ctx ->
            MapView(ctx).apply {
                start(object : MapLifeCycleCallback() {
                    override fun onMapDestroy() {}
                    override fun onMapError(e: Exception?) {}
                }, object : KakaoMapReadyCallback() {
                    override fun onMapReady(map: KakaoMap) { kakaoMap = map }
                })
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}
