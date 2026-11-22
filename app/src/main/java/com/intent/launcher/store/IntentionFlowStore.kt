package com.intent.launcher.store

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.intent.launcher.domain.AdaptiveBuilderState
import com.intent.launcher.domain.Approach
import com.intent.launcher.domain.ApproachStatus
import com.intent.launcher.domain.BuilderDecisionEvent
import com.intent.launcher.domain.BuilderDraft
import com.intent.launcher.domain.BuilderPhase
import com.intent.launcher.domain.ConversationTurn
import com.intent.launcher.domain.DailyContext
import com.intent.launcher.domain.DayCycleState
import com.intent.launcher.domain.DrawerCardLayout
import com.intent.launcher.domain.DrawerCards
import com.intent.launcher.domain.ErrandStatus
import com.intent.launcher.domain.ErrandTodo
import com.intent.launcher.domain.Evidence
import com.intent.launcher.domain.EvidenceAccuracy
import com.intent.launcher.domain.Fact
import com.intent.launcher.domain.FactConfirmation
import com.intent.launcher.domain.FactStatus
import com.intent.launcher.domain.FlowCommand
import com.intent.launcher.domain.FlowTransaction
import com.intent.launcher.domain.HeldApp
import com.intent.launcher.domain.Hypothesis
import com.intent.launcher.domain.Intention
import com.intent.launcher.domain.IntentionPhase
import com.intent.launcher.domain.IntentionSnapshot
import com.intent.launcher.domain.Occurrence
import com.intent.launcher.domain.OpenQuestion
import com.intent.launcher.domain.OpenQuestionState
import com.intent.launcher.domain.AnswerSchema
import com.intent.launcher.domain.ApproachActor
import com.intent.launcher.domain.ProactivityPrefs
import com.intent.launcher.domain.Proposal
import com.intent.launcher.domain.ProposalStatus
import com.intent.launcher.domain.Provenance
import com.intent.launcher.domain.ProvenanceKind
import com.intent.launcher.domain.QueuedIntervention
import com.intent.launcher.domain.ResponseBlock
import com.intent.launcher.domain.ResponseBlockType
import com.intent.launcher.domain.SCHEMA_VERSION
import com.intent.launcher.domain.ScheduledSession
import com.intent.launcher.domain.SessionRun
import com.intent.launcher.domain.SessionRunStatus
import com.intent.launcher.domain.ThoughtCapture
import com.intent.launcher.domain.ThoughtCaptureStatus
import com.intent.launcher.domain.ThoughtDisposition
import com.intent.launcher.domain.TrackerEntry
import com.intent.launcher.domain.TrackerEntryStatus
import com.intent.launcher.engine.BuilderAnswerCard
import com.intent.launcher.domain.TrashEntry
import com.intent.launcher.domain.TrashKind
import com.intent.launcher.domain.TriggerCooldown
import com.intent.launcher.domain.TriageItem
import com.intent.launcher.domain.TriageItemStatus
import com.intent.launcher.domain.TriageState
import com.intent.launcher.engine.CommandReversibility
import com.intent.launcher.engine.IntentionBuilder
import com.intent.launcher.engine.IntentionHierarchy
import com.intent.launcher.engine.TriggerCooldownPolicy
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/**
 * Single command path into the persistent intention model.
 * Agents propose FlowCommands; they never rewrite the file directly.
 * Reversible commitments append to the transaction log for undo (Rule 3 / §9).
 */
class IntentionFlowStore(
    private val file: File
) {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val state = AtomicReference(IntentionSnapshot())

    init {
        state.set(loadFromDiskOrEmpty())
        // Ensure durable file exists after recovery / first boot.
        if (!file.exists() || file.length() == 0L) {
            persist(state.get())
        }
    }

    /**
     * Gate D3 — survive process kill mid-write.
     * Prefer the primary file; if missing/empty/corrupt, recover a complete
     * sibling `.tmp` written by [persist] before the atomic rename.
     */
    private fun loadFromDiskOrEmpty(): IntentionSnapshot {
        val primary = readDecodable(file)
        if (primary != null) return primary
        val tmp = File(file.parentFile, file.name + ".tmp")
        val recovered = readDecodable(tmp)
        if (recovered != null) {
            // Promote recovered tmp so the next process sees the durable primary.
            runCatching {
                file.parentFile?.mkdirs()
                tmp.copyTo(file, overwrite = true)
            }
            return recovered
        }
        return IntentionSnapshot()
    }

    private fun readDecodable(candidate: File): IntentionSnapshot? {
        if (!candidate.exists() || candidate.length() == 0L) return null
        return runCatching { decode(candidate.readText()) }.getOrNull()
    }

    /** Test / recovery helper — reload from disk (primary, then tmp). */
    @Synchronized
    fun reloadFromDisk(): IntentionSnapshot {
        val loaded = loadFromDiskOrEmpty()
        state.set(loaded)
        return loaded
    }

    /**
     * Local snapshot import — replace in-memory + on-disk state from export JSON.
     * Uses the same [decode]/[migrate] stamp path as normal load (no store rewrite).
     */
    @Synchronized
    fun replaceFromJson(raw: String): IntentionSnapshot {
        val migrated = decode(raw)
        state.set(migrated)
        persist(migrated)
        return migrated
    }

    fun snapshot(): IntentionSnapshot = state.get()

    @Synchronized
    fun apply(command: FlowCommand): IntentionSnapshot {
        val current = migrate(state.get())
        val next = when (command) {
            is FlowCommand.UndoLastReversible -> undoLast(current, command)
            else -> {
                val reduced = reduce(current, command)
                appendHistory(current, reduced, command)
            }
        }
        state.set(next)
        persist(next)
        return next
    }

    fun reduce(current: IntentionSnapshot, command: FlowCommand): IntentionSnapshot {
        val migrated = migrate(current)
        return when (command) {
            is FlowCommand.SeedExploring -> {
                val parentId = command.parentIntentionId?.takeIf { candidate ->
                    candidate != command.intentionId &&
                        migrated.intentions.any { it.id == candidate }
                }
                val intention = Intention(
                    id = command.intentionId,
                    title = command.title,
                    statement = command.statement,
                    phase = IntentionPhase.EXPLORING,
                    createdAtEpochMs = command.nowEpochMs,
                    updatedAtEpochMs = command.nowEpochMs,
                    shapeTag = command.shapeTag,
                    parentIntentionId = parentId
                )
                val existingIds = migrated.intentions.map { it.id }
                val nextOrder = when {
                    intention.id in migrated.intentionOrder -> migrated.intentionOrder
                    migrated.intentionOrder.isEmpty() -> existingIds.filterNot { it == intention.id } + intention.id
                    else -> migrated.intentionOrder.filterNot { it == intention.id } + intention.id
                }
                migrated.copy(
                    intentions = migrated.intentions.filterNot { it.id == intention.id } + intention,
                    activeIntentionId = command.intentionId,
                    intentionOrder = nextOrder
                )
            }

            is FlowCommand.AddUserFact -> {
                val fact = Fact(
                    id = command.factId,
                    intentionId = command.intentionId,
                    text = command.text,
                    provenance = Provenance(ProvenanceKind.SAID, command.nowEpochMs, "user")
                )
                migrated.copy(
                    facts = migrated.facts.filterNot { it.id == fact.id } + fact,
                    intentions = touch(migrated.intentions, command.intentionId, command.nowEpochMs)
                )
            }

            is FlowCommand.AddHypothesis -> {
                val h = Hypothesis(
                    id = command.hypothesisId,
                    intentionId = command.intentionId,
                    text = command.text,
                    provenance = Provenance(ProvenanceKind.INFERRED, command.nowEpochMs, "agent")
                )
                migrated.copy(
                    hypotheses = migrated.hypotheses.filterNot { it.id == h.id } + h,
                    intentions = touch(migrated.intentions, command.intentionId, command.nowEpochMs)
                )
            }

            is FlowCommand.AddConversationTurn -> {
                val blocks = command.blocks.filter { block ->
                    // Reject unknown enum values at write path (contracts §4).
                    runCatching { ResponseBlockType.valueOf(block.type.name) }.isSuccess
                }
                val turn = ConversationTurn(
                    id = command.turnId,
                    intentionId = command.intentionId,
                    role = command.role,
                    text = command.text,
                    atEpochMs = command.nowEpochMs,
                    modality = command.modality,
                    blocks = blocks
                )
                migrated.copy(turns = migrated.turns + turn)
            }

            is FlowCommand.ProposeRecurringSession -> {
                val proposal = Proposal(
                    id = command.proposalId,
                    intentionId = command.intentionId,
                    title = command.title,
                    summary = command.summary,
                    proposedDayOfWeek = command.dayOfWeek,
                    proposedHour = command.hour,
                    proposedMinute = command.minute,
                    status = ProposalStatus.PENDING,
                    basedOnFactIds = command.basedOnFactIds,
                    provenance = Provenance(ProvenanceKind.INFERRED, command.nowEpochMs, "agent"),
                    proposedDaysOfWeek = command.daysOfWeek.ifEmpty { listOf(command.dayOfWeek) },
                    durationMinutes = command.durationMinutes,
                    timeZoneId = command.timeZoneId,
                    methodsSummary = command.methodsSummary,
                    capabilityHonesty = command.capabilityHonesty,
                    homeImpactNote = command.homeImpactNote,
                    includeMorningCheckIn = command.includeMorningCheckIn,
                    includeEveningReview = command.includeEveningReview
                )
                migrated.copy(
                    proposals = migrated.proposals.filterNot { it.id == proposal.id } + proposal,
                    intentions = setPhase(
                        migrated.intentions,
                        command.intentionId,
                        IntentionPhase.PROPOSAL_PENDING,
                        command.nowEpochMs
                    )
                )
            }

            is FlowCommand.ApproveProposal -> {
                val proposal = migrated.proposals.firstOrNull { it.id == command.proposalId }
                    ?: return migrated
                val approved = proposal.copy(status = ProposalStatus.APPROVED)
                val session = ScheduledSession(
                    id = command.sessionId,
                    intentionId = proposal.intentionId,
                    title = proposal.title,
                    dayOfWeek = proposal.proposedDayOfWeek,
                    hour = proposal.proposedHour,
                    minute = proposal.proposedMinute
                )
                // §8.10 — proposal / builder cards stop being interactive truth after Approve.
                val turnsDeactivated = migrated.turns.map { turn ->
                    if (turn.intentionId != proposal.intentionId) return@map turn
                    turn.copy(
                        blocks = turn.blocks.map { block ->
                            val isStaleProposal =
                                block.active && block.type == ResponseBlockType.PROPOSAL
                            val isStaleBuilder =
                                block.active && BuilderAnswerCard.isBuilderCard(block)
                            if (isStaleProposal || isStaleBuilder) {
                                block.copy(active = false)
                            } else {
                                block
                            }
                        }
                    )
                }
                // Gate E3 — receipt turn is part of the same reversible Apply so Undo
                // restores activation + receipt together (does not steal last-undoable).
                val receipt = com.intent.launcher.engine.ApproveReceipt.buildTurn(
                    proposal = approved,
                    sessionId = session.id,
                    nowEpochMs = command.nowEpochMs
                )
                val turnsAfter = turnsDeactivated.filterNot { it.id == receipt.id } + receipt
                migrated.copy(
                    proposals = migrated.proposals.map { if (it.id == proposal.id) approved else it },
                    sessions = migrated.sessions.filterNot { it.id == session.id } + session,
                    intentions = setPhase(
                        migrated.intentions,
                        proposal.intentionId,
                        IntentionPhase.ACTIVE,
                        command.nowEpochMs
                    ),
                    activeIntentionId = proposal.intentionId,
                    turns = turnsAfter
                )
            }

            is FlowCommand.DeclineProposal -> {
                val proposal = migrated.proposals.firstOrNull { it.id == command.proposalId }
                    ?: return migrated
                migrated.copy(
                    proposals = migrated.proposals.map {
                        if (it.id == proposal.id) it.copy(status = ProposalStatus.DECLINED) else it
                    },
                    intentions = setPhase(
                        migrated.intentions,
                        proposal.intentionId,
                        IntentionPhase.CLARIFYING,
                        command.nowEpochMs
                    )
                )
            }

            is FlowCommand.EditProposal -> {
                migrated.copy(
                    proposals = migrated.proposals.map {
                        if (it.id == command.proposalId) {
                            it.copy(
                                status = ProposalStatus.PENDING,
                                proposedDayOfWeek = command.dayOfWeek,
                                proposedHour = command.hour,
                                proposedMinute = command.minute
                            )
                        } else it
                    }
                )
            }

            is FlowCommand.DeferNextSession -> {
                val session = migrated.sessions.firstOrNull { it.id == command.sessionId }
                val inProgress = migrated.sessionRuns.any {
                    it.sessionId == command.sessionId && it.status == SessionRunStatus.IN_PROGRESS
                }
                if (inProgress) return migrated
                migrated.copy(
                    sessions = migrated.sessions.map {
                        if (it.id == command.sessionId) {
                            it.copy(deferredUntilEpochMs = command.deferUntilEpochMs)
                        } else it
                    },
                    intentions = if (session != null) {
                        touch(migrated.intentions, session.intentionId, command.nowEpochMs)
                    } else {
                        migrated.intentions
                    }
                )
            }

            is FlowCommand.BeginSession -> {
                val session = migrated.sessions.firstOrNull { it.id == command.sessionId }
                    ?: return migrated
                val alreadyActive = migrated.sessionRuns.any {
                    it.intentionId == session.intentionId && it.status == SessionRunStatus.IN_PROGRESS
                }
                if (alreadyActive) return migrated
                val run = SessionRun(
                    id = command.runId,
                    sessionId = session.id,
                    intentionId = session.intentionId,
                    title = session.title,
                    status = SessionRunStatus.IN_PROGRESS,
                    startedAtEpochMs = command.nowEpochMs,
                    affinityPackageName = command.affinityPackageName,
                    affinityAppLabel = command.affinityAppLabel
                )
                migrated.copy(
                    sessionRuns = migrated.sessionRuns.filterNot { it.id == run.id } + run,
                    sessions = migrated.sessions.map {
                        if (it.id == session.id) it.copy(deferredUntilEpochMs = null) else it
                    },
                    intentions = touch(migrated.intentions, session.intentionId, command.nowEpochMs)
                )
            }

            is FlowCommand.CompleteSessionRun -> {
                endSessionRun(migrated, command.runId, SessionRunStatus.COMPLETED, command.nowEpochMs)
            }

            is FlowCommand.AbandonSessionRun -> {
                endSessionRun(migrated, command.runId, SessionRunStatus.ABANDONED, command.nowEpochMs)
            }

            is FlowCommand.RecordObservedEvidence -> {
                val evidence = Evidence(
                    id = command.evidenceId,
                    intentionId = command.intentionId,
                    sessionRunId = command.sessionRunId,
                    packageName = command.packageName,
                    appLabel = command.appLabel,
                    foregroundMs = command.foregroundMs,
                    recordedAtEpochMs = command.nowEpochMs,
                    summaryText = command.summaryText,
                    provenance = Provenance(
                        kind = ProvenanceKind.OBSERVED,
                        learnedAtEpochMs = command.nowEpochMs,
                        sourceLabel = "usage_stats"
                    ),
                    accuracy = EvidenceAccuracy.ACTIVE
                )
                migrated.copy(
                    evidence = migrated.evidence.filterNot { it.id == evidence.id } + evidence,
                    intentions = touch(migrated.intentions, command.intentionId, command.nowEpochMs)
                )
            }

            is FlowCommand.RecordTrackerEntry -> {
                val eventAt = command.eventAtEpochMs ?: command.nowEpochMs
                val entry = TrackerEntry(
                    id = command.entryId,
                    intentionId = command.intentionId,
                    moduleId = command.moduleId,
                    valueText = command.valueText,
                    recordedAtEpochMs = command.nowEpochMs,
                    eventAtEpochMs = eventAt,
                    provenance = Provenance(
                        kind = command.provenanceKind,
                        learnedAtEpochMs = command.nowEpochMs,
                        sourceLabel = command.sourceLabel
                    ),
                    status = TrackerEntryStatus.ACTIVE
                )
                migrated.copy(
                    trackerEntries = migrated.trackerEntries.filterNot { it.id == entry.id } + entry,
                    intentions = touch(migrated.intentions, command.intentionId, command.nowEpochMs)
                )
            }

            is FlowCommand.CorrectTrackerEntry -> {
                val prior = migrated.trackerEntries.firstOrNull { it.id == command.priorEntryId }
                    ?: return migrated
                if (prior.status != TrackerEntryStatus.ACTIVE) return migrated
                val eventAt = command.eventTimeEpochMs ?: command.nowEpochMs
                val correctedPrior = prior.copy(status = TrackerEntryStatus.CORRECTED)
                val entry = TrackerEntry(
                    id = command.newEntryId,
                    intentionId = prior.intentionId,
                    moduleId = prior.moduleId,
                    valueText = command.valueText,
                    recordedAtEpochMs = command.nowEpochMs,
                    eventAtEpochMs = eventAt,
                    provenance = Provenance(
                        kind = command.provenanceKind,
                        learnedAtEpochMs = command.nowEpochMs,
                        sourceLabel = command.sourceLabel
                    ),
                    status = TrackerEntryStatus.ACTIVE,
                    supersedesEntryId = prior.id
                )
                val withoutDup = migrated.trackerEntries
                    .map { if (it.id == prior.id) correctedPrior else it }
                    .filterNot { it.id == entry.id }
                migrated.copy(
                    trackerEntries = withoutDup + entry,
                    intentions = touch(migrated.intentions, prior.intentionId, command.nowEpochMs)
                )
            }

            is FlowCommand.DismissEvidence -> {
                val evidence = migrated.evidence.firstOrNull { it.id == command.evidenceId }
                val triggerKey = evidence?.let {
                    TriggerCooldownPolicy.usageThresholdKey(it.intentionId)
                }
                val cooldownUntil = command.nowEpochMs + TriggerCooldownPolicy.DEFAULT_COOLDOWN_MS
                val nextCooldowns = if (triggerKey != null) {
                    upsertCooldown(
                        migrated.triggerCooldowns,
                        TriggerCooldown(
                            key = triggerKey,
                            armedUntilEpochMs = cooldownUntil,
                            reason = TriggerCooldownPolicy.REASON_DISMISS
                        )
                    )
                } else {
                    migrated.triggerCooldowns
                }
                val nextInterventions = if (triggerKey != null) {
                    migrated.queuedInterventions.map { job ->
                        if (job.triggerKey == triggerKey &&
                            job.status in TriggerCooldownPolicy.ACTIVE_STATUSES
                        ) {
                            job.copy(status = "dismissed")
                        } else {
                            job
                        }
                    }
                } else {
                    migrated.queuedInterventions
                }
                migrated.copy(
                    evidence = migrated.evidence.map {
                        if (it.id == command.evidenceId) {
                            it.copy(accuracy = EvidenceAccuracy.DISMISSED)
                        } else {
                            it
                        }
                    },
                    triggerCooldowns = nextCooldowns,
                    queuedInterventions = nextInterventions
                )
            }

            is FlowCommand.DeleteTrackerEntry -> {
                migrated.copy(
                    trackerEntries = migrated.trackerEntries.map {
                        if (it.id == command.entryId) {
                            it.copy(status = TrackerEntryStatus.DELETED)
                        } else {
                            it
                        }
                    }
                )
            }

            is FlowCommand.SoftPauseSessionRun -> {
                softPauseSessionRun(migrated, command.runId, command.nowEpochMs)
            }

            is FlowCommand.ResumeSessionRun -> {
                resumeSessionRun(migrated, command.runId, command.nowEpochMs)
            }

            is FlowCommand.SetActiveIntention -> {
                migrated.copy(activeIntentionId = command.intentionId)
            }

            is FlowCommand.SetParentIntention -> {
                if (!IntentionHierarchy.canSetParent(
                        migrated,
                        command.intentionId,
                        command.parentIntentionId
                    )
                ) {
                    return migrated
                }
                migrated.copy(
                    intentions = migrated.intentions.map {
                        if (it.id == command.intentionId) {
                            it.copy(
                                parentIntentionId = command.parentIntentionId,
                                updatedAtEpochMs = command.nowEpochMs
                            )
                        } else {
                            it
                        }
                    }
                )
            }

            is FlowCommand.MarkPhase -> {
                migrated.copy(
                    intentions = setPhase(
                        migrated.intentions,
                        command.intentionId,
                        command.phase,
                        command.nowEpochMs
                    )
                )
            }

            is FlowCommand.CaptureThought -> {
                val trimmed = command.text.trim()
                if (trimmed.isEmpty()) return migrated
                val thought = ThoughtCapture(
                    id = command.thoughtId,
                    intentionId = command.intentionId,
                    text = trimmed,
                    provenance = Provenance(
                        kind = ProvenanceKind.SAID,
                        learnedAtEpochMs = command.nowEpochMs,
                        sourceLabel = when (command.source) {
                            "voice" -> "voice_thought_capture"
                            "floater" -> "floater_thought_capture"
                            "triage" -> "triage_thought_capture"
                            else -> "home_thought_capture"
                        }
                    ),
                    capturedAtEpochMs = command.nowEpochMs,
                    source = command.source,
                    status = ThoughtCaptureStatus.RAW
                )
                val intentions = if (command.intentionId != null) {
                    touch(migrated.intentions, command.intentionId, command.nowEpochMs)
                } else {
                    migrated.intentions
                }
                migrated.copy(
                    thoughtCaptures = migrated.thoughtCaptures.filterNot { it.id == thought.id } +
                        thought,
                    intentions = intentions
                )
            }

            is FlowCommand.ConfirmDailyCheckIn -> {
                var facts = migrated.facts
                var hypotheses = migrated.hypotheses
                val note = command.adjustedFocusNote?.trim().orEmpty()
                if (note.isNotEmpty() && command.adjustedFactId != null) {
                    val fact = Fact(
                        id = command.adjustedFactId,
                        intentionId = command.intentionId,
                        text = note,
                        provenance = Provenance(ProvenanceKind.SAID, command.nowEpochMs, "user")
                    )
                    facts = facts.filterNot { it.id == fact.id } + fact
                }
                if (note.isNotEmpty() && command.adjustedHypothesisId != null) {
                    val hypothesis = Hypothesis(
                        id = command.adjustedHypothesisId,
                        intentionId = command.intentionId,
                        text = note,
                        provenance = Provenance(ProvenanceKind.SAID, command.nowEpochMs, "user")
                    )
                    hypotheses = hypotheses.filterNot { it.id == hypothesis.id } + hypothesis
                }
                val said = Provenance(ProvenanceKind.SAID, command.nowEpochMs, "user")
                var occurrences = migrated.occurrences
                val occurrenceId = command.occurrenceId
                if (occurrenceId != null) {
                    val occurrence = Occurrence(
                        id = occurrenceId,
                        intentionId = command.intentionId,
                        localDate = command.localDate,
                        phase = "morning",
                        kind = "check_in",
                        summary = command.focusSummary,
                        recordedAtEpochMs = command.nowEpochMs,
                        provenance = said
                    )
                    occurrences = occurrences.filterNot { it.id == occurrence.id } + occurrence
                }
                var dailyContexts = migrated.dailyContexts
                val dailyContextId = command.dailyContextId
                if (dailyContextId != null) {
                    val ctx = DailyContext(
                        id = dailyContextId,
                        localDate = command.localDate,
                        intentionId = command.intentionId,
                        phase = "morning",
                        availability = command.availability,
                        energy = command.energy,
                        priorities = command.priorities ?: command.focusSummary,
                        constraints = command.constraints,
                        note = note.ifBlank { null },
                        recordedAtEpochMs = command.nowEpochMs,
                        provenance = said
                    )
                    dailyContexts = dailyContexts.filterNot { it.id == ctx.id } + ctx
                }
                migrated.copy(
                    facts = facts,
                    hypotheses = hypotheses,
                    occurrences = occurrences,
                    dailyContexts = dailyContexts,
                    dayCycle = DayCycleState(
                        localDate = command.localDate,
                        checkInAtEpochMs = command.nowEpochMs,
                        checkOutAtEpochMs = null,
                        focusIntentionId = command.intentionId,
                        focusSummary = command.focusSummary,
                        middayAtEpochMs = null,
                        middayNote = null
                    ),
                    intentions = touch(migrated.intentions, command.intentionId, command.nowEpochMs)
                )
            }

            is FlowCommand.RecordMiddayCheck -> {
                val said = Provenance(ProvenanceKind.SAID, command.nowEpochMs, "user")
                val occurrence = Occurrence(
                    id = command.occurrenceId,
                    intentionId = command.intentionId,
                    localDate = command.localDate,
                    phase = "midday",
                    kind = "midday_check",
                    summary = command.summary,
                    recordedAtEpochMs = command.nowEpochMs,
                    provenance = said
                )
                val ctx = DailyContext(
                    id = command.contextId,
                    localDate = command.localDate,
                    intentionId = command.intentionId,
                    phase = "midday",
                    energy = command.energy,
                    constraints = command.constraints,
                    note = command.summary,
                    recordedAtEpochMs = command.nowEpochMs,
                    provenance = said
                )
                val prior = migrated.dayCycle
                val dayCycle = if (prior.localDate == command.localDate) {
                    prior.copy(
                        middayAtEpochMs = command.nowEpochMs,
                        middayNote = command.summary,
                        focusIntentionId = command.intentionId
                    )
                } else {
                    DayCycleState(
                        localDate = command.localDate,
                        focusIntentionId = command.intentionId,
                        middayAtEpochMs = command.nowEpochMs,
                        middayNote = command.summary
                    )
                }
                var sessions = migrated.sessions
                val deferSessionId = command.deferSessionId
                val deferUntil = command.deferUntilEpochMs
                if (deferSessionId != null && deferUntil != null) {
                    val inProgress = migrated.sessionRuns.any {
                        it.sessionId == deferSessionId && it.status == SessionRunStatus.IN_PROGRESS
                    }
                    if (!inProgress) {
                        sessions = sessions.map {
                            if (it.id == deferSessionId) {
                                it.copy(deferredUntilEpochMs = deferUntil)
                            } else {
                                it
                            }
                        }
                    }
                }
                migrated.copy(
                    occurrences = migrated.occurrences.filterNot { it.id == occurrence.id } + occurrence,
                    dailyContexts = migrated.dailyContexts.filterNot { it.id == ctx.id } + ctx,
                    dayCycle = dayCycle,
                    sessions = sessions,
                    intentions = touch(migrated.intentions, command.intentionId, command.nowEpochMs)
                )
            }

            is FlowCommand.CompleteDailyCheckOut -> {
                val fact = Fact(
                    id = command.factId,
                    intentionId = command.intentionId,
                    text = command.whatHappened,
                    provenance = Provenance(ProvenanceKind.SAID, command.nowEpochMs, "user")
                )
                var facts = migrated.facts.filterNot { it.id == fact.id } + fact
                var hypotheses = migrated.hypotheses
                val hypothesisText = command.hypothesisText?.trim().orEmpty()
                if (hypothesisText.isNotEmpty() && command.hypothesisId != null) {
                    val hypothesis = Hypothesis(
                        id = command.hypothesisId,
                        intentionId = command.intentionId,
                        text = hypothesisText,
                        provenance = Provenance(ProvenanceKind.SAID, command.nowEpochMs, "user")
                    )
                    hypotheses = hypotheses.filterNot { it.id == hypothesis.id } + hypothesis
                }
                val prior = migrated.dayCycle
                val dayCycle = DayCycleState(
                    localDate = command.localDate,
                    checkInAtEpochMs = if (prior.localDate == command.localDate) {
                        prior.checkInAtEpochMs
                    } else {
                        null
                    },
                    checkOutAtEpochMs = command.nowEpochMs,
                    focusIntentionId = command.intentionId,
                    focusSummary = prior.focusSummary,
                    middayAtEpochMs = if (prior.localDate == command.localDate) {
                        prior.middayAtEpochMs
                    } else {
                        null
                    },
                    middayNote = if (prior.localDate == command.localDate) {
                        prior.middayNote
                    } else {
                        null
                    }
                )
                var sessions = migrated.sessions
                val deferSessionId = command.deferSessionId
                val deferUntil = command.deferUntilEpochMs
                if (deferSessionId != null && deferUntil != null) {
                    val inProgress = migrated.sessionRuns.any {
                        it.sessionId == deferSessionId && it.status == SessionRunStatus.IN_PROGRESS
                    }
                    if (!inProgress) {
                        sessions = sessions.map {
                            if (it.id == deferSessionId) {
                                it.copy(deferredUntilEpochMs = deferUntil)
                            } else {
                                it
                            }
                        }
                    }
                }
                val said = Provenance(ProvenanceKind.SAID, command.nowEpochMs, "user")
                var occurrences = migrated.occurrences
                val occurrenceId = command.occurrenceId
                if (occurrenceId != null) {
                    val occurrence = Occurrence(
                        id = occurrenceId,
                        intentionId = command.intentionId,
                        localDate = command.localDate,
                        phase = "evening",
                        kind = "check_out",
                        summary = command.whatHappened,
                        recordedAtEpochMs = command.nowEpochMs,
                        provenance = said
                    )
                    occurrences = occurrences.filterNot { it.id == occurrence.id } + occurrence
                }
                var dailyContexts = migrated.dailyContexts
                val dailyContextId = command.dailyContextId
                if (dailyContextId != null) {
                    val ctx = DailyContext(
                        id = dailyContextId,
                        localDate = command.localDate,
                        intentionId = command.intentionId,
                        phase = "evening",
                        energy = command.energy,
                        constraints = command.constraints,
                        note = command.whatHappened,
                        recordedAtEpochMs = command.nowEpochMs,
                        provenance = said
                    )
                    dailyContexts = dailyContexts.filterNot { it.id == ctx.id } + ctx
                }
                migrated.copy(
                    facts = facts,
                    hypotheses = hypotheses,
                    sessions = sessions,
                    occurrences = occurrences,
                    dailyContexts = dailyContexts,
                    dayCycle = dayCycle,
                    intentions = touch(migrated.intentions, command.intentionId, command.nowEpochMs)
                )
            }

            is FlowCommand.MarkThoughtStatus -> {
                migrated.copy(
                    thoughtCaptures = migrated.thoughtCaptures.map {
                        if (it.id == command.thoughtId) it.copy(status = command.status) else it
                    }
                )
            }

            is FlowCommand.ClassifyThought -> classifyThought(migrated, command)

            is FlowCommand.StartBuilderDraft -> {
                val text = command.freeText.trim()
                if (text.isEmpty()) return migrated
                val auto = command.autoApplyShape
                migrated.copy(
                    builderDraft = BuilderDraft(
                        active = true,
                        freeText = text,
                        suggestedShapeTag = command.suggestedShapeTag,
                        shapeTag = if (auto) command.suggestedShapeTag else null,
                        title = command.title,
                        statement = command.statement,
                        factAnswers = emptyMap(),
                        phase = if (auto) BuilderPhase.MISSING_FACTS else BuilderPhase.PICK_SHAPE,
                        honestyNote = command.honestyNote
                    )
                )
            }

            is FlowCommand.SetBuilderShape -> {
                val draft = migrated.builderDraft
                if (!draft.active) return migrated
                migrated.copy(
                    builderDraft = draft.copy(
                        shapeTag = command.shapeTag,
                        title = command.title,
                        statement = command.statement,
                        phase = BuilderPhase.MISSING_FACTS
                    )
                )
            }

            is FlowCommand.AnswerBuilderPrompt -> {
                val draft = migrated.builderDraft
                if (!draft.active) return migrated
                val answer = command.answerText.trim()
                if (answer.isEmpty()) return migrated
                val answers = draft.factAnswers + (command.promptId to answer)
                // Advance when every required missing-fact prompt id is filled.
                // Extra seed-honesty answers (e.g. usage_measurement) must not
                // count toward the threshold or skip permission / intervention.
                val shape = draft.shapeTag ?: draft.suggestedShapeTag
                val requiredIds = IntentionBuilder.missingFactPrompts(shape).map { it.id }
                val phase = if (requiredIds.isEmpty() || requiredIds.all { it in answers }) {
                    BuilderPhase.CONFIRM
                } else {
                    BuilderPhase.MISSING_FACTS
                }
                migrated.copy(
                    builderDraft = draft.copy(
                        factAnswers = answers,
                        phase = phase
                    )
                )
            }

            is FlowCommand.CommitBuilderIntention -> {
                val draft = migrated.builderDraft
                if (!draft.active) return migrated
                val shape = draft.shapeTag ?: draft.suggestedShapeTag
                val title = draft.title.ifBlank { draft.freeText.take(48) }
                val statement = draft.statement.ifBlank { draft.freeText }
                val intention = Intention(
                    id = command.intentionId,
                    title = title,
                    statement = statement,
                    phase = IntentionPhase.EXPLORING,
                    createdAtEpochMs = command.nowEpochMs,
                    updatedAtEpochMs = command.nowEpochMs,
                    shapeTag = shape
                )
                var facts = migrated.facts
                draft.factAnswers.entries.forEachIndexed { index, (promptId, text) ->
                    val fact = Fact(
                        id = "fact-builder-${command.intentionId}-$promptId",
                        intentionId = command.intentionId,
                        text = text,
                        provenance = Provenance(
                            ProvenanceKind.SAID,
                            command.nowEpochMs + index,
                            "user"
                        ),
                        // Prompt id as key so Intention Space / adaptive hero
                        // can stop re-asking answered discovery questions.
                        key = promptId,
                        rawUtterance = text,
                        normalizedValue = text
                    )
                    facts = facts.filterNot { it.id == fact.id } + fact
                }
                // Seed statement itself as SAID when user provided free text.
                if (draft.freeText.isNotBlank()) {
                    val seedFact = Fact(
                        id = "fact-builder-${command.intentionId}-seed",
                        intentionId = command.intentionId,
                        text = draft.freeText.trim(),
                        provenance = Provenance(ProvenanceKind.SAID, command.nowEpochMs, "user")
                    )
                    facts = facts.filterNot { it.id == seedFact.id } + seedFact
                }
                migrated.copy(
                    intentions = migrated.intentions.filterNot { it.id == intention.id } + intention,
                    facts = facts,
                    activeIntentionId = command.intentionId,
                    builderDraft = BuilderDraft()
                )
            }

            is FlowCommand.CancelBuilderDraft -> {
                migrated.copy(builderDraft = BuilderDraft())
            }

            is FlowCommand.StartTriageBatch -> {
                if (command.items.isEmpty()) return migrated
                migrated.copy(
                    triage = TriageState(
                        active = true,
                        batchId = command.batchId,
                        items = command.items,
                        createdAtEpochMs = command.nowEpochMs
                    )
                )
            }

            is FlowCommand.ConfirmTriageItem -> {
                val triage = migrated.triage
                val item = triage.items.firstOrNull { it.id == command.itemId }
                    ?: return migrated
                if (item.status != TriageItemStatus.PENDING) return migrated
                val intention = Intention(
                    id = command.intentionId,
                    title = item.title,
                    statement = item.statement,
                    phase = IntentionPhase.EXPLORING,
                    createdAtEpochMs = command.nowEpochMs,
                    updatedAtEpochMs = command.nowEpochMs,
                    shapeTag = item.suggestedShapeTag
                )
                val seedFact = Fact(
                    id = "fact-triage-${command.intentionId}-seed",
                    intentionId = command.intentionId,
                    text = item.statement,
                    provenance = Provenance(ProvenanceKind.SAID, command.nowEpochMs, "user")
                )
                val thoughts = migrated.thoughtCaptures.map {
                    if (it.id in item.sourceThoughtIds) {
                        it.copy(status = ThoughtCaptureStatus.PROMOTED)
                    } else {
                        it
                    }
                }
                val items = triage.items.map {
                    if (it.id == item.id) it.copy(status = TriageItemStatus.CONFIRMED) else it
                }
                migrated.copy(
                    intentions = migrated.intentions.filterNot { it.id == intention.id } + intention,
                    facts = migrated.facts.filterNot { it.id == seedFact.id } + seedFact,
                    thoughtCaptures = thoughts,
                    activeIntentionId = command.intentionId,
                    triage = triage.copy(items = items, active = items.any { it.status == TriageItemStatus.PENDING })
                )
            }

            is FlowCommand.RejectTriageItem -> {
                val triage = migrated.triage
                val item = triage.items.firstOrNull { it.id == command.itemId }
                    ?: return migrated
                // Rejected proposals stay as ThoughtCapture evidence (RAW) — not discarded.
                val items = triage.items.map {
                    if (it.id == item.id) it.copy(status = TriageItemStatus.REJECTED) else it
                }
                migrated.copy(
                    triage = triage.copy(
                        items = items,
                        active = items.any { it.status == TriageItemStatus.PENDING }
                    )
                )
            }

            is FlowCommand.ClearTriageBatch -> {
                migrated.copy(triage = TriageState())
            }

            is FlowCommand.CaptureErrand -> {
                val todo = ErrandTodo(
                    id = command.todoId,
                    title = command.title,
                    firstAction = command.firstAction,
                    reminderNote = command.reminderNote,
                    status = ErrandStatus.OPEN,
                    createdAtEpochMs = command.nowEpochMs,
                    completedAtEpochMs = null,
                    provenance = Provenance(
                        kind = ProvenanceKind.SAID,
                        learnedAtEpochMs = command.nowEpochMs,
                        sourceLabel = "user"
                    ),
                    checklistItems = command.checklistItems,
                    reminderEnabled = command.reminderEnabled
                )
                migrated.copy(
                    errandTodos = migrated.errandTodos.filterNot { it.id == todo.id } + todo
                )
            }

            is FlowCommand.CompleteErrand -> {
                migrated.copy(
                    errandTodos = migrated.errandTodos.map {
                        if (it.id == command.todoId && it.status == ErrandStatus.OPEN) {
                            it.copy(
                                status = ErrandStatus.DONE,
                                completedAtEpochMs = command.nowEpochMs
                            )
                        } else it
                    }
                )
            }

            is FlowCommand.ReopenErrand -> {
                migrated.copy(
                    errandTodos = migrated.errandTodos.map {
                        if (it.id == command.todoId && it.status == ErrandStatus.DONE) {
                            it.copy(
                                status = ErrandStatus.OPEN,
                                completedAtEpochMs = null
                            )
                        } else it
                    }
                )
            }

            is FlowCommand.ToggleErrandChecklistItem -> {
                migrated.copy(
                    errandTodos = migrated.errandTodos.map { todo ->
                        if (todo.id != command.todoId) {
                            todo
                        } else {
                            todo.copy(
                                checklistItems = todo.checklistItems.map { item ->
                                    if (item.id == command.itemId) {
                                        item.copy(done = !item.done)
                                    } else {
                                        item
                                    }
                                }
                            )
                        }
                    }
                )
            }

            is FlowCommand.UpdateErrand -> {
                migrated.copy(
                    errandTodos = migrated.errandTodos.map { todo ->
                        if (todo.id != command.todoId) {
                            todo
                        } else {
                            todo.copy(
                                title = command.title,
                                firstAction = command.firstAction,
                                reminderNote = command.reminderNote,
                                checklistItems = command.checklistItems,
                                reminderEnabled = command.reminderEnabled
                            )
                        }
                    }
                )
            }

            is FlowCommand.SoftDeleteIntention -> softDeleteIntention(migrated, command)
            is FlowCommand.SoftDeleteSession -> softDeleteSession(migrated, command)
            is FlowCommand.RestoreTrashItem -> restoreTrash(migrated, command)
            is FlowCommand.PurgeTrashItem -> {
                if (migrated.trash.none { it.id == command.trashId }) return migrated
                migrated.copy(trash = migrated.trash.filterNot { it.id == command.trashId })
            }


            is FlowCommand.ReorderIntentions -> {
                val known = migrated.intentions.map { it.id }.toSet()
                val ordered = command.orderedIntentionIds.filter { it in known }.distinct()
                val remainder = migrated.intentions.map { it.id }.filterNot { it in ordered }
                migrated.copy(intentionOrder = ordered + remainder)
            }

            is FlowCommand.HoldAppForIntention -> {
                if (migrated.intentions.none { it.id == command.intentionId }) return migrated
                val existingForIntention = migrated.heldApps.filter {
                    it.intentionId == command.intentionId
                }
                val already = existingForIntention.firstOrNull {
                    it.packageName == command.packageName
                }
                if (already != null) {
                    return migrated.copy(
                        heldApps = migrated.heldApps.map {
                            if (it.id == already.id) {
                                it.copy(
                                    label = command.label,
                                    provenance = Provenance(
                                        ProvenanceKind.SAID,
                                        command.nowEpochMs,
                                        "user-hold"
                                    )
                                )
                            } else it
                        },
                        intentions = touch(migrated.intentions, command.intentionId, command.nowEpochMs)
                    )
                }
                val held = HeldApp(
                    id = command.heldAppId,
                    intentionId = command.intentionId,
                    packageName = command.packageName,
                    label = command.label,
                    sortOrder = existingForIntention.size,
                    provenance = Provenance(
                        ProvenanceKind.SAID,
                        command.nowEpochMs,
                        "user-hold"
                    )
                )
                migrated.copy(
                    heldApps = migrated.heldApps.filterNot { it.id == held.id } + held,
                    intentions = touch(migrated.intentions, command.intentionId, command.nowEpochMs)
                )
            }

            is FlowCommand.ReorderHeldApps -> {
                val forIntention = migrated.heldApps.filter { it.intentionId == command.intentionId }
                if (forIntention.isEmpty()) return migrated
                val known = forIntention.map { it.id }.toSet()
                val orderedIds = command.orderedHeldAppIds.filter { it in known }.distinct()
                val remainder = forIntention.map { it.id }.filterNot { it in orderedIds }
                val newOrder = orderedIds + remainder
                migrated.copy(
                    heldApps = migrated.heldApps.map { held ->
                        if (held.intentionId != command.intentionId) {
                            held
                        } else {
                            val index = newOrder.indexOf(held.id)
                            held.copy(sortOrder = if (index >= 0) index else held.sortOrder)
                        }
                    },
                    intentions = touch(migrated.intentions, command.intentionId, command.nowEpochMs)
                )
            }

            is FlowCommand.ReleaseHeldApp -> {
                val target = migrated.heldApps.firstOrNull { it.id == command.heldAppId }
                    ?: return migrated
                val remaining = migrated.heldApps.filterNot { it.id == command.heldAppId }
                val peers = remaining
                    .filter { it.intentionId == target.intentionId }
                    .sortedBy { it.sortOrder }
                val peerOrder = peers.mapIndexed { index, held -> held.id to index }.toMap()
                val reindexed = remaining.map { held ->
                    val newOrder = peerOrder[held.id]
                    if (newOrder != null) held.copy(sortOrder = newOrder) else held
                }
                migrated.copy(
                    heldApps = reindexed,
                    intentions = touch(migrated.intentions, target.intentionId, command.nowEpochMs)
                )
            }

            is FlowCommand.UpdateDrawerCardLayout -> {
                if (migrated.intentions.none { it.id == command.intentionId }) return migrated
                val span = DrawerCards.normalizeSpan(command.spanColumns)
                val imageKey = command.imageKey.ifBlank {
                    val intention = migrated.intentions.first { it.id == command.intentionId }
                    DrawerCards.coverKeyForShape(intention.shapeTag)
                }
                val layout = DrawerCardLayout(
                    intentionId = command.intentionId,
                    spanColumns = span,
                    priorityBorder = command.priorityBorder,
                    imageKey = imageKey
                )
                migrated.copy(
                    drawerCardLayouts = migrated.drawerCardLayouts
                        .filterNot { it.intentionId == command.intentionId } + layout,
                    intentions = touch(migrated.intentions, command.intentionId, command.nowEpochMs)
                )
            }

            is FlowCommand.SetProactivityPrefs -> {
                val budget = command.interruptionBudgetPerDay.coerceAtLeast(0)
                migrated.copy(
                    proactivityPrefs = migrated.proactivityPrefs.copy(
                        quietHoursStartMin = command.quietHoursStartMin,
                        quietHoursEndMin = command.quietHoursEndMin,
                        interruptionBudgetPerDay = budget
                    )
                )
            }

            is FlowCommand.ArmTriggerCooldown -> {
                if (command.key.isBlank()) return migrated
                migrated.copy(
                    triggerCooldowns = upsertCooldown(
                        migrated.triggerCooldowns,
                        TriggerCooldown(
                            key = command.key,
                            armedUntilEpochMs = command.armedUntilEpochMs,
                            reason = command.reason
                        )
                    )
                )
            }

            is FlowCommand.LeaveMeAlone -> {
                val prefs = migrated.proactivityPrefs.copy(
                    leaveMeAloneUntilEpochMs = command.untilEpochMs
                )
                val cooldowns = if (!command.triggerKey.isNullOrBlank()) {
                    upsertCooldown(
                        migrated.triggerCooldowns,
                        TriggerCooldown(
                            key = command.triggerKey,
                            armedUntilEpochMs = command.untilEpochMs,
                            reason = TriggerCooldownPolicy.REASON_LEAVE_ME_ALONE
                        )
                    )
                } else {
                    migrated.triggerCooldowns
                }
                val interventions = migrated.queuedInterventions.map { job ->
                    if (job.status in TriggerCooldownPolicy.ACTIVE_STATUSES) {
                        job.copy(status = "dismissed")
                    } else {
                        job
                    }
                }
                migrated.copy(
                    proactivityPrefs = prefs,
                    triggerCooldowns = cooldowns,
                    queuedInterventions = interventions
                )
            }

            is FlowCommand.QueueIntervention -> {
                val decision = TriggerCooldownPolicy.evaluate(
                    snapshot = migrated,
                    triggerKey = command.triggerKey,
                    nowEpochMs = command.nowEpochMs
                )
                if (decision !is TriggerCooldownPolicy.Decision.Allow) return migrated
                val intervention = QueuedIntervention(
                    jobId = command.jobId,
                    turnId = command.turnId,
                    intentionId = command.intentionId,
                    triggerKey = command.triggerKey,
                    kind = command.kind,
                    promptText = command.promptText,
                    evidenceId = command.evidenceId,
                    provenanceKind = command.provenanceKind,
                    createdAtEpochMs = command.nowEpochMs,
                    status = "queued"
                )
                // Replace any prior active jobs — at most one active intervention.
                val cleared = migrated.queuedInterventions.map { job ->
                    if (job.status in TriggerCooldownPolicy.ACTIVE_STATUSES) {
                        job.copy(status = "dismissed")
                    } else {
                        job
                    }
                }.filterNot { it.jobId == intervention.jobId }
                val agentText = command.agentTurnText
                val turns = if (!agentText.isNullOrBlank()) {
                    val blocks = command.agentTurnBlocks.filter {
                        runCatching { ResponseBlockType.valueOf(it.type.name) }.isSuccess
                    }
                    if (migrated.turns.any { it.id == command.turnId }) {
                        migrated.turns
                    } else {
                        migrated.turns + ConversationTurn(
                            id = command.turnId,
                            intentionId = command.intentionId,
                            role = "agent",
                            text = agentText,
                            atEpochMs = command.nowEpochMs,
                            modality = "FLOATER",
                            blocks = blocks
                        )
                    }
                } else {
                    migrated.turns
                }
                migrated.copy(
                    queuedInterventions = cleared + intervention,
                    turns = turns,
                    activeIntentionId = command.intentionId
                )
            }

            is FlowCommand.MarkInterventionShown -> {
                migrated.copy(
                    queuedInterventions = migrated.queuedInterventions.map { job ->
                        if (job.jobId == command.jobId && job.status == "queued") {
                            job.copy(status = "shown")
                        } else {
                            job
                        }
                    }
                )
            }

            is FlowCommand.DismissIntervention -> {
                val target = migrated.queuedInterventions.firstOrNull { it.jobId == command.jobId }
                    ?: return migrated
                migrated.copy(
                    queuedInterventions = migrated.queuedInterventions.map { job ->
                        if (job.jobId == command.jobId) job.copy(status = "dismissed") else job
                    },
                    triggerCooldowns = upsertCooldown(
                        migrated.triggerCooldowns,
                        TriggerCooldown(
                            key = target.triggerKey,
                            armedUntilEpochMs = command.cooldownUntilEpochMs,
                            reason = TriggerCooldownPolicy.REASON_DISMISS
                        )
                    )
                )
            }

            is FlowCommand.RecordExternalAction -> migrated

            is FlowCommand.UndoLastReversible -> migrated

            is FlowCommand.SaveTypedFact -> {
                val fact = Fact(
                    id = command.factId,
                    intentionId = command.intentionId,
                    text = command.text,
                    provenance = Provenance(ProvenanceKind.SAID, command.nowEpochMs, "user"),
                    key = command.key,
                    rawUtterance = command.rawUtterance,
                    normalizedValue = command.normalizedValue,
                    sourceTurnId = command.sourceTurnId,
                    timeZoneId = command.timeZoneId,
                    validFromEpochMs = command.validFromEpochMs,
                    validUntilEpochMs = command.validUntilEpochMs,
                    estimatedEpochMs = command.estimatedEpochMs,
                    estimatedRangeStartEpochMs = command.estimatedRangeStartEpochMs,
                    estimatedRangeEndEpochMs = command.estimatedRangeEndEpochMs,
                    approximate = command.approximate,
                    confirmation = command.confirmation,
                    status = FactStatus.ACTIVE,
                    supersedesFactId = command.supersedesFactId,
                    revision = command.revision
                )
                migrated.copy(
                    facts = migrated.facts.filterNot { it.id == fact.id } + fact,
                    intentions = touch(migrated.intentions, command.intentionId, command.nowEpochMs)
                )
            }

            is FlowCommand.CorrectTypedFact -> {
                val prior = migrated.facts.firstOrNull { it.id == command.priorFactId }
                    ?: return migrated
                val corrected = prior.copy(status = FactStatus.CORRECTED)
                val replacement = Fact(
                    id = command.newFactId,
                    intentionId = command.intentionId,
                    text = command.text,
                    provenance = Provenance(ProvenanceKind.SAID, command.nowEpochMs, "user"),
                    key = command.key ?: prior.key,
                    rawUtterance = command.rawUtterance,
                    normalizedValue = command.normalizedValue,
                    sourceTurnId = command.sourceTurnId,
                    timeZoneId = command.timeZoneId ?: prior.timeZoneId,
                    validFromEpochMs = command.validFromEpochMs ?: command.nowEpochMs,
                    validUntilEpochMs = command.validUntilEpochMs,
                    estimatedEpochMs = command.estimatedEpochMs,
                    estimatedRangeStartEpochMs = command.estimatedRangeStartEpochMs,
                    estimatedRangeEndEpochMs = command.estimatedRangeEndEpochMs,
                    approximate = command.approximate,
                    confirmation = command.confirmation,
                    status = FactStatus.ACTIVE,
                    supersedesFactId = prior.id,
                    revision = prior.revision + 1
                )
                migrated.copy(
                    facts = migrated.facts
                        .filterNot { it.id == prior.id || it.id == replacement.id } +
                        corrected + replacement,
                    intentions = touch(migrated.intentions, command.intentionId, command.nowEpochMs)
                )
            }

            is FlowCommand.UpsertOpenQuestion -> {
                migrated.copy(
                    openQuestions = migrated.openQuestions
                        .filterNot { it.id == command.question.id } + command.question,
                    intentions = touch(
                        migrated.intentions,
                        command.question.intentionId,
                        command.nowEpochMs
                    )
                )
            }

            is FlowCommand.AnswerOpenQuestion -> {
                val question = migrated.openQuestions.firstOrNull { it.id == command.questionId }
                    ?: return migrated
                if (question.state == OpenQuestionState.ANSWERED) return migrated
                val answered = question.copy(
                    state = OpenQuestionState.ANSWERED,
                    answeredAtEpochMs = command.nowEpochMs,
                    sourceTurnId = command.sourceTurnId
                )
                val turn = ConversationTurn(
                    id = command.sourceTurnId,
                    intentionId = command.intentionId,
                    role = "user",
                    text = command.answerText,
                    atEpochMs = command.nowEpochMs,
                    modality = command.modality,
                    blocks = listOf(
                        ResponseBlock(
                            blockId = "block-answered-${command.questionId}",
                            type = ResponseBlockType.RECORDED_ENTRY,
                            title = "Answered",
                            body = command.answerText,
                            sourceLabel = command.modality,
                            linkedObjectIds = listOf(
                                command.questionId,
                                command.idempotencyKey
                            ) + listOfNotNull(command.factId)
                        )
                    )
                )
                val turns = if (migrated.turns.any { it.id == turn.id }) {
                    migrated.turns
                } else {
                    migrated.turns + turn
                }
                migrated.copy(
                    openQuestions = migrated.openQuestions
                        .filterNot { it.id == answered.id } + answered,
                    turns = turns,
                    intentions = touch(migrated.intentions, command.intentionId, command.nowEpochMs)
                )
            }

            is FlowCommand.UpsertApproach -> {
                migrated.copy(
                    approaches = migrated.approaches
                        .filterNot { it.id == command.approach.id } + command.approach,
                    intentions = touch(
                        migrated.intentions,
                        command.approach.intentionId,
                        command.nowEpochMs
                    )
                )
            }

            is FlowCommand.SelectApproaches -> {
                val updated = migrated.approaches.map { approach ->
                    if (approach.intentionId != command.intentionId) {
                        approach
                    } else if (approach.id in command.approachIds) {
                        approach.copy(
                            status = ApproachStatus.SELECTED,
                            selectedAtEpochMs = command.nowEpochMs,
                            sourceTurnId = command.sourceTurnId ?: approach.sourceTurnId
                        )
                    } else if (approach.status == ApproachStatus.SELECTED) {
                        approach.copy(status = ApproachStatus.CANDIDATE, selectedAtEpochMs = null)
                    } else {
                        approach
                    }
                }
                migrated.copy(
                    approaches = updated,
                    intentions = touch(migrated.intentions, command.intentionId, command.nowEpochMs)
                )
            }

            is FlowCommand.SetAdaptiveBuilderState -> {
                migrated.copy(
                    adaptiveBuilderStates = migrated.adaptiveBuilderStates
                        .filterNot { it.intentionId == command.state.intentionId } + command.state
                )
            }

            is FlowCommand.RemapIntentionId -> {
                if (command.fromIntentionId == command.toIntentionId) return migrated
                if (migrated.intentions.none { it.id == command.fromIntentionId }) return migrated
                if (migrated.intentions.any { it.id == command.toIntentionId }) return migrated
                fun remapId(id: String) =
                    if (id == command.fromIntentionId) command.toIntentionId else id
                migrated.copy(
                    intentions = migrated.intentions.map {
                        if (it.id == command.fromIntentionId) {
                            it.copy(id = command.toIntentionId, updatedAtEpochMs = command.nowEpochMs)
                        } else {
                            it.copy(parentIntentionId = it.parentIntentionId?.let(::remapId))
                        }
                    },
                    facts = migrated.facts.map {
                        if (it.intentionId == command.fromIntentionId) {
                            it.copy(intentionId = command.toIntentionId)
                        } else it
                    },
                    hypotheses = migrated.hypotheses.map {
                        if (it.intentionId == command.fromIntentionId) {
                            it.copy(intentionId = command.toIntentionId)
                        } else it
                    },
                    proposals = migrated.proposals.map {
                        if (it.intentionId == command.fromIntentionId) {
                            it.copy(intentionId = command.toIntentionId)
                        } else it
                    },
                    sessions = migrated.sessions.map {
                        if (it.intentionId == command.fromIntentionId) {
                            it.copy(intentionId = command.toIntentionId)
                        } else it
                    },
                    sessionRuns = migrated.sessionRuns.map {
                        if (it.intentionId == command.fromIntentionId) {
                            it.copy(intentionId = command.toIntentionId)
                        } else it
                    },
                    turns = migrated.turns.map {
                        if (it.intentionId == command.fromIntentionId) {
                            it.copy(intentionId = command.toIntentionId)
                        } else it
                    },
                    thoughtCaptures = migrated.thoughtCaptures.map {
                        if (it.intentionId == command.fromIntentionId) {
                            it.copy(intentionId = command.toIntentionId)
                        } else it
                    },
                    openQuestions = migrated.openQuestions.map {
                        if (it.intentionId == command.fromIntentionId) {
                            it.copy(intentionId = command.toIntentionId)
                        } else it
                    },
                    approaches = migrated.approaches.map {
                        if (it.intentionId == command.fromIntentionId) {
                            it.copy(intentionId = command.toIntentionId)
                        } else it
                    },
                    adaptiveBuilderStates = migrated.adaptiveBuilderStates.map {
                        if (it.intentionId == command.fromIntentionId) {
                            it.copy(intentionId = command.toIntentionId)
                        } else it
                    },
                    drawerCardLayouts = migrated.drawerCardLayouts.map {
                        if (it.intentionId == command.fromIntentionId) {
                            it.copy(intentionId = command.toIntentionId)
                        } else it
                    },
                    evidence = migrated.evidence.map {
                        if (it.intentionId == command.fromIntentionId) {
                            it.copy(intentionId = command.toIntentionId)
                        } else it
                    },
                    trackerEntries = migrated.trackerEntries.map {
                        if (it.intentionId == command.fromIntentionId) {
                            it.copy(intentionId = command.toIntentionId)
                        } else it
                    },
                    decisionTrail = migrated.decisionTrail.map { event ->
                        if (event.intentionId == command.fromIntentionId) {
                            event.copy(intentionId = command.toIntentionId)
                        } else event
                    },
                    activeIntentionId = if (migrated.activeIntentionId == command.fromIntentionId) {
                        command.toIntentionId
                    } else {
                        migrated.activeIntentionId
                    }
                )
            }

            is FlowCommand.DeactivateBuilderAnswerCards -> {
                migrated.copy(
                    turns = migrated.turns.map { turn ->
                        if (turn.intentionId != command.intentionId) return@map turn
                        turn.copy(
                            blocks = turn.blocks.map { block ->
                                if (command.exceptBlockId != null &&
                                    block.blockId == command.exceptBlockId
                                ) {
                                    block
                                } else if (BuilderAnswerCard.isBuilderCard(block) && block.active) {
                                    block.copy(active = false)
                                } else {
                                    block
                                }
                            }
                        )
                    }
                )
            }

            is FlowCommand.AppendDecisionEvent -> {
                val capped = (migrated.decisionTrail + command.event)
                    .takeLast(BuilderDecisionEvent.MAX_TRAIL_EVENTS)
                migrated.copy(decisionTrail = capped)
            }
        }
    }

    private fun upsertCooldown(
        existing: List<TriggerCooldown>,
        cooldown: TriggerCooldown
    ): List<TriggerCooldown> =
        existing.filterNot { it.key == cooldown.key } + cooldown

    private fun softDeleteIntention(
        migrated: IntentionSnapshot,
        command: FlowCommand.SoftDeleteIntention
    ): IntentionSnapshot {
        val intention = migrated.intentions.firstOrNull { it.id == command.intentionId }
            ?: return migrated
        val payload = IntentionTrashPayload(
            intention = WireIntention.from(intention),
            facts = migrated.facts.filter { it.intentionId == intention.id }.map(WireFact::from),
            hypotheses = migrated.hypotheses.filter { it.intentionId == intention.id }
                .map(WireHypothesis::from),
            proposals = migrated.proposals.filter { it.intentionId == intention.id }
                .map(WireProposal::from),
            sessions = migrated.sessions.filter { it.intentionId == intention.id }
                .map(WireSession::from),
            sessionRuns = migrated.sessionRuns.filter { it.intentionId == intention.id }
                .map(WireSessionRun::from),
            turns = migrated.turns.filter { it.intentionId == intention.id }.map(WireTurn::from),
            thoughtCaptures = migrated.thoughtCaptures.filter { it.intentionId == intention.id }
                .map(WireThoughtCapture::from),
            openQuestions = migrated.openQuestions.filter { it.intentionId == intention.id }
                .map(WireOpenQuestion::from),
            approaches = migrated.approaches.filter { it.intentionId == intention.id }
                .map(WireApproach::from),
            adaptiveBuilderStates = migrated.adaptiveBuilderStates
                .filter { it.intentionId == intention.id }
                .map(WireAdaptiveBuilderState::from)
        )
        val entry = TrashEntry(
            id = command.trashId,
            kind = TrashKind.INTENTION,
            entityId = intention.id,
            title = intention.title,
            deletedAtEpochMs = command.nowEpochMs,
            expiresAtEpochMs = command.expiresAtEpochMs,
            parentIntentionId = intention.parentIntentionId,
            pathLabel = IntentionHierarchy.pathLabel(migrated, intention),
            payloadJson = gson.toJson(payload)
        )
        return migrated.copy(
            intentions = migrated.intentions.filterNot { it.id == intention.id },
            facts = migrated.facts.filterNot { it.intentionId == intention.id },
            hypotheses = migrated.hypotheses.filterNot { it.intentionId == intention.id },
            proposals = migrated.proposals.filterNot { it.intentionId == intention.id },
            sessions = migrated.sessions.filterNot { it.intentionId == intention.id },
            sessionRuns = migrated.sessionRuns.filterNot { it.intentionId == intention.id },
            turns = migrated.turns.filterNot { it.intentionId == intention.id },
            thoughtCaptures = migrated.thoughtCaptures.filterNot { it.intentionId == intention.id },
            openQuestions = migrated.openQuestions.filterNot { it.intentionId == intention.id },
            approaches = migrated.approaches.filterNot { it.intentionId == intention.id },
            adaptiveBuilderStates = migrated.adaptiveBuilderStates
                .filterNot { it.intentionId == intention.id },
            drawerCardLayouts = migrated.drawerCardLayouts.filterNot { it.intentionId == intention.id },
            activeIntentionId = if (migrated.activeIntentionId == intention.id) {
                migrated.intentions.firstOrNull { it.id != intention.id }?.id
            } else {
                migrated.activeIntentionId
            },
            trash = migrated.trash.filterNot { it.id == entry.id } + entry
        )
    }

    private fun softDeleteSession(
        migrated: IntentionSnapshot,
        command: FlowCommand.SoftDeleteSession
    ): IntentionSnapshot {
        val session = migrated.sessions.firstOrNull { it.id == command.sessionId }
            ?: return migrated
        val parent = migrated.intentions.firstOrNull { it.id == session.intentionId }
        val runs = migrated.sessionRuns.filter { it.sessionId == session.id }
        val payload = SessionTrashPayload(
            session = WireSession.from(session),
            sessionRuns = runs.map(WireSessionRun::from)
        )
        val entry = TrashEntry(
            id = command.trashId,
            kind = TrashKind.SESSION,
            entityId = session.id,
            title = session.title,
            deletedAtEpochMs = command.nowEpochMs,
            expiresAtEpochMs = command.expiresAtEpochMs,
            parentIntentionId = session.intentionId,
            pathLabel = listOfNotNull(parent?.title, session.title).joinToString(" / "),
            payloadJson = gson.toJson(payload)
        )
        return migrated.copy(
            sessions = migrated.sessions.filterNot { it.id == session.id },
            sessionRuns = migrated.sessionRuns.filterNot { it.sessionId == session.id },
            intentions = touch(migrated.intentions, session.intentionId, command.nowEpochMs),
            trash = migrated.trash.filterNot { it.id == entry.id } + entry
        )
    }

    private fun restoreTrash(
        migrated: IntentionSnapshot,
        command: FlowCommand.RestoreTrashItem
    ): IntentionSnapshot {
        val entry = migrated.trash.firstOrNull { it.id == command.trashId } ?: return migrated
        if (entry.expiresAtEpochMs != null && entry.expiresAtEpochMs < command.nowEpochMs) {
            return migrated
        }
        return when (entry.kind) {
            TrashKind.INTENTION -> {
                val payload = gson.fromJson(entry.payloadJson, IntentionTrashPayload::class.java)
                    ?: return migrated
                val intention = payload.intention.toDomain()
                migrated.copy(
                    intentions = migrated.intentions.filterNot { it.id == intention.id } +
                        intention.copy(updatedAtEpochMs = command.nowEpochMs),
                    facts = mergeById(migrated.facts, payload.facts.map { it.toDomain() }) { it.id },
                    hypotheses = mergeById(
                        migrated.hypotheses,
                        payload.hypotheses.map { it.toDomain() }
                    ) { it.id },
                    proposals = mergeById(
                        migrated.proposals,
                        payload.proposals.map { it.toDomain() }
                    ) { it.id },
                    sessions = mergeById(
                        migrated.sessions,
                        payload.sessions.map { it.toDomain() }
                    ) { it.id },
                    sessionRuns = mergeById(
                        migrated.sessionRuns,
                        payload.sessionRuns.map { it.toDomain() }
                    ) { it.id },
                    turns = migrated.turns + payload.turns.map { it.toDomain() }.filter { restored ->
                        migrated.turns.none { it.id == restored.id }
                    },
                    thoughtCaptures = mergeById(
                        migrated.thoughtCaptures,
                        payload.thoughtCaptures.map { it.toDomain() }
                    ) { it.id },
                    openQuestions = mergeById(
                        migrated.openQuestions,
                        payload.openQuestions.map { it.toDomain() }
                    ) { it.id },
                    approaches = mergeById(
                        migrated.approaches,
                        payload.approaches.map { it.toDomain() }
                    ) { it.id },
                    adaptiveBuilderStates = mergeById(
                        migrated.adaptiveBuilderStates,
                        payload.adaptiveBuilderStates.map { it.toDomain() }
                    ) { it.intentionId },
                    activeIntentionId = migrated.activeIntentionId ?: intention.id,
                    trash = migrated.trash.filterNot { it.id == entry.id }
                )
            }
            TrashKind.SESSION -> {
                val payload = gson.fromJson(entry.payloadJson, SessionTrashPayload::class.java)
                    ?: return migrated
                val session = payload.session.toDomain()
                migrated.copy(
                    sessions = migrated.sessions.filterNot { it.id == session.id } + session,
                    sessionRuns = mergeById(
                        migrated.sessionRuns,
                        payload.sessionRuns.map { it.toDomain() }
                    ) { it.id },
                    intentions = touch(migrated.intentions, session.intentionId, command.nowEpochMs),
                    trash = migrated.trash.filterNot { it.id == entry.id }
                )
            }
        }
    }

    private fun <T> mergeById(existing: List<T>, incoming: List<T>, idOf: (T) -> String): List<T> {
        val ids = incoming.map(idOf).toSet()
        return existing.filterNot { idOf(it) in ids } + incoming
    }

    private fun appendHistory(
        before: IntentionSnapshot,
        after: IntentionSnapshot,
        command: FlowCommand
    ): IntentionSnapshot {
        if (after === before || after == before) {
            // No-op reduce (e.g. missing entity) — still record irreversible external notes.
            if (command !is FlowCommand.RecordExternalAction) return after
        }
        val classification = CommandReversibility.classify(command)
        val txnId = when (command) {
            is FlowCommand.RecordExternalAction -> command.transactionId
            else -> "txn-${command.javaClass.simpleName}-${classification.summary.hashCode()}-" +
                when (command) {
                    is FlowCommand.ApproveProposal -> command.nowEpochMs
                    is FlowCommand.DeclineProposal -> command.nowEpochMs
                    is FlowCommand.EditProposal -> command.nowEpochMs
                    is FlowCommand.DeferNextSession -> command.nowEpochMs
                    is FlowCommand.BeginSession -> command.nowEpochMs
                    is FlowCommand.CompleteSessionRun -> command.nowEpochMs
                    is FlowCommand.AbandonSessionRun -> command.nowEpochMs
                    is FlowCommand.SoftPauseSessionRun -> command.nowEpochMs
                    is FlowCommand.ResumeSessionRun -> command.nowEpochMs
                    is FlowCommand.SoftDeleteIntention -> command.nowEpochMs
                    is FlowCommand.SoftDeleteSession -> command.nowEpochMs
                    is FlowCommand.RestoreTrashItem -> command.nowEpochMs
                    is FlowCommand.PurgeTrashItem -> command.nowEpochMs
                    is FlowCommand.RecordExternalAction -> command.nowEpochMs
                    is FlowCommand.SeedExploring -> command.nowEpochMs
                    is FlowCommand.AddUserFact -> command.nowEpochMs
                    is FlowCommand.AddHypothesis -> command.nowEpochMs
                    is FlowCommand.AddConversationTurn -> command.nowEpochMs
                    is FlowCommand.ProposeRecurringSession -> command.nowEpochMs
                    is FlowCommand.SetActiveIntention -> command.nowEpochMs
                    is FlowCommand.SetParentIntention -> command.nowEpochMs
                    is FlowCommand.MarkPhase -> command.nowEpochMs
                    is FlowCommand.CaptureThought -> command.nowEpochMs
                    is FlowCommand.CaptureErrand -> command.nowEpochMs
                    is FlowCommand.CompleteErrand -> command.nowEpochMs
                    is FlowCommand.ReopenErrand -> command.nowEpochMs
                    is FlowCommand.ToggleErrandChecklistItem -> command.nowEpochMs
                    is FlowCommand.UpdateErrand -> command.nowEpochMs
                    is FlowCommand.RecordObservedEvidence -> command.nowEpochMs
                    is FlowCommand.DismissEvidence -> command.nowEpochMs
                    is FlowCommand.ConfirmDailyCheckIn -> command.nowEpochMs
                    is FlowCommand.RecordMiddayCheck -> command.nowEpochMs
                    is FlowCommand.CompleteDailyCheckOut -> command.nowEpochMs
                    is FlowCommand.RecordTrackerEntry -> command.nowEpochMs
                    is FlowCommand.CorrectTrackerEntry -> command.nowEpochMs
                    is FlowCommand.DeleteTrackerEntry -> command.nowEpochMs
                    is FlowCommand.MarkThoughtStatus -> command.nowEpochMs
                    is FlowCommand.ClassifyThought -> command.nowEpochMs
                    is FlowCommand.StartBuilderDraft -> command.nowEpochMs
                    is FlowCommand.SetBuilderShape -> command.nowEpochMs
                    is FlowCommand.AnswerBuilderPrompt -> command.nowEpochMs
                    is FlowCommand.CommitBuilderIntention -> command.nowEpochMs
                    is FlowCommand.CancelBuilderDraft -> command.nowEpochMs
                    is FlowCommand.StartTriageBatch -> command.nowEpochMs
                    is FlowCommand.ConfirmTriageItem -> command.nowEpochMs
                    is FlowCommand.RejectTriageItem -> command.nowEpochMs
                    is FlowCommand.ClearTriageBatch -> command.nowEpochMs
                    is FlowCommand.ReorderIntentions -> command.nowEpochMs
                    is FlowCommand.HoldAppForIntention -> command.nowEpochMs
                    is FlowCommand.ReorderHeldApps -> command.nowEpochMs
                    is FlowCommand.ReleaseHeldApp -> command.nowEpochMs
                    is FlowCommand.UpdateDrawerCardLayout -> command.nowEpochMs
                    is FlowCommand.SetProactivityPrefs -> command.nowEpochMs
                    is FlowCommand.ArmTriggerCooldown -> command.nowEpochMs
                    is FlowCommand.LeaveMeAlone -> command.nowEpochMs
                    is FlowCommand.QueueIntervention -> command.nowEpochMs
                    is FlowCommand.MarkInterventionShown -> command.nowEpochMs
                    is FlowCommand.DismissIntervention -> command.nowEpochMs
                    is FlowCommand.UndoLastReversible -> command.nowEpochMs
                    is FlowCommand.SaveTypedFact -> command.nowEpochMs
                    is FlowCommand.CorrectTypedFact -> command.nowEpochMs
                    is FlowCommand.UpsertOpenQuestion -> command.nowEpochMs
                    is FlowCommand.AnswerOpenQuestion -> command.nowEpochMs
                    is FlowCommand.UpsertApproach -> command.nowEpochMs
                    is FlowCommand.SelectApproaches -> command.nowEpochMs
                    is FlowCommand.SetAdaptiveBuilderState -> command.nowEpochMs
                    is FlowCommand.RemapIntentionId -> command.nowEpochMs
                    is FlowCommand.DeactivateBuilderAnswerCards -> command.nowEpochMs
                    is FlowCommand.AppendDecisionEvent -> command.nowEpochMs
                }
        }
        val occurredAt = when (command) {
            is FlowCommand.ApproveProposal -> command.nowEpochMs
            is FlowCommand.DeclineProposal -> command.nowEpochMs
            is FlowCommand.EditProposal -> command.nowEpochMs
            is FlowCommand.DeferNextSession -> command.nowEpochMs
            is FlowCommand.BeginSession -> command.nowEpochMs
            is FlowCommand.CompleteSessionRun -> command.nowEpochMs
            is FlowCommand.AbandonSessionRun -> command.nowEpochMs
            is FlowCommand.SoftPauseSessionRun -> command.nowEpochMs
            is FlowCommand.ResumeSessionRun -> command.nowEpochMs
            is FlowCommand.SoftDeleteIntention -> command.nowEpochMs
            is FlowCommand.SoftDeleteSession -> command.nowEpochMs
            is FlowCommand.RestoreTrashItem -> command.nowEpochMs
            is FlowCommand.PurgeTrashItem -> command.nowEpochMs
            is FlowCommand.RecordExternalAction -> command.nowEpochMs
            is FlowCommand.SeedExploring -> command.nowEpochMs
            is FlowCommand.AddUserFact -> command.nowEpochMs
            is FlowCommand.AddHypothesis -> command.nowEpochMs
            is FlowCommand.AddConversationTurn -> command.nowEpochMs
            is FlowCommand.ProposeRecurringSession -> command.nowEpochMs
            is FlowCommand.SetActiveIntention -> command.nowEpochMs
            is FlowCommand.SetParentIntention -> command.nowEpochMs
            is FlowCommand.MarkPhase -> command.nowEpochMs
            is FlowCommand.CaptureThought -> command.nowEpochMs
            is FlowCommand.CaptureErrand -> command.nowEpochMs
            is FlowCommand.CompleteErrand -> command.nowEpochMs
            is FlowCommand.ReopenErrand -> command.nowEpochMs
            is FlowCommand.ToggleErrandChecklistItem -> command.nowEpochMs
            is FlowCommand.UpdateErrand -> command.nowEpochMs
            is FlowCommand.RecordObservedEvidence -> command.nowEpochMs
            is FlowCommand.DismissEvidence -> command.nowEpochMs
            is FlowCommand.ConfirmDailyCheckIn -> command.nowEpochMs
            is FlowCommand.RecordMiddayCheck -> command.nowEpochMs
            is FlowCommand.CompleteDailyCheckOut -> command.nowEpochMs
            is FlowCommand.RecordTrackerEntry -> command.nowEpochMs
            is FlowCommand.CorrectTrackerEntry -> command.nowEpochMs
            is FlowCommand.DeleteTrackerEntry -> command.nowEpochMs
            is FlowCommand.MarkThoughtStatus -> command.nowEpochMs
            is FlowCommand.ClassifyThought -> command.nowEpochMs
            is FlowCommand.StartBuilderDraft -> command.nowEpochMs
            is FlowCommand.SetBuilderShape -> command.nowEpochMs
            is FlowCommand.AnswerBuilderPrompt -> command.nowEpochMs
            is FlowCommand.CommitBuilderIntention -> command.nowEpochMs
            is FlowCommand.CancelBuilderDraft -> command.nowEpochMs
            is FlowCommand.StartTriageBatch -> command.nowEpochMs
            is FlowCommand.ConfirmTriageItem -> command.nowEpochMs
            is FlowCommand.RejectTriageItem -> command.nowEpochMs
            is FlowCommand.ClearTriageBatch -> command.nowEpochMs
            is FlowCommand.ReorderIntentions -> command.nowEpochMs
            is FlowCommand.HoldAppForIntention -> command.nowEpochMs
            is FlowCommand.ReorderHeldApps -> command.nowEpochMs
            is FlowCommand.ReleaseHeldApp -> command.nowEpochMs
            is FlowCommand.UpdateDrawerCardLayout -> command.nowEpochMs
            is FlowCommand.SetProactivityPrefs -> command.nowEpochMs
            is FlowCommand.ArmTriggerCooldown -> command.nowEpochMs
            is FlowCommand.LeaveMeAlone -> command.nowEpochMs
            is FlowCommand.QueueIntervention -> command.nowEpochMs
            is FlowCommand.MarkInterventionShown -> command.nowEpochMs
            is FlowCommand.DismissIntervention -> command.nowEpochMs
            is FlowCommand.UndoLastReversible -> command.nowEpochMs
            is FlowCommand.SaveTypedFact -> command.nowEpochMs
            is FlowCommand.CorrectTypedFact -> command.nowEpochMs
            is FlowCommand.UpsertOpenQuestion -> command.nowEpochMs
            is FlowCommand.AnswerOpenQuestion -> command.nowEpochMs
            is FlowCommand.UpsertApproach -> command.nowEpochMs
            is FlowCommand.SelectApproaches -> command.nowEpochMs
            is FlowCommand.SetAdaptiveBuilderState -> command.nowEpochMs
            is FlowCommand.RemapIntentionId -> command.nowEpochMs
            is FlowCommand.DeactivateBuilderAnswerCards -> command.nowEpochMs
            is FlowCommand.AppendDecisionEvent -> command.nowEpochMs
        }
        val txn = FlowTransaction(
            id = txnId,
            commandKind = command.javaClass.simpleName,
            summary = classification.summary,
            occurredAtEpochMs = occurredAt,
            reversible = classification.reversible,
            irreversibleReason = classification.irreversibleReason,
            beforeJson = if (classification.reversible) encodeDomain(before) else null
        )
        return after.copy(transactions = after.transactions + txn)
    }

    private fun undoLast(
        current: IntentionSnapshot,
        command: FlowCommand.UndoLastReversible
    ): IntentionSnapshot {
        val target = CommandReversibility.lastUndoable(current.transactions) ?: return current
        val restoredDomain = decodeDomain(target.beforeJson!!)
        val undoTxn = FlowTransaction(
            id = command.transactionId,
            commandKind = "UndoLastReversible",
            summary = "Undid: ${target.summary}",
            occurredAtEpochMs = command.nowEpochMs,
            reversible = false,
            irreversibleReason = null,
            beforeJson = null,
            undone = false,
            undoOfTransactionId = target.id
        )
        return restoredDomain.copy(
            schemaVersion = SCHEMA_VERSION,
            transactions = current.transactions.map {
                if (it.id == target.id) it.copy(undone = true) else it
            } + undoTxn
        )
    }

    private fun endSessionRun(
        migrated: IntentionSnapshot,
        runId: String,
        endStatus: SessionRunStatus,
        nowEpochMs: Long
    ): IntentionSnapshot {
        val run = migrated.sessionRuns.firstOrNull { it.id == runId } ?: return migrated
        if (run.status != SessionRunStatus.IN_PROGRESS) return migrated
        // Fold an open soft-pause into accumulation before ending.
        val folded = if (run.pausedAtEpochMs != null) {
            val extra = (nowEpochMs - run.pausedAtEpochMs).coerceAtLeast(0L)
            run.copy(
                accumulatedPausedMs = run.accumulatedPausedMs + extra,
                pausedAtEpochMs = null
            )
        } else {
            run
        }
        return migrated.copy(
            sessionRuns = migrated.sessionRuns.map {
                if (it.id == runId) {
                    folded.copy(status = endStatus, endedAtEpochMs = nowEpochMs)
                } else it
            },
            intentions = touch(migrated.intentions, run.intentionId, nowEpochMs)
        )
    }

    private fun softPauseSessionRun(
        migrated: IntentionSnapshot,
        runId: String,
        nowEpochMs: Long
    ): IntentionSnapshot {
        val run = migrated.sessionRuns.firstOrNull { it.id == runId } ?: return migrated
        if (run.status != SessionRunStatus.IN_PROGRESS) return migrated
        if (run.pausedAtEpochMs != null) return migrated
        return migrated.copy(
            sessionRuns = migrated.sessionRuns.map {
                if (it.id == runId) it.copy(pausedAtEpochMs = nowEpochMs) else it
            },
            intentions = touch(migrated.intentions, run.intentionId, nowEpochMs)
        )
    }

    private fun resumeSessionRun(
        migrated: IntentionSnapshot,
        runId: String,
        nowEpochMs: Long
    ): IntentionSnapshot {
        val run = migrated.sessionRuns.firstOrNull { it.id == runId } ?: return migrated
        if (run.status != SessionRunStatus.IN_PROGRESS) return migrated
        val pausedAt = run.pausedAtEpochMs ?: return migrated
        val extra = (nowEpochMs - pausedAt).coerceAtLeast(0L)
        return migrated.copy(
            sessionRuns = migrated.sessionRuns.map {
                if (it.id == runId) {
                    it.copy(
                        pausedAtEpochMs = null,
                        accumulatedPausedMs = it.accumulatedPausedMs + extra
                    )
                } else it
            },
            intentions = touch(migrated.intentions, run.intentionId, nowEpochMs)
        )
    }

    /**
     * OS-154 evening disposition — user-picked only. Thought text is reused as SAID;
     * no LLM invent.
     */
    private fun classifyThought(
        migrated: IntentionSnapshot,
        command: FlowCommand.ClassifyThought
    ): IntentionSnapshot {
        val thought = migrated.thoughtCaptures.firstOrNull { it.id == command.thoughtId }
            ?: return migrated
        if (thought.status != ThoughtCaptureStatus.RAW || thought.disposition != null) {
            return migrated
        }
        val text = thought.text.trim()
        if (text.isEmpty()) return migrated

        return when (command.disposition) {
            ThoughtDisposition.KEEP -> migrated.copy(
                thoughtCaptures = migrated.thoughtCaptures.map {
                    if (it.id == thought.id) {
                        it.copy(disposition = ThoughtDisposition.KEEP)
                    } else it
                }
            )

            ThoughtDisposition.DISCARD -> migrated.copy(
                thoughtCaptures = migrated.thoughtCaptures.map {
                    if (it.id == thought.id) {
                        it.copy(
                            status = ThoughtCaptureStatus.DISCARDED,
                            disposition = ThoughtDisposition.DISCARD
                        )
                    } else it
                }
            )

            ThoughtDisposition.REMINDER -> {
                val parentId = thought.intentionId
                    ?: migrated.activeIntentionId
                    ?: migrated.intentions.firstOrNull()?.id
                    ?: return migrated
                val factId = command.factId ?: return migrated
                val fact = Fact(
                    id = factId,
                    intentionId = parentId,
                    text = "Reminder: $text",
                    provenance = Provenance(
                        ProvenanceKind.SAID,
                        command.nowEpochMs,
                        "evening_thought_reminder"
                    )
                )
                migrated.copy(
                    facts = migrated.facts.filterNot { it.id == fact.id } + fact,
                    thoughtCaptures = migrated.thoughtCaptures.map {
                        if (it.id == thought.id) {
                            it.copy(
                                status = ThoughtCaptureStatus.PROMOTED,
                                disposition = ThoughtDisposition.REMINDER,
                                intentionId = parentId
                            )
                        } else it
                    },
                    intentions = touch(migrated.intentions, parentId, command.nowEpochMs)
                )
            }

            ThoughtDisposition.INTENTION -> {
                val intentionId = command.newIntentionId ?: return migrated
                val shape = IntentionBuilder.suggestShape(text)
                val title = IntentionBuilder.titleFrom(text, shape)
                val intention = Intention(
                    id = intentionId,
                    title = title,
                    statement = text,
                    phase = IntentionPhase.EXPLORING,
                    createdAtEpochMs = command.nowEpochMs,
                    updatedAtEpochMs = command.nowEpochMs,
                    shapeTag = shape
                )
                val factId = command.factId ?: "fact-evening-$intentionId-seed"
                val seedFact = Fact(
                    id = factId,
                    intentionId = intentionId,
                    text = text,
                    provenance = Provenance(
                        ProvenanceKind.SAID,
                        command.nowEpochMs,
                        "evening_thought_intention"
                    )
                )
                val existingIds = migrated.intentions.map { it.id }
                val nextOrder = when {
                    intention.id in migrated.intentionOrder -> migrated.intentionOrder
                    migrated.intentionOrder.isEmpty() ->
                        existingIds.filterNot { it == intention.id } + intention.id
                    else -> migrated.intentionOrder.filterNot { it == intention.id } + intention.id
                }
                migrated.copy(
                    intentions = migrated.intentions.filterNot { it.id == intention.id } + intention,
                    facts = migrated.facts.filterNot { it.id == seedFact.id } + seedFact,
                    thoughtCaptures = migrated.thoughtCaptures.map {
                        if (it.id == thought.id) {
                            it.copy(
                                status = ThoughtCaptureStatus.PROMOTED,
                                disposition = ThoughtDisposition.INTENTION,
                                intentionId = intentionId
                            )
                        } else it
                    },
                    activeIntentionId = intentionId,
                    intentionOrder = nextOrder
                )
            }

            ThoughtDisposition.TASK -> {
                // Lightweight todo/session stub until CASE-01 ErrandTodo lands on main.
                val parentId = thought.intentionId
                    ?: migrated.activeIntentionId
                    ?: migrated.intentions.firstOrNull()?.id
                var intentions = migrated.intentions
                var intentionOrder = migrated.intentionOrder
                var activeId = migrated.activeIntentionId
                val resolvedParent = if (parentId != null) {
                    parentId
                } else {
                    val newId = command.newIntentionId ?: return migrated
                    val shape = IntentionBuilder.suggestShape(text)
                    val intention = Intention(
                        id = newId,
                        title = IntentionBuilder.titleFrom(text, shape),
                        statement = text,
                        phase = IntentionPhase.ACTIVE,
                        createdAtEpochMs = command.nowEpochMs,
                        updatedAtEpochMs = command.nowEpochMs,
                        shapeTag = shape
                    )
                    intentions = intentions.filterNot { it.id == intention.id } + intention
                    intentionOrder = if (intentionOrder.isEmpty()) {
                        listOf(intention.id)
                    } else {
                        intentionOrder.filterNot { it == intention.id } + intention.id
                    }
                    activeId = intention.id
                    intention.id
                }
                val factId = command.factId ?: return migrated
                val sessionId = command.sessionId ?: return migrated
                val dayOfWeek = command.taskDayOfWeek ?: return migrated
                val hour = command.taskHour ?: return migrated
                val minute = command.taskMinute ?: 0
                val fact = Fact(
                    id = factId,
                    intentionId = resolvedParent,
                    text = "Task: $text",
                    provenance = Provenance(
                        ProvenanceKind.SAID,
                        command.nowEpochMs,
                        "evening_thought_task"
                    )
                )
                val session = ScheduledSession(
                    id = sessionId,
                    intentionId = resolvedParent,
                    title = text.take(64),
                    dayOfWeek = dayOfWeek,
                    hour = hour,
                    minute = minute
                )
                migrated.copy(
                    intentions = touch(intentions, resolvedParent, command.nowEpochMs),
                    intentionOrder = intentionOrder,
                    activeIntentionId = activeId ?: resolvedParent,
                    facts = migrated.facts.filterNot { it.id == fact.id } + fact,
                    sessions = migrated.sessions.filterNot { it.id == session.id } + session,
                    thoughtCaptures = migrated.thoughtCaptures.map {
                        if (it.id == thought.id) {
                            it.copy(
                                status = ThoughtCaptureStatus.PROMOTED,
                                disposition = ThoughtDisposition.TASK,
                                intentionId = resolvedParent
                            )
                        } else it
                    }
                )
            }
        }
    }

    fun migrate(snapshot: IntentionSnapshot): IntentionSnapshot {
        if (snapshot.schemaVersion == SCHEMA_VERSION) return snapshot
        return snapshot.copy(schemaVersion = SCHEMA_VERSION)
    }

    private fun touch(list: List<Intention>, id: String, now: Long) =
        list.map { if (it.id == id) it.copy(updatedAtEpochMs = now) else it }

    private fun setPhase(list: List<Intention>, id: String, phase: IntentionPhase, now: Long) =
        list.map {
            if (it.id == id) it.copy(phase = phase, updatedAtEpochMs = now) else it
        }

    private fun persist(snapshot: IntentionSnapshot) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(encode(snapshot))
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
    }

    fun encode(snapshot: IntentionSnapshot): String = gson.toJson(WireSnapshot.from(snapshot))

    /** Domain-only encode used as undo beforeRef (keeps history out of nested before blobs). */
    fun encodeDomain(snapshot: IntentionSnapshot): String =
        gson.toJson(
            WireSnapshot.from(
                snapshot.copy(transactions = emptyList())
            )
        )

    fun decode(raw: String): IntentionSnapshot {
        val wire = gson.fromJson(raw, WireSnapshot::class.java) ?: WireSnapshot()
        return migrate(wire.toDomain())
    }

    fun decodeDomain(raw: String): IntentionSnapshot {
        val wire = gson.fromJson(raw, WireSnapshot::class.java) ?: WireSnapshot()
        return migrate(wire.toDomain()).copy(transactions = emptyList())
    }

    data class IntentionTrashPayload(
        val intention: WireIntention = WireIntention(),
        val facts: List<WireFact> = emptyList(),
        val hypotheses: List<WireHypothesis> = emptyList(),
        val proposals: List<WireProposal> = emptyList(),
        val sessions: List<WireSession> = emptyList(),
        val sessionRuns: List<WireSessionRun> = emptyList(),
        val turns: List<WireTurn> = emptyList(),
        val thoughtCaptures: List<WireThoughtCapture> = emptyList(),
        val openQuestions: List<WireOpenQuestion> = emptyList(),
        val approaches: List<WireApproach> = emptyList(),
        val adaptiveBuilderStates: List<WireAdaptiveBuilderState> = emptyList()
    )

    data class SessionTrashPayload(
        val session: WireSession = WireSession(),
        val sessionRuns: List<WireSessionRun> = emptyList()
    )

    data class WireProvenance(
        val kind: String = ProvenanceKind.SAID.name,
        val learnedAtEpochMs: Long = 0L,
        val sourceLabel: String = ""
    ) {
        fun toDomain() = Provenance(
            kind = ProvenanceKind.valueOf(kind),
            learnedAtEpochMs = learnedAtEpochMs,
            sourceLabel = sourceLabel
        )
        companion object {
            fun from(p: Provenance) = WireProvenance(p.kind.name, p.learnedAtEpochMs, p.sourceLabel)
        }
    }

    data class WireIntention(
        val id: String = "",
        val title: String = "",
        val statement: String = "",
        val phase: String = IntentionPhase.EXPLORING.name,
        val createdAtEpochMs: Long = 0L,
        val updatedAtEpochMs: Long = 0L,
        val shapeTag: String = "other",
        val parentIntentionId: String? = null
    ) {
        fun toDomain() = Intention(
            id = id,
            title = title,
            statement = statement,
            phase = IntentionPhase.valueOf(phase),
            createdAtEpochMs = createdAtEpochMs,
            updatedAtEpochMs = updatedAtEpochMs,
            shapeTag = shapeTag,
            parentIntentionId = parentIntentionId
        )
        companion object {
            fun from(it: Intention) = WireIntention(
                it.id, it.title, it.statement, it.phase.name,
                it.createdAtEpochMs, it.updatedAtEpochMs, it.shapeTag,
                it.parentIntentionId
            )
        }
    }

    data class WireFact(
        val id: String = "",
        val intentionId: String = "",
        val text: String = "",
        val provenance: WireProvenance = WireProvenance(),
        val key: String? = null,
        val rawUtterance: String? = null,
        val normalizedValue: String? = null,
        val sourceTurnId: String? = null,
        val timeZoneId: String? = null,
        val validFromEpochMs: Long? = null,
        val validUntilEpochMs: Long? = null,
        val estimatedEpochMs: Long? = null,
        val estimatedRangeStartEpochMs: Long? = null,
        val estimatedRangeEndEpochMs: Long? = null,
        val approximate: Boolean = false,
        val confirmation: String = FactConfirmation.CONFIRMED.name,
        val status: String = FactStatus.ACTIVE.name,
        val supersedesFactId: String? = null,
        val revision: Int = 1
    ) {
        fun toDomain() = Fact(
            id = id,
            intentionId = intentionId,
            text = text,
            provenance = provenance.toDomain(),
            key = key,
            rawUtterance = rawUtterance,
            normalizedValue = normalizedValue,
            sourceTurnId = sourceTurnId,
            timeZoneId = timeZoneId,
            validFromEpochMs = validFromEpochMs,
            validUntilEpochMs = validUntilEpochMs,
            estimatedEpochMs = estimatedEpochMs,
            estimatedRangeStartEpochMs = estimatedRangeStartEpochMs,
            estimatedRangeEndEpochMs = estimatedRangeEndEpochMs,
            approximate = approximate,
            confirmation = runCatching { FactConfirmation.valueOf(confirmation) }
                .getOrDefault(FactConfirmation.CONFIRMED),
            status = runCatching { FactStatus.valueOf(status) }.getOrDefault(FactStatus.ACTIVE),
            supersedesFactId = supersedesFactId,
            revision = revision
        )
        companion object {
            fun from(f: Fact) = WireFact(
                id = f.id,
                intentionId = f.intentionId,
                text = f.text,
                provenance = WireProvenance.from(f.provenance),
                key = f.key,
                rawUtterance = f.rawUtterance,
                normalizedValue = f.normalizedValue,
                sourceTurnId = f.sourceTurnId,
                timeZoneId = f.timeZoneId,
                validFromEpochMs = f.validFromEpochMs,
                validUntilEpochMs = f.validUntilEpochMs,
                estimatedEpochMs = f.estimatedEpochMs,
                estimatedRangeStartEpochMs = f.estimatedRangeStartEpochMs,
                estimatedRangeEndEpochMs = f.estimatedRangeEndEpochMs,
                approximate = f.approximate,
                confirmation = f.confirmation.name,
                status = f.status.name,
                supersedesFactId = f.supersedesFactId,
                revision = f.revision
            )
        }
    }

    data class WireOpenQuestion(
        val id: String = "",
        val intentionId: String = "",
        val purpose: String = "",
        val whyItMatters: String = "",
        val blocksWhat: String = "",
        val answerSchema: String = AnswerSchema.FREE_TEXT.name,
        val optionIds: List<String> = emptyList(),
        val state: String = OpenQuestionState.OPEN.name,
        val priority: Int = 0,
        val askedAtEpochMs: Long? = null,
        val answeredAtEpochMs: Long? = null,
        val sourceTurnId: String? = null,
        val lastAskedEpochMs: Long? = null
    ) {
        fun toDomain() = OpenQuestion(
            id = id,
            intentionId = intentionId,
            purpose = purpose,
            whyItMatters = whyItMatters,
            blocksWhat = blocksWhat,
            answerSchema = runCatching { AnswerSchema.valueOf(answerSchema) }
                .getOrDefault(AnswerSchema.FREE_TEXT),
            optionIds = optionIds,
            state = runCatching { OpenQuestionState.valueOf(state) }
                .getOrDefault(OpenQuestionState.OPEN),
            priority = priority,
            askedAtEpochMs = askedAtEpochMs,
            answeredAtEpochMs = answeredAtEpochMs,
            sourceTurnId = sourceTurnId,
            lastAskedEpochMs = lastAskedEpochMs
        )
        companion object {
            fun from(q: OpenQuestion) = WireOpenQuestion(
                id = q.id,
                intentionId = q.intentionId,
                purpose = q.purpose,
                whyItMatters = q.whyItMatters,
                blocksWhat = q.blocksWhat,
                answerSchema = q.answerSchema.name,
                optionIds = q.optionIds,
                state = q.state.name,
                priority = q.priority,
                askedAtEpochMs = q.askedAtEpochMs,
                answeredAtEpochMs = q.answeredAtEpochMs,
                sourceTurnId = q.sourceTurnId,
                lastAskedEpochMs = q.lastAskedEpochMs
            )
        }
    }

    data class WireApproach(
        val id: String = "",
        val intentionId: String = "",
        val title: String = "",
        val summary: String = "",
        val status: String = ApproachStatus.CANDIDATE.name,
        val actor: String = ApproachActor.INTENT.name,
        val packageName: String? = null,
        val capabilityNote: String = "",
        val sourceTurnId: String? = null,
        val selectedAtEpochMs: Long? = null
    ) {
        fun toDomain() = Approach(
            id = id,
            intentionId = intentionId,
            title = title,
            summary = summary,
            status = runCatching { ApproachStatus.valueOf(status) }
                .getOrDefault(ApproachStatus.CANDIDATE),
            actor = runCatching { ApproachActor.valueOf(actor) }
                .getOrDefault(ApproachActor.INTENT),
            packageName = packageName,
            capabilityNote = capabilityNote,
            sourceTurnId = sourceTurnId,
            selectedAtEpochMs = selectedAtEpochMs
        )
        companion object {
            fun from(a: Approach) = WireApproach(
                id = a.id,
                intentionId = a.intentionId,
                title = a.title,
                summary = a.summary,
                status = a.status.name,
                actor = a.actor.name,
                packageName = a.packageName,
                capabilityNote = a.capabilityNote,
                sourceTurnId = a.sourceTurnId,
                selectedAtEpochMs = a.selectedAtEpochMs
            )
        }
    }

    data class WireAdaptiveBuilderState(
        val intentionId: String = "",
        val revision: Long = 0L,
        val typeFacets: List<String> = emptyList(),
        val currentQuestionId: String? = null,
        val pendingProposalId: String? = null,
        val conversationCursorTurnId: String? = null,
        val lastAnsweredTurnId: String? = null,
        val lifecycleHint: String = "discovery",
        val policyVersion: String = AdaptiveBuilderState.POLICY_VERSION,
        val updatedAtEpochMs: Long = 0L
    ) {
        fun toDomain() = AdaptiveBuilderState(
            intentionId = intentionId,
            revision = revision,
            typeFacets = typeFacets,
            currentQuestionId = currentQuestionId,
            pendingProposalId = pendingProposalId,
            conversationCursorTurnId = conversationCursorTurnId,
            lastAnsweredTurnId = lastAnsweredTurnId,
            lifecycleHint = lifecycleHint,
            policyVersion = policyVersion,
            updatedAtEpochMs = updatedAtEpochMs
        )
        companion object {
            fun from(s: AdaptiveBuilderState) = WireAdaptiveBuilderState(
                intentionId = s.intentionId,
                revision = s.revision,
                typeFacets = s.typeFacets,
                currentQuestionId = s.currentQuestionId,
                pendingProposalId = s.pendingProposalId,
                conversationCursorTurnId = s.conversationCursorTurnId,
                lastAnsweredTurnId = s.lastAnsweredTurnId,
                lifecycleHint = s.lifecycleHint,
                policyVersion = s.policyVersion,
                updatedAtEpochMs = s.updatedAtEpochMs
            )
        }
    }

    data class WireHypothesis(
        val id: String = "",
        val intentionId: String = "",
        val text: String = "",
        val provenance: WireProvenance = WireProvenance()
    ) {
        fun toDomain() = Hypothesis(id, intentionId, text, provenance.toDomain())
        companion object {
            fun from(h: Hypothesis) =
                WireHypothesis(h.id, h.intentionId, h.text, WireProvenance.from(h.provenance))
        }
    }

    data class WireProposal(
        val id: String = "",
        val intentionId: String = "",
        val title: String = "",
        val summary: String = "",
        val proposedDayOfWeek: Int = 1,
        val proposedHour: Int = 0,
        val proposedMinute: Int = 0,
        val status: String = ProposalStatus.PENDING.name,
        val basedOnFactIds: List<String> = emptyList(),
        val provenance: WireProvenance = WireProvenance(),
        val proposedDaysOfWeek: List<Int> = emptyList(),
        val durationMinutes: Int = 60,
        val timeZoneId: String = "",
        val methodsSummary: String = "",
        val capabilityHonesty: String = "",
        val homeImpactNote: String = "",
        val includeMorningCheckIn: Boolean = false,
        val includeEveningReview: Boolean = false
    ) {
        fun toDomain() = Proposal(
            id = id,
            intentionId = intentionId,
            title = title,
            summary = summary,
            proposedDayOfWeek = proposedDayOfWeek,
            proposedHour = proposedHour,
            proposedMinute = proposedMinute,
            status = ProposalStatus.valueOf(status),
            basedOnFactIds = basedOnFactIds,
            provenance = provenance.toDomain(),
            proposedDaysOfWeek = proposedDaysOfWeek,
            durationMinutes = durationMinutes,
            timeZoneId = timeZoneId,
            methodsSummary = methodsSummary,
            capabilityHonesty = capabilityHonesty,
            homeImpactNote = homeImpactNote,
            includeMorningCheckIn = includeMorningCheckIn,
            includeEveningReview = includeEveningReview
        )
        companion object {
            fun from(p: Proposal) = WireProposal(
                id = p.id,
                intentionId = p.intentionId,
                title = p.title,
                summary = p.summary,
                proposedDayOfWeek = p.proposedDayOfWeek,
                proposedHour = p.proposedHour,
                proposedMinute = p.proposedMinute,
                status = p.status.name,
                basedOnFactIds = p.basedOnFactIds,
                provenance = WireProvenance.from(p.provenance),
                proposedDaysOfWeek = p.proposedDaysOfWeek,
                durationMinutes = p.durationMinutes,
                timeZoneId = p.timeZoneId,
                methodsSummary = p.methodsSummary,
                capabilityHonesty = p.capabilityHonesty,
                homeImpactNote = p.homeImpactNote,
                includeMorningCheckIn = p.includeMorningCheckIn,
                includeEveningReview = p.includeEveningReview
            )
        }
    }

    data class WireSession(
        val id: String = "",
        val intentionId: String = "",
        val title: String = "",
        val dayOfWeek: Int = 1,
        val hour: Int = 0,
        val minute: Int = 0,
        val deferredUntilEpochMs: Long? = null
    ) {
        fun toDomain() = ScheduledSession(
            id, intentionId, title, dayOfWeek, hour, minute, deferredUntilEpochMs
        )
        companion object {
            fun from(s: ScheduledSession) = WireSession(
                s.id, s.intentionId, s.title, s.dayOfWeek, s.hour, s.minute, s.deferredUntilEpochMs
            )
        }
    }

    data class WireSessionRun(
        val id: String = "",
        val sessionId: String = "",
        val intentionId: String = "",
        val title: String = "",
        val status: String = SessionRunStatus.IN_PROGRESS.name,
        val startedAtEpochMs: Long = 0L,
        val endedAtEpochMs: Long? = null,
        val affinityPackageName: String? = null,
        val affinityAppLabel: String? = null,
        val pausedAtEpochMs: Long? = null,
        val accumulatedPausedMs: Long = 0L
    ) {
        fun toDomain() = SessionRun(
            id = id,
            sessionId = sessionId,
            intentionId = intentionId,
            title = title,
            status = SessionRunStatus.valueOf(status),
            startedAtEpochMs = startedAtEpochMs,
            endedAtEpochMs = endedAtEpochMs,
            affinityPackageName = affinityPackageName,
            affinityAppLabel = affinityAppLabel,
            pausedAtEpochMs = pausedAtEpochMs,
            accumulatedPausedMs = accumulatedPausedMs
        )
        companion object {
            fun from(r: SessionRun) = WireSessionRun(
                id = r.id,
                sessionId = r.sessionId,
                intentionId = r.intentionId,
                title = r.title,
                status = r.status.name,
                startedAtEpochMs = r.startedAtEpochMs,
                endedAtEpochMs = r.endedAtEpochMs,
                affinityPackageName = r.affinityPackageName,
                affinityAppLabel = r.affinityAppLabel,
                pausedAtEpochMs = r.pausedAtEpochMs,
                accumulatedPausedMs = r.accumulatedPausedMs
            )
        }
    }

    data class WireResponseBlock(
        val blockId: String = "",
        val type: String = "",
        val title: String = "",
        val body: String = "",
        val sourceLabel: String = "",
        val linkedObjectIds: List<String> = emptyList(),
        val actions: List<String> = emptyList(),
        val active: Boolean = true,
        val expiresAtEpochMs: Long? = null
    ) {
        fun toDomain(): ResponseBlock? {
            val parsed = runCatching { ResponseBlockType.valueOf(type) }.getOrNull() ?: return null
            return ResponseBlock(
                blockId = blockId,
                type = parsed,
                title = title,
                body = body,
                sourceLabel = sourceLabel,
                linkedObjectIds = linkedObjectIds,
                actions = actions,
                active = active,
                expiresAtEpochMs = expiresAtEpochMs
            )
        }

        companion object {
            fun from(b: ResponseBlock) = WireResponseBlock(
                blockId = b.blockId,
                type = b.type.name,
                title = b.title,
                body = b.body,
                sourceLabel = b.sourceLabel,
                linkedObjectIds = b.linkedObjectIds,
                actions = b.actions,
                active = b.active,
                expiresAtEpochMs = b.expiresAtEpochMs
            )
        }
    }

    data class WireTurn(
        val id: String = "",
        val intentionId: String = "",
        val role: String = "",
        val text: String = "",
        val atEpochMs: Long = 0L,
        val modality: String? = null,
        val blocks: List<WireResponseBlock> = emptyList()
    ) {
        fun toDomain() = ConversationTurn(
            id = id,
            intentionId = intentionId,
            role = role,
            text = text,
            atEpochMs = atEpochMs,
            modality = modality,
            blocks = blocks.mapNotNull { it.toDomain() }
        )
        companion object {
            fun from(t: ConversationTurn) =
                WireTurn(
                    id = t.id,
                    intentionId = t.intentionId,
                    role = t.role,
                    text = t.text,
                    atEpochMs = t.atEpochMs,
                    modality = t.modality,
                    blocks = t.blocks.map(WireResponseBlock::from)
                )
        }
    }

    data class WireProactivityPrefs(
        val quietHoursStartMin: Int? = null,
        val quietHoursEndMin: Int? = null,
        val interruptionBudgetPerDay: Int = 1,
        val leaveMeAloneUntilEpochMs: Long? = null
    ) {
        fun toDomain() = ProactivityPrefs(
            quietHoursStartMin = quietHoursStartMin,
            quietHoursEndMin = quietHoursEndMin,
            interruptionBudgetPerDay = interruptionBudgetPerDay,
            leaveMeAloneUntilEpochMs = leaveMeAloneUntilEpochMs
        )
        companion object {
            fun from(p: ProactivityPrefs) = WireProactivityPrefs(
                quietHoursStartMin = p.quietHoursStartMin,
                quietHoursEndMin = p.quietHoursEndMin,
                interruptionBudgetPerDay = p.interruptionBudgetPerDay,
                leaveMeAloneUntilEpochMs = p.leaveMeAloneUntilEpochMs
            )
        }
    }

    data class WireTriggerCooldown(
        val key: String = "",
        val armedUntilEpochMs: Long = 0L,
        val reason: String = ""
    ) {
        fun toDomain() = TriggerCooldown(key, armedUntilEpochMs, reason)
        companion object {
            fun from(c: TriggerCooldown) =
                WireTriggerCooldown(c.key, c.armedUntilEpochMs, c.reason)
        }
    }

    data class WireQueuedIntervention(
        val jobId: String = "",
        val turnId: String = "",
        val intentionId: String = "",
        val triggerKey: String = "",
        val kind: String = "",
        val promptText: String = "",
        val evidenceId: String? = null,
        val provenanceKind: String = ProvenanceKind.OBSERVED.name,
        val createdAtEpochMs: Long = 0L,
        val status: String = "queued"
    ) {
        fun toDomain() = QueuedIntervention(
            jobId = jobId,
            turnId = turnId,
            intentionId = intentionId,
            triggerKey = triggerKey,
            kind = kind,
            promptText = promptText,
            evidenceId = evidenceId,
            provenanceKind = runCatching { ProvenanceKind.valueOf(provenanceKind) }
                .getOrDefault(ProvenanceKind.OBSERVED),
            createdAtEpochMs = createdAtEpochMs,
            status = status
        )
        companion object {
            fun from(q: QueuedIntervention) = WireQueuedIntervention(
                jobId = q.jobId,
                turnId = q.turnId,
                intentionId = q.intentionId,
                triggerKey = q.triggerKey,
                kind = q.kind,
                promptText = q.promptText,
                evidenceId = q.evidenceId,
                provenanceKind = q.provenanceKind.name,
                createdAtEpochMs = q.createdAtEpochMs,
                status = q.status
            )
        }
    }

    data class WireTransaction(
        val id: String = "",
        val commandKind: String = "",
        val summary: String = "",
        val occurredAtEpochMs: Long = 0L,
        val reversible: Boolean = false,
        val irreversibleReason: String? = null,
        val beforeJson: String? = null,
        val undone: Boolean = false,
        val undoOfTransactionId: String? = null
    ) {
        fun toDomain() = FlowTransaction(
            id, commandKind, summary, occurredAtEpochMs, reversible,
            irreversibleReason, beforeJson, undone, undoOfTransactionId
        )
        companion object {
            fun from(t: FlowTransaction) = WireTransaction(
                t.id, t.commandKind, t.summary, t.occurredAtEpochMs, t.reversible,
                t.irreversibleReason, t.beforeJson, t.undone, t.undoOfTransactionId
            )
        }
    }

    data class WireTrashEntry(
        val id: String = "",
        val kind: String = TrashKind.INTENTION.name,
        val entityId: String = "",
        val title: String = "",
        val deletedAtEpochMs: Long = 0L,
        val expiresAtEpochMs: Long? = null,
        val parentIntentionId: String? = null,
        val pathLabel: String = "",
        val payloadJson: String = ""
    ) {
        fun toDomain() = TrashEntry(
            id, TrashKind.valueOf(kind), entityId, title, deletedAtEpochMs,
            expiresAtEpochMs, parentIntentionId, pathLabel, payloadJson
        )
        companion object {
            fun from(t: TrashEntry) = WireTrashEntry(
                t.id, t.kind.name, t.entityId, t.title, t.deletedAtEpochMs,
                t.expiresAtEpochMs, t.parentIntentionId, t.pathLabel, t.payloadJson
            )
        }
    }

    data class WireThoughtCapture(
        val id: String = "",
        val intentionId: String? = null,
        val text: String = "",
        val provenance: WireProvenance = WireProvenance(),
        val capturedAtEpochMs: Long = 0L,
        val source: String = "home",
        val status: String = ThoughtCaptureStatus.RAW.name,
        val disposition: String? = null
    ) {
        fun toDomain() = ThoughtCapture(
            id = id,
            intentionId = intentionId,
            text = text,
            provenance = provenance.toDomain(),
            capturedAtEpochMs = capturedAtEpochMs,
            source = source,
            status = runCatching { ThoughtCaptureStatus.valueOf(status) }
                .getOrDefault(ThoughtCaptureStatus.RAW),
            disposition = disposition?.let {
                runCatching { ThoughtDisposition.valueOf(it) }.getOrNull()
            }
        )
        companion object {
            fun from(t: ThoughtCapture) = WireThoughtCapture(
                id = t.id,
                intentionId = t.intentionId,
                text = t.text,
                provenance = WireProvenance.from(t.provenance),
                capturedAtEpochMs = t.capturedAtEpochMs,
                source = t.source,
                status = t.status.name,
                disposition = t.disposition?.name
            )
        }
    }

    data class WireEvidence(
        val id: String = "",
        val intentionId: String = "",
        val sessionRunId: String? = null,
        val packageName: String? = null,
        val appLabel: String? = null,
        val foregroundMs: Long? = null,
        val recordedAtEpochMs: Long = 0L,
        val summaryText: String = "",
        val provenance: WireProvenance = WireProvenance(kind = ProvenanceKind.OBSERVED.name),
        val accuracy: String = EvidenceAccuracy.ACTIVE.name
    ) {
        fun toDomain() = Evidence(
            id = id,
            intentionId = intentionId,
            sessionRunId = sessionRunId,
            packageName = packageName,
            appLabel = appLabel,
            foregroundMs = foregroundMs,
            recordedAtEpochMs = recordedAtEpochMs,
            summaryText = summaryText,
            provenance = provenance.toDomain(),
            accuracy = runCatching { EvidenceAccuracy.valueOf(accuracy) }
                .getOrDefault(EvidenceAccuracy.ACTIVE)
        )

        companion object {
            fun from(e: Evidence) = WireEvidence(
                id = e.id,
                intentionId = e.intentionId,
                sessionRunId = e.sessionRunId,
                packageName = e.packageName,
                appLabel = e.appLabel,
                foregroundMs = e.foregroundMs,
                recordedAtEpochMs = e.recordedAtEpochMs,
                summaryText = e.summaryText,
                provenance = WireProvenance.from(e.provenance),
                accuracy = e.accuracy.name
            )
        }
    }

    data class WireDayCycle(
        val localDate: String = "",
        val checkInAtEpochMs: Long? = null,
        val checkOutAtEpochMs: Long? = null,
        val focusIntentionId: String? = null,
        val focusSummary: String? = null,
        val middayAtEpochMs: Long? = null,
        val middayNote: String? = null
    ) {
        fun toDomain() = DayCycleState(
            localDate = localDate,
            checkInAtEpochMs = checkInAtEpochMs,
            checkOutAtEpochMs = checkOutAtEpochMs,
            focusIntentionId = focusIntentionId,
            focusSummary = focusSummary,
            middayAtEpochMs = middayAtEpochMs,
            middayNote = middayNote
        )

        companion object {
            fun from(d: DayCycleState) = WireDayCycle(
                localDate = d.localDate,
                checkInAtEpochMs = d.checkInAtEpochMs,
                checkOutAtEpochMs = d.checkOutAtEpochMs,
                focusIntentionId = d.focusIntentionId,
                focusSummary = d.focusSummary,
                middayAtEpochMs = d.middayAtEpochMs,
                middayNote = d.middayNote
            )
        }
    }

    data class WireTrackerEntry(
        val id: String = "",
        val intentionId: String = "",
        val moduleId: String = "",
        val valueText: String = "",
        val recordedAtEpochMs: Long = 0L,
        val provenance: WireProvenance = WireProvenance(),
        val status: String = TrackerEntryStatus.ACTIVE.name,
        val eventAtEpochMs: Long? = null,
        val supersedesEntryId: String? = null
    ) {
        fun toDomain(): TrackerEntry {
            val recorded = recordedAtEpochMs
            return TrackerEntry(
                id = id,
                intentionId = intentionId,
                moduleId = moduleId,
                valueText = valueText,
                recordedAtEpochMs = recorded,
                provenance = provenance.toDomain(),
                status = runCatching { TrackerEntryStatus.valueOf(status) }
                    .getOrDefault(TrackerEntryStatus.ACTIVE),
                eventAtEpochMs = eventAtEpochMs ?: recorded,
                supersedesEntryId = supersedesEntryId
            )
        }

        companion object {
            fun from(e: TrackerEntry) = WireTrackerEntry(
                id = e.id,
                intentionId = e.intentionId,
                moduleId = e.moduleId,
                valueText = e.valueText,
                recordedAtEpochMs = e.recordedAtEpochMs,
                provenance = WireProvenance.from(e.provenance),
                status = e.status.name,
                eventAtEpochMs = e.eventAtEpochMs,
                supersedesEntryId = e.supersedesEntryId
            )
        }
    }

    data class WireOccurrence(
        val id: String = "",
        val intentionId: String = "",
        val localDate: String = "",
        val phase: String = "",
        val kind: String = "",
        val summary: String = "",
        val recordedAtEpochMs: Long = 0L,
        val provenance: WireProvenance = WireProvenance()
    ) {
        fun toDomain() = Occurrence(
            id = id,
            intentionId = intentionId,
            localDate = localDate,
            phase = phase,
            kind = kind,
            summary = summary,
            recordedAtEpochMs = recordedAtEpochMs,
            provenance = provenance.toDomain()
        )

        companion object {
            fun from(o: Occurrence) = WireOccurrence(
                id = o.id,
                intentionId = o.intentionId,
                localDate = o.localDate,
                phase = o.phase,
                kind = o.kind,
                summary = o.summary,
                recordedAtEpochMs = o.recordedAtEpochMs,
                provenance = WireProvenance.from(o.provenance)
            )
        }
    }

    data class WireDailyContext(
        val id: String = "",
        val localDate: String = "",
        val intentionId: String? = null,
        val phase: String = "",
        val availability: String? = null,
        val energy: String? = null,
        val priorities: String? = null,
        val constraints: String? = null,
        val note: String? = null,
        val recordedAtEpochMs: Long = 0L,
        val provenance: WireProvenance = WireProvenance()
    ) {
        fun toDomain() = DailyContext(
            id = id,
            localDate = localDate,
            intentionId = intentionId,
            phase = phase,
            availability = availability,
            energy = energy,
            priorities = priorities,
            constraints = constraints,
            note = note,
            recordedAtEpochMs = recordedAtEpochMs,
            provenance = provenance.toDomain()
        )

        companion object {
            fun from(c: DailyContext) = WireDailyContext(
                id = c.id,
                localDate = c.localDate,
                intentionId = c.intentionId,
                phase = c.phase,
                availability = c.availability,
                energy = c.energy,
                priorities = c.priorities,
                constraints = c.constraints,
                note = c.note,
                recordedAtEpochMs = c.recordedAtEpochMs,
                provenance = WireProvenance.from(c.provenance)
            )
        }
    }

    data class WireBuilderDraft(
        val active: Boolean = false,
        val freeText: String = "",
        val suggestedShapeTag: String = "other",
        val shapeTag: String? = null,
        val title: String = "",
        val statement: String = "",
        val factAnswers: Map<String, String> = emptyMap(),
        val phase: String = BuilderPhase.ENTER_TEXT.name,
        val honestyNote: String = "On-device suggestion — not a live model."
    ) {
        fun toDomain() = BuilderDraft(
            active = active,
            freeText = freeText,
            suggestedShapeTag = suggestedShapeTag,
            shapeTag = shapeTag,
            title = title,
            statement = statement,
            factAnswers = factAnswers,
            phase = runCatching { BuilderPhase.valueOf(phase) }.getOrDefault(BuilderPhase.ENTER_TEXT),
            honestyNote = honestyNote
        )

        companion object {
            fun from(d: BuilderDraft) = WireBuilderDraft(
                active = d.active,
                freeText = d.freeText,
                suggestedShapeTag = d.suggestedShapeTag,
                shapeTag = d.shapeTag,
                title = d.title,
                statement = d.statement,
                factAnswers = d.factAnswers,
                phase = d.phase.name,
                honestyNote = d.honestyNote
            )
        }
    }

    data class WireTriageItem(
        val id: String = "",
        val title: String = "",
        val statement: String = "",
        val suggestedShapeTag: String = "other",
        val suggestedNextAction: String = "",
        val sourceThoughtIds: List<String> = emptyList(),
        val status: String = TriageItemStatus.PENDING.name,
        val honestyNote: String = "On-device split — not a live model."
    ) {
        fun toDomain() = TriageItem(
            id = id,
            title = title,
            statement = statement,
            suggestedShapeTag = suggestedShapeTag,
            suggestedNextAction = suggestedNextAction,
            sourceThoughtIds = sourceThoughtIds,
            status = runCatching { TriageItemStatus.valueOf(status) }
                .getOrDefault(TriageItemStatus.PENDING),
            honestyNote = honestyNote
        )

        companion object {
            fun from(i: TriageItem) = WireTriageItem(
                id = i.id,
                title = i.title,
                statement = i.statement,
                suggestedShapeTag = i.suggestedShapeTag,
                suggestedNextAction = i.suggestedNextAction,
                sourceThoughtIds = i.sourceThoughtIds,
                status = i.status.name,
                honestyNote = i.honestyNote
            )
        }
    }

    data class WireTriageState(
        val active: Boolean = false,
        val batchId: String = "",
        val items: List<WireTriageItem> = emptyList(),
        val createdAtEpochMs: Long = 0L
    ) {
        fun toDomain() = TriageState(
            active = active,
            batchId = batchId,
            items = items.map { it.toDomain() },
            createdAtEpochMs = createdAtEpochMs
        )

        companion object {
            fun from(t: TriageState) = WireTriageState(
                active = t.active,
                batchId = t.batchId,
                items = t.items.map { WireTriageItem.from(it) },
                createdAtEpochMs = t.createdAtEpochMs
            )
        }
    }

    data class WireHeldApp(
        val id: String = "",
        val intentionId: String = "",
        val packageName: String = "",
        val label: String = "",
        val sortOrder: Int = 0,
        val provenance: WireProvenance = WireProvenance()
    ) {
        fun toDomain() = HeldApp(
            id = id,
            intentionId = intentionId,
            packageName = packageName,
            label = label,
            sortOrder = sortOrder,
            provenance = provenance.toDomain()
        )
        companion object {
            fun from(h: HeldApp) = WireHeldApp(
                id = h.id,
                intentionId = h.intentionId,
                packageName = h.packageName,
                label = h.label,
                sortOrder = h.sortOrder,
                provenance = WireProvenance.from(h.provenance)
            )
        }
    }


    data class WireErrandChecklistItem(
        val id: String = "",
        val label: String = "",
        val done: Boolean = false
    ) {
        fun toDomain() = com.intent.launcher.domain.ErrandChecklistItem(
            id = id,
            label = label,
            done = done
        )
        companion object {
            fun from(i: com.intent.launcher.domain.ErrandChecklistItem) =
                WireErrandChecklistItem(id = i.id, label = i.label, done = i.done)
        }
    }

    data class WireErrandTodo(
        val id: String = "",
        val title: String = "",
        val firstAction: String = "",
        val reminderNote: String? = null,
        val status: String = ErrandStatus.OPEN.name,
        val createdAtEpochMs: Long = 0L,
        val completedAtEpochMs: Long? = null,
        val provenance: WireProvenance = WireProvenance(),
        val checklistItems: List<WireErrandChecklistItem> = emptyList(),
        val reminderEnabled: Boolean = false
    ) {
        fun toDomain() = ErrandTodo(
            id = id,
            title = title,
            firstAction = firstAction,
            reminderNote = reminderNote,
            status = ErrandStatus.valueOf(status),
            createdAtEpochMs = createdAtEpochMs,
            completedAtEpochMs = completedAtEpochMs,
            provenance = provenance.toDomain(),
            checklistItems = checklistItems.map { it.toDomain() },
            reminderEnabled = reminderEnabled
        )
        companion object {
            fun from(t: ErrandTodo) = WireErrandTodo(
                id = t.id,
                title = t.title,
                firstAction = t.firstAction,
                reminderNote = t.reminderNote,
                status = t.status.name,
                createdAtEpochMs = t.createdAtEpochMs,
                completedAtEpochMs = t.completedAtEpochMs,
                provenance = WireProvenance.from(t.provenance),
                checklistItems = t.checklistItems.map(WireErrandChecklistItem::from),
                reminderEnabled = t.reminderEnabled
            )
        }
    }

    data class WireDrawerCardLayout(
        val intentionId: String = "",
        val spanColumns: Int = 1,
        val priorityBorder: Boolean = false,
        val imageKey: String = "shape"
    ) {
        fun toDomain() = DrawerCardLayout(
            intentionId = intentionId,
            spanColumns = DrawerCards.normalizeSpan(spanColumns),
            priorityBorder = priorityBorder,
            imageKey = imageKey.ifBlank { "shape" }
        )
        companion object {
            fun from(l: DrawerCardLayout) = WireDrawerCardLayout(
                intentionId = l.intentionId,
                spanColumns = DrawerCards.normalizeSpan(l.spanColumns),
                priorityBorder = l.priorityBorder,
                imageKey = l.imageKey
            )
        }
    }


    data class WireDecisionEvent(
        val id: String = "",
        val atEpochMs: Long = 0L,
        val intentionId: String = "",
        val decisionType: String = "",
        val accepted: Boolean = false,
        val reason: String = "",
        val activeQuestionId: String? = null,
        val factSourceIds: List<String> = emptyList(),
        val selectedApproachIds: List<String> = emptyList(),
        val rejectedApproachIds: List<String> = emptyList(),
        val capabilitySnapshotVersion: String = "",
        val policyVersion: String = "",
        val proposalId: String? = null,
        val toolResults: List<String> = emptyList(),
        val expectedRevision: Long = 0L,
        val resultingRevision: Long = 0L,
        val applyDurationMs: Long = 0L,
        val sourceTurnId: String? = null
    ) {
        fun toDomain() = BuilderDecisionEvent(
            id = id,
            atEpochMs = atEpochMs,
            intentionId = intentionId,
            decisionType = decisionType,
            accepted = accepted,
            reason = reason,
            activeQuestionId = activeQuestionId,
            factSourceIds = factSourceIds,
            selectedApproachIds = selectedApproachIds,
            rejectedApproachIds = rejectedApproachIds,
            capabilitySnapshotVersion = capabilitySnapshotVersion,
            policyVersion = policyVersion.ifBlank { AdaptiveBuilderState.POLICY_VERSION },
            proposalId = proposalId,
            toolResults = toolResults,
            expectedRevision = expectedRevision,
            resultingRevision = resultingRevision,
            applyDurationMs = applyDurationMs,
            sourceTurnId = sourceTurnId
        )

        companion object {
            fun from(e: BuilderDecisionEvent) = WireDecisionEvent(
                id = e.id,
                atEpochMs = e.atEpochMs,
                intentionId = e.intentionId,
                decisionType = e.decisionType,
                accepted = e.accepted,
                reason = e.reason,
                activeQuestionId = e.activeQuestionId,
                factSourceIds = e.factSourceIds,
                selectedApproachIds = e.selectedApproachIds,
                rejectedApproachIds = e.rejectedApproachIds,
                capabilitySnapshotVersion = e.capabilitySnapshotVersion,
                policyVersion = e.policyVersion,
                proposalId = e.proposalId,
                toolResults = e.toolResults,
                expectedRevision = e.expectedRevision,
                resultingRevision = e.resultingRevision,
                applyDurationMs = e.applyDurationMs,
                sourceTurnId = e.sourceTurnId
            )
        }
    }

    data class WireSnapshot(
        val schemaVersion: Int = 0,
        val intentions: List<WireIntention> = emptyList(),
        val facts: List<WireFact> = emptyList(),
        val hypotheses: List<WireHypothesis> = emptyList(),
        val proposals: List<WireProposal> = emptyList(),
        val sessions: List<WireSession> = emptyList(),
        val sessionRuns: List<WireSessionRun> = emptyList(),
        val evidence: List<WireEvidence> = emptyList(),
        val trackerEntries: List<WireTrackerEntry> = emptyList(),
        val occurrences: List<WireOccurrence> = emptyList(),
        val dailyContexts: List<WireDailyContext> = emptyList(),
        val turns: List<WireTurn> = emptyList(),
        val thoughtCaptures: List<WireThoughtCapture> = emptyList(),
        val builderDraft: WireBuilderDraft = WireBuilderDraft(),
        val triage: WireTriageState = WireTriageState(),
        val errandTodos: List<WireErrandTodo> = emptyList(),
        val openQuestions: List<WireOpenQuestion> = emptyList(),
        val approaches: List<WireApproach> = emptyList(),
        val adaptiveBuilderStates: List<WireAdaptiveBuilderState> = emptyList(),
        val activeIntentionId: String? = null,
        val dayCycle: WireDayCycle = WireDayCycle(),
        val transactions: List<WireTransaction> = emptyList(),
        val trash: List<WireTrashEntry> = emptyList(),
        val intentionOrder: List<String> = emptyList(),
        val heldApps: List<WireHeldApp> = emptyList(),
        val drawerCardLayouts: List<WireDrawerCardLayout> = emptyList(),
        val proactivityPrefs: WireProactivityPrefs = WireProactivityPrefs(),
        val triggerCooldowns: List<WireTriggerCooldown> = emptyList(),
        val queuedInterventions: List<WireQueuedIntervention> = emptyList(),
        val decisionTrail: List<WireDecisionEvent> = emptyList()
    ) {
        fun toDomain() = IntentionSnapshot(
            schemaVersion = schemaVersion,
            intentions = intentions.map { it.toDomain() },
            facts = facts.map { it.toDomain() },
            hypotheses = hypotheses.map { it.toDomain() },
            proposals = proposals.map { it.toDomain() },
            sessions = sessions.map { it.toDomain() },
            sessionRuns = sessionRuns.map { it.toDomain() },
            evidence = evidence.map { it.toDomain() },
            trackerEntries = trackerEntries.map { it.toDomain() },
            occurrences = occurrences.map { it.toDomain() },
            dailyContexts = dailyContexts.map { it.toDomain() },
            turns = turns.map { it.toDomain() },
            thoughtCaptures = thoughtCaptures.map { it.toDomain() },
            builderDraft = builderDraft.toDomain(),
            triage = triage.toDomain(),
            errandTodos = errandTodos.map { it.toDomain() },
            openQuestions = openQuestions.map { it.toDomain() },
            approaches = approaches.map { it.toDomain() },
            adaptiveBuilderStates = adaptiveBuilderStates.map { it.toDomain() },
            activeIntentionId = activeIntentionId,
            dayCycle = dayCycle.toDomain(),
            transactions = transactions.map { it.toDomain() },
            trash = trash.map { it.toDomain() },
            intentionOrder = intentionOrder,
            heldApps = heldApps.map { it.toDomain() },
            drawerCardLayouts = drawerCardLayouts.map { it.toDomain() },
            proactivityPrefs = proactivityPrefs.toDomain(),
            triggerCooldowns = triggerCooldowns.map { it.toDomain() },
            queuedInterventions = queuedInterventions.map { it.toDomain() },
            decisionTrail = decisionTrail.map { it.toDomain() }
        )

        companion object {
            fun from(s: IntentionSnapshot) = WireSnapshot(
                schemaVersion = s.schemaVersion,
                intentions = s.intentions.map(WireIntention::from),
                facts = s.facts.map(WireFact::from),
                hypotheses = s.hypotheses.map(WireHypothesis::from),
                proposals = s.proposals.map(WireProposal::from),
                sessions = s.sessions.map(WireSession::from),
                sessionRuns = s.sessionRuns.map(WireSessionRun::from),
                evidence = s.evidence.map(WireEvidence::from),
                trackerEntries = s.trackerEntries.map(WireTrackerEntry::from),
                occurrences = s.occurrences.map(WireOccurrence::from),
                dailyContexts = s.dailyContexts.map(WireDailyContext::from),
                turns = s.turns.map(WireTurn::from),
                thoughtCaptures = s.thoughtCaptures.map(WireThoughtCapture::from),
                builderDraft = WireBuilderDraft.from(s.builderDraft),
                triage = WireTriageState.from(s.triage),
                errandTodos = s.errandTodos.map(WireErrandTodo::from),
                openQuestions = s.openQuestions.map(WireOpenQuestion::from),
                approaches = s.approaches.map(WireApproach::from),
                adaptiveBuilderStates = s.adaptiveBuilderStates.map(WireAdaptiveBuilderState::from),
                activeIntentionId = s.activeIntentionId,
                dayCycle = WireDayCycle.from(s.dayCycle),
                transactions = s.transactions.map(WireTransaction::from),
                trash = s.trash.map(WireTrashEntry::from),
                intentionOrder = s.intentionOrder,
                heldApps = s.heldApps.map(WireHeldApp::from),
                drawerCardLayouts = s.drawerCardLayouts.map(WireDrawerCardLayout::from),
                proactivityPrefs = WireProactivityPrefs.from(s.proactivityPrefs),
                triggerCooldowns = s.triggerCooldowns.map(WireTriggerCooldown::from),
                queuedInterventions = s.queuedInterventions.map(WireQueuedIntervention::from),
                decisionTrail = s.decisionTrail.map(WireDecisionEvent::from)
            )
        }
    }
}
