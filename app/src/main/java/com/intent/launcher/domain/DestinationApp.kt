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
 * Spanish → Duolingo; marathon / business have no installed-app destination in this build.
 */
object SessionDestinations {

    /** Duolingo — language practice destination for the Spanish shape. */
    const val SPANISH_PACKAGE = "com.duolingo"
    const val SPANISH_LABEL = "Duolingo"

    fun forIntention(intention: Intention?): DestinationApp? {
        if (intention == null) return null
        return forShape(intention.shapeTag, intention.id)
    }

    fun forShape(shapeTag: String?, intentionId: String?): DestinationApp? =
        when {
            intentionId == SpanishScenario.INTENTION_ID || shapeTag == "language" ->
                DestinationApp(packageName = SPANISH_PACKAGE, label = SPANISH_LABEL)
            else -> null
        }
}
