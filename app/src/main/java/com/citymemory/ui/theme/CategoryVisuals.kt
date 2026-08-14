package com.citymemory.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Diamond
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.Museum
import androidx.compose.material.icons.outlined.Park
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.citymemory.domain.model.PlaceCategory

/**
 * Per-category icon and hue.
 *
 * The hues are used only for low-alpha thumbnail gradients and small glyph
 * tints — never for status. Visited stays amber and wishlisted stays cyan
 * everywhere, so category colour can never be mistaken for progress.
 */
val PlaceCategory.icon: ImageVector
    get() = when (this) {
        PlaceCategory.TOURIST -> Icons.Outlined.AccountBalance
        PlaceCategory.CAFE -> Icons.Outlined.LocalCafe
        PlaceCategory.RESTAURANT -> Icons.Outlined.Restaurant
        PlaceCategory.PARK -> Icons.Outlined.Park
        PlaceCategory.CULTURE -> Icons.Outlined.Museum
        PlaceCategory.HIDDEN_GEM -> Icons.Outlined.Diamond
    }

val PlaceCategory.hue: Color
    get() = when (this) {
        PlaceCategory.TOURIST -> Color(0xFFE0A75E)
        PlaceCategory.CAFE -> Color(0xFFC08A62)
        PlaceCategory.RESTAURANT -> Color(0xFFD97B6C)
        PlaceCategory.PARK -> Color(0xFF6FB98A)
        PlaceCategory.CULTURE -> Color(0xFF9A8CD8)
        PlaceCategory.HIDDEN_GEM -> Color(0xFF5FC2C8)
    }

/** Short label used where the full plural name would wrap, e.g. filter chips. */
val PlaceCategory.shortName: String
    get() = when (this) {
        PlaceCategory.TOURIST -> "Tourist"
        PlaceCategory.CAFE -> "Cafes"
        PlaceCategory.RESTAURANT -> "Food"
        PlaceCategory.PARK -> "Parks"
        PlaceCategory.CULTURE -> "Culture"
        PlaceCategory.HIDDEN_GEM -> "Hidden"
    }
