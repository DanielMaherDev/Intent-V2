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

    const val JOB_OFFER_AGENT_NOTE =
        "I'll help you make a decision by a date — not assume the answer is yes or " +
            "auto-accept the offer. The deadline matters. When exactly is the response " +
            "due, and what matters most to you: pay, work, flexibility, growth, " +
            "location, or something else?"

    val USAGE_MEASUREMENT_OPTIONS: List<String> = listOf(
        "Track package time automatically",
        "Log Shorts time myself",
        "Use both"
    )

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
