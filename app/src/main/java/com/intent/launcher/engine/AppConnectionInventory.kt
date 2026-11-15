package com.intent.launcher.engine

import com.intent.launcher.domain.AppConnection
import com.intent.launcher.domain.AppConnectionStatus
import com.intent.launcher.domain.ConnectionCandidate
import com.intent.launcher.domain.ConnectionCandidates

/**
 * OS-157 / OS-158 — build honest [AppConnection] rows from the static candidate catalog
 * + PackageManager install probe. Never invent installs or LINKED accounts without tokens.
 */
object AppConnectionInventory {

    /**
     * All meaningful candidates with live install flags.
     * Missing packages stay listed for Play Store discovery (OS-164 light) but
     * [AppConnection.installed] is false — never claimed as present.
     */
    fun connections(
        launcher: InstalledAppLauncher,
        candidates: List<ConnectionCandidate> = ConnectionCandidates.ALL
    ): List<AppConnection> =
        candidates.map { candidate -> toConnection(candidate, launcher.isInstalled(candidate.packageName)) }

    /** Only packages PackageManager resolves as installed. */
    fun installedOnly(
        launcher: InstalledAppLauncher,
        candidates: List<ConnectionCandidate> = ConnectionCandidates.ALL
    ): List<AppConnection> =
        connections(launcher, candidates).filter { it.installed }

    fun toConnection(candidate: ConnectionCandidate, installed: Boolean): AppConnection =
        AppConnection(
            id = candidate.id,
            packageName = candidate.packageName,
            label = candidate.label,
            capabilityKey = candidate.capabilityKey,
            accountStatus = candidate.defaultStatus,
            grantedScopes = emptyList(),
            lastSyncAtEpochMs = null,
            installed = installed,
            limitNote = candidate.limitNote
        )

    /**
     * Unlink is an honest no-op when disconnected / not supported (no tokens to revoke).
     * Never invents a LINKED → DISCONNECTED transition without prior tokens.
     */
    fun unlink(connection: AppConnection): UnlinkResult {
        return when (connection.accountStatus) {
            AppConnectionStatus.DISCONNECTED -> UnlinkResult(
                connection = connection,
                changed = false,
                note = "${connection.label} is already disconnected — nothing to unlink."
            )
            AppConnectionStatus.NOT_SUPPORTED -> UnlinkResult(
                connection = connection,
                changed = false,
                note = "${connection.label} partner linking is not supported — unlink is a no-op."
            )
            AppConnectionStatus.LINKED -> {
                // Future OAuth path: clear tokens. This build never reaches LINKED.
                UnlinkResult(
                    connection = connection.copy(
                        accountStatus = AppConnectionStatus.DISCONNECTED,
                        grantedScopes = emptyList(),
                        lastSyncAtEpochMs = null
                    ),
                    changed = true,
                    note = "Unlinked ${connection.label}."
                )
            }
        }
    }

    data class UnlinkResult(
        val connection: AppConnection,
        val changed: Boolean,
        val note: String
    )
}
