package com.citymemory.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.citymemory.domain.model.Place
import com.citymemory.ui.theme.CitySurfaceElevated
import com.citymemory.ui.theme.GlowAmber
import com.citymemory.ui.theme.TextTertiary
import com.citymemory.ui.theme.WishCyan
import com.citymemory.ui.theme.hue
import com.citymemory.ui.theme.icon

/**
 * The stand-in for a photograph.
 *
 * The app ships without network access, so rather than an empty grey box each
 * place gets a generated tile: a soft gradient in its category's hue with the
 * category glyph. Visited tiles warm up and gain an amber edge; wishlisted ones
 * get a cyan edge. Consistent, offline, and it never looks like a broken image.
 *
 * When a dataset with real imagery arrives, load [Place.imageUrl] here and keep
 * this as the placeholder while it decodes.
 */
@Composable
fun PlaceThumbnail(
    place: Place,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
) {
    val borderColor by animateColorAsState(
        targetValue = when {
            place.isVisited -> GlowAmber.copy(alpha = 0.7f)
            place.isWishlisted -> WishCyan.copy(alpha = 0.55f)
            else -> Color.White.copy(alpha = 0.06f)
        },
        animationSpec = tween(400),
        label = "thumbnailBorder",
    )

    val tint = place.category.hue

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.linearGradient(
                    colors = if (place.isVisited) {
                        listOf(tint.copy(alpha = 0.34f), GlowAmber.copy(alpha = 0.12f), CitySurfaceElevated)
                    } else {
                        listOf(tint.copy(alpha = 0.16f), CitySurfaceElevated)
                    },
                ),
            )
            .border(1.dp, borderColor, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = place.category.icon,
            contentDescription = null,
            tint = if (place.isVisited) GlowAmber.copy(alpha = 0.95f) else TextTertiary,
            modifier = Modifier.size(size * 0.42f),
        )
    }
}
