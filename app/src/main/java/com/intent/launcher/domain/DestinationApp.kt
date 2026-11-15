package com.intent.launcher.domain

/**
 * App/tool destination for a session shape.
 * Opening this package is not lesson completion or partner evidence.
 */
data class DestinationApp(
    val packageName: String,
    val label: String
)

/**
 * Resolves destination packages for shapes that declare one.
 * Spanish → Duolingo; app_use → Instagram (primary Open); marathon / business none.
 *
 * CASE-06 also tracks YouTube for honesty copy — UsageStats cannot identify Shorts/Reels.
 */
object SessionDestinations {

    /** Duolingo — language practice destination for the Spanish shape. */
    const val SPANISH_PACKAGE = "com.duolingo"
    const val SPANISH_LABEL = "Duolingo"

    /** Instagram — primary Open destination for app-use reduction (CASE-06). */
    const val INSTAGRAM_PACKAGE = "com.instagram.android"
    const val INSTAGRAM_LABEL = "Instagram"

    /** YouTube — secondary tracked package for CASE-06; package time ≠ Shorts identity. */
    const val YOUTUBE_PACKAGE = "com.google.android.youtube"
    const val YOUTUBE_LABEL = "YouTube"

    fun forIntention(intention: Intention?): DestinationApp? {
        if (intention == null) return null
        return forShape(intention.shapeTag, intention.id)
    }

    fun forShape(shapeTag: String?, intentionId: String?): DestinationApp? =
        when {
            intentionId == SpanishScenario.INTENTION_ID || shapeTag == "language" ->
                DestinationApp(packageName = SPANISH_PACKAGE, label = SPANISH_LABEL)
            intentionId == AppUseReductionScenario.INTENTION_ID || shapeTag == "app_use" ->
                DestinationApp(packageName = INSTAGRAM_PACKAGE, label = INSTAGRAM_LABEL)
            else -> null
        }

    /** Packages CASE-06 may observe — none identify Shorts/Reels. */
    fun appUseTrackedPackages(): List<DestinationApp> = listOf(
        DestinationApp(packageName = INSTAGRAM_PACKAGE, label = INSTAGRAM_LABEL),
        DestinationApp(packageName = YOUTUBE_PACKAGE, label = YOUTUBE_LABEL)
    )

    fun isAppUsePackage(packageName: String?): Boolean =
        packageName == INSTAGRAM_PACKAGE || packageName == YOUTUBE_PACKAGE
}
