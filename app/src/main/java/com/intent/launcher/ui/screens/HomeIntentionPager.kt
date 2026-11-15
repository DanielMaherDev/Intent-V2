package com.intent.launcher.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.intent.launcher.domain.HomeIntentionSwipe
import kotlinx.coroutines.launch

/**
 * OS-137 — short horizontal swipe between Home and the Intention drawer.
 *
 * Additive shell: screens keep their own chrome; this pager only hosts them.
 * Nested vertical scroll on each page stays inside the page; system back from
 * the drawer page returns Home before leaving the launcher space.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeIntentionPager(
    initialPage: Int,
    home: @Composable (openIntentions: () -> Unit) -> Unit,
    drawer: @Composable (openHome: () -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    val startPage = initialPage.coerceIn(
        HomeIntentionSwipe.PAGE_HOME,
        HomeIntentionSwipe.PAGE_DRAWER
    )
    val pagerState = rememberPagerState(
        initialPage = startPage,
        pageCount = { HomeIntentionSwipe.PAGE_COUNT }
    )
    val scope = rememberCoroutineScope()

    LaunchedEffect(startPage) {
        if (pagerState.currentPage != startPage && !pagerState.isScrollInProgress) {
            pagerState.scrollToPage(startPage)
        }
    }

    val openIntentions: () -> Unit = {
        scope.launch {
            pagerState.animateScrollToPage(HomeIntentionSwipe.PAGE_DRAWER)
        }
    }
    val openHome: () -> Unit = {
        scope.launch {
            pagerState.animateScrollToPage(HomeIntentionSwipe.PAGE_HOME)
        }
    }

    BackHandler(enabled = pagerState.settledPage == HomeIntentionSwipe.PAGE_DRAWER) {
        openHome()
    }

    val pageDesc = when (pagerState.settledPage) {
        HomeIntentionSwipe.PAGE_DRAWER ->
            "Intention drawer. Swipe right or press back to return home."
        else ->
            "Home. Swipe left to open intentions."
    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier
            .fillMaxSize()
            .semantics { contentDescription = pageDesc },
        beyondBoundsPageCount = 0,
        flingBehavior = PagerDefaults.flingBehavior(
            state = pagerState,
            snapPositionalThreshold = HomeIntentionSwipe.SNAP_POSITIONAL_THRESHOLD
        ),
        pageSpacing = 0.dp,
        userScrollEnabled = true
    ) { page ->
        when (page) {
            HomeIntentionSwipe.PAGE_DRAWER -> drawer(openHome)
            else -> home(openIntentions)
        }
    }
}
