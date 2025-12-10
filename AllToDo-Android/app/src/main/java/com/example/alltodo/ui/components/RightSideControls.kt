package com.example.alltodo.ui.components

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
import com.example.alltodo.ui.theme.AllToDoGreen

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
        modifier = modifier.padding(end = 8.dp), // tight margin
        horizontalAlignment = Alignment.End
    ) {
        // Top Group: Notification & Login
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // [NEW] History Button
            ControlIcon(
                icon = Icons.Default.Refresh, // Changed to Refresh (Core Icon)
                contentDescription = "History",
                onClick = onHistoryClick,
                containerColor = if (showHistoryMode) Color.Red else AllToDoGreen.copy(alpha = 0.7f),
                iconTint = if (showHistoryMode) Color.White else Color(0xFF333333)
            )

            ControlIcon(
                icon = Icons.Default.Notifications,
                contentDescription = "Notification",
                onClick = onNotificationClick,
                enabled = !showHistoryMode
            )
            ControlIcon(
                icon = Icons.Default.Person, // Login Icon
                contentDescription = "Login",
                onClick = onLoginClick,
                enabled = !showHistoryMode
            )
        }

        Spacer(modifier = Modifier.height(24.dp)) // Space between top and center groups

        // Center Group: Location, Zoom, Compass
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.End
        ) {
            ControlIcon(
                icon = Icons.Default.MyLocation,
                contentDescription = "Current Location",
                onClick = onLocationClick
            )
            ControlIcon(
                icon = Icons.Default.Add,
                contentDescription = "Zoom In",
                onClick = onZoomInClick
            )
            ControlIcon(
                icon = Icons.Default.Remove,
                contentDescription = "Zoom Out",
                onClick = onZoomOutClick
            )
            
            // Compass: Show only when rotated
            if (abs(compassRotation) > 0.01f) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(AllToDoGreen.copy(alpha = 0.7f))
                        .clickable { onCompassClick() },
                    contentAlignment = Alignment.Center
                ) {




                    Canvas(
                        modifier = Modifier
                            .width(12.dp)
                            .height(36.dp)
                            .rotate(-compassRotation)
                    ) {
                        val widthStart = 0f
                        val widthEnd = size.width
                        val widthMid = size.width / 2
                        val heightStart = 0f
                        val heightEnd = size.height
                        val heightMid = size.height / 2

                        // Top Triangle (Red)
                        val topPath = Path().apply {
                            moveTo(widthMid, heightStart)
                            lineTo(widthEnd, heightMid)
                            lineTo(widthStart, heightMid)
                            close()
                        }
                        drawPath(topPath, Color.Red) 

                        // Bottom Triangle (White)
                        val bottomPath = Path().apply {
                            moveTo(widthStart, heightMid)
                            lineTo(widthEnd, heightMid)
                            lineTo(widthMid, heightEnd)
                            close()
                        }
                        drawPath(bottomPath, Color.White)

                        // Outline
                        val outlinePath = Path().apply {
                            moveTo(widthMid, heightStart)
                            lineTo(widthEnd, heightMid)
                            lineTo(widthMid, heightEnd)
                            lineTo(widthStart, heightMid)
                            close()
                        }
                        drawPath(outlinePath, Color(0xFF333333), style = Stroke(width = 2f)) 
                    }
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
