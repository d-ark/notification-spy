package com.darkapps.notificationspy

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.TimeUnit

data class InstalledApp(val packageName: String, val appName: String)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = AppPreferences(application)
    private val dao = AppDatabase.getInstance(application).eventDao()

    val webhookUrl: StateFlow<String> = prefs.webhookUrl
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val allowedPackages: StateFlow<Set<String>> = prefs.allowedPackages
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val events: StateFlow<List<EventEntity>> = dao
        .getRecentEvents(System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val installedApps: StateFlow<List<InstalledApp>> = _installedApps.asStateFlow()

    val packageNameToAppName: StateFlow<Map<String, String>> = _installedApps
        .map { apps -> apps.associate { it.packageName to it.appName } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _installedApps.value = loadInstalledApps()
            dao.deleteOlderThan(System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000)
        }
    }

    private fun loadInstalledApps(): List<InstalledApp> {
        val pm = getApplication<Application>().packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        return pm.queryIntentActivities(intent, 0)
            .map { InstalledApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .distinctBy { it.packageName }
            .sortedBy { it.appName }
    }

    val pendingWebhookUrl = MutableStateFlow<String?>(null)

    fun proposeWebhookUrl(url: String) {
        pendingWebhookUrl.value = url.trim()
    }

    fun confirmPendingWebhookUrl() {
        pendingWebhookUrl.value?.let { saveWebhookUrl(it) }
        pendingWebhookUrl.value = null
    }

    fun discardPendingWebhookUrl() {
        pendingWebhookUrl.value = null
    }

    fun saveWebhookUrl(url: String) {
        viewModelScope.launch { prefs.setWebhookUrl(url.trim()) }
    }

    fun toggleApp(packageName: String, enabled: Boolean) {
        viewModelScope.launch {
            val updated = allowedPackages.value.toMutableSet()
            if (enabled) updated.add(packageName) else updated.remove(packageName)
            prefs.setAllowedPackages(updated)
        }
    }

    fun seedDummyData() {
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val h = 3_600_000L
            val url = webhookUrl.value.ifBlank { "https://example.com/webhook" }

            listOf(
                EventEntity(UUID.randomUUID().toString(), now - 1 * h,  "com.skywatcher.weather", "Rain expected tomorrow afternoon. Umbrella recommended.", url, "{}", EventStatus.SUCCESS,  retryCount = 1),
                EventEntity(UUID.randomUUID().toString(), now - 2 * h,  "com.stepfit.tracker",    "Goal reached! You hit 10,000 steps today.",               url, "{}", EventStatus.SUCCESS,  retryCount = 1),
                EventEntity(UUID.randomUUID().toString(), now - 3 * h,  "com.parcelpath.delivery","Your package is out for delivery. Est. 2–4 PM.",           url, "{}", EventStatus.FAILED,   retryCount = 5),
                EventEntity(UUID.randomUUID().toString(), now - 5 * h,  "com.homehub.smarthome",  "Front door sensor triggered at 14:32.",                    url, "{}", EventStatus.SUCCESS,  retryCount = 1),
                EventEntity(UUID.randomUUID().toString(), now - 8 * h,  "com.taskly.reminders",   "Reminder: Water the plants",                               url, "{}", EventStatus.RETRYING, retryCount = 2),
                EventEntity(UUID.randomUUID().toString(), now - 12 * h, "com.skywatcher.weather", "UV index is high today. Don't forget sunscreen.",          url, "{}", EventStatus.SUCCESS,  retryCount = 1),
                EventEntity(UUID.randomUUID().toString(), now - 24 * h, "com.parcelpath.delivery","Delivery attempt failed. Rescheduled for tomorrow.",        url, "{}", EventStatus.FAILED,   retryCount = 5),
                EventEntity(UUID.randomUUID().toString(), now - 36 * h, "com.taskly.reminders",   "Reminder: Team standup in 10 minutes",                     url, "{}", EventStatus.SUCCESS,  retryCount = 1),
            ).forEach { dao.insert(it) }
        }
    }

    fun retryEvent(event: EventEntity) {
        viewModelScope.launch {
            dao.updateStatus(event.id, EventStatus.PENDING, 0)
            val request = OneTimeWorkRequestBuilder<NotificationWorker>()
                .setInputData(
                    workDataOf(
                        NotificationWorker.KEY_EVENT_ID to event.id,
                        NotificationWorker.KEY_PAYLOAD to event.payloadJson,
                        NotificationWorker.KEY_WEBHOOK_URL to event.webhookUrl
                    )
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build()
            WorkManager.getInstance(getApplication()).enqueue(request)
        }
    }
}
