package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.domain.Fact
import com.intent.launcher.domain.FactStatus
import com.intent.launcher.domain.Intention
import com.intent.launcher.domain.TrackerEntry
import com.intent.launcher.domain.TrackerEntryStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * OC6 Visible controls — source/freshness badge, Refresh data / Use manual baseline,
 * practice approaches, and a rating chart built only from dated imported or
 * user-entered [rating_self_report] rows. Never invents games, lesson completion,
 * or a live rating from a stale / missing import.
 */
object ConnectedMetricChrome {

    const val MODULE_RATING = "rating_self_report"
    const val ACTION_REFRESH = "Refresh data"
    const val ACTION_MANUAL = "Use manual baseline"

    data class RatingPoint(
        val valueText: String,
        val eventAtEpochMs: Long,
        val provenanceLabel: String,
        val numeric: Float?,
        val dayLabel: String
    )

    data class SpaceChrome(
        val badgeLabel: String,
        val honestyNote: String,
        val refreshLabel: String = ACTION_REFRESH,
        val manualLabel: String = ACTION_MANUAL,
        /** True when a provider refresh could be attempted (not NOT_SUPPORTED). */
        val refreshCanImport: Boolean,
        val practiceApproaches: List<String>,
        val ratingPoints: List<RatingPoint>,
        /** Shown only when there are no dated rating entries — empty stays empty. */
        val emptyChartNote: String
    )

    fun forChessIntention(
        intention: Intention?,
        facts: List<Fact>,
        trackerEntries: List<TrackerEntry>,
        nowEpochMs: Long,
        registry: CapabilityRegistry = CapabilityRegistry.default,
        timeZone: TimeZone = TimeZone.getDefault()
    ): SpaceChrome? {
        if (intention == null || intention.shapeTag != "chess") return null
        val view = StaleConnectedSource.evaluateChessRating(
            snapshot = com.intent.launcher.domain.IntentionSnapshot(
                intentions = listOf(intention),
                trackerEntries = trackerEntries.filter { it.intentionId == intention.id }
            ),
            intentionId = intention.id,
            nowEpochMs = nowEpochMs,
            registry = registry
        )
        val points = ratingSeries(
            intentionId = intention.id,
            trackerEntries = trackerEntries,
            timeZone = timeZone
        )
        val approaches = practiceApproaches(facts.filter { it.intentionId == intention.id })
        return SpaceChrome(
            badgeLabel = view.label,
            honestyNote = view.honestyNote,
            refreshCanImport = view.status !=
                com.intent.launcher.capability.CapabilityStatus.NOT_SUPPORTED,
            practiceApproaches = approaches,
            ratingPoints = points,
            emptyChartNote =
                "No dated rating entries yet — chart stays empty. " +
                    "Log a manual baseline or refresh a real import; " +
                    "never invent a live rating from a stale connection."
        )
    }

    /**
     * Dated rating points from ACTIVE [rating_self_report] only.
     * Skips blank values; does not invent goal/target points or interpolate.
     */
    fun ratingSeries(
        intentionId: String,
        trackerEntries: List<TrackerEntry>,
        timeZone: TimeZone = TimeZone.getDefault()
    ): List<RatingPoint> {
        val fmt = SimpleDateFormat("d MMM", Locale.US).apply { this.timeZone = timeZone }
        return trackerEntries
            .filter {
                it.intentionId == intentionId &&
                    it.moduleId == MODULE_RATING &&
                    it.status == TrackerEntryStatus.ACTIVE &&
                    it.valueText.isNotBlank()
            }
            .sortedBy { it.eventAtEpochMs }
            .map { entry ->
                RatingPoint(
                    valueText = entry.valueText.trim(),
                    eventAtEpochMs = entry.eventAtEpochMs,
                    provenanceLabel = ProgressProof.provenanceLabel(entry.provenance.kind),
                    numeric = parseNumeric(entry.valueText),
                    dayLabel = fmt.format(Date(entry.eventAtEpochMs))
                )
            }
    }

    fun practiceApproaches(facts: List<Fact>): List<String> {
        val raw = facts.firstOrNull {
            it.key == AdaptiveBuilderEngine.KEY_PRACTICE_PREFERENCE &&
                it.status == FactStatus.ACTIVE
        }?.normalizedValue?.ifBlank { null }
            ?: facts.firstOrNull {
                it.key == AdaptiveBuilderEngine.KEY_PRACTICE_PREFERENCE &&
                    it.status == FactStatus.ACTIVE
            }?.text
            ?: return emptyList()
        return splitApproaches(raw)
    }

    fun splitApproaches(raw: String): List<String> {
        val known = CreatePathSeedHonesty.PRACTICE_PREFERENCE_OPTIONS
            .filterNot { it.equals("A combination", ignoreCase = true) }
        val lower = raw.lowercase()
        val matched = known.filter { opt ->
            lower.contains(opt.lowercase())
        }
        if (matched.isNotEmpty()) return matched
        return raw.split(Regex("\\s*(?:,|/|\\+|\\band\\b)\\s*", RegexOption.IGNORE_CASE))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    /**
     * Build chart heights 0f..1f from real numeric points only.
     * Returns empty when no parseable numbers — caller must not invent bars.
     */
    fun chartHeights(points: List<RatingPoint>): List<Float> {
        val nums = points.mapNotNull { it.numeric }
        if (nums.isEmpty()) return emptyList()
        val min = nums.minOrNull() ?: return emptyList()
        val max = nums.maxOrNull() ?: return emptyList()
        val span = (max - min).takeIf { it > 0f } ?: 1f
        return points.map { p ->
            val n = p.numeric ?: return@map 0f
            ((n - min) / span).coerceIn(0f, 1f).let { t -> 0.25f + 0.75f * t }
        }
    }

    fun parseNumeric(text: String): Float? {
        val match = Regex("""(\d+(?:\.\d+)?)""").find(text) ?: return null
        return match.groupValues[1].toFloatOrNull()
    }

    /** Honest Refresh outcome when observe_chess_rating is unavailable. */
    fun refreshHonestyMessage(registry: CapabilityRegistry = CapabilityRegistry.default): String {
        val entry = registry.get(CapabilityRegistry.ID_OBSERVE_CHESS_RATING)
        return entry?.limitNote
            ?: "Chess.com sync is not shipped — Refresh cannot invent a live rating. " +
            "Use a manual SAID baseline instead."
    }
}
