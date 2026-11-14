package com.intent.launcher.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionElapsedTest {

    @Test
    fun format_zeroAndNegativeClampToZero() {
        assertEquals("0:00", SessionElapsed.format(1_000L, 1_000L))
        assertEquals("0:00", SessionElapsed.format(2_000L, 1_000L))
    }

    @Test
    fun format_minutesAndSeconds() {
        assertEquals("0:05", SessionElapsed.format(0L, 5_000L))
        assertEquals("1:05", SessionElapsed.format(0L, 65_000L))
        assertEquals("59:59", SessionElapsed.format(0L, 3_599_000L))
    }

    @Test
    fun format_hours() {
        assertEquals("1:00:00", SessionElapsed.format(0L, 3_600_000L))
        assertEquals("1:02:03", SessionElapsed.format(0L, 3_723_000L))
    }

    @Test
    fun label_prefixesElapsed() {
        assertEquals("Elapsed 5:03", SessionElapsed.label(0L, 303_000L))
    }
}
