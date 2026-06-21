package com.darkapps.notificationspy

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: EventEntity)

    @Query("SELECT * FROM events WHERE timestamp > :since ORDER BY timestamp DESC")
    fun getRecentEvents(since: Long): Flow<List<EventEntity>>

    @Query("UPDATE events SET status = :status, retryCount = :retryCount WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, retryCount: Int)

    @Query("DELETE FROM events WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
}
