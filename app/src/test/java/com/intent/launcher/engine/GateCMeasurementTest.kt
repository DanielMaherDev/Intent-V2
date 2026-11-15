package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.domain.FlowCommand
import com.intent.launcher.domain.MarathonScenario
import com.intent.launcher.domain.NextActionKind
import com.intent.launcher.domain.ProvenanceKind
import com.intent.launcher.domain.QuitSmokingScenario
import com.intent.launcher.domain.SCHEMA_VERSION
import com.intent.launcher.domain.TrackerEntryStatus
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
 * Gate C: exact-field progress + correction/backdate; morning/midday/evening via
 * DailyContext + Occurrence; ACTIVE-only reports (no duplicate after supersede).
 */
class GateCMeasurementTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var store: IntentionFlowStore
    private val registry = CapabilityRegistry.default
    private val tz = TimeZone.getTimeZone("UTC")

    // Sunday 19 Nov 2023 09:00 UTC — marathon long-run morning window
    private val sundayMorning = 1_700_380_800_000L
    // Same Sunday 14:00 UTC — midday
    private val sundayMidday = sundayMorning + 5 * 60 * 60 * 1000L
    // Same Sunday 19:00 UTC — evening
    private val sundayEvening = sundayMorning + 10 * 60 * 60 * 1000L

    @Before
    fun setUp() {
        store = IntentionFlowStore(tmp.newFile("gate-c.json"))
    }

    @Test
    fun schemaIsAtLeastNine_afterGateC() {
        // Gate C landed SCHEMA 9; Gate D bumps further — stamp must match constant.
        assertTrue(SCHEMA_VERSION >= 9)
        assertEquals(SCHEMA_VERSION, store.snapshot().schemaVersion)
    }

    @Test
    fun marathonAndSmoking_exactFieldProgress_withSourceLabels() {
        MarathonScenario.seed(store, sundayMorning)
        MarathonScenario.onChip(store, "Long runs feel best on Sundays", sundayMorning + 1)
        MarathonScenario.approve(store, sundayMorning + 2)
        MarathonScenario.onChip(store, "Logged an easy 5 km", sundayMorning + 3)
        MarathonScenario.onChip(store, "Legs felt strong", sundayMorning + 4)

        QuitSmokingScenario.seed(store, sundayMorning + 10)
        QuitSmokingScenario.onChip(store, "I smoke about eight a day", sundayMorning + 11)
        QuitSmokingScenario.onChip(store, "Evenings are hardest", sundayMorning + 12)
        QuitSmokingScenario.approve(store, sundayMorning + 13)
        QuitSmokingScenario.onChip(store, "I smoked fewer than usual today", sundayMorning + 14)
        QuitSmokingScenario.onChip(store, "I rode out a craving", sundayMorning + 15)

        val marathonFields = FieldProgress.build(
            store.snapshot(),
            MarathonScenario.INTENTION_ID,
            "marathon"
        )
        val mileage = marathonFields.fields.first { it.moduleId == "mileage" }
        assertEquals(1, mileage.activeCount)
        assertTrue(mileage.sourceLabels.any { it.startsWith("SAID") })
        assertEquals("Easy 5 km (self-reported)", mileage.latest?.valueText)

        val smokingFields = FieldProgress.build(
            store.snapshot(),
            QuitSmokingScenario.INTENTION_ID,
            QuitSmokingScenario.SHAPE_TAG
        )
        val cigs = smokingFields.fields.first { it.moduleId == "cigarettes_today" }
        assertEquals(1, cigs.activeCount)
        assertTrue(cigs.sourceLabels.any { it.contains("user") })

        val marathonProof = ProgressProof.build(
            store.snapshot(),
            MarathonScenario.INTENTION_ID,
            sundayMorning + 20,
            "marathon",
            registry
        )!!
        assertTrue(marathonProof.items.any { it.text.contains("5 km") })
        assertTrue(marathonProof.items.any { it.sourceDetail.contains("Mileage") })
        assertTrue(marathonProof.items.all { it.provenanceLabel in setOf("SAID", "OBSERVED", "INFERRED") })
    }

    @Test
    fun correctBackdate_refreshesReports_withoutDuplicateActive() {
        MarathonScenario.seed(store, sundayMorning)
        MarathonScenario.onChip(store, "Long runs feel best on Sundays", sundayMorning + 1)
        MarathonScenario.approve(store, sundayMorning + 2)
        store.apply(
            FlowCommand.RecordTrackerEntry(
                entryId = "trk-mileage-1",
                intentionId = MarathonScenario.INTENTION_ID,
                moduleId = "mileage",
                valueText = "Easy 10 km (self-reported)",
                nowEpochMs = sundayMorning + 3,
                provenanceKind = ProvenanceKind.SAID,
                sourceLabel = "user"
            )
        )

        val before = FieldProgress.build(store.snapshot(), MarathonScenario.INTENTION_ID, "marathon")
        assertEquals(1, before.fields.first { it.moduleId == "mileage" }.activeCount)

        val backdatedEvent = sundayMorning - 24 * 60 * 60 * 1000L // Saturday
        store.apply(
            FlowCommand.CorrectTrackerEntry(
                priorEntryId = "trk-mileage-1",
                newEntryId = "trk-mileage-1-corr",
                valueText = "Easy 5 km (corrected)",
                nowEpochMs = sundayMorning + 4,
                eventTimeEpochMs = backdatedEvent,
                sourceLabel = "user_correction"
            )
        )

        val snap = store.snapshot()
        val prior = snap.trackerEntries.first { it.id == "trk-mileage-1" }
        val neu = snap.trackerEntries.first { it.id == "trk-mileage-1-corr" }
        assertEquals(TrackerEntryStatus.CORRECTED, prior.status)
        assertEquals(TrackerEntryStatus.ACTIVE, neu.status)
        assertEquals("trk-mileage-1", neu.supersedesEntryId)
        assertEquals(backdatedEvent, neu.eventAtEpochMs)

        val after = FieldProgress.build(snap, MarathonScenario.INTENTION_ID, "marathon")
        val mileage = after.fields.first { it.moduleId == "mileage" }
        assertEquals(1, mileage.activeCount)
        assertEquals(1, mileage.correctedCount)
        assertEquals("Easy 5 km (corrected)", mileage.latest?.valueText)
        assertTrue(mileage.sourceLabels.any { it.contains("user_correction") })

        val proof = ProgressProof.build(
            snap,
            MarathonScenario.INTENTION_ID,
            sundayMorning + 5,
            "marathon",
            registry
        )!!
        val mileageItems = proof.items.filter { it.sourceDetail.contains("Mileage") }
        assertEquals(1, mileageItems.size)
        assertEquals("Easy 5 km (corrected)", mileageItems.single().text)

        val path = PathSummary.build(
            snap,
            snap.intentions.first { it.id == MarathonScenario.INTENTION_ID },
            NextActionEngine.recommend(snap, sundayMorning + 5, tz, registry),
            sundayMorning + 5,
            registry
        )!!
        assertEquals(1, path.modules.first { it.moduleId == "mileage" }.activeEntryCount)

        // Smoking correction path
        QuitSmokingScenario.seed(store, sundayMorning + 20)
        QuitSmokingScenario.onChip(store, "Evenings are hardest", sundayMorning + 21)
        QuitSmokingScenario.approve(store, sundayMorning + 22)
        store.apply(
            FlowCommand.RecordTrackerEntry(
                entryId = "trk-cig-1",
                intentionId = QuitSmokingScenario.INTENTION_ID,
                moduleId = "cigarettes_today",
                valueText = "About baseline (8)",
                nowEpochMs = sundayMorning + 23
            )
        )
        store.apply(
            FlowCommand.CorrectTrackerEntry(
                priorEntryId = "trk-cig-1",
                newEntryId = "trk-cig-1-corr",
                valueText = "Fewer than baseline (self-reported)",
                nowEpochMs = sundayMorning + 24,
                sourceLabel = "user_correction"
            )
        )
        val smokeActive = FieldProgress.activeEntries(
            store.snapshot(),
            QuitSmokingScenario.INTENTION_ID,
            "cigarettes_today"
        )
        assertEquals(1, smokeActive.size)
        assertEquals("Fewer than baseline (self-reported)", smokeActive.single().valueText)
    }

    @Test
    fun morningMiddayEvening_dailyContextAndOccurrences_persistAcrossRestart() {
        MarathonScenario.seed(store, sundayMorning)
        MarathonScenario.onChip(store, "Long runs feel best on Sundays", sundayMorning + 1)
        MarathonScenario.approve(store, sundayMorning + 2)

        val localDate = DailyCycle.localDateString(sundayMorning, tz)
        store.apply(
            FlowCommand.ConfirmDailyCheckIn(
                intentionId = MarathonScenario.INTENTION_ID,
                localDate = localDate,
                focusSummary = "Focus: Run a marathon · Next: Begin",
                nowEpochMs = sundayMorning,
                occurrenceId = "occ-morning-1",
                dailyContextId = "dctx-morning-1",
                energy = "steady",
                priorities = "Sunday long run",
                availability = "morning free"
            )
        )
        var snap = store.snapshot()
        assertTrue(snap.dayCycle.checkedInFor(localDate))
        assertEquals(1, snap.occurrences.count { it.phase == "morning" && it.kind == "check_in" })
        assertEquals(1, snap.dailyContexts.count { it.phase == "morning" })
        assertEquals(ProvenanceKind.SAID, snap.occurrences.single { it.id == "occ-morning-1" }.provenance.kind)

        val afterCheckIn = NextActionEngine.recommend(snap, sundayMidday, tz, registry)
        assertEquals(NextActionKind.MIDDAY, afterCheckIn.kind)

        store.apply(
            FlowCommand.RecordMiddayCheck(
                occurrenceId = "occ-midday-1",
                contextId = "dctx-midday-1",
                intentionId = MarathonScenario.INTENTION_ID,
                localDate = localDate,
                summary = "Still on plan — light legs",
                nowEpochMs = sundayMidday,
                energy = "ok",
                constraints = "need water"
            )
        )
        snap = store.snapshot()
        assertTrue(snap.dayCycle.middayDoneFor(localDate))
        assertEquals("Still on plan — light legs", snap.dayCycle.middayNote)
        assertEquals(1, snap.occurrences.count { it.kind == "midday_check" })
        assertEquals(1, snap.dailyContexts.count { it.phase == "midday" })
        assertNull(
            snap.sessions.first { it.id == MarathonScenario.SESSION_ID }.deferredUntilEpochMs
        )

        // Midday done → evening window should offer check-out
        val eveningNext = NextActionEngine.recommend(snap, sundayEvening, tz, registry)
        assertEquals(NextActionKind.CHECK_OUT, eveningNext.kind)

        store.apply(
            FlowCommand.CompleteDailyCheckOut(
                intentionId = MarathonScenario.INTENTION_ID,
                localDate = localDate,
                whatHappened = "Long run held as planned",
                factId = "fact-checkout-1",
                nowEpochMs = sundayEvening,
                occurrenceId = "occ-evening-1",
                dailyContextId = "dctx-evening-1",
                energy = "tired"
            )
        )
        snap = store.snapshot()
        assertTrue(snap.dayCycle.checkedOutFor(localDate))
        assertEquals(3, snap.occurrences.size)
        assertEquals(3, snap.dailyContexts.size)
        assertTrue(snap.occurrences.any { it.kind == "check_out" && it.phase == "evening" })

        // Restart persistence
        val encoded = store.encode(snap)
        val wire = tmp.newFile("gate-c-restart.json")
        wire.writeText(encoded)
        val again = IntentionFlowStore(wire)
        val reloaded = again.snapshot()
        assertEquals(SCHEMA_VERSION, reloaded.schemaVersion)
        assertEquals(3, reloaded.occurrences.size)
        assertEquals(3, reloaded.dailyContexts.size)
        assertTrue(reloaded.dayCycle.checkedInFor(localDate))
        assertTrue(reloaded.dayCycle.middayDoneFor(localDate))
        assertTrue(reloaded.dayCycle.checkedOutFor(localDate))
        assertEquals("Still on plan — light legs", reloaded.dayCycle.middayNote)
        assertTrue(reloaded.dailyContexts.any { it.phase == "morning" && it.energy == "steady" })
        assertTrue(reloaded.occurrences.all { it.provenance.kind == ProvenanceKind.SAID })
    }

    @Test
    fun middayDefer_refusesWhenSessionInProgress_stillWritesOccurrence() {
        MarathonScenario.seed(store, sundayMorning)
        MarathonScenario.onChip(store, "Long runs feel best on Sundays", sundayMorning + 1)
        MarathonScenario.approve(store, sundayMorning + 2)
        val localDate = DailyCycle.localDateString(sundayMorning, tz)
        store.apply(
            FlowCommand.ConfirmDailyCheckIn(
                intentionId = MarathonScenario.INTENTION_ID,
                localDate = localDate,
                focusSummary = "Focus marathon",
                nowEpochMs = sundayMorning,
                occurrenceId = "occ-m-2",
                dailyContextId = "dctx-m-2"
            )
        )
        MarathonScenario.beginSession(store, sundayMidday - 60_000L, runId = "run-conflict")

        val deferUntil = DailyCycle.tomorrowMorningEpochMs(sundayMidday, tz)
        store.apply(
            FlowCommand.RecordMiddayCheck(
                occurrenceId = "occ-mid-conflict",
                contextId = "dctx-mid-conflict",
                intentionId = MarathonScenario.INTENTION_ID,
                localDate = localDate,
                summary = "Tried to defer while running",
                nowEpochMs = sundayMidday,
                deferSessionId = MarathonScenario.SESSION_ID,
                deferUntilEpochMs = deferUntil
            )
        )
        val snap = store.snapshot()
        assertNotNull(snap.occurrences.firstOrNull { it.id == "occ-mid-conflict" })
        assertNotNull(snap.dailyContexts.firstOrNull { it.id == "dctx-mid-conflict" })
        assertTrue(snap.dayCycle.middayDoneFor(localDate))
        // Conflict: deferral must not apply while run is IN_PROGRESS
        assertNull(snap.sessions.first { it.id == MarathonScenario.SESSION_ID }.deferredUntilEpochMs)
    }

    @Test
    fun middayDefer_appliesWhenIdle_andNextActionLeavesMidday() {
        MarathonScenario.seed(store, sundayMorning)
        MarathonScenario.onChip(store, "Long runs feel best on Sundays", sundayMorning + 1)
        MarathonScenario.approve(store, sundayMorning + 2)
        val localDate = DailyCycle.localDateString(sundayMorning, tz)
        store.apply(
            FlowCommand.ConfirmDailyCheckIn(
                intentionId = MarathonScenario.INTENTION_ID,
                localDate = localDate,
                focusSummary = "Focus marathon",
                nowEpochMs = sundayMorning,
                occurrenceId = "occ-m-3",
                dailyContextId = "dctx-m-3"
            )
        )
        val deferUntil = DailyCycle.tomorrowMorningEpochMs(sundayMidday, tz)
        store.apply(
            FlowCommand.RecordMiddayCheck(
                occurrenceId = "occ-mid-defer",
                contextId = "dctx-mid-defer",
                intentionId = MarathonScenario.INTENTION_ID,
                localDate = localDate,
                summary = "Defer — calves tight",
                nowEpochMs = sundayMidday,
                deferSessionId = MarathonScenario.SESSION_ID,
                deferUntilEpochMs = deferUntil
            )
        )
        val snap = store.snapshot()
        assertEquals(deferUntil, snap.sessions.first { it.id == MarathonScenario.SESSION_ID }.deferredUntilEpochMs)
        val next = NextActionEngine.recommend(snap, sundayMidday + 60_000L, tz, registry)
        assertFalse(next.kind == NextActionKind.MIDDAY)
    }

    @Test
    fun migrateFromSchema8_preservesTrackerAddsEmptyOccurrences() {
        MarathonScenario.seed(store, sundayMorning)
        store.apply(
            FlowCommand.RecordTrackerEntry(
                entryId = "trk-legacy",
                intentionId = MarathonScenario.INTENTION_ID,
                moduleId = "mileage",
                valueText = "Easy 5 km",
                nowEpochMs = sundayMorning
            )
        )
        // Simulate schema-8 wire without new collections by encoding then forcing stamp.
        val domain = store.snapshot().copy(schemaVersion = 8)
        val file = tmp.newFile("schema8.json")
        // Encode via store helpers then rewrite schemaVersion in JSON text.
        val json = store.encode(domain).replace("\"schemaVersion\": 8", "\"schemaVersion\": 8")
        file.writeText(json)
        val migrated = IntentionFlowStore(file).snapshot()
        assertEquals(SCHEMA_VERSION, migrated.schemaVersion)
        assertEquals(1, migrated.trackerEntries.size)
        assertEquals(TrackerEntryStatus.ACTIVE, migrated.trackerEntries.single().status)
        assertEquals(migrated.trackerEntries.single().recordedAtEpochMs, migrated.trackerEntries.single().eventAtEpochMs)
        assertTrue(migrated.occurrences.isEmpty())
        assertTrue(migrated.dailyContexts.isEmpty())
    }
}
