package com.tmplayer.av

import android.content.Context
import android.content.SharedPreferences
import com.tmplayer.movie.MovieDetail
import com.tmplayer.movie.MovieItem
import com.tmplayer.movie.MovieSource
import com.tmplayer.movie.MovieWatchProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Quản lý riêng biệt cho Tab AV (18+):
 * 1. Bảo mật & Riêng tư: Ẩn/Hiện tab AV trên thanh điều hướng, Mã PIN 4 số khóa truy cập, Khóa nhanh 1 chạm.
 * 2. Lịch sử xem & Danh sách yêu thích riêng biệt (không trộn vào kệ phim gia đình ở tab Movie / Home).
 */
object AvStore {
    private const val PREFS_NAME = "mwplayer_av_prefs"
    private const val KEY_TAB_VISIBLE = "av_tab_visible"
    private const val KEY_PIN_CODE = "av_pin_code"
    private const val KEY_WATCHLIST = "av_watchlist"
    private const val KEY_HISTORY = "av_history"

    private val _isTabVisibleFlow = MutableStateFlow(true)
    val isTabVisibleFlow: StateFlow<Boolean> = _isTabVisibleFlow.asStateFlow()

    private val _pinCodeFlow = MutableStateFlow("")
    val pinCodeFlow: StateFlow<String> = _pinCodeFlow.asStateFlow()

    private val _isSessionUnlockedFlow = MutableStateFlow(true)
    val isSessionUnlockedFlow: StateFlow<Boolean> = _isSessionUnlockedFlow.asStateFlow()

    private val _watchlistFlow = MutableStateFlow<List<MovieItem>>(emptyList())
    val watchlistFlow: StateFlow<List<MovieItem>> = _watchlistFlow.asStateFlow()

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
        _isTabVisibleFlow.value = prefs.getBoolean(KEY_TAB_VISIBLE, true)
        val savedPin = prefs.getString(KEY_PIN_CODE, "")?.trim().orEmpty()
        _pinCodeFlow.value = savedPin
        _isSessionUnlockedFlow.value = savedPin.isEmpty()

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
                            episodeCurrent = obj.optString("episodeCurrent", ""),
                            source = MovieSource.fromString(obj.optString("source", "avdb"))
                        )
                    )
                }
                _watchlistFlow.value = list
            }
        } catch (_: Exception) {}

        // 2. History
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
    // PRIVACY & PIN LOCK
    // =========================================================================

    @Synchronized
    fun setTabVisible(context: Context, visible: Boolean) {
        init(context)
        _isTabVisibleFlow.value = visible
        getPrefs(context).edit().putBoolean(KEY_TAB_VISIBLE, visible).apply()
    }

    @Synchronized
    fun setPinCode(context: Context, pin: String) {
        init(context)
        val clean = pin.trim()
        _pinCodeFlow.value = clean
        _isSessionUnlockedFlow.value = true
        getPrefs(context).edit().putString(KEY_PIN_CODE, clean).apply()
    }

    @Synchronized
    fun clearPinCode(context: Context) {
        init(context)
        _pinCodeFlow.value = ""
        _isSessionUnlockedFlow.value = true
        getPrefs(context).edit().remove(KEY_PIN_CODE).apply()
    }

    fun verifyPin(input: String): Boolean {
        val current = _pinCodeFlow.value
        if (current.isEmpty()) {
            _isSessionUnlockedFlow.value = true
            return true
        }
        val match = current == input.trim()
        if (match) {
            _isSessionUnlockedFlow.value = true
        }
        return match
    }

    fun lockNow() {
        if (_pinCodeFlow.value.isNotEmpty()) {
            _isSessionUnlockedFlow.value = false
        }
    }

    // =========================================================================
    // WATCHLIST (YÊU THÍCH / THEO DÕI RIÊNG CỦA AV)
    // =========================================================================

    fun isInWatchlist(slug: String): Boolean {
        return _watchlistFlow.value.any { it.slug == slug }
    }

    @Synchronized
    fun toggleWatchlist(context: Context, movie: MovieItem): Boolean {
        init(context)
        val current = _watchlistFlow.value.toMutableList()
        val existingIdx = current.indexOfFirst { it.slug == movie.slug }
        val added = if (existingIdx >= 0) {
            current.removeAt(existingIdx)
            false
        } else {
            current.add(0, movie)
            true
        }
        _watchlistFlow.value = current
        saveWatchlist(context, current)
        return added
    }

    private fun saveWatchlist(context: Context, list: List<MovieItem>) {
        try {
            val array = JSONArray()
            list.forEach { item ->
                array.put(
                    JSONObject().apply {
                        put("name", item.name)
                        put("originName", item.originName)
                        put("slug", item.slug)
                        put("thumbUrl", item.thumbUrl)
                        put("posterUrl", item.posterUrl)
                        put("year", item.year)
                        put("quality", item.quality)
                        put("lang", item.lang)
                        put("episodeCurrent", item.episodeCurrent)
                        put("source", item.source.name.lowercase())
                    }
                )
            }
            getPrefs(context).edit().putString(KEY_WATCHLIST, array.toString()).apply()
        } catch (_: Exception) {}
    }

    // =========================================================================
    // WATCH HISTORY & PROGRESS (LỊCH SỬ XEM RIÊNG CỦA AV)
    // =========================================================================

    fun getProgress(slug: String): MovieWatchProgress? {
        return _historyFlow.value.firstOrNull { it.slug == slug }
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
        val movie = detail.movie
        val record = MovieWatchProgress(
            slug = movie.slug,
            name = movie.name,
            originName = movie.originName,
            thumbUrl = movie.thumbUrl,
            posterUrl = movie.posterUrl,
            year = movie.year,
            quality = movie.quality,
            lang = movie.lang,
            source = movie.source.name.lowercase(),
            serverIndex = serverIndex,
            episodeIndex = episodeIndex,
            episodeName = episodeName,
            positionMs = positionMs.coerceAtLeast(0L),
            durationMs = durationMs.coerceAtLeast(0L),
            updatedAt = System.currentTimeMillis()
        )
        val histList = _historyFlow.value.toMutableList()
        histList.removeAll { it.slug == movie.slug }
        histList.add(0, record)
        val trimmed = histList.take(100)
        _historyFlow.value = trimmed
        saveHistory(context, trimmed)
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
    fun removeMultipleFromHistory(context: Context, slugs: Set<String>) {
        if (slugs.isEmpty()) return
        init(context)
        val histList = _historyFlow.value.toMutableList()
        if (histList.removeAll { it.slug in slugs }) {
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

    private fun saveHistory(context: Context, list: List<MovieWatchProgress>) {
        try {
            val array = JSONArray()
            list.forEach { array.put(it.toJson()) }
            getPrefs(context).edit().putString(KEY_HISTORY, array.toString()).apply()
        } catch (_: Exception) {}
    }
}
