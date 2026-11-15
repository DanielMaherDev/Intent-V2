package com.intent.launcher.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * OS-156 — floater yields to recents, gesture nav, permission dialogs, and IME (no device).
 */
class FloaterSystemUiPolicyTest {

    private val density = 2.5f
    private val screen = FloaterSystemUiPolicy.Screen(
        widthPx = 1080,
        heightPx = 2400,
        density = density
    )
    private val bubble = FloaterSystemUiPolicy.bubbleSizePx(density)

    @Test
    fun overlayFlags_notFocusableAndNotTouchModal() {
        val flags = FloaterSystemUiPolicy.overlayFlags()
        assertTrue(flags and FloaterSystemUiPolicy.FLAG_NOT_FOCUSABLE != 0)
        assertTrue(flags and FloaterSystemUiPolicy.FLAG_NOT_TOUCH_MODAL != 0)
    }

    @Test
    fun defaultPlacement_clearsBottomGestureAndRecentsBand() {
        val insets = FloaterSystemUiPolicy.Insets(
            statusBar = 80,
            navBar = 0,
            gestureBottom = 96,
            gestureRight = 48
        )
        val p = FloaterSystemUiPolicy.place(screen, insets)
        assertTrue(p.visible)
        assertEquals(FloaterSystemUiPolicy.GRAVITY_TOP_END, p.gravity)
        assertTrue(p.xPx >= insets.gestureRight)
        val bubbleBottom = p.yPx + bubble
        val chromeTop = screen.heightPx - FloaterSystemUiPolicy.bottomClearancePx(insets, density)
        assertTrue(bubbleBottom <= chromeTop)
        assertFalse(
            FloaterSystemUiPolicy.intersectsSystemChrome(
                bubbleLeft = screen.widthPx - p.xPx - bubble,
                bubbleTop = p.yPx,
                bubbleSizePx = bubble,
                screen = screen,
                insets = insets
            )
        )
    }

    @Test
    fun threeButtonNav_usesMinBottomClearanceForRecents() {
        val insets = FloaterSystemUiPolicy.Insets(statusBar = 60, navBar = 40)
        val p = FloaterSystemUiPolicy.place(screen, insets)
        assertTrue(p.visible)
        val bubbleBottom = p.yPx + bubble
        val minClear = FloaterSystemUiPolicy.minBottomClearancePx(density) +
            FloaterSystemUiPolicy.clearancePx(density)
        assertTrue(bubbleBottom <= screen.heightPx - minClear)
    }

    @Test
    fun imeOpen_liftsBubbleAboveKeyboard() {
        val ime = 900
        val insets = FloaterSystemUiPolicy.Insets(
            statusBar = 80,
            gestureBottom = 48,
            ime = ime
        )
        val p = FloaterSystemUiPolicy.place(screen, insets)
        assertTrue(p.visible)
        assertTrue(p.reason.contains("IME", ignoreCase = true))
        val bubbleBottom = p.yPx + bubble
        val chromeTop = screen.heightPx - FloaterSystemUiPolicy.bottomClearancePx(insets, density)
        assertTrue(bubbleBottom <= chromeTop)
        assertTrue(chromeTop <= screen.heightPx - ime)
    }

    @Test
    fun imeTooTall_hidesBubbleInsteadOfCoveringKeyboard() {
        val insets = FloaterSystemUiPolicy.Insets(
            statusBar = 200,
            ime = screen.heightPx - 250 // almost full screen
        )
        val p = FloaterSystemUiPolicy.place(screen, insets)
        assertFalse(p.visible)
        assertTrue(p.reason.contains("keyboard", ignoreCase = true))
    }

    @Test
    fun preferredTop_clampedBelowStatusBar() {
        val insets = FloaterSystemUiPolicy.Insets(statusBar = 400)
        val preferred = FloaterSystemUiPolicy.preferredTopPx(density) // 120dp → 300px
        assertTrue(preferred < 400)
        val p = FloaterSystemUiPolicy.place(screen, insets)
        assertTrue(p.visible)
        assertTrue(p.yPx >= FloaterSystemUiPolicy.topClearancePx(insets, density))
    }

    @Test
    fun documentsOs156_mentionsFlagsAndGateD() {
        val doc = FloaterSystemUiPolicy.documentsOs156()
        assertTrue(doc.contains("FLAG_NOT_FOCUSABLE"))
        assertTrue(doc.contains("FLAG_NOT_TOUCH_MODAL"))
        assertTrue(doc.contains("IME") || doc.contains("keyboard", ignoreCase = true))
        assertTrue(doc.contains("recents", ignoreCase = true) || doc.contains("gesture", ignoreCase = true))
        assertTrue(doc.contains("Gate D") || doc.contains("#53"))
        assertFalse(doc.contains("always covers keyboard", ignoreCase = true))
    }
}
