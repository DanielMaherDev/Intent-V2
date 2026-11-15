package com.intent.launcher.platform

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.intent.launcher.engine.NotificationListenerAccess

/** Live NotificationListenerService grant probe. */
class AndroidNotificationListenerAccess(
    private val context: Context
) : NotificationListenerAccess {

    override fun hasPermission(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName)
}
