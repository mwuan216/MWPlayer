package com.tmplayer.movie

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

@Immutable
data class MovieWatchProgress(
    val slug: String,
    val name: String,
    val originName: String = "",
    val thumbUrl: String = "",
    val posterUrl: String = "",
    val year: String = "",
    val quality: String = "HD",
    val lang: String = "Vietsub",
    val source: String = "kkphim",
    val serverIndex: Int = 0,
    val episodeIndex: Int = 0,
    val episodeName: String = "",
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val updatedAt: Long = System.currentTimeMillis()
) {
    val progressFraction: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    val isFinished: Boolean
        get() = progressFraction >= 0.92f || (durationMs > 0 && durationMs - positionMs < 30_000L)

    fun toMovieItem(): MovieItem = MovieItem(
        name = name,
        originName = originName,
        slug = slug,
        thumbUrl = thumbUrl,
        posterUrl = posterUrl,
        year = year,
        quality = quality,
        lang = lang,
        source = MovieSource.fromString(source)
    )

    fun toJson(): JSONObject = JSONObject().apply {
        put("slug", slug)
        put("name", name)
        put("originName", originName)
        put("thumbUrl", thumbUrl)
        put("posterUrl", posterUrl)
        put("year", year)
        put("quality", quality)
        put("lang", lang)
        put("source", source)
        put("serverIndex", serverIndex)
        put("episodeIndex", episodeIndex)
        put("episodeName", episodeName)
        put("positionMs", positionMs)
        put("durationMs", durationMs)
        put("updatedAt", updatedAt)
    }

    companion object {
        fun fromJson(json: JSONObject): MovieWatchProgress? {
            val slug = json.optString("slug").takeIf { it.isNotBlank() } ?: return null
            val name = json.optString("name").takeIf { it.isNotBlank() } ?: return null
            return MovieWatchProgress(
                slug = slug,
                name = name,
                originName = json.optString("originName"),
                thumbUrl = json.optString("thumbUrl"),
                posterUrl = json.optString("posterUrl"),
                year = json.optString("year"),
                quality = json.optString("quality", "HD"),
                lang = json.optString("lang", "Vietsub"),
                source = json.optString("source", "kkphim"),
                serverIndex = json.optInt("serverIndex", 0),
                episodeIndex = json.optInt("episodeIndex", 0),
                episodeName = json.optString("episodeName"),
                positionMs = json.optLong("positionMs", 0L),
                durationMs = json.optLong("durationMs", 0L),
                updatedAt = json.optLong("updatedAt", System.currentTimeMillis())
            )
        }
    }
}

@Immutable
data class CloudSyncedMovie(
    val slug: String,
    val name: String,
    val originName: String = "",
    val thumbUrl: String = "",
    val posterUrl: String = "",
    val year: String = "",
    val quality: String = "HD",
    val lang: String = "Vietsub",
    val source: String = "vsmov",
    val pulledUpToEpisode: Int = 0,
    val pulledEpisodesText: String = "",
    val latestKnownEpisodes: Int = 0,
    val autoPullNewEpisodes: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
) {
    val hasNewEpisodes: Boolean
        get() = latestKnownEpisodes > pulledUpToEpisode && pulledUpToEpisode > 0

    val newEpisodesCount: Int
        get() = (latestKnownEpisodes - pulledUpToEpisode).coerceAtLeast(0)

    val newEpisodeRange: String
        get() = if (latestKnownEpisodes <= pulledUpToEpisode + 1) "$latestKnownEpisodes" else "${pulledUpToEpisode + 1}-$latestKnownEpisodes"

    fun toMovieItem(): MovieItem = MovieItem(
        name = name,
        originName = originName,
        slug = slug,
        thumbUrl = thumbUrl,
        posterUrl = posterUrl,
        year = year,
        quality = quality,
        lang = lang,
        episodeCurrent = if (hasNewEpisodes) "🔔 +$newEpisodesCount tập mới" else "☁️ Đã kéo $pulledUpToEpisode tập",
        source = MovieSource.fromString(source)
    )

    fun toJson(): JSONObject = JSONObject().apply {
        put("slug", slug)
        put("name", name)
        put("originName", originName)
        put("thumbUrl", thumbUrl)
        put("posterUrl", posterUrl)
        put("year", year)
        put("quality", quality)
        put("lang", lang)
        put("source", source)
        put("pulledUpToEpisode", pulledUpToEpisode)
        put("pulledEpisodesText", pulledEpisodesText)
        put("latestKnownEpisodes", latestKnownEpisodes)
        put("autoPullNewEpisodes", autoPullNewEpisodes)
        put("updatedAt", updatedAt)
    }

    companion object {
        fun fromJson(json: JSONObject): CloudSyncedMovie? {
            val slug = json.optString("slug").takeIf { it.isNotBlank() } ?: return null
            val name = json.optString("name").takeIf { it.isNotBlank() } ?: return null
            return CloudSyncedMovie(
                slug = slug,
                name = name,
                originName = json.optString("originName"),
                thumbUrl = json.optString("thumbUrl"),
                posterUrl = json.optString("posterUrl"),
                year = json.optString("year"),
                quality = json.optString("quality", "HD"),
                lang = json.optString("lang", "Vietsub"),
                source = json.optString("source", "vsmov"),
                pulledUpToEpisode = json.optInt("pulledUpToEpisode", 0),
                pulledEpisodesText = json.optString("pulledEpisodesText", ""),
                latestKnownEpisodes = json.optInt("latestKnownEpisodes", 0),
                autoPullNewEpisodes = json.optBoolean("autoPullNewEpisodes", true),
                updatedAt = json.optLong("updatedAt", System.currentTimeMillis())
            )
        }
    }
}

@Immutable
data class CloudDestination(
    val chatId: Long,
    val topicId: Int = 0,
    val chatTitle: String,
    val topicTitle: String = ""
) {
    val displayLabel: String
        get() = if (topicId > 0 && topicTitle.isNotBlank()) {
            "$chatTitle › $topicTitle"
        } else if (topicId > 0) {
            "$chatTitle (#$topicId)"
        } else {
            chatTitle
        }

    val isDefault: Boolean
        get() = chatId == MovieCloudDownloader.CLOUD_CHAT_ID && topicId == MovieCloudDownloader.CLOUD_TOPIC_ID

    companion object {
        val DEFAULT = CloudDestination(
            chatId = MovieCloudDownloader.CLOUD_CHAT_ID,
            topicId = MovieCloudDownloader.CLOUD_TOPIC_ID,
            chatTitle = "MovieBox",
            topicTitle = "#bot"
        )
    }
}

/**
 * Trình quản lý danh sách phim (Theo dõi / Yêu thích, Đang xem dở, Lịch sử xem gần đây, Đã kéo về Tele Cloud)
 * Lưu trữ bằng SharedPreferences JSON, tự động đồng bộ StateFlow tức thì
 */
object MovieStore {
    private const val PREFS_NAME = "mwplayer_movies_prefs"
    private const val KEY_WATCHLIST = "movie_watchlist"
    private const val KEY_CONTINUE = "movie_continue"
    private const val KEY_HISTORY = "movie_history"
    private const val KEY_CLOUD_SYNCED = "movie_cloud_synced"
    private const val KEY_CLOUD_DEST_CHAT_ID = "movie_cloud_dest_chat_id"
    private const val KEY_CLOUD_DEST_TOPIC_ID = "movie_cloud_dest_topic_id"
    private const val KEY_CLOUD_DEST_CHAT_TITLE = "movie_cloud_dest_chat_title"
    private const val KEY_CLOUD_DEST_TOPIC_TITLE = "movie_cloud_dest_topic_title"
    private const val KEY_SELECTED_BOT = "movie_selected_bot"
    private const val KEY_PULL_FORMAT = "movie_pull_format"

    private val _watchlistFlow = MutableStateFlow<List<MovieItem>>(emptyList())
    val watchlistFlow: StateFlow<List<MovieItem>> = _watchlistFlow.asStateFlow()

    private val _continueWatchingFlow = MutableStateFlow<List<MovieWatchProgress>>(emptyList())
    val continueWatchingFlow: StateFlow<List<MovieWatchProgress>> = _continueWatchingFlow.asStateFlow()

    private val _historyFlow = MutableStateFlow<List<MovieWatchProgress>>(emptyList())
    val historyFlow: StateFlow<List<MovieWatchProgress>> = _historyFlow.asStateFlow()

    private val _cloudSyncedFlow = MutableStateFlow<List<CloudSyncedMovie>>(emptyList())
    val cloudSyncedFlow: StateFlow<List<CloudSyncedMovie>> = _cloudSyncedFlow.asStateFlow()

    private val _cloudDestinationFlow = MutableStateFlow(CloudDestination.DEFAULT)
    val cloudDestinationFlow: StateFlow<CloudDestination> = _cloudDestinationFlow.asStateFlow()

    private val _selectedBotFlow = MutableStateFlow(MovieCloudDownloader.BOT_1)
    val selectedBotFlow: StateFlow<String> = _selectedBotFlow.asStateFlow()

    private val _pullFormatFlow = MutableStateFlow("token")
    val pullFormatFlow: StateFlow<String> = _pullFormatFlow.asStateFlow()

    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        loadAll(context)
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    @Synchronized
    private fun loadAll(context: Context) {
        val prefs = getPrefs(context)
        // 1. Watchlist
        try {
            val wlRaw = prefs.getString(KEY_WATCHLIST, null)
            if (!wlRaw.isNullOrBlank()) {
                val array = JSONArray(wlRaw)
                val list = mutableListOf<MovieItem>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        MovieItem(
                            name = obj.getString("name"),
                            originName = obj.optString("originName"),
                            slug = obj.getString("slug"),
                            thumbUrl = obj.optString("thumbUrl"),
                            posterUrl = obj.optString("posterUrl"),
                            year = obj.optString("year"),
                            quality = obj.optString("quality", "HD"),
                            lang = obj.optString("lang", "Vietsub"),
                            source = MovieSource.fromString(obj.optString("source", "kkphim"))
                        )
                    )
                }
                _watchlistFlow.value = list
            }
        } catch (_: Exception) {}

        // 2. Continue Watching
        try {
            val cwRaw = prefs.getString(KEY_CONTINUE, null)
            if (!cwRaw.isNullOrBlank()) {
                val array = JSONArray(cwRaw)
                val list = mutableListOf<MovieWatchProgress>()
                for (i in 0 until array.length()) {
                    MovieWatchProgress.fromJson(array.getJSONObject(i))?.let { list.add(it) }
                }
                _continueWatchingFlow.value = list.sortedByDescending { it.updatedAt }
            }
        } catch (_: Exception) {}

        // 3. History
        try {
            val histRaw = prefs.getString(KEY_HISTORY, null)
            if (!histRaw.isNullOrBlank()) {
                val array = JSONArray(histRaw)
                val list = mutableListOf<MovieWatchProgress>()
                for (i in 0 until array.length()) {
                    MovieWatchProgress.fromJson(array.getJSONObject(i))?.let { list.add(it) }
                }
                _historyFlow.value = list.sortedByDescending { it.updatedAt }.take(50)
            }
        } catch (_: Exception) {}

        // 4. Cloud Synced Movies
        try {
            val cloudRaw = prefs.getString(KEY_CLOUD_SYNCED, null)
            if (!cloudRaw.isNullOrBlank()) {
                val array = JSONArray(cloudRaw)
                val list = mutableListOf<CloudSyncedMovie>()
                for (i in 0 until array.length()) {
                    CloudSyncedMovie.fromJson(array.getJSONObject(i))?.let { list.add(it) }
                }
                _cloudSyncedFlow.value = list.sortedWith(
                    compareByDescending<CloudSyncedMovie> { it.hasNewEpisodes }.thenByDescending { it.updatedAt }
                )
            }
        } catch (_: Exception) {}

        // 5. Cloud Destination
        try {
            if (prefs.contains(KEY_CLOUD_DEST_CHAT_ID)) {
                val chatId = prefs.getLong(KEY_CLOUD_DEST_CHAT_ID, MovieCloudDownloader.CLOUD_CHAT_ID)
                var topicId = prefs.getInt(KEY_CLOUD_DEST_TOPIC_ID, MovieCloudDownloader.CLOUD_TOPIC_ID)
                var chatTitle = prefs.getString(KEY_CLOUD_DEST_CHAT_TITLE, "MovieBox").orEmpty().ifBlank { "MovieBox" }
                var topicTitle = prefs.getString(KEY_CLOUD_DEST_TOPIC_TITLE, "").orEmpty()
                if (topicId == 43264) {
                    topicId = MovieCloudDownloader.CLOUD_TOPIC_ID
                    topicTitle = "#bot"
                    chatTitle = "MovieBox"
                    prefs.edit()
                        .putInt(KEY_CLOUD_DEST_TOPIC_ID, topicId)
                        .putString(KEY_CLOUD_DEST_TOPIC_TITLE, topicTitle)
                        .putString(KEY_CLOUD_DEST_CHAT_TITLE, chatTitle)
                        .apply()
                }
                _cloudDestinationFlow.value = CloudDestination(
                    chatId = chatId,
                    topicId = topicId,
                    chatTitle = chatTitle,
                    topicTitle = topicTitle
                )
            } else {
                _cloudDestinationFlow.value = CloudDestination.DEFAULT
            }

            // 6. Selected Bot & Pull Format
            val bot = prefs.getString(KEY_SELECTED_BOT, MovieCloudDownloader.BOT_1) ?: MovieCloudDownloader.BOT_1
            _selectedBotFlow.value = bot
            val fmt = prefs.getString(KEY_PULL_FORMAT, "token") ?: "token"
            _pullFormatFlow.value = fmt
        } catch (_: Exception) {}
    }

    @Synchronized
    fun setSelectedBot(context: Context, bot: String) {
        init(context)
        _selectedBotFlow.value = bot
        getPrefs(context).edit().putString(KEY_SELECTED_BOT, bot).apply()
    }

    @Synchronized
    fun setPullFormat(context: Context, format: String) {
        init(context)
        _pullFormatFlow.value = format
        getPrefs(context).edit().putString(KEY_PULL_FORMAT, format).apply()
    }

    @Synchronized
    fun setCloudDestination(context: Context, destination: CloudDestination) {
        init(context)
        _cloudDestinationFlow.value = destination
        getPrefs(context).edit()
            .putLong(KEY_CLOUD_DEST_CHAT_ID, destination.chatId)
            .putInt(KEY_CLOUD_DEST_TOPIC_ID, destination.topicId)
            .putString(KEY_CLOUD_DEST_CHAT_TITLE, destination.chatTitle)
            .putString(KEY_CLOUD_DEST_TOPIC_TITLE, destination.topicTitle)
            .apply()
    }

    @Synchronized
    fun resetCloudDestination(context: Context) {
        setCloudDestination(context, CloudDestination.DEFAULT)
    }

    // =========================================================================
    // WATCHLIST (THEO DÕI / YÊU THÍCH)
    // =========================================================================

    fun isWatchlist(slug: String): Boolean {
        return _watchlistFlow.value.any { it.slug == slug }
    }

    @Synchronized
    fun toggleWatchlist(context: Context, movie: MovieItem): Boolean {
        init(context)
        val current = _watchlistFlow.value.toMutableList()
        val index = current.indexOfFirst { it.slug == movie.slug }
        val isAdded: Boolean
        if (index >= 0) {
            current.removeAt(index)
            isAdded = false
        } else {
            current.add(0, movie)
            isAdded = true
        }
        _watchlistFlow.value = current
        saveWatchlist(context, current)
        return isAdded
    }

    @Synchronized
    fun removeFromWatchlist(context: Context, slug: String) {
        init(context)
        val current = _watchlistFlow.value.toMutableList()
        if (current.removeAll { it.slug == slug }) {
            _watchlistFlow.value = current
            saveWatchlist(context, current)
        }
    }

    private fun saveWatchlist(context: Context, list: List<MovieItem>) {
        try {
            val array = JSONArray()
            list.forEach { m ->
                val obj = JSONObject().apply {
                    put("name", m.name)
                    put("originName", m.originName)
                    put("slug", m.slug)
                    put("thumbUrl", m.thumbUrl)
                    put("posterUrl", m.posterUrl)
                    put("year", m.year)
                    put("quality", m.quality)
                    put("lang", m.lang)
                    put("source", m.source.name.lowercase())
                }
                array.put(obj)
            }
            getPrefs(context).edit().putString(KEY_WATCHLIST, array.toString()).apply()
        } catch (_: Exception) {}
    }

    // =========================================================================
    // CONTINUE WATCHING & HISTORY
    // =========================================================================

    fun getProgress(slug: String): MovieWatchProgress? {
        return _continueWatchingFlow.value.firstOrNull { it.slug == slug }
            ?: _historyFlow.value.firstOrNull { it.slug == slug }
    }

    @Synchronized
    fun saveProgress(
        context: Context,
        detail: MovieDetail,
        serverIndex: Int,
        episodeIndex: Int,
        episodeName: String,
        positionMs: Long,
        durationMs: Long
    ) {
        init(context)
        val m = detail.movie
        val record = MovieWatchProgress(
            slug = m.slug,
            name = m.name,
            originName = m.originName,
            thumbUrl = m.thumbUrl,
            posterUrl = m.posterUrl,
            year = m.year,
            quality = m.quality,
            lang = m.lang,
            source = m.source.name.lowercase(),
            serverIndex = serverIndex,
            episodeIndex = episodeIndex,
            episodeName = episodeName,
            positionMs = positionMs,
            durationMs = durationMs,
            updatedAt = System.currentTimeMillis()
        )

        // 1. Update History (Always record recent watches, capped at 50)
        val histList = _historyFlow.value.toMutableList()
        histList.removeAll { it.slug == record.slug }
        histList.add(0, record)
        val cappedHist = histList.take(50)
        _historyFlow.value = cappedHist
        saveHistory(context, cappedHist)

        // 2. Update Continue Watching:
        // If finished (e.g. >92% progress) or watched less than 5s, remove from continue watching
        val contList = _continueWatchingFlow.value.toMutableList()
        contList.removeAll { it.slug == record.slug }
        if (!record.isFinished && positionMs >= 5000L) {
            contList.add(0, record)
        }
        val cappedCont = contList.take(50)
        _continueWatchingFlow.value = cappedCont
        saveContinue(context, cappedCont)
    }

    @Synchronized
    fun removeContinueWatching(context: Context, slug: String) {
        init(context)
        val contList = _continueWatchingFlow.value.toMutableList()
        if (contList.removeAll { it.slug == slug }) {
            _continueWatchingFlow.value = contList
            saveContinue(context, contList)
        }
    }

    @Synchronized
    fun removeFromHistory(context: Context, slug: String) {
        init(context)
        val histList = _historyFlow.value.toMutableList()
        if (histList.removeAll { it.slug == slug }) {
            _historyFlow.value = histList
            saveHistory(context, histList)
        }
        val contList = _continueWatchingFlow.value.toMutableList()
        if (contList.removeAll { it.slug == slug }) {
            _continueWatchingFlow.value = contList
            saveContinue(context, contList)
        }
    }

    @Synchronized
    fun removeMultipleFromHistory(context: Context, slugs: Set<String>) {
        if (slugs.isEmpty()) return
        init(context)
        val histList = _historyFlow.value.toMutableList()
        if (histList.removeAll { it.slug in slugs }) {
            _historyFlow.value = histList
            saveHistory(context, histList)
        }
        val contList = _continueWatchingFlow.value.toMutableList()
        if (contList.removeAll { it.slug in slugs }) {
            _continueWatchingFlow.value = contList
            saveContinue(context, contList)
        }
    }

    @Synchronized
    fun clearHistory(context: Context) {
        init(context)
        _historyFlow.value = emptyList()
        _continueWatchingFlow.value = emptyList()
        getPrefs(context).edit().remove(KEY_HISTORY).remove(KEY_CONTINUE).apply()
    }

    private fun saveContinue(context: Context, list: List<MovieWatchProgress>) {
        try {
            val array = JSONArray()
            list.forEach { array.put(it.toJson()) }
            getPrefs(context).edit().putString(KEY_CONTINUE, array.toString()).apply()
        } catch (_: Exception) {}
    }

    private fun saveHistory(context: Context, list: List<MovieWatchProgress>) {
        try {
            val array = JSONArray()
            list.forEach { array.put(it.toJson()) }
            getPrefs(context).edit().putString(KEY_HISTORY, array.toString()).apply()
        } catch (_: Exception) {}
    }

    // =========================================================================
    // CLOUD SYNCED MOVIES (TRACKING PHIM ĐÃ KÉO VỀ TELE CLOUD & TẬP MỚI)
    // =========================================================================

    fun getCloudSynced(slug: String): CloudSyncedMovie? {
        return _cloudSyncedFlow.value.firstOrNull { it.slug == slug }
    }

    fun parseMaxEpisodeFromRange(epRange: String, totalEpisodes: Int, previousPulled: Int): Int {
        val trimmed = epRange.trim().lowercase()
        if (trimmed == "all" || trimmed == "full" || trimmed == "remaining") {
            return maxOf(totalEpisodes, previousPulled, 1)
        }
        val nums = Regex("""\d+""").findAll(trimmed).mapNotNull { it.value.toIntOrNull() }.toList()
        val maxInRange = nums.maxOrNull() ?: totalEpisodes
        return maxOf(maxInRange, previousPulled)
    }

    @Synchronized
    fun markCloudSynced(
        context: Context,
        movie: MovieItem,
        epRange: String,
        totalEpisodes: Int,
        autoPull: Boolean? = null
    ): CloudSyncedMovie {
        init(context)
        val currentList = _cloudSyncedFlow.value.toMutableList()
        val existing = currentList.firstOrNull { it.slug == movie.slug }
        val newPulledUpTo = parseMaxEpisodeFromRange(
            epRange = epRange,
            totalEpisodes = totalEpisodes,
            previousPulled = existing?.pulledUpToEpisode ?: 0
        )
        val latestKnown = maxOf(totalEpisodes, existing?.latestKnownEpisodes ?: 0, newPulledUpTo)
        val rangeText = when (epRange.trim().lowercase()) {
            "all", "full", "remaining" -> if (newPulledUpTo > 1) "1-$newPulledUpTo" else "Full"
            else -> if (newPulledUpTo > 1) "1-$newPulledUpTo" else epRange.trim()
        }
        val updated = CloudSyncedMovie(
            slug = movie.slug,
            name = movie.name,
            originName = movie.originName.ifBlank { existing?.originName ?: "" },
            thumbUrl = movie.thumbUrl.ifBlank { existing?.thumbUrl ?: "" },
            posterUrl = movie.posterUrl.ifBlank { existing?.posterUrl ?: "" },
            year = movie.year.ifBlank { existing?.year ?: "" },
            quality = movie.quality.ifBlank { existing?.quality ?: "HD" },
            lang = movie.lang.ifBlank { existing?.lang ?: "Vietsub" },
            source = movie.source.name.lowercase(),
            pulledUpToEpisode = newPulledUpTo,
            pulledEpisodesText = rangeText,
            latestKnownEpisodes = latestKnown,
            autoPullNewEpisodes = autoPull ?: (existing?.autoPullNewEpisodes ?: true),
            updatedAt = System.currentTimeMillis()
        )
        currentList.removeAll { it.slug == movie.slug }
        currentList.add(0, updated)
        val sorted = currentList.sortedWith(
            compareByDescending<CloudSyncedMovie> { it.hasNewEpisodes }.thenByDescending { it.updatedAt }
        )
        _cloudSyncedFlow.value = sorted
        saveCloudSynced(context, sorted)
        return updated
    }

    @Synchronized
    fun updateCloudLatestEpisodes(
        context: Context,
        slug: String,
        latestEpisodes: Int,
        newPulledUpTo: Int? = null
    ): CloudSyncedMovie? {
        init(context)
        val currentList = _cloudSyncedFlow.value.toMutableList()
        val idx = currentList.indexOfFirst { it.slug == slug }
        if (idx < 0) return null
        val existing = currentList[idx]
        val pulled = newPulledUpTo?.let { maxOf(it, existing.pulledUpToEpisode) } ?: existing.pulledUpToEpisode
        val latest = maxOf(latestEpisodes, existing.latestKnownEpisodes, pulled)
        val updated = existing.copy(
            pulledUpToEpisode = pulled,
            pulledEpisodesText = if (pulled > 1) "1-$pulled" else existing.pulledEpisodesText,
            latestKnownEpisodes = latest,
            updatedAt = if (latest > existing.latestKnownEpisodes || newPulledUpTo != null) System.currentTimeMillis() else existing.updatedAt
        )
        currentList[idx] = updated
        val sorted = currentList.sortedWith(
            compareByDescending<CloudSyncedMovie> { it.hasNewEpisodes }.thenByDescending { it.updatedAt }
        )
        _cloudSyncedFlow.value = sorted
        saveCloudSynced(context, sorted)
        return updated
    }

    @Synchronized
    fun setCloudAutoPull(context: Context, slug: String, autoPull: Boolean) {
        init(context)
        val currentList = _cloudSyncedFlow.value.toMutableList()
        val idx = currentList.indexOfFirst { it.slug == slug }
        if (idx < 0) return
        currentList[idx] = currentList[idx].copy(autoPullNewEpisodes = autoPull)
        _cloudSyncedFlow.value = currentList
        saveCloudSynced(context, currentList)
    }

    @Synchronized
    fun removeCloudSynced(context: Context, slug: String) {
        init(context)
        val currentList = _cloudSyncedFlow.value.toMutableList()
        if (currentList.removeAll { it.slug == slug }) {
            _cloudSyncedFlow.value = currentList
            saveCloudSynced(context, currentList)
        }
    }

    private fun saveCloudSynced(context: Context, list: List<CloudSyncedMovie>) {
        try {
            val array = JSONArray()
            list.forEach { array.put(it.toJson()) }
            getPrefs(context).edit().putString(KEY_CLOUD_SYNCED, array.toString()).apply()
        } catch (_: Exception) {}
    }
}
