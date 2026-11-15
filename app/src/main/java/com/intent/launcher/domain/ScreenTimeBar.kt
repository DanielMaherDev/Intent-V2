package com.intent.launcher.domain

/**
 * OS-136 — configurable top screen-time / focus bar.
 *
 * Independent of the floating agent (position, overlay grant, bubble state).
 * Distinct from [AttentionStrip] secondary (session elapsed XOR session OBSERVED).
 * Never invents minutes; denied UsageStats → honesty + grant path, not fake totals.
 */
object ScreenTimeBar {

    sealed class Content {
        /** User hid the bar in Settings. */
        data object Hidden : Content()

        /** Preference on, but UsageStats not granted — show honesty, offer grant. */
        data class NeedsPermission(
            val text: String,
            val contentDescription: String,
            val canOpenSettings: Boolean
        ) : Content()

        /** Day-window foreground for the focus destination package. */
        data class FocusPackageToday(
            val text: String,
            val contentDescription: String,
            val packageLabel: String,
            val foregroundMs: Long
        ) : Content()

        /** Day-window sum of package foreground (device-ish screen-time proxy). */
        data class DeviceForegroundToday(
            val text: String,
            val contentDescription: String,
            val foregroundMs: Long
        ) : Content()

        /** Granted, but no honest minutes available yet. */
        data class GrantedNoData(
            val text: String,
            val contentDescription: String
        ) : Content()
    }

    /**
     * @param visiblePref user chrome preference (Settings → Home chrome)
     * @param usageGranted UsageStats AppOps granted
     * @param focusPackageLabel destination / held focus app label, when known
     * @param focusForegroundMs day-window package foreground for that focus app
     * @param deviceForegroundMs day-window sum across packages (nullable)
     * @param canOpenSettings whether a settings deep link is available
     */
    fun resolve(
        visiblePref: Boolean,
        usageGranted: Boolean,
        focusPackageLabel: String?,
        focusForegroundMs: Long?,
        deviceForegroundMs: Long?,
        canOpenSettings: Boolean
    ): Content {
        if (!visiblePref) return Content.Hidden

        if (!usageGranted) {
            return Content.NeedsPermission(
                text = "Screen time · needs usage access",
                contentDescription =
                    "Screen-time bar needs UsageStats. Package foreground only — " +
                        "not lesson completion. Grant usage access in system settings.",
                canOpenSettings = canOpenSettings
            )
        }

        val label = focusPackageLabel?.trim().orEmpty()
        if (label.isNotEmpty() && focusForegroundMs != null) {
            val formatted = SessionElapsed.formatMs(focusForegroundMs.coerceAtLeast(0L))
            return Content.FocusPackageToday(
                text = "Focus · $label · ~$formatted today",
                contentDescription =
                    "$label package foreground about $formatted today — " +
                        "not lesson completion. Independent of the floating agent.",
                packageLabel = label,
                foregroundMs = focusForegroundMs.coerceAtLeast(0L)
            )
        }

        if (deviceForegroundMs != null) {
            val formatted = SessionElapsed.formatMs(deviceForegroundMs.coerceAtLeast(0L))
            return Content.DeviceForegroundToday(
                text = "Today · ~$formatted package foreground",
                contentDescription =
                    "About $formatted total package foreground today — " +
                        "not lesson completion. Independent of the floating agent.",
                foregroundMs = deviceForegroundMs.coerceAtLeast(0L)
            )
        }

        return Content.GrantedNoData(
            text = "Usage access on · no focus minutes yet",
            contentDescription =
                "UsageStats granted, but no package foreground minutes are available yet. " +
                    "Not lesson completion."
        )
    }
}
