package com.tmplayer.data

import com.tmplayer.i18n.L

/**
 * "Update ready" (CP76): a notification that a new version is out, said only while TMPlayer is
 * closed (a phone) or in the background (the desktop), since the update popup already says it to
 * an app in front. Once per version, never for one the viewer chose to skip, and only behind its
 * own switch. Pressing it opens the app, whose launch check shows the popup.
 */
object UpdateAlerts {

    /**
     * The release to announce, or null. [alertedFor] is the version last announced, [skipped] the
     * one the viewer said to skip, [inFront] whether the app is on screen.
     */
    fun due(state: UpdateState, alertedFor: String, skipped: String, switchOn: Boolean, inFront: Boolean): Release? {
        if (!switchOn || inFront || !Updates.enabled) return null
        val release = (state as? UpdateState.Available)?.release ?: return null
        if (release.version == alertedFor || release.version == skipped) return null
        return release
    }

    /** The notification's title and line for [release]. */
    fun words(release: Release): Pair<String, String> =
        L.notifyUpdateReadyTitle(version = release.version) to L.notifyUpdateReadyBody

    /**
     * Checks the feed (quietly, as the scheduled check does) and returns the release to announce,
     * already recorded as announced. Off with the switch, nothing is fetched.
     */
    suspend fun check(settings: SettingsStore, inFront: () -> Boolean): Release? {
        if (!settings.noticeOnNow(Notice.UpdateReady) || !Updates.enabled) return null
        val skipped = settings.updatePrefs.skippedVersion()
        if (Updates.state.value !is UpdateState.Available) Updates.check(quiet = true, skipped = skipped)
        val release = due(Updates.state.value, settings.updateAlertedNow(), skipped, switchOn = true, inFront = inFront())
            ?: return null
        settings.setUpdateAlerted(release.version)
        return release
    }
}
