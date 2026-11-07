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
        silentToast: Boolean = false,
        onStatus: ((Boolean, String) -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        val srcName = if (source == MovieSource.VSMov) "vsmov" else "kkphim"
        val (part1, part2) = splitRange(epRange, totalEpisodes)

        val commands = mutableListOf<String>()
        commands.add("/dl@$BOT_1 $slug $part1 seq $srcName")
        if (part2 != null) {
            commands.add("/dl@$BOT_2 $slug $part2 seq $srcName")
        }

        var sentViaTdlib = false
        var sentViaBotApi = false

        // 1. Gửi lệnh qua TDLib (nếu người dùng đã đăng nhập và client sẵn sàng)
        try {
            val td = Td.client
            td.getChat(CLOUD_CHAT_ID)
            for (cmd in commands) {
                val content = InputMessageText(
                    FormattedText(cmd, emptyArray()),
                    LinkPreviewOptions(true, "", false, false, false),
                    false
                )
                val options = MessageSendOptions(null, false, false, false, false, 0L, false, null, 0L, 0, false)
                var res = td.sendMessage(CLOUD_CHAT_ID, MessageTopicForum(CLOUD_TOPIC_ID), null, options, null, content)
                if (res.valueOrNull == null) {
                    res = td.sendMessage(CLOUD_CHAT_ID, null, null, options, null, content)
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
            val rangeLabel = when (epRange) {
                "all" -> "Toàn bộ phim ($totalEpisodes tập)"
                "remaining" -> "Các tập còn lại"
                else -> "Tập $epRange"
            }
            val botExecLines = buildString {
                append("🤖 <b>Bot 1:</b> <code>${commands[0]}</code>\n")
                if (commands.size > 1) {
                    append("🤖 <b>Bot 2:</b> <code>${commands[1]}</code>\n")
                }
            }
            val text = "🎬 <b>[MWPlayer] Yêu cầu kéo phim về Tele Cloud (Dual Bot)</b>\n" +
                "📌 <b>Phim:</b> $movieTitle (<code>$slug</code>)\n" +
                "📑 <b>Khoảng tập:</b> $rangeLabel\n" +
                "🌐 <b>Nguồn:</b> ${source.displayName}\n" +
                botExecLines

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
            val code = conn.responseCode
            if (code in 200..299) {
                sentViaBotApi = true
            }
            conn.disconnect()
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

            val jsonBody = JSONObject().apply {
                put("slug", slug)
                put("range", epRange)
                put("source", srcName)
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
                source = source
            )
            MovieStore.markCloudSynced(
                context = context,
                movie = itemToTrack,
                epRange = epRange,
                totalEpisodes = totalEpisodes
            )
        }
        val rangeLabel = when (epRange) {
            "all" -> "toàn bộ phim"
            "remaining" -> "các tập còn lại"
            else -> "tập $epRange"
        }
        val msg = if (success) {
            if (commands.size > 1) {
                "Đã kích hoạt 2 bot song song (Bot 1: $part1, Bot 2: $part2) kéo '$movieTitle' về Cloud!"
            } else {
                "Đã gửi yêu cầu kéo $rangeLabel phim '$movieTitle' về MovieBox (Tele Cloud)!"
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
