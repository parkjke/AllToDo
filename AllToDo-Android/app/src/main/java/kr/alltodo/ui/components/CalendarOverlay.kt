package kr.alltodo.ui.components

import android.view.ContextThemeWrapper
import android.widget.CalendarView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kr.alltodo.ui.theme.*

@Composable
fun CalendarOverlay(
    isDark: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f)) // Dim background
            .clickable(onClick = onDismiss), // Dismiss on outside touch
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.7f)
                .clickable(enabled = false) { /* Prevent dismiss on inside touch */ }
                .clip(RoundedCornerShape(24.dp)),
            colors = CardDefaults.cardColors(
                containerColor = if (isDark) Gray9 else Color.White
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "할 일 달력",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color.White else Gray8
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "닫기",
                            tint = if (isDark) Color.White else Gray8,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Calendar Content
                AndroidView(
                    factory = { context ->
                        // Apply theme to CalendarView for dark mode support if needed
                        val themedContext = if (isDark) {
                            ContextThemeWrapper(context, android.R.style.Theme_DeviceDefault_NoActionBar)
                        } else {
                            context
                        }
                        CalendarView(themedContext).apply {
                            // Any additional configuration
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { view ->
                        // Update view when states change
                    }
                )
            }
        }
    }
}
