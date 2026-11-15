package com.intent.launcher.domain

/**
 * Stage 5 — Adaptive UI: composition and copy vary by intention shape,
 * reading the same model. Not a separate app per goal type.
 */
data class ApproachStep(
    val title: String,
    val detail: String,
    val muted: Boolean = false
)

data class IntentionHeroProjection(
    val phaseLabel: String,
    val heroLabel: String,
    val heroTitle: String,
    val heroBody: String,
    val heroCta: String,
    /** warm | success | accent — mapped by UI. */
    val tone: String
)

data class IntentionSpaceProjection(
    val hero: IntentionHeroProjection,
    val knowledgeSectionTitle: String,
    val approachSectionTitle: String?,
    val approachSteps: List<ApproachStep>,
    /** Optional resource / destination line (e.g. Duolingo for language). */
    val resourceLine: String?,
    val emptyNote: String?,
    val defaultFacts: List<String>,
    val homeSpaceSubtitle: String,
    val conversationEyebrow: String,
    val conversationHeadline: String
)

object ShapeProjection {

    fun project(
        intention: Intention?,
        facts: List<Fact> = emptyList(),
        hypotheses: List<Hypothesis> = emptyList(),
        sessions: List<ScheduledSession> = emptyList(),
        destination: DestinationApp? = null
    ): IntentionSpaceProjection {
        val shape = intention?.shapeTag
        val phase = intention?.phase ?: IntentionPhase.EXPLORING
        val hero = heroFor(phase, shape)
        val hasKnowledge = facts.isNotEmpty() || hypotheses.isNotEmpty() || sessions.isNotEmpty()
        val approach = approachFor(phase, shape, sessions)
        val dest = destination ?: SessionDestinations.forIntention(intention)

        return IntentionSpaceProjection(
            hero = hero,
            knowledgeSectionTitle = knowledgeTitle(phase, shape),
            approachSectionTitle = if (approach.isNotEmpty()) approachTitle(shape) else null,
            approachSteps = approach,
            resourceLine = resourceLine(phase, shape, dest),
            emptyNote = if (!hasKnowledge && phase != IntentionPhase.ACTIVE) {
                emptyNote(shape)
            } else {
                null
            },
            defaultFacts = defaultFacts(shape),
            homeSpaceSubtitle = homeSpaceSubtitle(phase, shape),
            conversationEyebrow = conversationEyebrow(phase, shape),
            conversationHeadline = conversationHeadline(phase, shape)
        )
    }

    fun homeSpaceSubtitle(phase: IntentionPhase, shapeTag: String?): String =
        when (phase) {
            IntentionPhase.EXPLORING, IntentionPhase.CLARIFYING -> when (shapeTag) {
                "marathon" -> "Training still taking shape"
                "business" -> "Still in discovery"
                else -> "Open your intention"
            }
            IntentionPhase.PROPOSAL_PENDING -> when (shapeTag) {
                "marathon" -> "Your approach is taking shape"
                "business" -> "A research habit is pending"
                else -> "Your approach is taking shape"
            }
            IntentionPhase.ACTIVE -> when (shapeTag) {
                "marathon" -> "Long-run routine in motion"
                "business" -> "Discovery habit in motion"
                else -> "Speaking practice in motion"
            }
            IntentionPhase.PAUSED -> "Paused — facts kept"
        }

    private fun knowledgeTitle(phase: IntentionPhase, shapeTag: String?): String =
        when {
            phase == IntentionPhase.PROPOSAL_PENDING -> "What has changed"
            phase == IntentionPhase.ACTIVE && shapeTag == "business" -> "What we are learning"
            phase == IntentionPhase.ACTIVE && shapeTag == "marathon" -> "Training facts"
            phase == IntentionPhase.ACTIVE -> "What we know"
            else -> "What we know"
        }

    private fun approachTitle(shapeTag: String?): String =
        when (shapeTag) {
            "marathon" -> "Your training approach"
            "business" -> "Your discovery approach"
            else -> "Your approach"
        }

    private fun emptyNote(shapeTag: String?): String =
        when (shapeTag) {
            "marathon" ->
                "No training plan has been invented. This intention stays open while you decide how to build base."
            "business" ->
                "No launch plan has been invented. Stay in discovery until a direction is worth testing."
            else ->
                "No tasks have been invented. This intention stays open while you decide on an approach."
        }

    private fun defaultFacts(shapeTag: String?): List<String> =
        when (shapeTag) {
            "marathon" -> listOf("Goal · finish a marathon", "Timeframe · build base first")
            "business" -> listOf("Goal · explore a business idea", "Timeframe · stay in discovery")
            else -> listOf("Goal · use Spanish at work", "Timeframe · within a year")
        }

    private fun resourceLine(
        phase: IntentionPhase,
        shapeTag: String?,
        destination: DestinationApp?
    ): String? {
        if (phase != IntentionPhase.ACTIVE && phase != IntentionPhase.PROPOSAL_PENDING) return null
        if (destination != null) {
            return "Destination · ${destination.label} — opening an app is not lesson completion."
        }
        return when (shapeTag) {
            "marathon" -> "Destination · outdoors / your own run log — no partner completion evidence."
            "business" -> "Destination · notes & conversations — no CRM completion claimed."
            else -> null
        }
    }

    private fun approachFor(
        phase: IntentionPhase,
        shapeTag: String?,
        sessions: List<ScheduledSession>
    ): List<ApproachStep> {
        if (phase != IntentionPhase.ACTIVE) return emptyList()
        val session = sessions.firstOrNull()
        val whenLabel = session?.let {
            "day ${it.dayOfWeek} · %02d:%02d".format(it.hour, it.minute)
        }
        return when (shapeTag) {
            "marathon" -> listOf(
                ApproachStep(
                    title = session?.title ?: "Weekly long run",
                    detail = (whenLabel?.let { "$it · approved" } ?: "Approved long-run rhythm")
                ),
                ApproachStep(
                    title = "Check how the run felt",
                    detail = "Effort and recovery — self-reported, not GPS proof.",
                    muted = true
                )
            )
            "business" -> listOf(
                ApproachStep(
                    title = session?.title ?: "Research hour",
                    detail = (whenLabel?.let { "$it · approved" } ?: "Approved discovery block")
                ),
                ApproachStep(
                    title = "Capture one learning",
                    detail = "What did you learn about customers or the idea?",
                    muted = true
                )
            )
            else -> listOf(
                ApproachStep(
                    title = session?.title ?: "Weekly speaking practice",
                    detail = (whenLabel?.let { "$it · approved" } ?: "Approved tutor / practice rhythm")
                ),
                ApproachStep(
                    title = "Check in afterward",
                    detail = "What did you practise? What feels difficult?",
                    muted = true
                )
            )
        }
    }

    private fun heroFor(phase: IntentionPhase, shapeTag: String?): IntentionHeroProjection =
        when (phase) {
            IntentionPhase.EXPLORING, IntentionPhase.CLARIFYING -> IntentionHeroProjection(
                phaseLabel = "Exploring",
                heroLabel = "The question now",
                heroTitle = when (shapeTag) {
                    "marathon" -> "How will you train?"
                    "business" -> "How will you learn?"
                    else -> "How will you practise?"
                },
                heroBody = when (shapeTag) {
                    "marathon" ->
                        "We can shape this around base mileage, long runs, or a mix you can keep."
                    "business" ->
                        "We can stay in discovery, talk to customers, or add a light research habit — nothing forced."
                    else ->
                        "We can make a routine around an app, a tutor, conversations with your agent, or a mix."
                },
                heroCta = "Work this out",
                tone = "accent"
            )
            IntentionPhase.PROPOSAL_PENDING -> IntentionHeroProjection(
                phaseLabel = "Approach in progress",
                heroLabel = "Decision needed",
                heroTitle = when (shapeTag) {
                    "marathon" -> "Add your long run?"
                    "business" -> "Add a research hour?"
                    else -> "Add your session?"
                },
                heroBody = "INTENT will bring it into your week once approved.",
                heroCta = "Review details",
                tone = "warm"
            )
            IntentionPhase.ACTIVE -> IntentionHeroProjection(
                phaseLabel = "In motion",
                heroLabel = "Next useful step",
                heroTitle = when (shapeTag) {
                    "marathon" -> "Prepare for your next long run"
                    "business" -> "Prepare your next research block"
                    else -> "Prepare for your next session"
                },
                heroBody = when (shapeTag) {
                    "marathon" -> "Decide pace and distance you can keep — no invented mileage totals."
                    "business" -> "Pick one question to explore — no fake launch plan."
                    else -> "Bring one situation you want to rehearse."
                },
                heroCta = "Begin preparation",
                tone = "success"
            )
            IntentionPhase.PAUSED -> IntentionHeroProjection(
                phaseLabel = "Paused",
                heroLabel = "When you are ready",
                heroTitle = "Pick this up again?",
                heroBody = "Your facts stay. Nothing is invented while you pause.",
                heroCta = "Work this out",
                tone = "accent"
            )
        }

    private fun conversationEyebrow(phase: IntentionPhase, shapeTag: String?): String =
        when (phase) {
            IntentionPhase.ACTIVE -> when (shapeTag) {
                "marathon" -> "Contextual conversation · training"
                "business" -> "Contextual conversation · discovery"
                else -> "Contextual conversation"
            }
            IntentionPhase.PROPOSAL_PENDING -> "Your ongoing conversation"
            else -> when (shapeTag) {
                "marathon" -> "A conversation about your training"
                "business" -> "A conversation about your idea"
                else -> "A conversation about your goal"
            }
        }

    private fun conversationHeadline(phase: IntentionPhase, shapeTag: String?): String =
        when (phase) {
            IntentionPhase.ACTIVE -> when (shapeTag) {
                "marathon" -> "Your next long run"
                "business" -> "Your next research hour"
                else -> "Your next session"
            }
            IntentionPhase.PROPOSAL_PENDING -> "What's changed?"
            else -> "Let's find a way that fits."
        }
}
