package com.intent.launcher.platform

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.intent.launcher.IntentApp
import com.intent.launcher.engine.SessionNotificationReviewStore

/**
 * Thin Stage 6 listener so the package appears under Notification access settings.
 * During an active session window, records **package + coarse category counts only** —
 * never titles/bodies (OS-173–174 privacy-safe review).
 */
class IntentNotificationListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val app = applicationContext as? IntentApp ?: return
        val category = SessionNotificationReviewStore.normalizeCategory(sbn.notification.category)
        app.sessionNotificationReviewStore.recordPosted(
            packageName = sbn.packageName.orEmpty(),
            coarseCategory = category,
            postedAtEpochMs = sbn.postTime
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Intentionally no-op — we do not retain content or removal payloads.
    }
}
