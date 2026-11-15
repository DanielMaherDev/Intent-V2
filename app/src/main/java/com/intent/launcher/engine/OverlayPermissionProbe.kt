package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityEntry
import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.capability.CapabilityStatus

/**
 * Stage 6 — floating overlay permission probe.
 * Honest: even when granted, the floating companion is not shipped in this build.
 */
object OverlayPermissionProbe {

    const val SETTINGS_ACTION = "android.settings.action.MANAGE_OVERLAY_PERMISSION"

    data class Observation(
        val permissionGranted: Boolean,
        val note: String,
        val canOpenSettings: Boolean,
        val settingsAction: String = SETTINGS_ACTION
    )

    fun liveRegistry(
        access: OverlayPermissionAccess,
        base: List<CapabilityEntry> = CapabilityRegistry.DEFAULT_ENTRIES
    ): CapabilityRegistry {
        val granted = access.canDrawOverlays()
        val entries = base.map { entry ->
            if (entry.id != CapabilityRegistry.ID_FLOATING_OVERLAY) entry
            else if (granted) {
                entry.copy(
                    status = CapabilityStatus.AVAILABLE,
                    limitNote = "Overlay permission granted, but the floating agent is not shipped in this build. " +
                        "Home and SessionCard remain the surfaces."
                )
            } else {
                entry.copy(
                    status = CapabilityStatus.PERMISSION_NEEDED,
                    limitNote = "Requires overlay permission; floating agent not shipped in this build."
                )
            }
        }
        return CapabilityRegistry(entries)
    }

    fun observe(access: OverlayPermissionAccess): Observation {
        val granted = access.canDrawOverlays()
        return if (granted) {
            Observation(
                permissionGranted = true,
                note = "Overlay permission is on. The floating agent is not shipped in this build — " +
                    "use Home and SessionCard instead.",
                canOpenSettings = false
            )
        } else {
            Observation(
                permissionGranted = false,
                note = "Overlay permission not granted. The floating agent is not shipped yet; " +
                    "granting it prepares a future companion and does not change completion rules.",
                canOpenSettings = true
            )
        }
    }
}
