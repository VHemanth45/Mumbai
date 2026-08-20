package com.citymemory.domain

import com.citymemory.domain.model.GeoPoint
import com.citymemory.domain.model.Place
import com.citymemory.domain.model.PlaceCategory
import com.citymemory.domain.model.SuggestionSource

/**
 * Which places a coordinate might be.
 *
 * Both automatic-logging features reduce to this question. A dwell gives a
 * coordinate the user sat at for twenty minutes; a photo gives the coordinate
 * its EXIF recorded. Neither knows *what* is there, and the catalog has 31,657
 * answers, up to a few dozen of which are inside the error circle of a phone
 * GPS fix in south Mumbai.
 *
 * **A linear scan is the right implementation, and that is worth defending.**
 * 31,657 distance computations is a few hundred microseconds of arithmetic on
 * flat data with no allocation; a spatial index would be a second structure to
 * build, hold in memory and keep in step with a table that changes whenever the
 * user adds a place. This runs when a dwell ends or a photo is imported —
 * events that happen a handful of times a day, never in a draw loop. If it ever
 * needs to run per frame, that is the moment to add the index, not now.
 *
 * The ranking is a *distance divided by a prior*, not a score out of ten. What
 * comes out is still metres — an "effective distance" where a likelier kind of
 * place feels nearer than it is — so the ordering can be read and argued with
 * rather than being an opaque number.
 */
object PlaceMatcher {

    /**
     * How far out to look, given what the fix claims about itself.
     *
     * A GPS fix reports accuracy as a 68% confidence radius, so searching
     * exactly that far throws away a third of the cases. The multiplier buys
     * those back. The floor exists because a fix claiming three metres is still
     * a fix taken inside a building; the ceiling because past a few hundred
     * metres in Mumbai the answer is "some of these two hundred places" and the
     * honest response is to offer nothing rather than a guess.
     */
    const val AccuracyMultiplier = 1.5
    const val MinRadiusMeters = 60.0

    /** What a fix with no stated accuracy is assumed to be worth. */
    const val AssumedAccuracyMeters = 60.0

    /**
     * The vaguest fix worth guessing from, in metres.
     *
     * This is a refusal, not a widening, and it matters most on Android 12 and
     * later where the user can hand an app *approximate* location instead of
     * precise. That is a circle one to three kilometres across. Central Mumbai
     * carries dozens of catalogued places per square kilometre, so a fix like
     * that contains hundreds of candidates — and the ranking would still
     * produce a nearest one, name it, and ask the user about it with complete
     * confidence. Being confidently wrong is the one failure this feature
     * cannot afford, because every wrong guess spends the user's trust in
     * every future guess.
     *
     * So past this, the honest answer is nothing at all.
     */
    const val MaxUsableAccuracyMeters = 150.0

    /** Whether a fix is precise enough to be worth matching against at all. */
    fun isFixUsable(accuracyMeters: Float?): Boolean =
        accuracyMeters == null || accuracyMeters <= MaxUsableAccuracyMeters

    /**
     * The widest circle this will ever search, derived rather than chosen.
     *
     * It is exactly what the vaguest *usable* fix produces, so the two numbers
     * cannot drift apart: raising what counts as usable widens this with it,
     * and no separate ceiling is left behind quietly clamping something the
     * gate above has already refused.
     */
    const val MaxRadiusMeters = MaxUsableAccuracyMeters * AccuracyMultiplier

    fun searchRadiusMeters(accuracyMeters: Float?): Double {
        val accuracy = accuracyMeters?.toDouble()?.takeIf { it > 0.0 } ?: AssumedAccuracyMeters
        return (accuracy * AccuracyMultiplier).coerceIn(MinRadiusMeters, MaxRadiusMeters)
    }

    /**
     * The best guesses at [point], nearest-effective first.
     *
     * Returns several rather than one on purpose: the user is going to be shown
     * a card, and being able to say "no, the one next door" is what makes a
     * near miss useful instead of annoying.
     */
    fun candidatesAt(
        point: GeoPoint,
        accuracyMeters: Float?,
        places: List<Place>,
        source: SuggestionSource,
        limit: Int = DefaultLimit,
    ): List<PlaceCandidate> {
        if (places.isEmpty() || limit <= 0) return emptyList()
        if (!isFixUsable(accuracyMeters)) return emptyList()
        val radius = searchRadiusMeters(accuracyMeters)

        val found = ArrayList<PlaceCandidate>()
        for (place in places) {
            val distance = point.distanceTo(place.location)
            if (distance > radius) continue
            found += PlaceCandidate(
                place = place,
                distanceMeters = distance,
                effectiveDistanceMeters = distance / priorFor(place.category, source),
            )
        }
        found.sortBy { it.effectiveDistanceMeters }
        return if (found.size <= limit) found else found.subList(0, limit).toList()
    }

    /**
     * How much likelier this kind of place is, given what noticed it.
     *
     * These are not measured — there is no corpus to measure against — so they
     * are deliberately gentle. The largest, 1.6, makes a restaurant feel about
     * 38% nearer than it is, which reorders places that were already close
     * together and cannot drag something from the far edge of the circle to the
     * top. The purpose is to break ties the way a person would, not to overrule
     * the geometry.
     *
     * Staying somewhere twenty minutes is eating or drinking far more often
     * than it is anything else. Taking a photograph is the opposite: people
     * photograph the Gateway of India and the banyan tree, and they photograph
     * their lunch, but a hundred-metre circle containing both is much likelier
     * to be about the landmark than about the third cafe on the block.
     */
    fun priorFor(category: PlaceCategory, source: SuggestionSource): Double = when (source) {
        SuggestionSource.DWELL -> when (category) {
            PlaceCategory.RESTAURANT -> 1.6
            PlaceCategory.CAFE -> 1.5
            PlaceCategory.CULTURE -> 1.2
            PlaceCategory.TOURIST -> 1.1
            PlaceCategory.PARK -> 1.0
            PlaceCategory.HIDDEN_GEM -> 0.9
        }

        SuggestionSource.PHOTO -> when (category) {
            PlaceCategory.TOURIST -> 1.6
            PlaceCategory.CULTURE -> 1.4
            PlaceCategory.PARK -> 1.3
            PlaceCategory.HIDDEN_GEM -> 1.1
            PlaceCategory.RESTAURANT -> 1.0
            PlaceCategory.CAFE -> 1.0
        }
    }

    /**
     * Five, because it is the most a notification's follow-up screen can offer
     * without becoming a search result — and if the right answer is not in the
     * nearest five, the fix was not good enough to be guessing from.
     */
    const val DefaultLimit = 5
}

/** One possible answer, with the arithmetic that ranked it left visible. */
data class PlaceCandidate(
    val place: Place,
    /** True metres, which is what the user is told. */
    val distanceMeters: Double,
    /** Metres after the category prior, which is what decided the order. */
    val effectiveDistanceMeters: Double,
)
