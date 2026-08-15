package com.citymemory.ui.screens.place

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.citymemory.domain.model.PlacePhoto
import com.citymemory.ui.components.PhotoImage
import com.citymemory.ui.theme.CityNight
import com.citymemory.ui.theme.CitySurface
import com.citymemory.ui.theme.GlowAmber
import com.citymemory.ui.theme.TextSecondary
import com.citymemory.ui.theme.TextTertiary

/**
 * The user's own photos of a place.
 *
 * The catalog ships no imagery at all — `imageUrl` is null throughout and the
 * app renders a generated category tile instead — so these are the only real
 * pictures in it, and they are the ones that make a visited place a memory of
 * a specific day rather than a row in a list.
 *
 * Photos are copied into the app's own storage on import rather than referenced
 * where they sit in the gallery; `PhotoStore` explains why that is the right way
 * round, and it is the reason a photo added today still opens next year.
 */
@Composable
fun PlacePhotosSection(
    photos: List<PlacePhoto>,
    isAdding: Boolean,
    onPhotoPicked: (String) -> Unit,
    onDeletePhoto: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The system photo picker: no storage permission, and on modern Android it
    // is a separate process that only ever hands back what was chosen.
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_AT_ONCE),
    ) { uris -> uris.forEach { onPhotoPicked(it.toString()) } }

    val pick = {
        picker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }

    var viewing by remember { mutableStateOf<PlacePhoto?>(null) }

    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "PHOTOS",
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
            )
            if (isAdding) {
                Spacer(Modifier.width(10.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.5.dp,
                    color = GlowAmber,
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        if (photos.isEmpty()) {
            OutlinedButton(onClick = { pick() }, shape = RoundedCornerShape(14.dp)) {
                Icon(
                    Icons.Outlined.AddAPhoto,
                    contentDescription = null,
                    tint = GlowAmber,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("Add a photo", color = TextSecondary)
            }
            return@Column
        }

        // Decoded at roughly twice the drawn size, so a thumbnail stays sharp
        // on a high-density screen without decoding the whole 1600 px file.
        val thumbPx = with(LocalDensity.current) { (THUMB_SIZE * 2).roundToPx() }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(end = 4.dp),
        ) {
            items(photos, key = { it.id }) { photo ->
                PhotoImage(
                    path = photo.path,
                    contentDescription = "Photo you added here",
                    targetPx = thumbPx,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(THUMB_SIZE)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { viewing = photo },
                )
            }
            item {
                Box(
                    modifier = Modifier
                        .size(THUMB_SIZE)
                        .clip(RoundedCornerShape(14.dp))
                        .background(CitySurface)
                        .clickable { pick() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.AddAPhoto,
                        contentDescription = "Add a photo",
                        tint = GlowAmber,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }

    viewing?.let { photo ->
        PhotoViewer(
            photo = photo,
            onClose = { viewing = null },
            onDelete = {
                onDeletePhoto(photo.id)
                viewing = null
            },
        )
    }
}

/**
 * One photo, full width, with the only destructive action in the feature.
 *
 * Deleting from here rather than from the strip is deliberate: a delete button
 * on a 96 dp thumbnail is a delete button you hit by accident, and the photo you
 * are about to lose should be the thing filling the screen when you decide.
 */
@Composable
private fun PhotoViewer(
    photo: PlacePhoto,
    onClose: () -> Unit,
    onDelete: () -> Unit,
) {
    var armed by remember(photo.id) { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(CityNight.copy(alpha = 0.97f))
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            PhotoImage(
                path = photo.path,
                contentDescription = "Photo you added here",
                // A pixel budget, not a dp measurement: it is the size the file
                // was written at, so this decodes it whole. Reading it off the
                // density would vary the cache key for no reason.
                targetPx = FULL_SIZE_PX,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .padding(horizontal = 16.dp),
            )

            IconButton(
                onClick = onClose,
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
            ) {
                Icon(Icons.Outlined.Close, contentDescription = "Close", tint = TextSecondary)
            }

            OutlinedButton(
                onClick = { if (armed) onDelete() else armed = true },
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(
                    1.dp,
                    if (armed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                ),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp),
            ) {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = null,
                    tint = if (armed) MaterialTheme.colorScheme.error else TextSecondary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (armed) "Delete for good?" else "Delete",
                    color = if (armed) MaterialTheme.colorScheme.error else TextSecondary,
                )
            }
        }
    }
}

private val THUMB_SIZE = 96.dp

/** What `PhotoStore` writes on import, so a full-screen view decodes it whole. */
private const val FULL_SIZE_PX = 1600

/**
 * How many photos one trip through the picker may add.
 *
 * Each one is decoded, rotated and re-encoded, so a hundred at once would be a
 * long unexplained wait. Ten is more than anybody adds to one place in a sitting.
 */
private const val MAX_AT_ONCE = 10
