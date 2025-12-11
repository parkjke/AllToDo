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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.alltodo.ui.createRedDotBitmap
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.label.LabelLayerOptions
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelStyle
import com.kakao.vectormap.label.LabelStyles
import com.kakao.vectormap.route.RouteLineOptions
import com.kakao.vectormap.route.RouteLineSegment
import com.kakao.vectormap.route.RouteLineStyle
import com.kakao.vectormap.route.RouteLineStyles

@Composable
fun PathDetailPopup(
    pathPoints: List<com.kakao.vectormap.LatLng>,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
             AndroidView(
                 factory = { ctx ->
                     MapView(ctx).apply {
                         start(object : MapLifeCycleCallback() {
                             override fun onMapDestroy() {}
                             override fun onMapError(e: Exception?) {}
                         }, object : KakaoMapReadyCallback() {
                             override fun onMapReady(map: KakaoMap) {
                                 // Draw Path Line
                                 val manager = map.routeLineManager
                                 val layer = manager?.getLayer("pathLayer") ?: manager?.addLayer("pathLayer", 1000)
                                 val style = RouteLineStyles.from(RouteLineStyle.from(20f, android.graphics.Color.RED))
                                 val segment = RouteLineSegment.from(pathPoints, style)
                                 layer?.addRouteLine(RouteLineOptions.from(segment))
                                 
                                 // Draw Red Pins for Start/End/All?
                                 val labelManager = map.labelManager
                                 val labelLayer = labelManager?.getLayer("pathLabels") ?: labelManager?.addLayer(LabelLayerOptions.from("pathLabels"))
                                 
                                 // Simple Red Dot Bitmap
                                 val dotBitmap = createRedDotBitmap()
                                 val labelStyle = labelManager?.addLabelStyles(LabelStyles.from(LabelStyle.from(dotBitmap)))
                                 
                                 if (labelStyle != null) {
                                     pathPoints.forEach { pt ->
                                         labelLayer?.addLabel(LabelOptions.from(pt).setStyles(labelStyle))
                                     }
                                 }

                                 // Fit Camera
                                 map.moveCamera(CameraUpdateFactory.fitMapPoints(pathPoints.toTypedArray(), 100))
                             }
                         })
                     }
                 },
                 modifier = Modifier.fillMaxSize()
             )
             
             // Close Button
             IconButton(
                 onClick = onDismiss,
                 modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
             ) {
                 Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Black)
             }
        }
    }
}
