package com.intent.launcher.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gate D / OS-155 — sticky resume honesty without a device.
 */
class FloatingAgentRestartPolicyTest {

    @Test
    fun deniedOverlay_stopNotSticky_withFallbackNote() {
        val d = FloatingAgentRestartPolicy.decide(
            overlayGranted = false,
            requestedState = FloatingAgentState.ATTENTION_NEEDED
        )
        assertEquals(FloatingAgentRestartPolicy.StartMode.STOP_NOT_STICKY, d.mode)
        assertNull(d.resolvedState)
        assertNotNull(d.fallbackNote)
        assertTrue(d.fallbackNote!!.contains("no floating bubble", ignoreCase = true))
        assertFalse(d.fallbackNote!!.contains("showing over", ignoreCase = true))
    }

    @Test
    fun grantedWithRequestedState_sticky() {
        val d = FloatingAgentRestartPolicy.decide(
            overlayGranted = true,
            requestedState = FloatingAgentState.ACTIVE_SESSION
        )
        assertEquals(FloatingAgentRestartPolicy.StartMode.STICKY_WITH_OVERLAY, d.mode)
        assertEquals(FloatingAgentState.ACTIVE_SESSION, d.resolvedState)
        assertNull(d.fallbackNote)
    }

    @Test
    fun stickyRestartNullIntent_defaultsIdle_whenOverlayStillGranted() {
        val d = FloatingAgentRestartPolicy.decide(
            overlayGranted = true,
            requestedState = null
        )
        assertEquals(FloatingAgentRestartPolicy.StartMode.STICKY_WITH_OVERLAY, d.mode)
        assertEquals(FloatingAgentState.IDLE, d.resolvedState)
        assertNull(d.fallbackNote)
    }

    @Test
    fun stickyRestartAfterRevoke_neverInventsGrant() {
        // Service killed while granted, then overlay revoked before restart.
        val d = FloatingAgentRestartPolicy.decide(
            overlayGranted = false,
            requestedState = null
        )
        assertEquals(FloatingAgentRestartPolicy.StartMode.STOP_NOT_STICKY, d.mode)
        assertNull(d.resolvedState)
        assertNotNull(d.fallbackNote)
        assertTrue(d.fallbackNote!!.contains("Settings", ignoreCase = true))
    }

    @Test
    fun documentsStickyRestart_mentionsRecheck() {
        val doc = FloatingAgentRestartPolicy.documentsStickyRestart()
        assertTrue(doc.contains("START_STICKY"))
        assertTrue(doc.contains("START_NOT_STICKY"))
        assertTrue(doc.contains("re-checked") || doc.contains("re-check"))
        assertTrue(doc.contains("IDLE"))
        assertFalse(doc.contains("always restarts with bubble", ignoreCase = true))
    }

    @Test
    fun serviceStoppedFallback_pointsToHomeAndNotification() {
        val note = TalkBackLabels.serviceStoppedFallbackNote()
        assertTrue(note.contains("not running", ignoreCase = true))
        assertTrue(note.contains("Companion menu", ignoreCase = true))
        assertTrue(note.contains("notification", ignoreCase = true))
        assertTrue(note.contains("not invented", ignoreCase = true))
    }
}
