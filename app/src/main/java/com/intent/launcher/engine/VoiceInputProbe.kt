package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityEntry
import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.capability.CapabilityStatus

/**
 * Stage 6 — voice / mic probe for Conversation.
 * Denied or unavailable → text chips remain the useful path (no fake listening UI claims).
 */
object VoiceInputProbe {

    data class Observation(
        val permissionGranted: Boolean,
        val recognitionAvailable: Boolean,
        val canCapture: Boolean,
        val note: String,
        val canRequestPermission: Boolean
    )

    fun liveRegistry(
        permission: VoicePermissionAccess,
        speech: SpeechToTextAccess,
        base: List<CapabilityEntry> = CapabilityRegistry.DEFAULT_ENTRIES
    ): CapabilityRegistry {
        val granted = permission.hasRecordAudioPermission()
        val stt = speech.isRecognitionAvailable()
        val entries = base.map { entry ->
            if (entry.id != CapabilityRegistry.ID_VOICE_INPUT) entry
            else when {
                !granted -> entry.copy(
                    status = CapabilityStatus.PERMISSION_NEEDED,
                    limitNote = "Requires microphone permission for voice turns. " +
                        "Use conversation chips (text) without it — same command path."
                )
                !stt -> entry.copy(
                    status = CapabilityStatus.TEMPORARILY_UNAVAILABLE,
                    limitNote = "Microphone allowed, but platform speech recognition is unavailable. " +
                        "Use chips — no invented transcription in this build."
                )
                else -> entry.copy(
                    status = CapabilityStatus.AVAILABLE,
                    limitNote = "Platform speech recognition only — matches chips or stores text. " +
                        "No live LLM interpretation or claimed transcription quality."
                )
            }
        }
        return CapabilityRegistry(entries)
    }

    fun observe(
        permission: VoicePermissionAccess,
        speech: SpeechToTextAccess
    ): Observation {
        val granted = permission.hasRecordAudioPermission()
        val stt = speech.isRecognitionAvailable()
        return when {
            !granted -> Observation(
                permissionGranted = false,
                recognitionAvailable = stt,
                canCapture = false,
                note = "Microphone permission needed for voice. Tap chips to continue in text — " +
                    "same Conversation path.",
                canRequestPermission = true
            )
            !stt -> Observation(
                permissionGranted = true,
                recognitionAvailable = false,
                canCapture = false,
                note = "Speech recognition isn’t available on this device right now. " +
                    "Use the chips above — no fake voice capture.",
                canRequestPermission = false
            )
            else -> Observation(
                permissionGranted = true,
                recognitionAvailable = true,
                canCapture = true,
                note = "Voice uses platform speech recognition. Spoken chip phrases run the same " +
                    "commands as taps — no live LLM.",
                canRequestPermission = false
            )
        }
    }
}
