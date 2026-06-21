package com.darkapps.notificationspy

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey val id: String,
    val timestamp: Long,
    val packageName: String,
    val notificationText: String,
    val webhookUrl: String,
    val payloadJson: String,
    val status: String,
    val retryCount: Int = 0
)

object EventStatus {
    const val PENDING = "PENDING"
    const val SUCCESS = "SUCCESS"
    const val FAILED = "FAILED"
    const val RETRYING = "RETRYING"
}
