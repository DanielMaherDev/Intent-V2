package com.intent.launcher.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FE-C3 field-type depth — seven worksheet types + honest numeric formatting.
 */
class TrackerFieldTypeTest {

    @Test
    fun shapeCatalog_coversAllSevenFieldTypes() {
        assertTrue(TrackerModules.coversAllFieldTypes())
        val byType = TrackerFieldType.entries.associateWith { type ->
            listOf("language", "marathon", "business", "quit_smoking", "chess_rating", null)
                .flatMap { TrackerModules.forShape(it) }
                .filter { it.fieldType == type }
        }
        TrackerFieldType.entries.forEach { type ->
            assertTrue("Missing modules for $type", byType.getValue(type).isNotEmpty())
        }
    }

    @Test
    fun language_hasDurationRatingAndDateTime() {
        val modules = TrackerModules.forShape("language")
        assertEquals(TrackerFieldType.DURATION, modules.first { it.id == "practice_log" }.fieldType)
        assertEquals(
            TrackerFieldType.RATING,
            modules.first { it.id == "confidence_note" }.fieldType
        )
        assertEquals(
            TrackerFieldType.DATE_TIME,
            modules.first { it.id == "practice_when" }.fieldType
        )
    }

    @Test
    fun quitSmoking_hasNumericSingleAndMulti() {
        val modules = TrackerModules.forShape("quit_smoking")
        assertEquals(
            TrackerFieldType.NUMERIC,
            modules.first { it.id == "cigarettes_today" }.fieldType
        )
        assertEquals(
            TrackerFieldType.SINGLE_CHOICE,
            modules.first { it.id == "craving_log" }.fieldType
        )
        assertEquals(
            TrackerFieldType.MULTIPLE_CHOICE,
            modules.first { it.id == "support_check" }.fieldType
        )
    }

    @Test
    fun formatNumeric_rejectsBlankAndNonNumeric() {
        assertNull(TrackerFieldControls.formatNumeric("", "km"))
        assertNull(TrackerFieldControls.formatNumeric("  ", null))
        assertNull(TrackerFieldControls.formatNumeric("abc", "km"))
        assertEquals("5 km", TrackerFieldControls.formatNumeric("5", "km"))
        assertEquals("5", TrackerFieldControls.formatNumeric("5", null))
    }

    @Test
    fun formatDuration_andDateTime_andMulti() {
        assertEquals("20 min", TrackerFieldControls.formatDurationMinutes("20"))
        assertEquals("20 min", TrackerFieldControls.formatDurationMinutes("20 min"))
        assertNull(TrackerFieldControls.formatDurationMinutes(""))
        assertEquals(
            "Applies · Today · 2023-11-16",
            TrackerFieldControls.formatDateTimeValue("Today", "2023-11-16")
        )
        assertNull(TrackerFieldControls.joinMultiple(emptyList()))
        assertEquals(
            "Told someone · Changed routine",
            TrackerFieldControls.joinMultiple(listOf("Told someone", "Changed routine"))
        )
        assertTrue(TrackerFieldControls.HONESTY_NOTE.contains("not coerced"))
    }
}
