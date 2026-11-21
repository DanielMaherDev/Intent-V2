package com.intent.launcher.engine

/**
 * Builder / Conversation choice sets prefer predefined options plus open
 * **Other** / **Not sure** / **Skip for now** paths (worksheet §3.4 / Gate D).
 * Voice and tap resolve through the same answer command once the value is known.
 */
object ChoiceOptions {
    const val OTHER = "Other"
    const val NOT_SURE = "Not sure"
    const val SKIP_FOR_NOW = "Skip for now"

    /** Append [OTHER] when missing (case-insensitive). Preserves existing order. */
    fun withOther(options: List<String>): List<String> {
        if (options.isEmpty()) return listOf(OTHER)
        val hasOther = options.any { isOther(it) }
        return if (hasOther) options else options + OTHER
    }

    /**
     * Closed sets for adaptive Ask cards: predefined + Other + Not sure + Skip.
     * Dedupes case-insensitively while preserving first-seen order.
     */
    fun withDeferralOptions(options: List<String>): List<String> {
        val base = withOther(options)
        val out = base.toMutableList()
        if (out.none { isNotSure(it) }) out += NOT_SURE
        if (out.none { isSkip(it) }) out += SKIP_FOR_NOW
        return out
    }

    fun isOther(label: String): Boolean =
        label.trim().equals(OTHER, ignoreCase = true)

    fun isNotSure(label: String): Boolean =
        label.trim().equals(NOT_SURE, ignoreCase = true) ||
            label.trim().equals("I'm not sure", ignoreCase = true) ||
            label.trim().equals("Not sure yet", ignoreCase = true)

    fun isSkip(label: String): Boolean =
        label.trim().equals(SKIP_FOR_NOW, ignoreCase = true) ||
            label.trim().equals("Skip", ignoreCase = true)

    fun isDeferral(label: String): Boolean = isNotSure(label) || isSkip(label)

    /**
     * Resolve a tapped / spoken label: predefined options return as-is;
     * Other never counts as a final value — callers collect free text instead.
     * Not sure / Skip are explicit deferrals (no fact write).
     */
    fun resolveSelection(label: String, options: List<String>): Selection {
        val trimmed = label.trim()
        if (trimmed.isEmpty()) return Selection.Empty
        if (isOther(trimmed)) return Selection.NeedsFreeText
        if (isNotSure(trimmed)) return Selection.NotSure
        if (isSkip(trimmed)) return Selection.Skip
        val match = options.firstOrNull {
            !isOther(it) && !isDeferral(it) && it.equals(trimmed, ignoreCase = true)
        }
        return if (match != null) Selection.Chosen(match) else Selection.FreeText(trimmed)
    }

    sealed class Selection {
        data object Empty : Selection()
        data object NeedsFreeText : Selection()
        data object NotSure : Selection()
        data object Skip : Selection()
        data class Chosen(val value: String) : Selection()
        /** Typed / spoken value outside the listed chips (treated as Other). */
        data class FreeText(val value: String) : Selection()
    }
}
