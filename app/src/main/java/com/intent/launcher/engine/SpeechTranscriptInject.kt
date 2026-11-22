package com.intent.launcher.engine

/**
 * Offline §8.2 / Gate D2 instrumentation — inject a speech transcript without
 * platform STT or live OpenAI.
 *
 * Production-safe honesty: the hook is always labeled as injected (never claimed
 * as mic STT). Same downstream path as real STT → [AdaptiveAnswerPath.submitFromVoice]
 * / [ConversationCoordinator.submitVoiceTranscript] via [com.intent.launcher.ui.LauncherViewModel.onVoiceTranscript].
 *
 * AVD / adb:
 * ```
 * adb shell am start -n com.intent.launcher/.MainActivity \
 *   -a com.intent.launcher.INJECT_SPEECH_TRANSCRIPT \
 *   --es com.intent.launcher.extra.SPEECH_TRANSCRIPT "Spanish"
 * ```
 */
object SpeechTranscriptInject {
    const val ACTION = "com.intent.launcher.INJECT_SPEECH_TRANSCRIPT"
    const val EXTRA_TRANSCRIPT = "com.intent.launcher.extra.SPEECH_TRANSCRIPT"
    const val HONESTY_PREFIX =
        "Injected speech transcript (offline test — not platform STT / OpenAI)."

    fun transcriptFromIntent(action: String?, transcriptExtra: String?): String? {
        if (action != ACTION) return null
        val trimmed = transcriptExtra?.trim().orEmpty()
        return trimmed.ifBlank { null }
    }

    fun prependHonesty(note: String?): String =
        if (note.isNullOrBlank()) HONESTY_PREFIX else "$HONESTY_PREFIX $note"
}
