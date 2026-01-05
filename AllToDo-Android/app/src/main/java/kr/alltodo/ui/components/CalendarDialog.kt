package kr.alltodo.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kr.alltodo.ui.theme.AppColors
import kr.alltodo.ui.theme.*
import kr.alltodo.data.TodoItem
import kr.alltodo.ui.UnifiedItem
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.*

import kr.alltodo.ui.TodoViewModel
import java.time.Instant
import java.time.ZoneId

/**
 * AllToDo 커스텀 캘린더 다이얼로그
 */
@Composable
fun CalendarDialog(
    viewModel: kr.alltodo.ui.TodoViewModel,
    onDismissRequest: () -> Unit,
    onPathClick: (TodoItem) -> Unit,
    onEditClick: (TodoItem) -> Unit,
    isDark: Boolean = isSystemInDarkTheme()
) {
    var isLoading by remember { mutableStateOf(true) }
    var currentMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }

    // 실제 데이터 구독
    val todoItems by viewModel.todoItems.collectAsState()

    // 로딩 처리 애니메이션 (딜레이 동그라미)
    LaunchedEffect(Unit) {
        delay(800) // 가상의 데이터 로딩 시간
        isLoading = false
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.Calendar.background(isDark))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = AppColors.Calendar.todayBorder(isDark))
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // 1. 헤더 (연도/월 네비게이션 좌측, 닫기 우측)
                    CalendarHeader(
                        currentMonth = currentMonth,
                        onMonthChange = { currentMonth = it },
                        onDismiss = onDismissRequest,
                        isDark = isDark
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 2. 캘린더 그리드 (7열)
                    CalendarGrid(
                        currentMonth = currentMonth,
                        todoItems = todoItems,
                        selectedDate = selectedDate,
                        onDateSelect = { selectedDate = it },
                        isDark = isDark,
                        modifier = Modifier.weight(1f)
                    )

                    Divider(
                        color = AppColors.Calendar.divider(isDark),
                        thickness = 1.dp
                    )

                    // 3. 하단 할 일 목록 영역
                    TodoSummaryArea(
                        viewModel = viewModel,
                        todoItems = todoItems,
                        selectedDate = selectedDate,
                        onPathClick = onPathClick,
                        onEditClick = onEditClick,
                        isDark = isDark,
                        modifier = Modifier.height(240.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CalendarHeader(
    currentMonth: YearMonth,
    onMonthChange: (YearMonth) -> Unit,
    onDismiss: () -> Unit,
    isDark: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 왼쪽: 연도 및 월 네비게이션 통합 배치
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 연도 네비게이션
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onMonthChange(currentMonth.minusYears(1)) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.KeyboardArrowLeft, contentDescription = null, tint = AppColors.Calendar.headerText(isDark))
                }
                Text(
                    text = "${currentMonth.year}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = AppColors.Calendar.headerText(isDark)
                )
                IconButton(onClick = { onMonthChange(currentMonth.plusYears(1)) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = AppColors.Calendar.headerText(isDark))
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // 월 네비게이션 (큰 글씨)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onMonthChange(currentMonth.minusMonths(1)) }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.KeyboardArrowLeft, contentDescription = null, tint = AppColors.Calendar.headerText(isDark), modifier = Modifier.size(36.dp))
                }
                Text(
                    text = "${currentMonth.monthValue}월",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.Calendar.headerText(isDark)
                )
                IconButton(onClick = { onMonthChange(currentMonth.plusMonths(1)) }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = AppColors.Calendar.headerText(isDark), modifier = Modifier.size(36.dp))
                }
            }
        }

        // 오른쪽: 닫기 버튼
        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "닫기",
                tint = AppColors.Calendar.headerText(isDark),
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
fun CalendarGrid(
    currentMonth: YearMonth,
    todoItems: List<TodoItem>,
    selectedDate: LocalDate,
    onDateSelect: (LocalDate) -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val daysInMonth = currentMonth.lengthOfMonth()
    val firstDayOfMonth = currentMonth.atDay(1).dayOfWeek.value % 7 // 0(Sun) to 6(Sat)
    val days = (1..daysInMonth).toList()
    
    // 요일 헤더 (일~토)
    val weekDays = listOf("일", "월", "화", "수", "목", "금", "토")

    // 날짜별 핀 통계 사전 계산
    val statsByDate = remember(todoItems, currentMonth) {
        todoItems.filter { 
            it.todo_id != "CURRENT_LOCATION" && it.int_lat != null && it.int_long != null 
        }.filter {
            val ts = if (it.type == "00") it.begin_time ?: it.created_at else it.created_at
            val date = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalDate()
            date.year == currentMonth.year && date.month == currentMonth.month
        }.groupBy {
            val ts = if (it.type == "00") it.begin_time ?: it.created_at else it.created_at
            Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalDate()
        }.mapValues { (_, items) ->
            Triple(
               items.count { it.source != "local" }, // Blue
               items.count { it.source == "local" && it.type == "10" }, // Green
               items.count { it.type == "00" } // Red
            )
        }
    }

    Column(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            weekDays.forEachIndexed { index, day ->
                val textColor = when (index) {
                    0 -> Color.Red // 일요일
                    6 -> Color(0xFF1F8FFF) // 토요일
                    else -> AppColors.Calendar.secondaryText(isDark)
                }
                Text(
                    text = day,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
            }
        }
        
        Spacer(modifier = Modifier.height(4.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(0.dp)
        ) {
            items(firstDayOfMonth) {
                Box(modifier = Modifier.aspectRatio(0.7f).border(0.5.dp, AppColors.Calendar.divider(isDark)))
            }

            items(days) { day ->
                val date = currentMonth.atDay(day)
                val isToday = date == LocalDate.now()
                val isSelected = date == selectedDate
                val stats = statsByDate[date] ?: Triple(0, 0, 0)

                DayCell(
                    day = day,
                    stats = stats,
                    isToday = isToday,
                    isSelected = isSelected,
                    isDark = isDark,
                    onClick = { onDateSelect(date) }
                )
            }
        }
    }
}

@Composable
fun DayCell(
    day: Int,
    stats: Triple<Int, Int, Int>,
    isToday: Boolean,
    isSelected: Boolean,
    isDark: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(0.7f)
            .border(0.5.dp, AppColors.Calendar.divider(isDark))
            .background(if (isSelected) AppColors.Calendar.selectedBackground(isDark) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(2.dp)
    ) {
        if (isToday) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(1.5.dp, AppColors.Calendar.todayBorder(isDark), RoundedCornerShape(4.dp))
            )
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "$day",
                fontSize = 14.sp,
                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                color = AppColors.Calendar.primaryText(isDark),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Start
            )

            // 핀 데이터 표시 (0건인 색상은 미표시)
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (stats.first > 0) PinInfo(color = AppColors.Calendar.pinBlue, count = stats.first, isDark = isDark)
                if (stats.second > 0) PinInfo(color = AppColors.Calendar.pinGreen, count = stats.second, isDark = isDark)
                if (stats.third > 0) PinInfo(color = AppColors.Calendar.pinRed, count = stats.third, isDark = isDark)
                
                // 가독성을 위한 빈 공간 (최대 3줄 확보)
                repeat(3 - (listOf(stats.first, stats.second, stats.third).count { it > 0 })) {
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
fun PinInfo(color: Color, count: Int, isDark: Boolean = isSystemInDarkTheme()) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .background(color, RoundedCornerShape(1.5.dp))
        )
        Text(
            text = "$count",
            fontSize = 10.sp,
            color = AppColors.Calendar.primaryText(isDark).copy(alpha = 0.9f),
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun TodoSummaryArea(
    viewModel: kr.alltodo.ui.TodoViewModel,
    todoItems: List<TodoItem>,
    selectedDate: LocalDate,
    onPathClick: (TodoItem) -> Unit,
    onEditClick: (TodoItem) -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    // 필터 상태 유지 (TodoListLayer와 동일한 로직 권장되나, 요청에 따라 내부 관리 가능)
    var filterServer by remember { mutableStateOf(true) }
    var filterTodo by remember { mutableStateOf(true) }
    var filterHistory by remember { mutableStateOf(true) }

    // 날짜 및 필터 적용 데이터
    val displayItems = remember(todoItems, selectedDate, filterServer, filterTodo, filterHistory) {
        todoItems.filter { it.todo_id != "CURRENT_LOCATION" }.filter {
            val ts = if (it.type == "00") it.begin_time ?: it.created_at else it.created_at
            val date = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalDate()
            date == selectedDate
        }.filter {
            when {
                it.type == "00" -> filterHistory
                it.source == "server" -> filterServer
                else -> filterTodo
            }
        }.map { if (it.type == "00") kr.alltodo.ui.UnifiedItem.History(it) else kr.alltodo.ui.UnifiedItem.Todo(it) }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${selectedDate.format(DateTimeFormatter.ofPattern("M월 d일"))} 할 일",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.Calendar.headerText(isDark)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CalendarFilterButton(color = AllToDoBlue, isSelected = filterServer, onClick = { filterServer = !filterServer })
                CalendarFilterButton(color = AllToDoGreen, isSelected = filterTodo, onClick = { filterTodo = !filterTodo })
                CalendarFilterButton(color = AllToDoRed, isSelected = filterHistory, onClick = { filterHistory = !filterHistory })
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(displayItems) { item ->
                val innerItem = when(item) {
                    is kr.alltodo.ui.UnifiedItem.Todo -> item.item
                    is kr.alltodo.ui.UnifiedItem.History -> item.item
                    else -> null
                }
                
                TodoItemCard(
                    item = item,
                    onPathClick = { innerItem?.let { onPathClick(it) } },
                    onDeleteClick = { innerItem?.let { viewModel.deleteTodo(it) } },
                    onNameClick = { innerItem?.let { onEditClick(it) } },
                    isDark = isDark
                )
            }
            
            if (displayItems.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("할 일이 없습니다.", color = AppColors.Calendar.secondaryText(isDark))
                    }
                }
            }
        }
    }
}

@Composable
fun CalendarFilterButton(
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) color else color.copy(alpha = 0.2f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
