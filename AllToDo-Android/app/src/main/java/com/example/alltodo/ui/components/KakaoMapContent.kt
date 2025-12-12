package com.example.alltodo.ui.components

import android.graphics.Color
import android.util.Log
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.alltodo.ui.MapProvider
import com.example.alltodo.ui.PinCluster
import com.example.alltodo.ui.UnifiedItem
import com.example.alltodo.ui.generateDiamondPin
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

@Composable
fun KakaoMapContent(
    modifier: Modifier = Modifier,
    isSdkInitialized: Boolean,
    items: List<UnifiedItem>,
    currentLocation: android.location.Location?,
    selectedCluster: PinCluster?,
    onMapReady: (KakaoMap) -> Unit,
    onClusterClick: (PinCluster) -> Unit,
    onMapClick: (LatLng) -> Unit,
    onCameraRotate: (Float) -> Unit,
    onCameraMoveStart: () -> Unit
) {
    val context = LocalContext.current
    var kakaoMap by remember { mutableStateOf<KakaoMap?>(null) }
    var isMapReady by remember { mutableStateOf(false) }

    // [Logic] Render Markers & Clusters
    // Re-run when map is ready or items change
    LaunchedEffect(kakaoMap, items) { 
        val map = kakaoMap ?: return@LaunchedEffect
        val labelManager = map.labelManager ?: return@LaunchedEffect
        val layer = labelManager.getLayer("mainLayer") ?: labelManager.addLayer(LabelLayerOptions.from("mainLayer"))
        layer?.removeAll()

        if (items.isNotEmpty()) {
            val density = context.resources.displayMetrics.density
            val clusterThresholdPx = 60 * density

            val screenPoints = items.mapNotNull { item ->
                val latLng = LatLng.from(item.latitude, item.longitude)
                val pt = map.toScreenPoint(latLng)
                if (pt != null) Triple(item, pt.x.toFloat(), pt.y.toFloat()) else null
            }

            val clusters = mutableListOf<PinCluster>()
            val visited = BooleanArray(screenPoints.size)

            for (i in screenPoints.indices) {
                if (visited[i]) continue
                val center = screenPoints[i]
                val clusterItems = mutableListOf<UnifiedItem>()
                clusterItems.add(center.first)
                visited[i] = true

                for (j in i + 1 until screenPoints.size) {
                    if (visited[j]) continue
                    val other = screenPoints[j]
                    val dx = center.second - other.second
                    val dy = center.third - other.third
                    val dist = kotlin.math.sqrt(dx * dx + dy * dy)

                    if (dist <= clusterThresholdPx) {
                        clusterItems.add(other.first)
                        visited[j] = true
                    }
                }

                var hasTodo = false
                var hasHistory = false
                var hasCurrent = false
                clusterItems.forEach { 
                    if (it is UnifiedItem.Todo) hasTodo = true
                    if (it is UnifiedItem.History) hasHistory = true
                    if (it is UnifiedItem.CurrentLocation) hasCurrent = true
                }
                val type = when {
                    hasCurrent -> "current"
                    hasTodo && hasHistory -> "mixed"
                    hasTodo -> "todo"
                    else -> "history"
                }

                clusters.add(PinCluster(center.first.latitude, center.first.longitude, clusterItems, type, (hasTodo && hasHistory) || (hasCurrent && (hasTodo || hasHistory))))
            }

            clusters.forEach { cluster ->
                val styleId = "cluster_${cluster.items.size}_${cluster.type}"
                var styles = labelManager.getLabelStyles(styleId)
                if (styles == null) {
                    if (cluster.items.size == 1) {
                        val item = cluster.items.first()
                        val resId = when (item) {
                            is UnifiedItem.Todo -> com.example.alltodo.R.drawable.pin_todo
                            is UnifiedItem.History -> com.example.alltodo.R.drawable.pin_history
                            is UnifiedItem.CurrentLocation -> com.example.alltodo.R.drawable.pin_current
                        }
                        
                         val anchorY = if (item is UnifiedItem.Todo) 1.0f else 0.5f
                         val bitmap = android.graphics.BitmapFactory.decodeResource(context.resources, resId)
                         val finalStyle = LabelStyle.from(bitmap).setAnchorRatio(0.5f, anchorY)
                         styles = labelManager.addLabelStyles(LabelStyles.from(styleId, finalStyle))

                    } else {
                        val color = when {
                            cluster.items.any { it is UnifiedItem.CurrentLocation } -> android.graphics.Color.RED
                            cluster.hasMixed -> 0xFF808080.toInt()
                            cluster.items.any { it is UnifiedItem.History } -> android.graphics.Color.RED
                            cluster.items.any { it is UnifiedItem.Todo } -> 0xFF00AA00.toInt()
                            else -> android.graphics.Color.BLUE
                        }
    
                        val bitmap = generateDiamondPin(color, cluster.items.size)
                        if (bitmap != null) {
                            styles = labelManager.addLabelStyles(LabelStyles.from(styleId, LabelStyle.from(bitmap)))
                        }
                    }
                }

                if (styles != null) {
                    val options = LabelOptions.from(LatLng.from(cluster.latitude, cluster.longitude))
                        .setStyles(styles)
                        .setClickable(true)
                    layer?.addLabel(options)
                }
            }
        }
    }

    if (isSdkInitialized) {
        AndroidView(
            factory = { ctx ->
                try {
                    MapView(ctx).apply {
                        start(object : MapLifeCycleCallback() {
                            override fun onMapDestroy() {}
                            override fun onMapError(e: Exception?) {
                                Log.e("AllToDo", "Map Error: ${e?.message}")
                            }
                        }, object : KakaoMapReadyCallback() {
                            override fun onMapReady(map: KakaoMap) {
                                kakaoMap = map
                                isMapReady = true
                                onMapReady(map)

                                // Initial Camera
                                currentLocation?.let { loc ->
                                    val cameraUpdate = CameraUpdateFactory.newCenterPosition(LatLng.from(loc.latitude, loc.longitude))
                                    map.moveCamera(cameraUpdate)
                                }

                                map.setOnLabelClickListener { _, _, label ->
                                    val pos = label.position
                                    // Recalculate clusters to find which one was clicked
                                    // Note: Ideally we should store cluster/label mapping, but for now we re-calculate logic same as above or use proximity
                                    
                                    // Simplified: Find cluster near label position
                                    // We need original items list here? Yes, 'items' is captured.
                                    
                                    // Re-running heavy logic on click isn't great, but matches original implementation for correctness
                                    val density = ctx.resources.displayMetrics.density
                                    val clusterThresholdPx = 60 * density
                                    
                                    val screenPoints = items.mapNotNull { item ->
                                        val pt = map.toScreenPoint(LatLng.from(item.latitude, item.longitude))
                                        if (pt != null) Triple(item, pt.x.toFloat(), pt.y.toFloat()) else null
                                    }

                                    val clusters = mutableListOf<PinCluster>()
                                    val visited = BooleanArray(screenPoints.size)
                                    // Same clustering for finding target...
                                    for (i in screenPoints.indices) {
                                        if (visited[i]) continue
                                        val center = screenPoints[i]
                                        val clusterItems = mutableListOf<UnifiedItem>()
                                        clusterItems.add(center.first)
                                        visited[i] = true
                                        for (j in i + 1 until screenPoints.size) {
                                            if (visited[j]) continue
                                            val other = screenPoints[j]
                                            val dx = center.second - other.second
                                            val dy = center.third - other.third
                                            val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                                            if (dist <= clusterThresholdPx) {
                                                clusterItems.add(other.first)
                                                visited[j] = true
                                            }
                                        }
                                        clusters.add(PinCluster(center.first.latitude, center.first.longitude, clusterItems))
                                    }

                                    val clickedCluster = clusters.find {
                                        Math.abs(it.latitude - pos.latitude) < 0.00001 && Math.abs(it.longitude - pos.longitude) < 0.00001
                                    }

                                    if (clickedCluster != null) {
                                        onClusterClick(clickedCluster)
                                    }
                                    return@setOnLabelClickListener true
                                }

                                map.setOnCameraMoveEndListener { _, cameraPosition, _ ->
                                    onCameraRotate(-Math.toDegrees(cameraPosition.rotationAngle).toFloat())
                                }
                                map.setOnCameraMoveStartListener { _, _ ->
                                    onCameraMoveStart()
                                }

                                map.setOnMapClickListener { _, latLng, _, _ ->
                                    onMapClick(latLng)
                                }
                            }
                            override fun getPosition(): LatLng {
                                return LatLng.from(37.5665, 126.9780) // Default Seoul
                            }
                            override fun getZoomLevel(): Int {
                                return 15
                            }
                        })
                    }
                } catch (t: Throwable) {
                    Log.e("AllToDo", "CRITICAL: Failed to create MapView", t)
                    TextView(ctx).apply {
                        text = "Map Unavailable: ${t.message}"
                        setTextColor(Color.RED)
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
