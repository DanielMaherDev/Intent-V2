package com.intent.launcher.floating

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.intent.launcher.MainActivity
import com.intent.launcher.R
import com.intent.launcher.engine.FloatingAgentRestartPolicy

/**
 * Gate D3 — permitted notification takeover when the floating bubble must stop.
 * Posts only when [FloatingAgentRestartPolicy.Decision.postTakeoverNotification] is true
 * and the system still allows notifications. Opens Conversation with the same
 * intervention turn/job ids when present.
 */
object FloatingAgentInterruptNotifier {

    const val CHANNEL_ID = "intent_companion_interrupt"
    const val NOTIFICATION_ID = 1602

    fun notificationsPermitted(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun postTakeover(context: Context, decision: FloatingAgentRestartPolicy.Decision) {
        if (!decision.postTakeoverNotification) return
        if (!notificationsPermitted(context)) return
        val title = decision.notificationTitle ?: return
        val text = decision.notificationText ?: return
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            conversationIntent(
                context = context,
                jobId = decision.deepLink.jobId,
                turnId = decision.deepLink.turnId
            ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOngoing(false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    fun conversationIntent(
        context: Context,
        jobId: String?,
        turnId: String?
    ): Intent =
        Intent(context, MainActivity::class.java).apply {
            action = FloatingAgentService.ACTION_OPEN_CONVERSATION
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_NEW_TASK
            if (!jobId.isNullOrBlank()) {
                putExtra(FloatingAgentService.EXTRA_INTERVENTION_JOB_ID, jobId)
            }
            if (!turnId.isNullOrBlank()) {
                putExtra(FloatingAgentService.EXTRA_INTERVENTION_TURN_ID, turnId)
            }
        }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        mgr.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "INTENT saved work",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description =
                    "When the floating companion stops (overlay revoked or process death), " +
                        "INTENT posts a notice that saved work is still on-device and opens Conversation."
            }
        )
    }
}
