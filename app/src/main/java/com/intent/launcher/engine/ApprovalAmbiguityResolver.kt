package com.intent.launcher.engine

import com.intent.launcher.domain.IntentionSnapshot
import com.intent.launcher.domain.Proposal
import com.intent.launcher.domain.ProposalStatus

/**
 * §17 case 33 — spoken approval ambiguity (“Yes” while two proposals are pending).
 * Ask which proposal — never approve both or an arbitrary one.
 */
object ApprovalAmbiguityResolver {

    data class Candidate(
        val proposalId: String,
        val title: String,
        val intentionId: String,
        val summary: String
    )

    sealed class Outcome {
        data class Single(
            val proposalId: String
        ) : Outcome()

        data class Ambiguous(
            val candidates: List<Candidate>,
            val message: String
        ) : Outcome()

        data class None(
            val message: String
        ) : Outcome()
    }

    fun looksLikeBareApproval(utterance: String): Boolean {
        val t = utterance.trim().lowercase()
        return t == "yes" ||
            t == "yeah" ||
            t == "yep" ||
            t == "approve" ||
            t == "ok" ||
            t == "okay" ||
            t == "sure"
    }

    fun pendingProposals(snapshot: IntentionSnapshot): List<Proposal> =
        snapshot.proposals.filter { it.status == ProposalStatus.PENDING }

    fun resolve(
        snapshot: IntentionSnapshot,
        utterance: String,
        explicitProposalId: String? = null
    ): Outcome {
        if (!explicitProposalId.isNullOrBlank()) {
            val hit = snapshot.proposals.firstOrNull {
                it.id == explicitProposalId && it.status == ProposalStatus.PENDING
            }
            return if (hit != null) {
                Outcome.Single(hit.id)
            } else {
                Outcome.None("No pending proposal $explicitProposalId")
            }
        }
        val pending = pendingProposals(snapshot)
        return when {
            pending.isEmpty() ->
                Outcome.None("No pending proposals to approve")
            pending.size == 1 ->
                Outcome.Single(pending.first().id)
            looksLikeBareApproval(utterance) || utterance.isBlank() ->
                Outcome.Ambiguous(
                    candidates = pending.map {
                        Candidate(
                            proposalId = it.id,
                            title = it.title,
                            intentionId = it.intentionId,
                            summary = it.summary
                        )
                    },
                    message = "“Yes” is ambiguous — ${pending.size} proposals are pending. " +
                        "Which one should I approve? I will not approve both or pick arbitrarily."
                )
            else -> {
                val matched = pending.filter { p ->
                    utterance.contains(p.title, ignoreCase = true) ||
                        utterance.contains(p.id, ignoreCase = true)
                }
                when {
                    matched.size == 1 -> Outcome.Single(matched.first().id)
                    matched.isEmpty() -> Outcome.Ambiguous(
                        candidates = pending.map {
                            Candidate(it.id, it.title, it.intentionId, it.summary)
                        },
                        message = "Which pending proposal did you mean? I will not pick arbitrarily."
                    )
                    else -> Outcome.Ambiguous(
                        candidates = matched.map {
                            Candidate(it.id, it.title, it.intentionId, it.summary)
                        },
                        message = "Several proposals still match — pick one exactly."
                    )
                }
            }
        }
    }
}
