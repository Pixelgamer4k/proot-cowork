package com.proot.cowork.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.proot.cowork.userland.UserlandConfig
import com.proot.cowork.userland.UserlandMigration

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "proot_cowork_prefs")

data class LlmConfig(
    val baseUrl: String = "https://openrouter.ai/api/v1",
    val apiKey: String = "",
    val model: String = "openrouter/owl-alpha",
)

data class RootfsState(
    val isInstalled: Boolean = false,
    val isImporting: Boolean = false,
    val importProgress: Float = 0f,
    val distroName: String = "ubuntu",
)

class SettingsRepository(private val context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val securePrefs = EncryptedSharedPreferences.create(
        context,
        "proot_cowork_secure",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private object Keys {
        val BASE_URL = stringPreferencesKey("base_url")
        val MODEL = stringPreferencesKey("model")
        val API_KEY = "api_key"
        val ROOTFS_INSTALLED = booleanPreferencesKey("rootfs_installed")
        val ROOTFS_NAME = stringPreferencesKey("rootfs_name")
        val IMPORTING = booleanPreferencesKey("rootfs_importing")
        val IMPORT_PROGRESS = floatPreferencesKey("rootfs_import_progress")
    }

    val llmConfig: Flow<LlmConfig> = context.dataStore.data.map { prefs ->
        LlmConfig(
            baseUrl = prefs[Keys.BASE_URL] ?: "https://openrouter.ai/api/v1",
            apiKey = securePrefs.getString(Keys.API_KEY, "") ?: "",
            model = prefs[Keys.MODEL] ?: "openrouter/owl-alpha",
        )
    }

    val rootfsState: Flow<RootfsState> = context.dataStore.data.map { prefs ->
        RootfsState(
            isInstalled = prefs[Keys.ROOTFS_INSTALLED] == true,
            isImporting = prefs[Keys.IMPORTING] == true,
            importProgress = prefs[Keys.IMPORT_PROGRESS] ?: 0f,
            distroName = prefs[Keys.ROOTFS_NAME] ?: "ubuntu",
        )
    }

    suspend fun saveLlmConfig(baseUrl: String, apiKey: String, model: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.BASE_URL] = baseUrl
            prefs[Keys.MODEL] = model
        }
        securePrefs.edit().putString(Keys.API_KEY, apiKey).apply()
    }

    suspend fun setImporting(importing: Boolean, progress: Float = 0f) {
        context.dataStore.edit { prefs ->
            prefs[Keys.IMPORTING] = importing
            prefs[Keys.IMPORT_PROGRESS] = progress
        }
    }

    suspend fun setRootfsInstalled(name: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ROOTFS_INSTALLED] = true
            prefs[Keys.ROOTFS_NAME] = name
            prefs[Keys.IMPORTING] = false
            prefs[Keys.IMPORT_PROGRESS] = 1f
        }
    }

    suspend fun clearRootfsInstalled() {
        context.dataStore.edit { prefs ->
            prefs[Keys.ROOTFS_INSTALLED] = false
            prefs[Keys.IMPORTING] = false
            prefs[Keys.IMPORT_PROGRESS] = 0f
        }
    }

    suspend fun clearImportingState() {
        context.dataStore.edit { prefs ->
            prefs[Keys.IMPORTING] = false
            prefs[Keys.IMPORT_PROGRESS] = 0f
        }
    }

    /** Repair UI when rootfs files exist but the installed flag was lost (e.g. after debug import). */
    suspend fun ensureRootfsInstalledIfPresent(rootfsDir: java.io.File) {
        context.dataStore.edit { prefs ->
            if (prefs[Keys.ROOTFS_INSTALLED] != true) {
                prefs[Keys.ROOTFS_INSTALLED] = true
                if (prefs[Keys.ROOTFS_NAME].isNullOrBlank()) {
                    prefs[Keys.ROOTFS_NAME] = "ubuntu"
                }
            }
        }
    }

    fun getRootfsDir(): java.io.File {
        UserlandMigration.migrateRootfsLayout(context.filesDir)
        return context.filesDir.resolve(UserlandConfig.FILESYSTEM_DIR)
    }

    fun getRootfsPartialDir() = context.filesDir.resolve("rootfs.partial")

    fun getSkillsDir() = context.filesDir.resolve("skills")

    fun getArtifactsDir() = context.filesDir.resolve("artifacts").also { it.mkdirs() }
}
