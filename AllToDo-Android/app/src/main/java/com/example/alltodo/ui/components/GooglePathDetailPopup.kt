package com.example.alltodo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import com.example.alltodo.ui.UnifiedItem
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
                    
                    // Draw Start/End Markers
                    Marker(
                        state = MarkerState(position = pathPoints.first()),
                        title = "Start",
                    )
                    Marker(
                        state = MarkerState(position = pathPoints.last()),
                        title = "End",
                    )
                    
                    // Draw small dots for intermediate points? 
                    // To match Kakao impl (red dots for all points), but that might be too heavy for Google Map Markers if many points.
                    // For now, let's stick to Line + Endpoints which is cleaner for Google Maps.
                }
            }

            // Close Button
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Black)
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
