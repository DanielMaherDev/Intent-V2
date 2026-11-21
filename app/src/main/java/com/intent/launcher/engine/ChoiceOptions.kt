package com.intent.launcher.engine

/**
 * Builder / Conversation choice sets prefer predefined options plus an open
 * **Other** path (worksheet §3.4 / Gate D). Voice and tap resolve through the
 * same answer command once the value is known.
 */
object ChoiceOptions {
    const val OTHER = "Other"

    /** Append [OTHER] when missing (case-insensitive). Preserves existing order. */
    fun withOther(options: List<String>): List<String> {
        if (options.isEmpty()) return listOf(OTHER)
        val hasOther = options.any { isOther(it) }
        return if (hasOther) options else options + OTHER
    }

    fun isOther(label: String): Boolean =
        label.trim().equals(OTHER, ignoreCase = true)

    /**
     * Resolve a tapped / spoken label: predefined options return as-is;
     * Other never counts as a final value — callers collect free text instead.
     */
    fun resolveSelection(label: String, options: List<String>): Selection {
        val trimmed = label.trim()
        if (trimmed.isEmpty()) return Selection.Empty
        if (isOther(trimmed)) return Selection.NeedsFreeText
        val match = options.firstOrNull {
            !isOther(it) && it.equals(trimmed, ignoreCase = true)
        }
        return if (match != null) Selection.Chosen(match) else Selection.FreeText(trimmed)
    }

    sealed class Selection {
        data object Empty : Selection()
        data object NeedsFreeText : Selection()
        data class Chosen(val value: String) : Selection()
        /** Typed / spoken value outside the listed chips (treated as Other). */
        data class FreeText(val value: String) : Selection()
    }
}
