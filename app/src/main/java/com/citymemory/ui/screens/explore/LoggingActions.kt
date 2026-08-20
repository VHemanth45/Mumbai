package com.citymemory.ui.screens.explore

import android.Manifest
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.citymemory.data.photo.PhotoImportResult
import com.citymemory.ui.theme.CityNight
import com.citymemory.ui.theme.CitySurface
import com.citymemory.ui.theme.GlowAmber
import com.citymemory.ui.theme.TextSecondary
import com.citymemory.ui.theme.TextTertiary
import com.citymemory.ui.util.AutoLogPermissions
import com.citymemory.ui.util.AutoLogStep
import com.citymemory.ui.util.LOCATION_PERMISSIONS
import com.citymemory.ui.util.openAppSettings

/**
 * The two ways the app can log something without being told, offered as two
 * small buttons over the map.
 *
 * There is no settings screen to put them on, and inventing one for two
 * controls would bury them. Over the map they sit next to the thing they
 * change: both of these exist to make more of it light up.
 */
@Composable
fun LoggingActions(
    autoLogEnabled: Boolean,
    isImporting: Boolean,
    onPhotosPicked: (List<String>) -> Unit,
    onSetAutoLog: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val currentOnSetAutoLog by rememberUpdatedState(onSetAutoLog)

    // True while walking the permission steps. It has to survive each launcher
    // round trip, because every grant lands back here needing to know whether
    // it was part of a switch-on or just an unrelated answer.
    var enabling by remember { mutableStateOf(false) }
    var showBackgroundRationale by remember { mutableStateOf(false) }

    fun advance() {
        if (!enabling) return
        when (AutoLogPermissions.nextStep(context)) {
            AutoLogStep.READY -> {
                enabling = false
                currentOnSetAutoLog(true)
            }
            // Handled by the launchers below; re-entering here after a refusal
            // would fire the same dialog again in a loop, so it stops instead
            // and the user can tap the button again when they mean it.
            else -> Unit
        }
    }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants.values.any { it }) step(context, ::advance) { showBackgroundRationale = true }
        else enabling = false
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        // A refused notification permission does not stop the feature — the
        // suggestion still lands on the card. It only means the user will find
        // it next time they open the app rather than being told.
        step(context, ::advance) { showBackgroundRationale = true }
    }

    val backgroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { advance() }

    // Coming back from the Settings page is the only way "Allow all the time"
    // can be granted on Android 11 and later, and nothing tells the app it
    // happened — so the answer is read again on every resume.
    LifecycleResumeEffect(Unit) {
        if (enabling) advance()
        onPauseOrDispose { }
    }

    fun beginEnabling() {
        enabling = true
        when (AutoLogPermissions.nextStep(context)) {
            AutoLogStep.READY -> {
                enabling = false
                currentOnSetAutoLog(true)
            }
            AutoLogStep.NEED_FOREGROUND_LOCATION -> locationLauncher.launch(LOCATION_PERMISSIONS)
            AutoLogStep.NEED_NOTIFICATIONS ->
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            AutoLogStep.NEED_BACKGROUND_LOCATION -> showBackgroundRationale = true
        }
    }

    val photoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris -> onPhotosPicked(uris.map { it.toString() }) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.End,
    ) {
        RoundAction(
            icon = Icons.Filled.PhotoLibrary,
            description = "Log places from your photos",
            active = false,
            busy = isImporting,
            onClick = {
                photoLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
        )
        RoundAction(
            icon = if (autoLogEnabled) Icons.Filled.Sensors else Icons.Outlined.Sensors,
            description = if (autoLogEnabled) {
                "Automatic logging is on. Turn it off"
            } else {
                "Automatically notice places you stay at"
            },
            active = autoLogEnabled,
            busy = false,
            onClick = { if (autoLogEnabled) onSetAutoLog(false) else beginEnabling() },
        )
    }

    if (showBackgroundRationale) {
        BackgroundLocationDialog(
            onDismiss = {
                showBackgroundRationale = false
                enabling = false
            },
            onContinue = {
                showBackgroundRationale = false
                if (AutoLogPermissions.backgroundNeedsSettings) {
                    // From Android 11 no dialog the app can raise contains
                    // "Allow all the time". Settings is the only place it
                    // exists, and `LifecycleResumeEffect` above picks up the
                    // answer when the user comes back.
                    openAppSettings(context)
                } else {
                    backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }
            },
        )
    }
}

/** Re-reads the next step and fires whichever request it names. */
private inline fun step(
    context: Context,
    advance: () -> Unit,
    onNeedsBackground: () -> Unit,
) {
    when (AutoLogPermissions.nextStep(context)) {
        AutoLogStep.NEED_BACKGROUND_LOCATION -> onNeedsBackground()
        else -> advance()
    }
}

@Composable
private fun RoundAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    active: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = !busy,
        shape = RoundedCornerShape(percent = 50),
        color = if (active) GlowAmber.copy(alpha = 0.18f) else CitySurface.copy(alpha = 0.92f),
        border = BorderStroke(
            1.dp,
            if (active) GlowAmber.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.08f),
        ),
        modifier = Modifier
            .size(46.dp)
            .semantics { contentDescription = description },
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(11.dp),
        ) {
            if (busy) {
                CircularProgressIndicator(
                    color = GlowAmber,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (active) GlowAmber else TextSecondary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun BackgroundLocationDialog(onDismiss: () -> Unit, onContinue: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CitySurface,
        title = { Text("Let City Memory notice where you stop") },
        text = {
            Text(
                text = "To offer to log a place after you have been there twenty minutes, " +
                    "location has to be allowed all the time.\n\n" +
                    "Nothing leaves your phone — the app has no internet permission at all. " +
                    "Fixes are read on the device, matched against the offline map, and thrown away.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
        },
        confirmButton = {
            TextButton(onClick = onContinue) {
                Text(
                    text = if (AutoLogPermissions.backgroundNeedsSettings) {
                        "Open settings"
                    } else {
                        "Continue"
                    },
                    color = GlowAmber,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Not now", color = TextTertiary) }
        },
    )
}

/**
 * What a photo import found, said in the plainest terms available.
 *
 * "Nothing found" is the most likely outcome and the least useful sentence, so
 * every branch here names *which* nothing it was: photos with no location at
 * all, places already visited, or nowhere in the catalog near enough to name.
 */
@Composable
fun ImportResultBanner(
    result: PhotoImportResult?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = result != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        val shown = remember(result) { result }
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = CitySurface,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            ) {
                Text(
                    text = shown?.let { describe(it) }.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onDismiss) { Text("OK", color = GlowAmber) }
            }
        }
    }
}

private fun describe(result: PhotoImportResult): String = when {
    result.suggested == 1 -> "Found 1 place to check."
    result.suggested > 1 -> "Found ${result.suggested} places to check."
    result.photosSeen == 0 -> "No photos chosen."
    result.photosWithoutLocation == result.photosSeen ->
        "None of those photos have a location saved. Screenshots and photos sent " +
            "through chat apps usually don't."
    result.alreadyKnown > 0 && result.unmatched == 0 ->
        "Those are all places you have already logged."
    result.unmatched > 0 ->
        "Those photos are of somewhere the map doesn't have a place for. " +
            "You can still add it by name."
    else -> "Nothing new in those photos."
}
