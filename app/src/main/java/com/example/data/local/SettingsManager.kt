package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "m4ditv_settings")

class SettingsManager(private val context: Context) {

    companion object {
        val DEFAULT_QUALITY = stringPreferencesKey("default_quality") // "Auto", "480p", "720p", "1080p"
        val BUFFER_SIZE = stringPreferencesKey("buffer_size") // "Small", "Medium", "Large"
        val HARDWARE_ACCEL = booleanPreferencesKey("hardware_accel")
        val RECONNECT_ON_ERROR = booleanPreferencesKey("reconnect_on_error")
        val MAX_RETRIES = intPreferencesKey("max_retries")
        val RETRY_TIMEOUT = stringPreferencesKey("retry_timeout") // "5s", "10s", "30s"

        // Display
        val THEME = stringPreferencesKey("theme") // "Dark"
        val LIST_VIEW_TYPE = stringPreferencesKey("list_view_type") // "List", "Grid"
        val SHOW_CHANNEL_NUMBER = booleanPreferencesKey("show_channel_number")
        val SHOW_GROUP_BADGE = booleanPreferencesKey("show_group_badge")

        // Playlist
        val AUTO_REFRESH_INTERVAL = stringPreferencesKey("auto_refresh_interval") // "Never", "6h", "12h", "24h"
        val REFRESH_WIFI_ONLY = booleanPreferencesKey("refresh_wifi_only")

        // EPG/PIN
        val PARENTAL_PIN = stringPreferencesKey("parental_pin") // 4-digit PIN, default empty
    }

    val settingsFlow: Flow<SettingsData> = context.dataStore.data.map { prefs ->
        SettingsData(
            defaultQuality = prefs[DEFAULT_QUALITY] ?: "Auto",
            bufferSize = prefs[BUFFER_SIZE] ?: "Medium",
            hardwareAccel = prefs[HARDWARE_ACCEL] ?: true,
            reconnectOnError = prefs[RECONNECT_ON_ERROR] ?: true,
            maxRetries = prefs[MAX_RETRIES] ?: 3,
            retryTimeout = prefs[RETRY_TIMEOUT] ?: "10s",
            theme = prefs[THEME] ?: "Dark",
            listViewType = prefs[LIST_VIEW_TYPE] ?: "List",
            showChannelNumber = prefs[SHOW_CHANNEL_NUMBER] ?: true,
            showGroupBadge = prefs[SHOW_GROUP_BADGE] ?: true,
            autoRefreshInterval = prefs[AUTO_REFRESH_INTERVAL] ?: "12h",
            refreshWifiOnly = prefs[REFRESH_WIFI_ONLY] ?: true,
            parentalPin = prefs[PARENTAL_PIN] ?: ""
        )
    }

    suspend fun updateQuality(quality: String) {
        context.dataStore.edit { it[DEFAULT_QUALITY] = quality }
    }

    suspend fun updateBufferSize(size: String) {
        context.dataStore.edit { it[BUFFER_SIZE] = size }
    }

    suspend fun updateHardwareAccel(enabled: Boolean) {
        context.dataStore.edit { it[HARDWARE_ACCEL] = enabled }
    }

    suspend fun updateReconnectOnError(enabled: Boolean) {
        context.dataStore.edit { it[RECONNECT_ON_ERROR] = enabled }
    }

    suspend fun updateMaxRetries(retries: Int) {
        context.dataStore.edit { it[MAX_RETRIES] = retries }
    }

    suspend fun updateRetryTimeout(timeout: String) {
        context.dataStore.edit { it[RETRY_TIMEOUT] = timeout }
    }

    suspend fun updateListViewType(type: String) {
        context.dataStore.edit { it[LIST_VIEW_TYPE] = type }
    }

    suspend fun updateShowChannelNumber(show: Boolean) {
        context.dataStore.edit { it[SHOW_CHANNEL_NUMBER] = show }
    }

    suspend fun updateShowGroupBadge(show: Boolean) {
        context.dataStore.edit { it[SHOW_GROUP_BADGE] = show }
    }

    suspend fun updateAutoRefreshInterval(interval: String) {
        context.dataStore.edit { it[AUTO_REFRESH_INTERVAL] = interval }
    }

    suspend fun updateRefreshWifiOnly(wifiOnly: Boolean) {
        context.dataStore.edit { it[REFRESH_WIFI_ONLY] = wifiOnly }
    }

    suspend fun updateParentalPin(pin: String) {
        context.dataStore.edit { it[PARENTAL_PIN] = pin }
    }
}

data class SettingsData(
    val defaultQuality: String,
    val bufferSize: String,
    val hardwareAccel: Boolean,
    val reconnectOnError: Boolean,
    val maxRetries: Int,
    val retryTimeout: String,
    val theme: String,
    val listViewType: String,
    val showChannelNumber: Boolean,
    val showGroupBadge: Boolean,
    val autoRefreshInterval: String,
    val refreshWifiOnly: Boolean,
    val parentalPin: String
)
