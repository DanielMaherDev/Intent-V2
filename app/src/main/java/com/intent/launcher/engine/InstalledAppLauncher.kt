package com.intent.launcher.engine

/**
 * Platform adapter for launching an installed package.
 * Production uses PackageManager; unit tests supply a fake.
 */
interface InstalledAppLauncher {
    fun isInstalled(packageName: String): Boolean
    /** @return true when the launch intent was started successfully. */
    fun launch(packageName: String): Boolean
}

/** Test / preview stub — never launches a real process. */
class FakeInstalledAppLauncher(
    private val installed: Set<String> = emptySet(),
    private val failLaunchFor: Set<String> = emptySet()
) : InstalledAppLauncher {
    val launchedPackages: MutableList<String> = mutableListOf()

    override fun isInstalled(packageName: String): Boolean =
        packageName in installed

    override fun launch(packageName: String): Boolean {
        if (packageName !in installed || packageName in failLaunchFor) return false
        launchedPackages += packageName
        return true
    }
}
