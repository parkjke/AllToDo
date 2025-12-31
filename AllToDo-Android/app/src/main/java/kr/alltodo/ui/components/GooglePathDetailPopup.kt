package kr.alltodo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kr.alltodo.ui.UnifiedItem
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState

@Composable
fun GooglePathDetailPopup(
    pathPoints: List<LatLng>,
    onDismiss: () -> Unit
) {
    val cameraPositionState = rememberCameraPositionState()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState
            ) {
                // Draw Path Line
                if (pathPoints.isNotEmpty()) {
                    Polyline(
                        points = pathPoints,
                        color = Color.Red,
                        width = 10f
                    )
                    
                    // [FIX] Use Static Pins (01 for Start, 02 for End)
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val startBitmap = kr.alltodo.ui.PinImageManager.fetchStaticPin(context, "01")
                    val endBitmap = kr.alltodo.ui.PinImageManager.fetchStaticPin(context, "02")

                    fun getIcon(bm: android.graphics.Bitmap?) = if (bm != null) {
                         com.google.android.gms.maps.model.BitmapDescriptorFactory.fromBitmap(bm)
                    } else {
                         com.google.android.gms.maps.model.BitmapDescriptorFactory.defaultMarker(com.google.android.gms.maps.model.BitmapDescriptorFactory.HUE_RED)
                    }

                    // Start Marker
                    Marker(
                        state = MarkerState(position = pathPoints.first()),
                        title = "Start",
                        icon = getIcon(startBitmap),
                        anchor = androidx.compose.ui.geometry.Offset(0.5f, 1.0f)
                    )

                    // End Marker
                    Marker(
                        state = MarkerState(position = pathPoints.last()),
                        title = "End",
                        icon = getIcon(endBitmap),
                        anchor = androidx.compose.ui.geometry.Offset(0.5f, 1.0f) 
                    )
                }
            }

            // Close Button [Styled to match RightSideControls]
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(kr.alltodo.ui.theme.AllToDoGreen.copy(alpha = 0.7f))
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

    // Fit Camera to Path
    LaunchedEffect(pathPoints) {
        if (pathPoints.isNotEmpty()) {
            val bounds = LatLngBounds.builder()
            pathPoints.forEach { bounds.include(it) }
            // Add padding
            try {
                cameraPositionState.move(CameraUpdateFactory.newLatLngBounds(bounds.build(), 100))
            } catch (e: Exception) {
                // Map layout not ready
            }
        }
    }
}
