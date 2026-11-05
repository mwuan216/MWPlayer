package com.tmplayer.data

import com.tmplayer.i18n.L

/**
 * "Finished downloads" (CP76): one notification when a download is done or did not finish, with
 * Play or Try again, and one for a whole season or for every piece of a cut video rather than one
 * per file.
 *
 * Files are grouped by what they are part of ([groupOf]): the byte pieces of one cut video
 * ([SplitPieces]) by the chat and the pieces' key, the episodes of one season by the chat, the
 * show and the season, anything else on its own. A group's notification goes out when the last of
 * its files still on the queue has settled ([record]): finished, or failed. A paused or held file
 * is still on its way, so the group waits for it; a cancelled one is simply gone.
 *
 * The same rules on Android (the download service) and the desktop (its runner). Nothing here
 * posts anything: [words] is what the notification says, and the caller draws it. With the kids
 * lock hiding the chat, or the app lock asking when the app opens, it names no video ([words]'s
 * `private`).
 */
object DownloadResults {

    /** What a group stands for, which decides how it is worded. */
    enum class Kind {
        /** One video, alone. */
        Video,

        /** Every byte piece of one cut video. */
        Pieces,

        /** Episodes of one season of a show. */
        Season,
    }

    /** One group: its [key], what it is, and the show or video it is named after. */
    data class Group(val key: String, val kind: Kind, val name: String, val season: Int? = null)

    /** One file that settled: [ok] when it is in Downloads. */
    data class Settled(val request: DownloadRequest, val ok: Boolean)

    /** A group whose every file has settled: what the one notification is about. */
    data class Result(val group: Group, val done: List<DownloadRequest>, val failed: List<DownloadRequest>) {
        val total: Int get() = done.size + failed.size
    }

    /** The notification's words, the video Play opens, and the files Try again asks for. */
    data class Words(
        val title: String,
        val body: String?,
        val play: DownloadRequest?,
        val retry: List<DownloadRequest>,
    )

    /** The group [request] belongs to. */
    fun groupOf(request: DownloadRequest): Group {
        val name = request.fileName.ifBlank { request.title }
        SplitPieces.pieceOf(name)?.takeIf { it.certain }?.let { piece ->
            val title = MediaName.parse(piece.base).title.ifBlank { piece.base }
            return Group("pieces:${request.chatId}:${piece.key}", Kind.Pieces, title)
        }
        val parsed = MediaName.parse(name, request.title.takeIf { it != name })
        if (parsed.isEpisode) {
            val show = SeriesShelf.keyOf(parsed.title)
            if (show.isNotEmpty()) {
                return Group("season:${request.chatId}:$show|${parsed.season ?: 0}", Kind.Season, parsed.title, parsed.season)
            }
        }
        return Group("file:${request.chatId}:${request.messageId}:${request.fileId}", Kind.Video, request.title)
    }

    private val lock = Any()
    private val pending = HashMap<String, MutableList<Settled>>()

    /**
     * One file has settled. [outstanding] is every other request still on the queue (queued,
     * running, held, paused or moving; not failed ones). Returns the group's result once nothing
     * of it is outstanding, and forgets the group; null while some of it is still to come.
     */
    fun record(request: DownloadRequest, ok: Boolean, outstanding: Collection<DownloadRequest>): Result? {
        val group = groupOf(request)
        synchronized(lock) {
            val list = pending.getOrPut(group.key) { mutableListOf() }
            list.removeAll { it.request.fileId == request.fileId }
            list += Settled(request, ok)
            val waiting = outstanding.any { it.fileId != request.fileId && groupOf(it).key == group.key }
            if (waiting) return null
            pending.remove(group.key)
            return Result(group, list.filter { it.ok }.map { it.request }, list.filter { !it.ok }.map { it.request })
        }
    }

    /** Forgets every group still collecting, for tests. */
    fun reset() = synchronized(lock) { pending.clear() }

    /** The episode a season's Play opens: the earliest that downloaded. */
    private fun firstEpisode(done: List<DownloadRequest>): DownloadRequest? =
        done.minWithOrNull(
            compareBy<DownloadRequest>(
                { MediaName.parse(it.fileName.ifBlank { it.title }, it.title).season ?: 0 },
                { MediaName.parse(it.fileName.ifBlank { it.title }, it.title).episode ?: Int.MAX_VALUE },
                { it.messageId },
            ),
        )

    /** The piece a cut video's Play opens: the first, from which the player finds the rest. */
    private fun firstPiece(done: List<DownloadRequest>): DownloadRequest? =
        done.minByOrNull { SplitPieces.pieceOf(it.fileName.ifBlank { it.title })?.number ?: Int.MAX_VALUE }

    /**
     * What [result]'s notification says. [reason] is a lone failure's own sentence, when there is
     * one. [private] names nothing: the kids lock hides the chat, or the app lock is asked for on
     * opening. A season of one episode, or one piece alone, is worded as the video it is.
     */
    fun words(result: Result, private: Boolean, reason: String? = null): Words {
        val group = result.group
        val ok = result.failed.isEmpty()
        if (private) {
            return Words(
                title = if (ok) L.notifyDownloadDonePrivate else L.notifyDownloadFailedPrivate,
                body = null,
                play = null,
                retry = result.failed,
            )
        }
        val alone = result.total == 1 || group.kind == Kind.Video
        if (alone) {
            val request = result.done.firstOrNull() ?: result.failed.first()
            return Words(
                title = request.title,
                body = if (ok) L.notifyDownloaded else reason ?: L.downloadsFailed,
                play = result.done.firstOrNull(),
                retry = result.failed,
            )
        }
        return when (group.kind) {
            Kind.Pieces -> Words(
                title = group.name,
                body = if (ok) {
                    L.notifyPiecesDone
                } else {
                    L.notifyPiecesFailed(failed = result.failed.size, total = result.total)
                },
                // A cut video plays from its first piece only when every piece is here.
                play = if (ok) firstPiece(result.done) else null,
                retry = result.failed,
            )
            else -> Words(
                title = group.season?.let { L.notifySeasonTitle(season = it, show = group.name) } ?: group.name,
                body = when {
                    ok -> L.notifySeasonDone(count = result.done.size)
                    result.done.isEmpty() -> L.notifySeasonFailed(count = result.failed.size)
                    else -> L.notifySeasonPartial(done = result.done.size, total = result.total)
                },
                play = firstEpisode(result.done),
                retry = result.failed,
            )
        }
    }

    /**
     * Whether a result about [chatId] must name nothing: the kids lock leaves the chat out, or the
     * app lock asks for its PIN when the app opens.
     */
    fun isPrivate(chatId: Long, allowed: Set<Long>?, lock: AppLockSettings): Boolean =
        !KidsLock.allows(allowed, chatId) || (lock.pinSet && lock.onOpen)

    /** A stable notification id for [group], clear of the ids the download service uses. */
    fun notificationId(group: Group): Int = RESULT_ID_BASE + (group.key.hashCode() and 0x7FFFF)

    private const val RESULT_ID_BASE = 0x300000
}
