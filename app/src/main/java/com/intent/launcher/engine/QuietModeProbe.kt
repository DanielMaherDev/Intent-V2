package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityEntry
import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.capability.CapabilityStatus

/**
 * Stage 6 follow-on — quiet mode / DND honesty (OS-169–170, OS-175).
 *
 * Keeps [CapabilityRegistry.ID_QUIET_MODE] as [CapabilityStatus.NOT_SUPPORTED]:
 * INTENT does not apply a device-wide interruption filter. SessionCard may
 * offer a deep link to system Do Not Disturb without claiming silence succeeded.
 */
object QuietModeProbe {

    /** Opens the system Zen / DND settings UI (OEM screens vary). */
    const val DND_SETTINGS_ACTION = "android.settings.ZEN_MODE_SETTINGS"

    /** Opens Notification policy access (which apps may change DND). */
    const val POLICY_ACCESS_SETTINGS_ACTION =
        "android.settings.NOTIFICATION_POLICY_ACCESS_SETTINGS"

    data class Observation(
        /** User asked for quiet during this session (local UI intent only). */
        val quietRequested: Boolean,
        /** Policy access granted — still does not mean INTENT silenced anything. */
        val policyAccessGranted: Boolean,
        /** Always false in this build — no fake silence success. */
        val silenceApplied: Boolean = false,
        val note: String,
        val canOpenDndSettings: Boolean,
        val dndSettingsAction: String = DND_SETTINGS_ACTION
    )

    /**
     * Refreshes quiet_mode limit notes from policy-access probe.
     * Status stays [CapabilityStatus.NOT_SUPPORTED] — no agent silence adapter yet.
     */
    fun liveRegistry(
        access: QuietModeAccess,
        base: List<CapabilityEntry> = CapabilityRegistry.DEFAULT_ENTRIES
    ): CapabilityRegistry {
        val policyGranted = access.hasNotificationPolicyAccess()
        val entries = base.map { entry ->
            if (entry.id != CapabilityRegistry.ID_QUIET_MODE) {
                entry
            } else {
                entry.copy(
                    status = CapabilityStatus.NOT_SUPPORTED,
                    limitNote = if (policyGranted) {
                        "Notification policy access is granted, but INTENT still does not " +
                            "apply quiet mode or suppress other apps. OEM DND behavior varies; " +
                            "use system Do Not Disturb. Emergency/system alerts stay outside " +
                            "any agent suppression."
                    } else {
                        "User-initiated quiet mode is not shipped as an INTENT filter. " +
                            "INTENT cannot silence all apps — open system Do Not Disturb if you " +
                            "want device quiet. OEM variance applies; emergency alerts remain."
                    }
                )
            }
        }
        return CapabilityRegistry(entries)
    }

    /**
     * Honesty after the user requests quiet on an active SessionCard.
     * Never reports [Observation.silenceApplied] = true.
     */
    fun observeAfterRequest(
        access: QuietModeAccess,
        quietRequested: Boolean,
        registry: CapabilityRegistry = CapabilityRegistry.default
    ): Observation {
        val entry = registry.get(CapabilityRegistry.ID_QUIET_MODE)
        val policyGranted = access.hasNotificationPolicyAccess()
        val unsupported = entry == null ||
            entry.status == CapabilityStatus.NOT_SUPPORTED ||
            !registry.isActionable(CapabilityRegistry.ID_QUIET_MODE)

        if (!quietRequested) {
            return Observation(
                quietRequested = false,
                policyAccessGranted = policyGranted,
                silenceApplied = false,
                note = "",
                canOpenDndSettings = false
            )
        }

        val note = if (unsupported) {
            "INTENT cannot silence all apps or guarantee a quiet session. " +
                "No notifications were suppressed by INTENT. " +
                "Open system Do Not Disturb if you want device quiet — OEM screens vary. " +
                "Emergency and system alerts stay outside agent control."
        } else {
            // Future LIVE path must still refuse silence claims without API confirmation.
            "Quiet capability is listed, but INTENT has no confirmed system silence for this session."
        }

        return Observation(
            quietRequested = true,
            policyAccessGranted = policyGranted,
            silenceApplied = false,
            note = note,
            canOpenDndSettings = true
        )
    }
}
