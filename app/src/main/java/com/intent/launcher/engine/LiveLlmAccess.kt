package com.intent.launcher.engine

/**
 * Optional live-LLM wiring for Discuss.
 *
 * Keys must never ship inside the APK. A future app-secret / BuildConfig presence
 * flag can flip [hasApiKeyConfigured]; this interface alone must not claim LIVE.
 */
interface LiveLlmAccess {
    /** True when a non-empty API key is configured outside the APK (secret / local prop). */
    fun hasApiKeyConfigured(): Boolean

    /** Explicit user opt-in for cloud model suggestions in Discuss. */
    fun userOptedIn(): Boolean

    /**
     * True only when a real Discuss LLM client is shipped and safe to call.
     * Remains false in v0.0.61 so key wiring never claims LIVE by itself.
     */
    fun liveClientEnabled(): Boolean
}

/** Test / default: no key, no opt-in, no client. */
class FakeLiveLlmAccess(
    private val keyConfigured: Boolean = false,
    private val optedIn: Boolean = false,
    private val clientEnabled: Boolean = false
) : LiveLlmAccess {
    override fun hasApiKeyConfigured(): Boolean = keyConfigured
    override fun userOptedIn(): Boolean = optedIn
    override fun liveClientEnabled(): Boolean = clientEnabled
}
