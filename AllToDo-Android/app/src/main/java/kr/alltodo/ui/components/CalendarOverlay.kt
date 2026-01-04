package kr.alltodo.ui.components

import android.view.ContextThemeWrapper
import android.widget.CalendarView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    // [FIX] True Full Screen Layout
    Surface(
        modifier = modifier.fillMaxSize(),
        color = if (isDark) Gray9 else Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "할 일 달력",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color.White else Gray8
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "닫기",
                        tint = if (isDark) Color.White else Gray8,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Calendar Content
            AndroidView(
                factory = { context ->
                    val themedContext = if (isDark) {
                        ContextThemeWrapper(context, android.R.style.Theme_DeviceDefault)
                    } else {
                        context
                    }
                    CalendarView(themedContext).apply {
                        // Maximize space
                        layoutParams = android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    // Standard update logic
                }
            )
        }
    }
}
