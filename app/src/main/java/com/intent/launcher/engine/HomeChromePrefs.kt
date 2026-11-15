package com.intent.launcher.engine

/**
 * Appearance theme preference — independent of IntentionFlowStore / SCHEMA.
 * Default [SYSTEM] follows the platform dark setting.
 *
 * [HIGH_CONTRAST] is a real elevated-contrast palette (not a fake checkbox):
 * it still follows the device light/dark setting to pick the HC light or HC dark
 * token set, but ink / paper / line / muted are pushed toward WCAG-friendly extremes.
 */
enum class ThemePreference {
    SYSTEM,
    LIGHT,
    DARK,
    HIGH_CONTRAST;

    fun resolvesToDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM, HIGH_CONTRAST -> systemDark
        LIGHT -> false
        DARK -> true
    }

    /** True when Settings selected the dedicated high-contrast token set. */
    fun usesHighContrastTokens(): Boolean = this == HIGH_CONTRAST

    companion object {
        fun fromStorage(raw: String?): ThemePreference =
            entries.find { it.name == raw } ?: SYSTEM
    }
}

/**
 * User chrome preferences for Home (OS-136 screen-time bar visibility)
 * plus appearance theme (dark + high-contrast tokens). Independent of
 * floating-agent overlay state and IntentionFlowStore.
 */
interface HomeChromePrefs {
    /** When false, the top screen-time bar is omitted. Default true. */
    fun isScreenTimeBarVisible(): Boolean

    fun setScreenTimeBarVisible(visible: Boolean)

    /** Appearance: System / Light / Dark / High contrast. Default [ThemePreference.SYSTEM]. */
    fun themePreference(): ThemePreference

    fun setThemePreference(preference: ThemePreference)
}

/** In-memory stub for unit tests. */
class FakeHomeChromePrefs(
    var visible: Boolean = true,
    var theme: ThemePreference = ThemePreference.SYSTEM
) : HomeChromePrefs {
    override fun isScreenTimeBarVisible(): Boolean = visible

    override fun setScreenTimeBarVisible(visible: Boolean) {
        this.visible = visible
    }

    override fun themePreference(): ThemePreference = theme

    override fun setThemePreference(preference: ThemePreference) {
        theme = preference
    }
}
