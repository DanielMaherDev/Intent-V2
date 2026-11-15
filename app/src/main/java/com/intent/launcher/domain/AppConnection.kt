package com.intent.launcher.domain

/**
 * OS-158 — local partner / destination connection record.
 *
 * Account status is honest: [DISCONNECTED] when no tokens exist,
 * [NOT_SUPPORTED] when partner OAuth/sync is not shipped,
 * [LINKED] only when real tokens exist (not used in this build).
 */
enum class AppConnectionStatus {
    DISCONNECTED,
    NOT_SUPPORTED,
    LINKED
}

data class AppConnection(
    val id: String,
    val packageName: String,
    val label: String,
    /** Capability registry key (e.g. partner_spotify). */
    val capabilityKey: String,
    val accountStatus: AppConnectionStatus,
    val grantedScopes: List<String> = emptyList(),
    val lastSyncAtEpochMs: Long? = null,
    /** True only when PackageManager resolves the package as installed. */
    val installed: Boolean,
    /** Honest product limit for UI / agent. */
    val limitNote: String
) {
    val canClaimPartnerFeed: Boolean
        get() = accountStatus == AppConnectionStatus.LINKED &&
            grantedScopes.isNotEmpty() &&
            lastSyncAtEpochMs != null
}

/**
 * OS-157 — static meaningful candidates. Visibility uses `<queries>` + PackageManager;
 * never invent installs.
 */
data class ConnectionCandidate(
    val id: String,
    val packageName: String,
    val label: String,
    val capabilityKey: String,
    /** Default account status when no tokens exist. */
    val defaultStatus: AppConnectionStatus,
    val limitNote: String
)

object ConnectionCandidates {

    const val ID_DUOLINGO = "conn_duolingo"
    const val ID_CHESS = "conn_chess"
    const val ID_SPOTIFY = "conn_spotify"
    const val ID_STRAVA = "conn_strava"

    const val PACKAGE_DUOLINGO = SessionDestinations.SPANISH_PACKAGE
    const val PACKAGE_CHESS = "com.chess"
    const val PACKAGE_SPOTIFY = "com.spotify.music"
    const val PACKAGE_STRAVA = "com.strava"

    val ALL: List<ConnectionCandidate> = listOf(
        ConnectionCandidate(
            id = ID_DUOLINGO,
            packageName = PACKAGE_DUOLINGO,
            label = SessionDestinations.SPANISH_LABEL,
            capabilityKey = "observe_lesson_completion",
            defaultStatus = AppConnectionStatus.DISCONNECTED,
            limitNote = "Duolingo can be opened and held as SAID affinity. " +
                "Lesson completion is not observed unless a reliable partner source exists."
        ),
        ConnectionCandidate(
            id = ID_CHESS,
            packageName = PACKAGE_CHESS,
            label = "Chess.com",
            capabilityKey = "partner_chess",
            defaultStatus = AppConnectionStatus.NOT_SUPPORTED,
            limitNote = "Chess.com partner ratings / game sync are not supported in this build. " +
                "Use a manual SAID baseline — never invent ratings or games."
        ),
        ConnectionCandidate(
            id = ID_SPOTIFY,
            packageName = PACKAGE_SPOTIFY,
            label = "Spotify",
            capabilityKey = "partner_spotify",
            defaultStatus = AppConnectionStatus.NOT_SUPPORTED,
            limitNote = "Spotify OAuth, playback state, and controls are not supported in this build. " +
                "No fake now-playing or completion claims."
        ),
        ConnectionCandidate(
            id = ID_STRAVA,
            packageName = PACKAGE_STRAVA,
            label = "Strava",
            capabilityKey = "partner_strava",
            defaultStatus = AppConnectionStatus.NOT_SUPPORTED,
            limitNote = "Strava activity sync is not supported in this build. " +
                "No fabricated runs or auto-Complete from partner data."
        )
    )

    fun byId(id: String): ConnectionCandidate? = ALL.firstOrNull { it.id == id }

    fun byPackage(packageName: String): ConnectionCandidate? =
        ALL.firstOrNull { it.packageName == packageName }
}
