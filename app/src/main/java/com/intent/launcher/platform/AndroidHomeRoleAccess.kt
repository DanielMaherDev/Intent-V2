package com.intent.launcher.platform

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.intent.launcher.engine.HomeRoleAccess

/** Live HOME-role / default-launcher probe (OS-133). */
class AndroidHomeRoleAccess(
    private val context: Context
) : HomeRoleAccess {

    override fun isDefaultHome(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                return roleManager.isRoleHeld(RoleManager.ROLE_HOME)
            }
        }
        return resolveDefaultHomePackage() == context.packageName
    }

    override fun canRequestHomeRole(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val roleManager = context.getSystemService(RoleManager::class.java) ?: return false
        return roleManager.isRoleAvailable(RoleManager.ROLE_HOME)
    }

    private fun resolveDefaultHomePackage(): String? {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = context.packageManager.resolveActivity(
            home,
            PackageManager.MATCH_DEFAULT_ONLY
        )
        return resolved?.activityInfo?.packageName
    }
}
