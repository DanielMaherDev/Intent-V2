package com.intent.launcher.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gate D / OS-155 / D3 — sticky resume honesty + interrupt takeover policy.
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
        assertTrue(d.fallbackNote!!.contains("Companion", ignoreCase = true))
        assertFalse(d.fallbackNote!!.contains("Overlay permission not granted", ignoreCase = true))
        assertFalse(d.fallbackNote!!.contains("showing over", ignoreCase = true))
        assertFalse(d.postTakeoverNotification)
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
        assertFalse(d.postTakeoverNotification)
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
            requestedState = null,
            stickyRestart = true,
            notificationsPermitted = true
        )
        assertEquals(FloatingAgentRestartPolicy.StartMode.STOP_NOT_STICKY, d.mode)
        assertNull(d.resolvedState)
        assertNotNull(d.fallbackNote)
        assertTrue(d.fallbackNote!!.contains("Companion", ignoreCase = true))
        assertFalse(d.fallbackNote!!.contains("Overlay permission not granted", ignoreCase = true))
        assertTrue(d.postTakeoverNotification)
        assertEquals(
            FloatingAgentRestartPolicy.InterruptReason.PROCESS_DEATH_OVERLAY_DENIED,
            d.interruptReason
        )
    }

    @Test
    fun revokeWhileRunning_postsPermittedNotification() {
        val d = FloatingAgentRestartPolicy.decide(
            overlayGranted = false,
            requestedState = FloatingAgentState.ATTENTION_NEEDED,
            wasRunningWithOverlay = true,
            notificationsPermitted = true,
            interventionJobId = "j1",
            interventionTurnId = "t1"
        )
        assertTrue(d.postTakeoverNotification)
        assertEquals("j1", d.deepLink.jobId)
        assertEquals("t1", d.deepLink.turnId)
        assertNotNull(d.notificationTitle)
        assertNotNull(d.notificationText)
    }

    @Test
    fun documentsStickyRestart_mentionsRecheck() {
        val doc = FloatingAgentRestartPolicy.documentsStickyRestart()
        assertTrue(doc.contains("START_STICKY"))
        assertTrue(doc.contains("START_NOT_STICKY"))
        assertTrue(doc.contains("re-checked") || doc.contains("re-check"))
        assertTrue(doc.contains("IDLE"))
        assertTrue(doc.contains("notification", ignoreCase = true))
        assertFalse(doc.contains("always restarts with bubble", ignoreCase = true))
    }

    @Test
    fun serviceStoppedFallback_pointsToHomeAndNotification() {
        val note = TalkBackLabels.serviceStoppedFallbackNote()
        assertTrue(note.contains("Companion", ignoreCase = true))
        assertTrue(note.contains("notification", ignoreCase = true))
        assertFalse(note.contains("Overlay permission not granted", ignoreCase = true))
    }
}
