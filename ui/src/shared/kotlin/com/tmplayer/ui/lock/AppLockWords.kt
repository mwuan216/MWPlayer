package com.tmplayer.ui.lock

import com.tmplayer.data.AppLockSettings
import com.tmplayer.i18n.L

/**
 * The App lock section's words, worked out once for the phone, the television and the desktop.
 */
object AppLockWords {

    /** The line under "App lock" on Settings' category list. */
    fun summary(lock: AppLockSettings): String = when {
        lock.kids -> L.lockSummaryKids(count = lock.kidsChats.size)
        lock.pinSet -> L.lockSummaryOn
        else -> L.lockSummaryOff
    }

    /** The line under "Allowed chats only": what it does now, or why it cannot be switched on yet. */
    fun kidsDetail(lock: AppLockSettings): String = when {
        !lock.pinSet -> L.lockKidsNeedsPin
        lock.kids -> L.lockKidsOn
        else -> L.lockKidsOff
    }
}
