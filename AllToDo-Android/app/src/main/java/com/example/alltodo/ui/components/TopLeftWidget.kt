package com.example.alltodo.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Icon

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.alltodo.ui.theme.AllToDoBlue
import com.example.alltodo.ui.theme.AllToDoGreen
import com.example.alltodo.ui.theme.AllToDoRed
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
            .background(AllToDoGreen.copy(alpha = 0.7f))
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
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "할 일",
                color = Color(0xFF333333),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            
            // 1. Red Badge (History / Location Only)
            StatBadge(color = AllToDoRed, count = historyCount)

            // 2. Green Badge (Local Todo - User Entered)
            StatBadge(color = AllToDoGreen, count = localTodoCount)

            // 3. Blue Badge (Server Todo - Future/Instructions)
            StatBadge(color = AllToDoBlue, count = serverTodoCount)
        }
    }
}

@Composable
fun StatBadge(color: Color, count: Int) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = count.toString(),
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
