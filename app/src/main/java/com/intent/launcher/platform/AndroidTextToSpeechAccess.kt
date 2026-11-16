package com.intent.launcher.platform

import android.content.Context
import android.speech.tts.TextToSpeech
import com.intent.launcher.engine.TextToSpeechAccess
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Thin Android TTS wrapper. Availability does not imply OEM barge-in — Conversation
 * still cancels via [com.intent.launcher.engine.InterruptedVoice].
 */
class AndroidTextToSpeechAccess(
    context: Context
) : TextToSpeechAccess {
    private val appContext = context.applicationContext
    private val ready = AtomicBoolean(false)
    private val speaking = AtomicBoolean(false)
    private var tts: TextToSpeech? = null

    init {
        tts = TextToSpeech(appContext) { status ->
            ready.set(status == TextToSpeech.SUCCESS)
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
            }
        }
    }

    override fun isAvailable(): Boolean = ready.get() && tts != null

    override fun speak(text: String, utteranceId: String): Boolean {
        val engine = tts ?: return false
        if (!ready.get() || text.isBlank()) return false
        speaking.set(true)
        val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        if (result != TextToSpeech.SUCCESS) {
            speaking.set(false)
            return false
        }
        return true
    }

    override fun stop() {
        tts?.stop()
        speaking.set(false)
    }

    override fun isSpeaking(): Boolean = speaking.get() || (tts?.isSpeaking == true)
}
