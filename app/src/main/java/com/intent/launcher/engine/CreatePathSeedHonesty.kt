package com.intent.launcher.engine

/**
 * Offline / on-device create-path honesty for seeds that would otherwise land as
 * “something else” without measurement or decide-by-date framing.
 *
 * OC5 — Instagram / YouTube Shorts / app-time under N minutes: UsageStats can
 * measure package foreground when usage access is granted; it cannot identify
 * Shorts/Reels. Never claim exact Shorts minutes.
 *
 * OC9 — job-offer / decide-whether-to-accept / N-day deadline: frame decide-by-date
 * (help decide; do not auto-accept; deadline matters).
 */
object CreatePathSeedHonesty {

    enum class Kind { USAGE_APP_TIME, JOB_OFFER_DECIDE }

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

    const val JOB_OFFER_AGENT_NOTE =
        "I'll help you make a decision by a date — not assume the answer is yes or " +
            "auto-accept the offer. The deadline matters. When exactly is the response " +
            "due, and what matters most to you: pay, work, flexibility, growth, " +
            "location, or something else?"

    const val PROMPT_USAGE_ACCESS = "usage_access"
    const val PROMPT_USAGE_INTERVENTION = "usage_intervention"

    val USAGE_MEASUREMENT_OPTIONS: List<String> = listOf(
        "Track package time automatically",
        "Log Shorts time myself",
        "Use both"
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

    fun liveProseStatesJobOfferHonesty(text: String): Boolean {
        val t = text.lowercase()
        val decideFrame =
            t.contains("decide") || t.contains("decision") || t.contains("deadline")
        val notAuto =
            t.contains("auto-accept") || t.contains("not assume") ||
                t.contains("will not accept") || t.contains("won't accept") ||
                t.contains("not auto")
        return decideFrame && notAuto
    }

    /** Merge live model prose with required seed honesty — never drop on casual product mentions. */
    fun mergeLiveProseWithSeedHonesty(formatted: String, honesty: Honesty): String {
        val already = when (honesty.kind) {
            Kind.USAGE_APP_TIME -> liveProseStatesUsageHonesty(formatted)
            Kind.JOB_OFFER_DECIDE -> liveProseStatesJobOfferHonesty(formatted)
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
        if (looksLikeJobOfferDecideSeed(trimmed)) {
            return Honesty(
                kind = Kind.JOB_OFFER_DECIDE,
                agentNote = JOB_OFFER_AGENT_NOTE
            )
        }
        return null
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
}
