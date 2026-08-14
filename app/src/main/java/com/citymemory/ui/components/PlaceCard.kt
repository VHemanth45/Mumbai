package com.citymemory.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.citymemory.domain.model.Place
import com.citymemory.ui.theme.CitySurface
import com.citymemory.ui.theme.GlowAmber
import com.citymemory.ui.theme.TextSecondary
import com.citymemory.ui.theme.TextTertiary
import com.citymemory.ui.theme.WishCyan

/**
 * One place in a list. The card itself carries the state signal — an explored
 * place has a warm border and a lit thumbnail, so a scrolled list shows
 * progress without the user reading a single label.
 */
@Composable
fun PlaceCard(
    place: Place,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onToggleWishlist: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val borderColor by animateColorAsState(
        targetValue = when {
            place.isVisited -> GlowAmber.copy(alpha = 0.28f)
            place.isWishlisted -> WishCyan.copy(alpha = 0.22f)
            else -> Color.White.copy(alpha = 0.05f)
        },
        animationSpec = tween(400),
        label = "cardBorder",
    )

    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = CitySurface,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlaceThumbnail(place = place, size = 58.dp)

            Spacer(Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = place.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    // Two lines: Mumbai has places called "Chhatrapati Shivaji
                    // Maharaj Vastu Sangrahalaya", and truncating to one line
                    // makes several of them indistinguishable in a list.
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = place.category.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary,
                    )
                    if (place.isVisited) {
                        Text(
                            text = "  ·  ",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextTertiary,
                        )
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = GlowAmber,
                            modifier = Modifier.size(12.dp),
                        )
                        Text(
                            text = " EXPLORED",
                            style = MaterialTheme.typography.labelSmall,
                            color = GlowAmber,
                        )
                    }
                }

                if (place.description.isNotBlank()) {
                    Text(
                        text = place.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.width(6.dp))

            when {
                trailing != null -> trailing()
                onToggleWishlist != null -> WishlistToggle(
                    isWishlisted = place.isWishlisted,
                    placeName = place.name,
                    onToggle = onToggleWishlist,
                )
            }
        }
    }
}

@Composable
fun WishlistToggle(
    isWishlisted: Boolean,
    placeName: String,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onToggle, modifier = modifier) {
        Icon(
            imageVector = if (isWishlisted) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
            contentDescription = if (isWishlisted) {
                "Remove $placeName from wishlist"
            } else {
                "Add $placeName to wishlist"
            },
            tint = if (isWishlisted) WishCyan else TextTertiary,
        )
    }
}
