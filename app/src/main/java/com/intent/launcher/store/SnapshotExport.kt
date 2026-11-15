package com.intent.launcher.store

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * Local-device snapshot export / import helpers (OS-004).
 *
 * Shares IntentionFlowStore JSON via Android share/save, and imports the same
 * format through [IntentionFlowStore.replaceFromJson] (stamp migrate only).
 * Honest scope: local device copy — not cloud backup.
 */
object SnapshotExport {
    const val MIME_TYPE = "application/json"
    const val AUTHORITY_SUFFIX = ".fileprovider"

    /** Honest share body — never claim cloud backup. */
    const val HONESTY_NOTE =
        "Local device copy of your INTENT intention snapshot. " +
            "Not a cloud backup. Import replaces the on-device store from the same JSON format."

    /** Settings copy — import replaces local state; migration is stamp-only. */
    const val IMPORT_HONESTY_NOTE =
        "Import a previously exported INTENT snapshot JSON. " +
            "This replaces the current on-device store. Schema stamps forward via " +
            "IntentionFlowStore migration — no cloud sync."

    fun authority(packageName: String): String = "$packageName$AUTHORITY_SUFFIX"

    fun fileName(nowEpochMs: Long): String = "intent-snapshot-$nowEpochMs.json"

    /**
     * Writes [json] under `cacheDir/exports/` for FileProvider sharing.
     * Caller must pass the same bytes produced by [IntentionFlowStore.encode].
     */
    fun writeExportFile(
        cacheDir: File,
        json: String,
        nowEpochMs: Long = System.currentTimeMillis()
    ): File {
        val dir = File(cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, fileName(nowEpochMs))
        file.writeText(json)
        return file
    }

    fun shareIntent(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            authority(context.packageName),
            file
        )
        val send = Intent(Intent.ACTION_SEND).apply {
            type = MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "INTENT local snapshot")
            putExtra(Intent.EXTRA_TEXT, HONESTY_NOTE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "Share local snapshot")
    }

    /**
     * Read UTF-8 text from a content/file [uri] opened via ContentResolver.
     * Returns null when the stream cannot be opened or is empty.
     */
    fun readImportText(context: Context, uri: android.net.Uri): String? {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }
}
