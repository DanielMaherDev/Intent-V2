package com.intent.launcher.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * OS-144 — back never strands the user or silently ends a live Discuss/voice path.
 */
class BackNavigationPolicyTest {

    private fun ctx(
        route: String?,
        canPop: Boolean = true,
        overlay: Boolean = false,
        live: Boolean = false
    ) = BackNavigationPolicy.Context(
        route = route,
        canPopBackStack = canPop,
        overlayOpen = overlay,
        liveEngagementActive = live
    )

    @Test
    fun homeRoot_consumesBack_neverFinishes() {
        assertEquals(
            BackNavigationPolicy.Action.CONSUME_STAY,
            BackNavigationPolicy.resolve(ctx("home", canPop = false))
        )
        assertEquals(
            BackNavigationPolicy.Action.CONSUME_STAY,
            BackNavigationPolicy.resolve(ctx("home", canPop = true))
        )
    }

    @Test
    fun nullRoute_treatedAsHome() {
        assertEquals(
            BackNavigationPolicy.Action.CONSUME_STAY,
            BackNavigationPolicy.resolve(ctx(null, canPop = false))
        )
    }

    @Test
    fun overlayOpen_dismissesFirst_evenOnConversationLive() {
        assertEquals(
            BackNavigationPolicy.Action.DISMISS_OVERLAY,
            BackNavigationPolicy.resolve(
                ctx("conversation", overlay = true, live = true)
            )
        )
        assertEquals(
            BackNavigationPolicy.Action.DISMISS_OVERLAY,
            BackNavigationPolicy.resolve(ctx("home", overlay = true))
        )
    }

    @Test
    fun drawer_navigatesHome() {
        assertEquals(
            BackNavigationPolicy.Action.NAVIGATE_HOME,
            BackNavigationPolicy.resolve(ctx("drawer", canPop = true))
        )
        assertEquals(
            BackNavigationPolicy.Action.NAVIGATE_HOME,
            BackNavigationPolicy.resolve(ctx("drawer", canPop = false))
        )
    }

    @Test
    fun intentionDetail_popsWhenStackExists() {
        assertEquals(
            BackNavigationPolicy.Action.POP_BACK_STACK,
            BackNavigationPolicy.resolve(ctx("intention", canPop = true))
        )
    }

    @Test
    fun intentionDetail_goesHomeWhenStackEmpty_neverStrands() {
        assertEquals(
            BackNavigationPolicy.Action.NAVIGATE_HOME,
            BackNavigationPolicy.resolve(ctx("intention", canPop = false))
        )
    }

    @Test
    fun conversation_withoutLive_popsOrHomes() {
        assertEquals(
            BackNavigationPolicy.Action.POP_BACK_STACK,
            BackNavigationPolicy.resolve(ctx("conversation", canPop = true, live = false))
        )
        assertEquals(
            BackNavigationPolicy.Action.NAVIGATE_HOME,
            BackNavigationPolicy.resolve(ctx("conversation", canPop = false, live = false))
        )
    }

    @Test
    fun conversation_withLive_requiresConfirm_neverSilentHangup() {
        assertEquals(
            BackNavigationPolicy.Action.CONFIRM_LEAVE_LIVE,
            BackNavigationPolicy.resolve(ctx("conversation", canPop = true, live = true))
        )
        assertEquals(
            BackNavigationPolicy.Action.CONFIRM_LEAVE_LIVE,
            BackNavigationPolicy.resolve(ctx("conversation", canPop = false, live = true))
        )
        assertTrue(BackNavigationPolicy.conversationNeedsLeaveConfirm(true))
        assertFalse(BackNavigationPolicy.conversationNeedsLeaveConfirm(false))
    }

    @Test
    fun sessionCardRouteIsHome_backDoesNotAbandon() {
        // SessionCard lives on Home next-action chrome — back must CONSUME_STAY.
        assertEquals(
            BackNavigationPolicy.Action.CONSUME_STAY,
            BackNavigationPolicy.resolve(ctx("home", live = false))
        )
    }

    @Test
    fun trackerReportsBuilder_popOrHome() {
        listOf("tracker", "reports", "builder", "triage", "connections", "proposal").forEach { route ->
            assertEquals(
                route,
                BackNavigationPolicy.Action.POP_BACK_STACK,
                BackNavigationPolicy.resolve(ctx(route, canPop = true))
            )
            assertEquals(
                route,
                BackNavigationPolicy.Action.NAVIGATE_HOME,
                BackNavigationPolicy.resolve(ctx(route, canPop = false))
            )
        }
    }
}
