package com.intent.launcher.platform

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import com.intent.launcher.engine.UsageStatsAccess

/** Live UsageStatsManager probe — package-level foreground only. */
class AndroidUsageStatsAccess(
    private val context: Context
) : UsageStatsAccess {

    override fun hasPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    override fun foregroundMs(
        packageName: String,
        startEpochMs: Long,
        endEpochMs: Long
    ): Long? {
        if (!hasPermission()) return null
        if (endEpochMs <= startEpochMs) return 0L
        return try {
            val manager =
                context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val stats = manager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startEpochMs,
                endEpochMs
            ) ?: return 0L
            stats.filter { it.packageName == packageName }
                .sumOf { it.totalTimeInForeground }
        } catch (_: Exception) {
            null
        }
    }
}
