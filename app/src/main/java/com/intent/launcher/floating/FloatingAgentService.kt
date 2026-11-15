package com.intent.launcher.floating

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.intent.launcher.MainActivity
import com.intent.launcher.R
import com.intent.launcher.engine.FloatingAgentRestartPolicy
import com.intent.launcher.engine.FloatingAgentState
import com.intent.launcher.engine.FloatingCompanion
import com.intent.launcher.engine.TalkBackLabels

/**
 * Overlay bubble for the floating companion — same agent as Conversation.
 * Only runs when [Settings.canDrawOverlays] is true; otherwise stay stopped.
 *
 * Sticky honesty (OS-155): [START_STICKY] only while overlay is granted.
 * Sticky restart re-checks overlay and never invents a grant; Home / Companion
 * menu / notification remain the Conversation path when denied or stopped.
 */
class FloatingAgentService : Service() {

    private var windowManager: WindowManager? = null
    private var bubble: FrameLayout? = null
    private var labelView: TextView? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val overlayGranted = Settings.canDrawOverlays(this)
        val requested = intent?.getStringExtra(EXTRA_STATE)?.let { name ->
            runCatching { FloatingAgentState.valueOf(name) }.getOrNull()
        }
        // Sticky restart delivers a null intent — policy defaults to IDLE when overlay OK.
        val decision = FloatingAgentRestartPolicy.decide(
            overlayGranted = overlayGranted,
            requestedState = requested
        )
        if (decision.mode == FloatingAgentRestartPolicy.StartMode.STOP_NOT_STICKY) {
            removeBubble()
            stopSelf()
            return START_NOT_STICKY
        }
        val state = decision.resolvedState ?: FloatingAgentState.IDLE
        ensureForeground()
        ensureBubble()
        updateBubble(state)
        return START_STICKY
    }

    override fun onDestroy() {
        removeBubble()
        super.onDestroy()
    }

    private fun ensureForeground() {
        val channelId = "intent_floating_companion"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            mgr.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    "INTENT companion",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                action = ACTION_OPEN_CONVERSATION
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("INTENT companion")
            .setContentText(
                "Same agent as Conversation — tap the bubble or this notice. " +
                    "If the bubble is gone, open Home’s Companion menu."
            )
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureBubble() {
        if (bubble != null) return
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager = wm
        val density = resources.displayMetrics.density
        val size = (56 * density).toInt()
        val pad = (12 * density).toInt()
        val frame = FrameLayout(this).apply {
            setBackgroundColor(0xFF5459C9.toInt())
            setPadding(pad, pad, pad, pad)
            elevation = 12f * density
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            contentDescription = TalkBackLabels.floaterBubble(FloatingAgentState.IDLE)
        }
        val tv = TextView(this).apply {
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 11f
            gravity = Gravity.CENTER
            text = FloatingCompanion.labelFor(FloatingAgentState.IDLE)
            // Parent FrameLayout owns the spoken label (OS-201).
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        frame.addView(
            tv,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        frame.setOnClickListener {
            val launch = Intent(this, MainActivity::class.java).apply {
                action = ACTION_OPEN_CONVERSATION
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(launch)
        }
        frame.setOnLongClickListener {
            val launch = Intent(this, MainActivity::class.java).apply {
                action = ACTION_OPEN_DRAWER
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(launch)
            true
        }
        val params = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = (16 * density).toInt()
            y = (120 * density).toInt()
        }
        wm.addView(frame, params)
        bubble = frame
        labelView = tv
    }

    private fun updateBubble(state: FloatingAgentState) {
        labelView?.text = FloatingCompanion.labelFor(state)
        bubble?.contentDescription = TalkBackLabels.floaterBubble(state)
        val color = when (state) {
            FloatingAgentState.ACTIVE_SESSION -> 0xFF2D786A.toInt()
            FloatingAgentState.ATTENTION_NEEDED -> 0xFFA76531.toInt()
            FloatingAgentState.IDLE -> 0xFF5459C9.toInt()
            FloatingAgentState.CAPTURING,
            FloatingAgentState.THINKING,
            FloatingAgentState.RESPONSE_READY -> 0xFF4146AE.toInt()
        }
        bubble?.setBackgroundColor(color)
    }

    private fun removeBubble() {
        val b = bubble ?: return
        try {
            windowManager?.removeView(b)
        } catch (_: Exception) {
            // already detached
        }
        bubble = null
        labelView = null
    }

    companion object {
        const val ACTION_OPEN_CONVERSATION = "com.intent.launcher.OPEN_CONVERSATION"
        /** Long-press bubble → Ask / Today / Capture drawer. */
        const val ACTION_OPEN_DRAWER = "com.intent.launcher.OPEN_FLOATER_DRAWER"
        /** Drawer Capture (or direct) → thought sheet. */
        const val ACTION_CAPTURE_THOUGHT = "com.intent.launcher.CAPTURE_THOUGHT"
        const val EXTRA_STATE = "floating_state"
        private const val NOTIFICATION_ID = 1601

        /**
         * Sync overlay bubble to [state].
         * Null / overlay denied → stop service (no invented bubble); Home fallback applies.
         */
        fun sync(context: Context, state: FloatingAgentState?) {
            if (state == null || !Settings.canDrawOverlays(context)) {
                context.stopService(Intent(context, FloatingAgentService::class.java))
                return
            }
            val intent = Intent(context, FloatingAgentService::class.java).apply {
                putExtra(EXTRA_STATE, state.name)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
