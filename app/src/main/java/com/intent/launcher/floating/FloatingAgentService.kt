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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.intent.launcher.MainActivity
import com.intent.launcher.R
import com.intent.launcher.engine.FloaterSystemUiPolicy
import com.intent.launcher.engine.FloatingAgentState
import com.intent.launcher.engine.FloatingCompanion

/**
 * Overlay bubble for the floating companion — same agent as Conversation.
 * Only runs when [Settings.canDrawOverlays] is true; otherwise stay stopped.
 *
 * OS-156: [FloaterSystemUiPolicy] keeps the bubble out of recents / gesture /
 * IME bands and uses NOT_FOCUSABLE|NOT_TOUCH_MODAL so system permission dialogs
 * and the keyboard keep input. Additive with Gate D sticky honesty (PR #53).
 */
class FloatingAgentService : Service() {

    private var windowManager: WindowManager? = null
    private var bubble: FrameLayout? = null
    private var labelView: TextView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var bubbleSizePx: Int = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        val stateName = intent?.getStringExtra(EXTRA_STATE) ?: FloatingAgentState.IDLE.name
        val state = runCatching { FloatingAgentState.valueOf(stateName) }
            .getOrDefault(FloatingAgentState.IDLE)
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
            .setContentText("Same agent as Conversation — tap the bubble or this notice.")
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
        val size = FloaterSystemUiPolicy.bubbleSizePx(density)
        bubbleSizePx = size
        val pad = (12 * density).toInt()
        val frame = FrameLayout(this).apply {
            setBackgroundColor(0xFF5459C9.toInt())
            setPadding(pad, pad, pad, pad)
            elevation = 12f * density
        }
        val tv = TextView(this).apply {
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 11f
            gravity = Gravity.CENTER
            text = FloatingCompanion.labelFor(FloatingAgentState.IDLE)
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

        val metrics = resources.displayMetrics
        val screen = FloaterSystemUiPolicy.Screen(
            widthPx = metrics.widthPixels,
            heightPx = metrics.heightPixels,
            density = density
        )
        // Conservative fallback insets until the first WindowInsets pass.
        val fallback = FloaterSystemUiPolicy.Insets(
            statusBar = (24 * density).toInt(),
            navBar = FloaterSystemUiPolicy.minBottomClearancePx(density),
            gestureBottom = FloaterSystemUiPolicy.minBottomClearancePx(density),
            gestureRight = FloaterSystemUiPolicy.preferredMarginPx(density)
        )
        val placement = FloaterSystemUiPolicy.place(screen, fallback, bubbleSizePx = size)
        val params = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            FloaterSystemUiPolicy.overlayFlags(),
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = placement.xPx
            y = placement.yPx
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Fit system bars + IME so the window reports those insets for OS-156.
                setFitInsetsTypes(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
                )
            }
        }
        layoutParams = params
        frame.visibility = if (placement.visible) View.VISIBLE else View.GONE
        // Never claim edge gesture exclusion — system nav / back gestures stay free.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            frame.systemGestureExclusionRects = emptyList()
        }
        ViewCompat.setOnApplyWindowInsetsListener(frame) { _, insets ->
            applySystemUiInsets(insets)
            insets
        }
        wm.addView(frame, params)
        bubble = frame
        labelView = tv
        ViewCompat.requestApplyInsets(frame)
    }

    private fun applySystemUiInsets(windowInsets: WindowInsetsCompat) {
        val frame = bubble ?: return
        val params = layoutParams ?: return
        val wm = windowManager ?: return
        val density = resources.displayMetrics.density
        val metrics = resources.displayMetrics
        val systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
        val gestures = windowInsets.getInsets(WindowInsetsCompat.Type.systemGestures())
        val ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime())
        val insets = FloaterSystemUiPolicy.Insets(
            statusBar = systemBars.top,
            navBar = systemBars.bottom,
            gestureLeft = gestures.left,
            gestureRight = gestures.right,
            gestureBottom = gestures.bottom,
            ime = ime.bottom
        )
        val screen = FloaterSystemUiPolicy.Screen(
            widthPx = metrics.widthPixels,
            heightPx = metrics.heightPixels,
            density = density
        )
        val placement = FloaterSystemUiPolicy.place(
            screen = screen,
            insets = insets,
            bubbleSizePx = bubbleSizePx
        )
        params.x = placement.xPx
        params.y = placement.yPx
        params.gravity = Gravity.TOP or Gravity.END
        params.flags = FloaterSystemUiPolicy.overlayFlags()
        frame.visibility = if (placement.visible) View.VISIBLE else View.GONE
        try {
            wm.updateViewLayout(frame, params)
        } catch (_: Exception) {
            // view may already be detached
        }
    }

    private fun updateBubble(state: FloatingAgentState) {
        labelView?.text = FloatingCompanion.labelFor(state)
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
        layoutParams = null
        bubbleSizePx = 0
    }

    companion object {
        const val ACTION_OPEN_CONVERSATION = "com.intent.launcher.OPEN_CONVERSATION"
        /** Long-press bubble → Ask / Today / Capture drawer. */
        const val ACTION_OPEN_DRAWER = "com.intent.launcher.OPEN_FLOATER_DRAWER"
        /** Drawer Capture (or direct) → thought sheet. */
        const val ACTION_CAPTURE_THOUGHT = "com.intent.launcher.CAPTURE_THOUGHT"
        const val EXTRA_STATE = "floating_state"
        private const val NOTIFICATION_ID = 1601

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
