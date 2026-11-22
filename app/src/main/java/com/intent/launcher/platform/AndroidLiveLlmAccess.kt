package com.intent.launcher.platform

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.intent.launcher.BuildConfig
import com.intent.launcher.engine.LiveLlmAccess

/**
 * Live LLM key + opt-in for Discuss.
 *
 * - Build-time key: [BuildConfig.OPENAI_API_KEY] from Cursor/CI secret **OPENAPI_GENERAL**
 *   (canonical; fallback **OPENAI_GENERAL**) via env / gitignored `local.properties`
 *   when `-Pintent.embed.openai.key=true` — empty string in public builds.
 *   Never commit the value.
 * - Runtime key: EncryptedSharedPreferences on device (Settings paste after rotate).
 * - Opt-in: same encrypted prefs; default false — no network until enabled.
 * - Live client: shipped (OpenAI Chat Completions).
 */
class AndroidLiveLlmAccess(
    context: Context
) : LiveLlmAccess {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = createPrefs(appContext)

    override fun hasApiKeyConfigured(): Boolean =
        !resolveApiKey().isNullOrBlank()

    override fun userOptedIn(): Boolean =
        prefs.getBoolean(KEY_OPT_IN, false)

    override fun liveClientEnabled(): Boolean = true

    override fun resolveApiKey(): String? {
        val runtime = prefs.getString(KEY_RUNTIME_API, null)?.trim()
        if (!runtime.isNullOrEmpty()) return runtime
        val built = BuildConfig.OPENAI_API_KEY.trim()
        return built.takeIf { it.isNotEmpty() }
    }

    override fun setUserOptedIn(optedIn: Boolean) {
        prefs.edit().putBoolean(KEY_OPT_IN, optedIn).apply()
    }

    override fun setRuntimeApiKey(key: String) {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) {
            clearRuntimeApiKey()
            return
        }
        prefs.edit().putString(KEY_RUNTIME_API, trimmed).apply()
    }

    override fun clearRuntimeApiKey() {
        prefs.edit().remove(KEY_RUNTIME_API).apply()
    }

    companion object {
        private const val PREFS = "intent_live_llm_secure"
        private const val LEGACY_PREFS = "intent_live_llm"
        private const val KEY_OPT_IN = "user_opted_in"
        private const val KEY_RUNTIME_API = "runtime_openai_api_key"

        private fun createPrefs(context: Context): SharedPreferences {
            val encrypted = runCatching {
                val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
                EncryptedSharedPreferences.create(
                    PREFS,
                    masterKeyAlias,
                    context,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            }.getOrNull()

            val prefs = encrypted ?: context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            migrateLegacyOptIn(context, prefs)
            return prefs
        }

        /** Carry forward opt-in from the pre-encryption SharedPreferences name. */
        private fun migrateLegacyOptIn(context: Context, target: SharedPreferences) {
            if (target.contains(KEY_OPT_IN)) return
            val legacy = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
            if (!legacy.contains(KEY_OPT_IN)) return
            target.edit()
                .putBoolean(KEY_OPT_IN, legacy.getBoolean(KEY_OPT_IN, false))
                .apply()
        }
    }
}
