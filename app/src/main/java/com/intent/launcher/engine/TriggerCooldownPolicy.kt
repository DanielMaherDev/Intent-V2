package com.intent.launcher.engine

import com.intent.launcher.domain.IntentionSnapshot
import com.intent.launcher.domain.QueuedIntervention
import com.intent.launcher.domain.TriggerCooldown
import java.util.Calendar
import java.util.TimeZone

/**
 * Gate D proactivity policy — quiet hours, leave-me-alone, cooldown after dismiss,
 * and interruption budget. Worksheet §13 / scenario C: at most one queued prompt.
 */
object TriggerCooldownPolicy {

    const val KIND_USAGE_THRESHOLD = "usage_threshold"
    const val REASON_DISMISS = "dismiss"
    const val REASON_LEAVE_ME_ALONE = "leave_me_alone"
    const val REASON_BUDGET = "budget"
    const val REASON_NOT_NOW = "not_now"

    /** Default cooldown after dismiss / leave-me-alone (4 hours). */
    const val DEFAULT_COOLDOWN_MS: Long = 4L * 60L * 60L * 1000L

    /** Leave-me-alone default window (24 hours). */
    const val DEFAULT_LEAVE_ME_ALONE_MS: Long = 24L * 60L * 60L * 1000L

    val ACTIVE_STATUSES: Set<String> = setOf("queued", "shown")

    fun usageThresholdKey(intentionId: String): String =
        "usage_threshold:$intentionId"

    sealed class Decision {
        data object Allow : Decision()
        data class Suppress(val reason: String) : Decision()
    }

    fun evaluate(
        snapshot: IntentionSnapshot,
        triggerKey: String,
        nowEpochMs: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Decision {
        val prefs = snapshot.proactivityPrefs
        val leaveUntil = prefs.leaveMeAloneUntilEpochMs
        if (leaveUntil != null && nowEpochMs < leaveUntil) {
            return Decision.Suppress(REASON_LEAVE_ME_ALONE)
        }
        if (inQuietHours(prefs.quietHoursStartMin, prefs.quietHoursEndMin, nowEpochMs, timeZone)) {
            return Decision.Suppress("quiet_hours")
        }
        val armed = snapshot.triggerCooldowns.firstOrNull { it.key == triggerKey }
        if (armed != null && nowEpochMs < armed.armedUntilEpochMs) {
            return Decision.Suppress(armed.reason.ifBlank { REASON_DISMISS })
        }
        val active = snapshot.queuedInterventions.filter { it.status in ACTIVE_STATUSES }
        if (active.any { it.triggerKey == triggerKey }) {
            return Decision.Suppress("already_queued")
        }
        // Global at-most-one active intervention (scenario C).
        if (active.isNotEmpty()) {
            return Decision.Suppress("already_queued")
        }
        val budget = prefs.interruptionBudgetPerDay.coerceAtLeast(0)
        if (budget == 0) {
            return Decision.Suppress(REASON_BUDGET)
        }
        val dayStart = DailyCycle.startOfLocalDayEpochMs(nowEpochMs, timeZone)
        // Any intervention created today counts against the daily interruption budget.
        val usedToday = snapshot.queuedInterventions.count { job ->
            job.createdAtEpochMs >= dayStart
        }
        if (usedToday >= budget) {
            return Decision.Suppress(REASON_BUDGET)
        }
        return Decision.Allow
    }

    fun activeIntervention(snapshot: IntentionSnapshot): QueuedIntervention? =
        snapshot.queuedInterventions
            .filter { it.status in ACTIVE_STATUSES }
            .maxByOrNull { it.createdAtEpochMs }

    fun cooldownFor(
        snapshot: IntentionSnapshot,
        key: String,
        nowEpochMs: Long
    ): TriggerCooldown? {
        val c = snapshot.triggerCooldowns.firstOrNull { it.key == key } ?: return null
        return if (nowEpochMs < c.armedUntilEpochMs) c else null
    }

    fun inQuietHours(
        startMin: Int?,
        endMin: Int?,
        nowEpochMs: Long,
        timeZone: TimeZone
    ): Boolean {
        if (startMin == null || endMin == null) return false
        if (startMin == endMin) return false
        val cal = Calendar.getInstance(timeZone)
        cal.timeInMillis = nowEpochMs
        val nowMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        return if (startMin < endMin) {
            nowMin in startMin until endMin
        } else {
            // Wraps midnight (e.g. 22:00–07:00).
            nowMin >= startMin || nowMin < endMin
        }
    }
}
