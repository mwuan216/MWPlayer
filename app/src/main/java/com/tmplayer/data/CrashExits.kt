package com.tmplayer.data

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import io.sentry.Hint
import io.sentry.hints.AbnormalExit
import io.sentry.hints.NativeCrashExit
import io.sentry.util.HintUtils
import java.io.File
import java.util.Properties

/**
 * The app stopping in a way its own crash handler never sees: Android killing it for not
 * responding (an ANR), or a crash in native code (TDLib, FFmpeg, the media codecs).
 *
 * Neither runs any of this app's code as it happens, so nothing can be written down at the time.
 * Android keeps a list of how each recent process ended instead (`getHistoricalProcessExitReasons`,
 * Android 11 and newer), and Sentry's ANR and tombstone integrations build their reports from that
 * list at the next start. With the switch off, [latestUnasked] reads the same list, so the viewer
 * can be asked about the newest such exit as they are about a crash.
 */
internal enum class ExitKind { NotResponding, NativeCrash }

/** One process exit from Android's list: what it was and when, in its own milliseconds. */
internal data class AppExit(val kind: ExitKind, val timestamp: Long)

internal object CrashExits {

    /** How far back the list is read: enough for the last few starts, and a stop for a long one. */
    private const val MAX_EXITS = 16

    /**
     * The newest ANR or native crash after [askedUpTo] and no older than [CrashRecord.KEEP_MILLIS],
     * or null.
     */
    fun latestUnasked(context: Context, askedUpTo: Long, now: Long): AppExit? =
        exits(context).filter { it.timestamp > askedUpTo && now - it.timestamp <= CrashRecord.KEEP_MILLIS }
            .maxByOrNull { it.timestamp }

    /** The newest ANR or native crash of any age, for marking everything up to it as dealt with. */
    fun latest(context: Context): Long? = exits(context).maxOfOrNull { it.timestamp }

    /**
     * This app's ANRs and native crashes in Android's list. ANRs from Android 11; native crashes
     * from Android 12, where the SDK's tombstone integration starts, since an exit nothing could
     * send would be asked about for nothing.
     */
    private fun exits(context: Context): List<AppExit> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return emptyList()
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return emptyList()
        val list = runCatching { manager.getHistoricalProcessExitReasons(context.packageName, 0, MAX_EXITS) }
            .getOrNull() ?: return emptyList()
        return list.mapNotNull { info ->
            when {
                info.reason == ApplicationExitInfo.REASON_ANR -> AppExit(ExitKind.NotResponding, info.timestamp)
                info.reason == ApplicationExitInfo.REASON_CRASH_NATIVE &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> AppExit(ExitKind.NativeCrash, info.timestamp)
                else -> null
            }
        }
    }

    /**
     * The moment of the process exit a report is about, when the SDK built it from Android's list:
     * the ANR integration's hint is an [AbnormalExit], the tombstone integration's a
     * [NativeCrashExit], and both carry the exit's own timestamp. Null for every other report.
     */
    fun exitTime(hint: Hint?): Long? = when (val sdk = hint?.let(HintUtils::getSentrySdkHint)) {
        is AbnormalExit -> sdk.timestamp()
        is NativeCrashExit -> sdk.timestamp()
        else -> null
    }

    /**
     * Whether a report about the exit at [exitTime] may go.
     *
     * Never one at or before [declinedUpTo]: the viewer said Don't send, and turning the switch on
     * later must not send it after all. The one the viewer has just agreed to send ([accepted])
     * goes whatever the switch says; any other goes only with the switch on ([enabled]).
     */
    fun allows(exitTime: Long, declinedUpTo: Long, accepted: Long?, enabled: Boolean): Boolean = when {
        exitTime <= declinedUpTo -> false
        exitTime == accepted -> true
        else -> enabled
    }
}

/**
 * What the viewer has been asked about and turned down, in a small private file of its own.
 *
 * [askedUpTo] is the newest exit the viewer was asked about, or that went with the switch on, so
 * nothing is asked about twice. [declinedUpTo] is the newest exit they said Don't send to, which
 * the beforeSend gate holds to for good (see [CrashExits.allows]). Both are Android's exit
 * timestamps, and both only ever move forward.
 */
internal class ExitAnswers(private val file: File) {

    private val props = Properties().apply {
        runCatching { if (file.isFile) file.inputStream().use { load(it) } }
    }

    @get:Synchronized
    val askedUpTo: Long get() = props.getProperty(ASKED)?.toLongOrNull() ?: Long.MIN_VALUE

    @get:Synchronized
    val declinedUpTo: Long get() = props.getProperty(DECLINED)?.toLongOrNull() ?: Long.MIN_VALUE

    /** Asked about, or sent: not to be offered again. */
    @Synchronized
    fun markAsked(timestamp: Long) {
        if (timestamp <= askedUpTo) return
        props.setProperty(ASKED, timestamp.toString())
        save()
    }

    /** Turned down: not offered again, and never sent, even with the switch on later. */
    @Synchronized
    fun markDeclined(timestamp: Long) {
        if (timestamp > askedUpTo) props.setProperty(ASKED, timestamp.toString())
        if (timestamp > declinedUpTo) props.setProperty(DECLINED, timestamp.toString())
        save()
    }

    private fun save() {
        runCatching {
            file.parentFile?.mkdirs()
            val partial = File(file.path + ".part")
            partial.outputStream().use { props.store(it, null) }
            partial.renameTo(file)
        }
    }

    private companion object {
        const val ASKED = "askedUpTo"
        const val DECLINED = "declinedUpTo"
    }
}
