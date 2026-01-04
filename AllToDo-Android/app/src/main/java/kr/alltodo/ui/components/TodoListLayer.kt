package kr.alltodo.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
    onEditTodo: (UnifiedItem) -> Unit,
    onAddClick: () -> Unit,
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
        // [FIX] Full Screen Background (Non-Transparent)
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(Color.White) // Cover everything
        ) {
            // 1. Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 40.dp, bottom = 16.dp), // Account for TopLeftWidget area
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // [+] Add Todo Button
                    IconButton(onClick = onAddClick) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "추가",
                            tint = Gray8,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Sort Toggle
                    IconButton(onClick = { viewModel.setSortByTime(!sortByTime) }) {
                        Icon(
                            imageVector = if (sortByTime) Icons.Default.AccessTime else Icons.Default.Palette,
                            contentDescription = "정렬",
                            tint = Gray8,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))

                    // [FIX] Filter Buttons (No circles, just rounded check boxes)
                    FilterIconButton(
                        color = AllToDoBlue,
                        isSelected = filterServer,
                        onClick = { viewModel.toggleFilterServer() }
                    )
                    FilterIconButton(
                        color = AllToDoGreen,
                        isSelected = filterTodo,
                        onClick = { viewModel.toggleFilterTodo() }
                    )
                    FilterIconButton(
                        color = AllToDoRed,
                        isSelected = filterHistory,
                        onClick = { viewModel.toggleFilterHistory() }
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // [FIX] Dynamic Calendar Icon
                    IconButton(onClick = { viewModel.toggleCalendar() }) {
                        TodayCalendarIcon(tint = Gray8)
                    }

                    // Close
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "닫기",
                            tint = Gray8,
                            modifier = Modifier.size(36.dp)
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
                        },
                        onNameClick = { onEditTodo(item) }
                    )
                }
            }
        }
    }
}

@Composable
fun FilterIconButton(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    // [FIX] Rounded Rectangle Style without circle icon
    Box(
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) color else color.copy(alpha = 0.2f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check, // Sharp checkmark
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
