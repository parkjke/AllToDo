package kr.alltodo.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kr.alltodo.ui.theme.AppColors

@Composable
fun RippleEffectView(
    isDark: Boolean = false,
    modifier: Modifier = Modifier
) {
    val rippleColor = AppColors.Search.ripple(isDark)
    
    val infiniteTransition = rememberInfiniteTransition(label = "ripple")
    
    val ripples = listOf(0, 1, 2)
    
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        ripples.forEach { index ->
            val progress by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1500, easing = LinearEasing, delayMillis = index * 400),
                    repeatMode = RepeatMode.Restart
                ),
                label = "progress_$index"
            )
            
            Canvas(modifier = Modifier.fillMaxSize()) {
                val radius = 10.dp.toPx() + (progress * 30.dp.toPx())
                val alpha = (1f - progress) * 0.6f
                
                drawCircle(
                    color = rippleColor.copy(alpha = alpha),
                    radius = radius,
                    style = Stroke(width = 4.dp.toPx())
                )
            }
        }
    }
}
