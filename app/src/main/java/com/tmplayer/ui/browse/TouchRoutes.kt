package com.tmplayer.ui.browse

/** Where Back goes between the pages the phone's floating bar reaches. */
internal object TouchRoutes {
    /**
     * Where Back from [to] should go, when [to] was opened from [from].
     *
     * Settings and Downloads sit side by side behind the bar's picture ([lateral]): stepping from one to the
     * other is a change of page, not a step deeper, so Back from the second goes where Back from
     * the first went ([fromBack]) rather than bouncing between the two. When that is [to] itself
     * (Downloads opened from Settings' Cached videos row, then Settings from the popover), [to] keeps the
     * Back it had ([toBack]). Anything that would still land on a side page falls back to [home].
     */
    fun <T> backTarget(to: T, from: T, fromBack: T?, toBack: T?, lateral: (T) -> Boolean, home: T): T {
        val target = if (lateral(from)) fromBack ?: home else from
        if (target == to) return toBack?.takeUnless { it == to || lateral(it) } ?: home
        return if (lateral(target)) home else target
    }
}
