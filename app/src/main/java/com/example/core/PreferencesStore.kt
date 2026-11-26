package com.example.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.AiModelChoice
import com.example.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.vybDataStore: DataStore<Preferences> by preferencesDataStore(name = "vyb_preferences")

class PreferencesStore(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val AI_MODEL = stringPreferencesKey("ai_model")
        val BOOKMARKS = stringSetPreferencesKey("bookmarked_post_ids")
        val PRIVATE_LOCATION_ENABLED = booleanPreferencesKey("private_location_enabled")
        val FCM_TOKEN = stringPreferencesKey("fcm_token")
        val LAST_BG_SYNC_EPOCH_MS = longPreferencesKey("last_bg_sync_epoch_ms")
        val NOTIFIED_EVENT_KEYS = stringSetPreferencesKey("notified_event_keys")
        val REQUESTED_PERMISSIONS = stringSetPreferencesKey("requested_permissions")
    }

    private val safeData: Flow<Preferences> = context.vybDataStore.data.catch { exception ->
        if (exception is IOException) {
            emit(emptyPreferences())
        } else {
            throw exception
        }
    }

    val themeMode: Flow<ThemeMode> = safeData.map { prefs ->
        val raw = prefs[Keys.THEME_MODE]
        ThemeMode.entries.firstOrNull { it.name == raw } ?: ThemeMode.SYSTEM
    }

    val aiModel: Flow<AiModelChoice> = safeData.map { prefs ->
        val raw = prefs[Keys.AI_MODEL]
        AiModelChoice.entries.firstOrNull { it.name == raw } ?: AiModelChoice.LITE
    }

    val bookmarkedPostIds: Flow<Set<String>> = safeData.map { prefs ->
        prefs[Keys.BOOKMARKS] ?: emptySet()
    }

    val privateLocationEnabled: Flow<Boolean> = safeData.map { prefs ->
        prefs[Keys.PRIVATE_LOCATION_ENABLED] ?: false
    }

    val fcmToken: Flow<String?> = safeData.map { prefs ->
        prefs[Keys.FCM_TOKEN]?.takeIf { it.isNotBlank() }
    }

    val requestedPermissions: Flow<Set<String>> = safeData.map { prefs ->
        prefs[Keys.REQUESTED_PERMISSIONS] ?: emptySet()
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.vybDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setAiModel(choice: AiModelChoice) {
        context.vybDataStore.edit { it[Keys.AI_MODEL] = choice.name }
    }

    suspend fun toggleBookmark(postId: String) {
        context.vybDataStore.edit { prefs ->
            val current = prefs[Keys.BOOKMARKS] ?: emptySet()
            prefs[Keys.BOOKMARKS] = if (postId in current) current - postId else current + postId
        }
    }

    suspend fun setBookmarkedPostIds(ids: Set<String>) {
        context.vybDataStore.edit { prefs ->
            prefs[Keys.BOOKMARKS] = ids
        }
    }

    suspend fun setPrivateLocationEnabled(enabled: Boolean) {
        context.vybDataStore.edit { it[Keys.PRIVATE_LOCATION_ENABLED] = enabled }
    }

    suspend fun setFcmToken(token: String?) {
        context.vybDataStore.edit { prefs ->
            if (token.isNullOrBlank()) {
                prefs.remove(Keys.FCM_TOKEN)
            } else {
                prefs[Keys.FCM_TOKEN] = token.trim().take(512)
            }
        }
    }

    suspend fun getLastBgSyncEpochMs(): Long {
        return safeData.first()[Keys.LAST_BG_SYNC_EPOCH_MS] ?: 0L
    }

    suspend fun setLastBgSyncEpochMs(epochMs: Long) {
        context.vybDataStore.edit { it[Keys.LAST_BG_SYNC_EPOCH_MS] = epochMs }
    }

    suspend fun markEventNotifiedIfNew(eventKey: String): Boolean {
        var isNew = false
        context.vybDataStore.edit { prefs ->
            val current = prefs[Keys.NOTIFIED_EVENT_KEYS] ?: emptySet()
            if (eventKey !in current) {
                isNew = true
                val trimmed = (current + eventKey).toList().takeLast(80).toSet()
                prefs[Keys.NOTIFIED_EVENT_KEYS] = trimmed
            }
        }
        return isNew
    }

    suspend fun recordRequestedPermissions(permissions: Collection<String>) {
        if (permissions.isEmpty()) return
        context.vybDataStore.edit { prefs ->
            val current = prefs[Keys.REQUESTED_PERMISSIONS] ?: emptySet()
            prefs[Keys.REQUESTED_PERMISSIONS] = current + permissions
        }
    }
}
