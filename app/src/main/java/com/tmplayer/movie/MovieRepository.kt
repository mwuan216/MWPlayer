package com.tmplayer.movie

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.collection.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.security.MessageDigest

object MovieRepository {

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private suspend fun fetchJson(url: String, referer: String? = null): String? = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val uri = URI(url).toURL()
            conn = (uri.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 12_000
                readTimeout = 15_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json, text/plain, */*")
                if (referer != null) {
                    setRequestProperty("Referer", referer)
                }
            }
            val code = conn.responseCode
            if (code in 200..299) {
                val input = conn.inputStream
                readString(input)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun readString(input: InputStream): String {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            out.write(buffer, 0, read)
        }
        return out.toString(Charsets.UTF_8.name())
    }

    // =========================================================================
    // 1. LATEST MOVIES
    // =========================================================================

    suspend fun getLatestMovies(source: MovieSource, page: Int = 1): List<MovieItem> {
        val url = when (source) {
            MovieSource.KKPhim -> "https://phimapi.com/danh-sach/phim-moi-cap-nhat?page=$page"
            MovieSource.VSMov -> "https://vsmov.com/api/danh-sach/phim-moi-cap-nhat?page=$page"
        }
        val referer = if (source == MovieSource.VSMov) "https://vsmov.com/" else "https://phimapi.com/"
        val jsonStr = fetchJson(url, referer) ?: return emptyList()
        return parseMovieList(jsonStr, source)
    }

    // =========================================================================
    // 2. CATEGORY / SHELF MOVIES
    // =========================================================================

    suspend fun getCategoryMovies(source: MovieSource, categorySlug: String, page: Int = 1): List<MovieItem> {
        val url = when (source) {
            MovieSource.KKPhim -> "https://phimapi.com/v1/api/danh-sach/$categorySlug?page=$page&limit=18"
            MovieSource.VSMov -> {
                // VSMov routes genres/anime under /the-loai/ instead of /danh-sach/
                val path = when (categorySlug) {
                    "hoat-hinh", "co-trang", "hanh-dong", "tinh-cam", "hai-huoc", "tam-ly",
                    "kinh-di", "khoa-hoc-vien-tuong", "vo-thuat", "phieu-luu", "hinh-su" -> "the-loai/$categorySlug"
                    "phim-moi-cap-nhat" -> "danh-sach/phim-moi-cap-nhat"
                    else -> "danh-sach/$categorySlug"
                }
                "https://vsmov.com/api/$path?page=$page"
            }
        }
        val referer = if (source == MovieSource.VSMov) "https://vsmov.com/" else "https://phimapi.com/"
        val jsonStr = fetchJson(url, referer) ?: return emptyList()
        return parseMovieList(jsonStr, source)
    }

    suspend fun getGenreMovies(source: MovieSource, genreSlug: String, page: Int = 1): List<MovieItem> {
        val url = when (source) {
            MovieSource.KKPhim -> "https://phimapi.com/v1/api/the-loai/$genreSlug?page=$page&limit=18"
            MovieSource.VSMov -> "https://vsmov.com/api/the-loai/$genreSlug?page=$page"
        }
        val referer = if (source == MovieSource.VSMov) "https://vsmov.com/" else "https://phimapi.com/"
        val jsonStr = fetchJson(url, referer) ?: return emptyList()
        return parseMovieList(jsonStr, source)
    }

    suspend fun getCountryMovies(source: MovieSource, countrySlug: String, page: Int = 1): List<MovieItem> {
        val url = when (source) {
            MovieSource.KKPhim -> "https://phimapi.com/v1/api/quoc-gia/$countrySlug?page=$page&limit=18"
            MovieSource.VSMov -> "https://vsmov.com/api/quoc-gia/$countrySlug?page=$page"
        }
        val referer = if (source == MovieSource.VSMov) "https://vsmov.com/" else "https://phimapi.com/"
        val jsonStr = fetchJson(url, referer) ?: return emptyList()
        return parseMovieList(jsonStr, source)
    }

    // =========================================================================
    // 3. ADVANCED FILTER (CAT, GENRE, COUNTRY, YEAR, KEYWORD)
    // =========================================================================

    suspend fun filterMovies(
        source: MovieSource,
        category: String = "",
        genre: String = "",
        country: String = "",
        year: String = "",
        keyword: String = "",
        page: Int = 1
    ): Pair<List<MovieItem>, Int> {
        val referer = if (source == MovieSource.VSMov) "https://vsmov.com/" else "https://phimapi.com/"
        val baseUrl = if (source == MovieSource.VSMov) "https://vsmov.com/api" else "https://phimapi.com/v1/api"

        val url = buildString {
            if (keyword.isNotBlank()) {
                val encKw = URLEncoder.encode(keyword, "UTF-8")
                append("$baseUrl/tim-kiem?keyword=$encKw&page=$page")
                if (source == MovieSource.KKPhim) append("&limit=18")
            } else if (category.isNotBlank()) {
                if (source == MovieSource.VSMov && category in listOf("hoat-hinh", "co-trang")) {
                    append("$baseUrl/the-loai/$category?page=$page")
                } else {
                    append("$baseUrl/danh-sach/$category?page=$page")
                    if (source == MovieSource.KKPhim) append("&limit=18")
                }
            } else if (genre.isNotBlank()) {
                append("$baseUrl/the-loai/$genre?page=$page")
                if (source == MovieSource.KKPhim) append("&limit=18")
            } else if (country.isNotBlank()) {
                append("$baseUrl/quoc-gia/$country?page=$page")
                if (source == MovieSource.KKPhim) append("&limit=18")
            } else if (year.isNotBlank()) {
                if (source == MovieSource.KKPhim) {
                    append("$baseUrl/nam-phat-hanh/$year?page=$page&limit=18")
                } else {
                    append("$baseUrl/nam/$year?page=$page")
                }
            } else {
                val latestBase = if (source == MovieSource.VSMov) "https://vsmov.com/api" else "https://phimapi.com"
                append("$latestBase/danh-sach/phim-moi-cap-nhat?page=$page")
            }
        }

        val jsonStr = fetchJson(url, referer) ?: return emptyList<MovieItem>() to 1
        val items = parseMovieList(jsonStr, source)
        val totalPages = parseTotalPages(jsonStr)
        return items to totalPages
    }

    // =========================================================================
    // 4. SEARCH
    // =========================================================================

    suspend fun searchMovies(source: MovieSource, keyword: String, page: Int = 1): List<MovieItem> {
        val enc = URLEncoder.encode(keyword.trim(), "UTF-8")
        val url = when (source) {
            MovieSource.KKPhim -> "https://phimapi.com/v1/api/tim-kiem?keyword=$enc&page=$page&limit=24"
            MovieSource.VSMov -> "https://vsmov.com/api/tim-kiem?keyword=$enc&page=$page"
        }
        val referer = if (source == MovieSource.VSMov) "https://vsmov.com/" else "https://phimapi.com/"
        val jsonStr = fetchJson(url, referer) ?: return emptyList()
        return parseMovieList(jsonStr, source)
    }

    // =========================================================================
    // 5. MOVIE DETAIL
    // =========================================================================

    suspend fun getMovieDetail(source: MovieSource, slug: String): MovieDetail? {
        val url = when (source) {
            MovieSource.KKPhim -> "https://phimapi.com/phim/$slug"
            MovieSource.VSMov -> "https://vsmov.com/api/phim/$slug"
        }
        val referer = if (source == MovieSource.VSMov) "https://vsmov.com/" else "https://phimapi.com/"
        val jsonStr = fetchJson(url, referer) ?: return null

        return try {
            val root = JSONObject(jsonStr)
            val mvObj = root.optJSONObject("movie") ?: return null

            val cdn = root.optJSONObject("data")?.optString("APP_DOMAIN_CDN_IMAGE", "https://phimimg.com")
                ?: "https://phimimg.com"
            val pathImage = root.optString("pathImage", "")

            fun fixImg(u: String?): String {
                if (u.isNullOrBlank()) return ""
                if (u.startsWith("http://") || u.startsWith("https://")) return u
                val prefix = if (pathImage.isNotBlank()) pathImage else cdn
                return "${prefix.trimEnd('/')}/${u.trimStart('/')}"
            }

            val thumb = fixImg(mvObj.optString("thumb_url"))
            val poster = fixImg(mvObj.optString("poster_url")).ifBlank { thumb }

            val tmdb = mvObj.optJSONObject("tmdb")
            val vote = tmdb?.optDouble("vote_average", 0.0) ?: 0.0
            val rating = if (vote > 0.0) String.format(java.util.Locale.US, "%.1f", vote) else ""

            val item = MovieItem(
                name = mvObj.optString("name", "Phim"),
                originName = mvObj.optString("origin_name", ""),
                slug = mvObj.optString("slug", slug),
                thumbUrl = thumb,
                posterUrl = poster,
                year = mvObj.optString("year", ""),
                quality = mvObj.optString("quality", "HD"),
                lang = mvObj.optString("lang", "Vietsub"),
                type = mvObj.optString("type", ""),
                episodeCurrent = mvObj.optString("episode_current", ""),
                rating = rating,
                source = source
            )

            val directors = parseStringList(mvObj.optJSONArray("director"))
            val actors = parseStringList(mvObj.optJSONArray("actor"))
            val cats = parseNameList(mvObj.optJSONArray("category"))
            val countries = parseNameList(mvObj.optJSONArray("country"))

            val servers = mutableListOf<MovieServer>()
            val epArr = root.optJSONArray("episodes")
            if (epArr != null) {
                for (sIdx in 0 until epArr.length()) {
                    val sObj = epArr.optJSONObject(sIdx) ?: continue
                    val sName = sObj.optString("server_name", "Server ${sIdx + 1}")
                    val dataArr = sObj.optJSONArray("server_data")
                    val eps = mutableListOf<MovieEpisode>()
                    if (dataArr != null) {
                        for (eIdx in 0 until dataArr.length()) {
                            val eObj = dataArr.optJSONObject(eIdx) ?: continue
                            eps.add(
                                MovieEpisode(
                                    name = eObj.optString("name", "Tập ${eIdx + 1}"),
                                    slug = eObj.optString("slug", "tap-${eIdx + 1}"),
                                    linkM3u8 = eObj.optString("link_m3u8", ""),
                                    linkEmbed = eObj.optString("link_embed", "")
                                )
                            )
                        }
                    }
                    if (eps.isNotEmpty()) {
                        servers.add(MovieServer(sName, eps))
                    }
                }
            }

            MovieDetail(
                movie = item,
                content = mvObj.optString("content", "").replace(Regex("<[^>]*>"), "").trim(),
                time = mvObj.optString("time", ""),
                episodeTotal = mvObj.optString("episode_total", ""),
                director = directors,
                actor = actors,
                categories = cats,
                countries = countries,
                servers = servers
            )
        } catch (e: Exception) {
            null
        }
    }

    // =========================================================================
    // 6. STREAM RESOLVER
    // =========================================================================

    private val subtitleCache = java.util.concurrent.ConcurrentHashMap<String, List<MovieSubtitleTrack>>()

    fun resolveStream(source: MovieSource, movieTitle: String, server: MovieServer, episode: MovieEpisode): MovieStream {
        val referer = if (source == MovieSource.VSMov) "https://vsmov.com/" else "https://phimapi.com/"
        var streamUrl = episode.linkM3u8.trim()

        if (streamUrl.isBlank() && episode.linkEmbed.isNotBlank()) {
            val embed = episode.linkEmbed.trim()
            if ("/video/" in embed) {
                val clean = embed.substringBefore('?').trimEnd('/')
                streamUrl = clean.replace("/video/", "/stream/") + "/master.m3u8"
            } else if ("url=" in embed) {
                val rawUrl = embed.substringAfter("url=").substringBefore('&')
                streamUrl = try {
                    java.net.URLDecoder.decode(rawUrl, "UTF-8")
                } catch (_: Exception) {
                    rawUrl
                }
            } else {
                streamUrl = embed
            }
        }

        return MovieStream(
            streamUrl = streamUrl,
            referer = referer,
            movieTitle = movieTitle,
            episodeName = episode.name,
            serverName = server.serverName,
            source = source
        )
    }

    suspend fun resolveStreamWithSubtitles(
        source: MovieSource,
        movieTitle: String,
        server: MovieServer,
        episode: MovieEpisode
    ): MovieStream = withContext(Dispatchers.IO) {
        val baseStream = resolveStream(source, movieTitle, server, episode)
        if (source != MovieSource.VSMov || episode.linkEmbed.isBlank()) {
            return@withContext baseStream
        }

        val embedUrl = episode.linkEmbed.trim()
        val cached = subtitleCache[embedUrl]
        if (cached != null) {
            val defaultSub = cached.firstOrNull { it.isDefault } ?: cached.firstOrNull()
            return@withContext baseStream.copy(
                subtitleUrl = defaultSub?.url ?: "",
                subtitles = cached
            )
        }

        val subs = parseSubtitlesFromEmbed(embedUrl)
        if (subs.isNotEmpty()) {
            subtitleCache[embedUrl] = subs
        }
        val defaultSub = subs.firstOrNull { it.isDefault } ?: subs.firstOrNull()
        baseStream.copy(
            subtitleUrl = defaultSub?.url ?: "",
            subtitles = subs
        )
    }

    private suspend fun parseSubtitlesFromEmbed(embedUrl: String): List<MovieSubtitleTrack> {
        return try {
            val html = fetchJson(embedUrl, "https://vsmov.com/") ?: return emptyList()
            val match = Regex("""subtitles\s*:\s*(\[[^\]]*\])""").find(html) ?: return emptyList()
            val jsonArray = JSONArray(match.groupValues[1])
            val host = try {
                val uri = URI(embedUrl)
                "${uri.scheme}://${uri.host}"
            } catch (e: Exception) {
                "https://v4.streamvsmov.com"
            }

            val list = mutableListOf<MovieSubtitleTrack>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.optJSONObject(i) ?: continue
                val rawUrl = obj.optString("url", "")
                if (rawUrl.isBlank()) continue
                val fullUrl = if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
                    rawUrl
                } else {
                    host.trimEnd('/') + "/" + rawUrl.trimStart('/')
                }
                val code = obj.optString("code", "").lowercase()
                val name = obj.optString("name", "")
                val isVie = code.startsWith("vi") || "việt" in name.lowercase() || "vie" in name.lowercase()
                val label = when {
                    isVie -> "Tiếng Việt"
                    code.startsWith("en") || "eng" in name.lowercase() -> "Tiếng Anh"
                    name.isNotBlank() -> name
                    else -> "Phụ đề ${i + 1}"
                }
                list.add(
                    MovieSubtitleTrack(
                        label = label,
                        url = fullUrl,
                        language = if (isVie) "vi" else if (code.startsWith("en")) "en" else code.ifBlank { "und" },
                        isDefault = isVie
                    )
                )
            }
            list.sortedByDescending { it.isDefault }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // =========================================================================
    // JSON PARSING HELPERS
    // =========================================================================

    private fun parseMovieList(jsonStr: String, source: MovieSource): List<MovieItem> {
        return try {
            val root = JSONObject(jsonStr)
            val dObj = root.optJSONObject("data")
            val itemsArr = dObj?.optJSONArray("items") ?: root.optJSONArray("items") ?: return emptyList()

            val cdn = dObj?.optString("APP_DOMAIN_CDN_IMAGE", "https://phimimg.com") ?: "https://phimimg.com"
            val pathImage = root.optString("pathImage", "")

            fun fixImg(u: String?): String {
                if (u.isNullOrBlank()) return ""
                if (u.startsWith("http://") || u.startsWith("https://")) return u
                val prefix = if (pathImage.isNotBlank()) pathImage else cdn
                return "${prefix.trimEnd('/')}/${u.trimStart('/')}"
            }

            val list = mutableListOf<MovieItem>()
            for (i in 0 until itemsArr.length()) {
                val obj = itemsArr.optJSONObject(i) ?: continue
                val thumb = fixImg(obj.optString("thumb_url").ifBlank { obj.optString("poster_url") })
                val poster = fixImg(obj.optString("poster_url")).ifBlank { thumb }

                val tmdb = obj.optJSONObject("tmdb")
                val vote = tmdb?.optDouble("vote_average", 0.0) ?: 0.0
                val rating = if (vote > 0.0) String.format(java.util.Locale.US, "%.1f", vote) else ""

                list.add(
                    MovieItem(
                        name = obj.optString("name", "Phim"),
                        originName = obj.optString("origin_name", ""),
                        slug = obj.optString("slug", ""),
                        thumbUrl = thumb,
                        posterUrl = poster,
                        year = obj.optString("year", ""),
                        quality = obj.optString("quality").ifBlank { "HD" },
                        lang = obj.optString("lang").ifBlank { "Vietsub" },
                        type = obj.optString("type", ""),
                        episodeCurrent = obj.optString("episode_current", ""),
                        rating = rating,
                        source = source
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseTotalPages(jsonStr: String): Int {
        return try {
            val root = JSONObject(jsonStr)
            val dObj = root.optJSONObject("data")
            val params = dObj?.optJSONObject("params") ?: root.optJSONObject("params")
            val pagination = params?.optJSONObject("pagination") ?: root.optJSONObject("pagination")
            val pages = pagination?.optInt("totalPages", 0) ?: 0
            if (pages > 0) return pages
            val totalItems = pagination?.optInt("totalItems", 0) ?: 0
            val perPage = pagination?.optInt("totalItemsPerPage", 24) ?: 24
            if (totalItems > 0 && perPage > 0) {
                return (totalItems + perPage - 1) / perPage
            }
            1
        } catch (e: Exception) {
            1
        }
    }

    private fun parseStringList(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        val list = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val s = arr.optString(i, "")
            if (s.isNotBlank()) list.add(s)
        }
        return list
    }

    private fun parseNameList(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        val list = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i)
            if (obj != null) {
                val n = obj.optString("name", "")
                if (n.isNotBlank()) list.add(n)
            } else {
                val s = arr.optString(i, "")
                if (s.isNotBlank()) list.add(s)
            }
        }
        return list
    }
}

/** Trình tải và cache ảnh poster nhẹ, siêu mượt cho Movie screen */
object MovieImageLoader {

    private val memoryCache = object : LruCache<String, ImageBitmap>(40 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int {
            // Estimating size in bytes (width * height * 4)
            return value.width * value.height * 4
        }
    }

    fun peek(url: String): ImageBitmap? {
        if (url.isBlank()) return null
        return memoryCache.get(url)
    }

    suspend fun load(context: Context, url: String, targetWidth: Int = 360): ImageBitmap? = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext null

        memoryCache.get(url)?.let { return@withContext it }

        val cacheDir = File(context.cacheDir, "movie_posters").apply { if (!exists()) mkdirs() }
        val hash = md5(url)
        val file = File(cacheDir, "$hash.img")

        if (!file.exists() || file.length() == 0L) {
            var conn: HttpURLConnection? = null
            try {
                val u = URI(url).toURL()
                conn = (u.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8_000
                    readTimeout = 10_000
                    setRequestProperty("User-Agent", "Mozilla/5.0")
                }
                if (conn.responseCode in 200..299) {
                    file.outputStream().use { fos ->
                        conn.inputStream.use { fis -> fis.copyTo(fos) }
                    }
                }
            } catch (e: Exception) {
                file.delete()
                return@withContext null
            } finally {
                conn?.disconnect()
            }
        }

        if (file.exists() && file.length() > 0L) {
            try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, bounds)

                var sample = 1
                while (bounds.outWidth / sample > targetWidth * 2) sample *= 2

                val opts = BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                val bitmap = BitmapFactory.decodeFile(file.absolutePath, opts)
                if (bitmap != null) {
                    val img = bitmap.asImageBitmap()
                    memoryCache.put(url, img)
                    return@withContext img
                }
            } catch (e: Exception) {
                // Ignore decode failure
            }
        }
        null
    }

    private fun md5(s: String): String {
        return try {
            val md = MessageDigest.getInstance("MD5")
            val digest = md.digest(s.toByteArray())
            digest.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            s.hashCode().toString()
        }
    }
}
