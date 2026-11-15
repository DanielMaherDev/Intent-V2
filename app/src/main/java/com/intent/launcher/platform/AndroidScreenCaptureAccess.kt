package com.intent.launcher.platform

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import com.intent.launcher.engine.ScreenCaptureAccess
import com.intent.launcher.engine.ScreenCaptureConsent

/**
 * Live MediaProjection consent probe — consent-at-ask only.
 * Does not start a projection FGS or retain frames in this build.
 */
class AndroidScreenCaptureAccess(
    private val context: Context
) : ScreenCaptureAccess {

    @Volatile
    private var consent: ScreenCaptureConsent = ScreenCaptureConsent.NOT_ASKED

    private val projectionManager: MediaProjectionManager?
        get() = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager

    override fun isCaptureApiAvailable(): Boolean = projectionManager != null

    override fun consentState(): ScreenCaptureConsent = consent

    override fun createConsentIntent(): Intent? =
        projectionManager?.createScreenCaptureIntent()

    override fun recordConsentGranted() {
        consent = ScreenCaptureConsent.GRANTED
    }

    override fun recordConsentDenied() {
        consent = ScreenCaptureConsent.DENIED
    }
}
