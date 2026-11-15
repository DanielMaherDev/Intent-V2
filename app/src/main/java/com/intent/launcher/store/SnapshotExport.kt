package com.intent.launcher.store

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * Local-device snapshot export helpers (OS-004).
 *
 * Shares IntentionFlowStore JSON via Android share/save. Honest scope:
 * local file copy only — not cloud backup; no import/restore path.
 */
object SnapshotExport {
    const val MIME_TYPE = "application/json"
    const val AUTHORITY_SUFFIX = ".fileprovider"

    /** Honest share body — never claim cloud backup or restore. */
    const val HONESTY_NOTE =
        "Local device copy of your INTENT intention snapshot. " +
            "Not a cloud backup. Import/restore is not available yet."

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
}
