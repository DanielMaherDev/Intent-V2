package com.intent.launcher.engine

/**
 * Optional live-LLM wiring for Discuss.
 *
 * Keys must never ship inside committed / public APKs. Presence may come from:
 * - a gitignored `local.properties` / CI-injected BuildConfig string (empty in public builds)
 * - a runtime key stored in EncryptedSharedPreferences on device
 *
 * LIVE Discuss requires key + user opt-in + shipped client.
 */
interface LiveLlmAccess {
    /** True when a non-empty API key is available (build-time and/or runtime). */
    fun hasApiKeyConfigured(): Boolean

    /** Explicit user opt-in for cloud model suggestions in Discuss. */
    fun userOptedIn(): Boolean

    /**
     * True when a real Discuss LLM client is shipped and safe to call.
     * OpenAI Chat Completions client is enabled in this build.
     */
    fun liveClientEnabled(): Boolean

    /**
     * Resolves the raw API key for a live call. Never log or put this in UI copy.
     * Prefer runtime EncryptedSharedPreferences; else build-injected value.
     */
    fun resolveApiKey(): String? = null

    fun setUserOptedIn(optedIn: Boolean) {}

    /** Store a rotated runtime key on-device (EncryptedSharedPreferences). */
    fun setRuntimeApiKey(key: String) {}

    fun clearRuntimeApiKey() {}
}

/** Test / default: no key, no opt-in, no client unless explicitly set. */
class FakeLiveLlmAccess(
    private val keyConfigured: Boolean = false,
    private val optedIn: Boolean = false,
    private val clientEnabled: Boolean = false,
    private val apiKey: String? = null
) : LiveLlmAccess {
    private var opted = optedIn
    private var key: String? = apiKey?.trim()?.takeIf { it.isNotEmpty() }

    override fun hasApiKeyConfigured(): Boolean =
        keyConfigured || !key.isNullOrBlank()

    override fun userOptedIn(): Boolean = opted

    override fun liveClientEnabled(): Boolean = clientEnabled

    override fun resolveApiKey(): String? = key

    override fun setUserOptedIn(optedIn: Boolean) {
        opted = optedIn
    }

    override fun setRuntimeApiKey(key: String) {
        this.key = key.trim().takeIf { it.isNotEmpty() }
    }

    override fun clearRuntimeApiKey() {
        key = null
    }
}
