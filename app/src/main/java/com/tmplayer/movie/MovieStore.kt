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

/**
 * Trình quản lý danh sách phim (Theo dõi / Yêu thích, Đang xem dở, Lịch sử xem gần đây)
 * Lưu trữ bằng SharedPreferences JSON, tự động đồng bộ StateFlow tức thì
 */
object MovieStore {
    private const val PREFS_NAME = "mwplayer_movies_prefs"
    private const val KEY_WATCHLIST = "movie_watchlist"
    private const val KEY_CONTINUE = "movie_continue"
    private const val KEY_HISTORY = "movie_history"

    private val _watchlistFlow = MutableStateFlow<List<MovieItem>>(emptyList())
    val watchlistFlow: StateFlow<List<MovieItem>> = _watchlistFlow.asStateFlow()

    private val _continueWatchingFlow = MutableStateFlow<List<MovieWatchProgress>>(emptyList())
    val continueWatchingFlow: StateFlow<List<MovieWatchProgress>> = _continueWatchingFlow.asStateFlow()

    private val _historyFlow = MutableStateFlow<List<MovieWatchProgress>>(emptyList())
    val historyFlow: StateFlow<List<MovieWatchProgress>> = _historyFlow.asStateFlow()

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
                _historyFlow.value = list.sortedByDescending { it.updatedAt }
            }
        } catch (_: Exception) {}
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

        // 1. Update History (Always record recent watches, capped at 100)
        val histList = _historyFlow.value.toMutableList()
        histList.removeAll { it.slug == record.slug }
        histList.add(0, record)
        val cappedHist = histList.take(100)
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
    }

    @Synchronized
    fun clearHistory(context: Context) {
        init(context)
        _historyFlow.value = emptyList()
        getPrefs(context).edit().remove(KEY_HISTORY).apply()
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
}
