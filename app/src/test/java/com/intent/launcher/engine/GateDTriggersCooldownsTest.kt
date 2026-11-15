package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.capability.CapabilityStatus
import com.intent.launcher.domain.AppUseReductionScenario
import com.intent.launcher.domain.FlowCommand
import com.intent.launcher.domain.NextAction
import com.intent.launcher.domain.NextActionKind
import com.intent.launcher.domain.ResponseBlockType
import com.intent.launcher.domain.SCHEMA_VERSION
import com.intent.launcher.domain.SessionDestinations
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
 * Gate D1 — trigger/cooldown/quiet-hours/budget, usage→≤1 intervention,
 * typed CAPABILITY_GAP, floater↔Conversation shared turn/job id.
 */
class GateDTriggersCooldownsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var store: IntentionFlowStore
    private val tz = TimeZone.getTimeZone("UTC")
    // Monday 13 Nov 2023 15:00 UTC — outside default quiet hours
    private val now = 1_699_887_600_000L
    private val seedAt = now - 60_000L

    @Before
    fun setUp() {
        store = IntentionFlowStore(tmp.newFile("gate-d1.json"))
    }

    private fun observeRegistry(access: UsageStatsAccess): CapabilityRegistry =
        UsageStatsProbe.liveRegistry(access)

    private fun overlayRegistry(base: CapabilityRegistry): CapabilityRegistry {
        val entries = CapabilityRegistry.DEFAULT_ENTRIES.map { entry ->
            val fromBase = base.get(entry.id)
            when {
                entry.id == CapabilityRegistry.ID_FLOATING_OVERLAY ->
                    entry.copy(status = CapabilityStatus.AVAILABLE, limitNote = "ok")
                fromBase != null -> fromBase
                else -> entry
            }
        }
        return CapabilityRegistry(entries)
    }

    @Test
    fun schemaIsTen_afterGateD1() {
        assertEquals(10, SCHEMA_VERSION)
        assertEquals(SCHEMA_VERSION, store.snapshot().schemaVersion)
    }

    @Test
    fun dismissEvidence_armsCooldown_suppressesIdenticalTrigger() {
        AppUseReductionScenario.seed(store, seedAt)
        val access = FakeUsageStatsAccess(
            granted = true,
            foregroundByPackage = mapOf(
                SessionDestinations.INSTAGRAM_PACKAGE to 50L * 60_000L
            )
        )
        val registry = observeRegistry(access)
        val first = UsageInterventionTrigger.maybeQueueFromDayUsage(
            store = store,
            access = access,
            nowEpochMs = now,
            registry = registry,
            timeZone = tz
        )
        assertNotNull(first.queued)
        val evidenceId = "obs-dismiss-d1"
        store.apply(
            FlowCommand.RecordObservedEvidence(
                evidenceId = evidenceId,
                intentionId = AppUseReductionScenario.INTENTION_ID,
                sessionRunId = null,
                packageName = SessionDestinations.INSTAGRAM_PACKAGE,
                appLabel = SessionDestinations.INSTAGRAM_LABEL,
                foregroundMs = 50L * 60_000L,
                summaryText = ObservedProvenance.summaryText("Instagram", 50L * 60_000L),
                nowEpochMs = now + 1
            )
        )
        store.apply(
            FlowCommand.DismissEvidence(
                evidenceId = evidenceId,
                nowEpochMs = now + 2
            )
        )
        val key = TriggerCooldownPolicy.usageThresholdKey(AppUseReductionScenario.INTENTION_ID)
        val cooldown = TriggerCooldownPolicy.cooldownFor(store.snapshot(), key, now + 3)
        assertNotNull(cooldown)
        assertEquals(TriggerCooldownPolicy.REASON_DISMISS, cooldown!!.reason)

        val second = UsageInterventionTrigger.maybeQueueFromDayUsage(
            store = store,
            access = access,
            nowEpochMs = now + 10,
            registry = registry,
            timeZone = tz
        )
        assertNull(second.queued)
        assertEquals(TriggerCooldownPolicy.REASON_DISMISS, second.suppressedReason)
    }

    @Test
    fun leaveMeAlone_armsCooldown_andSuppressesUsageIntervention() {
        AppUseReductionScenario.seed(store, seedAt)
        AppUseReductionScenario.onChip(store, "I needed a break; leave me alone.", now)
        val prefs = store.snapshot().proactivityPrefs
        assertNotNull(prefs.leaveMeAloneUntilEpochMs)
        assertTrue(prefs.leaveMeAloneUntilEpochMs!! > now)

        val access = FakeUsageStatsAccess(
            granted = true,
            foregroundByPackage = mapOf(
                SessionDestinations.INSTAGRAM_PACKAGE to 60L * 60_000L,
                SessionDestinations.YOUTUBE_PACKAGE to 10L * 60_000L
            )
        )
        val result = UsageInterventionTrigger.maybeQueueFromDayUsage(
            store = store,
            access = access,
            nowEpochMs = now + 5,
            registry = observeRegistry(access),
            timeZone = tz
        )
        assertNull(result.queued)
        assertEquals(TriggerCooldownPolicy.REASON_LEAVE_ME_ALONE, result.suppressedReason)
    }

    @Test
    fun usageOverTarget_queuesAtMostOneIntervention() {
        AppUseReductionScenario.seed(store, seedAt)
        val access = FakeUsageStatsAccess(
            granted = true,
            foregroundByPackage = mapOf(
                SessionDestinations.INSTAGRAM_PACKAGE to 30L * 60_000L,
                SessionDestinations.YOUTUBE_PACKAGE to 20L * 60_000L
            )
        )
        val registry = observeRegistry(access)
        val first = UsageInterventionTrigger.maybeQueueFromDayUsage(
            store = store,
            access = access,
            nowEpochMs = now,
            registry = registry,
            timeZone = tz
        )
        assertNotNull(first.queued)
        assertEquals("queued", first.queued!!.status)
        assertEquals(TriggerCooldownPolicy.KIND_USAGE_THRESHOLD, first.queued!!.kind)

        val second = UsageInterventionTrigger.maybeQueueFromDayUsage(
            store = store,
            access = access,
            nowEpochMs = now + 1,
            registry = registry,
            timeZone = tz
        )
        assertNull(second.queued)
        val active = store.snapshot().queuedInterventions.filter {
            it.status in TriggerCooldownPolicy.ACTIVE_STATUSES
        }
        assertEquals(1, active.size)
    }

    @Test
    fun quietHours_suppressQueue() {
        AppUseReductionScenario.seed(store, seedAt)
        store.apply(
            FlowCommand.SetProactivityPrefs(
                quietHoursStartMin = 0,
                quietHoursEndMin = 24 * 60,
                interruptionBudgetPerDay = 1,
                nowEpochMs = now
            )
        )
        val access = FakeUsageStatsAccess(
            granted = true,
            foregroundByPackage = mapOf(
                SessionDestinations.INSTAGRAM_PACKAGE to 50L * 60_000L
            )
        )
        val result = UsageInterventionTrigger.maybeQueueFromDayUsage(
            store = store,
            access = access,
            nowEpochMs = now + 1,
            registry = observeRegistry(access),
            timeZone = tz
        )
        assertNull(result.queued)
        assertEquals("quiet_hours", result.suppressedReason)
    }

    @Test
    fun shortsIdentity_emitsTypedCapabilityGapBlock() {
        AppUseReductionScenario.seed(store, seedAt)
        AppUseReductionScenario.onChip(
            store,
            "Track Shorts only automatically",
            now,
            CapabilityRegistry.default
        )
        val agent = store.snapshot().turns.last { it.role == "agent" }
        assertTrue(agent.blocks.any { it.type == ResponseBlockType.CAPABILITY_GAP })
        val gap = agent.blocks.first { it.type == ResponseBlockType.CAPABILITY_GAP }
        assertTrue(gap.body.contains("UsageStats", ignoreCase = true) ||
            gap.body.contains("Shorts", ignoreCase = true))
        assertEquals("Capability registry", gap.sourceLabel)
    }

    @Test
    fun freeTextShortsClaim_sameCapabilityGapPath() {
        AppUseReductionScenario.seed(store, seedAt)
        val intention = store.snapshot().intentions.first {
            it.id == AppUseReductionScenario.INTENTION_ID
        }
        ConversationCoordinator.submitFreeText(
            store = store,
            intention = intention,
            text = "Auto-detect my YouTube Shorts minutes",
            nowEpochMs = now,
            registry = CapabilityRegistry.default
        )
        val agent = store.snapshot().turns.last { it.role == "agent" }
        assertTrue(agent.blocks.any { it.type == ResponseBlockType.CAPABILITY_GAP })
    }

    @Test
    fun floaterAndConversation_shareInterventionTurnAndJobId() {
        AppUseReductionScenario.seed(store, seedAt)
        val access = FakeUsageStatsAccess(
            granted = true,
            foregroundByPackage = mapOf(
                SessionDestinations.INSTAGRAM_PACKAGE to 50L * 60_000L
            )
        )
        val base = observeRegistry(access)
        val registry = overlayRegistry(base)
        val queued = UsageInterventionTrigger.maybeQueueFromDayUsage(
            store = store,
            access = access,
            nowEpochMs = now,
            registry = registry,
            timeZone = tz
        ).queued!!

        val next = NextAction(
            kind = NextActionKind.DISCUSS,
            label = "Discuss",
            detail = "Queued usage check-in",
            intentionId = AppUseReductionScenario.INTENTION_ID
        )
        val presence = FloatingCompanion.presence(
            nextAction = next,
            overlayGranted = true,
            registry = registry,
            snapshot = store.snapshot()
        )
        assertEquals(FloatingAgentState.ATTENTION_NEEDED, presence.state)
        assertEquals(queued.jobId, presence.interventionJobId)
        assertEquals(queued.turnId, presence.interventionTurnId)
        assertTrue(presence.unreadIntervention)

        val opened = FloatingCompanion.onPress(store, next, now + 2, registry)
        assertEquals(DiscussFromHome.CONVERSATION_ROUTE, opened.navigateTo)
        assertEquals(queued.jobId, opened.interventionJobId)
        assertEquals(queued.turnId, opened.interventionTurnId)
        assertEquals(AppUseReductionScenario.INTENTION_ID, opened.intentionId)

        val turn = store.snapshot().turns.firstOrNull { it.id == queued.turnId }
        assertNotNull(turn)
        assertEquals("agent", turn!!.role)
        assertTrue(store.snapshot().queuedInterventions.any {
            it.jobId == queued.jobId && it.status == "shown"
        })
    }

    @Test
    fun persist_roundTrip_preservesProactivityAndBlocks() {
        AppUseReductionScenario.seed(store, seedAt)
        store.apply(
            FlowCommand.SetProactivityPrefs(
                quietHoursStartMin = 22 * 60,
                quietHoursEndMin = 7 * 60,
                interruptionBudgetPerDay = 1,
                nowEpochMs = now
            )
        )
        AppUseReductionScenario.onChip(store, "Track Shorts only automatically", now + 1)
        val access = FakeUsageStatsAccess(
            granted = true,
            foregroundByPackage = mapOf(
                SessionDestinations.INSTAGRAM_PACKAGE to 50L * 60_000L
            )
        )
        // Quiet hours wrap midnight — 15:00 UTC is outside 22:00–07:00.
        UsageInterventionTrigger.maybeQueueFromDayUsage(
            store = store,
            access = access,
            nowEpochMs = now + 2,
            registry = observeRegistry(access),
            timeZone = tz
        )
        val encoded = store.encode(store.snapshot())
        val wire = tmp.newFile("gate-d1-wire.json")
        wire.writeText(encoded)
        val reopened = IntentionFlowStore(wire)
        assertEquals(SCHEMA_VERSION, reopened.snapshot().schemaVersion)
        assertEquals(22 * 60, reopened.snapshot().proactivityPrefs.quietHoursStartMin)
        assertTrue(
            reopened.snapshot().turns.any { t ->
                t.blocks.any { it.type == ResponseBlockType.CAPABILITY_GAP }
            }
        )
        assertFalse(reopened.snapshot().queuedInterventions.isEmpty())
    }

    @Test
    fun underTarget_doesNotQueue() {
        AppUseReductionScenario.seed(store, seedAt)
        val access = FakeUsageStatsAccess(
            granted = true,
            foregroundByPackage = mapOf(
                SessionDestinations.INSTAGRAM_PACKAGE to 10L * 60_000L
            )
        )
        val result = UsageInterventionTrigger.maybeQueueFromDayUsage(
            store = store,
            access = access,
            nowEpochMs = now,
            registry = observeRegistry(access),
            timeZone = tz
        )
        assertNull(result.queued)
        assertEquals("under_target", result.suppressedReason)
    }
}
