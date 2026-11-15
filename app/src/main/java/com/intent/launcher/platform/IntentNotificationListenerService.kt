package com.intent.launcher.platform

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Thin Stage 6 listener so the package appears under Notification access settings.
 * Does not retain notification content — session summaries are a later slice.
 */
class IntentNotificationListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        // Intentionally no-op: no content retention in v0.0.22.
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Intentionally no-op.
    }
}
