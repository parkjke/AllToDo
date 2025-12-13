package com.example.alltodo.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.alltodo.ui.UnifiedItem
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

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
    showHistoryMode: Boolean,
    initialAnimationDone: Boolean,
    onInitialAnimationDone: () -> Unit
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
            isMyLocationEnabled = false // [FIX] Disable native blue dot to avoid overlap with custom marker
        )
    }



    // 2. Launch Animation & History Mode Handler
    // Add currentLocation to keys to handle "No Pins -> Zoom 15" when location arrives
    // [FIX] Add isMapReady to prevent crash on re-entry (trying to animate before map loads)
    // 2. Launch Animation & History Mode Handler
    // 2. Launch Animation & History Mode Handler
    // 2. iPhone-like Launch Animation (Fit Bounds with Max Zoom 9)
    // 2. iPhone-like Launch Animation (Fit Bounds with Max Zoom 11)
    // [FIX] Loop prevention: animate once, never restart.
    // Depend on Unit so it doesn't restart on data change.
    LaunchedEffect(Unit) {
        if (initialAnimationDone) return@LaunchedEffect

        // Wait for Map Limit or Data
        while (true) {
             delay(500) // Poll
             if (!isMapReady) continue
             
             // [DEBUG] Check for valid data
             val validPoints = mutableListOf<LatLng>()
             
             // 1. Filter valid locations
             val allUnifiedItems = clusteredItems.flatMap { it.items }
             val itemPoints = allUnifiedItems.filter { item ->
                  (item is UnifiedItem.Todo || item is UnifiedItem.History) && item.latitude != 0.0 && item.longitude != 0.0
             }.map { item ->
                  LatLng(item.latitude, item.longitude)
             }
             validPoints.addAll(itemPoints)

             if (currentLocation != null && currentLocation.latitude != 0.0) {
                 validPoints.add(LatLng(currentLocation.latitude, currentLocation.longitude))
             }
             
             // If we have data, start animation sequence
             if (validPoints.isNotEmpty()) {
                  val boundsBuilder = LatLngBounds.builder()
                  validPoints.forEach { boundsBuilder.include(it) }
                  val bounds = boundsBuilder.build()
                  val center = bounds.center

                  // Zoom Logic: "If Zoom > 11, set to 11" (Approx Span 0.4)
                  val MIN_SPAN = 0.4 
                  val ne = bounds.northeast
                  val sw = bounds.southwest
                  var latSpan = ne.latitude - sw.latitude
                  var lngSpan = ne.longitude - sw.longitude
                  
                  if (latSpan < MIN_SPAN) latSpan = MIN_SPAN
                  if (lngSpan < MIN_SPAN) lngSpan = MIN_SPAN
                  
                  val expandedBounds = LatLngBounds(
                      LatLng(center.latitude - latSpan / 2, center.longitude - lngSpan / 2),
                      LatLng(center.latitude + latSpan / 2, center.longitude + lngSpan / 2)
                  )

                  try {
                      cameraPositionState.animate(
                          CameraUpdateFactory.newLatLngBounds(expandedBounds, 100),
                          1500
                      )
                      
                      delay(3000)
                      
                      if (currentLocation != null) {
                          cameraPositionState.animate(
                              CameraUpdateFactory.newLatLngZoom(
                                  LatLng(currentLocation.latitude, currentLocation.longitude), 
                                  15f
                              ),
                              1000 
                          )
                      }
                  } catch (e: Exception) {
                      e.printStackTrace()
                  }
                  
                  onInitialAnimationDone()
                  break // Exit loop, never run again
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
                bitmapDescriptorFromVector(context, firstItem.getPinResId(), 40)
            } else {
                // Cluster Item
                // [FIX] Match iOS Logic: Determine Dominant Type & Color (Badge Strategy)
                var hasHistory = false
                var hasTodo = false
                cluster.items.forEach { 
                    if (it is UnifiedItem.History) hasHistory = true
                    if (it is UnifiedItem.Todo) hasTodo = true
                }
                
                val (resId, badgeColor) = when {
                    hasHistory && !hasTodo -> com.example.alltodo.R.drawable.pin_history to android.graphics.Color.RED
                    else -> com.example.alltodo.R.drawable.pin_todo_ready to android.graphics.Color.parseColor("#00AA00") // Green
                }
                createClusterBitmap(context, cluster.count, resId, badgeColor)
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
                icon = bitmapDescriptorFromVector(context, com.example.alltodo.R.drawable.pin_current)
            )
        }
    }
}

// Helper to create cluster icon with text (iOS Style: Pin + Badge)
fun createClusterBitmap(context: android.content.Context, count: Int, @androidx.annotation.DrawableRes resId: Int, badgeColor: Int): com.google.android.gms.maps.model.BitmapDescriptor {
    val density = context.resources.displayMetrics.density
    
    // iOS Size: 40x50 pts (Matched with iOS GoogleMapView.swift)
    val widthDp = 40
    val heightDp = 50
    val wPx = (widthDp * density).toInt()
    val hPx = (heightDp * density).toInt()
    
    // Badge Size
    val badgeRadiusDp = 10f
    val badgeRadiusPx = badgeRadiusDp * density
    
    // Canvas Size (Need extra space for badge if it hangs out? iOS code: "badge overhang")
    // Let's add padding.
    val padding = (badgeRadiusPx / 1.5).toInt() 
    val bitmapW = wPx + padding
    val bitmapH = hPx + padding
    
    val bitmap = android.graphics.Bitmap.createBitmap(bitmapW, bitmapH, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    
    // 1. Draw Base Pin (Centered and Aspect Correct)
    val drawable = androidx.core.content.ContextCompat.getDrawable(context, resId)
    if (drawable != null) {
        // Calculate aspect ratio
        val dw = drawable.intrinsicWidth
        val dh = drawable.intrinsicHeight
        
        val targetW: Int
        val targetH: Int
        
        if (dw > 0 && dh > 0) {
            val aspect = dw.toFloat() / dh.toFloat()
            val boxAspect = wPx.toFloat() / hPx.toFloat()
            
            if (aspect > boxAspect) {
                // Drawable is wider than box -> width fits, height adjusts
                targetW = wPx
                targetH = (wPx / aspect).toInt()
            } else {
                // Drawable is taller than box -> height fits, width adjusts
                targetH = hPx
                targetW = (hPx * aspect).toInt()
            }
        } else {
            targetW = wPx
            targetH = hPx
        }
        
        // Center horizontally
        val left = (wPx - targetW) / 2
        // Bottom Align or Top Align?
        // Usually pins are anchored at bottom. 
        // We have 'padding' at top. So box is from (0, padding) to (wPx, hPx + padding)
        // Let's align to bottom of that box.
        val top = padding + (hPx - targetH) / 2 // Center Vertically in the Pin Box? Or Bottom?
        // Let's try Centering in the 40x50 area.
        
        drawable.setBounds(left, top, left + targetW, top + targetH)
        drawable.draw(canvas)
    }
    
    // 2. Draw Badge (Top Right of Pin)
    val paint = android.graphics.Paint()
    paint.isAntiAlias = true
    paint.style = android.graphics.Paint.Style.FILL
    paint.color = badgeColor
    
    // Center of badge: Top-Right corner of Pin Rect
    val cx = wPx.toFloat() - (badgeRadiusPx / 2) // Slightly inside
    val cy = padding.toFloat() // Top aligned
    
    canvas.drawCircle(cx, cy, badgeRadiusPx, paint)
    
    // Badge Border
    paint.style = android.graphics.Paint.Style.STROKE
    paint.color = android.graphics.Color.WHITE
    paint.strokeWidth = 2f * density
    canvas.drawCircle(cx, cy, badgeRadiusPx, paint)
    
    // 3. Text
    paint.style = android.graphics.Paint.Style.FILL
    paint.color = android.graphics.Color.WHITE
    paint.textSize = 10f * density
    paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
    paint.textAlign = android.graphics.Paint.Align.CENTER
    
    val text = if (count > 99) "99+" else count.toString()
    // Center text vertically
    val bounds = android.graphics.Rect()
    paint.getTextBounds(text, 0, text.length, bounds)
    val textY = cy + (bounds.height() / 2f) - (bounds.bottom * 0.1f) // adjustments
    
    canvas.drawText(text, cx, textY, paint)
    
    return com.google.android.gms.maps.model.BitmapDescriptorFactory.fromBitmap(bitmap)
}

// [FIX] Safe Vector Drawable Loader with Scaling
fun bitmapDescriptorFromVector(
    context: android.content.Context,
    @androidx.annotation.DrawableRes vectorResId: Int,
    targetSizeDp: Int = 40 
): com.google.android.gms.maps.model.BitmapDescriptor? {
    return try {
        val vectorDrawable = androidx.core.content.ContextCompat.getDrawable(context, vectorResId) ?: return null
        
        val density = context.resources.displayMetrics.density
        val sizePx = (targetSizeDp * density).toInt()

        // Maintain aspect ratio if intrinsic dimensions exist
        val w = vectorDrawable.intrinsicWidth
        val h = vectorDrawable.intrinsicHeight
        
        val finalW: Int
        val finalH: Int
        
        if (w > 0 && h > 0) {
            val aspect = w.toFloat() / h.toFloat()
            if (w > h) {
                finalW = sizePx
                finalH = (sizePx / aspect).toInt()
            } else {
                finalH = sizePx
                finalW = (sizePx * aspect).toInt()
            }
        } else {
            finalW = sizePx
            finalH = sizePx
        }

        vectorDrawable.setBounds(0, 0, finalW, finalH)
        val bitmap = android.graphics.Bitmap.createBitmap(
            finalW,
            finalH,
            android.graphics.Bitmap.Config.ARGB_8888
        )
        val canvas = android.graphics.Canvas(bitmap)
        vectorDrawable.draw(canvas)
        com.google.android.gms.maps.model.BitmapDescriptorFactory.fromBitmap(bitmap)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

