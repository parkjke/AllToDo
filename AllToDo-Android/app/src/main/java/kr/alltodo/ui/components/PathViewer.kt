package kr.alltodo.ui.components

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
import kr.alltodo.data.UserLog
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.CameraUpdate
import com.naver.maps.map.compose.*
import kr.alltodo.ui.theme.AllToDoGreen

@OptIn(ExperimentalNaverMapApi::class)
@Composable
fun PathViewer(
    pathData: List<kr.alltodo.data.PathItem>,
    onClose: () -> Unit
) {
    var lineColor by remember { mutableStateOf(Color.Red) }
    var lineThickness by remember { mutableStateOf(5.dp) }
    
    val pathPoints = remember(pathData) {
        pathData.map { LatLng(it.int_lat / 100_000.0, it.int_long / 100_000.0) }
    }

    val cameraPositionState = rememberCameraPositionState {
        if (pathPoints.isNotEmpty()) {
            position = com.naver.maps.map.CameraPosition(pathPoints.first(), 15.0)
        }
    }

    // Auto-center on path
    LaunchedEffect(pathPoints) {
        if (pathPoints.size >= 2) {
            val bounds = com.naver.maps.geometry.LatLngBounds.from(pathPoints)
            cameraPositionState.animate(CameraUpdate.fitBounds(bounds, 100))
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable {  }) {
        Card(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Map
                NaverMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    uiSettings = MapUiSettings(isZoomControlEnabled = false)
                ) {
                    if (pathPoints.size >= 2) {
                        PathOverlay(
                            coords = pathPoints,
                            color = lineColor,
                            width = lineThickness,
                            outlineWidth = 0.dp
                        )
                        
                        // Start Marker
                        Marker(
                            state = MarkerState(pathPoints.first()),
                            icon = com.naver.maps.map.overlay.OverlayImage.fromResource(kr.alltodo.R.drawable.pin_history),
                            width = 30.dp,
                            height = 36.dp
                        )
                        
                        // End Marker
                        Marker(
                            state = MarkerState(pathPoints.last()),
                            icon = com.naver.maps.map.overlay.OverlayImage.fromResource(kr.alltodo.R.drawable.pin_history),
                            width = 30.dp,
                            height = 36.dp
                        )
                    }
                }

                // Close Button
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).background(Color.White.copy(alpha = 0.7f), CircleShape)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Black)
                }

                // Controls
                Column(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).background(Color.White.copy(alpha = 0.9f), MaterialTheme.shapes.medium).padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Color Selection
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Color: ", style = MaterialTheme.typography.bodySmall)
                        listOf(Color.Red, Color.Blue, AllToDoGreen).forEach { color ->
                            Box(
                                modifier = Modifier.size(32.dp).padding(4.dp).background(color, CircleShape).clickable { lineColor = color }
                                    .let { if (lineColor == color) it.background(color.copy(alpha = 0.3f), CircleShape) else it }
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Thickness Selection
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Thickness: ", style = MaterialTheme.typography.bodySmall)
                        listOf(3.dp, 6.dp, 10.dp).forEach { thickness ->
                            Box(
                                modifier = Modifier.height(32.dp).width(48.dp).padding(4.dp).background(Color.LightGray, MaterialTheme.shapes.small).clickable { lineThickness = thickness }.padding(horizontal = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(modifier = Modifier.fillMaxWidth().height(thickness / 2).background(lineColor))
                            }
                        }
                    }
                }
            }
        }
    }
}
