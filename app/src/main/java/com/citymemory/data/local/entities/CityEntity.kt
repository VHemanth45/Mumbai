package com.citymemory.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cities")
data class CityEntity(
    @PrimaryKey val id: String,
    val name: String,
    val country: String,
    /**
     * Which catalog this city's places were seeded from — the stamp in the
     * asset header, see [com.citymemory.data.local.seed.PlaceCatalogCodec].
     *
     * It lives on the city row rather than in preferences so that noticing a
     * new catalog and writing it happen in the same transaction: a re-seed that
     * is interrupted half way leaves the old stamp, and the next launch does it
     * again. Null on a database seeded before the catalog moved into an asset.
     */
    val catalogStamp: String? = null,
)
