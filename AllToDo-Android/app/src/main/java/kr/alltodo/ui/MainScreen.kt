package kr.alltodo.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import kr.alltodo.ui.theme.AllToDoTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.hilt.navigation.compose.hiltViewModel
import kr.alltodo.ui.components.RightSideControls
import kr.alltodo.ui.components.TopLeftWidget
import kr.alltodo.ui.components.UserProfileView
import kr.alltodo.ui.components.KakaoMapContent
import kr.alltodo.ui.components.NaverMapContent
import kr.alltodo.ui.components.GoogleMapContent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Job
import androidx.compose.ui.unit.dp

@Composable
fun MainScreen(
    todoViewModel: TodoViewModel = hiltViewModel(),
    gpsAuthViewModel: GpsAuthViewModel = hiltViewModel(),
    mapViewModel: MapFeatureViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("kr.alltodo.prefs", android.content.Context.MODE_PRIVATE) }
    
    // [NEW] 5-second Grace Period State
    var pendingEndSessionTask by remember { mutableStateOf<Job?>(null) }

    // 1. States for Requirements
    // MapProvider logic (Local for now, passed to VM)
    var mapProvider by remember { 
        mutableStateOf(MapProvider.valueOf(prefs.getString("map_provider", "Naver") ?: "Naver"))
//        mutableStateOf(MapProvider.Kakao)
    }

    // ViewModel State Collection
    val showMyInfo by mapViewModel.showMyInfo.collectAsState()
    val compassRotation by mapViewModel.compassRotation.collectAsState()
    
    // [NEW] Callout States for "Water Balloon"
    val selectedCluster by mapViewModel.selectedCluster.collectAsState()
    val tapScreenPosition by mapViewModel.tapPosition.collectAsState()
    val maxPopupItems by todoViewModel.maxPopupItems.collectAsState()
    val popupFontSize by todoViewModel.popupFontSize.collectAsState()

    // [MOVED UP] GPS Auth State
    val isTracking by gpsAuthViewModel.isTracking.collectAsState()
    val showActivePath by gpsAuthViewModel.showActivePath.collectAsState()
    val activePoints by gpsAuthViewModel.points.collectAsState()
    
    // [NEW] Search VM (Moved up to avoid unresolved references)
    val searchViewModel: kr.alltodo.ui.SearchViewModel = hiltViewModel()
    val isSearchVisible by searchViewModel.isOverlayVisible.collectAsState()
    val isListVisible by todoViewModel.isListLayerVisible.collectAsState()

    // [NEW] Path Viewer State (Now using TodoItem for ID context)
    var viewingPathTodo by remember { mutableStateOf<kr.alltodo.data.TodoItem?>(null) }

    // [NEW] Create Todo States
    val isCreatingTodo by mapViewModel.isCreatingTodo.collectAsState()
    val creatingLocation by mapViewModel.creatingLocation.collectAsState()
    val initialTodoName by mapViewModel.initialTodoName.collectAsState()
    val initialTodoTitle by mapViewModel.initialTodoTitle.collectAsState()
    
    // Bridge for legacy LatLng types locally if needed, or use the one from VM
    // We will update the usages to use `creatingLocation`.
    
    // [NEW] Navigation Recovery State
    var shouldRestoreList by remember { mutableStateOf(false) }
    var shouldRestoreCalendar by remember { mutableStateOf(false) }

    // [Item 0] beforeLocation Persistence
    val beforeLocation = remember {
        val lat = prefs.getFloat("before_lat", kr.alltodo.utils.SmartLocationManager.GWANGHWAMUN_LAT.toFloat())
        val lon = prefs.getFloat("before_lon", kr.alltodo.utils.SmartLocationManager.GWANGHWAMUN_LON.toFloat())
        mutableStateOf(android.location.Location("service").apply {
            latitude = lat.toDouble()
            longitude = lon.toDouble()
        })
    }
    
    // Zoom/Location States
    val currentLocation = remember { mutableStateOf<android.location.Location?>(null) }
    val googleCameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            LatLng(beforeLocation.value.latitude, beforeLocation.value.longitude),
            15f
        )
    }
    
    // Permission Handling
    var hasLocationPermission by remember { 
        mutableStateOf(context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasLocationPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasLocationPermission) permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    // Kakao SDK Init (Handled in Application)
    val isKakaoInitialized = true

    // [FIX] Location Tracking: Continuous updates for Path Recording
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val locationRequest = remember {
        com.google.android.gms.location.LocationRequest.Builder(
            com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
            1000 // 1 second interval
        ).setMinUpdateIntervalMillis(500).build()
    }
    
    val locationCallback = remember {
        object : com.google.android.gms.location.LocationCallback() {
            override fun onLocationResult(result: com.google.android.gms.location.LocationResult) {
                result.locations.forEach { loc ->
                    if (loc.latitude != 0.0 || loc.longitude != 0.0) {
                        currentLocation.value = loc
                        val now = System.currentTimeMillis()
                        todoViewModel.updateCurrentLocation(loc.latitude, loc.longitude)
                        todoViewModel.saveLocation(loc.latitude, loc.longitude) // [NEW] Save to History Buffer
                        gpsAuthViewModel.addLocation(loc.latitude, loc.longitude, now) // [FIX] Sync with GPS Tracker
                        
                        // [Item 0] Continually update beforeLocation
                        beforeLocation.value = loc
                        prefs.edit()
                            .putFloat("before_lat", loc.latitude.toFloat())
                            .putFloat("before_lon", loc.longitude.toFloat())
                            .apply()
                    }
                }
            }
        }
    }

    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            try {
                // [FIX] Priority HIGH_ACCURACY + Persistent request
                fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                    if (loc != null && (loc.latitude != 0.0 || loc.longitude != 0.0)) {
                        currentLocation.value = loc
                        todoViewModel.updateCurrentLocation(loc.latitude, loc.longitude)
                        todoViewModel.saveLocation(loc.latitude, loc.longitude)
                        gpsAuthViewModel.addLocation(loc.latitude, loc.longitude, System.currentTimeMillis())
                        beforeLocation.value = loc
                    }
                }
                
                fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    android.os.Looper.getMainLooper()
                )
            } catch (e: SecurityException) { 
                e.printStackTrace()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

    // [NEW] 5-second Grace Period for path continuity (Match iOS parity)
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> {
                    // Start 5-second timer
                    pendingEndSessionTask = scope.launch {
                        delay(5000)
                        println(">>> APP STATE: Background -> Grace Period Expired -> Ending Session")
                        todoViewModel.endSession()
                        gpsAuthViewModel.stopTrackingAndSave()
                    }
                }
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                    // Check if grace period task is still running
                    if (pendingEndSessionTask?.isActive == true) {
                        println(">>> APP STATE: Active -> Grace Period Success (Session Continues)")
                        pendingEndSessionTask?.cancel()
                    } else {
                        // Task finished (End Session called) or First Resume
                        println(">>> APP STATE: Active -> Long Resume -> Refreshing Data Validation")
                        todoViewModel.updateFilteredItems(immediate = true)
                    }
                    pendingEndSessionTask = null
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // [NEW] Toast for Far Items
    // Use MapViewModel's farItemsCount
    val farItemCount by mapViewModel.farItemsCount.collectAsState()
    LaunchedEffect(farItemCount) {
        if (farItemCount > 0) {
            android.widget.Toast.makeText(context, "현재 위치에서 너무 먼 핀 ${farItemCount}개는 표시되지 않습니다.", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    // [Item 4] Animation State - Reset when provider changes
    var initialAnimationDone by remember(mapProvider) { mutableStateOf(false) }
    
    // [FIX] Auto-hide My Info on Map Change
    LaunchedEffect(mapProvider) {
        mapViewModel.toggleMyInfo(false)
    }
    
    // [NEW] Data Synchronization (TodoViewModel -> MapViewModel)
    val todoItems by todoViewModel.todoItems.collectAsState()
    LaunchedEffect(todoItems, currentLocation.value, mapProvider) {
        System.out.println(">>> [MainScreen] Updating Map Items: count=${todoItems.size}, provider=$mapProvider")
        mapViewModel.updateMapItems(
            allItems = todoItems,
            currentLocation = currentLocation.value,
            mapProvider = mapProvider
        )
    }
    
    // Map Instances for Camera Control
    var naverMapInstance by remember { mutableStateOf<com.naver.maps.map.NaverMap?>(null) }
    var kakaoMapInstance by remember { mutableStateOf<com.kakao.vectormap.KakaoMap?>(null) }
    var isGoogleMapReady by remember(mapProvider) { mutableStateOf(false) } // [FIX]
    
    // [FIX] Live Path Data for Visualization
    val liveSessionPoints by todoViewModel.liveSessionPoints.collectAsState()
    
    // [NEW] Map Action Observation Loop
    val mapAction by mapViewModel.mapAction.collectAsState()
    
    // Helper to execute actions (DRY)
    fun executeMapAction(action: MapAction) {
        when (action) {
            MapAction.ZOOM_IN -> {
                when (mapProvider) {
                    MapProvider.Naver -> naverMapInstance?.let { it.moveCamera(com.naver.maps.map.CameraUpdate.zoomIn().animate(com.naver.maps.map.CameraAnimation.Easing)) }
                    MapProvider.Kakao -> kakaoMapInstance?.let { it.moveCamera(com.kakao.vectormap.camera.CameraUpdateFactory.zoomIn(), com.kakao.vectormap.camera.CameraAnimation.from(300, true, true)) }
                    MapProvider.Google -> scope.launch { googleCameraPositionState.animate(com.google.android.gms.maps.CameraUpdateFactory.zoomIn()) }
                }
            }
            MapAction.ZOOM_OUT -> {
                when (mapProvider) {
                    MapProvider.Naver -> naverMapInstance?.let { it.moveCamera(com.naver.maps.map.CameraUpdate.zoomOut().animate(com.naver.maps.map.CameraAnimation.Easing)) }
                    MapProvider.Kakao -> kakaoMapInstance?.let { it.moveCamera(com.kakao.vectormap.camera.CameraUpdateFactory.zoomOut(), com.kakao.vectormap.camera.CameraAnimation.from(300, true, true)) }
                    MapProvider.Google -> scope.launch { googleCameraPositionState.animate(com.google.android.gms.maps.CameraUpdateFactory.zoomOut()) }
                }
            }
            MapAction.CURRENT_LOCATION -> {
                 if (currentLocation.value != null) {
                    when (mapProvider) {
                        MapProvider.Naver -> {
                            naverMapInstance?.let { map ->
                                val loc = currentLocation.value!!
                                map.moveCamera(com.naver.maps.map.CameraUpdate.scrollTo(com.naver.maps.geometry.LatLng(loc.latitude, loc.longitude)).animate(com.naver.maps.map.CameraAnimation.Easing))
                                map.moveCamera(com.naver.maps.map.CameraUpdate.zoomTo(18.0).animate(com.naver.maps.map.CameraAnimation.Easing))
                            }
                        }
                        MapProvider.Kakao -> {
                             kakaoMapInstance?.let { map ->
                                val loc = currentLocation.value!!
                                map.moveCamera(com.kakao.vectormap.camera.CameraUpdateFactory.newCenterPosition(com.kakao.vectormap.LatLng.from(loc.latitude, loc.longitude)), com.kakao.vectormap.camera.CameraAnimation.from(300, true, true))
                                map.moveCamera(com.kakao.vectormap.camera.CameraUpdateFactory.zoomTo(18), com.kakao.vectormap.camera.CameraAnimation.from(300, true, true))
                             }
                        }
                        MapProvider.Google -> {
                            scope.launch {
                                val loc = currentLocation.value!!
                                googleCameraPositionState.animate(com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(com.google.android.gms.maps.model.LatLng(loc.latitude, loc.longitude), 18f), 1000)
                            }
                        }
                    }
                 }
            }
            MapAction.MOVE_TO_LOCATION -> {
                val loc = mapViewModel.targetLocation.value
                println(">>> [MainScreen] MOVE_TO_LOCATION triggered for loc=$loc, provider=$mapProvider")
                loc?.let { l ->
                    when (mapProvider) {
                        MapProvider.Naver -> {
                            println(">>> [MainScreen] Moving Naver Map to ${l.latitude}, ${l.longitude}")
                            naverMapInstance?.let { map ->
                                val update = com.naver.maps.map.CameraUpdate.scrollAndZoomTo(
                                    com.naver.maps.geometry.LatLng(l.latitude, l.longitude), 18.0
                                ).animate(com.naver.maps.map.CameraAnimation.Easing)
                                map.moveCamera(update)
                            } ?: println(">>> [MainScreen] ERROR: naverMapInstance is NULL")
                        }
                        MapProvider.Kakao -> {
                            println(">>> [MainScreen] Moving Kakao Map to ${l.latitude}, ${l.longitude}")
                            kakaoMapInstance?.let { map ->
                                val update = com.kakao.vectormap.camera.CameraUpdateFactory.newCenterPosition(
                                    com.kakao.vectormap.LatLng.from(l.latitude, l.longitude), 18
                                )
                                map.moveCamera(update, com.kakao.vectormap.camera.CameraAnimation.from(500, true, true))
                            } ?: println(">>> [MainScreen] ERROR: kakaoMapInstance is NULL")
                        }
                        MapProvider.Google -> {
                            println(">>> [MainScreen] Moving Google Map to ${l.latitude}, ${l.longitude}")
                            scope.launch {
                                googleCameraPositionState.animate(
                                    com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(
                                        com.google.android.gms.maps.model.LatLng(l.latitude, l.longitude), 18f
                                    ), 1000
                                )
                            }
                        }
                    }
                } ?: println(">>> [MainScreen] ERROR: targetLocation is NULL")
            }
            else -> {}
        }
    }
    
    // [DEBUG] Log Map Action
    LaunchedEffect(mapAction) {
        if (mapAction != MapAction.NONE) {
            System.out.println(">>> [MainScreen] Detected Map Action: $mapAction")
            if (mapAction == MapAction.CURRENT_LOCATION) {
                 System.out.println(">>> [MainScreen] Attempting to EXECUTE CURRENT_LOCATION ACTION")
            }
            executeMapAction(mapAction)
            mapViewModel.consumeMapAction()
        }
    }


    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val centerX = with(density) { (maxWidth / 2).toPx() }
        val centerY = with(density) { (maxHeight / 2).toPx() }

        // [NEW] Global Overlay Theme Logic (iOS Parity)
        val isSystemDark = isSystemInDarkTheme()
        val isOverlayDark = remember(mapProvider, isSystemDark) {
            if (mapProvider == MapProvider.Google) isSystemDark else false
        }

        key(mapProvider) {
            System.out.println(">>> [MainScreen] Displaying Map: $mapProvider")
            when (mapProvider) {
                MapProvider.Naver -> {
                    // Use MapViewModel's clusteredItems
                    val clusteredItems by mapViewModel.clusteredItems.collectAsState()
                    NaverMapContent(
                        modifier = Modifier.fillMaxSize(),
                        clusteredItems = clusteredItems,
                        beforeLocation = beforeLocation.value, // [Item 1]
                        currentLocation = currentLocation.value,
                        onMapReady = { naverMapInstance = it },
                        onClusterClickWithCoords = { items, x, y ->
                            mapViewModel.selectCluster(items, x, y)
                        },
                        onItemClickWithCoords = { item, x, y ->
                            mapViewModel.selectCluster(listOf(item), x, y)
                        },
                        onCameraRotate = { mapViewModel.updateCompassRotation(it) },
                        initialAnimationDone = initialAnimationDone,
                        onInitialAnimationDone = { initialAnimationDone = true },
                        onResetAnimationDone = { initialAnimationDone = false }, // [NEW]
                        onZoomChange = { mapViewModel.updateZoom(it, naverMapInstance?.cameraPosition?.target?.latitude, MapProvider.Naver) },
                        // MapViewModel needs zoom updates too!
                        // Wait, mapViewModel has updateZoom logic.
                        // Checked MapFeatureViewModel: fun updateZoom(zoom: Float)
                        // Let's check MainScreen usage for onZoomChange.
                        onEnableClustering = { mapViewModel.enableClustering() },
                        onMapLongClick = { latLng ->
                            mapViewModel.startCreatingTodo(latLng.latitude, latLng.longitude)
                        },
                        creatingTodoLocation = creatingLocation?.let { com.naver.maps.geometry.LatLng(it.latitude, it.longitude) },
                        activePoints = activePoints,
                        showActivePath = showActivePath,
                        livePath = liveSessionPoints, // [FIX] Pass live path data
                        onCameraIdle = { wm, zoom, lat -> 
                             mapViewModel.handleCameraIdle(wm, zoom, lat, MapProvider.Naver) 
                        },
                        showMyLocation = !isListVisible // [FIX] Hide user on map when list is open
                    )
                }
                MapProvider.Kakao -> {
                    val clusteredItems by mapViewModel.clusteredItems.collectAsState()
                    KakaoMapContent(
                        modifier = Modifier.fillMaxSize(),
                        isSdkInitialized = isKakaoInitialized,
                        clusteredItems = clusteredItems,
                        beforeLocation = beforeLocation.value, // [Item 1]
                        currentLocation = currentLocation.value,
                        onMapReady = { kakaoMapInstance = it },
                        onClusterClickWithCoords = { items, x, y ->
                            mapViewModel.selectCluster(items, x, y)
                        },
                        onItemClickWithCoords = { item, x, y ->
                            mapViewModel.selectCluster(listOf(item), x, y)
                        },
                        onCameraRotate = { mapViewModel.updateCompassRotation(it) },
                        initialAnimationDone = initialAnimationDone,
                        onInitialAnimationDone = { initialAnimationDone = true },
                        onResetAnimationDone = { initialAnimationDone = false }, // [NEW]
                        onZoomChange = { mapViewModel.updateZoom(it, kakaoMapInstance?.cameraPosition?.getPosition()?.latitude, MapProvider.Kakao) },
                        onEnableClustering = { mapViewModel.enableClustering() },
                        onMapLongClick = { latLng ->
                            mapViewModel.startCreatingTodo(latLng.latitude, latLng.longitude)
                        },
                        creatingTodoLocation = creatingLocation?.let { com.kakao.vectormap.LatLng.from(it.latitude, it.longitude) },
                        contentPaddingBottom = if (isCreatingTodo) (context.resources.displayMetrics.heightPixels * 0.7).toInt() else 0,
                        activePoints = activePoints,
                        showActivePath = showActivePath,
                        livePath = liveSessionPoints, // [FIX] Pass live path data
                        onCameraIdle = { wm, zoom, lat -> 
                             mapViewModel.handleCameraIdle(wm, zoom, lat, MapProvider.Kakao) 
                        },
                        showMyLocation = !isListVisible // [FIX] Hide user on map when list is open
                    )
                }
                MapProvider.Google -> {
                    val clusteredItems by mapViewModel.clusteredItems.collectAsState()
                    
                    // [FIX] Sync Zoom with MapViewModel for clustering
                    LaunchedEffect(googleCameraPositionState.position.zoom, googleCameraPositionState.position.target.latitude) {
                        mapViewModel.updateZoom(
                            googleCameraPositionState.position.zoom,
                            googleCameraPositionState.position.target.latitude,
                            MapProvider.Google
                        )
                    }

                    System.out.println(">>> [MainScreen] Displaying Map: Google")
                    GoogleMapContent(
                        modifier = Modifier.fillMaxSize(),
                        clusteredItems = clusteredItems,
                        beforeLocation = beforeLocation.value, // [Item 1]
                        currentLocation = currentLocation.value,
                        cameraPositionState = googleCameraPositionState,
                        onMapClick = { },
                        onItemClick = { },
                        onItemClickWithCoords = { item, x, y ->
                            mapViewModel.selectCluster(listOf(item), x, y)
                        },
                        onClusterClickWithCoords = { items, x, y ->
                            mapViewModel.selectCluster(items, x, y)
                        },
                        onRotationChange = { mapViewModel.updateCompassRotation(it) },
                        isMapReady = isGoogleMapReady,
                        onMapLoaded = { isGoogleMapReady = true },
                        showHistoryMode = showActivePath,
                        initialAnimationDone = initialAnimationDone,
                        onInitialAnimationDone = { initialAnimationDone = true },
                        onResetAnimationDone = { initialAnimationDone = false }, // [NEW]
                        onEnableClustering = { mapViewModel.enableClustering() },
                        onFarItemsDetected = { },
                        creatingTodoLocation = creatingLocation?.let { com.google.android.gms.maps.model.LatLng(it.latitude, it.longitude) },
                        contentPaddingBottom = if (isCreatingTodo) (context.resources.displayMetrics.heightPixels * 0.7).toInt() else 0,
                        onMapLongClick = { latLng: com.google.android.gms.maps.model.LatLng ->
                            mapViewModel.startCreatingTodo(latLng.latitude, latLng.longitude)
                        },
                        activePoints = activePoints,
                        showActivePath = showActivePath,
                        livePath = liveSessionPoints, // [FIX] Essential for Google Map Path rendering
                        onCameraIdle = { wm, zoom, lat -> 
                             mapViewModel.handleCameraIdle(wm, zoom, lat, MapProvider.Google) 
                        },
                        showMyLocation = !isListVisible // [FIX] Hide user on map when list is open
                    )
                }
            }
        }

        // [Item 1] Top Left Widget (Todo Info)
        val currentTodoItems by todoViewModel.todoItems.collectAsState()
        TopLeftWidget(
            historyCount = currentTodoItems.count { it.type == "00" },
            localTodoCount = currentTodoItems.count { it.source == "local" && it.type == "10" },
            serverTodoCount = currentTodoItems.count { it.source != "local" && it.type == "10" },
            modifier = Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = 40.dp),
            onExpandClick = { todoViewModel.toggleListLayer() },
            isDark = isOverlayDark
        )

        // [Item 2] Right Side Controls
        // State is now at the top of MainScreen

        // [Item 2] Right Side Controls (Includes My Info)
        if (!isListVisible) {
            RightSideControls(
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 40.dp),
                compassRotation = compassRotation,
                isTracking = isTracking,
                showActivePath = showActivePath,
                onToggleActivePath = { gpsAuthViewModel.toggleActivePath() },
                onLoginClick = { mapViewModel.toggleMyInfo(true) },
                onZoomInClick = { mapViewModel.handleZoomIn() },
                onZoomOutClick = { mapViewModel.handleZoomOut() },
                onLocationClick = { mapViewModel.handleLocationClick() },
                onCompassClick = { mapViewModel.handleCompassClick() },
                isDark = isOverlayDark
            )
        }
        
        // --- Overlay Visibility States (Lifted Scope) ---
        val isOverlayVisible by gpsAuthViewModel.isOverlayVisible.collectAsState()
        val showCalendar by todoViewModel.showCalendar.collectAsState()



        // [NEW] Callout (Water Balloon) Overlay
        selectedCluster?.let { cluster ->
            tapScreenPosition?.let { pos ->
                AllToDoTheme(darkTheme = isOverlayDark) {
                    kr.alltodo.ui.components.CalloutBubble(
                        items = cluster,
                        screenPosition = pos,
                        maxPopupItems = maxPopupItems,
                        popupFontSize = popupFontSize,
                        onClose = { mapViewModel.clearSelection() },
                        onDeleteTodo = { todoViewModel.deleteTodo(it.item) },
                        onDeleteLog = { todoViewModel.deleteTodo(it.item) },
                        onSelectLog = { 
                            mapViewModel.clearSelection()
                            viewingPathTodo = it
                            todoViewModel.fetchPathForHistory(it)
                        },
                        onCreateTodo = { 
                            mapViewModel.startCreatingTodo(it.latitude, it.longitude, "할 일", when(it) {
                                is kr.alltodo.ui.UnifiedItem.Todo -> it.item.todo_name
                                is kr.alltodo.ui.UnifiedItem.History -> it.item.todo_name
                                else -> ""
                            })
                            mapViewModel.clearSelection()
                        },
                        mapProvider = mapProvider,
                        forceLightMode = !isOverlayDark
                    )
                }
            }
        }

        // [NEW] Path Viewer Layer
        val selectedHistoryPath by todoViewModel.selectedHistoryPath.collectAsState()
        viewingPathTodo?.let { todo ->
            AllToDoTheme(darkTheme = isOverlayDark) {
                kr.alltodo.ui.components.PathViewer(
                    todo = todo,
                    pathData = selectedHistoryPath,
                    mapProvider = mapProvider,
                    onClose = { 
                        viewingPathTodo = null
                        // [FIX] Use restoration logic
                        if (shouldRestoreList) {
                            todoViewModel.toggleListLayer()
                            shouldRestoreList = false
                        }
                        if (shouldRestoreCalendar) {
                            todoViewModel.toggleCalendar()
                            shouldRestoreCalendar = false
                        }
                    }
                )
            }
        }

        // [NEW] Create Todo Layer
        if (isCreatingTodo) {
            val recentNames by todoViewModel.recentNames.collectAsState()
            val recentMemos by todoViewModel.recentMemos.collectAsState()

            // [Item 6] Reverse Geocode for Default Name (Eup/Myeon/Dong)
            var defaultTodoName by remember { mutableStateOf("요기") }
            val geocoder = android.location.Geocoder(context, java.util.Locale.KOREA)
            LaunchedEffect(creatingLocation) {
                creatingLocation?.let { loc ->
                    try {
                        val addresses = geocoder.getFromLocation(loc.latitude, loc.longitude, 1)
                        if (!addresses.isNullOrEmpty()) {
                            val addr = addresses[0]
                            // Thoroughfare is Dong/Eup/Myeon in Korea
                            val dong = addr.thoroughfare ?: addr.subLocality ?: addr.locality ?: addr.subAdminArea
                            if (!dong.isNullOrBlank()) {
                                defaultTodoName = dong
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            fun centerMapOn(lat: Double, lon: Double) {
                when (mapProvider) {
                    MapProvider.Naver -> {
                        naverMapInstance?.moveCamera(com.naver.maps.map.CameraUpdate.scrollTo(com.naver.maps.geometry.LatLng(lat, lon)).animate(com.naver.maps.map.CameraAnimation.Easing))
                    }
                    MapProvider.Kakao -> {
                        kakaoMapInstance?.moveCamera(com.kakao.vectormap.camera.CameraUpdateFactory.newCenterPosition(com.kakao.vectormap.LatLng.from(lat, lon)), com.kakao.vectormap.camera.CameraAnimation.from(300, true, true))
                    }
                    MapProvider.Google -> {
                        scope.launch {
                            googleCameraPositionState.animate(com.google.android.gms.maps.CameraUpdateFactory.newLatLng(com.google.android.gms.maps.model.LatLng(lat, lon)))
                        }
                    }
                }
            }

            AllToDoTheme(darkTheme = isOverlayDark) {
                kr.alltodo.ui.components.CreateTodoLayer(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    recentNames = recentNames,
                    recentMemos = recentMemos,
                    defaultName = defaultTodoName,
                    initialName = initialTodoName,
                    title = initialTodoTitle,
                    isDark = isOverlayDark,
                    onRegister = { name, person, date, time, memo ->
                        creatingLocation?.let { loc ->
                            todoViewModel.addTodo(name, loc.latitude, loc.longitude, person, date, time, memo)
                            scope.launch {
                                delay(300) // Wait for padding removal animation
                                centerMapOn(loc.latitude, loc.longitude)
                            }
                        }
                        mapViewModel.cancelCreatingTodo()
                        // [FIX] Restore list if needed
                        if (shouldRestoreList) {
                            todoViewModel.toggleListLayer()
                            shouldRestoreList = false
                        }
                    },
                    onCancel = {
                        creatingLocation?.let { loc ->
                            scope.launch {
                                delay(300) // Wait for padding removal animation
                                centerMapOn(loc.latitude, loc.longitude)
                            }
                        }
                        mapViewModel.cancelCreatingTodo()
                        // [FIX] Restore list if needed
                        if (shouldRestoreList) {
                            todoViewModel.toggleListLayer()
                            shouldRestoreList = false
                        }
                        if (shouldRestoreCalendar) {
                            todoViewModel.toggleCalendar()
                            shouldRestoreCalendar = false
                        }
                    }
                )
            }
        }

        // [NEW] Overlay Coordination: Hide Info/Search when List is shown, Restore when closed
        var wasInfoVisibleBeforeList by remember { mutableStateOf(false) }
        var wasSearchVisibleBeforeList by remember { mutableStateOf(false) }

        LaunchedEffect(isListVisible) {
            if (isListVisible) {
                // Store previous states correctly if they were visible
                if (mapViewModel.showMyInfo.value) {
                    wasInfoVisibleBeforeList = true
                    mapViewModel.toggleMyInfo(false)
                }
                if (searchViewModel.isOverlayVisible.value) {
                    wasSearchVisibleBeforeList = true
                    searchViewModel.toggleOverlay()
                }
            } else {
                // Restore if needed when list is closed (excluding when path viewer is active)
                if (viewingPathTodo == null) {
                    if (wasInfoVisibleBeforeList) {
                        mapViewModel.toggleMyInfo(true)
                        wasInfoVisibleBeforeList = false
                    }
                    if (wasSearchVisibleBeforeList) {
                        searchViewModel.toggleOverlay()
                        wasSearchVisibleBeforeList = false
                    }
                }
            }
        }

        // [NEW] Todo List Layer
        if (isListVisible) {
            AllToDoTheme(darkTheme = isOverlayDark) {
                kr.alltodo.ui.components.TodoListLayer(
                    viewModel = todoViewModel,
                    onPathClick = { item ->
                        todoViewModel.toggleListLayer()
                        shouldRestoreList = true // [FIX] Store state for return
                        if (item is kr.alltodo.ui.UnifiedItem.History) {
                            viewingPathTodo = item.item
                            todoViewModel.fetchPathForHistory(item.item)
                        } else if (item is kr.alltodo.ui.UnifiedItem.Todo) {
                            viewingPathTodo = item.item
                            todoViewModel.fetchPathForHistory(item.item)
                        }
                    },
                    onEditTodo = { item ->
                        todoViewModel.toggleListLayer()
                        shouldRestoreList = true // [FIX] Store state for return
                        when (item) {
                            is kr.alltodo.ui.UnifiedItem.Todo -> {
                                mapViewModel.startCreatingTodo(
                                    lat = item.latitude,
                                    lon = item.longitude,
                                    title = "할 일 수정",
                                    name = item.item.todo_name
                                )
                            }
                            is kr.alltodo.ui.UnifiedItem.History -> {
                                mapViewModel.startCreatingTodo(
                                    lat = item.latitude,
                                    lon = item.longitude,
                                    title = "히스토리 수정",
                                    name = item.item.todo_name
                                )
                            }
                            else -> {}
                        }
                    },
                    onAddClick = {
                        todoViewModel.toggleListLayer()
                        shouldRestoreList = true // [FIX] Store state for return
                        // Use current map center or current location
                        val center = when (mapProvider) {
                            MapProvider.Naver -> naverMapInstance?.cameraPosition?.target?.let { LatLng(it.latitude, it.longitude) }
                            MapProvider.Kakao -> kakaoMapInstance?.cameraPosition?.getPosition()?.let { LatLng(it.latitude, it.longitude) }
                            MapProvider.Google -> googleCameraPositionState.position.target.let { LatLng(it.latitude, it.longitude) }
                        } ?: currentLocation.value?.let { LatLng(it.latitude, it.longitude) }
                        
                        center?.let {
                            mapViewModel.startCreatingTodo(it.latitude, it.longitude, "할 일 만들기")
                        }
                    },
                    onDismiss = { todoViewModel.toggleListLayer() },
                    isDark = isOverlayDark,
                    modifier = Modifier
                        .padding(top = 100.dp) // Below TopLeftWidget
                        .fillMaxSize()
                )
            }
        }


        // [NEW] Search Button and Overlay
        val searchQuery by searchViewModel.searchQuery.collectAsState()
        val searchResults by searchViewModel.searchResults.collectAsState()
        val isSearching by searchViewModel.isSearching.collectAsState()
        val errorMessage by searchViewModel.errorMessage.collectAsState()
        val showRipple by searchViewModel.showRipple.collectAsState()

        val voiceLauncher = rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                val data = result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
                data?.get(0)?.let { searchViewModel.onVoiceResult(it) }
            }
        }

        fun startVoiceRecognition() {
            val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, java.util.Locale.KOREAN)
            }
            try {
                voiceLauncher.launch(intent)
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "음성 인식을 사용할 수 없습니다.", android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        // Search Overlay (Positioned below TopLeftWidget)
        if (isSearchVisible) {
            // [NEW] Dismissal Layer: Touching anywhere outside the search overlay closes it
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures {
                            searchViewModel.toggleOverlay()
                        }
                    }
            )

            AllToDoTheme(darkTheme = isOverlayDark) {
                kr.alltodo.ui.components.SearchOverlay(
                    query = searchQuery,
                    results = searchResults,
                    isSearching = isSearching,
                    errorMessage = errorMessage,
                    mapProvider = mapProvider, // [FIX] Required for theme policy
                    onQueryChange = { searchViewModel.onQueryChange(it, currentLocation.value?.latitude, currentLocation.value?.longitude) },
                    onSearch = { searchViewModel.performSearch(currentLocation.value?.latitude, currentLocation.value?.longitude) },
                    onVoiceClick = { startVoiceRecognition() },
                    onResultClick = { result ->
                        mapViewModel.moveToLocation(result.latitude, result.longitude)
                        searchViewModel.triggerRipple()
                        searchViewModel.toggleOverlay()
                    },
                    modifier = Modifier
                        .padding(top = 110.dp, start = 16.dp)
                        .align(Alignment.TopStart),
                    isDark = isOverlayDark
                )
            }
        }

        // Search Button (Bottom Center)
        if (!isListVisible && !showMyInfo) { // [FIX] Hide search when my info is open
            kr.alltodo.ui.components.SearchButton(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 60.dp),
                onClick = { searchViewModel.toggleOverlay() }
            )
        }

        // --- Final Layering: Modal Overlays (Always on Top) ---

        // [Item 3] My Info (UserProfileView)
        if (showMyInfo) {
            val maxPopupItems by todoViewModel.maxPopupItems.collectAsState()
            val popupFontSize by todoViewModel.popupFontSize.collectAsState()
            val isGpsTracking by gpsAuthViewModel.isTracking.collectAsState()
            
            AllToDoTheme(darkTheme = isOverlayDark) {
                UserProfileView(
                    modifier = Modifier.align(Alignment.CenterStart),
                    onDismiss = { mapViewModel.toggleMyInfo(false) },
                    maxPopupItems = maxPopupItems,
                    onMaxItemsChange = { todoViewModel.updateMaxPopupItems(it) },
                    popupFontSize = popupFontSize,
                    onFontSizeChange = { todoViewModel.updatePopupFontSize(it) },
                    currentMapProvider = mapProvider,
                    onMapProviderChange = { newProvider ->
                        mapViewModel.toggleMyInfo(false) 
                        mapProvider = newProvider
                        prefs.edit().putString("map_provider", newProvider.name).apply()
                    },
                    isTracking = isGpsTracking,
                    onGpsAuthClick = { 
                        if (isGpsTracking) {
                            todoViewModel.toggleTracking() 
                            gpsAuthViewModel.stopTrackingAndSave() 
                            mapViewModel.toggleMyInfo(false)
                        } else {
                            todoViewModel.toggleTracking() 
                            gpsAuthViewModel.startTracking() 
                            gpsAuthViewModel.setOverlayVisible(true) 
                            mapViewModel.toggleMyInfo(false)
                        }
                    },
                    isDark = isOverlayDark
                )
            }
        }

        // GPS Auth Overlay
        if (isOverlayVisible) {
            AllToDoTheme(darkTheme = isOverlayDark) {
                kr.alltodo.ui.components.GpsAuthOverlay(
                    viewModel = gpsAuthViewModel,
                    currentLocation = currentLocation.value,
                    mapProvider = mapProvider,
                    onDismiss = { gpsAuthViewModel.setOverlayVisible(false) }
                )
            }
        }

        // Calendar Overlay
        if (showCalendar) {
            AllToDoTheme(darkTheme = isOverlayDark) {
                kr.alltodo.ui.components.CalendarDialog(
                    viewModel = todoViewModel,
                    isDark = isOverlayDark,
                    onDismissRequest = { todoViewModel.toggleCalendar() },
                    onPathClick = { item ->
                        if (isListVisible) {
                            todoViewModel.toggleListLayer()
                            shouldRestoreList = true
                        }
                        todoViewModel.toggleCalendar()
                        shouldRestoreCalendar = true
                        viewingPathTodo = item
                        todoViewModel.fetchPathForHistory(item)
                    },
                    onEditClick = { item ->
                        if (isListVisible) {
                            todoViewModel.toggleListLayer()
                            shouldRestoreList = true
                        }
                        todoViewModel.toggleCalendar()
                        shouldRestoreCalendar = true
                        mapViewModel.startCreatingTodo(
                            lat = (item.int_lat ?: 0) / 100_000.0,
                            lon = (item.int_long ?: 0) / 100_000.0,
                            title = if (item.type == "00") "히스토리 수정" else "할 일 수정",
                            name = item.todo_name
                        )
                    }
                )
            }
        }

        // [NEW] Ripple Effect for Search Results
        if (showRipple) {
            kr.alltodo.ui.components.RippleEffectView(isDark = isOverlayDark)
        }
    }
}
