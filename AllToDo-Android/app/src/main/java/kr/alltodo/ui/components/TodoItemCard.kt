package kr.alltodo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
    modifier: Modifier = Modifier
) {
    val backgroundColor = when (item) {
        is kr.alltodo.ui.UnifiedItem.Todo -> if (item.item.source != "local") AllToDoBlue.copy(alpha = 0.1f) else AllToDoGreen.copy(alpha = 0.1f)
        is kr.alltodo.ui.UnifiedItem.History -> AllToDoRed.copy(alpha = 0.1f)
        else -> Color.White
    }

    val typeColor = when (item) {
        is kr.alltodo.ui.UnifiedItem.Todo -> if (item.item.source != "local") AllToDoBlue else AllToDoGreen
        is kr.alltodo.ui.UnifiedItem.History -> AllToDoRed
        else -> Gray3
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            // 1. Map Icon
            val noOfPath = when (item) {
                is kr.alltodo.ui.UnifiedItem.Todo -> item.item.no_of_path
                is kr.alltodo.ui.UnifiedItem.History -> item.item.no_of_path
                else -> 0
            }

            if (noOfPath > 0) {
                IconButton(
                    onClick = onPathClick,
                    enabled = noOfPath > 1,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Map,
                        contentDescription = "경로 보기",
                        tint = if (noOfPath > 1) typeColor else Gray4
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
            } else {
                Spacer(modifier = Modifier.width(36.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                // 2. Date/Time (Water balloon style)
                val dateTime = when (item) {
                    is kr.alltodo.ui.UnifiedItem.Todo -> item.item.date_time
                    is kr.alltodo.ui.UnifiedItem.History -> {
                        val sdf = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.KOREA)
                        sdf.format(Date(item.item.begin_time ?: item.item.created_at))
                    }
                    else -> null
                }

                if (!dateTime.isNullOrEmpty()) {
                    Surface(
                        color = Color.White.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Text(
                            text = dateTime,
                            fontSize = 11.sp,
                            color = Gray2,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                // 3. Name
                val name = when (item) {
                    is kr.alltodo.ui.UnifiedItem.Todo -> item.item.todo_name
                    is kr.alltodo.ui.UnifiedItem.History -> item.item.todo_name
                    else -> ""
                }

                Text(
                    text = name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Gray1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // 4. Memo
                val memo = when (item) {
                    is kr.alltodo.ui.UnifiedItem.Todo -> item.item.memo
                    is kr.alltodo.ui.UnifiedItem.History -> null
                    else -> null
                }

                if (!memo.isNullOrEmpty()) {
                    Text(
                        text = memo,
                        fontSize = 13.sp,
                        color = Gray3,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // 5. Person Count
            val personCount = when (item) {
                is kr.alltodo.ui.UnifiedItem.Todo -> item.item.person?.toIntOrNull() ?: 0
                else -> 0
            }

            if (personCount > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = Gray4,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = personCount.toString(),
                        fontSize = 12.sp,
                        color = Gray4,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
