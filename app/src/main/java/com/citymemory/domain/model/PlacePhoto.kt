package com.citymemory.domain.model

/**
 * One photo the user took at a place.
 *
 * [path] is an absolute path to a file inside the app's own storage, resolved
 * on read. It is deliberately not a `content://` URI: the bytes are copied in
 * on import so the photo survives the picker's grant lapsing and survives the
 * user deleting the original from their gallery. See `PhotoStore` for why that
 * trade is the right way round.
 *
 * The file may still be gone — a user can clear app storage — so anything
 * drawing this has to survive it not decoding.
 */
data class PlacePhoto(
    val id: String,
    val placeId: String,
    val path: String,
    val addedAt: Long,
)
