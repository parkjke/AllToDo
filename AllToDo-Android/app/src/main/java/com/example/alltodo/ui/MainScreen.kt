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
import com.example.alltodo.ui.components.PathDetailPopup
import com.example.alltodo.ui.components.GooglePathDetailPopup
import com.example.alltodo.ui.components.NaverPathDetailPopup // [NEW]
import com.example.alltodo.ui.components.KakaoMapContent
import com.example.alltodo.ui.components.NaverMapContent // [NEW]
import com.example.alltodo.ui.components.UserProfileView // [NEW]
import com.example.alltodo.ui.components.GoogleMapMarkers // [NEW]
import com.example.alltodo.ui.components.GoogleMapContent // [NEW]
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
import kotlinx.coroutines.launch // [FIX] Import
import com.kakao.vectormap.camera.CameraAnimation // [FIX] Import
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import android.os.Looper
import kotlinx.coroutines.awaitCancellation

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
                // viewModel.startSession() // Already called in init? No, init calls it.
                // But onResume logic might be needed.
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
    var kakaoMap by remember { mutableStateOf<KakaoMap?>(null) }
    var naverMap by remember { mutableStateOf<com.naver.maps.map.NaverMap?>(null) }
    
    // [FIX] Split isMapReady
    var isKakaoMapReady by remember { mutableStateOf(false) }
    var isGoogleMapReady by remember { mutableStateOf(false) }
    var isNaverMapReady by remember { mutableStateOf(false) }

    var isSdkInitialized by remember { mutableStateOf(false) }
    var showBottomSheet by remember { mutableStateOf(false) }
    var showMyInfo by remember { mutableStateOf(false) }
    var compassRotation by remember { mutableStateOf(0f) }

    // [NEW] Time Travel & Settings
    var showHistoryMode by remember { mutableStateOf(false) }
    val maxPopupItems by viewModel.maxPopupItems.collectAsState()
    val popupFontSize by viewModel.popupFontSize.collectAsState()

    // Coroutine Scope for Map Animations
    val scope = rememberCoroutineScope() 

    // Selected Cluster/Item for Callout
    var selectedCluster by remember { mutableStateOf<PinCluster?>(null) }
    var selectedClusterPosition by remember { mutableStateOf<Offset?>(null) } // Screen position
    var showDetailPopup by remember { mutableStateOf<UnifiedItem?>(null) } 

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
                 isSdkInitialized = true
             }
        } catch (t: Throwable) {
             android.util.Log.e("AllToDo", "SDK Init Fail", t)
             // Maybe show a toast?
        }
    }

    // Map Provider State with Persistence
    val prefs = remember { context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE) }
    var mapProvider by remember {
        val saved = prefs.getString("map_provider", "Kakao")
        val initial = MapProvider.values().find { it.name == saved } ?: MapProvider.Kakao
        mutableStateOf(initial)
    }
    LaunchedEffect(mapProvider) {
        isKakaoMapReady = false
        isGoogleMapReady = false
        isNaverMapReady = false
        prefs.edit().putString("map_provider", mapProvider.name).apply()
    }
    
    // [FIX] Location Tracking Loop (Buffering via ViewModel)
    // Dependencies: fusedLocationClient, mapProvider, flags
    LaunchedEffect(isKakaoMapReady, isGoogleMapReady) {
        val isReady = when(mapProvider) { // Use simpler logic or just run if provider matches
           MapProvider.Kakao -> isKakaoMapReady
           MapProvider.Google -> isGoogleMapReady
           else -> false
        }
        
        if (isReady) {
           // Intro Animation specific to Kakao for now
           if (mapProvider == MapProvider.Kakao) {
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
           }
           
            // Motion Detection Logic
            var isMoving by remember { mutableStateOf(true) }
            val motionListener = remember {
                object : com.example.alltodo.utils.MotionListener {
                    override fun onMotionStateChanged(moving: Boolean) {
                        isMoving = moving
                    }
                }
            }
            val motionDetector = remember { com.example.alltodo.utils.MotionDetector(context, motionListener) }
            
            // Start Motion Detection
            DisposableEffect(Unit) {
                motionDetector.start()
                onDispose { motionDetector.stop() }
            }

           // Common Tracking Loop - [FIX] Use Callback instead of Polling
           val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 900)
               .setMinUpdateIntervalMillis(900)
               .build()

           val locationCallback = object : LocationCallback() {
               override fun onLocationResult(result: LocationResult) {
                   for (location in result.locations) {
                       currentLocation = location
                       viewModel.saveLocation(location.latitude, location.longitude)
                   }
               }
           }
           
           // Toggle Location Updates based on Motion
           LaunchedEffect(isMoving) {
               if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                   if (isMoving) {
                        try {
                            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
                            com.example.alltodo.utils.OptimizationLogger.log(context, com.example.alltodo.utils.LogType.LOCATION_RESUME, "User Moving")
                        } catch (e: SecurityException) { e.printStackTrace() }
                   } else {
                        fusedLocationClient.removeLocationUpdates(locationCallback)
                        com.example.alltodo.utils.OptimizationLogger.log(context, com.example.alltodo.utils.LogType.LOCATION_PAUSE, "User Stationary")
                   }
               }
           }
           
           // Cleanup on exit
           DisposableEffect(Unit) {
               onDispose {
                   fusedLocationClient.removeLocationUpdates(locationCallback)
               }
           }
        }
    }

    // [FIX] Hoisted Google Camera State
    val googleCameraState = com.google.maps.android.compose.rememberCameraPositionState {
         position = com.google.android.gms.maps.model.CameraPosition.fromLatLngZoom(
             com.google.android.gms.maps.model.LatLng(37.5665, 126.9780), 12f
         )
    }

    // [NEW] Unified Filtering Logic for All Maps
    // [NEW] Use Clustered Items from ViewModel
    val clusteredItems by viewModel.clusteredItems.collectAsState()
    val displayItems by viewModel.displayItems.collectAsState() // [FIX] For Kakao/Naver fallback
    
    // [FIX] Zoom Tracking for Google Map
    LaunchedEffect(googleCameraState.position.zoom) {
         viewModel.updateZoom(googleCameraState.position.zoom)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            when (mapProvider) {
                MapProvider.Google -> {
                    GoogleMapContent(
                        modifier = Modifier.fillMaxSize(),
                        clusteredItems = clusteredItems, // [FIX] Pass Clustered Items
                        currentLocation = currentLocation,
                        cameraPositionState = googleCameraState,
                        onMapClick = { latLng ->
                            showDetailPopup = null
                            selectedCluster = null // Also clear cluster
                        },
                        onMapLongClick = { latLng ->
                            newTodoLocation = latLng
                            showAddTodoDialog = true
                        },
                        onItemClick = { item -> showDetailPopup = item }, // Fallback
                        onItemClickWithCoords = { item, x, y ->
                            selectedClusterPosition = Offset(x, y)
                            selectedCluster = PinCluster(
                                item.latitude, item.longitude, 
                                listOf(item), 
                                if(item is UnifiedItem.Todo) "todo" else "history"
                            )
                        },
                        onClusterClickWithCoords = { items, x, y -> // [FIX] New Callback for Clusters
                            selectedClusterPosition = Offset(x, y)
                            // Determine type logic simplistically
                            val hasTodo = items.any { it is UnifiedItem.Todo }
                            val hasHistory = items.any { it is UnifiedItem.History }
                            val type = if(hasTodo && hasHistory) "mixed" else if(hasTodo) "todo" else "history"
                            
                            val first = items.firstOrNull() ?: items.first()
                            selectedCluster = PinCluster(
                                first.latitude, first.longitude,
                                items,
                                type
                            )
                        },
                        onRotationChange = { rot -> compassRotation = rot },
                        isMapReady = isGoogleMapReady, // [FIX] Use Google flag
                        onMapLoaded = { isGoogleMapReady = true }, // [FIX] Set Google flag
                        showHistoryMode = showHistoryMode
                    )
                }

                MapProvider.Kakao -> {
                    com.example.alltodo.ui.components.KakaoMapContent(
                        modifier = Modifier.fillMaxSize(),
                        isSdkInitialized = isSdkInitialized,
                        items = displayItems, // [FIX] Use displayItems
                        currentLocation = currentLocation,
                        selectedCluster = selectedCluster,
                        onMapReady = { map ->
                            kakaoMap = map
                            isKakaoMapReady = true // [FIX] Set Kakao flag
                        },
                        onClusterClick = { cluster ->
                            if (selectedCluster?.latitude == cluster.latitude && selectedCluster?.longitude == cluster.longitude) {
                                selectedCluster = null
                            } else {
                                selectedCluster = cluster
                            }
                        },
                        onMapClick = { latLng ->
                            newTodoLocation = latLng
                            showAddTodoDialog = true
                        },
                        onCameraRotate = { rot -> compassRotation = rot },
                        onCameraMoveStart = { selectedCluster = null }
                    )
                }
                MapProvider.Naver -> {
                    NaverMapContent(
                        modifier = Modifier.fillMaxSize(),
                        items = displayItems, // [FIX] Use displayItems
                        currentLocation = currentLocation,
                        onMapReady = { map ->
                            naverMap = map
                            isNaverMapReady = true // [FIX] Set Naver flag
                        },
                        onItemClick = { item -> showDetailPopup = item }
                    )
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
                     modifier = Modifier.align(Alignment.CenterStart).padding(start = 20.dp), // Left aligned,
                     onDismiss = { showMyInfo = false },
                     maxPopupItems = maxPopupItems,
                     onMaxItemsChange = { viewModel.updateMaxPopupItems(it) },
                     popupFontSize = popupFontSize,
                     onFontSizeChange = { viewModel.updatePopupFontSize(it) },
                     currentMapProvider = mapProvider,
                     onMapProviderChange = { mapProvider = it }
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

            onCompassClick = {
                when (mapProvider) {
                    MapProvider.Kakao -> {
                        kakaoMap?.moveCamera(CameraUpdateFactory.rotateTo(0.0), CameraAnimation.from(500, true, true))
                        compassRotation = 0f
                    }
                    MapProvider.Google -> {
                         scope.launch {
                             try {
                                 googleCameraState.animate(
                                     com.google.android.gms.maps.CameraUpdateFactory.newCameraPosition(
                                         com.google.android.gms.maps.model.CameraPosition.Builder(googleCameraState.position)
                                             .bearing(0f)
                                             .build()
                                     ),
                                     500 // duration ms
                                 )
                                 compassRotation = 0f // Update after animation or immediately? UI hides on 0.
                             } catch (e: Exception) {}
                         }
                    }
                    MapProvider.Naver -> {
                         val target = naverMap?.cameraPosition?.target ?: com.naver.maps.geometry.LatLng(37.5665, 126.9780)
                         val currentZoom = naverMap?.cameraPosition?.zoom ?: 15.0
                         val cameraPosition = com.naver.maps.map.CameraPosition(target, currentZoom, 0.0, 0.0)
                         val update = com.naver.maps.map.CameraUpdate.toCameraPosition(cameraPosition)
                             .animate(com.naver.maps.map.CameraAnimation.Easing)
                         naverMap?.moveCamera(update)
                         compassRotation = 0f
                    }
                }
            },
            onLocationClick = {
                 if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                     fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                         loc?.let { 
                             val lat = it.latitude
                             val lon = it.longitude
                             when (mapProvider) {
                                 MapProvider.Kakao -> {
                                     kakaoMap?.moveCamera(CameraUpdateFactory.newCenterPosition(com.kakao.vectormap.LatLng.from(lat, lon))) 
                                 }
                                 MapProvider.Naver -> {
                                     val update = com.naver.maps.map.CameraUpdate.scrollTo(com.naver.maps.geometry.LatLng(lat, lon))
                                     naverMap?.moveCamera(update)
                                 }
                                 MapProvider.Google -> {
                                     googleCameraState.move(com.google.android.gms.maps.CameraUpdateFactory.newLatLng(com.google.android.gms.maps.model.LatLng(lat, lon)))
                                 }
                                 else -> {}
                             }
                         }
                     }
                 }
            },
            onZoomInClick = { 
                when (mapProvider) {
                    MapProvider.Kakao -> kakaoMap?.moveCamera(CameraUpdateFactory.zoomIn())
                    MapProvider.Naver -> {
                        val update = com.naver.maps.map.CameraUpdate.zoomBy(1.0)
                        naverMap?.moveCamera(update)
                    }
                    MapProvider.Google -> googleCameraState.move(com.google.android.gms.maps.CameraUpdateFactory.zoomIn())
                    else -> {}
                }
            },
            onZoomOutClick = { 
                when (mapProvider) {
                    MapProvider.Kakao -> kakaoMap?.moveCamera(CameraUpdateFactory.zoomOut())
                    MapProvider.Naver -> {
                        val update = com.naver.maps.map.CameraUpdate.zoomBy(-1.0)
                        naverMap?.moveCamera(update)
                    }
                    MapProvider.Google -> googleCameraState.move(com.google.android.gms.maps.CameraUpdateFactory.zoomOut())
                    else -> {}
                }
            }
        )



        if (showPathMode) {
            if (mapProvider == MapProvider.Google) {
                // Convert Kakao LatLngs (currentPathPoints) to Google LatLngs
                val googlePoints = currentPathPoints.map {
                    com.google.android.gms.maps.model.LatLng(it.latitude, it.longitude)
                }
                GooglePathDetailPopup(
                    pathPoints = googlePoints,
                    onDismiss = { showPathMode = false }
                )
            } else if (mapProvider == MapProvider.Naver) {
                // Convert to Naver LatLngs
                val naverPoints = currentPathPoints.map {
                    com.naver.maps.geometry.LatLng(it.latitude, it.longitude)
                }
                NaverPathDetailPopup(
                    pathPoints = naverPoints,
                    onDismiss = { showPathMode = false }
                )
            } else {
                // Default / Kakao
                PathDetailPopup(
                    pathPoints = currentPathPoints,
                    onDismiss = { showPathMode = false }
                )
            }
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
}


