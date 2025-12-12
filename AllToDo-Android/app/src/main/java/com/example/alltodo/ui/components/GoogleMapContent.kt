package com.example.alltodo.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.alltodo.ui.UnifiedItem
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.clustering.ClusterItem
import com.google.maps.android.compose.*
import com.google.maps.android.compose.clustering.Clustering // [FIX] Import Clustering explicitly
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

// Wrapper for Clustering
data class MapClusterItem(
    val item: UnifiedItem,
    val itemPosition: LatLng, // [FIX] Renamed to avoid clash
    val itemTitle: String,
    val itemSnippet: String?
) : ClusterItem {
    override fun getPosition(): LatLng = itemPosition
    override fun getTitle(): String = itemTitle
    override fun getSnippet(): String? = itemSnippet
    override fun getZIndex(): Float? = 0f
}

@OptIn(MapsComposeExperimentalApi::class)
@Composable
fun GoogleMapContent(
    modifier: Modifier = Modifier,
    clusteredItems: List<com.example.alltodo.ui.TodoViewModel.PinClusterItem>, // [FIX] Use Clustered Items
    currentLocation: android.location.Location?,
    cameraPositionState: CameraPositionState,
    onMapClick: (com.kakao.vectormap.LatLng) -> Unit,
    onMapLongClick: (com.kakao.vectormap.LatLng) -> Unit, 
    onItemClick: (UnifiedItem) -> Unit,
    onItemClickWithCoords: (UnifiedItem, Float, Float) -> Unit, 
    onClusterClickWithCoords: (List<UnifiedItem>, Float, Float) -> Unit,
    onRotationChange: (Float) -> Unit,
    isMapReady: Boolean,
    onMapLoaded: () -> Unit,
    showHistoryMode: Boolean
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // 1. UI Settings (Disable Toolbar & Zoom)
    val uiSettings = remember {
        MapUiSettings(
            zoomControlsEnabled = false,
            compassEnabled = false,
            myLocationButtonEnabled = false,
            mapToolbarEnabled = false // [FIX] Hide Google Map Button
        )
    }

    val properties = remember {
        MapProperties(
            isMyLocationEnabled = false // [FIX] Disable native blue dot, use custom pin
        )
    }

    var initialAnimationDone by remember { mutableStateOf(false) }

    // 2. Launch Animation & History Mode Handler
    // Add currentLocation to keys to handle "No Pins -> Zoom 15" when location arrives
    // [FIX] Add isMapReady to prevent crash on re-entry (trying to animate before map loads)
    // 2. Launch Animation & History Mode Handler
    LaunchedEffect(clusteredItems, showHistoryMode, currentLocation, isMapReady) {
        if (!isMapReady) return@LaunchedEffect

        val shouldAnimate = showHistoryMode || !initialAnimationDone

        if (shouldAnimate) {
            val hasItems = clusteredItems.isNotEmpty()
            
            if (hasItems) {
                val boundsBuilder = LatLngBounds.builder()
                clusteredItems.forEach {
                    boundsBuilder.include(LatLng(it.latitude, it.longitude))
                }

                try {
                    val bounds = boundsBuilder.build()
                    // Check if bounds valid (not single point with 0 size if strict check needed, but Google Maps handles single point bounds okay usually, or standard builder needs >0 points)
                     cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(bounds, 100), 1000)
                     
                     if (!initialAnimationDone && !showHistoryMode) {
                         delay(1000) // [FIX] Changed from 3000 to 1000
                         currentLocation?.let {
                            cameraPositionState.animate(
                                CameraUpdateFactory.newCameraPosition(
                                    CameraPosition.Builder()
                                        .target(LatLng(it.latitude, it.longitude))
                                        .zoom(16f)
                                        .bearing(cameraPositionState.position.bearing)
                                        .tilt(cameraPositionState.position.tilt)
                                        .build()
                                ),
                                1000
                            )
                         }
                     }
                     if (!showHistoryMode) initialAnimationDone = true
                } catch (e: Exception) {}
            } else {
                 currentLocation?.let {
                    try {
                        cameraPositionState.animate(
                            CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 15f),
                            1000
                        )
                        if (!showHistoryMode) initialAnimationDone = true
                    } catch (e: Exception) {}
                }
            }
        }
    }

    // ... Rotation Sync ...
    LaunchedEffect(cameraPositionState) {
        snapshotFlow { cameraPositionState.position.bearing }
            .collectLatest { bearing ->
                onRotationChange(bearing)
            }
    }

    // [FIX] Projection State
    var mapProjection by remember { mutableStateOf<com.google.android.gms.maps.Projection?>(null) }

    GoogleMap(
        modifier = modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        properties = properties,
        uiSettings = uiSettings,
        onMapClick = { latLng ->
            onMapClick(com.kakao.vectormap.LatLng.from(latLng.latitude, latLng.longitude))
        },
        onMapLongClick = { latLng ->
             onMapLongClick(com.kakao.vectormap.LatLng.from(latLng.latitude, latLng.longitude))
        },
        onMapLoaded = onMapLoaded
    ) {
        MapEffect(Unit) { map ->
            map.setOnCameraMoveListener {
                mapProjection = map.projection
            }
            map.setOnCameraIdleListener {
                mapProjection = map.projection
            }
            mapProjection = map.projection
        }
        
        // [FIX] Render Clustered Items
        clusteredItems.forEach { cluster ->
            val position = LatLng(cluster.latitude, cluster.longitude)
            val isSingle = cluster.count == 1
            val firstItem = cluster.items.firstOrNull()
            
            // Determine Icon
            val iconDescriptor = if (isSingle && firstItem != null) {
                // Formatting Single Item
                when (firstItem) {
                    is UnifiedItem.Todo -> com.google.android.gms.maps.model.BitmapDescriptorFactory.fromResource(com.example.alltodo.R.drawable.pin_todo)
                    is UnifiedItem.History -> com.google.android.gms.maps.model.BitmapDescriptorFactory.fromResource(com.example.alltodo.R.drawable.pin_history)
                    is UnifiedItem.CurrentLocation -> com.google.android.gms.maps.model.BitmapDescriptorFactory.fromResource(com.example.alltodo.R.drawable.pin_current)
                }
            } else {
                // Cluster Item
                // Color based on majority or just Red/Green mixed? 
                // Spec say: "Todo Green, Unregistered Red"
                // If cluster has any Todo -> Green?
                val hasTodo = cluster.items.any { it is UnifiedItem.Todo }
                createClusterBitmap(context, cluster.count, !hasTodo)
            }

            Marker(
                state = MarkerState(position = position),
                icon = iconDescriptor,
                onClick = {
                    val point = mapProjection?.toScreenLocation(position)
                    if (point != null) {
                        if (isSingle && firstItem != null) {
                            onItemClickWithCoords(firstItem, point.x.toFloat(), point.y.toFloat())
                        } else {
                            onClusterClickWithCoords(cluster.items, point.x.toFloat(), point.y.toFloat())
                        }
                    }
                    true
                }
            )
        }

        // [FIX] Standalone Current Location Marker
        currentLocation?.let {
            val latLng = LatLng(it.latitude, it.longitude)
            Marker(
                state = MarkerState(position = latLng),
                title = "Current Location",
                snippet = "You are here",
                onClick = { marker ->
                    val item = UnifiedItem.CurrentLocation(it.latitude, it.longitude)
                    val point = mapProjection?.toScreenLocation(latLng)
                    if (point != null) {
                        onItemClickWithCoords(item, point.x.toFloat(), point.y.toFloat())
                    }
                    true
                },
                icon = com.google.android.gms.maps.model.BitmapDescriptorFactory.fromResource(com.example.alltodo.R.drawable.pin_current)
            )
        }
    }
}

// Helper to create cluster icon with text
fun createClusterBitmap(context: android.content.Context, count: Int, isRed: Boolean): com.google.android.gms.maps.model.BitmapDescriptor {
    val size = 100 // px
    val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val paint = android.graphics.Paint()
    
    // Circle
    paint.color = if (isRed) android.graphics.Color.RED else 0xFF00AA00.toInt()
    paint.style = android.graphics.Paint.Style.FILL
    canvas.drawCircle(size/2f, size/2f, size/2f, paint)
    
    // Border
    paint.color = android.graphics.Color.WHITE
    paint.style = android.graphics.Paint.Style.STROKE
    paint.strokeWidth = 5f
    canvas.drawCircle(size/2f, size/2f, size/2f - 2, paint)
    
    // Text
    paint.color = android.graphics.Color.WHITE
    paint.style = android.graphics.Paint.Style.FILL
    paint.textSize = 40f
    paint.textAlign = android.graphics.Paint.Align.CENTER
    paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
    
    val text = if (count > 9) "9+" else count.toString()
    val yPos = (size / 2) - ((paint.descent() + paint.ascent()) / 2)
    canvas.drawText(text, size/2f, yPos, paint)
    
    return com.google.android.gms.maps.model.BitmapDescriptorFactory.fromBitmap(bitmap)
}

