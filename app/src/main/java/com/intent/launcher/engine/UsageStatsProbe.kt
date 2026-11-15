package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityEntry
import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.capability.CapabilityStatus
import com.intent.launcher.domain.DestinationApp
import com.intent.launcher.domain.SessionElapsed

/**
 * Stage 6 — UsageStats honesty: live permission probe + package-level foreground.
 * Never treats app use as lesson / partner completion.
 */
object UsageStatsProbe {

    const val SETTINGS_ACTION = "android.settings.USAGE_ACCESS_SETTINGS"

    data class Observation(
        val permissionGranted: Boolean,
        val packageName: String?,
        val appLabel: String?,
        val foregroundMs: Long?,
        /** Honest UI note — always denies lesson-completion claims. */
        val note: String,
        val canOpenSettings: Boolean,
        val settingsAction: String = SETTINGS_ACTION
    )

    fun liveRegistry(
        access: UsageStatsAccess,
        base: List<CapabilityEntry> = CapabilityRegistry.DEFAULT_ENTRIES
    ): CapabilityRegistry {
        val granted = access.hasPermission()
        val entries = base.map { entry ->
            if (entry.id != CapabilityRegistry.ID_USAGE_STATS) entry
            else if (granted) {
                entry.copy(
                    status = CapabilityStatus.AVAILABLE,
                    limitNote = "Package-level foreground time only — not lesson or video identity. " +
                        "App use is not lesson completion."
                )
            } else {
                entry.copy(
                    status = CapabilityStatus.PERMISSION_NEEDED,
                    limitNote = "Needs UsageStats access; package-level use only — not lesson or video identity."
                )
            }
        }
        return CapabilityRegistry(entries)
    }

    /**
     * Observe destination-package foreground during an active session window.
     * Does not mutate session-run status.
     */
    fun observeSessionForeground(
        access: UsageStatsAccess,
        destination: DestinationApp?,
        sessionStartedAtEpochMs: Long?,
        nowEpochMs: Long,
        registry: CapabilityRegistry = liveRegistry(access)
    ): Observation {
        val entry = registry.get(CapabilityRegistry.ID_USAGE_STATS)
        if (destination == null || sessionStartedAtEpochMs == null) {
            return Observation(
                permissionGranted = access.hasPermission(),
                packageName = destination?.packageName,
                appLabel = destination?.label,
                foregroundMs = null,
                note = entry?.limitNote
                    ?: "UsageStats is unavailable for this session.",
                canOpenSettings = !access.hasPermission()
            )
        }
        if (!access.hasPermission()) {
            return Observation(
                permissionGranted = false,
                packageName = destination.packageName,
                appLabel = destination.label,
                foregroundMs = null,
                note = "Usage access not granted. INTENT can still Complete from your report — " +
                    "granting UsageStats only shows package-level time in ${destination.label}, " +
                    "not lesson completion.",
                canOpenSettings = true
            )
        }
        val ms = access.foregroundMs(
            packageName = destination.packageName,
            startEpochMs = sessionStartedAtEpochMs,
            endEpochMs = nowEpochMs
        )
        val formatted = if (ms != null) {
            SessionElapsed.format(0L, ms.coerceAtLeast(0L))
        } else {
            "—"
        }
        return Observation(
            permissionGranted = true,
            packageName = destination.packageName,
            appLabel = destination.label,
            foregroundMs = ms,
            note = "${destination.label} foreground ~$formatted (package-level). " +
                "App foreground time is not lesson completion — mark Complete yourself when finished.",
            canOpenSettings = false
        )
    }
}
