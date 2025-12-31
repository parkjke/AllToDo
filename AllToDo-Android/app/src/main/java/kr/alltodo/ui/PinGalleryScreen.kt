package kr.alltodo.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import kr.alltodo.R
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.shape.RoundedCornerShape
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

@Composable
fun PinGalleryScreen(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var refreshKey by remember { mutableStateOf(0) }

    val allTypes = listOf(
        "00", "01", "02",
        "10", "11", "12", "13", "14",
        "20", "21", "22", "23", "24",
        "25"
    )
    val targetTypes = listOf("00", "10", "20", "25")

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = Color.White,
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    item {
                        Spacer(modifier = Modifier.height(30.dp))
                        Text("📌 Pin Gallery Refactored", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("Static Asset Verification (Android)", fontSize = 14.sp, color = Color.Gray)
                    }

                    // Section 1: All Bitmap Pins
                    item {
                        SectionHeader("1. All Bitmap Pins", "Assets 내의 모든 map_pin_XX 정적 이미지")
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            mainAxisSpacing = 8.dp,
                            crossAxisSpacing = 8.dp
                        ) {
                            allTypes.forEach { type ->
                                PinCell(type = type)
                            }
                        }
                    }

                    item { Divider(color = Color.LightGray.copy(alpha = 0.5f)) }

                    // Section 2: Badged Pins + Anchor
                    item {
                        SectionHeader("2. Badged Pins + Anchor", "뱃지(Count: 5) 적용 및 지도 좌표(Anchor) 빨간 점 표시")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            targetTypes.forEach { type ->
                                AnchorPinCell(type = type, count = 5, showBadge = true)
                            }
                        }
                    }

                    item { Divider(color = Color.LightGray.copy(alpha = 0.5f)) }

                    // Section 3: Raw Pins + Anchor
                    item {
                        SectionHeader("3. Raw Pins + Anchor", "뱃지 없음, 지도 좌표(Anchor) 빨간 점 표시")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            targetTypes.forEach { type ->
                                AnchorPinCell(type = type, count = 0, showBadge = false)
                            }
                        }
                    }

                    item { Divider(color = Color.LightGray.copy(alpha = 0.5f)) }

                    // Section 4: Engine Preview
                    item {
                        SectionHeader("4. Engine Provider Preview", "각 지도 SDK별 렌더링 시뮬레이션 (Scale 0.7-1.0)")
                        EnginePreviewTable(targetTypes)
                    }
                    
                    item { Spacer(modifier = Modifier.height(100.dp)) }
                }

                // Controls
                Row(
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = { 
                        PinImageManager.clearCache()
                        refreshKey++ 
                    }) {
                        Icon(Icons.Default.Refresh, "Refresh", tint = Color(0xFF28CD41))
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Close")
                    }
                }
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, subtitle: String) {
    Column(modifier = Modifier.padding(bottom = 8.dp)) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, fontSize = 12.sp, color = Color.Gray)
    }
}

@Composable
fun PinCell(type: String) {
    val context = LocalContext.current
    val bitmap = remember(type) { PinImageManager.fetchStaticPin(context, type) }

    Column(
        modifier = Modifier
            .width(60.dp)
            .background(Color.Gray.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = type,
                modifier = Modifier.size(40.dp)
            )
        } else {
            Icon(Icons.Default.Error, "Error", tint = Color.Red, modifier = Modifier.size(40.dp))
        }
        Text(type, fontSize = 10.sp, color = Color.Gray)
    }
}

@Composable
fun AnchorPinCell(type: String, count: Int, showBadge: Boolean) {
    val context = LocalContext.current
    val density = LocalContext.current.resources.displayMetrics.density
    
    // For Anchor Cell, we want to simulate the offset logic used in Map Providers
    // Anchor in this project is 0.5 (unbadged) or 0.392 (badged)
    val anchorX = if (count > 0) 0.392f else 0.5f
    val anchorY = 1.0f
    
    val badgeColor = when(type) {
        "10" -> android.graphics.Color.parseColor("#28CD41")
        "20" -> android.graphics.Color.parseColor("#1976D2")
        else -> android.graphics.Color.RED
    }
    
    val bitmap = remember(type, count, showBadge) {
        PinImageManager.fetchStaticPin(context, type, count, badgeColor)
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .background(Color.Gray.copy(alpha = 0.1f))
        ) {
            // Coordinate Crosshair
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.Blue.copy(alpha = 0.2f)).align(Alignment.Center))
            Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(Color.Blue.copy(alpha = 0.2f)).align(Alignment.Center))
            
            // Map Point (Anchor Destination)
            Box(modifier = Modifier.size(4.dp).background(Color.Red, CircleShape).align(Alignment.Center).zIndex(5f))

            if (bitmap != null) {
                // Calculation to place Anchor at Center of Box
                // Box Center = (30dp, 30dp)
                // We want Bitmap(anchorX, anchorY) to be at Box Center.
                
                val imgW = bitmap.width / density
                val imgH = bitmap.height / density
                
                // Offset calculation:
                // Normal align(Center) places bitmap center at (30, 30).
                // Offset needed = (BitmapCenter - Anchor)
                val offsetX = (imgW / 2f) - (imgW * anchorX)
                val offsetY = (imgH / 2f) - (imgH * anchorY)

                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = type,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(x = offsetX.dp, y = offsetY.dp)
                )
            }
        }
        Text(type, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun EnginePreviewTable(types: List<String>) {
    val providers = listOf(
        Triple("Google", 1.0f, Color.Blue),
        Triple("Naver", 0.9f, Color(0xFF28CD41)),
        Triple("Kakao", 0.7f, Color(0xFFFFCC00))
    )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Table Header
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Engine", fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(50.dp))
            types.forEach { _ -> Text("Type", fontSize = 11.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center) }
        }
        
        Divider(thickness = 1.dp)

        providers.forEach { (name, scale, color) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(name, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color, modifier = Modifier.width(50.dp))
                
                types.forEach { type ->
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        EnginePinCell(type = type, scale = scale)
                    }
                }
            }
            Divider(color = Color.LightGray.copy(alpha = 0.2f))
        }
    }
}

@Composable
fun EnginePinCell(type: String, scale: Float) {
    val context = LocalContext.current
    val badgeColor = if (type == "20") android.graphics.Color.BLUE else android.graphics.Color.RED
    val bitmap = remember(type, scale) { 
        PinImageManager.fetchStaticPin(context, type, count = if (type == "20") 5 else 0, badgeColor = badgeColor, scale = scale) 
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.height(40.dp) // Visual constraint for table
        )
    }
}

// FlowRow implementation (Simple)
@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    mainAxisSpacing: androidx.compose.ui.unit.Dp = 0.dp,
    crossAxisSpacing: androidx.compose.ui.unit.Dp = 0.dp,
    content: @Composable () -> Unit
) {
    androidx.compose.ui.layout.Layout(
        content = content,
        modifier = modifier
    ) { measurables, constraints ->
        val placeholders = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val rows = mutableListOf<List<androidx.compose.ui.layout.Placeable>>()
        var currentRow = mutableListOf<androidx.compose.ui.layout.Placeable>()
        var currentRowWidth = 0
        
        placeholders.forEach { placeable ->
            if (currentRowWidth + placeable.width + mainAxisSpacing.toPx() > constraints.maxWidth && currentRow.isNotEmpty()) {
                rows.add(currentRow)
                currentRow = mutableListOf()
                currentRowWidth = 0
            }
            currentRow.add(placeable)
            currentRowWidth += placeable.width + mainAxisSpacing.toPx().toInt()
        }
        rows.add(currentRow)
        
        val height = rows.sumOf { row -> row.maxOf { it.height } + crossAxisSpacing.toPx().toInt() }
        
        layout(constraints.maxWidth, height) {
            var y = 0
            rows.forEach { row ->
                var x = 0
                val rowHeight = row.maxOf { it.height }
                row.forEach { placeable ->
                    placeable.placeRelative(x, y)
                    x += placeable.width + mainAxisSpacing.toPx().toInt()
                }
                y += rowHeight + crossAxisSpacing.toPx().toInt()
            }
        }
    }
}

