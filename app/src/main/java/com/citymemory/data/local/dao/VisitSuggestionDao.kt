package com.citymemory.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.citymemory.data.local.entities.VisitSuggestionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VisitSuggestionDao {

    /** Newest first: the thing that just happened is the thing to answer first. */
    @Query(
        "SELECT * FROM visit_suggestions WHERE status = 'pending' ORDER BY detectedAt DESC",
    )
    fun observePending(): Flow<List<VisitSuggestionEntity>>

    @Query("SELECT * FROM visit_suggestions WHERE id = :id")
    suspend fun get(id: String): VisitSuggestionEntity?

    @Query("SELECT COUNT(*) FROM visit_suggestions WHERE status = 'pending'")
    suspend fun pendingCount(): Int

    /**
     * The most recent row for a place whatever its status, which is what the
     * dedupe rules are written against — a dismissal has to be as visible as a
     * pending question or the detector re-asks it forever.
     */
    @Query(
        "SELECT * FROM visit_suggestions WHERE placeId = :placeId " +
            "ORDER BY detectedAt DESC LIMIT 1",
    )
    suspend fun latestFor(placeId: String): VisitSuggestionEntity?

    @Insert
    suspend fun insert(suggestion: VisitSuggestionEntity)

    @Query("UPDATE visit_suggestions SET status = :status, resolvedAt = :at WHERE id = :id")
    suspend fun setStatus(id: String, status: String, at: Long)

    /**
     * Clears everything still pending for a place.
     *
     * Called when a place is marked visited by hand: whatever the app was about
     * to ask has just been answered by the user going and doing it themselves,
     * and a notification about it afterwards would be absurd.
     */
    @Query(
        "UPDATE visit_suggestions SET status = 'dismissed', resolvedAt = :at " +
            "WHERE placeId = :placeId AND status = 'pending'",
    )
    suspend fun dismissPendingFor(placeId: String, at: Long)
}
