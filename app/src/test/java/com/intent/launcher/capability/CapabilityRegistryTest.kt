package com.intent.launcher.capability

import com.intent.launcher.domain.NextActionKind
import com.intent.launcher.domain.ProposalStatus
import com.intent.launcher.domain.SpanishScenario
import com.intent.launcher.engine.NextActionEngine
import com.intent.launcher.store.IntentionFlowStore
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CapabilityRegistryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var store: IntentionFlowStore
    private val now = 1_700_000_000_000L
    private val tz = TimeZone.getTimeZone("UTC")
    private val registry = CapabilityRegistry.default

    @Before
    fun setUp() {
        store = IntentionFlowStore(tmp.newFile("flow.json"))
    }

    @Test
    fun registry_marksLessonCompletionUnsupported_andLaunchAvailable() {
        val lesson = registry.get(CapabilityRegistry.ID_OBSERVE_LESSON_COMPLETION)!!
        assertEquals(CapabilityStatus.NOT_SUPPORTED, lesson.status)
        assertTrue(lesson.limitNote.contains("lesson completion", ignoreCase = true))

        val launch = registry.get(CapabilityRegistry.ID_LAUNCH_INSTALLED_APP)!!
        assertEquals(CapabilityStatus.AVAILABLE, launch.status)
        assertTrue(registry.isActionable(CapabilityRegistry.ID_LAUNCH_INSTALLED_APP))
        assertTrue(registry.canLaunchInstalledApp())
        assertFalse(registry.isActionable(CapabilityRegistry.ID_OBSERVE_LESSON_COMPLETION))
    }

    @Test
    fun registry_marksPartnerConnectionsUnsupported() {
        assertTrue(registry.isUnsupported(CapabilityRegistry.ID_PARTNER_SPOTIFY))
        assertTrue(registry.isUnsupported(CapabilityRegistry.ID_PARTNER_CHESS))
        assertTrue(registry.isUnsupported(CapabilityRegistry.ID_PARTNER_STRAVA))
        assertFalse(registry.canUsePartnerSpotify())
        assertFalse(registry.canUsePartnerChess())
        assertFalse(registry.canUsePartnerStrava())
        assertTrue(
            registry.get(CapabilityRegistry.ID_PARTNER_SPOTIFY)!!.limitNote
                .contains("OAuth", ignoreCase = true)
        )
        assertTrue(
            registry.get(CapabilityRegistry.ID_PARTNER_CHESS)!!.limitNote
                .contains("manual SAID", ignoreCase = true)
        )
        assertTrue(
            registry.get(CapabilityRegistry.ID_PARTNER_STRAVA)!!.limitNote
                .contains("auto-Complete", ignoreCase = true)
        )
    }

    @Test
    fun registry_marksNotificationListenerPermissionNeeded_andQuietModeUnsupported() {
        assertTrue(registry.needsPermission(CapabilityRegistry.ID_NOTIFICATION_LISTENER))
        assertFalse(registry.canReadNotificationSummaries())
        assertTrue(registry.isUnsupported(CapabilityRegistry.ID_QUIET_MODE))
        assertTrue(
            registry.get(CapabilityRegistry.ID_NOTIFICATION_LISTENER)!!.limitNote
                .contains("Does not silence", ignoreCase = true)
        )
    }

    @Test
    fun registry_marksUndoTrashAvailable_andExternalUndoUnsupported() {
        assertTrue(registry.canUndoLast())
        assertTrue(registry.canSoftDelete())
        assertTrue(registry.canRestoreTrash())
        assertFalse(registry.canUndoExternalAction())
        assertEquals(
            CapabilityStatus.NOT_SUPPORTED,
            registry.get(CapabilityRegistry.ID_EXTERNAL_ACTION_UNDO)!!.status
        )
    }

    @Test
    fun registry_marksScreenCapturePermissionNeeded_withHonesty() {
        assertTrue(registry.needsPermission(CapabilityRegistry.ID_SCREEN_CAPTURE))
        assertFalse(registry.canUseScreenCapture())
        assertTrue(
            registry.get(CapabilityRegistry.ID_SCREEN_CAPTURE)!!.limitNote
                .contains("lesson completion", ignoreCase = true)
        )
        assertTrue(
            registry.get(CapabilityRegistry.ID_SCREEN_CAPTURE)!!.limitNote
                .contains("OCR", ignoreCase = true) ||
                registry.get(CapabilityRegistry.ID_SCREEN_CAPTURE)!!.limitNote
                    .contains("MediaProjection", ignoreCase = true)
        )
    }

    @Test
    fun registry_marksUsageStatsPermissionNeeded_andCalendarUnsupported() {
        assertTrue(registry.needsPermission(CapabilityRegistry.ID_USAGE_STATS))
        assertFalse(registry.isActionable(CapabilityRegistry.ID_USAGE_STATS))
        assertFalse(registry.canReadUsageStats())
        assertTrue(registry.needsPermission(CapabilityRegistry.ID_OBSERVE_APP_USAGE))
        assertFalse(registry.canObserveAppUsage())
        assertTrue(registry.isUnsupported(CapabilityRegistry.ID_OBSERVE_LESSON_COMPLETION))
        assertTrue(registry.isUnsupported(CapabilityRegistry.ID_EXTERNAL_CALENDAR))
        assertTrue(registry.canProposeRecurringSession())
        assertTrue(registry.canRecommendBegin())
    }

    @Test
    fun registry_marksQuietModeUnsupported_noFakeSilence() {
        assertTrue(registry.isUnsupported(CapabilityRegistry.ID_QUIET_MODE))
        assertFalse(registry.canApplyQuietMode())
        assertTrue(
            registry.get(CapabilityRegistry.ID_QUIET_MODE)!!.limitNote
                .contains("cannot silence", ignoreCase = true)
        )
    }

    @Test
    fun registry_marksNotificationReviewPermissionNeeded_andQuietUnsupported() {
        assertTrue(registry.needsPermission(CapabilityRegistry.ID_NOTIFICATION_LISTENER))
        assertTrue(registry.needsPermission(CapabilityRegistry.ID_SESSION_NOTIFICATION_REVIEW))
        assertFalse(registry.canReviewSessionNotifications())
        assertTrue(registry.isUnsupported(CapabilityRegistry.ID_QUIET_MODE))
        assertTrue(
            registry.get(CapabilityRegistry.ID_SESSION_NOTIFICATION_REVIEW)!!.limitNote
                .contains("titles/bodies", ignoreCase = true)
        )
    }

    @Test
    fun filterChips_dropsDishonestLessonCompletionSuggestion() {
        val mixed = SpanishScenario.suggestedChips + SpanishScenario.dishonestLessonCompletionChip
        val labels = registry.filterChips(mixed)
        assertTrue(labels.contains("Everyday conversations"))
        assertTrue(labels.contains("I meet a tutor on Thursdays at six"))
        assertFalse(labels.any { it.contains("Duolingo", ignoreCase = true) })
        assertFalse(labels.any { it.contains("lesson completion", ignoreCase = true) })
        assertEquals(SpanishScenario.conversationChips(registry), registry.filterChips(SpanishScenario.suggestedChips))
    }

    @Test
    fun spanishShapePath_proposalsAndNextActionsConsultRegistry() {
        SpanishScenario.seed(store, now)
        val chips = SpanishScenario.conversationChips(registry)
        assertTrue(chips.isNotEmpty())
        assertFalse(chips.any { it.contains("Duolingo", ignoreCase = true) })

        SpanishScenario.onChip(store, "I meet a tutor on Thursdays at six", now + 10, registry)
        var snap = store.snapshot()
        assertEquals(1, snap.proposals.size)
        assertEquals(ProposalStatus.PENDING, snap.proposals.first().status)

        val review = NextActionEngine.recommend(snap, now + 20, tz, registry)
        assertEquals(NextActionKind.REVIEW_PROPOSAL, review.kind)

        SpanishScenario.approve(store, now + 30)
        snap = store.snapshot()
        // Thursday 16 Nov 2023 18:30 UTC — due now for Thu 18:00 session
        val dueNow = 1_700_159_400_000L
        val begin = NextActionEngine.recommend(snap, dueNow, tz, registry)
        assertEquals(NextActionKind.BEGIN, begin.kind)
        assertEquals("Begin", begin.beginLabel)
        assertEquals("Later", begin.deferLabel)
        assertTrue(begin.reason.isNotBlank())
    }

    @Test
    fun whenProposeUnavailable_spanishChipRecordsFactButNoProposal() {
        val lockedNote = "Recurring session proposals are disabled in this test registry."
        val locked = CapabilityRegistry(
            CapabilityRegistry.DEFAULT_ENTRIES.map { entry ->
                if (entry.id == CapabilityRegistry.ID_PROPOSE_RECURRING_SESSION) {
                    entry.copy(status = CapabilityStatus.NOT_SUPPORTED, limitNote = lockedNote)
                } else {
                    entry
                }
            }
        )
        assertFalse(locked.canProposeRecurringSession())
        assertTrue(
            locked.filterChips(SpanishScenario.suggestedChips)
                .none { it.contains("tutor", ignoreCase = true) }
        )

        SpanishScenario.seed(store, now)
        SpanishScenario.onChip(store, "I meet a tutor on Thursdays at six", now + 5, locked)
        val snap = store.snapshot()
        assertEquals(1, snap.facts.size)
        assertTrue(snap.proposals.isEmpty())
        assertTrue(snap.turns.any { it.role == "agent" && it.text == lockedNote })
        val next = NextActionEngine.recommend(snap, now + 10, tz, locked)
        assertTrue(next.kind != NextActionKind.REVIEW_PROPOSAL)
    }

    @Test
    fun whenBeginUnavailable_nextActionDoesNotOfferBegin() {
        val noBegin = CapabilityRegistry(
            CapabilityRegistry.DEFAULT_ENTRIES.map { entry ->
                if (entry.id == CapabilityRegistry.ID_BEGIN_SESSION) {
                    entry.copy(status = CapabilityStatus.NOT_SUPPORTED)
                } else {
                    entry
                }
            }
        )
        SpanishScenario.seed(store, now)
        SpanishScenario.onChip(store, "I meet a tutor on Thursdays at six", now + 1, registry)
        SpanishScenario.approve(store, now + 2)
        val snap = store.snapshot()
        assertEquals(1, snap.sessions.size)
        val dueNow = 1_700_159_400_000L
        val next = NextActionEngine.recommend(snap, dueNow, tz, noBegin)
        assertTrue(next.kind != NextActionKind.BEGIN)
        assertTrue(next.reason.isNotBlank())
    }
}
