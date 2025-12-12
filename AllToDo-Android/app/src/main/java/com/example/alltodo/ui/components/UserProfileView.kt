package com.example.alltodo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
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
import com.example.alltodo.ui.theme.AllToDoGreen
import com.example.alltodo.ui.MapProvider
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.widget.Toast

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
    onMapProviderChange: (MapProvider) -> Unit
) {
    Card(
        modifier = modifier
            .width(300.dp)
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp)) 

                val context = androidx.compose.ui.platform.LocalContext.current
                val scope = rememberCoroutineScope()
                
                Text(
                    text = "My Info",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = AllToDoGreen,
                    modifier = Modifier.pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { 
                                // Single tap logic if needed
                            },
                            onDoubleTap = {
                                // Double tap logic
                            },
                            onLongPress = {
                                // Long press
                            }
                        ) {
                             // This block is for simple tap, but we want multi-tap count.
                             // detectTapGestures doesn't provide tap count directly in a single callback easily for exact customized count?
                             // wait, detectTapGestures HAS onDoubleTap.
                             // For Triple Tap, we might need custom logic or just use double tap for now?
                             // Or simply, use a counter state.
                        }
                    }
                    // Let's use a simpler Clickable with counter for Triple Tap
                    .clickable { 
                         // Check triple tap logic manually?
                    }
                )
                // Let's implement custom triple tap detector
                var tapCount by remember { mutableStateOf(0) }
                LaunchedEffect(tapCount) {
                    if (tapCount > 0) {
                        kotlinx.coroutines.delay(400) // Reset window
                        if (tapCount >= 3) {
                             uploadLogs(context)
                        }
                        tapCount = 0
                    }
                }

                Text(
                    text = "My Info",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = AllToDoGreen,
                    modifier = Modifier.clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null // No ripple
                    ) {
                        tapCount++
                    }
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Profile Icon
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier
                    .size(80.dp)
                    .background(Color.LightGray.copy(alpha = 0.2f), RoundedCornerShape(40.dp))
                    .padding(16.dp),
                tint = Color.Gray
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Info Fields (Read Only for now)
            OutlinedTextField(
                value = "User 1234",
                onValueChange = {},
                label = { Text("Nickname") },
                readOnly = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = "010-1234-5678",
                onValueChange = {},
                label = { Text("Phone Number") },
                readOnly = true,
                modifier = Modifier.fillMaxWidth()
            )

            Divider(modifier = Modifier.padding(vertical = 16.dp))

            // Settings
            Text("Settings", fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
            Spacer(modifier = Modifier.height(8.dp))

            // Max Items Stepper
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Max Popup Items: $maxPopupItems")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (maxPopupItems > 1) onMaxItemsChange(maxPopupItems - 1) }) {
                        Icon(Icons.Default.Remove, contentDescription = "Decrease")
                    }
                    IconButton(onClick = { if (maxPopupItems < 10) onMaxItemsChange(maxPopupItems + 1) }) {
                        Icon(Icons.Default.Add, contentDescription = "Increase")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Font Size
            Text("Font Size", modifier = Modifier.align(Alignment.Start))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                FilterChip(
                    selected = popupFontSize == 0,
                    onClick = { onFontSizeChange(0) },
                    label = { Text("Small") }
                )
                FilterChip(
                    selected = popupFontSize == 1,
                    onClick = { onFontSizeChange(1) },
                    label = { Text("Medium") }
                )
                FilterChip(
                    selected = popupFontSize == 2,
                    onClick = { onFontSizeChange(2) },
                    label = { Text("Large") }
                )
            }
            // Map Provider Settings
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            Text("Map Provider", fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
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
                            onClick = { onMapProviderChange(provider) }
                        )
                        Text(
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

// [NEW] Log Upload Logic
private fun uploadLogs(context: android.content.Context) {
    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
        try {
            val logContent = com.example.alltodo.utils.OptimizationLogger.readLogs(context)
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
