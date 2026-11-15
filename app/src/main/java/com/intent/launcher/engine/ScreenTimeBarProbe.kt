package com.intent.launcher.engine

import com.intent.launcher.domain.DestinationApp
import com.intent.launcher.domain.ScreenTimeBar
import java.util.TimeZone

/**
 * OS-136 — build top screen-time bar content from UsageStats + chrome prefs.
 * Never mutates session state; never claims lesson completion.
 */
object ScreenTimeBarProbe {

    data class Observation(
        val content: ScreenTimeBar.Content,
        val permissionGranted: Boolean,
        val canOpenSettings: Boolean,
        val settingsAction: String = UsageStatsProbe.SETTINGS_ACTION
    )

    fun observe(
        access: UsageStatsAccess,
        prefs: HomeChromePrefs,
        focusDestination: DestinationApp?,
        nowEpochMs: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Observation {
        val granted = access.hasPermission()
        val dayStart = DailyCycle.startOfLocalDayEpochMs(nowEpochMs, timeZone)

        var focusMs: Long? = null
        val focusLabel = focusDestination?.label
        if (granted && focusDestination != null) {
            focusMs = access.foregroundMs(
                packageName = focusDestination.packageName,
                startEpochMs = dayStart,
                endEpochMs = nowEpochMs
            )
        }

        val deviceMs = if (granted) {
            access.totalForegroundMs(dayStart, nowEpochMs)
        } else {
            null
        }

        val content = ScreenTimeBar.resolve(
            visiblePref = prefs.isScreenTimeBarVisible(),
            usageGranted = granted,
            focusPackageLabel = focusLabel,
            focusForegroundMs = focusMs,
            deviceForegroundMs = deviceMs,
            canOpenSettings = !granted
        )

        return Observation(
            content = content,
            permissionGranted = granted,
            canOpenSettings = !granted && content is ScreenTimeBar.Content.NeedsPermission
        )
    }
}
