package com.intent.launcher.engine

/**
 * Platform speech-to-text availability (e.g. [android.speech.SpeechRecognizer]).
 * Does not imply transcription quality or an on-device LLM.
 */
interface SpeechToTextAccess {
    fun isRecognitionAvailable(): Boolean
}

class FakeSpeechToTextAccess(
    var available: Boolean = true
) : SpeechToTextAccess {
    override fun isRecognitionAvailable(): Boolean = available
}
