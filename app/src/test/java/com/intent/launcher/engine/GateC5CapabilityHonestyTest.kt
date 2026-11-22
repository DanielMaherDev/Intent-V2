package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.capability.CapabilityStatus
import com.intent.launcher.domain.AdaptiveBuilderFixtures
import com.intent.launcher.domain.ApproachActor
import com.intent.launcher.domain.ApproachStatus
import com.intent.launcher.domain.BuilderDecision
import com.intent.launcher.domain.ConnectionCandidates
import com.intent.launcher.domain.DestinationApp
import com.intent.launcher.domain.FactDraft
import com.intent.launcher.domain.SpanishScenario
import com.intent.launcher.store.IntentionFlowStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Gate C5 — capability-aware app suggestions: install / launch / usage /
 * progress-read kept distinct; never fabricate Duolingo lessons or partner progress.
 *
 * JVM-only: no live OpenAI / AVD review-card walk.
 */
class GateC5CapabilityHonestyTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var storeFile: File
    private lateinit var store: IntentionFlowStore
    private val registry = CapabilityRegistry.default
    private val now = 1_700_000_400_000L
    private val tz = AdaptiveBuilderFixtures.utc()

    @Before
    fun setUp() {
        storeFile = tmp.newFile("gate-c5-capability.json")
        store = IntentionFlowStore(storeFile)
    }

    @Test
    fun installedPackage_verifiedVsExternalPlayStoreSuggestion() {
        val none = FakeInstalledAppLauncher(installed = emptySet())
        assertTrue(AppConnectionInventory.installedOnly(none).isEmpty())
        val missingDuo = AppConnectionInventory.connections(none).first {
            it.packageName == ConnectionCandidates.PACKAGE_DUOLINGO
        }
        assertFalse(missingDuo.installed)
        assertFalse(missingDuo.canClaimPartnerFeed)

        // External suggestion is sourced Play Store — not an invented install.
        val offer = PlayStoreDeepLink.forPackage(
            ConnectionCandidates.PACKAGE_DUOLINGO,
            "Duolingo"
        )
        assertEquals(PlayStoreDeepLink.SOURCE_LABEL, offer.sourceLabel)
        assertTrue(offer.httpsUri.contains("play.google.com"))
        assertTrue(offer.note.contains("does not link", ignoreCase = true))

        val launchMissing = LaunchDestinationApp.attempt(
            DestinationApp(
                packageName = ConnectionCandidates.PACKAGE_DUOLINGO,
                label = "Duolingo"
            ),
            none,
            registry
        )
        assertEquals(LaunchDestinationApp.Outcome.MISSING_APP, launchMissing.outcome)
        assertFalse(launchMissing.note.contains("lesson completed", ignoreCase = true))
        assertTrue(launchMissing.note.contains("not lesson completion", ignoreCase = true))

        val duoOnly = FakeInstalledAppLauncher(
            installed = setOf(ConnectionCandidates.PACKAGE_DUOLINGO)
        )
        val installed = AppConnectionInventory.installedOnly(duoOnly)
        assertEquals(1, installed.size)
        assertEquals(ConnectionCandidates.PACKAGE_DUOLINGO, installed.single().packageName)
        assertTrue(installed.single().installed)
        // Other catalog apps stay unverified — never promoted to installed.
        assertTrue(
            AppConnectionInventory.connections(duoOnly)
                .filter { it.packageName != ConnectionCandidates.PACKAGE_DUOLINGO }
                .none { it.installed }
        )
    }

    @Test
    fun launchUsageAndProgressRead_keptDistinct_inRegistry() {
        val launch = registry.get(CapabilityRegistry.ID_LAUNCH_INSTALLED_APP)!!
        val usageStats = registry.get(CapabilityRegistry.ID_USAGE_STATS)!!
        val observeUsage = registry.get(CapabilityRegistry.ID_OBSERVE_APP_USAGE)!!
        val lesson = registry.get(CapabilityRegistry.ID_OBSERVE_LESSON_COMPLETION)!!
        val progress = registry.get(CapabilityRegistry.ID_PROGRESS_REPORTS)!!

        assertEquals(CapabilityStatus.AVAILABLE, launch.status)
        assertTrue(registry.canLaunchInstalledApp())
        assertTrue(launch.limitNote.contains("cannot read", ignoreCase = true))

        assertEquals(CapabilityStatus.PERMISSION_NEEDED, usageStats.status)
        assertEquals(CapabilityStatus.PERMISSION_NEEDED, observeUsage.status)
        assertFalse(registry.canReadUsageStats())
        assertFalse(registry.canObserveAppUsage())
        assertTrue(usageStats.limitNote.contains("UsageStats", ignoreCase = true))
        assertTrue(observeUsage.limitNote.contains("package foreground", ignoreCase = true))

        assertEquals(CapabilityStatus.NOT_SUPPORTED, lesson.status)
        assertFalse(registry.isActionable(CapabilityRegistry.ID_OBSERVE_LESSON_COMPLETION))
        assertTrue(lesson.limitNote.contains("lesson completion", ignoreCase = true))

        // Progress reports are local SAID/OBSERVED proof — not partner lesson read.
        assertEquals(CapabilityStatus.AVAILABLE, progress.status)
        assertTrue(progress.limitNote.contains("never invents", ignoreCase = true))

        // Distinct axes — statuses must not collapse into one.
        assertNotEquals(launch.status, usageStats.status)
        assertNotEquals(launch.status, lesson.status)
        assertNotEquals(usageStats.status, lesson.status)
        assertNotEquals(observeUsage.status, lesson.status)

        // Conversation chips must not offer dishonest Duolingo lesson completion.
        val chips = registry.filterChips(
            SpanishScenario.suggestedChips + SpanishScenario.dishonestLessonCompletionChip
        )
        assertFalse(chips.any { it.contains("Duolingo", ignoreCase = true) })
        assertFalse(chips.any { it.contains("lesson completion", ignoreCase = true) })
    }

    @Test
    fun offerApproachesAndPropose_doNotInventDuolingoLessonsOrProgress() {
        AdaptiveBuilderFixtures.seedSpanishDiscovery(store, now)
        val id = AdaptiveBuilderFixtures.SPANISH_ID
        var t = now

        fun rev(): Long =
            AdaptiveBuilderEngine.projectContext(store.snapshot(), id, t, tz)!!.revision

        // Non-approximate move so policy reaches OfferApproaches (not stale reconfirm).
        listOf(
            Triple("fact-lang-c5", AdaptiveBuilderEngine.KEY_TARGET_LANGUAGE, "Spanish"),
            Triple("fact-mot-c5", AdaptiveBuilderEngine.KEY_MOTIVATION, "Moving to Spain"),
            Triple("fact-move-c5", AdaptiveBuilderEngine.KEY_MOVE_ESTIMATE, "June 2027")
        ).forEach { (factId, key, text) ->
            AdaptiveBuilderEngine.applyDecision(
                store,
                BuilderDecision.SaveFact(
                    intentionId = id,
                    expectedRevision = rev(),
                    factDraft = FactDraft(factId = factId, text = text, key = key),
                    sourceTurnId = "turn-$factId"
                ),
                t,
                timeZone = tz
            )
            t += 2
        }

        val offerDecision = AdaptiveBuilderEngine.suggestNextDecision(
            AdaptiveBuilderEngine.projectContext(store.snapshot(), id, t, tz)!!,
            t,
            tz
        )
        assertTrue(
            "expected OfferApproaches, got $offerDecision",
            offerDecision is BuilderDecision.OfferApproaches
        )
        val offer = offerDecision as BuilderDecision.OfferApproaches
        val learningApp = offer.approachDrafts.first {
            it.approachId == "approach-learning-app"
        }
        assertEquals(ApproachActor.APP, learningApp.actor)
        assertTrue(learningApp.summary.contains("installed", ignoreCase = true))
        assertTrue(
            learningApp.capabilityNote.contains("Launch", ignoreCase = true) ||
                learningApp.capabilityNote.contains("foreground", ignoreCase = true)
        )
        assertTrue(
            learningApp.capabilityNote.contains("not lesson completion", ignoreCase = true)
        )
        // Offer text must not invent lesson counts or partner Duolingo progress.
        assertFalse(
            offer.approachDrafts.any { draft ->
                inventsLessonOrPartnerProgress(draft.capabilityNote) ||
                    inventsLessonOrPartnerProgress(draft.summary) ||
                    inventsLessonOrPartnerProgress(draft.title)
            }
        )

        assertTrue(
            AdaptiveBuilderEngine.applyDecision(store, offer, t, timeZone = tz).accepted
        )
        t += 2
        AdaptiveBuilderEngine.selectApproaches(
            store,
            id,
            listOf("approach-learning-app"),
            t,
            "turn-select-c5",
            rev(),
            "TEXT_CHIP"
        )
        t += 2
        AdaptiveBuilderEngine.applyDecision(
            store,
            BuilderDecision.SaveFact(
                intentionId = id,
                expectedRevision = rev(),
                factDraft = FactDraft(
                    factId = "fact-freq-c5",
                    text = "3 days per week",
                    key = AdaptiveBuilderEngine.KEY_PRACTICE_FREQUENCY
                ),
                sourceTurnId = "turn-freq-c5"
            ),
            t,
            timeZone = tz
        )
        t += 2

        val propose = AdaptiveBuilderEngine.suggestNextDecision(
            AdaptiveBuilderEngine.projectContext(store.snapshot(), id, t, tz)!!,
            t,
            tz
        )
        assertTrue("expected Propose, got $propose", propose is BuilderDecision.Propose)
        val proposeDecision = propose as BuilderDecision.Propose
        assertFalse(inventsLessonOrPartnerProgress(proposeDecision.capabilityHonesty))
        assertFalse(inventsLessonOrPartnerProgress(proposeDecision.summary))
        assertFalse(inventsLessonOrPartnerProgress(proposeDecision.methodsSummary))
        assertTrue(
            proposeDecision.capabilityHonesty.contains("not lesson completion", ignoreCase = true) ||
                proposeDecision.capabilityHonesty.contains("check-in", ignoreCase = true) ||
                proposeDecision.capabilityHonesty.contains("Launch", ignoreCase = true)
        )

        assertTrue(
            AdaptiveBuilderEngine.applyDecision(store, proposeDecision, t, timeZone = tz).accepted
        )

        val snap = store.snapshot()
        assertTrue(
            snap.approaches.any {
                it.id == "approach-learning-app" &&
                    it.intentionId == id &&
                    it.status == ApproachStatus.SELECTED &&
                    it.actor == ApproachActor.APP &&
                    it.capabilityNote.contains("not lesson completion", ignoreCase = true)
            }
        )
        val pending = snap.proposals.first { it.intentionId == id }
        assertFalse(inventsLessonOrPartnerProgress(pending.capabilityHonesty))
        assertFalse(inventsLessonOrPartnerProgress(pending.summary))
        assertFalse(inventsLessonOrPartnerProgress(pending.methodsSummary))
        // Selecting a learning-app approach does not fabricate Duolingo progress facts.
        assertTrue(
            snap.facts.none {
                it.intentionId == id && inventsLessonOrPartnerProgress(it.text)
            }
        )
        assertTrue(snap.sessions.none { it.intentionId == id })
    }

    @Test
    fun launchAttempt_whenInstalled_neverClaimsProgressRead() {
        val duo = DestinationApp(
            packageName = ConnectionCandidates.PACKAGE_DUOLINGO,
            label = "Duolingo"
        )
        val launcher = FakeInstalledAppLauncher(
            installed = setOf(ConnectionCandidates.PACKAGE_DUOLINGO)
        )
        val result = LaunchDestinationApp.attempt(duo, launcher, registry)
        assertEquals(LaunchDestinationApp.Outcome.LAUNCHED, result.outcome)
        assertTrue(result.note.contains("Opened Duolingo", ignoreCase = true))
        assertTrue(result.note.contains("not lesson completion", ignoreCase = true))
        assertFalse(inventsLessonOrPartnerProgress(result.note))
        // Launch capability stays distinct from usage / progress-read.
        assertTrue(registry.canLaunchInstalledApp())
        assertFalse(registry.canObserveAppUsage())
        assertFalse(registry.isActionable(CapabilityRegistry.ID_OBSERVE_LESSON_COMPLETION))
    }

    private fun inventsLessonOrPartnerProgress(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("lesson completed") ||
            lower.contains("lessons completed") ||
            lower.contains("lesson was completed") ||
            lower.contains("lesson completion synced") ||
            Regex("""\b\d+\s+lessons?\b""").containsMatchIn(lower) ||
            lower.contains("duolingo xp") ||
            lower.contains("duolingo streak") ||
            lower.contains("partner progress") ||
            lower.contains("read your duolingo") ||
            lower.contains("synced duolingo")
    }
}
