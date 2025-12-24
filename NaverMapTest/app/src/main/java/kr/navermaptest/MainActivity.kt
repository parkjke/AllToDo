package kr.navermaptest

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.naver.maps.map.compose.ExperimentalNaverMapApi
import com.naver.maps.map.compose.NaverMap
import com.naver.maps.map.compose.MapProperties
import com.naver.maps.map.compose.MapUiSettings
import com.naver.maps.map.compose.LocationTrackingMode

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Header
                        TopAppBar()
                        
                        // Map
                        NaverMapScreen(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
fun TopAppBar() {
    Surface(
        color = Color(0xFF00C73C), // Naver Green
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text(
                text = "Naver Map Test",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@OptIn(ExperimentalNaverMapApi::class)
@Composable
fun NaverMapScreen(modifier: Modifier = Modifier) {
    NaverMap(
        modifier = modifier.fillMaxSize(),
        properties = MapProperties(
            locationTrackingMode = LocationTrackingMode.Follow
        ),
        uiSettings = MapUiSettings(
            isLocationButtonEnabled = true
        )
    )
}
