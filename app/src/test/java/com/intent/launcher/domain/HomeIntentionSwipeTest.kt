package com.intent.launcher.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeIntentionSwipeTest {

    @Test
    fun pageLabels_matchBottomNavKeys() {
        assertEquals("home", HomeIntentionSwipe.pageLabel(HomeIntentionSwipe.PAGE_HOME))
        assertEquals("intentions", HomeIntentionSwipe.pageLabel(HomeIntentionSwipe.PAGE_DRAWER))
        assertTrue(HomeIntentionSwipe.isHome(HomeIntentionSwipe.PAGE_HOME))
        assertTrue(HomeIntentionSwipe.isDrawer(HomeIntentionSwipe.PAGE_DRAWER))
        assertFalse(HomeIntentionSwipe.isDrawer(HomeIntentionSwipe.PAGE_HOME))
    }

    @Test
    fun shortSwipeLeft_fromHome_opensDrawer() {
        assertEquals(
            HomeIntentionSwipe.PAGE_DRAWER,
            HomeIntentionSwipe.settlePage(
                fromPage = HomeIntentionSwipe.PAGE_HOME,
                dragFractionOfWidth = -HomeIntentionSwipe.SNAP_POSITIONAL_THRESHOLD
            )
        )
        assertEquals(
            HomeIntentionSwipe.PAGE_DRAWER,
            HomeIntentionSwipe.settlePage(
                fromPage = HomeIntentionSwipe.PAGE_HOME,
                dragFractionOfWidth = -0.5f
            )
        )
    }

    @Test
    fun shortSwipeRight_fromDrawer_returnsHome() {
        assertEquals(
            HomeIntentionSwipe.PAGE_HOME,
            HomeIntentionSwipe.settlePage(
                fromPage = HomeIntentionSwipe.PAGE_DRAWER,
                dragFractionOfWidth = HomeIntentionSwipe.SNAP_POSITIONAL_THRESHOLD
            )
        )
        assertEquals(
            HomeIntentionSwipe.PAGE_HOME,
            HomeIntentionSwipe.settlePage(
                fromPage = HomeIntentionSwipe.PAGE_DRAWER,
                dragFractionOfWidth = 0.4f
            )
        )
    }

    @Test
    fun subThresholdDrag_staysOnPage() {
        val under = HomeIntentionSwipe.SNAP_POSITIONAL_THRESHOLD - 0.01f
        assertEquals(
            HomeIntentionSwipe.PAGE_HOME,
            HomeIntentionSwipe.settlePage(
                fromPage = HomeIntentionSwipe.PAGE_HOME,
                dragFractionOfWidth = -under
            )
        )
        assertEquals(
            HomeIntentionSwipe.PAGE_DRAWER,
            HomeIntentionSwipe.settlePage(
                fromPage = HomeIntentionSwipe.PAGE_DRAWER,
                dragFractionOfWidth = under
            )
        )
    }

    @Test
    fun settle_clampsAtEnds() {
        assertEquals(
            HomeIntentionSwipe.PAGE_HOME,
            HomeIntentionSwipe.settlePage(
                fromPage = HomeIntentionSwipe.PAGE_HOME,
                dragFractionOfWidth = 0.9f
            )
        )
        assertEquals(
            HomeIntentionSwipe.PAGE_DRAWER,
            HomeIntentionSwipe.settlePage(
                fromPage = HomeIntentionSwipe.PAGE_DRAWER,
                dragFractionOfWidth = -0.9f
            )
        )
        assertEquals(
            HomeIntentionSwipe.PAGE_HOME,
            HomeIntentionSwipe.settlePage(fromPage = -3, dragFractionOfWidth = 0f)
        )
        assertEquals(
            HomeIntentionSwipe.PAGE_DRAWER,
            HomeIntentionSwipe.settlePage(fromPage = 9, dragFractionOfWidth = 0f)
        )
    }
}
