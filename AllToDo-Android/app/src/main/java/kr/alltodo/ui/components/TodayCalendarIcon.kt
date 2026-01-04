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

    // [FIX] Use a slightly larger outer box (40dp) with enough internal space (32dp content) to prevent ANY clipping
    Box(
        modifier = modifier
            .size(40.dp), // Fixed size for the whole component area
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(32.dp) // The actual visual icon size
                .border(2.dp, tint, RoundedCornerShape(6.dp))
                .padding(0.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top accent bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(tint, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                )
                
                // Day number area
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = dayOfMonth.toString(),
                        fontSize = 14.sp,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight.Black,
                        color = textColor
                    )
                }
            }
        }
    }
}
