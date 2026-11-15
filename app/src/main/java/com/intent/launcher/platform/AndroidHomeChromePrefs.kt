package com.intent.launcher.platform

import android.content.Context
import com.intent.launcher.engine.HomeChromePrefs
import com.intent.launcher.engine.ThemePreference

/** SharedPreferences-backed Home chrome toggles (OS-136) + appearance theme. */
class AndroidHomeChromePrefs(
    context: Context
) : HomeChromePrefs {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun isScreenTimeBarVisible(): Boolean =
        prefs.getBoolean(KEY_SCREEN_TIME_BAR, true)

    override fun setScreenTimeBarVisible(visible: Boolean) {
        prefs.edit().putBoolean(KEY_SCREEN_TIME_BAR, visible).apply()
    }

    override fun themePreference(): ThemePreference =
        ThemePreference.fromStorage(prefs.getString(KEY_THEME, null))

    override fun setThemePreference(preference: ThemePreference) {
        prefs.edit().putString(KEY_THEME, preference.name).apply()
    }

    companion object {
        private const val PREFS = "intent_home_chrome"
        private const val KEY_SCREEN_TIME_BAR = "screen_time_bar_visible"
        private const val KEY_THEME = "theme_preference"
    }
}
