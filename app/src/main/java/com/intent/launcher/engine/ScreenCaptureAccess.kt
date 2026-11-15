package com.intent.launcher.engine

import android.content.Intent

/**
 * Platform adapter for MediaProjection consent-at-ask.
 * Grant is session-scoped system consent — never OCR, lesson completion, or continuous recording.
 */
enum class ScreenCaptureConsent {
    /** User has not been asked in this process. */
    NOT_ASKED,
    /** System dialog returned granted (result OK). */
    GRANTED,
    /** User denied or cancelled — no silent retry this session. */
    DENIED
}

interface ScreenCaptureAccess {
    /** True when the platform can show MediaProjection consent. */
    fun isCaptureApiAvailable(): Boolean

    fun consentState(): ScreenCaptureConsent

    /** Intent for [MediaProjectionManager.createScreenCaptureIntent]; null if unavailable. */
    fun createConsentIntent(): Intent?

    fun recordConsentGranted()

    fun recordConsentDenied()
}

/** Test stub. */
class FakeScreenCaptureAccess(
    var available: Boolean = true,
    var consent: ScreenCaptureConsent = ScreenCaptureConsent.NOT_ASKED
) : ScreenCaptureAccess {
    override fun isCaptureApiAvailable(): Boolean = available
    override fun consentState(): ScreenCaptureConsent = consent
    override fun createConsentIntent(): Intent? = if (available) Intent("test.SCREEN_CAPTURE") else null
    override fun recordConsentGranted() {
        consent = ScreenCaptureConsent.GRANTED
    }
    override fun recordConsentDenied() {
        consent = ScreenCaptureConsent.DENIED
    }
}
