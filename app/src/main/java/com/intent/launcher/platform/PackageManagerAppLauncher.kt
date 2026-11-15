package com.intent.launcher.platform

import android.content.Context
import android.content.Intent
import com.intent.launcher.engine.InstalledAppLauncher

/** PackageManager-backed [InstalledAppLauncher] for the device. */
class PackageManagerAppLauncher(
    private val context: Context
) : InstalledAppLauncher {

    override fun isInstalled(packageName: String): Boolean =
        context.packageManager.getLaunchIntentForPackage(packageName) != null

    override fun launch(packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
