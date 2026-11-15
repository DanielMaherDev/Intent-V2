package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityEntry
import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.capability.CapabilityStatus

/**
 * OS-133 / OS-134 — HOME role honesty.
 *
 * INTENT qualifies as a launcher via MAIN + HOME + DEFAULT. Default selection uses
 * [android.app.role.RoleManager] when available, otherwise Settings → Home app.
 * Holding HOME is not an OEM privilege and does not fix PackageInstaller.
 */
object HomeRoleProbe {

    /** RoleManager.createRequestRoleIntent(ROLE_HOME) — API 29+. */
    const val ROLE_HOME = "android.app.role.HOME"

    /** Opens the system Home-app picker (API 24+). */
    const val HOME_SETTINGS_ACTION = "android.settings.HOME_SETTINGS"

    /** Fallback when HOME_SETTINGS is missing on an OEM skin. */
    const val DEFAULT_APPS_SETTINGS_ACTION =
        "android.settings.MANAGE_DEFAULT_APPS_SETTINGS"

    data class Observation(
        /** This package is the current default HOME. */
        val isDefaultHome: Boolean,
        /** RoleManager request path is available (API 29+). */
        val canRequestRole: Boolean,
        val note: String,
        /** Show in-app “Set as HOME” / Settings deep link. */
        val canOpenHomePicker: Boolean,
        val preferredSettingsAction: String = HOME_SETTINGS_ACTION
    )

    fun liveRegistry(
        access: HomeRoleAccess,
        base: List<CapabilityEntry> = CapabilityRegistry.DEFAULT_ENTRIES
    ): CapabilityRegistry {
        val isDefault = access.isDefaultHome()
        val entries = base.map { entry ->
            if (entry.id != CapabilityRegistry.ID_HOME_ROLE) {
                entry
            } else if (isDefault) {
                entry.copy(
                    status = CapabilityStatus.AVAILABLE,
                    limitNote = "INTENT is the default HOME launcher. " +
                        "Unlock, app switch, and HOME return to this surface when the " +
                        "system delivers MAIN/HOME. No OEM privileges."
                )
            } else {
                entry.copy(
                    status = CapabilityStatus.PERMISSION_NEEDED,
                    limitNote = "Not the default HOME yet. Use Set as HOME (RoleManager when " +
                        "available, otherwise Settings → Home app). Holding HOME does not " +
                        "grant PackageInstaller or partner OAuth powers."
                )
            }
        }
        return CapabilityRegistry(entries)
    }

    fun observe(access: HomeRoleAccess): Observation {
        val isDefault = access.isDefaultHome()
        val canRequest = access.canRequestHomeRole()
        return if (isDefault) {
            Observation(
                isDefaultHome = true,
                canRequestRole = canRequest,
                note = "INTENT is your default HOME. Pressing Home or unlocking should " +
                    "return here — not a stranded Conversation or Intention screen.",
                canOpenHomePicker = false
            )
        } else {
            Observation(
                isDefaultHome = false,
                canRequestRole = canRequest,
                note = if (canRequest) {
                    "INTENT is not the default HOME. Tap Set as HOME to open the system " +
                        "picker (RoleManager). You can also change Home app in Settings."
                } else {
                    "INTENT is not the default HOME. Open Settings → Home app to select " +
                        "INTENT. RoleManager request is unavailable on this device/API."
                },
                canOpenHomePicker = true,
                preferredSettingsAction = HOME_SETTINGS_ACTION
            )
        }
    }

    /** True when [intent] is a system HOME launch (ACTION_MAIN + CATEGORY_HOME). */
    fun isHomeLaunchIntent(action: String?, categories: Set<String>?): Boolean {
        if (action != "android.intent.action.MAIN") return false
        return categories?.contains("android.intent.category.HOME") == true
    }
}
