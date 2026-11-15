package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.capability.CapabilityStatus
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SessionNotificationReviewTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var store: SessionNotificationReviewStore
    private val now = 1_700_000_000_000L

    @Before
    fun setUp() {
        store = SessionNotificationReviewStore(tmp.newFile("tallies.json"))
    }

    @Test
    fun recordPosted_countsPackageInSessionWindow_noTitleBody() {
        store.beginWindow("run-1", now)
        store.recordPosted("com.example.mail", "email", now + 1_000)
        store.recordPosted("com.example.mail", "email", now + 2_000)
        store.recordPosted("com.example.chat", "msg", now + 3_000)
        // Outside window start — ignored
        store.recordPosted("com.example.early", "other", now - 5_000)

        val rows = store.reviewRows("run-1")
        assertEquals(2, rows.size)
        val mail = rows.first { it.packageName == "com.example.mail" }
        assertEquals(2, mail.count)
        assertEquals("email", mail.coarseCategory)
        assertFalse(mail.dismissed)
        assertTrue(rows.none { it.packageName == "com.example.early" })
    }

    @Test
    fun endWindow_stopsNewCounts_reviewStillAvailable() {
        store.beginWindow("run-2", now)
        store.recordPosted("com.a", "other", now + 10)
        store.endWindow(now + 100)
        store.recordPosted("com.a", "other", now + 200)
        val rows = store.reviewRows("run-2")
        assertEquals(1, rows.single().count)
    }

    @Test
    fun dismiss_hidesRow_withoutAutoCompleteSideEffects() {
        store.beginWindow("run-3", now)
        store.recordPosted("com.x", "social", now + 1)
        store.dismiss("com.x", "social", "run-3")
        assertTrue(store.reviewRows("run-3").isEmpty())
    }

    @Test
    fun persistAcrossRestart() {
        store.beginWindow("run-4", now)
        store.recordPosted("com.persist", "other", now + 1)
        val restarted = SessionNotificationReviewStore(File(tmp.root, "tallies.json"))
        val rows = restarted.reviewRows("run-4")
        assertEquals(1, rows.single().count)
        assertEquals("com.persist", rows.single().packageName)
    }

    @Test
    fun probe_permissionNeededWithoutListener_availableWithCountsHonesty() {
        val denied = SessionNotificationReviewProbe.liveRegistry(
            FakeNotificationListenerAccess(granted = false)
        )
        assertTrue(denied.needsPermission(CapabilityRegistry.ID_SESSION_NOTIFICATION_REVIEW))
        assertFalse(denied.canReviewSessionNotifications())

        val granted = SessionNotificationReviewProbe.liveRegistry(
            FakeNotificationListenerAccess(granted = true)
        )
        assertTrue(granted.canReviewSessionNotifications())
        assertEquals(
            CapabilityStatus.AVAILABLE,
            granted.get(CapabilityRegistry.ID_SESSION_NOTIFICATION_REVIEW)!!.status
        )
        assertTrue(
            granted.get(CapabilityRegistry.ID_SESSION_NOTIFICATION_REVIEW)!!.limitNote
                .contains("Titles and bodies are not retained", ignoreCase = true)
        )
        assertTrue(
            granted.get(CapabilityRegistry.ID_SESSION_NOTIFICATION_REVIEW)!!.limitNote
                .contains("never auto-Complete", ignoreCase = true)
        )
    }

    @Test
    fun observe_deniedOffersSettings_grantedShowsRows() {
        store.beginWindow("run-5", now)
        store.recordPosted("com.y", "other", now + 1)
        val deniedObs = SessionNotificationReviewProbe.observe(
            FakeNotificationListenerAccess(granted = false),
            store,
            sessionRunId = "run-5"
        )
        assertTrue(deniedObs.canOpenSettings)
        assertTrue(deniedObs.rows.isEmpty())
        assertTrue(deniedObs.note.contains("not granted", ignoreCase = true))

        val grantedReg = SessionNotificationReviewProbe.liveRegistry(
            FakeNotificationListenerAccess(granted = true)
        )
        val grantedObs = SessionNotificationReviewProbe.observe(
            FakeNotificationListenerAccess(granted = true),
            store,
            registry = grantedReg,
            sessionRunId = "run-5"
        )
        assertTrue(grantedObs.listenerGranted)
        assertEquals(1, grantedObs.rows.size)
        assertFalse(grantedObs.canOpenSettings)
        assertTrue(grantedObs.note.contains("not retained", ignoreCase = true))
    }

    @Test
    fun platformCapabilityLive_chainsSessionReview() {
        val registry = PlatformCapabilityLive.registry(
            usage = FakeUsageStatsAccess(granted = true),
            overlay = FakeOverlayPermissionAccess(granted = true),
            voicePermission = FakeVoicePermissionAccess(granted = true),
            speech = FakeSpeechToTextAccess(available = true),
            notifications = FakeNotificationListenerAccess(granted = true)
        )
        assertTrue(registry.canReadNotificationSummaries())
        assertTrue(registry.canReviewSessionNotifications())
        assertTrue(registry.isUnsupported(CapabilityRegistry.ID_QUIET_MODE))
        // Post-#31: screen_capture is PERMISSION_NEEDED (consent-at-ask), not NOT_SUPPORTED.
        assertTrue(registry.needsPermission(CapabilityRegistry.ID_SCREEN_CAPTURE))
    }
}
