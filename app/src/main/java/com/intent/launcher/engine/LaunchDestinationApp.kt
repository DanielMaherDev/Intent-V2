package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.domain.DestinationApp
import com.intent.launcher.domain.Intention
import com.intent.launcher.domain.NextAction
import com.intent.launcher.domain.SessionDestinations

/**
 * Validated command path for opening a session's destination installed app.
 * Consults [CapabilityRegistry] honesty — opening ≠ lesson completion.
 */
object LaunchDestinationApp {

    enum class Outcome {
        LAUNCHED,
        MISSING_APP,
        LAUNCH_FAILED,
        BLOCKED_BY_REGISTRY,
        NO_DESTINATION
    }

    data class Result(
        val outcome: Outcome,
        val destination: DestinationApp? = null,
        /** Honest note for Home / SessionCard — never claims lesson completion. */
        val note: String
    ) {
        val opened: Boolean get() = outcome == Outcome.LAUNCHED
    }

    fun resolve(
        intention: Intention?,
        nextAction: NextAction? = null
    ): DestinationApp? {
        nextAction?.destinationPackageName?.let { pkg ->
            val label = nextAction.destinationAppLabel ?: pkg
            return DestinationApp(packageName = pkg, label = label)
        }
        return SessionDestinations.forIntention(intention)
    }

    /**
     * Attempt to open [destination] when the registry allows launch_installed_app.
     * Does not mutate session-run state — Complete remains user-reported.
     */
    fun attempt(
        destination: DestinationApp?,
        launcher: InstalledAppLauncher,
        registry: CapabilityRegistry = CapabilityRegistry.default
    ): Result {
        if (destination == null) {
            return Result(
                outcome = Outcome.NO_DESTINATION,
                note = "This session has no destination app to open."
            )
        }
        if (!registry.canLaunchInstalledApp()) {
            val limit = registry.get(CapabilityRegistry.ID_LAUNCH_INSTALLED_APP)?.limitNote
                ?: "Opening installed apps is not available in this build."
            return Result(
                outcome = Outcome.BLOCKED_BY_REGISTRY,
                destination = destination,
                note = "$limit Opening an app is not lesson completion."
            )
        }
        if (!launcher.isInstalled(destination.packageName)) {
            return Result(
                outcome = Outcome.MISSING_APP,
                destination = destination,
                note = "${destination.label} isn't installed. Opening an app is not lesson completion — " +
                    "install it, or mark Complete when you finish another way."
            )
        }
        return if (launcher.launch(destination.packageName)) {
            Result(
                outcome = Outcome.LAUNCHED,
                destination = destination,
                note = "Opened ${destination.label}. Opening an app is not lesson completion."
            )
        } else {
            Result(
                outcome = Outcome.LAUNCH_FAILED,
                destination = destination,
                note = "Couldn't open ${destination.label}. Opening an app is not lesson completion."
            )
        }
    }
}
