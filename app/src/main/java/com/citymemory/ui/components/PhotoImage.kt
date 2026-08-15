package com.citymemory.ui.components

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.citymemory.ui.theme.CitySurface
import com.citymemory.ui.theme.TextTertiary
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A photo from the app's own storage, decoded at roughly the size it is drawn.
 *
 * **No image-loading library.** Coil would do this and more, and for a screen
 * full of remote images it would be the obvious answer. These are local files,
 * already downscaled to 1600 px on import by `PhotoStore`, and shown a handful
 * at a time — which is the whole job, and it is forty lines. A dependency that
 * exists to solve network caching, request cancellation and content negotiation
 * is a poor trade for an app that has no `INTERNET` permission.
 *
 * What it does still have to get right is the two things that bite: decoding
 * off the main thread, and not decoding again on every recomposition.
 */
@Composable
fun PhotoImage(
    path: String,
    contentDescription: String?,
    targetPx: Int,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    var bitmap by remember(path, targetPx) { mutableStateOf(PhotoCache[path, targetPx]) }
    var failed by remember(path, targetPx) { mutableStateOf(false) }

    LaunchedEffect(path, targetPx) {
        if (bitmap != null) return@LaunchedEffect
        val decoded = withContext(Dispatchers.IO) { PhotoCache.load(path, targetPx) }
        if (decoded == null) failed = true else bitmap = decoded
    }

    Box(modifier.background(CitySurface), contentAlignment = Alignment.Center) {
        val current = bitmap
        when {
            current != null -> Image(
                bitmap = current,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
            )
            // A user can clear app storage, and a photo whose file has gone
            // should read as a missing photo rather than as an empty box that
            // never finishes loading.
            failed -> Icon(
                imageVector = Icons.Outlined.BrokenImage,
                contentDescription = "This photo is missing",
                tint = TextTertiary,
            )
        }
    }
}

/**
 * Decoded photos, bounded by bytes rather than by count.
 *
 * A count-based cache is the wrong bound here: a thumbnail and a full-screen
 * view of the same photo differ by two orders of magnitude in memory, and
 * counting them the same either wastes the budget or blows it. Sized against
 * the heap the app was actually given.
 */
private object PhotoCache {

    private val cache = object : LruCache<String, ImageBitmap>(maxBytes()) {
        // `ImageBitmap` does not expose a row stride, so this assumes four
        // bytes a pixel. That is what `BitmapFactory` produces here by default,
        // and over-counting a cheaper config would only make the cache smaller
        // than it could be, never larger than the heap allows.
        override fun sizeOf(key: String, value: ImageBitmap): Int =
            value.width * value.height * BYTES_PER_PIXEL
    }

    operator fun get(path: String, targetPx: Int): ImageBitmap? = cache[key(path, targetPx)]

    /** Decodes at the smallest power-of-two scale that still covers [targetPx]. */
    fun load(path: String, targetPx: Int): ImageBitmap? {
        val cached = get(path, targetPx)
        if (cached != null) return cached
        if (!File(path).isFile) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        while (targetPx > 0 && longest / (sample * 2) >= targetPx) sample *= 2

        val decoded = BitmapFactory.decodeFile(
            path,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: return null

        return decoded.asImageBitmap().also { cache.put(key(path, targetPx), it) }
    }

    private fun key(path: String, targetPx: Int) = "$path@$targetPx"

    private const val BYTES_PER_PIXEL = 4

    /** An eighth of the heap: generous for photos, nowhere near the limit. */
    private fun maxBytes(): Int =
        (Runtime.getRuntime().maxMemory() / 8).coerceIn(4L shl 20, 48L shl 20).toInt()
}
