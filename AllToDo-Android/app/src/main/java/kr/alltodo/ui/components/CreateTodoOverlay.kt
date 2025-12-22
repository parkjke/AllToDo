package kr.alltodo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextStyle
import kr.alltodo.ui.theme.AllToDoGreen

@Composable
fun CreateTodoLayer(
    modifier: Modifier = Modifier,
    recentNames: List<String> = emptyList(),
    recentMemos: List<String> = emptyList(),
    defaultName: String = "요기",
    initialName: String = "",
    title: String = "할 일 만들기",
    onRegister: (name: String, person: String, date: String, time: String, memo: String) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    var todoName by remember { mutableStateOf(initialName) }
    var person by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("") }
    var memo by remember { mutableStateOf("") }

    var activeInputMode by remember { mutableStateOf(InputMode.None) }

    val gray7 = Color(0xFF616161)

    Box(modifier = modifier.fillMaxSize()) {
        // Main Bottom Sheet
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.7f) // Increased height for visibility
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(Color(0xFFE0E0E0)) // Gray 2
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF333333) // Gray 8
                    )
                    Row {
                        IconButton(onClick = { 
                            val finalName = if (todoName.isBlank()) defaultName else todoName
                            
                            // [NEW] Default Date/Time to Current if blank
                            val finalDate = if (date.isBlank()) {
                                val cal = java.util.Calendar.getInstance()
                                String.format("%d.%02d.%02d", cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.DAY_OF_MONTH))
                            } else date
                            
                            val finalTime = if (time.isBlank()) {
                                val cal = java.util.Calendar.getInstance()
                                String.format("%02d:%02d", cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
                            } else time

                            onRegister(finalName, person, finalDate, finalTime, memo) 
                        }) {
                            Icon(Icons.Default.Check, contentDescription = "Register", tint = gray7)
                        }
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel", tint = gray7)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Form
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    Spacer(modifier = Modifier.height(24.dp))
                    InputField(
                        label = "할 일 이름",
                        value = if (todoName.isEmpty()) "할 일 이름을 넣어주세요 (미입력 시 '$defaultName')" else todoName,
                        labelColor = gray7,
                        isPlaceholder = todoName.isEmpty(),
                        onClick = { activeInputMode = InputMode.Name }
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    InputField(
                        label = "같이 할 사람이 있나요",
                        value = if (person.isEmpty()) "연락처에서 선택" else person,
                        labelColor = gray7,
                        isPlaceholder = person.isEmpty(),
                        onClick = { activeInputMode = InputMode.Person }
                    )
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Box(modifier = Modifier.weight(1f)) {
                            InputField(
                                label = "날짜",
                                value = if (date.isEmpty()) "날짜" else date,
                                labelColor = gray7,
                                isPlaceholder = date.isEmpty(),
                                onClick = {
                                    val calendar = java.util.Calendar.getInstance()
                                    android.app.DatePickerDialog(
                                        context,
                                        { _, y, m, d -> date = String.format("%d.%02d.%02d", y, m + 1, d) },
                                        calendar.get(java.util.Calendar.YEAR),
                                        calendar.get(java.util.Calendar.MONTH),
                                        calendar.get(java.util.Calendar.DAY_OF_MONTH)
                                    ).show()
                                }
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            InputField(
                                label = "시간",
                                value = if (time.isEmpty()) "시간" else time,
                                labelColor = gray7,
                                isPlaceholder = time.isEmpty(),
                                onClick = {
                                    val calendar = java.util.Calendar.getInstance()
                                    android.app.TimePickerDialog(
                                        context,
                                        { _, h, min -> time = String.format("%02d:%02d", h, min) },
                                        calendar.get(java.util.Calendar.HOUR_OF_DAY),
                                        calendar.get(java.util.Calendar.MINUTE),
                                        true
                                    ).show()
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    InputField(
                        label = "메모",
                        value = if (memo.isEmpty()) "기억을 위한 메모" else memo,
                        labelColor = gray7,
                        isPlaceholder = memo.isEmpty(),
                        onClick = { activeInputMode = InputMode.Memo }
                    )
                }
            }
        }

        // Sub-views overlays (Now truly Full Screen)
        when (activeInputMode) {
            InputMode.Name -> TodoNameInputOverlay(
                initialValue = todoName,
                recents = recentNames,
                onDone = { todoName = it; activeInputMode = InputMode.None },
                onCancel = { activeInputMode = InputMode.None }
            )
            InputMode.Person -> ContactPickerOverlay(
                onDone = { person = it; activeInputMode = InputMode.None },
                onCancel = { activeInputMode = InputMode.None }
            )
            InputMode.Memo -> MemoInputOverlay(
                initialValue = memo,
                recents = recentMemos,
                onDone = { memo = it; activeInputMode = InputMode.None },
                onCancel = { activeInputMode = InputMode.None }
            )
            InputMode.None -> {}
        }
    }
}

@Composable
fun InputField(
    label: String,
    value: String,
    labelColor: Color,
    isPlaceholder: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable { onClick() }
    ) {
        Text(text = label, fontSize = 12.sp, color = labelColor, fontWeight = FontWeight.Medium)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .background(Color.White, RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            Text(
                text = value,
                fontSize = 16.sp,
                color = if (isPlaceholder) Color.Gray else Color(0xFF212121) // Gray 9
            )
        }
    }
}

enum class InputMode {
    None, Name, Person, Memo
}

@Composable
fun TodoNameInputOverlay(
    initialValue: String,
    recents: List<String>,
    onDone: (String) -> Unit,
    onCancel: () -> Unit
) {
    val gray7 = Color(0xFF616161)
    var text by remember { mutableStateOf(initialValue) }
    Box(Modifier.fillMaxSize().background(Color.White)) {
        Column(Modifier.padding(16.dp)) {
            Spacer(modifier = Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = onCancel) { Icon(Icons.Default.Close, null, tint = gray7) }
                Text("할 일 이름", fontWeight = FontWeight.Bold, color = gray7, fontSize = 18.sp)
                IconButton(onClick = { onDone(text) }) { Icon(Icons.Default.Check, null, tint = gray7) }
            }
            Spacer(modifier = Modifier.height(16.dp))
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                placeholder = { Text("할 일 이름을 넣어주세요", style = TextStyle(color = gray7)) },
                shape = RoundedCornerShape(12.dp),
                textStyle = TextStyle(color = Color(0xFF212121))
            )
            
            if (recents.isNotEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                Text("최근 할 일", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                Spacer(modifier = Modifier.height(8.dp))
                recents.forEach { name ->
                    Text(
                        text = name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onDone(name) }
                            .padding(vertical = 12.dp),
                        fontSize = 16.sp,
                        color = Color(0xFF212121) // Gray 9
                    )
                    Divider(color = Color(0xFFEEEEEE))
                }
            }
        }
    }
}

@Composable
fun ContactPickerOverlay(onDone: (String) -> Unit, onCancel: () -> Unit) {
    val gray7 = Color(0xFF616161)
    var search by remember { mutableStateOf("") }
    Box(Modifier.fillMaxSize().background(Color.White)) {
        Column(Modifier.padding(16.dp)) {
            Spacer(modifier = Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = onCancel) { Icon(Icons.Default.Close, null, tint = gray7) }
                Text("연락처 검색", fontWeight = FontWeight.Bold, color = gray7, fontSize = 18.sp)
                IconButton(onClick = { onDone("Sample Person") }) { Icon(Icons.Default.Check, null, tint = gray7) }
            }
            Spacer(modifier = Modifier.height(16.dp))
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                placeholder = { Text("연락처에서 선택", style = TextStyle(color = gray7)) },
                shape = RoundedCornerShape(12.dp),
                leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.Gray) },
                textStyle = TextStyle(color = Color(0xFF212121))
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text("연락처 리스트", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
            // list... (Sample UI)
            Text("데이터를 불러오는 중...", Modifier.padding(top = 16.dp), color = Color.Gray)
        }
    }
}

@Composable
fun MemoInputOverlay(
    initialValue: String,
    recents: List<String>,
    onDone: (String) -> Unit,
    onCancel: () -> Unit
) {
    val gray7 = Color(0xFF616161)
    var text by remember { mutableStateOf(initialValue) }
    Box(Modifier.fillMaxSize().background(Color.White)) {
        Column(Modifier.padding(16.dp)) {
            Spacer(modifier = Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = onCancel) { Icon(Icons.Default.Close, null, tint = gray7) }
                Text("메모 입력", fontWeight = FontWeight.Bold, color = gray7, fontSize = 18.sp)
                IconButton(onClick = { onDone(text) }) { Icon(Icons.Default.Check, null, tint = gray7) }
            }
            Spacer(modifier = Modifier.height(16.dp))
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().height(200.dp).focusRequester(focusRequester),
                placeholder = { Text("기억을 위한 메모", style = TextStyle(color = gray7)) },
                shape = RoundedCornerShape(12.dp),
                textStyle = TextStyle(color = Color(0xFF212121))
            )
            
            if (recents.isNotEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                Text("이전에 썼던 메모", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                Spacer(modifier = Modifier.height(8.dp))
                recents.forEach { memo ->
                    Text(
                        text = memo,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onDone(memo) }
                            .padding(vertical = 12.dp),
                        fontSize = 14.sp,
                        color = Color(0xFF212121), // Gray 9
                        maxLines = 2
                    )
                    Divider(color = Color(0xFFEEEEEE))
                }
            }
        }
    }
}
