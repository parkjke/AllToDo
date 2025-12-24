package kr.navermaptest

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.CameraAnimation
import com.naver.maps.map.CameraUpdate
import com.naver.maps.map.compose.*
import com.naver.maps.map.util.FusedLocationSource
import kotlinx.coroutines.launch
import com.naver.maps.map.CameraPosition

class MainActivity : ComponentActivity() {
    private lateinit var locationSource: FusedLocationSource

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            // Permission granted
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        locationSource = FusedLocationSource(this, LOCATION_PERMISSION_REQUEST_CODE)

        if (!hasLocationPermission()) {
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }

        setContent {
            MaterialTheme {
                MainScreen(locationSource)
            }
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1000
    }
}

@OptIn(ExperimentalNaverMapApi::class)
@Composable
fun MainScreen(locationSource: FusedLocationSource) {
    val cameraPositionState = rememberCameraPositionState()
    val scope = rememberCoroutineScope()
    
    // UI state for compass
    // Naver Map rotation is accessed via cameraPositionState.position.bearing
    val bearing = cameraPositionState.position.bearing
    val showCompass = bearing != 0.0

    Box(modifier = Modifier.fillMaxSize()) {
        NaverMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            locationSource = locationSource,
            properties = MapProperties(
                locationTrackingMode = LocationTrackingMode.NoFollow
            ),
            uiSettings = MapUiSettings(
                isLocationButtonEnabled = false,
                isZoomControlEnabled = false,
                isCompassEnabled = false
            )
        ) {
            // User location is automatically shown by locationSource + locationTrackingMode
        }

        // Custom UI controls
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Compass Icon
            if (showCompass) {
                MapButton(
                    icon = Icons.Default.Explore,
                    onClick = {
                        scope.launch {
                            cameraPositionState.animate(
                                CameraUpdate.toCameraPosition(
                                    CameraPosition(
                                        cameraPositionState.position.target,
                                        cameraPositionState.position.zoom,
                                        cameraPositionState.position.tilt,
                                        0.0
                                    )
                                ),
                                CameraAnimation.Easing
                            )
                        }
                    },
                    modifier = Modifier.graphicsLayer {
                        rotationZ = -bearing.toFloat()
                    }
                )
            }

            // Zoom buttons group
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
            ) {
                IconButton(onClick = {
                    scope.launch { cameraPositionState.animate(CameraUpdate.zoomIn()) }
                }) {
                    Icon(Icons.Default.Add, contentDescription = "Zoom In", tint = Color.Black)
                }
                Divider(modifier = Modifier.width(40.dp), thickness = 0.5.dp, color = Color.LightGray)
                IconButton(onClick = {
                    scope.launch { cameraPositionState.animate(CameraUpdate.zoomOut()) }
                }) {
                    Icon(Icons.Default.Remove, contentDescription = "Zoom Out", tint = Color.Black)
                }
            }

            // Current Location Button
            MapButton(
                icon = Icons.Default.MyLocation,
                onClick = {
                    locationSource.lastLocation?.let { location ->
                        val latLng = LatLng(location.latitude, location.longitude)
                        scope.launch {
                            cameraPositionState.animate(
                                CameraUpdate.scrollTo(latLng),
                                CameraAnimation.Fly
                            )
                            cameraPositionState.animate(
                                CameraUpdate.zoomTo(15.0),
                                CameraAnimation.Fly
                            )
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun MapButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(48.dp),
        shape = CircleShape,
        color = Color.White,
        shadowElevation = 4.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
