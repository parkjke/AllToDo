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

    // [FIX] Increase overall size slightly (36 -> 38) and add enough padding to avoid clipping
    Box(
        modifier = modifier
            .size(38.dp) // Slightly larger to accommodate border
            .border(2.dp, tint, RoundedCornerShape(8.dp))
            .padding(1.dp), // Minimal padding inside border
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top bar like a desk calendar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp) // Slightly taller
                    .background(tint, RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
            )
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = dayOfMonth.toString(),
                    fontSize = 15.sp, // Slightly larger
                    fontWeight = FontWeight.ExtraBold, // More emphasis
                    color = textColor,
                    modifier = Modifier.offset(y = (-1).dp) // Adjust visual balance
                )
            }
        }
    }
}
