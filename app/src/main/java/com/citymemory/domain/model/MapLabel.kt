package com.citymemory.domain.model

/**
 * A name the map can draw, and where it goes.
 *
 * The map is real geometry and nothing else — no text at all — which makes a
 * lit area a shape you recognise rather than one you can read. These are the
 * names that fix that, generated at build time by `tools/build_labels.py`.
 *
 * Coordinates are lat/lng like everything else here, so the renderer projects
 * them with exactly the projection it projects the streets with, and a label
 * cannot drift away from the thing it names.
 */
data class MapLabel(
    val tier: LabelTier,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    /**
     * Whatever else the tier carries: a pin code for [LabelTier.AREA], and
     * nothing at all for [LabelTier.PLACE]. Kept as loose text because the
     * renderer only ever shows it, never reads it.
     */
    val detail: String = "",
) {
    val location: GeoPoint get() = GeoPoint(latitude, longitude)
}

/**
 * How important a name is, which decides the zoom it appears at.
 *
 * The ids are wire values written by `tools/build_labels.py` and read back by
 * `MapLabelCodec`, so they must not be renumbered without regenerating the
 * asset.
 */
enum class LabelTier(val id: Int) {
    /**
     * One of Mumbai's 89 postal localities — Bandra, Colaba, Andheri East.
     *
     * These are what the city navigates by and what the whole-city view should
     * carry: at the overview a place name would be one of three thousand
     * illegible specks, and "Bandra" is the answer to what you are looking at.
     */
    AREA(0),

    /**
     * A place from the head of a category in the catalog, so one the data has
     * a lot to say about — an encyclopaedia entry, a heritage listing.
     *
     * Only worth drawing once you are close enough for a single building to
     * mean something, which is why it is a separate tier rather than a
     * different font.
     */
    PLACE(1),
    ;

    companion object {
        private val byId = entries.associateBy { it.id }

        fun fromId(id: Int): LabelTier? = byId[id]
    }
}
