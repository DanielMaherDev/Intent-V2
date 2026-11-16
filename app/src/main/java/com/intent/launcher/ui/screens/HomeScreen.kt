package com.intent.launcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.AddTask
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.HourglassBottom
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intent.launcher.domain.AppDurationCriterion
import com.intent.launcher.domain.AttentionStrip
import com.intent.launcher.domain.ErrandStatus
import com.intent.launcher.domain.ErrandTodo
import com.intent.launcher.domain.HomeAttention
import com.intent.launcher.domain.NextAction
import com.intent.launcher.domain.NextActionKind
import com.intent.launcher.domain.SessionElapsed
import com.intent.launcher.engine.InterventionProvenance
import com.intent.launcher.ui.components.Callout
import com.intent.launcher.engine.TalkBackLabels
import com.intent.launcher.domain.ScreenTimeBar
import com.intent.launcher.ui.components.DateLabel
import com.intent.launcher.ui.components.GlyphTone
import com.intent.launcher.ui.components.HeroCard
import com.intent.launcher.ui.components.HeroTone
import com.intent.launcher.ui.components.HomeBottomNav
import com.intent.launcher.ui.components.HomeBrandRow
import com.intent.launcher.ui.components.IntentPill
import com.intent.launcher.ui.components.IntentPrimaryButton
import com.intent.launcher.ui.components.IntentQuietButton
import com.intent.launcher.ui.components.IntentSecondaryButton
import com.intent.launcher.ui.components.Lede
import com.intent.launcher.ui.components.LineRow
import com.intent.launcher.ui.components.PillTone
import com.intent.launcher.ui.components.OverflowMenuItem
import com.intent.launcher.ui.components.ScreenTimeTopBar
import com.intent.launcher.ui.components.SectionTitle
import com.intent.launcher.ui.components.SessionMetaCell
import com.intent.launcher.ui.components.SessionSummaryCard
import com.intent.launcher.ui.components.SmallLabel
import com.intent.launcher.ui.theme.IntentColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * Home — brand-first four-core hierarchy (v0.0.104): ✦ INTENT, date, greeting
 * headline + lede, then one next-action attention strip (OS-135–136) with
 * optional secondary timer/usage line and soft-pause honesty. Quiet grants,
 * errands inbox, and drawer behaviors stay below the strip.
 * Home — brand, optional OS-136 top screen-time bar (independent of floater),
 * next-action attention strip (OS-135), quiet grant prompts. No competing
 * headline stack or multi-stat first viewport. Thought capture stays quiet.
 * Home — OS-135 four-slot hierarchy without clutter: today's recommendation
 * (attention strip), optional next deadline, agent attention, and due-work
 * summary. OS-136 secondary timer/usage line stays on the strip. Additive to
 * four-core Home visual reclaim (#52) — does not own greeting/lede/pills.
 * Thought capture stays a quiet control (v0.0.23).
 */
@Composable
fun HomeScreen(
    nextAction: NextAction,
    intentionHint: String,
    intentionTitle: String,
    /** OS-135 due / deadline / agent slots — omit empty fields (no invented rows). */
    homeAttention: HomeAttention.Model = HomeAttention.Model(
        nextDeadlineLine = null,
        dueWorkSummary = null,
        agentAttention = null
    ),
    onContinueExploring: () -> Unit,
    onOpenIntention: () -> Unit,
    onOpenIntentionDrawer: () -> Unit = onOpenIntention,
    /** Dedicated Today — planned · completed · deferred. */
    onOpenToday: (() -> Unit)? = null,
    onReviewProposal: () -> Unit,
    onBegin: () -> Unit,
    onDefer: () -> Unit,
    /** One-line deferral preview when Later is offered. */
    deferPreviewLine: String? = null,
    onDiscuss: () -> Unit,
    onCompleteSession: () -> Unit = {},
    onAbandonSession: () -> Unit = {},
    onOpenDestination: () -> Unit = {},
    onConfirmCheckIn: () -> Unit = {},
    onConfirmMidday: () -> Unit = {},
    onDeferMidday: () -> Unit = {},
    onCheckOutKeepSchedule: () -> Unit = {},
    onCheckOutDeferTomorrow: () -> Unit = {},
    destinationLaunchNote: String? = null,
    /** OS-136 configurable top screen-time / focus bar (above brand). */
    screenTimeBarContent: ScreenTimeBar.Content = ScreenTimeBar.Content.Hidden,
    usageNote: String? = null,
    usageGranted: Boolean = false,
    usageForegroundMs: Long? = null,
    usageAppLabel: String? = null,
    showGrantUsageAccess: Boolean = false,
    onGrantUsageAccess: () -> Unit = {},
    /** Durable OBSERVED evidence summary (package foreground ≠ lesson completion). */
    observedEvidenceNote: String? = null,
    showDismissObserved: Boolean = false,
    onDismissObserved: () -> Unit = {},
    /** Post-Complete check-out note from OBSERVED evidence. */
    checkoutObservationNote: String? = null,
    onClearCheckoutNote: () -> Unit = {},
    notificationNote: String? = null,
    showGrantNotificationAccess: Boolean = false,
    onGrantNotificationAccess: () -> Unit = {},
    quietNote: String? = null,
    showRequestQuiet: Boolean = false,
    onRequestQuiet: () -> Unit = {},
    showOpenDndSettings: Boolean = false,
    onOpenDndSettings: () -> Unit = {},
    screenCaptureNote: String? = null,
    showCaptureScreen: Boolean = false,
    onCaptureScreen: () -> Unit = {},
    /** Privacy-safe session notification review rows (v0.0.31) — quiet, not first-viewport clutter. */
    sessionNotificationRows: List<Pair<String, String>> = emptyList(),
    onDismissNotificationRow: ((String, String) -> Unit)? = null,
    /** Soft-pause honesty for app-linked sessions (Away from app). */
    sessionSoftPaused: Boolean = false,
    sessionPauseNote: String? = null,
    sessionPausedAtEpochMs: Long? = null,
    sessionAccumulatedPausedMs: Long = 0L,
    /**
     * Explicit app-duration criterion (e.g. 15-min Duolingo). Soft-pause freezes
     * completed/target; never invents lesson completion. Null when undeclared.
     */
    durationProgress: AppDurationCriterion.Progress? = null,
    showResumeSession: Boolean = false,
    onResumeSession: () -> Unit = {},
    overlayNote: String? = null,
    showGrantOverlay: Boolean = false,
    onGrantOverlay: () -> Unit = {},
    /** HOME role honesty when INTENT is not yet the default launcher (OS-133). */
    homeRoleNote: String? = null,
    showSetAsHome: Boolean = false,
    onSetAsHome: () -> Unit = {},
    /** Non-null when the overlay bubble is live (same agent as Conversation). */
    floatingCompanionLabel: String? = null,
    /** Honest manual route when overlay is denied — no fake floating UI. */
    floatingManualRouteNote: String? = null,
    /**
     * Gate D2 — active usage intervention with explicit OBSERVED / INFERRED label.
     * Never silent mix; omit when none queued/shown.
     */
    interventionPrompt: InterventionProvenance.PromptView? = null,
    onOpenIntervention: () -> Unit = onDiscuss,
    onDismissIntervention: () -> Unit = {},
    /** Floater Today focus note for the next-action strip. */
    floaterTodayNote: String? = null,
    onCaptureThought: () -> Unit = {},
    /** Overlay-denied fallback: open Ask / Today / Capture drawer. */
    onOpenFloaterDrawer: () -> Unit = {},
    thoughtCaptureNote: String? = null,
    onExploreMarathon: (() -> Unit)? = null,
    onExploreBusiness: (() -> Unit)? = null,
    onExploreMove: (() -> Unit)? = null,
    onExploreJobOffer: (() -> Unit)? = null,
    onExploreQuitSmoking: (() -> Unit)? = null,
    onExploreAppUse: (() -> Unit)? = null,
    onExploreEuWorkSpain: (() -> Unit)? = null,
    onExploreChess: (() -> Unit)? = null,
    undoLabel: String? = null,
    onUndo: (() -> Unit)? = null,
    undoTrashNote: String? = null,
    onStartBuilder: (() -> Unit)? = null,
    onOpenTriage: (() -> Unit)? = null,
    rawThoughtCount: Int = 0,
    onSettings: (() -> Unit)? = null,
    /** Open errands for the lightweight to-do inbox (OS-073). */
    openErrands: List<ErrandTodo> = emptyList(),
    errandNote: String? = null,
    onAddQuickTodo: (() -> Unit)? = null,
    onToggleErrand: (ErrandTodo) -> Unit = {},
    /** OS-143 — History / Trash via overflow, not a primary tab. */
    overflowItems: List<OverflowMenuItem> = emptyList(),
    modifier: Modifier = Modifier
) {
    val dateLabel = SimpleDateFormat("EEEE · d MMMM", Locale.getDefault())
        .format(Date())
    val greeting = homeGreeting(nextAction, intentionTitle, sessionSoftPaused)
    val stripMeta = attentionStripMeta(nextAction, intentionTitle, sessionSoftPaused)

    val startedAt = nextAction.startedAtEpochMs
    var nowEpochMs by remember(startedAt, sessionPausedAtEpochMs) {
        mutableLongStateOf(System.currentTimeMillis())
    }
    LaunchedEffect(startedAt, nextAction.kind, sessionPausedAtEpochMs) {
        if (nextAction.kind != NextActionKind.ACTIVE_SESSION || startedAt == null) {
            return@LaunchedEffect
        }
        while (true) {
            nowEpochMs = System.currentTimeMillis()
            delay(1_000L)
        }
    }

    // Recompute against the ticking local clock so soft-pause freezes the bar.
    val liveDuration = durationProgress?.let { base ->
        val elapsed = if (startedAt != null) {
            SessionElapsed.activeElapsedMs(
                startedAtEpochMs = startedAt,
                nowEpochMs = nowEpochMs,
                pausedAtEpochMs = sessionPausedAtEpochMs,
                accumulatedPausedMs = sessionAccumulatedPausedMs
            )
        } else {
            base.elapsedMs
        }
        AppDurationCriterion.progressFromElapsed(
            targetMinutes = base.targetMinutes,
            elapsedMs = elapsed,
            softPaused = base.softPaused || sessionSoftPaused || sessionPausedAtEpochMs != null
        )
    }

    val secondary = AttentionStrip.secondaryLine(
        nextAction = nextAction,
        nowEpochMs = nowEpochMs,
        usageGranted = usageGranted,
        usageForegroundMs = usageForegroundMs,
        usageAppLabel = usageAppLabel,
        pausedAtEpochMs = sessionPausedAtEpochMs,
        accumulatedPausedMs = sessionAccumulatedPausedMs,
        softPaused = sessionSoftPaused,
        durationProgress = liveDuration
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(IntentColors.Paper)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 19.dp, vertical = 15.dp)
        ) {
            if (screenTimeBarContent !is ScreenTimeBar.Content.Hidden) {
                ScreenTimeTopBar(
                    content = screenTimeBarContent,
                    onGrantUsageAccess = onGrantUsageAccess
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            HomeBrandRow(onSettings = onSettings, overflowItems = overflowItems)
            Spacer(modifier = Modifier.height(22.dp))
            DateLabel(dateLabel)
            Spacer(modifier = Modifier.height(11.dp))
            Text(
                text = greeting.headline,
                style = MaterialTheme.typography.displayLarge,
                color = IntentColors.Ink,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Lede(greeting.lede)
            Spacer(modifier = Modifier.height(26.dp))

            // Active session uses SessionSummaryCard (approval-mock language).
            // Other next-action kinds keep the existing attention-strip hero.
            if (nextAction.kind == NextActionKind.ACTIVE_SESSION) {
                ActiveSessionCard(
                    nextAction = nextAction,
                    intentionTitle = intentionTitle,
                    nowEpochMs = nowEpochMs,
                    secondary = secondary,
                    sessionSoftPaused = sessionSoftPaused,
                    sessionPauseNote = sessionPauseNote,
                    sessionPausedAtEpochMs = sessionPausedAtEpochMs,
                    sessionAccumulatedPausedMs = sessionAccumulatedPausedMs,
                    durationProgress = liveDuration,
                    showResumeSession = showResumeSession,
                    onResumeSession = onResumeSession,
                    onDiscuss = onDiscuss,
                    onCompleteSession = onCompleteSession,
                    onAbandonSession = onAbandonSession,
                    onOpenDestination = onOpenDestination,
                    destinationLaunchNote = destinationLaunchNote,
                    showGrantUsageAccess = showGrantUsageAccess,
                    onGrantUsageAccess = onGrantUsageAccess,
                    observedEvidenceNote = observedEvidenceNote,
                    showDismissObserved = showDismissObserved,
                    onDismissObserved = onDismissObserved,
                    notificationNote = notificationNote,
                    showGrantNotificationAccess = showGrantNotificationAccess,
                    onGrantNotificationAccess = onGrantNotificationAccess,
                    quietNote = quietNote,
                    showRequestQuiet = showRequestQuiet,
                    onRequestQuiet = onRequestQuiet,
                    showOpenDndSettings = showOpenDndSettings,
                    onOpenDndSettings = onOpenDndSettings,
                    screenCaptureNote = screenCaptureNote,
                    showCaptureScreen = showCaptureScreen,
                    onCaptureScreen = onCaptureScreen,
                    sessionNotificationRows = sessionNotificationRows,
                    onDismissNotificationRow = onDismissNotificationRow
                )
            } else {
                AttentionStripCard(
                    nextAction = nextAction,
                    meta = stripMeta,
                    pills = greeting.pills,
                    secondary = secondary,
                    deferPreviewLine = deferPreviewLine,
                    onContinueExploring = onContinueExploring,
                    onOpenIntention = onOpenIntention,
                    onStartBuilder = onStartBuilder,
                    onReviewProposal = onReviewProposal,
                    onBegin = onBegin,
                    onDefer = onDefer,
                    onDiscuss = onDiscuss,
                    onCompleteSession = onCompleteSession,
                    onAbandonSession = onAbandonSession,
                    onOpenDestination = onOpenDestination,
                    onConfirmCheckIn = onConfirmCheckIn,
                    onConfirmMidday = onConfirmMidday,
                    onDeferMidday = onDeferMidday,
                    onCheckOutKeepSchedule = onCheckOutKeepSchedule,
                    onCheckOutDeferTomorrow = onCheckOutDeferTomorrow,
                    destinationLaunchNote = destinationLaunchNote,
                    showGrantUsageAccess = showGrantUsageAccess,
                    onGrantUsageAccess = onGrantUsageAccess,
                    observedEvidenceNote = observedEvidenceNote,
                    showDismissObserved = showDismissObserved,
                    onDismissObserved = onDismissObserved,
                    notificationNote = notificationNote,
                    showGrantNotificationAccess = showGrantNotificationAccess,
                    onGrantNotificationAccess = onGrantNotificationAccess,
                    quietNote = quietNote,
                    showRequestQuiet = showRequestQuiet,
                    onRequestQuiet = onRequestQuiet,
                    showOpenDndSettings = showOpenDndSettings,
                    onOpenDndSettings = onOpenDndSettings,
                    screenCaptureNote = screenCaptureNote,
                    showCaptureScreen = showCaptureScreen,
                    onCaptureScreen = onCaptureScreen,
                    sessionNotificationRows = sessionNotificationRows,
                    onDismissNotificationRow = onDismissNotificationRow,
                    sessionSoftPaused = sessionSoftPaused,
                    sessionPauseNote = sessionPauseNote,
                    showResumeSession = showResumeSession,
                    onResumeSession = onResumeSession
                )
            }

            // Gate D2 — intervention prompt always shows OBSERVED vs INFERRED.
            if (interventionPrompt != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Callout(
                    title = InterventionProvenance.homeTitle(interventionPrompt),
                    text = buildString {
                        append(interventionPrompt.promptText)
                        append("\n")
                        append(interventionPrompt.sourceDetail)
                        append(" — ")
                        append(interventionPrompt.honestyNote)
                    },
                    modifier = Modifier.semantics {
                        contentDescription =
                            "${interventionPrompt.provenanceLabel} intervention. " +
                                interventionPrompt.promptText
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IntentPrimaryButton(
                        text = "Review",
                        onClick = onOpenIntervention,
                        modifier = Modifier.weight(1.4f)
                    )
                    IntentQuietButton(
                        text = "Dismiss",
                        onClick = onDismissIntervention,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // OS-135 — quiet hierarchy under the recommendation strip (omit empties).
            if (homeAttention.nextDeadlineLine != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Next · ${homeAttention.nextDeadlineLine}",
                    style = MaterialTheme.typography.bodySmall,
                    color = IntentColors.Muted,
                    modifier = Modifier.semantics {
                        contentDescription =
                            "Next deadline ${homeAttention.nextDeadlineLine}"
                    }
                )
            }
            if (homeAttention.agentAttention != null) {
                Spacer(modifier = Modifier.height(14.dp))
                LineRow(
                    title = homeAttention.agentAttention.title,
                    subtitle = homeAttention.agentAttention.subtitle,
                    onClick = onDiscuss,
                    glyphIcon = Icons.Outlined.AutoAwesome,
                    glyphTone = GlyphTone.Accent
                )
            }
            if (homeAttention.dueWorkSummary != null) {
                Spacer(modifier = Modifier.height(18.dp))
                SectionTitle("Other things today")
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = homeAttention.dueWorkSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = IntentColors.Muted,
                    modifier = Modifier.semantics {
                        contentDescription = homeAttention.dueWorkSummary
                    }
                )
            }

            QuietGrantStrip(
                showGrantUsageAccess = showGrantUsageAccess &&
                    nextAction.kind != NextActionKind.ACTIVE_SESSION,
                onGrantUsageAccess = onGrantUsageAccess,
                usageNote = if (
                    showGrantUsageAccess && nextAction.kind != NextActionKind.ACTIVE_SESSION
                ) {
                    usageNote
                } else {
                    null
                },
                checkoutObservationNote = if (
                    nextAction.kind != NextActionKind.ACTIVE_SESSION
                ) {
                    checkoutObservationNote
                } else {
                    null
                },
                onClearCheckoutNote = onClearCheckoutNote,
                overlayNote = overlayNote,
                showGrantOverlay = showGrantOverlay,
                onGrantOverlay = onGrantOverlay,
                homeRoleNote = homeRoleNote,
                showSetAsHome = showSetAsHome,
                onSetAsHome = onSetAsHome,
                floatingCompanionLabel = floatingCompanionLabel,
                floatingManualRouteNote = floatingManualRouteNote,
                floatingInterventionProvenance = interventionPrompt?.provenanceLabel,
                floaterTodayNote = floaterTodayNote,
                onOpenFloaterDrawer = onOpenFloaterDrawer,
                onCaptureThought = onCaptureThought,
                thoughtCaptureNote = thoughtCaptureNote
            )

            if (nextAction.kind != NextActionKind.ACTIVE_SESSION &&
                sessionNotificationRows.isNotEmpty()
            ) {
                Spacer(modifier = Modifier.height(14.dp))
                sessionNotificationRows.forEach { (label, key) ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        color = IntentColors.Muted
                    )
                    if (onDismissNotificationRow != null) {
                        val parts = key.split("|", limit = 2)
                        if (parts.size == 2) {
                            Spacer(modifier = Modifier.height(2.dp))
                            IntentQuietButton(
                                text = "Dismiss",
                                onClick = { onDismissNotificationRow(parts[0], parts[1]) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(26.dp))
            SectionTitle("Errands")
            Spacer(modifier = Modifier.height(9.dp))
            if (onAddQuickTodo != null) {
                LineRow(
                    title = "Add a quick to-do",
                    subtitle = "Small errand with a reminder — not an intention",
                    onClick = onAddQuickTodo,
                    glyphIcon = Icons.Outlined.AddTask,
                    glyphTone = GlyphTone.Accent
                )
            }
            openErrands.forEach { errand ->
                val subtitle = buildString {
                    append(errand.firstAction)
                    if (!errand.reminderNote.isNullOrBlank()) {
                        append(" · ")
                        append(errand.reminderNote)
                    }
                }
                LineRow(
                    title = errand.title,
                    subtitle = subtitle,
                    onClick = { onToggleErrand(errand) },
                    glyphIcon = if (errand.status == ErrandStatus.DONE) {
                        Icons.Outlined.CheckCircle
                    } else {
                        Icons.Outlined.RadioButtonUnchecked
                    },
                    glyphTone = if (errand.status == ErrandStatus.DONE) {
                        GlyphTone.Success
                    } else {
                        GlyphTone.Accent
                    }
                )
            }
            if (errandNote != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errandNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = IntentColors.Muted
                )
            }

            Spacer(modifier = Modifier.height(26.dp))
            SectionTitle("Your space")
            Spacer(modifier = Modifier.height(9.dp))
            if (onOpenToday != null) {
                LineRow(
                    title = "Today",
                    subtitle = "Planned · completed · deferred",
                    onClick = onOpenToday,
                    glyphIcon = Icons.Outlined.Today,
                    glyphTone = GlyphTone.Accent
                )
            }
            LineRow(
                title = intentionTitle.ifBlank { "Your intention" },
                subtitle = intentionHint.ifBlank { "Open your intention" },
                onClick = onOpenIntention,
                glyphIcon = Icons.Outlined.Spa,
                glyphTone = GlyphTone.Success
            )
            if (onExploreMarathon != null) {
                LineRow(
                    title = "Explore a marathon",
                    subtitle = "Second shape on the same foundation",
                    onClick = onExploreMarathon,
                    glyphIcon = Icons.Outlined.AutoAwesome,
                    glyphTone = GlyphTone.Accent
                )
            }
            if (onExploreBusiness != null) {
                LineRow(
                    title = "Explore a business idea",
                    subtitle = "Third shape — discovery without forced tasks",
                    onClick = onExploreBusiness,
                    glyphIcon = Icons.Outlined.AutoAwesome,
                    glyphTone = GlyphTone.Accent
                )
            }
            if (onExploreMove != null) {
                LineRow(
                    title = "Plan a six-week move",
                    subtitle = "Finite plan — landlord steps stay gated",
                    onClick = onExploreMove,
                    glyphIcon = Icons.Outlined.AutoAwesome,
                    glyphTone = GlyphTone.Accent
                )
            }
            if (onExploreJobOffer != null) {
                LineRow(
                    title = "Decide on a job offer",
                    subtitle = "Comparison support — INTENT does not choose for you",
                    onClick = onExploreJobOffer,
                    glyphIcon = Icons.Outlined.AutoAwesome,
                    glyphTone = GlyphTone.Accent
                )
            }
            if (onExploreQuitSmoking != null) {
                LineRow(
                    title = "Explore quitting smoking",
                    subtitle = "CASE-05 — baseline + honest self-report",
                    onClick = onExploreQuitSmoking,
                    glyphIcon = Icons.Outlined.AutoAwesome,
                    glyphTone = GlyphTone.Accent
                )
            }
            if (onExploreAppUse != null) {
                LineRow(
                    title = "Cut Instagram & Shorts time",
                    subtitle = "CASE-06 — 45-min target; package ≠ Shorts identity",
                    onClick = onExploreAppUse,
                    glyphIcon = Icons.Outlined.AutoAwesome,
                    glyphTone = GlyphTone.Accent
                )
            }
            if (onExploreEuWorkSpain != null) {
                LineRow(
                    title = "Work legally in Spain",
                    subtitle = "Research checklist — SAID facts, no invented law",
                    onClick = onExploreEuWorkSpain,
                    glyphIcon = Icons.Outlined.AutoAwesome,
                    glyphTone = GlyphTone.Accent
                )
            }

            if (onExploreChess != null) {
                LineRow(
                    title = "Explore chess rating",
                    subtitle = "CASE-07 — manual baseline + honest practice log",
                    onClick = onExploreChess,
                    glyphIcon = Icons.Outlined.AutoAwesome,
                    glyphTone = GlyphTone.Accent
                )
            }


            if (onStartBuilder != null) {
                LineRow(
                    title = "Start a new intention",
                    subtitle = "Guided builder — heuristic shape, SAID facts on confirm",
                    onClick = onStartBuilder,
                    glyphIcon = Icons.Outlined.AutoAwesome,
                    glyphTone = GlyphTone.Accent
                )
            }
            if (onOpenTriage != null) {
                LineRow(
                    title = "Triage thoughts",
                    subtitle = if (rawThoughtCount > 0) {
                        "$rawThoughtCount raw · confirm or reject proposals"
                    } else {
                        "Split multiple goals into proposed intentions"
                    },
                    onClick = onOpenTriage,
                    glyphIcon = Icons.Outlined.AutoAwesome,
                    glyphTone = GlyphTone.Success
                )
            }

            if (onUndo != null && undoLabel != null) {
                Spacer(modifier = Modifier.height(18.dp))
                IntentQuietButton(text = undoLabel, onClick = onUndo)
            }
            if (undoTrashNote != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = undoTrashNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = IntentColors.Warm
                )
            }
        }

        HomeBottomNav(
            selected = "home",
            onHome = { },
            onIntentions = onOpenIntentionDrawer,
            onAgent = onDiscuss
        )
    }
}

/**
 * Greeting above the strip — matches four-core Home mock (date → headline → lede).
 * Strip title/support stay in [attentionStripMeta]; pills live with the greeting.
 */
private data class HomeGreeting(
    val headline: String,
    val lede: String,
    val pills: List<Pair<String, PillTone>>
)

private fun homeGreeting(
    nextAction: NextAction,
    intentionTitle: String,
    sessionSoftPaused: Boolean = false
): HomeGreeting = when (nextAction.kind) {
    NextActionKind.CONTINUE_EXPLORING -> HomeGreeting(
        headline = "Where would you like to begin?",
        lede = "One goal is ready for a little more thought.",
        pills = listOf("Exploring" to PillTone.Accent, "No plan yet" to PillTone.Neutral)
    )
    NextActionKind.REVIEW_PROPOSAL -> HomeGreeting(
        headline = "You're finding your rhythm.",
        lede = "One useful decision is waiting for you.",
        pills = listOf("Needs approval" to PillTone.Warm)
    )
    NextActionKind.BEGIN -> HomeGreeting(
        headline = "One small step today.",
        lede = nextAction.reason.ifBlank {
            nextAction.detail.ifBlank { "Something useful is ready." }
        },
        pills = listOf(
            intentionTitle.ifBlank { "In motion" } to PillTone.Success
        )
    )
    NextActionKind.ACTIVE_SESSION -> HomeGreeting(
        headline = if (sessionSoftPaused) {
            "You're away from the linked app."
        } else {
            "You're in a session."
        },
        lede = if (sessionSoftPaused) {
            "Resume when you're back — soft-pause is not lesson completion."
        } else {
            "Mark complete when finished, or abandon if you stop."
        },
        pills = emptyList()
    )
    NextActionKind.DISCUSS -> HomeGreeting(
        headline = "Not due just yet.",
        lede = nextAction.reason.ifBlank { "Talk it through when you are ready." },
        pills = listOf(intentionTitle.ifBlank { "Planned" } to PillTone.Warm)
    )
    NextActionKind.CHECK_IN -> HomeGreeting(
        headline = "Good morning.",
        lede = nextAction.reason.ifBlank {
            "Confirm today's focus, or adjust it in Discuss."
        },
        pills = listOf(
            intentionTitle.ifBlank { "Today's focus" } to PillTone.Accent,
            "Confirm or adjust" to PillTone.Neutral
        )
    )
    NextActionKind.MIDDAY -> HomeGreeting(
        headline = "How is the day going?",
        lede = nextAction.reason.ifBlank {
            "Confirm the plan still holds, or defer if things slipped."
        },
        pills = listOf(
            intentionTitle.ifBlank { "Midday" } to PillTone.Warm,
            "Keep or defer" to PillTone.Neutral
        )
    )
    NextActionKind.CHECK_OUT -> HomeGreeting(
        headline = "How did today go?",
        lede = nextAction.reason.ifBlank {
            "Report what happened, then keep the schedule or defer tomorrow."
        },
        pills = listOf(
            intentionTitle.ifBlank { "Review" } to PillTone.Warm,
            "User-reported" to PillTone.Neutral
        )
    )
    else -> HomeGreeting(
        headline = "Where would you like to begin?",
        lede = nextAction.reason.ifBlank { "Your space is ready when you are." },
        pills = emptyList()
    )
}

private data class AttentionStripMeta(
    val smallLabel: String,
    val heroTone: HeroTone,
    val title: String,
    val support: String
)

private fun attentionStripMeta(
    nextAction: NextAction,
    intentionTitle: String,
    sessionSoftPaused: Boolean = false
): AttentionStripMeta {
    val support = AttentionStrip.supportingSentence(nextAction)
    return when (nextAction.kind) {
        NextActionKind.CONTINUE_EXPLORING -> AttentionStripMeta(
            smallLabel = "Still taking shape",
            heroTone = HeroTone.Accent,
            title = if (intentionTitle.contains("Spanish", ignoreCase = true)) {
                "Speak Spanish at work"
            } else {
                intentionTitle.ifBlank { nextAction.label }
            },
            support = support.ifBlank {
                "We know what matters to you. Let's decide how you want to practise."
            }
        )
        NextActionKind.REVIEW_PROPOSAL -> AttentionStripMeta(
            smallLabel = "Ready for your review",
            heroTone = HeroTone.Warm,
            title = nextAction.label.ifBlank { "Review proposal" },
            support = support
        )
        NextActionKind.BEGIN -> AttentionStripMeta(
            smallLabel = "Relevant now",
            heroTone = HeroTone.Success,
            title = nextAction.label,
            support = support
        )
        NextActionKind.ACTIVE_SESSION -> AttentionStripMeta(
            smallLabel = if (sessionSoftPaused) "Away from app" else "In progress",
            heroTone = if (sessionSoftPaused) HeroTone.Warm else HeroTone.Success,
            title = nextAction.label.ifBlank { "Active session" },
            support = intentionTitle.ifBlank { support }
        )
        NextActionKind.DISCUSS -> AttentionStripMeta(
            smallLabel = "Upcoming",
            heroTone = HeroTone.Warm,
            title = nextAction.label,
            support = support.ifBlank { "Talk it through when you are ready." }
        )
        NextActionKind.CHECK_IN -> AttentionStripMeta(
            smallLabel = "Morning check-in",
            heroTone = HeroTone.Accent,
            title = nextAction.label.ifBlank { "Today's focus" },
            support = support.ifBlank { "Confirm today's focus, or adjust it in Discuss." }
        )
        NextActionKind.MIDDAY -> AttentionStripMeta(
            smallLabel = "Midday check",
            heroTone = HeroTone.Warm,
            title = nextAction.label.ifBlank { "How is the day going?" },
            support = support.ifBlank {
                "Confirm the plan still holds, or defer if things slipped."
            }
        )
        NextActionKind.CHECK_OUT -> AttentionStripMeta(
            smallLabel = "Evening check-out",
            heroTone = HeroTone.Warm,
            title = nextAction.label.ifBlank { "How did today go?" },
            support = support.ifBlank {
                "Report what happened, then keep the schedule or defer tomorrow."
            }
        )
        else -> AttentionStripMeta(
            smallLabel = "Your space",
            heroTone = HeroTone.Accent,
            title = nextAction.label.ifBlank { "Where would you like to begin?" },
            support = support.ifBlank { "Your space is ready when you are." }
        )
    }
}


/**
 * Explicit app-duration criterion: completed / target minutes + honest progress bar.
 * Soft-pause freezes the bar; local timer met ≠ lesson completion.
 */
@Composable
private fun DurationCriterionBlock(
    ratioLabel: String,
    fraction: Float,
    softPaused: Boolean,
    localTimerMet: Boolean,
    honestyNote: String,
    contentDescription: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { this.contentDescription = contentDescription }
    ) {
        SmallLabel(
            text = if (softPaused) {
                "Duration criterion · paused"
            } else if (localTimerMet) {
                "Duration criterion · local timer met"
            } else {
                "Duration criterion"
            },
            color = when {
                softPaused -> IntentColors.Warm
                localTimerMet -> IntentColors.Success
                else -> IntentColors.Muted
            }
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = ratioLabel,
            style = MaterialTheme.typography.headlineSmall,
            color = IntentColors.Ink,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = if (softPaused) IntentColors.Warm else IntentColors.Success,
            trackColor = IntentColors.Line,
            strokeCap = StrokeCap.Round
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = honestyNote,
            style = MaterialTheme.typography.bodySmall,
            color = if (softPaused) IntentColors.Warm else IntentColors.Muted
        )
    }
}

/**
 * Active SessionCard — SoftCard + 2×2 meta grid matching the approval weekly mock.
 * Begin/Complete/Abandon/Open/Discuss/Resume and honesty strings stay intact;
 * Home hero for other next-action kinds is unchanged.
 */
@Composable
private fun ActiveSessionCard(
    nextAction: NextAction,
    intentionTitle: String,
    nowEpochMs: Long,
    secondary: AttentionStrip.SecondaryLine?,
    sessionSoftPaused: Boolean,
    sessionPauseNote: String?,
    sessionPausedAtEpochMs: Long?,
    sessionAccumulatedPausedMs: Long,
    durationProgress: AppDurationCriterion.Progress?,
    showResumeSession: Boolean,
    onResumeSession: () -> Unit,
    onDiscuss: () -> Unit,
    onCompleteSession: () -> Unit,
    onAbandonSession: () -> Unit,
    onOpenDestination: () -> Unit,
    destinationLaunchNote: String?,
    showGrantUsageAccess: Boolean,
    onGrantUsageAccess: () -> Unit,
    observedEvidenceNote: String?,
    showDismissObserved: Boolean,
    onDismissObserved: () -> Unit,
    notificationNote: String?,
    showGrantNotificationAccess: Boolean,
    onGrantNotificationAccess: () -> Unit,
    quietNote: String?,
    showRequestQuiet: Boolean,
    onRequestQuiet: () -> Unit,
    showOpenDndSettings: Boolean,
    onOpenDndSettings: () -> Unit,
    screenCaptureNote: String?,
    showCaptureScreen: Boolean,
    onCaptureScreen: () -> Unit,
    sessionNotificationRows: List<Pair<String, String>>,
    onDismissNotificationRow: ((String, String) -> Unit)?
) {
    val startedAt = nextAction.startedAtEpochMs
    val elapsedMs = if (startedAt != null) {
        SessionElapsed.activeElapsedMs(
            startedAtEpochMs = startedAt,
            nowEpochMs = nowEpochMs,
            pausedAtEpochMs = sessionPausedAtEpochMs,
            accumulatedPausedMs = sessionAccumulatedPausedMs
        )
    } else {
        0L
    }
    val elapsedText = SessionElapsed.formatMs(elapsedMs)
    val elapsedDesc = when {
        startedAt == null -> "Elapsed time unavailable"
        sessionSoftPaused || sessionPausedAtEpochMs != null ->
            "Paused $elapsedText — soft-paused while away from the linked app, not lesson completion"
        else -> "Elapsed $elapsedText"
    }
    val destinationValue = nextAction.destinationAppLabel?.takeIf { it.isNotBlank() } ?: "—"
    val statusValue = when {
        sessionSoftPaused -> "Away"
        durationProgress?.localTimerMet == true -> "Timer met"
        secondary is AttentionStrip.SecondaryLine.ObservedPackageMinutes -> "Observed"
        else -> "In progress"
    }
    val sourceValue = when (secondary) {
        is AttentionStrip.SecondaryLine.DurationProgress ->
            if (secondary.softPaused) "Soft-paused" else "Duration"
        is AttentionStrip.SecondaryLine.ObservedPackageMinutes -> secondary.text
        is AttentionStrip.SecondaryLine.SessionElapsed ->
            if (sessionSoftPaused) "Soft-paused" else "Local clock"
        null -> if (sessionSoftPaused) "Soft-paused" else "Local clock"
    }

    SmallLabel(
        text = if (sessionSoftPaused) "Away from app" else "In progress",
        color = if (sessionSoftPaused) IntentColors.Warm else IntentColors.Success
    )
    Spacer(modifier = Modifier.height(7.dp))
    Text(
        text = if (sessionSoftPaused) "Session soft-paused." else "You're in a session.",
        style = MaterialTheme.typography.displayLarge,
        color = IntentColors.Ink
    )
    Spacer(modifier = Modifier.height(10.dp))
    Text(
        text = intentionTitle.ifBlank {
            AttentionStrip.supportingSentence(nextAction).ifBlank {
                "Complete when finished, or abandon if plans change."
            }
        },
        style = MaterialTheme.typography.bodyLarge,
        color = IntentColors.Lede
    )
    Spacer(modifier = Modifier.height(22.dp))

    SessionSummaryCard(
        title = nextAction.label.ifBlank { "Active session" },
        headerIcon = Icons.Outlined.School,
        cells = listOf(
            SessionMetaCell(
                icon = Icons.Outlined.HourglassBottom,
                label = if (sessionSoftPaused) "Paused" else "Elapsed",
                value = elapsedText,
                contentDescription = elapsedDesc
            ),
            SessionMetaCell(
                icon = Icons.Outlined.AccessTime,
                label = "Status",
                value = statusValue
            ),
            SessionMetaCell(
                icon = Icons.Outlined.Widgets,
                label = "Destination",
                value = destinationValue
            ),
            SessionMetaCell(
                icon = Icons.Outlined.PersonOutline,
                label = "Source",
                value = sourceValue
            )
        )
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = when {
                sessionSoftPaused ->
                    "Soft-paused while away from the linked app — not lesson completion."
                secondary is AttentionStrip.SecondaryLine.ObservedPackageMinutes ->
                    "Package foreground only — not lesson completion."
                durationProgress != null ->
                    "Local timer toward ${durationProgress.targetMinutes}-min criterion — " +
                        "not lesson completion."
                else ->
                    "Local clock from Begin — not verified completion."
            },
            style = MaterialTheme.typography.bodySmall,
            color = IntentColors.Muted
        )
    }

    if (durationProgress != null) {
        Spacer(modifier = Modifier.height(14.dp))
        DurationCriterionBlock(
            ratioLabel = durationProgress.ratioLabel,
            fraction = durationProgress.fraction,
            softPaused = durationProgress.softPaused,
            localTimerMet = durationProgress.localTimerMet,
            honestyNote = durationProgress.honestyNote,
            contentDescription = durationProgress.contentDescription
        )
    }

    Spacer(modifier = Modifier.height(12.dp))
    Callout(
        title = "While you're in it",
        text = "Complete when you finish, or Abandon if plans change. Opening a destination app is not lesson completion."
    )

    if (sessionPauseNote != null) {
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = sessionPauseNote,
            style = MaterialTheme.typography.bodySmall,
            color = if (sessionSoftPaused) IntentColors.Warm else IntentColors.Muted
        )
    }
    if (destinationLaunchNote != null) {
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = destinationLaunchNote,
            style = MaterialTheme.typography.bodySmall,
            color = IntentColors.Warm
        )
    }
    if (showGrantUsageAccess) {
        Spacer(modifier = Modifier.height(6.dp))
        IntentQuietButton(text = "Grant usage access", onClick = onGrantUsageAccess)
    }
    if (observedEvidenceNote != null) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = observedEvidenceNote,
            style = MaterialTheme.typography.bodySmall,
            color = IntentColors.Muted
        )
        if (showDismissObserved) {
            Spacer(modifier = Modifier.height(2.dp))
            IntentQuietButton(
                text = "Mark observation inaccurate",
                onClick = onDismissObserved
            )
        }
    }
    if (notificationNote != null) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = notificationNote,
            style = MaterialTheme.typography.bodySmall,
            color = IntentColors.Muted
        )
    }
    if (showGrantNotificationAccess) {
        Spacer(modifier = Modifier.height(4.dp))
        IntentQuietButton(
            text = "Grant notification access",
            onClick = onGrantNotificationAccess
        )
    }
    if (showRequestQuiet) {
        Spacer(modifier = Modifier.height(4.dp))
        IntentQuietButton(
            text = "Request quiet for this session",
            onClick = onRequestQuiet
        )
    }
    if (quietNote != null && quietNote.isNotBlank()) {
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = quietNote,
            style = MaterialTheme.typography.bodySmall,
            color = IntentColors.Muted
        )
    }
    if (showOpenDndSettings) {
        Spacer(modifier = Modifier.height(2.dp))
        IntentQuietButton(
            text = "Open system Do Not Disturb",
            onClick = onOpenDndSettings
        )
    }
    if (screenCaptureNote != null && screenCaptureNote.isNotBlank()) {
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = screenCaptureNote,
            style = MaterialTheme.typography.bodySmall,
            color = IntentColors.Muted
        )
    }
    if (showCaptureScreen) {
        Spacer(modifier = Modifier.height(2.dp))
        IntentQuietButton(text = "Capture screen", onClick = onCaptureScreen)
    }
    if (sessionNotificationRows.isNotEmpty()) {
        sessionNotificationRows.forEach { (label, key) ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = IntentColors.Muted
            )
            if (onDismissNotificationRow != null) {
                val parts = key.split("|", limit = 2)
                if (parts.size == 2) {
                    Spacer(modifier = Modifier.height(2.dp))
                    IntentQuietButton(
                        text = "Dismiss",
                        onClick = { onDismissNotificationRow(parts[0], parts[1]) }
                    )
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))
    if (showResumeSession) {
        IntentPrimaryButton(
            text = "Resume",
            onClick = onResumeSession
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
    if (nextAction.openDestinationLabel != null) {
        IntentSecondaryButton(
            text = nextAction.openDestinationLabel,
            onClick = onOpenDestination
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (nextAction.completeLabel != null) {
            IntentPrimaryButton(
                text = nextAction.completeLabel,
                onClick = onCompleteSession,
                modifier = Modifier.weight(1.4f)
            )
        }
        if (nextAction.abandonLabel != null) {
            IntentSecondaryButton(
                text = nextAction.abandonLabel,
                onClick = onAbandonSession,
                modifier = Modifier.weight(1f)
            )
        }
    }
    if (nextAction.discussLabel != null) {
        Spacer(modifier = Modifier.height(2.dp))
        IntentQuietButton(
            text = nextAction.discussLabel,
            onClick = onDiscuss
        )
    }
}

@Composable
private fun AttentionStripCard(
    nextAction: NextAction,
    meta: AttentionStripMeta,
    pills: List<Pair<String, PillTone>>,
    secondary: AttentionStrip.SecondaryLine?,
    deferPreviewLine: String?,
    onContinueExploring: () -> Unit,
    onOpenIntention: () -> Unit,
    onStartBuilder: (() -> Unit)? = null,
    onReviewProposal: () -> Unit,
    onBegin: () -> Unit,
    onDefer: () -> Unit,
    onDiscuss: () -> Unit,
    onCompleteSession: () -> Unit,
    onAbandonSession: () -> Unit,
    onOpenDestination: () -> Unit,
    onConfirmCheckIn: () -> Unit,
    onConfirmMidday: () -> Unit,
    onDeferMidday: () -> Unit,
    onCheckOutKeepSchedule: () -> Unit,
    onCheckOutDeferTomorrow: () -> Unit,
    destinationLaunchNote: String?,
    showGrantUsageAccess: Boolean,
    onGrantUsageAccess: () -> Unit,
    observedEvidenceNote: String?,
    showDismissObserved: Boolean,
    onDismissObserved: () -> Unit,
    notificationNote: String?,
    showGrantNotificationAccess: Boolean,
    onGrantNotificationAccess: () -> Unit,
    quietNote: String?,
    showRequestQuiet: Boolean,
    onRequestQuiet: () -> Unit,
    showOpenDndSettings: Boolean,
    onOpenDndSettings: () -> Unit,
    screenCaptureNote: String?,
    showCaptureScreen: Boolean,
    onCaptureScreen: () -> Unit,
    sessionNotificationRows: List<Pair<String, String>>,
    onDismissNotificationRow: ((String, String) -> Unit)?,
    sessionSoftPaused: Boolean = false,
    sessionPauseNote: String? = null,
    showResumeSession: Boolean = false,
    onResumeSession: () -> Unit = {}
) {
    val labelColor = when (meta.heroTone) {
        HeroTone.Warm -> IntentColors.Warm
        HeroTone.Success -> IntentColors.Success
        HeroTone.Accent -> IntentColors.Accent
    }

    val stripDescription = TalkBackLabels.attentionStripContainer(
        nextAction = nextAction,
        smallLabel = meta.smallLabel,
        title = meta.title,
        support = meta.support,
        softPaused = sessionSoftPaused
    )
    HeroCard(
        tone = meta.heroTone,
        modifier = Modifier.semantics { contentDescription = stripDescription }
    ) {
        SmallLabel(text = meta.smallLabel, color = labelColor)
        Spacer(modifier = Modifier.height(7.dp))
        Text(
            text = meta.title,
            style = MaterialTheme.typography.headlineSmall,
            color = IntentColors.Ink
        )
        if (meta.support.isNotBlank()) {
            Spacer(modifier = Modifier.height(7.dp))
            Text(
                text = meta.support,
                style = MaterialTheme.typography.bodyMedium,
                color = IntentColors.Muted
            )
        }
        if (pills.isNotEmpty()) {
            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pills.forEach { (text, tone) ->
                    IntentPill(text = text, tone = tone)
                }
            }
        }

        // One secondary line only — duration criterion, elapsed, or OBSERVED (never stacked).
        if (secondary != null) {
            Spacer(modifier = Modifier.height(12.dp))
            when (secondary) {
                is AttentionStrip.SecondaryLine.DurationProgress -> {
                    DurationCriterionBlock(
                        ratioLabel = secondary.ratioLabel,
                        fraction = secondary.fraction,
                        softPaused = secondary.softPaused,
                        localTimerMet = secondary.fraction >= 1f,
                        honestyNote = secondary.honestyNote,
                        contentDescription = secondary.contentDescription
                    )
                }
                is AttentionStrip.SecondaryLine.SessionElapsed -> {
                    Text(
                        text = secondary.text,
                        modifier = Modifier.semantics {
                            contentDescription = secondary.contentDescription
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = IntentColors.Ink,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (sessionSoftPaused) {
                            "Soft-paused while away from the linked app — not lesson completion."
                        } else {
                            "Local clock from Begin — not verified completion."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = IntentColors.Muted
                    )
                }
                is AttentionStrip.SecondaryLine.ObservedPackageMinutes -> {
                    Text(
                        text = secondary.text,
                        modifier = Modifier.semantics {
                            contentDescription = secondary.contentDescription
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = IntentColors.Ink,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Package foreground only — not lesson completion.",
                        style = MaterialTheme.typography.bodySmall,
                        color = IntentColors.Muted
                    )
                }
            }
        }

        if (nextAction.kind == NextActionKind.ACTIVE_SESSION &&
            nextAction.destinationAppLabel != null
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Destination · ${nextAction.destinationAppLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = IntentColors.Muted
            )
        }

        if (destinationLaunchNote != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = destinationLaunchNote,
                style = MaterialTheme.typography.bodySmall,
                color = IntentColors.Warm
            )
        }

        if (sessionPauseNote != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = sessionPauseNote,
                style = MaterialTheme.typography.bodySmall,
                color = if (sessionSoftPaused) IntentColors.Warm else IntentColors.Muted
            )
        }

        // Quiet grants inside the strip for active sessions only.
        if (nextAction.kind == NextActionKind.ACTIVE_SESSION && showGrantUsageAccess) {
            Spacer(modifier = Modifier.height(6.dp))
            IntentQuietButton(
                text = "Grant usage access",
                onClick = onGrantUsageAccess
            )
        }
        if (nextAction.kind == NextActionKind.ACTIVE_SESSION && observedEvidenceNote != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = observedEvidenceNote,
                style = MaterialTheme.typography.bodySmall,
                color = IntentColors.Muted
            )
            if (showDismissObserved) {
                Spacer(modifier = Modifier.height(2.dp))
                IntentQuietButton(
                    text = "Mark observation inaccurate",
                    onClick = onDismissObserved
                )
            }
        }
        if (nextAction.kind == NextActionKind.ACTIVE_SESSION && notificationNote != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = notificationNote,
                style = MaterialTheme.typography.bodySmall,
                color = IntentColors.Muted
            )
        }
        if (nextAction.kind == NextActionKind.ACTIVE_SESSION && showGrantNotificationAccess) {
            Spacer(modifier = Modifier.height(4.dp))
            IntentQuietButton(
                text = "Grant notification access",
                onClick = onGrantNotificationAccess
            )
        }
        if (nextAction.kind == NextActionKind.ACTIVE_SESSION && showRequestQuiet) {
            Spacer(modifier = Modifier.height(4.dp))
            IntentQuietButton(
                text = "Request quiet for this session",
                onClick = onRequestQuiet
            )
        }
        if (nextAction.kind == NextActionKind.ACTIVE_SESSION &&
            quietNote != null && quietNote.isNotBlank()
        ) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = quietNote,
                style = MaterialTheme.typography.bodySmall,
                color = IntentColors.Muted
            )
        }
        if (nextAction.kind == NextActionKind.ACTIVE_SESSION && showOpenDndSettings) {
            Spacer(modifier = Modifier.height(2.dp))
            IntentQuietButton(
                text = "Open system Do Not Disturb",
                onClick = onOpenDndSettings
            )
        }
        if (nextAction.kind == NextActionKind.ACTIVE_SESSION &&
            screenCaptureNote != null && screenCaptureNote.isNotBlank()
        ) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = screenCaptureNote,
                style = MaterialTheme.typography.bodySmall,
                color = IntentColors.Muted
            )
        }
        if (nextAction.kind == NextActionKind.ACTIVE_SESSION && showCaptureScreen) {
            Spacer(modifier = Modifier.height(2.dp))
            IntentQuietButton(
                text = "Capture screen",
                onClick = onCaptureScreen
            )
        }
        if (nextAction.kind == NextActionKind.ACTIVE_SESSION &&
            sessionNotificationRows.isNotEmpty()
        ) {
            sessionNotificationRows.forEach { (label, key) ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = IntentColors.Muted
                )
                if (onDismissNotificationRow != null) {
                    val parts = key.split("|", limit = 2)
                    if (parts.size == 2) {
                        Spacer(modifier = Modifier.height(2.dp))
                        IntentQuietButton(
                            text = "Dismiss",
                            onClick = { onDismissNotificationRow(parts[0], parts[1]) }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(15.dp))
        AttentionStripActions(
            nextAction = nextAction,
            deferPreviewLine = deferPreviewLine,
            onContinueExploring = onContinueExploring,
            onOpenIntention = onOpenIntention,
            onStartBuilder = onStartBuilder,
            onReviewProposal = onReviewProposal,
            onBegin = onBegin,
            onDefer = onDefer,
            onDiscuss = onDiscuss,
            onCompleteSession = onCompleteSession,
            onAbandonSession = onAbandonSession,
            onOpenDestination = onOpenDestination,
            onConfirmCheckIn = onConfirmCheckIn,
            onConfirmMidday = onConfirmMidday,
            onDeferMidday = onDeferMidday,
            onCheckOutKeepSchedule = onCheckOutKeepSchedule,
            onCheckOutDeferTomorrow = onCheckOutDeferTomorrow,
            showResumeSession = showResumeSession,
            onResumeSession = onResumeSession
        )
    }
}

@Composable
private fun AttentionStripActions(
    nextAction: NextAction,
    deferPreviewLine: String?,
    onContinueExploring: () -> Unit,
    onOpenIntention: () -> Unit,
    onStartBuilder: (() -> Unit)? = null,
    onReviewProposal: () -> Unit,
    onBegin: () -> Unit,
    onDefer: () -> Unit,
    onDiscuss: () -> Unit,
    onCompleteSession: () -> Unit,
    onAbandonSession: () -> Unit,
    onOpenDestination: () -> Unit,
    onConfirmCheckIn: () -> Unit,
    onConfirmMidday: () -> Unit,
    onDeferMidday: () -> Unit,
    onCheckOutKeepSchedule: () -> Unit,
    onCheckOutDeferTomorrow: () -> Unit,
    showResumeSession: Boolean = false,
    onResumeSession: () -> Unit = {}
) {
    when (nextAction.kind) {
        NextActionKind.CONTINUE_EXPLORING -> {
            IntentPrimaryButton(
                text = "Continue exploring",
                onClick = onContinueExploring
            )
        }
        NextActionKind.REVIEW_PROPOSAL -> {
            IntentPrimaryButton(
                text = "Review proposal",
                onClick = onReviewProposal
            )
        }
        NextActionKind.BEGIN -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IntentPrimaryButton(
                    text = nextAction.beginLabel ?: "Begin",
                    onClick = onBegin,
                    modifier = Modifier.weight(1.4f)
                )
                if (nextAction.deferLabel != null) {
                    IntentSecondaryButton(
                        text = nextAction.deferLabel,
                        onClick = onDefer,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            if (deferPreviewLine != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = deferPreviewLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = IntentColors.Muted
                )
            }
            if (nextAction.openDestinationLabel != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Begin also opens ${nextAction.destinationAppLabel ?: "the destination app"} when installed — opening an app is not lesson completion.",
                    style = MaterialTheme.typography.bodySmall,
                    color = IntentColors.Muted
                )
            }
            if (nextAction.discussLabel != null) {
                Spacer(modifier = Modifier.height(4.dp))
                IntentSecondaryButton(
                    text = nextAction.discussLabel,
                    onClick = onDiscuss
                )
            }
        }
        NextActionKind.ACTIVE_SESSION -> {
            if (showResumeSession) {
                IntentPrimaryButton(
                    text = "Resume",
                    onClick = onResumeSession
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            if (nextAction.openDestinationLabel != null) {
                IntentSecondaryButton(
                    text = nextAction.openDestinationLabel,
                    onClick = onOpenDestination
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (nextAction.completeLabel != null) {
                    IntentPrimaryButton(
                        text = nextAction.completeLabel,
                        onClick = onCompleteSession,
                        modifier = Modifier.weight(1.4f)
                    )
                }
                if (nextAction.abandonLabel != null) {
                    IntentSecondaryButton(
                        text = nextAction.abandonLabel,
                        onClick = onAbandonSession,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            if (nextAction.discussLabel != null) {
                Spacer(modifier = Modifier.height(2.dp))
                IntentQuietButton(
                    text = nextAction.discussLabel,
                    onClick = onDiscuss
                )
            }
        }
        NextActionKind.DISCUSS -> {
            IntentPrimaryButton(
                text = nextAction.discussLabel ?: "Discuss",
                onClick = onDiscuss
            )
        }
        NextActionKind.CHECK_IN -> {
            IntentPrimaryButton(
                text = nextAction.confirmLabel ?: "Confirm focus",
                onClick = onConfirmCheckIn
            )
            Spacer(modifier = Modifier.height(4.dp))
            IntentSecondaryButton(
                text = nextAction.adjustLabel ?: nextAction.discussLabel
                    ?: "Adjust in Discuss",
                onClick = onDiscuss
            )
        }
        NextActionKind.MIDDAY -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IntentPrimaryButton(
                    text = nextAction.confirmLabel ?: "Keep plan",
                    onClick = onConfirmMidday,
                    modifier = Modifier.weight(1.4f)
                )
                if (nextAction.adjustLabel != null || nextAction.deferLabel != null) {
                    IntentSecondaryButton(
                        text = nextAction.adjustLabel
                            ?: nextAction.deferLabel
                            ?: "Defer later",
                        onClick = onDeferMidday,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            IntentQuietButton(
                text = nextAction.discussLabel ?: "Discuss",
                onClick = onDiscuss
            )
        }
        NextActionKind.CHECK_OUT -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IntentPrimaryButton(
                    text = nextAction.confirmLabel ?: "Keep schedule",
                    onClick = onCheckOutKeepSchedule,
                    modifier = Modifier.weight(1.4f)
                )
                if (nextAction.adjustLabel != null || nextAction.deferLabel != null ||
                    nextAction.deferTomorrowLabel != null
                ) {
                    IntentSecondaryButton(
                        text = nextAction.adjustLabel
                            ?: nextAction.deferTomorrowLabel
                            ?: nextAction.deferLabel
                            ?: "Defer tomorrow",
                        onClick = onCheckOutDeferTomorrow,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            IntentQuietButton(
                text = nextAction.discussLabel ?: "Discuss",
                onClick = onDiscuss
            )
        }
        else -> {
            // Empty / NONE — send new users into the Intention Builder, not an empty space.
            if (onStartBuilder != null) {
                IntentPrimaryButton(
                    text = "Start a new intention",
                    onClick = onStartBuilder
                )
            } else {
                IntentPrimaryButton(
                    text = "Open your intention",
                    onClick = onOpenIntention
                )
            }
        }
    }
}

/**
 * Quiet grant / companion / capture notes below the strip — never competing cards.
 */
@Composable
private fun QuietGrantStrip(
    showGrantUsageAccess: Boolean,
    onGrantUsageAccess: () -> Unit,
    usageNote: String?,
    checkoutObservationNote: String?,
    onClearCheckoutNote: () -> Unit,
    overlayNote: String?,
    showGrantOverlay: Boolean,
    onGrantOverlay: () -> Unit,
    homeRoleNote: String?,
    showSetAsHome: Boolean,
    onSetAsHome: () -> Unit,
    floatingCompanionLabel: String?,
    floatingManualRouteNote: String?,
    /** OBSERVED | INFERRED when floater attention is an intervention (Gate D2). */
    floatingInterventionProvenance: String? = null,
    floaterTodayNote: String?,
    onOpenFloaterDrawer: () -> Unit,
    onCaptureThought: () -> Unit,
    thoughtCaptureNote: String?
) {
    if (homeRoleNote != null) {
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = homeRoleNote,
            style = MaterialTheme.typography.bodySmall,
            color = IntentColors.Muted,
            modifier = Modifier.semantics {
                contentDescription = "Home launcher status: $homeRoleNote"
            }
        )
        if (showSetAsHome) {
            Spacer(modifier = Modifier.height(4.dp))
            IntentQuietButton(
                text = "Set as HOME",
                onClick = onSetAsHome
            )
        }
    }
    if (checkoutObservationNote != null) {
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = checkoutObservationNote,
            style = MaterialTheme.typography.bodySmall,
            color = IntentColors.Muted
        )
        Spacer(modifier = Modifier.height(4.dp))
        IntentQuietButton(
            text = "Dismiss note",
            onClick = onClearCheckoutNote
        )
    }
    if (usageNote != null) {
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = usageNote,
            style = MaterialTheme.typography.bodySmall,
            color = IntentColors.Muted
        )
    }
    if (showGrantUsageAccess) {
        Spacer(modifier = Modifier.height(4.dp))
        IntentQuietButton(
            text = "Grant usage access",
            onClick = onGrantUsageAccess
        )
    }
    if (overlayNote != null) {
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = overlayNote,
            style = MaterialTheme.typography.bodySmall,
            color = IntentColors.Muted
        )
        if (showGrantOverlay) {
            Spacer(modifier = Modifier.height(4.dp))
            IntentQuietButton(
                text = "Grant overlay permission",
                onClick = onGrantOverlay
            )
        }
    }
    if (floaterTodayNote != null) {
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = floaterTodayNote,
            style = MaterialTheme.typography.bodySmall,
            color = IntentColors.Warm
        )
    }
    if (floatingCompanionLabel != null) {
        Spacer(modifier = Modifier.height(10.dp))
        val provenanceNote = floatingInterventionProvenance
            ?.let { " Provenance $it — never silent mix." }
            .orEmpty()
        Text(
            text = "Floating companion: $floatingCompanionLabel — same agent as Conversation." +
                provenanceNote +
                " Tap to Ask · long-press for Ask / Today / Capture.",
            style = MaterialTheme.typography.bodySmall,
            color = IntentColors.Muted
        )
    } else if (floatingManualRouteNote != null && overlayNote == null) {
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = floatingManualRouteNote,
            modifier = Modifier.semantics {
                contentDescription = floatingManualRouteNote
            },
            style = MaterialTheme.typography.bodySmall,
            color = IntentColors.Muted
        )
    }
    Spacer(modifier = Modifier.height(10.dp))
    IntentQuietButton(
        text = "Companion menu",
        onClick = onOpenFloaterDrawer
    )
    Spacer(modifier = Modifier.height(6.dp))
    IntentQuietButton(
        text = "Capture thought",
        onClick = onCaptureThought
    )
    if (thoughtCaptureNote != null) {
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = thoughtCaptureNote,
            style = MaterialTheme.typography.bodySmall,
            color = IntentColors.Success
        )
    }
}
