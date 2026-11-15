package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.capability.CapabilityStatus
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * OS-133 / OS-134 — HOME role probe + launch-mode honesty checks.
 */
class HomeRoleProbeTest {

    @Test
    fun liveRegistry_permissionNeededWhenNotDefault() {
        val access = FakeHomeRoleAccess(isDefault = false, canRequestRole = true)
        val registry = HomeRoleProbe.liveRegistry(access)
        assertTrue(registry.needsPermission(CapabilityRegistry.ID_HOME_ROLE))
        assertFalse(registry.isHomeRoleHeld())
        assertTrue(
            registry.get(CapabilityRegistry.ID_HOME_ROLE)!!.limitNote
                .contains("Set as HOME", ignoreCase = true)
        )
    }

    @Test
    fun liveRegistry_availableWhenDefault() {
        val access = FakeHomeRoleAccess(isDefault = true)
        val registry = HomeRoleProbe.liveRegistry(access)
        assertTrue(registry.isHomeRoleHeld())
        assertEquals(
            CapabilityStatus.AVAILABLE,
            registry.get(CapabilityRegistry.ID_HOME_ROLE)!!.status
        )
        assertTrue(
            registry.get(CapabilityRegistry.ID_HOME_ROLE)!!.limitNote
                .contains("default HOME", ignoreCase = true)
        )
        assertFalse(
            registry.get(CapabilityRegistry.ID_HOME_ROLE)!!.limitNote
                .contains("PackageInstaller fixed", ignoreCase = true)
        )
    }

    @Test
    fun observe_notDefault_offersPickerAndRolePath() {
        val obs = HomeRoleProbe.observe(
            FakeHomeRoleAccess(isDefault = false, canRequestRole = true)
        )
        assertFalse(obs.isDefaultHome)
        assertTrue(obs.canRequestRole)
        assertTrue(obs.canOpenHomePicker)
        assertTrue(obs.note.contains("Set as HOME", ignoreCase = true))
        assertEquals(HomeRoleProbe.HOME_SETTINGS_ACTION, obs.preferredSettingsAction)
    }

    @Test
    fun observe_notDefault_withoutRoleManager_pointsToSettings() {
        val obs = HomeRoleProbe.observe(
            FakeHomeRoleAccess(isDefault = false, canRequestRole = false)
        )
        assertFalse(obs.canRequestRole)
        assertTrue(obs.canOpenHomePicker)
        assertTrue(obs.note.contains("Settings", ignoreCase = true))
        assertTrue(obs.note.contains("RoleManager", ignoreCase = true))
    }

    @Test
    fun observe_default_hidesPicker() {
        val obs = HomeRoleProbe.observe(FakeHomeRoleAccess(isDefault = true))
        assertTrue(obs.isDefaultHome)
        assertFalse(obs.canOpenHomePicker)
        assertTrue(obs.note.contains("default HOME", ignoreCase = true))
    }

    @Test
    fun isHomeLaunchIntent_detectsMainHome() {
        assertTrue(
            HomeRoleProbe.isHomeLaunchIntent(
                "android.intent.action.MAIN",
                setOf("android.intent.category.HOME")
            )
        )
        assertFalse(
            HomeRoleProbe.isHomeLaunchIntent(
                "android.intent.action.MAIN",
                setOf("android.intent.category.LAUNCHER")
            )
        )
        assertFalse(
            HomeRoleProbe.isHomeLaunchIntent(
                "android.intent.action.VIEW",
                setOf("android.intent.category.HOME")
            )
        )
    }

    @Test
    fun platformCapabilityLive_chainsHomeRole() {
        val usage = FakeUsageStatsAccess(granted = true)
        val overlay = FakeOverlayPermissionAccess(granted = true)
        val home = FakeHomeRoleAccess(isDefault = false)
        val registry = PlatformCapabilityLive.registry(
            usage = usage,
            overlay = overlay,
            homeRole = home
        )
        assertTrue(registry.canReadUsageStats())
        assertTrue(registry.canUseFloatingOverlay())
        assertTrue(registry.needsPermission(CapabilityRegistry.ID_HOME_ROLE))
    }

    @Test
    fun manifestDeclaresHomeLauncherAndSingleTask() {
        val candidates = listOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml")
        )
        val manifest = candidates.firstOrNull { it.isFile }?.readText().orEmpty()
        assertTrue(
            "HOME category required for OS-133 launcher eligibility",
            manifest.contains("android.intent.category.HOME")
        )
        assertTrue(
            "DEFAULT category required with HOME",
            manifest.contains("android.intent.category.DEFAULT")
        )
        assertTrue(
            "LAUNCHER category keeps sideload entry point",
            manifest.contains("android.intent.category.LAUNCHER")
        )
        assertTrue(
            "singleTask avoids stranded duplicate launcher tasks",
            manifest.contains("android:launchMode=\"singleTask\"")
        )
        assertTrue(
            "clearTaskOnLaunch returns to launcher root after HOME",
            manifest.contains("android:clearTaskOnLaunch=\"true\"")
        )
        assertTrue(
            "stateNotNeeded reduces stale restore after process death",
            manifest.contains("android:stateNotNeeded=\"true\"")
        )
        assertFalse(
            "Do not declare QUERY_ALL_PACKAGES on sideload",
            manifest.contains("android.permission.QUERY_ALL_PACKAGES")
        )
        assertTrue(
            "Keep SYSTEM_ALERT_WINDOW for overlay settings list",
            manifest.contains("android.permission.SYSTEM_ALERT_WINDOW")
        )
    }

    @Test
    fun homeSettingsActionConstant_matchesPlatform() {
        assertEquals("android.settings.HOME_SETTINGS", HomeRoleProbe.HOME_SETTINGS_ACTION)
        assertEquals(
            "android.settings.MANAGE_DEFAULT_APPS_SETTINGS",
            HomeRoleProbe.DEFAULT_APPS_SETTINGS_ACTION
        )
        assertEquals("android.app.role.HOME", HomeRoleProbe.ROLE_HOME)
    }
}
