package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.domain.IntentionPhase
import com.intent.launcher.domain.ProposalStatus
import com.intent.launcher.domain.ResponseBlockType
import com.intent.launcher.store.IntentionFlowStore
import com.intent.launcher.ui.components.PracticeMinuteChoices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * IV-227 — after Approve, create shell is cleared so Discuss free-text hits
 * [ConversationCoordinator] → [GuidedPractice] (not CreateIntentionChat rename).
 */
class ClearCreateSessionAfterApproveTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var store: IntentionFlowStore
    private val registry = CapabilityRegistry.default
    private val now = 1_700_000_300_000L

    @Before
    fun setUp() {
        store = IntentionFlowStore(tmp.newFile("clear-create-approve.json"))
    }

    @Test
    fun approve_clearsCreateSession_freeTextHitsGuidedPractice_notRename() {
        CreateIntentionChat.begin(store, now, registry)
        CreateIntentionChat.submitUtterance(
            store, "I want to learn Spanish", now + 10, registry
        )
        CreateIntentionChat.submitQuantity(
            store, 20, PracticeMinuteChoices.UNIT, now + 20, registry
        )
        CreateIntentionChat.submitChoice(store, "Self-study", now + 30, registry)
        val commit = CreateIntentionChat.submitChoice(
            store, "Confirm intention", now + 40, registry
        )
        assertTrue(commit.ok)
        assertNotNull(commit.pendingProposalId)
        assertFalse(
            "Confirm bridges adaptive state — create-in-progress should be false",
            CreateIntentionChat.isCreateInProgress(store)
        )
        // Tip-170 bug surface: isCreateSession stayed true forever on SESSION id.
        assertTrue(
            CreateIntentionChat.isCreateSession(
                store.snapshot().intentions.first {
                    it.id == CreateIntentionChat.SESSION_INTENTION_ID
                }
            )
        )

        val pending = store.snapshot().proposals.first {
            it.id == commit.pendingProposalId && it.status == ProposalStatus.PENDING
        }
        val sessionId = "session-approve-${pending.id}"
        val gateway = CommandGateway.approveProposal(
            store = store,
            turnId = "turn-approve-iv227",
            commandId = "cmd-approve-iv227",
            sessionId = sessionId,
            nowEpochMs = now + 100,
            utterance = "Yes",
            explicitProposalId = pending.id
        )
        assertEquals(GatewayStatus.APPLIED, gateway.status)

        val after = store.snapshot()
        assertTrue(
            "Create shell remapped off intention-creating",
            after.intentions.none { it.id == CreateIntentionChat.SESSION_INTENTION_ID }
        )
        val durable = after.intentions.first { it.phase == IntentionPhase.ACTIVE }
        assertNotEquals(CreateIntentionChat.SESSION_INTENTION_ID, durable.id)
        assertEquals(durable.id, after.activeIntentionId)
        assertFalse(CreateIntentionChat.isCreateInProgress(store))
        assertFalse(CreateIntentionChat.isCreateSessionActive(store))
        assertFalse(CreateIntentionChat.isCreateSession(durable))

        val titleBefore = durable.title
        val utterance = "Lets practise a customer call in Spanish"
        // Same routing decision ViewModel uses for Discuss free-text.
        assertFalse(CreateIntentionChat.isCreateInProgress(store))
        val result = ConversationCoordinator.submitFreeText(
            store = store,
            intention = durable,
            text = utterance,
            nowEpochMs = now + 200,
            registry = registry,
            access = FakeLiveLlmAccess()
        )
        assertEquals(utterance, result.appliedChip)
        assertFalse(result.liveCallAttempted)
        assertTrue(
            result.honestyNote!!.contains("Guided session", ignoreCase = true) ||
                result.honestyNote!!.contains("Spanish intention", ignoreCase = true)
        )

        val snap = store.snapshot()
        // Must not rename the approved create intention (tip-170 CreateIntentionChat path).
        assertEquals(
            titleBefore,
            snap.intentions.first { it.id == durable.id }.title
        )
        assertFalse(
            snap.intentions.any {
                it.id == durable.id &&
                    it.title.contains("customer call", ignoreCase = true)
            }
        )
        // GuidedPractice shared-history honesty on Spanish fixture intention.
        assertTrue(
            snap.turns.any {
                it.role == "agent" &&
                    it.text.contains("not a separate bot", ignoreCase = true)
            }
        )
        assertTrue(
            snap.turns.any { turn ->
                turn.blocks.any {
                    it.type == ResponseBlockType.EXPLANATION &&
                        it.title.contains("Guided practice", ignoreCase = true)
                }
            }
        )
        assertTrue(snap.intentions.any { it.id == com.intent.launcher.domain.SpanishScenario.INTENTION_ID })
    }
}
