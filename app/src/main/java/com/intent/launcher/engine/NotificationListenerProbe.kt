package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityEntry
import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.capability.CapabilityStatus

/**
 * Stage 6 — notification listener honesty.
 * When granted, summaries may be built later; when denied, Complete remains user-reported.
 * Never claims device-wide silence or lesson completion.
 */
object NotificationListenerProbe {

    /** Matches [android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS]. */
    const val SETTINGS_ACTION = "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"

    data class Observation(
        val permissionGranted: Boolean,
        /** Honest UI note — always denies silence / lesson claims. */
        val note: String,
        val canOpenSettings: Boolean,
        val settingsAction: String = SETTINGS_ACTION
    )

    fun liveRegistry(
        access: NotificationListenerAccess,
        base: List<CapabilityEntry> = CapabilityRegistry.DEFAULT_ENTRIES
    ): CapabilityRegistry {
        val granted = access.hasPermission()
        val entries = base.map { entry ->
            if (entry.id != CapabilityRegistry.ID_NOTIFICATION_LISTENER) entry
            else if (granted) {
                entry.copy(
                    status = CapabilityStatus.AVAILABLE,
                    limitNote = "Notification listener connected — permitted summaries only. " +
                        "Not a universal record, and INTENT cannot silence every app. " +
                        "Session notification review is not built yet; no content retained by default."
                )
            } else {
                entry.copy(
                    status = CapabilityStatus.PERMISSION_NEEDED,
                    limitNote = "Needs Notification access for permitted summaries. " +
                        "Does not silence notifications or prove lesson completion — " +
                        "Complete remains your report."
                )
            }
        }
        return CapabilityRegistry(entries)
    }

    /**
     * Active-session honesty for notification access.
     * Does not mutate session-run status or store notification bodies.
     */
    fun observeForSession(access: NotificationListenerAccess): Observation {
        return if (access.hasPermission()) {
            Observation(
                permissionGranted = true,
                note = "Notification access granted — session summaries not built yet " +
                    "(no content retained). Listening is not lesson completion or quiet mode.",
                canOpenSettings = false
            )
        } else {
            Observation(
                permissionGranted = false,
                note = "Notification access not granted. INTENT can still Complete from your report — " +
                    "granting access only enables permitted notification summaries later, " +
                    "not silence or lesson completion.",
                canOpenSettings = true
            )
        }
    }

    fun observe(access: NotificationListenerAccess): Observation =
        observeForSession(access)
}
