package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.domain.AdaptiveBuilderFixtures
import com.intent.launcher.domain.AdaptiveBuilderState
import com.intent.launcher.domain.AnswerQuestion
import com.intent.launcher.domain.AnswerSchema
import com.intent.launcher.domain.ApproachStatus
import com.intent.launcher.domain.BuilderDecision
import com.intent.launcher.domain.BuilderDecisionEvent
import com.intent.launcher.domain.FlowCommand
import com.intent.launcher.domain.SCHEMA_VERSION
import com.intent.launcher.store.IntentionFlowStore
import com.intent.launcher.store.SnapshotExport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Gate F — decision trail record + redacted troubleshooting export.
 */
class GateFDecisionTraceTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var storeFile: File
    private lateinit var store: IntentionFlowStore
    private val now = 1_700_000_000_000L
    private val tz = AdaptiveBuilderFixtures.utc()

    @Before
    fun setUp() {
        storeFile = tmp.newFile("gate-f-trace.json")
        store = IntentionFlowStore(storeFile)
    }

    @Test
    fun askProposePauseSelect_recordTrailWithVersionsAndDuration() {
        AdaptiveBuilderFixtures.seedSpanishDiscovery(store, now)
        val id = AdaptiveBuilderFixtures.SPANISH_ID
        var ctx = AdaptiveBuilderEngine.projectContext(store.snapshot(), id, now, tz)!!

        val ask = AdaptiveBuilderEngine.applyDecision(
            store,
            BuilderDecision.Ask(
                intentionId = id,
                expectedRevision = ctx.revision,
                questionId = "q-motivation-$id",
                whyItMatters = "Motivation shapes practice intensity.",
                answerSchema = AnswerSchema.SINGLE_CHOICE,
                purpose = AdaptiveBuilderEngine.KEY_MOTIVATION,
                options = AdaptiveBuilderEngine.MOTIVATION_OPTIONS
            ),
            now,
            timeZone = tz
        )
        assertTrue(ask.accepted)
        assertEquals("Ask", ask.decisionKind)

        ctx = AdaptiveBuilderEngine.projectContext(store.snapshot(), id, now + 1, tz)!!
        val answer = AdaptiveBuilderEngine.answerQuestion(
            store,
            AnswerQuestion(
                questionId = "q-motivation-$id",
                intentionId = id,
                normalizedValue = "Moving or travel",
                rawValue = "Moving or travel",
                modality = "CHIP",
                sourceTurnId = "turn-motivation-$id",
                answeredAtEpochMs = now + 1,
                idempotencyKey = "motivation-1",
                expectedRevision = ctx.revision
            ),
            factKey = AdaptiveBuilderEngine.KEY_MOTIVATION,
            timeZone = tz
        )
        assertTrue(answer.accepted)

        ctx = AdaptiveBuilderEngine.projectContext(store.snapshot(), id, now + 2, tz)!!
        val offer = AdaptiveBuilderEngine.applyDecision(
            store,
            BuilderDecision.OfferApproaches(
                intentionId = id,
                expectedRevision = ctx.revision,
                approachDrafts = AdaptiveBuilderEngine.defaultApproachDrafts()
            ),
            now + 2,
            timeZone = tz
        )
        assertTrue(offer.accepted)

        ctx = AdaptiveBuilderEngine.projectContext(store.snapshot(), id, now + 3, tz)!!
        val select = AdaptiveBuilderEngine.selectApproaches(
            store = store,
            intentionId = id,
            approachIds = listOf("approach-ai-practice", "approach-learning-app"),
            nowEpochMs = now + 3,
            sourceTurnId = "turn-select-$id",
            expectedRevision = ctx.revision,
            modality = "CHIP"
        )
        assertTrue(select.accepted)
        assertEquals("SelectApproaches", select.decisionKind)

        ctx = AdaptiveBuilderEngine.projectContext(store.snapshot(), id, now + 4, tz)!!
        val pause = AdaptiveBuilderEngine.applyDecision(
            store,
            BuilderDecision.Pause(
                intentionId = id,
                expectedRevision = ctx.revision,
                reason = "Need eligibility facts before drafting a plan.",
                resumeCue = "Continue discussion"
            ),
            now + 4,
            timeZone = tz
        )
        assertTrue(pause.accepted)

        val trail = store.snapshot().decisionTrail
        assertTrue("expected Ask+Answer+Offer+Select+Pause, got ${trail.map { it.decisionType }}", trail.size >= 5)
        assertTrue(trail.any { it.decisionType == "Ask" && it.accepted })
        assertTrue(trail.any { it.decisionType == "AnswerQuestion" && it.accepted })
        assertTrue(trail.any { it.decisionType == "OfferApproaches" && it.accepted })
        assertTrue(trail.any { it.decisionType == "SelectApproaches" && it.accepted })
        assertTrue(trail.any { it.decisionType == "Pause" && it.accepted })

        val selectEvent = trail.last { it.decisionType == "SelectApproaches" }
        assertEquals(
            listOf("approach-ai-practice", "approach-learning-app"),
            selectEvent.selectedApproachIds
        )
        assertTrue(selectEvent.rejectedApproachIds.contains("approach-offline"))
        assertEquals(CapabilityRegistry.SNAPSHOT_VERSION, selectEvent.capabilitySnapshotVersion)
        assertEquals(AdaptiveBuilderState.POLICY_VERSION, selectEvent.policyVersion)
        assertTrue(selectEvent.applyDurationMs >= 0L)
        assertTrue(selectEvent.resultingRevision >= selectEvent.expectedRevision)

        val pauseEvent = trail.last { it.decisionType == "Pause" }
        assertEquals(listOf("pause_recorded"), pauseEvent.toolResults)
        assertTrue(pauseEvent.applyDurationMs >= 0L)

        // Round-trip persists trail (schema 11).
        assertEquals(SCHEMA_VERSION, store.snapshot().schemaVersion)
        val encoded = store.encode(store.snapshot())
        assertTrue(encoded.contains("\"decisionTrail\""))
        val reloaded = IntentionFlowStore(tmp.newFile("gate-f-reload.json"))
        reloaded.replaceFromJson(encoded)
        assertEquals(trail.size, reloaded.snapshot().decisionTrail.size)
        assertEquals(
            trail.map { it.decisionType },
            reloaded.snapshot().decisionTrail.map { it.decisionType }
        )
    }

    @Test
    fun troubleshootingExport_containsTrail_omitsChatAndSecrets() {
        AdaptiveBuilderFixtures.seedSpanishDiscovery(store, now)
        val id = AdaptiveBuilderFixtures.SPANISH_ID
        val ctx = AdaptiveBuilderEngine.projectContext(store.snapshot(), id, now, tz)!!
        AdaptiveBuilderEngine.applyDecision(
            store,
            BuilderDecision.Ask(
                intentionId = id,
                expectedRevision = ctx.revision,
                questionId = "q-secret-test",
                whyItMatters = "Why it matters for practice.",
                answerSchema = AnswerSchema.FREE_TEXT,
                purpose = AdaptiveBuilderEngine.KEY_MOTIVATION
            ),
            now,
            timeZone = tz
        )
        // Inject a secret-bearing reason to prove export redaction.
        store.apply(
            FlowCommand.AppendDecisionEvent(
                event = BuilderDecisionEvent(
                    id = "dec-secret",
                    atEpochMs = now + 1,
                    intentionId = id,
                    decisionType = "Pause",
                    accepted = false,
                    reason = "tool failed sk-testsecretABCDEFGH",
                    capabilitySnapshotVersion = CapabilityRegistry.SNAPSHOT_VERSION,
                    policyVersion = AdaptiveBuilderState.POLICY_VERSION,
                    applyDurationMs = 1L
                ),
                nowEpochMs = now + 1
            )
        )

        val json = SnapshotExport.encodeTroubleshootingJson(
            snapshot = store.snapshot(),
            appVersionName = "0.0.198",
            nowEpochMs = now + 2
        )
        assertTrue(json.contains("\"exportKind\": \"troubleshooting\""))
        assertTrue(json.contains("\"decisionTrail\""))
        assertTrue(json.contains("Ask"))
        assertTrue(json.contains(CapabilityRegistry.SNAPSHOT_VERSION))
        assertTrue(json.contains(AdaptiveBuilderState.POLICY_VERSION))
        assertTrue(json.contains("applyDurationMs"))
        assertTrue(json.contains("intentionSummaries"))

        // No full chat dump / turn text from seed.
        assertFalse(json.contains("\"turns\""))
        assertFalse(json.contains("rawUtterance"))
        assertFalse(json.contains("beforeJson"))
        // Secret-like tokens redacted from trail reasons.
        assertFalse(json.contains("sk-testsecretABCDEFGH"))
        assertTrue(json.contains("[redacted]"))

        val selected = store.snapshot().approaches
            .filter { it.intentionId == id && it.status == ApproachStatus.SELECTED }
        assertTrue(selected.isEmpty())
        assertNotNull(store.snapshot().decisionTrail.firstOrNull { it.decisionType == "Ask" })
    }

    @Test
    fun redactDecisionReason_stripsApiKeyShapes() {
        val redacted = AdaptiveBuilderEngine.redactDecisionReason(
            "failed with sk-abcdefghijklmnopqrst and Bearer abc.def"
        )
        assertFalse(redacted.contains("sk-abcdefghijklmnopqrst"))
        assertFalse(redacted.contains("Bearer abc.def"))
        assertTrue(redacted.contains("[redacted]"))
    }
}
