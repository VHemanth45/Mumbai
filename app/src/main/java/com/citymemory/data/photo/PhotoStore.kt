package com.citymemory.data.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Where the user's photos of a place actually live.
 *
 * **The bytes are copied in, rather than a `content://` URI being kept.** That
 * is the whole decision in this file and it is worth being explicit about,
 * because keeping the URI is less code and looks like it works:
 *
 *  * the system photo picker grants read access to the URI it returns, and that
 *    grant lasts until the process dies. A photo added today is a permission
 *    error tomorrow morning. `ACTION_OPEN_DOCUMENT` can be asked for a
 *    *persistable* grant instead, but the picker contract cannot, and the
 *    picker is the one that does not need a storage permission;
 *  * a photo that lives in the gallery leaves this app the day the user tidies
 *    up their camera roll — and a memory that disappears when you delete the
 *    original is not much of a memory;
 *  * this app has no `INTERNET` permission and no backend. Self-contained is
 *    the whole design, and pointing at somebody else's file is not that.
 *
 * The cost is disk, and it is paid down by downscaling on the way in: a 12 MP
 * phone photo lands as a ~1600 px JPEG of a few hundred kilobytes, which is
 * more than a phone screen can show anyway.
 *
 * Behind an interface so the repository can be tested without a filesystem.
 */
interface PhotoStore {

    /**
     * Copies the image at [source] into the app's own storage, downscaled.
     *
     * Returns the file name to store against the place, or null if the image
     * could not be read — a URI whose permission has already lapsed, a file
     * that is not an image, a picker that returned something unreadable.
     */
    suspend fun import(source: Uri): String?

    /** The file a stored name refers to. It may not exist. */
    fun fileFor(fileName: String): File

    /**
     * Removes one file.
     *
     * There is deliberately no "sweep everything the database has forgotten"
     * companion to this. Both delete paths in the repository read the file
     * names *before* the rows go, so nothing is orphaned in the ordinary course
     * of things, and a periodic sweep would mean opening the database at
     * startup — which the Application goes out of its way not to do. The
     * residue is one file if the process dies mid-delete, which is a few
     * hundred kilobytes and no correctness problem at all.
     */
    suspend fun delete(fileName: String)
}

/**
 * A store that keeps nothing, for the tests and callers that have no business
 * writing files.
 *
 * Refusing an import rather than pretending to accept one is deliberate: the
 * repository turns a null into `addPhoto() == false`, and the screen says the
 * photo could not be added. A no-op that reported success would put a row in
 * the database pointing at a file that was never written.
 */
object NoPhotoStore : PhotoStore {
    override suspend fun import(source: Uri): String? = null
    override fun fileFor(fileName: String): File = File(fileName)
    override suspend fun delete(fileName: String) = Unit
}

class FilePhotoStore(context: Context) : PhotoStore {

    private val appContext = context.applicationContext

    private val directory: File by lazy {
        File(appContext.filesDir, DIRECTORY).apply { mkdirs() }
    }

    override fun fileFor(fileName: String): File = File(directory, fileName)

    override suspend fun import(source: Uri): String? = withContext(Dispatchers.IO) {
        // Two goes. The second is half the size in a format that costs two
        // bytes a pixel instead of four, which is a quarter of the memory — see
        // [attempt] for why running out of it is a case that has to be handled
        // rather than allowed to happen.
        attempt(source, MAX_EDGE_PX, Bitmap.Config.ARGB_8888)
            ?: attempt(source, MAX_EDGE_PX / 2, Bitmap.Config.RGB_565)
    }

    /**
     * One attempt at copying the photo in, at a given size and colour depth.
     *
     * **Catches [Throwable], not [Exception], and that is the point.** Decoding
     * a photo is the one thing this app does that can exhaust the heap, and
     * `OutOfMemoryError` is an `Error`: caught as `Exception` it is not caught
     * at all, so on a small-heap device a large photo did not become "that
     * photo could not be added" — it unwound out of the view model's coroutine
     * and took the process with it. A photo at 800 px is worth more to the user
     * than a crash, and no photo with an explanation is worth more than both.
     *
     * `CancellationException` is re-thrown because it is not a failure: it is
     * the user backing out of the screen, and swallowing it would leave the
     * coroutine machinery believing this work is still running.
     */
    private fun attempt(source: Uri, maxEdge: Int, config: Bitmap.Config): String? = try {
        val bitmap = decodeDownscaled(source, maxEdge, config)
        if (bitmap == null) {
            null
        } else {
            val upright = try {
                applyExifRotation(source, bitmap)
            } catch (e: Exception) {
                // A missing or unreadable EXIF block is not a reason to lose the
                // photo; it just means it may arrive on its side.
                Log.w(TAG, "could not read orientation, keeping the photo as-is", e)
                bitmap
            }
            publish(upright).also {
                if (upright !== bitmap) bitmap.recycle()
                upright.recycle()
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Log.w(TAG, "could not import $source at $maxEdge px", e)
        null
    }

    /**
     * Writes the bitmap to a `.part` file and renames it into place.
     *
     * A rename within one directory is atomic, so the published file either
     * does not exist or is a complete JPEG. Compressing straight to the final
     * name would leave a window — process death, a full disk — where the strip
     * has a row pointing at half an image, which decodes to a broken tile that
     * nothing will ever repair.
     */
    private fun publish(bitmap: Bitmap): String {
        val name = "${UUID.randomUUID()}.jpg"
        val staged = File(directory, "$name$STAGING_SUFFIX")
        try {
            staged.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
            check(staged.renameTo(fileFor(name))) { "could not publish $name" }
            return name
        } catch (e: Throwable) {
            staged.delete()
            throw e
        }
    }

    override suspend fun delete(fileName: String) {
        withContext(Dispatchers.IO) { fileFor(fileName).delete() }
    }

    /**
     * Decodes at the smallest power-of-two scale that still covers [MAX_EDGE_PX].
     *
     * Two passes, which is the only way to do this without deciding how much
     * memory to spend before knowing how big the image is. A 12 MP photo
     * decoded whole is 48 MB of ARGB_8888 and an `OutOfMemoryError` on the
     * older devices this app still supports; `inSampleSize` makes the decoder
     * skip pixels as it reads, so the full bitmap never exists.
     */
    private fun decodeDownscaled(source: Uri, maxEdge: Int, config: Bitmap.Config): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (
            bounds.outWidth / (sample * 2) >= maxEdge ||
            bounds.outHeight / (sample * 2) >= maxEdge
        ) {
            sample *= 2
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = config
        }
        val decoded = openStream(source)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: return null

        // `inSampleSize` only halves, so the result can still be up to twice
        // the target. One exact scale finishes the job.
        val longest = maxOf(decoded.width, decoded.height)
        if (longest <= maxEdge) return decoded
        val ratio = maxEdge.toFloat() / longest
        val scaled = Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * ratio).toInt().coerceAtLeast(1),
            (decoded.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
        if (scaled !== decoded) decoded.recycle()
        return scaled
    }

    /**
     * Phones store a photo the way the sensor read it and record which way up
     * that was. Ignoring the tag is why a portrait photo so often arrives on
     * its side; the copy is written upright so nothing downstream has to know.
     */
    private fun applyExifRotation(source: Uri, bitmap: Bitmap): Bitmap {
        val orientation = openStream(source)?.use { stream ->
            ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        } ?: return bitmap

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun openStream(source: Uri) = appContext.contentResolver.openInputStream(source)

    private companion object {
        const val DIRECTORY = "place-photos"
        const val TAG = "PhotoStore"

        /**
         * Longest edge kept, in pixels. Comfortably more than a phone screen
         * shows, and about a tenth of the bytes of the original.
         */
        const val MAX_EDGE_PX = 1600

        const val JPEG_QUALITY = 85

        /**
         * Extension a photo wears while it is being written. Renamed away once
         * the bytes are all there; see [publish].
         */
        const val STAGING_SUFFIX = ".part"
    }
}
