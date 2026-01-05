package kr.alltodo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kr.alltodo.ui.MapFeatureViewModel
import kr.alltodo.ui.TodoViewModel
import kr.alltodo.ui.theme.AppColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTodoSheet(
    todoViewModel: TodoViewModel,
    mapViewModel: MapFeatureViewModel,
    isDark: Boolean,
    onDismiss: () -> Unit
) {
    val showSheet by mapViewModel.showAllTodoSheet.collectAsState()
    val mainTab by mapViewModel.mainSheetTab.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    
    // Pager State
    val pagerState = rememberPagerState(pageCount = { 2 })
    
    // Sync Pager with ViewModel Tab
    LaunchedEffect(mainTab) {
        if (pagerState.currentPage != mainTab) {
            pagerState.animateScrollToPage(mainTab)
        }
    }
    
    // Sync ViewModel Tab with Pager
    LaunchedEffect(pagerState.currentPage) {
        if (mainTab != pagerState.currentPage) {
            mapViewModel.setMainSheetTab(pagerState.currentPage)
        }
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            dragHandle = { BottomSheetDefaults.DragHandle() },
            containerColor = AppColors.TodoList.background(isDark),
            modifier = Modifier.fillMaxHeight(0.95f)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // 1. Base Layer: Tabs (List / Calendar)
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = true // Enable swipe between tabs
                ) { page ->
                    when (page) {
                        0 -> TodoListLayer(
                            viewModel = todoViewModel,
                            mapViewModel = mapViewModel,
                            onPathClick = { item ->
                                mapViewModel.setViewingPathTodo(
                                    when(item) {
                                        is kr.alltodo.ui.UnifiedItem.Todo -> item.item
                                        is kr.alltodo.ui.UnifiedItem.History -> item.item
                                        else -> null
                                    }
                                )
                                todoViewModel.fetchPathForHistory(mapViewModel.viewingPathTodo.value ?: return@TodoListLayer)
                            },
                            onEditTodo = { item ->
                                mapViewModel.setSelectedItem(
                                    when(item) {
                                        is kr.alltodo.ui.UnifiedItem.Todo -> item.item
                                        is kr.alltodo.ui.UnifiedItem.History -> item.item
                                        else -> null
                                    }
                                )
                            },
                            onAddClick = {
                                // Handled in MainScreen or via mapViewModel
                                mapViewModel.startCreatingTodo(37.5, 127.0) // Placeholder coords
                            },
                            onDismiss = { mapViewModel.setShowAllTodoSheet(false) },
                            isDark = isDark
                        )
                        1 -> CalendarLayer(
                            viewModel = todoViewModel,
                            mapViewModel = mapViewModel,
                            onPathClick = { item ->
                                mapViewModel.setViewingPathTodo(item)
                                todoViewModel.fetchPathForHistory(item)
                            },
                            onEditClick = { item ->
                                mapViewModel.setSelectedItem(item)
                            },
                            onDismiss = { mapViewModel.setShowAllTodoSheet(false) },
                            isDark = isDark
                        )
                    }
                }

                // 2. Overlay Layer: Internal Detail / Edit
                val viewingPathTodo by mapViewModel.viewingPathTodo.collectAsState()
                val selectedItem by mapViewModel.selectedItem.collectAsState()
                val selectedHistoryPath by todoViewModel.selectedHistoryPath.collectAsState()

                val isCreatingTodo by mapViewModel.isCreatingTodo.collectAsState()
                val creatingLocation by mapViewModel.creatingLocation.collectAsState()
                val initialTodoTitle by mapViewModel.initialTodoTitle.collectAsState()
                val initialTodoName by mapViewModel.initialTodoName.collectAsState()
                val currentProvider by mapViewModel.currentProvider.collectAsState()

                // Path Viewer Overlay
                if (viewingPathTodo != null) {
                    PathViewer(
                        todo = viewingPathTodo!!,
                        pathData = selectedHistoryPath,
                        mapProvider = currentProvider,
                        onClose = { mapViewModel.setViewingPathTodo(null) }
                    )
                }

                // Edit/Create Todo Overlay
                if (isCreatingTodo || selectedItem != null) {
                    CreateTodoLayer(
                        title = initialTodoTitle,
                        initialName = initialTodoName,
                        recentNames = emptyList(), // Can fetch from todoViewModel if needed
                        recentMemos = emptyList(),
                        onRegister = { name, person, date, time, memo ->
                            if (selectedItem != null) {
                                // Logic for updating existing item
                                // todoViewModel.updateTodo(...) 
                                mapViewModel.setSelectedItem(null)
                            } else {
                                todoViewModel.addTodo(
                                    text = name,
                                    latitude = creatingLocation?.latitude ?: 0.0,
                                    longitude = creatingLocation?.longitude ?: 0.0,
                                    person = person,
                                    date = date,
                                    time = time,
                                    memo = memo
                                )
                            }
                            mapViewModel.cancelCreatingTodo()
                        },
                        onCancel = { 
                            mapViewModel.cancelCreatingTodo()
                            mapViewModel.setSelectedItem(null)
                        },
                        isDark = isDark
                    )
                }
            }
        }
    }
}

// Helper extension for Color opacity
fun Color.opacity(alpha: Float): Color = this.copy(alpha = alpha)
