package com.example.alltodo.ui.components

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
                                 if (pathPoints.isNotEmpty()) {
                                     // Draw Path Line
                                     val manager = map.routeLineManager
                                     val layer = manager?.getLayer("pathLayer") ?: manager?.addLayer("pathLayer", 1000)
                                     val style = RouteLineStyles.from(RouteLineStyle.from(20f, android.graphics.Color.RED))
                                     val segment = RouteLineSegment.from(pathPoints, style)
                                     layer?.addRouteLine(RouteLineOptions.from(segment))
                                     
                                     // Labels
                                     val labelManager = map.labelManager
                                     val labelLayer = labelManager?.getLayer("pathLabels") ?: labelManager?.addLayer(LabelLayerOptions.from("pathLabels"))
                                     
                                     // End: History Pin (Used for both Start & End)
                                     val historyBitmap = com.example.alltodo.ui.PinImageManager.getPinBitmap(com.example.alltodo.R.drawable.pin_history)
                                     if (historyBitmap != null) {
                                         val historyStyle = labelManager?.addLabelStyles(LabelStyles.from(LabelStyle.from(historyBitmap).setAnchorPoint(0.5f, 1.0f)))
                                         if (historyStyle != null) {
                                             // Start Marker
                                             labelLayer?.addLabel(LabelOptions.from(pathPoints.first()).setStyles(historyStyle))
                                             // End Marker
                                             labelLayer?.addLabel(LabelOptions.from(pathPoints.last()).setStyles(historyStyle))
                                         }
                                     }

                                     // Fit Camera
                                     map.moveCamera(CameraUpdateFactory.fitMapPoints(pathPoints.toTypedArray(), 100))
                                 }
                             }
                         })
                     }
                 },
                 modifier = Modifier.fillMaxSize()
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
