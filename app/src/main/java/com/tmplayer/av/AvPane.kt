package com.tmplayer.av

import android.app.Activity
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.tmplayer.movie.*
import com.tmplayer.ui.icons.TmIcons
import com.tmplayer.ui.theme.Tone
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToLong

private enum class AvMode {
    Home, ForYou, Category, Search, Watchlist, History
}

private data class AvHomeShelf(
    val title: String,
    val slug: String,
    val items: List<MovieItem>
)

private class AvCatalogState {
    var loaded by mutableStateOf(false)
    var featured by mutableStateOf<List<MovieItem>>(emptyList())
    var shelves by mutableStateOf<List<AvHomeShelf>>(emptyList())
    var gridMovies by mutableStateOf<List<MovieItem>>(emptyList())
    var currentPage by mutableIntStateOf(1)
    var isLoading by mutableStateOf(false)
    var isLoadingMore by mutableStateOf(false)
    val homeScrollState = LazyListState()
    val gridScrollState = LazyGridState()
}

private val avSources = listOf(MovieSource.AVDB, MovieSource.MissAV, MovieSource.XXVN)

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvPane(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        AvStore.init(context)
        MovieStore.init(context)
    }

    val pinCode by AvStore.pinCodeFlow.collectAsState()
    val isUnlocked by AvStore.isSessionUnlockedFlow.collectAsState()
    val isTabVisible by AvStore.isTabVisibleFlow.collectAsState()
    val watchlist by AvStore.watchlistFlow.collectAsState()
    val history by AvStore.historyFlow.collectAsState()
    val preferredActresses by AvStore.preferredActressesFlow.collectAsState()
    val preferredPrefixes by AvStore.preferredPrefixesFlow.collectAsState()

    // Gate content behind PIN when set and not yet unlocked in this session
    if (pinCode.isNotEmpty() && !isUnlocked) {
        AvPinLockScreen(
            onVerify = { entered -> AvStore.verifyPin(entered) }
        )
        return
    }

    var currentSource by remember { mutableStateOf(MovieSource.AVDB) }
    var mode by remember { mutableStateOf(AvMode.Home) }
    var selectedCategorySlug by remember { mutableStateOf("") }
    var selectedCategoryTitle by remember { mutableStateOf("Mới cập nhật") }
    var searchQuery by remember { mutableStateOf("") }
    var submittedSearch by remember { mutableStateOf("") }

    var selectedMovie by remember { mutableStateOf<MovieItem?>(null) }
    var movieDetail by remember { mutableStateOf<MovieDetail?>(null) }
    var relatedMovies by remember { mutableStateOf<List<MovieItem>>(emptyList()) }
    var isLoadingDetail by remember { mutableStateOf(false) }
    var isLoadingRelated by remember { mutableStateOf(false) }

    var forYouMovies by remember { mutableStateOf<List<MovieItem>>(emptyList()) }
    var isLoadingForYou by remember { mutableStateOf(false) }

    var isHistoryEditMode by remember { mutableStateOf(false) }
    var selectedHistorySlugs by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showPrivacySheet by remember { mutableStateOf(false) }
    var isPullRefreshing by remember { mutableStateOf(false) }

    val catalogCache = remember { mutableStateMapOf<String, AvCatalogState>() }
    val activeKey = remember(currentSource, mode, selectedCategorySlug, submittedSearch) {
        "${currentSource.name}|${mode.name}|$selectedCategorySlug|$submittedSearch"
    }
    val activeCatalog = catalogCache.getOrPut(activeKey) { AvCatalogState() }

    // Load personalized "Đề xuất cho bạn" whenever source or user history/affinity changes
    LaunchedEffect(currentSource, history.firstOrNull()?.slug, watchlist.size, preferredActresses.firstOrNull(), preferredPrefixes.firstOrNull()) {
        isLoadingForYou = true
        forYouMovies = AvRepository.getRecommendationsForYou(
            source = currentSource,
            history = history,
            watchlist = watchlist,
            preferredActresses = preferredActresses,
            preferredPrefixes = preferredPrefixes
        )
        isLoadingForYou = false
    }

    fun loadCatalog(forceRefresh: Boolean) {
        if (mode == AvMode.Watchlist || mode == AvMode.History || mode == AvMode.ForYou) {
            if (forceRefresh && mode == AvMode.ForYou) {
                scope.launch {
                    isPullRefreshing = true
                    forYouMovies = AvRepository.getRecommendationsForYou(
                        source = currentSource,
                        history = history,
                        watchlist = watchlist,
                        preferredActresses = preferredActresses,
                        preferredPrefixes = preferredPrefixes
                    )
                    isPullRefreshing = false
                }
            }
            return
        }
        if (!forceRefresh && activeCatalog.loaded) return

        val targetCatalog = activeCatalog
        val targetSource = currentSource
        val targetMode = mode
        val targetCatSlug = selectedCategorySlug
        val targetSearch = submittedSearch

        scope.launch {
            if (forceRefresh) isPullRefreshing = true
            targetCatalog.isLoading = true
            targetCatalog.currentPage = 1

            try {
                when (targetMode) {
                    AvMode.Home -> {
                        val shelfConfigs = when (targetSource) {
                            MovieSource.AVDB -> listOf(
                                "🔥 Không Che (Uncensored)" to "2",
                                "💎 Uncensored Leak" to "3",
                                "🎬 Có Che (Censored)" to "1",
                                "📹 Amateur / FC2" to "4",
                                "💃 Chinese AV" to "5",
                                "🌸 Hentai Anime" to "6"
                            )
                            MovieSource.XXVN -> listOf(
                                "🇻🇳 JAV Vietsub" to "vietsub",
                                "🔥 JAV HD" to "jav-hd",
                                "💎 Không Che" to "khong-che",
                                "💃 Clip Việt Nam" to "viet-nam-clip",
                                "✨ Hàn Quốc 18+" to "han-quoc-18-",
                                "🌸 Trung Quốc" to "trung-quoc",
                                "🌍 Châu Âu" to "chau-au"
                            )
                            else -> listOf(
                                "🔥 Không Che (Uncensored)" to "uncensored",
                                "💎 Uncensored Leak" to "uncensored-leak",
                                "🎬 Có Che (Censored)" to "censored",
                                "📹 Amateur / FC2" to "siro",
                                "💃 Chinese AV" to "chinese-av"
                            )
                        }
                        val latestDef = async { AvRepository.getLatest(targetSource, 1) }
                        val shelvesDef = shelfConfigs.map { (title, slug) ->
                            async {
                                val list = AvRepository.getCategory(targetSource, slug, 1)
                                AvHomeShelf(title, slug, list)
                            }
                        }
                        val latest = latestDef.await()
                        val loadedShelves = shelvesDef.awaitAll().filter { it.items.isNotEmpty() }
                        targetCatalog.featured = latest.take(10)
                        targetCatalog.shelves = loadedShelves
                        targetCatalog.gridMovies = latest
                        targetCatalog.loaded = latest.isNotEmpty() || loadedShelves.isNotEmpty()
                    }
                    AvMode.Category -> {
                        val list = if (targetCatSlug.isBlank() || targetCatSlug == "latest") {
                            AvRepository.getLatest(targetSource, 1)
                        } else {
                            AvRepository.getCategory(targetSource, targetCatSlug, 1)
                        }
                        targetCatalog.gridMovies = list
                        targetCatalog.loaded = list.isNotEmpty()
                    }
                    AvMode.Search -> {
                        val list = if (targetSearch.isNotBlank()) {
                            AvRepository.filterAv(targetSource, "", targetSearch, 1).first
                        } else emptyList()
                        targetCatalog.gridMovies = list
                        targetCatalog.loaded = true
                    }
                    else -> Unit
                }
            } finally {
                targetCatalog.isLoading = false
                isPullRefreshing = false
            }
        }
    }

    LaunchedEffect(activeKey) {
        loadCatalog(forceRefresh = false)
    }

    fun loadMore() {
        if (mode != AvMode.Category && mode != AvMode.Search) return
        if (activeCatalog.isLoading || activeCatalog.isLoadingMore) return
        val targetCatalog = activeCatalog
        val targetSource = currentSource
        val targetMode = mode
        val targetCatSlug = selectedCategorySlug
        val targetSearch = submittedSearch

        scope.launch {
            targetCatalog.isLoadingMore = true
            val nextPage = targetCatalog.currentPage + 1
            val more = when (targetMode) {
                AvMode.Category -> if (targetCatSlug.isBlank() || targetCatSlug == "latest") {
                    AvRepository.getLatest(targetSource, nextPage)
                } else {
                    AvRepository.getCategory(targetSource, targetCatSlug, nextPage)
                }
                AvMode.Search -> AvRepository.filterAv(targetSource, "", targetSearch, nextPage).first
                else -> emptyList()
            }
            if (more.isNotEmpty()) {
                targetCatalog.currentPage = nextPage
                targetCatalog.gridMovies = (targetCatalog.gridMovies + more).distinctBy { it.slug.lowercase() }
            }
            targetCatalog.isLoadingMore = false
        }
    }

    fun openMovie(item: MovieItem) {
        selectedMovie = item
        val cached = AvRepository.getCachedDetail(item.slug)
        movieDetail = cached
        isLoadingDetail = cached == null
        relatedMovies = emptyList()
        isLoadingRelated = true

        scope.launch {
            val detail = AvRepository.getDetail(item.source, item.slug) ?: cached
            movieDetail = detail
            isLoadingDetail = false
            if (detail != null) {
                AvStore.recordAffinity(context, detail)
                relatedMovies = AvRepository.getRelatedMovies(detail)
            }
            isLoadingRelated = false
        }
    }

    fun playFullscreen(detail: MovieDetail, serverIndex: Int = 0, episodeIndex: Int = 0) {
        OnlinePlayerActivity.launch(context, detail, serverIndex, episodeIndex)
    }

    BackHandler(enabled = selectedMovie != null || isHistoryEditMode || mode != AvMode.Home) {
        when {
            selectedMovie != null -> {
                selectedMovie = null
                movieDetail = null
                relatedMovies = emptyList()
            }
            isHistoryEditMode -> {
                isHistoryEditMode = false
                selectedHistorySlugs = emptySet()
            }
            mode != AvMode.Home -> {
                mode = AvMode.Home
                searchQuery = ""
                submittedSearch = ""
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Tone.background)
            .statusBarsPadding()
    ) {
        if (selectedMovie != null) {
            AvDetailWithInlinePlayer(
                item = selectedMovie!!,
                detail = movieDetail,
                relatedMovies = relatedMovies,
                isLoading = isLoadingDetail,
                isLoadingRelated = isLoadingRelated,
                isBookmarked = watchlist.any { it.slug == selectedMovie!!.slug },
                historyRecord = history.firstOrNull { it.slug == selectedMovie!!.slug },
                onBack = {
                    selectedMovie = null
                    movieDetail = null
                    relatedMovies = emptyList()
                },
                onOpenFullscreen = { srvIdx, epIdx ->
                    movieDetail?.let { playFullscreen(it, srvIdx, epIdx) }
                },
                onToggleWatchlist = {
                    AvStore.toggleWatchlist(context, selectedMovie!!)
                },
                onSelectRelatedMovie = { nextItem ->
                    openMovie(nextItem)
                },
                onSearchKeyword = { kw ->
                    selectedMovie = null
                    movieDetail = null
                    relatedMovies = emptyList()
                    searchQuery = kw
                    submittedSearch = kw
                    mode = AvMode.Search
                }
            )
        } else {
            Column(Modifier.fillMaxSize()) {
                // Header Bar: Brand + Source Switcher + Privacy/Lock Button (Unified Theme with MoviePane)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Tone.accent)
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "18+ AV",
                                color = Color.Black,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp
                            )
                        }
                        if (mode != AvMode.Home) {
                            Text(
                                text = "← Trang chủ",
                                color = Tone.accent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        mode = AvMode.Home
                                        isHistoryEditMode = false
                                        selectedHistorySlugs = emptySet()
                                    }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Source Switcher Pills (Unified with MoviePane)
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(Tone.surfaceHigh)
                                .border(0.5.dp, Tone.outline.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                                .padding(2.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            avSources.forEach { source ->
                                val active = currentSource == source
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(if (active) Tone.accent else Color.Transparent)
                                        .clickable {
                                            if (currentSource != source) {
                                                currentSource = source
                                                if (mode == AvMode.Category) {
                                                    selectedCategorySlug = ""
                                                    selectedCategoryTitle = "Mới cập nhật"
                                                }
                                            }
                                        }
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = source.badge,
                                        color = if (active) Color.Black else Tone.text.copy(alpha = 0.7f),
                                        fontSize = 11.sp,
                                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }

                        // Privacy & PIN Lock Button
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(
                                    if (pinCode.isNotEmpty()) Tone.accent.copy(alpha = 0.2f)
                                    else Tone.surfaceHigh
                                )
                                .border(
                                    0.5.dp,
                                    if (pinCode.isNotEmpty()) Tone.accent else Tone.outline.copy(alpha = 0.4f),
                                    CircleShape
                                )
                                .clickable { showPrivacySheet = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (pinCode.isNotEmpty()) "🔒" else "🛡️",
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                // Search Bar (Fixed Cutoff using BasicTextField inside 42.dp Box, identical to MoviePane)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp)
                        .height(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Tone.surfaceHigh)
                        .border(0.5.dp, Tone.outline.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = TmIcons.Search,
                            contentDescription = "Search",
                            tint = Tone.text.copy(alpha = 0.5f),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Tìm tên phim, mã JAV (SONE-614, FC2...), diễn viên...",
                                    color = Tone.text.copy(alpha = 0.45f),
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                textStyle = TextStyle(
                                    color = Tone.text,
                                    fontSize = 14.sp
                                ),
                                singleLine = true,
                                cursorBrush = SolidColor(Tone.accent),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(
                                    onSearch = {
                                        focusManager.clearFocus()
                                        if (searchQuery.isNotBlank()) {
                                            submittedSearch = searchQuery.trim()
                                            mode = AvMode.Search
                                        }
                                    }
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (searchQuery.isNotEmpty()) {
                            Spacer(Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Tone.accent)
                                    .clickable {
                                        focusManager.clearFocus()
                                        submittedSearch = searchQuery.trim()
                                        mode = AvMode.Search
                                    }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Tìm",
                                    color = Color.Black,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                imageVector = TmIcons.Close,
                                contentDescription = "Xóa",
                                tint = Tone.text.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        searchQuery = ""
                                        submittedSearch = ""
                                        if (mode == AvMode.Search) mode = AvMode.Home
                                    }
                            )
                        }
                    }
                }

                // Category & Personal Shelf Chips Row
                val categories = remember(currentSource) { AvFilterData.categoriesFor(currentSource) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AvChip(
                        label = "🏠 Trang chủ",
                        selected = mode == AvMode.Home,
                        onClick = { mode = AvMode.Home }
                    )
                    AvChip(
                        label = "✨ Đề xuất cho bạn",
                        selected = mode == AvMode.ForYou,
                        onClick = { mode = AvMode.ForYou }
                    )
                    AvChip(
                        label = "⭐ Theo dõi (${watchlist.size})",
                        selected = mode == AvMode.Watchlist,
                        onClick = {
                            isHistoryEditMode = false
                            selectedHistorySlugs = emptySet()
                            mode = AvMode.Watchlist
                        }
                    )
                    AvChip(
                        label = "🎬 Đã xem (${history.size})",
                        selected = mode == AvMode.History,
                        onClick = {
                            isHistoryEditMode = false
                            selectedHistorySlugs = emptySet()
                            mode = AvMode.History
                        }
                    )
                    categories.filter { it.slug.isNotBlank() && it.slug !in listOf("for-you", "history", "watchlist") }.forEach { cat ->
                        val selected = mode == AvMode.Category && selectedCategorySlug == cat.slug
                        AvChip(
                            label = cat.label,
                            selected = selected,
                            onClick = {
                                selectedCategorySlug = cat.slug
                                selectedCategoryTitle = cat.label
                                mode = AvMode.Category
                            }
                        )
                    }
                }

                // Main Content Area
                when (mode) {
                    AvMode.Home -> {
                        PullToRefreshBox(
                            isRefreshing = isPullRefreshing,
                            onRefresh = { loadCatalog(forceRefresh = true) },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            if (activeCatalog.isLoading && !activeCatalog.loaded) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = Tone.accent)
                                }
                            } else {
                                LazyColumn(
                                    state = activeCatalog.homeScrollState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(bottom = 110.dp),
                                    verticalArrangement = Arrangement.spacedBy(18.dp)
                                ) {
                                    // Featured 16:9 Hero Carousel
                                    if (activeCatalog.featured.isNotEmpty()) {
                                        item {
                                            AvFeaturedCarousel(
                                                items = activeCatalog.featured,
                                                onOpen = ::openMovie
                                            )
                                        }
                                    }

                                    // Watchlist Shelf
                                    if (watchlist.isNotEmpty()) {
                                        item {
                                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 16.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "⭐ AV Đang Theo Dõi (${watchlist.size})",
                                                        color = Tone.text,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 15.sp
                                                    )
                                                    Text(
                                                        text = "Xem tất cả ›",
                                                        color = Tone.accent,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        modifier = Modifier.clickable { mode = AvMode.Watchlist }
                                                    )
                                                }
                                                LazyRow(
                                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                ) {
                                                    items(watchlist, key = { "${it.source.name}:${it.slug}" }) { item ->
                                                        AvLandscapeCard(
                                                            item = item,
                                                            onClick = { openMovie(item) },
                                                            modifier = Modifier.width(210.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // History Shelf
                                    if (history.isNotEmpty()) {
                                        item {
                                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 16.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "🎬 AV Xem Gần Đây (${history.size})",
                                                        color = Tone.text,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 15.sp
                                                    )
                                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                                        Text(
                                                            text = "Chọn xóa",
                                                            color = Tone.danger,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            modifier = Modifier.clickable {
                                                                isHistoryEditMode = true
                                                                selectedHistorySlugs = emptySet()
                                                                mode = AvMode.History
                                                            }
                                                        )
                                                        Text(
                                                            text = "Xem tất cả ›",
                                                            color = Tone.accent,
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            modifier = Modifier.clickable { mode = AvMode.History }
                                                        )
                                                    }
                                                }
                                                LazyRow(
                                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                ) {
                                                    items(history.take(15), key = { it.slug }) { rec ->
                                                        AvHistoryCard(
                                                            record = rec,
                                                            onClick = { openMovie(rec.toMovieItem()) },
                                                            onRemove = {
                                                                AvStore.removeFromHistory(context, rec.slug)
                                                            },
                                                            modifier = Modifier.width(210.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Personalized Recommendations ("✨ Đề xuất cho bạn")
                                    if (forYouMovies.isNotEmpty()) {
                                        item {
                                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 16.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = "✨ Đề Xuất Cho Bạn",
                                                            color = Tone.text,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 15.sp
                                                        )
                                                        val reasonParts = (preferredActresses.take(2) + preferredPrefixes.take(2)).distinct()
                                                        if (reasonParts.isNotEmpty()) {
                                                            Text(
                                                                text = "Gợi ý tương đồng theo: ${reasonParts.joinToString(", ")}",
                                                                color = Tone.text.copy(alpha = 0.55f),
                                                                fontSize = 11.sp,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }
                                                    }
                                                    Text(
                                                        text = "Xem thêm ›",
                                                        color = Tone.accent,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        modifier = Modifier.clickable { mode = AvMode.ForYou }
                                                    )
                                                }
                                                LazyRow(
                                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                ) {
                                                    items(forYouMovies.take(16), key = { "foryou:${it.slug}" }) { movie ->
                                                        AvLandscapeCard(
                                                            item = movie,
                                                            onClick = { openMovie(movie) },
                                                            modifier = Modifier.width(210.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Category Shelves
                                    items(activeCatalog.shelves, key = { it.slug }) { shelf ->
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 16.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = shelf.title,
                                                    color = Tone.text,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 15.sp
                                                )
                                                Text(
                                                    text = "Xem thêm ›",
                                                    color = Tone.accent,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    modifier = Modifier.clickable {
                                                        selectedCategorySlug = shelf.slug
                                                        selectedCategoryTitle = shelf.title
                                                        mode = AvMode.Category
                                                    }
                                                )
                                            }
                                            LazyRow(
                                                contentPadding = PaddingValues(horizontal = 16.dp),
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                items(shelf.items, key = { "${it.source.name}:${it.slug}" }) { movie ->
                                                    AvLandscapeCard(
                                                        item = movie,
                                                        onClick = { openMovie(movie) },
                                                        modifier = Modifier.width(210.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    AvMode.ForYou -> {
                        PullToRefreshBox(
                            isRefreshing = isPullRefreshing,
                            onRefresh = { loadCatalog(forceRefresh = true) },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            if (isLoadingForYou && forYouMovies.isEmpty()) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = Tone.accent)
                                }
                            } else if (forYouMovies.isEmpty()) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("Hãy xem thử vài phim để nhận gợi ý phù hợp nhất với bạn", color = Tone.muted)
                                }
                            } else {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(2),
                                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 110.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(
                                                text = "✨ Đề xuất cho bạn (${currentSource.badge})",
                                                color = Tone.text,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp
                                            )
                                            val tags = (preferredActresses.take(3) + preferredPrefixes.take(3)).distinct()
                                            Text(
                                                text = if (tags.isNotEmpty()) "Dựa trên các phim đã xem: ${tags.joinToString(", ")}"
                                                else "Phim xu hướng được đề xuất dành riêng cho bạn",
                                                color = Tone.text.copy(alpha = 0.6f),
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                    items(forYouMovies, key = { "fy_grid:${it.slug}" }) { item ->
                                        AvLandscapeCard(
                                            item = item,
                                            onClick = { openMovie(item) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    AvMode.Watchlist -> {
                        if (watchlist.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Chưa có phim AV nào trong danh sách theo dõi", color = Tone.muted)
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 110.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(watchlist, key = { "${it.source.name}:${it.slug}" }) { item ->
                                    AvLandscapeCard(
                                        item = item,
                                        onClick = { openMovie(item) }
                                    )
                                }
                            }
                        }
                    }

                    AvMode.History -> {
                        Column(Modifier.fillMaxSize()) {
                            if (history.isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isHistoryEditMode) "Đã chọn ${selectedHistorySlugs.size}/${history.size}"
                                        else "Lịch sử xem AV (${history.size})",
                                        color = Tone.text,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (isHistoryEditMode) {
                                            val allSlugs = history.map { it.slug }.toSet()
                                            TextButton(onClick = {
                                                selectedHistorySlugs = if (selectedHistorySlugs.size == allSlugs.size) emptySet() else allSlugs
                                            }) {
                                                Text(
                                                    if (selectedHistorySlugs.size == allSlugs.size) "Bỏ chọn" else "Chọn hết",
                                                    color = Tone.accent,
                                                    fontSize = 12.sp
                                                )
                                            }
                                            if (selectedHistorySlugs.isNotEmpty()) {
                                                Button(
                                                    onClick = {
                                                        AvStore.removeMultipleFromHistory(context, selectedHistorySlugs)
                                                        selectedHistorySlugs = emptySet()
                                                        isHistoryEditMode = false
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = Tone.danger),
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                                    modifier = Modifier.height(32.dp)
                                                ) {
                                                    Text("Xóa (${selectedHistorySlugs.size})", fontSize = 12.sp, color = Color.White)
                                                }
                                            }
                                            TextButton(onClick = {
                                                isHistoryEditMode = false
                                                selectedHistorySlugs = emptySet()
                                            }) {
                                                Text("Xong", color = Tone.muted, fontSize = 12.sp)
                                            }
                                        } else {
                                            TextButton(onClick = {
                                                isHistoryEditMode = true
                                                selectedHistorySlugs = emptySet()
                                            }) {
                                                Text("Chọn xóa", color = Tone.accent, fontSize = 12.sp)
                                            }
                                            TextButton(onClick = { AvStore.clearHistory(context) }) {
                                                Text("Xóa tất cả", color = Tone.danger, fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }

                            if (history.isEmpty()) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("Chưa có lịch sử xem phim AV", color = Tone.muted)
                                }
                            } else {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(2),
                                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 110.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(history, key = { it.slug }) { rec ->
                                        val isSelected = rec.slug in selectedHistorySlugs
                                        AvHistoryCard(
                                            record = rec,
                                            isEditMode = isHistoryEditMode,
                                            isSelected = isSelected,
                                            onClick = {
                                                if (isHistoryEditMode) {
                                                    selectedHistorySlugs = if (isSelected) selectedHistorySlugs - rec.slug else selectedHistorySlugs + rec.slug
                                                } else {
                                                    openMovie(rec.toMovieItem())
                                                }
                                            },
                                            onLongClick = {
                                                if (!isHistoryEditMode) {
                                                    isHistoryEditMode = true
                                                    selectedHistorySlugs = setOf(rec.slug)
                                                }
                                            },
                                            onRemove = {
                                                AvStore.removeFromHistory(context, rec.slug)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    AvMode.Category, AvMode.Search -> {
                        PullToRefreshBox(
                            isRefreshing = isPullRefreshing,
                            onRefresh = { loadCatalog(forceRefresh = true) },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            if (activeCatalog.isLoading && activeCatalog.gridMovies.isEmpty()) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = Tone.accent)
                                }
                            } else if (activeCatalog.gridMovies.isEmpty()) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("Không tìm thấy phim phù hợp", color = Tone.muted)
                                }
                            } else {
                                val gridState = activeCatalog.gridScrollState
                                val shouldLoadMore by remember {
                                    derivedStateOf {
                                        val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                                        val total = gridState.layoutInfo.totalItemsCount
                                        total > 0 && lastVisible >= total - 4
                                    }
                                }
                                LaunchedEffect(shouldLoadMore) {
                                    if (shouldLoadMore) loadMore()
                                }

                                LazyVerticalGrid(
                                    state = gridState,
                                    columns = GridCells.Fixed(2),
                                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 110.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        Text(
                                            text = if (mode == AvMode.Search) "🔍 Kết quả cho \"$submittedSearch\" (${activeCatalog.gridMovies.size})"
                                            else "📂 $selectedCategoryTitle (${currentSource.badge})",
                                            color = Tone.text,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            modifier = Modifier.padding(bottom = 4.dp)
                                        )
                                    }
                                    items(activeCatalog.gridMovies, key = { "${it.source.name}:${it.slug}" }) { movie ->
                                        AvLandscapeCard(
                                            item = movie,
                                            onClick = { openMovie(movie) }
                                        )
                                    }
                                    if (activeCatalog.isLoadingMore) {
                                        item(span = { GridItemSpan(maxLineSpan) }) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(16.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(24.dp),
                                                    color = Tone.accent,
                                                    strokeWidth = 2.dp
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
        }
    }

    if (showPrivacySheet) {
        AvPrivacyBottomSheet(
            hasPin = pinCode.isNotEmpty(),
            isTabVisible = isTabVisible,
            onDismiss = { showPrivacySheet = false },
            onLockNow = {
                AvStore.lockNow()
                showPrivacySheet = false
            },
            onSetPin = { newPin ->
                AvStore.setPinCode(context, newPin)
                Toast.makeText(context, "Đã bật khóa mã PIN cho Tab AV", Toast.LENGTH_SHORT).show()
            },
            onClearPin = {
                AvStore.clearPinCode(context)
                Toast.makeText(context, "Đã tắt khóa mã PIN", Toast.LENGTH_SHORT).show()
            },
            onVerifyCurrentPin = { input -> AvStore.verifyPin(input) },
            onToggleTabVisible = { visible ->
                AvStore.setTabVisible(context, visible)
                if (!visible) {
                    Toast.makeText(
                        context,
                        "Đã ẩn Tab AV trên thanh điều hướng. Nhấn giữ nút Movie hoặc mở từ menu Hồ sơ để vào lại.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        )
    }
}

// =============================================================================
// MISSAV-STYLE INLINE PORTRAIT PLAYER + DETAIL + RELATED MOVIES
// =============================================================================

@OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AvDetailWithInlinePlayer(
    item: MovieItem,
    detail: MovieDetail?,
    relatedMovies: List<MovieItem>,
    isLoading: Boolean,
    isLoadingRelated: Boolean,
    isBookmarked: Boolean,
    historyRecord: MovieWatchProgress?,
    onBack: () -> Unit,
    onOpenFullscreen: (serverIndex: Int, episodeIndex: Int) -> Unit,
    onToggleWatchlist: () -> Unit,
    onSelectRelatedMovie: (MovieItem) -> Unit,
    onSearchKeyword: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val movie = detail?.movie ?: item
    val scrollState = rememberScrollState()

    var serverIdx by remember(movie.slug) { mutableIntStateOf(historyRecord?.serverIndex ?: 0) }
    var epIdx by remember(movie.slug) { mutableIntStateOf(historyRecord?.episodeIndex ?: 0) }
    var showDetailsSection by remember(movie.slug) { mutableStateOf(true) }
    var showCloudPullSheet by remember { mutableStateOf(false) }

    val currentServer = detail?.servers?.getOrNull(serverIdx) ?: detail?.servers?.firstOrNull()
    val currentEp = currentServer?.episodes?.getOrNull(epIdx) ?: currentServer?.episodes?.firstOrNull()

    var resolvedStream by remember(movie.slug, serverIdx, epIdx) { mutableStateOf<MovieStream?>(null) }
    var isStreamLoading by remember(movie.slug, serverIdx, epIdx) { mutableStateOf(true) }
    var isPlaying by remember(movie.slug) { mutableStateOf(true) }
    var isBuffering by remember(movie.slug) { mutableStateOf(true) }
    var isMuted by remember { mutableStateOf(false) }
    var currentPosMs by remember(movie.slug) { mutableLongStateOf(0L) }
    var durationMs by remember(movie.slug) { mutableLongStateOf(0L) }
    var showPlayerControls by remember { mutableStateOf(true) }
    var lastControlTapMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var initialSeekApplied by remember(movie.slug, serverIdx, epIdx) { mutableStateOf(false) }
    var seekToastLabel by remember { mutableStateOf<String?>(null) }
    var streamError by remember(movie.slug, serverIdx, epIdx) { mutableStateOf<String?>(null) }
    var retryTrigger by remember(movie.slug) { mutableIntStateOf(0) }

    // Scroll to top when switching to a related movie
    LaunchedEffect(movie.slug) {
        scrollState.scrollTo(0)
    }

    val httpSourceFactory = remember {
        DefaultHttpDataSource.Factory()
            .setUserAgent(AvRepository.IOS_SAFARI_UA)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
            .setAllowCrossProtocolRedirects(true)
    }

    val exoPlayer = remember {
        val dataSourceFactory = DefaultDataSource.Factory(context, httpSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
    }

    // Keep screen on while inline player is active and playing
    DisposableEffect(isPlaying) {
        val window = (context as? Activity)?.window
        if (isPlaying) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Resolve stream when detail / server / episode is ready
    LaunchedEffect(detail, serverIdx, epIdx, retryTrigger) {
        if (detail != null && currentServer != null && currentEp != null) {
            isStreamLoading = true
            streamError = null
            val stream = AvRepository.resolveAvStream(
                source = detail.movie.source,
                movieTitle = detail.movie.name,
                server = currentServer,
                episode = currentEp
            )
            resolvedStream = stream
            isStreamLoading = false
        }
    }

    // Load resolved stream into ExoPlayer
    LaunchedEffect(resolvedStream?.streamUrl, retryTrigger) {
        val stream = resolvedStream ?: return@LaunchedEffect
        if (stream.streamUrl.isBlank()) {
            streamError = "Chưa lấy được link phát trực tiếp"
            isBuffering = false
            return@LaunchedEffect
        }
        try {
            isBuffering = true
            streamError = null
            initialSeekApplied = false
            val ref = stream.referer.ifBlank { "https://upload18.org/" }
            httpSourceFactory.setUserAgent(AvRepository.IOS_SAFARI_UA)
            httpSourceFactory.setDefaultRequestProperties(
                mapOf(
                    "Referer" to ref,
                    "Origin" to ref.trimEnd('/'),
                    "Accept" to "*/*",
                    "Accept-Language" to "vi-VN,vi;q=0.9,en-US;q=0.8,en;q=0.7"
                )
            )
            val mediaItem = MediaItem.Builder()
                .setUri(stream.streamUrl)
                .apply {
                    if (stream.streamUrl.contains(".m3u8") ||
                        stream.streamUrl.contains("/m/") ||
                        stream.streamUrl.contains("/s/") ||
                        stream.streamUrl.contains("surrit.com")
                    ) {
                        setMimeType(MimeTypes.APPLICATION_M3U8)
                    }
                }
                .build()
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        } catch (e: Exception) {
            isBuffering = false
            streamError = "Không thể phát: ${e.message}"
        }
    }

    // Sync player state & listeners
    DisposableEffect(exoPlayer, detail, serverIdx, epIdx) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = (state == Player.STATE_BUFFERING)
                if (state == Player.STATE_READY) {
                    isBuffering = false
                    streamError = null
                    durationMs = exoPlayer.duration.coerceAtLeast(0L)
                    if (!initialSeekApplied) {
                        initialSeekApplied = true
                        val saved = AvStore.getProgress(movie.slug)
                        if (saved != null && !saved.isFinished && saved.positionMs > 2_000L) {
                            exoPlayer.seekTo(saved.positionMs)
                            currentPosMs = saved.positionMs
                        }
                    }
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlayerError(error: PlaybackException) {
                if (detail != null && serverIdx + 1 < detail.servers.size) {
                    serverIdx += 1
                    epIdx = 0
                } else {
                    isBuffering = false
                    streamError = "Lỗi luồng phát (${error.errorCodeName})"
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            val d = detail
            val pos = exoPlayer.currentPosition
            val dur = exoPlayer.duration
            if (d != null && pos > 2_000L && dur > 0L) {
                AvStore.saveProgress(
                    context = context,
                    detail = d,
                    serverIndex = serverIdx,
                    episodeIndex = epIdx,
                    episodeName = currentEp?.name ?: "Full",
                    positionMs = pos,
                    durationMs = dur
                )
            }
            exoPlayer.removeListener(listener)
        }
    }

    // Release ExoPlayer when leaving detail view completely
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    // Sync position when returning from Fullscreen OnlinePlayerActivity
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, movie.slug) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                exoPlayer.pause()
            } else if (event == Lifecycle.Event.ON_RESUME) {
                val latestSaved = AvStore.getProgress(movie.slug)
                if (latestSaved != null && latestSaved.positionMs > 1_000L && exoPlayer.duration > 0L) {
                    if (kotlin.math.abs(exoPlayer.currentPosition - latestSaved.positionMs) > 2_500L) {
                        exoPlayer.seekTo(latestSaved.positionMs)
                        currentPosMs = latestSaved.positionMs
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Periodic progress update & auto-save
    LaunchedEffect(exoPlayer, isPlaying, detail) {
        var tick = 0
        while (true) {
            currentPosMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            val dur = exoPlayer.duration
            if (dur > 0L) durationMs = dur
            tick++
            if (tick % 10 == 0 && isPlaying && detail != null && currentPosMs > 3_000L && durationMs > 0L) {
                AvStore.saveProgress(
                    context = context,
                    detail = detail,
                    serverIndex = serverIdx,
                    episodeIndex = epIdx,
                    episodeName = currentEp?.name ?: "Full",
                    positionMs = currentPosMs,
                    durationMs = durationMs
                )
            }
            delay(500)
        }
    }

    // Auto-hide player controls overlay after 4s of playing
    LaunchedEffect(lastControlTapMs, showPlayerControls, isPlaying) {
        if (showPlayerControls && isPlaying) {
            delay(4000)
            if (System.currentTimeMillis() - lastControlTapMs >= 3800 && isPlaying) {
                showPlayerControls = false
            }
        }
    }

    // Clear quick-seek toast label
    LaunchedEffect(seekToastLabel) {
        if (seekToastLabel != null) {
            delay(900)
            seekToastLabel = null
        }
    }

    fun seekByDelta(deltaMs: Long, label: String) {
        val dur = exoPlayer.duration.takeIf { it > 0L } ?: durationMs.takeIf { it > 0L } ?: Long.MAX_VALUE
        val target = (exoPlayer.currentPosition + deltaMs).coerceIn(0L, dur)
        exoPlayer.seekTo(target)
        currentPosMs = target
        seekToastLabel = label
        lastControlTapMs = System.currentTimeMillis()
    }

    fun launchLandscapeFullscreen() {
        val d = detail ?: return
        val pos = exoPlayer.currentPosition
        val dur = exoPlayer.duration
        if (pos > 1_000L) {
            AvStore.saveProgress(
                context = context,
                detail = d,
                serverIndex = serverIdx,
                episodeIndex = epIdx,
                episodeName = currentEp?.name ?: "Full",
                positionMs = pos,
                durationMs = dur.coerceAtLeast(pos + 60_000L)
            )
        }
        exoPlayer.pause()
        onOpenFullscreen(serverIdx, epIdx)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Tone.background)
            .verticalScroll(scrollState)
            .padding(bottom = 110.dp)
    ) {
        // =====================================================================
        // 1. INLINE 16:9 PORTRAIT VIDEO PLAYER (Thay thế phần Poster)
        // =====================================================================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.Black)
                .pointerInput(durationMs) {
                    detectTapGestures(
                        onTap = {
                            showPlayerControls = !showPlayerControls
                            lastControlTapMs = System.currentTimeMillis()
                        },
                        onDoubleTap = { offset ->
                            if (offset.x < size.width / 2f) {
                                seekByDelta(-10_000L, "⏪ -10s")
                            } else {
                                seekByDelta(10_000L, "⏩ +10s")
                            }
                        }
                    )
                }
                .pointerInput(durationMs) {
                    var dragAccumPx = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { dragAccumPx = 0f },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            dragAccumPx += dragAmount
                        },
                        onDragEnd = {
                            val widthPx = size.width.coerceAtLeast(1).toFloat()
                            val fraction = dragAccumPx / widthPx
                            if (kotlin.math.abs(fraction) > 0.04f) {
                                val fullSpanMs = (durationMs.takeIf { it > 0L } ?: 3_600_000L) * 0.25f
                                val delta = (fraction * fullSpanMs).roundToLong()
                                val sec = delta / 1000L
                                val sign = if (sec >= 0) "+" else ""
                                seekByDelta(delta, "${sign}${sec}s")
                            }
                        }
                    )
                }
        ) {
            // ExoPlayer Surface
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        player = exoPlayer
                    }
                },
                update = { view ->
                    if (view.player !== exoPlayer) {
                        view.player = exoPlayer
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Poster preview while stream is still resolving or before first frame
            if ((isLoading || isStreamLoading || (isBuffering && currentPosMs < 500L)) && streamError == null) {
                AsyncAvImage(
                    url = movie.thumbUrl.ifEmpty { movie.posterUrl },
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f))
                )
            }

            // Center Play/Pause or Buffering Spinner or Seek Feedback
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                when {
                    seekToastLabel != null -> {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color.Black.copy(alpha = 0.78f))
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = seekToastLabel!!,
                                color = Tone.accent,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }
                    isLoading || isStreamLoading || isBuffering -> {
                        CircularProgressIndicator(
                            color = Tone.accent,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                    streamError != null -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black.copy(alpha = 0.75f))
                                .padding(12.dp)
                        ) {
                            Text(streamError!!, color = Color.White, fontSize = 12.sp)
                            Spacer(Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Tone.accent)
                                    .clickable {
                                        AvRepository.invalidateStreamCache(currentEp)
                                        retryTrigger += 1
                                    }
                                    .padding(horizontal = 12.dp, vertical = 5.dp)
                            ) {
                                Text("Thử lại", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    showPlayerControls || !isPlaying -> {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(Tone.accent.copy(alpha = 0.9f))
                                .clickable {
                                    lastControlTapMs = System.currentTimeMillis()
                                    if (exoPlayer.isPlaying) {
                                        exoPlayer.pause()
                                    } else {
                                        exoPlayer.play()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPlaying) TmIcons.PauseSolid else TmIcons.PlaySolid,
                                contentDescription = if (isPlaying) "Tạm dừng" else "Phát",
                                tint = Color.Black,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }
            }

            // Top bar overlay inside player: Back button + Server switcher + Fullscreen button
            androidx.compose.animation.AnimatedVisibility(
                visible = showPlayerControls || !isPlaying,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
                            )
                        )
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.6f))
                                .clickable(onClick = onBack),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = TmIcons.ArrowBack,
                                contentDescription = "Quay lại",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Tone.accent)
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = movie.source.badge,
                                color = Color.Black,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Server pills if multiple servers exist
                    if ((detail?.servers?.size ?: 0) > 1) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            detail!!.servers.forEachIndexed { idx, srv ->
                                val active = serverIdx == idx
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (active) Tone.accent else Color.Black.copy(alpha = 0.6f))
                                        .clickable { serverIdx = idx }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = srv.serverName,
                                        color = if (active) Color.Black else Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Bottom control bar inside player (matching MissAV screenshot 1: ⏪ ▶ ⏩ [slider] 00:00 🔊 ⛶)
            androidx.compose.animation.AnimatedVisibility(
                visible = showPlayerControls || !isPlaying,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                            )
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Rewind 10s
                    Text(
                        text = "⏪",
                        color = Color.White,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { seekByDelta(-10_000L, "⏪ -10s") }
                            .padding(4.dp)
                    )
                    // Play / Pause
                    Icon(
                        imageVector = if (isPlaying) TmIcons.PauseSolid else TmIcons.PlaySolid,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .size(22.dp)
                            .clickable {
                                lastControlTapMs = System.currentTimeMillis()
                                if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                            }
                    )
                    // Fast-forward 10s
                    Text(
                        text = "⏩",
                        color = Color.White,
                        fontSize = 13.sp,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { seekByDelta(10_000L, "⏩ +10s") }
                            .padding(4.dp)
                    )

                    // Progress Slider
                    val fraction = if (durationMs > 0L) (currentPosMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
                    Slider(
                        value = fraction,
                        onValueChange = { newFrac ->
                            lastControlTapMs = System.currentTimeMillis()
                            if (durationMs > 0L) {
                                val target = (newFrac * durationMs).toLong()
                                currentPosMs = target
                                exoPlayer.seekTo(target)
                            }
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Tone.accent,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(26.dp)
                    )

                    // Time label
                    Text(
                        text = formatAvTime(currentPosMs),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )

                    // Mute / Unmute
                    Text(
                        text = if (isMuted) "🔇" else "🔊",
                        fontSize = 13.sp,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable {
                                isMuted = !isMuted
                                exoPlayer.volume = if (isMuted) 0f else 1f
                            }
                            .padding(4.dp)
                    )

                    // Fullscreen Landscape Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.16f))
                            .clickable { launchLandscapeFullscreen() }
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "⛶",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // =====================================================================
        // 2. MISSAV QUICK SEEK STRIP: [<< 10m | < 1m | < 10s]  [10s > | 1m > | 10m >>]
        // =====================================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Rewind Segmented Group
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Tone.surfaceHigh)
                    .border(0.8.dp, Tone.outline.copy(alpha = 0.55f), RoundedCornerShape(8.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                QuickSeekSegmentButton(
                    label = "<< 10m",
                    onClick = { seekByDelta(-600_000L, "⏪ -10 phút") },
                    modifier = Modifier.weight(1f)
                )
                VerticalSegmentDivider()
                QuickSeekSegmentButton(
                    label = "< 1m",
                    onClick = { seekByDelta(-60_000L, "⏪ -1 phút") },
                    modifier = Modifier.weight(1f)
                )
                VerticalSegmentDivider()
                QuickSeekSegmentButton(
                    label = "< 10s",
                    onClick = { seekByDelta(-10_000L, "⏪ -10 giây") },
                    modifier = Modifier.weight(1f)
                )
            }

            // Right Forward Segmented Group
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Tone.surfaceHigh)
                    .border(0.8.dp, Tone.outline.copy(alpha = 0.55f), RoundedCornerShape(8.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                QuickSeekSegmentButton(
                    label = "10s >",
                    onClick = { seekByDelta(10_000L, "⏩ +10 giây") },
                    modifier = Modifier.weight(1f)
                )
                VerticalSegmentDivider()
                QuickSeekSegmentButton(
                    label = "1m >",
                    onClick = { seekByDelta(60_000L, "⏩ +1 phút") },
                    modifier = Modifier.weight(1f)
                )
                VerticalSegmentDivider()
                QuickSeekSegmentButton(
                    label = "10m >>",
                    onClick = { seekByDelta(600_000L, "⏩ +10 phút") },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // =====================================================================
        // 3. TITLE (MAX 3 LINES WITH ELLIPSIS) + ACTION ROW + CHI TIẾT
        // =====================================================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Movie Title: Strictly max 3 lines with ellipsis (...)
            Text(
                text = movie.name,
                color = Tone.text,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            // Action Buttons Bar (Theo dõi | Kéo về Tele | Toàn màn hình)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AvActionPill(
                    icon = if (isBookmarked) "♥" else "♡",
                    label = if (isBookmarked) "Đã lưu" else "Theo dõi",
                    highlighted = isBookmarked,
                    onClick = onToggleWatchlist
                )
                AvActionPill(
                    icon = "☁️",
                    label = "Kéo về Tele",
                    highlighted = false,
                    onClick = { showCloudPullSheet = true }
                )
                AvActionPill(
                    icon = "⛶",
                    label = "Toàn màn hình",
                    highlighted = false,
                    onClick = { launchLandscapeFullscreen() }
                )
            }

            // "Chi tiết" Header with accent underline (matching MissAV screenshot 1 & 2)
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Chi tiết",
                            color = Tone.accent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Box(
                            modifier = Modifier
                                .width(56.dp)
                                .height(2.dp)
                                .background(Tone.accent)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Tone.surfaceHigh)
                            .clickable { showDetailsSection = !showDetailsSection },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (showDetailsSection) "✕" else "▾",
                            color = Tone.muted,
                            fontSize = 12.sp
                        )
                    }
                }
                HorizontalDivider(color = Tone.outline.copy(alpha = 0.35f), thickness = 0.5.dp)
            }

            if (showDetailsSection) {
                val codePrefix = remember(movie.slug, movie.name) {
                    AvStore.extractCodePrefix(movie.slug, movie.name).ifBlank { movie.slug.uppercase() }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Mã phim & Thời lượng
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Mã phim:", color = Tone.muted, fontSize = 13.sp)
                        Text(
                            text = movie.rating.ifBlank { movie.slug.uppercase() },
                            color = Tone.accent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { onSearchKeyword(codePrefix) }
                        )
                        if (!detail?.time.isNullOrBlank()) {
                            Text("• ${detail!!.time}", color = Tone.muted, fontSize = 13.sp)
                        }
                    }

                    // Diễn viên (Clickable)
                    if (!detail?.actor.isNullOrEmpty()) {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Diễn viên:", color = Tone.muted, fontSize = 13.sp)
                            detail!!.actor.forEach { actress ->
                                Text(
                                    text = actress,
                                    color = Tone.accent,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Tone.surfaceHigh)
                                        .clickable { onSearchKeyword(actress) }
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    // Thể loại (Matching screenshot 2: "thể loại: Nghiệp dư")
                    if (!detail?.categories.isNullOrEmpty()) {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Thể loại:", color = Tone.muted, fontSize = 13.sp)
                            detail!!.categories.forEach { catName ->
                                Text(
                                    text = catName,
                                    color = Tone.accent,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { onSearchKeyword(catName) }
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    // Nhãn / Studio Prefix (Matching screenshot 2: "Nhãn: FC2")
                    if (codePrefix.isNotBlank()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Nhãn:", color = Tone.muted, fontSize = 13.sp)
                            Text(
                                text = codePrefix,
                                color = Tone.accent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { onSearchKeyword(codePrefix) }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // Mô tả ngắn gọn (tối đa 3 dòng, bấm để mở rộng)
                    if (!detail?.content.isNullOrBlank() && detail!!.content != movie.name) {
                        var expandedDesc by remember { mutableStateOf(false) }
                        Text(
                            text = detail.content,
                            color = Tone.text.copy(alpha = 0.78f),
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            maxLines = if (expandedDesc) 20 else 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable { expandedDesc = !expandedDesc }
                        )
                    }
                }
            }

            // =================================================================
            // 4. PHIM TƯƠNG TỰ (2-COLUMN LANDSCAPE GRID, GIỐNG ẢNH MISSAV SỐ 2)
            // =================================================================
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Phim tương tự",
                color = Tone.text,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )

            if (isLoadingRelated && relatedMovies.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = Tone.accent,
                        modifier = Modifier.size(26.dp),
                        strokeWidth = 2.dp
                    )
                }
            } else if (relatedMovies.isEmpty()) {
                Text(
                    text = "Đang cập nhật danh sách phim tương tự...",
                    color = Tone.muted,
                    fontSize = 12.sp
                )
            } else {
                val rows = remember(relatedMovies) { relatedMovies.chunked(2) }
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    rows.forEach { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            pair.forEach { relItem ->
                                AvLandscapeCard(
                                    item = relItem,
                                    onClick = { onSelectRelatedMovie(relItem) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (pair.size == 1) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }

    // =========================================================================
    // 5. CLOUD PULL SHEET CHO AV (CHỌN ĐÍCH ĐẾN TELE + KÉO VỀ TELE CLOUD)
    // =========================================================================
    if (showCloudPullSheet) {
        val currentDest by MovieStore.cloudDestinationFlow.collectAsState()
        ModalBottomSheet(
            onDismissRequest = { showCloudPullSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Tone.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Kéo phim AV về Tele Cloud",
                        color = Tone.text,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Tone.surfaceHigh)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = currentDest.displayLabel,
                            color = Tone.accent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text(
                    text = movie.name,
                    color = Tone.text.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Reusable Destination Picker Card (same as Movie tab)
                CloudDestinationSelectorCard()

                // Pull Full HD button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Tone.accent)
                        .clickable {
                            showCloudPullSheet = false
                            scope.launch {
                                MovieCloudDownloader.pullToCloud(
                                    context = context,
                                    slug = movie.slug,
                                    movieTitle = movie.name,
                                    source = movie.source,
                                    epRange = "full",
                                    totalEpisodes = 1,
                                    movie = movie,
                                    detail = detail
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
                                .background(Color.Black.copy(alpha = 0.18f)),
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
                                text = "☁️ Kéo bản Full HD về ${currentDest.displayLabel}",
                                color = Color.Black,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Tự động trích xuất luồng HLS m3u8 & gửi lệnh tải về đích đã chọn",
                                color = Color.Black.copy(alpha = 0.75f),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickSeekSegmentButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Tone.text,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
private fun VerticalSegmentDivider() {
    Box(
        modifier = Modifier
            .width(0.8.dp)
            .fillMaxHeight()
            .background(Tone.outline.copy(alpha = 0.55f))
    )
}

@Composable
private fun AvActionPill(
    icon: String,
    label: String,
    highlighted: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (highlighted) Tone.accent.copy(alpha = 0.18f) else Tone.surfaceHigh)
            .border(
                0.5.dp,
                if (highlighted) Tone.accent else Tone.outline.copy(alpha = 0.35f),
                RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(
            text = icon,
            color = if (highlighted) Tone.accent else Tone.text,
            fontSize = 13.sp
        )
        Text(
            text = label,
            color = if (highlighted) Tone.accent else Tone.text,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun formatAvTime(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSec = ms / 1000L
    val h = totalSec / 3600L
    val m = (totalSec % 3600L) / 60L
    val s = totalSec % 60L
    return if (h > 0L) {
        String.format("%d:%02d:%02d", h, m, s)
    } else {
        String.format("%02d:%02d", m, s)
    }
}

// =============================================================================
// REUSABLE CARDS & CHIPS (UNIFIED WITH APP THEME + MISSAV CARD LAYOUT)
// =============================================================================

@Composable
private fun AvChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) Tone.accent else Tone.surfaceHigh)
            .border(
                0.5.dp,
                if (selected) Tone.accent else Tone.outline.copy(alpha = 0.35f),
                RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = if (selected) Color.Black else Tone.text,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun AvFeaturedCarousel(
    items: List<MovieItem>,
    onOpen: (MovieItem) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { items.size })
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            pageSpacing = 12.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(195.dp)
        ) { page ->
            val item = items[page]
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Tone.surfaceHigh)
                    .clickable { onOpen(item) }
            ) {
                AsyncAvImage(
                    url = item.thumbUrl.ifEmpty { item.posterUrl },
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.35f),
                                    Color.Black.copy(alpha = 0.88f)
                                )
                            )
                        )
                )
                // Code / Duration Badge at Top Left
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (item.episodeCurrent.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Tone.accent)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = item.episodeCurrent,
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                    if (item.quality.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.Black.copy(alpha = 0.72f))
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = item.quality,
                                color = Tone.accent,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                ) {
                    Text(
                        text = item.name,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (item.originName.isNotBlank() && item.originName != item.name) {
                        Text(
                            text = item.originName,
                            color = Color.White.copy(alpha = 0.78f),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * Thẻ phim ngang 16:9 chuẩn giao diện MissAV (giống ảnh đính kèm số 2):
 * - Góc dưới phải: Thời lượng phim (VD: 1:00:04, 1:10:01) trên nền đen mờ bo góc.
 * - Góc dưới trái: Nhãn Không kiểm duyệt / Vietsub (nếu có).
 * - Bên dưới ảnh: Tên phim (tối đa 2 dòng, quá dài thì ...).
 */
@Composable
private fun AvLandscapeCard(
    item: MovieItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9.2f)
                .clip(RoundedCornerShape(10.dp))
                .background(Tone.surfaceHigh)
        ) {
            AsyncAvImage(
                url = item.posterUrl.ifEmpty { item.thumbUrl },
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Bottom-left Uncensored / Category badge (matching MissAV screenshot 2)
            val leftTag = when {
                item.lang.contains("Không", ignoreCase = true) ||
                    item.lang.contains("Uncensored", ignoreCase = true) ||
                    item.slug.contains("uncensored", ignoreCase = true) ||
                    item.slug.startsWith("fc2", ignoreCase = true) -> "Không kiểm duyệt"
                item.lang.contains("Vietsub", ignoreCase = true) -> "Vietsub"
                else -> ""
            }
            if (leftTag.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color(0xFF1E40AF).copy(alpha = 0.88f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = leftTag,
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }

            // Bottom-right Duration / Code badge (matching MissAV screenshot 2: e.g. "1:00:04")
            val durationOrCode = item.episodeCurrent.ifBlank { item.quality }
            if (durationOrCode.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color.Black.copy(alpha = 0.78f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = durationOrCode,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
        }

        Text(
            text = item.name,
            color = Tone.text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 16.sp
        )
    }
}

@kotlin.OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AvHistoryCard(
    record: MovieWatchProgress,
    isEditMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = record.progressFraction.coerceIn(0f, 1f)

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9.2f)
                .clip(RoundedCornerShape(10.dp))
                .background(Tone.surfaceHigh)
                .then(
                    if (isSelected) Modifier.border(2.dp, Tone.accent, RoundedCornerShape(10.dp))
                    else Modifier
                )
        ) {
            AsyncAvImage(
                url = record.posterUrl.ifEmpty { record.thumbUrl },
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (isEditMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) Tone.accent else Color.Black.copy(alpha = 0.65f))
                        .border(1.5.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Text("✓", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.7f))
                        .clickable { onRemove() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("✕", color = Color.White, fontSize = 11.sp)
                }
            }

            if (record.durationMs > 0L) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 8.dp, end = 6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.78f))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "${formatAvTime(record.positionMs)} / ${formatAvTime(record.durationMs)}",
                        color = Color.White,
                        fontSize = 9.sp
                    )
                }
            }

            if (progress > 0f) {
                LinearProgressIndicator(
                    progress = { progress.coerceAtLeast(0.04f) },
                    color = Tone.accent,
                    trackColor = Color.White.copy(alpha = 0.25f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.5.dp)
                )
            }
        }
        Text(
            text = record.name,
            color = Tone.text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 15.sp
        )
    }
}

@Composable
private fun AvPinLockScreen(
    onVerify: (String) -> Boolean
) {
    var enteredPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Tone.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Tone.accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Text("🔒", fontSize = 28.sp)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Khu Vực Riêng Tư 18+",
            color = Tone.text,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = errorMessage ?: "Nhập mã PIN 4 số để mở khóa Tab AV",
            color = if (errorMessage != null) Tone.danger else Tone.muted,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(24.dp))

        // 4 PIN Dots
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(4) { idx ->
                val filled = idx < enteredPin.length
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(if (filled) Tone.accent else Tone.surfaceHigh)
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("C", "0", "⌫")
        )
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    row.forEach { key ->
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(Tone.surfaceHigh)
                                .clickable {
                                    errorMessage = null
                                    when (key) {
                                        "C" -> enteredPin = ""
                                        "⌫" -> if (enteredPin.isNotEmpty()) enteredPin = enteredPin.dropLast(1)
                                        else -> {
                                            if (enteredPin.length < 4) {
                                                enteredPin += key
                                                if (enteredPin.length == 4) {
                                                    if (!onVerify(enteredPin)) {
                                                        errorMessage = "Mã PIN không đúng, vui lòng thử lại"
                                                        enteredPin = ""
                                                    }
                                                }
                                            }
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = key,
                                color = Tone.text,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AvPrivacyBottomSheet(
    hasPin: Boolean,
    isTabVisible: Boolean,
    onDismiss: () -> Unit,
    onLockNow: () -> Unit,
    onSetPin: (String) -> Unit,
    onClearPin: () -> Unit,
    onVerifyCurrentPin: (String) -> Boolean,
    onToggleTabVisible: (Boolean) -> Unit
) {
    var currentPinInput by remember { mutableStateOf("") }
    var newPinInput by remember { mutableStateOf("") }
    var pinMode by remember { mutableStateOf<String?>(null) }
    var pinError by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Tone.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "🔒 Bảo mật & Ẩn Tab AV 18+",
                color = Tone.text,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Tone.surfaceHigh)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Hiện Tab AV trên thanh điều hướng",
                        color = Tone.text,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Khi tắt, Tab AV sẽ ẩn khỏi thanh dưới cùng. Nhấn giữ nút Movie hoặc mở từ menu Hồ sơ để vào lại.",
                        color = Tone.muted,
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = isTabVisible,
                    onCheckedChange = onToggleTabVisible,
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = Tone.accent)
                )
            }

            if (hasPin) {
                Button(
                    onClick = onLockNow,
                    colors = ButtonDefaults.buttonColors(containerColor = Tone.accent),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🔒 Khóa Tab AV ngay lập tức", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }

            if (pinMode == null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            pinError = null
                            currentPinInput = ""
                            newPinInput = ""
                            pinMode = if (hasPin) "change" else "set"
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (hasPin) "Đổi mã PIN" else "Đặt mã PIN 4 số", color = Tone.text)
                    }
                    if (hasPin) {
                        OutlinedButton(
                            onClick = {
                                pinError = null
                                currentPinInput = ""
                                pinMode = "remove"
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Xóa mã PIN", color = Tone.danger)
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Tone.surfaceHigh)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (pinMode == "change" || pinMode == "remove") {
                        OutlinedTextField(
                            value = currentPinInput,
                            onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) currentPinInput = it },
                            label = { Text("Mã PIN hiện tại (4 số)") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (pinMode == "set" || pinMode == "change") {
                        OutlinedTextField(
                            value = newPinInput,
                            onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) newPinInput = it },
                            label = { Text("Mã PIN mới (4 số)") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (pinError != null) {
                        Text(pinError!!, color = Tone.danger, fontSize = 12.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TextButton(onClick = { pinMode = null }) {
                            Text("Hủy", color = Tone.muted)
                        }
                        Button(
                            onClick = {
                                when (pinMode) {
                                    "set" -> {
                                        if (newPinInput.length == 4) {
                                            onSetPin(newPinInput)
                                            pinMode = null
                                        } else {
                                            pinError = "Vui lòng nhập đủ 4 chữ số"
                                        }
                                    }
                                    "change" -> {
                                        if (!onVerifyCurrentPin(currentPinInput)) {
                                            pinError = "Mã PIN hiện tại không đúng"
                                        } else if (newPinInput.length != 4) {
                                            pinError = "Mã PIN mới phải gồm 4 chữ số"
                                        } else {
                                            onSetPin(newPinInput)
                                            pinMode = null
                                        }
                                    }
                                    "remove" -> {
                                        if (!onVerifyCurrentPin(currentPinInput)) {
                                            pinError = "Mã PIN hiện tại không đúng"
                                        } else {
                                            onClearPin()
                                            pinMode = null
                                        }
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Tone.accent)
                        ) {
                            Text("Lưu", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AsyncAvImage(
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
