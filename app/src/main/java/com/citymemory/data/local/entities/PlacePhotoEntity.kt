package com.citymemory.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A photo the user took at a place.
 *
 * Its own table rather than a column on [UserPlaceStateEntity], because a place
 * has any number of photos and a row that held a delimited list of paths would
 * be a list pretending to be a string. The foreign key cascades from [PlaceEntity]
 * for the same reason the state row does: removing a place the user added must
 * not leave rows pointing at nothing.
 *
 * [fileName] is a name inside the app's own photo directory, not a path and not
 * a `content://` URI. Two reasons, and both are about the photo still being
 * there next year. A `content://` grant from the system photo picker lasts as
 * long as the process, so it would be dead on the next launch; and a photo that
 * lived in the gallery would vanish from here the day the user tidied up their
 * camera roll. So the bytes are copied in on import, and this names the copy.
 * Storing the leaf name rather than an absolute path means the app's data
 * directory can move — which it does, on a restore to a new device — without
 * every photo becoming a broken link.
 */
@Entity(
    tableName = "place_photos",
    foreignKeys = [
        ForeignKey(
            entity = PlaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["placeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("placeId")],
)
data class PlacePhotoEntity(
    @PrimaryKey val id: String,
    val placeId: String,
    val fileName: String,
    /** When it was added here, not when it was taken. */
    val addedAt: Long,
)
