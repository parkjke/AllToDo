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
    onCreateTodo: (UnifiedItem) -> Unit,
    mapProvider: kr.alltodo.ui.MapProvider = kr.alltodo.ui.MapProvider.Naver
) {
    val fontSize = when (popupFontSize) {
        0 -> 12.sp
        1 -> 15.sp
        2 -> 18.sp
        else -> 15.sp
    }

    val bubbleWidth = 260.dp
    val rowHeight = when (popupFontSize) {
        0 -> 38.dp
        1 -> 42.dp
        2 -> 52.dp
        else -> 42.dp
    }
    
    val headerHeight = 40.dp
    val maxListItems = maxPopupItems.coerceAtLeast(1)
    val displayCount = items.size.coerceAtMost(maxListItems)
    val bubbleHeight = (rowHeight * displayCount) + headerHeight + 8.dp

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
                .height(bubbleHeight + 10.dp)
                .clickable(enabled = false) { }
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(bubbleHeight),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    shadowElevation = 8.dp
                ) {

                    Column {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(headerHeight)
                        ) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .fillMaxHeight()
                                    .width(60.dp)
                                    .clickable(onClick = onClose),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "닫기",
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    modifier = Modifier.size(24.dp)
                                )

                            }
                        }

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

                val isDark = androidx.compose.foundation.isSystemInDarkTheme()
                val bubbleColor = if (isDark) Color(0xFF333333).copy(alpha = 0.95f) else Color(0xFFE0E0E0).copy(alpha = 0.95f)

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
                    drawPath(path, color = bubbleColor, style = Fill)
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
            val hasPath = (item as? UnifiedItem.History)?.item?.no_of_path ?: 0 > 0
            
            IconButton(
                onClick = { if (hasPath) onSelectLog((item as UnifiedItem.History).item) },
                enabled = hasPath,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Map,
                    contentDescription = "경로 보기",
                    tint = if (hasPath) Color(0xFF1B5E20) else Color(0xFF333333).copy(alpha = 0.1f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

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
                        Text(text = dateStr, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), fontSize = fontSize)
                        Spacer(Modifier.width(4.dp))
                        Text(text = timeStr, color = MaterialTheme.colorScheme.onSurface, fontSize = fontSize, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        Text(text = shortName, color = MaterialTheme.colorScheme.onSurface, fontSize = fontSize)
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
                    contentDescription = "나",
                    tint = Color.Red,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "삭제",
                    tint = Color.Red,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
