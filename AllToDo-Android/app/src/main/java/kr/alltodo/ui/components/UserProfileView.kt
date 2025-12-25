package kr.alltodo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.alltodo.ui.theme.AllToDoGreen
import kr.alltodo.ui.MapProvider
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.widget.Toast

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

@OptIn(ExperimentalMaterial3Api::class) // [FIX]
@Composable
fun UserProfileView(
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    maxPopupItems: Int,
    onMaxItemsChange: (Int) -> Unit,
    popupFontSize: Int,
    onFontSizeChange: (Int) -> Unit,
    currentMapProvider: MapProvider,
    onMapProviderChange: (MapProvider) -> Unit,
    isTracking: Boolean = false,
    onGpsAuthClick: () -> Unit = {} // [NEW]
) {
    var showPinViewer by remember { mutableStateOf(false) }



    Card(
        modifier = modifier
            .fillMaxHeight()
            .fillMaxWidth(0.85f), // Leave 15% for right controls
        shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE0E0E0)), // Opaque Gray 2
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val context = androidx.compose.ui.platform.LocalContext.current
            
            // Triple Tap Logic State
            var tapCount by remember { mutableStateOf(0) }
            LaunchedEffect(tapCount) {
                if (tapCount > 0) {
                    kotlinx.coroutines.delay(400)
                    if (tapCount >= 3) uploadLogs(context)
                    tapCount = 0
                }
            }

            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Title
                Text(
                    text = "내 정보",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF333333),
                    modifier = Modifier.clickable { tapCount++ } // Triple tap logic
                )

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "닫기", tint = Color(0xFF333333), modifier = Modifier.size(32.dp))
                }
            }
            
            Divider(color = Color(0xFF333333).copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 16.dp))
            
            Spacer(modifier = Modifier.height(16.dp))

            // Profile Section with side buttons (iOS Style)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Button: Pin Gallery
                Surface(
                    onClick = { showPinViewer = true },
                    modifier = Modifier.size(50.dp),
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = Color.White.copy(alpha = 0.5f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF333333).copy(alpha = 0.3f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(kr.alltodo.R.drawable.pin_todo_ready),
                            contentDescription = "핀 보관함",
                            tint = Color.Unspecified,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(32.dp))

                // Center: Profile Icon
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier
                        .size(80.dp)
                        .background(Color.LightGray.copy(alpha = 0.2f), RoundedCornerShape(40.dp))
                        .padding(16.dp),
                    tint = Color(0xFF333333)
                )

                Spacer(modifier = Modifier.width(32.dp))

                Surface(
                    onClick = onGpsAuthClick,
                    modifier = Modifier.size(50.dp),
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = Color.White.copy(alpha = 0.5f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF333333).copy(alpha = 0.3f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(id = kr.alltodo.R.drawable.ic_path_tracking),
                            contentDescription = "경로추적",
                            tint = if (isTracking) kr.alltodo.ui.theme.AllToDoRed else Color(0xFF333333),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Info Fields (Read Only for now)
            OutlinedTextField(
                value = "User 1234",
                onValueChange = {},
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedLabelColor = Color(0xFF333333),
                    unfocusedLabelColor = Color(0xFF333333).copy(alpha = 0.7f),
                    focusedBorderColor = Color(0xFF333333),
                    unfocusedBorderColor = Color(0xFF333333).copy(alpha = 0.5f),
                    focusedTextColor = Color(0xFF333333),
                    unfocusedTextColor = Color(0xFF333333)
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = "010-1234-5678",
                onValueChange = {},
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedLabelColor = Color(0xFF333333),
                    unfocusedLabelColor = Color(0xFF333333).copy(alpha = 0.7f),
                    focusedBorderColor = Color(0xFF333333),
                    unfocusedBorderColor = Color(0xFF333333).copy(alpha = 0.5f),
                    focusedTextColor = Color(0xFF333333),
                    unfocusedTextColor = Color(0xFF333333)
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = "Seoul, Gangnam-gu, ...",
                onValueChange = {},
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedLabelColor = Color(0xFF333333),
                    unfocusedLabelColor = Color(0xFF333333).copy(alpha = 0.7f),
                    focusedBorderColor = Color(0xFF333333),
                    unfocusedBorderColor = Color(0xFF333333).copy(alpha = 0.5f),
                    focusedTextColor = Color(0xFF333333),
                    unfocusedTextColor = Color(0xFF333333)
                )
            )

            Divider(modifier = Modifier.padding(vertical = 16.dp))

            // Settings
            Text("설정", color = Color(0xFF333333), fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
            Spacer(modifier = Modifier.height(8.dp))

            // Max Items Stepper
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("최대 팝업 항목 수: $maxPopupItems", color = Color(0xFF333333))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (maxPopupItems > 1) onMaxItemsChange(maxPopupItems - 1) }) {
                        Icon(Icons.Default.Remove, contentDescription = "감소", tint = Color(0xFF333333))
                    }
                    IconButton(onClick = { if (maxPopupItems < 10) onMaxItemsChange(maxPopupItems + 1) }) {
                        Icon(Icons.Default.Add, contentDescription = "증가", tint = Color(0xFF333333))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Font Size
            Text("글꼴 크기", color = Color(0xFF333333), modifier = Modifier.align(Alignment.Start))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                val chipColors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFF9E9E9E), // Gray 500 (Gray 5)
                    selectedLabelColor = Color.White,
                    labelColor = Color(0xFF333333)
                )

                FilterChip(
                    selected = popupFontSize == 0,
                    onClick = { onFontSizeChange(0) },
                    label = { Text("작게") },
                    colors = chipColors
                )
                FilterChip(
                    selected = popupFontSize == 1,
                    onClick = { onFontSizeChange(1) },
                    label = { Text("보통") },
                    colors = chipColors
                )
                FilterChip(
                    selected = popupFontSize == 2,
                    onClick = { onFontSizeChange(2) },
                    label = { Text("크게") },
                    colors = chipColors
                )
            }
            // Map Provider Settings
            Divider(color = Color(0xFF333333).copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 8.dp))
            Text("지도 서비스 제공자", color = Color(0xFF333333), fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
            Column {
                MapProvider.values().forEach { provider ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onMapProviderChange(provider) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (provider == currentMapProvider),
                            onClick = { onMapProviderChange(provider) },
                            colors = RadioButtonDefaults.colors(selectedColor = kr.alltodo.ui.theme.AllToDoGreen, unselectedColor = Color(0xFF333333).copy(alpha = 0.7f))
                        )
                        Text(
                            text = provider.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color(0xFF333333),
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                    if (provider != MapProvider.values().last()) { // Add divider between items, but not after the last one
                        Divider(color = Color(0xFF333333).copy(alpha = 0.3f))
                    }
                }
            }
        }

    if (showPinViewer) {
        // [FIX] Use the full package name if PinGalleryScreen is in .ui package
        kr.alltodo.ui.PinGalleryScreen(onDismiss = { showPinViewer = false })
    }
    }
}

// [NEW] Log Upload Logic
private fun uploadLogs(context: android.content.Context) {
    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
        try {
            val logContent = kr.alltodo.utils.OptimizationLogger.readLogs(context)
            if (logContent.isEmpty() || logContent == "No logs found.") {
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    Toast.makeText(context, "No logs to upload", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            val lines = logContent.lines().filter { it.isNotBlank() }
            val jsonArray = org.json.JSONArray()
            val deviceId = android.os.Build.MODEL ?: "Android_Unknown"

            lines.forEach { line ->
                try {
                    val original = org.json.JSONObject(line)
                    val mapped = org.json.JSONObject()
                    mapped.put("level", original.optString("type", "INFO"))
                    val msg = original.optString("value", "") + " [Bat: " + original.optString("battery", "?") + "]"
                    mapped.put("message", msg)
                    mapped.put("device", deviceId)
                    mapped.put("timestamp", original.optLong("timestamp", System.currentTimeMillis()) / 1000.0)
                    jsonArray.put(mapped)
                } catch (e: Exception) {
                    // Skip malformed lines
                }
            }
            
            if (jsonArray.length() == 0) return@launch

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = jsonArray.toString().toRequestBody(mediaType)
            
            // Using logic from BuildConfig or hardcoded for dev
            val url = "http://175.194.163.56:8003/dev/logs/batch" 
            
            val request = okhttp3.Request.Builder()
                .url(url)
                .post(body)
                .build()

            val client = okhttp3.OkHttpClient()
            val response = client.newCall(request).execute()
            
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                if (response.isSuccessful) {
                    Toast.makeText(context, "Logs Uploaded! (${jsonArray.length()})", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Upload Failed: ${response.code}", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
