package com.tmplayer.movie

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.net.Uri
import android.util.TypedValue
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.tmplayer.data.SettingsStore
import com.tmplayer.player.SubtitleColour
import com.tmplayer.player.SubtitleLook
import com.tmplayer.player.SubtitlePosition
import com.tmplayer.player.SubtitleSize
import com.tmplayer.player.SubtitleStyle
import com.tmplayer.ui.icons.TmIcons
import com.tmplayer.ui.theme.TMPlayerTheme
import com.tmplayer.ui.theme.Tone
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Trình phát video phim trực tuyến cho MovieBot / MWPlayer
 * Đồng bộ các cử chỉ vuốt, phím điều khiển, chế độ khóa, PiP, tỉ lệ khung hình như Player chính.
 */
object OnlinePlayerSession {
    var detail: MovieDetail? = null
    var serverIndex: Int = 0
    var episodeIndex: Int = 0
}

@OptIn(UnstableApi::class)
class OnlinePlayerActivity : ComponentActivity() {

    internal var exoPlayerInstance: ExoPlayer? = null
    private var inPipMode: Boolean = false

    companion object {
        fun launch(context: Context, detail: MovieDetail, serverIdx: Int = 0, epIdx: Int = 0) {
            OnlinePlayerSession.detail = detail
            OnlinePlayerSession.serverIndex = serverIdx
            OnlinePlayerSession.episodeIndex = epIdx
            val intent = Intent(context, OnlinePlayerActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        hideSystemUI()

        setContent {
            TMPlayerTheme {
                OnlinePlayerScreen(
                    activity = this,
                    onClose = { finish() },
                    onToggleOrientation = { toggleOrientation() },
                    onEnterPip = { enterPip() }
                )
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && exoPlayerInstance?.isPlaying == true) {
            enterPip()
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPipMode = isInPictureInPictureMode
    }

    private fun enterPip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val params = PictureInPictureParams.Builder().build()
                enterPictureInPictureMode(params)
            } catch (_: Exception) {}
        }
    }

    private fun hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
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
        exoPlayerInstance?.release()
        exoPlayerInstance = null
    }
}

@OptIn(UnstableApi::class)
@Composable
fun OnlinePlayerScreen(
    activity: OnlinePlayerActivity,
    onClose: () -> Unit,
    onToggleOrientation: () -> Unit,
    onEnterPip: () -> Unit
) {
    val detail = OnlinePlayerSession.detail ?: run {
        onClose()
        return
    }

    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val settings = remember { SettingsStore(context) }
    val subtitleStyle by settings.subtitleStyle.collectAsState(initial = SubtitleStyle())
    val scope = rememberCoroutineScope()

    var serverIdx by remember { mutableIntStateOf(OnlinePlayerSession.serverIndex) }
    var epIdx by remember { mutableIntStateOf(OnlinePlayerSession.episodeIndex) }

    val currentServer = detail.servers.getOrNull(serverIdx) ?: detail.servers.firstOrNull()
    val episodes = currentServer?.episodes ?: emptyList()
    val currentEp = episodes.getOrNull(epIdx) ?: episodes.firstOrNull()

    var streamInfo by remember {
        mutableStateOf(
            if (currentServer != null && currentEp != null) {
                MovieRepository.resolveStream(detail.movie.source, detail.movie.name, currentServer, currentEp)
            } else null
        )
    }

    var showSubtitleSheet by remember { mutableStateOf(false) }
    var selectedSubTrack by remember { mutableStateOf<MovieSubtitleTrack?>(null) }
    var isSubtitleEnabled by remember { mutableStateOf(true) }

    LaunchedEffect(serverIdx, epIdx) {
        if (currentServer != null && currentEp != null) {
            val resolved = MovieRepository.resolveStreamWithSubtitles(
                detail.movie.source,
                detail.movie.name,
                currentServer,
                currentEp
            )
            streamInfo = resolved
            selectedSubTrack = resolved.subtitles.firstOrNull { it.isDefault } ?: resolved.subtitles.firstOrNull()
            isSubtitleEnabled = selectedSubTrack != null
        }
    }

    var isPlaying by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(true) }
    var isEnded by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var showControls by remember { mutableStateOf(true) }
    var lastInteraction by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showEpisodesSheet by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }

    // Screen lock state
    var isLocked by remember { mutableStateOf(false) }

    // Gesture HUD states
    var brightnessLevel by remember { mutableFloatStateOf(-1f) }
    var volumeLevel by remember { mutableFloatStateOf(-1f) }
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubTargetMs by remember { mutableLongStateOf(0L) }
    var scrubDeltaMs by remember { mutableLongStateOf(0L) }
    var isHoldingSpeed by remember { mutableStateOf(false) }
    var doubleTapSeekSide by remember { mutableStateOf<Int?>(null) } // -1 left, 1 right
    var doubleTapSeekSeconds by remember { mutableIntStateOf(0) }

    // Auto-hide HUD overlays
    LaunchedEffect(brightnessLevel) {
        if (brightnessLevel >= 0f) {
            delay(1500)
            brightnessLevel = -1f
        }
    }
    LaunchedEffect(volumeLevel) {
        if (volumeLevel >= 0f) {
            delay(1500)
            volumeLevel = -1f
        }
    }
    LaunchedEffect(doubleTapSeekSeconds) {
        if (doubleTapSeekSeconds != 0) {
            delay(800)
            doubleTapSeekSide = null
            doubleTapSeekSeconds = 0
        }
    }

    // Auto-hide controls overlay
    LaunchedEffect(lastInteraction, showControls, isLocked) {
        if (showControls && !isLocked) {
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

    val exoPlayer = remember {
        val httpSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(
                mapOf(
                    "Referer" to (streamInfo?.referer ?: "https://phimapi.com/"),
                    "Origin" to (streamInfo?.referer ?: "https://phimapi.com/").trimEnd('/')
                )
            )

        val defaultDataSourceFactory = DefaultDataSource.Factory(context, httpSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(defaultDataSourceFactory)

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build().also {
                activity.exoPlayerInstance = it
            }
    }

    fun applySubtitleSelection(sub: MovieSubtitleTrack?, enabled: Boolean) {
        selectedSubTrack = sub
        isSubtitleEnabled = enabled && sub != null
        if (!isSubtitleEnabled || sub == null) {
            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
        } else {
            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setPreferredTextLanguage(sub.language)
                .build()
        }
    }

    // Update media source when stream changes
    LaunchedEffect(streamInfo?.streamUrl, streamInfo?.subtitles) {
        val stream = streamInfo
        if (stream != null && stream.streamUrl.isNotBlank()) {
            isLoading = true
            isEnded = false
            errorMessage = null
            try {
                val subConfigs = stream.subtitles.map { sub ->
                    val finalUri = MovieRepository.getNormalizedSubtitleUri(context, sub.url)
                    MediaItem.SubtitleConfiguration.Builder(finalUri)
                        .setMimeType(MimeTypes.TEXT_VTT)
                        .setLanguage(sub.language)
                        .setLabel(sub.label)
                        .setSelectionFlags(if (sub.isDefault) C.SELECTION_FLAG_DEFAULT else 0)
                        .build()
                }

                val mediaItem = MediaItem.Builder()
                    .setUri(stream.streamUrl)
                    .apply {
                        if (stream.streamUrl.contains(".m3u8") || stream.streamUrl.contains("/stream/")) {
                            setMimeType(MimeTypes.APPLICATION_M3U8)
                        }
                        if (subConfigs.isNotEmpty()) {
                            setSubtitleConfigurations(subConfigs)
                        }
                    }
                    .build()

                val defaultLang = selectedSubTrack?.language ?: "vi"
                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                    .buildUpon()
                    .setPreferredTextLanguage(defaultLang)
                    .setIgnoredTextSelectionFlags(0)
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !isSubtitleEnabled)
                    .build()

                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true
            } catch (e: Exception) {
                isLoading = false
                errorMessage = "Không thể phát luồng: ${e.message}"
            }
        }
    }

    // Player event listener
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                isLoading = (state == Player.STATE_BUFFERING)
                isEnded = (state == Player.STATE_ENDED)
                if (state == Player.STATE_READY) {
                    isLoading = false
                    errorMessage = null
                    duration = exoPlayer.duration.coerceAtLeast(0L)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                isLoading = false
                errorMessage = "Lỗi phát video (${error.errorCodeName}): ${error.message}"
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

    fun getScreenBrightness(): Float {
        val cur = activity.window.attributes.screenBrightness
        if (cur >= 0f) return cur
        return runCatching {
            Settings.System.getInt(activity.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
        }.getOrDefault(0.5f)
    }

    fun setScreenBrightness(level: Float) {
        val clamped = level.coerceIn(0.02f, 1.0f)
        activity.window.attributes = activity.window.attributes.apply {
            screenBrightness = clamped
        }
        brightnessLevel = clamped
    }

    fun getScreenVolume(): Float {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (max <= 0) return 0f
        return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) / max.toFloat()
    }

    fun setScreenVolume(fraction: Float) {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (max <= 0) return
        val clamped = fraction.coerceIn(0f, 1f)
        val step = (clamped * max).roundToInt()
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, step, 0)
        volumeLevel = step / max.toFloat()
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val screenWidthPx = constraints.maxWidth.toFloat()
        val screenHeightPx = constraints.maxHeight.toFloat()

        // GESTURE DETECTOR LAYER
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isLocked, showControls) {
                    if (isLocked) {
                        detectTapGestures(
                            onTap = {
                                // Briefly show unlock pill
                                showControls = !showControls
                            }
                        )
                        return@pointerInput
                    }

                    var dragMode = 0 // 0 none, 1 brightness, 2 volume, 3 scrub
                    var initialBrightness = 0.5f
                    var initialVolume = 0.5f
                    var initialSeekPos = 0L

                    detectDragGestures(
                        onDragStart = { offset ->
                            initialBrightness = getScreenBrightness()
                            initialVolume = getScreenVolume()
                            initialSeekPos = exoPlayer.currentPosition
                            dragMode = 0
                        },
                        onDragEnd = {
                            if (dragMode == 3 && duration > 0) {
                                exoPlayer.seekTo(scrubTargetMs)
                                currentPosition = scrubTargetMs
                            }
                            dragMode = 0
                            isScrubbing = false
                        },
                        onDragCancel = {
                            dragMode = 0
                            isScrubbing = false
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val (dx, dy) = dragAmount
                            val pos = change.position

                            if (dragMode == 0) {
                                if (abs(dx) > abs(dy) * 1.5f && abs(dx) > 12f) {
                                    dragMode = 3 // Horizontal Scrub
                                    isScrubbing = true
                                } else if (abs(dy) > abs(dx) * 1.5f && abs(dy) > 12f) {
                                    dragMode = if (pos.x < screenWidthPx / 2f) 1 else 2 // Left = Brightness, Right = Volume
                                }
                            }

                            when (dragMode) {
                                1 -> { // Brightness
                                    val deltaFraction = -dy / (screenHeightPx * 0.7f)
                                    val next = (getScreenBrightness() + deltaFraction).coerceIn(0.02f, 1f)
                                    setScreenBrightness(next)
                                }
                                2 -> { // Volume
                                    val deltaFraction = -dy / (screenHeightPx * 0.7f)
                                    val next = (getScreenVolume() + deltaFraction).coerceIn(0f, 1f)
                                    setScreenVolume(next)
                                }
                                3 -> { // Scrub
                                    if (duration > 0) {
                                        val scrubFactor = 120_000L // full screen swipe = 2 minutes
                                        val deltaMs = (dx / screenWidthPx * scrubFactor).toLong()
                                        initialSeekPos = (initialSeekPos + deltaMs).coerceIn(0L, duration)
                                        scrubTargetMs = initialSeekPos
                                        scrubDeltaMs = scrubTargetMs - currentPosition
                                        isScrubbing = true
                                    }
                                }
                            }
                        }
                    )
                }
                .pointerInput(isLocked) {
                    if (isLocked) return@pointerInput
                    detectTapGestures(
                        onTap = {
                            showControls = !showControls
                            lastInteraction = System.currentTimeMillis()
                        },
                        onDoubleTap = { offset ->
                            lastInteraction = System.currentTimeMillis()
                            val x = offset.x
                            if (x < screenWidthPx * 0.35f) {
                                // Double tap left: Rewind 10s
                                doubleTapSeekSide = -1
                                doubleTapSeekSeconds += 10
                                val target = (exoPlayer.currentPosition - 10_000).coerceAtLeast(0L)
                                exoPlayer.seekTo(target)
                                currentPosition = target
                            } else if (x > screenWidthPx * 0.65f) {
                                // Double tap right: Fast forward 10s
                                doubleTapSeekSide = 1
                                doubleTapSeekSeconds += 10
                                val dur = exoPlayer.duration
                                val target = (exoPlayer.currentPosition + 10_000).let { if (dur > 0) it.coerceAtMost(dur) else it }
                                exoPlayer.seekTo(target)
                                currentPosition = target
                            } else {
                                // Double tap center: Play / Pause
                                if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                            }
                        },
                        onLongPress = {
                            // Hold to fast forward 2.0x
                            isHoldingSpeed = true
                            exoPlayer.playbackParameters = PlaybackParameters(2.0f)
                        }
                    )
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
                        subtitleView?.apply {
                            setApplyEmbeddedStyles(true)
                            setApplyEmbeddedFontSizes(false)
                            setFractionalTextSize(subtitleStyle.size.fraction)
                            val bottomPad = when (subtitleStyle.position) {
                                SubtitlePosition.Bottom -> 0.22f
                                SubtitlePosition.Raised -> 0.30f
                                SubtitlePosition.High -> 0.40f
                            }
                            setBottomPaddingFraction(bottomPad)
                            setStyle(SubtitleLook.captionStyle(subtitleStyle))
                        }
                    }
                },
                update = { view ->
                    view.resizeMode = resizeMode
                    view.subtitleView?.apply {
                        setApplyEmbeddedStyles(true)
                        setApplyEmbeddedFontSizes(false)
                        setFractionalTextSize(subtitleStyle.size.fraction)
                        val bottomPad = when (subtitleStyle.position) {
                            SubtitlePosition.Bottom -> 0.22f
                            SubtitlePosition.Raised -> 0.30f
                            SubtitlePosition.High -> 0.40f
                        }
                        setBottomPaddingFraction(bottomPad)
                        setStyle(SubtitleLook.captionStyle(subtitleStyle))
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Release hold fast forward
        LaunchedEffect(isHoldingSpeed) {
            if (isHoldingSpeed) {
                delay(3000)
                isHoldingSpeed = false
                exoPlayer.playbackParameters = PlaybackParameters(playbackSpeed)
            }
        }

        // Loading Spinner
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Tone.accent, strokeWidth = 3.dp)
            }
        }

        // =====================================================================
        // HUD FEEDBACK OVERLAYS (Brightness, Volume, Scrub, Double Tap, Hold)
        // =====================================================================

        // BRIGHTNESS HUD (Left)
        if (brightnessLevel >= 0f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .align(Alignment.CenterStart)
                    .padding(start = 28.dp),
                contentAlignment = Alignment.Center
            ) {
                LevelIndicatorHud(
                    icon = TmIcons.Trailer, // Sun/Brightness placeholder
                    label = "${(brightnessLevel * 100).roundToInt()}%",
                    progress = brightnessLevel
                )
            }
        }

        // VOLUME HUD (Right)
        if (volumeLevel >= 0f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .align(Alignment.CenterEnd)
                    .padding(end = 28.dp),
                contentAlignment = Alignment.Center
            ) {
                LevelIndicatorHud(
                    icon = if (volumeLevel <= 0f) TmIcons.VolumeOff else TmIcons.VolumeUp,
                    label = "${(volumeLevel * 100).roundToInt()}%",
                    progress = volumeLevel
                )
            }
        }

        // SCRUB TIME PREVIEW HUD (Center)
        if (isScrubbing) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.82f))
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = formatTimeMs(scrubTargetMs),
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    val sign = if (scrubDeltaMs >= 0) "+" else ""
                    Text(
                        text = "[$sign${formatTimeMs(abs(scrubDeltaMs))}] / ${formatTimeMs(duration)}",
                        color = Tone.accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // DOUBLE TAP RIPPLE PILLS
        if (doubleTapSeekSide != null && doubleTapSeekSeconds > 0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 48.dp),
                contentAlignment = if (doubleTapSeekSide == -1) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 18.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = if (doubleTapSeekSide == -1) "« $doubleTapSeekSeconds giây" else "$doubleTapSeekSeconds giây »",
                        color = Tone.accent,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // HOLD TO 2.0X SPEED HUD (Top Center)
        if (isHoldingSpeed) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Tone.accent)
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "2.0x  ▶▶",
                    color = Color.Black,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // UNLOCK BUTTON (When Locked)
        if (isLocked) {
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .clickable {
                            isLocked = false
                            showControls = true
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(imageVector = TmIcons.LockOpen, contentDescription = null, tint = Tone.accent, modifier = Modifier.size(18.dp))
                    Text("Chạm để mở khóa", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Error Overlay
        if (errorMessage != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.88f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(imageVector = TmIcons.Close, contentDescription = null, tint = Tone.caution, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = errorMessage ?: "Lỗi phát video",
                        color = Color.White,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ControlPill(
                            text = "Thử lại",
                            icon = TmIcons.Refresh,
                            onClick = {
                                errorMessage = null
                                isLoading = true
                                val stream = streamInfo
                                if (stream != null && stream.streamUrl.isNotBlank()) {
                                    val mediaItem = MediaItem.Builder().setUri(stream.streamUrl).build()
                                    exoPlayer.setMediaItem(mediaItem)
                                    exoPlayer.prepare()
                                    exoPlayer.playWhenReady = true
                                }
                            }
                        )
                        if (detail.servers.size > 1) {
                            ControlPill(
                                text = "Đổi Server",
                                icon = TmIcons.Episodes,
                                onClick = {
                                    serverIdx = (serverIdx + 1) % detail.servers.size
                                    OnlinePlayerSession.serverIndex = serverIdx
                                }
                            )
                        }
                    }
                }
            }
        }

        // Controls Overlay (When not locked)
        AnimatedVisibility(
            visible = showControls && !isLocked,
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
                        val subType = remember(currentServer, detail.movie.lang) {
                            val s = (currentServer?.serverName ?: "").lowercase()
                            val l = detail.movie.lang.lowercase()
                            when {
                                // 1. Priority: check selected server name explicitly
                                "lồng tiếng" in s || "long tieng" in s -> "Lồng tiếng"
                                "thuyết minh" in s || "thuyet minh" in s -> "Thuyết minh"
                                "vietsub" in s || "sub" in s -> "Vietsub"
                                // 2. Fallback: check movie metadata
                                "vietsub" in l || "sub" in l -> "Vietsub"
                                "thuyết minh" in l || "thuyet minh" in l -> "Thuyết minh"
                                "lồng tiếng" in l || "long tieng" in l -> "Lồng tiếng"
                                else -> currentServer?.serverName?.replace(Regex("\\s+"), " ")?.trim()?.ifBlank { "Vietsub" } ?: "Vietsub"
                            }
                        }
                        val epName = currentEp?.name?.trim() ?: ""
                        val epLabel = when {
                            epName.isBlank() -> ""
                            epName.lowercase().startsWith("tập") -> epName
                            else -> "Tập $epName"
                        }
                        Text(
                            text = if (epLabel.isNotBlank()) "$epLabel • $subType" else subType,
                            color = Tone.accent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Screen Lock button
                    ControlButton(
                        icon = TmIcons.Lock,
                        onClick = {
                            isLocked = true
                            showControls = false
                        }
                    )
                    Spacer(Modifier.width(8.dp))

                    // Picture-in-Picture
                    ControlButton(
                        icon = TmIcons.PictureInPicture,
                        onClick = onEnterPip
                    )
                    Spacer(Modifier.width(8.dp))

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
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
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

                            // Subtitle selector pill
                            if (streamInfo?.subtitles?.isNotEmpty() == true) {
                                ControlPill(
                                    text = if (isSubtitleEnabled && selectedSubTrack != null) selectedSubTrack!!.label else "Phụ đề: Tắt",
                                    icon = TmIcons.Subtitles,
                                    onClick = {
                                        showSubtitleSheet = true
                                        lastInteraction = System.currentTimeMillis()
                                    }
                                )
                            }

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

        // SUBTITLE SELECTOR DIALOG / SHEET
        if (showSubtitleSheet) {
            val subs = streamInfo?.subtitles ?: emptyList()
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .clickable { showSubtitleSheet = false },
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .widthIn(max = 380.dp)
                        .padding(24.dp)
                        .clickable(enabled = false) {},
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF1E1E22),
                    shadowElevation = 16.dp
                ) {
                    Column(
                        modifier = Modifier
                            .padding(20.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(imageVector = TmIcons.Subtitles, contentDescription = null, tint = Tone.accent, modifier = Modifier.size(20.dp))
                                Text(
                                    text = "Phụ đề (Subtitles)",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            IconButton(onClick = { showSubtitleSheet = false }, modifier = Modifier.size(28.dp)) {
                                Icon(imageVector = TmIcons.Close, contentDescription = "Close", tint = Color.White.copy(alpha = 0.7f))
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // Option "Tắt"
                        val isOff = !isSubtitleEnabled || selectedSubTrack == null
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isOff) Tone.accent.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f))
                                .clickable {
                                    applySubtitleSelection(null, false)
                                    showSubtitleSheet = false
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Tắt phụ đề",
                                color = if (isOff) Tone.accent else Color.White,
                                fontSize = 14.sp,
                                fontWeight = if (isOff) FontWeight.Bold else FontWeight.Normal
                            )
                            if (isOff) {
                                Icon(imageVector = TmIcons.Check, contentDescription = null, tint = Tone.accent, modifier = Modifier.size(18.dp))
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        // Available subtitles
                        subs.forEach { sub ->
                            val isSelected = isSubtitleEnabled && selectedSubTrack?.url == sub.url
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) Tone.accent.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f))
                                    .clickable {
                                        applySubtitleSelection(sub, true)
                                        showSubtitleSheet = false
                                    }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = sub.label,
                                        color = if (isSelected) Tone.accent else Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                    if (sub.language.isNotBlank()) {
                                        Text(
                                            text = sub.language.uppercase(),
                                            color = Color.White.copy(alpha = 0.5f),
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                                if (isSelected) {
                                    Icon(imageVector = TmIcons.Check, contentDescription = null, tint = Tone.accent, modifier = Modifier.size(18.dp))
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }

                        Spacer(Modifier.height(6.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Color.White.copy(alpha = 0.12f))
                        )
                        Spacer(Modifier.height(12.dp))

                        Text("Kiểu hiển thị (Đồng bộ Cài đặt)", color = Tone.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(10.dp))

                        // Cỡ chữ
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Cỡ chữ", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf(
                                    SubtitleSize.Small to "Nhỏ",
                                    SubtitleSize.Medium to "Vừa",
                                    SubtitleSize.Large to "Lớn",
                                    SubtitleSize.ExtraLarge to "Rất lớn"
                                ).forEach { (sz, szLabel) ->
                                    val isCur = subtitleStyle.size == sz
                                    Box(
                                        Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isCur) Tone.accent else Color.White.copy(alpha = 0.1f))
                                            .clickable {
                                                scope.launch { settings.setSubtitleStyle(subtitleStyle.copy(size = sz)) }
                                            }
                                            .padding(horizontal = 8.dp, vertical = 5.dp)
                                    ) {
                                        Text(
                                            szLabel,
                                            color = if (isCur) Color.Black else Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = if (isCur) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        // Vị trí
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Vị trí", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf(
                                    SubtitlePosition.Bottom to "Dưới",
                                    SubtitlePosition.Raised to "Nâng cao",
                                    SubtitlePosition.High to "Cao"
                                ).forEach { (pos, posLabel) ->
                                    val isCur = subtitleStyle.position == pos
                                    Box(
                                        Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isCur) Tone.accent else Color.White.copy(alpha = 0.1f))
                                            .clickable {
                                                scope.launch { settings.setSubtitleStyle(subtitleStyle.copy(position = pos)) }
                                            }
                                            .padding(horizontal = 8.dp, vertical = 5.dp)
                                    ) {
                                        Text(
                                            posLabel,
                                            color = if (isCur) Color.Black else Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = if (isCur) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        // Màu chữ
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Màu sắc", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(
                                    SubtitleColour.White to Color.White,
                                    SubtitleColour.Yellow to Color(0xFFFFE14D),
                                    SubtitleColour.Cyan to Color(0xFF5CE6FF),
                                    SubtitleColour.Green to Color(0xFF7CF08A),
                                    SubtitleColour.Pink to Color(0xFFFF9AE0)
                                ).forEach { (col, cVal) ->
                                    val isCur = subtitleStyle.colour == col
                                    Box(
                                        Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(cVal)
                                            .clickable {
                                                scope.launch { settings.setSubtitleStyle(subtitleStyle.copy(colour = col)) }
                                            }
                                            .then(
                                                if (isCur) Modifier.border(2.dp, Tone.accent, CircleShape)
                                                else Modifier
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isCur) {
                                            Box(Modifier.size(6.dp).clip(CircleShape).background(Color.Black))
                                        }
                                    }
                                }
                            }
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
                                            srv.serverName.replace(Regex("\\s+"), " ").trim(),
                                            color = if (isSelected) Color.Black else Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
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
private fun LevelIndicatorHud(
    icon: ImageVector,
    label: String,
    progress: Float
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.78f))
            .padding(horizontal = 12.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, contentDescription = null, tint = Tone.accent, modifier = Modifier.size(24.dp))
        // Vertical Progress Bar
        Box(
            modifier = Modifier
                .width(6.dp)
                .height(100.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = 0.25f)),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(progress.coerceIn(0f, 1f))
                    .background(Tone.accent)
            )
        }
        Text(text = label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
        Text(
            text = text,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
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
