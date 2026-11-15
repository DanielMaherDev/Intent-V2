package com.intent.launcher

import android.app.Application
import com.intent.launcher.domain.SpanishScenario
import com.intent.launcher.engine.InstalledAppLauncher
import com.intent.launcher.engine.NotificationListenerAccess
import com.intent.launcher.engine.OverlayPermissionAccess
import com.intent.launcher.engine.SpeechToTextAccess
import com.intent.launcher.engine.UsageStatsAccess
import com.intent.launcher.engine.VoicePermissionAccess
import com.intent.launcher.platform.AndroidNotificationListenerAccess
import com.intent.launcher.platform.AndroidOverlayPermissionAccess
import com.intent.launcher.platform.AndroidSpeechToTextAccess
import com.intent.launcher.platform.AndroidUsageStatsAccess
import com.intent.launcher.platform.AndroidVoicePermissionAccess
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

    lateinit var overlayPermissionAccess: OverlayPermissionAccess
        private set

    lateinit var voicePermissionAccess: VoicePermissionAccess
        private set

    lateinit var speechToTextAccess: SpeechToTextAccess
        private set

    lateinit var notificationListenerAccess: NotificationListenerAccess
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        val file = File(filesDir, "intention_flow_v1.json")
        store = IntentionFlowStore(file)
        appLauncher = PackageManagerAppLauncher(this)
        usageStatsAccess = AndroidUsageStatsAccess(this)
        overlayPermissionAccess = AndroidOverlayPermissionAccess(this)
        voicePermissionAccess = AndroidVoicePermissionAccess(this)
        speechToTextAccess = AndroidSpeechToTextAccess(this)
        notificationListenerAccess = AndroidNotificationListenerAccess(this)
        SpanishScenario.ensureSeeded(store)
    }

    companion object {
        lateinit var instance: IntentApp
            private set
    }
}
