package com.example.alltodo.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Close // [NEW]
import androidx.compose.material3.*
import androidx.compose.ui.window.Dialog // [NEW]
import androidx.compose.ui.window.DialogProperties // [NEW]
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.zIndex // [NEW]
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.alltodo.R

@Composable
fun PinGalleryScreen(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val baseIds = listOf(
        R.drawable.pin_todo_ready to "TodoReady",
        R.drawable.pin_todo_done to "TodoDone",
        R.drawable.pin_history to "History",
        R.drawable.pin_receive_ready to "Receive",
        R.drawable.pin_current to "Current"
    )


    val testCounts = listOf(1, 5, 10, 99)

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
                // Spacer for close button
                item { Spacer(modifier = Modifier.height(30.dp)) }
                // Hedear
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "📌 핀 디자인 검증 갤러리",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "iOS와 Android 간 핀 렌더링 일관성(크기, 뱃지 위치, 중심점)을 확인하는 도구입니다.",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }

        // Section 1: Base Assets
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "1. 기본 에셋 (40x50)",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "원본 이미지가 깨지지 않고 선명한지 확인하세요.\n(Density에 따라 자동 리사이징됨)",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    baseIds.forEach { (resId, name) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val bitmap = PinImageManager.getPinBitmap(resId)
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = name,
                                    modifier = Modifier
                                        .border(1.dp, Color.Blue.copy(alpha = 0.3f))
                                )
                            }
                            Text(text = name, fontSize = 10.sp, color = Color.Gray)
                        }
                    }
                }
            }
        }

        item { Divider() }

        // Section 2: Overhang Test
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "2. 뱃지 오버행 (Overhang)",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "✅ 정상: 붉은색 뱃지가 핀 우측 상단으로 튀어나와야 합니다.\n❌ 실패: 뱃지가 잘리거나 핀 안쪽에 갇혀있으면 안 됩니다.",
                    fontSize = 12.sp,
                    color = Color.Gray
                )

                // Grid for combinations
                // Note: LazyVerticalGrid cannot be nested inside LazyColumn item easily without fixed height.
                // We use a custom Row/Column layout or FlowRow for simplicity in this demo.
                
                baseIds.forEach { (resId, name) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        testCounts.forEach { count ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                val bitmap = PinImageManager.createClusterPin(context, resId, count, android.graphics.Color.RED, 1.0f)
                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = "$name $count",
                                        modifier = Modifier.border(1.dp, Color.Red.copy(alpha = 0.3f))
                                    )
                                }
                                Text(text = "+$count", fontSize = 10.sp)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }

        item { Divider() }

        // Section 3: Anchor Point
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "3. 앵커 포인트 (중심점) 확인",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "🔴 빨간 점 = 지도 좌표\n✅ 정상: 핀의 뾰족한 끝이 빨간 점 정중앙에 닿아야 합니다.\n(Anchor Point 0.4, 1.0 검증)",
                    fontSize = 12.sp,
                    color = Color.Gray
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    baseIds.take(3).forEach { (resId, name) ->
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .background(Color.Gray.copy(alpha = 0.1f))
                        ) {
                            // The Red Dot (Simulated Map Point)
                            Box(
                                modifier = Modifier
                                    .size(4.dp)
                                    .background(Color.Red, CircleShape)
                                    .align(Alignment.Center)
                                    .zIndex(1f) // Show on top of image to verify
                            )

                            // The Pin
                            // Visual Logic:
                            // We want to simulate the map rendering.
                            // Anchor (0.4, 1.0) means the point (40% width, 100% height) of the image should be at the Center of the Box.
                            
                            val bitmap = PinImageManager.createClusterPin(context, resId, 5, android.graphics.Color.RED, 1.0f) // Test with badge
                            if (bitmap != null) {
                                // In Compose Box(contentAlignment = Center), the image center is placed at Box center.
                                // We need to offset the image so its Anchor Point is at Box center.
                                
                                // Image Size (dp):
                                val density = context.resources.displayMetrics.density
                                val imgWidthDp = bitmap.width / density
                                val imgHeightDp = bitmap.height / density
                                
                                // Anchor Point in Image Coords (relative to top-left):
                                val anchorX = imgWidthDp * 0.4f
                                val anchorY = imgHeightDp * 1.0f
                                
                                // Image Center in Image Coords:
                                val centerX = imgWidthDp / 2f
                                val centerY = imgHeightDp / 2f
                                
                                // Vector from Center to Anchor: (anchorX - centerX, anchorY - centerY)
                                // We want to move visual Image by NEGATIVE of this vector to make Anchor match Center.
                                val offsetX = -(anchorX - centerX)
                                val offsetY = -(anchorY - centerY)
                                
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = name,
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
            
            // Close Button
            androidx.compose.material3.IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                androidx.compose.material3.Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.Close,
                    contentDescription = "Close"
                )
            }
        }
            }
        }
    }

