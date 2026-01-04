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
import kr.alltodo.ui.theme.AppColors
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
    onGpsAuthClick: () -> Unit = {}, // [NEW]
    isDark: Boolean = androidx.compose.foundation.isSystemInDarkTheme()
) {
    val backgroundColor = AppColors.UserProfile.background(isDark)
    val contentColor = AppColors.UserProfile.content(isDark)
    val dividerColor = AppColors.UserProfile.divider(isDark)
    var showPinViewer by remember { mutableStateOf(false) }



    Card(
        modifier = modifier
            .fillMaxHeight()
            .fillMaxWidth(0.85f), // Leave 15% for right controls
        shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor), // Opaque Gray 2
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp, bottom = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val context = androidx.compose.ui.platform.LocalContext.current
            
            // Triple Tap Logic State (Simplified/Restored)
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
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp), // [NEW] Top margin
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Title
                Text(
                    text = "내 정보",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    modifier = Modifier.clickable { tapCount++ } // Triple tap logic restored
                )

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "닫기", tint = contentColor, modifier = Modifier.size(32.dp))
                }
            }
            
            Divider(color = dividerColor, modifier = Modifier.padding(vertical = 16.dp))
            
            Spacer(modifier = Modifier.height(16.dp))

            // Profile Section (Restored Gallery, Larger Profile)
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Pin Gallery (Restored)
                Surface(
                    onClick = { showPinViewer = true },
                    modifier = Modifier.size(52.dp),
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = AppColors.UserProfile.subButtonBackground(isDark),
                    border = androidx.compose.foundation.BorderStroke(width = 1.dp, color = dividerColor)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(kr.alltodo.R.drawable.map_pin_01),
                            contentDescription = "핀 보관함",
                            tint = Color.Unspecified,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(32.dp))

                // Center: Profile Icon (Enlarged for better impact)
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier
                        .size(100.dp)
                        .background(AppColors.UserProfile.profileIconBackground(isDark), RoundedCornerShape(50.dp))
                        .padding(20.dp),
                    tint = contentColor
                )

                Spacer(modifier = Modifier.width(32.dp))

                // Right: GPS Auth
                Surface(
                    onClick = onGpsAuthClick,
                    modifier = Modifier.size(52.dp),
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = AppColors.UserProfile.subButtonBackground(isDark),
                    border = androidx.compose.foundation.BorderStroke(width = 1.dp, color = dividerColor)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(id = kr.alltodo.R.drawable.ic_path_tracking),
                            contentDescription = "경로추적",
                            tint = if (isTracking) kr.alltodo.ui.theme.AllToDoRed else AppColors.UserProfile.subButtonContent(isDark),
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
                    focusedLabelColor = contentColor,
                    unfocusedLabelColor = contentColor.copy(alpha = 0.7f),
                    focusedBorderColor = contentColor,
                    unfocusedBorderColor = contentColor.copy(alpha = 0.5f),
                    focusedTextColor = contentColor,
                    unfocusedTextColor = contentColor
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = "010-1234-5678",
                onValueChange = {},
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedLabelColor = contentColor,
                    unfocusedLabelColor = contentColor.copy(alpha = 0.7f),
                    focusedBorderColor = contentColor,
                    unfocusedBorderColor = contentColor.copy(alpha = 0.5f),
                    focusedTextColor = contentColor,
                    unfocusedTextColor = contentColor
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = "Seoul, Gangnam-gu, ...",
                onValueChange = {},
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedLabelColor = contentColor,
                    unfocusedLabelColor = contentColor.copy(alpha = 0.7f),
                    focusedBorderColor = contentColor,
                    unfocusedBorderColor = contentColor.copy(alpha = 0.5f),
                    focusedTextColor = contentColor,
                    unfocusedTextColor = contentColor
                )
            )

            Divider(modifier = Modifier.padding(vertical = 16.dp), color = dividerColor)
            
            // Settings
            Text("설정", color = AppColors.UserProfile.settingHeader(isDark), fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
            Spacer(modifier = Modifier.height(8.dp))

            // Max Items Stepper
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("최대 팝업 항목 수: $maxPopupItems", color = contentColor)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (maxPopupItems > 1) onMaxItemsChange(maxPopupItems - 1) }) {
                        Icon(Icons.Default.Remove, contentDescription = "감소", tint = contentColor)
                    }
                    IconButton(onClick = { if (maxPopupItems < 10) onMaxItemsChange(maxPopupItems + 1) }) {
                        Icon(Icons.Default.Add, contentDescription = "증가", tint = contentColor)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Font Size
            Text("글꼴 크기", color = contentColor, modifier = Modifier.align(Alignment.Start))
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                val chipColors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = AppColors.UserProfile.chipSelectedContainer(isDark),
                    selectedLabelColor = AppColors.UserProfile.chipText(isDark),
                    containerColor = AppColors.UserProfile.chipUnselectedContainer(isDark),
                    labelColor = contentColor
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
            Divider(color = dividerColor, modifier = Modifier.padding(vertical = 8.dp))
            Text("지도 서비스 제공자", color = AppColors.UserProfile.settingHeader(isDark), fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
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
                            colors = RadioButtonDefaults.colors(
                                selectedColor = kr.alltodo.ui.theme.AllToDoGreen,
                                unselectedColor = contentColor.copy(alpha = 0.5f)
                            )
                        )
                        Text(
                            text = provider.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = contentColor,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                    if (provider != MapProvider.values().last()) { 
                        Divider(color = dividerColor)
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
