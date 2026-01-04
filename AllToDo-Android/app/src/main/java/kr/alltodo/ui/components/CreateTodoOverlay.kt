package kr.alltodo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.isSystemInDarkTheme
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
import kr.alltodo.ui.theme.*

@Composable
fun CreateTodoLayer(
    modifier: Modifier = Modifier,
    recentNames: List<String> = emptyList(),
    recentMemos: List<String> = emptyList(),
    defaultName: String = "요기",
    initialName: String = "",
    title: String = "할 일 만들기",
    isDark: Boolean = isSystemInDarkTheme(),
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

    Box(modifier = modifier.fillMaxSize()) {
        // Main Bottom Sheet
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.7f) // [REVERT] Back to 0.7f per user request
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(AppColors.TodoLayer.background(isDark))
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
                        color = AppColors.TodoLayer.headerText(isDark)
                    )
                    Row {
                        IconButton(
                            modifier = Modifier.size(48.dp),
                            onClick = { 
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
                            }
                        ) {
                            Icon(
                                Icons.Default.Check, // [FIX] Back to Default (Filled) to avoid import error
                                contentDescription = "Register", 
                                tint = AppColors.TodoLayer.primaryText(isDark),
                                modifier = Modifier.size(32.dp) // [STAY] Keep 32dp for "웅장함"
                            )
                        }
                        IconButton(
                            modifier = Modifier.size(48.dp),
                            onClick = onCancel
                        ) {
                            Icon(
                                Icons.Default.Close, // [FIX] Back to Default (Filled) to avoid import error
                                contentDescription = "Cancel", 
                                tint = AppColors.TodoLayer.primaryText(isDark),
                                modifier = Modifier.size(32.dp) // [STAY] Keep 32dp for "웅장함"
                            )
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
                    InputField(
                        label = "",
                        value = if (todoName.isEmpty()) "할 일에 이름을 지어주세요" else todoName,
                        labelColor = AppColors.TodoLayer.labelText(isDark),
                        isPlaceholder = todoName.isEmpty(),
                        isDark = isDark,
                        onClick = { activeInputMode = InputMode.Name }
                    )
                    Spacer(modifier = Modifier.height(16.dp)) // [FIX] Unified Spacing
                    InputField(
                        label = "",
                        value = if (person.isEmpty()) "알릴 사람을 주소록에서 넣을 수 있어요" else person,
                        labelColor = AppColors.TodoLayer.labelText(isDark),
                        isPlaceholder = person.isEmpty(),
                        isDark = isDark,
                        onClick = { activeInputMode = InputMode.Person }
                    )
                    Spacer(modifier = Modifier.height(16.dp)) // [FIX] Added missing spacing to match iOS
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Box(modifier = Modifier.weight(1f)) {
                            InputField(
                                label = "",
                                value = if (date.isEmpty()) "날짜" else date,
                                labelColor = AppColors.TodoLayer.labelText(isDark),
                                isPlaceholder = date.isEmpty(),
                                isDark = isDark,
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
                                label = "",
                                value = if (time.isEmpty()) "시간" else time,
                                labelColor = AppColors.TodoLayer.labelText(isDark),
                                isPlaceholder = time.isEmpty(),
                                isDark = isDark,
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
                    Spacer(modifier = Modifier.height(16.dp)) // [FIX] Unified Spacing
                    InputField(
                        label = "",
                        value = if (memo.isEmpty()) "기억을 위한 메모" else memo,
                        labelColor = AppColors.TodoLayer.labelText(isDark),
                        isPlaceholder = memo.isEmpty(),
                        isDark = isDark,
                        modifier = Modifier.heightIn(min = 250.dp),
                        onClick = { activeInputMode = InputMode.Memo }
                    )
                    Spacer(modifier = Modifier.height(40.dp))
                }
            }
        }

        // Sub-views overlays (Now truly Full Screen)
        when (activeInputMode) {
            InputMode.Name -> TodoNameInputOverlay(
                initialValue = todoName,
                recents = recentNames,
                isDark = isDark,
                onDone = { todoName = it; activeInputMode = InputMode.None },
                onCancel = { activeInputMode = InputMode.None }
            )
            InputMode.Person -> ContactPickerOverlay(
                isDark = isDark,
                onDone = { person = it; activeInputMode = InputMode.None },
                onCancel = { activeInputMode = InputMode.None }
            )
            InputMode.Memo -> MemoInputOverlay(
                initialValue = memo,
                recents = recentMemos,
                isDark = isDark,
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
    isDark: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(AppColors.TodoLayer.inputBackground(isDark), RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(12.dp)
    ) {
        Text(
            text = value,
            fontSize = 16.sp,
            color = if (isPlaceholder) AppColors.TodoLayer.placeholderText(isDark) else AppColors.TodoLayer.primaryText(isDark)
        )
    }
}

enum class InputMode {
    None, Name, Person, Memo
}

@Composable
fun TodoNameInputOverlay(
    initialValue: String,
    recents: List<String>,
    isDark: Boolean,
    onDone: (String) -> Unit,
    onCancel: () -> Unit
) {
    var text by remember { mutableStateOf(initialValue) }
    Box(Modifier.fillMaxSize().background(AppColors.TodoLayer.background(isDark))) {
        Column(Modifier.padding(16.dp)) {
            Spacer(modifier = Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = onCancel) { Icon(Icons.Default.Close, null, tint = AppColors.TodoLayer.primaryText(isDark)) }
                Text("할 일 이름", fontWeight = FontWeight.Bold, color = AppColors.TodoLayer.headerText(isDark), fontSize = 18.sp)
                IconButton(onClick = { onDone(text) }) { Icon(Icons.Default.Check, null, tint = AppColors.TodoLayer.primaryText(isDark)) }
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
                placeholder = { Text("할 일에 이름을 지어주세요", style = TextStyle(color = AppColors.TodoLayer.placeholderText(isDark))) },
                shape = RoundedCornerShape(12.dp),
                textStyle = TextStyle(color = AppColors.TodoLayer.primaryText(isDark)),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = AppColors.TodoLayer.labelText(isDark).copy(alpha = 0.3f),
                    focusedBorderColor = AppColors.TodoLayer.primaryText(isDark)
                )
            )
            
            if (recents.isNotEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                Text("최근 할 일", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AppColors.TodoLayer.labelText(isDark))
                Spacer(modifier = Modifier.height(8.dp))
                recents.forEach { name ->
                    Text(
                        text = name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onDone(name) }
                            .padding(vertical = 12.dp),
                        fontSize = 16.sp,
                        color = AppColors.TodoLayer.primaryText(isDark)
                    )
                    Divider(color = AppColors.Search.divider(isDark))
                }
            }
        }
    }
}

@Composable
fun ContactPickerOverlay(isDark: Boolean, onDone: (String) -> Unit, onCancel: () -> Unit) {
    var search by remember { mutableStateOf("") }
    Box(Modifier.fillMaxSize().background(AppColors.TodoLayer.background(isDark))) {
        Column(Modifier.padding(16.dp)) {
            Spacer(modifier = Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = onCancel) { Icon(Icons.Default.Close, null, tint = AppColors.TodoLayer.primaryText(isDark)) }
                Text("연락처 검색", fontWeight = FontWeight.Bold, color = AppColors.TodoLayer.headerText(isDark), fontSize = 18.sp)
                IconButton(onClick = { onDone("Sample Person") }) { Icon(Icons.Default.Check, null, tint = AppColors.TodoLayer.primaryText(isDark)) }
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
                placeholder = { Text("알릴 사람을 주소록에서 넣을 수 있어요", style = TextStyle(color = AppColors.TodoLayer.placeholderText(isDark))) },
                shape = RoundedCornerShape(12.dp),
                leadingIcon = { Icon(Icons.Default.Search, null, tint = AppColors.TodoLayer.labelText(isDark)) },
                textStyle = TextStyle(color = AppColors.TodoLayer.primaryText(isDark)),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = AppColors.TodoLayer.labelText(isDark).copy(alpha = 0.3f),
                    focusedBorderColor = AppColors.TodoLayer.primaryText(isDark)
                )
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text("연락처 리스트", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AppColors.TodoLayer.labelText(isDark))
            // list... (Sample UI)
            Text("데이터를 불러오는 중...", Modifier.padding(top = 16.dp), color = AppColors.TodoLayer.placeholderText(isDark))
        }
    }
}

@Composable
fun MemoInputOverlay(
    initialValue: String,
    recents: List<String>,
    isDark: Boolean,
    onDone: (String) -> Unit,
    onCancel: () -> Unit
) {
    var text by remember { mutableStateOf(initialValue) }
    Box(Modifier.fillMaxSize().background(AppColors.TodoLayer.background(isDark))) {
        Column(Modifier.padding(16.dp)) {
            Spacer(modifier = Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = onCancel) { Icon(Icons.Default.Close, null, tint = AppColors.TodoLayer.primaryText(isDark)) }
                Text("메모 입력", fontWeight = FontWeight.Bold, color = AppColors.TodoLayer.headerText(isDark), fontSize = 18.sp)
                IconButton(onClick = { onDone(text) }) { Icon(Icons.Default.Check, null, tint = AppColors.TodoLayer.primaryText(isDark)) }
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
                placeholder = { Text("기억을 위한 메모", style = TextStyle(color = AppColors.TodoLayer.placeholderText(isDark))) },
                shape = RoundedCornerShape(12.dp),
                textStyle = TextStyle(color = AppColors.TodoLayer.primaryText(isDark)),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = AppColors.TodoLayer.labelText(isDark).copy(alpha = 0.3f),
                    focusedBorderColor = AppColors.TodoLayer.primaryText(isDark)
                )
            )
            
            if (recents.isNotEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                Text("이전에 썼던 메모", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AppColors.TodoLayer.labelText(isDark))
                Spacer(modifier = Modifier.height(8.dp))
                recents.forEach { memo ->
                    Text(
                        text = memo,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onDone(memo) }
                            .padding(vertical = 12.dp),
                        fontSize = 14.sp,
                        color = AppColors.TodoLayer.primaryText(isDark),
                        maxLines = 2
                    )
                    Divider(color = AppColors.Search.divider(isDark))
                }
            }
        }
    }
}
