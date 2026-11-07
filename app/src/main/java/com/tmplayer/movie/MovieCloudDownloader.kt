package com.tmplayer.movie

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.tmplayer.R
import com.tmplayer.data.Td
import com.tmplayer.data.valueOrNull
import dev.g000sha256.tdl.dto.FormattedText
import dev.g000sha256.tdl.dto.InputMessageText
import dev.g000sha256.tdl.dto.LinkPreviewOptions
import dev.g000sha256.tdl.dto.MessageSendOptions
import dev.g000sha256.tdl.dto.MessageTopicForum
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Xử lý yêu cầu kéo phim và tập phim từ KKPhim / VSMov về Telegram Cloud (MovieBox).
 *
 * Cấu hình đích đến:
 * - Group Chat ID: -1004495574377
 * - Forum Topic ID (MovieBox): 43264
 *
 * Tối ưu song song 2 bot để x2 tốc độ và chống trùng lặp:
 * - Bot 1: @Mw_movie_downloader_bot
 * - Bot 2: @Movie_bot_laptop_bot
 */
object MovieCloudDownloader {

    const val CLOUD_CHAT_ID = -1004495574377L
    const val CLOUD_TOPIC_ID = 43264
    const val BOT_TOKEN = "9166002043:AAEgSw9QEIzlMYniod09qV7PyuIMajRUjuQ"
    const val BOT_1 = "Mw_movie_downloader_bot"
    const val BOT_2 = "Movie_bot_laptop_bot"
    private const val NOTIF_CHANNEL_ID = "mwplayer_cloud_episode_updates"
    private var lastAutoCheckTimeMs = 0L

    /**
     * Chia khoảng tập ra cho 2 bot để chạy song song x2 tốc độ, tránh duplicate:
     * - Bot 1: @Mw_movie_downloader_bot
     * - Bot 2: @Movie_bot_laptop_bot
     * Trả về Pair(part1, part2OrNull).
     */
    fun splitRange(epRange: String, totalEpisodes: Int): Pair<String, String?> {
        val trimmed = epRange.trim().lowercase()

        // 1. Trường hợp "all" hoặc "full"
        if (trimmed == "all" || trimmed == "full") {
            if (totalEpisodes <= 1) return Pair(if (totalEpisodes == 1) "1" else "all", null)
            if (totalEpisodes == 2) return Pair("1", "2")
            val mid = totalEpisodes / 2
            val part1 = "1-$mid"
            val part2 = "${mid + 1}-$totalEpisodes"
            return Pair(part1, part2)
        }

        // 2. Trường hợp "remaining"
        if (trimmed == "remaining") {
            if (totalEpisodes > 1) {
                val mid = totalEpisodes / 2
                val part1 = "1-$mid"
                val part2 = "${mid + 1}-$totalEpisodes"
                return Pair(part1, part2)
            }
            return Pair("remaining", null)
        }

        // 3. Trường hợp dạng dải "start-end" ví dụ "1-20" hoặc "5-10"
        val rangeRegex = Regex("""^(\d+)\s*[-~]\s*(\d+)$""")
        val match = rangeRegex.find(trimmed)
        if (match != null) {
            val start = match.groupValues[1].toIntOrNull() ?: 1
            val end = match.groupValues[2].toIntOrNull() ?: start
            if (end <= start) return Pair("$start", null)
            val count = end - start + 1
            if (count == 2) return Pair("$start", "$end")
            val mid = start + (count / 2) - 1
            val part1 = if (start == mid) "$start" else "$start-$mid"
            val nextStart = mid + 1
            val part2 = if (nextStart == end) "$end" else "$nextStart-$end"
            return Pair(part1, part2)
        }

        // 4. Trường hợp dạng danh sách phân cách bằng dấu phẩy ví dụ "1,2,3,4,5,6"
        if (trimmed.contains(",")) {
            val list = trimmed.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            if (list.size <= 1) return Pair(trimmed, null)
            val mid = list.size / 2
            val part1 = list.take(mid).joinToString(",")
            val part2 = list.drop(mid).joinToString(",")
            return Pair(part1, part2)
        }

        // 5. Số tập đơn lẻ (ví dụ "12") hoặc format khác
        return Pair(trimmed, null)
    }

    /**
     * Kéo phim hoặc khoảng tập về Telegram Cloud (MovieBox).
     *
     * @param slug Slug của bộ phim (ví dụ: lan-huong-nhu-co)
     * @param movieTitle Tên phim
     * @param source KKPhim hoặc VSMov
     * @param epRange "all" (toàn bộ), "remaining" (các tập còn lại), "12" (tập 12), "1-10"
     * @param totalEpisodes Tổng số tập của server đang chọn (dùng để chia dải tập chính xác)
     */
    suspend fun pullToCloud(
        context: Context,
        slug: String,
        movieTitle: String,
        source: MovieSource,
        epRange: String = "all",
        totalEpisodes: Int = 0,
        movie: MovieItem? = null,
        detail: MovieDetail? = null,
        destination: CloudDestination? = null,
        directStreamUrl: String = "",
        directReferer: String = "",
        targetBot: String = "",
        pullFormat: String = "",
        silentToast: Boolean = false,
        onStatus: ((Boolean, String) -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        MovieStore.init(context)
        val selectedBot = targetBot.ifBlank { MovieStore.selectedBotFlow.value }.ifBlank { BOT_1 }
        val selectedFormat = pullFormat.ifBlank { MovieStore.pullFormatFlow.value }.ifBlank { "token" }
        val botTag = if (selectedBot.isNotBlank()) "@$selectedBot" else ""

        val dest = destination ?: MovieStore.cloudDestinationFlow.value
        val targetChatId = dest.chatId
        val targetTopicId = dest.topicId

        val srcName = when (source) {
            MovieSource.VSMov -> "vsmov"
            MovieSource.KKPhim -> "kkphim"
            else -> source.name.lowercase()
        }

        val resolvedAvStream = if (source.isAv) {
            if (directStreamUrl.isNotBlank()) {
                directStreamUrl
            } else {
                try {
                    val d = detail ?: com.tmplayer.av.AvRepository.getDetail(source, slug)
                    val srv = d?.servers?.firstOrNull()
                    val ep = srv?.episodes?.firstOrNull()
                    if (d != null && srv != null && ep != null) {
                        com.tmplayer.av.AvRepository.resolveAvStream(source, movieTitle, srv, ep).streamUrl
                    } else ""
                } catch (_: Exception) {
                    ""
                }
            }
        } else ""

        val finalReferer = if (directReferer.isNotBlank()) {
            directReferer
        } else {
            when (source) {
                MovieSource.MissAV -> "https://missav.ai/"
                MovieSource.XXVN -> "https://a.dfplayer.cc/"
                MovieSource.AVDB -> "https://upload18.org/"
                else -> "https://missav.ai/"
            }
        }

        val posterUrl = movie?.posterUrl?.ifBlank { movie.thumbUrl }
            ?: detail?.movie?.posterUrl?.ifBlank { detail.movie.thumbUrl }
            ?: ""

        val tagList = mutableListOf<String>()
        if (source.isAv) {
            tagList.add("#${source.badge}")
            val prefix = com.tmplayer.av.AvStore.extractCodePrefix(slug, movieTitle)
            if (prefix.isNotBlank()) {
                tagList.add("#${prefix.replace("-", "_")}")
            }
            if (!detail?.actor.isNullOrEmpty()) {
                detail!!.actor.take(2).forEach {
                    val clean = it.replace(" ", "_").replace(Regex("[^a-zA-Z0-9_]"), "")
                    if (clean.isNotBlank()) tagList.add("#$clean")
                }
            }
            tagList.add("#Vietsub")
        }

        val avStreamOrPage = resolvedAvStream.ifBlank {
            when (source) {
                MovieSource.MissAV -> "https://missav.ai/vi/$slug"
                MovieSource.XXVN -> "https://xxvnapi.com/phim/$slug"
                else -> "https://avdbapi.com/detail/$slug"
            }
        }

        val avJsonBody = if (source.isAv) {
            JSONObject().apply {
                put("stream_url", avStreamOrPage)
                put("referer", finalReferer)
                put("title", movieTitle)
                if (posterUrl.isNotBlank()) put("poster", posterUrl)
                put("dest", targetChatId)
                if (targetTopicId > 0) put("topic", targetTopicId)
                put("tags", org.json.JSONArray(tagList))
            }
        } else null

        val pullJsonCommand = if (avJsonBody != null) "/pull_json$botTag\n${avJsonBody.toString(2)}" else ""
        val pullTokenCommand = if (source.isAv) {
            buildString {
                append("/pull$botTag $avStreamOrPage")
                append(" title=\"${movieTitle.replace("\"", "")}\"")
                append(" ref=\"$finalReferer\"")
                if (posterUrl.isNotBlank()) append(" thumb=\"$posterUrl\"")
                append(" dest=\"$targetChatId\"")
                if (targetTopicId > 0) append(" topic=\"$targetTopicId\"")
                if (tagList.isNotEmpty()) append(" tag=\"${tagList.joinToString(" ")}\"")
            }
        } else ""

        val (part1, part2) = if (source.isAv) {
            Pair("full", null)
        } else {
            splitRange(epRange, totalEpisodes)
        }

        // CHỈ GỬI DUY NHẤT 1 LỆNH (TOKEN HOẶC JSON), KHÔNG GỬI CẢ 2 GÂY DUPLICATE
        val commands = mutableListOf<String>()
        if (source.isAv) {
            val singleCmd = if (selectedFormat == "json") pullJsonCommand else pullTokenCommand
            commands.add(singleCmd)
        } else {
            if (part2 == null) {
                commands.add("/dl@$selectedBot $slug $part1 seq $srcName")
            } else {
                commands.add("/dl@$BOT_1 $slug $part1 seq $srcName")
                commands.add("/dl@$BOT_2 $slug $part2 seq $srcName")
            }
        }

        var sentViaTdlib = false
        var sentViaBotApi = false

        // 1. Gửi lệnh qua TDLib tới đích đến đã chọn (nếu người dùng đã đăng nhập và client sẵn sàng)
        try {
            val td = Td.client
            td.getChat(targetChatId)
            for (cmd in commands) {
                val content = InputMessageText(
                    FormattedText(cmd, emptyArray()),
                    LinkPreviewOptions(true, "", false, false, false),
                    false
                )
                val options = MessageSendOptions(null, false, false, false, false, 0L, false, null, 0L, 0, false)
                val topicForum = if (targetTopicId > 0) MessageTopicForum(targetTopicId) else null
                var res = td.sendMessage(targetChatId, topicForum, null, options, null, content)
                if (res.valueOrNull == null && topicForum != null) {
                    res = td.sendMessage(targetChatId, null, null, options, null, content)
                }
                if (res.valueOrNull != null) {
                    sentViaTdlib = true
                }
            }
        } catch (_: Exception) {
            // TDLib client chưa sẵn sàng hoặc không trong phiên đăng nhập
        }

        // 2. Gửi thông báo thực thi lệnh tới Telegram Bot API
        try {
            val rangeLabel = when {
                source.isAv -> "Full HD"
                epRange == "all" -> "Toàn bộ phim ($totalEpisodes tập)"
                epRange == "remaining" -> "Các tập còn lại"
                else -> "Tập $epRange"
            }
            val botExecLines = if (source.isAv) {
                val singleCmd = commands.firstOrNull().orEmpty()
                buildString {
                    append("🤖 <b>Bot kéo:</b> <code>@$selectedBot</code>\n")
                    append("⚡ <b>Lệnh thực thi (${if (selectedFormat == "json") "JSON Block" else "Token"}):</b>\n")
                    if (selectedFormat == "json") {
                        append("<pre><code class=\"language-json\">$singleCmd</code></pre>\n")
                    } else {
                        append("<code>$singleCmd</code>\n")
                    }
                    if (finalReferer.isNotBlank()) {
                        append("🌐 <b>Referer:</b> <code>$finalReferer</code>\n")
                    }
                }
            } else {
                buildString {
                    append("🤖 <b>Bot 1:</b> <code>${commands[0]}</code>\n")
                    if (commands.size > 1) {
                        append("🤖 <b>Bot 2:</b> <code>${commands[1]}</code>\n")
                    }
                }
            }
            val text = "🎬 <b>[MWPlayer] Yêu cầu kéo phim về Tele Cloud</b>\n" +
                "📌 <b>Phim:</b> $movieTitle (<code>$slug</code>)\n" +
                "📑 <b>Khoảng tập:</b> $rangeLabel\n" +
                "📂 <b>Đích đến:</b> ${dest.displayLabel}\n" +
                "🌐 <b>Nguồn:</b> ${source.displayName}\n" +
                botExecLines

            fun sendBotMsg(chatId: Long, topicId: Int): Boolean {
                return try {
                    val url = URL("https://api.telegram.org/bot$BOT_TOKEN/sendMessage")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    conn.doOutput = true
                    conn.connectTimeout = 8_000
                    conn.readTimeout = 8_000

                    val postData = buildString {
                        append("chat_id=$chatId")
                        if (topicId > 0) append("&message_thread_id=$topicId")
                        append("&parse_mode=HTML")
                        append("&text=").append(URLEncoder.encode(text, "UTF-8"))
                    }

                    conn.outputStream.use { it.write(postData.toByteArray(Charsets.UTF_8)) }
                    val code = conn.responseCode
                    conn.disconnect()
                    code in 200..299
                } catch (_: Exception) {
                    false
                }
            }

            if (sendBotMsg(targetChatId, targetTopicId)) {
                sentViaBotApi = true
            } else if (targetChatId != CLOUD_CHAT_ID) {
                if (sendBotMsg(CLOUD_CHAT_ID, CLOUD_TOPIC_ID)) {
                    sentViaBotApi = true
                }
            }
        } catch (_: Exception) {
            // Bỏ qua lỗi mạng bot API
        }

        // 3. Kích hoạt MovieBot API cục bộ nếu cùng mạng LAN
        try {
            val apiUrl = URL("http://127.0.0.1:8088/api/external/download")
            val conn = apiUrl.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            conn.doOutput = true
            conn.connectTimeout = 1_500
            conn.readTimeout = 2_000

            val jsonBody = if (source.isAv && avJsonBody != null) {
                avJsonBody
            } else {
                JSONObject().apply {
                    put("slug", slug)
                    put("range", epRange)
                    put("source", srcName)
                    put("chat_id", targetChatId)
                    put("topic_id", targetTopicId)
                    if (resolvedAvStream.isNotBlank()) {
                        put("stream_url", resolvedAvStream)
                    }
                }
            }
            conn.outputStream.use { it.write(jsonBody.toString().toByteArray(Charsets.UTF_8)) }
            conn.responseCode
            conn.disconnect()
        } catch (_: Exception) {
            // Bỏ qua nếu MovieBot local API không mở port
        }

        val success = sentViaTdlib || sentViaBotApi
        if (success) {
            val itemToTrack = movie ?: MovieItem(
                name = movieTitle,
                slug = slug,
                source = source,
                posterUrl = posterUrl,
                thumbUrl = posterUrl
            )
            MovieStore.markCloudSynced(
                context = context,
                movie = itemToTrack,
                epRange = if (source.isAv) "full" else epRange,
                totalEpisodes = if (source.isAv) 1 else totalEpisodes
            )
        }
        val rangeLabel = when {
            source.isAv -> "bản Full HD"
            epRange == "all" -> "toàn bộ phim"
            epRange == "remaining" -> "các tập còn lại"
            else -> "tập $epRange"
        }
        val msg = if (success) {
            if (commands.size > 1) {
                "Đã kích hoạt 2 bot song song (Bot 1: $part1, Bot 2: $part2) kéo '$movieTitle' về ${dest.displayLabel}!"
            } else if (source.isAv) {
                "Đã gửi lệnh kéo bản Full HD ($selectedFormat) tới @$selectedBot về ${dest.displayLabel}!"
            } else {
                "Đã gửi yêu cầu kéo $rangeLabel phim '$movieTitle' tới @$selectedBot về ${dest.displayLabel}!"
            }
        } else {
            "Không thể kết nối Telegram. Vui lòng kiểm tra lại kết nối mạng!"
        }

        withContext(Dispatchers.Main) {
            if (!silentToast) {
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
            onStatus?.invoke(success, msg)
        }
    }

    /**
     * Kiểm tra các phim đã đánh dấu kéo về Tele Cloud xem có tập mới không.
     * Nếu có tập mới:
     * - Gửi thông báo hệ thống (Android Notification) và thông báo lên Telegram MovieBox
     * - Nếu phim bật `autoPullNewEpisodes = true`, tự động kích hoạt lệnh kéo các tập mới về Tele Cloud
     */
    suspend fun checkTrackedMoviesForNewEpisodes(
        context: Context,
        forceCheck: Boolean = false,
        onComplete: ((checkedCount: Int, updatedMoviesCount: Int) -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        MovieStore.init(context)
        val now = System.currentTimeMillis()
        if (!forceCheck && now - lastAutoCheckTimeMs < 15 * 60_000L) {
            return@withContext
        }
        lastAutoCheckTimeMs = now

        val trackedList = MovieStore.cloudSyncedFlow.value
        if (trackedList.isEmpty()) {
            withContext(Dispatchers.Main) {
                onComplete?.invoke(0, 0)
            }
            return@withContext
        }

        var updatedMoviesCount = 0
        for (tracked in trackedList) {
            try {
                val src = MovieSource.fromString(tracked.source)
                val detail = MovieRepository.getMovieDetail(src, tracked.slug) ?: continue
                val latestEpCount = detail.servers.maxOfOrNull { it.episodes.size } ?: 0
                if (latestEpCount <= 0) continue

                if (latestEpCount > tracked.pulledUpToEpisode && tracked.pulledUpToEpisode > 0) {
                    val newCount = latestEpCount - tracked.pulledUpToEpisode
                    val newRange = if (newCount == 1) "$latestEpCount" else "${tracked.pulledUpToEpisode + 1}-$latestEpCount"
                    updatedMoviesCount++

                    if (tracked.autoPullNewEpisodes) {
                        // Tự động kéo các tập mới về Tele Cloud
                        pullToCloud(
                            context = context,
                            slug = tracked.slug,
                            movieTitle = tracked.name,
                            source = src,
                            epRange = newRange,
                            totalEpisodes = latestEpCount,
                            movie = detail.movie,
                            silentToast = true
                        )
                        MovieStore.updateCloudLatestEpisodes(
                            context = context,
                            slug = tracked.slug,
                            latestEpisodes = latestEpCount,
                            newPulledUpTo = latestEpCount
                        )
                        postNewEpisodeNotification(
                            context = context,
                            movieTitle = tracked.name,
                            newRange = newRange,
                            autoPulled = true
                        )
                    } else {
                        // Chỉ cập nhật trạng thái có tập mới & gửi thông báo
                        MovieStore.updateCloudLatestEpisodes(
                            context = context,
                            slug = tracked.slug,
                            latestEpisodes = latestEpCount,
                            newPulledUpTo = null
                        )
                        postNewEpisodeNotification(
                            context = context,
                            movieTitle = tracked.name,
                            newRange = newRange,
                            autoPulled = false
                        )
                        sendTelegramNewEpisodeAlert(
                            movieTitle = tracked.name,
                            slug = tracked.slug,
                            source = src,
                            newRange = newRange,
                            latestEpCount = latestEpCount
                        )
                    }
                } else if (latestEpCount > tracked.latestKnownEpisodes) {
                    MovieStore.updateCloudLatestEpisodes(
                        context = context,
                        slug = tracked.slug,
                        latestEpisodes = latestEpCount
                    )
                }
            } catch (_: Exception) {
            }
        }

        withContext(Dispatchers.Main) {
            if (forceCheck) {
                val msg = if (updatedMoviesCount > 0) {
                    "🔔 Phát hiện $updatedMoviesCount phim có tập mới! Đã xử lý kéo/thông báo."
                } else {
                    "✅ Đã kiểm tra ${trackedList.size} phim đã kéo Cloud: Chưa có tập mới."
                }
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
            onComplete?.invoke(trackedList.size, updatedMoviesCount)
        }
    }

    private fun postNewEpisodeNotification(
        context: Context,
        movieTitle: String,
        newRange: String,
        autoPulled: Boolean
    ) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    NOTIF_CHANNEL_ID,
                    "Cập nhật tập mới (Tele Cloud)",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Thông báo khi phim đã kéo về Tele Cloud có tập mới"
                }
                nm.createNotificationChannel(channel)
            }
            val title = if (autoPulled) {
                "☁️ Đã tự động kéo tập mới: $movieTitle"
            } else {
                "🔔 Có tập mới: $movieTitle"
            }
            val body = if (autoPulled) {
                "Phát hiện Tập $newRange mới — đã tự động kích hoạt bot kéo về Tele Cloud (MovieBox)!"
            } else {
                "Phát hiện Tập $newRange mới — mở MWPlayer để kéo tập mới về Tele Cloud!"
            }
            val notif = NotificationCompat.Builder(context, NOTIF_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .build()
            nm.notify(movieTitle.hashCode(), notif)
        } catch (_: Exception) {}
    }

    private fun sendTelegramNewEpisodeAlert(
        movieTitle: String,
        slug: String,
        source: MovieSource,
        newRange: String,
        latestEpCount: Int
    ) {
        try {
            val text = "🔔 <b>[MWPlayer] Phát hiện tập mới của phim đã kéo Cloud!</b>\n" +
                "📌 <b>Phim:</b> $movieTitle (<code>$slug</code>)\n" +
                "🆕 <b>Tập mới:</b> Tập $newRange (Tổng hiện tại: $latestEpCount tập)\n" +
                "🌐 <b>Nguồn:</b> ${source.displayName}"
            val url = URL("https://api.telegram.org/bot$BOT_TOKEN/sendMessage")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            conn.doOutput = true
            conn.connectTimeout = 8_000
            conn.readTimeout = 8_000
            val postData = "chat_id=$CLOUD_CHAT_ID" +
                "&message_thread_id=$CLOUD_TOPIC_ID" +
                "&parse_mode=HTML" +
                "&text=" + URLEncoder.encode(text, "UTF-8")
            conn.outputStream.use { it.write(postData.toByteArray(Charsets.UTF_8)) }
            conn.responseCode
            conn.disconnect()
        } catch (_: Exception) {}
    }
}
