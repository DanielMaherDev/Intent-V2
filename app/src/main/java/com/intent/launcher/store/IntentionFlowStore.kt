package com.intent.launcher.store

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.intent.launcher.domain.ConversationTurn
import com.intent.launcher.domain.DayCycleState
import com.intent.launcher.domain.DrawerCardLayout
import com.intent.launcher.domain.DrawerCards
import com.intent.launcher.domain.ErrandStatus
import com.intent.launcher.domain.ErrandTodo
import com.intent.launcher.domain.Evidence
import com.intent.launcher.domain.EvidenceAccuracy
import com.intent.launcher.domain.Fact
import com.intent.launcher.domain.FlowCommand
import com.intent.launcher.domain.HeldApp
import com.intent.launcher.domain.FlowTransaction
import com.intent.launcher.domain.Hypothesis
import com.intent.launcher.domain.Intention
import com.intent.launcher.domain.IntentionPhase
import com.intent.launcher.domain.IntentionSnapshot
import com.intent.launcher.domain.Proposal
import com.intent.launcher.domain.ProposalStatus
import com.intent.launcher.domain.Provenance
import com.intent.launcher.domain.ProvenanceKind
import com.intent.launcher.domain.SCHEMA_VERSION
import com.intent.launcher.domain.ScheduledSession
import com.intent.launcher.domain.SessionRun
import com.intent.launcher.domain.SessionRunStatus
import com.intent.launcher.domain.BuilderDraft
import com.intent.launcher.domain.BuilderPhase
import com.intent.launcher.domain.ThoughtCapture
import com.intent.launcher.domain.ThoughtCaptureStatus
import com.intent.launcher.domain.ThoughtDisposition
import com.intent.launcher.engine.IntentionBuilder
import com.intent.launcher.domain.TrackerEntry
import com.intent.launcher.domain.TrackerEntryStatus
import com.intent.launcher.domain.TrashEntry
import com.intent.launcher.domain.TrashKind
import com.intent.launcher.domain.TriageItem
import com.intent.launcher.domain.TriageItemStatus
import com.intent.launcher.domain.TriageState
import com.intent.launcher.engine.CommandReversibility
import com.intent.launcher.engine.IntentionHierarchy
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
        if (file.exists() && file.length() > 0L) {
            state.set(decode(file.readText()))
        } else {
            persist(state.get())
        }
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
                val turn = ConversationTurn(
                    id = command.turnId,
                    intentionId = command.intentionId,
                    role = command.role,
                    text = command.text,
                    atEpochMs = command.nowEpochMs,
                    modality = command.modality
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
                    provenance = Provenance(ProvenanceKind.INFERRED, command.nowEpochMs, "agent")
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
                migrated.copy(
                    proposals = migrated.proposals.map { if (it.id == proposal.id) approved else it },
                    sessions = migrated.sessions.filterNot { it.id == session.id } + session,
                    intentions = setPhase(
                        migrated.intentions,
                        proposal.intentionId,
                        IntentionPhase.ACTIVE,
                        command.nowEpochMs
                    )
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
                val entry = TrackerEntry(
                    id = command.entryId,
                    intentionId = command.intentionId,
                    moduleId = command.moduleId,
                    valueText = command.valueText,
                    recordedAtEpochMs = command.nowEpochMs,
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

            is FlowCommand.DismissEvidence -> {
                migrated.copy(
                    evidence = migrated.evidence.map {
                        if (it.id == command.evidenceId) {
                            it.copy(accuracy = EvidenceAccuracy.DISMISSED)
                        } else {
                            it
                        }
                    }
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
                migrated.copy(
                    facts = facts,
                    hypotheses = hypotheses,
                    dayCycle = DayCycleState(
                        localDate = command.localDate,
                        checkInAtEpochMs = command.nowEpochMs,
                        checkOutAtEpochMs = null,
                        focusIntentionId = command.intentionId,
                        focusSummary = command.focusSummary
                    ),
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
                    focusSummary = prior.focusSummary
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
                migrated.copy(
                    facts = facts,
                    hypotheses = hypotheses,
                    sessions = sessions,
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
                migrated.copy(
                    builderDraft = BuilderDraft(
                        active = true,
                        freeText = text,
                        suggestedShapeTag = command.suggestedShapeTag,
                        shapeTag = null,
                        title = command.title,
                        statement = command.statement,
                        factAnswers = emptyMap(),
                        phase = BuilderPhase.PICK_SHAPE,
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
                // Two answers is enough — avoid endless “needs more questions” loops.
                val phase = if (answers.size >= 2) {
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
                        )
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
                    )
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

            is FlowCommand.RecordExternalAction -> migrated

            is FlowCommand.UndoLastReversible -> migrated
        }
    }

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
                .map(WireThoughtCapture::from)
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
                    is FlowCommand.RecordObservedEvidence -> command.nowEpochMs
                    is FlowCommand.DismissEvidence -> command.nowEpochMs
                    is FlowCommand.ConfirmDailyCheckIn -> command.nowEpochMs
                    is FlowCommand.CompleteDailyCheckOut -> command.nowEpochMs
                    is FlowCommand.RecordTrackerEntry -> command.nowEpochMs
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
                    is FlowCommand.UndoLastReversible -> command.nowEpochMs
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
            is FlowCommand.RecordObservedEvidence -> command.nowEpochMs
            is FlowCommand.DismissEvidence -> command.nowEpochMs
            is FlowCommand.ConfirmDailyCheckIn -> command.nowEpochMs
            is FlowCommand.CompleteDailyCheckOut -> command.nowEpochMs
            is FlowCommand.RecordTrackerEntry -> command.nowEpochMs
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
            is FlowCommand.UndoLastReversible -> command.nowEpochMs
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
        val thoughtCaptures: List<WireThoughtCapture> = emptyList()
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
        val provenance: WireProvenance = WireProvenance()
    ) {
        fun toDomain() = Fact(id, intentionId, text, provenance.toDomain())
        companion object {
            fun from(f: Fact) = WireFact(f.id, f.intentionId, f.text, WireProvenance.from(f.provenance))
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
        val provenance: WireProvenance = WireProvenance()
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
            provenance = provenance.toDomain()
        )
        companion object {
            fun from(p: Proposal) = WireProposal(
                p.id, p.intentionId, p.title, p.summary,
                p.proposedDayOfWeek, p.proposedHour, p.proposedMinute,
                p.status.name, p.basedOnFactIds, WireProvenance.from(p.provenance)
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

    data class WireTurn(
        val id: String = "",
        val intentionId: String = "",
        val role: String = "",
        val text: String = "",
        val atEpochMs: Long = 0L,
        val modality: String? = null
    ) {
        fun toDomain() = ConversationTurn(id, intentionId, role, text, atEpochMs, modality)
        companion object {
            fun from(t: ConversationTurn) =
                WireTurn(t.id, t.intentionId, t.role, t.text, t.atEpochMs, t.modality)
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
        val focusSummary: String? = null
    ) {
        fun toDomain() = DayCycleState(
            localDate = localDate,
            checkInAtEpochMs = checkInAtEpochMs,
            checkOutAtEpochMs = checkOutAtEpochMs,
            focusIntentionId = focusIntentionId,
            focusSummary = focusSummary
        )

        companion object {
            fun from(d: DayCycleState) = WireDayCycle(
                localDate = d.localDate,
                checkInAtEpochMs = d.checkInAtEpochMs,
                checkOutAtEpochMs = d.checkOutAtEpochMs,
                focusIntentionId = d.focusIntentionId,
                focusSummary = d.focusSummary
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
        val status: String = TrackerEntryStatus.ACTIVE.name
    ) {
        fun toDomain() = TrackerEntry(
            id = id,
            intentionId = intentionId,
            moduleId = moduleId,
            valueText = valueText,
            recordedAtEpochMs = recordedAtEpochMs,
            provenance = provenance.toDomain(),
            status = runCatching { TrackerEntryStatus.valueOf(status) }
                .getOrDefault(TrackerEntryStatus.ACTIVE)
        )

        companion object {
            fun from(e: TrackerEntry) = WireTrackerEntry(
                id = e.id,
                intentionId = e.intentionId,
                moduleId = e.moduleId,
                valueText = e.valueText,
                recordedAtEpochMs = e.recordedAtEpochMs,
                provenance = WireProvenance.from(e.provenance),
                status = e.status.name
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
        val honestyNote: String = "Heuristic shape suggestion — no live LLM."
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
        val honestyNote: String = "Heuristic split — no live LLM."
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


    data class WireErrandTodo(
        val id: String = "",
        val title: String = "",
        val firstAction: String = "",
        val reminderNote: String? = null,
        val status: String = ErrandStatus.OPEN.name,
        val createdAtEpochMs: Long = 0L,
        val completedAtEpochMs: Long? = null,
        val provenance: WireProvenance = WireProvenance()
    ) {
        fun toDomain() = ErrandTodo(
            id = id,
            title = title,
            firstAction = firstAction,
            reminderNote = reminderNote,
            status = ErrandStatus.valueOf(status),
            createdAtEpochMs = createdAtEpochMs,
            completedAtEpochMs = completedAtEpochMs,
            provenance = provenance.toDomain()
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
                provenance = WireProvenance.from(t.provenance)
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
        val turns: List<WireTurn> = emptyList(),
        val thoughtCaptures: List<WireThoughtCapture> = emptyList(),
        val builderDraft: WireBuilderDraft = WireBuilderDraft(),
        val triage: WireTriageState = WireTriageState(),
        val errandTodos: List<WireErrandTodo> = emptyList(),
        val activeIntentionId: String? = null,
        val dayCycle: WireDayCycle = WireDayCycle(),
        val transactions: List<WireTransaction> = emptyList(),
        val trash: List<WireTrashEntry> = emptyList(),
        val intentionOrder: List<String> = emptyList(),
        val heldApps: List<WireHeldApp> = emptyList(),
        val drawerCardLayouts: List<WireDrawerCardLayout> = emptyList()
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
            turns = turns.map { it.toDomain() },
            thoughtCaptures = thoughtCaptures.map { it.toDomain() },
            builderDraft = builderDraft.toDomain(),
            triage = triage.toDomain(),
            errandTodos = errandTodos.map { it.toDomain() },
            activeIntentionId = activeIntentionId,
            dayCycle = dayCycle.toDomain(),
            transactions = transactions.map { it.toDomain() },
            trash = trash.map { it.toDomain() },
            intentionOrder = intentionOrder,
            heldApps = heldApps.map { it.toDomain() },
            drawerCardLayouts = drawerCardLayouts.map { it.toDomain() }
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
                turns = s.turns.map(WireTurn::from),
                thoughtCaptures = s.thoughtCaptures.map(WireThoughtCapture::from),
                builderDraft = WireBuilderDraft.from(s.builderDraft),
                triage = WireTriageState.from(s.triage),
                errandTodos = s.errandTodos.map(WireErrandTodo::from),
                activeIntentionId = s.activeIntentionId,
                dayCycle = WireDayCycle.from(s.dayCycle),
                transactions = s.transactions.map(WireTransaction::from),
                trash = s.trash.map(WireTrashEntry::from),
                intentionOrder = s.intentionOrder,
                heldApps = s.heldApps.map(WireHeldApp::from),
                drawerCardLayouts = s.drawerCardLayouts.map(WireDrawerCardLayout::from)
            )
        }
    }
}
