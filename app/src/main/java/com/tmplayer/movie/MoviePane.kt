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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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

    var activeSource by remember { mutableStateOf(MovieSource.KKPhim) }
    var searchQuery by remember { mutableStateOf("") }

    // Quick category pill selection
    var selectedCategory by remember { mutableStateOf("") }

    // Advanced filter sheet states
    var showFilterSheet by remember { mutableStateOf(false) }
    var selectedGenre by remember { mutableStateOf("") }
    var selectedCountry by remember { mutableStateOf("") }
    var selectedYear by remember { mutableStateOf("") }

    // Section view all target (null means home catalog view)
    var viewAllCategory by remember { mutableStateOf<Pair<String, String>?>(null) } // slug to title

    // Home rich shelves data
    var latestMovies by remember { mutableStateOf<List<MovieItem>>(emptyList()) }
    var cinemaMovies by remember { mutableStateOf<List<MovieItem>>(emptyList()) }
    var seriesMovies by remember { mutableStateOf<List<MovieItem>>(emptyList()) }
    var singleMovies by remember { mutableStateOf<List<MovieItem>>(emptyList()) }
    var costumeMovies by remember { mutableStateOf<List<MovieItem>>(emptyList()) }
    var chinaMovies by remember { mutableStateOf<List<MovieItem>>(emptyList()) }
    var koreaMovies by remember { mutableStateOf<List<MovieItem>>(emptyList()) }
    var animeMovies by remember { mutableStateOf<List<MovieItem>>(emptyList()) }
    var subteamMovies by remember { mutableStateOf<List<MovieItem>>(emptyList()) }
    var isHomeLoading by remember { mutableStateOf(true) }

    // Grid filter / search data (Infinite scroll)
    var gridMovies by remember { mutableStateOf<List<MovieItem>>(emptyList()) }
    var gridPage by remember { mutableIntStateOf(1) }
    var gridTotalPages by remember { mutableIntStateOf(1) }
    var isGridLoading by remember { mutableStateOf(false) }
    var isGridLoadingMore by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()

    // Detail Sheet
    var selectedMovieSlug by remember { mutableStateOf<String?>(null) }
    var movieDetail by remember { mutableStateOf<MovieDetail?>(null) }
    var isDetailLoading by remember { mutableStateOf(false) }

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

    // Load Home Shelves
    fun loadHome() {
        scope.launch {
            isHomeLoading = true
            try {
                latestMovies = MovieRepository.getLatestMovies(activeSource, 1)
                cinemaMovies = MovieRepository.getCategoryMovies(activeSource, "phim-chieu-rap", 1)
                seriesMovies = MovieRepository.getCategoryMovies(activeSource, "phim-bo", 1)
                singleMovies = MovieRepository.getCategoryMovies(activeSource, "phim-le", 1)
                costumeMovies = MovieRepository.getGenreMovies(activeSource, "co-trang", 1)
                chinaMovies = MovieRepository.getCountryMovies(activeSource, "trung-quoc", 1)
                koreaMovies = MovieRepository.getCountryMovies(activeSource, "han-quoc", 1)
                animeMovies = MovieRepository.getCategoryMovies(activeSource, "hoat-hinh", 1)
                if (activeSource == MovieSource.VSMov) {
                    subteamMovies = MovieRepository.getCategoryMovies(activeSource, "subteam", 1)
                }
            } catch (_: Exception) {
            } finally {
                isHomeLoading = false
            }
        }
    }

    // Load Grid Data Initial
    fun loadGridInitial() {
        scope.launch {
            isGridLoading = true
            gridPage = 1
            try {
                if (searchQuery.isNotBlank()) {
                    val res = MovieRepository.searchMovies(activeSource, searchQuery, 1)
                    gridMovies = res
                    gridTotalPages = if (res.size >= 18) 2 else 1
                } else if (viewAllCategory != null) {
                    val slug = viewAllCategory!!.first
                    val res = when (slug) {
                        "trung-quoc", "han-quoc", "au-my" -> MovieRepository.getCountryMovies(activeSource, slug, 1)
                        "co-trang" -> MovieRepository.getGenreMovies(activeSource, slug, 1)
                        "latest" -> MovieRepository.getLatestMovies(activeSource, 1)
                        else -> MovieRepository.getCategoryMovies(activeSource, slug, 1)
                    }
                    gridMovies = res
                    gridTotalPages = if (res.size >= 12) 2 else 1
                } else {
                    val (items, total) = MovieRepository.filterMovies(
                        source = activeSource,
                        category = selectedCategory,
                        genre = selectedGenre,
                        country = selectedCountry,
                        year = selectedYear,
                        keyword = "",
                        page = 1
                    )
                    gridMovies = items
                    gridTotalPages = total
                }
            } catch (_: Exception) {
                gridMovies = emptyList()
            } finally {
                isGridLoading = false
            }
        }
    }

    // Load More Grid Data (Endless Pagination)
    fun loadGridMore() {
        if (isGridLoadingMore || isGridLoading || gridPage >= gridTotalPages) return
        scope.launch {
            isGridLoadingMore = true
            val nextPage = gridPage + 1
            try {
                val newItems = if (searchQuery.isNotBlank()) {
                    val res = MovieRepository.searchMovies(activeSource, searchQuery, nextPage)
                    if (res.size >= 18) gridTotalPages = nextPage + 1
                    res
                } else if (viewAllCategory != null) {
                    val slug = viewAllCategory!!.first
                    val res = when (slug) {
                        "trung-quoc", "han-quoc", "au-my" -> MovieRepository.getCountryMovies(activeSource, slug, nextPage)
                        "co-trang" -> MovieRepository.getGenreMovies(activeSource, slug, nextPage)
                        "latest" -> MovieRepository.getLatestMovies(activeSource, nextPage)
                        else -> MovieRepository.getCategoryMovies(activeSource, slug, nextPage)
                    }
                    if (res.size >= 12) gridTotalPages = nextPage + 1
                    res
                } else {
                    val (items, total) = MovieRepository.filterMovies(
                        source = activeSource,
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
                    gridMovies = gridMovies + distinctNew
                    gridPage = nextPage
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

    // Trigger home load on source change
    LaunchedEffect(activeSource) {
        if (!isCatalogGridActive) {
            loadHome()
        } else {
            loadGridInitial()
        }
    }

    // Trigger grid load when filter / search / viewAll changes
    LaunchedEffect(searchQuery, selectedCategory, selectedGenre, selectedCountry, selectedYear, viewAllCategory) {
        if (isCatalogGridActive) {
            loadGridInitial()
        }
    }

    // Fetch movie detail when selected
    LaunchedEffect(selectedMovieSlug) {
        val slug = selectedMovieSlug
        if (slug != null) {
            isDetailLoading = true
            movieDetail = null
            try {
                movieDetail = MovieRepository.getMovieDetail(activeSource, slug)
            } catch (_: Exception) {
                movieDetail = null
            } finally {
                isDetailLoading = false
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

                // SOURCE SWITCHER (KKPhim / VSMov)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Tone.surfaceHigh)
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    SourceTabButton(
                        title = "KKPhim",
                        isSelected = activeSource == MovieSource.KKPhim,
                        onClick = { activeSource = MovieSource.KKPhim }
                    )
                    SourceTabButton(
                        title = "VSMov",
                        isSelected = activeSource == MovieSource.VSMov,
                        onClick = { activeSource = MovieSource.VSMov }
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
                        "han-quoc", "trung-quoc" -> selectedCountry == slug
                        "co-trang" -> selectedGenre == slug
                        else -> selectedCategory == slug && selectedCountry.isBlank() && selectedGenre.isBlank()
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) Tone.accent else Tone.surfaceHigh)
                            .clickable {
                                viewAllCategory = null
                                when (slug) {
                                    "han-quoc", "trung-quoc" -> {
                                        selectedCountry = if (selectedCountry == slug) "" else slug
                                        selectedCategory = ""
                                        selectedGenre = ""
                                    }
                                    "co-trang" -> {
                                        selectedGenre = if (selectedGenre == slug) "" else slug
                                        selectedCategory = ""
                                        selectedCountry = ""
                                    }
                                    else -> {
                                        selectedCategory = if (selectedCategory == slug) "" else slug
                                        selectedCountry = ""
                                        selectedGenre = ""
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

        // MAIN CONTENT AREA
        Box(
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
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = TmIcons.Trailer,
                                contentDescription = null,
                                tint = Tone.text.copy(alpha = 0.3f),
                                modifier = Modifier.size(54.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Không tìm thấy phim phù hợp",
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
                        items(gridMovies, key = { it.slug }) { item ->
                            MoviePosterCard(
                                movie = item,
                                onClick = { selectedMovieSlug = item.slug }
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
    onClick: () -> Unit
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
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(10.dp))
                .background(Tone.surfaceHigh)
        ) {
            AsyncMovieImage(
                url = movie.thumbUrl.ifBlank { movie.posterUrl },
                modifier = Modifier.fillMaxSize()
            )

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
    var selectedServerIdx by remember { mutableIntStateOf(0) }
    var expandedDescription by remember { mutableStateOf(false) }
    var showCloudSheet by remember { mutableStateOf(false) }
    var isCloudMode by remember { mutableStateOf(false) }
    var customRangeInput by remember { mutableStateOf("") }

    val activeServer = detail.servers.getOrNull(selectedServerIdx) ?: detail.servers.firstOrNull()
    val epList = activeServer?.episodes ?: emptyList()

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

        // PRIMARY ACTION BUTTONS (Xem phim ngay + Kéo về Cloud)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Xem phim ngay
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Tone.accent)
                    .clickable { onPlayEpisode(selectedServerIdx, 0) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = TmIcons.PlaySolid,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Xem phim ngay",
                        color = Color.Black,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Kéo về Cloud (MovieBox)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Tone.surfaceHigh)
                    .border(0.5.dp, Tone.outline.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .clickable { showCloudSheet = true }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = TmIcons.Download,
                        contentDescription = null,
                        tint = Tone.accent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Kéo về Cloud",
                        color = Tone.text,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
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
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isCloudMode) Tone.accent.copy(alpha = 0.15f) else Tone.surfaceHigh)
                                        .border(
                                            0.5.dp,
                                            if (isCloudMode) Tone.accent.copy(alpha = 0.6f) else Tone.outline.copy(alpha = 0.5f),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            if (isCloudMode) {
                                                scope.launch {
                                                    val epNum = "${eIdx + 1}"
                                                    MovieCloudDownloader.pullToCloud(
                                                        context = context,
                                                        slug = detail.movie.slug,
                                                        movieTitle = detail.movie.name,
                                                        source = detail.movie.source,
                                                        epRange = epNum,
                                                        totalEpisodes = epList.size
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
                                            Icon(
                                                imageVector = TmIcons.Download,
                                                contentDescription = null,
                                                tint = Tone.accent,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(Modifier.width(3.dp))
                                        }
                                        Text(
                                            text = ep.name,
                                            color = if (isCloudMode) Tone.accent else Tone.text,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
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
        ModalBottomSheet(
            onDismissRequest = { showCloudSheet = false },
            sheetState = rememberModalBottomSheetState(),
            containerColor = Tone.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
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
                        text = "MovieBox #43264",
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

                Spacer(Modifier.height(16.dp))

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
                                    totalEpisodes = epList.size
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
                                text = "Tải toàn bộ ${epList.size} tập về Topic MovieBox",
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
                                    totalEpisodes = epList.size
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
                                        totalEpisodes = epList.size
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

                Spacer(Modifier.height(30.dp))
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
