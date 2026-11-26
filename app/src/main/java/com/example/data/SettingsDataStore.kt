package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "meetpilot_prefs")

data class UserPreferences(
    val alertSoundOnConnect: Boolean = true,
    val alertVibrateOnConnect: Boolean = true,
    val defaultAutoMuteMic: Boolean = true,
    val defaultAutoCamOff: Boolean = true,
    val defaultRetryIntervalSeconds: Int = 15,
    val defaultPreferredGmail: String = "",
    val savedGmails: List<String> = emptyList()
)

class SettingsDataStore(private val context: Context) {
    private object Keys {
        val ALERT_SOUND = booleanPreferencesKey("alert_sound_on_connect")
        val ALERT_VIBRATE = booleanPreferencesKey("alert_vibrate_on_connect")
        val DEFAULT_MUTE = booleanPreferencesKey("default_auto_mute_mic")
        val DEFAULT_CAM_OFF = booleanPreferencesKey("default_auto_cam_off")
        val DEFAULT_RETRY_SEC = intPreferencesKey("default_retry_interval_seconds")
        val DEFAULT_GMAIL = stringPreferencesKey("default_preferred_gmail")
        val SAVED_GMAILS = stringSetPreferencesKey("saved_gmails")
    }

    val preferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            alertSoundOnConnect = prefs[Keys.ALERT_SOUND] ?: true,
            alertVibrateOnConnect = prefs[Keys.ALERT_VIBRATE] ?: true,
            defaultAutoMuteMic = prefs[Keys.DEFAULT_MUTE] ?: true,
            defaultAutoCamOff = prefs[Keys.DEFAULT_CAM_OFF] ?: true,
            defaultRetryIntervalSeconds = prefs[Keys.DEFAULT_RETRY_SEC] ?: 15,
            defaultPreferredGmail = prefs[Keys.DEFAULT_GMAIL] ?: "",
            savedGmails = (prefs[Keys.SAVED_GMAILS] ?: emptySet()).sorted()
        )
    }

    suspend fun setAlertSound(enabled: Boolean) {
        context.dataStore.edit { it[Keys.ALERT_SOUND] = enabled }
    }

    suspend fun setAlertVibrate(enabled: Boolean) {
        context.dataStore.edit { it[Keys.ALERT_VIBRATE] = enabled }
    }

    suspend fun setDefaultAutoMute(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DEFAULT_MUTE] = enabled }
    }

    suspend fun setDefaultAutoCamOff(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DEFAULT_CAM_OFF] = enabled }
    }

    suspend fun setDefaultRetrySeconds(seconds: Int) {
        context.dataStore.edit { it[Keys.DEFAULT_RETRY_SEC] = seconds }
    }

    suspend fun setDefaultPreferredGmail(email: String) {
        val clean = email.trim().lowercase()
        context.dataStore.edit { prefs ->
            prefs[Keys.DEFAULT_GMAIL] = clean
            if (clean.contains("@")) {
                val existing = prefs[Keys.SAVED_GMAILS] ?: emptySet()
                prefs[Keys.SAVED_GMAILS] = existing + clean
            }
        }
    }

    suspend fun rememberGmail(email: String) {
        val clean = email.trim().lowercase()
        if (!clean.contains("@")) return
        context.dataStore.edit { prefs ->
            val existing = prefs[Keys.SAVED_GMAILS] ?: emptySet()
            prefs[Keys.SAVED_GMAILS] = existing + clean
        }
    }
}
