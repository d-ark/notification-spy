package com.darkapps.notificationspy

import android.content.ComponentName
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.work.*
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import java.util.UUID
import java.util.concurrent.TimeUnit

class NotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var prefs: AppPreferences
    private lateinit var dao: EventDao

    @Volatile private var webhookUrl: String = ""
    @Volatile private var allowedPackages: Set<String> = emptySet()

    override fun onCreate() {
        super.onCreate()
        prefs = AppPreferences(applicationContext)
        dao = AppDatabase.getInstance(applicationContext).eventDao()

        scope.launch { prefs.webhookUrl.collect { webhookUrl = it } }
        scope.launch { prefs.allowedPackages.collect { allowedPackages = it } }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onListenerDisconnected() {
        requestRebind(ComponentName(this, NotificationListener::class.java))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn?.notification?.let { notification ->
            if (sbn.packageName !in allowedPackages) return
            val url = webhookUrl
            if (url.isBlank()) return

            val text = notification.extras.getCharSequence("android.text")?.toString() ?: ""
            val title = notification.extras.getCharSequence("android.title")?.toString() ?: ""
            if (text.isBlank() && title.isBlank()) return

            val eventId = UUID.randomUUID().toString()
            val payload = buildPayload(notification.extras, sbn.packageName, text, title, eventId)

            scope.launch {
                dao.insert(
                    EventEntity(
                        id = eventId,
                        timestamp = System.currentTimeMillis(),
                        packageName = sbn.packageName,
                        notificationText = if (text.isNotBlank()) text else title,
                        webhookUrl = url,
                        payloadJson = payload,
                        status = EventStatus.PENDING
                    )
                )

                val request = OneTimeWorkRequestBuilder<NotificationWorker>()
                    .setInputData(
                        workDataOf(
                            NotificationWorker.KEY_EVENT_ID to eventId,
                            NotificationWorker.KEY_PAYLOAD to payload,
                            NotificationWorker.KEY_WEBHOOK_URL to url
                        )
                    )
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build()
                    )
                    .build()

                WorkManager.getInstance(applicationContext).enqueue(request)
            }
        }
    }

    private fun buildPayload(extras: Bundle, pkgName: String, text: String, title: String, eventId: String): String {
        val details = mutableMapOf<String, Any>()
        for (key in extras.keySet()) {
            details[key] = extras.getString(key) ?: "null"
        }
        return Gson().toJson(
            mapOf(
                "eventId" to eventId,
                "pkgName" to pkgName,
                "title" to title,
                "text" to text,
                "timestamp" to System.currentTimeMillis(),
                "details" to details
            )
        )
    }
}
