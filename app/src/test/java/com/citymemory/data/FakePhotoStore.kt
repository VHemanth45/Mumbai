package com.citymemory.data

import android.net.Uri
import com.citymemory.data.photo.PhotoStore
import java.io.File

/**
 * A photo store that keeps names instead of bytes.
 *
 * The payoff for [PhotoStore] being an interface: everything the repository
 * does around a photo — writing the row, resolving the path, deleting the file
 * when the row goes, cascading when a place goes — is exercised on the JVM
 * without decoding a JPEG or touching a disk. Only the decoding itself needs a
 * device, and that is the part with no branching in it.
 */
class FakePhotoStore(
    /** Set false to make every import fail, the way an expired URI would. */
    var readable: Boolean = true,
) : PhotoStore {

    val imported = mutableListOf<String>()
    val deleted = mutableListOf<String>()

    private var counter = 0

    override suspend fun import(source: Uri): String? {
        if (!readable) return null
        counter++
        return "photo-$counter.jpg".also { imported.add(it) }
    }

    override fun fileFor(fileName: String): File = File("/photos/$fileName")

    override suspend fun delete(fileName: String) {
        deleted.add(fileName)
    }
}
