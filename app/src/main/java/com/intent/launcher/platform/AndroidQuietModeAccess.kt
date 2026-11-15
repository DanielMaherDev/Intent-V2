package com.intent.launcher.platform

import android.app.NotificationManager
import android.content.Context
import com.intent.launcher.engine.QuietModeAccess

/**
 * Live probe for NotificationManager policy access.
 * Does not change interruption filter or claim silence.
 */
class AndroidQuietModeAccess(
    private val context: Context
) : QuietModeAccess {
    override fun hasNotificationPolicyAccess(): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return false
        return nm.isNotificationPolicyAccessGranted
    }
}
