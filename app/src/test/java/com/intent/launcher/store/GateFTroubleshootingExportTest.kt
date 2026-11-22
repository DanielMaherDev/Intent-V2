package com.intent.launcher.store

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.domain.AdaptiveBuilderState
import com.intent.launcher.domain.BuilderDecisionEvent
import com.intent.launcher.domain.Intention
import com.intent.launcher.domain.IntentionPhase
import com.intent.launcher.domain.IntentionSnapshot
import com.intent.launcher.domain.SCHEMA_VERSION
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GateFTroubleshootingExportTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun encodeTroubleshootingJson_embedsTrail_notFullDump() {
        val event = BuilderDecisionEvent(
            id = "dec-1",
            atEpochMs = 1_700_000_000_000L,
            intentionId = "int-1",
            decisionType = "Propose",
            accepted = true,
            reason = "ok",
            activeQuestionId = "q-1",
            factSourceIds = listOf("fact-a", "fact-b"),
            selectedApproachIds = listOf("approach-ai-practice"),
            rejectedApproachIds = listOf("approach-offline"),
            capabilitySnapshotVersion = CapabilityRegistry.SNAPSHOT_VERSION,
            policyVersion = AdaptiveBuilderState.POLICY_VERSION,
            proposalId = "prop-1",
            toolResults = listOf("proposal_drafted"),
            expectedRevision = 3L,
            resultingRevision = 4L,
            applyDurationMs = 12L,
            sourceTurnId = "turn-1"
        )
        val snap = IntentionSnapshot(
            schemaVersion = SCHEMA_VERSION,
            intentions = listOf(
                Intention(
                    id = "int-1",
                    title = "Learn Spanish",
                    statement = "Practice Spanish",
                    phase = IntentionPhase.EXPLORING,
                    createdAtEpochMs = 1L,
                    updatedAtEpochMs = 1L,
                    shapeTag = "language"
                )
            ),
            adaptiveBuilderStates = listOf(
                AdaptiveBuilderState(
                    intentionId = "int-1",
                    revision = 4L,
                    currentQuestionId = null,
                    pendingProposalId = "prop-1",
                    lifecycleHint = "proposing"
                )
            ),
            decisionTrail = listOf(event),
            turns = emptyList()
        )
        val json = SnapshotExport.encodeTroubleshootingJson(snap, "0.0.198", nowEpochMs = 99L)
        assertTrue(json.contains("\"exportKind\": \"troubleshooting\""))
        assertTrue(json.contains("\"decisionType\": \"Propose\""))
        assertTrue(json.contains("\"proposalId\": \"prop-1\""))
        assertTrue(json.contains("\"applyDurationMs\": 12"))
        assertTrue(json.contains("\"capabilitySnapshotVersion\": \"${CapabilityRegistry.SNAPSHOT_VERSION}\""))
        assertTrue(json.contains("Learn Spanish"))
        assertFalse(json.contains("\"turns\""))
        assertEquals(
            SnapshotExport.EXPORT_KIND_TROUBLESHOOTING,
            SnapshotExport.EXPORT_KIND_TROUBLESHOOTING
        )
    }

    @Test
    fun writeExportFile_troubleshootingName() {
        val cache = tmp.newFolder("cache")
        val json = """{"exportKind":"troubleshooting"}"""
        val file = SnapshotExport.writeExportFile(
            cacheDir = cache,
            json = json,
            nowEpochMs = 1_700_000_000_000L,
            exportFileName = SnapshotExport.troubleshootingFileName(1_700_000_000_000L)
        )
        assertEquals("intent-troubleshooting-1700000000000.json", file.name)
        assertEquals(File(cache, "exports"), file.parentFile)
        assertEquals(json, file.readText())
    }

    @Test
    fun troubleshootingHonesty_statesNoChatNoCloud() {
        val note = SnapshotExport.TROUBLESHOOTING_HONESTY_NOTE.lowercase()
        assertTrue(note.contains("decision trail"))
        assertTrue(note.contains("no conversation") || note.contains("no api"))
        assertTrue(note.contains("not a cloud"))
    }

    @Test
    fun redactSecretLike_stripsKeys() {
        val out = SnapshotExport.redactSecretLike("header sk-abcdefghijklmnopqrst footer")
        assertFalse(out.contains("sk-abcdefghijklmnopqrst"))
        assertTrue(out.contains("[redacted]"))
    }
}
