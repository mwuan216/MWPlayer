package com.tmplayer.ui.browse

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import com.tmplayer.data.Series
import kotlinx.coroutines.delay

/**
 * Which show's page is open over a screen, kept across a configuration change.
 *
 * A [Series] is not something a saved state can hold, so what is saved is the show's key and the
 * chat it is in; after the activity is made again (a phone turned on its side), the show is looked
 * up again in what the screen has loaded, and its page comes back. Until it has been found the page
 * stays closed; a show that never turns up within [RESTORE_WAIT_MS] is forgotten, so a page does
 * not open by itself long after the turn.
 */
@Stable
class OpenSeries internal constructor(
    private val token: MutableState<String?>,
    private val held: MutableState<Series?>,
    private val found: () -> Series?,
) {
    /** The show whose page is open, or null while none is. */
    val series: Series? get() = held.value ?: found()

    /** Opens [series]'s page, or closes the page with null. */
    fun open(series: Series?) {
        held.value = series
        token.value = series?.let { tokenOf(it.key, it.cover.chatId) }
    }

    internal companion object {
        fun tokenOf(key: String, chatId: Long) = "$chatId:$key"

        /** The show's key and chat in a saved [token], or null for one this cannot read. */
        fun parse(token: String): Pair<String, Long>? {
            val at = token.indexOf(':')
            if (at <= 0) return null
            val chat = token.substring(0, at).toLongOrNull() ?: return null
            return token.substring(at + 1) to chat
        }
    }
}

/** How long a page restored after a configuration change waits for its show to be loaded again. */
const val RESTORE_WAIT_MS = 15_000L

/**
 * Remembers the open show across a configuration change; see [OpenSeries]. [find] looks a show up
 * by its key and chat among what the screen has loaded, and [inputs] reset it, as `remember`'s do.
 */
@Composable
fun rememberOpenSeries(vararg inputs: Any?, find: (key: String, chatId: Long) -> Series?): OpenSeries {
    val token = rememberSaveable(*inputs) { mutableStateOf<String?>(null) }
    val held = remember(*inputs) { mutableStateOf<Series?>(null) }
    val lookup by rememberUpdatedState(find)
    val open = remember(token, held) {
        OpenSeries(token, held) {
            // Only while a restored page has not found its show yet.
            token.value?.let(OpenSeries::parse)?.let { (key, chat) -> lookup(key, chat) }
        }
    }
    val restoring = held.value == null && token.value != null
    if (restoring) {
        val found = open.series
        // Found: hold it from now on, so a list that reloads under the page does not close it.
        if (found != null) SideEffect { if (held.value == null) held.value = found }
    }
    LaunchedEffect(token.value, restoring) {
        if (!restoring) return@LaunchedEffect
        delay(RESTORE_WAIT_MS)
        if (held.value == null) token.value = null
    }
    return open
}
