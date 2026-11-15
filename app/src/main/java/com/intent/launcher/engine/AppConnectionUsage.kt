package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.capability.CapabilityStatus
import com.intent.launcher.domain.AppConnection
import com.intent.launcher.domain.AppConnectionStatus
import com.intent.launcher.domain.EvidenceAccuracy
import com.intent.launcher.domain.HeldApps
import com.intent.launcher.domain.IntentionSnapshot
import com.intent.launcher.domain.ProvenanceKind
import com.intent.launcher.domain.SessionDestinations

/**
 * OS-159 — which active intentions reference each connection, and what data
 * would contribute today (manual SAID / OBSERVED only). Empty + limit note
 * when no partner feed is available.
 */
object AppConnectionUsage {

    enum class ContributeKind {
        SAID_HOLD,
        SAID_SHAPE_DESTINATION,
        OBSERVED_FOREGROUND,
        PARTNER_FEED
    }

    data class IntentionUse(
        val intentionId: String,
        val intentionTitle: String,
        val contributeKinds: List<ContributeKind>,
        val detail: String
    )

    data class ConnectionUsage(
        val connection: AppConnection,
        val intentions: List<IntentionUse>,
        /** True when a partner API feed is contributing (never in this build). */
        val hasPartnerFeed: Boolean,
        val emptyNote: String?,
        val limitNote: String
    )

    fun forConnection(
        connection: AppConnection,
        snapshot: IntentionSnapshot,
        registry: CapabilityRegistry = CapabilityRegistry.default
    ): ConnectionUsage {
        val uses = mutableListOf<IntentionUse>()
        val byId = snapshot.intentions.associateBy { it.id }

        for (intention in snapshot.intentions) {
            val kinds = linkedSetOf<ContributeKind>()
            val details = mutableListOf<String>()

            val held = HeldApps.forIntention(snapshot, intention.id)
                .filter { it.packageName == connection.packageName }
            if (held.isNotEmpty()) {
                kinds += ContributeKind.SAID_HOLD
                details += "SAID held app"
            }

            val shapeDest = SessionDestinations.forIntention(intention)
            if (shapeDest?.packageName == connection.packageName && held.isEmpty()) {
                kinds += ContributeKind.SAID_SHAPE_DESTINATION
                details += "shape destination (confirm to hold as SAID)"
            }

            val observed = snapshot.evidence.filter {
                it.intentionId == intention.id &&
                    it.packageName == connection.packageName &&
                    it.accuracy == EvidenceAccuracy.ACTIVE &&
                    it.provenance.kind == ProvenanceKind.OBSERVED
            }
            if (observed.isNotEmpty()) {
                kinds += ContributeKind.OBSERVED_FOREGROUND
                details += "OBSERVED package foreground (${observed.size})"
            }

            // Partner feed never contributes without LINKED + actionable capability.
            val partnerCap = registry.get(connection.capabilityKey)
            val partnerLive = connection.accountStatus == AppConnectionStatus.LINKED &&
                connection.canClaimPartnerFeed &&
                partnerCap != null &&
                partnerCap.status == CapabilityStatus.CONNECTED &&
                registry.isActionable(connection.capabilityKey)
            if (partnerLive) {
                kinds += ContributeKind.PARTNER_FEED
                details += "partner feed"
            }

            if (kinds.isNotEmpty()) {
                uses += IntentionUse(
                    intentionId = intention.id,
                    intentionTitle = intention.title,
                    contributeKinds = kinds.toList(),
                    detail = details.joinToString(" · ")
                )
            }
        }

        // Also surface intentions that only appear via heldApps if intention row missing
        // (defensive — snapshot.intentions should already include them).
        for (held in snapshot.heldApps.filter { it.packageName == connection.packageName }) {
            if (uses.any { it.intentionId == held.intentionId }) continue
            val intention = byId[held.intentionId] ?: continue
            uses += IntentionUse(
                intentionId = intention.id,
                intentionTitle = intention.title,
                contributeKinds = listOf(ContributeKind.SAID_HOLD),
                detail = "SAID held app"
            )
        }

        val partnerUnsupported = registry.isUnsupported(connection.capabilityKey) ||
            connection.accountStatus == AppConnectionStatus.NOT_SUPPORTED ||
            connection.accountStatus == AppConnectionStatus.DISCONNECTED
        val hasPartnerFeed = uses.any { ContributeKind.PARTNER_FEED in it.contributeKinds }
        val emptyNote = when {
            hasPartnerFeed -> null
            uses.isEmpty() && partnerUnsupported ->
                "No intentions use ${connection.label} yet. " +
                    "Partner feed is empty — only manual SAID holds and OBSERVED package " +
                    "foreground can contribute today."
            uses.isEmpty() ->
                "No intentions reference ${connection.label} yet."
            partnerUnsupported ->
                "Partner feed empty for ${connection.label}. " +
                    "Contributions today are SAID / OBSERVED only — never auto-Complete."
            else -> null
        }

        return ConnectionUsage(
            connection = connection,
            intentions = uses.sortedBy { it.intentionTitle },
            hasPartnerFeed = hasPartnerFeed,
            emptyNote = emptyNote,
            limitNote = connection.limitNote
        )
    }

    fun forAll(
        connections: List<AppConnection>,
        snapshot: IntentionSnapshot,
        registry: CapabilityRegistry = CapabilityRegistry.default
    ): List<ConnectionUsage> =
        connections.map { forConnection(it, snapshot, registry) }

    /**
     * Usage rows relevant to one intention (Intention Space).
     * Includes shape destination + held/observed matches; empty partner note when no feed.
     */
    fun forIntention(
        intentionId: String?,
        connections: List<AppConnection>,
        snapshot: IntentionSnapshot,
        registry: CapabilityRegistry = CapabilityRegistry.default
    ): List<ConnectionUsage> {
        if (intentionId == null) return emptyList()
        val intention = snapshot.intentions.firstOrNull { it.id == intentionId } ?: return emptyList()
        val shapePkg = SessionDestinations.forIntention(intention)?.packageName
        return forAll(connections, snapshot, registry).mapNotNull { usage ->
            val filtered = usage.intentions.filter { it.intentionId == intentionId }
            val relevant = filtered.isNotEmpty() ||
                usage.connection.packageName == shapePkg ||
                usage.connection.installed
            if (!relevant) return@mapNotNull null
            val partnerEmpty = !usage.hasPartnerFeed &&
                (registry.isUnsupported(usage.connection.capabilityKey) ||
                    usage.connection.accountStatus != AppConnectionStatus.LINKED)
            usage.copy(
                intentions = filtered,
                emptyNote = when {
                    filtered.isEmpty() && partnerEmpty ->
                        "This intention has no SAID hold or OBSERVED evidence for " +
                            "${usage.connection.label}. Partner feed unavailable — " +
                            "manual SAID / OBSERVED only."
                    filtered.isNotEmpty() && partnerEmpty ->
                        "Partner feed empty for ${usage.connection.label}. " +
                            "Contributions today are SAID / OBSERVED only — never auto-Complete."
                    else -> null
                }
            )
        }
    }
}
