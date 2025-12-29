package kr.alltodo.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.alltodo.R

// Helper Data
data class PinGalleryItem(
    val name: String,
    val shieldName: String,
    val markName: String,
    val badgeColor: Int
)

@Composable
fun PinGalleryScreen(onDismiss: () -> Unit) {
    val context = LocalContext.current
    
    // [FIX] Trigger for Recomposition (Regenerate)
    var refreshKey by remember { mutableStateOf(0) }

    val baseItems = listOf(
        PinGalleryItem("TodoReady", "pin_shield_1x", "pin_mark_10", android.graphics.Color.GREEN), // Green
        PinGalleryItem("TodoDone", "pin_shield_1x", "pin_mark_12", android.graphics.Color.GREEN),
        PinGalleryItem("History", "pin_shield_0x", "pin_mark_01", android.graphics.Color.RED),
        PinGalleryItem("Server", "pin_shield_2x", "pin_mark_20", android.graphics.Color.BLUE), 
        PinGalleryItem("Current", "pin_shield_0x", "pin_mark_00", android.graphics.Color.RED),
        PinGalleryItem("Pin 23", "pin_shield_2x", "pin_mark_23", android.graphics.Color.BLUE)
    )

    val testCounts = listOf(1, 10, 99)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false) // Full Screen
    ) {
        Surface(
            color = Color.White,
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(30.dp)
                ) {
                    // Header
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("📌 핀 디자인 검증 갤러리", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text("Dynamic Composition v4.0 (Android)", fontSize = 14.sp, color = Color.Blue)
                            Text(
                                "iOS와 Android 간 핀 렌더링 일관성 확인.\nKey: $refreshKey",
                                fontSize = 12.sp, color = Color.Gray,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }

                    // Section 1: Base Composite Assets
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("1. 동적 합성 (Base Composite)", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                baseItems.forEach { item ->
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        val shieldId = PinImageManager.getResourceId(context, item.shieldName)
                                        val markId = PinImageManager.getResourceId(context, item.markName)
                                        
                                        if (shieldId != 0 && markId != 0) {
                                            val bitmap = PinImageManager.fetchCompositePin(
                                                context, shieldId, markId, 0, item.badgeColor
                                            )
                                            if (bitmap != null) {
                                                Image(
                                                    bitmap = bitmap.asImageBitmap(),
                                                    contentDescription = item.name,
                                                    modifier = Modifier.border(1.dp, Color.Blue.copy(alpha = 0.3f))
                                                )
                                            }
                                        } else {
                                            Text("Missing Res", fontSize = 8.sp, color = Color.Red)
                                        }
                                        Text(item.name, fontSize = 10.sp, color = Color.Gray)
                                    }
                                }
                            }
                        }
                    }

                    item { Divider() }

                    // Section 2: Badged Pins
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("2. 뱃지 오버행 (Overhang)", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            
                            baseItems.take(4).forEach { item ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    testCounts.forEach { count ->
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            val shieldId = PinImageManager.getResourceId(context, item.shieldName)
                                            val markId = PinImageManager.getResourceId(context, item.markName)
                                            
                                            val bitmap = PinImageManager.fetchCompositePin(
                                                context, shieldId, markId, count, item.badgeColor
                                            )
                                            if (bitmap != null) {
                                                Image(
                                                    bitmap = bitmap.asImageBitmap(),
                                                    contentDescription = "${item.name} $count",
                                                    modifier = Modifier.border(1.dp, Color.Red.copy(alpha = 0.3f))
                                                )
                                            }
                                            Text("+$count", fontSize = 10.sp)
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                            }
                        }
                    }
                    
                    item { Divider() }

                    // Section 3: Anchor Point Verification
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("3. 앵커 포인트 (중심점) 확인", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("🔴 빨간 점 = 지도 좌표 (Anchor 0.4, 1.0)", fontSize = 12.sp, color = Color.Gray)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                baseItems.take(3).forEach { item ->
                                    Box(
                                        modifier = Modifier
                                            .size(100.dp)
                                            .background(Color.Gray.copy(alpha = 0.1f))
                                    ) {
                                        // The Red Dot (Simulated Map Point) at Box Center
                                        Box(
                                            modifier = Modifier
                                                .size(4.dp)
                                                .background(Color.Red, CircleShape)
                                                .align(Alignment.Center)
                                                .zIndex(1f)
                                        )

                                        // The Pin
                                        val shieldId = PinImageManager.getResourceId(context, item.shieldName)
                                        val markId = PinImageManager.getResourceId(context, item.markName)
                                        val bitmap = PinImageManager.fetchCompositePin(context, shieldId, markId, 5, item.badgeColor)
                                        
                                        if (bitmap != null) {
                                            // Visual Logic (Same as before):
                                            val density = context.resources.displayMetrics.density
                                            val imgW = bitmap.width / density
                                            val imgH = bitmap.height / density
                                            
                                            // Anchor Point (0.4, 1.0)
                                            val anchorX = imgW * 0.4f
                                            val anchorY = imgH * 1.0f
                                            
                                            val centerX = imgW / 2f
                                            val centerY = imgH / 2f
                                            
                                            val offsetX = -(anchorX - centerX)
                                            val offsetY = -(anchorY - centerY)
                                            
                                            Image(
                                                bitmap = bitmap.asImageBitmap(),
                                                contentDescription = item.name,
                                                modifier = Modifier
                                                    .align(Alignment.Center)
                                                    .offset(x = offsetX.dp, y = offsetY.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Controls
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Regenerate Button
                    androidx.compose.material3.IconButton(
                        onClick = {
                            PinImageManager.clearCache(context)
                            refreshKey += 1 // Force Recomposition
                        }
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = androidx.compose.material.icons.Default.Refresh,
                            contentDescription = "Regenerate",
                            tint = Color.Blue
                        )
                    }
                    
                    // Close Button
                    androidx.compose.material3.IconButton(
                        onClick = onDismiss
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = androidx.compose.material.icons.Default.Close,
                            contentDescription = "Close"
                        )
                    }
                }
            }
        }
    }
}
