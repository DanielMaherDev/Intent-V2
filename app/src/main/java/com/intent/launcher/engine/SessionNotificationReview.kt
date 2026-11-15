package com.intent.launcher.engine

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.intent.launcher.capability.CapabilityEntry
import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.capability.CapabilityStatus
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Privacy-safe session notification review (OS-173–174).
 * Persists **package + count + coarse category** only for the active session window.
 * Never stores titles/bodies; never drives Complete/Abandon.
 */
data class NotificationPackageTally(
    val packageName: String,
    val coarseCategory: String,
    val count: Int,
    val sessionRunId: String,
    val dismissed: Boolean = false
)

data class SessionNotificationWindow(
    val sessionRunId: String,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long? = null
)

class SessionNotificationReviewStore(
    private val file: File
) {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val lock = Any()

    @Volatile
    private var window: SessionNotificationWindow? = null

    /** Key = "$sessionRunId|$packageName|$coarseCategory" */
    private val tallies = ConcurrentHashMap<String, NotificationPackageTally>()

    init {
        if (file.exists() && file.length() > 0L) {
            load()
        }
    }

    fun activeWindow(): SessionNotificationWindow? = window

    fun beginWindow(sessionRunId: String, startedAtEpochMs: Long) {
        synchronized(lock) {
            window = SessionNotificationWindow(sessionRunId, startedAtEpochMs)
            persist()
        }
    }

    fun endWindow(endedAtEpochMs: Long) {
        synchronized(lock) {
            val current = window ?: return
            window = current.copy(endedAtEpochMs = endedAtEpochMs)
            persist()
        }
    }

    fun clearWindow() {
        synchronized(lock) {
            window = null
            persist()
        }
    }

    /**
     * Record one posted notification. Ignores events outside the open session window.
     * Stores no title/body — [packageName] + [coarseCategory] only.
     */
    fun recordPosted(
        packageName: String,
        coarseCategory: String,
        postedAtEpochMs: Long
    ) {
        if (packageName.isBlank()) return
        synchronized(lock) {
            val w = window ?: return
            if (w.endedAtEpochMs != null) return
            if (postedAtEpochMs < w.startedAtEpochMs) return
            val category = coarseCategory.ifBlank { CATEGORY_OTHER }
            val key = "${w.sessionRunId}|$packageName|$category"
            val existing = tallies[key]
            tallies[key] = if (existing == null) {
                NotificationPackageTally(
                    packageName = packageName,
                    coarseCategory = category,
                    count = 1,
                    sessionRunId = w.sessionRunId
                )
            } else if (existing.dismissed) {
                existing
            } else {
                existing.copy(count = existing.count + 1)
            }
            persist()
        }
    }

    fun reviewRows(sessionRunId: String? = window?.sessionRunId): List<NotificationPackageTally> {
        val id = sessionRunId ?: return emptyList()
        return tallies.values
            .filter { it.sessionRunId == id && !it.dismissed }
            .sortedWith(compareByDescending<NotificationPackageTally> { it.count }.thenBy { it.packageName })
    }

    fun dismiss(packageName: String, coarseCategory: String, sessionRunId: String) {
        synchronized(lock) {
            val key = "$sessionRunId|$packageName|$coarseCategory"
            val existing = tallies[key] ?: return
            tallies[key] = existing.copy(dismissed = true)
            persist()
        }
    }

    /** Test helper — inject tallies without a live listener. */
    fun putForTest(tally: NotificationPackageTally) {
        synchronized(lock) {
            val key = "${tally.sessionRunId}|${tally.packageName}|${tally.coarseCategory}"
            tallies[key] = tally
            persist()
        }
    }

    private fun persist() {
        file.parentFile?.mkdirs()
        val wire = WireStore(
            window = window?.let {
                WireWindow(it.sessionRunId, it.startedAtEpochMs, it.endedAtEpochMs)
            },
            tallies = tallies.values.map {
                WireTally(it.packageName, it.coarseCategory, it.count, it.sessionRunId, it.dismissed)
            }
        )
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(gson.toJson(wire))
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
    }

    private fun load() {
        val wire = gson.fromJson(file.readText(), WireStore::class.java) ?: return
        window = wire.window?.let {
            SessionNotificationWindow(it.sessionRunId, it.startedAtEpochMs, it.endedAtEpochMs)
        }
        tallies.clear()
        wire.tallies.forEach { t ->
            val key = "${t.sessionRunId}|${t.packageName}|${t.coarseCategory}"
            tallies[key] = NotificationPackageTally(
                t.packageName, t.coarseCategory, t.count, t.sessionRunId, t.dismissed
            )
        }
    }

    data class WireWindow(
        val sessionRunId: String = "",
        val startedAtEpochMs: Long = 0L,
        val endedAtEpochMs: Long? = null
    )

    data class WireTally(
        val packageName: String = "",
        val coarseCategory: String = CATEGORY_OTHER,
        val count: Int = 0,
        val sessionRunId: String = "",
        val dismissed: Boolean = false
    )

    data class WireStore(
        val window: WireWindow? = null,
        val tallies: List<WireTally> = emptyList()
    )

    companion object {
        const val CATEGORY_OTHER = "other"

        fun normalizeCategory(raw: String?): String {
            val c = raw?.trim().orEmpty()
            if (c.isEmpty()) return CATEGORY_OTHER
            return c.lowercase()
        }
    }
}

object SessionNotificationReviewProbe {

    data class Observation(
        val listenerGranted: Boolean,
        val rows: List<NotificationPackageTally>,
        val note: String,
        val canOpenSettings: Boolean,
        val canReview: Boolean
    )

    fun liveRegistry(
        access: NotificationListenerAccess,
        base: List<CapabilityEntry> = CapabilityRegistry.DEFAULT_ENTRIES
    ): CapabilityRegistry {
        val granted = access.hasPermission()
        val withListener = NotificationListenerProbe.liveRegistry(access, base)
        val entries = withListener.all().map { entry ->
            if (entry.id != CapabilityRegistry.ID_SESSION_NOTIFICATION_REVIEW) {
                entry
            } else if (granted) {
                entry.copy(
                    status = CapabilityStatus.AVAILABLE,
                    limitNote = "Package-level counts during the session window only. " +
                        "Titles and bodies are not retained. Not silence, not lesson completion, " +
                        "and never auto-Complete."
                )
            } else {
                entry.copy(
                    status = CapabilityStatus.PERMISSION_NEEDED,
                    limitNote = "Needs Notification access for package counts during a session. " +
                        "Does not store titles/bodies, silence apps, or mark sessions complete."
                )
            }
        }
        return CapabilityRegistry(entries)
    }

    fun observe(
        access: NotificationListenerAccess,
        store: SessionNotificationReviewStore,
        registry: CapabilityRegistry = CapabilityRegistry.default,
        sessionRunId: String? = store.activeWindow()?.sessionRunId
    ): Observation {
        val granted = access.hasPermission()
        val canReview = registry.isActionable(CapabilityRegistry.ID_SESSION_NOTIFICATION_REVIEW)
        if (!granted) {
            return Observation(
                listenerGranted = false,
                rows = emptyList(),
                note = "Notification access not granted — package counts for this session are unavailable. " +
                    "Granting enables counts only (no titles/bodies). Not silence or lesson completion.",
                canOpenSettings = true,
                canReview = false
            )
        }
        val rows = if (canReview) store.reviewRows(sessionRunId) else emptyList()
        val note = when {
            !canReview ->
                registry.get(CapabilityRegistry.ID_SESSION_NOTIFICATION_REVIEW)?.limitNote
                    ?: "Session notification review is unavailable."
            rows.isEmpty() ->
                "No package notification counts for this session yet. " +
                    "Titles/bodies are not retained. Counts never auto-Complete."
            else ->
                "Package counts during this session (titles/bodies not retained). " +
                    "Dismiss a row anytime — presence never marks Complete."
        }
        return Observation(
            listenerGranted = true,
            rows = rows,
            note = note,
            canOpenSettings = false,
            canReview = canReview
        )
    }
}
