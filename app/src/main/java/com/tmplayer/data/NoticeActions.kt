package com.tmplayer.data

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.tmplayer.App
import com.tmplayer.ui.about.About
import kotlinx.coroutines.launch

/**
 * The support notification's buttons (CP76): Buy me a coffee and Star on GitHub open the page in
 * the browser through [NoticeLinkActivity] (since Android 12 a notification may only open an
 * activity directly, never from a receiver), and Not now ([NoticeDismissReceiver]) only takes the
 * notification down, which already counted as asked when it went out.
 */
object NoticeActions {

    const val EXTRA_LINK = "com.tmplayer.extra.NOTICE_LINK"
    const val LINK_COFFEE = "coffee"
    const val LINK_STAR = "star"

    fun link(context: Context, which: String, notificationId: Int): PendingIntent {
        val intent = Intent(context, NoticeLinkActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY)
            .putExtra(EXTRA_LINK, which)
            .putExtra(AppNotices.EXTRA_DISMISS, notificationId)
        return PendingIntent.getActivity(
            context,
            notificationId + which.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun dismiss(context: Context, notificationId: Int): PendingIntent {
        val intent = Intent(context, NoticeDismissReceiver::class.java).putExtra(AppNotices.EXTRA_DISMISS, notificationId)
        return PendingIntent.getBroadcast(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun cancel(context: Context, intent: Intent?) {
        val id = intent?.getIntExtra(AppNotices.EXTRA_DISMISS, 0) ?: 0
        if (id != 0) runCatching { AppNotices.manager(context).cancel(id) }
    }
}

/** Not now: the notification goes, and nothing else changes. */
class NoticeDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        NoticeActions.cancel(context, intent)
    }
}

/**
 * Opens Buy Me a Coffee or the GitHub page in the browser and closes at once, with no window of
 * its own. Star on GitHub is remembered, so no star is asked for again.
 */
class NoticeLinkActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val which = intent?.getStringExtra(NoticeActions.EXTRA_LINK)
        NoticeActions.cancel(this, intent)
        val url = when (which) {
            NoticeActions.LINK_STAR -> About.SOURCE
            NoticeActions.LINK_COFFEE -> About.COFFEE
            else -> null
        }
        if (which == NoticeActions.LINK_STAR) {
            val store = SettingsStore(applicationContext)
            App.backgroundScope.launch { runCatching { store.markSupportStarred() } }
        }
        if (url != null) {
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
        finish()
    }
}
