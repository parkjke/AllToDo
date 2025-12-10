package com.example.alltodo.ui

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Canvas
import androidx.compose.ui.zIndex
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.alltodo.ui.components.RightSideControls
import com.example.alltodo.ui.components.TopLeftWidget
import com.example.alltodo.ui.components.TodoListContent
import com.example.alltodo.ui.components.RightSideControls
import com.example.alltodo.ui.components.TopLeftWidget
import com.example.alltodo.ui.components.TodoListContent
import com.example.alltodo.ui.components.UserProfileView // [NEW]
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Close // [FIX] Import

import com.google.android.gms.location.LocationServices
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapSdk
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.camera.CameraPosition
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelStyle
import com.kakao.vectormap.label.LabelStyles
import com.kakao.vectormap.route.RouteLineLayer
import com.kakao.vectormap.route.RouteLineManager
import com.kakao.vectormap.route.RouteLineOptions
import com.kakao.vectormap.route.RouteLineSegment
import com.kakao.vectormap.route.RouteLineStyle
import com.kakao.vectormap.route.RouteLineStyles
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import kotlinx.coroutines.delay
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: TodoViewModel = hiltViewModel()
) {
    val todoItems by viewModel.todoItems.collectAsState()
    val userLogs by viewModel.userLogs.collectAsState() // Observe Sessions

    // Lifecycle - Session Management
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_START) {
                viewModel.startSession()
            } else if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                viewModel.endSession()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val context = LocalContext.current
    var kakaoMap: KakaoMap? by remember { mutableStateOf(null) }
    var isMapReady by remember { mutableStateOf(false) }
    var isSdkInitialized by remember { mutableStateOf(false) } // [FIX] SDK Init Flag
    var showBottomSheet by remember { mutableStateOf(false) }
    var showMyInfo by remember { mutableStateOf(false) }
    var compassRotation by remember { mutableStateOf(0f) }
    
    // [NEW] Time Travel & Settings
    var showHistoryMode by remember { mutableStateOf(false) }
    val maxPopupItems by viewModel.maxPopupItems.collectAsState()
    val popupFontSize by viewModel.popupFontSize.collectAsState()

    // Selected Cluster/Item for Callout
    var selectedCluster by remember { mutableStateOf<PinCluster?>(null) }
    var selectedClusterPosition by remember { mutableStateOf<Offset?>(null) } // Screen position
    var showDetailPopup by remember { mutableStateOf<UnifiedItem?>(null) } // [NEW] For detail popup

    // Current Location & Add Todo State
    var currentLocation by remember { mutableStateOf<android.location.Location?>(null) }
    var showAddTodoDialog by remember { mutableStateOf(false) }
    var newTodoLocation by remember { mutableStateOf<com.kakao.vectormap.LatLng?>(null) }
    var newTodoText by remember { mutableStateOf("") }

    // [NEW] Path Visualization State
    var showPathMode by remember { mutableStateOf(false) }
    var currentPathPoints by remember { mutableStateOf<List<com.kakao.vectormap.LatLng>>(emptyList()) }

    // Fused Location Client
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    // Permission Launcher
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    // Request permissions on start
    LaunchedEffect(Unit) {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    // [FIX] Ensure Session Ends on Exit
    DisposableEffect(Unit) {
        onDispose {
            viewModel.endSession()
        }
    }

    // [FIX] Restore SDK Init
    LaunchedEffect(Unit) {
        try {
             val appInfo = context.packageManager.getApplicationInfo(context.packageName, android.content.pm.PackageManager.GET_META_DATA)
             val k = appInfo.metaData.getString("com.kakao.vectormap.APP_KEY")
             if (k != null) {
                 KakaoMapSdk.init(context, k)
                 isSdkInitialized = true // [FIX] Signal Ready
             }
        } catch (e: Exception) {
             android.util.Log.e("AllToDo", "SDK Init Fail", e)
        }
    }

    // Location Tracking Loop (Buffering via ViewModel)
    LaunchedEffect(isMapReady) {
        if (isMapReady) {
            // Intro Animation (Wide -> Close)
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    location?.let {
                        kakaoMap?.moveCamera(CameraUpdateFactory.newCenterPosition(com.kakao.vectormap.LatLng.from(it.latitude, it.longitude), 12))
                    }
                }
                delay(1000)
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    location?.let {
                        kakaoMap?.moveCamera(CameraUpdateFactory.newCenterPosition(com.kakao.vectormap.LatLng.from(it.latitude, it.longitude), 15),
                            com.kakao.vectormap.camera.CameraAnimation.from(1000, true, true))
                    }
                }
            }

            // Tracking
            while (true) {
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                        if (location != null) {
                            currentLocation = location // Update State for UI Pin
                            viewModel.saveLocation(location.latitude, location.longitude)
                        }
                    }
                }
                delay(5000) // Poll every 5s
            }
        }
    }

    var cameraMoveState by remember { mutableStateOf(0) }



    LaunchedEffect(kakaoMap, userLogs, todoItems, currentLocation, cameraMoveState) {
        val map = kakaoMap ?: return@LaunchedEffect
        val labelManager = map.labelManager ?: return@LaunchedEffect
        val layer = labelManager.getLayer("mainLayer") ?: labelManager.addLayer(com.kakao.vectormap.label.LabelLayerOptions.from("mainLayer"))
        layer?.removeAll()

        layer?.removeAll()

        // [NEW] Time Filter Logic
        val now = System.currentTimeMillis()
        val oneDay = 24 * 60 * 60 * 1000L
        val targetTime = if (showHistoryMode) now - oneDay else now
        val minTime = targetTime - oneDay
        val maxTime = targetTime + oneDay

        val logItems = userLogs.filter { it.startTime in minTime..maxTime }.map { UnifiedItem.History(it) }
        val todoItemsList = todoItems.filter { 
            val t = it.createdAt
            t in minTime..maxTime 
        }.map { UnifiedItem.Todo(it) }
        
        val currentItems = if (currentLocation != null) listOf(UnifiedItem.CurrentLocation(currentLocation!!.latitude, currentLocation!!.longitude)) else emptyList()
        val allItems = logItems + todoItemsList + currentItems

        if (allItems.isNotEmpty()) {
            val density = context.resources.displayMetrics.density
            val clusterThresholdPx = 60 * density

            val screenPoints = allItems.mapNotNull { item ->
                val latLng = com.kakao.vectormap.LatLng.from(item.latitude, item.longitude)
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

                if (styles != null) {
                    val options = LabelOptions.from(com.kakao.vectormap.LatLng.from(cluster.latitude, cluster.longitude))
                        .setStyles(styles)
                        .setClickable(true)
                    layer?.addLabel(options)
                }
            }
        }
    }



    Box(modifier = Modifier.fillMaxSize()) {
        if (isSdkInitialized) { // [FIX] Only render MapView after SDK Init
            AndroidView(
                factory = { ctx ->
                    try {
                        MapView(ctx).apply {
                        start(object : MapLifeCycleCallback() {
                            override fun onMapDestroy() {}
                            override fun onMapError(e: Exception?) {
                                android.util.Log.e("AllToDo", "Map Error: ${e?.message}")
                            }
                        }, object : KakaoMapReadyCallback() {
                            override fun onMapReady(map: KakaoMap) {
                                kakaoMap = map
                                isMapReady = true



                                map.setOnLabelClickListener { _, _, label ->
                                    val pos = label.position
                                    val logItems = viewModel.userLogs.value.map { UnifiedItem.History(it) }
                                    val todoItemsList = viewModel.todoItems.value.map { UnifiedItem.Todo(it) }
                                    val curr = if (currentLocation != null) listOf(UnifiedItem.CurrentLocation(currentLocation!!.latitude, currentLocation!!.longitude)) else emptyList()
                                    val all = logItems + todoItemsList + curr

                                    val density = ctx.resources.displayMetrics.density
                                    val clusterThresholdPx = 60 * density

                                    val screenPoints = all.mapNotNull { item ->
                                        val pt = map.toScreenPoint(com.kakao.vectormap.LatLng.from(item.latitude, item.longitude))
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
                                            val dx = (center.second - other.second).toDouble()
                                            val dy = (center.third - other.third).toDouble()
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
                                        if (selectedCluster?.latitude == clickedCluster.latitude && selectedCluster?.longitude == clickedCluster.longitude) {
                                            selectedCluster = null
                                        } else {
                                            selectedCluster = clickedCluster
                                        }
                                    }
                                    return@setOnLabelClickListener true
                                }

                                map.setOnCameraMoveEndListener { _, cameraPosition, _ ->
                                    cameraMoveState++
                                    compassRotation = -Math.toDegrees(cameraPosition.rotationAngle).toFloat()
                                }
                                map.setOnCameraMoveStartListener { _, _ ->
                                    selectedCluster = null
                                }

                                map.setOnMapClickListener { _, latLng, _, _ ->
                                    newTodoLocation = latLng
                                    showAddTodoDialog = true
                                }
                            }
                        })
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AllToDo", "CRITICAL: Failed to create MapView", e)
                    android.widget.TextView(ctx).apply {
                        text = "Map Unavailable: ${e.message}"
                        setTextColor(android.graphics.Color.RED)
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        } else {
             Box(Modifier.fillMaxSize().background(Color.White), contentAlignment=Alignment.Center) { 
                 Text("Initializing Map...") 
             }
        }

        if (showAddTodoDialog) {
            AlertDialog(
                onDismissRequest = { showAddTodoDialog = false },
                title = { Text("Add Todo") },
                text = {
                    TextField(
                        value = newTodoText,
                        onValueChange = { newTodoText = it },
                        placeholder = { Text("Task name") }
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        newTodoLocation?.let {
                            viewModel.addTodo(newTodoText, it.latitude, it.longitude)
                        }
                        showAddTodoDialog = false
                        newTodoText = ""
                    }) { Text("Add") }
                },
                dismissButton = {
                    Button(onClick = { showAddTodoDialog = false }) { Text("Cancel") }
                }
            )
        }

        // [NEW] Bottom Sheet / All Items List
        if (showBottomSheet) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(1000f)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { showBottomSheet = false }
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .clickable(enabled = false) {} // Prevent click-through
                ) {
                    TodoListContent(
                        todoItems = todoItems,
                        onAddTodo = { text ->
                            val lat = currentLocation?.latitude ?: 0.0
                            val lon = currentLocation?.longitude ?: 0.0
                            viewModel.addTodo(text, lat, lon)
                        },
                        onToggleTodo = { viewModel.toggleTodo(it) },
                        onDeleteTodo = { viewModel.deleteTodo(it) }
                    )
                }
            }
        }

        // [NEW] Detail Popup Dialog
        if (showDetailPopup != null) {
            val item = showDetailPopup!!
            val title = when (item) {
                is UnifiedItem.Todo -> "Todo Details"
                is UnifiedItem.History -> "History Log Details"
                is UnifiedItem.CurrentLocation -> "Current Location"
            }
            val timeStr = SimpleDateFormat("yyyy.MM.dd HH:mm:ss", Locale.getDefault()).format(Date(item.timestamp))

            AlertDialog(
                onDismissRequest = { showDetailPopup = null },
                title = { Text(title) },
                text = {
                    Column {
                        if (item is UnifiedItem.Todo) {
                            Text("Task: ${item.item.text}")
                        }
                        Text("Time: $timeStr")
                        Text("Coords: %.4f, %.4f".format(item.latitude, item.longitude))
                    }
                },
                confirmButton = {
                    Button(onClick = { showDetailPopup = null }) {
                        Text("Close")
                    }
                }
            )
        }
        
        // [NEW] User Profile Overlay
        if (showMyInfo) {
             Box(
                 modifier = Modifier
                     .fillMaxSize()
                     .background(Color.Black.copy(alpha = 0.3f))
                     .zIndex(1100f) // Top
                     .clickable { showMyInfo = false }
             ) {
                 UserProfileView(
                     modifier = Modifier.align(Alignment.CenterStart).padding(start = 20.dp), // Left aligned, avoiding right controls
                     onDismiss = { showMyInfo = false },
                     maxPopupItems = maxPopupItems,
                     onMaxItemsChange = { viewModel.updateMaxPopupItems(it) },
                     popupFontSize = popupFontSize,
                     onFontSizeChange = { viewModel.updatePopupFontSize(it) }
                 )
             }
        }

        LaunchedEffect(selectedCluster) {
            val map = kakaoMap
            if (selectedCluster != null && map != null) {
                while (selectedCluster != null) {
                    val pt = map.toScreenPoint(com.kakao.vectormap.LatLng.from(selectedCluster!!.latitude, selectedCluster!!.longitude))
                    if (pt != null) {
                        selectedClusterPosition = Offset(pt.x.toFloat(), pt.y.toFloat())
                    }
                    delay(16)
                }
            }
        }

        // [NEW] History Mode Camera Logic
        LaunchedEffect(showHistoryMode) {
             val map = kakaoMap
             if (map != null && !showHistoryMode) { 
                 // Returning to Today
                 val points = mutableListOf<com.kakao.vectormap.LatLng>()
                 if (currentLocation != null) points.add(com.kakao.vectormap.LatLng.from(currentLocation!!.latitude, currentLocation!!.longitude))
                 todoItems.filter { it.source == "local" }.forEach { points.add(com.kakao.vectormap.LatLng.from(it.latitude ?: 0.0, it.longitude ?: 0.0)) }
                 
                 if (points.isNotEmpty()) {
                     map.moveCamera(CameraUpdateFactory.fitMapPoints(points.toTypedArray(), 100))
                 }
                 
                 delay(3000)
                 
                 if (currentLocation != null) {
                     map.moveCamera(
                         CameraUpdateFactory.newCenterPosition(com.kakao.vectormap.LatLng.from(currentLocation!!.latitude, currentLocation!!.longitude), 15),
                         com.kakao.vectormap.camera.CameraAnimation.from(500, true, true)
                     )
                 }
             }
        }

        if (selectedCluster != null && selectedClusterPosition != null) {
            val cluster = selectedCluster!!
            val items = cluster.items

            val isSingle = items.size == 1
            val bubbleWidth = if (isSingle) 180.dp else 260.dp
            val bubbleHeight = if (isSingle) 50.dp else if (items.size >= 4) 250.dp else (items.size * 50 + 25).dp

            val density = LocalDensity.current
            val centerOffsetX = with(density) { (bubbleWidth.toPx() / 2).toInt() }

            // [FIX] Overlay Implementation
            Box(modifier = Modifier.fillMaxSize().zIndex(999f)) {
                // Scrim (Transparent & Clickable to Close)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(androidx.compose.ui.graphics.Color.Transparent)
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, 
                            indication = null
                        ) { 
                            selectedCluster = null 
                        }
                )

                // Callout Bubble
                // [NEW] Callout Bubble Redesign
                Box(
                    modifier = Modifier
                    .offset { androidx.compose.ui.unit.IntOffset(x = selectedClusterPosition!!.x.toInt() - centerOffsetX, y = selectedClusterPosition!!.y.toInt() - bubbleHeight.roundToPx() - 60.dp.roundToPx()) }
                    .width(bubbleWidth)
                    // .height(bubbleHeight) // Let it wrap content? No, maintain logic for now or adapt.
                    // The old logic calculated fixed height. Let's try flexible height but capped.
                    .heightIn(max = (maxPopupItems * 60).dp.coerceIn(100.dp, 400.dp))
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .shadow(4.dp, RoundedCornerShape(16.dp))
                    .border(1.dp, Color.LightGray, RoundedCornerShape(16.dp))
                    .zIndex(1000f)
                ) {
                     // val limitedItems = items.take(maxPopupItems) // [RESTORED] Limit logic - REMOVED
                     val fontSizeSp = when(popupFontSize) {

                         0 -> 12.sp
                         2 -> 16.sp
                         else -> 14.sp
                     }
                     
                     LazyColumn(modifier = Modifier.padding(vertical = 8.dp)) {
                         items(items.size) { idx -> // [FIX] Use ALL items
                             val item = items[idx]
                             
                             // [NEW] Check Path Existence
                             var hasPath = false
                             if (item is UnifiedItem.History) {
                                 val raw = item.log.pathData
                                 if (raw != null && raw.length > 10 && raw != "[]") hasPath = true
                             }

                             Row(
                                 modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 28.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                                 verticalAlignment = Alignment.CenterVertically
                             ) {
                                 // 1. Icon
                                 val iconTint = when {
                                     item is UnifiedItem.Todo -> Color(0xFF00AA00)
                                     item is UnifiedItem.History && hasPath -> Color.Red
                                     else -> Color.LightGray 
                                 }
                                 Icon(
                                     imageVector = Icons.Default.LocationOn, 
                                     contentDescription = null, 
                                     tint = iconTint,
                                     modifier = Modifier.size(20.dp).clickable(enabled = hasPath || item is UnifiedItem.Todo) {
                                          if (item is UnifiedItem.History && hasPath) {
                                              try {
                                                  val raw = item.log.pathData
                                                  val json = JSONArray(raw)
                                                  val points = mutableListOf<com.kakao.vectormap.LatLng>()
                                                  for (i in 0 until json.length()) {
                                                      val obj = json.getJSONObject(i)
                                                      points.add(com.kakao.vectormap.LatLng.from(obj.getDouble("lat"), obj.getDouble("lon")))
                                                  }
                                                  currentPathPoints = points
                                                  showPathMode = true
                                                  // selectedCluster = null // Keep open or close? User didn't specify, but usually new window -> close old context
                                                  selectedCluster = null
                                              } catch (e: Exception) {}
                                          }
                                     }
                                 )
                                 
                                 Spacer(modifier = Modifier.width(12.dp))
                                 
                                 // 2. Time/Title
                                 val timeStr = SimpleDateFormat("a h:mm", Locale.getDefault()).format(Date(item.timestamp)) // [FIX] AM/PM Format
                                 val text = if (item is UnifiedItem.Todo) item.item.text else timeStr
                                 
                                 Text(
                                     text = text,
                                     fontSize = fontSizeSp,
                                     color = Color.Black,
                                     modifier = Modifier.weight(1f).clickable {
                                          if (item is UnifiedItem.Todo) showDetailPopup = item
                                          if (item is UnifiedItem.History && hasPath) {
                                              try {
                                                  val raw = item.log.pathData
                                                      val json = JSONArray(raw)
                                                      val points = mutableListOf<com.kakao.vectormap.LatLng>()
                                                      for (i in 0 until json.length()) {
                                                          val obj = json.getJSONObject(i)
                                                          points.add(com.kakao.vectormap.LatLng.from(obj.getDouble("lat"), obj.getDouble("lon")))
                                                      }
                                                      currentPathPoints = points
                                                      showPathMode = true
                                                      selectedCluster = null 
                                              } catch (e: Exception) {}
                                          }
                                     },
                                     maxLines = 1
                                 )
                                 
                                 // 3. Trash
                                 IconButton(
                                     onClick = {
                                        if (item is UnifiedItem.Todo) viewModel.deleteTodo(item.item)
                                        if (item is UnifiedItem.History) viewModel.deleteUserLog(item.log)
                                        selectedCluster = null // [FIX] Force Close to refresh/confirm deletion
                                     },
                                     modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 4.dp).size(24.dp) 
                                 ) {
                                     Icon(Icons.Default.Delete, contentDescription = "Del", tint = Color.Red)
                                 }
                             }
                             if (idx < items.size - 1) {
                                 Divider(color = Color.LightGray, thickness = 0.5.dp)
                             }
                         }
                     }
                }
            } // Close Overlay Box
        }

        TopLeftWidget(
            historyCount = userLogs.size,
            localTodoCount = todoItems.count { it.source == "local" },
            serverTodoCount = todoItems.count { it.source != "local" },
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 80.dp),
            onExpandClick = { showBottomSheet = true }
        )

        RightSideControls(
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 16.dp),
            compassRotation = compassRotation,
            showHistoryMode = showHistoryMode, // [NEW]
            onHistoryClick = { showHistoryMode = !showHistoryMode }, // [NEW]
            onNotificationClick = {},
            onLoginClick = { showMyInfo = true },
            onLocationClick = {
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                        loc?.let { kakaoMap?.moveCamera(CameraUpdateFactory.newCenterPosition(com.kakao.vectormap.LatLng.from(it.latitude, it.longitude))) }
                    }
                }
            },
            onZoomInClick = { kakaoMap?.moveCamera(CameraUpdateFactory.zoomIn()) },
            onZoomOutClick = { kakaoMap?.moveCamera(CameraUpdateFactory.zoomOut()) },
            onCompassClick = { kakaoMap?.moveCamera(CameraUpdateFactory.rotateTo(0.0)) }
        )

        if (showPathMode) {
             PathDetailPopup(
                 pathPoints = currentPathPoints,
                 onDismiss = { showPathMode = false }
             )
        }



        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, bottom = 180.dp)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            Text(
                text = "ID: ${com.example.alltodo.services.RemoteLogger.deviceID.take(8)}",
                color = Color.LightGray,
                fontSize = 10.sp
            )

            val debugText by viewModel.debugStatus.collectAsState()
            Text(
                text = debugText,
                color = Color.White,
                fontSize = 12.sp
            )
        }
    }
}

sealed class UnifiedItem {
    abstract val latitude: Double
    abstract val longitude: Double
    abstract val timestamp: Long

    data class Todo(val item: com.example.alltodo.data.TodoItem) : UnifiedItem() {
        override val latitude get() = item.latitude ?: 0.0
        override val longitude get() = item.longitude ?: 0.0
        override val timestamp get() = item.createdAt
    }

    data class History(val log: com.example.alltodo.data.UserLog) : UnifiedItem() {
        override val latitude get() = log.latitude
        override val longitude get() = log.longitude
        override val timestamp get() = log.startTime
    }

    data class CurrentLocation(val lat: Double, val lon: Double) : UnifiedItem() {
        override val latitude get() = lat
        override val longitude get() = lon
        override val timestamp get() = System.currentTimeMillis()
    }
}

// [FIX] Path Detail Popup Component
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
                                 // User said "Pins color should be Red".
                                 val labelManager = map.labelManager
                                 val labelLayer = labelManager?.getLayer("pathLabels") ?: labelManager?.addLayer(com.kakao.vectormap.label.LabelLayerOptions.from("pathLabels"))
                                 
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

fun createRedDotBitmap(): Bitmap {
    val size = 40
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint()
    paint.color = android.graphics.Color.RED
    paint.isAntiAlias = true
    canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
    return bitmap
}

data class PinCluster(
    val latitude: Double,
    val longitude: Double,
    val items: List<UnifiedItem>,
    val type: String = "mixed",
    val hasMixed: Boolean = false
)

fun generateDiamondPin(color: Int, count: Int): android.graphics.Bitmap? {
    val width = 100
    val height = 120
    val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)

    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        style = android.graphics.Paint.Style.FILL
        setColor(color)
    }

    val path = android.graphics.Path()
    path.moveTo(width / 2f, 0f)
    path.lineTo(width.toFloat(), height * 0.4f)
    path.lineTo(width / 2f, height.toFloat())
    path.lineTo(0f, height * 0.4f)
    path.close()

    canvas.drawPath(path, paint)

    if (count > 1) {
        paint.color = android.graphics.Color.WHITE
        val cx = width / 2f
        val cy = height * 0.4f
        val r = width / 4f
        canvas.drawCircle(cx, cy, r, paint)

        paint.color = color
        paint.textSize = 30f
        paint.textAlign = android.graphics.Paint.Align.CENTER
        val txt = if (count > 9) "9+" else count.toString()
        val bounds = android.graphics.Rect()
        paint.getTextBounds(txt, 0, txt.length, bounds)
        canvas.drawText(txt, cx, cy - bounds.exactCenterY(), paint)
    } else {
        paint.color = android.graphics.Color.WHITE
        canvas.drawCircle(width / 2f, height * 0.4f, width / 8f, paint)
    }

    return bitmap
}
