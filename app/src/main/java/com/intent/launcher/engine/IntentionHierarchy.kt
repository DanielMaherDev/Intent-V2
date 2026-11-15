package com.intent.launcher.engine

import com.intent.launcher.domain.Intention
import com.intent.launcher.domain.IntentionSnapshot

/**
 * Honest local parent/child navigation (worksheet OS-141).
 *
 * Reads only the stored [Intention.parentIntentionId] pointer.
 * Does not invent dependency edges, task prerequisites, or rollups.
 */
object IntentionHierarchy {

    data class Crumb(
        val intentionId: String,
        val title: String,
        val isCurrent: Boolean
    )

    data class ChildLink(
        val intentionId: String,
        val title: String,
        val phaseLabel: String
    )

    data class View(
        /** Root → … → parent → current. Empty when intention is missing. */
        val crumbs: List<Crumb>,
        val children: List<ChildLink>,
        /** True when parentIntentionId points at an id not in the local store. */
        val orphanParent: Boolean,
        val honestyNote: String
    )

    fun childrenOf(snapshot: IntentionSnapshot, parentId: String): List<Intention> =
        snapshot.intentions
            .filter { it.parentIntentionId == parentId }
            .sortedBy { it.title.lowercase() }

    /**
     * Ancestors from root to immediate parent (excludes [intentionId]).
     * Stops on missing parent or cycle — never fabricates missing nodes.
     */
    fun ancestors(snapshot: IntentionSnapshot, intentionId: String): List<Intention> {
        val byId = snapshot.intentions.associateBy { it.id }
        val current = byId[intentionId] ?: return emptyList()
        val chain = ArrayList<Intention>()
        val seen = mutableSetOf(intentionId)
        var parentId = current.parentIntentionId
        while (parentId != null) {
            if (parentId in seen) break
            val parent = byId[parentId] ?: break
            seen += parentId
            chain.add(0, parent)
            parentId = parent.parentIntentionId
        }
        return chain
    }

    fun pathLabel(snapshot: IntentionSnapshot, intention: Intention): String {
        val titles = ancestors(snapshot, intention.id).map { it.title } + intention.title
        return titles.joinToString(" / ")
    }

    fun orphanParent(snapshot: IntentionSnapshot, intention: Intention): Boolean {
        val parentId = intention.parentIntentionId ?: return false
        return snapshot.intentions.none { it.id == parentId }
    }

    /**
     * True when [parentIntentionId] may be assigned to [intentionId] without a cycle
     * and the parent exists locally (or parent is null = clear).
     */
    fun canSetParent(
        snapshot: IntentionSnapshot,
        intentionId: String,
        parentIntentionId: String?
    ): Boolean {
        if (parentIntentionId == null) return snapshot.intentions.any { it.id == intentionId }
        if (parentIntentionId == intentionId) return false
        if (snapshot.intentions.none { it.id == intentionId }) return false
        if (snapshot.intentions.none { it.id == parentIntentionId }) return false
        // Refuse if parentIntentionId is a descendant of intentionId (cycle).
        val byId = snapshot.intentions.associateBy { it.id }
        var walk: String? = parentIntentionId
        val seen = mutableSetOf<String>()
        while (walk != null) {
            if (walk == intentionId) return false
            if (!seen.add(walk)) return false
            walk = byId[walk]?.parentIntentionId
        }
        return true
    }

    fun build(snapshot: IntentionSnapshot, intention: Intention?): View {
        if (intention == null) {
            return View(
                crumbs = emptyList(),
                children = emptyList(),
                orphanParent = false,
                honestyNote = "No active intention — breadcrumb reads local store only."
            )
        }
        val ancestorList = ancestors(snapshot, intention.id)
        val orphan = orphanParent(snapshot, intention)
        val crumbs = buildList {
            ancestorList.forEach { add(Crumb(it.id, it.title, isCurrent = false)) }
            add(Crumb(intention.id, intention.title, isCurrent = true))
        }
        val childLinks = childrenOf(snapshot, intention.id).map {
            ChildLink(
                intentionId = it.id,
                title = it.title,
                phaseLabel = PathSummary.phaseLabel(it.phase)
            )
        }
        val honesty = when {
            orphan ->
                "Parent id is stored but not in the local intention list — " +
                    "shown as orphaned, not invented."
            crumbs.size == 1 && childLinks.isEmpty() ->
                "Root intention — no local parent or children linked."
            else ->
                "Parent/child links are local pointers only — not a dependency graph."
        }
        return View(
            crumbs = crumbs,
            children = childLinks,
            orphanParent = orphan,
            honestyNote = honesty
        )
    }
}
