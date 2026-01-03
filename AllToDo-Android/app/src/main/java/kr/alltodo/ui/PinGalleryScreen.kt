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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.clickable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind

@Composable
fun PinGalleryScreen(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var selectedDetail by remember { mutableStateOf<PinDetail?>(null) }

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
                        Text("Pin Gallery", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF888888))
                        Text("Static Asset Verification (Android)", fontSize = 16.sp, color = Color.DarkGray)
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
                                AnchorPinCell(type = type, count = 5, showBadge = true) { detail ->
                                    selectedDetail = detail
                                }
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
                                AnchorPinCell(type = type, count = 0, showBadge = false) { detail ->
                                    selectedDetail = detail
                                }
                            }
                        }
                    }

                    item { Divider(color = Color.LightGray.copy(alpha = 0.5f)) }

                    // Section 4: Engine Preview
                    item {
                        SectionHeader("4. Engine Provider Preview", "각 지도 SDK별 렌더링 시뮬레이션 (Scale 0.7-1.0)")
                        EnginePreviewTable(targetTypes) { detail ->
                            selectedDetail = detail
                        }
                    }
                    
                    item { Spacer(modifier = Modifier.height(100.dp)) }
                }

                // Controls
                Row(
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Close")
                    }
                }
                
                // Pin Inspector Overlay
                if (selectedDetail != null) {
                    PinInspectorDialog(
                        detail = selectedDetail!!,
                        onDismiss = { selectedDetail = null }
                    )
                }
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, subtitle: String) {
    Column(modifier = Modifier.padding(bottom = 12.dp)) {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        Text(subtitle, fontSize = 14.sp, color = Color.DarkGray)
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
        Text(type, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.Black)
    }
}

@Composable
fun AnchorPinCell(type: String, count: Int, showBadge: Boolean, onTap: (PinDetail) -> Unit) {
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

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable {
            if (bitmap != null) {
                // Section 2, 3 uses Naver logic (0.392 or 0.5) as reference
                onTap(PinDetail("Simulated", type, count, bitmap, Offset(anchorX, anchorY)))
            }
        }
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .background(Color.Gray.copy(alpha = 0.1f))
        ) {
            // Coordinate Crosshair (75% position: X=30, Y=45)
            Box(modifier = Modifier.fillMaxWidth().height(45.dp).drawBehind {
                drawLine(Color.Blue.copy(alpha = 0.2f), Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
            })
            Box(modifier = Modifier.fillMaxHeight().width(30.dp).drawBehind {
                drawLine(Color.Blue.copy(alpha = 0.2f), Offset(size.width, 0f), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
            })
            
            // Map Point (Anchor Destination at 30, 45)
            Box(modifier = Modifier.size(4.dp).offset(x = 28.dp, y = 43.dp).background(Color.Red, CircleShape).zIndex(5f))

            if (bitmap != null) {
                val imgW = bitmap.width / density
                val imgH = bitmap.height / density
                
                // Target Point is (30, 45). Pin Anchor (ax, ay) should be there.
                // Image TopLeft = (30 - ax*w, 45 - ay*h)
                val drawX = 30f - (anchorX * imgW)
                val drawY = 45f - (anchorY * imgH)

                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = type,
                    modifier = Modifier
                        .offset(x = drawX.dp, y = drawY.dp)
                )
            }
        }
        Text(type, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun EnginePreviewTable(types: List<String>, onDetailClick: (PinDetail) -> Unit) {
    val providers = listOf(
        Triple("Google", 1.0f, Color.Blue),
        Triple("Naver", 0.9f, Color(0xFF28CD41)),
        Triple("Kakao", 0.7f, Color(0xFFFFCC00))
    )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Table Header
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Engine", fontSize = 13.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(60.dp))
            types.forEach { _ -> Text("Type", fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center) }
        }
        
        Divider(thickness = 1.dp)

        providers.forEach { (name, scale, color) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(name, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = color, modifier = Modifier.width(60.dp))
                
                types.forEach { type ->
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        EnginePinCell(type = type, scale = scale, provider = name) { detail ->
                            onDetailClick(detail)
                        }
                    }
                }
            }
            Divider(color = Color.LightGray.copy(alpha = 0.2f))
        }
    }
}

@Composable
fun EnginePinCell(type: String, scale: Float, provider: String, onDetailClick: (PinDetail) -> Unit) {
    val context = LocalContext.current
    val badgeColor = if (type == "20") android.graphics.Color.RED else android.graphics.Color.RED // Red is default for Todo
    val actualCount = if (type == "20") 5 else 0
    val bitmap = remember(type, scale) { 
        PinImageManager.fetchStaticPin(context, type, count = actualCount, badgeColor = badgeColor, scale = scale) 
    }
    
    // Engine specific anchor logic
    // Unified Standard: 0.392f for badged pins (20/51 ratio), 0.5f for raw pins
    val anchorX = if (actualCount > 1) 0.392f else 0.5f
    val anchorY = 1.0f

    Box(
        modifier = Modifier
            .size(60.dp)
            .background(Color.Gray.copy(alpha = 0.1f))
            .clickable {
                if (bitmap != null) {
                    onDetailClick(PinDetail(provider, type, actualCount, bitmap, Offset(anchorX, anchorY)))
                }
            }
    ) {
        // Coordinate Crosshair (75% position: X=30, Y=45)
        Box(modifier = Modifier.fillMaxWidth().height(45.dp).drawBehind {
            drawLine(Color.Blue.copy(alpha = 0.2f), Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
        })
        Box(modifier = Modifier.fillMaxHeight().width(30.dp).drawBehind {
            drawLine(Color.Blue.copy(alpha = 0.2f), Offset(size.width, 0f), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
        })
        
        // Map Point (Anchor Destination at 30, 45)
        Box(modifier = Modifier.size(4.dp).offset(x = 28.dp, y = 43.dp).background(Color.Red, CircleShape).zIndex(5f))

        if (bitmap != null) {
            val density = context.resources.displayMetrics.density
            val imgW = bitmap.width / density
            val imgH = bitmap.height / density
            
            val drawX = 30f - (anchorX * imgW)
            val drawY = 45f - (anchorY * imgH)

            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.offset(x = drawX.dp, y = drawY.dp)
            )
        }
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

@Composable
fun PinInspectorDialog(detail: PinDetail, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White
        ) {
            val context = LocalContext.current
            val density = context.resources.displayMetrics.density

            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("${detail.provider} Map - Type ${detail.type}", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color.Black)
                        Text(if (detail.count > 1) "Cluster (${detail.count} items)" else "Single Item", fontSize = 16.sp, color = Color.DarkGray)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Close")
                    }
                }
                Divider()

                // Inspection Area (8x Zoom)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color(0xFFF5F5F5))
                        .pointerInput(Unit) { /* Just consume pointer */ }
                ) {
                    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                        val center = Offset(size.width / 2f, size.height * 0.75f)
                        val scale = 8.0f
                        
                        // 1. Grid
                        val step = 20.dp.toPx()
                        for (x in 0..(size.width / step).toInt()) {
                            drawLine(Color.LightGray.copy(alpha = 0.5f), Offset(x * step, 0f), Offset(x * step, size.height))
                        }
                        for (y in 0..(size.height / step).toInt()) {
                            drawLine(Color.LightGray.copy(alpha = 0.5f), Offset(0f, y * step), Offset(size.width, y * step))
                        }

                        // 2. Crosshair (Map Target)
                        drawLine(Color.Blue.copy(alpha = 0.7f), Offset(center.x, 0f), Offset(center.x, size.height), strokeWidth = 2f)
                        drawLine(Color.Blue.copy(alpha = 0.7f), Offset(0f, center.y), Offset(size.width, center.y), strokeWidth = 2f)

                        // 3. Pin (8x Zoomed) - Target: Anchor Point at Crosshair
                        val img = detail.bitmap
                        val ax = detail.anchor.x
                        val ay = detail.anchor.y
                        
                        val w = img.width * scale / density
                        val h = img.height * scale / density
                        
                        // TopLeft = Center - (Anchor * Size)
                        val topLeft = Offset(
                            center.x - (ax * w),
                            center.y - (ay * h)
                        )
                        
                        drawImage(
                            image = img.asImageBitmap(),
                            dstOffset = androidx.compose.ui.unit.IntOffset(topLeft.x.toInt(), topLeft.y.toInt()),
                            dstSize = androidx.compose.ui.unit.IntSize(w.toInt(), h.toInt()),
                            filterQuality = FilterQuality.None // CRITICAL: Sharp Pixels
                        )

                        // 4. Anchor Point Indicator (Red Dot)
                        drawCircle(Color.Red, radius = 4f, center = center)
                    }
                }

                // Footer Info
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Logic Mirroring", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = Color.Black)
                    Text("Policy: normalized anchor (UV)", fontSize = 13.sp, color = Color.DarkGray)
                    Text("Anchor: (${String.format("%.3f", detail.anchor.x)}, ${String.format("%.3f", detail.anchor.y)})", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1976D2))
                    Text("Asset Size: ${detail.bitmap.width}x${detail.bitmap.height} px", fontSize = 13.sp, color = Color.DarkGray)
                }
            }
        }
    }
}

data class PinDetail(
    val provider: String,
    val type: String,
    val count: Int,
    val bitmap: Bitmap,
    val anchor: androidx.compose.ui.geometry.Offset
)

