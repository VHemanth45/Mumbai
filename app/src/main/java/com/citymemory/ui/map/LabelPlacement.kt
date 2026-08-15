package com.citymemory.ui.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.citymemory.domain.model.LabelTier

/**
 * Which names get drawn when more of them want the same piece of screen.
 *
 * Overlapping text is unreadable text, and at the overview all 89 postal areas
 * are on screen at once over a city about a thousand pixels wide. Something has
 * to give way. This is the something.
 *
 * Greedy first-come rejection, evaluated in the order the caller offers labels
 * — which is the order `tools/build_labels.py` writes them, areas before
 * places. That ordering *is* the priority: the district you are in matters more
 * than which of six temples happens to be nearby, and an area label that lost
 * its space to a temple would be the wrong outcome.
 *
 * Pulled out of the draw pass into a plain object with no Compose dependency
 * beyond geometry types, because it is the part of drawing labels that can be
 * wrong in a way looking at the screen would not reliably catch — a subtly
 * wrong overlap test shows up as text that collides once every few pans.
 */
internal object LabelPlacement {

    /**
     * How many positions a tier will try before giving its space up.
     *
     * An [LabelTier.AREA] names a region, so it is centred on the region's pole
     * and there is nowhere else for it to be. A [LabelTier.PLACE] names a
     * *point*, and that point already has a marker drawn on it — centring one
     * of those puts the name on top of its own dot, which is what this used to
     * do for all 130 of them. So a place name gets three boxes to try.
     */
    fun placementCount(tier: LabelTier): Int = if (tier == LabelTier.AREA) 1 else PLACE_PLACEMENTS

    /**
     * The top-left corner of placement [index], counting from the best.
     *
     * The order is a preference and not a fallback chain: right of the marker
     * is how a map labels a point, left of it reads fine when the right is
     * taken, and under it is the last resort that still is not *on* it.
     */
    fun placementTopLeft(
        tier: LabelTier,
        index: Int,
        center: Offset,
        width: Float,
        height: Float,
        gap: Float,
    ): Offset = when {
        tier == LabelTier.AREA -> Offset(center.x - width / 2f, center.y - height / 2f)
        index == 0 -> Offset(center.x + gap, center.y - height / 2f)
        index == 1 -> Offset(center.x - gap - width, center.y - height / 2f)
        else -> Offset(center.x - width / 2f, center.y + gap)
    }

    /**
     * The screen rectangle a label at [topLeft] would occupy, grown by
     * [padding] so two names that merely touch still count as colliding.
     */
    fun claimAt(topLeft: Offset, width: Float, height: Float, padding: Float): Rect = Rect(
        left = topLeft.x - padding,
        top = topLeft.y - padding,
        right = topLeft.x + width + padding,
        bottom = topLeft.y + height + padding,
    )

    /**
     * Takes the space if it is free, and says whether it did.
     *
     * A rectangle entirely off screen is refused without being recorded, so a
     * name the user cannot see never denies its space to one they can. A
     * rectangle that is partly on screen is kept: half a name at the edge is
     * how every map behaves, and dropping it would make names pop in and out
     * as you pan.
     */
    fun claim(placed: MutableList<Rect>, claim: Rect, viewport: Size): Boolean {
        if (!intersectsViewport(claim, viewport)) return false
        for (index in placed.indices) {
            if (placed[index].overlaps(claim)) return false
        }
        placed.add(claim)
        return true
    }

    fun intersectsViewport(rect: Rect, viewport: Size): Boolean =
        rect.right > 0f && rect.bottom > 0f &&
            rect.left < viewport.width && rect.top < viewport.height

    /** Right of the marker, left of it, under it. */
    private const val PLACE_PLACEMENTS = 3
}
