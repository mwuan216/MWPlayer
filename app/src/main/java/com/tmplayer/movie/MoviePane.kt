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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tmplayer.ui.components.LocalBottomChrome
import com.tmplayer.ui.icons.TmIcons
import com.tmplayer.ui.theme.Tone
import kotlinx.coroutines.launch

/**
 * Màn hình Movie Online: KKPhim & VSMov
 * Bao gồm:
 * - Chuyển nguồn nhanh (KKPhim / VSMov)
 * - Ô tìm kiếm phim online
 * - Hàng bộ lọc: Danh mục (Phim bộ, Phim lẻ, Hoạt hình, TV Shows), Thể loại, Quốc gia, Năm
 * - Catalog trang chủ: Banner nổi bật, các danh sách ngang (Mới cập nhật, Phim bộ, Phim lẻ, Trung Quốc, Hàn Quốc, Hoạt hình...)
 * - Grid xem tất cả / kết quả tìm kiếm / kết quả lọc với phân trang
 * - Bottom Sheet xem chi tiết, chọn server, chọn tập và mở Player xem trực tiếp!
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
    var isSearching by remember { mutableStateOf(false) }

    // Filter states
    var selectedCategory by remember { mutableStateOf("") }
    var selectedGenre by remember { mutableStateOf("") }
    var selectedCountry by remember { mutableStateOf("") }
    var selectedYear by remember { mutableStateOf("") }

    // Section view all target (null means home view)
    var viewAllCategory by remember { mutableStateOf<Pair<String, String>?>(null) } // slug to title

    // Home sections data
    var latestMovies by remember { mutableStateOf<List<MovieItem>>(emptyList()) }
    var seriesMovies by remember { mutableStateOf<List<MovieItem>>(emptyList()) }
    var singleMovies by remember { mutableStateOf<List<MovieItem>>(emptyList()) }
    var chinaMovies by remember { mutableStateOf<List<MovieItem>>(emptyList()) }
    var koreaMovies by remember { mutableStateOf<List<MovieItem>>(emptyList()) }
    var animeMovies by remember { mutableStateOf<List<MovieItem>>(emptyList()) }
    var isHomeLoading by remember { mutableStateOf(true) }

    // Grid filter / search data
    var gridMovies by remember { mutableStateOf<List<MovieItem>>(emptyList()) }
    var gridPage by remember { mutableIntStateOf(1) }
    var gridTotalPages by remember { mutableIntStateOf(1) }
    var isGridLoading by remember { mutableStateOf(false) }

    // Detail Sheet
    var selectedMovieSlug by remember { mutableStateOf<String?>(null) }
    var movieDetail by remember { mutableStateOf<MovieDetail?>(null) }
    var isDetailLoading by remember { mutableStateOf(false) }

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
                seriesMovies = MovieRepository.getCategoryMovies(activeSource, "phim-bo", 1)
                singleMovies = MovieRepository.getCategoryMovies(activeSource, "phim-le", 1)
                chinaMovies = MovieRepository.getCountryMovies(activeSource, "trung-quoc", 1)
                koreaMovies = MovieRepository.getCountryMovies(activeSource, "han-quoc", 1)
                animeMovies = MovieRepository.getCategoryMovies(activeSource, "hoat-hinh", 1)
            } catch (e: Exception) {
                // Ignore errors
            } finally {
                isHomeLoading = false
            }
        }
    }

    // Load Grid Data (Filter / Search / View All)
    fun loadGrid(page: Int) {
        scope.launch {
            isGridLoading = true
            gridPage = page
            try {
                if (searchQuery.isNotBlank()) {
                    val res = MovieRepository.searchMovies(activeSource, searchQuery, page)
                    gridMovies = res
                    gridTotalPages = if (res.size >= 18) page + 1 else page
                } else if (viewAllCategory != null) {
                    val slug = viewAllCategory!!.first
                    val res = if (slug == "trung-quoc" || slug == "han-quoc") {
                        MovieRepository.getCountryMovies(activeSource, slug, page)
                    } else if (slug == "latest") {
                        MovieRepository.getLatestMovies(activeSource, page)
                    } else {
                        MovieRepository.getCategoryMovies(activeSource, slug, page)
                    }
                    gridMovies = res
                    gridTotalPages = if (res.size >= 12) page + 1 else page
                } else {
                    val (items, total) = MovieRepository.filterMovies(
                        source = activeSource,
                        category = selectedCategory,
                        genre = selectedGenre,
                        country = selectedCountry,
                        year = selectedYear,
                        keyword = "",
                        page = page
                    )
                    gridMovies = items
                    gridTotalPages = total
                }
            } catch (e: Exception) {
                gridMovies = emptyList()
            } finally {
                isGridLoading = false
            }
        }
    }

    // Trigger home load on source change
    LaunchedEffect(activeSource) {
        if (!isCatalogGridActive) {
            loadHome()
        } else {
            loadGrid(1)
        }
    }

    // Trigger grid load when filter / search / viewAll changes
    LaunchedEffect(searchQuery, selectedCategory, selectedGenre, selectedCountry, selectedYear, viewAllCategory) {
        if (isCatalogGridActive) {
            loadGrid(1)
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
            } catch (e: Exception) {
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

            // SEARCH BAR
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
        }

        // FILTER CHIPS ROW (Categories, Genres, Countries, Years)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Tone.surface)
                .padding(bottom = 8.dp)
        ) {
            // Category Row
            FilterScrollRow(
                options = MovieFilterData.categories,
                selected = selectedCategory,
                onSelect = {
                    selectedCategory = it
                    viewAllCategory = null
                }
            )

            // Genre Row
            FilterScrollRow(
                options = MovieFilterData.genres,
                selected = selectedGenre,
                onSelect = {
                    selectedGenre = it
                    viewAllCategory = null
                }
            )

            // Country & Year Combined Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Clear all filters button if any is selected
                if (selectedCategory.isNotBlank() || selectedGenre.isNotBlank() || selectedCountry.isNotBlank() || selectedYear.isNotBlank() || viewAllCategory != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Tone.accent.copy(alpha = 0.2f))
                            .border(1.dp, Tone.accent, RoundedCornerShape(8.dp))
                            .clickable {
                                selectedCategory = ""
                                selectedGenre = ""
                                selectedCountry = ""
                                selectedYear = ""
                                viewAllCategory = null
                                searchQuery = ""
                            }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✕ Đặt lại", color = Tone.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Country options
                MovieFilterData.countries.forEach { opt ->
                    FilterChip(
                        label = opt.label,
                        isSelected = selectedCountry == opt.slug,
                        onClick = {
                            selectedCountry = if (selectedCountry == opt.slug) "" else opt.slug
                            viewAllCategory = null
                        }
                    )
                }

                // Year options
                MovieFilterData.years.forEach { opt ->
                    FilterChip(
                        label = opt.label,
                        isSelected = selectedYear == opt.slug,
                        onClick = {
                            selectedYear = if (selectedYear == opt.slug) "" else opt.slug
                            viewAllCategory = null
                        }
                    )
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
                // CATALOG GRID VIEW (FILTER / SEARCH / VIEW ALL)
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
                    Column(Modifier.fillMaxSize()) {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 130.dp),
                            contentPadding = PaddingValues(
                                start = 12.dp,
                                end = 12.dp,
                                top = 12.dp,
                                bottom = bottomChrome + 64.dp
                            ),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            items(gridMovies, key = { it.slug }) { item ->
                                MoviePosterCard(
                                    movie = item,
                                    onClick = { selectedMovieSlug = item.slug }
                                )
                            }
                        }

                        // Pagination bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Tone.surface)
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (gridPage > 1) Tone.surfaceHigh else Color.Transparent)
                                    .clickable(enabled = gridPage > 1) { loadGrid(gridPage - 1) }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    "‹ Trang trước",
                                    color = if (gridPage > 1) Tone.text else Tone.text.copy(alpha = 0.3f),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Text(
                                "Trang $gridPage" + (if (gridTotalPages > 1) " / $gridTotalPages" else ""),
                                color = Tone.accent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (gridPage < gridTotalPages) Tone.surfaceHigh else Color.Transparent)
                                    .clickable(enabled = gridPage < gridTotalPages) { loadGrid(gridPage + 1) }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    "Trang tiếp ›",
                                    color = if (gridPage < gridTotalPages) Tone.text else Tone.text.copy(alpha = 0.3f),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            } else {
                // =============================================================
                // HOME SHELVES VIEW (HERO CAROUSEL + HORIZONTAL ROWS)
                // =============================================================
                if (isHomeLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Tone.accent, strokeWidth = 3.dp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 10.dp, bottom = bottomChrome + 40.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        // FEATURED HERO BANNER
                        if (latestMovies.isNotEmpty()) {
                            item(key = "featured_hero") {
                                val featured = latestMovies.first()
                                MovieFeaturedHero(
                                    movie = featured,
                                    onClick = { selectedMovieSlug = featured.slug }
                                )
                            }
                        }

                        // SHELF 1: PHIM MỚI CẬP NHẬT
                        if (latestMovies.size > 1) {
                            item(key = "shelf_latest") {
                                MovieShelfRow(
                                    title = "🌟 Phim Mới Cập Nhật",
                                    movies = latestMovies.drop(1),
                                    onViewAll = { viewAllCategory = "latest" to "Phim Mới Cập Nhật" },
                                    onMovieClick = { selectedMovieSlug = it.slug }
                                )
                            }
                        }

                        // SHELF 2: PHIM BỘ ĐANG HOT
                        if (seriesMovies.isNotEmpty()) {
                            item(key = "shelf_series") {
                                MovieShelfRow(
                                    title = "🎬 Phim Bộ Đang Hot",
                                    movies = seriesMovies,
                                    onViewAll = { viewAllCategory = "phim-bo" to "Phim Bộ" },
                                    onMovieClick = { selectedMovieSlug = it.slug }
                                )
                            }
                        }

                        // SHELF 3: PHIM LẺ ĐẶC SẮC
                        if (singleMovies.isNotEmpty()) {
                            item(key = "shelf_single") {
                                MovieShelfRow(
                                    title = "🍿 Phim Lẻ Đặc Sắc",
                                    movies = singleMovies,
                                    onViewAll = { viewAllCategory = "phim-le" to "Phim Lẻ" },
                                    onMovieClick = { selectedMovieSlug = it.slug }
                                )
                            }
                        }

                        // SHELF 4: PHIM TRUNG QUỐC
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

                        // SHELF 5: PHIM HÀN QUỐC
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

                        // SHELF 6: HOẠT HÌNH & ANIME
                        if (animeMovies.isNotEmpty()) {
                            item(key = "shelf_anime") {
                                MovieShelfRow(
                                    title = "🎌 Hoạt Hình & Anime",
                                    movies = animeMovies,
                                    onViewAll = { viewAllCategory = "hoat-hinh" to "Hoạt Hình" },
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
                    .height(580.dp)
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

@Composable
private fun FilterScrollRow(
    options: List<FilterOption>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        options.forEach { opt ->
            FilterChip(
                label = opt.label,
                isSelected = selected == opt.slug,
                onClick = { onSelect(if (selected == opt.slug) "" else opt.slug) }
            )
        }
    }
}

@Composable
private fun FilterChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) Tone.accent else Tone.surfaceHigh)
            .border(
                0.5.dp,
                if (isSelected) Tone.accent else Tone.outline.copy(alpha = 0.4f),
                RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            color = if (isSelected) Color.Black else Tone.text.copy(alpha = 0.8f),
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun MovieFeaturedHero(
    movie: MovieItem,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Tone.surfaceHigh)
            .clickable(onClick = onClick)
    ) {
        // Poster / Backdrop Image
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
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f)),
                        startY = 60f
                    )
                )
        )

        // Movie Info Overlay
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (movie.quality.isNotBlank()) {
                    BadgePill(text = movie.quality, bg = Tone.accent, textColor = Color.Black)
                    Spacer(Modifier.width(6.dp))
                }
                if (movie.lang.isNotBlank()) {
                    BadgePill(text = movie.lang, bg = Color.White.copy(alpha = 0.2f), textColor = Color.White)
                    Spacer(Modifier.width(6.dp))
                }
                if (movie.episodeCurrent.isNotBlank()) {
                    BadgePill(text = movie.episodeCurrent, bg = Color.Black.copy(alpha = 0.5f), textColor = Tone.accent)
                }
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = movie.name,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (movie.originName.isNotBlank()) {
                Text(
                    text = movie.originName + if (movie.year.isNotBlank()) " (${movie.year})" else "",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
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

            // Top-left badge: Quality or Episode
            if (movie.episodeCurrent.isNotBlank()) {
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
                    fontWeight = FontWeight.Bold
                )

                if (detail.movie.originName.isNotBlank()) {
                    Text(
                        text = detail.movie.originName,
                        color = Tone.text.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    )
                }

                Spacer(Modifier.height(6.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (detail.movie.year.isNotBlank()) {
                        BadgePill(detail.movie.year, bg = Tone.surfaceHigh, textColor = Tone.text)
                    }
                    if (detail.movie.quality.isNotBlank()) {
                        BadgePill(detail.movie.quality, bg = Tone.accent, textColor = Color.Black)
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
                        fontSize = 12.sp
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
                                text = srv.serverName,
                                color = if (isSel) Color.Black else Tone.text,
                                fontSize = 12.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
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
                                                        epRange = epNum
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
                            // Fill remaining spaces in row
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
                                    epRange = "all"
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
                                    epRange = "remaining"
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
                                        epRange = input
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
private fun BadgePill(text: String, bg: Color, textColor: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text = text, color = textColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
