package com.intent.launcher.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Offline §8.2 inject hook — honesty-labeled, not platform STT. */
class SpeechTranscriptInjectTest {

    @Test
    fun transcriptFromIntent_readsExtraOnlyForInjectAction() {
        assertEquals(
            "Spanish",
            SpeechTranscriptInject.transcriptFromIntent(
                SpeechTranscriptInject.ACTION,
                "  Spanish  "
            )
        )
        assertNull(
            SpeechTranscriptInject.transcriptFromIntent(
                SpeechTranscriptInject.ACTION,
                "   "
            )
        )
        assertNull(
            SpeechTranscriptInject.transcriptFromIntent(
                "android.intent.action.MAIN",
                "Spanish"
            )
        )
    }

    @Test
    fun prependHonesty_neverClaimsPlatformStt() {
        val note = SpeechTranscriptInject.prependHonesty("Answer recorded.")
        assertTrue(note.contains("not platform STT", ignoreCase = true))
        assertTrue(note.contains("OpenAI", ignoreCase = true))
        assertTrue(note.contains("Answer recorded."))
        assertEquals(
            SpeechTranscriptInject.HONESTY_PREFIX,
            SpeechTranscriptInject.prependHonesty(null)
        )
    }
}
