package com.intent.launcher.domain

/**
 * OS-137 — Home ↔ Intention drawer as adjacent launcher pages.
 *
 * Pure page/settle helpers so unit tests can lock short-swipe thresholds without
 * inventing OS-level gesture overrides. Wiring uses Compose [HorizontalPager]
 * inside the launcher; system nav stays untouched.
 */
object HomeIntentionSwipe {
    const val PAGE_HOME = 0
    const val PAGE_DRAWER = 1
    const val PAGE_COUNT = 2

    /** Fraction of page width that settles to the next page (short swipe). */
    const val SNAP_POSITIONAL_THRESHOLD = 0.22f

    fun pageLabel(page: Int): String = when (page) {
        PAGE_HOME -> "home"
        PAGE_DRAWER -> "intentions"
        else -> "unknown"
    }

    fun isHome(page: Int): Boolean = page == PAGE_HOME

    fun isDrawer(page: Int): Boolean = page == PAGE_DRAWER

    /**
     * Settled page after a horizontal drag, given start page and drag as a
     * fraction of pager width (negative = toward drawer / trailing edge).
     */
    fun settlePage(
        fromPage: Int,
        dragFractionOfWidth: Float,
        threshold: Float = SNAP_POSITIONAL_THRESHOLD
    ): Int {
        val clampedFrom = fromPage.coerceIn(PAGE_HOME, PAGE_DRAWER)
        val target = when {
            dragFractionOfWidth <= -threshold -> clampedFrom + 1
            dragFractionOfWidth >= threshold -> clampedFrom - 1
            else -> clampedFrom
        }
        return target.coerceIn(PAGE_HOME, PAGE_DRAWER)
    }
}
