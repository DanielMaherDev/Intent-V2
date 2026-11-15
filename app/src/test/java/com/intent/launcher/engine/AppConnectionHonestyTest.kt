package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.capability.CapabilityStatus
import com.intent.launcher.domain.AppConnection
import com.intent.launcher.domain.AppConnectionStatus
import com.intent.launcher.domain.ConnectionCandidates
import com.intent.launcher.domain.FlowCommand
import com.intent.launcher.domain.SessionDestinations
import com.intent.launcher.domain.SpanishScenario
import com.intent.launcher.store.IntentionFlowStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * OS-157–159 — inventory filter + AppConnection status honesty.
 * Never invent installs, partner feeds, or LINKED accounts without tokens.
 */
class AppConnectionHonestyTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var store: IntentionFlowStore
    private val registry = CapabilityRegistry.default
    private val seedAt = 1_700_049_600_000L

    @Before
    fun setUp() {
        store = IntentionFlowStore(tmp.newFile("flow.json"))
    }

    @Test
    fun inventory_neverInventInstalls_onlyPackageManagerTruth() {
        val none = FakeInstalledAppLauncher(installed = emptySet())
        val allMissing = AppConnectionInventory.connections(none)
        assertEquals(ConnectionCandidates.ALL.size, allMissing.size)
        assertTrue(allMissing.none { it.installed })
        assertTrue(AppConnectionInventory.installedOnly(none).isEmpty())

        val duoOnly = FakeInstalledAppLauncher(
            installed = setOf(ConnectionCandidates.PACKAGE_DUOLINGO)
        )
        val installed = AppConnectionInventory.installedOnly(duoOnly)
        assertEquals(1, installed.size)
        assertEquals(ConnectionCandidates.PACKAGE_DUOLINGO, installed.single().packageName)
        assertTrue(installed.single().installed)

        val mixed = AppConnectionInventory.connections(duoOnly)
        assertEquals(1, mixed.count { it.installed })
        assertEquals(3, mixed.count { !it.installed })
        assertFalse(
            mixed.any {
                it.packageName == ConnectionCandidates.PACKAGE_SPOTIFY && it.installed
            }
        )
    }

    @Test
    fun partners_areNotSupported_neverLinkedWithoutTokens() {
        assertTrue(registry.isUnsupported(CapabilityRegistry.ID_PARTNER_SPOTIFY))
        assertTrue(registry.isUnsupported(CapabilityRegistry.ID_PARTNER_CHESS))
        assertTrue(registry.isUnsupported(CapabilityRegistry.ID_PARTNER_STRAVA))
        assertFalse(registry.canUsePartnerSpotify())
        assertFalse(registry.canUsePartnerChess())
        assertFalse(registry.canUsePartnerStrava())

        val fake = FakeInstalledAppLauncher(
            installed = setOf(
                ConnectionCandidates.PACKAGE_SPOTIFY,
                ConnectionCandidates.PACKAGE_CHESS,
                ConnectionCandidates.PACKAGE_STRAVA
            )
        )
        val connections = AppConnectionInventory.connections(fake)
        val spotify = connections.first { it.id == ConnectionCandidates.ID_SPOTIFY }
        val chess = connections.first { it.id == ConnectionCandidates.ID_CHESS }
        val strava = connections.first { it.id == ConnectionCandidates.ID_STRAVA }

        assertEquals(AppConnectionStatus.NOT_SUPPORTED, spotify.accountStatus)
        assertEquals(AppConnectionStatus.NOT_SUPPORTED, chess.accountStatus)
        assertEquals(AppConnectionStatus.NOT_SUPPORTED, strava.accountStatus)
        assertTrue(spotify.grantedScopes.isEmpty())
        assertNull(spotify.lastSyncAtEpochMs)
        assertFalse(spotify.canClaimPartnerFeed)
        assertEquals(CapabilityRegistry.ID_PARTNER_SPOTIFY, spotify.capabilityKey)
        assertEquals(CapabilityRegistry.ID_PARTNER_CHESS, chess.capabilityKey)
        assertEquals(CapabilityRegistry.ID_PARTNER_STRAVA, strava.capabilityKey)
    }

    @Test
    fun unlink_whenDisconnectedOrNotSupported_isHonestNoOp() {
        val disconnected = AppConnectionInventory.toConnection(
            ConnectionCandidates.byId(ConnectionCandidates.ID_DUOLINGO)!!,
            installed = true
        )
        assertEquals(AppConnectionStatus.DISCONNECTED, disconnected.accountStatus)
        val unlinkDuo = AppConnectionInventory.unlink(disconnected)
        assertFalse(unlinkDuo.changed)
        assertEquals(disconnected, unlinkDuo.connection)
        assertTrue(unlinkDuo.note.contains("already disconnected", ignoreCase = true))

        val notSupported = AppConnectionInventory.toConnection(
            ConnectionCandidates.byId(ConnectionCandidates.ID_SPOTIFY)!!,
            installed = true
        )
        val unlinkSpotify = AppConnectionInventory.unlink(notSupported)
        assertFalse(unlinkSpotify.changed)
        assertEquals(notSupported, unlinkSpotify.connection)
        assertTrue(unlinkSpotify.note.contains("not supported", ignoreCase = true))
    }

    @Test
    fun usage_surfacesSaidAndObserved_partnerFeedStaysEmpty() {
        SpanishScenario.seed(store, seedAt)
        SpanishScenario.onChip(store, "I meet a tutor on Thursdays at six", seedAt + 1)
        SpanishScenario.approve(store, seedAt + 2)
        val intentionId = SpanishScenario.INTENTION_ID

        store.apply(
            FlowCommand.HoldAppForIntention(
                heldAppId = "held-duo",
                intentionId = intentionId,
                packageName = SessionDestinations.SPANISH_PACKAGE,
                label = SessionDestinations.SPANISH_LABEL,
                nowEpochMs = seedAt + 3
            )
        )
        store.apply(
            FlowCommand.RecordObservedEvidence(
                evidenceId = "obs-duo",
                intentionId = intentionId,
                sessionRunId = null,
                packageName = SessionDestinations.SPANISH_PACKAGE,
                appLabel = SessionDestinations.SPANISH_LABEL,
                foregroundMs = 60_000L,
                summaryText = "Duolingo foreground 1m",
                nowEpochMs = seedAt + 4
            )
        )

        val snap = store.snapshot()
        val duo = AppConnectionInventory.toConnection(
            ConnectionCandidates.byId(ConnectionCandidates.ID_DUOLINGO)!!,
            installed = true
        )
        val usage = AppConnectionUsage.forConnection(duo, snap, registry)
        assertEquals(1, usage.intentions.size)
        assertEquals(intentionId, usage.intentions.single().intentionId)
        assertTrue(
            usage.intentions.single().contributeKinds.contains(
                AppConnectionUsage.ContributeKind.SAID_HOLD
            )
        )
        assertTrue(
            usage.intentions.single().contributeKinds.contains(
                AppConnectionUsage.ContributeKind.OBSERVED_FOREGROUND
            )
        )
        assertFalse(
            usage.intentions.single().contributeKinds.contains(
                AppConnectionUsage.ContributeKind.PARTNER_FEED
            )
        )
        assertFalse(usage.hasPartnerFeed)
        assertNotNull(usage.emptyNote)
        assertTrue(usage.emptyNote!!.contains("Partner feed", ignoreCase = true))

        val spotify = AppConnectionInventory.toConnection(
            ConnectionCandidates.byId(ConnectionCandidates.ID_SPOTIFY)!!,
            installed = false
        )
        val spotifyUsage = AppConnectionUsage.forConnection(spotify, snap, registry)
        assertTrue(spotifyUsage.intentions.isEmpty())
        assertFalse(spotifyUsage.hasPartnerFeed)
        assertNotNull(spotifyUsage.emptyNote)
        assertTrue(spotifyUsage.emptyNote!!.contains("Partner feed is empty", ignoreCase = true))
    }

    @Test
    fun playStoreDeepLink_isSourceLabeled() {
        val offer = PlayStoreDeepLink.forPackage(
            ConnectionCandidates.PACKAGE_CHESS,
            "Chess.com"
        )
        assertEquals(PlayStoreDeepLink.SOURCE_LABEL, offer.sourceLabel)
        assertTrue(offer.marketUri.contains("com.chess"))
        assertTrue(offer.httpsUri.contains("play.google.com"))
        assertTrue(offer.note.contains("Google Play Store"))
        assertTrue(offer.note.contains("does not link", ignoreCase = true))
    }

    @Test
    fun registry_partnerEntries_matchDefaultStatus() {
        for (id in listOf(
            CapabilityRegistry.ID_PARTNER_SPOTIFY,
            CapabilityRegistry.ID_PARTNER_CHESS,
            CapabilityRegistry.ID_PARTNER_STRAVA
        )) {
            val entry = registry.get(id)!!
            assertEquals(CapabilityStatus.NOT_SUPPORTED, entry.status)
            assertFalse(registry.isActionable(id))
        }
        assertEquals(
            CapabilityStatus.NOT_SUPPORTED,
            registry.get(CapabilityRegistry.ID_OBSERVE_LESSON_COMPLETION)!!.status
        )
    }

    @Test
    fun linkedStub_onlyWithRealTokens_notClaimedHere() {
        // Optional LINKED stub exists on the enum but inventory never emits it without tokens.
        val connections = AppConnectionInventory.connections(FakeInstalledAppLauncher())
        assertTrue(connections.none { it.accountStatus == AppConnectionStatus.LINKED })
        assertTrue(connections.none { it.canClaimPartnerFeed })

        // Even a hand-built LINKED without scopes/sync must not claim a partner feed.
        val fakeLinked = AppConnection(
            id = "fake",
            packageName = ConnectionCandidates.PACKAGE_SPOTIFY,
            label = "Spotify",
            capabilityKey = CapabilityRegistry.ID_PARTNER_SPOTIFY,
            accountStatus = AppConnectionStatus.LINKED,
            grantedScopes = emptyList(),
            lastSyncAtEpochMs = null,
            installed = true,
            limitNote = "test"
        )
        assertFalse(fakeLinked.canClaimPartnerFeed)
        val usage = AppConnectionUsage.forConnection(fakeLinked, store.snapshot(), registry)
        assertFalse(usage.hasPartnerFeed)
        assertFalse(
            usage.intentions.any {
                AppConnectionUsage.ContributeKind.PARTNER_FEED in it.contributeKinds
            }
        )
    }
}
