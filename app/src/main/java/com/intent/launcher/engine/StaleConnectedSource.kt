package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityEntry
import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.capability.CapabilityStatus
import com.intent.launcher.domain.IntentionSnapshot
import com.intent.launcher.domain.ProvenanceKind
import com.intent.launcher.domain.ResponseBlock
import com.intent.launcher.domain.ResponseBlockType
import com.intent.launcher.domain.TrackerEntryStatus

/**
 * §17 case 23 — connected source stale (e.g. Chess rating import is old).
 * Never presents a stale rating as current; offers refresh / manual SAID fallback.
 */
object StaleConnectedSource {

    /** Default freshness window for partner imports (7 days). */
    const val DEFAULT_STALE_AFTER_MS: Long = 7L * 24L * 60L * 60L * 1000L

    data class View(
        val intentionId: String,
        val capabilityId: String,
        val status: CapabilityStatus,
        val lastRefreshEpochMs: Long?,
        val valueText: String?,
        val isStale: Boolean,
        val label: String,
        val actions: List<String>,
        val honestyNote: String,
        val block: ResponseBlock
    )

    fun evaluateChessRating(
        snapshot: IntentionSnapshot,
        intentionId: String,
        nowEpochMs: Long,
        registry: CapabilityRegistry = CapabilityRegistry.default,
        importedAtEpochMs: Long? = null,
        staleAfterMs: Long = DEFAULT_STALE_AFTER_MS
    ): View {
        val capId = CapabilityRegistry.ID_OBSERVE_CHESS_RATING
        val entry: CapabilityEntry? = registry.get(capId)
        val status = entry?.status ?: CapabilityStatus.NOT_SUPPORTED
        val lastSaid = snapshot.trackerEntries
            .filter {
                it.intentionId == intentionId &&
                    it.moduleId == "rating_self_report" &&
                    it.status == TrackerEntryStatus.ACTIVE
            }
            .maxByOrNull { it.eventAtEpochMs }
        val refreshAt = importedAtEpochMs ?: lastSaid?.eventAtEpochMs
        val ageMs = refreshAt?.let { nowEpochMs - it }
        val staleByAge = ageMs != null && ageMs > staleAfterMs
        val staleByCapability = status == CapabilityStatus.STALE ||
            status == CapabilityStatus.NOT_SUPPORTED ||
            status == CapabilityStatus.TEMPORARILY_UNAVAILABLE
        val isStale = staleByCapability || staleByAge
        val valueText = lastSaid?.valueText
        val label = when {
            status == CapabilityStatus.NOT_SUPPORTED -> "NOT_SUPPORTED · manual only"
            isStale && valueText != null -> "STALE · last known: $valueText"
            isStale -> "STALE · no current rating"
            else -> "CURRENT · $valueText"
        }
        val actions = buildList {
            // Always expose Refresh data — when NOT_SUPPORTED it states honesty
            // instead of inventing an import (OC6 Visible controls).
            add(ConnectedMetricChrome.ACTION_REFRESH)
            add(ConnectedMetricChrome.ACTION_MANUAL)
        }
        val honesty = when {
            status == CapabilityStatus.NOT_SUPPORTED ->
                entry?.limitNote
                    ?: "Chess.com sync is not shipped — use a manual SAID baseline. " +
                    "Never show an invented partner rating as current."
            staleByAge ->
                "Connected rating is older than the freshness window — shown as STALE, not current."
            else ->
                "Connected source status=${status.name}; stale presentation when not fresh."
        }
        val block = ResponseBlock(
            blockId = "block-stale-chess-$intentionId",
            type = if (isStale) ResponseBlockType.CAPABILITY_GAP else ResponseBlockType.PROGRESS,
            title = if (isStale) "Rating source stale" else "Rating",
            body = honesty,
            sourceLabel = if (isStale) "STALE" else ProgressProof.provenanceLabel(
                lastSaid?.provenance?.kind ?: ProvenanceKind.SAID
            ),
            linkedObjectIds = listOfNotNull(intentionId, lastSaid?.id),
            actions = actions,
            active = true
        )
        return View(
            intentionId = intentionId,
            capabilityId = capId,
            status = status,
            lastRefreshEpochMs = refreshAt,
            valueText = valueText,
            isStale = isStale,
            label = label,
            actions = actions,
            honestyNote = honesty,
            block = block
        )
    }

    /** Registry variant that marks Chess.com observation as STALE (connected but old). */
    fun staleChessRegistry(
        base: CapabilityRegistry = CapabilityRegistry.default
    ): CapabilityRegistry {
        val entries = base.all().map { e ->
            if (e.id == CapabilityRegistry.ID_OBSERVE_CHESS_RATING) {
                e.copy(
                    status = CapabilityStatus.STALE,
                    limitNote = "Chess.com connection exists but the last rating import is stale. " +
                        "Show STALE + refresh/manual — never as current."
                )
            } else {
                e
            }
        }
        return CapabilityRegistry(entries)
    }
}
