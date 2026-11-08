package com.tmplayer.movie

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tmplayer.ui.components.LocalBottomChrome
import com.tmplayer.ui.icons.TmIcons
import com.tmplayer.ui.theme.Tone
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class SourceHomeCache(
    val latestMovies: List<MovieItem> = emptyList(),
    val cinemaMovies: List<MovieItem> = emptyList(),
    val seriesMovies: List<MovieItem> = emptyList(),
    val singleMovies: List<MovieItem> = emptyList(),
    val costumeMovies: List<MovieItem> = emptyList(),
    val chinaMovies: List<MovieItem> = emptyList(),
    val koreaMovies: List<MovieItem> = emptyList(),
    val animeMovies: List<MovieItem> = emptyList(),
    val subteamMovies: List<MovieItem> = emptyList()
)

data class GridCacheEntry(
    val movies: List<MovieItem>,
    val page: Int,
    val totalPages: Int
)

/**
 * State holder singleton for Movie catalog to preserve data across navigation,
 * player activity launches, and screen configuration changes.
 */
object MovieCatalogState {
    var activeSource by mutableStateOf(MovieSource.VSMov)
    var lastLoadedSource: MovieSource? = null

    val homeCache = mutableMapOf<MovieSource, SourceHomeCache>()
    val gridCache = mutableMapOf<String, GridCacheEntry>()

    var searchQuery by mutableStateOf("")

    // Quick category pill selection
    var selectedCategory by mutableStateOf("")

    // Advanced filter sheet states
    var showFilterSheet by mutableStateOf(false)
    var selectedGenre by mutableStateOf("")
    var selectedCountry by mutableStateOf("")
    var selectedYear by mutableStateOf("")

    // Section view all target (null means home catalog view)
    var viewAllCategory by mutableStateOf<Pair<String, String>?>(null)

    // Home rich shelves data
    var latestMovies by mutableStateOf<List<MovieItem>>(emptyList())
    var cinemaMovies by mutableStateOf<List<MovieItem>>(emptyList())
    var seriesMovies by mutableStateOf<List<MovieItem>>(emptyList())
    var singleMovies by mutableStateOf<List<MovieItem>>(emptyList())
    var costumeMovies by mutableStateOf<List<MovieItem>>(emptyList())
    var chinaMovies by mutableStateOf<List<MovieItem>>(emptyList())
    var koreaMovies by mutableStateOf<List<MovieItem>>(emptyList())
    var animeMovies by mutableStateOf<List<MovieItem>>(emptyList())
    var subteamMovies by mutableStateOf<List<MovieItem>>(emptyList())
    var isHomeLoading by mutableStateOf(false)

    // Grid filter / search data (Infinite scroll)
    var gridMovies by mutableStateOf<List<MovieItem>>(emptyList())
    var gridPage by mutableIntStateOf(1)
    var gridTotalPages by mutableIntStateOf(1)
    var isGridLoading by mutableStateOf(false)
    var isGridLoadingMore by mutableStateOf(false)

    // Detail Sheet
    var selectedMovieSlug by mutableStateOf<String?>(null)
    var movieDetail by mutableStateOf<MovieDetail?>(null)
    var isDetailLoading by mutableStateOf(false)
}

/**
 * Màn hình Movie Online: KKPhim & VSMov
 * Tính năng nâng cấp:
 * - Multi-movie Featured Carousel (Slide nhiều phim nổi bật tự động chuyển)
 * - Tận dụng đa dạng API (Phim chiếu rạp, Cổ trang, Hàn Quốc, Trung Quốc, Subteam, Anime...)
 * - Scroll tự update vô tận (Infinite Scroll) không cần bấm chuyển trang
 * - Tinh giản bộ lọc: Quick category pills + ModalBottomSheet bộ lọc đa tiêu chí (Thể loại, Quốc gia, Năm)
 * - Tích hợp xem online với OnlinePlayerActivity đồng bộ giao diện và cử chỉ
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoviePane(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val bottomChrome = LocalBottomChrome.current

    val catalogState = MovieCatalogState
    var activeSource by catalogState::activeSource
    var searchQuery by catalogState::searchQuery

    // Quick category pill selection
    var selectedCategory by catalogState::selectedCategory

    // Advanced filter sheet states
    var showFilterSheet by catalogState::showFilterSheet
    var selectedGenre by catalogState::selectedGenre
    var selectedCountry by catalogState::selectedCountry
    var selectedYear by catalogState::selectedYear

    // Section view all target (null means home catalog view)
    var viewAllCategory by catalogState::viewAllCategory

    // Home rich shelves data
    var latestMovies by catalogState::latestMovies
    var cinemaMovies by catalogState::cinemaMovies
    var seriesMovies by catalogState::seriesMovies
    var singleMovies by catalogState::singleMovies
    var costumeMovies by catalogState::costumeMovies
    var chinaMovies by catalogState::chinaMovies
    var koreaMovies by catalogState::koreaMovies
    var animeMovies by catalogState::animeMovies
    var subteamMovies by catalogState::subteamMovies
    var isHomeLoading by catalogState::isHomeLoading

    // Grid filter / search data (Infinite scroll)
    var gridMovies by catalogState::gridMovies
    var gridPage by catalogState::gridPage
    var gridTotalPages by catalogState::gridTotalPages
    var isGridLoading by catalogState::isGridLoading
    var isGridLoadingMore by catalogState::isGridLoadingMore
    val gridState = rememberLazyGridState()

    // Detail Sheet
    var selectedMovieSlug by catalogState::selectedMovieSlug
    var movieDetail by catalogState::movieDetail
    var isDetailLoading by catalogState::isDetailLoading

    val watchlist by MovieStore.watchlistFlow.collectAsState()
    val history by MovieStore.historyFlow.collectAsState()
    val cloudSynced by MovieStore.cloudSyncedFlow.collectAsState()
    var isCheckingCloudUpdates by remember { mutableStateOf(false) }
    var isSelectingHistory by remember { mutableStateOf(false) }
    var selectedHistorySlugs by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(selectedCategory) {
        if (selectedCategory != "history") {
            isSelectingHistory = false
            selectedHistorySlugs = emptySet()
        }
    }

    LaunchedEffect(Unit) {
        MovieStore.init(context)
        MovieCloudDownloader.checkTrackedMoviesForNewEpisodes(context, forceCheck = false)
    }

    val activeFilterCount = (if (selectedCategory.isNotBlank()) 1 else 0) +
        (if (selectedGenre.isNotBlank()) 1 else 0) +
        (if (selectedCountry.isNotBlank()) 1 else 0) +
        (if (selectedYear.isNotBlank()) 1 else 0)

    val isCatalogGridActive = searchQuery.isNotBlank() ||
        selectedCategory.isNotBlank() ||
        selectedGenre.isNotBlank() ||
        selectedCountry.isNotBlank() ||
        selectedYear.isNotBlank() ||
        viewAllCategory != null

    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullToRefreshState()

    fun currentGridCacheKey(source: MovieSource = activeSource): String =
        "${source.name}|${searchQuery.trim()}|$selectedCategory|$selectedGenre|$selectedCountry|$selectedYear|${viewAllCategory?.first.orEmpty()}"

    // Load Home Shelves (with per-source caching; only reloads from network if cache is empty or forceRefresh = true)
    fun loadHome(forceRefresh: Boolean = false, onComplete: () -> Unit = {}) {
        val targetSource = activeSource
        val cached = catalogState.homeCache[targetSource]
        if (!forceRefresh && cached != null && cached.latestMovies.isNotEmpty()) {
            latestMovies = cached.latestMovies
            cinemaMovies = cached.cinemaMovies
            seriesMovies = cached.seriesMovies
            singleMovies = cached.singleMovies
            costumeMovies = cached.costumeMovies
            chinaMovies = cached.chinaMovies
            koreaMovies = cached.koreaMovies
            animeMovies = cached.animeMovies
            subteamMovies = cached.subteamMovies
            isHomeLoading = false
            onComplete()
            return
        }

        scope.launch {
            if (!forceRefresh || latestMovies.isEmpty()) {
                isHomeLoading = true
            }
            try {
                val latest = MovieRepository.getLatestMovies(targetSource, 1)
                val cinema = MovieRepository.getCategoryMovies(targetSource, "phim-chieu-rap", 1)
                val series = MovieRepository.getCategoryMovies(targetSource, "phim-bo", 1)
                val single = MovieRepository.getCategoryMovies(targetSource, "phim-le", 1)
                val costume = MovieRepository.getGenreMovies(targetSource, "co-trang", 1)
                val china = MovieRepository.getCountryMovies(targetSource, "trung-quoc", 1)
                val korea = MovieRepository.getCountryMovies(targetSource, "han-quoc", 1)
                val anime = MovieRepository.getCategoryMovies(targetSource, "hoat-hinh", 1)
                val subteam = if (targetSource == MovieSource.VSMov) {
                    MovieRepository.getCategoryMovies(targetSource, "subteam", 1)
                } else {
                    emptyList()
                }

                if (latest.isNotEmpty()) {
                    catalogState.homeCache[targetSource] = SourceHomeCache(
                        latestMovies = latest,
                        cinemaMovies = cinema,
                        seriesMovies = series,
                        singleMovies = single,
                        costumeMovies = costume,
                        chinaMovies = china,
                        koreaMovies = korea,
                        animeMovies = anime,
                        subteamMovies = subteam
                    )
                }

                if (activeSource == targetSource) {
                    latestMovies = latest
                    cinemaMovies = cinema
                    seriesMovies = series
                    singleMovies = single
                    costumeMovies = costume
                    chinaMovies = china
                    koreaMovies = korea
                    animeMovies = anime
                    subteamMovies = subteam
                }
            } catch (_: Exception) {
            } finally {
                isHomeLoading = false
                onComplete()
            }
        }
    }

    // Load Grid Data Initial (with per-source & filter caching; only reloads from network if cache is empty or forceRefresh = true)
    fun loadGridInitial(forceRefresh: Boolean = false, onComplete: () -> Unit = {}) {
        if (selectedCategory == "watchlist") {
            gridMovies = watchlist
            gridPage = 1
            gridTotalPages = 1
            isGridLoading = false
            onComplete()
            return
        }
        if (selectedCategory == "history") {
            gridMovies = history.map { it.toMovieItem() }
            gridPage = 1
            gridTotalPages = 1
            isGridLoading = false
            onComplete()
            return
        }
        if (selectedCategory == "cloud") {
            gridMovies = cloudSynced.map { it.toMovieItem() }
            gridPage = 1
            gridTotalPages = 1
            isGridLoading = false
            if (forceRefresh) {
                scope.launch {
                    isCheckingCloudUpdates = true
                    MovieCloudDownloader.checkTrackedMoviesForNewEpisodes(context, forceCheck = true) { _, _ ->
                        isCheckingCloudUpdates = false
                        onComplete()
                    }
                }
            } else {
                onComplete()
            }
            return
        }

        val targetSource = activeSource
        val cacheKey = currentGridCacheKey(targetSource)
        val cached = catalogState.gridCache[cacheKey]
        if (!forceRefresh && cached != null && cached.movies.isNotEmpty()) {
            gridMovies = cached.movies
            gridPage = cached.page
            gridTotalPages = cached.totalPages
            isGridLoading = false
            onComplete()
            return
        }

        scope.launch {
            if (!forceRefresh || gridMovies.isEmpty()) {
                isGridLoading = true
            }
            gridPage = 1
            try {
                val (resItems, resTotal) = if (searchQuery.isNotBlank()) {
                    val res = MovieRepository.searchMovies(targetSource, searchQuery, 1)
                    res to (if (res.size >= 18) 2 else 1)
                } else if (viewAllCategory != null) {
                    val slug = viewAllCategory!!.first
                    val res = when (slug) {
                        "trung-quoc", "han-quoc", "au-my" -> MovieRepository.getCountryMovies(targetSource, slug, 1)
                        "co-trang" -> MovieRepository.getGenreMovies(targetSource, slug, 1)
                        "latest" -> MovieRepository.getLatestMovies(targetSource, 1)
                        else -> MovieRepository.getCategoryMovies(targetSource, slug, 1)
                    }
                    res to (if (res.size >= 12) 2 else 1)
                } else {
                    MovieRepository.filterMovies(
                        source = targetSource,
                        category = selectedCategory,
                        genre = selectedGenre,
                        country = selectedCountry,
                        year = selectedYear,
                        keyword = "",
                        page = 1
                    )
                }

                if (resItems.isNotEmpty()) {
                    catalogState.gridCache[cacheKey] = GridCacheEntry(
                        movies = resItems,
                        page = 1,
                        totalPages = resTotal
                    )
                }
                if (activeSource == targetSource && currentGridCacheKey(activeSource) == cacheKey) {
                    gridMovies = resItems
                    gridTotalPages = resTotal
                }
            } catch (_: Exception) {
                if (!forceRefresh) {
                    gridMovies = emptyList()
                }
            } finally {
                isGridLoading = false
                onComplete()
            }
        }
    }

    // Load More Grid Data (Endless Pagination)
    fun loadGridMore() {
        if (selectedCategory in listOf("watchlist", "history", "cloud")) return
        if (isGridLoadingMore || isGridLoading || gridPage >= gridTotalPages) return
        val targetSource = activeSource
        val cacheKey = currentGridCacheKey(targetSource)
        scope.launch {
            isGridLoadingMore = true
            val nextPage = gridPage + 1
            try {
                val newItems = if (searchQuery.isNotBlank()) {
                    val res = MovieRepository.searchMovies(targetSource, searchQuery, nextPage)
                    if (res.size >= 18) gridTotalPages = nextPage + 1
                    res
                } else if (viewAllCategory != null) {
                    val slug = viewAllCategory!!.first
                    val res = when (slug) {
                        "trung-quoc", "han-quoc", "au-my" -> MovieRepository.getCountryMovies(targetSource, slug, nextPage)
                        "co-trang" -> MovieRepository.getGenreMovies(targetSource, slug, nextPage)
                        "latest" -> MovieRepository.getLatestMovies(targetSource, nextPage)
                        else -> MovieRepository.getCategoryMovies(targetSource, slug, nextPage)
                    }
                    if (res.size >= 12) gridTotalPages = nextPage + 1
                    res
                } else {
                    val (items, total) = MovieRepository.filterMovies(
                        source = targetSource,
                        category = selectedCategory,
                        genre = selectedGenre,
                        country = selectedCountry,
                        year = selectedYear,
                        keyword = "",
                        page = nextPage
                    )
                    gridTotalPages = total
                    items
                }

                if (newItems.isNotEmpty()) {
                    val existingSlugs = gridMovies.map { it.slug }.toSet()
                    val distinctNew = newItems.filter { it.slug !in existingSlugs }
                    val updatedList = gridMovies + distinctNew
                    gridMovies = updatedList
                    gridPage = nextPage
                    catalogState.gridCache[cacheKey] = GridCacheEntry(
                        movies = updatedList,
                        page = nextPage,
                        totalPages = gridTotalPages
                    )
                }
            } catch (_: Exception) {
            } finally {
                isGridLoadingMore = false
            }
        }
    }

    // Endless scroll detection
    val shouldLoadMore = remember {
        derivedStateOf {
            val layoutInfo = gridState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleIndex >= totalItems - 6
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value && !isGridLoading && !isGridLoadingMore && gridPage < gridTotalPages) {
            loadGridMore()
        }
    }

    // Trigger home or grid load on source / filter change (uses cache unless cache for that source/filter is empty)
    LaunchedEffect(activeSource, isCatalogGridActive) {
        if (!isCatalogGridActive) {
            catalogState.lastLoadedSource = activeSource
            loadHome(forceRefresh = false)
        } else {
            loadGridInitial(forceRefresh = false)
        }
    }

    // Trigger grid load when filter / search / viewAll changes
    LaunchedEffect(searchQuery, selectedCategory, selectedGenre, selectedCountry, selectedYear, viewAllCategory) {
        if (isCatalogGridActive) {
            loadGridInitial(forceRefresh = false)
        }
    }

    LaunchedEffect(watchlist, history, selectedCategory) {
        if (selectedCategory == "watchlist") {
            gridMovies = watchlist
            gridTotalPages = 1
        } else if (selectedCategory == "history") {
            gridMovies = history.map { it.toMovieItem() }
            gridTotalPages = 1
        }
    }

    // Fetch movie detail when selected or when source changes
    LaunchedEffect(selectedMovieSlug, activeSource) {
        val slug = selectedMovieSlug
        if (slug != null) {
            if (movieDetail?.movie?.slug != slug || movieDetail?.movie?.source != activeSource) {
                isDetailLoading = true
                movieDetail = null
                try {
                    val fetched = MovieRepository.getMovieDetail(activeSource, slug)
                    if (fetched != null) {
                        movieDetail = fetched
                    } else {
                        val fallbackSource = if (activeSource == MovieSource.VSMov) MovieSource.KKPhim else MovieSource.VSMov
                        movieDetail = MovieRepository.getMovieDetail(fallbackSource, slug)
                    }
                } catch (_: Exception) {
                    movieDetail = null
                } finally {
                    isDetailLoading = false
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Tone.background)
    ) {
        // TOP HEADER BAR
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Tone.surface)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            // Row 1: Title & Source Switcher
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = TmIcons.Trailer,
                        contentDescription = null,
                        tint = Tone.accent,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Movie Online",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Tone.text
                    )
                }

                // SOURCE SWITCHER (VSMov Tab 1 / KKPhim Tab 2)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Tone.surfaceHigh)
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    SourceTabButton(
                        title = "VSMov",
                        isSelected = activeSource == MovieSource.VSMov,
                        onClick = {
                            if (activeSource != MovieSource.VSMov) {
                                activeSource = MovieSource.VSMov
                                if (selectedMovieSlug == null) movieDetail = null
                            }
                        }
                    )
                    SourceTabButton(
                        title = "KKPhim",
                        isSelected = activeSource == MovieSource.KKPhim,
                        onClick = {
                            if (activeSource != MovieSource.KKPhim) {
                                activeSource = MovieSource.KKPhim
                                if (selectedMovieSlug == null) movieDetail = null
                            }
                        }
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Row 2: Search Input Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Tone.surfaceHigh)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = TmIcons.Search,
                    contentDescription = null,
                    tint = Tone.text.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Tone.text, fontSize = 14.sp),
                    cursorBrush = SolidColor(Tone.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    modifier = Modifier.weight(1f),
                    decorationBox = { innerTextField ->
                        if (searchQuery.isEmpty()) {
                            Text(
                                "Tìm kiếm phim online (${activeSource.badge})...",
                                color = Tone.text.copy(alpha = 0.4f),
                                fontSize = 14.sp
                            )
                        }
                        innerTextField()
                    }
                )
                if (searchQuery.isNotEmpty()) {
                    Icon(
                        imageVector = TmIcons.Close,
                        contentDescription = "Clear",
                        tint = Tone.text.copy(alpha = 0.6f),
                        modifier = Modifier
                            .size(18.dp)
                            .clickable {
                                searchQuery = ""
                                focusManager.clearFocus()
                            }
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Row 3: Quick Category Pills + Filter Sheet Button (Clean & Compact)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Filter Button with Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (activeFilterCount > 0) Tone.accent else Tone.surfaceHigh)
                        .border(
                            0.5.dp,
                            if (activeFilterCount > 0) Tone.accent else Tone.outline.copy(alpha = 0.5f),
                            RoundedCornerShape(16.dp)
                        )
                        .clickable { showFilterSheet = true }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = TmIcons.FilterHorizontal,
                            contentDescription = null,
                            tint = if (activeFilterCount > 0) Color.Black else Tone.text,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (activeFilterCount > 0) "Bộ lọc ($activeFilterCount)" else "Bộ lọc",
                            color = if (activeFilterCount > 0) Color.Black else Tone.text,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Quick Category Pills
                val quickPills = listOf(
                    "" to "Tất cả",
                    "history" to "Xem gần đây",
                    "watchlist" to "Theo dõi",
                    "phim-bo" to "Phim bộ",
                    "phim-le" to "Phim lẻ",
                    "phim-chieu-rap" to "Chiếu rạp",
                    "hoat-hinh" to "Hoạt hình",
                    "co-trang" to "Cổ trang",
                    "han-quoc" to "Hàn Quốc",
                    "trung-quoc" to "Trung Quốc"
                )

                quickPills.forEach { (slug, label) ->
                    val isSelected = when (slug) {
                        "" -> selectedCategory.isBlank() && selectedGenre.isBlank() && selectedCountry.isBlank() && selectedYear.isBlank()
                        "han-quoc", "trung-quoc" -> selectedCountry == slug
                        "co-trang" -> selectedGenre == slug
                        else -> selectedCategory == slug
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) Tone.accent else Tone.surfaceHigh)
                            .clickable {
                                viewAllCategory = null
                                when (slug) {
                                    "" -> {
                                        selectedCategory = ""
                                        selectedGenre = ""
                                        selectedCountry = ""
                                        selectedYear = ""
                                    }
                                    "han-quoc", "trung-quoc" -> {
                                        selectedCountry = if (selectedCountry == slug) "" else slug
                                    }
                                    "co-trang" -> {
                                        selectedGenre = if (selectedGenre == slug) "" else slug
                                    }
                                    else -> {
                                        selectedCategory = if (selectedCategory == slug) "" else slug
                                    }
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) Color.Black else Tone.text.copy(alpha = 0.85f),
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            // Row 4: Dismissible Active Filters Row (Only shown if filters are chosen)
            if (activeFilterCount > 0 || viewAllCategory != null) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (viewAllCategory != null) {
                        ActiveChip(
                            label = "Mục: ${viewAllCategory!!.second}",
                            onDismiss = { viewAllCategory = null }
                        )
                    }
                    if (selectedCategory.isNotBlank()) {
                        val catLabel = MovieFilterData.categories.firstOrNull { it.slug == selectedCategory }?.label ?: selectedCategory
                        ActiveChip(
                            label = "Danh mục: $catLabel",
                            onDismiss = { selectedCategory = "" }
                        )
                    }
                    if (selectedGenre.isNotBlank()) {
                        val gLabel = MovieFilterData.genres.firstOrNull { it.slug == selectedGenre }?.label ?: selectedGenre
                        ActiveChip(
                            label = "Thể loại: $gLabel",
                            onDismiss = { selectedGenre = "" }
                        )
                    }
                    if (selectedCountry.isNotBlank()) {
                        val cLabel = MovieFilterData.countries.firstOrNull { it.slug == selectedCountry }?.label ?: selectedCountry
                        ActiveChip(
                            label = "Quốc gia: $cLabel",
                            onDismiss = { selectedCountry = "" }
                        )
                    }
                    if (selectedYear.isNotBlank()) {
                        ActiveChip(
                            label = "Năm: $selectedYear",
                            onDismiss = { selectedYear = "" }
                        )
                    }

                    // Reset all button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Tone.surfaceHigh)
                            .clickable {
                                selectedCategory = ""
                                selectedGenre = ""
                                selectedCountry = ""
                                selectedYear = ""
                                viewAllCategory = null
                                searchQuery = ""
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("Xóa tất cả", color = Tone.accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        HorizontalDivider(color = Tone.outline.copy(alpha = 0.5f), thickness = 0.5.dp)

        // MAIN CONTENT AREA (PULL TO REFRESH)
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                if (isCatalogGridActive) {
                    loadGridInitial(forceRefresh = true) { isRefreshing = false }
                } else {
                    loadHome(forceRefresh = true) { isRefreshing = false }
                }
            },
            state = pullRefreshState,
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullRefreshState,
                    isRefreshing = isRefreshing,
                    containerColor = Tone.surfaceHigh,
                    color = Tone.accent,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (isCatalogGridActive) {
                // =============================================================
                // CATALOG GRID VIEW (INFINITE SCROLL / ENDLESS PAGINATION)
                // =============================================================
                if (isGridLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Tone.accent, strokeWidth = 3.dp)
                    }
                } else if (gridMovies.isEmpty()) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = TmIcons.Trailer,
                                contentDescription = null,
                                tint = Tone.text.copy(alpha = 0.3f),
                                modifier = Modifier.size(54.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                            val emptyMsg = when (selectedCategory) {
                                "watchlist" -> "Chưa có phim trong danh sách theo dõi"
                                "history" -> "Chưa có phim xem gần đây"
                                "cloud" -> "Chưa có phim nào được đánh dấu kéo về Tele Cloud"
                                else -> "Không tìm thấy phim phù hợp"
                            }
                            Text(
                                emptyMsg,
                                color = Tone.text.copy(alpha = 0.6f),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Adaptive(minSize = 130.dp),
                        contentPadding = PaddingValues(
                            start = 12.dp,
                            end = 12.dp,
                            top = 12.dp,
                            bottom = bottomChrome + 48.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (selectedCategory == "history" && gridMovies.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Tone.surfaceHigh)
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (!isSelectingHistory) {
                                        Text(
                                            text = "${gridMovies.size} phim trong lịch sử",
                                            color = Tone.text.copy(alpha = 0.7f),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Tone.surface)
                                                    .border(0.5.dp, Tone.accent.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                                    .clickable {
                                                        isSelectingHistory = true
                                                        selectedHistorySlugs = emptySet()
                                                    }
                                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                                            ) {
                                                Text(
                                                    text = "Chọn xóa",
                                                    color = Tone.accent,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Text(
                                                text = "Xóa tất cả",
                                                color = Tone.text.copy(alpha = 0.6f),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.clickable {
                                                    MovieStore.clearHistory(context)
                                                    gridMovies = emptyList()
                                                    isSelectingHistory = false
                                                    selectedHistorySlugs = emptySet()
                                                }
                                            )
                                        }
                                    } else {
                                        val allSelected = selectedHistorySlugs.size == gridMovies.size && gridMovies.isNotEmpty()
                                        Text(
                                            text = if (allSelected) "Bỏ chọn tất cả" else "Chọn tất cả (${gridMovies.size})",
                                            color = Tone.accent,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.clickable {
                                                selectedHistorySlugs = if (allSelected) {
                                                    emptySet()
                                                } else {
                                                    gridMovies.map { it.slug }.toSet()
                                                }
                                            }
                                        )
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(if (selectedHistorySlugs.isNotEmpty()) Tone.accent else Tone.surface)
                                                    .clickable(enabled = selectedHistorySlugs.isNotEmpty()) {
                                                        MovieStore.removeMultipleFromHistory(context, selectedHistorySlugs)
                                                        selectedHistorySlugs = emptySet()
                                                        isSelectingHistory = false
                                                    }
                                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                                            ) {
                                                Text(
                                                    text = "Xóa (${selectedHistorySlugs.size})",
                                                    color = if (selectedHistorySlugs.isNotEmpty()) Color.Black else Tone.text.copy(alpha = 0.4f),
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Text(
                                                text = "Hủy",
                                                color = Tone.text.copy(alpha = 0.7f),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.clickable {
                                                    isSelectingHistory = false
                                                    selectedHistorySlugs = emptySet()
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        } else if (selectedCategory == "cloud" && gridMovies.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Tone.surfaceHigh)
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        val newEpsMovies = cloudSynced.count { it.hasNewEpisodes }
                                        Text(
                                            text = if (newEpsMovies > 0) "🔔 $newEpsMovies phim có tập mới chưa kéo!" else "☁️ ${gridMovies.size} phim đã kéo về Tele Cloud",
                                            color = if (newEpsMovies > 0) Tone.accent else Tone.text,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Tự động theo dõi tập mới & kéo về MovieBox",
                                            color = Tone.text.copy(alpha = 0.6f),
                                            fontSize = 11.sp
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Tone.accent)
                                            .clickable(enabled = !isCheckingCloudUpdates) {
                                                scope.launch {
                                                    isCheckingCloudUpdates = true
                                                    MovieCloudDownloader.checkTrackedMoviesForNewEpisodes(context, forceCheck = true) { _, _ ->
                                                        isCheckingCloudUpdates = false
                                                    }
                                                }
                                            }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = if (isCheckingCloudUpdates) "Đang quét..." else "🔄 Quét tập mới",
                                            color = Color.Black,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                        items(gridMovies, key = { it.slug }) { item ->
                            val isHistoryGrid = selectedCategory == "history"
                            val isItemSelected = isHistoryGrid && isSelectingHistory && item.slug in selectedHistorySlugs
                            MoviePosterCard(
                                movie = item,
                                isSelectionMode = isHistoryGrid && isSelectingHistory,
                                isSelected = isItemSelected,
                                onRemoveItem = if (isHistoryGrid && !isSelectingHistory) {
                                    { MovieStore.removeFromHistory(context, item.slug) }
                                } else null,
                                onClick = {
                                    if (isHistoryGrid && isSelectingHistory) {
                                        selectedHistorySlugs = if (item.slug in selectedHistorySlugs) {
                                            selectedHistorySlugs - item.slug
                                        } else {
                                            selectedHistorySlugs + item.slug
                                        }
                                    } else {
                                        selectedMovieSlug = item.slug
                                    }
                                }
                            )
                        }

                        // Bottom loading indicator for endless scroll
                        if (isGridLoadingMore) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = Tone.accent, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                                }
                            }
                        } else if (gridPage >= gridTotalPages && gridMovies.size >= 12) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Đã xem hết danh sách", color = Tone.text.copy(alpha = 0.4f), fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            } else {
                // =============================================================
                // HOME SHELVES VIEW (HERO CAROUSEL + HORIZONTAL SHELVES)
                // =============================================================
                if (isHomeLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Tone.accent, strokeWidth = 3.dp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 10.dp, bottom = bottomChrome + 40.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        // FEATURED MULTI-MOVIE HERO CAROUSEL
                        if (latestMovies.isNotEmpty()) {
                            item(key = "featured_hero_carousel") {
                                MovieHeroCarousel(
                                    movies = latestMovies.take(6),
                                    onMovieClick = { selectedMovieSlug = it.slug }
                                )
                            }
                        }

                        // SHELF: PHIM ĐANG THEO DÕI (Nằm trên danh sách Phim Xem Gần Đây)
                        if (watchlist.isNotEmpty()) {
                            item(key = "shelf_watchlist") {
                                MovieShelfRow(
                                    title = "⭐ Phim Đang Theo Dõi",
                                    movies = watchlist,
                                    onViewAll = { selectedCategory = "watchlist" },
                                    onMovieClick = { selectedMovieSlug = it.slug }
                                )
                            }
                        }

                        // SHELF: PHIM XEM GẦN ĐÂY (Gộp chung phim đang xem dở & lịch sử gần đây)
                        if (history.isNotEmpty()) {
                            item(key = "shelf_history") {
                                MovieContinueShelfRow(
                                    title = "🎬 Phim Xem Gần Đây",
                                    records = history,
                                    onViewAll = { selectedCategory = "history" },
                                    onRecordClick = { rec -> selectedMovieSlug = rec.slug },
                                    onRemoveRecord = { rec -> MovieStore.removeFromHistory(context, rec.slug) }
                                )
                            }
                        }

                        // SHELF 1: PHIM MỚI CẬP NHẬT
                        if (latestMovies.size > 6) {
                            item(key = "shelf_latest") {
                                MovieShelfRow(
                                    title = "🌟 Phim Mới Cập Nhật",
                                    movies = latestMovies.drop(6),
                                    onViewAll = { viewAllCategory = "latest" to "Phim Mới Cập Nhật" },
                                    onMovieClick = { selectedMovieSlug = it.slug }
                                )
                            }
                        }

                        // SHELF 2: PHIM CHIẾU RẠP ĐẶC SẮC
                        if (cinemaMovies.isNotEmpty()) {
                            item(key = "shelf_cinema") {
                                MovieShelfRow(
                                    title = "🎬 Phim Chiếu Rạp",
                                    movies = cinemaMovies,
                                    onViewAll = { viewAllCategory = "phim-chieu-rap" to "Phim Chiếu Rạp" },
                                    onMovieClick = { selectedMovieSlug = it.slug }
                                )
                            }
                        }

                        // SHELF 3: PHIM BỘ ĐANG HOT
                        if (seriesMovies.isNotEmpty()) {
                            item(key = "shelf_series") {
                                MovieShelfRow(
                                    title = "🍿 Phim Bộ Đang Hot",
                                    movies = seriesMovies,
                                    onViewAll = { viewAllCategory = "phim-bo" to "Phim Bộ" },
                                    onMovieClick = { selectedMovieSlug = it.slug }
                                )
                            }
                        }

                        // SHELF 4: PHIM LẺ ĐẶC SẮC
                        if (singleMovies.isNotEmpty()) {
                            item(key = "shelf_single") {
                                MovieShelfRow(
                                    title = "🎥 Phim Lẻ Đặc Sắc",
                                    movies = singleMovies,
                                    onViewAll = { viewAllCategory = "phim-le" to "Phim Lẻ" },
                                    onMovieClick = { selectedMovieSlug = it.slug }
                                )
                            }
                        }

                        // SHELF 5: PHIM CỔ TRANG ĐẶC TUYỂN
                        if (costumeMovies.isNotEmpty()) {
                            item(key = "shelf_costume") {
                                MovieShelfRow(
                                    title = "🏮 Phim Cổ Trang",
                                    movies = costumeMovies,
                                    onViewAll = { viewAllCategory = "co-trang" to "Phim Cổ Trang" },
                                    onMovieClick = { selectedMovieSlug = it.slug }
                                )
                            }
                        }

                        // SHELF 6: PHIM HÀN QUỐC
                        if (koreaMovies.isNotEmpty()) {
                            item(key = "shelf_korea") {
                                MovieShelfRow(
                                    title = "🇰🇷 Phim Hàn Quốc",
                                    movies = koreaMovies,
                                    onViewAll = { viewAllCategory = "han-quoc" to "Phim Hàn Quốc" },
                                    onMovieClick = { selectedMovieSlug = it.slug }
                                )
                            }
                        }

                        // SHELF 7: PHIM TRUNG QUỐC
                        if (chinaMovies.isNotEmpty()) {
                            item(key = "shelf_china") {
                                MovieShelfRow(
                                    title = "🏮 Phim Trung Quốc",
                                    movies = chinaMovies,
                                    onViewAll = { viewAllCategory = "trung-quoc" to "Phim Trung Quốc" },
                                    onMovieClick = { selectedMovieSlug = it.slug }
                                )
                            }
                        }

                        // SHELF 8: HOẠT HÌNH & ANIME
                        if (animeMovies.isNotEmpty()) {
                            item(key = "shelf_anime") {
                                MovieShelfRow(
                                    title = "🎌 Hoạt Hình & Anime",
                                    movies = animeMovies,
                                    onViewAll = { viewAllCategory = "hoat-hinh" to "Hoạt Hình & Anime" },
                                    onMovieClick = { selectedMovieSlug = it.slug }
                                )
                            }
                        }

                        // SHELF 9: SUBTEAM / THUYẾT MINH (VSMov)
                        if (subteamMovies.isNotEmpty()) {
                            item(key = "shelf_subteam") {
                                MovieShelfRow(
                                    title = "👥 Phim Thuyết Minh & Subteam",
                                    movies = subteamMovies,
                                    onViewAll = { viewAllCategory = "subteam" to "Subteam" },
                                    onMovieClick = { selectedMovieSlug = it.slug }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // =========================================================================
    // ADVANCED FILTER MODAL BOTTOM SHEET
    // =========================================================================
    if (showFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Tone.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Bộ lọc phim (${activeSource.badge})",
                        color = Tone.text,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Đặt lại",
                        color = Tone.accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable {
                            selectedCategory = ""
                            selectedGenre = ""
                            selectedCountry = ""
                            selectedYear = ""
                            viewAllCategory = null
                        }
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Section 1: Categories
                Text("📂 Danh mục", color = Tone.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                FlowChipsRow(
                    options = MovieFilterData.categories,
                    selected = selectedCategory,
                    onSelect = { selectedCategory = it }
                )

                Spacer(Modifier.height(16.dp))

                // Section 2: Genres
                Text("🎭 Thể loại", color = Tone.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                FlowChipsRow(
                    options = MovieFilterData.genres,
                    selected = selectedGenre,
                    onSelect = { selectedGenre = it }
                )

                Spacer(Modifier.height(16.dp))

                // Section 3: Countries
                Text("🌏 Quốc gia", color = Tone.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                FlowChipsRow(
                    options = MovieFilterData.countries,
                    selected = selectedCountry,
                    onSelect = { selectedCountry = it }
                )

                Spacer(Modifier.height(16.dp))

                // Section 4: Years
                Text("📅 Năm phát hành", color = Tone.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                FlowChipsRow(
                    options = MovieFilterData.years,
                    selected = selectedYear,
                    onSelect = { selectedYear = it }
                )

                Spacer(Modifier.height(24.dp))

                // Apply Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Tone.accent)
                        .clickable {
                            showFilterSheet = false
                            viewAllCategory = null
                        }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Áp dụng bộ lọc", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }

    // =========================================================================
    // MOVIE DETAIL BOTTOM SHEET
    // =========================================================================
    if (selectedMovieSlug != null) {
        ModalBottomSheet(
            onDismissRequest = { selectedMovieSlug = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Tone.surface,
            scrimColor = Color.Black.copy(alpha = 0.65f)
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.88f)
            ) {
                if (isDetailLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Tone.accent, strokeWidth = 3.dp)
                    }
                } else if (movieDetail != null) {
                    val detail = movieDetail!!
                    MovieDetailContent(
                        detail = detail,
                        onPlayEpisode = { sIdx, eIdx ->
                            val epName = detail.servers.getOrNull(sIdx)?.episodes?.getOrNull(eIdx)?.name ?: "Tập ${eIdx + 1}"
                            MovieStore.markEpisodeWatched(context, detail, sIdx, eIdx, epName)
                            OnlinePlayerActivity.launch(context, detail, sIdx, eIdx)
                        }
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Không thể tải thông tin phim.", color = Tone.text.copy(alpha = 0.6f))
                    }
                }
            }
        }
    }
}

// =============================================================================
// SUB-COMPONENTS
// =============================================================================

@Composable
private fun FlowChipsRow(
    options: List<FilterOption>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { opt ->
            val isSelected = selected == opt.slug
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) Tone.accent else Tone.surfaceHigh)
                    .border(
                        0.5.dp,
                        if (isSelected) Tone.accent else Tone.outline.copy(alpha = 0.4f),
                        RoundedCornerShape(10.dp)
                    )
                    .clickable { onSelect(if (isSelected) "" else opt.slug) }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Text(
                    text = opt.label,
                    color = if (isSelected) Color.Black else Tone.text.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun ActiveChip(
    label: String,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Tone.accent.copy(alpha = 0.18f))
            .border(0.5.dp, Tone.accent, RoundedCornerShape(12.dp))
            .clickable(onClick = onDismiss)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text = label, color = Tone.accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Icon(imageVector = TmIcons.Close, contentDescription = null, tint = Tone.accent, modifier = Modifier.size(12.dp))
    }
}

@Composable
private fun SourceTabButton(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) Tone.accent else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = title,
            color = if (isSelected) Color.Black else Tone.text.copy(alpha = 0.7f),
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

/**
 * Slide Hero nhiều phim với Pager và Auto-scroll
 */
@Composable
private fun MovieHeroCarousel(
    movies: List<MovieItem>,
    onMovieClick: (MovieItem) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { movies.size })

    // Auto-scroll every 5.5s
    LaunchedEffect(pagerState, movies.size) {
        if (movies.size > 1) {
            while (true) {
                delay(5500)
                if (!pagerState.isScrollInProgress) {
                    val next = (pagerState.currentPage + 1) % movies.size
                    pagerState.animateScrollToPage(next)
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Tone.surfaceHigh)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val movie = movies.getOrNull(page) ?: return@HorizontalPager

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { onMovieClick(movie) }
                ) {
                    // Backdrop / Poster Image
                    AsyncMovieImage(
                        url = movie.posterUrl.ifBlank { movie.thumbUrl },
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )

                    // Gradient Scrim
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.45f),
                                        Color.Black.copy(alpha = 0.92f)
                                    ),
                                    startY = 40f
                                )
                            )
                    )

                    // Info Overlay
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomStart)
                            .padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (movie.rating.isNotBlank()) {
                                BadgePill(text = "⭐ ${movie.rating}", bg = Tone.accent, textColor = Color.Black)
                                Spacer(Modifier.width(6.dp))
                            }
                            if (movie.quality.isNotBlank()) {
                                BadgePill(text = movie.quality, bg = Color.White.copy(alpha = 0.2f), textColor = Color.White)
                                Spacer(Modifier.width(6.dp))
                            }
                            if (movie.lang.isNotBlank()) {
                                BadgePill(text = movie.lang, bg = Color.Black.copy(alpha = 0.5f), textColor = Tone.accent)
                                Spacer(Modifier.width(6.dp))
                            }
                            if (movie.year.isNotBlank()) {
                                BadgePill(text = movie.year, bg = Color.White.copy(alpha = 0.15f), textColor = Color.White)
                            }
                        }

                        Spacer(Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = movie.name,
                                    color = Color.White,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (movie.originName.isNotBlank()) {
                                    Text(
                                        text = movie.originName,
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Spacer(Modifier.width(10.dp))

                            // Play pill button
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Tone.accent)
                                    .clickable { onMovieClick(movie) }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = TmIcons.PlaySolid,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "Xem",
                                        color = Color.Black,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Indicator Dots inside bottom right
            if (movies.size > 1) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(movies.size) { index ->
                        val isCurrent = pagerState.currentPage == index
                        Box(
                            modifier = Modifier
                                .height(4.dp)
                                .width(if (isCurrent) 16.dp else 5.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (isCurrent) Tone.accent else Color.White.copy(alpha = 0.4f))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MovieContinueShelfRow(
    title: String,
    records: List<MovieWatchProgress>,
    onViewAll: () -> Unit,
    onRecordClick: (MovieWatchProgress) -> Unit,
    onRemoveRecord: ((MovieWatchProgress) -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = Tone.text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Xem tất cả ›",
                color = Tone.accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(onClick = onViewAll)
            )
        }

        Spacer(Modifier.height(6.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(records, key = { it.slug }) { record ->
                Box(modifier = Modifier.width(135.dp)) {
                    MovieContinueCard(
                        record = record,
                        onClick = { onRecordClick(record) },
                        onRemove = if (onRemoveRecord != null) {
                            { onRemoveRecord(record) }
                        } else null
                    )
                }
            }
        }
    }
}

@Composable
private fun MovieContinueCard(
    record: MovieWatchProgress,
    onClick: () -> Unit,
    onRemove: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f)
                .clip(RoundedCornerShape(10.dp))
                .background(Tone.surfaceHigh)
        ) {
            AsyncMovieImage(
                url = record.posterUrl.ifBlank { record.thumbUrl },
                modifier = Modifier.fillMaxSize()
            )

            // Play icon overlay in center
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .align(Alignment.Center)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = TmIcons.PlaySolid,
                    contentDescription = null,
                    tint = Tone.accent,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Episode Badge
            if (record.episodeName.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = record.episodeName,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Quick single-item remove button on top-right
            if (onRemove != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.7f))
                        .clickable { onRemove() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = TmIcons.Close,
                        contentDescription = "Xóa khỏi lịch sử",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(12.dp)
                    )
                }
            }

            // Progress bar at bottom
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Color.White.copy(alpha = 0.3f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(record.progressFraction)
                        .fillMaxHeight()
                        .background(Tone.accent)
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        Text(
            text = record.name,
            color = Tone.text,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        val progressPercent = (record.progressFraction * 100).toInt()
        Text(
            text = if (record.isFinished) "Đã hoàn thành" else "Còn lại • $progressPercent%",
            color = Tone.accent,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun MovieShelfRow(
    title: String,
    movies: List<MovieItem>,
    onViewAll: () -> Unit,
    onMovieClick: (MovieItem) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = Tone.text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Xem tất cả ›",
                color = Tone.accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(onClick = onViewAll)
            )
        }

        Spacer(Modifier.height(6.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(movies, key = { it.slug }) { movie ->
                Box(modifier = Modifier.width(120.dp)) {
                    MoviePosterCard(movie = movie, onClick = { onMovieClick(movie) })
                }
            }
        }
    }
}

@Composable
private fun MoviePosterCard(
    movie: MovieItem,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onRemoveItem: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val cloudSyncedList by MovieStore.cloudSyncedFlow.collectAsState()
    val cloudRecord = cloudSyncedList.firstOrNull { it.slug == movie.slug }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(10.dp))
                .background(Tone.surfaceHigh)
                .then(
                    if (isSelected) Modifier.border(2.dp, Tone.accent, RoundedCornerShape(10.dp))
                    else Modifier
                )
        ) {
            AsyncMovieImage(
                url = movie.thumbUrl.ifBlank { movie.posterUrl },
                modifier = Modifier.fillMaxSize()
            )

            // Top-right selection checkbox or quick remove button (for History screen)
            if (isSelectionMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) Tone.accent else Color.Black.copy(alpha = 0.65f))
                        .border(1.5.dp, if (isSelected) Tone.accent else Color.White.copy(alpha = 0.8f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = TmIcons.Check,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            } else if (onRemoveItem != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.72f))
                        .clickable { onRemoveItem() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = TmIcons.Close,
                        contentDescription = "Xóa phim này",
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(13.dp)
                    )
                }
            }

            // Top-left badge: Rating or Episode
            if (movie.rating.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "⭐ ${movie.rating}",
                        color = Tone.accent,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else if (movie.episodeCurrent.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Tone.accent)
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = movie.episodeCurrent,
                        color = Color.Black,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Top-right or Bottom-left badge: Cloud Sync status ("☁️ Tele" or "🔔 +N tập mới")
            if (cloudRecord != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (cloudRecord.hasNewEpisodes) Tone.accent else Color(0xDD123829))
                        .border(
                            0.5.dp,
                            if (cloudRecord.hasNewEpisodes) Color.Black else Tone.accent.copy(alpha = 0.8f),
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (cloudRecord.hasNewEpisodes) {
                            "🔔 +${cloudRecord.newEpisodesCount} mới"
                        } else if (cloudRecord.pulledEpisodesText.equals("full", true) || cloudRecord.latestKnownEpisodes <= 1) {
                            "☁️ Đã kéo"
                        } else {
                            "☁️ Tele (${cloudRecord.pulledUpToEpisode})"
                        },
                        color = if (cloudRecord.hasNewEpisodes) Color.Black else Tone.accent,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Bottom-right badge: Lang (Vietsub / TM)
            if (movie.lang.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = movie.lang,
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        Text(
            text = movie.name,
            color = Tone.text,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = if (movie.year.isNotBlank()) "${movie.year} • ${movie.quality}" else movie.quality,
            color = Tone.text.copy(alpha = 0.5f),
            fontSize = 11.sp,
            maxLines = 1
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MovieDetailContent(
    detail: MovieDetail,
    onPlayEpisode: (serverIdx: Int, epIdx: Int) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedServerIdx by remember(detail.movie.slug, detail.movie.source) { mutableIntStateOf(0) }
    var expandedDescription by remember(detail.movie.slug, detail.movie.source) { mutableStateOf(false) }
    var showCloudSheet by remember { mutableStateOf(false) }
    var isCloudMode by remember { mutableStateOf(false) }
    var customRangeInput by remember { mutableStateOf("") }

    val continueWatchingList by MovieStore.continueWatchingFlow.collectAsState()
    val historyList by MovieStore.historyFlow.collectAsState()
    val savedProgress = continueWatchingList.firstOrNull { it.slug == detail.movie.slug }
        ?: historyList.firstOrNull { it.slug == detail.movie.slug }
    val watchlist by MovieStore.watchlistFlow.collectAsState()
    val isFollowed = watchlist.any { it.slug == detail.movie.slug }
    val cloudSyncedList by MovieStore.cloudSyncedFlow.collectAsState()
    val cloudSync = cloudSyncedList.firstOrNull { it.slug == detail.movie.slug }

    val activeServer = detail.servers.getOrNull(selectedServerIdx) ?: detail.servers.firstOrNull()
    val epList = activeServer?.episodes ?: emptyList()
    val hasUnpulledNewEps = cloudSync != null && epList.size > cloudSync.pulledUpToEpisode && cloudSync.pulledUpToEpisode > 0
    val newEpCount = if (hasUnpulledNewEps) epList.size - cloudSync!!.pulledUpToEpisode else 0

    LaunchedEffect(detail.movie.slug, epList.size) {
        if (epList.isNotEmpty() && cloudSync != null && epList.size > cloudSync.latestKnownEpisodes) {
            MovieStore.updateCloudLatestEpisodes(context, detail.movie.slug, epList.size)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // TOP HEADER: Poster + Info
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(105.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Tone.surfaceHigh)
            ) {
                AsyncMovieImage(
                    url = detail.movie.thumbUrl.ifBlank { detail.movie.posterUrl },
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = detail.movie.name,
                    color = Tone.text,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (detail.movie.originName.isNotBlank()) {
                    Text(
                        text = detail.movie.originName,
                        color = Tone.text.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(6.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (detail.movie.rating.isNotBlank()) {
                        BadgePill("⭐ ${detail.movie.rating}", bg = Tone.accent, textColor = Color.Black)
                    }
                    if (detail.movie.year.isNotBlank()) {
                        BadgePill(detail.movie.year, bg = Tone.surfaceHigh, textColor = Tone.text)
                    }
                    if (detail.movie.quality.isNotBlank()) {
                        BadgePill(detail.movie.quality, bg = Tone.surfaceHigh, textColor = Tone.accent)
                    }
                    if (detail.movie.lang.isNotBlank()) {
                        BadgePill(detail.movie.lang, bg = Tone.surfaceHigh, textColor = Tone.text)
                    }
                }

                Spacer(Modifier.height(6.dp))

                if (detail.categories.isNotEmpty()) {
                    Text(
                        text = "Thể loại: " + detail.categories.joinToString(", "),
                        color = Tone.text.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (detail.countries.isNotEmpty()) {
                    Text(
                        text = "Quốc gia: " + detail.countries.joinToString(", "),
                        color = Tone.text.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (detail.time.isNotBlank()) {
                    Text(
                        text = "Thời lượng: ${detail.time}",
                        color = Tone.text.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // CONTINUE WATCHING BUTTON (If progress exists)
        if (savedProgress != null && !savedProgress.isFinished && savedProgress.positionMs > 5000L) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Tone.accent)
                    .clickable { onPlayEpisode(savedProgress.serverIndex, savedProgress.episodeIndex) }
                    .padding(vertical = 12.dp, horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = TmIcons.PlaySolid,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    val epText = if (savedProgress.episodeName.isNotBlank()) savedProgress.episodeName else "Tập ${savedProgress.episodeIndex + 1}"
                    val percent = (savedProgress.progressFraction * 100).toInt()
                    Text(
                        text = "Tiếp tục xem: $epText ($percent%)",
                        color = Color.Black,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        // PRIMARY ACTION BUTTONS (Xem phim ngay + Theo dõi + Kéo về Cloud)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Xem tập đầu / Xem ngay
            val hasResume = savedProgress != null && !savedProgress.isFinished && savedProgress.positionMs > 5000L
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (hasResume) Tone.surfaceHigh else Tone.accent)
                    .border(0.5.dp, if (hasResume) Tone.outline.copy(alpha = 0.5f) else Color.Transparent, RoundedCornerShape(10.dp))
                    .clickable { onPlayEpisode(selectedServerIdx, 0) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = TmIcons.PlaySolid,
                        contentDescription = null,
                        tint = if (hasResume) Tone.text else Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (hasResume) "Xem từ đầu" else "Xem ngay",
                        color = if (hasResume) Tone.text else Color.Black,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Theo dõi / Yêu thích
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isFollowed) Tone.accent.copy(alpha = 0.18f) else Tone.surfaceHigh)
                    .border(0.5.dp, if (isFollowed) Tone.accent else Tone.outline.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .clickable { MovieStore.toggleWatchlist(context, detail.movie) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isFollowed) TmIcons.Star else TmIcons.StarOutline,
                        contentDescription = null,
                        tint = if (isFollowed) Tone.accent else Tone.text,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (isFollowed) "Đã theo dõi" else "Theo dõi",
                        color = if (isFollowed) Tone.accent else Tone.text,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Kéo về Cloud (MovieBox)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        when {
                            hasUnpulledNewEps -> Tone.accent
                            cloudSync != null -> Tone.accent.copy(alpha = 0.18f)
                            else -> Tone.surfaceHigh
                        }
                    )
                    .border(
                        0.5.dp,
                        if (cloudSync != null) Tone.accent else Tone.outline.copy(alpha = 0.5f),
                        RoundedCornerShape(10.dp)
                    )
                    .clickable { showCloudSheet = true }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (cloudSync != null && !hasUnpulledNewEps) TmIcons.Check else TmIcons.Download,
                        contentDescription = null,
                        tint = if (hasUnpulledNewEps) Color.Black else Tone.accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = when {
                            hasUnpulledNewEps -> "⚡ +$newEpCount tập mới"
                            cloudSync != null -> "✓ Đã kéo (${cloudSync.pulledEpisodesCount(epList.size)}/${epList.size.coerceAtLeast(cloudSync.pulledUpToEpisode)})"
                            else -> "Về Cloud"
                        },
                        color = when {
                            hasUnpulledNewEps -> Color.Black
                            cloudSync != null -> Tone.accent
                            else -> Tone.text
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // DESCRIPTION
        if (detail.content.isNotBlank()) {
            Text(
                text = "Nội dung:",
                color = Tone.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = detail.content,
                color = Tone.text.copy(alpha = 0.75f),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                maxLines = if (expandedDescription) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { expandedDescription = !expandedDescription }
            )
            if (!expandedDescription && detail.content.length > 150) {
                Text(
                    text = "Xem thêm...",
                    color = Tone.accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { expandedDescription = true }
                )
            }
            Spacer(Modifier.height(14.dp))
        }

        // SERVERS TABS & MODE SWITCHER
        if (detail.servers.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tập phim (${epList.size} tập):",
                    color = Tone.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                // Mode switch chips
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Tone.surfaceHigh)
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (!isCloudMode) Tone.accent else Color.Transparent)
                            .clickable { isCloudMode = false }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Xem online",
                            fontSize = 11.sp,
                            fontWeight = if (!isCloudMode) FontWeight.Bold else FontWeight.Normal,
                            color = if (!isCloudMode) Color.Black else Tone.text.copy(alpha = 0.7f)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isCloudMode) Tone.accent else Color.Transparent)
                            .clickable { isCloudMode = true }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Kéo về Cloud",
                            fontSize = 11.sp,
                            fontWeight = if (isCloudMode) FontWeight.Bold else FontWeight.Normal,
                            color = if (isCloudMode) Color.Black else Tone.text.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            if (detail.servers.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    detail.servers.forEachIndexed { sIdx, srv ->
                        val isSel = (sIdx == selectedServerIdx)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) Tone.accent else Tone.surfaceHigh)
                                .clickable { selectedServerIdx = sIdx }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = srv.serverName.replace(Regex("\\s+"), " ").trim(),
                                color = if (isSel) Color.Black else Tone.text,
                                fontSize = 12.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            // Status banner above episode grid (Watch progress in "Xem online" mode; Cloud Sync status in "Kéo về Cloud" mode)
            if (!isCloudMode && savedProgress != null && epList.isNotEmpty()) {
                val watchedCount = savedProgress.watchedEpisodesCount(epList.size)
                val lastEpLabel = savedProgress.episodeName.ifBlank { "Tập ${savedProgress.episodeIndex + 1}" }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Tone.surfaceHigh)
                        .border(0.5.dp, Tone.outline.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                        .clickable { onPlayEpisode(savedProgress.serverIndex, savedProgress.episodeIndex) }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "✓",
                            color = Tone.accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Đã xem $watchedCount/${epList.size} tập • Gần nhất: $lastEpLabel",
                            color = Tone.text,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = "Tiếp tục ›",
                        color = Tone.accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(8.dp))
            } else if (isCloudMode && cloudSync != null && epList.isNotEmpty()) {
                val pulledCount = cloudSync.pulledEpisodesCount(epList.size)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (hasUnpulledNewEps) Tone.accent.copy(alpha = 0.16f) else Tone.surfaceHigh)
                        .border(
                            0.5.dp,
                            if (hasUnpulledNewEps) Tone.accent else Tone.outline.copy(alpha = 0.35f),
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { showCloudSheet = true }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (hasUnpulledNewEps) "⚡" else "✓",
                            color = Tone.accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (hasUnpulledNewEps)
                                "Có ${epList.size - cloudSync.pulledUpToEpisode} tập mới chưa kéo về Tele"
                            else
                                "Đã kéo $pulledCount/${epList.size} tập về Tele Cloud",
                            color = if (hasUnpulledNewEps) Tone.accent else Tone.text,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = "Tùy chọn kéo ›",
                        color = Tone.accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            // EPISODES GRID
            if (epList.isNotEmpty()) {
                val chunked = epList.chunked(4)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    chunked.forEach { rowEps ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowEps.forEach { ep ->
                                val eIdx = epList.indexOf(ep)
                                val epNumber = eIdx + 1
                                val isWatched = savedProgress?.isEpisodeWatched(eIdx) == true
                                val isCurrentWatchedEp = savedProgress != null && savedProgress.episodeIndex == eIdx
                                val isPulledToCloud = cloudSync != null && cloudSync.isEpisodePulled(epNumber)
                                val isNewUnpulledEp = cloudSync != null && cloudSync.isEpisodeNew(epNumber, epList.size)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isCloudMode) {
                                                when {
                                                    isNewUnpulledEp -> Tone.accent.copy(alpha = 0.22f)
                                                    isPulledToCloud -> Tone.surfaceHigh.copy(alpha = 0.9f)
                                                    else -> Tone.surfaceHigh
                                                }
                                            } else {
                                                when {
                                                    isCurrentWatchedEp -> Tone.accent.copy(alpha = 0.22f)
                                                    isWatched -> Tone.surfaceHigh.copy(alpha = 0.9f)
                                                    else -> Tone.surfaceHigh
                                                }
                                            }
                                        )
                                        .border(
                                            width = if ((isCloudMode && isNewUnpulledEp) || (!isCloudMode && isCurrentWatchedEp)) 1.5.dp else 0.5.dp,
                                            color = if (isCloudMode) {
                                                when {
                                                    isNewUnpulledEp -> Tone.accent
                                                    isPulledToCloud -> Tone.accent.copy(alpha = 0.45f)
                                                    else -> Tone.outline.copy(alpha = 0.5f)
                                                }
                                            } else {
                                                when {
                                                    isCurrentWatchedEp -> Tone.accent
                                                    isWatched -> Tone.accent.copy(alpha = 0.45f)
                                                    else -> Tone.outline.copy(alpha = 0.5f)
                                                }
                                            },
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            if (isCloudMode) {
                                                scope.launch {
                                                    val epNum = "$epNumber"
                                                    MovieCloudDownloader.pullToCloud(
                                                        context = context,
                                                        slug = detail.movie.slug,
                                                        movieTitle = detail.movie.name,
                                                        source = detail.movie.source,
                                                        epRange = epNum,
                                                        totalEpisodes = epList.size,
                                                        movie = detail.movie
                                                    )
                                                }
                                            } else {
                                                onPlayEpisode(selectedServerIdx, eIdx)
                                            }
                                        }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (isCloudMode) {
                                            if (isPulledToCloud) {
                                                Text(
                                                    text = "✓",
                                                    color = Tone.accent,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.ExtraBold
                                                )
                                                Spacer(Modifier.width(3.dp))
                                            } else if (isNewUnpulledEp) {
                                                Text(
                                                    text = "⚡",
                                                    color = Tone.accent,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Spacer(Modifier.width(3.dp))
                                            } else {
                                                Icon(
                                                    imageVector = TmIcons.Download,
                                                    contentDescription = null,
                                                    tint = Tone.text.copy(alpha = 0.7f),
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Spacer(Modifier.width(3.dp))
                                            }
                                            Text(
                                                text = ep.name,
                                                color = if (isNewUnpulledEp) Tone.accent else Tone.text,
                                                fontSize = 12.sp,
                                                fontWeight = if (isPulledToCloud || isNewUnpulledEp) FontWeight.Bold else FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        } else {
                                            if (isWatched) {
                                                Text(
                                                    text = "✓",
                                                    color = Tone.accent,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.ExtraBold
                                                )
                                                Spacer(Modifier.width(3.dp))
                                            }
                                            Text(
                                                text = ep.name,
                                                color = if (isCurrentWatchedEp) Tone.accent else Tone.text,
                                                fontSize = 12.sp,
                                                fontWeight = if (isWatched) FontWeight.Bold else FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                            if (rowEps.size < 4) {
                                repeat(4 - rowEps.size) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = "Không có tập nào trên server này.",
                    color = Tone.text.copy(alpha = 0.5f),
                    fontSize = 13.sp
                )
            }
        } else {
            Text(
                text = "Hiện chưa có link phát cho bộ phim này.",
                color = Tone.text.copy(alpha = 0.5f),
                fontSize = 13.sp
            )
        }

        Spacer(Modifier.height(30.dp))
    }

    // CLOUD PULL OPTIONS SHEET / DIALOG
    if (showCloudSheet) {
        val currentDest by MovieStore.cloudDestinationFlow.collectAsState()
        var selectedBot by remember { mutableStateOf(MovieStore.selectedBotFlow.value) }
        ModalBottomSheet(
            onDismissRequest = { showCloudSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Tone.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Kéo phim về Tele Cloud",
                        color = Tone.text,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    BadgePill(
                        text = currentDest.displayLabel,
                        bg = Tone.surfaceHigh,
                        textColor = Tone.accent
                    )
                }

                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Phim: ${detail.movie.name} (${detail.movie.source.displayName})",
                    color = Tone.text.copy(alpha = 0.6f),
                    fontSize = 13.sp
                )
                if (cloudSync != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "☁️ Đã kéo tới tập ${cloudSync.pulledUpToEpisode}/${epList.size} • Khoảng gần nhất: ${cloudSync.pulledEpisodesText}",
                        color = Tone.accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(Modifier.height(10.dp))
                CloudDestinationSelectorCard()

                Spacer(Modifier.height(12.dp))

                // Chọn Bot kéo phim
                Text(
                    text = "🤖 Chọn Bot kéo phim:",
                    color = Tone.text,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isBot1 = selectedBot == MovieCloudDownloader.BOT_1
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isBot1) Tone.accent.copy(alpha = 0.18f) else Tone.surfaceHigh)
                            .border(
                                width = if (isBot1) 1.5.dp else 0.5.dp,
                                color = if (isBot1) Tone.accent else Tone.outline.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                selectedBot = MovieCloudDownloader.BOT_1
                                MovieStore.setSelectedBot(context, MovieCloudDownloader.BOT_1)
                            }
                            .padding(vertical = 10.dp, horizontal = 10.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🤖", fontSize = 13.sp)
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "Bot 1 (Chính)",
                                    color = if (isBot1) Tone.accent else Tone.text,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "@${MovieCloudDownloader.BOT_1}",
                                color = Tone.muted,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    val isBot2 = selectedBot == MovieCloudDownloader.BOT_2
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isBot2) Tone.accent.copy(alpha = 0.18f) else Tone.surfaceHigh)
                            .border(
                                width = if (isBot2) 1.5.dp else 0.5.dp,
                                color = if (isBot2) Tone.accent else Tone.outline.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                selectedBot = MovieCloudDownloader.BOT_2
                                MovieStore.setSelectedBot(context, MovieCloudDownloader.BOT_2)
                            }
                            .padding(vertical = 10.dp, horizontal = 10.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("💻", fontSize = 13.sp)
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "Bot 2 (Laptop)",
                                    color = if (isBot2) Tone.accent else Tone.text,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "@${MovieCloudDownloader.BOT_2}",
                                color = Tone.muted,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                // DANH SÁCH TẬP PHIM & TRẠNG THÁI KÉO CLOUD
                if (epList.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "📋 Trạng thái các tập phim",
                                color = Tone.text,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "(${epList.size} tập)",
                                color = Tone.muted,
                                fontSize = 12.sp
                            )
                        }
                        if (cloudSync != null) {
                            val pulledCount = cloudSync.pulledEpisodesCount(epList.size)
                            Text(
                                text = "✓ Đã kéo: $pulledCount/${epList.size}",
                                color = Tone.accent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    // Chú thích phân biệt rõ ràng: V. Đã kéo vs ⚡ Tập mới
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Tone.surfaceHigh.copy(alpha = 0.5f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("✓", color = Tone.accent, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                            Spacer(Modifier.width(4.dp))
                            Text("V. Đã kéo về Tele", color = Tone.text.copy(alpha = 0.8f), fontSize = 11.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("⚡", color = Tone.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(4.dp))
                            Text("Tập mới (Chưa kéo)", color = Tone.accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    var showAllSheetEps by remember { mutableStateOf(epList.size <= 24) }
                    val epChunked = epList.chunked(4)
                    val displayedChunks = if (showAllSheetEps) epChunked else epChunked.take(4)

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        displayedChunks.forEach { rowEps ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                rowEps.forEach { ep ->
                                    val eIdx = epList.indexOf(ep)
                                    val epNumber = eIdx + 1
                                    val isPulled = cloudSync != null && cloudSync.isEpisodePulled(epNumber)
                                    val isNew = cloudSync != null && cloudSync.isEpisodeNew(epNumber, epList.size)

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                when {
                                                    isNew -> Tone.accent.copy(alpha = 0.22f)
                                                    isPulled -> Tone.surfaceHigh
                                                    else -> Tone.surfaceHigh.copy(alpha = 0.6f)
                                                }
                                            )
                                            .border(
                                                width = if (isNew) 1.5.dp else 0.5.dp,
                                                color = when {
                                                    isNew -> Tone.accent
                                                    isPulled -> Tone.accent.copy(alpha = 0.45f)
                                                    else -> Tone.outline.copy(alpha = 0.3f)
                                                },
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .clickable {
                                                showCloudSheet = false
                                                scope.launch {
                                                    MovieCloudDownloader.pullToCloud(
                                                        context = context,
                                                        slug = detail.movie.slug,
                                                        movieTitle = detail.movie.name,
                                                        source = detail.movie.source,
                                                        epRange = "$epNumber",
                                                        totalEpisodes = epList.size,
                                                        movie = detail.movie,
                                                        targetBot = selectedBot
                                                    )
                                                }
                                            }
                                            .padding(vertical = 8.dp, horizontal = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            if (isPulled) {
                                                Text(
                                                    text = "✓",
                                                    color = Tone.accent,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.ExtraBold
                                                )
                                                Spacer(Modifier.width(3.dp))
                                            } else if (isNew) {
                                                Text(
                                                    text = "⚡",
                                                    color = Tone.accent,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Spacer(Modifier.width(2.dp))
                                            }
                                            Text(
                                                text = ep.name,
                                                color = if (isNew) Tone.accent else Tone.text,
                                                fontSize = 11.sp,
                                                fontWeight = if (isNew || isPulled) FontWeight.Bold else FontWeight.Normal,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                                if (rowEps.size < 4) {
                                    repeat(4 - rowEps.size) {
                                        Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }

                        if (epList.size > 24 && !showAllSheetEps) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Tone.surfaceHigh)
                                    .clickable { showAllSheetEps = true }
                                    .padding(vertical = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Xem tất cả ${epList.size} tập (còn ${epList.size - 16} tập)...",
                                    color = Tone.accent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Nếu có tập mới chưa kéo: Nút kéo nhanh các tập mới
                if (hasUnpulledNewEps && cloudSync != null) {
                    val nextEp = cloudSync.pulledUpToEpisode + 1
                    val rangeStr = if (nextEp == epList.size) "$nextEp" else "$nextEp-${epList.size}"
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Tone.accent)
                            .clickable {
                                showCloudSheet = false
                                scope.launch {
                                    MovieCloudDownloader.pullToCloud(
                                        context = context,
                                        slug = detail.movie.slug,
                                        movieTitle = detail.movie.name,
                                        source = detail.movie.source,
                                        epRange = rangeStr,
                                        totalEpisodes = epList.size,
                                        movie = detail.movie,
                                        targetBot = selectedBot
                                    )
                                }
                            }
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = TmIcons.Download,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "⚡ Kéo ngay tập mới (Tập $rangeStr)",
                                    color = Color.Black,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Kéo thêm ${epList.size - cloudSync.pulledUpToEpisode} tập mới phát hành về Tele",
                                    color = Color.Black.copy(alpha = 0.75f),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }

                // Option 1: Kéo toàn bộ phim
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Tone.surfaceHigh)
                        .clickable {
                            showCloudSheet = false
                            scope.launch {
                                MovieCloudDownloader.pullToCloud(
                                    context = context,
                                    slug = detail.movie.slug,
                                    movieTitle = detail.movie.name,
                                    source = detail.movie.source,
                                    epRange = "all",
                                    totalEpisodes = epList.size,
                                    movie = detail.movie,
                                    targetBot = selectedBot
                                )
                            }
                        }
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Tone.accent),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = TmIcons.Download,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Kéo toàn bộ phim (Tất cả tập)",
                                color = Tone.text,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Tải toàn bộ ${epList.size} tập về ${currentDest.displayLabel}",
                                color = Tone.text.copy(alpha = 0.6f),
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Option 2: Kéo các tập còn lại
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Tone.surfaceHigh)
                        .clickable {
                            showCloudSheet = false
                            scope.launch {
                                MovieCloudDownloader.pullToCloud(
                                    context = context,
                                    slug = detail.movie.slug,
                                    movieTitle = detail.movie.name,
                                    source = detail.movie.source,
                                    epRange = "remaining",
                                    totalEpisodes = epList.size,
                                    movie = detail.movie,
                                    targetBot = selectedBot
                                )
                            }
                        }
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Tone.surface),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = TmIcons.Checklist,
                                contentDescription = null,
                                tint = Tone.accent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Kéo các tập còn lại",
                                color = Tone.text,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Tự động bỏ qua các tập đã tải lên trước đó",
                                color = Tone.text.copy(alpha = 0.6f),
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Option 3: Nhập khoảng tập tùy chọn
                Text(
                    text = "Hoặc nhập khoảng tập cần kéo:",
                    color = Tone.text,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Tone.surfaceHigh)
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        BasicTextField(
                            value = customRangeInput,
                            onValueChange = { customRangeInput = it },
                            textStyle = TextStyle(color = Tone.text, fontSize = 14.sp),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { innerTextField ->
                                if (customRangeInput.isEmpty()) {
                                    Text(
                                        text = "Ví dụ: 1-10 hoặc 12,15",
                                        color = Tone.text.copy(alpha = 0.4f),
                                        fontSize = 13.sp
                                    )
                                }
                                innerTextField()
                            }
                        )
                    }

                    Box(
                        modifier = Modifier
                            .height(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (customRangeInput.isNotBlank()) Tone.accent else Tone.surfaceHigh)
                            .clickable(enabled = customRangeInput.isNotBlank()) {
                                val input = customRangeInput.trim()
                                showCloudSheet = false
                                scope.launch {
                                    MovieCloudDownloader.pullToCloud(
                                        context = context,
                                        slug = detail.movie.slug,
                                        movieTitle = detail.movie.name,
                                        source = detail.movie.source,
                                        epRange = input,
                                        totalEpisodes = epList.size,
                                        movie = detail.movie,
                                        targetBot = selectedBot
                                    )
                                }
                            }
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Kéo ngay",
                            color = if (customRangeInput.isNotBlank()) Color.Black else Tone.text.copy(alpha = 0.4f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Option 4: Đánh dấu & Tự động theo dõi tập mới để kéo về Tele
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Tone.surfaceHigh)
                        .clickable {
                            if (cloudSync != null) {
                                MovieStore.setCloudAutoPull(context, detail.movie.slug, !cloudSync.autoPullNewEpisodes)
                            } else {
                                MovieStore.markCloudSynced(
                                    context = context,
                                    movie = detail.movie,
                                    epRange = "all",
                                    totalEpisodes = epList.size,
                                    autoPull = true
                                )
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "🤖 Tự động kéo & báo tập mới",
                            color = Tone.text,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (cloudSync != null)
                                "Tự động kiểm tra và kéo tập mới về Tele khi có cập nhật"
                            else
                                "Bấm để đánh dấu phim đã kéo Tele & bật tự động kéo tập mới",
                            color = Tone.text.copy(alpha = 0.6f),
                            fontSize = 11.sp
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    val autoEnabled = cloudSync?.autoPullNewEpisodes == true
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (autoEnabled) Tone.accent else Tone.surface)
                            .border(0.5.dp, if (autoEnabled) Tone.accent else Tone.outline, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (autoEnabled) "Đang BẬT" else "TẮT",
                            color = if (autoEnabled) Color.Black else Tone.text.copy(alpha = 0.7f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (cloudSync != null) {
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .align(Alignment.End)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                MovieStore.removeCloudSynced(context, detail.movie.slug)
                                showCloudSheet = false
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Bỏ đánh dấu đã kéo Tele",
                            color = Tone.danger,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun BadgePill(text: String, bg: Color, textColor: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun AsyncMovieImage(
    url: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current
    var bitmap by remember(url) { mutableStateOf(MovieImageLoader.peek(url)) }

    LaunchedEffect(url) {
        if (bitmap == null && url.isNotBlank()) {
            bitmap = MovieImageLoader.load(context, url)
        }
    }

    val img = bitmap
    if (img != null) {
        Image(
            bitmap = img,
            contentDescription = null,
            contentScale = contentScale,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier.background(Tone.surfaceHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = TmIcons.Trailer,
                contentDescription = null,
                tint = Tone.text.copy(alpha = 0.15f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudDestinationSelectorCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { MovieStore.init(context) }
    val currentDest by MovieStore.cloudDestinationFlow.collectAsState()
    var showPicker by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Tone.surfaceHigh)
            .border(0.5.dp, Tone.outline.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .clickable { showPicker = true }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Tone.accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = TmIcons.Folder,
                    contentDescription = null,
                    tint = Tone.accent,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = "Đích đến: ${currentDest.displayLabel}",
                    color = Tone.text,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Nhấn để chọn Nhóm / Topic hoặc Tin nhắn đã lưu",
                    color = Tone.text.copy(alpha = 0.55f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Đổi đích ›",
            color = Tone.accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }

    if (showPicker) {
        CloudDestinationPickerModal(onDismiss = { showPicker = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudDestinationPickerModal(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentDest by MovieStore.cloudDestinationFlow.collectAsState()

    var chats by remember { mutableStateOf<List<com.tmplayer.data.ChatSummary>>(emptyList()) }
    var loadingChats by remember { mutableStateOf(true) }
    var filterQuery by remember { mutableStateOf("") }
    var selectedChatForTopics by remember { mutableStateOf<com.tmplayer.data.ChatSummary?>(null) }
    var forumTopics by remember { mutableStateOf<List<dev.g000sha256.tdl.dto.ForumTopic>>(emptyList()) }
    var loadingTopics by remember { mutableStateOf(false) }

    var showManualInput by remember { mutableStateOf(false) }
    var manualChatId by remember { mutableStateOf(currentDest.chatId.toString()) }
    var manualTopicId by remember { mutableStateOf(if (currentDest.topicId > 0) currentDest.topicId.toString() else "") }
    var manualTitle by remember { mutableStateOf(currentDest.chatTitle) }

    LaunchedEffect(Unit) {
        loadingChats = true
        chats = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                com.tmplayer.data.ChatRepository(com.tmplayer.data.Td.client).cachedChats(limit = 60).chats
            }.getOrDefault(emptyList())
        }
        loadingChats = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Tone.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (selectedChatForTopics != null) "Chọn Topic: ${selectedChatForTopics!!.title}"
                    else "Chọn đích đến trên Telegram",
                    color = Tone.text,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (selectedChatForTopics != null) {
                    Text(
                        text = "← Quay lại",
                        color = Tone.accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { selectedChatForTopics = null }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (selectedChatForTopics != null) {
                val chat = selectedChatForTopics!!
                if (loadingTopics) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Tone.accent, modifier = Modifier.size(26.dp))
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(340.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // General / No specific topic
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Tone.surfaceHigh)
                                .clickable {
                                    MovieStore.setCloudDestination(
                                        context,
                                        CloudDestination(
                                            chatId = chat.id,
                                            topicId = 0,
                                            chatTitle = chat.title,
                                            topicTitle = ""
                                        )
                                    )
                                    onDismiss()
                                }
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "💬 Luồng chung (${chat.title})",
                                color = Tone.text,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        forumTopics.forEach { topic ->
                            val tId = topic.info.forumTopicId
                            val tName = topic.info.name.ifBlank { "Topic #$tId" }
                            val isChosen = currentDest.chatId == chat.id && currentDest.topicId == tId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isChosen) Tone.accent.copy(alpha = 0.16f) else Tone.surfaceHigh)
                                    .border(
                                        0.5.dp,
                                        if (isChosen) Tone.accent else Color.Transparent,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable {
                                        MovieStore.setCloudDestination(
                                            context,
                                            CloudDestination(
                                                chatId = chat.id,
                                                topicId = tId,
                                                chatTitle = chat.title,
                                                topicTitle = tName
                                            )
                                        )
                                        onDismiss()
                                    }
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "# $tName",
                                    color = if (isChosen) Tone.accent else Tone.text,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "ID: $tId",
                                    color = Tone.text.copy(alpha = 0.5f),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            } else {
                // 1. Default MovieBox #bot (Topic 1)
                val isDefaultSelected = currentDest.isDefault
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isDefaultSelected) Tone.accent.copy(alpha = 0.16f) else Tone.surfaceHigh)
                        .border(
                            0.5.dp,
                            if (isDefaultSelected) Tone.accent else Tone.outline.copy(alpha = 0.3f),
                            RoundedCornerShape(12.dp)
                        )
                        .clickable {
                            MovieStore.resetCloudDestination(context)
                            onDismiss()
                        }
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "☁️ MovieBox Cloud › MovieBox (#43264)",
                            color = if (isDefaultSelected) Tone.accent else Tone.text,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Kho lưu trữ phim mặc định (Lệnh tự động gửi vào topic 1 #bot)",
                            color = Tone.text.copy(alpha = 0.6f),
                            fontSize = 11.sp
                        )
                    }
                    if (isDefaultSelected) {
                        Text("✓", color = Tone.accent, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }

                // 2. Saved Messages
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Tone.surfaceHigh)
                        .clickable {
                            scope.launch {
                                val savedChat = chats.firstOrNull { it.kind == com.tmplayer.data.ChatKind.Saved }
                                val myId = savedChat?.id ?: kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    runCatching { com.tmplayer.data.Td.myId() }.getOrDefault(0L)
                                }
                                if (myId != 0L) {
                                    MovieStore.setCloudDestination(
                                        context,
                                        CloudDestination(
                                            chatId = myId,
                                            topicId = 0,
                                            chatTitle = "Tin nhắn đã lưu",
                                            topicTitle = ""
                                        )
                                    )
                                    onDismiss()
                                }
                            }
                        }
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "🔖 Tin nhắn đã lưu (Saved Messages)",
                            color = Tone.text,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Gửi lệnh & link trực tiếp vào kho tin nhắn cá nhân của bạn",
                            color = Tone.text.copy(alpha = 0.6f),
                            fontSize = 11.sp
                        )
                    }
                }

                // 3. Manual ID input toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Nhóm & Kênh Telegram của bạn:",
                        color = Tone.text.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (showManualInput) "Ẩn nhập ID" else "⚙ Nhập ID thủ công",
                        color = Tone.accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { showManualInput = !showManualInput }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                if (showManualInput) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Tone.surfaceHigh)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .weight(1.2f)
                                    .height(38.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Tone.surface)
                                    .padding(horizontal = 10.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                BasicTextField(
                                    value = manualChatId,
                                    onValueChange = { manualChatId = it },
                                    textStyle = TextStyle(color = Tone.text, fontSize = 12.sp),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    decorationBox = { inner ->
                                        if (manualChatId.isEmpty()) {
                                            Text("Chat ID (VD: -100...)", color = Tone.text.copy(alpha = 0.4f), fontSize = 12.sp)
                                        }
                                        inner()
                                    }
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .weight(0.8f)
                                    .height(38.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Tone.surface)
                                    .padding(horizontal = 10.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                BasicTextField(
                                    value = manualTopicId,
                                    onValueChange = { manualTopicId = it },
                                    textStyle = TextStyle(color = Tone.text, fontSize = 12.sp),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    decorationBox = { inner ->
                                        if (manualTopicId.isEmpty()) {
                                            Text("Topic ID (Tùy chọn)", color = Tone.text.copy(alpha = 0.4f), fontSize = 12.sp)
                                        }
                                        inner()
                                    }
                                )
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Tone.surface)
                                    .padding(horizontal = 10.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                BasicTextField(
                                    value = manualTitle,
                                    onValueChange = { manualTitle = it },
                                    textStyle = TextStyle(color = Tone.text, fontSize = 12.sp),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    decorationBox = { inner ->
                                        if (manualTitle.isEmpty()) {
                                            Text("Tên gợi nhớ", color = Tone.text.copy(alpha = 0.4f), fontSize = 12.sp)
                                        }
                                        inner()
                                    }
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .height(38.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Tone.accent)
                                    .clickable {
                                        val cId = manualChatId.trim().toLongOrNull()
                                        val tId = manualTopicId.trim().toIntOrNull() ?: 0
                                        if (cId != null && cId != 0L) {
                                            MovieStore.setCloudDestination(
                                                context,
                                                CloudDestination(
                                                    chatId = cId,
                                                    topicId = tId,
                                                    chatTitle = manualTitle.trim().ifBlank { "Chat $cId" },
                                                    topicTitle = if (tId > 0) "Topic #$tId" else ""
                                                )
                                            )
                                            onDismiss()
                                        }
                                    }
                                    .padding(horizontal = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Lưu", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Filter chats input
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Tone.surfaceHigh)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    BasicTextField(
                        value = filterQuery,
                        onValueChange = { filterQuery = it },
                        textStyle = TextStyle(color = Tone.text, fontSize = 13.sp),
                        singleLine = true,
                        cursorBrush = SolidColor(Tone.accent),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            if (filterQuery.isEmpty()) {
                                Text("Tìm tên nhóm / kênh...", color = Tone.text.copy(alpha = 0.4f), fontSize = 12.sp)
                            }
                            inner()
                        }
                    )
                }

                // Chat list
                if (loadingChats) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Tone.accent, modifier = Modifier.size(24.dp))
                    }
                } else {
                    val filtered = remember(chats, filterQuery) {
                        chats.filter {
                            it.kind != com.tmplayer.data.ChatKind.Saved &&
                                (filterQuery.isBlank() || it.title.contains(filterQuery, ignoreCase = true))
                        }
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(230.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (filtered.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Không tìm thấy nhóm/kênh phù hợp", color = Tone.text.copy(alpha = 0.5f), fontSize = 12.sp)
                            }
                        } else {
                            filtered.take(35).forEach { chat ->
                                val isChosen = currentDest.chatId == chat.id
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isChosen) Tone.accent.copy(alpha = 0.14f) else Tone.surfaceHigh)
                                        .clickable {
                                            scope.launch {
                                                loadingTopics = true
                                                val topics = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                    runCatching {
                                                        com.tmplayer.data.Td.client
                                                            .getForumTopics(chat.id, "", 0, 0L, 0, 100)
                                                            .let { res ->
                                                                if (res is dev.g000sha256.tdl.TdlResult.Success) {
                                                                    res.result.topics.toList()
                                                                } else emptyList()
                                                            }
                                                    }.getOrDefault(emptyList())
                                                }
                                                loadingTopics = false
                                                if (topics.isNotEmpty()) {
                                                    forumTopics = topics
                                                    selectedChatForTopics = chat
                                                } else {
                                                    MovieStore.setCloudDestination(
                                                        context,
                                                        CloudDestination(
                                                            chatId = chat.id,
                                                            topicId = 0,
                                                            chatTitle = chat.title,
                                                            topicTitle = ""
                                                        )
                                                    )
                                                    onDismiss()
                                                }
                                            }
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = chat.title,
                                            color = if (isChosen) Tone.accent else Tone.text,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${chat.kind.label} • ID: ${chat.id}",
                                            color = Tone.text.copy(alpha = 0.5f),
                                            fontSize = 11.sp
                                        )
                                    }
                                    Text(
                                        text = "Chọn ›",
                                        color = Tone.accent,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
