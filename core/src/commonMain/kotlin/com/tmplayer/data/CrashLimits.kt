package com.tmplayer.data

import io.sentry.SentryEvent
import io.sentry.protocol.SentryStackFrame
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * How many reports one device may send in a day, so a crash loop stays inside Sentry's free plan.
 *
 * The plan takes 5,000 errors a month for every device together. A crash on every start, on a
 * few hundred phones and televisions at once, would spend that in a day and leave nothing for
 * the rest of the month. So each device sends the same crash (the exception type and the top
 * frame of the app's own code) at most once a day, and no more than [MAX_PER_DAY] reports a day
 * of any kind. Sentry's own spike protection is the second line, for what this cannot see.
 *
 * Counted in a small private file next to the pending crash, by the UTC day of [clock]. The file
 * holds the day, the count and the crashes already sent that day, as class and method names; no
 * message, no file and nothing about the viewer.
 */
class CrashLimits(
    private val file: File,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /**
     * Whether [event] may go, counting it if so. Called last in beforeSend, after the scrubber,
     * on whatever thread the SDK sends from.
     */
    @Synchronized
    fun admit(event: SentryEvent): Boolean {
        val today = clock() / DAY_MILLIS
        val state = read()?.takeIf { it.day == today } ?: State(today, 0, emptyList())
        val crash = fingerprint(event)
        if (state.count >= MAX_PER_DAY) return false
        if (crash != null && crash in state.seen) return false
        write(State(today, state.count + 1, if (crash != null) (state.seen + crash).takeLast(MAX_PER_DAY) else state.seen))
        return true
    }

    private class State(val day: Long, val count: Int, val seen: List<String>)

    private fun read(): State? = runCatching {
        if (!file.isFile) return null
        val json = JSONObject(file.readText())
        val seen = json.getJSONArray("seen")
        State(json.getLong("day"), json.getInt("count"), (0 until seen.length()).map { seen.getString(it) })
    }.getOrNull()

    private fun write(state: State) {
        runCatching {
            file.parentFile?.mkdirs()
            val partial = File(file.path + ".part")
            partial.writeText(
                JSONObject().put("day", state.day).put("count", state.count).put("seen", JSONArray(state.seen)).toString(),
            )
            partial.renameTo(file)
        }
    }

    companion object {

        /** Reports a device may send in a day, of every kind together. */
        const val MAX_PER_DAY = 10

        private const val DAY_MILLIS = 24L * 60 * 60 * 1000

        /**
         * What makes two reports the same crash: the type of the exception that was thrown (the
         * last in Sentry's list, causes first) and the top frame of the app's own code in it, or
         * the top frame of any code where none is the app's, as in a native crash in a library.
         * The line number is part of the frame, so two crashes in one method stay apart. Null for
         * a report with no exception at all, which is counted but never taken for another.
         */
        fun fingerprint(event: SentryEvent): String? {
            val exception = event.exceptions?.lastOrNull() ?: return null
            val frames = exception.stacktrace?.frames.orEmpty()
            val top = frames.lastOrNull { it.isInApp == true } ?: frames.lastOrNull()
            return listOfNotNull(exception.module, exception.type, top?.let(::frameName)).joinToString(" ")
        }

        private fun frameName(frame: SentryStackFrame): String =
            listOfNotNull(frame.module ?: frame.`package`, frame.function, frame.lineno?.toString(), frame.instructionAddr)
                .joinToString(":")
    }
}
