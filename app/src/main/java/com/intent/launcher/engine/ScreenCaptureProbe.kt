package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityEntry
import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.capability.CapabilityStatus

/**
 * Stage 6 — screen-capture consent honesty (OS-176–180).
 * Consent-at-ask via MediaProjection; never OCR / lesson completion / continuous recording.
 */
object ScreenCaptureProbe {

    data class Observation(
        val apiAvailable: Boolean,
        val consent: ScreenCaptureConsent,
        val note: String,
        /** True when SessionCard may offer Capture screen (API up and not denied). */
        val canRequestConsent: Boolean,
        /** True when consent already granted this process — no frame pipeline yet. */
        val consentGranted: Boolean
    )

    fun liveRegistry(
        access: ScreenCaptureAccess,
        base: List<CapabilityEntry> = CapabilityRegistry.DEFAULT_ENTRIES
    ): CapabilityRegistry {
        val entries = base.map { entry ->
            if (entry.id != CapabilityRegistry.ID_SCREEN_CAPTURE) entry
            else statusEntry(access, entry)
        }
        return CapabilityRegistry(entries)
    }

    private fun statusEntry(
        access: ScreenCaptureAccess,
        entry: CapabilityEntry
    ): CapabilityEntry {
        if (!access.isCaptureApiAvailable()) {
            return entry.copy(
                status = CapabilityStatus.NOT_SUPPORTED,
                limitNote = "Screen capture API unavailable on this device. " +
                    "INTENT will not claim screenshots, OCR, or lesson completion."
            )
        }
        return when (access.consentState()) {
            ScreenCaptureConsent.GRANTED -> entry.copy(
                status = CapabilityStatus.AVAILABLE,
                limitNote = "MediaProjection consent granted for this session. " +
                    "Frame retention / OCR not shipped — a screenshot is never lesson completion. " +
                    "No continuous recording."
            )
            ScreenCaptureConsent.DENIED -> entry.copy(
                status = CapabilityStatus.PERMISSION_NEEDED,
                limitNote = "Screen capture consent denied — no silent retry. " +
                    "Complete remains your report; never OCR or lesson completion."
            )
            ScreenCaptureConsent.NOT_ASKED -> entry.copy(
                status = CapabilityStatus.PERMISSION_NEEDED,
                limitNote = "Requires one-shot MediaProjection system consent when you ask. " +
                    "Does not OCR screens or prove lesson completion; frames not retained by default."
            )
        }
    }

    fun observeForSession(access: ScreenCaptureAccess): Observation {
        if (!access.isCaptureApiAvailable()) {
            return Observation(
                apiAvailable = false,
                consent = access.consentState(),
                note = "Screen capture unavailable on this device — " +
                    "INTENT will not invent screenshots or lesson completion.",
                canRequestConsent = false,
                consentGranted = false
            )
        }
        return when (access.consentState()) {
            ScreenCaptureConsent.GRANTED -> Observation(
                apiAvailable = true,
                consent = ScreenCaptureConsent.GRANTED,
                note = "Screen capture consent granted — evidence path does not retain frames " +
                    "or OCR in this build. Never lesson completion.",
                canRequestConsent = false,
                consentGranted = true
            )
            ScreenCaptureConsent.DENIED -> Observation(
                apiAvailable = true,
                consent = ScreenCaptureConsent.DENIED,
                note = "Screen capture denied — no silent retry. " +
                    "Complete remains your report; no OCR or lesson claims.",
                canRequestConsent = false,
                consentGranted = false
            )
            ScreenCaptureConsent.NOT_ASKED -> Observation(
                apiAvailable = true,
                consent = ScreenCaptureConsent.NOT_ASKED,
                note = "Optional: request screen capture consent (system dialog). " +
                    "Consent is one-shot; INTENT does not OCR or mark lessons complete.",
                canRequestConsent = true,
                consentGranted = false
            )
        }
    }
}
