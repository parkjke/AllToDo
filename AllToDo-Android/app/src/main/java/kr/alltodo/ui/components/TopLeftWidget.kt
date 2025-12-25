package kr.alltodo.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Icon

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.alltodo.ui.theme.AllToDoBlue
import kr.alltodo.ui.theme.AllToDoGreen
import kr.alltodo.ui.theme.AllToDoRed
import androidx.compose.material.icons.filled.Checklist

@Composable
fun TopLeftWidget(
    historyCount: Int,
    localTodoCount: Int,
    serverTodoCount: Int,
    modifier: Modifier = Modifier,
    onExpandClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(AllToDoGreen.copy(alpha = 0.8f))
            .clickable { onExpandClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Checklist,
                contentDescription = null,
                tint = Color(0xFF333333),
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = "할 일",
                color = Color(0xFF333333),
                fontSize = 16.sp,
                fontWeight = FontWeight.Black
            )
            
            Spacer(modifier = Modifier.width(6.dp))

            // 1. Blue Badge (Server)
            StatBadge(color = AllToDoBlue, count = serverTodoCount)

            // 2. Green Badge (Local)
            StatBadge(color = AllToDoGreen, count = localTodoCount)

            // 3. Red Badge (History)
            StatBadge(color = AllToDoRed, count = historyCount)

        }
    }
}

@Composable
fun StatBadge(color: Color, count: Int) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(color, shape = CircleShape)

            .drawWithCache {
                val brush = Brush.sweepGradient(
                    0.0f to Color.White,
                    0.416f to Color(0xFF9E9E9E), // Gray 5
                    1.0f to Color.White
                )
                onDrawWithContent {
                    drawContent()
                    rotate(degrees = -90f) {
                        drawCircle(
                            brush = brush,
                            radius = (size.minDimension - 1.dp.toPx()) / 2,
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = count.toString(),
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}


