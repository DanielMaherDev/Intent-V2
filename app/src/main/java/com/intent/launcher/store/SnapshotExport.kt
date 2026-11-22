package com.intent.launcher.store

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.google.gson.GsonBuilder
import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.domain.AdaptiveBuilderState
import com.intent.launcher.domain.BuilderDecisionEvent
import com.intent.launcher.domain.IntentionSnapshot
import com.intent.launcher.domain.SCHEMA_VERSION
import java.io.File

/**
 * Local-device snapshot export / import helpers (OS-004) plus Gate F
 * troubleshooting export that embeds a redacted builder decision trail.
 *
 * Shares IntentionFlowStore JSON via Android share/save, and imports the same
 * format through [IntentionFlowStore.replaceFromJson] (stamp migrate only).
 * Honest scope: local device copy — not cloud backup.
 */
object SnapshotExport {
    const val MIME_TYPE = "application/json"
    const val AUTHORITY_SUFFIX = ".fileprovider"
    const val EXPORT_KIND_TROUBLESHOOTING = "troubleshooting"

    /** Honest share body — never claim cloud backup. */
    const val HONESTY_NOTE =
        "Local device copy of your INTENT intention snapshot. " +
            "Not a cloud backup. Import replaces the on-device store from the same JSON format."

    /** Settings copy — import replaces local state; migration is stamp-only. */
    const val IMPORT_HONESTY_NOTE =
        "Import a previously exported INTENT snapshot JSON. " +
            "This replaces the current on-device store. Schema stamps forward via " +
            "IntentionFlowStore migration — no cloud sync."

    /**
     * Gate F troubleshooting export honesty — decision trail only, no full chat
     * dump, secrets, or private audio.
     */
    const val TROUBLESHOOTING_HONESTY_NOTE =
        "Local troubleshooting export: redacted builder decision trail and " +
            "intention summaries only. No conversation dump, no private audio, " +
            "no API keys. Not a cloud backup and not a full store restore file."

    private val troubleshootingGson = GsonBuilder().setPrettyPrinting().create()

    fun authority(packageName: String): String = "$packageName$AUTHORITY_SUFFIX"

    fun fileName(nowEpochMs: Long): String = "intent-snapshot-$nowEpochMs.json"

    fun troubleshootingFileName(nowEpochMs: Long): String =
        "intent-troubleshooting-$nowEpochMs.json"

    /**
     * Writes [json] under `cacheDir/exports/` for FileProvider sharing.
     * Caller must pass the same bytes produced by [IntentionFlowStore.encode]
     * or [encodeTroubleshootingJson].
     */
    fun writeExportFile(
        cacheDir: File,
        json: String,
        nowEpochMs: Long = System.currentTimeMillis(),
        exportFileName: String? = null
    ): File {
        val dir = File(cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, exportFileName ?: fileName(nowEpochMs))
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

    fun shareTroubleshootingIntent(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            authority(context.packageName),
            file
        )
        val send = Intent(Intent.ACTION_SEND).apply {
            type = MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "INTENT troubleshooting export")
            putExtra(Intent.EXTRA_TEXT, TROUBLESHOOTING_HONESTY_NOTE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "Share troubleshooting export")
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

    /**
     * Gate F sibling of OS-004 full snapshot: redacted decision trail + thin
     * intention summaries. Omits turns, raw utterances, transaction beforeJson,
     * and any secret-bearing fields.
     */
    fun encodeTroubleshootingJson(
        snapshot: IntentionSnapshot,
        appVersionName: String,
        nowEpochMs: Long = System.currentTimeMillis()
    ): String {
        val trail = snapshot.decisionTrail.map { redactEventForExport(it) }
        val summaries = snapshot.intentions.map { intention ->
            val state = snapshot.adaptiveBuilderStates
                .firstOrNull { it.intentionId == intention.id }
            TroubleshootingIntentionSummary(
                id = intention.id,
                title = intention.title,
                phase = intention.phase.name,
                shapeTag = intention.shapeTag,
                revision = state?.revision ?: 0L,
                lifecycleHint = state?.lifecycleHint ?: "",
                currentQuestionId = state?.currentQuestionId,
                pendingProposalId = state?.pendingProposalId,
                policyVersion = state?.policyVersion ?: AdaptiveBuilderState.POLICY_VERSION
            )
        }
        val payload = TroubleshootingExport(
            exportKind = EXPORT_KIND_TROUBLESHOOTING,
            schemaVersion = SCHEMA_VERSION,
            appVersionName = appVersionName,
            exportedAtEpochMs = nowEpochMs,
            policyVersion = AdaptiveBuilderState.POLICY_VERSION,
            capabilitySnapshotVersion = CapabilityRegistry.SNAPSHOT_VERSION,
            honestyNote = TROUBLESHOOTING_HONESTY_NOTE,
            decisionTrail = trail,
            intentionSummaries = summaries
        )
        return troubleshootingGson.toJson(payload)
    }

    /** Ensure exported trail rows never carry secret-like tokens. */
    fun redactEventForExport(event: BuilderDecisionEvent): BuilderDecisionEvent =
        event.copy(reason = redactSecretLike(event.reason))

    fun redactSecretLike(text: String): String {
        if (text.isBlank()) return text
        return SECRET_LIKE.replace(text, "[redacted]")
    }

    private val SECRET_LIKE = Regex(
        """(?i)\b(sk-[A-Za-z0-9_\-]{8,}|Bearer\s+[A-Za-z0-9\-._~+/]+=*|api[_-]?key\s*[:=]\s*\S+)\b"""
    )

    data class TroubleshootingIntentionSummary(
        val id: String,
        val title: String,
        val phase: String,
        val shapeTag: String,
        val revision: Long,
        val lifecycleHint: String,
        val currentQuestionId: String?,
        val pendingProposalId: String?,
        val policyVersion: String
    )

    data class TroubleshootingExport(
        val exportKind: String,
        val schemaVersion: Int,
        val appVersionName: String,
        val exportedAtEpochMs: Long,
        val policyVersion: String,
        val capabilitySnapshotVersion: String,
        val honestyNote: String,
        val decisionTrail: List<BuilderDecisionEvent>,
        val intentionSummaries: List<TroubleshootingIntentionSummary>
    )
}
