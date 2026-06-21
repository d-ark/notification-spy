package com.darkapps.notificationspy

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class NotificationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_EVENT_ID = "event_id"
        const val KEY_PAYLOAD = "payload"
        const val KEY_WEBHOOK_URL = "webhook_url"
        private const val MAX_ATTEMPTS = 5
    }

    private val dao = AppDatabase.getInstance(applicationContext).eventDao()

    override suspend fun doWork(): Result {
        val eventId = inputData.getString(KEY_EVENT_ID) ?: return Result.failure()
        val payload = inputData.getString(KEY_PAYLOAD) ?: return Result.failure()
        val webhookUrl = inputData.getString(KEY_WEBHOOK_URL) ?: return Result.failure()

        dao.updateStatus(eventId, EventStatus.RETRYING, runAttemptCount)

        return withContext(Dispatchers.IO) {
            try {
                val connection = URL(webhookUrl).openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                connection.doOutput = true
                connection.outputStream.use { it.write(payload.toByteArray()) }
                val code = connection.responseCode
                connection.disconnect()

                if (code in 200..299) {
                    dao.updateStatus(eventId, EventStatus.SUCCESS, runAttemptCount + 1)
                    Result.success()
                } else {
                    onDeliveryFailed(eventId)
                }
            } catch (e: Exception) {
                onDeliveryFailed(eventId)
            }
        }
    }

    private suspend fun onDeliveryFailed(eventId: String): Result {
        return if (runAttemptCount >= MAX_ATTEMPTS - 1) {
            dao.updateStatus(eventId, EventStatus.FAILED, runAttemptCount + 1)
            Result.failure()
        } else {
            Result.retry()
        }
    }
}
