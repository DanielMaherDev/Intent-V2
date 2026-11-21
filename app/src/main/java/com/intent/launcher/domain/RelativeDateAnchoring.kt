package com.intent.launcher.domain

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Anchors relative date phrases ("in about three months") to absolute estimates.
 * Stores utterance time + approximate target; on resume, freshness checks use elapsed wall time.
 */
object RelativeDateAnchoring {

    const val DEFAULT_FRESHNESS_DAYS: Int = 21

    data class AnchoredEstimate(
        val rawUtterance: String,
        val utteredAtEpochMs: Long,
        val timeZoneId: String,
        val estimatedEpochMs: Long,
        val rangeStartEpochMs: Long,
        val rangeEndEpochMs: Long,
        val approximate: Boolean,
        val unit: String,
        val amount: Int,
        val displayMonthLabel: String
    )

    data class FreshnessCheck(
        val factId: String,
        val needsReconfirm: Boolean,
        val reason: String,
        /** Calendar month label for the anchored estimate at utterance time. */
        val originalEstimateLabel: String,
        /** How the estimate reads if restated as current relative phrasing. */
        val staleRelativeLabel: String?,
        val suggestedPrompt: String
    )

    private val RELATIVE: Pattern = Pattern.compile(
        """(?i)\b(?:in\s+)?(?:about\s+|around\s+|roughly\s+|approx(?:imately)?\s+)?""" +
            """(\d+|one|two|three|four|five|six|seven|eight|nine|ten|twelve)\s+""" +
            """(days?|weeks?|months?|years?)\b"""
    )

    private val WORD_AMOUNTS = mapOf(
        "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
        "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10,
        "twelve" to 12
    )

    fun looksRelative(utterance: String): Boolean =
        RELATIVE.matcher(utterance.trim()).find()

    /**
     * Parse a relative phrase against [utteredAtEpochMs] in [timeZone].
     * Returns null when no relative unit is recognized.
     */
    fun anchor(
        utterance: String,
        utteredAtEpochMs: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): AnchoredEstimate? {
        val matcher = RELATIVE.matcher(utterance.trim())
        if (!matcher.find()) return null
        val amountToken = matcher.group(1)?.lowercase(Locale.US) ?: return null
        val unitToken = matcher.group(2)?.lowercase(Locale.US) ?: return null
        val amount = amountToken.toIntOrNull() ?: WORD_AMOUNTS[amountToken] ?: return null
        val cal = Calendar.getInstance(timeZone)
        cal.timeInMillis = utteredAtEpochMs
        when {
            unitToken.startsWith("day") -> cal.add(Calendar.DAY_OF_YEAR, amount)
            unitToken.startsWith("week") -> cal.add(Calendar.WEEK_OF_YEAR, amount)
            unitToken.startsWith("month") -> cal.add(Calendar.MONTH, amount)
            unitToken.startsWith("year") -> cal.add(Calendar.YEAR, amount)
            else -> return null
        }
        val estimated = cal.timeInMillis
        val slackMs = when {
            unitToken.startsWith("day") -> TimeUnit.DAYS.toMillis(1)
            unitToken.startsWith("week") -> TimeUnit.DAYS.toMillis(3)
            unitToken.startsWith("month") -> TimeUnit.DAYS.toMillis(14)
            else -> TimeUnit.DAYS.toMillis(30)
        }
        val approximate = utterance.contains("about", ignoreCase = true) ||
            utterance.contains("around", ignoreCase = true) ||
            utterance.contains("roughly", ignoreCase = true) ||
            utterance.contains("approx", ignoreCase = true)
        return AnchoredEstimate(
            rawUtterance = utterance.trim(),
            utteredAtEpochMs = utteredAtEpochMs,
            timeZoneId = timeZone.id,
            estimatedEpochMs = estimated,
            rangeStartEpochMs = estimated - slackMs,
            rangeEndEpochMs = estimated + slackMs,
            approximate = approximate,
            unit = unitToken.removeSuffix("s"),
            amount = amount,
            displayMonthLabel = monthYearLabel(estimated, timeZone)
        )
    }

    /**
     * Whether a time-sensitive fact should be reconfirmed after elapsed time.
     * Does not mutate the fact or invent a new plan.
     */
    fun freshnessCheck(
        fact: Fact,
        nowEpochMs: Long,
        freshnessDays: Int = DEFAULT_FRESHNESS_DAYS,
        timeZone: TimeZone = TimeZone.getDefault()
    ): FreshnessCheck {
        val tz = fact.timeZoneId?.let { TimeZone.getTimeZone(it) } ?: timeZone
        val estimated = fact.estimatedEpochMs
        val learnedAt = fact.provenance.learnedAtEpochMs
        val label = estimated?.let { monthYearLabel(it, tz) }
            ?: fact.normalizedValue
            ?: fact.text
        if (estimated == null && !fact.approximate) {
            return FreshnessCheck(
                factId = fact.id,
                needsReconfirm = false,
                reason = "Fact has no anchored estimate.",
                originalEstimateLabel = label,
                staleRelativeLabel = null,
                suggestedPrompt = ""
            )
        }
        val ageMs = (nowEpochMs - learnedAt).coerceAtLeast(0L)
        val ageDays = TimeUnit.MILLISECONDS.toDays(ageMs)
        val remainingMs = (estimated ?: learnedAt) - nowEpochMs
        val remainingDays = TimeUnit.MILLISECONDS.toDays(remainingMs)
        val staleRelative = when {
            remainingDays >= 60 -> "in about ${remainingDays / 30} months"
            remainingDays >= 14 -> "in about ${remainingDays / 7} weeks"
            remainingDays >= 2 -> "in about $remainingDays days"
            remainingDays >= 0 -> "in the coming days"
            else -> "around now or past"
        }
        val needs = fact.approximate || ageDays >= freshnessDays.toLong() ||
            (estimated != null && remainingDays <= 45 && ageDays >= 14)
        val prompt = if (needs) {
            "Last time, you expected this around $label. Is that still about right?"
        } else {
            ""
        }
        return FreshnessCheck(
            factId = fact.id,
            needsReconfirm = needs,
            reason = if (needs) {
                "Elapsed ${ageDays}d since relative estimate; confirm against $label."
            } else {
                "Estimate still within freshness window."
            },
            originalEstimateLabel = label,
            staleRelativeLabel = if (needs) staleRelative else null,
            suggestedPrompt = prompt
        )
    }

    fun monthYearLabel(epochMs: Long, timeZone: TimeZone): String {
        val fmt = SimpleDateFormat("MMMM yyyy", Locale.US)
        fmt.timeZone = timeZone
        return fmt.format(Date(epochMs))
    }
}
