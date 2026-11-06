package com.tmplayer.movie

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.tmplayer.ui.icons.TmIcons
import com.tmplayer.ui.theme.Tone
import kotlinx.coroutines.delay

/**
 * Trình phát video phim trực tuyến cho MovieBot / MWPlayer
 * Hỗ trợ HLS .m3u8, MP4 đa luồng, chọn server, chọn tập, điều khiển tốc độ, tua 10s...
 */
object OnlinePlayerSession {
    var detail: MovieDetail? = null
    var serverIndex: Int = 0
    var episodeIndex: Int = 0
}

@OptIn(UnstableApi::class)
class OnlinePlayerActivity : ComponentActivity() {

    private var player: ExoPlayer? = null

    companion object {
        fun launch(context: Context, detail: MovieDetail, serverIdx: Int = 0, epIdx: Int = 0) {
            OnlinePlayerSession.detail = detail
            OnlinePlayerSession.serverIndex = serverIdx
            OnlinePlayerSession.episodeIndex = epIdx
            context.startActivity(Intent(context, OnlinePlayerActivity::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemUI()

        setContent {
            OnlinePlayerScreen(
                onClose = { finish() },
                onToggleOrientation = { toggleOrientation() }
            )
        }
    }

    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
        }
    }

    private fun toggleOrientation() {
        requestedOrientation = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
    }
}

@OptIn(UnstableApi::class)
@Composable
fun OnlinePlayerScreen(
    onClose: () -> Unit,
    onToggleOrientation: () -> Unit
) {
    val detail = OnlinePlayerSession.detail ?: run {
        onClose()
        return
    }

    var serverIdx by remember { mutableIntStateOf(OnlinePlayerSession.serverIndex) }
    var epIdx by remember { mutableIntStateOf(OnlinePlayerSession.episodeIndex) }

    val currentServer = detail.servers.getOrNull(serverIdx) ?: detail.servers.firstOrNull()
    val episodes = currentServer?.episodes ?: emptyList()
    val currentEp = episodes.getOrNull(epIdx) ?: episodes.firstOrNull()

    var streamInfo by remember(serverIdx, epIdx) {
        mutableStateOf(
            if (currentServer != null && currentEp != null) {
                MovieRepository.resolveStream(detail.movie.source, detail.movie.name, currentServer, currentEp)
            } else null
        )
    }

    var isPlaying by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(true) }
    var isEnded by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var showControls by remember { mutableStateOf(true) }
    var lastInteraction by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showEpisodesSheet by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }

    // Auto-hide overlay
    LaunchedEffect(lastInteraction, showControls) {
        if (showControls) {
            delay(4500)
            if (System.currentTimeMillis() - lastInteraction >= 4000 && isPlaying) {
                showControls = false
            }
        }
    }

    // Auto next episode when finished
    LaunchedEffect(isEnded) {
        if (isEnded) {
            if (epIdx + 1 < episodes.size) {
                epIdx += 1
                OnlinePlayerSession.episodeIndex = epIdx
                isEnded = false
            }
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current

    val exoPlayer = remember {
        val httpSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
            .setAllowCrossProtocolRedirects(true)

        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(httpSourceFactory)

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
    }

    // Update media source when stream changes
    LaunchedEffect(streamInfo?.streamUrl) {
        val stream = streamInfo
        if (stream != null && stream.streamUrl.isNotBlank()) {
            isLoading = true
            isEnded = false
            val item = MediaItem.fromUri(stream.streamUrl)
            exoPlayer.setMediaItem(item)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        }
    }

    // Player event listener
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                isLoading = (state == Player.STATE_BUFFERING)
                isEnded = (state == Player.STATE_ENDED)
                if (state == Player.STATE_READY) {
                    duration = exoPlayer.duration.coerceAtLeast(0L)
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Position tracker loop
    LaunchedEffect(isPlaying) {
        while (true) {
            if (exoPlayer.playbackState == Player.STATE_READY || exoPlayer.isPlaying) {
                currentPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
                duration = exoPlayer.duration.coerceAtLeast(0L)
            }
            delay(500)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                showControls = !showControls
                lastInteraction = System.currentTimeMillis()
            }
    ) {
        // Video Surface
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = exoPlayer
                    useController = false
                    this.resizeMode = resizeMode
                    setBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            update = { view ->
                view.resizeMode = resizeMode
            },
            modifier = Modifier.fillMaxSize()
        )

        // Loading Spinner
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Tone.accent, strokeWidth = 3.dp)
            }
        }

        // Controls Overlay
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.52f))
            ) {
                // TOP BAR
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ControlButton(icon = TmIcons.ArrowBack, onClick = onClose)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = detail.movie.name,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = currentEp?.name ?: "",
                                color = Tone.accent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            if (currentServer != null) {
                                Text(
                                    text = " • ${currentServer.serverName}",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 12.sp
                                )
                            }
                            Text(
                                text = " • [${detail.movie.source.badge}]",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Aspect ratio / resize mode
                    ControlButton(
                        icon = TmIcons.AspectRatio,
                        onClick = {
                            resizeMode = when (resizeMode) {
                                AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                            }
                            lastInteraction = System.currentTimeMillis()
                        }
                    )
                    Spacer(Modifier.width(8.dp))

                    // Orientation toggle
                    ControlButton(icon = TmIcons.Fullscreen, onClick = onToggleOrientation)
                }

                // CENTER CONTROLS
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Rewind 10s
                    ControlButton(
                        icon = TmIcons.Rewind,
                        size = 48.dp,
                        onClick = {
                            exoPlayer.seekTo((exoPlayer.currentPosition - 10000).coerceAtLeast(0L))
                            lastInteraction = System.currentTimeMillis()
                        }
                    )

                    // Play / Pause
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(Tone.accent)
                            .clickable {
                                if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                                lastInteraction = System.currentTimeMillis()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) TmIcons.PauseSolid else TmIcons.PlaySolid,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.Black,
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    // Forward 10s
                    ControlButton(
                        icon = TmIcons.FastForward,
                        size = 48.dp,
                        onClick = {
                            val dur = exoPlayer.duration
                            val target = exoPlayer.currentPosition + 10000
                            exoPlayer.seekTo(if (dur > 0) target.coerceAtMost(dur) else target)
                            lastInteraction = System.currentTimeMillis()
                        }
                    )
                }

                // BOTTOM CONTROLS
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    // Seek Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatTimeMs(currentPosition),
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp,
                            modifier = Modifier.width(48.dp)
                        )
                        Slider(
                            value = if (duration > 0) (currentPosition.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                            onValueChange = { frac ->
                                lastInteraction = System.currentTimeMillis()
                                if (duration > 0) {
                                    val target = (frac * duration).toLong()
                                    exoPlayer.seekTo(target)
                                    currentPosition = target
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = Tone.accent,
                                activeTrackColor = Tone.accent,
                                inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                            )
                        )
                        Text(
                            text = formatTimeMs(duration),
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp,
                            modifier = Modifier.width(48.dp)
                        )
                    }

                    // Action buttons row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Prev episode
                            if (epIdx > 0) {
                                ControlPill(
                                    text = "Tập trước",
                                    icon = TmIcons.SkipPrevious,
                                    onClick = {
                                        epIdx -= 1
                                        OnlinePlayerSession.episodeIndex = epIdx
                                        lastInteraction = System.currentTimeMillis()
                                    }
                                )
                            }

                            // Next episode
                            if (epIdx + 1 < episodes.size) {
                                ControlPill(
                                    text = "Tập tiếp",
                                    icon = TmIcons.SkipNext,
                                    onClick = {
                                        epIdx += 1
                                        OnlinePlayerSession.episodeIndex = epIdx
                                        lastInteraction = System.currentTimeMillis()
                                    }
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Playback speed toggle
                            ControlPill(
                                text = "${playbackSpeed}x",
                                onClick = {
                                    playbackSpeed = when (playbackSpeed) {
                                        1.0f -> 1.25f
                                        1.25f -> 1.5f
                                        1.5f -> 2.0f
                                        2.0f -> 0.75f
                                        else -> 1.0f
                                    }
                                    exoPlayer.playbackParameters = PlaybackParameters(playbackSpeed)
                                    lastInteraction = System.currentTimeMillis()
                                }
                            )

                            // Episode list picker
                            ControlPill(
                                text = "Danh sách tập",
                                icon = TmIcons.Episodes,
                                onClick = {
                                    showEpisodesSheet = true
                                    lastInteraction = System.currentTimeMillis()
                                }
                            )
                        }
                    }
                }
            }
        }

        // EPISODES DRAWER / SHEET
        if (showEpisodesSheet) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .clickable { showEpisodesSheet = false },
                contentAlignment = Alignment.CenterEnd
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.55f)
                        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {},
                    color = Tone.surface,
                    shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
                ) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Chọn tập phim",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            ControlButton(icon = TmIcons.Close, onClick = { showEpisodesSheet = false })
                        }

                        Spacer(Modifier.height(10.dp))

                        // Server tabs if multiple
                        if (detail.servers.size > 1) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                detail.servers.forEachIndexed { sI, srv ->
                                    val isSelected = (sI == serverIdx)
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) Tone.accent else Color.White.copy(alpha = 0.12f))
                                            .clickable {
                                                serverIdx = sI
                                                OnlinePlayerSession.serverIndex = sI
                                                epIdx = 0
                                                OnlinePlayerSession.episodeIndex = 0
                                            }
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            srv.serverName,
                                            color = if (isSelected) Color.Black else Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }

                        // Episodes Grid
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            itemsIndexed(episodes) { i, ep ->
                                val isCurrent = (i == epIdx)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isCurrent) Tone.accent else Color.White.copy(alpha = 0.08f))
                                        .clickable {
                                            epIdx = i
                                            OnlinePlayerSession.episodeIndex = i
                                            showEpisodesSheet = false
                                        }
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = ep.name,
                                        color = if (isCurrent) Color.Black else Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
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

@Composable
private fun ControlButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 40.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.15f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.55f))
    }
}

@Composable
private fun ControlPill(
    text: String,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.15f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
        Text(text = text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

private fun formatTimeMs(timeMs: Long): String {
    if (timeMs <= 0) return "00:00"
    val totalSec = timeMs / 1000
    val sec = totalSec % 60
    val min = (totalSec / 60) % 60
    val hrs = totalSec / 3600
    return if (hrs > 0) {
        "%d:%02d:%02d".format(hrs, min, sec)
    } else {
        "%02d:%02d".format(min, sec)
    }
}
