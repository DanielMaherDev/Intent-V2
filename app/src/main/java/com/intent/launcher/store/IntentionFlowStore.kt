package com.intent.launcher.store

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.intent.launcher.domain.ConversationTurn
import com.intent.launcher.domain.Fact
import com.intent.launcher.domain.FlowCommand
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
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/**
 * Single command path into the persistent intention model.
 * Agents propose FlowCommands; they never rewrite the file directly.
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
        val next = reduce(state.get(), command)
        state.set(next)
        persist(next)
        return next
    }

    fun reduce(current: IntentionSnapshot, command: FlowCommand): IntentionSnapshot {
        val migrated = migrate(current)
        return when (command) {
            is FlowCommand.SeedExploring -> {
                val intention = Intention(
                    id = command.intentionId,
                    title = command.title,
                    statement = command.statement,
                    phase = IntentionPhase.EXPLORING,
                    createdAtEpochMs = command.nowEpochMs,
                    updatedAtEpochMs = command.nowEpochMs,
                    shapeTag = command.shapeTag
                )
                migrated.copy(
                    intentions = migrated.intentions.filterNot { it.id == intention.id } + intention,
                    activeIntentionId = command.intentionId
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
                    atEpochMs = command.nowEpochMs
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
                // Do not defer while a run for this session is already in progress.
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
                    startedAtEpochMs = command.nowEpochMs
                )
                migrated.copy(
                    sessionRuns = migrated.sessionRuns.filterNot { it.id == run.id } + run,
                    // Starting clears a pending deferral for this session.
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

            is FlowCommand.SetActiveIntention -> {
                migrated.copy(activeIntentionId = command.intentionId)
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
        }
    }

    private fun endSessionRun(
        migrated: IntentionSnapshot,
        runId: String,
        endStatus: SessionRunStatus,
        nowEpochMs: Long
    ): IntentionSnapshot {
        val run = migrated.sessionRuns.firstOrNull { it.id == runId } ?: return migrated
        if (run.status != SessionRunStatus.IN_PROGRESS) return migrated
        return migrated.copy(
            sessionRuns = migrated.sessionRuns.map {
                if (it.id == runId) {
                    it.copy(status = endStatus, endedAtEpochMs = nowEpochMs)
                } else it
            },
            intentions = touch(migrated.intentions, run.intentionId, nowEpochMs)
        )
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

    fun decode(raw: String): IntentionSnapshot {
        val wire = gson.fromJson(raw, WireSnapshot::class.java) ?: WireSnapshot()
        return migrate(wire.toDomain())
    }

    /** Gson-friendly wire format (enums as strings). */
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
        val shapeTag: String = "other"
    )

    data class WireFact(
        val id: String = "",
        val intentionId: String = "",
        val text: String = "",
        val provenance: WireProvenance = WireProvenance()
    )

    data class WireHypothesis(
        val id: String = "",
        val intentionId: String = "",
        val text: String = "",
        val provenance: WireProvenance = WireProvenance()
    )

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
    )

    data class WireSession(
        val id: String = "",
        val intentionId: String = "",
        val title: String = "",
        val dayOfWeek: Int = 1,
        val hour: Int = 0,
        val minute: Int = 0,
        val deferredUntilEpochMs: Long? = null
    )

    data class WireSessionRun(
        val id: String = "",
        val sessionId: String = "",
        val intentionId: String = "",
        val title: String = "",
        val status: String = SessionRunStatus.IN_PROGRESS.name,
        val startedAtEpochMs: Long = 0L,
        val endedAtEpochMs: Long? = null
    )

    data class WireTurn(
        val id: String = "",
        val intentionId: String = "",
        val role: String = "",
        val text: String = "",
        val atEpochMs: Long = 0L
    )

    data class WireSnapshot(
        val schemaVersion: Int = 0,
        val intentions: List<WireIntention> = emptyList(),
        val facts: List<WireFact> = emptyList(),
        val hypotheses: List<WireHypothesis> = emptyList(),
        val proposals: List<WireProposal> = emptyList(),
        val sessions: List<WireSession> = emptyList(),
        val sessionRuns: List<WireSessionRun> = emptyList(),
        val turns: List<WireTurn> = emptyList(),
        val activeIntentionId: String? = null
    ) {
        fun toDomain() = IntentionSnapshot(
            schemaVersion = schemaVersion,
            intentions = intentions.map {
                Intention(
                    id = it.id,
                    title = it.title,
                    statement = it.statement,
                    phase = IntentionPhase.valueOf(it.phase),
                    createdAtEpochMs = it.createdAtEpochMs,
                    updatedAtEpochMs = it.updatedAtEpochMs,
                    shapeTag = it.shapeTag
                )
            },
            facts = facts.map {
                Fact(it.id, it.intentionId, it.text, it.provenance.toDomain())
            },
            hypotheses = hypotheses.map {
                Hypothesis(it.id, it.intentionId, it.text, it.provenance.toDomain())
            },
            proposals = proposals.map {
                Proposal(
                    id = it.id,
                    intentionId = it.intentionId,
                    title = it.title,
                    summary = it.summary,
                    proposedDayOfWeek = it.proposedDayOfWeek,
                    proposedHour = it.proposedHour,
                    proposedMinute = it.proposedMinute,
                    status = ProposalStatus.valueOf(it.status),
                    basedOnFactIds = it.basedOnFactIds,
                    provenance = it.provenance.toDomain()
                )
            },
            sessions = sessions.map {
                ScheduledSession(
                    id = it.id,
                    intentionId = it.intentionId,
                    title = it.title,
                    dayOfWeek = it.dayOfWeek,
                    hour = it.hour,
                    minute = it.minute,
                    deferredUntilEpochMs = it.deferredUntilEpochMs
                )
            },
            sessionRuns = sessionRuns.map {
                SessionRun(
                    id = it.id,
                    sessionId = it.sessionId,
                    intentionId = it.intentionId,
                    title = it.title,
                    status = SessionRunStatus.valueOf(it.status),
                    startedAtEpochMs = it.startedAtEpochMs,
                    endedAtEpochMs = it.endedAtEpochMs
                )
            },
            turns = turns.map {
                ConversationTurn(it.id, it.intentionId, it.role, it.text, it.atEpochMs)
            },
            activeIntentionId = activeIntentionId
        )

        companion object {
            fun from(s: IntentionSnapshot) = WireSnapshot(
                schemaVersion = s.schemaVersion,
                intentions = s.intentions.map {
                    WireIntention(
                        it.id, it.title, it.statement, it.phase.name,
                        it.createdAtEpochMs, it.updatedAtEpochMs, it.shapeTag
                    )
                },
                facts = s.facts.map {
                    WireFact(it.id, it.intentionId, it.text, WireProvenance.from(it.provenance))
                },
                hypotheses = s.hypotheses.map {
                    WireHypothesis(it.id, it.intentionId, it.text, WireProvenance.from(it.provenance))
                },
                proposals = s.proposals.map {
                    WireProposal(
                        it.id, it.intentionId, it.title, it.summary,
                        it.proposedDayOfWeek, it.proposedHour, it.proposedMinute,
                        it.status.name, it.basedOnFactIds, WireProvenance.from(it.provenance)
                    )
                },
                sessions = s.sessions.map {
                    WireSession(
                        it.id, it.intentionId, it.title, it.dayOfWeek, it.hour, it.minute,
                        it.deferredUntilEpochMs
                    )
                },
                sessionRuns = s.sessionRuns.map {
                    WireSessionRun(
                        it.id, it.sessionId, it.intentionId, it.title,
                        it.status.name, it.startedAtEpochMs, it.endedAtEpochMs
                    )
                },
                turns = s.turns.map {
                    WireTurn(it.id, it.intentionId, it.role, it.text, it.atEpochMs)
                },
                activeIntentionId = s.activeIntentionId
            )
        }
    }
}
