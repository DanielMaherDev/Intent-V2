package com.intent.launcher

import android.app.Application
import com.intent.launcher.domain.SpanishScenario
import com.intent.launcher.engine.InstalledAppLauncher
import com.intent.launcher.engine.UsageStatsAccess
import com.intent.launcher.platform.AndroidUsageStatsAccess
import com.intent.launcher.platform.PackageManagerAppLauncher
import com.intent.launcher.store.IntentionFlowStore
import java.io.File

class IntentApp : Application() {
    lateinit var store: IntentionFlowStore
        private set

    lateinit var appLauncher: InstalledAppLauncher
        private set

    lateinit var usageStatsAccess: UsageStatsAccess
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        val file = File(filesDir, "intention_flow_v1.json")
        store = IntentionFlowStore(file)
        appLauncher = PackageManagerAppLauncher(this)
        usageStatsAccess = AndroidUsageStatsAccess(this)
        SpanishScenario.ensureSeeded(store)
    }

    companion object {
        lateinit var instance: IntentApp
            private set
    }
}
