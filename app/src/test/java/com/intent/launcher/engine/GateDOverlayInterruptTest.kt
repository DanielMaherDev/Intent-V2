package com.intent.launcher.engine

import com.intent.launcher.domain.FlowCommand
import com.intent.launcher.domain.ProvenanceKind
import com.intent.launcher.store.IntentionFlowStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Gate D3 — overlay revoke / process-kill → durable store + takeover policy.
 */
class GateDOverlayInterruptTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun overlayRevokeWhileRunning_postsTakeover_withInterventionIds() {
        val d = FloatingAgentRestartPolicy.decide(
            overlayGranted = false,
            requestedState = FloatingAgentState.ATTENTION_NEEDED,
            wasRunningWithOverlay = true,
            stickyRestart = false,
            notificationsPermitted = true,
            interventionJobId = "job-1",
            interventionTurnId = "turn-1",
            savedWorkSummary = "Active intervention (OBSERVED) is still queued."
        )
        assertEquals(FloatingAgentRestartPolicy.StartMode.STOP_NOT_STICKY, d.mode)
        assertEquals(
            FloatingAgentRestartPolicy.InterruptReason.OVERLAY_REVOKED,
            d.interruptReason
        )
        assertTrue(d.postTakeoverNotification)
        assertEquals("job-1", d.deepLink.jobId)
        assertEquals("turn-1", d.deepLink.turnId)
        assertNotNull(d.notificationTitle)
        assertTrue(d.notificationTitle!!.contains("Conversation", ignoreCase = true))
        assertTrue(d.notificationText!!.contains("job-1"))
        assertTrue(d.notificationText!!.contains("turn-1"))
        assertTrue(d.notificationText!!.contains("revoked", ignoreCase = true))
        assertNull(d.resolvedState)
    }

    @Test
    fun overlayRevoke_notificationsDenied_doesNotPost() {
        val d = FloatingAgentRestartPolicy.decide(
            overlayGranted = false,
            requestedState = FloatingAgentState.IDLE,
            wasRunningWithOverlay = true,
            notificationsPermitted = false,
            interventionJobId = "job-x",
            interventionTurnId = "turn-x"
        )
        assertEquals(FloatingAgentRestartPolicy.StartMode.STOP_NOT_STICKY, d.mode)
        assertFalse(d.postTakeoverNotification)
        assertNull(d.notificationTitle)
        assertNotNull(d.fallbackNote)
    }

    @Test
    fun stickyRestartAfterKill_overlayDenied_postsTakeover() {
        val d = FloatingAgentRestartPolicy.decide(
            overlayGranted = false,
            requestedState = null,
            wasRunningWithOverlay = false,
            stickyRestart = true,
            notificationsPermitted = true,
            interventionJobId = "job-kill",
            interventionTurnId = "turn-kill"
        )
        assertEquals(
            FloatingAgentRestartPolicy.InterruptReason.PROCESS_DEATH_OVERLAY_DENIED,
            d.interruptReason
        )
        assertTrue(d.postTakeoverNotification)
        assertTrue(d.notificationText!!.contains("process", ignoreCase = true))
        assertTrue(d.deepLink.hasActiveIds)
    }

    @Test
    fun overlayNeverGranted_noSpamTakeover_withoutIntervention() {
        val d = FloatingAgentRestartPolicy.decide(
            overlayGranted = false,
            requestedState = FloatingAgentState.IDLE,
            wasRunningWithOverlay = false,
            stickyRestart = false,
            notificationsPermitted = true
        )
        assertEquals(FloatingAgentRestartPolicy.InterruptReason.OVERLAY_DENIED, d.interruptReason)
        assertFalse(d.postTakeoverNotification)
        // Redesign #110 scrubbed overlay essays from user-facing fallback copy —
        // route to Companion/Discuss/Agent instead of naming the permission.
        assertTrue(d.fallbackNote!!.contains("Companion", ignoreCase = true))
        assertFalse(d.fallbackNote!!.contains("Overlay permission not granted", ignoreCase = true))
    }

    @Test
    fun overlayNeverGranted_butActiveIntervention_postsTakeover() {
        val d = FloatingAgentRestartPolicy.decide(
            overlayGranted = false,
            requestedState = null,
            wasRunningWithOverlay = false,
            stickyRestart = false,
            notificationsPermitted = true,
            interventionJobId = "job-active",
            interventionTurnId = "turn-active"
        )
        assertTrue(d.postTakeoverNotification)
        assertEquals("job-active", d.deepLink.jobId)
    }

    @Test
    fun stickyWithOverlay_unchanged() {
        val d = FloatingAgentRestartPolicy.decide(
            overlayGranted = true,
            requestedState = FloatingAgentState.ACTIVE_SESSION,
            wasRunningWithOverlay = true,
            interventionJobId = "job-ok",
            interventionTurnId = "turn-ok"
        )
        assertEquals(FloatingAgentRestartPolicy.StartMode.STICKY_WITH_OVERLAY, d.mode)
        assertEquals(FloatingAgentState.ACTIVE_SESSION, d.resolvedState)
        assertFalse(d.postTakeoverNotification)
        assertEquals("job-ok", d.deepLink.jobId)
    }

    @Test
    fun documentsStickyRestart_mentionsNotificationAndStore() {
        val doc = FloatingAgentRestartPolicy.documentsStickyRestart()
        assertTrue(doc.contains("notification", ignoreCase = true))
        assertTrue(doc.contains("IntentionFlowStore"))
        assertTrue(doc.contains("intervention"))
        assertTrue(doc.contains("tmp recovery") || doc.contains("atomic"))
    }

    @Test
    fun processKillMidWrite_recoversFromTmp() {
        val file = tmp.newFile("intention_flow_v1.json")
        val store = IntentionFlowStore(file)
        store.apply(
            FlowCommand.SeedExploring(
                intentionId = "intention-d3",
                title = "Gate D3",
                statement = "Survive kill mid-write",
                shapeTag = "app_use",
                nowEpochMs = 1_000L
            )
        )
        store.apply(
            FlowCommand.CaptureThought(
                thoughtId = "thought-d3",
                intentionId = "intention-d3",
                text = "Saved before kill",
                source = "test",
                nowEpochMs = 2_000L
            )
        )
        store.apply(
            FlowCommand.QueueIntervention(
                jobId = "job-d3",
                turnId = "turn-d3",
                intentionId = "intention-d3",
                triggerKey = "usage_threshold:intention-d3",
                kind = TriggerCooldownPolicy.KIND_USAGE_THRESHOLD,
                promptText = "Still on Instagram?",
                nowEpochMs = 3_000L,
                evidenceId = null,
                provenanceKind = ProvenanceKind.OBSERVED
            )
        )
        val goodJson = file.readText()
        assertTrue(goodJson.contains("job-d3"))

        // Simulate kill after tmp write, before/during primary replace:
        // leave a complete tmp and a corrupt/empty primary.
        val tmpFile = File(file.parentFile, file.name + ".tmp")
        tmpFile.writeText(goodJson)
        file.writeText("{corrupt")

        val recovered = IntentionFlowStore(file)
        val snap = recovered.snapshot()
        assertEquals("intention-d3", snap.intentions.single().id)
        assertEquals("thought-d3", snap.thoughtCaptures.single().id)
        val job = TriggerCooldownPolicy.activeIntervention(snap)
        assertNotNull(job)
        assertEquals("job-d3", job!!.jobId)
        assertEquals("turn-d3", job.turnId)
        // Primary should be promoted from tmp.
        assertTrue(file.readText().contains("job-d3"))
    }

    @Test
    fun reloadFromDisk_afterAtomicPersist_keepsIntervention() {
        val file = tmp.newFile("roundtrip.json")
        val store = IntentionFlowStore(file)
        store.apply(
            FlowCommand.SeedExploring(
                intentionId = "intention-rt",
                title = "RT",
                statement = "rt",
                shapeTag = "app_use",
                nowEpochMs = 10L
            )
        )
        store.apply(
            FlowCommand.QueueIntervention(
                jobId = "job-rt",
                turnId = "turn-rt",
                intentionId = "intention-rt",
                triggerKey = "usage_threshold:intention-rt",
                kind = TriggerCooldownPolicy.KIND_USAGE_THRESHOLD,
                promptText = "Check usage?",
                nowEpochMs = 20L,
                evidenceId = null,
                provenanceKind = ProvenanceKind.INFERRED
            )
        )
        val reloaded = store.reloadFromDisk()
        val job = TriggerCooldownPolicy.activeIntervention(reloaded)
        assertEquals("job-rt", job?.jobId)
        assertEquals(ProvenanceKind.INFERRED, job?.provenanceKind)
    }
}
