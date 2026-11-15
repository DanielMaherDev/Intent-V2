package com.intent.launcher.engine

/**
 * OS-156 — pure placement / flag policy so the floating companion never blocks
 * recents, gesture navigation, system permission dialogs, or the IME keyboard.
 *
 * No Android framework deps (unit-testable). [com.intent.launcher.floating.FloatingAgentService]
 * maps [Placement] onto [android.view.WindowManager.LayoutParams].
 *
 * Additive with Gate D floater sticky honesty (PR #53 / reclaim 0.0.109): this policy
 * only governs geometry and window flags while the bubble is shown; sticky / overlay-denied
 * resume remains owned by FloatingAgentRestartPolicy.
 */
object FloaterSystemUiPolicy {

    /** Matches [android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE]. */
    const val FLAG_NOT_FOCUSABLE = 0x00000008

    /**
     * Matches [android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL] —
     * touches outside the bubble pass through to permission dialogs / apps below.
     */
    const val FLAG_NOT_TOUCH_MODAL = 0x00000020

    /** Matches [android.view.Gravity.TOP] or [android.view.Gravity.END]. */
    const val GRAVITY_TOP = 0x30
    const val GRAVITY_END = 0x00800005
    const val GRAVITY_TOP_END = GRAVITY_TOP or GRAVITY_END

    const val BUBBLE_SIZE_DP = 56
    const val PREFERRED_MARGIN_DP = 16
    const val PREFERRED_TOP_DP = 120
    /** Extra padding beyond system insets so the hit target is not on the edge. */
    const val CLEARANCE_DP = 8
    /** Floor for bottom clearance when nav/gesture insets are missing (3-button recents). */
    const val MIN_BOTTOM_CLEARANCE_DP = 48

    data class Screen(
        val widthPx: Int,
        val heightPx: Int,
        val density: Float
    )

    /**
     * System chrome reported by WindowInsets (px). IME height is 0 when the keyboard is hidden.
     */
    data class Insets(
        val statusBar: Int = 0,
        val navBar: Int = 0,
        val gestureLeft: Int = 0,
        val gestureRight: Int = 0,
        val gestureBottom: Int = 0,
        val ime: Int = 0
    ) {
        val imeVisible: Boolean get() = ime > 0
    }

    data class Placement(
        val xPx: Int,
        val yPx: Int,
        val gravity: Int,
        /** False when the bubble would cover the IME and cannot be lifted clear. */
        val visible: Boolean,
        val reason: String
    )

    /** Overlay flags: never focusable (IME / dialogs keep input); never touch-modal. */
    fun overlayFlags(): Int = FLAG_NOT_FOCUSABLE or FLAG_NOT_TOUCH_MODAL

    fun bubbleSizePx(density: Float): Int = (BUBBLE_SIZE_DP * density).toInt()

    fun preferredMarginPx(density: Float): Int = (PREFERRED_MARGIN_DP * density).toInt()

    fun preferredTopPx(density: Float): Int = (PREFERRED_TOP_DP * density).toInt()

    fun clearancePx(density: Float): Int = (CLEARANCE_DP * density).toInt()

    fun minBottomClearancePx(density: Float): Int =
        (MIN_BOTTOM_CLEARANCE_DP * density).toInt()

    /**
     * Bottom inset the bubble must stay above: max of nav bar, gesture bar, IME, and a
     * floor large enough for 3-button recents when insets are thin.
     */
    fun bottomClearancePx(insets: Insets, density: Float): Int {
        val chrome = maxOf(insets.navBar, insets.gestureBottom, insets.ime)
        return maxOf(chrome, minBottomClearancePx(density)) + clearancePx(density)
    }

    fun sideClearanceEndPx(insets: Insets, density: Float): Int =
        maxOf(insets.gestureRight, preferredMarginPx(density)) + clearancePx(density)

    fun topClearancePx(insets: Insets, density: Float): Int =
        maxOf(insets.statusBar, clearancePx(density)) + clearancePx(density)

    /**
     * Clamp preferred TOP|END placement into the safe region.
     * When the IME is up and the safe band cannot fit the bubble, [Placement.visible] is false
     * so the service hides the view instead of covering the keyboard.
     */
    fun place(
        screen: Screen,
        insets: Insets,
        bubbleSizePx: Int = bubbleSizePx(screen.density),
        preferredMarginPx: Int = preferredMarginPx(screen.density),
        preferredTopPx: Int = preferredTopPx(screen.density)
    ): Placement {
        require(screen.widthPx > 0 && screen.heightPx > 0) { "screen metrics required" }
        require(bubbleSizePx > 0) { "bubble size required" }

        val density = screen.density
        val topMin = topClearancePx(insets, density)
        val bottomClear = bottomClearancePx(insets, density)
        val endMin = sideClearanceEndPx(insets, density)
        val yMax = screen.heightPx - bottomClear - bubbleSizePx

        if (yMax < topMin) {
            return Placement(
                xPx = maxOf(preferredMarginPx, endMin),
                yPx = topMin,
                gravity = GRAVITY_TOP_END,
                visible = false,
                reason = if (insets.imeVisible) {
                    "Hidden — keyboard open and no room above IME without covering it"
                } else {
                    "Hidden — safe band too small for bubble (nav / gesture / recents)"
                }
            )
        }

        val x = maxOf(preferredMarginPx, endMin)
        val y = preferredTopPx.coerceIn(topMin, yMax)
        val reason = buildString {
            append("TOP|END safe placement")
            if (insets.imeVisible) append("; lifted clear of IME")
            append("; clears gesture / nav / recents insets")
        }
        return Placement(
            xPx = x,
            yPx = y,
            gravity = GRAVITY_TOP_END,
            visible = true,
            reason = reason
        )
    }

    /**
     * True when a candidate bubble rect (screen coords, top-left origin) intersects the
     * bottom system / IME band — used by tests and service audits.
     */
    fun intersectsSystemChrome(
        bubbleLeft: Int,
        bubbleTop: Int,
        bubbleSizePx: Int,
        screen: Screen,
        insets: Insets
    ): Boolean {
        val bottomClear = bottomClearancePx(insets, screen.density)
        val chromeTop = screen.heightPx - bottomClear
        val bubbleBottom = bubbleTop + bubbleSizePx
        val bubbleRight = bubbleLeft + bubbleSizePx
        val sideHit =
            bubbleLeft < insets.gestureLeft + clearancePx(screen.density) ||
                bubbleRight > screen.widthPx - insets.gestureRight - clearancePx(screen.density)
        val bottomHit = bubbleBottom > chromeTop
        val topHit = bubbleTop < topClearancePx(insets, screen.density)
        return sideHit || bottomHit || topHit
    }

    fun documentsOs156(): String =
        "OS-156: FloatingAgentService uses FLAG_NOT_FOCUSABLE|FLAG_NOT_TOUCH_MODAL so the " +
            "IME and system permission dialogs keep focus and outside touches. " +
            "Placement clamps to status / gesture / nav / IME insets and hides the bubble when " +
            "the keyboard leaves no safe band — never covers recents, gesture nav, or the keyboard. " +
            "Additive with Gate D sticky honesty (PR #53): geometry only while overlay-granted."
}
