package com.intent.launcher.platform

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
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

    override fun totalForegroundMs(
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
            stats.sumOf { it.totalTimeInForeground }
        } catch (_: Exception) {
            null
        }
    }

    override fun currentForegroundPackage(): String? {
        if (!hasPermission()) return null
        return try {
            val manager =
                context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val end = System.currentTimeMillis()
            val begin = end - 60_000L
            val events = manager.queryEvents(begin, end) ?: return null
            var last: String? = null
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (isForegroundEvent(event.eventType)) {
                    last = event.packageName
                }
            }
            last
        } catch (_: Exception) {
            null
        }
    }

    override fun lastTimePackageLeftForeground(
        packageName: String,
        windowStartEpochMs: Long,
        endEpochMs: Long
    ): Long? {
        if (!hasPermission()) return null
        if (endEpochMs <= windowStartEpochMs) return null
        return try {
            val manager =
                context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val events = manager.queryEvents(windowStartEpochMs, endEpochMs) ?: return null
            var lastLeave: Long? = null
            var inForeground = false
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.packageName != packageName) continue
                when {
                    isForegroundEvent(event.eventType) -> inForeground = true
                    isBackgroundEvent(event.eventType) -> {
                        if (inForeground) {
                            lastLeave = event.timeStamp
                            inForeground = false
                        }
                    }
                }
            }
            if (inForeground) null else lastLeave
        } catch (_: Exception) {
            null
        }
    }

    private fun isForegroundEvent(type: Int): Boolean {
        if (type == UsageEvents.Event.MOVE_TO_FOREGROUND) return true
        if (Build.VERSION.SDK_INT >= 29 && type == UsageEvents.Event.ACTIVITY_RESUMED) return true
        return false
    }

    private fun isBackgroundEvent(type: Int): Boolean {
        if (type == UsageEvents.Event.MOVE_TO_BACKGROUND) return true
        if (Build.VERSION.SDK_INT >= 29 && type == UsageEvents.Event.ACTIVITY_PAUSED) return true
        return false
    }
}
