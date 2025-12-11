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
    items: List<UnifiedItem>,
    currentLocation: android.location.Location?,
    cameraPositionState: CameraPositionState,
    onMapClick: (com.kakao.vectormap.LatLng) -> Unit,
    onMapLongClick: (com.kakao.vectormap.LatLng) -> Unit, // [FIX] Added Long Click
    onItemClick: (UnifiedItem) -> Unit,
    onItemClickWithCoords: (UnifiedItem, Float, Float) -> Unit, // [FIX] New Callback
    onClusterClickWithCoords: (List<UnifiedItem>, Float, Float) -> Unit, // [FIX] New Callback
    onRotationChange: (Float) -> Unit,
    isMapReady: Boolean,
    onMapLoaded: () -> Unit,
    showHistoryMode: Boolean // [FIX] Added parameter
) {
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
    LaunchedEffect(items, showHistoryMode, currentLocation, isMapReady) {
        if (!isMapReady) return@LaunchedEffect

        // Condition:
        // 1. If History Mode -> Always update camera (Fit items OR Zoom 15 if empty)
        // 2. If Today (Initial) -> Fit items OR Zoom 15 if empty. Only once.
        // 3. If Today (Update) -> Do NOT move camera automatically.

        val shouldAnimate = showHistoryMode || !initialAnimationDone

        if (shouldAnimate) {
            // Check if we have items to fit
            val hasItems = items.isNotEmpty()
            
            if (hasItems) {
                val boundsBuilder = LatLngBounds.builder()
                
                // Collect points
                items.forEach {
                    val loc = when(it) {
                        is UnifiedItem.Todo -> it.item.latitude?.let { lat -> it.item.longitude?.let { lon -> LatLng(lat, lon) } }
                        is UnifiedItem.History -> LatLng(it.log.latitude, it.log.longitude)
                        else -> null
                    }
                    loc?.let { l -> 
                        boundsBuilder.include(l)
                    }
                }

                // Action
                try {
                    val bounds = boundsBuilder.build()
                    // IMPORTANT: Check if the bounds has any points before animating
                    if (items.any { it.latitude != 0.0 || it.longitude != 0.0 }) {
                        cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(bounds, 100), 1000)
                    }
                     
                     // If Initial Launch with points -> Zoom to user after delay
                     if (!initialAnimationDone && !showHistoryMode) {
                         delay(3000)
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
                // No Items -> Wait for Current Location then Zoom 15
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

    // ... Cluster Items Prep ...
    val clusterItems = remember(items) {
        items.mapNotNull { item ->
            val pos = when (item) {
                is UnifiedItem.Todo -> item.item.latitude?.let { lat -> item.item.longitude?.let { lon -> LatLng(lat, lon) } }
                is UnifiedItem.History -> LatLng(item.log.latitude, item.log.longitude)
                is UnifiedItem.CurrentLocation -> null
            }
            pos?.let {
                val title = when (item) {
                    is UnifiedItem.Todo -> item.item.text
                    is UnifiedItem.History -> "History"
                    else -> ""
                }
                MapClusterItem(item, it, title, null)
            }
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
        onMapLongClick = { latLng -> // [FIX] Handle Long Click
             onMapLongClick(com.kakao.vectormap.LatLng.from(latLng.latitude, latLng.longitude))
        },
        onMapLoaded = onMapLoaded
    ) {
        // Capture Projection
        MapEffect(Unit) { map ->
            map.setOnCameraMoveListener {
                mapProjection = map.projection
            }
            map.setOnCameraIdleListener {
                mapProjection = map.projection
            }
            mapProjection = map.projection // Initial
        }
        
        // [FIX] Clustering for Todo/History
        Clustering(
            items = clusterItems,
            // [FIX] Green for Todo, Red for History
            clusterItemContent = { clusterItem ->
                val isTodo = clusterItem.item is UnifiedItem.Todo
                val hue = if (isTodo) com.google.android.gms.maps.model.BitmapDescriptorFactory.HUE_GREEN else com.google.android.gms.maps.model.BitmapDescriptorFactory.HUE_RED
                
                Marker(
                    state = MarkerState(position = clusterItem.position),
                    icon = com.google.android.gms.maps.model.BitmapDescriptorFactory.defaultMarker(hue),
                    onClick = {
                        val point = mapProjection?.toScreenLocation(clusterItem.position)
                        if (point != null) {
                            onItemClickWithCoords(clusterItem.item, point.x.toFloat(), point.y.toFloat())
                        } else {
                            onItemClick(clusterItem.item)
                        }
                        true
                    }
                )
            },
            // [FIX] Cluster Click
            onClusterClick = { cluster ->
                val items = cluster.items.map { (it as MapClusterItem).item }
                val point = mapProjection?.toScreenLocation(cluster.position)
                if (point != null) {
                    onClusterClickWithCoords(items, point.x.toFloat(), point.y.toFloat())
                }
                true // Consume event (no zoom)
            }
        )

        // [FIX] Standalone Current Location Marker
        currentLocation?.let {
            val latLng = LatLng(it.latitude, it.longitude)
            Marker(
                state = MarkerState(position = latLng),
                title = "Current Location",
                snippet = "You are here",
                onClick = { marker ->
                    // Show callout for current location? 
                    // Use UnifiedItem.CurrentLocation wrapper
                    val item = UnifiedItem.CurrentLocation(it.latitude, it.longitude)
                    val point = mapProjection?.toScreenLocation(latLng)
                    if (point != null) {
                        onItemClickWithCoords(item, point.x.toFloat(), point.y.toFloat())
                    }
                    true
                },
                icon = com.google.android.gms.maps.model.BitmapDescriptorFactory.defaultMarker(com.google.android.gms.maps.model.BitmapDescriptorFactory.HUE_RED)
            )
        }
    }
}
