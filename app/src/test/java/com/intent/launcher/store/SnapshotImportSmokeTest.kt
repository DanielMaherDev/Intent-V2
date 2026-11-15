package com.intent.launcher.store

import com.intent.launcher.domain.SCHEMA_VERSION
import com.intent.launcher.domain.SpanishScenario
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Smoke proof — export JSON round-trips through [IntentionFlowStore.replaceFromJson]
 * (same format Settings import uses). Stamp migration only; no store rewrite.
 */
class SnapshotImportSmokeTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun exportThenImport_preservesIntentionsAndStampsSchema() {
        val sourceFile = tmp.newFile("source.json")
        val source = IntentionFlowStore(sourceFile)
        SpanishScenario.seed(source, now = 1_700_000_000_000L)
        val exported = source.encode(source.snapshot())
        assertTrue(exported.contains("schemaVersion"))

        val targetFile = tmp.newFile("target.json")
        val target = IntentionFlowStore(targetFile)
        assertTrue(target.snapshot().intentions.isEmpty())

        val imported = target.replaceFromJson(exported)
        assertEquals(SCHEMA_VERSION, imported.schemaVersion)
        assertEquals(
            source.snapshot().intentions.map { it.id },
            imported.intentions.map { it.id }
        )
        assertEquals(SpanishScenario.INTENTION_ID, imported.activeIntentionId)

        val reopened = IntentionFlowStore(targetFile).snapshot()
        assertEquals(SCHEMA_VERSION, reopened.schemaVersion)
        assertEquals(imported.intentions.map { it.id }, reopened.intentions.map { it.id })
    }

    @Test
    fun importLegacyFixture_stampsToCurrentSchema() {
        val raw = javaClass.classLoader
            .getResourceAsStream("fixtures/legacy-export-schema0.json")!!
            .bufferedReader()
            .use { it.readText() }
        assertTrue(raw.contains("\"schemaVersion\": 0") || raw.contains("\"schemaVersion\":0"))

        val file = tmp.newFile("legacy-import.json")
        file.writeText("""{"schemaVersion":1,"intentions":[]}""")
        val store = IntentionFlowStore(file)
        val imported = store.replaceFromJson(raw)
        assertEquals(SCHEMA_VERSION, imported.schemaVersion)
        assertEquals(3, imported.intentionOrder.size)
        assertEquals(
            listOf(
                "intention-legacy-spanish",
                "intention-legacy-health",
                "intention-legacy-run"
            ),
            imported.intentionOrder
        )
    }
}
