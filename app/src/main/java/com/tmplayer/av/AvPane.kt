package com.tmplayer.av

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import com.tmplayer.movie.*
import com.tmplayer.ui.icons.TmIcons
import com.tmplayer.ui.theme.Tone
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch

private val AvAccent = Color(0xFFFF2E93)
private val AvAccentSoft = Color(0xFF9C27B0)

private enum class AvMode {
    Home, Category, Search, Watchlist, History
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

private val avSources = listOf(MovieSource.AVDB, MovieSource.XXVN, MovieSource.MissAV)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvPane(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        AvStore.init(context)
    }

    val pinCode by AvStore.pinCodeFlow.collectAsState()
    val isUnlocked by AvStore.isSessionUnlockedFlow.collectAsState()
    val isTabVisible by AvStore.isTabVisibleFlow.collectAsState()
    val watchlist by AvStore.watchlistFlow.collectAsState()
    val history by AvStore.historyFlow.collectAsState()

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
    var isLoadingDetail by remember { mutableStateOf(false) }

    var isHistoryEditMode by remember { mutableStateOf(false) }
    var selectedHistorySlugs by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showPrivacySheet by remember { mutableStateOf(false) }
    var isPullRefreshing by remember { mutableStateOf(false) }

    val catalogCache = remember { mutableStateMapOf<String, AvCatalogState>() }
    val activeKey = remember(currentSource, mode, selectedCategorySlug, submittedSearch) {
        "${currentSource.name}|${mode.name}|$selectedCategorySlug|$submittedSearch"
    }
    val activeCatalog = catalogCache.getOrPut(activeKey) { AvCatalogState() }

    fun loadCatalog(forceRefresh: Boolean) {
        if (mode == AvMode.Watchlist || mode == AvMode.History) return
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
                                "📹 Amateur / FC2" to "fc2",
                                "💃 Chinese AV" to "madou"
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
        if (mode == AvMode.Home || mode == AvMode.Watchlist || mode == AvMode.History) return
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
                targetCatalog.gridMovies = (targetCatalog.gridMovies + more).distinctBy { it.slug }
            }
            targetCatalog.isLoadingMore = false
        }
    }

    fun openMovie(item: MovieItem) {
        selectedMovie = item
        isLoadingDetail = true
        movieDetail = null
        scope.launch {
            movieDetail = AvRepository.getDetail(item.source, item.slug)
            isLoadingDetail = false
        }
    }

    fun playMovie(detail: MovieDetail, serverIndex: Int = 0, episodeIndex: Int = 0) {
        OnlinePlayerActivity.launch(context, detail, serverIndex, episodeIndex)
    }

    BackHandler(enabled = selectedMovie != null || isHistoryEditMode || mode != AvMode.Home) {
        when {
            selectedMovie != null -> {
                selectedMovie = null
                movieDetail = null
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
            AvDetailContent(
                item = selectedMovie!!,
                detail = movieDetail,
                isLoading = isLoadingDetail,
                isBookmarked = watchlist.any { it.slug == selectedMovie!!.slug },
                historyRecord = history.firstOrNull { it.slug == selectedMovie!!.slug },
                onBack = {
                    selectedMovie = null
                    movieDetail = null
                },
                onPlay = { srvIdx, epIdx ->
                    movieDetail?.let { playMovie(it, srvIdx, epIdx) }
                },
                onToggleWatchlist = {
                    AvStore.toggleWatchlist(context, selectedMovie!!)
                },
                onSearchActress = { actress ->
                    selectedMovie = null
                    movieDetail = null
                    searchQuery = actress
                    submittedSearch = actress
                    mode = AvMode.Search
                }
            )
        } else {
            Column(Modifier.fillMaxSize()) {
                // Header Bar: Brand + Source Switcher + Privacy/Lock Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
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
                                .background(
                                    Brush.horizontalGradient(listOf(AvAccent, AvAccentSoft))
                                )
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "18+ AV",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp
                            )
                        }
                        if (mode != AvMode.Home) {
                            Text(
                                text = "← Trang chủ",
                                color = AvAccent,
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
                        // Source Switcher Pills
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .padding(2.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            avSources.forEach { source ->
                                val active = currentSource == source
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(if (active) AvAccent else Color.Transparent)
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
                                        color = if (active) Color.White else Tone.muted,
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
                                    if (pinCode.isNotEmpty()) AvAccent.copy(alpha = 0.2f)
                                    else Color.White.copy(alpha = 0.08f)
                                )
                                .border(
                                    1.dp,
                                    if (pinCode.isNotEmpty()) AvAccent.copy(alpha = 0.6f) else Color.Transparent,
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

                // Search Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                "Tìm mã JAV (SONE-614, FC2...), diễn viên...",
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingIcon = {
                            Icon(TmIcons.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = {
                                    searchQuery = ""
                                    submittedSearch = ""
                                    if (mode == AvMode.Search) mode = AvMode.Home
                                }) {
                                    Icon(TmIcons.Close, contentDescription = "Xóa", modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        singleLine = true,
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
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AvAccent,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.14f),
                            focusedContainerColor = Color.White.copy(alpha = 0.05f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.05f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    )
                }

                // Category & Personal Shelf Chips Row
                val categories = remember(currentSource) { AvFilterData.categoriesFor(currentSource) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AvChip(
                        label = "🏠 Trang chủ",
                        selected = mode == AvMode.Home,
                        onClick = { mode = AvMode.Home }
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
                    categories.filter { it.slug.isNotBlank() }.forEach { cat ->
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
                                    CircularProgressIndicator(color = AvAccent)
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
                                                        text = "Xem tất cả →",
                                                        color = AvAccent,
                                                        fontSize = 12.sp,
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
                                                            color = Color(0xFFFF6B6B),
                                                            fontSize = 12.sp,
                                                            modifier = Modifier.clickable {
                                                                isHistoryEditMode = true
                                                                selectedHistorySlugs = emptySet()
                                                                mode = AvMode.History
                                                            }
                                                        )
                                                        Text(
                                                            text = "Xem tất cả →",
                                                            color = AvAccent,
                                                            fontSize = 12.sp,
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
                                                    text = "Xem thêm →",
                                                    color = AvAccent,
                                                    fontSize = 12.sp,
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

                    AvMode.Watchlist -> {
                        if (watchlist.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Chưa có phim AV nào trong danh sách theo dõi", color = Tone.muted)
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 165.dp),
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 110.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
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
                                                    color = AvAccent,
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
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
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
                                                Text("Chọn xóa", color = AvAccent, fontSize = 12.sp)
                                            }
                                            TextButton(onClick = { AvStore.clearHistory(context) }) {
                                                Text("Xóa tất cả", color = Color(0xFFFF6B6B), fontSize = 12.sp)
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
                                    columns = GridCells.Adaptive(minSize = 165.dp),
                                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 110.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
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
                                    CircularProgressIndicator(color = AvAccent)
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
                                    columns = GridCells.Adaptive(minSize = 165.dp),
                                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 110.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        Text(
                                            text = if (mode == AvMode.Search) "🔍 Kết quả cho \"$submittedSearch\""
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
                                                    color = AvAccent,
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

@Composable
private fun AvChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) AvAccent else Color.White.copy(alpha = 0.07f))
            .border(
                1.dp,
                if (selected) AvAccent else Color.White.copy(alpha = 0.1f),
                RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else Tone.text,
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
                    .background(Color(0xFF1A1A24))
                    .clickable { onOpen(item) }
            ) {
                AsyncAvImage(url = item.thumbUrl.ifEmpty { item.posterUrl }, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
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
                // Code / Quality Badge at Top Left
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
                                .background(AvAccent)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = item.episodeCurrent,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                    if (item.quality.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.Black.copy(alpha = 0.7f))
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = item.quality,
                                color = Color(0xFFFFD54F),
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

@Composable
private fun AvLandscapeCard(
    item: MovieItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9.5f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1A1A24))
        ) {
            AsyncAvImage(url = item.posterUrl.ifEmpty { item.thumbUrl }, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            // Code Badge
            if (item.episodeCurrent.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(AvAccent.copy(alpha = 0.9f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = item.episodeCurrent,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
            if (item.quality.isNotBlank() || item.lang.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color.Black.copy(alpha = 0.72f))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = listOf(item.quality, item.lang).filter { it.isNotBlank() }.distinct().joinToString(" • "),
                        color = Color.White,
                        fontSize = 9.sp,
                        maxLines = 1
                    )
                }
            }
        }
        Text(
            text = item.name,
            color = Tone.text,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 15.sp
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
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
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9.5f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1A1A24))
                .then(
                    if (isSelected) Modifier.border(2.dp, AvAccent, RoundedCornerShape(12.dp))
                    else Modifier
                )
        ) {
            AsyncAvImage(url = record.posterUrl.ifEmpty { record.thumbUrl }, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (isEditMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) AvAccent else Color.Black.copy(alpha = 0.65f))
                        .border(1.5.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Text("✓", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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

            if (progress > 0f) {
                LinearProgressIndicator(
                    progress = { progress.coerceAtLeast(0.04f) },
                    color = AvAccent,
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
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 15.sp
        )
    }
}

@Composable
private fun AvDetailContent(
    item: MovieItem,
    detail: MovieDetail?,
    isLoading: Boolean,
    isBookmarked: Boolean,
    historyRecord: MovieWatchProgress?,
    onBack: () -> Unit,
    onPlay: (serverIndex: Int, episodeIndex: Int) -> Unit,
    onToggleWatchlist: () -> Unit,
    onSearchActress: (String) -> Unit
) {
    val context = LocalContext.current
    val movie = detail?.movie ?: item

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 110.dp)
    ) {
        // Top 16:9 Cover Preview
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9.2f)
                .background(Color(0xFF14141D))
        ) {
            AsyncAvImage(url = movie.thumbUrl.ifEmpty { movie.posterUrl }, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.45f),
                                Color.Transparent,
                                Tone.background
                            )
                        )
                    )
            )
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
            ) {
                Icon(TmIcons.ArrowBack, contentDescription = "Quay lại", tint = Color.White)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Badges
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(AvAccent)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = movie.source.badge,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (movie.episodeCurrent.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = movie.episodeCurrent,
                            color = Color(0xFFFFD54F),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (movie.year.isNotBlank()) {
                    Text(movie.year, color = Tone.muted, fontSize = 12.sp)
                }
                if (detail?.time?.isNotBlank() == true) {
                    Text("• ${detail.time}", color = Tone.muted, fontSize = 12.sp)
                }
            }

            Text(
                text = movie.name,
                color = Tone.text,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        if (detail != null && detail.servers.isNotEmpty()) {
                            val srvIdx = historyRecord?.serverIndex ?: 0
                            val epIdx = historyRecord?.episodeIndex ?: 0
                            onPlay(srvIdx, epIdx)
                        }
                    },
                    enabled = !isLoading && detail != null && detail.servers.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = AvAccent),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = if (historyRecord != null && historyRecord.positionMs > 15_000L) "▶ Xem tiếp"
                            else "▶ Xem ngay",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }

                OutlinedButton(
                    onClick = onToggleWatchlist,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(46.dp)
                ) {
                    Text(
                        text = if (isBookmarked) "★ Đang theo dõi" else "☆ Theo dõi",
                        color = if (isBookmarked) Color(0xFFFFD54F) else Tone.text,
                        fontSize = 13.sp
                    )
                }
            }

            // Clickable Actress Tags
            if (!detail?.actor.isNullOrEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "👩 Diễn viên (Nhấn để tìm phim cùng diễn viên):",
                        color = Tone.muted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        detail!!.actor.forEach { actress ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(AvAccent.copy(alpha = 0.16f))
                                    .border(1.dp, AvAccent.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                                    .clickable { onSearchActress(actress) }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "👩 $actress",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // Category Tags
            if (!detail?.categories.isNullOrEmpty()) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    detail!!.categories.forEach { catName ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .clickable { onSearchActress(catName) }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(catName, color = Tone.muted, fontSize = 11.sp)
                        }
                    }
                }
            }

            // Description / Overview
            if (!detail?.content.isNullOrBlank()) {
                Text(
                    text = detail!!.content,
                    color = Tone.text.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            }
        }
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
                .background(AvAccent.copy(alpha = 0.16f)),
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
            color = if (errorMessage != null) Color(0xFFFF5252) else Tone.muted,
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
                        .background(if (filled) AvAccent else Color.White.copy(alpha = 0.18f))
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
                                .background(Color.White.copy(alpha = 0.08f))
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

@OptIn(ExperimentalMaterial3Api::class)
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
    var pinMode by remember { mutableStateOf<String?>(null) } // "set", "change", "remove"
    var pinError by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A24)
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
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )

            // Show / Hide AV Tab on Navigation Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Hiện Tab AV trên thanh điều hướng",
                        color = Color.White,
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
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AvAccent)
                )
            }

            if (hasPin) {
                Button(
                    onClick = onLockNow,
                    colors = ButtonDefaults.buttonColors(containerColor = AvAccent),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🔒 Khóa Tab AV ngay lập tức", color = Color.White, fontWeight = FontWeight.Bold)
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
                        Text(if (hasPin) "Đổi mã PIN" else "Đặt mã PIN 4 số", color = Color.White)
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
                            Text("Xóa mã PIN", color = Color(0xFFFF6B6B))
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.06f))
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
                        Text(pinError!!, color = Color(0xFFFF5252), fontSize = 12.sp)
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
                            colors = ButtonDefaults.buttonColors(containerColor = AvAccent)
                        ) {
                            Text("Lưu", color = Color.White)
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
            modifier = modifier.background(Color(0xFF1A1A24)),
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
