package kr.alltodo.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.hilt.navigation.compose.hiltViewModel
import kr.alltodo.ui.components.RightSideControls
import kr.alltodo.ui.components.TopLeftWidget
import kr.alltodo.ui.components.UserProfileView
import kr.alltodo.ui.components.KakaoMapContent
import kr.alltodo.ui.components.NaverMapContent
import kr.alltodo.ui.components.GoogleMapContent
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
import androidx.compose.ui.unit.dp

@Composable
fun MainScreen(
    todoViewModel: TodoViewModel = hiltViewModel(),
    gpsAuthViewModel: GpsAuthViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("kr.alltodo.prefs", android.content.Context.MODE_PRIVATE) }

    // 1. States for Requirements
    var mapProvider by remember { 
        mutableStateOf(MapProvider.valueOf(prefs.getString("map_provider", "Naver") ?: "Naver")) 
    }
    var showMyInfo by remember { mutableStateOf(false) }
    var compassRotation by remember { mutableStateOf(0f) }
    
    // [NEW] Callout States for "Water Balloon"
    var selectedCluster by remember { mutableStateOf<List<kr.alltodo.ui.UnifiedItem>?>(null) }
    var tapScreenPosition by remember { mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }
    val maxPopupItems by todoViewModel.maxPopupItems.collectAsState()
    val popupFontSize by todoViewModel.popupFontSize.collectAsState()
    
    // [NEW] Path Viewer State (Now using TodoItem for ID context)
    var viewingPathTodo by remember { mutableStateOf<kr.alltodo.data.TodoItem?>(null) }

    // [NEW] Create Todo States
    var isCreatingTodo by remember { mutableStateOf(false) }
    var creatingTodoLocation by remember { mutableStateOf<com.kakao.vectormap.LatLng?>(null) }
    var initialTodoName by remember { mutableStateOf("") }
    var initialTodoTitle by remember { mutableStateOf("할 일 만들기") }
    
    // [Item 0] beforeLocation Persistence
    val beforeLocation = remember {
        val lat = prefs.getFloat("before_lat", 37.5759f)
        val lon = prefs.getFloat("before_lon", 126.9768f)
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
                // Initial jump to last known location
                fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                    if (loc != null && (loc.latitude != 0.0 || loc.longitude != 0.0)) {
                        currentLocation.value = loc
                        todoViewModel.updateCurrentLocation(loc.latitude, loc.longitude)
                        todoViewModel.saveLocation(loc.latitude, loc.longitude)
                        gpsAuthViewModel.addLocation(loc.latitude, loc.longitude, System.currentTimeMillis())

                        // [Item 0] Update beforeLocation
                        beforeLocation.value = loc
                        prefs.edit()
                            .putFloat("before_lat", loc.latitude.toFloat())
                            .putFloat("before_lon", loc.longitude.toFloat())
                            .apply()
                    }
                }
                
                // Start continuous updates
                fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    android.os.Looper.getMainLooper()
                )
            } catch (e: SecurityException) { }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

    // [NEW] Toast for Far Items
    val farItemCount by todoViewModel.farItemCount.collectAsState()
    LaunchedEffect(farItemCount) {
        if (farItemCount > 0) {
            android.widget.Toast.makeText(context, "현재 위치에서 너무 먼 핀 ${farItemCount}개는 표시되지 않습니다.", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    // [Item 4] Animation State - Reset when provider changes
    var initialAnimationDone by remember(mapProvider) { mutableStateOf(false) }
    
    // [FIX] Auto-hide My Info on Map Change
    LaunchedEffect(mapProvider) {
        showMyInfo = false
    }
    
    // Map Instances for Camera Control
    var naverMapInstance by remember { mutableStateOf<com.naver.maps.map.NaverMap?>(null) }
    var kakaoMapInstance by remember { mutableStateOf<com.kakao.vectormap.KakaoMap?>(null) }
    var isGoogleMapReady by remember(mapProvider) { mutableStateOf(false) } // [FIX]

    Box(modifier = Modifier.fillMaxSize()) {
        key(mapProvider) {
            when (mapProvider) {
                MapProvider.Naver -> {
                    val clusteredItems by todoViewModel.clusteredItems.collectAsState()
                    NaverMapContent(
                        modifier = Modifier.fillMaxSize(),
                        clusteredItems = clusteredItems,
                        beforeLocation = beforeLocation.value, // [Item 1]
                        currentLocation = currentLocation.value,
                        onMapReady = { naverMapInstance = it },
                        onClusterClickWithCoords = { items, x, y ->
                            selectedCluster = items
                            tapScreenPosition = androidx.compose.ui.geometry.Offset(x, y)
                            // Auto-center (Naver)
                            items.firstOrNull()?.let { first ->
                                naverMapInstance?.moveCamera(com.naver.maps.map.CameraUpdate.scrollTo(com.naver.maps.geometry.LatLng(first.latitude, first.longitude)).animate(com.naver.maps.map.CameraAnimation.Easing))
                            }
                        },
                        onItemClickWithCoords = { item, x, y ->
                            selectedCluster = listOf(item)
                            tapScreenPosition = androidx.compose.ui.geometry.Offset(x, y)
                            // Auto-center (Naver)
                            naverMapInstance?.moveCamera(com.naver.maps.map.CameraUpdate.scrollTo(com.naver.maps.geometry.LatLng(item.latitude, item.longitude)).animate(com.naver.maps.map.CameraAnimation.Easing))
                        },
                        onCameraRotate = { compassRotation = it },
                        initialAnimationDone = initialAnimationDone,
                        onInitialAnimationDone = { initialAnimationDone = true },
                        onResetAnimationDone = { initialAnimationDone = false }, // [NEW]
                        onZoomChange = { todoViewModel.updateZoom(it) },
                        onEnableClustering = { todoViewModel.enableClustering() },
                        onMapLongClick = { latLng ->
                            creatingTodoLocation = com.kakao.vectormap.LatLng.from(latLng.latitude, latLng.longitude)
                            initialTodoTitle = "할 일 만들기"
                            isCreatingTodo = true
                        },
                        creatingTodoLocation = creatingTodoLocation?.let { com.naver.maps.geometry.LatLng(it.latitude, it.longitude) },
                        contentPaddingBottom = if (isCreatingTodo) (context.resources.displayMetrics.heightPixels * 0.7).toInt() else 0
                    )
                }
                MapProvider.Kakao -> {
                    val clusteredItems by todoViewModel.clusteredItems.collectAsState()
                    KakaoMapContent(
                        modifier = Modifier.fillMaxSize(),
                        isSdkInitialized = isKakaoInitialized,
                        clusteredItems = clusteredItems,
                        beforeLocation = beforeLocation.value, // [Item 1]
                        currentLocation = currentLocation.value,
                        onMapReady = { kakaoMapInstance = it },
                        onClusterClickWithCoords = { items, x, y ->
                            selectedCluster = items
                            tapScreenPosition = androidx.compose.ui.geometry.Offset(x, y)
                            // Auto-center (Kakao)
                            items.firstOrNull()?.let { first ->
                                kakaoMapInstance?.moveCamera(com.kakao.vectormap.camera.CameraUpdateFactory.newCenterPosition(com.kakao.vectormap.LatLng.from(first.latitude, first.longitude)), com.kakao.vectormap.camera.CameraAnimation.from(300, true, true))
                            }
                        },
                        onItemClickWithCoords = { item, x, y ->
                            selectedCluster = listOf(item)
                            tapScreenPosition = androidx.compose.ui.geometry.Offset(x, y)
                            // Auto-center (Kakao)
                            kakaoMapInstance?.moveCamera(com.kakao.vectormap.camera.CameraUpdateFactory.newCenterPosition(com.kakao.vectormap.LatLng.from(item.latitude, item.longitude)), com.kakao.vectormap.camera.CameraAnimation.from(300, true, true))
                        },
                        onCameraRotate = { compassRotation = it },
                        initialAnimationDone = initialAnimationDone,
                        onInitialAnimationDone = { initialAnimationDone = true },
                        onResetAnimationDone = { initialAnimationDone = false }, // [NEW]
                        onZoomChange = { todoViewModel.updateZoom(it) },
                        onEnableClustering = { todoViewModel.enableClustering() },
                        onMapLongClick = { latLng ->
                            creatingTodoLocation = latLng
                            initialTodoTitle = "할 일 만들기"
                            isCreatingTodo = true
                        },
                        creatingTodoLocation = creatingTodoLocation,
                        contentPaddingBottom = if (isCreatingTodo) (context.resources.displayMetrics.heightPixels * 0.7).toInt() else 0
                    )
                }
                MapProvider.Google -> {
                    val clusteredItems by todoViewModel.clusteredItems.collectAsState()
                    GoogleMapContent(
                        modifier = Modifier.fillMaxSize(),
                        clusteredItems = clusteredItems,
                        beforeLocation = beforeLocation.value, // [Item 1]
                        currentLocation = currentLocation.value,
                        cameraPositionState = googleCameraPositionState,
                        onMapClick = { },
                        onItemClick = { },
                        onItemClickWithCoords = { item, x, y ->
                            selectedCluster = listOf(item)
                            tapScreenPosition = androidx.compose.ui.geometry.Offset(x, y)
                            // Auto-center (Google)
                            scope.launch {
                                googleCameraPositionState.animate(com.google.android.gms.maps.CameraUpdateFactory.newLatLng(com.google.android.gms.maps.model.LatLng(item.latitude, item.longitude)))
                            }
                        },
                        onClusterClickWithCoords = { items, x, y ->
                            selectedCluster = items
                            tapScreenPosition = androidx.compose.ui.geometry.Offset(x, y)
                            // Auto-center (Google)
                            items.firstOrNull()?.let { first ->
                                scope.launch {
                                    googleCameraPositionState.animate(com.google.android.gms.maps.CameraUpdateFactory.newLatLng(com.google.android.gms.maps.model.LatLng(first.latitude, first.longitude)))
                                }
                            }
                        },
                        onRotationChange = { compassRotation = it },
                        isMapReady = isGoogleMapReady,
                        onMapLoaded = { isGoogleMapReady = true },
                        showHistoryMode = false,
                        initialAnimationDone = initialAnimationDone,
                        onInitialAnimationDone = { initialAnimationDone = true },
                        onResetAnimationDone = { initialAnimationDone = false }, // [NEW]
                        onEnableClustering = { todoViewModel.enableClustering() },
                        onFarItemsDetected = { },
                        creatingTodoLocation = creatingTodoLocation?.let { com.google.android.gms.maps.model.LatLng(it.latitude, it.longitude) },
                        contentPaddingBottom = if (isCreatingTodo) (context.resources.displayMetrics.heightPixels * 0.7).toInt() else 0,
                        onMapLongClick = { latLng: com.kakao.vectormap.LatLng ->
                            creatingTodoLocation = latLng
                            initialTodoTitle = "할 일 만들기"
                            isCreatingTodo = true
                        }
                    )
                }
            }
        }

        // [Item 1] Top Left Widget
        val todoItems by todoViewModel.todoItems.collectAsState()
        TopLeftWidget(
            historyCount = todoItems.count { it.type == "00" },
            localTodoCount = todoItems.count { it.source == "local" && it.type == "10" },
            serverTodoCount = todoItems.count { it.source != "local" && it.type == "10" },
            modifier = Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = 40.dp),
            onExpandClick = { /* Disabled for now */ }
        )

        // [Item 2] Right Side Controls
        RightSideControls(
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 40.dp),
            compassRotation = compassRotation,
            onLoginClick = { showMyInfo = true },
            onZoomInClick = { 
                when (mapProvider) {
                    MapProvider.Naver -> naverMapInstance?.let { it.moveCamera(com.naver.maps.map.CameraUpdate.zoomIn().animate(com.naver.maps.map.CameraAnimation.Easing)) }
                    MapProvider.Kakao -> kakaoMapInstance?.let { it.moveCamera(com.kakao.vectormap.camera.CameraUpdateFactory.zoomIn(), com.kakao.vectormap.camera.CameraAnimation.from(300, true, true)) }
                    MapProvider.Google -> scope.launch { googleCameraPositionState.animate(com.google.android.gms.maps.CameraUpdateFactory.zoomIn()) }
                }
            },
            onZoomOutClick = { 
                when (mapProvider) {
                    MapProvider.Naver -> naverMapInstance?.let { it.moveCamera(com.naver.maps.map.CameraUpdate.zoomOut().animate(com.naver.maps.map.CameraAnimation.Easing)) }
                    MapProvider.Kakao -> kakaoMapInstance?.let { it.moveCamera(com.kakao.vectormap.camera.CameraUpdateFactory.zoomOut(), com.kakao.vectormap.camera.CameraAnimation.from(300, true, true)) }
                    MapProvider.Google -> scope.launch { googleCameraPositionState.animate(com.google.android.gms.maps.CameraUpdateFactory.zoomOut()) }
                }
            },
            onLocationClick = { 
                currentLocation.value?.let { loc ->
                    val latLng = com.naver.maps.geometry.LatLng(loc.latitude, loc.longitude)
                    when (mapProvider) {
                        MapProvider.Naver -> {
                            naverMapInstance?.moveCamera(com.naver.maps.map.CameraUpdate.scrollAndZoomTo(latLng, 18.0).animate(com.naver.maps.map.CameraAnimation.Easing, 800))
                        }
                        MapProvider.Kakao -> {
                            kakaoMapInstance?.moveCamera(com.kakao.vectormap.camera.CameraUpdateFactory.newCenterPosition(com.kakao.vectormap.LatLng.from(loc.latitude, loc.longitude), 18), com.kakao.vectormap.camera.CameraAnimation.from(800, true, true))
                        }
                        MapProvider.Google -> {
                            scope.launch { 
                                googleCameraPositionState.animate(com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(com.google.android.gms.maps.model.LatLng(loc.latitude, loc.longitude), 18f))
                            }
                        }
                    }
                }
            },
            onCompassClick = { 
                when (mapProvider) {
                    MapProvider.Naver -> naverMapInstance?.let { 
                        // Naver V3: rotateTo is the correct param method
                        it.moveCamera(com.naver.maps.map.CameraUpdate.withParams(com.naver.maps.map.CameraUpdateParams().rotateTo(0.0)).animate(com.naver.maps.map.CameraAnimation.Easing)) 
                    }
                    MapProvider.Kakao -> kakaoMapInstance?.let { 
                        // Kakao: Just reset rotation via simple rotateTo or absolute camera move
                        it.moveCamera(
                            com.kakao.vectormap.camera.CameraUpdateFactory.rotateTo(0.0),
                            com.kakao.vectormap.camera.CameraAnimation.from(300, true, true)
                        )
                    }
                    MapProvider.Google -> scope.launch { 
                        googleCameraPositionState.animate(
                            com.google.android.gms.maps.CameraUpdateFactory.newCameraPosition(
                                com.google.android.gms.maps.model.CameraPosition.builder(googleCameraPositionState.position).bearing(0f).build()
                            )
                        ) 
                    }
                }
            }
        )

        // [Item 3] My Info (UserProfileView)
        if (showMyInfo) {
            val maxPopupItems by todoViewModel.maxPopupItems.collectAsState()
            val popupFontSize by todoViewModel.popupFontSize.collectAsState()
            val isTracking by gpsAuthViewModel.isTracking.collectAsState()
            
            UserProfileView(
                modifier = Modifier.align(Alignment.CenterStart),
                onDismiss = { showMyInfo = false },
                maxPopupItems = maxPopupItems,
                onMaxItemsChange = { todoViewModel.updateMaxPopupItems(it) },
                popupFontSize = popupFontSize,
                onFontSizeChange = { todoViewModel.updatePopupFontSize(it) },
                currentMapProvider = mapProvider,
                onMapProviderChange = { newProvider ->
                    showMyInfo = false // [FIX] Move to top for immediate UI feedback
                    mapProvider = newProvider
                    prefs.edit().putString("map_provider", newProvider.name).apply()
                },
                isTracking = isTracking,
                onGpsAuthClick = { 
                    if (isTracking) {
                        todoViewModel.toggleTracking() // Stops Todo session
                        gpsAuthViewModel.stopTrackingAndSave() // Stops GPS session
                        showMyInfo = false
                    } else {
                        todoViewModel.toggleTracking() // Starts Todo session
                        gpsAuthViewModel.startTracking() // Starts GPS session
                        gpsAuthViewModel.setOverlayVisible(true) // Open for feedback
                        showMyInfo = false
                    }
                }
            )
        }

        // [NEW] GPS Auth Overlay Layer
        val isOverlayVisible by gpsAuthViewModel.isOverlayVisible.collectAsState()
        if (isOverlayVisible) {
            kr.alltodo.ui.components.GpsAuthOverlay(
                viewModel = gpsAuthViewModel,
                currentLocation = currentLocation.value,
                mapProvider = mapProvider,
                onDismiss = { gpsAuthViewModel.setOverlayVisible(false) }
            )
        }
        // [NEW] Callout (Water Balloon) Overlay
        selectedCluster?.let { cluster ->
            tapScreenPosition?.let { pos ->
                kr.alltodo.ui.components.CalloutBubble(
                    items = cluster,
                    screenPosition = pos,
                    maxPopupItems = maxPopupItems,
                    popupFontSize = popupFontSize,
                    onClose = { selectedCluster = null },
                    onDeleteTodo = { todoViewModel.deleteTodo(it.item) },
                    onDeleteLog = { todoViewModel.deleteTodo(it.item) },
                    onSelectLog = { 
                        selectedCluster = null
                        viewingPathTodo = it
                        todoViewModel.fetchPathForHistory(it)
                    },
                    onCreateTodo = { 
                        creatingTodoLocation = com.kakao.vectormap.LatLng.from(it.latitude, it.longitude)
                        initialTodoName = when(it) {
                            is kr.alltodo.ui.UnifiedItem.Todo -> it.item.todo_name
                            is kr.alltodo.ui.UnifiedItem.History -> it.item.todo_name
                            else -> ""
                        }
                        initialTodoTitle = "할 일"
                        isCreatingTodo = true
                        selectedCluster = null
                    }
                )
            }
        }

        // [NEW] Path Viewer Layer
        val selectedHistoryPath by todoViewModel.selectedHistoryPath.collectAsState()
        viewingPathTodo?.let { todo ->
            kr.alltodo.ui.components.PathViewer(
                pathData = selectedHistoryPath,
                onClose = { viewingPathTodo = null }
            )
        }

        // [NEW] Create Todo Layer
        if (isCreatingTodo) {
            val recentNames by todoViewModel.recentNames.collectAsState()
            val recentMemos by todoViewModel.recentMemos.collectAsState()

            // [Item 6] Reverse Geocode for Default Name (Eup/Myeon/Dong)
            var defaultTodoName by remember { mutableStateOf("요기") }
            val geocoder = android.location.Geocoder(context, java.util.Locale.KOREA)
            LaunchedEffect(creatingTodoLocation) {
                creatingTodoLocation?.let { loc ->
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

            kr.alltodo.ui.components.CreateTodoLayer(
                modifier = Modifier.align(Alignment.BottomCenter),
                recentNames = recentNames,
                recentMemos = recentMemos,
                defaultName = defaultTodoName,
                initialName = initialTodoName,
                title = initialTodoTitle,
                onRegister = { name, person, date, time, memo ->
                    creatingTodoLocation?.let { loc ->
                        todoViewModel.addTodo(name, loc.latitude, loc.longitude, person, date, time, memo)
                    }
                    isCreatingTodo = false
                    creatingTodoLocation = null
                    initialTodoName = ""
                },
                onCancel = {
                    isCreatingTodo = false
                    creatingTodoLocation = null
                    initialTodoName = ""
                }
            )
        }
    }
}
