package kr.alltodo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.alltodo.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun TodoItemCard(
    item: kr.alltodo.ui.UnifiedItem,
    onPathClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onNameClick: () -> Unit,
    isDark: Boolean = androidx.compose.foundation.isSystemInDarkTheme(),
    modifier: Modifier = Modifier
) {
    val textColor = AppColors.TodoList.primaryText(isDark)
    
    val backgroundColor = when (item) {
        is kr.alltodo.ui.UnifiedItem.Todo -> if (item.item.source != "local") AllToDoBlue.copy(alpha = 0.1f) else AllToDoGreen.copy(alpha = 0.1f)
        is kr.alltodo.ui.UnifiedItem.History -> AllToDoRed.copy(alpha = 0.1f)
        else -> AppColors.TodoList.background(isDark)
    }

    val typeColor = when (item) {
        is kr.alltodo.ui.UnifiedItem.Todo -> if (item.item.source != "local") AllToDoBlue else AllToDoGreen
        is kr.alltodo.ui.UnifiedItem.History -> AllToDoRed
        else -> Gray8
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Map Icon [지] - [FIX] 1.5x Size (approx 42dp for button, 30dp for icon)
            val noOfPath = when (item) {
                is kr.alltodo.ui.UnifiedItem.Todo -> item.item.no_of_path
                is kr.alltodo.ui.UnifiedItem.History -> item.item.no_of_path
                else -> 0
            }

            if (noOfPath > 0) {
                IconButton(
                    onClick = onPathClick,
                    enabled = noOfPath > 1,
                    modifier = Modifier.size(42.dp) // [FIX] Enlarge
                ) {
                    Icon(
                        imageVector = Icons.Default.Map,
                        contentDescription = "경로 보기",
                        tint = if (noOfPath > 1) typeColor else Color.LightGray.copy(alpha = 0.5f),
                        modifier = Modifier.size(30.dp) // [FIX] Enlarge
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(42.dp))
            }

            Spacer(modifier = Modifier.width(4.dp))

            // 2 & 3. Date & Time
            val timestamp = when (item) {
                is kr.alltodo.ui.UnifiedItem.Todo -> item.item.created_at
                is kr.alltodo.ui.UnifiedItem.History -> item.item.begin_time ?: item.item.created_at
                else -> System.currentTimeMillis()
            }
            
            val dateStr = SimpleDateFormat("M/d", Locale.KOREA).format(Date(timestamp))
            val timeStr = SimpleDateFormat("HH:mm", Locale.KOREA).format(Date(timestamp))

            Text(
                text = dateStr,
                fontSize = 13.sp,
                color = textColor,
                modifier = Modifier.padding(end = 4.dp)
            )
            Text(
                text = timeStr,
                fontSize = 13.sp,
                color = textColor,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(end = 8.dp)
            )

            // 4. Name
            val name = when (item) {
                is kr.alltodo.ui.UnifiedItem.Todo -> item.item.todo_name
                is kr.alltodo.ui.UnifiedItem.History -> item.item.todo_name
                else -> ""
            }

            Text(
                text = name,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onNameClick() }
            )

            // 5. Person Icon & Count (FORCED FOR TESTING)
            val personCount = when (item) {
                is kr.alltodo.ui.UnifiedItem.Todo -> item.item.person?.toIntOrNull() ?: 0
                else -> 0
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(2.dp))
                val personCount = item.person?.split(",")?.filter { it.isNotBlank() }?.size ?: 0
                Text(
                    text = personCount.toString(),
                    fontSize = 13.sp,
                    color = textColor,
                    fontWeight = FontWeight.Medium
                )
            }

            // 6. Delete Icon [휴] - [FIX] 1.5x Size (alltodoRed)
            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier.size(42.dp) // [FIX] Enlarge
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "삭제",
                    tint = AllToDoRed,
                    modifier = Modifier.size(30.dp) // [FIX] Enlarge
                )
            }
        }
    }
}
