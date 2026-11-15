package com.intent.launcher.engine

import com.intent.launcher.domain.FlowCommand
import com.intent.launcher.domain.SCHEMA_VERSION
import com.intent.launcher.domain.SpanishScenario
import com.intent.launcher.store.IntentionFlowStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class IntentionHierarchyTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var store: IntentionFlowStore
    private val now = 1_700_000_000_000L

    @Before
    fun setUp() {
        store = IntentionFlowStore(File(tmp.root, "flow.json"))
    }

    @Test
    fun spanishSeed_linksHonestLocalChild_withoutStealingActive() {
        SpanishScenario.seed(store, now)
        SpanishScenario.seedChild(store, now)
        val snap = store.snapshot()
        assertEquals(SCHEMA_VERSION, snap.schemaVersion)
        assertEquals(SpanishScenario.INTENTION_ID, snap.activeIntentionId)

        val child = snap.intentions.first { it.id == SpanishScenario.CHILD_INTENTION_ID }
        assertEquals(SpanishScenario.INTENTION_ID, child.parentIntentionId)

        val parentView = IntentionHierarchy.build(
            snap,
            snap.intentions.first { it.id == SpanishScenario.INTENTION_ID }
        )
        assertEquals(1, parentView.crumbs.size)
        assertEquals(1, parentView.children.size)
        assertEquals(SpanishScenario.CHILD_TITLE, parentView.children.first().title)
        assertFalse(parentView.orphanParent)
        assertTrue(parentView.honestyNote.contains("local", ignoreCase = true))

        val childView = IntentionHierarchy.build(snap, child)
        assertEquals(2, childView.crumbs.size)
        assertEquals(SpanishScenario.TITLE, childView.crumbs.first().title)
        assertTrue(childView.crumbs.last().isCurrent)
        assertTrue(childView.children.isEmpty())
    }

    @Test
    fun setParent_refusesCycleAndUnknownParent() {
        SpanishScenario.seed(store, now)
        SpanishScenario.seedChild(store, now)
        assertFalse(
            IntentionHierarchy.canSetParent(
                store.snapshot(),
                SpanishScenario.INTENTION_ID,
                SpanishScenario.CHILD_INTENTION_ID
            )
        )
        store.apply(
            FlowCommand.SetParentIntention(
                intentionId = SpanishScenario.INTENTION_ID,
                parentIntentionId = SpanishScenario.CHILD_INTENTION_ID,
                nowEpochMs = now + 10
            )
        )
        assertNull(
            store.snapshot().intentions
                .first { it.id == SpanishScenario.INTENTION_ID }
                .parentIntentionId
        )

        store.apply(
            FlowCommand.SetParentIntention(
                intentionId = SpanishScenario.CHILD_INTENTION_ID,
                parentIntentionId = "missing-parent",
                nowEpochMs = now + 11
            )
        )
        assertEquals(
            SpanishScenario.INTENTION_ID,
            store.snapshot().intentions
                .first { it.id == SpanishScenario.CHILD_INTENTION_ID }
                .parentIntentionId
        )
    }

    @Test
    fun softDeleteChild_preservesParentPathInTrash() {
        SpanishScenario.seed(store, now)
        SpanishScenario.seedChild(store, now)
        store.apply(
            FlowCommand.SoftDeleteIntention(
                trashId = "trash-child",
                intentionId = SpanishScenario.CHILD_INTENTION_ID,
                nowEpochMs = now + 20
            )
        )
        val trash = store.snapshot().trash.first { it.id == "trash-child" }
        assertEquals(SpanishScenario.INTENTION_ID, trash.parentIntentionId)
        assertEquals(
            "${SpanishScenario.TITLE} / ${SpanishScenario.CHILD_TITLE}",
            trash.pathLabel
        )
    }

    @Test
    fun orphanParent_honestNote_noInventedCrumb() {
        SpanishScenario.seed(store, now)
        SpanishScenario.seedChild(store, now)
        store.apply(
            FlowCommand.SoftDeleteIntention(
                trashId = "trash-parent",
                intentionId = SpanishScenario.INTENTION_ID,
                nowEpochMs = now + 30
            )
        )
        // Soft-delete of parent leaves child with dangling parent id in payload only —
        // active may move; re-seed child pointer via SetParent after orphan setup:
        val childStill = store.snapshot().intentions
            .firstOrNull { it.id == SpanishScenario.CHILD_INTENTION_ID }
        // Parent soft-delete removes parent but keeps child; child still points at parent id.
        assertTrue(childStill != null)
        val view = IntentionHierarchy.build(store.snapshot(), childStill)
        assertTrue(view.orphanParent)
        assertEquals(1, view.crumbs.size)
        assertTrue(view.honestyNote.contains("orphan", ignoreCase = true))
    }

    @Test
    fun seedExploring_ignoresUnknownParentId() {
        store.apply(
            FlowCommand.SeedExploring(
                intentionId = "alone",
                title = "Alone",
                statement = "No parent in store",
                shapeTag = "other",
                nowEpochMs = now,
                parentIntentionId = "does-not-exist"
            )
        )
        assertNull(store.snapshot().intentions.first().parentIntentionId)
    }
}
