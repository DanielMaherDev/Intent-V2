package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityEntry
import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.capability.CapabilityStatus

/**
 * Stage 6 — floating overlay permission probe.
 * When granted, the floating companion surface may show; when denied, use Home/Agent → Conversation.
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
                    limitNote = "Floating companion overlay available — same agent as Conversation. " +
                        "Voice turns use Conversation mic when permitted; no live LLM."
                )
            } else {
                entry.copy(
                    status = CapabilityStatus.PERMISSION_NEEDED,
                    limitNote = "Requires overlay permission for the floating companion. " +
                        "Use Home’s Agent tab or Discuss to open Conversation without it."
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
                note = "Floating companion can appear over other apps — same agent as Conversation. " +
                    "Tap it to open Discuss.",
                canOpenSettings = false
            )
        } else {
            Observation(
                permissionGranted = false,
                note = "Overlay permission not granted — no floating bubble. " +
                    "Use Home’s Agent tab or Discuss for the same Conversation.",
                canOpenSettings = true
            )
        }
    }
}
