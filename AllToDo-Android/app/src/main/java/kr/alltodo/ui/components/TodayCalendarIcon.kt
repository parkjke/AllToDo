package kr.alltodo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.alltodo.ui.theme.Gray8
import java.util.*

@Composable
fun TodayCalendarIcon(
    modifier: Modifier = Modifier,
    tint: Color = Gray8
) {
    val calendar = Calendar.getInstance()
    val dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)
    val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) // 1: Sun, 7: Sat

    val textColor = when (dayOfWeek) {
        Calendar.SUNDAY -> Color.Red
        Calendar.SATURDAY -> Color(0xFF1F8FFF) // AllToDoBlue
        else -> tint
    }

    Box(
        modifier = modifier
            .size(36.dp)
            .border(2.dp, tint, RoundedCornerShape(8.dp))
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // [OPTIONAL] Could add a small bar at the top like a real calendar icon
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(tint, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                    .align(Alignment.TopCenter)
            )
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = dayOfMonth.toString(),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
            }
        }
    }
}
