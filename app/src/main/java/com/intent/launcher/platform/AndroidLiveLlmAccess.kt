package com.intent.launcher.platform

import android.content.Context
import com.intent.launcher.BuildConfig
import com.intent.launcher.engine.LiveLlmAccess

/**
 * Optional app-secret / BuildConfig wiring for a future LLM key.
 *
 * - Key presence: [BuildConfig.LLM_API_KEY_CONFIGURED] (from `llm.api.key.configured`
 *   in local.properties / CI — never embed the raw key in the APK).
 * - Opt-in: SharedPreferences flag; default false.
 * - Live client: always false in v0.0.61 — wiring must not claim LIVE.
 */
class AndroidLiveLlmAccess(
    context: Context
) : LiveLlmAccess {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun hasApiKeyConfigured(): Boolean = BuildConfig.LLM_API_KEY_CONFIGURED

    override fun userOptedIn(): Boolean =
        prefs.getBoolean(KEY_OPT_IN, false)

    override fun liveClientEnabled(): Boolean = false

    fun setUserOptedIn(optedIn: Boolean) {
        prefs.edit().putBoolean(KEY_OPT_IN, optedIn).apply()
    }

    companion object {
        private const val PREFS = "intent_live_llm"
        private const val KEY_OPT_IN = "user_opted_in"
    }
}
