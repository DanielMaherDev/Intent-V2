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
import com.intent.launcher.IntentApp
import com.intent.launcher.MainActivity
import com.intent.launcher.R
import com.intent.launcher.engine.FloatingAgentRestartPolicy
import com.intent.launcher.engine.FloaterSystemUiPolicy
import com.intent.launcher.engine.FloatingAgentState
import com.intent.launcher.engine.FloatingCompanion
import com.intent.launcher.engine.TalkBackLabels
import com.intent.launcher.engine.TriggerCooldownPolicy
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Overlay bubble for the floating companion — same agent as Conversation.
 * Only runs when [Settings.canDrawOverlays] is true; otherwise stay stopped.
 *
 * Sticky honesty (OS-155 / Gate D3): [START_STICKY] only while overlay is granted.
 * Sticky restart re-checks overlay and never invents a grant. When overlay is
 * revoked (or denied after process death), the bubble stops and a **permitted**
 * notification opens Conversation — reusing intervention turn/job ids when active.
 * IntentionFlowStore mutations survive; Home / Companion menu remain the path.
 * OS-156: [FloaterSystemUiPolicy] keeps the bubble out of recents / gesture /
 * IME bands and uses NOT_FOCUSABLE|NOT_TOUCH_MODAL so system permission dialogs
 * and the keyboard keep input.
 */
class FloatingAgentService : Service() {

    private var windowManager: WindowManager? = null
    private var bubble: FrameLayout? = null
    private var labelView: TextView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var bubbleSizePx: Int = 0
    /** True after we successfully showed the bubble / FGS in this process. */
    private var runningWithOverlay: Boolean = false
    private var lastInterventionJobId: String? = null
    private var lastInterventionTurnId: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val overlayGranted = Settings.canDrawOverlays(this)
        val stickyRestart = intent == null
        val requested = intent?.getStringExtra(EXTRA_STATE)?.let { name ->
            runCatching { FloatingAgentState.valueOf(name) }.getOrNull()
        }
        val provenanceLabel = intent?.getStringExtra(EXTRA_PROVENANCE_LABEL)
        val attentionLabel = intent?.getStringExtra(EXTRA_ATTENTION_LABEL)
        val jobFromIntent = intent?.getStringExtra(EXTRA_INTERVENTION_JOB_ID)
        val turnFromIntent = intent?.getStringExtra(EXTRA_INTERVENTION_TURN_ID)
        val (jobId, turnId, savedSummary) = resolveInterventionExtras(jobFromIntent, turnFromIntent)
        lastInterventionJobId = jobId
        lastInterventionTurnId = turnId

        val decision = FloatingAgentRestartPolicy.decide(
            overlayGranted = overlayGranted,
            requestedState = requested,
            wasRunningWithOverlay = runningWithOverlay || bubble != null,
            stickyRestart = stickyRestart,
            notificationsPermitted = FloatingAgentInterruptNotifier.notificationsPermitted(this),
            interventionJobId = jobId,
            interventionTurnId = turnId,
            savedWorkSummary = savedSummary
        )
        if (decision.mode == FloatingAgentRestartPolicy.StartMode.STOP_NOT_STICKY) {
            removeBubble()
            stopForegroundCompat()
            maybePostTakeover(this, decision)
            runningWithOverlay = false
            companionActive.set(false)
            stopSelf()
            return START_NOT_STICKY
        }
        val state = decision.resolvedState ?: FloatingAgentState.IDLE
        ensureForeground(jobId = jobId, turnId = turnId)
        ensureBubble()
        updateBubble(
            state = state,
            attentionLabel = attentionLabel,
            provenanceLabel = provenanceLabel
        )
        runningWithOverlay = true
        companionActive.set(true)
        takeoverPostedForDenial.set(false)
        return START_STICKY
    }

    override fun onDestroy() {
        removeBubble()
        runningWithOverlay = false
        super.onDestroy()
    }

    private fun resolveInterventionExtras(
        jobFromIntent: String?,
        turnFromIntent: String?
    ): Triple<String?, String?, String?> {
        var jobId = jobFromIntent?.takeIf { it.isNotBlank() } ?: lastInterventionJobId
        var turnId = turnFromIntent?.takeIf { it.isNotBlank() } ?: lastInterventionTurnId
        var summary: String? = null
        runCatching {
            val store = IntentApp.instance.store
            val intervention = TriggerCooldownPolicy.activeIntervention(store.snapshot())
            if (intervention != null) {
                if (jobId.isNullOrBlank()) jobId = intervention.jobId
                if (turnId.isNullOrBlank()) turnId = intervention.turnId
                summary = "Active intervention (${intervention.provenanceKind.name}) is still queued."
            }
        }
        return Triple(jobId, turnId, summary)
    }

    private fun ensureForeground(jobId: String?, turnId: String?) {
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
            FloatingAgentInterruptNotifier.conversationIntent(
                context = this,
                jobId = jobId,
                turnId = turnId
            ).apply {
                // FGS content intent already has NEW_TASK from helper; keep single-top.
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("INTENT companion")
            .setContentText(
                "Same agent as Conversation — tap the bubble or this notice. " +
                    "If the bubble is gone, open Companion."
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

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
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
            val launch = FloatingAgentInterruptNotifier.conversationIntent(
                context = this,
                jobId = lastInterventionJobId,
                turnId = lastInterventionTurnId
            )
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

    private fun updateBubble(
        state: FloatingAgentState,
        attentionLabel: String? = null,
        provenanceLabel: String? = null
    ) {
        val spoken = attentionLabel?.takeIf { it.isNotBlank() }
            ?: FloatingCompanion.labelFor(state)
        labelView?.text = spoken
        bubble?.contentDescription = TalkBackLabels.floaterBubble(
            state = state,
            spokenLabel = spoken,
            interventionProvenanceLabel = provenanceLabel
        )
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
        /** App Shortcut / companion → Today (planned · completed · deferred). */
        const val ACTION_OPEN_TODAY = "com.intent.launcher.OPEN_TODAY"
        /** App Shortcut / companion → local History (undo) — not cloud sync. */
        const val ACTION_OPEN_HISTORY = "com.intent.launcher.OPEN_HISTORY"
        const val EXTRA_STATE = "floating_state"
        const val EXTRA_PROVENANCE_LABEL = "intervention_provenance"
        const val EXTRA_ATTENTION_LABEL = "intervention_attention"
        const val EXTRA_INTERVENTION_JOB_ID = "intervention_job_id"
        const val EXTRA_INTERVENTION_TURN_ID = "intervention_turn_id"
        private const val NOTIFICATION_ID = 1601

        /** Process-wide: companion successfully showed with overlay this session. */
        private val companionActive = AtomicBoolean(false)
        /** Avoid spamming takeover notices on every Home resume while overlay stays denied. */
        private val takeoverPostedForDenial = AtomicBoolean(false)

        /**
         * Sync overlay bubble to [state].
         * Null / overlay denied → stop service (no invented bubble). When overlay
         * was revoked after the companion had been active, Gate D3 posts one
         * permitted takeover notice (not on every subsequent resume).
         */
        fun sync(
            context: Context,
            state: FloatingAgentState?,
            /** OBSERVED | INFERRED when an intervention prompt is active (Gate D2). */
            provenanceLabel: String? = null,
            attentionLabel: String? = null,
            interventionJobId: String? = null,
            interventionTurnId: String? = null
        ) {
            val overlayGranted = Settings.canDrawOverlays(context)
            if (state == null || !overlayGranted) {
                if (!overlayGranted && companionActive.getAndSet(false)) {
                    val (jobId, turnId, summary) = resolveSavedIntervention(
                        context = context,
                        jobFromCaller = interventionJobId,
                        turnFromCaller = interventionTurnId
                    )
                    val decision = FloatingAgentRestartPolicy.decide(
                        overlayGranted = false,
                        requestedState = state,
                        wasRunningWithOverlay = true,
                        stickyRestart = false,
                        notificationsPermitted =
                            FloatingAgentInterruptNotifier.notificationsPermitted(context),
                        interventionJobId = jobId,
                        interventionTurnId = turnId,
                        savedWorkSummary = summary
                    )
                    maybePostTakeover(context, decision)
                }
                context.stopService(Intent(context, FloatingAgentService::class.java))
                return
            }
            val intent = Intent(context, FloatingAgentService::class.java).apply {
                putExtra(EXTRA_STATE, state.name)
                if (!provenanceLabel.isNullOrBlank()) {
                    putExtra(EXTRA_PROVENANCE_LABEL, provenanceLabel)
                }
                if (!attentionLabel.isNullOrBlank()) {
                    putExtra(EXTRA_ATTENTION_LABEL, attentionLabel)
                }
                if (!interventionJobId.isNullOrBlank()) {
                    putExtra(EXTRA_INTERVENTION_JOB_ID, interventionJobId)
                }
                if (!interventionTurnId.isNullOrBlank()) {
                    putExtra(EXTRA_INTERVENTION_TURN_ID, interventionTurnId)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        private fun maybePostTakeover(
            context: Context,
            decision: FloatingAgentRestartPolicy.Decision
        ) {
            if (!decision.postTakeoverNotification) return
            if (!takeoverPostedForDenial.compareAndSet(false, true)) return
            FloatingAgentInterruptNotifier.postTakeover(context, decision)
        }

        private fun resolveSavedIntervention(
            context: Context,
            jobFromCaller: String?,
            turnFromCaller: String?
        ): Triple<String?, String?, String?> {
            var jobId = jobFromCaller?.takeIf { it.isNotBlank() }
            var turnId = turnFromCaller?.takeIf { it.isNotBlank() }
            var summary: String? = null
            runCatching {
                val app = context.applicationContext as? IntentApp ?: IntentApp.instance
                val intervention = TriggerCooldownPolicy.activeIntervention(app.store.snapshot())
                if (intervention != null) {
                    if (jobId.isNullOrBlank()) jobId = intervention.jobId
                    if (turnId.isNullOrBlank()) turnId = intervention.turnId
                    summary =
                        "Active intervention (${intervention.provenanceKind.name}) is still queued."
                }
            }
            return Triple(jobId, turnId, summary)
        }
    }
}
