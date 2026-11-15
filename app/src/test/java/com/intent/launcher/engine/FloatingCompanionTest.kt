package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.capability.CapabilityStatus
import com.intent.launcher.domain.NextAction
import com.intent.launcher.domain.NextActionKind
import com.intent.launcher.domain.SpanishScenario
import com.intent.launcher.store.IntentionFlowStore
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Floating companion — same agent as Conversation; overlay-gated; no fake bubble.
 */
class FloatingCompanionTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var store: IntentionFlowStore
    private val tz = TimeZone.getTimeZone("UTC")
    private val registry = CapabilityRegistry.default
    private val seedAt = 1_700_049_600_000L
    private val dueNow = 1_700_159_400_000L

    @Before
    fun setUp() {
        store = IntentionFlowStore(tmp.newFile("flow.json"))
    }

    private fun approveSpanish() {
        SpanishScenario.seed(store, seedAt)
        SpanishScenario.onChip(store, "I meet a tutor on Thursdays at six", seedAt + 1)
        SpanishScenario.approve(store, seedAt + 2)
    }

    private fun overlayAvailableRegistry(): CapabilityRegistry {
        val entries = CapabilityRegistry.DEFAULT_ENTRIES.map { entry ->
            if (entry.id == CapabilityRegistry.ID_FLOATING_OVERLAY) {
                entry.copy(status = CapabilityStatus.AVAILABLE, limitNote = "ok")
            } else {
                entry
            }
        }
        return CapabilityRegistry(entries)
    }

    @Test
    fun presence_deniedOverlay_noBubble_manualRoute() {
        approveSpanish()
        val next = NextActionEngine.recommend(store.snapshot(), dueNow, tz, registry)
        val presence = FloatingCompanion.presence(
            nextAction = next,
            overlayGranted = false,
            registry = registry
        )
        assertNull(presence.state)
        assertNotNull(presence.manualRouteNote)
        assertTrue(presence.manualRouteNote!!.contains("Discuss", ignoreCase = true))
        assertTrue(presence.manualRouteNote!!.contains("Home", ignoreCase = true))
    }

    @Test
    fun presence_registryLocked_noBubbleEvenIfOsGranted() {
        approveSpanish()
        val next = NextActionEngine.recommend(store.snapshot(), dueNow, tz, registry)
        val locked = CapabilityRegistry(
            CapabilityRegistry.DEFAULT_ENTRIES.map { entry ->
                if (entry.id == CapabilityRegistry.ID_FLOATING_OVERLAY) {
                    entry.copy(
                        status = CapabilityStatus.PERMISSION_NEEDED,
                        limitNote = "need permission"
                    )
                } else {
                    entry
                }
            }
        )
        val presence = FloatingCompanion.presence(
            nextAction = next,
            overlayGranted = true,
            registry = locked
        )
        assertNull(presence.state)
        assertNotNull(presence.manualRouteNote)
    }

    @Test
    fun presence_granted_idle_attention_active() {
        approveSpanish()
        val overlayReg = overlayAvailableRegistry()

        val begin = NextActionEngine.recommend(store.snapshot(), dueNow, tz, overlayReg)
        assertEquals(NextActionKind.BEGIN, begin.kind)
        val attention = FloatingCompanion.presence(begin, overlayGranted = true, overlayReg)
        assertEquals(FloatingAgentState.ATTENTION_NEEDED, attention.state)
        assertEquals("Needs you", attention.label)
        assertNull(attention.manualRouteNote)

        val futureDay = seedAt
        val discuss = NextActionEngine.recommend(store.snapshot(), futureDay, tz, overlayReg)
        assertEquals(NextActionKind.DISCUSS, discuss.kind)
        val idle = FloatingCompanion.presence(discuss, overlayGranted = true, overlayReg)
        assertEquals(FloatingAgentState.IDLE, idle.state)
        assertEquals("INTENT", idle.label)

        SpanishScenario.beginSession(store, dueNow, runId = "run-float-active")
        val activeNext = NextActionEngine.recommend(store.snapshot(), dueNow + 30_000L, tz, overlayReg)
        assertEquals(NextActionKind.ACTIVE_SESSION, activeNext.kind)
        val active = FloatingCompanion.presence(activeNext, overlayGranted = true, overlayReg)
        assertEquals(FloatingAgentState.ACTIVE_SESSION, active.state)
        assertEquals("In session", active.label)
    }

    @Test
    fun resolveState_coversRequiredAndStubLabels() {
        fun na(kind: NextActionKind) = NextAction(
            kind = kind,
            label = kind.name,
            detail = "",
            intentionId = "i1"
        )
        assertEquals(FloatingAgentState.IDLE, FloatingCompanion.resolveState(na(NextActionKind.DISCUSS)))
        assertEquals(FloatingAgentState.IDLE, FloatingCompanion.resolveState(na(NextActionKind.NONE)))
        assertEquals(
            FloatingAgentState.ATTENTION_NEEDED,
            FloatingCompanion.resolveState(na(NextActionKind.BEGIN))
        )
        assertEquals(
            FloatingAgentState.ATTENTION_NEEDED,
            FloatingCompanion.resolveState(na(NextActionKind.REVIEW_PROPOSAL))
        )
        assertEquals(
            FloatingAgentState.ATTENTION_NEEDED,
            FloatingCompanion.resolveState(na(NextActionKind.CHECK_IN))
        )
        assertEquals(
            FloatingAgentState.ATTENTION_NEEDED,
            FloatingCompanion.resolveState(na(NextActionKind.MIDDAY))
        )
        assertEquals(
            FloatingAgentState.ATTENTION_NEEDED,
            FloatingCompanion.resolveState(na(NextActionKind.CHECK_OUT))
        )
        assertEquals(
            FloatingAgentState.ACTIVE_SESSION,
            FloatingCompanion.resolveState(na(NextActionKind.ACTIVE_SESSION))
        )
        assertEquals("Listening…", FloatingCompanion.labelFor(FloatingAgentState.CAPTURING))
        assertEquals("Thinking…", FloatingCompanion.labelFor(FloatingAgentState.THINKING))
        assertEquals("Reply ready", FloatingCompanion.labelFor(FloatingAgentState.RESPONSE_READY))
        assertEquals("Needs you", FloatingCompanion.labelFor(FloatingAgentState.ATTENTION_NEEDED))
        assertEquals("In session", FloatingCompanion.labelFor(FloatingAgentState.ACTIVE_SESSION))
        assertEquals("INTENT", FloatingCompanion.labelFor(FloatingAgentState.IDLE))
    }

    @Test
    fun onPress_opensSameConversationAsDiscuss() {
        approveSpanish()
        val overlayReg = overlayAvailableRegistry()
        val next = NextActionEngine.recommend(store.snapshot(), dueNow, tz, overlayReg)
        assertEquals(NextActionKind.BEGIN, next.kind)

        val viaDiscuss = DiscussFromHome.open(store, next, dueNow + 1, overlayReg)
        // Reset activation side-effect by re-opening store path is same intention
        val viaCompanion = FloatingCompanion.onPress(store, next, dueNow + 2, overlayReg)
        assertEquals(viaDiscuss.navigateTo, viaCompanion.navigateTo)
        assertEquals(DiscussFromHome.CONVERSATION_ROUTE, viaCompanion.navigateTo)
        assertEquals(SpanishScenario.INTENTION_ID, viaCompanion.intentionId)
        assertFalse(viaCompanion.navigateTo.isNullOrBlank())
    }

    @Test
    fun liveProbe_granted_enablesCompanionRegistry() {
        val access = FakeOverlayPermissionAccess(granted = true)
        val live = OverlayPermissionProbe.liveRegistry(access)
        assertTrue(live.canUseFloatingOverlay())
        assertTrue(
            live.get(CapabilityRegistry.ID_FLOATING_OVERLAY)!!.limitNote
                .contains("same agent", ignoreCase = true)
        )
        assertFalse(
            live.get(CapabilityRegistry.ID_FLOATING_OVERLAY)!!.limitNote
                .contains("not shipped", ignoreCase = true)
        )
    }
}
