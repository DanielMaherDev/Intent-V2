package com.intent.launcher.domain

/**
 * Worksheet §11 field types — tracker controls suited to the value.
 * Values remain user SAID text; never coerce blank → zero / default choice.
 */
enum class TrackerFieldType {
    NUMERIC,
    YES_NO,
    SINGLE_CHOICE,
    RATING,
    DURATION,
    DATE_TIME,
    MULTIPLE_CHOICE
}

object TrackerFieldControls {

    val RATING_VALUES: List<String> = listOf("1", "2", "3", "4", "5")

    val YES_NO_VALUES: List<String> = listOf("Yes", "No")

    fun typeLabel(type: TrackerFieldType): String =
        when (type) {
            TrackerFieldType.NUMERIC -> "Numeric"
            TrackerFieldType.YES_NO -> "Yes / no"
            TrackerFieldType.SINGLE_CHOICE -> "Single choice"
            TrackerFieldType.RATING -> "Rating"
            TrackerFieldType.DURATION -> "Duration"
            TrackerFieldType.DATE_TIME -> "Date / time"
            TrackerFieldType.MULTIPLE_CHOICE -> "Multiple choice"
        }

    /**
     * Normalize a user-entered numeric/duration string with optional [unit].
     * Returns null when blank or not a finite number — caller must not save.
     */
    fun formatNumeric(raw: String, unit: String?): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val number = trimmed.toDoubleOrNull() ?: return null
        if (!number.isFinite()) return null
        val rendered = if (number == number.toLong().toDouble()) {
            number.toLong().toString()
        } else {
            trimmed
        }
        return if (unit.isNullOrBlank()) rendered else "$rendered $unit"
    }

    fun formatDurationMinutes(raw: String): String? {
        val trimmed = raw.trim().removeSuffix("min").removeSuffix("minutes").trim()
        val mins = formatNumeric(trimmed, null) ?: return null
        return "$mins min"
    }

    fun formatDateTimeValue(dayLabel: String, localDate: String): String =
        "Applies · $dayLabel · $localDate"

    fun joinMultiple(selected: Collection<String>): String? {
        val cleaned = selected.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (cleaned.isEmpty()) return null
        return cleaned.joinToString(" · ")
    }

    const val HONESTY_NOTE: String =
        "Controls match field type; save only what you enter. " +
            "Blank is not coerced to zero or a default choice."
}
