package com.darkapps.notificationspy

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_prefs")

class AppPreferences(private val context: Context) {
    companion object {
        private val WEBHOOK_URL = stringPreferencesKey("webhook_url")
        private val ALLOWED_PACKAGES = stringSetPreferencesKey("allowed_packages")
    }

    val webhookUrl: Flow<String> = context.dataStore.data.map { it[WEBHOOK_URL] ?: "" }
    val allowedPackages: Flow<Set<String>> = context.dataStore.data.map { it[ALLOWED_PACKAGES] ?: emptySet() }

    suspend fun setWebhookUrl(url: String) {
        context.dataStore.edit { it[WEBHOOK_URL] = url }
    }

    suspend fun setAllowedPackages(packages: Set<String>) {
        context.dataStore.edit { it[ALLOWED_PACKAGES] = packages }
    }
}
