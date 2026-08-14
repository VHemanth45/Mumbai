package com.citymemory.data.mapper

import com.citymemory.data.local.entities.CityEntity
import com.citymemory.data.local.entities.PlaceEntity
import com.citymemory.data.local.entities.PlaceWithState
import com.citymemory.domain.model.City
import com.citymemory.domain.model.Place
import com.citymemory.domain.model.PlaceCategory

fun CityEntity.toDomain(): City = City(
    id = id,
    name = name,
    country = country,
)

/**
 * Flattens the place row and its optional state row into the read model.
 * A missing state row means "untouched", i.e. neither visited nor wishlisted.
 */
fun PlaceWithState.toDomain(): Place = Place(
    id = place.id,
    cityId = place.cityId,
    name = place.name,
    category = PlaceCategory.fromId(place.category),
    description = place.description,
    latitude = place.latitude,
    longitude = place.longitude,
    imageUrl = place.imageUrl,
    displayOrder = place.displayOrder,
    isVisited = state?.isVisited == true,
    isWishlisted = state?.isWishlisted == true,
    visitedAt = state?.visitedAt,
)

fun Place.toEntity(): PlaceEntity = PlaceEntity(
    id = id,
    cityId = cityId,
    name = name,
    category = category.id,
    description = description,
    latitude = latitude,
    longitude = longitude,
    imageUrl = imageUrl,
    displayOrder = displayOrder,
)
