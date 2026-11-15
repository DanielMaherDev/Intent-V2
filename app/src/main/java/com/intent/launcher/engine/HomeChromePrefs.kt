package com.intent.launcher.engine

/**
 * User chrome preferences for Home (OS-136 screen-time bar visibility).
 * Independent of floating-agent overlay state.
 */
interface HomeChromePrefs {
    /** When false, the top screen-time bar is omitted. Default true. */
    fun isScreenTimeBarVisible(): Boolean

    fun setScreenTimeBarVisible(visible: Boolean)
}

/** In-memory stub for unit tests. */
class FakeHomeChromePrefs(
    var visible: Boolean = true
) : HomeChromePrefs {
    override fun isScreenTimeBarVisible(): Boolean = visible

    override fun setScreenTimeBarVisible(visible: Boolean) {
        this.visible = visible
    }
}
