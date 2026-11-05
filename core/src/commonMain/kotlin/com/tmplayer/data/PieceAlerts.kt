package com.tmplayer.data

import com.tmplayer.i18n.L
import com.tmplayer.platform.Logger
import dev.g000sha256.tdl.TdlClient
import kotlinx.coroutines.flow.first

/**
 * "Missing parts of a video" (CP76): a cut video ([SplitPieces]) the viewer played with a piece
 * missing is remembered ([remember], from the player), and a later check looks for its pieces
 * again ([check]). When a missing piece has been posted the notification says so, with Play:
 * "Part 4 of Night Train is in Video Club", or "Every part of Night Train is in Video Club now"
 * once nothing is missing. A video whose pieces are all there, or that has waited [KEEP_MS], is
 * forgotten.
 *
 * Remembered whether the switch is on or not (a few lines, on this device only), so turning it on
 * later still covers a video played before. The kids lock leaves a hidden chat's videos out, and
 * with the app lock on ("Ask when the app opens") the notification names nothing.
 */
object PieceAlerts {

    /** At most this many videos are remembered, the newest first. */
    const val LIMIT = 16

    const val KEEP_MS = 60L * 24 * 60 * 60 * 1000

    private const val TAG = "PieceAlerts"

    /**
     * A cut video with a gap: its first piece's message (the one the player is opened on), the
     * pieces' [key], and which were missing out of [total] when it was last looked at.
     */
    data class Gap(
        val chatId: Long,
        val messageId: Long,
        val fileId: Int,
        val fileName: String,
        val title: String,
        val chatTitle: String,
        val key: String,
        /** The pieces missing, numbered from 1 as a viewer counts. */
        val missing: List<Int>,
        val total: Int,
        val at: Long,
    ) {
        fun lead(): MediaItem = MediaItem(
            chatId = chatId,
            messageId = messageId,
            fileId = fileId,
            title = title,
            sizeBytes = 0,
            durationSec = 0,
            mimeType = "",
            thumbnailFileId = 0,
            miniThumbnail = null,
            date = 0,
            fileName = fileName,
        )
    }

    /** What a look at a remembered video found. */
    data class Alert(val title: String, val body: String?, val play: MediaItem?, val chatTitle: String)

    /**
     * Adds what the player saw: [pieces] of one cut video in [chatTitle]. Nothing when no piece is
     * missing; a complete video is forgotten instead.
     */
    fun remember(gaps: List<Gap>, pieces: List<MediaItem>, chatTitle: String, now: Long): List<Gap> {
        val lead = SplitPieces.ordered(pieces).firstOrNull() ?: return gaps
        val piece = pieces.firstNotNullOfOrNull { it.splitPiece } ?: return gaps
        val report = SplitPieces.report(pieces)
        val rest = gaps.filter { !(it.chatId == lead.chatId && it.key == piece.key) }
        if (report.complete) return rest
        val gap = Gap(
            chatId = lead.chatId,
            messageId = lead.messageId,
            fileId = lead.fileId,
            fileName = lead.fileName,
            title = MediaName.parse(piece.base).title.ifBlank { piece.base },
            chatTitle = chatTitle,
            key = piece.key,
            missing = report.missing,
            total = report.total,
            at = now,
        )
        return (listOf(gap) + rest).take(LIMIT)
    }

    /**
     * Compares [gap] with [pieces] found now. Returns the notification when fewer pieces are
     * missing than before (null otherwise), and what to remember instead of [gap] (null: forget it).
     */
    fun compare(gap: Gap, pieces: List<MediaItem>, private: Boolean, now: Long): Pair<Alert?, Gap?> {
        if (pieces.isEmpty()) return null to gap.takeIf { now - it.at < KEEP_MS }
        val report = SplitPieces.report(pieces)
        val lead = SplitPieces.ordered(pieces).firstOrNull()
        val arrived = gap.missing.filter { it !in report.missing && it <= report.total }
        val fewer = arrived.isNotEmpty()
        val keep = if (report.complete) null else gap.copy(missing = report.missing, total = report.total)
        if (!fewer) return null to keep?.takeIf { now - gap.at < KEEP_MS }
        val alert = when {
            private -> Alert(L.notifyPiecesPrivate, null, null, "")
            report.complete -> Alert(
                L.notifyPiecesComplete(title = gap.title, chat = gap.chatTitle),
                L.notifyPiecesCompleteBody,
                lead,
                gap.chatTitle,
            )
            else -> {
                Alert(
                    // The lowest piece that was missing before and is here now.
                    L.notifyPieceArrived(number = arrived.min(), title = gap.title, chat = gap.chatTitle),
                    L.notifyPieceArrivedBody(playable = report.playable, total = report.total),
                    lead.takeIf { report.playable > 0 },
                    gap.chatTitle,
                )
            }
        }
        return alert to keep
    }

    /**
     * Looks at every remembered video the kids lock lets through, stores what is left and returns
     * the notifications to post. Off with the switch, it does nothing.
     */
    suspend fun check(td: TdlClient, settings: SettingsStore, allowed: Set<Long>?, now: Long): List<Alert> {
        if (!settings.noticeOnNow(Notice.MissingPieces)) return emptyList()
        val gaps = settings.pieceGapsNow()
        if (gaps.isEmpty()) return emptyList()
        val lock = settings.appLock.first()
        val private = lock.pinSet && lock.onOpen
        val repository = ChatRepository(td)
        val alerts = mutableListOf<Alert>()
        val kept = mutableListOf<Gap>()
        for (gap in gaps) {
            if (!KidsLock.allows(allowed, gap.chatId)) {
                kept += gap
                continue
            }
            val pieces = runCatching {
                SplitPieces.lookUp(gap.lead()) { query, cursors ->
                    runCatching { repository.mediaPage(gap.chatId, cursors, query = query) }.getOrNull()
                }
            }.onFailure { Logger.w(TAG, "pieces not checked: ${it.message}") }.getOrNull().orEmpty()
            // One piece found is the lead alone: the search came back empty, which says nothing.
            val (alert, next) = compare(gap, pieces.takeIf { it.size > 1 }.orEmpty(), private, now)
            alert?.let { alerts += it }
            next?.let { kept += it }
        }
        settings.setPieceGaps(kept)
        return alerts
    }

    /** The player saw [pieces] of a cut video: remembered when one is missing, forgotten when not. */
    suspend fun notePlayed(settings: SettingsStore, pieces: List<MediaItem>, chatTitle: String, now: Long = System.currentTimeMillis()) {
        if (pieces.isEmpty()) return
        val before = settings.pieceGapsNow()
        val after = remember(before, pieces, chatTitle, now)
        if (after != before) settings.setPieceGaps(after)
    }

    fun encode(gaps: List<Gap>): String = gaps.joinToString("\n") { g ->
        listOf(
            g.chatId.toString(), g.messageId.toString(), g.fileId.toString(), clean(g.fileName), clean(g.title),
            clean(g.chatTitle), clean(g.key), g.missing.joinToString(","), g.total.toString(), g.at.toString(),
        ).joinToString("\t")
    }

    fun decode(text: String?): List<Gap> = text.orEmpty().split('\n').mapNotNull { line ->
        val f = line.split('\t')
        if (f.size != 10) return@mapNotNull null
        Gap(
            chatId = f[0].toLongOrNull() ?: return@mapNotNull null,
            messageId = f[1].toLongOrNull() ?: return@mapNotNull null,
            fileId = f[2].toIntOrNull() ?: return@mapNotNull null,
            fileName = f[3],
            title = f[4],
            chatTitle = f[5],
            key = f[6],
            missing = f[7].split(',').mapNotNull { it.toIntOrNull() },
            total = f[8].toIntOrNull() ?: return@mapNotNull null,
            at = f[9].toLongOrNull() ?: return@mapNotNull null,
        )
    }

    private fun clean(text: String): String = text.replace('\t', ' ').replace('\n', ' ')
}
