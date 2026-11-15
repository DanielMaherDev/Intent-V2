package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityEntry
import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.capability.CapabilityStatus

/**
 * Honest [CapabilityRegistry.ID_LIVE_LLM] probe for Discuss.
 *
 * LIVE ([CapabilityStatus.CONNECTED]) only when key + opt-in + live client.
 * Without those, Discuss stays on labeled local heuristics — never OBSERVED
 * from model output, never auto-Complete.
 */
object LiveLlmProbe {

    const val LOCAL_HEURISTIC_LABEL = "Local heuristics"
    const val MODEL_SUGGESTION_LABEL = "Model suggestion"

    data class Observation(
        val keyConfigured: Boolean,
        val userOptedIn: Boolean,
        val liveClientEnabled: Boolean,
        /** True only when registry may treat live LLM as actionable. */
        val isLive: Boolean,
        /** Short UI banner for Conversation / Discuss. */
        val sourceLabel: String,
        val note: String
    )

    fun liveRegistry(
        access: LiveLlmAccess,
        base: List<CapabilityEntry> = CapabilityRegistry.DEFAULT_ENTRIES
    ): CapabilityRegistry {
        val status = resolveStatus(access)
        val limitNote = resolveLimitNote(access, status)
        val entries = base.map { entry ->
            if (entry.id != CapabilityRegistry.ID_LIVE_LLM) entry
            else entry.copy(status = status, limitNote = limitNote)
        }
        return CapabilityRegistry(entries)
    }

    fun observe(access: LiveLlmAccess): Observation {
        val status = resolveStatus(access)
        val isLive = status == CapabilityStatus.CONNECTED
        return Observation(
            keyConfigured = access.hasApiKeyConfigured(),
            userOptedIn = access.userOptedIn(),
            liveClientEnabled = access.liveClientEnabled(),
            isLive = isLive,
            sourceLabel = if (isLive) MODEL_SUGGESTION_LABEL else LOCAL_HEURISTIC_LABEL,
            note = resolveLimitNote(access, status)
        )
    }

    /**
     * Discuss honesty banner: always label local heuristics when not LIVE.
     */
    fun discussSourceLabel(registry: CapabilityRegistry): String =
        if (registry.canUseLiveLlm()) MODEL_SUGGESTION_LABEL else LOCAL_HEURISTIC_LABEL

    fun resolveStatus(access: LiveLlmAccess): CapabilityStatus = when {
        !access.hasApiKeyConfigured() -> CapabilityStatus.NOT_SUPPORTED
        !access.userOptedIn() -> CapabilityStatus.DENIED
        !access.liveClientEnabled() -> CapabilityStatus.TEMPORARILY_UNAVAILABLE
        else -> CapabilityStatus.CONNECTED
    }

    private fun resolveLimitNote(access: LiveLlmAccess, status: CapabilityStatus): String =
        when (status) {
            CapabilityStatus.NOT_SUPPORTED ->
                "No LLM API key configured. Discuss uses labeled local heuristics " +
                    "(scripted chips / templates) — not a live model. Never writes OBSERVED " +
                    "from model output; never auto-Completes sessions."
            CapabilityStatus.DENIED ->
                "An LLM key may be available, but live Discuss is off until you opt in. " +
                    "Showing local heuristics only — not model suggestions."
            CapabilityStatus.TEMPORARILY_UNAVAILABLE ->
                "Key and opt-in are ready, but the live Discuss client is not shipped in this " +
                    "build. Still using labeled local heuristics — not claiming LIVE."
            CapabilityStatus.CONNECTED ->
                "Live LLM Discuss is connected. Model output is suggestion-only: user must " +
                    "confirm before SAID facts or FlowCommands. Never writes OBSERVED from " +
                    "the model; never auto-Completes sessions."
            else ->
                "Discuss path honesty unknown — treat replies as local heuristics " +
                    "(keyConfigured=${access.hasApiKeyConfigured()})."
        }
}
