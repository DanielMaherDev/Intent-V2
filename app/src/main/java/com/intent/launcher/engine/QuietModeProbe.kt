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
 * Settings surfaces the same honesty — never a fake silence toggle.
 */
object QuietModeProbe {

    /** Opens the system Zen / DND settings UI (OEM screens vary). */
    const val DND_SETTINGS_ACTION = "android.settings.ZEN_MODE_SETTINGS"

    /** Opens Notification policy access (which apps may change DND). */
    const val POLICY_ACCESS_SETTINGS_ACTION =
        "android.settings.NOTIFICATION_POLICY_ACCESS_SETTINGS"

    /** Settings section title — quiet / silence honesty (no invent toggle). */
    const val SETTINGS_SECTION_TITLE = "Quiet mode / silence"

    data class SettingsCopy(
        val sectionTitle: String = SETTINGS_SECTION_TITLE,
        /** Short status chip — e.g. "Not supported · quiet_mode". */
        val statusLabel: String,
        /** Full honesty body for Settings (never claims silence applied). */
        val body: String,
        /**
         * True only when quiet_mode is actionable. Always false while
         * [CapabilityStatus.NOT_SUPPORTED] — Settings must not invent a silence Switch.
         */
        val showSilenceToggle: Boolean,
        /** Optional system DND deep link — not an INTENT silence claim. */
        val canOpenDndSettings: Boolean = true,
        val dndSettingsAction: String = DND_SETTINGS_ACTION
    )

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
     * Settings / Connections honesty for quiet_mode.
     * When [CapabilityStatus.NOT_SUPPORTED], never offers a silence toggle.
     */
    fun settingsCopy(
        access: QuietModeAccess,
        registry: CapabilityRegistry = CapabilityRegistry.default
    ): SettingsCopy {
        val entry = registry.get(CapabilityRegistry.ID_QUIET_MODE)
        val unsupported = entry == null ||
            entry.status == CapabilityStatus.NOT_SUPPORTED ||
            !registry.isActionable(CapabilityRegistry.ID_QUIET_MODE)
        val policyGranted = access.hasNotificationPolicyAccess()
        val statusLabel = when {
            unsupported -> "Not supported · ${CapabilityRegistry.ID_QUIET_MODE}"
            entry!!.status == CapabilityStatus.PERMISSION_NEEDED ->
                "Permission needed · ${CapabilityRegistry.ID_QUIET_MODE}"
            else -> "${entry.status.name} · ${CapabilityRegistry.ID_QUIET_MODE}"
        }
        val body = if (unsupported) {
            val policyLine = if (policyGranted) {
                "Notification policy access is granted on this device, but that does not " +
                    "mean INTENT can silence apps."
            } else {
                "INTENT has no interruption filter — quiet_mode stays not supported."
            }
            "$policyLine SessionCard may offer “Request quiet” as a local intent only; " +
                "it never reports silence applied. Open system Do Not Disturb if you want " +
                "device quiet — OEM screens vary; emergency/system alerts stay outside " +
                "agent control. No Settings silence toggle in this build."
        } else {
            // Future LIVE path must still refuse unconfirmed silence claims.
            (entry?.limitNote?.takeIf { it.isNotBlank() }
                ?: "Quiet capability is listed, but INTENT has no confirmed silence adapter.") +
                " Settings will not invent a silence toggle without a confirmed API."
        }
        return SettingsCopy(
            statusLabel = statusLabel,
            body = body,
            showSilenceToggle = !unsupported && registry.canApplyQuietMode(),
            canOpenDndSettings = true
        )
    }

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
