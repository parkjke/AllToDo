package kr.alltodo.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.unit.*
import kr.alltodo.ui.UnifiedItem
import kr.alltodo.ui.theme.AllToDoGreen
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.text.font.FontWeight

/**
 * Custom Shape that includes a rounded rectangle bubble and a triangle tail at the bottom center.
 */
class BubbleShape(
    private val cornerRadius: Dp = 12.dp,
    private val tailWidth: Dp = 20.dp,
    private val tailHeight: Dp = 10.dp
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val radiusP = with(density) { cornerRadius.toPx() }
        val twP = with(density) { tailWidth.toPx() }
        val thP = with(density) { tailHeight.toPx() }
        
        val bodyHeight = size.height - thP
        
        val path = Path().apply {
            // Main Bubble (Rounded Rect)
            addRoundRect(RoundRect(0f, 0f, size.width, bodyHeight, CornerRadius(radiusP)))
            
            // Triangle Tail at bottom center
            val cx = size.width / 2f
            moveTo(cx - twP / 2f, bodyHeight)
            lineTo(cx + twP / 2f, bodyHeight)
            lineTo(cx, size.height)
            close()
        }
        return Outline.Generic(path)
    }
}

@Composable
fun CalloutBubble(
    items: List<UnifiedItem>,
    screenPosition: Offset,
    maxPopupItems: Int,
    popupFontSize: Int,
    onClose: () -> Unit,
    onDeleteTodo: (UnifiedItem.Todo) -> Unit,
    onDeleteLog: (UnifiedItem.History) -> Unit,
    onSelectLog: (kr.alltodo.data.TodoItem) -> Unit,
    onCreateTodo: (UnifiedItem) -> Unit,
    mapProvider: kr.alltodo.ui.MapProvider = kr.alltodo.ui.MapProvider.Naver
) {
    val fontSize = when (popupFontSize) {
        0 -> 12.sp
        1 -> 15.sp
        2 -> 18.sp
        else -> 15.sp
    }

    val bubbleWidth = 280.dp // Slightly wider for better text fit
    val rowHeight = when (popupFontSize) {
        0 -> 40.dp
        1 -> 48.dp
        2 -> 56.dp
        else -> 48.dp
    }
    
    val headerHeight = 44.dp
    val maxListItems = maxPopupItems.coerceAtLeast(1)
    val displayCount = items.size.coerceAtMost(maxListItems)
    val totalContentHeight = (rowHeight * displayCount) + headerHeight + 8.dp
    val tailHeight = 10.dp
    val bubbleHeight = totalContentHeight + tailHeight

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onClose)
    ) {
        Box(
            modifier = Modifier
                .offset {
                    val totalOffset = when(mapProvider) {
                        kr.alltodo.ui.MapProvider.Google -> 120.dp
                        kr.alltodo.ui.MapProvider.Kakao, kr.alltodo.ui.MapProvider.Naver -> 130.dp
                    }.toPx()

                    IntOffset(
                        x = (screenPosition.x - (bubbleWidth.toPx() / 2)).toInt(),
                        y = (screenPosition.y - bubbleHeight.toPx() - totalOffset).toInt()
                    )
                }
                .width(bubbleWidth)
                .height(bubbleHeight)
                .shadow(elevation = 8.dp, shape = BubbleShape())
                .clickable(enabled = false) { }
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = BubbleShape(),
                color = Color.White.copy(alpha = 0.95f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f))
            ) {
                Column {
                    // [Header] Center Close Button (iOS Style)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(headerHeight)
                            .clickable(onClick = onClose),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "닫기",
                            tint = Color.Black.copy(alpha = 0.4f),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f, fill = false)
                    ) {
                        items(items.take(maxPopupItems)) { item ->
                            CalloutRow(
                                item = item,
                                fontSize = fontSize,
                                onDeleteTodo = onDeleteTodo,
                                onDeleteLog = onDeleteLog,
                                onSelectLog = onSelectLog,
                                onCreateTodo = onCreateTodo
                            )
                            if (item != items.take(maxPopupItems).last()) {
                                Divider(color = Color.Black.copy(alpha = 0.1f), modifier = Modifier.padding(horizontal = 12.dp))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp)) // Extra padding for the tail area
                }
            }
        }
    }
}

@Composable
fun CalloutRow(
    item: UnifiedItem,
    fontSize: androidx.compose.ui.unit.TextUnit,
    onDeleteTodo: (UnifiedItem.Todo) -> Unit,
    onDeleteLog: (UnifiedItem.History) -> Unit,
    onSelectLog: (kr.alltodo.data.TodoItem) -> Unit,
    onCreateTodo: (UnifiedItem) -> Unit
) {
    val dateFormat = SimpleDateFormat("MM.dd", Locale.getDefault())
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // [Col 1] Map Icon
        Box(modifier = Modifier.width(36.dp), contentAlignment = Alignment.CenterStart) {
            val history = (item as? UnifiedItem.History)
            val hasPath = history?.item?.no_of_path ?: 0 > 0
            
            IconButton(
                onClick = { if (hasPath) onSelectLog(history!!.item) },
                enabled = hasPath,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Map,
                    contentDescription = "경로 보기",
                    tint = if (hasPath) Color(0xFF28CD41) else Color.Gray.copy(alpha = 0.3f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // [Col 2] Content (Centered-ish but aligned to icon)
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable { onCreateTodo(item) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (item) {
                is UnifiedItem.Todo, is UnifiedItem.History -> {
                    val timestamp = if (item is UnifiedItem.Todo) item.item.created_at else (item as UnifiedItem.History).item.begin_time ?: (item as UnifiedItem.History).item.created_at
                    val name = if (item is UnifiedItem.Todo) item.item.todo_name else (item as UnifiedItem.History).item.todo_name ?: "히스토리"
                    
                    val dateStr = dateFormat.format(Date(timestamp))
                    val timeStr = timeFormat.format(Date(timestamp))
                    
                    // Path Count
                    if (item is UnifiedItem.History && item.item.no_of_path > 0) {
                        Text(
                            text = "(${item.item.no_of_path})",
                            color = Color(0xFF28CD41),
                            fontSize = (fontSize.value - 2).sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(32.dp)
                        )
                    }

                    Text(text = dateStr, color = Color.Gray, fontSize = (fontSize.value - 1).sp)
                    Spacer(Modifier.width(6.dp))
                    Text(text = timeStr, color = Color.Black, fontSize = fontSize, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Text(text = name, color = Color.Black, fontSize = fontSize, maxLines = 1)
                }
                is UnifiedItem.CurrentLocation -> {
                    Icon(Icons.Default.Person, null, tint = Color.Red, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = timeFormat.format(Date()),
                        color = Color.Red,
                        fontSize = fontSize,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("현재 위치", color = Color.Red, fontSize = fontSize)
                }
            }
        }

        // [Col 3] Delete Icon
        IconButton(
            onClick = {
                when (item) {
                    is UnifiedItem.Todo -> onDeleteTodo(item)
                    is UnifiedItem.History -> onDeleteLog(item)
                    else -> {}
                }
            },
            modifier = Modifier.size(36.dp)
        ) {
            if (item !is UnifiedItem.CurrentLocation) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "삭제",
                    tint = Color.Red.copy(alpha = 0.6f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
