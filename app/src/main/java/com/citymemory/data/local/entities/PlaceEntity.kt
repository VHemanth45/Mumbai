package com.citymemory.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Catalog data only. Nothing here changes as the user explores — which is what
 * makes replacing the mock Mumbai seed with a real dataset a data-only change.
 *
 * With one exception, [isUserAdded]: a place the user typed in themselves lives
 * in this table too, because everything downstream — the map, search, progress,
 * the visit and the review — already works on `places` rows and should not have
 * to learn about a second kind. The flag exists so re-seeding can tell the two
 * apart, not so the rest of the app can.
 */
@Entity(
    tableName = "places",
    foreignKeys = [
        ForeignKey(
            entity = CityEntity::class,
            parentColumns = ["id"],
            childColumns = ["cityId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("cityId"), Index("category")],
)
data class PlaceEntity(
    @PrimaryKey val id: String,
    val cityId: String,
    val name: String,
    /** Stored as [com.citymemory.domain.model.PlaceCategory.id], not the enum name. */
    val category: String,
    val description: String,
    val latitude: Double,
    val longitude: Double,
    val imageUrl: String?,
    val displayOrder: Int,
    /**
     * Street address where OSM has one, and the locality and pin code from the
     * postal boundary where it does not. Null only for a user-added place they
     * chose not to give one.
     *
     * It earns its column because the catalog now ships every mapped place,
     * chains included: "Starbucks" is in here thirty-two times and the address
     * is the only thing that tells them apart.
     */
    val address: String?,
    /**
     * True for a place the user added themselves. Declares the SQLite default
     * so Room's schema validation matches the `ALTER TABLE` in `MIGRATION_2_3`.
     */
    @ColumnInfo(defaultValue = "0")
    val isUserAdded: Boolean = false,
)
