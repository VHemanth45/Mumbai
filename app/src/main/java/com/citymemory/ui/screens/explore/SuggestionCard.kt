package com.citymemory.ui.screens.explore

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.citymemory.domain.model.SuggestionSource
import com.citymemory.domain.model.VisitSuggestion
import com.citymemory.ui.components.PlaceThumbnail
import com.citymemory.ui.theme.CityNight
import com.citymemory.ui.theme.CitySurface
import com.citymemory.ui.theme.GlowAmber
import com.citymemory.ui.theme.TextSecondary
import com.citymemory.ui.theme.TextTertiary
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * "Were you at Kala Ghoda Cafe?"
 *
 * The entire user-facing surface of both automatic-logging features, and it is
 * one card with two buttons on purpose. Everything the detectors do is a guess,
 * and the only honest way to present a guess is as a question that is as cheap
 * to refuse as to accept — so "Not this one" is the same size as "Yes", not a
 * small grey link under it.
 *
 * The evidence is shown rather than hidden: how far the fix was from the place,
 * and when. A person can tell instantly that "40 m away, 2 hours ago" is
 * probably right and "180 m away, yesterday" probably is not, and showing them
 * is what lets them answer quickly instead of having to remember.
 */
@Composable
fun SuggestionCard(
    suggestion: VisitSuggestion,
    remaining: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    now: Long,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = CitySurface,
        border = BorderStroke(1.dp, GlowAmber.copy(alpha = 0.28f)),
        tonalElevation = 8.dp,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlaceThumbnail(place = suggestion.place, size = 48.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Were you at ${suggestion.place.name}?",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = when (suggestion.source) {
                                SuggestionSource.DWELL -> Icons.Filled.Schedule
                                SuggestionSource.PHOTO -> Icons.Filled.PhotoCamera
                            },
                            contentDescription = null,
                            tint = TextTertiary,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = evidenceLine(suggestion, now),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextTertiary,
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Not this one")
                }
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GlowAmber,
                        contentColor = CityNight,
                    ),
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Yes, log it")
                }
            }

            if (remaining > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (remaining == 1) {
                        "1 more place to check"
                    } else {
                        "$remaining more places to check"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                )
            }
        }
    }
}

/**
 * "40 m away · 2 hours ago", in the plainest words that are still true.
 *
 * Deliberately not a timestamp. Someone answering this is reconstructing their
 * own afternoon, and "2 hours ago" is the form that memory is in; a clock time
 * would have to be converted back before it meant anything.
 */
private fun evidenceLine(suggestion: VisitSuggestion, now: Long): String {
    val distance = suggestion.distanceMeters.roundToInt()
    val distanceText = if (distance < 1_000) "$distance m away" else "far away"
    return "$distanceText  ·  ${relativeTime(now - suggestion.detectedAt)}"
}

private fun relativeTime(elapsedMillis: Long): String {
    if (elapsedMillis < 0) return "just now"
    val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsedMillis)
    if (minutes < 2) return "just now"
    if (minutes < 60) return "$minutes minutes ago"
    val hours = TimeUnit.MILLISECONDS.toHours(elapsedMillis)
    if (hours < 24) return if (hours == 1L) "an hour ago" else "$hours hours ago"
    val days = TimeUnit.MILLISECONDS.toDays(elapsedMillis)
    if (days == 1L) return "yesterday"
    if (days < 30) return "$days days ago"
    val months = days / 30
    if (months < 12) return if (months == 1L) "last month" else "$months months ago"
    val years = days / 365
    return if (years == 1L) "a year ago" else "$years years ago"
}
