package com.tmplayer.av

import com.tmplayer.movie.FilterOption
import com.tmplayer.movie.MovieDetail
import com.tmplayer.movie.MovieEpisode
import com.tmplayer.movie.MovieItem
import com.tmplayer.movie.MovieServer
import com.tmplayer.movie.MovieSource
import com.tmplayer.movie.MovieStream
import com.tmplayer.movie.MovieWatchProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap

object AvFilterData {
    fun categoriesFor(source: MovieSource): List<FilterOption> = when (source) {
        MovieSource.XXVN -> listOf(
            FilterOption("", "Tất cả"),
            FilterOption("for-you", "✨ Đề xuất cho bạn"),
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
            FilterOption("for-you", "✨ Đề xuất cho bạn"),
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
            FilterOption("for-you", "✨ Đề xuất cho bạn"),
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

    const val IOS_SAFARI_UA =
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1"

    const val CHROME_UA = IOS_SAFARI_UA

    private val detailCache = ConcurrentHashMap<String, MovieDetail>()
    private val allDetailsBySlug = ConcurrentHashMap<String, MovieDetail>()
    private val resolvedStreamCache = ConcurrentHashMap<String, Pair<Long, MovieStream>>()

    fun invalidateStreamCache(episode: MovieEpisode?) {
        if (episode == null) {
            resolvedStreamCache.clear()
            return
        }
        val suffix = ":${episode.linkM3u8}:${episode.linkEmbed}"
        resolvedStreamCache.keys.removeIf { it.endsWith(suffix) }
    }

    private fun cacheDetail(source: MovieSource, detail: MovieDetail) {
        detailCache["${source.name}:${detail.movie.slug}"] = detail
        allDetailsBySlug[detail.movie.slug.lowercase()] = detail
    }

    fun getCachedDetail(slug: String): MovieDetail? {
        return allDetailsBySlug[slug.lowercase()]
    }

    private suspend fun fetchUrl(
        url: String,
        referer: String? = null,
        timeoutMs: Int = 10_000,
        userAgent: String = IOS_SAFARI_UA
    ): String? = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val uri = URI(url).toURL()
            conn = (uri.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", userAgent)
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

    private fun normalizeSearchText(input: String): String {
        if (input.isBlank()) return ""
        val nfd = Normalizer.normalize(input.lowercase(), Normalizer.Form.NFD)
        return nfd
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .replace('đ', 'd')
            .replace('[', ' ')
            .replace(']', ' ')
            .replace('_', ' ')
            .replace('-', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun extractJavCode(query: String): String? {
        val clean = query.uppercase().replace('_', '-')
        val fc2Match = Regex("""FC2-PPV-\d{5,8}""").find(clean)
        if (fc2Match != null) return fc2Match.value
        val stdMatch = Regex("""\b[A-Z]{2,7}-\d{2,6}\b""").find(clean)
        return stdMatch?.value
    }

    /**
     * Tìm kiếm tức thì trong toàn bộ danh sách phim đã tải về bộ nhớ đệm (kể cả các kệ trên màn hình chính,
     * danh mục, MissAV, AVDB, XXVN) theo tên phim, mã JAV, tên diễn viên, thể loại hoặc nội dung mô tả.
     */
    private fun searchLocalCache(targetSource: MovieSource, rawQuery: String): List<MovieItem> {
        val qNorm = normalizeSearchText(rawQuery)
        if (qNorm.isBlank()) return emptyList()
        val qCompact = qNorm.replace(" ", "")
        val qTokens = qNorm.split(' ').filter { it.isNotBlank() }
        val codeToken = extractJavCode(rawQuery)?.lowercase()

        val scored = mutableListOf<Pair<Int, MovieItem>>()
        val seenSlugs = HashSet<String>()

        val candidates = (detailCache.values + allDetailsBySlug.values)
        for (detail in candidates) {
            val slugKey = detail.movie.slug.lowercase()
            if (!seenSlugs.add(slugKey)) continue

            val nameNorm = normalizeSearchText(detail.movie.name)
            val originNorm = normalizeSearchText(detail.movie.originName)
            val slugNorm = normalizeSearchText(detail.movie.slug)
            val slugCompact = slugNorm.replace(" ", "")
            val actorsNorm = detail.actor.map { normalizeSearchText(it) }
            val catsNorm = detail.categories.map { normalizeSearchText(it) }
            val contentNorm = normalizeSearchText(detail.content)

            var score = 0
            if (codeToken != null && (detail.movie.slug.lowercase().contains(codeToken) || detail.movie.name.lowercase().contains(codeToken))) {
                score = maxOf(score, 100)
            }
            if (slugCompact == qCompact || slugNorm.contains(qNorm) || slugCompact.contains(qCompact)) {
                score = maxOf(score, 95)
            }
            if (nameNorm.contains(qNorm) || originNorm.contains(qNorm)) {
                score = maxOf(score, 88)
            }
            if (actorsNorm.any { it.contains(qNorm) || qNorm.contains(it) && it.length >= 3 }) {
                score = maxOf(score, 85)
            }
            if (catsNorm.any { it.contains(qNorm) }) {
                score = maxOf(score, 70)
            }
            if (score == 0 && qTokens.size > 1) {
                val combined = "$nameNorm $originNorm $slugNorm ${actorsNorm.joinToString(" ")} ${catsNorm.joinToString(" ")} $contentNorm"
                if (qTokens.all { token -> combined.contains(token) }) {
                    score = 65
                }
            }
            if (score == 0 && contentNorm.contains(qNorm)) {
                score = 50
            }

            if (score > 0) {
                val sourceBonus = if (detail.movie.source == targetSource) 5 else 0
                val adaptedItem = if (detail.movie.source == targetSource) {
                    detail.movie
                } else {
                    detail.movie.copy(source = targetSource).also {
                        detailCache["${targetSource.name}:${it.slug}"] = detail.copy(movie = it)
                    }
                }
                scored.add((score + sourceBonus) to adaptedItem)
            }
        }

        return scored
            .sortedByDescending { it.first }
            .map { it.second }
    }

    // =========================================================================
    // 1. LATEST, CATEGORY, SEARCH, RELATED & RECOMMENDATIONS
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
    ): Pair<List<MovieItem>, Int> = coroutineScope {
        if (keyword.isNotBlank()) {
            return@coroutineScope searchAvComprehensive(source, keyword.trim(), page)
        }
        when (source) {
            MovieSource.XXVN -> fetchXxvnList(category = category, keyword = "", page = page)
            MovieSource.MissAV -> fetchMissAvList(category = category, keyword = "", page = page)
            else -> fetchAvdbList(source = MovieSource.AVDB, typeId = category, keyword = "", page = page)
        }
    }

    /**
     * Tìm kiếm đa tầng ổn định cho cả 3 nguồn (AVDB, MissAV, XXVN):
     * 1. Tìm ngay trong bộ nhớ đệm cục bộ (đảm bảo mọi phim đang hiển thị trong danh sách phát đều khớp 100%).
     * 2. Tìm song song theo Mã phim / Tên phim (`wd`), Diễn viên (`actor`), và fallback sang `ac=list` nếu `ac=detail` bị quá tải.
     * 3. Hỗ trợ tìm chéo tiếng Việt / tiếng Anh / Romaji để luôn trả về kết quả chính xác nhất.
     */
    private suspend fun searchAvComprehensive(
        source: MovieSource,
        rawKeyword: String,
        page: Int
    ): Pair<List<MovieItem>, Int> = coroutineScope {
        val localMatches = if (page == 1) searchLocalCache(source, rawKeyword) else emptyList()

        val cleanedKeyword = rawKeyword
            .replace('[', ' ')
            .replace(']', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
        val extractedCode = extractJavCode(rawKeyword)

        val jobs = mutableListOf<kotlinx.coroutines.Deferred<List<MovieItem>>>()

        // Job 1: Nguồn hiện tại
        jobs.add(async {
            when (source) {
                MovieSource.XXVN -> fetchXxvnList(category = "", keyword = cleanedKeyword, page = page).first
                MovieSource.MissAV -> fetchMissAvList(category = "", keyword = cleanedKeyword, page = page).first
                else -> fetchAvdbList(source = MovieSource.AVDB, typeId = "", keyword = cleanedKeyword, page = page).first
            }
        })

        // Job 2: Nếu người dùng copy cả tên dài kèm mã JAV (VD: "[FC2-PPV-4834713] 12 năm..."), tìm thêm theo riêng mã JAV
        if (extractedCode != null && !extractedCode.equals(cleanedKeyword, ignoreCase = true)) {
            jobs.add(async {
                fetchAvdbList(source = source, typeId = "", keyword = extractedCode, page = page).first
            })
        }

        // Job 3: Tìm theo tên diễn viên (Actor) trên AVDB OpenAPI (vì tham số `wd` của AVDB không tìm trong cột `actor`)
        if (extractedCode == null && cleanedKeyword.none { it.isDigit() }) {
            jobs.add(async {
                fetchAvdbByActor(source = source, actorName = cleanedKeyword, page = page)
            })
        }

        // Job 4: Tìm bổ sung từ AVDB (nếu đang ở XXVN) hoặc từ XXVN (nếu từ khóa có dấu tiếng Việt)
        if (source == MovieSource.XXVN) {
            jobs.add(async {
                fetchAvdbList(source = MovieSource.XXVN, typeId = "", keyword = cleanedKeyword, page = page).first
            })
        } else if (cleanedKeyword.any { it.code > 127 } || page == 1) {
            jobs.add(async {
                fetchXxvnList(category = "", keyword = cleanedKeyword, page = page).first.map { item ->
                    item.copy(source = source).also { adapted ->
                        allDetailsBySlug[item.slug.lowercase()]?.let { d ->
                            detailCache["${source.name}:${adapted.slug}"] = d.copy(movie = adapted)
                        }
                    }
                }
            })
        }

        val remoteLists = jobs.awaitAll().flatten()

        // Nếu vẫn ít kết quả ở trang 1, kiểm tra lại bộ nhớ đệm sau khi các job mạng đã nạp thêm dữ liệu
        val postFetchLocal = if (page == 1) searchLocalCache(source, rawKeyword) else emptyList()

        val merged = (localMatches + remoteLists + postFetchLocal)
            .distinctBy { it.slug.lowercase() }

        merged to (if (merged.size >= 16) page + 1 else page)
    }

    /**
     * Đề xuất các phim tương tự (Related Movies) giống giao diện MissAV:
     * Kết hợp phim cùng diễn viên, cùng hãng/nhãn (FC2, SONE, IPZZ...), và cùng thể loại.
     */
    suspend fun getRelatedMovies(detail: MovieDetail): List<MovieItem> = coroutineScope {
        val source = detail.movie.source
        val currentSlug = detail.movie.slug.lowercase()
        val primaryActress = detail.actor.firstOrNull { it.isNotBlank() && !it.equals("Updating", ignoreCase = true) }
        val codePrefix = AvStore.extractCodePrefix(detail.movie.slug, detail.movie.name)

        // 1. Tìm nhanh các phim tương đồng đã có trong cache
        val cachedRelated = allDetailsBySlug.values
            .asSequence()
            .filter { it.movie.slug.lowercase() != currentSlug }
            .mapNotNull { cand ->
                var score = 0
                if (primaryActress != null && cand.actor.any { it.equals(primaryActress, ignoreCase = true) }) {
                    score += 50
                }
                if (codePrefix.isNotBlank() &&
                    AvStore.extractCodePrefix(cand.movie.slug, cand.movie.name).equals(codePrefix, ignoreCase = true)
                ) {
                    score += 35
                }
                val sharedCats = cand.categories.count { c -> detail.categories.any { it.equals(c, ignoreCase = true) } }
                score += sharedCats * 10
                if (score > 0) score to cand.movie else null
            }
            .sortedByDescending { it.first }
            .map { it.second }
            .take(16)
            .toList()

        // 2. Gọi song song API theo Diễn viên và theo Nhãn/Prefix (VD: FC2-PPV, SONE, IPZZ...)
        val actressDef = async {
            if (!primaryActress.isNullOrBlank()) {
                fetchAvdbByActor(source, primaryActress, 1)
            } else emptyList()
        }
        val prefixDef = async {
            if (codePrefix.isNotBlank()) {
                fetchAvdbList(source, typeId = "", keyword = codePrefix, page = 1).first
            } else emptyList()
        }

        val remoteActress = actressDef.await()
        val remotePrefix = prefixDef.await()

        val combined = (remoteActress + remotePrefix + cachedRelated)
            .filter { !it.slug.equals(detail.movie.slug, ignoreCase = true) }
            .distinctBy { it.slug.lowercase() }
            .take(24)

        if (combined.size >= 6) {
            combined
        } else {
            val fallback = getLatest(source, 1)
                .filter { !it.slug.equals(detail.movie.slug, ignoreCase = true) }
            (combined + fallback).distinctBy { it.slug.lowercase() }.take(20)
        }
    }

    /**
     * "✨ Đề xuất cho bạn" (Personalized Recommendations dựa trên các phim đã từng xem & theo dõi):
     * Phân tích diễn viên, nhãn hãng phim (FC2, SONE, IPZZ, MIDV...) và thể loại từ lịch sử xem để gợi ý phim tương đồng.
     */
    suspend fun getRecommendationsForYou(
        source: MovieSource,
        history: List<MovieWatchProgress>,
        watchlist: List<MovieItem>,
        preferredActresses: List<String>,
        preferredPrefixes: List<String>
    ): List<MovieItem> = coroutineScope {
        val watchedSlugs = history.map { it.slug.lowercase() }.toSet()

        // Trích xuất thêm diễn viên & prefix từ lịch sử xem và danh sách theo dõi
        val historyPrefixes = (history.map { AvStore.extractCodePrefix(it.slug, it.name) } +
            watchlist.map { AvStore.extractCodePrefix(it.slug, it.name) })
            .filter { it.isNotBlank() }

        val historyActresses = (history.mapNotNull { allDetailsBySlug[it.slug.lowercase()]?.actor } +
            watchlist.mapNotNull { allDetailsBySlug[it.slug.lowercase()]?.actor })
            .flatten()
            .filter { it.isNotBlank() && !it.equals("Updating", ignoreCase = true) }

        val topActresses = (preferredActresses + historyActresses).distinct().take(2)
        val topPrefixes = (preferredPrefixes + historyPrefixes).distinct().take(3)

        val jobs = mutableListOf<kotlinx.coroutines.Deferred<List<MovieItem>>>()

        for (actress in topActresses) {
            jobs.add(async { fetchAvdbByActor(source, actress, 1) })
        }
        for (prefix in topPrefixes) {
            jobs.add(async { fetchAvdbList(source, typeId = "", keyword = prefix, page = 1).first })
        }

        // Nếu người dùng mới mở chưa có lịch sử, gợi ý các dòng phim hot nhất trên MissAV / AVDB
        if (jobs.isEmpty()) {
            jobs.add(async { fetchAvdbList(source, typeId = "2", keyword = "", page = 1).first })
            jobs.add(async { fetchAvdbList(source, typeId = "1", keyword = "", page = 1).first })
        }

        val fetchedLists = jobs.awaitAll()
        // Đan xen (interleave) kết quả từ các sở thích khác nhau để kệ đề xuất đa dạng
        val interleaved = mutableListOf<MovieItem>()
        val maxLen = fetchedLists.maxOfOrNull { it.size } ?: 0
        for (i in 0 until maxLen) {
            for (list in fetchedLists) {
                if (i < list.size) {
                    interleaved.add(list[i])
                }
            }
        }

        val unwatched = interleaved
            .filter { it.slug.lowercase() !in watchedSlugs }
            .distinctBy { it.slug.lowercase() }

        if (unwatched.size >= 8) {
            unwatched.take(24)
        } else {
            val extra = getLatest(source, 1).filter { it.slug.lowercase() !in watchedSlugs }
            (unwatched + extra).distinctBy { it.slug.lowercase() }.take(24)
        }
    }

    // =========================================================================
    // 2. AVDB OPENAPI (https://avdbapi.com/api.php/provide/vod)
    // =========================================================================

    private suspend fun fetchAvdbByActor(
        source: MovieSource,
        actorName: String,
        page: Int = 1
    ): List<MovieItem> {
        val enc = URLEncoder.encode(actorName.trim(), "UTF-8")
        val url = "https://avdbapi.com/api.php/provide/vod?ac=detail&pg=$page&actor=$enc"
        val raw = fetchUrl(url, referer = "https://avdbapi.com/") ?: return emptyList()
        return parseAvdbResponse(raw, source).first
    }

    private suspend fun fetchAvdbList(
        source: MovieSource,
        typeId: String = "",
        keyword: String = "",
        page: Int = 1
    ): Pair<List<MovieItem>, Int> {
        val url = buildString {
            append("https://avdbapi.com/api.php/provide/vod?ac=detail&pg=$page")
            if (typeId.isNotBlank() && typeId.all { it.isDigit() }) {
                append("&t=$typeId")
            }
            if (keyword.isNotBlank()) {
                val enc = URLEncoder.encode(keyword.trim(), "UTF-8")
                append("&wd=$enc")
            }
        }
        val raw = fetchUrl(url, referer = "https://avdbapi.com/")
        if (!raw.isNullOrBlank()) {
            val parsed = parseAvdbResponse(raw, source)
            if (parsed.first.isNotEmpty()) return parsed
        }

        // Fallback nhẹ qua ac=list -> ac=detail&ids=... nếu truy vấn ac=detail&wd=... bị Cloudflare 521 timeout
        if (keyword.isNotBlank()) {
            val enc = URLEncoder.encode(keyword.trim(), "UTF-8")
            val listUrl = "https://avdbapi.com/api.php/provide/vod?ac=list&pg=$page&wd=$enc"
            val listRaw = fetchUrl(listUrl, referer = "https://avdbapi.com/")
            if (!listRaw.isNullOrBlank()) {
                try {
                    val root = JSONObject(listRaw)
                    val arr = root.optJSONArray("list")
                    if (arr != null && arr.length() > 0) {
                        val ids = mutableListOf<String>()
                        for (i in 0 until minOf(arr.length(), 20)) {
                            val id = arr.optJSONObject(i)?.optString("id", "").orEmpty()
                            if (id.isNotBlank()) ids.add(id)
                        }
                        if (ids.isNotEmpty()) {
                            val detailByIdsUrl = "https://avdbapi.com/api.php/provide/vod?ac=detail&ids=${ids.joinToString(",")}"
                            val detailRaw = fetchUrl(detailByIdsUrl, referer = "https://avdbapi.com/")
                            if (!detailRaw.isNullOrBlank()) {
                                return parseAvdbResponse(detailRaw, source)
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        return emptyList<MovieItem>() to 1
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
                cacheDetail(source, detail)
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
        val typeName = obj.optString("type_name", "JAV")
        val time = obj.optString("time", "").trim()
        val year = obj.optString("year", "").trim()
        val quality = obj.optString("quality", "HD").ifBlank { "HD" }

        val displayTitle = if (rawName.startsWith(movieCode, ignoreCase = true) ||
            rawName.startsWith("[$movieCode]", ignoreCase = true)
        ) {
            rawName
        } else {
            "$movieCode $rawName"
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
            thumbUrl = poster, // Trong AVDB poster_url là ảnh ngang 16:9 cực nét
            posterUrl = poster,
            year = year,
            quality = quality,
            lang = typeName,
            type = "single",
            episodeCurrent = time.ifBlank { movieCode },
            rating = movieCode,
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
                val slugs = Regex("""(?:https://xxvnapi\.com)?/phim/([a-zA-Z0-9_-]+)""")
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
                cacheDetail(MovieSource.XXVN, detail)
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
            rating = codePrefix,
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
        if (link.contains(".m3u8")) return link.replace("/v1/s/", "/v2/s/")
        val host = try {
            val u = URI(link)
            "${u.scheme}://${u.host}"
        } catch (_: Exception) {
            "https://a.dfplayer.cc"
        }
        val didMatch = Regex("""[?&]did=([^&#]+)""").find(link)
        if (didMatch != null) {
            return "$host/v2/s/${didMatch.groupValues[1]}.m3u8"
        }
        val idMatch = Regex("""[?&]id=([^&#]+)""").find(link)
        if (idMatch != null) {
            // /v1/s/ points to dead TikTok CDN segments (403 Forbidden); /v2/s/ uses the same numeric ID on self-hosted CDN (200 OK)
            return "$host/v2/s/${idMatch.groupValues[1]}.m3u8"
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
        val path = when {
            keyword.isNotBlank() -> {
                val enc = URLEncoder.encode(keyword.trim(), "UTF-8")
                "/vi/search/$enc?page=$page"
            }
            category.isBlank() || category == "new" -> "/vi/new?page=$page"
            else -> "/vi/$category?page=$page"
        }

        for (domain in MISSAV_DOMAINS) {
            val html = fetchUrl("$domain$path", referer = "$domain/vi", timeoutMs = 3_000)
            if (!html.isNullOrBlank() && ("thumbnail" in html || "missav" in html.lowercase())) {
                val parsed = parseMissAvHtmlList(html)
                if (parsed.isNotEmpty()) {
                    return parsed to 50
                }
            }
        }

        val mappedTypeId = when (category) {
            "uncensored-leak" -> "3"
            "uncensored" -> "2"
            "censored" -> "1"
            "siro", "fc2" -> "4"
            "chinese-subtitle" -> "7"
            "chinese-av", "madou" -> "5"
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

    private fun parseMissAvHtmlList(html: String): List<MovieItem> {
        val list = mutableListOf<MovieItem>()
        val cardRegex = Regex(
            """<div class="thumbnail group"[^>]*>(.*?)</div>\s*</div>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        val linkImgRegex = Regex(
            """<a href="([^"]+)"[^>]*>.*?<img[^>]+data-src="([^"]+)"[^>]+alt="([^"]*)"[^>]*>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        val durationRegex = Regex("""\b(\d{1,2}:\d{2}:\d{2}|\d{1,3}:\d{2})\b""")

        for (cardMatch in cardRegex.findAll(html)) {
            val block = cardMatch.groupValues[1]
            val match = linkImgRegex.find(block) ?: continue
            val href = match.groupValues[1].trim()
            val img = match.groupValues[2].trim()
            val title = decodeHtmlEntities(match.groupValues[3].trim())
            val slug = href.trimEnd('/').substringAfterLast('/')
            val dur = durationRegex.find(block)?.groupValues?.get(1).orEmpty()
            if (slug.isNotBlank() && title.isNotBlank()) {
                val item = MovieItem(
                    name = title,
                    originName = slug.uppercase(),
                    slug = slug,
                    thumbUrl = img,
                    posterUrl = img,
                    year = "2026",
                    quality = "FHD",
                    lang = if ("uncensored" in slug.lowercase() || "fc2" in slug.lowercase()) "Không kiểm duyệt" else "MissAV",
                    type = "single",
                    episodeCurrent = dur.ifBlank { slug.uppercase() },
                    rating = slug.uppercase(),
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
        val directSurrit = Regex("""https?://surrit\.com/[0-9a-fA-F-]{36}/playlist\.m3u8""").find(html)
        if (directSurrit != null) return directSurrit.value

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
        allDetailsBySlug[slug.lowercase()]?.let { cached ->
            val adapted = cached.copy(movie = cached.movie.copy(source = source))
            detailCache["${source.name}:$slug"] = adapted
            return adapted
        }

        return when (source) {
            MovieSource.XXVN -> {
                val url = "https://xxvnapi.com/api/phim/$slug"
                val raw = fetchUrl(url, referer = "https://xxvnapi.com/") ?: return fetchAvdbDetailBySlug(source, slug)
                try {
                    val root = JSONObject(raw)
                    val mvObj = root.optJSONObject("movie") ?: return fetchAvdbDetailBySlug(source, slug)
                    val detail = parseXxvnDetailObject(mvObj)
                    if (detail != null) {
                        cacheDetail(source, detail)
                    }
                    detail
                } catch (_: Exception) {
                    fetchAvdbDetailBySlug(source, slug)
                }
            }
            MovieSource.MissAV -> {
                for (domain in MISSAV_DOMAINS) {
                    val html = fetchUrl("$domain/vi/$slug", referer = "$domain/vi", timeoutMs = 3_000)
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
                                rating = slug.uppercase(),
                                source = MovieSource.MissAV
                            )
                            val detail = MovieDetail(
                                movie = item,
                                content = desc,
                                time = "FHD",
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
                            cacheDetail(source, detail)
                            return detail
                        }
                    }
                }
                fetchAvdbDetailBySlug(source, slug)
            }
            else -> fetchAvdbDetailBySlug(source, slug)
        }
    }

    private suspend fun fetchAvdbDetailBySlug(source: MovieSource, slug: String): MovieDetail? {
        val queries = listOf(slug, slug.replace('-', ' ')).distinct()
        for (q in queries) {
            val enc = URLEncoder.encode(q, "UTF-8")
            val url = "https://avdbapi.com/api.php/provide/vod?ac=detail&wd=$enc"
            val raw = fetchUrl(url, referer = "https://avdbapi.com/") ?: continue
            try {
                val root = JSONObject(raw)
                val arr = root.optJSONArray("list") ?: continue
                var firstDetail: MovieDetail? = null
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val detail = parseAvdbDetailObject(obj, source) ?: continue
                    cacheDetail(source, detail)
                    if (firstDetail == null) firstDetail = detail
                    if (detail.movie.slug.equals(slug, ignoreCase = true)) {
                        return detail
                    }
                }
                if (firstDetail != null) return firstDetail
            } catch (_: Exception) {}
        }
        return null
    }

    // =========================================================================
    // 6. STREAM RESOLUTION FOR AV SOURCES
    // =========================================================================

    private fun extractM3u8FromUpload18Html(html: String): String {
        val cfgMatch = Regex("""window\.PLAYER_CONFIG\s*=\s*(\{.*?\});""", RegexOption.DOT_MATCHES_ALL).find(html)
        if (cfgMatch != null) {
            try {
                val cfg = JSONObject(cfgMatch.groupValues[1])
                val m3u8 = cfg.optString("m3u8", "")
                    .replace("\\/", "/")
                    .replace("\\u0026", "&")
                    .trim()
                if (m3u8.isNotBlank()) return m3u8
            } catch (_: Exception) {}
        }
        val regexFallback = Regex(""""m3u8"\s*:\s*"([^"]+)"""").find(html)
        if (regexFallback != null) {
            return regexFallback.groupValues[1]
                .replace("\\/", "/")
                .replace("\\u0026", "&")
                .trim()
        }
        return ""
    }

    suspend fun resolveAvStream(
        source: MovieSource,
        movieTitle: String,
        server: MovieServer,
        episode: MovieEpisode
    ): MovieStream = withContext(Dispatchers.IO) {
        val cacheKey = "${source.name}:${episode.linkM3u8}:${episode.linkEmbed}"
        val now = System.currentTimeMillis()
        resolvedStreamCache[cacheKey]?.let { (ts, cached) ->
            if (now - ts < 5 * 60 * 1000L && cached.streamUrl.isNotBlank()) {
                return@withContext cached
            }
        }

        // 1. XXVN dfplayer links
        if (source == MovieSource.XXVN && (episode.linkM3u8.contains("dfplayer") || episode.linkEmbed.contains("dfplayer"))) {
            val m3u8 = episode.linkM3u8
                .ifBlank { convertDfPlayerToM3u8(episode.linkEmbed) }
                .replace("/v1/s/", "/v2/s/")
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
            val normalizedM3u8 = if ("dfplayer.cc" in episode.linkM3u8) {
                episode.linkM3u8.replace("/v1/s/", "/v2/s/")
            } else {
                episode.linkM3u8
            }
            val ref = when {
                "surrit.com" in normalizedM3u8 -> "https://missav.ws/"
                "helvid.com" in normalizedM3u8 -> "https://upload18.org/"
                "dfplayer.cc" in normalizedM3u8 -> "https://a.dfplayer.cc/"
                else -> "https://avdbapi.com/"
            }
            val stream = MovieStream(
                streamUrl = normalizedM3u8,
                referer = ref,
                movieTitle = movieTitle,
                episodeName = episode.name,
                serverName = server.serverName,
                source = source
            )
            resolvedStreamCache[cacheKey] = now to stream
            return@withContext stream
        }

        // 3. Extract from upload18.org embed player (window.PLAYER_CONFIG = {...}) using iOS Safari UA (d=apple native HLS)
        val embed = episode.linkEmbed.trim()
        if (embed.isNotBlank()) {
            if ("dfplayer.cc" in embed) {
                val m3u8 = convertDfPlayerToM3u8(embed)
                if (m3u8.isNotBlank()) {
                    val stream = MovieStream(
                        streamUrl = m3u8,
                        referer = "https://a.dfplayer.cc/",
                        movieTitle = movieTitle,
                        episodeName = episode.name,
                        serverName = server.serverName,
                        source = source
                    )
                    resolvedStreamCache[cacheKey] = now to stream
                    return@withContext stream
                }
            }

            if ("upload18." in embed) {
                val html = fetchUrl(embed, referer = "https://avdbapi.com/", userAgent = IOS_SAFARI_UA)
                if (!html.isNullOrBlank()) {
                    val m3u8 = extractM3u8FromUpload18Html(html)
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
                }
            }

            // 4. Try unpacking Dean Edwards if embed is a MissAV page
            val html = fetchUrl(embed, referer = embed, userAgent = IOS_SAFARI_UA)
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
