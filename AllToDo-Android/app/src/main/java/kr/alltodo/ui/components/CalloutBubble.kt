package kr.alltodo.ui.components

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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.alltodo.ui.UnifiedItem
import kr.alltodo.ui.theme.AllToDoGreen
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.foundation.Canvas

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
    onCreateTodo: (UnifiedItem) -> Unit
) {
    val fontSize = when (popupFontSize) {
        0 -> 12.sp
        1 -> 15.sp
        2 -> 18.sp
        else -> 15.sp
    }

    val bubbleWidth = 260.dp
    // iOS height calculation logic roughly translated to dp
    val rowHeight = when (popupFontSize) {
        0 -> 38.dp
        1 -> 42.dp
        2 -> 52.dp
        else -> 42.dp
    }
    
    val headerHeight = 40.dp
    val maxListItems = maxPopupItems.coerceAtLeast(1)
    val displayCount = items.size.coerceAtMost(maxListItems)
    val bubbleHeight = (rowHeight * displayCount) + headerHeight + 8.dp // 8dp padding

    // Position calculation (iOS style)
    // We want the tip of the triangle to be at screenPosition.
    // The bubble is centered horizontally on screenPosition.x
    // The bubble is placed above screenPosition.y
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onClose) // Dismiss on background click
    ) {
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = (screenPosition.x - (bubbleWidth.toPx() / 2)).toInt(),
                        y = (screenPosition.y - bubbleHeight.toPx() - 10.dp.toPx()).toInt() // 10dp for triangle
                    )
                }
                .width(bubbleWidth)
                .height(bubbleHeight + 10.dp) // Include triangle
                .clickable(enabled = false) { } // Prevent closing when clicking bubble
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Content Container
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(bubbleHeight),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE0E0E0).copy(alpha = 0.9f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column {
                        // Header
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(headerHeight)
                        ) {
                            IconButton(
                                onClick = onClose,
                                modifier = Modifier.align(Alignment.Center)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color(0xFF333333).copy(alpha = 0.6f)
                                )
                            }
                        }

                        // List
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth()
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
                                Divider(color = Color.Black.copy(alpha = 0.1f))
                            }
                        }
                    }
                }

                // Triangle
                Canvas(
                    modifier = Modifier
                        .width(20.dp)
                        .height(10.dp)
                ) {
                    val path = Path().apply {
                        moveTo(0f, 0f)
                        lineTo(size.width, 0f)
                        lineTo(size.width / 2f, size.height)
                        close()
                    }
                    drawPath(path, color = Color(0xFFE0E0E0).copy(alpha = 0.9f), style = Fill)
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
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.width(32.dp)) {
            val hasPath = (item as? UnifiedItem.History)?.item?.is_exist_location_path == true
            
            IconButton(
                onClick = { if (hasPath) onSelectLog((item as UnifiedItem.History).item) },
                enabled = hasPath,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Map,
                    contentDescription = "Show Path",
                    tint = if (hasPath) Color(0xFF1B5E20) else Color(0xFF333333).copy(alpha = 0.1f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // [Col 2] Content
        Column(
            modifier = Modifier.clickable { onCreateTodo(item) },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (item) {
                is UnifiedItem.Todo, is UnifiedItem.History -> {
                    val timestamp = if (item is UnifiedItem.Todo) item.item.created_at else (item as UnifiedItem.History).item.begin_time ?: (item as UnifiedItem.History).item.created_at
                    val name = if (item is UnifiedItem.Todo) item.item.todo_name else (item as UnifiedItem.History).item.todo_name ?: "히스토리"
                    
                    val dateStr = dateFormat.format(Date(timestamp))
                    val timeStr = timeFormat.format(Date(timestamp))
                    val shortName = name.let { if (it.length > 3) it.take(3) + "..." else it }
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = dateStr, color = Color(0xFF333333).copy(alpha = 0.6f), fontSize = fontSize)
                        Spacer(Modifier.width(4.dp))
                        Text(text = timeStr, color = Color(0xFF333333), fontSize = fontSize, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        Text(text = shortName, color = Color(0xFF333333), fontSize = fontSize)
                    }
                }
                is UnifiedItem.CurrentLocation -> {
                    Text(
                        text = timeFormat.format(Date()),
                        color = Color.Red,
                        fontSize = fontSize,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // [Col 3] Action Icon
        IconButton(
            onClick = {
                when (item) {
                    is UnifiedItem.Todo -> onDeleteTodo(item)
                    is UnifiedItem.History -> onDeleteLog(item)
                    else -> {}
                }
            },
            modifier = Modifier.size(32.dp)
        ) {
            if (item is UnifiedItem.CurrentLocation) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Me",
                    tint = Color.Red,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = Color.Red,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
