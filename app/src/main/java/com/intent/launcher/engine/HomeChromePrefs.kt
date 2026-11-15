package com.intent.launcher.engine

/**
 * Appearance theme preference — independent of IntentionFlowStore / SCHEMA.
 * Default [SYSTEM] follows the platform dark setting.
 */
enum class ThemePreference {
    SYSTEM,
    LIGHT,
    DARK;

    fun resolvesToDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun fromStorage(raw: String?): ThemePreference =
            entries.find { it.name == raw } ?: SYSTEM
    }
}

/**
 * User chrome preferences for Home (OS-136 screen-time bar visibility)
 * plus appearance theme (dark tokens). Independent of floating-agent overlay state
 * and IntentionFlowStore.
 */
interface HomeChromePrefs {
    /** When false, the top screen-time bar is omitted. Default true. */
    fun isScreenTimeBarVisible(): Boolean

    fun setScreenTimeBarVisible(visible: Boolean)

    /** Appearance: System / Light / Dark. Default [ThemePreference.SYSTEM]. */
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
