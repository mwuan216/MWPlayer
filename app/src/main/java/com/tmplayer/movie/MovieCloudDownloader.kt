package com.tmplayer.movie

import android.content.Context
import android.widget.Toast
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
 */
object MovieCloudDownloader {

    const val CLOUD_CHAT_ID = -1004495574377L
    const val CLOUD_TOPIC_ID = 43264
    const val BOT_TOKEN = "9166002043:AAEgSw9QEIzlMYniod09qV7PyuIMajRUjuQ"

    /**
     * Kéo phim hoặc khoảng tập về Telegram Cloud (MovieBox).
     *
     * @param slug Slug của bộ phim (ví dụ: lan-huong-nhu-co)
     * @param movieTitle Tên phim
     * @param source KKPhim hoặc VSMov
     * @param epRange "all" (toàn bộ), "remaining" (các tập còn lại), "12" (tập 12), "1-10"
     */
    suspend fun pullToCloud(
        context: Context,
        slug: String,
        movieTitle: String,
        source: MovieSource,
        epRange: String = "all",
        onStatus: ((Boolean, String) -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        val srcName = if (source == MovieSource.VSMov) "vsmov" else "kkphim"
        val command = "/dl $slug $epRange seq $srcName"
        var sentViaTdlib = false
        var sentViaBotApi = false

        // 1. Gửi lệnh qua TDLib (nếu người dùng đã đăng nhập và client sẵn sàng)
        try {
            val td = Td.client
            val content = InputMessageText(
                FormattedText(command, emptyArray()),
                LinkPreviewOptions(true, "", false, false, false),
                false
            )
            val options = MessageSendOptions(null, false, false, false, false, 0L, false, null, 0L, 0, false)
            val res = td.sendMessage(CLOUD_CHAT_ID, MessageTopicForum(CLOUD_TOPIC_ID), null, options, null, content)
            if (res.valueOrNull != null) {
                sentViaTdlib = true
            }
        } catch (_: Exception) {
            // TDLib client chưa sẵn sàng hoặc không trong phiên đăng nhập
        }

        // 2. Gửi thông báo thực thi lệnh tới Telegram Bot API
        try {
            val rangeLabel = when (epRange) {
                "all" -> "Toàn bộ phim"
                "remaining" -> "Các tập còn lại"
                else -> "Tập $epRange"
            }
            val text = "🎬 <b>[MWPlayer] Yêu cầu kéo phim về Tele Cloud</b>\n" +
                "📌 <b>Phim:</b> $movieTitle (<code>$slug</code>)\n" +
                "📑 <b>Khoảng tập:</b> $rangeLabel\n" +
                "🌐 <b>Nguồn:</b> ${source.displayName}\n" +
                "👉 <b>Lệnh thực thi:</b> <code>$command</code>"

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
        val rangeLabel = when (epRange) {
            "all" -> "toàn bộ phim"
            "remaining" -> "các tập còn lại"
            else -> "tập $epRange"
        }
        val msg = if (success) {
            "Đã gửi yêu cầu kéo $rangeLabel phim '$movieTitle' về MovieBox (Tele Cloud)!"
        } else {
            "Không thể kết nối Telegram. Vui lòng kiểm tra lại kết nối mạng!"
        }

        withContext(Dispatchers.Main) {
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            onStatus?.invoke(success, msg)
        }
    }
}
