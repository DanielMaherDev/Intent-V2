package com.intent.launcher.platform

import android.content.Context
import com.intent.launcher.engine.HomeChromePrefs

/** SharedPreferences-backed Home chrome toggles (OS-136). */
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

    companion object {
        private const val PREFS = "intent_home_chrome"
        private const val KEY_SCREEN_TIME_BAR = "screen_time_bar_visible"
    }
}
