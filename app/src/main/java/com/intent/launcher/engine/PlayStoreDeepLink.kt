package com.intent.launcher.engine

/**
 * OS-164 light — source-labeled Play Store discovery for a missing candidate package.
 * Does not invent installs or claim partner linking.
 */
object PlayStoreDeepLink {

    const val SOURCE_LABEL = "Google Play Store"

    data class Offer(
        val packageName: String,
        val label: String,
        /** market:// URI preferred on-device. */
        val marketUri: String,
        /** https fallback when Play Store app is missing. */
        val httpsUri: String,
        val sourceLabel: String = SOURCE_LABEL,
        val note: String
    )

    fun forPackage(packageName: String, label: String): Offer =
        Offer(
            packageName = packageName,
            label = label,
            marketUri = "market://details?id=$packageName",
            httpsUri = "https://play.google.com/store/apps/details?id=$packageName",
            note = "Open $label on $SOURCE_LABEL to install. " +
                "Installing does not link a partner account or prove completion."
        )
}
