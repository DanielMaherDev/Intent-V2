package com.intent.launcher.store

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SnapshotExportTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun writeExportFile_writesJsonUnderExports() {
        val cache = tmp.newFolder("cache")
        val json = """{"schemaVersion":6,"intentions":[]}"""
        val file = SnapshotExport.writeExportFile(cache, json, nowEpochMs = 1_700_000_000_000L)
        assertEquals("intent-snapshot-1700000000000.json", file.name)
        assertEquals(File(cache, "exports"), file.parentFile)
        assertEquals(json, file.readText())
        assertTrue(file.exists())
    }

    @Test
    fun honestyNote_statesLocalNotCloud() {
        val note = SnapshotExport.HONESTY_NOTE.lowercase()
        assertTrue(note.contains("local"))
        assertTrue(note.contains("not a cloud backup"))
        assertTrue(note.contains("import"))
        val importNote = SnapshotExport.IMPORT_HONESTY_NOTE.lowercase()
        assertTrue(importNote.contains("replaces"))
        assertTrue(importNote.contains("no cloud"))
    }

    @Test
    fun authority_usesPackageSuffix() {
        assertEquals(
            "com.intent.launcher.fileprovider",
            SnapshotExport.authority("com.intent.launcher")
        )
    }
}
