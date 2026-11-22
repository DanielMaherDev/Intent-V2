package com.intent.launcher.engine

/**
 * Offline / on-device create-path honesty for seeds that would otherwise land as
 * “something else” without measurement or decide-by-date framing.
 *
 * OC4 — habit change with self-report count / craving check-in: baseline and
 * evening check-ins are SAID only. A checked task does not prove cessation;
 * an unreported day is not zero.
 *
 * OC5 — Instagram / YouTube Shorts / app-time under N minutes: UsageStats can
 * measure package foreground when usage access is granted; it cannot identify
 * Shorts/Reels. Never claim exact Shorts minutes.
 *
 * OC6 — connected partner metric (e.g. Chess.com / Lichess rating toward a
 * target): verify baseline from a real provider import or a manual SAID entry;
 * keep practice distinct from rating proof; never invent a live rating or games.
 *
 * Time-limited decisions (incl. OC9 job-offer / decide-whether / N-day deadline):
 * frame decide-by-date (help decide; do not auto-accept / assume yes; deadline matters).
 * Detection is generic — not title-hardcoded to “job offer”.
 *
 * OC7 — finite relocation / lease-bound move: project board with Next user action /
 * Waiting on someone; never invent viewings or a secured place.
 */
object CreatePathSeedHonesty {

    enum class Kind {
        HABIT_CHECKIN,
        USAGE_APP_TIME,
        CONNECTED_METRIC,
        TIME_LIMITED_DECISION,
        FINITE_MOVE_PROJECT
    }

    data class Honesty(
        val kind: Kind,
        /** Agent turn text appended on create (must not rely on user Other typing). */
        val agentNote: String,
        /** Optional first in-chat choice labels (prompt id [PROMPT_USAGE_MEASUREMENT]). */
        val firstChoiceOptions: List<String> = emptyList(),
        val firstChoicePromptId: String? = null,
        val firstChoiceQuestion: String? = null
    )

    const val PROMPT_USAGE_MEASUREMENT = "usage_measurement"
    const val PROMPT_HABIT_START = "habit_start"
    const val PROMPT_RATING_BASELINE = AdaptiveBuilderEngine.KEY_RATING_BASELINE
    const val PROMPT_USAGE_ACCESS = "usage_access"
    const val PROMPT_USAGE_INTERVENTION = "usage_intervention"

    const val HABIT_AGENT_NOTE =
        "We can start without choosing a quit date today. A short daily check-in can " +
            "record count and, if useful, craving intensity (self-report only). " +
            "A checked task does not prove cessation, and an unreported day is not " +
            "the same as zero — missing stays missing."

    const val USAGE_AGENT_NOTE =
        "With Android usage access, I can measure time spent in Instagram and YouTube " +
            "as apps (package foreground) when usage access is granted. That does not " +
            "tell me which videos inside YouTube were Shorts — package time ≠ Shorts " +
            "or Reels identity. Would you like to track package time automatically, " +
            "log Shorts time yourself, or use both? I will not claim exact Shorts minutes."

    const val USAGE_ACCESS_QUESTION =
        "I can open Android Usage access settings when you're ready. " +
            "I won't report automatic app time until you grant it — " +
            "and I will not silently turn on an overlay or block apps."

    const val USAGE_INTERVENTION_QUESTION =
        "When you approach your limit, how should I approach you?"

    const val CONNECTED_METRIC_AGENT_NOTE =
        "I'll check whether a connected rating import exists before treating a number " +
            "in your goal as the baseline. If nothing is imported (or the feed is stale), " +
            "use a manual baseline — I will not invent a live partner rating, games, or " +
            "lesson completion. Practice stays distinct from rating: a played game is not " +
            "proof that your rating improved."

    const val DECISION_AGENT_NOTE =
        "I'll help you make a decision by a date — not assume a particular outcome " +
            "or auto-accept. The deadline matters. When exactly is the response due, " +
            "and what matters most: pay, work, flexibility, growth, location, or something else?"

    /** @deprecated Prefer [DECISION_AGENT_NOTE]; kept for older tests/string matches. */
    const val JOB_OFFER_AGENT_NOTE = DECISION_AGENT_NOTE

    const val FINITE_MOVE_AGENT_NOTE =
        "I'll make a project proposal with connected paths: confirm lease obligations, " +
            "search, view places, decide and sign, then arrange the move. Some steps " +
            "depend on landlords or appointments, so I'll mark them as Waiting on someone " +
            "rather than tasks you can complete yourself. I will not invent viewings or " +
            "assume a property is secured. What's the latest date you need to leave, and " +
            "what budget and location requirements should shape the search?"

    val USAGE_MEASUREMENT_OPTIONS: List<String> = listOf(
        "Track package time automatically",
        "Log Shorts time myself",
        "Use both"
    )

    val HABIT_START_OPTIONS: List<String> = listOf(
        "Track a baseline",
        "Discuss approaches",
        "Both"
    )

    val USAGE_ACCESS_OPTIONS: List<String> = listOf(
        "Open Usage access when ready",
        "I'll grant later",
        "Track manually for now"
    )

    val USAGE_INTERVENTION_OPTIONS: List<String> = listOf(
        "Quiet reminder",
        "Ask me first",
        "No interruption by default"
    )

    val RATING_BASELINE_OPTIONS: List<String> = listOf(
        "Use manual baseline (e.g. 900)",
        "Refresh from connected provider",
        "Not connected yet"
    )

    /** Multiselect practice approaches — combination is selecting several, not a fourth mode. */
    val PRACTICE_PREFERENCE_OPTIONS: List<String> = listOf(
        "Playing games",
        "Reviewing games",
        "Studying"
    )

    /**
     * True when [text] already states package-vs-Shorts measurement honesty
     * (not merely naming Instagram / YouTube Shorts as products).
     */
    fun liveProseStatesUsageHonesty(text: String): Boolean {
        val t = text.lowercase()
        val access = t.contains("usage access") || t.contains("usagestats")
        val packageLimit =
            t.contains("package") ||
                t.contains("≠ shorts") ||
                t.contains("not shorts") ||
                t.contains("cannot identify shorts") ||
                t.contains("does not tell") ||
                t.contains("can't tell which") ||
                t.contains("cannot tell which")
        val refuseExact =
            t.contains("will not claim exact shorts") ||
                t.contains("won't claim exact shorts") ||
                t.contains("not claim exact shorts")
        return access && (packageLimit || refuseExact)
    }

    fun liveProseStatesJobOfferHonesty(text: String): Boolean =
        liveProseHasDecisionHonesty(text)

    fun liveProseStatesConnectedMetricHonesty(text: String): Boolean {
        val t = text.lowercase()
        val baseline =
            t.contains("baseline") || t.contains("manual") || t.contains("import")
        val noInvent =
            t.contains("invent") || t.contains("stale") ||
                t.contains("practice stays") || t.contains("not proof")
        return baseline && noInvent
    }

    fun liveProseStatesHabitCheckInHonesty(text: String): Boolean {
        val t = text.lowercase()
        val checkIn =
            t.contains("check-in") || t.contains("check in") || t.contains("baseline") ||
                t.contains("craving")
        val noFalseCessation =
            t.contains("cessation") || t.contains("unreported") ||
                t.contains("does not prove") || t.contains("not the same as zero") ||
                t.contains("missing stays missing")
        return checkIn && noFalseCessation
    }

    fun liveProseStatesFiniteMoveHonesty(text: String): Boolean {
        val t = text.lowercase()
        val waiting = t.contains("waiting on someone") || t.contains("waiting on landlord")
        val noInvent =
            t.contains("invent") || t.contains("not invent") ||
                t.contains("won't invent") || t.contains("will not invent") ||
                t.contains("secured") || t.contains("assume a property")
        return waiting && noInvent
    }

    /** Merge live model prose with required seed honesty — never drop on casual product mentions. */
    fun mergeLiveProseWithSeedHonesty(formatted: String, honesty: Honesty): String {
        val already = when (honesty.kind) {
            Kind.HABIT_CHECKIN -> liveProseStatesHabitCheckInHonesty(formatted)
            Kind.USAGE_APP_TIME -> liveProseStatesUsageHonesty(formatted)
            Kind.CONNECTED_METRIC -> liveProseStatesConnectedMetricHonesty(formatted)
            Kind.TIME_LIMITED_DECISION -> liveProseHasDecisionHonesty(formatted)
            Kind.FINITE_MOVE_PROJECT -> liveProseStatesFiniteMoveHonesty(formatted)
        }
        return if (already) formatted else "$formatted\n\n${honesty.agentNote}"
    }

    fun forDescription(text: String): Honesty? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        // Shorts / app-time honesty is primary when both patterns could match.
        if (looksLikeUsageAppTimeSeed(trimmed)) {
            return Honesty(
                kind = Kind.USAGE_APP_TIME,
                agentNote = USAGE_AGENT_NOTE,
                firstChoiceOptions = USAGE_MEASUREMENT_OPTIONS,
                firstChoicePromptId = PROMPT_USAGE_MEASUREMENT,
                firstChoiceQuestion =
                    "How should we measure Instagram / YouTube time for this intention?"
            )
        }
        if (looksLikeHabitCheckInSeed(trimmed)) {
            return Honesty(
                kind = Kind.HABIT_CHECKIN,
                agentNote = HABIT_AGENT_NOTE,
                firstChoiceOptions = HABIT_START_OPTIONS,
                firstChoicePromptId = PROMPT_HABIT_START,
                firstChoiceQuestion =
                    "Would you rather track a baseline, discuss approaches, or do both?"
            )
        }
        if (looksLikeConnectedMetricSeed(trimmed)) {
            return Honesty(
                kind = Kind.CONNECTED_METRIC,
                agentNote = CONNECTED_METRIC_AGENT_NOTE,
                firstChoiceOptions = RATING_BASELINE_OPTIONS,
                firstChoicePromptId = PROMPT_RATING_BASELINE,
                firstChoiceQuestion =
                    "How should we set the rating baseline for this intention?"
            )
        }
        if (looksLikeTimeLimitedDecisionSeed(trimmed)) {
            return Honesty(
                kind = Kind.TIME_LIMITED_DECISION,
                agentNote = DECISION_AGENT_NOTE
            )
        }
        if (IntentionBuilder.looksLikeFiniteMoveProject(trimmed.lowercase())) {
            return Honesty(
                kind = Kind.FINITE_MOVE_PROJECT,
                agentNote = FINITE_MOVE_AGENT_NOTE
            )
        }
        return null
    }

    /**
     * Habit-change seeds that need count/craving check-in honesty (not app-time).
     * Generic: nicotine / smoking cues, or reduce/quit + daily-count language —
     * not tied to one intention title.
     */
    fun looksLikeHabitCheckInSeed(text: String): Boolean {
        val t = text.lowercase()
        if (looksLikeUsageAppTimeSeed(t)) return false
        val nicotineOrSmoke =
            t.contains("smoke") || t.contains("smoking") || t.contains("cigarette") ||
                t.contains("cig ") || t.contains("vape") || t.contains("nicotine") ||
                t.contains("tobacco")
        if (nicotineOrSmoke) return true
        val reduceOrQuit =
            t.contains("quit") || t.contains("reduce") || t.contains("cut down") ||
                t.contains("cut back") || t.contains("fewer")
        val dailyCountCue =
            t.contains("a day") || t.contains("per day") || t.contains("daily") ||
                t.contains("craving") || t.contains("check-in") || t.contains("check in") ||
                Regex("""\b\d+\s*(a|/)\s*day\b""").containsMatchIn(t)
        return reduceOrQuit && dailyCountCue
    }

    fun looksLikeUsageAppTimeSeed(text: String): Boolean {
        val t = text.lowercase()
        val mentionsShortForm =
            t.contains("shorts") || t.contains("reels") ||
                t.contains("instagram") || t.contains("youtube")
        if (!mentionsShortForm) return false
        val mentionsCapOrAppTime =
            t.contains("minute") || t.contains("min ") || t.contains(" under ") ||
                t.contains("less than") || t.contains("limit") || t.contains("cut ") ||
                t.contains("reduce") || t.contains("app time") || t.contains("screen time") ||
                t.contains("usage") || t.contains("a day") || t.contains("daily") ||
                t.contains("per day")
        // Instagram/YouTube + Shorts/Reels alone is enough (OC5 seed names Shorts).
        return mentionsCapOrAppTime || t.contains("shorts") || t.contains("reels")
    }

    /**
     * Generic connected-metric seeds: partner rating apps, or rating/elo/score
     * with a numeric target range — not a single intention title hardcode.
     */
    fun looksLikeConnectedMetricSeed(text: String): Boolean {
        val t = text.lowercase()
        val partner = t.contains("chess.com") || t.contains("chesscom") ||
            t.contains("lichess") || t.contains("chess")
        val metricWord = t.contains("rating") || t.contains("elo") ||
            t.contains("rapid") || t.contains("blitz") || t.contains("bullet")
        val towardTarget = t.contains(" to ") || t.contains("→") ||
            t.contains("toward") || t.contains("towards") ||
            t.contains("from ") || t.contains("raise") || t.contains("reach")
        val hasNumber = Regex("""\b\d{3,4}\b""").containsMatchIn(t)
        if (partner && (metricWord || (towardTarget && hasNumber))) return true
        if (metricWord && towardTarget && hasNumber &&
            (t.contains("connected") || t.contains("import") || t.contains("sync") ||
                t.contains("provider") || partner)
        ) {
            return true
        }
        return false
    }

    fun looksLikeTimeLimitedDecisionSeed(text: String): Boolean =
        IntentionBuilder.looksLikeTimeLimitedDecision(text.lowercase()) ||
            looksLikeJobOfferDecideSeed(text)

    /** OC9-oriented detector kept for tests; subsumed by [looksLikeTimeLimitedDecisionSeed]. */
    fun looksLikeJobOfferDecideSeed(text: String): Boolean {
        val t = text.lowercase()
        val offer = t.contains("job offer") ||
            (t.contains("offer") && (t.contains("job") || t.contains("role")))
        val decide = t.contains("decide") || t.contains("whether to accept") ||
            t.contains("should i accept") || t.contains("accept or decline")
        val deadline = t.contains("deadline") || t.contains("due") ||
            Regex("""\b\d+\s*days?\b""").containsMatchIn(t) ||
            t.contains("ten days") || t.contains("two weeks")
        return offer && (decide || (deadline && t.contains("help")))
    }

    /** True when live/model prose already carries decide-by-date / non-assumption honesty. */
    fun liveProseHasDecisionHonesty(formatted: String): Boolean {
        val t = formatted.lowercase()
        val nonAssume = t.contains("not assume") || t.contains("won't assume") ||
            t.contains("will not assume") || t.contains("auto-accept") ||
            t.contains("not auto-accept") || t.contains("will not accept") ||
            t.contains("won't accept") || t.contains("does not choose") ||
            t.contains("won't choose") || t.contains("will not choose")
        val deadlineCue = t.contains("deadline") || t.contains("due") ||
            t.contains("decide by") || t.contains("by a date")
        return nonAssume && deadlineCue
    }
}
