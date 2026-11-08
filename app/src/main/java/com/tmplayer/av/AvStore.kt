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
    private const val KEY_TAB_VISIBLE = "av_tab_visible_v2"
    private const val KEY_CHATS_TAB_VISIBLE = "chats_tab_visible_v2"
    private const val KEY_PIN_CODE = "av_pin_code"
    private const val KEY_WATCHLIST = "av_watchlist"
    private const val KEY_HISTORY = "av_history"
    private const val KEY_AFFINITY_ACTRESSES = "av_affinity_actresses"
    private const val KEY_AFFINITY_PREFIXES = "av_affinity_prefixes"
    private const val KEY_AFFINITY_CATEGORIES = "av_affinity_categories"

    private val _isTabVisibleFlow = MutableStateFlow(false)
    val isTabVisibleFlow: StateFlow<Boolean> = _isTabVisibleFlow.asStateFlow()

    private val _isChatsTabVisibleFlow = MutableStateFlow(false)
    val isChatsTabVisibleFlow: StateFlow<Boolean> = _isChatsTabVisibleFlow.asStateFlow()

    private val _pinCodeFlow = MutableStateFlow("")
    val pinCodeFlow: StateFlow<String> = _pinCodeFlow.asStateFlow()

    private val _isSessionUnlockedFlow = MutableStateFlow(true)
    val isSessionUnlockedFlow: StateFlow<Boolean> = _isSessionUnlockedFlow.asStateFlow()

    private val _watchlistFlow = MutableStateFlow<List<MovieItem>>(emptyList())
    val watchlistFlow: StateFlow<List<MovieItem>> = _watchlistFlow.asStateFlow()

    private val _historyFlow = MutableStateFlow<List<MovieWatchProgress>>(emptyList())
    val historyFlow: StateFlow<List<MovieWatchProgress>> = _historyFlow.asStateFlow()

    private val _preferredActressesFlow = MutableStateFlow<List<String>>(emptyList())
    val preferredActressesFlow: StateFlow<List<String>> = _preferredActressesFlow.asStateFlow()

    private val _preferredPrefixesFlow = MutableStateFlow<List<String>>(emptyList())
    val preferredPrefixesFlow: StateFlow<List<String>> = _preferredPrefixesFlow.asStateFlow()

    private val _preferredCategoriesFlow = MutableStateFlow<List<String>>(emptyList())
    val preferredCategoriesFlow: StateFlow<List<String>> = _preferredCategoriesFlow.asStateFlow()

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
        _isTabVisibleFlow.value = prefs.getBoolean(KEY_TAB_VISIBLE, false)
        _isChatsTabVisibleFlow.value = prefs.getBoolean(KEY_CHATS_TAB_VISIBLE, false)
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
                _historyFlow.value = list.sortedByDescending { it.updatedAt }.take(50)
            }
        } catch (_: Exception) {}

        // 3. Affinity / Preferences for Recommendations
        _preferredActressesFlow.value = loadStringList(prefs, KEY_AFFINITY_ACTRESSES)
        _preferredPrefixesFlow.value = loadStringList(prefs, KEY_AFFINITY_PREFIXES)
        _preferredCategoriesFlow.value = loadStringList(prefs, KEY_AFFINITY_CATEGORIES)
    }

    private fun loadStringList(prefs: SharedPreferences, key: String): List<String> {
        return try {
            val raw = prefs.getString(key, null) ?: return emptyList()
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val s = arr.optString(i, "").trim()
                    if (s.isNotBlank()) add(s)
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveStringList(context: Context, key: String, list: List<String>) {
        try {
            val arr = JSONArray()
            list.forEach { arr.put(it) }
            getPrefs(context).edit().putString(key, arr.toString()).apply()
        } catch (_: Exception) {}
    }

    @Synchronized
    fun recordAffinity(context: Context, detail: MovieDetail) {
        init(context)
        // 1. Actresses
        val newActresses = detail.actor
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.equals("Updating", ignoreCase = true) }
        if (newActresses.isNotEmpty()) {
            val merged = (newActresses + _preferredActressesFlow.value).distinct().take(25)
            _preferredActressesFlow.value = merged
            saveStringList(context, KEY_AFFINITY_ACTRESSES, merged)
        }

        // 2. Code Prefix (e.g. FC2-PPV, SONE, IPZZ, START, MIDV)
        val prefix = extractCodePrefix(detail.movie.slug, detail.movie.name)
        if (prefix.isNotBlank()) {
            val merged = (listOf(prefix) + _preferredPrefixesFlow.value).distinct().take(20)
            _preferredPrefixesFlow.value = merged
            saveStringList(context, KEY_AFFINITY_PREFIXES, merged)
        }

        // 3. Categories
        val newCats = detail.categories
            .map { it.trim() }
            .filter { it.isNotBlank() && it.length >= 2 }
        if (newCats.isNotEmpty()) {
            val merged = (newCats + _preferredCategoriesFlow.value).distinct().take(20)
            _preferredCategoriesFlow.value = merged
            saveStringList(context, KEY_AFFINITY_CATEGORIES, merged)
        }
    }

    fun extractCodePrefix(slug: String, title: String = ""): String {
        val upperSlug = slug.uppercase()
        if (upperSlug.startsWith("FC2-PPV")) return "FC2-PPV"
        if (upperSlug.startsWith("FC2")) return "FC2"
        val codeMatch = Regex("""\b([A-Z]{2,7})-\d{2,7}\b""").find(upperSlug)
            ?: Regex("""\b([A-Z]{2,7})-\d{2,7}\b""").find(title.uppercase())
        return codeMatch?.groupValues?.get(1).orEmpty()
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
    fun setChatsTabVisible(context: Context, visible: Boolean) {
        init(context)
        _isChatsTabVisibleFlow.value = visible
        getPrefs(context).edit().putBoolean(KEY_CHATS_TAB_VISIBLE, visible).apply()
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
        val trimmed = histList.take(50)
        _historyFlow.value = trimmed
        saveHistory(context, trimmed)
        recordAffinity(context, detail)
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

    // =========================================================================
    // BACKUP & RESTORE (SAO LƯU & KHÔI PHỤC DỮ LIỆU AV)
    // =========================================================================

    @Synchronized
    fun exportBackupJson(context: Context): JSONObject {
        init(context)
        return JSONObject().apply {
            val wlArray = JSONArray()
            _watchlistFlow.value.forEach { item ->
                wlArray.put(
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
            put("watchlist", wlArray)

            val histArray = JSONArray()
            _historyFlow.value.forEach { histArray.put(it.toJson()) }
            put("history", histArray)

            put("affinityActresses", JSONArray(_preferredActressesFlow.value))
            put("affinityPrefixes", JSONArray(_preferredPrefixesFlow.value))
            put("affinityCategories", JSONArray(_preferredCategoriesFlow.value))
            put("tabVisible", _isTabVisibleFlow.value)
            put("chatsTabVisible", _isChatsTabVisibleFlow.value)
        }
    }

    @Synchronized
    fun countBackupChanges(context: Context, jsonStr: String?): Int {
        if (jsonStr.isNullOrBlank()) return 0
        init(context)
        return try {
            val obj = JSONObject(jsonStr)
            var count = 0
            val existingWl = _watchlistFlow.value.map { it.slug }.toSet()
            val wlArr = obj.optJSONArray("watchlist")
            if (wlArr != null) {
                for (i in 0 until wlArr.length()) {
                    val slug = wlArr.optJSONObject(i)?.optString("slug").orEmpty()
                    if (slug.isNotBlank() && slug !in existingWl) count++
                }
            }
            val existingHist = _historyFlow.value.associateBy { it.slug }
            val histArr = obj.optJSONArray("history")
            if (histArr != null) {
                for (i in 0 until histArr.length()) {
                    val item = histArr.optJSONObject(i)?.let { MovieWatchProgress.fromJson(it) } ?: continue
                    val cur = existingHist[item.slug]
                    if (cur == null || item.updatedAt > cur.updatedAt) {
                        count++
                    }
                }
            }
            count
        } catch (_: Exception) {
            0
        }
    }

    @Synchronized
    fun restoreFromBackupJson(context: Context, jsonStr: String?) {
        if (jsonStr.isNullOrBlank()) return
        init(context)
        try {
            val obj = JSONObject(jsonStr)

            // 1. Merge Watchlist
            val wlArr = obj.optJSONArray("watchlist")
            if (wlArr != null) {
                val mergedWl = _watchlistFlow.value.toMutableList()
                val seenSlugs = mergedWl.map { it.slug }.toMutableSet()
                for (i in 0 until wlArr.length()) {
                    val itemObj = wlArr.optJSONObject(i) ?: continue
                    val slug = itemObj.optString("slug").trim()
                    val name = itemObj.optString("name").trim()
                    if (slug.isNotBlank() && name.isNotBlank() && seenSlugs.add(slug)) {
                        mergedWl.add(
                            MovieItem(
                                name = name,
                                originName = itemObj.optString("originName"),
                                slug = slug,
                                thumbUrl = itemObj.optString("thumbUrl"),
                                posterUrl = itemObj.optString("posterUrl"),
                                year = itemObj.optString("year"),
                                quality = itemObj.optString("quality", "HD"),
                                lang = itemObj.optString("lang", "Vietsub"),
                                episodeCurrent = itemObj.optString("episodeCurrent", ""),
                                source = MovieSource.fromString(itemObj.optString("source", "avdb"))
                            )
                        )
                    }
                }
                _watchlistFlow.value = mergedWl
                saveWatchlist(context, mergedWl)
            }

            // 2. Merge History & Progress
            val histArr = obj.optJSONArray("history")
            if (histArr != null) {
                val map = _historyFlow.value.associateBy { it.slug }.toMutableMap()
                for (i in 0 until histArr.length()) {
                    val item = histArr.optJSONObject(i)?.let { MovieWatchProgress.fromJson(it) } ?: continue
                    val cur = map[item.slug]
                    if (cur == null) {
                        map[item.slug] = item
                    } else {
                        val newer = if (item.updatedAt >= cur.updatedAt) item else cur
                        map[item.slug] = newer.copy(watchedEpisodes = cur.watchedEpisodes + item.watchedEpisodes)
                    }
                }
                val mergedHist = map.values.sortedByDescending { it.updatedAt }.take(50)
                _historyFlow.value = mergedHist
                saveHistory(context, mergedHist)
            }

            // 3. Merge Affinity Lists
            fun mergeStrings(arr: JSONArray?, current: List<String>, max: Int): List<String> {
                if (arr == null) return current
                val incoming = buildList {
                    for (i in 0 until arr.length()) {
                        val s = arr.optString(i, "").trim()
                        if (s.isNotBlank()) add(s)
                    }
                }
                return (current + incoming).distinct().take(max)
            }

            val mergedActresses = mergeStrings(obj.optJSONArray("affinityActresses"), _preferredActressesFlow.value, 25)
            _preferredActressesFlow.value = mergedActresses
            saveStringList(context, KEY_AFFINITY_ACTRESSES, mergedActresses)

            val mergedPrefixes = mergeStrings(obj.optJSONArray("affinityPrefixes"), _preferredPrefixesFlow.value, 20)
            _preferredPrefixesFlow.value = mergedPrefixes
            saveStringList(context, KEY_AFFINITY_PREFIXES, mergedPrefixes)

            val mergedCategories = mergeStrings(obj.optJSONArray("affinityCategories"), _preferredCategoriesFlow.value, 20)
            _preferredCategoriesFlow.value = mergedCategories
            saveStringList(context, KEY_AFFINITY_CATEGORIES, mergedCategories)

            if (obj.has("tabVisible")) {
                setTabVisible(context, obj.optBoolean("tabVisible", false))
            }
            if (obj.has("chatsTabVisible")) {
                setChatsTabVisible(context, obj.optBoolean("chatsTabVisible", false))
            }
        } catch (_: Exception) {}
    }
}
