package com.intent.launcher.engine

import com.intent.launcher.domain.NextAction
import com.intent.launcher.domain.NextActionKind

/**
 * Gate D / OS-201 — pure TalkBack label builders (no Compose / View deps).
 * Harden thin visible copy into spoken container descriptions without redesigning UI.
 */
object TalkBackLabels {

    /** SessionCard / active-session strip container. */
    fun sessionCardContainer(
        statusLabel: String,
        sessionTitle: String,
        intentionTitle: String,
        softPaused: Boolean = false
    ): String {
        val status = statusLabel.trim().ifBlank {
            if (softPaused) "Away from app" else "In progress"
        }
        val title = sessionTitle.trim().ifBlank { "Active session" }
        val intention = intentionTitle.trim()
        return buildString {
            append("Session card. ")
            append(status)
            append(". ")
            append(title)
            if (intention.isNotEmpty() && !intention.equals(title, ignoreCase = true)) {
                append(". Intention: ")
                append(intention)
            }
            append(". Complete and Abandon always available.")
        }
    }

    /**
     * Home next-action / attention strip container.
     * When visible labels are thin (blank / very short), expand with kind context.
     */
    fun attentionStripContainer(
        nextAction: NextAction,
        smallLabel: String,
        title: String,
        support: String,
        softPaused: Boolean = false
    ): String {
        if (nextAction.kind == NextActionKind.ACTIVE_SESSION) {
            return sessionCardContainer(
                statusLabel = smallLabel,
                sessionTitle = title,
                intentionTitle = support,
                softPaused = softPaused
            )
        }
        val spokenStatus = expandThin(smallLabel, defaultForKind(nextAction.kind).status)
        val spokenTitle = expandThin(title, defaultForKind(nextAction.kind).title)
        val spokenSupport = support.trim().ifBlank { defaultForKind(nextAction.kind).support }
        return buildString {
            append("Next action. ")
            append(spokenStatus)
            append(". ")
            append(spokenTitle)
            if (spokenSupport.isNotEmpty()) {
                append(". ")
                append(spokenSupport)
            }
        }
    }

    /** Overlay bubble FrameLayout — role + state; tap / long-press hints. */
    fun floaterBubble(state: FloatingAgentState): String {
        val label = FloatingCompanion.labelFor(state)
        return "INTENT companion. $label. Same agent as Conversation. " +
            "Tap to Ask. Long-press for Ask, Today, or Capture."
    }

    /**
     * Honest overlay-denied / service-stopped fallback copy (OS-155).
     * Never claims a floating bubble or overlay grant.
     */
    fun overlayDeniedFallbackNote(): String =
        "Overlay permission not granted — no floating bubble. " +
            "Use Home’s Companion menu, Discuss, or Agent for the same Conversation. " +
            "Grant overlay only from system Settings if you want the bubble."

    fun serviceStoppedFallbackNote(): String =
        "Floating companion is not running. " +
            "Use Home’s Companion menu, Discuss, or the INTENT companion notification when shown. " +
            "Overlay is not invented — grant it in system Settings to show the bubble again."

    private data class KindDefaults(val status: String, val title: String, val support: String)

    private fun defaultForKind(kind: NextActionKind): KindDefaults =
        when (kind) {
            NextActionKind.CONTINUE_EXPLORING -> KindDefaults(
                status = "Still taking shape",
                title = "Continue exploring your intention",
                support = "Open your intention space to keep shaping it."
            )
            NextActionKind.REVIEW_PROPOSAL -> KindDefaults(
                status = "Ready for your review",
                title = "Review proposal",
                support = "Approve or adjust before anything is scheduled."
            )
            NextActionKind.BEGIN -> KindDefaults(
                status = "Relevant now",
                title = "Begin your session",
                support = "Start when you are ready, or discuss first."
            )
            NextActionKind.DEFER -> KindDefaults(
                status = "Later",
                title = "Defer this session",
                support = "Confirm a later window when you are ready."
            )
            NextActionKind.DISCUSS -> KindDefaults(
                status = "Upcoming",
                title = "Discuss when ready",
                support = "Talk it through in Conversation."
            )
            NextActionKind.CHECK_IN -> KindDefaults(
                status = "Morning check-in",
                title = "Today's focus",
                support = "Confirm today's focus, or adjust it in Discuss."
            )
            NextActionKind.CHECK_OUT -> KindDefaults(
                status = "Evening check-out",
                title = "How did today go?",
                support = "Report what happened, then keep or defer."
            )
            NextActionKind.ACTIVE_SESSION -> KindDefaults(
                status = "In progress",
                title = "Active session",
                support = "Complete or Abandon when finished."
            )
            NextActionKind.NONE -> KindDefaults(
                status = "Your space",
                title = "Where would you like to begin?",
                support = "Your space is ready when you are."
            )
        }

    /** Treat blank or ≤2-char labels as thin and replace with [fallback]. */
    fun expandThin(visible: String, fallback: String): String {
        val t = visible.trim()
        return if (t.length <= 2) fallback else t
    }
}
