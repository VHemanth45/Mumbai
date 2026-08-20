package com.citymemory.data.photo

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import com.citymemory.domain.PhotoRecord
import com.citymemory.domain.model.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Where and when a photograph was taken, if it will say.
 *
 * **The redaction problem is the whole reason this class is not three lines.**
 * Since Android 10 the media store strips GPS tags out of the bytes it hands an
 * app, silently — you get a valid JPEG, a valid `ExifInterface`, and
 * `latLong == null`, which is indistinguishable from a photo that never had a
 * location. Getting the real thing needs two things together: the
 * `ACCESS_MEDIA_LOCATION` permission, *and* asking for the original with
 * [MediaStore.setRequireOriginal]. Miss either and the feature appears to work
 * while quietly deciding that none of the user's photos know where they were.
 *
 * So this tries for the original, falls back to the plain URI when that is not
 * a URI the media store will redirect, and reports "no location" only after
 * both have been tried.
 */
interface PhotoLocationReader {

    /**
     * Reads one photo, or null when it carries no coordinate.
     *
     * Null is an ordinary answer, not an error: screenshots, saved images and
     * anything shared through a messaging app have no GPS tag, and a camera
     * roll is full of them.
     */
    suspend fun read(uri: String): PhotoRecord?

    /** Whether un-redacted EXIF is actually available to this process. */
    fun canReadLocation(): Boolean
}

class AndroidPhotoLocationReader(context: Context) : PhotoLocationReader {

    private val appContext = context.applicationContext

    override fun canReadLocation(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.ACCESS_MEDIA_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED

    override suspend fun read(uri: String): PhotoRecord? = withContext(Dispatchers.IO) {
        val parsed = runCatching { Uri.parse(uri) }.getOrNull() ?: return@withContext null

        // The original first, then the plain URI. Two attempts rather than one
        // because `setRequireOriginal` only means anything for media-store
        // URIs: the system photo picker hands back its own, which throws here
        // and reads perfectly well unredacted from the second attempt.
        val exif = readExif(originalOf(parsed)) ?: readExif(parsed) ?: return@withContext null

        val latLong = exif.latLong ?: return@withContext null
        // 0,0 is in the Atlantic. It is what a camera with a confused GPS
        // writes, and it is never where a photograph of Mumbai was taken.
        if (latLong[0] == 0.0 && latLong[1] == 0.0) return@withContext null

        PhotoRecord(
            uri = uri,
            takenAt = takenAt(exif, parsed),
            location = GeoPoint(latLong[0], latLong[1]),
        )
    }

    private fun readExif(uri: Uri): ExifInterface? = runCatching {
        appContext.contentResolver.openInputStream(uri)?.use { ExifInterface(it) }
    }.onFailure { Log.d(TAG, "could not read exif from $uri", it) }.getOrNull()

    private fun originalOf(uri: Uri): Uri {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return uri
        if (!canReadLocation()) return uri
        return runCatching { MediaStore.setRequireOriginal(uri) }.getOrDefault(uri)
    }

    /**
     * When the shutter fired, falling back through progressively worse answers.
     *
     * EXIF first, because it is the only one that is about the photograph
     * rather than about the file. The media store's `DATE_TAKEN` is next and is
     * usually the same number. Last is the current time, which is wrong but
     * bounded: a photo with no date at all still groups into a visit of its
     * own, and the user is shown the date on the card before confirming
     * anything.
     */
    private fun takenAt(exif: ExifInterface, uri: Uri): Long =
        exif.dateTimeOriginal
            ?: exif.dateTime
            ?: mediaStoreDateTaken(uri)
            ?: System.currentTimeMillis()

    private fun mediaStoreDateTaken(uri: Uri): Long? = runCatching {
        appContext.contentResolver.query(
            uri,
            arrayOf(MediaStore.Images.Media.DATE_TAKEN),
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            cursor.getLong(0).takeIf { it > 0L }
        }
    }.getOrNull()

    private companion object {
        const val TAG = "PhotoLocation"
    }
}

/** Reads nothing, for tests and for callers with no content resolver. */
object NoPhotoLocationReader : PhotoLocationReader {
    override suspend fun read(uri: String): PhotoRecord? = null
    override fun canReadLocation(): Boolean = false
}
