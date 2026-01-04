package kr.alltodo.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.alltodo.ui.TodoViewModel
import kr.alltodo.ui.UnifiedItem
import kr.alltodo.ui.theme.*

@Composable
fun TodoListLayer(
    viewModel: TodoViewModel,
    onPathClick: (UnifiedItem) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isVisible by viewModel.isListLayerVisible.collectAsState()
    val sortByTime by viewModel.sortByTime.collectAsState()
    val filterServer by viewModel.filterServer.collectAsState()
    val filterTodo by viewModel.filterTodo.collectAsState()
    val filterHistory by viewModel.filterHistory.collectAsState()
    val displayItems by viewModel.displayItems.collectAsState()

    AnimatedVisibility(
        visible = isVisible,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(Color.White.copy(alpha = 0.95f))
        ) {
            // 1. Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Sort Toggle
                    IconButton(onClick = { viewModel.setSortByTime(!sortByTime) }) {
                        Icon(
                            imageVector = if (sortByTime) Icons.Default.AccessTime else Icons.Default.Palette,
                            contentDescription = "정렬",
                            tint = Gray8
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))

                    // Filter Checkboxes
                    FilterIconButton(
                        icon = Icons.Default.CheckCircle,
                        color = AllToDoBlue,
                        isSelected = filterServer,
                        onClick = { viewModel.toggleFilterServer() }
                    )
                    FilterIconButton(
                        icon = Icons.Default.CheckCircle,
                        color = AllToDoGreen,
                        isSelected = filterTodo,
                        onClick = { viewModel.toggleFilterTodo() }
                    )
                    FilterIconButton(
                        icon = Icons.Default.CheckCircle,
                        color = AllToDoRed,
                        isSelected = filterHistory,
                        onClick = { viewModel.toggleFilterHistory() }
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Calendar
                    IconButton(onClick = { /* Calendar logic later */ }) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = "캘린더",
                            tint = Gray8
                        )
                    }

                    // Close
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "닫기",
                            tint = Gray8
                        )
                    }
                }
            }

            // 2. List Content
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(displayItems.filter { it !is UnifiedItem.CurrentLocation }) { item ->
                    TodoItemCard(
                        item = item,
                        onPathClick = { onPathClick(item) },
                        onDeleteClick = {
                            when (item) {
                                is UnifiedItem.Todo -> viewModel.deleteTodo(item.item)
                                is UnifiedItem.History -> viewModel.deleteTodo(item.item)
                                else -> {}
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun FilterIconButton(
    icon: ImageVector,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isSelected) color else color.copy(alpha = 0.3f)
        )
    }
}
