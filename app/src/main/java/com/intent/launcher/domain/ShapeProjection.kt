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
        val approach = approachFor(phase, shape, sessions, facts, hypotheses)
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
                "move" -> "Move plan · landlord steps gated"
                "decision" -> "Comparing — no choice made yet"
                "cessation" -> "Baseline and support still open"
                "app_use" -> "Usage target still taking shape"
                "research" -> "Clarifying facts before research"
                "chess" -> "Baseline and practice still open"
                else -> "Open your intention"
            }
            IntentionPhase.PROPOSAL_PENDING -> when (shapeTag) {
                "marathon" -> "Your approach is taking shape"
                "business" -> "A research habit is pending"
                "move" -> "Apartment search pending approval"
                "decision" -> "A decision-review block is pending"
                "cessation" -> "An evening check-in is pending"
                "app_use" -> "A usage review is pending"
                "research" -> "A sourced checklist is pending"
                "chess" -> "An evening practice block is pending"
                else -> "Your approach is taking shape"
            }
            IntentionPhase.ACTIVE -> when (shapeTag) {
                "marathon" -> "Long-run routine in motion"
                "business" -> "Discovery habit in motion"
                "move" -> "Search rhythm in motion · lease still gated"
                "decision" -> "Decision support in motion"
                "cessation" -> "Self-report check-in in motion"
                "app_use" -> "Daily usage target in motion"
                "research" -> "Checklist research in motion"
                "chess" -> "Practice and review in motion"
                else -> "Speaking practice in motion"
            }
            IntentionPhase.PAUSED -> "Paused — facts kept"
        }

    private fun knowledgeTitle(phase: IntentionPhase, shapeTag: String?): String =
        when {
            phase == IntentionPhase.PROPOSAL_PENDING -> "What has changed"
            phase == IntentionPhase.ACTIVE && shapeTag == "business" -> "What we are learning"
            phase == IntentionPhase.ACTIVE && shapeTag == "marathon" -> "Training facts"
            phase == IntentionPhase.ACTIVE && shapeTag == "move" -> "Move constraints"
            phase == IntentionPhase.ACTIVE && shapeTag == "decision" -> "Your comparison matrix"
            phase == IntentionPhase.CLARIFYING && shapeTag == "decision" -> "Your comparison matrix"
            phase == IntentionPhase.ACTIVE && shapeTag == "cessation" -> "Baseline and reports"
            phase == IntentionPhase.ACTIVE && shapeTag == "app_use" -> "Usage facts"
            phase == IntentionPhase.ACTIVE && shapeTag == "research" -> "Confirmed facts"
            phase == IntentionPhase.ACTIVE && shapeTag == "chess" -> "Baseline and reports"
            phase == IntentionPhase.ACTIVE -> "What we know"
            else -> "What we know"
        }

    private fun approachTitle(shapeTag: String?): String =
        when (shapeTag) {
            "marathon" -> "Your training approach"
            "business" -> "Your discovery approach"
            "move" -> "Your move plan"
            "decision" -> "Your decision support"
            "cessation" -> "Your first-phase support"
            "app_use" -> "Your usage approach"
            "research" -> "Your research checklist"
            "chess" -> "Your practice approach"
            else -> "Your approach"
        }

    private fun emptyNote(shapeTag: String?): String =
        when (shapeTag) {
            "marathon" ->
                "No training plan has been invented. This intention stays open while you decide how to build base."
            "business" ->
                "No launch plan has been invented. Stay in discovery until a direction is worth testing."
            "move" ->
                "No lease or landlord confirmation has been invented. Search and budget can start; " +
                    "lease, notice, and movers stay gated until a place is confirmed."
            "decision" ->
                "No accept/decline has been chosen for you. Add SAID criteria to compare before the deadline."
            "cessation" ->
                "No quit date or smoke-free streak has been invented. Baseline and self-report come first."
            "app_use" ->
                "No blocking or auto-cap has been invented. Package time is a proxy — not Shorts or Reels identity."
            "research" ->
                "No legal requirements have been invented. Confirm citizenship, arrangement, duration, and documents first — then a sourced checklist of research questions."
            "chess" ->
                "No Chess.com scrape or fabricated games. Manual baseline and self-report come first."
            else ->
                "No tasks yet. This space is ready while you choose an approach."
        }

    private fun defaultFacts(shapeTag: String?): List<String> =
        when (shapeTag) {
            "marathon" -> listOf("Goal · finish a marathon", "Timeframe · build base first")
            "business" -> listOf("Goal · explore a business idea", "Timeframe · stay in discovery")
            "move" -> listOf(
                "Goal · Barcelona → Valencia",
                "Timeframe · about six weeks · renting · no place found"
            )
            "decision" -> listOf(
                "Goal · decide on a job offer",
                "Timeframe · about ten days",
                "Support · compare criteria — INTENT does not choose"
            )
            "cessation" -> listOf("Goal · quit smoking", "Baseline · self-reported cigarettes/day", "Quit date · none yet")
            "app_use" -> listOf(
                "Goal · Instagram & YouTube under 45 min/day",
                "Limit · package foreground ≠ Shorts/Reels"
            )
            "research" -> listOf(
                "Goal · work legally in Spain",
                "Honesty · official sources only — no invented law text"
            )
            "chess" -> listOf(
                "Goal · Chess.com rapid toward 1200",
                "Baseline · self-reported rapid rating",
                "Partner sync · not supported"
            )
            else -> listOf("Goal · use Spanish at work", "Timeframe · within a year")
        }

    private fun resourceLine(
        phase: IntentionPhase,
        shapeTag: String?,
        destination: DestinationApp?
    ): String? {
        if (phase != IntentionPhase.ACTIVE && phase != IntentionPhase.PROPOSAL_PENDING) return null
        if (destination != null) {
            return if (shapeTag == "app_use" || SessionDestinations.isAppUsePackage(destination.packageName)) {
                "Destination · ${destination.label} (+ YouTube) — package foreground only; " +
                    "not Shorts/Reels identity or auto-Complete."
            } else {
                "Destination · ${destination.label} — opening an app is not lesson completion."
            }
        }
        return when (shapeTag) {
            "marathon" -> "Destination · outdoors / your own run log — no partner completion evidence."
            "business" -> "Destination · notes & conversations — no CRM completion claimed."
            "move" ->
                "Destination · listings & notes — no invented landlord confirmation or lease."
            "decision" ->
                "Destination · your notes & criteria — INTENT never accepts or declines for you."
            "cessation" -> "Measures · SAID self-report only — no biometric or automatic smoke-free streak."
            "app_use" ->
                "Destination · Instagram / YouTube — package foreground only; not Shorts/Reels identity."
            "research" ->
                "Destination · official Spanish / EU portals — record source URL + retrieval date; not legal advice."
            "chess" -> "Measures · SAID rating and practice only — Chess.com auto-sync not supported."
            else -> null
        }
    }

    private fun approachFor(
        phase: IntentionPhase,
        shapeTag: String?,
        sessions: List<ScheduledSession>,
        facts: List<Fact> = emptyList(),
        hypotheses: List<Hypothesis> = emptyList()
    ): List<ApproachStep> {
        if (shapeTag == "move") {
            return moveDependencyPlan(phase, sessions, facts, hypotheses)
        }
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
            "decision" -> listOf(
                ApproachStep(
                    title = session?.title ?: "Decision review",
                    detail = (whenLabel?.let { "$it · approved" } ?: "Approved review block")
                ),
                ApproachStep(
                    title = "You choose accept or decline",
                    detail = "INTENT only holds your SAID criteria — never picks for you.",
                    muted = true
                )
            )
            "cessation" -> listOf(
                ApproachStep(
                    title = session?.title ?: "Evening craving check-in",
                    detail = (whenLabel?.let { "$it · approved" } ?: "Approved evening support rhythm")
                ),
                ApproachStep(
                    title = "Log cigarettes and cravings",
                    detail = "Self-reported SAID only — not a smoke-free streak chart.",
                    muted = true
                )
            )
            "app_use" -> listOf(
                ApproachStep(
                    title = session?.title ?: "Evening usage review",
                    detail = (whenLabel?.let { "$it · approved" } ?: "Approved usage-review rhythm")
                ),
                ApproachStep(
                    title = "Compare package time to 45 min",
                    detail = "Instagram/YouTube foreground only — not Shorts or Reels identity. " +
                        "Complete stays your report.",
                    muted = true
                )
            )
            "research" -> listOf(
                ApproachStep(
                    title = session?.title ?: "Sourced checklist review",
                    detail = (whenLabel?.let { "$it · approved" } ?: "Approved checklist block")
                ),
                ApproachStep(
                    title = "Research questions (not answers)",
                    detail = EuWorkSpainScenario.CHECKLIST_RESEARCH_QUESTIONS.first(),
                    muted = true
                ),
                ApproachStep(
                    title = "Honesty limit",
                    detail = "No invented statutes. Live web research not shipped — verify on official portals yourself.",
                    muted = true
                )
            )
            "chess" -> listOf(
                ApproachStep(
                    title = session?.title ?: "Evening chess practice",
                    detail = (whenLabel?.let { "$it · approved" } ?: "Approved practice / review rhythm")
                ),
                ApproachStep(
                    title = "Log rating and practice",
                    detail = "Self-reported SAID only — not a Chess.com scrape or fabricated games.",
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

    /**
     * Finite dependency plan for CASE-08. Ready steps first; landlord-gated steps muted
     * until a place is SAID-confirmed. Shown once clarifying facts/hypotheses exist
     * (or when active), so the gate is visible before a search session is approved.
     */
    private fun moveDependencyPlan(
        phase: IntentionPhase,
        sessions: List<ScheduledSession>,
        facts: List<Fact>,
        hypotheses: List<Hypothesis>
    ): List<ApproachStep> {
        val hasPlanSignal = facts.isNotEmpty() ||
            hypotheses.isNotEmpty() ||
            phase == IntentionPhase.ACTIVE ||
            phase == IntentionPhase.PROPOSAL_PENDING
        if (!hasPlanSignal) return emptyList()

        val placeConfirmed = MoveScenario.placeIsConfirmed(facts)
        val session = sessions.firstOrNull()
        val whenLabel = session?.let {
            "day ${it.dayOfWeek} · %02d:%02d".format(it.hour, it.minute)
        }
        val searchDetail = when {
            session != null -> whenLabel?.let { "$it · approved search block" } ?: "Approved search rhythm"
            phase == IntentionPhase.PROPOSAL_PENDING -> "Ready now · search proposal pending approval"
            else -> "Ready now · listings do not require a landlord"
        }
        return listOf(
            ApproachStep(
                title = session?.title ?: "Search listings",
                detail = searchDetail,
                muted = false
            ),
            ApproachStep(
                title = "Set rent budget & timing",
                detail = "Ready now · stay inside the six-week window",
                muted = false
            ),
            ApproachStep(
                title = "Sign lease",
                detail = if (placeConfirmed) {
                    "Place confirmed · review lease with landlord"
                } else {
                    "Gated · waiting on landlord / place — not invented"
                },
                muted = !placeConfirmed
            ),
            ApproachStep(
                title = "Give Barcelona notice",
                detail = if (placeConfirmed) {
                    "Depends on signed lease date"
                } else {
                    "Gated · depends on signed lease"
                },
                muted = !placeConfirmed
            ),
            ApproachStep(
                title = "Book movers / utilities",
                detail = if (placeConfirmed) {
                    "Depends on landlord move-in date"
                } else {
                    "Gated · depends on landlord move-in date"
                },
                muted = !placeConfirmed
            )
        )
    }

    private fun heroFor(phase: IntentionPhase, shapeTag: String?): IntentionHeroProjection =
        when (phase) {
            IntentionPhase.EXPLORING, IntentionPhase.CLARIFYING -> IntentionHeroProjection(
                phaseLabel = "Exploring",
                heroLabel = "The question now",
                heroTitle = when (shapeTag) {
                    "marathon" -> "How will you train?"
                    "business" -> "How will you learn?"
                    "move" -> "What depends on a landlord?"
                    "decision" -> "What will you weigh?"
                    "cessation" -> "What is your baseline?"
                    "app_use" -> "How will you hold the cap?"
                    "research" -> "What must we confirm first?"
                    "chess" -> "What is your rating baseline?"
                    else -> "How will you practise?"
                },
                heroBody = when (shapeTag) {
                    "marathon" ->
                        "We can shape this around base mileage, long runs, or a mix you can keep."
                    "business" ->
                        "We can stay in discovery, talk to customers, or add a light research habit — nothing forced."
                    "move" ->
                        "Search and budget can start now. Lease, notice, and movers stay gated until a place is confirmed — nothing invents a landlord."
                    "decision" ->
                        "Capture SAID criteria into a comparison matrix. INTENT will not accept or decline for you."
                    "cessation" ->
                        "Start with cigarettes/day and when cravings hit. No quit date is forced; measures stay self-reported."
                    "app_use" ->
                        "We can set a 45-minute daily target and review Instagram/YouTube package time — " +
                            "not Shorts or Reels identity."
                    "research" ->
                        "Citizenship, arrangement, duration, and documents — SAID facts only. No invented legal requirements."
                    "chess" ->
                        "Start with a manual rapid baseline (e.g. 900 → 1200). Chess.com sync is not available; practice stays self-reported."
                    else ->
                        "We can shape this around an app, a tutor, conversations with your agent, or a mix."
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
                    "move" -> "Add a weekly search block?"
                    "decision" -> "Add a decision-review block?"
                    "cessation" -> "Add an evening check-in?"
                    "app_use" -> "Add an evening usage review?"
                    "research" -> "Add a sourced checklist review?"
                    "chess" -> "Add evening practice?"
                    else -> "Add your session?"
                },
                heroBody = when (shapeTag) {
                    "move" ->
                        "INTENT will bring the search into your week once approved. Landlord-gated steps stay blocked."
                    "decision" ->
                        "A review block helps you weigh criteria before the deadline — not a choice made for you."
                    "chess" ->
                        "Evening practice stays optional until you approve. Rating stays manual SAID — never scraped."
                    else -> "INTENT will bring it into your week once approved."
                },
                heroCta = "Review details",
                tone = "warm"
            )
            IntentionPhase.ACTIVE -> IntentionHeroProjection(
                phaseLabel = "In motion",
                heroLabel = "Next useful step",
                heroTitle = when (shapeTag) {
                    "marathon" -> "Prepare for your next long run"
                    "business" -> "Prepare your next research block"
                    "move" -> "Prepare your next search block"
                    "decision" -> "Prepare your decision review"
                    "cessation" -> "Prepare your evening check-in"
                    "app_use" -> "Prepare your next usage review"
                    "research" -> "Prepare your next checklist review"
                    "chess" -> "Prepare your evening practice"
                    else -> "Prepare for your next session"
                },
                heroBody = when (shapeTag) {
                    "marathon" -> "Decide pace and distance you can keep — no invented mileage totals."
                    "business" -> "Pick one question to explore — no fake launch plan."
                    "move" ->
                        "Look at listings within budget — no invented lease or move-in date."
                    "decision" ->
                        "Revisit your SAID matrix. You still choose accept or decline."
                    "cessation" -> "Log cigarettes and cravings honestly — no invented smoke-free streak."
                    "app_use" ->
                        "Compare package foreground to your 45-min target — never auto-Complete from UsageStats."
                    "research" ->
                        "Pick one research question and verify it on an official portal — record source + date."
                    "chess" -> "Log rating and practice honestly — no invented Chess.com games."
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
                "move" -> "Contextual conversation · move plan"
                "decision" -> "Contextual conversation · decision support"
                "cessation" -> "Contextual conversation · cessation support"
                "app_use" -> "Contextual conversation · usage"
                "research" -> "Contextual conversation · research checklist"
                "chess" -> "Contextual conversation · chess practice"
                else -> "Contextual conversation"
            }
            IntentionPhase.PROPOSAL_PENDING -> "Your ongoing conversation"
            else -> when (shapeTag) {
                "marathon" -> "A conversation about your training"
                "business" -> "A conversation about your idea"
                "move" -> "A conversation about your move"
                "decision" -> "A conversation about your offer"
                "cessation" -> "A conversation about quitting smoking"
                "app_use" -> "A conversation about your usage target"
                "research" -> "A conversation about working legally in Spain"
                "chess" -> "A conversation about your chess rating"
                else -> "A conversation about your goal"
            }
        }

    private fun conversationHeadline(phase: IntentionPhase, shapeTag: String?): String =
        when (phase) {
            IntentionPhase.ACTIVE -> when (shapeTag) {
                "marathon" -> "Your next long run"
                "business" -> "Your next research hour"
                "move" -> "Your next apartment search"
                "decision" -> "Your decision review"
                "cessation" -> "Your evening check-in"
                "app_use" -> "Your next usage review"
                "research" -> "Your next checklist review"
                "chess" -> "Your evening practice"
                else -> "Your next session"
            }
            IntentionPhase.PROPOSAL_PENDING -> "What's changed?"
            else -> when (shapeTag) {
                "decision" -> "Compare — I will not choose for you."
                else -> "Let's find a way that fits."
            }
        }
}
