package com.intent.launcher.engine

/**
 * Optional platform text-to-speech. Conversation barge-in must work even when
 * speech synthesis is unavailable — never invent OEM silent barge-in.
 */
interface TextToSpeechAccess {
    /** True when the platform can attempt spoken playback. */
    fun isAvailable(): Boolean

    /**
     * Begin speaking [text]. Returns false when the engine cannot start.
     * Callers must still own the interrupt / cancel turn path.
     */
    fun speak(text: String, utteranceId: String): Boolean

    /** Stop any in-flight utterance immediately. */
    fun stop()

    fun isSpeaking(): Boolean
}

/** JVM / tests — never speaks; interrupt UI still exercises [InterruptedVoice]. */
class FakeTextToSpeechAccess(
    private val available: Boolean = false
) : TextToSpeechAccess {
    private var speaking = false
    var lastUtteranceId: String? = null
        private set
    var speakCalls: Int = 0
        private set
    var stopCalls: Int = 0
        private set

    override fun isAvailable(): Boolean = available

    override fun speak(text: String, utteranceId: String): Boolean {
        speakCalls += 1
        if (!available || text.isBlank()) {
            speaking = false
            return false
        }
        lastUtteranceId = utteranceId
        speaking = true
        return true
    }

    override fun stop() {
        stopCalls += 1
        speaking = false
    }

    override fun isSpeaking(): Boolean = speaking
}
