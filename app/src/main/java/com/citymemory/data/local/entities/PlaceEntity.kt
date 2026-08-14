package com.citymemory.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Catalog data only. Nothing here changes as the user explores — which is what
 * makes replacing the mock Mumbai seed with a real dataset a data-only change.
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
)
