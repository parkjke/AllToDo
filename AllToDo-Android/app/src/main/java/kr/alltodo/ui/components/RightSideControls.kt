package kr.alltodo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Refresh // [FIX] Core Icon
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kr.alltodo.ui.theme.AllToDoGreen

import androidx.compose.material.icons.filled.ArrowDropUp
import kotlin.math.abs
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke

// ... (Imports handled by context if needed, but adding specific ones)

@Composable
fun RightSideControls(
    modifier: Modifier = Modifier,
    compassRotation: Float = 0f,
    showHistoryMode: Boolean = false, // [NEW]
    onHistoryClick: () -> Unit = {}, // [NEW]
    onNotificationClick: () -> Unit = {},
    onLoginClick: () -> Unit = {},
    onLocationClick: () -> Unit = {},
    onZoomInClick: () -> Unit = {},
    onZoomOutClick: () -> Unit = {},
    onCompassClick: () -> Unit = {}
) {
    Column(
        modifier = modifier.padding(end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.End
    ) {
        // [Item 2] Top to Bottom: [My Info] -> ([Zoom In] [Zoom Out]) -> [Compass]
        
        // 1. My Info (Person)
        ControlIcon(
            icon = Icons.Default.Person,
            contentDescription = "My Info",
            onClick = onLoginClick,
            containerColor = AllToDoGreen.copy(alpha = 0.9f),
            iconTint = Color.White
        )

        // [NEW] Current Location Button
        ControlIcon(
            icon = Icons.Default.MyLocation,
            contentDescription = "Current Location",
            onClick = onLocationClick,
            containerColor = Color.White.copy(alpha = 0.9f),
            iconTint = AllToDoGreen
        )

        // 2. Zoom Group
        ControlIcon(
            icon = Icons.Default.Add,
            contentDescription = "Zoom In",
            onClick = onZoomInClick,
            containerColor = Color.White.copy(alpha = 0.9f),
            iconTint = Color(0xFF333333)
        )
        ControlIcon(
            icon = Icons.Default.Remove,
            contentDescription = "Zoom Out",
            onClick = onZoomOutClick,
            containerColor = Color.White.copy(alpha = 0.9f),
            iconTint = Color(0xFF333333)
        )
        
        // 3. Compass (Visible only when bearing > 0.01)
        if (abs(compassRotation) > 0.01f) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.9f))
                    .clickable { onCompassClick() },
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier
                        .width(12.dp)
                        .height(36.dp)
                        .rotate(-compassRotation)
                ) {
                    val widthMid = size.width / 2
                    val heightMid = size.height / 2

                    // Top (Red)
                    val topPath = Path().apply {
                        moveTo(widthMid, 0f)
                        lineTo(size.width, heightMid)
                        lineTo(0f, heightMid)
                        close()
                    }
                    drawPath(topPath, Color.Red) 

                    // Bottom (White)
                    val bottomPath = Path().apply {
                        moveTo(0f, heightMid)
                        lineTo(size.width, heightMid)
                        lineTo(widthMid, size.height)
                        close()
                    }
                    drawPath(bottomPath, Color.White)

                    // Outline
                    val outlinePath = Path().apply {
                        moveTo(widthMid, 0f)
                        lineTo(size.width, heightMid)
                        lineTo(widthMid, size.height)
                        lineTo(0f, heightMid)
                        close()
                    }
                    drawPath(outlinePath, Color(0xFF333333), style = Stroke(width = 2f)) 
                }
            }
        }
    }
}

@Composable
fun ControlIcon(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    iconModifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = AllToDoGreen.copy(alpha = 0.7f),
    iconTint: Color = Color(0xFF333333)
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(12.dp)) // Rounded Square
            .background(containerColor.copy(alpha = if (enabled) containerColor.alpha else 0.3f)) // Dim if disabled
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconTint.copy(alpha = if (enabled) 1f else 0.5f), // Dim icon
            modifier = iconModifier.size(24.dp)
        )
    }
}
