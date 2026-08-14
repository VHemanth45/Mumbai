package com.citymemory.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.citymemory.ui.theme.GlowAmber
import com.citymemory.ui.theme.GlowCore
import com.citymemory.ui.theme.GlowEmber

/**
 * A progress bar that reads as light rather than as a control: an ember-to-gold
 * fill with a soft bloom at its leading edge.
 */
@Composable
fun GlowProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    trackColor: Color = Color.White.copy(alpha = 0.07f),
    accent: Color = GlowAmber,
    contentDescription: String? = null,
) {
    val animatedFraction by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 850, easing = FastOutSlowInEasing),
        label = "progressFraction",
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                }
            ),
    ) {
        val radius = CornerRadius(size.height / 2f, size.height / 2f)

        drawRoundRect(color = trackColor, cornerRadius = radius)

        val filledWidth = size.width * animatedFraction
        if (filledWidth <= 0f) return@Canvas

        // The ramp is derived from the accent rather than fixed to amber, so a
        // category bar stays in its own hue instead of fading orange-to-green.
        val tail = lerp(accent, GlowEmber, 0.35f)
        val head = lerp(accent, GlowCore, 0.65f)

        drawRoundRect(
            brush = Brush.horizontalGradient(
                colors = listOf(tail, accent, head),
                startX = 0f,
                endX = filledWidth.coerceAtLeast(1f),
            ),
            size = Size(filledWidth, size.height),
            cornerRadius = radius,
        )

        // Bloom at the leading edge — the light spilling past the bar.
        val edge = Offset(filledWidth, size.height / 2f)
        val bloomRadius = size.height * 1.6f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(head.copy(alpha = 0.45f), Color.Transparent),
                center = edge,
                radius = bloomRadius,
            ),
            radius = bloomRadius,
            center = edge,
        )
    }
}
