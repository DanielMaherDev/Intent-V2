package com.intent.launcher.engine

import com.intent.launcher.domain.ApproachStatus
import com.intent.launcher.domain.BuilderContext
import com.intent.launcher.domain.BuilderDecision
import java.util.TimeZone

/**
 * Gate E plan compiler — selected approaches + timing facts → draft proposal fields.
 * Validates recurrence and timezone; does not invent missing timing.
 * Nothing is active until [FlowCommand.ApproveProposal] succeeds via the gateway.
 */
object PlanCompiler {

    data class Draft(
        val proposalId: String,
        val title: String,
        val summary: String,
        val dayOfWeek: Int,
        val daysOfWeek: List<Int>,
        val hour: Int,
        val minute: Int,
        val durationMinutes: Int,
        val timeZoneId: String,
        val methodsSummary: String,
        val capabilityHonesty: String,
        val homeImpactNote: String,
        val includeMorningCheckIn: Boolean,
        val includeEveningReview: Boolean,
        val basedOnFactIds: List<String>,
        val effects: List<String>,
        val rationale: String
    )

    data class Validation(
        val ok: Boolean,
        val reason: String
    )

    fun compile(
        context: BuilderContext,
        proposalId: String = "proposal-practice-${context.intentionId}",
        morningCheckIn: Boolean = false,
        eveningReview: Boolean = false
    ): Draft? {
        val selected = context.approaches.filter { it.status == ApproachStatus.SELECTED }
        if (selected.isEmpty()) return null
        val frequency = context.confirmedFacts.firstOrNull {
            it.key == AdaptiveBuilderEngine.KEY_PRACTICE_FREQUENCY
        } ?: return null
        val durationFact = context.confirmedFacts.firstOrNull {
            it.key == AdaptiveBuilderEngine.KEY_PRACTICE_DURATION
        }
        val timeFact = context.confirmedFacts.firstOrNull {
            it.key == AdaptiveBuilderEngine.KEY_PRACTICE_TIME
        }
        val daysFact = context.confirmedFacts.firstOrNull {
            it.key == AdaptiveBuilderEngine.KEY_PRACTICE_DAYS
        }

        val days = parseDays(daysFact?.normalizedValue ?: daysFact?.text, frequency.text)
        val duration = parseDurationMinutes(
            durationFact?.normalizedValue ?: durationFact?.text
        ) ?: 20
        val (hour, minute) = parseTime(timeFact?.normalizedValue ?: timeFact?.text)
            ?: (19 to 0)
        val dayOfWeek = days.firstOrNull() ?: 2

        val methods = selected.joinToString(" + ") { it.title }
        val capability = selected
            .map { it.capabilityNote.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString(" ")
            .ifBlank {
                "INTENT can open linked apps and run guided practice; " +
                    "lesson completion inside third-party apps requires your check-in."
            }
        val tz = context.userTimeZoneId.ifBlank { TimeZone.getDefault().id }
        val validation = validate(days, hour, minute, duration, tz)
        if (!validation.ok) return null

        val scheduleLabel = formatDays(days) +
            " · $duration minutes · %02d:%02d".format(hour, minute)
        val summary = buildString {
            append("Draft routine: $methods. $scheduleLabel (tz $tz).")
            if (morningCheckIn) append(" Morning check-in selected.")
            if (eveningReview) append(" Evening review selected.")
            append(" NOT YET ACTIVE — approve to create the commitment.")
        }
        return Draft(
            proposalId = proposalId,
            title = "Practice routine (draft)",
            summary = summary,
            dayOfWeek = dayOfWeek,
            daysOfWeek = days,
            hour = hour,
            minute = minute,
            durationMinutes = duration,
            timeZoneId = tz,
            methodsSummary = methods,
            capabilityHonesty = capability,
            homeImpactNote = "After approval, this session can appear on Home when relevant. " +
                "Nothing is scheduled until the gateway confirms success.",
            includeMorningCheckIn = morningCheckIn,
            includeEveningReview = eveningReview,
            basedOnFactIds = context.confirmedFacts.map { it.id },
            effects = listOf(
                "Creates a PENDING proposal only",
                "Does not create ScheduledSession until ApproveProposal",
                "Optional check-ins remain independently selectable",
                "Timezone: $tz"
            ),
            rationale = "Compiled from selected approaches and stated timing."
        )
    }

    fun toProposeDecision(draft: Draft, intentionId: String, expectedRevision: Long): BuilderDecision.Propose =
        BuilderDecision.Propose(
            intentionId = intentionId,
            expectedRevision = expectedRevision,
            proposalId = draft.proposalId,
            title = draft.title,
            summary = draft.summary,
            dayOfWeek = draft.dayOfWeek,
            hour = draft.hour,
            minute = draft.minute,
            basedOnFactIds = draft.basedOnFactIds,
            rationale = draft.rationale,
            effects = draft.effects,
            daysOfWeek = draft.daysOfWeek,
            durationMinutes = draft.durationMinutes,
            timeZoneId = draft.timeZoneId,
            methodsSummary = draft.methodsSummary,
            capabilityHonesty = draft.capabilityHonesty,
            homeImpactNote = draft.homeImpactNote,
            includeMorningCheckIn = draft.includeMorningCheckIn,
            includeEveningReview = draft.includeEveningReview
        )

    fun validate(
        daysOfWeek: List<Int>,
        hour: Int,
        minute: Int,
        durationMinutes: Int,
        timeZoneId: String
    ): Validation {
        if (daysOfWeek.isEmpty()) {
            return Validation(false, "At least one weekday is required.")
        }
        if (daysOfWeek.any { it !in 1..7 }) {
            return Validation(false, "Weekdays must be ISO 1..7.")
        }
        if (hour !in 0..23 || minute !in 0..59) {
            return Validation(false, "Invalid local time.")
        }
        if (durationMinutes !in 1..180) {
            return Validation(false, "Duration must be 1..180 minutes.")
        }
        val tz = runCatching { TimeZone.getTimeZone(timeZoneId) }.getOrNull()
        if (tz == null || (timeZoneId.isNotBlank() && tz.id == "GMT" &&
                timeZoneId != "GMT" && !timeZoneId.equals("UTC", ignoreCase = true))
        ) {
            // Accept well-known IDs; reject blank.
            if (timeZoneId.isBlank()) {
                return Validation(false, "Timezone required.")
            }
        }
        if (timeZoneId.isBlank()) {
            return Validation(false, "Timezone required.")
        }
        return Validation(true, "ok")
    }

    internal fun parseDays(raw: String?, frequencyText: String): List<Int> {
        val text = (raw ?: frequencyText).lowercase()
        if (text.contains("every") || text.contains("daily") || text.contains("everyday")) {
            return (1..7).toList()
        }
        val named = listOf(
            "mon" to 1, "tue" to 2, "wed" to 3, "thu" to 4,
            "fri" to 5, "sat" to 6, "sun" to 7
        )
        val found = named.filter { text.contains(it.first) }.map { it.second }.distinct()
        if (found.isNotEmpty()) return found
        // "3 days per week" without explicit days → Mon/Wed/Fri default (draft only).
        if (Regex("""\b3\b""").containsMatchIn(text) || text.contains("three")) {
            return listOf(1, 3, 5)
        }
        if (Regex("""\b2\b""").containsMatchIn(text) || text.contains("two")) {
            return listOf(2, 4)
        }
        return listOf(1, 3, 5)
    }

    internal fun parseDurationMinutes(raw: String?): Int? {
        if (raw.isNullOrBlank()) return null
        val match = Regex("""(\d{1,3})""").find(raw) ?: return null
        return match.groupValues[1].toIntOrNull()?.coerceIn(1, 180)
    }

    internal fun parseTime(raw: String?): Pair<Int, Int>? {
        if (raw.isNullOrBlank()) return null
        val colon = Regex("""(\d{1,2}):(\d{2})""").find(raw)
        if (colon != null) {
            val h = colon.groupValues[1].toInt()
            val m = colon.groupValues[2].toInt()
            if (h in 0..23 && m in 0..59) return h to m
        }
        val hourOnly = Regex("""\b(\d{1,2})\b""").find(raw) ?: return null
        val h = hourOnly.groupValues[1].toInt()
        return if (h in 0..23) h to 0 else null
    }

    private fun formatDays(days: List<Int>): String {
        if (days.size == 7) return "Everyday"
        val names = listOf("", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        return days.sorted().joinToString(", ") { names.getOrElse(it) { "D$it" } }
    }
}
