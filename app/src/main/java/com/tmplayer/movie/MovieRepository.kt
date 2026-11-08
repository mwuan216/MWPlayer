package com.tmplayer.movie

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.collection.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
import java.text.Normalizer

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
        if (source.isAv) return com.tmplayer.av.AvRepository.getLatest(source, page)
        val url = when (source) {
            MovieSource.KKPhim -> "https://phimapi.com/danh-sach/phim-moi-cap-nhat?page=$page"
            else -> "https://vsmov.com/api/danh-sach/phim-moi-cap-nhat?page=$page"
        }
        val referer = if (source == MovieSource.VSMov) "https://vsmov.com/" else "https://phimapi.com/"
        val jsonStr = fetchJson(url, referer) ?: return emptyList()
        return parseMovieList(jsonStr, source)
    }

    // =========================================================================
    // 2. CATEGORY / SHELF MOVIES
    // =========================================================================

    suspend fun getCategoryMovies(source: MovieSource, categorySlug: String, page: Int = 1): List<MovieItem> {
        if (source.isAv) return com.tmplayer.av.AvRepository.getCategory(source, categorySlug, page)
        val url = when (source) {
            MovieSource.KKPhim -> "https://phimapi.com/v1/api/danh-sach/$categorySlug?page=$page&limit=18"
            else -> {
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
            else -> "https://vsmov.com/api/the-loai/$genreSlug?page=$page"
        }
        val referer = if (source == MovieSource.VSMov) "https://vsmov.com/" else "https://phimapi.com/"
        val jsonStr = fetchJson(url, referer) ?: return emptyList()
        return parseMovieList(jsonStr, source)
    }

    suspend fun getCountryMovies(source: MovieSource, countrySlug: String, page: Int = 1): List<MovieItem> {
        val url = when (source) {
            MovieSource.KKPhim -> "https://phimapi.com/v1/api/quoc-gia/$countrySlug?page=$page&limit=18"
            else -> "https://vsmov.com/api/quoc-gia/$countrySlug?page=$page"
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
        if (source.isAv) return com.tmplayer.av.AvRepository.filterAv(source, category, keyword, page)
        val referer = if (source == MovieSource.VSMov) "https://vsmov.com/" else "https://phimapi.com/"

        val url = when (source) {
            MovieSource.KKPhim -> {
                val baseUrl = "https://phimapi.com/v1/api"
                buildString {
                    if (keyword.isNotBlank()) {
                        val encKw = URLEncoder.encode(keyword.trim(), "UTF-8")
                        append("$baseUrl/tim-kiem?keyword=$encKw&page=$page&limit=24")
                        if (category.isNotBlank()) append("&category=$category")
                        if (genre.isNotBlank()) append("&category=$genre")
                        if (country.isNotBlank()) append("&country=$country")
                        if (year.isNotBlank()) append("&year=$year")
                    } else {
                        val catSlug = category.ifBlank { "phim-moi-cap-nhat" }
                        append("$baseUrl/danh-sach/$catSlug?page=$page&limit=24")
                        if (genre.isNotBlank()) append("&category=$genre")
                        if (country.isNotBlank()) append("&country=$country")
                        if (year.isNotBlank()) append("&year=$year")
                    }
                }
            }
            else -> {
                val baseUrl = "https://vsmov.com/api"
                buildString {
                    if (keyword.isNotBlank()) {
                        val encKw = URLEncoder.encode(keyword.trim(), "UTF-8")
                        append("$baseUrl/tim-kiem?keyword=$encKw&page=$page")
                        if (country.isNotBlank()) append("&country=$country")
                        if (year.isNotBlank()) append("&year=$year")
                    } else if (genre.isNotBlank()) {
                        append("$baseUrl/the-loai/$genre?page=$page")
                        if (country.isNotBlank()) append("&country=$country")
                        if (year.isNotBlank()) append("&year=$year")
                    } else if (country.isNotBlank()) {
                        append("$baseUrl/quoc-gia/$country?page=$page")
                        if (year.isNotBlank()) append("&year=$year")
                    } else if (year.isNotBlank()) {
                        append("$baseUrl/nam/$year?page=$page")
                    } else if (category.isNotBlank()) {
                        if (category in listOf("hoat-hinh", "co-trang")) {
                            append("$baseUrl/the-loai/$category?page=$page")
                        } else {
                            append("$baseUrl/danh-sach/$category?page=$page")
                        }
                    } else {
                        append("$baseUrl/danh-sach/phim-moi-cap-nhat?page=$page")
                    }
                }
            }
        }

        val jsonStr = fetchJson(url, referer) ?: return emptyList<MovieItem>() to 1
        val items = parseMovieList(jsonStr, source)
        val totalPages = parseTotalPages(jsonStr)
        return items to totalPages
    }

    // =========================================================================
    // 4. SEARCH (COMBINED KKPHIM #kk + MSMOV #msv)
    // =========================================================================

    private suspend fun searchMoviesSingle(source: MovieSource, keyword: String, page: Int = 1): List<MovieItem> {
        val enc = URLEncoder.encode(keyword.trim(), "UTF-8")
        val url = when (source) {
            MovieSource.KKPhim -> "https://phimapi.com/v1/api/tim-kiem?keyword=$enc&page=$page&limit=24"
            else -> "https://vsmov.com/api/tim-kiem?keyword=$enc&page=$page"
        }
        val referer = if (source == MovieSource.VSMov) "https://vsmov.com/" else "https://phimapi.com/"
        val jsonStr = fetchJson(url, referer) ?: return emptyList()
        return parseMovieList(jsonStr, source)
    }

    suspend fun searchMovies(source: MovieSource, keyword: String, page: Int = 1): List<MovieItem> = coroutineScope {
        if (source.isAv) return@coroutineScope com.tmplayer.av.AvRepository.filterAv(source, "", keyword, page).first
        val kkDeferred = async { searchMoviesSingle(MovieSource.KKPhim, keyword, page) }
        val msvDeferred = async { searchMoviesSingle(MovieSource.VSMov, keyword, page) }
        val kkList = kkDeferred.await()
        val msvList = msvDeferred.await()
        mergeMovieLists(kkList, msvList)
    }

    private fun normalizeTitle(raw: String): String {
        if (raw.isBlank()) return ""
        val nfd = Normalizer.normalize(raw.lowercase().trim(), Normalizer.Form.NFD)
        return nfd
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .replace('đ', 'd')
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
    }

    private fun isSameMovie(a: MovieItem, b: MovieItem): Boolean {
        if (a.slug.isNotBlank() && a.slug.equals(b.slug, ignoreCase = true)) return true
        val normNameA = normalizeTitle(a.name)
        val normNameB = normalizeTitle(b.name)
        val nameMatches = normNameA.isNotBlank() && normNameA == normNameB
        val normOrigA = normalizeTitle(a.originName)
        val normOrigB = normalizeTitle(b.originName)
        val origMatches = normOrigA.isNotBlank() && normOrigA == normOrigB
        if (!nameMatches && !origMatches) return false
        val yearA = a.year.toIntOrNull()
        val yearB = b.year.toIntOrNull()
        if (yearA != null && yearB != null && kotlin.math.abs(yearA - yearB) > 1) return false
        return true
    }

    private fun mergeTwoMovieItems(kkItem: MovieItem, msvItem: MovieItem, primarySlug: String = msvItem.slug.ifBlank { kkItem.slug }): MovieItem {
        return MovieItem(
            name = msvItem.name.ifBlank { kkItem.name },
            originName = msvItem.originName.ifBlank { kkItem.originName },
            slug = primarySlug,
            thumbUrl = msvItem.thumbUrl.ifBlank { kkItem.thumbUrl },
            posterUrl = msvItem.posterUrl.ifBlank { kkItem.posterUrl },
            year = msvItem.year.ifBlank { kkItem.year },
            quality = msvItem.quality.ifBlank { kkItem.quality },
            lang = kkItem.lang.ifBlank { msvItem.lang },
            type = msvItem.type.ifBlank { kkItem.type },
            episodeCurrent = kkItem.episodeCurrent.ifBlank { msvItem.episodeCurrent },
            rating = msvItem.rating.ifBlank { kkItem.rating },
            source = MovieSource.KKPhim,
            hasBothSources = true,
            kkSlug = kkItem.slug,
            msvSlug = msvItem.slug
        )
    }

    private fun mergeMovieLists(kkList: List<MovieItem>, msvList: List<MovieItem>): List<MovieItem> {
        if (kkList.isEmpty()) return msvList.map { it.copy(msvSlug = it.slug) }
        if (msvList.isEmpty()) return kkList.map { it.copy(kkSlug = it.slug) }

        val matchedKkToMsv = mutableMapOf<Int, Int>()
        val matchedMsvToKk = mutableMapOf<Int, Int>()

        for (kIdx in kkList.indices) {
            val kItem = kkList[kIdx]
            val mIdx = msvList.indices.firstOrNull { mIdxCandidate ->
                !matchedMsvToKk.containsKey(mIdxCandidate) && isSameMovie(kItem, msvList[mIdxCandidate])
            } ?: -1
            if (mIdx >= 0) {
                matchedKkToMsv[kIdx] = mIdx
                matchedMsvToKk[mIdx] = kIdx
            }
        }

        val usedKk = mutableSetOf<Int>()
        val usedMsv = mutableSetOf<Int>()
        val merged = mutableListOf<MovieItem>()
        val maxLen = maxOf(kkList.size, msvList.size)

        for (i in 0 until maxLen) {
            if (i < kkList.size && i !in usedKk) {
                val mIdx = matchedKkToMsv[i]
                if (mIdx != null) {
                    usedKk.add(i)
                    usedMsv.add(mIdx)
                    merged.add(mergeTwoMovieItems(kkList[i], msvList[mIdx], primarySlug = kkList[i].slug))
                } else {
                    usedKk.add(i)
                    merged.add(kkList[i].copy(source = MovieSource.KKPhim, kkSlug = kkList[i].slug))
                }
            }
            if (i < msvList.size && i !in usedMsv) {
                val kIdx = matchedMsvToKk[i]
                if (kIdx != null) {
                    usedMsv.add(i)
                    usedKk.add(kIdx)
                    merged.add(mergeTwoMovieItems(kkList[kIdx], msvList[i], primarySlug = msvList[i].slug))
                } else {
                    usedMsv.add(i)
                    merged.add(msvList[i].copy(source = MovieSource.VSMov, msvSlug = msvList[i].slug))
                }
            }
        }
        return merged
    }

    // =========================================================================
    // 5. MOVIE DETAIL (UNIFIED SERVER 1 #kk + SERVER 2 #msv)
    // =========================================================================

    private fun extractServerSubLabel(rawServerName: String): String {
        val s = rawServerName.lowercase()
        return when {
            "lồng tiếng" in s || "long tieng" in s -> "Lồng tiếng"
            "thuyết minh" in s || "thuyet minh" in s -> "Thuyết minh"
            "vietsub" in s || "sub" in s -> "Vietsub"
            else -> ""
        }
    }

    private fun formatUnifiedServerName(
        source: MovieSource,
        rawServerName: String,
        indexInSource: Int,
        totalInSource: Int
    ): String {
        val basePrefix = if (source == MovieSource.KKPhim) "Server 1 #kk" else "Server 2 #msv"
        val subLabel = extractServerSubLabel(rawServerName)
        return when {
            totalInSource <= 1 -> {
                if (subLabel.isNotBlank() && subLabel != "Vietsub") {
                    "$basePrefix ($subLabel)"
                } else {
                    basePrefix
                }
            }
            else -> {
                val detailLabel = subLabel.ifBlank {
                    rawServerName
                        .replace("#", "")
                        .replace(Regex("(?i)server\\s*\\d*"), "")
                        .trim()
                        .ifBlank { "Bản ${indexInSource + 1}" }
                }
                "$basePrefix ($detailLabel)"
            }
        }
    }

    private suspend fun fetchSingleSourceDetail(source: MovieSource, slug: String): MovieDetail? {
        if (slug.isBlank()) return null
        val url = when (source) {
            MovieSource.KKPhim -> "https://phimapi.com/phim/$slug"
            else -> "https://vsmov.com/api/phim/$slug"
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
            val resolvedSlug = mvObj.optString("slug", slug).ifBlank { slug }

            val item = MovieItem(
                name = mvObj.optString("name", "Phim"),
                originName = mvObj.optString("origin_name", ""),
                slug = resolvedSlug,
                thumbUrl = thumb,
                posterUrl = poster,
                year = mvObj.optString("year", ""),
                quality = mvObj.optString("quality", "HD"),
                lang = mvObj.optString("lang", "Vietsub"),
                type = mvObj.optString("type", ""),
                episodeCurrent = mvObj.optString("episode_current", ""),
                rating = rating,
                source = source,
                kkSlug = if (source == MovieSource.KKPhim) resolvedSlug else "",
                msvSlug = if (source == MovieSource.VSMov) resolvedSlug else ""
            )

            val directors = parseStringList(mvObj.optJSONArray("director"))
            val actors = parseStringList(mvObj.optJSONArray("actor"))
            val cats = parseNameList(mvObj.optJSONArray("category"))
            val countries = parseNameList(mvObj.optJSONArray("country"))

            val rawServers = mutableListOf<Pair<String, List<MovieEpisode>>>()
            val epArr = root.optJSONArray("episodes")
            if (epArr != null) {
                for (sIdx in 0 until epArr.length()) {
                    val sObj = epArr.optJSONObject(sIdx) ?: continue
                    val sName = sObj.optString("server_name", "Server ${sIdx + 1}").replace(Regex("\\s+"), " ").trim()
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
                        rawServers.add(sName to eps)
                    }
                }
            }

            val servers = rawServers.mapIndexed { idx, (rawName, eps) ->
                MovieServer(
                    serverName = formatUnifiedServerName(source, rawName, idx, rawServers.size),
                    episodes = eps,
                    source = source,
                    sourceSlug = resolvedSlug
                )
            }

            val cleanContent = mvObj.optString("content", "")
                .replace(Regex("<[^>]*>"), "")
                .replace("&quot;", "\"")
                .replace("&#039;", "'")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .trim()

            MovieDetail(
                movie = item,
                content = cleanContent,
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

    suspend fun getMovieDetail(
        source: MovieSource,
        slug: String,
        hintItem: MovieItem? = null
    ): MovieDetail? = coroutineScope {
        if (source.isAv) return@coroutineScope com.tmplayer.av.AvRepository.getDetail(source, slug)

        val kkSlugCandidate = hintItem?.kkSlug?.takeIf { it.isNotBlank() } ?: slug
        val msvSlugCandidate = hintItem?.msvSlug?.takeIf { it.isNotBlank() } ?: slug

        val kkDeferred = async { fetchSingleSourceDetail(MovieSource.KKPhim, kkSlugCandidate) }
        val msvDeferred = async { fetchSingleSourceDetail(MovieSource.VSMov, msvSlugCandidate) }

        var kkDetail = kkDeferred.await()
        var msvDetail = msvDeferred.await()

        // Nếu 1 trong 2 nguồn khác slug, tự động tìm nhanh theo tên phim để gộp đủ cả 2 server (#kk & #msv)
        if (kkDetail == null && msvDetail != null) {
            val targetMovie = msvDetail.movie
            val candidates = searchMoviesSingle(MovieSource.KKPhim, targetMovie.name, 1)
            val matched = candidates.firstOrNull { isSameMovie(it, targetMovie) }
            if (matched != null && matched.slug.isNotBlank() && matched.slug != kkSlugCandidate) {
                kkDetail = fetchSingleSourceDetail(MovieSource.KKPhim, matched.slug)
            }
        } else if (msvDetail == null && kkDetail != null) {
            val targetMovie = kkDetail.movie
            val candidates = searchMoviesSingle(MovieSource.VSMov, targetMovie.name, 1)
            val matched = candidates.firstOrNull { isSameMovie(it, targetMovie) }
            if (matched != null && matched.slug.isNotBlank() && matched.slug != msvSlugCandidate) {
                msvDetail = fetchSingleSourceDetail(MovieSource.VSMov, matched.slug)
            }
        }

        if (kkDetail == null && msvDetail == null) return@coroutineScope null

        // Server 1 ưu tiên là KKPhim (#kk), Server 2 là MSMov (#msv)
        val combinedServers = buildList {
            if (kkDetail != null) addAll(kkDetail.servers)
            if (msvDetail != null) addAll(msvDetail.servers)
        }

        val hasBoth = kkDetail != null && msvDetail != null
        val primaryDetail = msvDetail ?: kkDetail!!
        val secondaryDetail = if (primaryDetail === msvDetail) kkDetail else msvDetail

        val mergedMovie = MovieItem(
            name = primaryDetail.movie.name.ifBlank { secondaryDetail?.movie?.name.orEmpty() },
            originName = primaryDetail.movie.originName.ifBlank { secondaryDetail?.movie?.originName.orEmpty() },
            slug = slug.ifBlank { primaryDetail.movie.slug },
            thumbUrl = primaryDetail.movie.thumbUrl.ifBlank { secondaryDetail?.movie?.thumbUrl.orEmpty() },
            posterUrl = primaryDetail.movie.posterUrl.ifBlank { secondaryDetail?.movie?.posterUrl.orEmpty() },
            year = primaryDetail.movie.year.ifBlank { secondaryDetail?.movie?.year.orEmpty() },
            quality = primaryDetail.movie.quality.ifBlank { secondaryDetail?.movie?.quality ?: "HD" },
            lang = (kkDetail?.movie?.lang ?: primaryDetail.movie.lang).ifBlank { "Vietsub" },
            type = primaryDetail.movie.type.ifBlank { secondaryDetail?.movie?.type.orEmpty() },
            episodeCurrent = (kkDetail?.movie?.episodeCurrent ?: primaryDetail.movie.episodeCurrent)
                .ifBlank { secondaryDetail?.movie?.episodeCurrent.orEmpty() },
            rating = primaryDetail.movie.rating.ifBlank { secondaryDetail?.movie?.rating.orEmpty() },
            source = if (kkDetail != null) MovieSource.KKPhim else MovieSource.VSMov,
            hasBothSources = hasBoth,
            kkSlug = kkDetail?.movie?.slug.orEmpty(),
            msvSlug = msvDetail?.movie?.slug.orEmpty()
        )

        MovieDetail(
            movie = mergedMovie,
            content = primaryDetail.content.ifBlank { secondaryDetail?.content.orEmpty() },
            time = primaryDetail.time.ifBlank { secondaryDetail?.time.orEmpty() },
            episodeTotal = primaryDetail.episodeTotal.ifBlank { secondaryDetail?.episodeTotal.orEmpty() },
            director = primaryDetail.director.ifEmpty { secondaryDetail?.director.orEmpty() },
            actor = primaryDetail.actor.ifEmpty { secondaryDetail?.actor.orEmpty() },
            categories = primaryDetail.categories.ifEmpty { secondaryDetail?.categories.orEmpty() },
            countries = primaryDetail.countries.ifEmpty { secondaryDetail?.countries.orEmpty() },
            servers = combinedServers
        )
    }

    // =========================================================================
    // 6. STREAM RESOLVER
    // =========================================================================

    private val subtitleCache = java.util.concurrent.ConcurrentHashMap<String, List<MovieSubtitleTrack>>()

    fun resolveStream(source: MovieSource, movieTitle: String, server: MovieServer, episode: MovieEpisode): MovieStream {
        val actualSource = if (source.isAv) source else server.source
        val referer = if (actualSource == MovieSource.VSMov) "https://vsmov.com/" else "https://phimapi.com/"
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
            source = actualSource
        )
    }

    suspend fun resolveStreamWithSubtitles(
        source: MovieSource,
        movieTitle: String,
        server: MovieServer,
        episode: MovieEpisode
    ): MovieStream = withContext(Dispatchers.IO) {
        if (source.isAv) {
            return@withContext com.tmplayer.av.AvRepository.resolveAvStream(source, movieTitle, server, episode)
        }
        val actualSource = server.source
        val baseStream = resolveStream(actualSource, movieTitle, server, episode)
        if (actualSource != MovieSource.VSMov || episode.linkEmbed.isBlank()) {
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

    suspend fun getNormalizedSubtitleUri(context: Context, subUrl: String): Uri = withContext(Dispatchers.IO) {
        if (subUrl.isBlank()) return@withContext Uri.EMPTY
        try {
            val cacheDir = File(context.cacheDir, "movie_subs").apply { if (!exists()) mkdirs() }
            val hash = md5(subUrl)
            val file = File(cacheDir, "$hash.vtt")
            if (file.exists() && file.length() > 10L) {
                return@withContext Uri.fromFile(file)
            }

            val rawText = fetchJson(subUrl, "https://vsmov.com/")
                ?: fetchJson(subUrl, "https://vsmov.com/")
                ?: return@withContext Uri.parse(subUrl)
            // Normalize WebVTT:
            // 1. Strip UTF-8 BOM and leading whitespace, then ensure WEBVTT header
            val trimmed = rawText.trimStart('\uFEFF', ' ', '\t', '\r', '\n')
            val header = if (trimmed.startsWith("WEBVTT")) "" else "WEBVTT\n\n"
            // 2. Fix comma in timestamps: 00:01:30,780 --> 00:01:34,371 => 00:01:30.780 --> 00:01:34.371
            val fixedText = header + trimmed.replace(Regex("""(\d{2}:\d{2}(?::\d{2})?),(\d{3})"""), "$1.$2")
            file.writeText(fixedText, Charsets.UTF_8)
            Uri.fromFile(file)
        } catch (e: Exception) {
            Uri.parse(subUrl)
        }
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
