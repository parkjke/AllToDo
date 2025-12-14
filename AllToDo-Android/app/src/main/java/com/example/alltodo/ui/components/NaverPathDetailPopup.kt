package com.example.alltodo.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier // Core modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.alltodo.ui.createRedDotBitmap
import com.example.alltodo.ui.theme.AllToDoGreen
import com.naver.maps.geometry.LatLng
import com.naver.maps.geometry.LatLngBounds
import com.naver.maps.map.CameraUpdate
import com.naver.maps.map.MapView
import com.naver.maps.map.overlay.Marker
import com.naver.maps.map.overlay.OverlayImage
import com.naver.maps.map.overlay.PathOverlay
import android.graphics.PointF

@Composable
fun NaverPathDetailPopup(
    pathPoints: List<LatLng>,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    MapView(context).apply {
                        getMapAsync { map ->
                            // Draw Path
                            if (pathPoints.size >= 2) {
                                val path = PathOverlay()
                                path.coords = pathPoints
                                path.color = android.graphics.Color.RED
                                path.width = 20
                                path.map = map
                                
                                // Start & End Marker (History Pin)
                                val historyBitmap = com.example.alltodo.ui.PinImageManager.getPinBitmap(com.example.alltodo.R.drawable.pin_history)
                                if (historyBitmap != null) {
                                    val overlayImage = OverlayImage.fromBitmap(historyBitmap)
                                    
                                    // Start
                                    val startMarker = Marker()
                                    startMarker.position = pathPoints.first()
                                    startMarker.icon = overlayImage
                                    startMarker.anchor = PointF(0.5f, 1.0f)
                                    startMarker.map = map

                                    // End
                                    val endMarker = Marker()
                                    endMarker.position = pathPoints.last()
                                    endMarker.icon = overlayImage
                                    endMarker.anchor = PointF(0.5f, 1.0f)
                                    endMarker.map = map
                                }
                                
                                // Fit Camera
                                val bounds = LatLngBounds.Builder().include(pathPoints).build()
                                val update = CameraUpdate.fitBounds(bounds, 100)
                                map.moveCamera(update)
                            }
                        }
                    }
                }
            )
            
            // Close Button [Styled]
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(com.example.alltodo.ui.theme.AllToDoGreen.copy(alpha = 0.7f))
                    .clickable { onDismiss() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close, 
                    contentDescription = "Close", 
                    tint = Color(0xFF333333),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
