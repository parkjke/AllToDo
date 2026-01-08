package kr.alltodo.tp

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.os.BatteryManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.*
import com.kakao.vectormap.*
import com.kakao.vectormap.camera.CameraAnimation
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.label.*
import com.kakao.vectormap.route.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kr.alltodo.tp.data.*
import kr.alltodo.tp.logic.TrajectoryManager

class MainActivity : ComponentActivity() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        setContent {
            MaterialTheme {
                MainScreen(fusedLocationClient)
            }
        }
    }
}

@Composable
fun MainScreen(fusedLocationClient: FusedLocationProviderClient) {
    val context = LocalContext.current
    
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
    }

    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            launcher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    var kakaoMap by remember { mutableStateOf<KakaoMap?>(null) }
    var myLocationLabel by remember { mutableStateOf<Label?>(null) }
    var showTrajectoryList by remember { mutableStateOf(false) }
    var isInitialCameraMove by remember { mutableStateOf(true) }
    
    val trajectoryManager = remember { TrajectoryManager(context) }
    var isRecording by remember { mutableStateOf(false) }
    var savedTrajectories by remember { mutableStateOf<List<TrajectoryEntity>>(emptyList()) }

    // Playback & Dot States
    var isPlaybackMode by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) } // Changed to Float
    var lastKnownLocation by remember { mutableStateOf<LatLng?>(null) }
    val recordingLabels = remember { mutableStateListOf<Label>() }
    val playbackLabels = remember { mutableStateListOf<Label>() }
    val recordingRoutes = remember { mutableStateListOf<Route>() } // New: Routes for recording
    val playbackRoutes = remember { mutableStateListOf<Route>() } // New: Routes for playback
    
    var lastRecordingPos by remember { mutableStateOf<LatLng?>(null) } // New: Tracking last pos for lines
    var lastPlaybackPos by remember { mutableStateOf<LatLng?>(null) } // New: Tracking last pos for lines

    var playbackJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var isLocationTrackingActive by remember { mutableStateOf(true) }
    var showPathLines by remember { mutableStateOf(true) } // New: Path lines toggle
    
    // Customization States
    var selectedColor by remember { mutableStateOf(0xFF007AFF.toInt()) }
    var selectedThickness by remember { mutableStateOf(3) }
    
    // Tracking Sensitivity States
    var trackingInterval by remember { mutableLongStateOf(0L) } // 0ms (default)
    var trackingDistance by remember { mutableFloatStateOf(0f) } // 0m (default)

    // Fetch trajectories when overlay is shown
    LaunchedEffect(showTrajectoryList) {
        if (showTrajectoryList) {
            val db = TrajectoryDatabase.getDatabase(context)
            savedTrajectories = db.trajectoryDao().getAllTrajectories()
        }
    }

    // Location Tracking Logic
    DisposableEffect(hasLocationPermission, kakaoMap, myLocationLabel, isLocationTrackingActive, trackingInterval, trackingDistance) {
        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    val newPos = LatLng.from(location.latitude, location.longitude)
                    myLocationLabel?.let { label ->
                        if (label.layer != null) label.moveTo(newPos)
                    }
                    lastKnownLocation = newPos
                    
                    if (isInitialCameraMove) {
                        kakaoMap?.moveCamera(CameraUpdateFactory.newCenterPosition(newPos, 18))
                        isInitialCameraMove = false
                    }

                    // T.3. 기록 중이면 포인트 추가 및 지도에 점 출시
                    if (isRecording) {
                        trajectoryManager.addPoint(location.latitude, location.longitude)
                        
                        // 실시간 파란 점 표시 (3pt) - 프리미엄 블루 적용
                        kakaoMap?.let { map ->
                            val dotBitmap = createDotBitmap(0xFF007AFF.toInt(), 3, context)
                            val styles = map.labelManager?.addLabelStyles(
                                LabelStyles.from(LabelStyle.from(dotBitmap).setAnchorPoint(0.5f, 0.5f))
                            )
                            val label = map.labelManager?.layer?.addLabel(
                                LabelOptions.from(newPos).setStyles(styles)
                            )
                            if (label != null) recordingLabels.add(label)

                            // Draw path line if showPathLines is true
                            if (showPathLines && lastRecordingPos != null) {
                                val routeOptions = RouteOptions.from(
                                    RouteLineOptions.from(
                                        RouteLineSegment.from(lastRecordingPos, newPos)
                                            .setStrokeWidth(selectedThickness.dp)
                                            .setStrokeColor(Color(selectedColor))
                                    )
                                )
                                map.routeManager?.layer?.addRoute(routeOptions)?.let {
                                    recordingRoutes.add(it)
                                }
                            }
                            lastRecordingPos = newPos
                        }
                    }
                }
            }
        }

        if (hasLocationPermission && kakaoMap != null && myLocationLabel != null && isLocationTrackingActive) {
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, trackingInterval)
                .setMinUpdateDistanceMeters(trackingDistance)
                .build()

            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, android.os.Looper.getMainLooper())
        }

        onDispose {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (hasLocationPermission) {
            KakaoMapView(
                onMapReady = { map ->
                    kakaoMap = map
                    setupMyLocationPin(context, map) { label ->
                        myLocationLabel = label
                    }
                }
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("위치 권한이 필요합니다.")
            }
        }

        // Right Side Controls (Top-End: Exact Reversion to End Alignment)
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 32.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.End // Important: Keep buttons pinned to the right edge
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // [Route Toggle] - Added when recording is active
                if (isRecording) {
                    MapControlButton(
                        icon = Icons.Default.Route,
                        iconTint = if (showPathLines) Color(selectedColor) else Color.Gray,
                        onClick = {
                            showPathLines = !showPathLines
                            if (!showPathLines) {
                                recordingRoutes.forEach { it.remove() }
                                recordingRoutes.clear()
                            }
                        }
                    )
                }

                // [Recording/Stop Icon] - Added to the left of hamburger
                if (!isPlaybackMode) {
                    MapControlButton(
                        icon = if (isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                        iconTint = Color.Red,
                        showIconBorder = true,
                        onClick = {
                            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                            val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

                            if (!isRecording) {
                                trajectoryManager.startRecording(batteryLevel)
                                isRecording = true
                                lastRecordingPos = null // Reset for new recording
                            } else {
                                trajectoryManager.stopAndSave(batteryLevel) {
                                    isRecording = false
                                    // Clear all recording dots from map
                                    recordingLabels.forEach { it.remove() }
                                    recordingLabels.clear()
                                    recordingRoutes.forEach { it.remove() }
                                    recordingRoutes.clear()
                                    lastRecordingPos = null
                                }
                            }
                        }
                    )
                }

                // [Hamburger]
                MapControlButton(Icons.Default.Menu) {
                    showTrajectoryList = true
                }
            }

            // [MyLocation] - Exact original position
            MapControlButton(Icons.Default.MyLocation) {
                kakaoMap?.let { map ->
                    myLocationLabel?.let { label ->
                        val trackingManager = map.trackingManager
                        if (trackingManager != null) {
                            trackingManager.startTracking(label)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            
            // [Zoom & Compass] - Exact original position
            MapControlButton(Icons.Default.Add) {
                kakaoMap?.let { it.moveCamera(CameraUpdateFactory.zoomIn()) }
            }
            MapControlButton(Icons.Default.Remove) {
                kakaoMap?.let { it.moveCamera(CameraUpdateFactory.zoomOut()) }
            }
            MapControlButton(Icons.Default.Explore) {
                kakaoMap?.let { map ->
                    map.cameraPosition?.let { pos ->
                        map.moveCamera(CameraUpdateFactory.newCenterPosition(pos.position))
                    }
                }
            }
        }

        if (showTrajectoryList) {
            TrajectoryListOverlay(
                trajectories = savedTrajectories,
                onClose = { showTrajectoryList = false },
                selectedColor = selectedColor,
                onColorChange = { selectedColor = it },
                selectedThickness = selectedThickness,
                onThicknessChange = { selectedThickness = it },
                currentInterval = trackingInterval,
                onIntervalChange = { trackingInterval = it },
                currentDistance = trackingDistance,
                onDistanceChange = { trackingDistance = it },
                onDeleteClick = { item ->
                    (context as? LifecycleOwner)?.lifecycleScope?.launch {
                        val db = TrajectoryDatabase.getDatabase(context)
                        db.trajectoryDao().deleteTrajectory(item)
                        savedTrajectories = db.trajectoryDao().getAllTrajectories() // Refresh list
                    }
                },
                onItemClick = { item ->
                    showTrajectoryList = false
                    isLocationTrackingActive = false // 정지
                    isPlaybackMode = true
                    
                    // 지도 초기화 (현재 위치 핀 포함 모두 삭제)
                    kakaoMap?.labelManager?.layer?.removeAll()
                    kakaoMap?.routeManager?.layer?.removeAll() // Clear all routes
                    recordingLabels.clear()
                    playbackLabels.clear()
                    recordingRoutes.clear()
                    playbackRoutes.clear()
                    lastPlaybackPos = null // Reset for playback
                    
                    // Start Playback Logic
                    playbackJob?.cancel()
                    playbackJob = (context as? LifecycleOwner)?.lifecycleScope?.launch {
                        val paths = trajectoryManager.getPaths(item.todo_id)
                        var lastTime = item.begin_time
                        
                        val dotBitmap = createDotBitmap(selectedColor, selectedThickness, context)
                        val styles = kakaoMap?.labelManager?.addLabelStyles(
                            LabelStyles.from(LabelStyle.from(dotBitmap).setAnchorPoint(0.5f, 0.5f))
                        )

                        paths.forEach { path ->
                            val currentPos = LatLng.from(
                                trajectoryManager.decompress(path.int_lat),
                                trajectoryManager.decompress(path.int_long)
                            )
                            val delayTime = ((path.time - lastTime) / playbackSpeed).toLong()
                            if (delayTime > 0) kotlinx.coroutines.delay(delayTime)
                            
                            kakaoMap?.let { map ->
                                val label = map.labelManager?.layer?.addLabel(
                                    LabelOptions.from(currentPos).setStyles(styles)
                                )
                                if (label != null) playbackLabels.add(label)

                                // Draw path line if showPathLines is true
                                if (showPathLines && lastPlaybackPos != null) {
                                    val routeOptions = RouteOptions.from(
                                        RouteLineOptions.from(
                                            RouteLineSegment.from(lastPlaybackPos, currentPos)
                                                .setStrokeWidth(selectedThickness.dp)
                                                .setStrokeColor(Color(selectedColor))
                                        )
                                    )
                                    map.routeManager?.layer?.addRoute(routeOptions)?.let {
                                        playbackRoutes.add(it)
                                    }
                                }
                            }
                            lastPlaybackPos = currentPos
                            lastTime = path.time
                        }
                    }
                }
            )
        }

        // Speed Control (Bottom-Start)
        if (isPlaybackMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = 32.dp, start = 16.dp)
            ) {
                SpeedButtonGroup(
                    currentSpeed = playbackSpeed.toInt(), // Convert back to Int for display
                    onSpeedChange = { playbackSpeed = it.toFloat() } // Convert to Float for state
                )
            }

            // Back Button (Bottom-End)
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 32.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // [Route Toggle Button]
                PlaybackControlButton(
                    icon = Icons.Default.Route,
                    iconTint = if (showPathLines) Color(selectedColor) else Color.Gray
                ) {
                    showPathLines = !showPathLines
                    if (!showPathLines) {
                        playbackRoutes.forEach { it.remove() }
                        playbackRoutes.clear()
                    }
                }

                PlaybackControlButton(Icons.Default.ArrowBack) {
                    // Reset Playback
                    playbackJob?.cancel()
                    kakaoMap?.labelManager?.layer?.removeAll()
                    kakaoMap?.routeManager?.layer?.removeAll() // Clear all routes
                    recordingLabels.clear()
                    playbackLabels.clear()
                    recordingRoutes.clear()
                    playbackRoutes.clear()
                    lastPlaybackPos = null
                    lastRecordingPos = null
                    
                    isPlaybackMode = false
                    isLocationTrackingActive = true // 다시 시작
                }
            }
        }
    }
}

@Composable
fun SpeedButtonGroup(currentSpeed: Int, onSpeedChange: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .background(Color.White, RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(8.dp)), // Grey 8 Border (approx)
        horizontalArrangement = Arrangement.Start
    ) {
        listOf(1, 2, 5).forEachIndexed { index, speed ->
            val isSelected = currentSpeed == speed
            val shape = when (index) {
                0 -> RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)
                2 -> RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)
                else -> RoundedCornerShape(0.dp)
            }
            
            Button(
                onClick = { onSpeedChange(speed) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSelected) Color(0xFFE0E0E0) else Color.White,
                    contentColor = if (isSelected) Color(0xFF007AFF) else Color.Black
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier
                    .height(44.dp)
                    .width(54.dp),
                shape = shape,
                elevation = null,
                border = if (index > 0) BorderStroke(0.dp, Color.Transparent) else null // Managed by parent border
            ) {
                Text("x$speed", fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
            }
            
            if (index < 2) {
                Spacer(modifier = Modifier
                    .width(1.dp)
                    .height(44.dp)
                    .background(Color(0xFFE0E0E0)))
            }
        }
    }
}

@Composable
fun PlaybackControlButton(icon: ImageVector, iconTint: Color = Color.Black, onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        modifier = Modifier
            .size(50.dp)
            .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = Color.White,
            contentColor = iconTint
        )
    ) {
        Icon(icon, contentDescription = null, tint = iconTint)
    }
}

@Composable
fun MapControlButton(
    icon: ImageVector,
    iconTint: Color = Color.White,
    showIconBorder: Boolean = false,
    onClick: () -> Unit
) {
    FilledIconButton(
        onClick = onClick,
        modifier = Modifier.size(50.dp),
        shape = RoundedCornerShape(12.dp),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = Color(0xFF007AFF), // Premium Blue
            contentColor = iconTint
        )
    ) {
        if (showIconBorder) {
            val shape = if (icon == Icons.Default.Stop) RoundedCornerShape(2.dp) else CircleShape
            // Using a Box instead of an Icon to ensure the red fill is perfectly flush with the white border
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .background(Color.Red, shape)
                    .border(2.dp, Color.White, shape)
            )
        } else {
            Icon(icon, contentDescription = null)
        }
    }
}

private fun createDotBitmap(color: Int, sizeDp: Int, context: Context): Bitmap {
    val px = (sizeDp * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint().apply {
        this.color = color
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    canvas.drawCircle(px / 2f, px / 2f, px / 2f, paint)
    return bitmap
}


@Composable
fun TrajectoryListOverlay(
    trajectories: List<TrajectoryEntity>,
    onClose: () -> Unit,
    selectedColor: Int,
    onColorChange: (Int) -> Unit,
    selectedThickness: Int,
    onThicknessChange: (Int) -> Unit,
    currentInterval: Long,
    onIntervalChange: (Long) -> Unit,
    currentDistance: Float,
    onDistanceChange: (Float) -> Unit,
    onDeleteClick: (TrajectoryEntity) -> Unit,
    onItemClick: (TrajectoryEntity) -> Unit
) {
    val context = LocalContext.current
    val sdfDate = remember { java.text.SimpleDateFormat("MM-dd", java.util.Locale.getDefault()) }
    val sdfTime = remember { java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFE0E0E0)) // Gray 2 (Opaque)
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header (Outside the Gray 4 container)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "궤적 리스트",
                    style = androidx.compose.ui.text.TextStyle(
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        fontSize = 24.sp,
                        color = Color(0xFF424242) // Gray 8
                    )
                )
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color(0xFF212121), // Gray 9
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            // Main Content Container (Gray 4)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(Color(0xFFBDBDBD), RoundedCornerShape(16.dp)) // Gray 4, Rounded
                    .padding(16.dp)
            ) {
                if (trajectories.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("기록된 궤적이 없습니다.", color = Color(0xFF424242))
                    }
                } else {
                    // List
                    androidx.compose.foundation.lazy.LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        items(trajectories.size) { index ->
                            val item = trajectories[index]
                            val dateStr = sdfDate.format(java.util.Date(item.created_at))
                            val startTimeStr = sdfTime.format(java.util.Date(item.begin_time))
                            val durationStr = if (item.end_time != null) {
                                val diff = item.end_time - item.begin_time
                                val hours = diff / (1000 * 60 * 60)
                                val mins = (diff / (1000 * 60)) % 60
                                val secs = (diff / 1000) % 60
                                String.format("%02d:%02d:%02d", hours, mins, secs)
                            } else "Ing..."

                            Column {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onItemClick(item) }
                                        .padding(vertical = 12.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    val usagePerSecond = if (item.end_time != null && item.end_time > item.begin_time) {
                                        val seconds = (item.end_time - item.begin_time).toDouble() / 1000.0
                                        if (seconds > 0) String.format("%.4f%%/s", item.usage.toDouble() / seconds) else "0.0000%%/s"
                                    } else "N/A"

                                    Column(modifier = Modifier.weight(1.1f)) {
                                        Text(text = dateStr, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = 12.sp)
                                        Text(text = "시작: $startTimeStr", fontSize = 10.sp, color = Color.Gray)
                                    }
                                    Text(
                                        text = "${item.no_of_path}개",
                                        modifier = Modifier.weight(0.6f),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = durationStr,
                                        modifier = Modifier.weight(0.9f),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = usagePerSecond,
                                        modifier = Modifier.weight(0.8f),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                                        fontSize = 10.sp, // Slightly smaller to fit precision
                                        color = Color(0xFF616161),
                                        fontWeight = FontWeight.Bold
                                    )
                                    IconButton(
                                        onClick = { onDeleteClick(item) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = Color.Red,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                if (index < trajectories.size - 1) {
                                    Divider(
                                        modifier = Modifier.padding(horizontal = 4.dp),
                                        thickness = 0.5.dp,
                                        color = Color(0xFF9E9E9E)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Settings Bottom Area (Tracking Sensitivity)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Tracking Time Settings
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 32.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("위치 추적 시간", modifier = Modifier.width(110.dp), fontWeight = FontWeight.Bold, color = Color(0xFF424242), fontSize = 14.sp, maxLines = 1, softWrap = false)
                    Row(
                        modifier = Modifier
                            .background(Color.White, RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFF9E9E9E), RoundedCornerShape(8.dp))
                    ) {
                        val intervals = listOf(0L to "X", 1000L to "1", 3000L to "3", 5000L to "5")
                        intervals.forEachIndexed { index, (value, label) ->
                            val isSelected = currentInterval == value
                            val shape = when (index) {
                                0 -> RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)
                                intervals.size - 1 -> RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)
                                else -> RoundedCornerShape(0.dp)
                            }
                            Box(
                                modifier = Modifier
                                    .height(36.dp)
                                    .width(44.dp)
                                    .background(if (isSelected) Color(0xFFE0E0E0) else Color.White, shape)
                                    .clickable { onIntervalChange(value) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(label, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                            }
                            if (index < intervals.size - 1) {
                                Spacer(modifier = Modifier.width(1.dp).height(36.dp).background(Color(0xFF9E9E9E)))
                            }
                        }
                    }
                }

                // Tracking Distance Settings
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 32.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("위치 추적 거리", modifier = Modifier.width(110.dp), fontWeight = FontWeight.Bold, color = Color(0xFF424242), fontSize = 14.sp, maxLines = 1, softWrap = false)
                    Row(
                        modifier = Modifier
                            .background(Color.White, RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFF9E9E9E), RoundedCornerShape(8.dp))
                    ) {
                        val distances = listOf(0f to "X", 1f to "1", 3f to "3", 5f to "5")
                        distances.forEachIndexed { index, (value, label) ->
                            val isSelected = currentDistance == value
                            val shape = when (index) {
                                0 -> RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)
                                distances.size - 1 -> RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)
                                else -> RoundedCornerShape(0.dp)
                            }
                            Box(
                                modifier = Modifier
                                    .height(36.dp)
                                    .width(44.dp)
                                    .background(if (isSelected) Color(0xFFE0E0E0) else Color.White, shape)
                                    .clickable { onDistanceChange(value) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(label, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                            }
                            if (index < distances.size - 1) {
                                Spacer(modifier = Modifier.width(1.dp).height(36.dp).background(Color(0xFF9E9E9E)))
                            }
                        }
                    }
                }
            }

            // Customization Options Bottom Area
            Column(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Color Selection (Visual Group)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 32.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("궤적 색깔", modifier = Modifier.width(110.dp), fontWeight = FontWeight.Bold, color = Color(0xFF424242), maxLines = 1, softWrap = false)
                    Spacer(modifier = Modifier.width(32.dp))
                    Row(
                        modifier = Modifier
                            .background(Color.White, RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(8.dp))
                    ) {
                        val colors = listOf(
                            0xFF007AFF.toInt(), // Premium Blue
                            android.graphics.Color.GREEN,
                            android.graphics.Color.RED
                        )
                        colors.forEachIndexed { index, color ->
                            val isSelected = selectedColor == color
                            val shape = when (index) {
                                0 -> RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)
                                colors.size - 1 -> RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)
                                else -> RoundedCornerShape(0.dp)
                            }
                            
                            Box(
                                modifier = Modifier
                                    .height(44.dp)
                                    .width(60.dp)
                                    .background(Color(color), shape)
                                    .then(
                                        if (isSelected) Modifier.border(2.dp, Color(0xFF424242), shape) 
                                        else Modifier
                                    )
                                    .clickable { onColorChange(color) }
                            )
                        }
                    }
                }
                
                // Thickness Selection (Visual Group)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 32.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("궤적 굵기", modifier = Modifier.width(110.dp), fontWeight = FontWeight.Bold, color = Color(0xFF424242), maxLines = 1, softWrap = false)
                    Spacer(modifier = Modifier.width(32.dp))
                    Row(
                        modifier = Modifier
                            .background(Color.White, RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(8.dp))
                    ) {
                        val sizes = listOf(3, 5, 6)
                        sizes.forEachIndexed { index, size ->
                            val isSelected = selectedThickness == size
                            val shape = when (index) {
                                0 -> RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)
                                sizes.size - 1 -> RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)
                                else -> RoundedCornerShape(0.dp)
                            }
                            
                            Box(
                                modifier = Modifier
                                    .height(44.dp)
                                    .width(60.dp)
                                    .background(Color.White, shape)
                                    .then(
                                        if (isSelected) Modifier.padding(2.dp).border(2.dp, Color(0xFF424242), shape) 
                                        else Modifier
                                    )
                                    .clickable { onThicknessChange(size) },
                                contentAlignment = Alignment.Center
                            ) {
                                // Draw a dot representing the size (approximate visual)
                                Box(
                                    modifier = Modifier
                                        .size((size * 2).dp) // Scale for visibility
                                        .background(Color.Black, androidx.compose.foundation.shape.CircleShape)
                                )
                            }
                            
                            if (index < sizes.size - 1) {
                                Spacer(modifier = Modifier.width(1.dp).height(44.dp).background(Color(0xFFE0E0E0)))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun KakaoMapView(onMapReady: (KakaoMap) -> Unit) {
    val context = LocalContext.current
    
    AndroidView(
        factory = { ctx ->
            MapView(ctx).apply {
                start(object : MapLifeCycleCallback() {
                    override fun onMapDestroy() {}
                    override fun onMapError(e: Exception?) {}
                }, object : KakaoMapReadyCallback() {
                    override fun onMapReady(map: KakaoMap) {
                        onMapReady(map)
                    }
                    override fun getPosition(): LatLng = LatLng.from(37.5665, 126.9780)
                })
            }
        },
        update = { view ->
            view.resume() // Ensure the map is resumed
        },
        modifier = Modifier.fillMaxSize()
    )
}

private fun setupMyLocationPin(context: Context, map: KakaoMap, onLabelCreated: (Label) -> Unit) {
    val labelManager = map.labelManager ?: return
    val layer = labelManager.layer ?: return
    
    // Load map_pin_00.png
    val bitmap = BitmapFactory.decodeResource(context.resources, kr.alltodo.tp.R.drawable.map_pin_00)
    if (bitmap != null) {
        val styles = labelManager.addLabelStyles(
            LabelStyles.from("myLocStyle", LabelStyle.from(bitmap).setAnchorPoint(0.5f, 1.0f))
        )
        val options = LabelOptions.from(LatLng.from(37.5665, 126.9780))
            .setStyles(styles)
        
        val label = layer.addLabel(options)
        if (label != null) {
            onLabelCreated(label)
        }
    }
}
