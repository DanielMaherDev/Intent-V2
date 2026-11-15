package com.intent.launcher.domain

/**
 * Intention drawer order + hold-for-apps affinity helpers.
 * Held packages are user-confirmed SAID bindings — never UsageStats auto-rank.
 */
object IntentionOrdering {

    /** Intentions in drawer order (explicit order, then any remainder). */
    fun ordered(snapshot: IntentionSnapshot): List<Intention> {
        if (snapshot.intentions.isEmpty()) return emptyList()
        val byId = snapshot.intentions.associateBy { it.id }
        val order = snapshot.intentionOrder.ifEmpty { snapshot.intentions.map { it.id } }
        val seen = linkedSetOf<String>()
        val result = mutableListOf<Intention>()
        for (id in order) {
            val intention = byId[id] ?: continue
            if (seen.add(id)) result.add(intention)
        }
        for (intention in snapshot.intentions) {
            if (seen.add(intention.id)) result.add(intention)
        }
        return result
    }

    fun move(
        orderedIds: List<String>,
        intentionId: String,
        direction: Int
    ): List<String> {
        val list = orderedIds.toMutableList()
        val index = list.indexOf(intentionId)
        if (index < 0) return orderedIds
        val target = index + direction
        if (target !in list.indices) return orderedIds
        val item = list.removeAt(index)
        list.add(target, item)
        return list
    }
}

/**
 * Drawer card layout helpers (OS-139).
 * Defaults are honest chrome from phase / active — not UsageStats priority.
 */
object DrawerCards {

    fun normalizeSpan(spanColumns: Int): Int = if (spanColumns >= 2) 2 else 1

    fun layoutFor(
        snapshot: IntentionSnapshot,
        intention: Intention
    ): DrawerCardLayout {
        val stored = snapshot.drawerCardLayouts.firstOrNull { it.intentionId == intention.id }
        if (stored != null) {
            return stored.copy(spanColumns = normalizeSpan(stored.spanColumns))
        }
        return defaultLayout(intention, snapshot.activeIntentionId)
    }

    /**
     * Default: proposal-pending or active intention → full-width + priority border;
     * otherwise half-width. Cover key follows shapeTag.
     */
    fun defaultLayout(
        intention: Intention,
        activeIntentionId: String?
    ): DrawerCardLayout {
        val elevated =
            intention.phase == IntentionPhase.PROPOSAL_PENDING ||
                intention.phase == IntentionPhase.ACTIVE ||
                intention.id == activeIntentionId
        return DrawerCardLayout(
            intentionId = intention.id,
            spanColumns = if (elevated) 2 else 1,
            priorityBorder = elevated,
            imageKey = coverKeyForShape(intention.shapeTag)
        )
    }

    fun coverKeyForShape(shapeTag: String): String =
        when (shapeTag.lowercase()) {
            "language" -> "language"
            "marathon" -> "marathon"
            "business" -> "business"
            "move" -> "move"
            else -> "shape"
        }
}

object HeldApps {

    fun forIntention(snapshot: IntentionSnapshot, intentionId: String?): List<HeldApp> {
        if (intentionId == null) return emptyList()
        return snapshot.heldApps
            .filter { it.intentionId == intentionId }
            .sortedBy { it.sortOrder }
    }

    /**
     * Primary destination: first held app (SAID), else shape default.
     * Shape default is not a silent SAID binding — UI should invite confirm.
     */
    fun primaryDestination(
        snapshot: IntentionSnapshot,
        intention: Intention?
    ): DestinationApp? {
        if (intention == null) return null
        val held = forIntention(snapshot, intention.id).firstOrNull()
        if (held != null) {
            return DestinationApp(packageName = held.packageName, label = held.label)
        }
        return SessionDestinations.forIntention(intention)
    }

    fun isHeld(
        snapshot: IntentionSnapshot,
        intentionId: String,
        packageName: String
    ): Boolean =
        snapshot.heldApps.any {
            it.intentionId == intentionId && it.packageName == packageName
        }

    fun move(
        held: List<HeldApp>,
        heldAppId: String,
        direction: Int
    ): List<String> {
        val ordered = held.sortedBy { it.sortOrder }.map { it.id }.toMutableList()
        val index = ordered.indexOf(heldAppId)
        if (index < 0) return ordered
        val target = index + direction
        if (target !in ordered.indices) return ordered
        val item = ordered.removeAt(index)
        ordered.add(target, item)
        return ordered
    }
}

/**
 * Short confirm-catalog for hold-for-apps — not a classic app grid / system drawer.
 * Packages must remain visible via `<queries>` (no QUERY_ALL_PACKAGES).
 */
object HoldableApps {

    val CATALOG: List<DestinationApp> = listOf(
        DestinationApp(
            packageName = SessionDestinations.SPANISH_PACKAGE,
            label = SessionDestinations.SPANISH_LABEL
        )
    )

    /**
     * Suggestions the user may confirm as SAID holds for this intention.
     * Shape default first, then catalog entries not already held.
     */
    fun suggestions(
        intention: Intention?,
        snapshot: IntentionSnapshot
    ): List<DestinationApp> {
        if (intention == null) return emptyList()
        val heldPackages = HeldApps.forIntention(snapshot, intention.id)
            .map { it.packageName }
            .toSet()
        val out = linkedMapOf<String, DestinationApp>()
        SessionDestinations.forIntention(intention)?.let { dest ->
            if (dest.packageName !in heldPackages) out[dest.packageName] = dest
        }
        for (entry in CATALOG) {
            if (entry.packageName !in heldPackages) out.putIfAbsent(entry.packageName, entry)
        }
        return out.values.toList()
    }
}
