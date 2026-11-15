package com.intent.launcher.engine

import com.intent.launcher.domain.FlowCommand
import com.intent.launcher.domain.ThoughtCapture
import com.intent.launcher.store.IntentionFlowStore
import java.util.UUID

/**
 * Quick thought capture — same agent / command path, not a second bot.
 * Stores raw text with SAID provenance; no invented summary.
 */
object CaptureThought {

    const val SOURCE_FLOATER = "floater"
    const val SOURCE_HOME = "home"
    const val SOURCE_VOICE = "voice"
    const val SOURCE_TRIAGE = "triage"

    data class Result(
        val ok: Boolean,
        val thought: ThoughtCapture?,
        val confirmation: String
    )

    /**
     * Resolve intention link: active intention, else first intention, else null.
     */
    fun resolveIntentionId(store: IntentionFlowStore): String? {
        val snap = store.snapshot()
        val active = snap.activeIntentionId
        if (active != null && snap.intentions.any { it.id == active }) return active
        return snap.intentions.firstOrNull()?.id
    }

    fun capture(
        store: IntentionFlowStore,
        text: String,
        source: String,
        nowEpochMs: Long,
        thoughtId: String = "thought-${UUID.randomUUID()}",
        intentionId: String? = resolveIntentionId(store)
    ): Result {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return Result(
                ok = false,
                thought = null,
                confirmation = "Nothing to save — type or speak a thought first."
            )
        }
        store.apply(
            FlowCommand.CaptureThought(
                thoughtId = thoughtId,
                intentionId = intentionId,
                text = trimmed,
                source = source,
                nowEpochMs = nowEpochMs
            )
        )
        val saved = store.snapshot().thoughtCaptures.firstOrNull { it.id == thoughtId }
        return Result(
            ok = saved != null,
            thought = saved,
            confirmation = "Thought saved — raw text only, no summary. " +
                "Triage or Conversation can classify later."
        )
    }
}
