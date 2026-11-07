package com.tmplayer.av

import com.tmplayer.movie.FilterOption
import com.tmplayer.movie.MovieDetail
import com.tmplayer.movie.MovieEpisode
import com.tmplayer.movie.MovieItem
import com.tmplayer.movie.MovieServer
import com.tmplayer.movie.MovieSource
import com.tmplayer.movie.MovieStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

object AvFilterData {
    fun categoriesFor(source: MovieSource): List<FilterOption> = when (source) {
        MovieSource.XXVN -> listOf(
            FilterOption("", "Tất cả"),
            FilterOption("history", "Xem gần đây"),
            FilterOption("watchlist", "Theo dõi"),
            FilterOption("vietsub", "JAV Vietsub"),
            FilterOption("jav-hd", "JAV HD"),
            FilterOption("khong-che", "AV Không che"),
            FilterOption("nhat-ban", "Nhật Bản"),
            FilterOption("viet-nam-clip", "Việt Nam"),
            FilterOption("han-quoc-18-", "Hàn Quốc 18+"),
            FilterOption("trung-quoc", "Trung Quốc"),
            FilterOption("chau-au", "Âu Mỹ"),
            FilterOption("hoc-sinh", "Học sinh"),
            FilterOption("vung-trom", "Vụng trộm"),
            FilterOption("hentai", "Hentai")
        )
        MovieSource.MissAV -> listOf(
            FilterOption("", "Tất cả"),
            FilterOption("history", "Xem gần đây"),
            FilterOption("watchlist", "Theo dõi"),
            FilterOption("new", "Mới phát hành"),
            FilterOption("uncensored-leak", "Uncensored Leak"),
            FilterOption("uncensored", "Không che (Uncensored)"),
            FilterOption("censored", "Có che (Censored)"),
            FilterOption("siro", "Amateur / SIRO / FC2"),
            FilterOption("chinese-subtitle", "Subtitled"),
            FilterOption("chinese-av", "Chinese AV"),
            FilterOption("hentai", "Hentai 18+")
        )
        else -> listOf(
            FilterOption("", "Tất cả"),
            FilterOption("history", "Xem gần đây"),
            FilterOption("watchlist", "Theo dõi"),
            FilterOption("2", "Không che (Uncensored)"),
            FilterOption("3", "Rò rỉ (Uncensored Leak)"),
            FilterOption("1", "Có che (Censored)"),
            FilterOption("4", "Nghiệp dư (Amateur/FC2)"),
            FilterOption("7", "Engsub / Phụ đề"),
            FilterOption("5", "Trung Quốc (Chinese AV)"),
            FilterOption("6", "Hentai 18+")
        )
    }
}

object AvRepository {

    const val CHROME_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private val detailCache = ConcurrentHashMap<String, MovieDetail>()
    private val resolvedStreamCache = ConcurrentHashMap<String, Pair<Long, MovieStream>>()

    private suspend fun fetchUrl(
        url: String,
        referer: String? = null,
        timeoutMs: Int = 10_000
    ): String? = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val uri = URI(url).toURL()
            conn = (uri.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", CHROME_UA)
                setRequestProperty("Accept", "application/json, text/html, text/plain, */*")
                setRequestProperty("Accept-Language", "vi-VN,vi;q=0.9,en-US;q=0.8,en;q=0.7")
                if (referer != null) {
                    setRequestProperty("Referer", referer)
                }
            }
            val code = conn.responseCode
            if (code in 200..299) {
                readString(conn.inputStream)
            } else {
                null
            }
        } catch (_: Exception) {
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
    // 1. LATEST & CATEGORY FETCHING
    // =========================================================================

    suspend fun getLatest(source: MovieSource, page: Int = 1): List<MovieItem> {
        return filterAv(source = source, category = "", keyword = "", page = page).first
    }

    suspend fun getCategory(source: MovieSource, category: String, page: Int = 1): List<MovieItem> {
        return filterAv(source = source, category = category, keyword = "", page = page).first
    }

    suspend fun filterAv(
        source: MovieSource,
        category: String = "",
        keyword: String = "",
        page: Int = 1
    ): Pair<List<MovieItem>, Int> {
        return when (source) {
            MovieSource.XXVN -> fetchXxvnList(category = category, keyword = keyword, page = page)
            MovieSource.MissAV -> fetchMissAvList(category = category, keyword = keyword, page = page)
            else -> fetchAvdbList(source = MovieSource.AVDB, typeId = category, keyword = keyword, page = page)
        }
    }

    // =========================================================================
    // 2. AVDB OPENAPI (https://avdbapi.com/api.php/provide/vod/)
    // =========================================================================

    private suspend fun fetchAvdbList(
        source: MovieSource,
        typeId: String = "",
        keyword: String = "",
        page: Int = 1
    ): Pair<List<MovieItem>, Int> {
        val url = buildString {
            append("https://avdbapi.com/api.php/provide/vod/?ac=detail&pg=$page")
            if (typeId.isNotBlank() && typeId.all { it.isDigit() }) {
                append("&t=$typeId")
            }
            if (keyword.isNotBlank()) {
                val enc = URLEncoder.encode(keyword.trim(), "UTF-8")
                append("&wd=$enc")
            }
        }
        val raw = fetchUrl(url, referer = "https://avdbapi.com/") ?: return emptyList<MovieItem>() to 1
        return parseAvdbResponse(raw, source)
    }

    private fun parseAvdbResponse(jsonStr: String, source: MovieSource): Pair<List<MovieItem>, Int> {
        return try {
            val root = JSONObject(jsonStr)
            val totalPages = root.optInt("pagecount", 1).coerceAtLeast(1)
            val arr = root.optJSONArray("list") ?: return emptyList<MovieItem>() to 1
            val items = mutableListOf<MovieItem>()

            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val detail = parseAvdbDetailObject(obj, source) ?: continue
                detailCache["${source.name}:${detail.movie.slug}"] = detail
                items.add(detail.movie)
            }
            items to totalPages
        } catch (_: Exception) {
            emptyList<MovieItem>() to 1
        }
    }

    private fun parseAvdbDetailObject(obj: JSONObject, source: MovieSource): MovieDetail? {
        val slug = obj.optString("slug").takeIf { it.isNotBlank() } ?: return null
        val rawName = decodeHtmlEntities(obj.optString("name", slug))
        val movieCode = obj.optString("movie_code", "").trim().ifBlank { slug.uppercase() }
        val poster = obj.optString("poster_url", "").ifBlank { obj.optString("thumb_url", "") }
        val thumb = obj.optString("thumb_url", "").ifBlank { poster }
        val typeName = obj.optString("type_name", "JAV")
        val time = obj.optString("time", "").trim()
        val year = obj.optString("year", "").trim()
        val quality = obj.optString("quality", "HD").ifBlank { "HD" }

        val displayTitle = if (rawName.startsWith(movieCode, ignoreCase = true)) {
            rawName
        } else {
            "[$movieCode] $rawName"
        }

        val actors = mutableListOf<String>()
        val actorArr = obj.optJSONArray("actor")
        if (actorArr != null) {
            for (i in 0 until actorArr.length()) {
                val a = decodeHtmlEntities(actorArr.optString(i, "")).trim()
                if (a.isNotBlank() && !a.equals("Updating", ignoreCase = true)) actors.add(a)
            }
        }

        val categories = mutableListOf<String>()
        if (typeName.isNotBlank()) categories.add(typeName)
        val catArr = obj.optJSONArray("category")
        if (catArr != null) {
            for (i in 0 until catArr.length()) {
                val c = decodeHtmlEntities(catArr.optString(i, "")).trim()
                if (c.isNotBlank() && c !in categories) categories.add(c)
            }
        }

        val countries = mutableListOf<String>()
        val countryArr = obj.optJSONArray("country")
        if (countryArr != null) {
            for (i in 0 until countryArr.length()) {
                val c = decodeHtmlEntities(countryArr.optString(i, "")).trim()
                if (c.isNotBlank()) countries.add(c)
            }
        }

        val servers = mutableListOf<MovieServer>()
        val epObj = obj.optJSONObject("episodes")
        if (epObj != null) {
            val sName = epObj.optString("server_name", "VIP #1")
            val sData = epObj.optJSONObject("server_data")
            val eps = mutableListOf<MovieEpisode>()
            if (sData != null) {
                val keys = sData.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val eItem = sData.optJSONObject(key) ?: continue
                    val linkEmbed = eItem.optString("link_embed", "")
                    val linkM3u8 = eItem.optString("link_m3u8", "")
                    if (linkEmbed.isNotBlank() || linkM3u8.isNotBlank()) {
                        eps.add(
                            MovieEpisode(
                                name = key.ifBlank { "Full" },
                                slug = eItem.optString("slug", "full"),
                                linkM3u8 = linkM3u8,
                                linkEmbed = linkEmbed
                            )
                        )
                    }
                }
            }
            if (eps.isNotEmpty()) {
                servers.add(MovieServer(sName, eps))
            }
        }

        val item = MovieItem(
            name = displayTitle,
            originName = actors.firstOrNull() ?: movieCode,
            slug = slug,
            thumbUrl = poster, // Trong AVDB poster_url là ảnh ngang 16:9 cực nét, thumb_url là ảnh dọc crop
            posterUrl = poster,
            year = year,
            quality = quality,
            lang = typeName,
            type = "single",
            episodeCurrent = time.ifBlank { movieCode },
            rating = "",
            source = source
        )

        val desc = decodeHtmlEntities(obj.optString("description", ""))
            .replace(Regex("<[^>]*>"), "")
            .trim()
            .ifBlank { displayTitle }

        return MovieDetail(
            movie = item,
            content = desc,
            time = time,
            episodeTotal = "Full",
            director = emptyList(),
            actor = actors,
            categories = categories,
            countries = countries.ifEmpty { listOf("Nhật Bản") },
            servers = servers
        )
    }

    // =========================================================================
    // 3. XXVN OPENAPI (https://xxvnapi.com/api/)
    // =========================================================================

    private suspend fun fetchXxvnList(
        category: String = "",
        keyword: String = "",
        page: Int = 1
    ): Pair<List<MovieItem>, Int> = coroutineScope {
        if (keyword.isNotBlank()) {
            val enc = URLEncoder.encode(keyword.trim(), "UTF-8")
            val searchUrl = "https://xxvnapi.com/tim-kiem?k=$enc&page=$page"
            val html = fetchUrl(searchUrl, referer = "https://xxvnapi.com/")
            if (!html.isNullOrBlank()) {
                val slugs = Regex("""https://xxvnapi\.com/phim/([a-zA-Z0-9_-]+)""")
                    .findAll(html)
                    .map { it.groupValues[1] }
                    .distinct()
                    .take(18)
                    .toList()
                if (slugs.isNotEmpty()) {
                    val details = slugs.map { slug ->
                        async { getDetail(MovieSource.XXVN, slug) }
                    }.awaitAll().filterNotNull()
                    if (details.isNotEmpty()) {
                        return@coroutineScope details.map { it.movie } to 1
                    }
                }
            }
            return@coroutineScope emptyList<MovieItem>() to 1
        }

        val url = if (category.isNotBlank()) {
            "https://xxvnapi.com/api/chuyen-muc/$category?page=$page"
        } else {
            "https://xxvnapi.com/api/phim-moi-cap-nhat?page=$page"
        }
        val raw = fetchUrl(url, referer = "https://xxvnapi.com/") ?: return@coroutineScope emptyList<MovieItem>() to 1
        try {
            val root = JSONObject(raw)
            val pageObj = root.optJSONObject("page")
            val lastPage = pageObj?.optInt("last_page", 1)?.coerceAtLeast(1) ?: 1
            val moviesArr = root.optJSONArray("movies") ?: return@coroutineScope emptyList<MovieItem>() to 1
            val items = mutableListOf<MovieItem>()

            for (i in 0 until moviesArr.length()) {
                val obj = moviesArr.optJSONObject(i) ?: continue
                val detail = parseXxvnDetailObject(obj) ?: continue
                detailCache["${MovieSource.XXVN.name}:${detail.movie.slug}"] = detail
                items.add(detail.movie)
            }
            items to lastPage
        } catch (_: Exception) {
            emptyList<MovieItem>() to 1
        }
    }

    private fun parseXxvnDetailObject(obj: JSONObject): MovieDetail? {
        val slug = obj.optString("slug").takeIf { it.isNotBlank() } ?: return null
        val name = decodeHtmlEntities(obj.optString("name", slug))
        val thumb = obj.optString("thumb_url", "")
        val time = obj.optString("time", "")
        val quality = obj.optString("quality", "FHD").ifBlank { "HD" }
        val lang = obj.optString("lang", "Vietsub").ifBlank { "Vietsub" }

        val actors = mutableListOf<String>()
        val actArr = obj.optJSONArray("actors")
        if (actArr != null) {
            for (i in 0 until actArr.length()) {
                val a = decodeHtmlEntities(actArr.optString(i, "")).trim()
                if (a.isNotBlank()) actors.add(a)
            }
        }

        val categories = mutableListOf<String>()
        val catArr = obj.optJSONArray("categories")
        if (catArr != null) {
            for (i in 0 until catArr.length()) {
                val cObj = catArr.optJSONObject(i)
                val cName = cObj?.optString("name", "")?.trim().orEmpty()
                if (cName.isNotBlank()) categories.add(cName)
            }
        }

        val countryName = obj.optJSONObject("country")?.optString("name", "Nhật Bản") ?: "Nhật Bản"

        val servers = mutableListOf<MovieServer>()
        val epArr = obj.optJSONArray("episodes")
        if (epArr != null) {
            for (sIdx in 0 until epArr.length()) {
                val sObj = epArr.optJSONObject(sIdx) ?: continue
                val sName = sObj.optString("server_name", "Server #${sIdx + 1}")
                val sData = sObj.optJSONArray("server_data")
                val eps = mutableListOf<MovieEpisode>()
                if (sData != null) {
                    for (eIdx in 0 until sData.length()) {
                        val eObj = sData.optJSONObject(eIdx) ?: continue
                        val rawLink = eObj.optString("link", "").trim()
                        val directM3u8 = convertDfPlayerToM3u8(rawLink)
                        eps.add(
                            MovieEpisode(
                                name = eObj.optString("name", "Full"),
                                slug = eObj.optString("slug", "full"),
                                linkM3u8 = directM3u8,
                                linkEmbed = rawLink
                            )
                        )
                    }
                }
                if (eps.isNotEmpty()) {
                    servers.add(MovieServer(sName, eps))
                }
            }
        }

        val codePrefix = name.substringBefore('_').substringBefore(' ').takeIf { "-" in it } ?: ""
        val cleanName = name.replace('_', ' ')

        val item = MovieItem(
            name = cleanName,
            originName = actors.firstOrNull() ?: codePrefix,
            slug = slug,
            thumbUrl = thumb,
            posterUrl = thumb,
            year = "2025",
            quality = quality,
            lang = lang,
            type = "single",
            episodeCurrent = time.ifBlank { lang },
            rating = "",
            source = MovieSource.XXVN
        )

        val content = decodeHtmlEntities(obj.optString("content", ""))
            .replace(Regex("<[^>]*>"), "")
            .trim()
            .ifBlank { cleanName }

        return MovieDetail(
            movie = item,
            content = content,
            time = time,
            episodeTotal = "Full",
            director = emptyList(),
            actor = actors,
            categories = categories,
            countries = listOf(countryName),
            servers = servers
        )
    }

    private fun convertDfPlayerToM3u8(link: String): String {
        if (link.isBlank()) return ""
        if (link.contains(".m3u8")) return link
        // https://a.dfplayer.cc/bf.html?id=597 -> https://a.dfplayer.cc/v1/s/597.m3u8
        // https://a.dfplayer.cc/bf.html?did=597 -> https://a.dfplayer.cc/v2/s/597.m3u8
        val host = try {
            val u = URI(link)
            "${u.scheme}://${u.host}"
        } catch (_: Exception) {
            "https://a.dfplayer.cc"
        }
        val idMatch = Regex("""[?&]id=([^&#]+)""").find(link)
        if (idMatch != null) {
            return "$host/v1/s/${idMatch.groupValues[1]}.m3u8"
        }
        val didMatch = Regex("""[?&]did=([^&#]+)""").find(link)
        if (didMatch != null) {
            return "$host/v2/s/${didMatch.groupValues[1]}.m3u8"
        }
        return ""
    }

    // =========================================================================
    // 4. MISSAV SCRAPER + DEAN EDWARDS UNPACKER + SMART FALLBACK
    // =========================================================================

    private val MISSAV_DOMAINS = listOf(
        "https://missav.ws",
        "https://missav.ai",
        "https://missav.live"
    )

    private suspend fun fetchMissAvList(
        category: String = "",
        keyword: String = "",
        page: Int = 1
    ): Pair<List<MovieItem>, Int> {
        // 1. Try direct MissAV HTML scraping first (works when user has 1.1.1.1 / VPN or unblocked ISP)
        val path = when {
            keyword.isNotBlank() -> {
                val enc = URLEncoder.encode(keyword.trim(), "UTF-8")
                "/vi/search/$enc?page=$page"
            }
            category.isBlank() || category == "new" -> "/vi/new?page=$page"
            else -> "/vi/$category?page=$page"
        }

        for (domain in MISSAV_DOMAINS) {
            val html = fetchUrl("$domain$path", referer = "$domain/vi", timeoutMs = 3_500)
            if (!html.isNullOrBlank() && ("thumbnail" in html || "missav" in html.lowercase())) {
                val parsed = parseMissAvHtmlList(html, domain)
                if (parsed.isNotEmpty()) {
                    return parsed to 50
                }
            }
        }

        // 2. Smart Fallback via OpenAPI mapped to MissAV categories so it works 100% in VN without VPN
        val mappedTypeId = when (category) {
            "uncensored-leak" -> "3"
            "uncensored" -> "2"
            "censored" -> "1"
            "siro" -> "4"
            "chinese-subtitle" -> "7"
            "chinese-av" -> "5"
            "hentai" -> "6"
            else -> ""
        }
        return fetchAvdbList(
            source = MovieSource.MissAV,
            typeId = mappedTypeId,
            keyword = keyword,
            page = page
        )
    }

    private fun parseMissAvHtmlList(html: String, baseUrl: String): List<MovieItem> {
        val list = mutableListOf<MovieItem>()
        val cardRegex = Regex(
            """<div class="thumbnail group"[^>]*>.*?<a href="([^"]+)"[^>]*>.*?<img[^>]+data-src="([^"]+)"[^>]+alt="([^"]*)"[^>]*>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        for (match in cardRegex.findAll(html)) {
            val href = match.groupValues[1].trim()
            val img = match.groupValues[2].trim()
            val title = decodeHtmlEntities(match.groupValues[3].trim())
            val slug = href.trimEnd('/').substringAfterLast('/')
            if (slug.isNotBlank() && title.isNotBlank()) {
                val item = MovieItem(
                    name = title,
                    originName = slug.uppercase(),
                    slug = slug,
                    thumbUrl = img,
                    posterUrl = img,
                    year = "2026",
                    quality = "FHD",
                    lang = "MissAV",
                    type = "single",
                    episodeCurrent = slug.uppercase(),
                    source = MovieSource.MissAV
                )
                list.add(item)
            }
        }
        return list
    }

    /**
     * Giải nén thuật toán Dean Edwards Packer: eval(function(p,a,c,k,e,d){...}('payload', radix, count, 'keywords'.split('|')))
     * để trích xuất trực tiếp link HLS https://surrit.com/{uuid}/playlist.m3u8 từ trang MissAV.
     */
    fun unpackDeanEdwards(html: String): String? {
        // Cách 1: Tìm trực tiếp UUID của surrit.com hoặc m3u8 trong HTML nếu có
        val directSurrit = Regex("""https?://surrit\.com/[0-9a-fA-F-]{36}/playlist\.m3u8""").find(html)
        if (directSurrit != null) return directSurrit.value

        // Cách 2: Tìm khối eval(function(p,a,c,k,e,d)...
        val packerRegex = Regex(
            """eval\(function\(p,a,c,k,e,[dr]\)\{.*?\}\(\s*'((?:\\.|[^'])*)'\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*'((?:\\.|[^'])*)'\.split\('\|'\)""",
            RegexOption.DOT_MATCHES_ALL
        )
        for (m in packerRegex.findAll(html)) {
            val payload = m.groupValues[1]
            val radix = m.groupValues[2].toIntOrNull() ?: 36
            val symtab = m.groupValues[4].split('|')

            val unpacked = Regex("""\b\w+\b""").replace(payload) { wordMatch ->
                val word = wordMatch.value
                val idx = word.toIntOrNull(radix)
                if (idx != null && idx in symtab.indices && symtab[idx].isNotEmpty()) {
                    symtab[idx]
                } else {
                    word
                }
            }
            val surritMatch = Regex("""https?://surrit\.com/[0-9a-fA-F-]{36}/playlist\.m3u8""").find(unpacked.replace("\\/", "/"))
            if (surritMatch != null) return surritMatch.value

            val uuidMatch = Regex("""[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}""").find(unpacked)
            if (uuidMatch != null) {
                return "https://surrit.com/${uuidMatch.value}/playlist.m3u8"
            }
        }
        return null
    }

    // =========================================================================
    // 5. DETAIL FETCHING
    // =========================================================================

    suspend fun getDetail(source: MovieSource, slug: String): MovieDetail? {
        detailCache["${source.name}:$slug"]?.let { return it }

        return when (source) {
            MovieSource.XXVN -> {
                val url = "https://xxvnapi.com/api/phim/$slug"
                val raw = fetchUrl(url, referer = "https://xxvnapi.com/") ?: return null
                try {
                    val root = JSONObject(raw)
                    val mvObj = root.optJSONObject("movie") ?: return null
                    val detail = parseXxvnDetailObject(mvObj)
                    if (detail != null) {
                        detailCache["${source.name}:$slug"] = detail
                    }
                    detail
                } catch (_: Exception) {
                    null
                }
            }
            MovieSource.MissAV -> {
                // Try direct MissAV detail page first
                for (domain in MISSAV_DOMAINS) {
                    val html = fetchUrl("$domain/vi/$slug", referer = "$domain/vi", timeoutMs = 3_500)
                    if (!html.isNullOrBlank()) {
                        val m3u8 = unpackDeanEdwards(html)
                        if (!m3u8.isNullOrBlank()) {
                            val titleMatch = Regex("""<meta property="og:title" content="([^"]+)"""").find(html)
                            val imgMatch = Regex("""<meta property="og:image" content="([^"]+)"""").find(html)
                            val descMatch = Regex("""<meta property="og:description" content="([^"]+)"""").find(html)
                            val title = decodeHtmlEntities(titleMatch?.groupValues?.get(1) ?: slug.uppercase())
                            val img = imgMatch?.groupValues?.get(1) ?: ""
                            val desc = decodeHtmlEntities(descMatch?.groupValues?.get(1) ?: title)
                            val item = MovieItem(
                                name = title,
                                originName = slug.uppercase(),
                                slug = slug,
                                thumbUrl = img,
                                posterUrl = img,
                                year = "2026",
                                quality = "FHD",
                                lang = "MissAV",
                                episodeCurrent = slug.uppercase(),
                                source = MovieSource.MissAV
                            )
                            val detail = MovieDetail(
                                movie = item,
                                content = desc,
                                time = "HD",
                                episodeTotal = "Full",
                                countries = listOf("Nhật Bản"),
                                servers = listOf(
                                    MovieServer(
                                        serverName = "MissAV Surrit CDN",
                                        episodes = listOf(
                                            MovieEpisode(
                                                name = "Full HD",
                                                slug = "full",
                                                linkM3u8 = m3u8,
                                                linkEmbed = "$domain/vi/$slug"
                                            )
                                        )
                                    )
                                )
                            )
                            detailCache["${source.name}:$slug"] = detail
                            return detail
                        }
                    }
                }
                // Fallback to AVDB lookup
                fetchAvdbDetailBySlug(source, slug)
            }
            else -> fetchAvdbDetailBySlug(source, slug)
        }
    }

    private suspend fun fetchAvdbDetailBySlug(source: MovieSource, slug: String): MovieDetail? {
        val enc = URLEncoder.encode(slug.replace('-', ' '), "UTF-8")
        val url = "https://avdbapi.com/api.php/provide/vod/?ac=detail&wd=$enc"
        val raw = fetchUrl(url, referer = "https://avdbapi.com/") ?: return null
        return try {
            val root = JSONObject(raw)
            val arr = root.optJSONArray("list") ?: return null
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val detail = parseAvdbDetailObject(obj, source) ?: continue
                detailCache["${source.name}:${detail.movie.slug}"] = detail
                if (detail.movie.slug.equals(slug, ignoreCase = true)) {
                    return detail
                }
            }
            if (arr.length() > 0) {
                parseAvdbDetailObject(arr.getJSONObject(0), source)
            } else null
        } catch (_: Exception) {
            null
        }
    }

    // =========================================================================
    // 6. STREAM RESOLUTION FOR AV SOURCES
    // =========================================================================

    suspend fun resolveAvStream(
        source: MovieSource,
        movieTitle: String,
        server: MovieServer,
        episode: MovieEpisode
    ): MovieStream = withContext(Dispatchers.IO) {
        val cacheKey = "${source.name}:${episode.linkM3u8}:${episode.linkEmbed}"
        val now = System.currentTimeMillis()
        resolvedStreamCache[cacheKey]?.let { (ts, cached) ->
            // Cache signed stream URLs for 15 minutes
            if (now - ts < 15 * 60 * 1000L && cached.streamUrl.isNotBlank()) {
                return@withContext cached
            }
        }

        // 1. XXVN dfplayer links
        if (source == MovieSource.XXVN) {
            val m3u8 = episode.linkM3u8.ifBlank { convertDfPlayerToM3u8(episode.linkEmbed) }
            val stream = MovieStream(
                streamUrl = m3u8,
                referer = "https://a.dfplayer.cc/",
                movieTitle = movieTitle,
                episodeName = episode.name,
                serverName = server.serverName,
                source = source
            )
            if (m3u8.isNotBlank()) resolvedStreamCache[cacheKey] = now to stream
            return@withContext stream
        }

        // 2. If episode already has direct surrit.com or .m3u8
        if (episode.linkM3u8.isNotBlank() && (".m3u8" in episode.linkM3u8 || "helvid.com" in episode.linkM3u8)) {
            val ref = when {
                "surrit.com" in episode.linkM3u8 -> "https://missav.ws/"
                "helvid.com" in episode.linkM3u8 -> "https://upload18.org/"
                else -> "https://avdbapi.com/"
            }
            val stream = MovieStream(
                streamUrl = episode.linkM3u8,
                referer = ref,
                movieTitle = movieTitle,
                episodeName = episode.name,
                serverName = server.serverName,
                source = source
            )
            resolvedStreamCache[cacheKey] = now to stream
            return@withContext stream
        }

        // 3. Extract from upload18.org embed player (window.PLAYER_CONFIG = {...})
        val embed = episode.linkEmbed.trim()
        if (embed.isNotBlank()) {
            if ("upload18." in embed) {
                val html = fetchUrl(embed, referer = "https://avdbapi.com/")
                if (!html.isNullOrBlank()) {
                    val cfgMatch = Regex("""window\.PLAYER_CONFIG\s*=\s*(\{.*?\});""").find(html)
                    if (cfgMatch != null) {
                        try {
                            val cfg = JSONObject(cfgMatch.groupValues[1])
                            val m3u8 = cfg.optString("m3u8", "").trim()
                            if (m3u8.isNotBlank()) {
                                val embedHost = try {
                                    val u = URI(embed)
                                    "${u.scheme}://${u.host}/"
                                } catch (_: Exception) {
                                    "https://upload18.org/"
                                }
                                val stream = MovieStream(
                                    streamUrl = m3u8,
                                    referer = embedHost,
                                    movieTitle = movieTitle,
                                    episodeName = episode.name,
                                    serverName = server.serverName,
                                    source = source
                                )
                                resolvedStreamCache[cacheKey] = now to stream
                                return@withContext stream
                            }
                        } catch (_: Exception) {}
                    }
                }
            }

            // 4. Try unpacking Dean Edwards if embed is a MissAV page
            val html = fetchUrl(embed, referer = embed)
            if (!html.isNullOrBlank()) {
                val unpackedM3u8 = unpackDeanEdwards(html)
                if (!unpackedM3u8.isNullOrBlank()) {
                    val stream = MovieStream(
                        streamUrl = unpackedM3u8,
                        referer = "https://missav.ws/",
                        movieTitle = movieTitle,
                        episodeName = episode.name,
                        serverName = server.serverName,
                        source = source
                    )
                    resolvedStreamCache[cacheKey] = now to stream
                    return@withContext stream
                }
            }
        }

        MovieStream(
            streamUrl = episode.linkM3u8.ifBlank { episode.linkEmbed },
            referer = "https://upload18.org/",
            movieTitle = movieTitle,
            episodeName = episode.name,
            serverName = server.serverName,
            source = source
        )
    }

    private fun decodeHtmlEntities(input: String): String {
        if (input.isBlank() || !input.contains('&')) return input
        return input
            .replace("&#039;", "'")
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
    }
}
