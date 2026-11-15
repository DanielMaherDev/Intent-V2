package com.intent.launcher.engine

/**
 * Shipped deep entries for floater / companion / system honesty docs (Gate D1 leftover).
 *
 * Not a typed NavArgs graph for every worksheet shortcut — companion + overlay
 * intents are the real deep entries. Safe fallbacks live in [MainActivity]
 * (`consumeCompanionIntent`) and [BackNavigationPolicy].
 *
 * Pure inventory for JVM tests + docs; no Android framework deps.
 */
object DeepLinkEntries {

    data class Entry(
        /** Intent action string (or logical key for drawer-only routes). */
        val action: String,
        /** Human label (Ask / Today / Capture / History / …). */
        val label: String,
        /** NavHost route, overlay sheet, or system settings action. */
        val destination: String,
        /** Honesty note — what this entry does *not* invent. */
        val honestyNote: String
    )

    /** Floater long-press drawer items → same agent as Conversation. */
    val FLOATER_DRAWER: List<Entry> = listOf(
        Entry(
            action = "com.intent.launcher.OPEN_CONVERSATION",
            label = "Ask",
            destination = "conversation",
            honestyNote = "Same Conversation agent / chips path — not a second bot or live LLM."
        ),
        Entry(
            action = "com.intent.launcher.OPEN_FLOATER_DRAWER→TODAY",
            label = "Today",
            destination = FloaterActionDrawer.TODAY_ROUTE,
            honestyNote = "Opens Today planned/completed/deferred — not chat prose."
        ),
        Entry(
            action = "com.intent.launcher.CAPTURE_THOUGHT",
            label = "Capture",
            destination = "thought_capture_sheet",
            honestyNote = "Raw SAID thought only — no summary, OCR, or lesson claim."
        ),
        Entry(
            action = "com.intent.launcher.OPEN_FLOATER_DRAWER→HISTORY",
            label = "History",
            destination = FloaterActionDrawer.HISTORY_ROUTE,
            honestyNote = "Local transaction History / undo — not cloud sync."
        )
    )

    /** Explicit companion intents consumed by MainActivity. */
    val COMPANION_INTENTS: List<Entry> = listOf(
        Entry(
            action = "com.intent.launcher.OPEN_CONVERSATION",
            label = "Open Conversation",
            destination = "conversation",
            honestyNote = "Optional intervention job/turn extras focus the same queued turn."
        ),
        Entry(
            action = "com.intent.launcher.OPEN_FLOATER_DRAWER",
            label = "Open floater drawer",
            destination = "floater_action_drawer",
            honestyNote = "Surfaces Ask / Today / Capture / History — same agent."
        ),
        Entry(
            action = "com.intent.launcher.CAPTURE_THOUGHT",
            label = "Capture thought",
            destination = "thought_capture_sheet",
            honestyNote = "Direct Capture without opening the drawer first."
        )
    )

    /** System deep links offered for honesty (not INTENT silence / completion). */
    val SYSTEM_HONESTY: List<Entry> = listOf(
        Entry(
            action = QuietModeProbe.DND_SETTINGS_ACTION,
            label = "System Do Not Disturb",
            destination = QuietModeProbe.DND_SETTINGS_ACTION,
            honestyNote = "OEM DND UI — INTENT never claims silence applied via quiet_mode."
        )
    )

    fun floaterLabels(): List<String> = FLOATER_DRAWER.map { it.label }

    fun allShipped(): List<Entry> = FLOATER_DRAWER + COMPANION_INTENTS + SYSTEM_HONESTY
}
