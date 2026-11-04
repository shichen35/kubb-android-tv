package com.kubb.tv

import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import com.kubb.tv.data.Detail
import com.kubb.tv.data.Episode
import com.kubb.tv.data.Summary
import com.kubb.tv.data.USER_AGENT
import com.kubb.tv.data.episodeTitle
import com.kubb.tv.data.nextEpisode
import com.kubb.tv.data.primaryAction
import com.kubb.tv.data.resumePosition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

private const val SEEK_MS = 15_000L
private const val MEDIA_SEEK_MS = 30_000L
private const val UP_NEXT_MS = 30_000L
private const val CONTROLS_MS = 5_000L

/** What a fatal playback error should do: a first 403 means the stream token expired, so fetch a fresh URL once. */
internal fun shouldRefresh(error: PlaybackException, refreshedSincePlaying: Boolean): Boolean =
    !refreshedSincePlaying && (error.cause as? HttpDataSource.InvalidResponseCodeException)?.responseCode == 403

internal fun errorText(error: PlaybackException): String = when (error.errorCode) {
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "网络连接失败，请检查网络后重试"
    PlaybackException.ERROR_CODE_DECODING_FAILED, PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> "这台设备无法解码此片源"
    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "播放地址已失效，请重试"
    else -> "视频暂时无法播放，请重试"
}

internal fun clock(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%02d:%02d".format(s / 60, s % 60)
}

@OptIn(UnstableApi::class)
@Composable
internal fun PlayerScreen(vm: AppViewModel, item: Summary, startEpisode: Int?) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val player = remember {
        val http = DefaultHttpDataSource.Factory().setUserAgent(USER_AGENT).setAllowCrossProtocolRedirects(true)
        ExoPlayer.Builder(context).setMediaSourceFactory(DefaultMediaSourceFactory(http)).build()
    }
    var detail by remember { mutableStateOf<Detail?>(null) }
    var episode by remember { mutableIntStateOf(-1) } // the playing episode's index; -1 until known
    var startAt by remember { mutableLongStateOf(0L) }
    var reload by remember { mutableIntStateOf(0) }
    var refreshed by remember { mutableStateOf(false) }
    var label by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    var ended by remember { mutableStateOf(false) }
    var controls by remember { mutableStateOf(false) }
    var controlsAt by remember { mutableLongStateOf(0L) }
    var strip by remember { mutableStateOf(false) }
    var upNextDismissed by remember { mutableStateOf(false) }

    val episodes = detail?.episodes.orEmpty()
    val current = episodes.firstOrNull { it.index == episode }
    val next = nextEpisode(episodes, episode)
    val currentDetail by rememberUpdatedState(detail)
    val currentEpisode by rememberUpdatedState(episode)

    fun save() {
        val d = currentDetail ?: return
        val dur = player.duration
        if (currentEpisode > 0 && dur > 0 && player.currentPosition > 0) {
            vm.library.recordProgress(d.summary, d.episodes, currentEpisode, player.currentPosition, dur)
        }
    }

    fun switchTo(index: Int) {
        save()
        startAt = resumePosition(vm.library.watchOf(item.id), index)
        refreshed = false
        upNextDismissed = false
        episode = index
        reload++
    }

    fun showControls() {
        controls = true
        controlsAt = System.currentTimeMillis()
    }

    fun seekBy(delta: Long) {
        if (!player.isCurrentMediaItemSeekable) return
        val dur = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
        player.seekTo((player.currentPosition + delta).coerceIn(0, dur))
        showControls()
    }

    LaunchedEffect(Unit) {
        try {
            val d = vm.detail(item.id)
            val watch = vm.library.watchOf(item.id)
            val index = startEpisode ?: primaryAction(d.episodes, watch).playIndex
            detail = d
            startAt = resumePosition(watch, index)
            if (startAt > 0) notice = "从 ${clock(startAt)} 继续播放"
            episode = index
            reload++
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = userMessage(e)
        }
    }

    // Resolve and start the stream: on every episode change, retry or token refresh.
    LaunchedEffect(reload) {
        val d = detail ?: return@LaunchedEffect
        error = null
        ended = false
        try {
            // Titles without an episode list play their default stream from the title key.
            val stream = vm.api.resolveStream(d.summary.id, d.episodes.firstOrNull { it.index == episode })
            label = stream.label
            player.setMediaItem(MediaItem.Builder().setUri(stream.url).setMimeType(MimeTypes.APPLICATION_M3U8).build(), startAt)
            player.prepare()
            if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) player.play()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = userMessage(e)
        }
    }

    DisposableEffect(player, owner) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
                if (isPlaying) refreshed = false else save()
            }

            override fun onPlaybackStateChanged(state: Int) {
                ended = state == Player.STATE_ENDED
            }

            override fun onPlayerError(e: PlaybackException) {
                if (shouldRefresh(e, refreshed)) {
                    refreshed = true
                    startAt = player.currentPosition
                    reload++
                } else {
                    error = errorText(e)
                }
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                save()
                player.pause()
            }
        }
        player.addListener(listener)
        owner.lifecycle.addObserver(observer)
        onDispose {
            save()
            owner.lifecycle.removeObserver(observer)
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(Unit) {
        var lastSave = 0L
        while (true) {
            position = player.currentPosition.coerceAtLeast(0)
            duration = player.duration.coerceAtLeast(0)
            val now = System.currentTimeMillis()
            if (player.isPlaying && now - lastSave > 10_000) {
                save()
                lastSave = now
            }
            if (controls && !strip && now - controlsAt > CONTROLS_MS) controls = false
            delay(500)
        }
    }
    LaunchedEffect(notice) {
        if (notice != null) {
            delay(4000)
            notice = null
        }
    }
    LaunchedEffect(ended) {
        if (!ended) return@LaunchedEffect
        val n = nextEpisode(episodes, episode)
        if (n != null) switchTo(n.index) else {
            save()
            notice = "已播放完毕"
        }
    }

    val upNext = next != null && !upNextDismissed && duration > 0 && duration - position in 1..UP_NEXT_MS && error == null
    val rootFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos {}
        rootFocus.requestFocus()
    }
    BackHandler(enabled = strip || controls) {
        strip = false
        controls = false
        rootFocus.requestFocus()
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .focusRequester(rootFocus)
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (strip) {
                    if (e.key == Key.DirectionUp) {
                        strip = false
                        rootFocus.requestFocus()
                        return@onPreviewKeyEvent true
                    }
                    return@onPreviewKeyEvent false
                }
                when (e.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> when {
                        error != null -> reload++
                        upNext -> switchTo(next.index)
                        else -> {
                            if (player.isPlaying) player.pause() else player.play()
                            showControls()
                        }
                    }
                    Key.DirectionLeft -> seekBy(-SEEK_MS)
                    Key.DirectionRight -> seekBy(SEEK_MS)
                    Key.DirectionDown -> if (controls && episodes.size > 1) strip = true else showControls()
                    Key.DirectionUp -> if (upNext) upNextDismissed = true else controls = false
                    Key.MediaPlayPause -> if (player.isPlaying) player.pause() else player.play()
                    Key.MediaPlay -> player.play()
                    Key.MediaPause, Key.MediaStop -> player.pause()
                    Key.MediaFastForward -> seekBy(MEDIA_SEEK_MS)
                    Key.MediaRewind -> seekBy(-MEDIA_SEEK_MS)
                    else -> return@onPreviewKeyEvent false
                }
                true
            }
            .focusable(),
    ) {
        AndroidView(
            factory = { PlayerView(it).apply { this.player = player; useController = false; isFocusable = false; keepScreenOn = true } },
            modifier = Modifier.fillMaxSize(),
        )
        val title = listOfNotNull(item.name, current?.takeIf { episodes.size > 1 }?.let(::episodeTitle)).joinToString("  ")
        if (controls || error != null) Text(
            listOf(title, label).filter { it.isNotBlank() }.joinToString("  ·  "), color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopStart).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .8f), Color.Transparent))).padding(SafeX, 28.dp),
        )
        when {
            error != null -> Message(error!!, Modifier.align(Alignment.Center), "按确认重试", onAction = { reload++ })
            detail == null || (duration == 0L && !playing) -> Message("正在加载…", Modifier.align(Alignment.Center))
        }
        notice?.let { Badge(it, Modifier.align(Alignment.TopEnd).padding(SafeX, 28.dp)) }
        if (upNext) Badge("即将播放 ${episodeTitle(next)} · 按确认立即播放", Modifier.align(Alignment.BottomEnd).padding(SafeX, 40.dp))
        if (controls) Controls(playing, position, duration, Modifier.align(Alignment.BottomCenter)) {
            if (strip) EpisodeStrip(episodes, episode) { strip = false; rootFocus.requestFocus(); if (it.index != episode) switchTo(it.index) }
        }
    }
}

@Composable
private fun Badge(text: String, modifier: Modifier) {
    Text(
        text, color = TextPrimary, fontSize = 17.sp,
        modifier = modifier.background(Panel.copy(alpha = .92f), RoundedCornerShape(10.dp)).padding(horizontal = 18.dp, vertical = 12.dp),
    )
}

@Composable
private fun Controls(playing: Boolean, position: Long, duration: Long, modifier: Modifier, extra: @Composable () -> Unit) {
    Column(
        modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .85f))))
            .padding(horizontal = SafeX, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(if (playing) "播放中" else "已暂停", color = Accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("${clock(position)} / ${clock(duration)}", color = TextPrimary, fontSize = 16.sp)
            Text("左右键 ±15 秒 · 下键选集", color = TextMuted, fontSize = 14.sp)
        }
        Box(Modifier.fillMaxWidth().height(4.dp).background(Color.White.copy(alpha = .25f))) {
            Box(Modifier.fillMaxWidth(if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f).height(4.dp).background(Accent))
        }
        extra()
    }
}

@Composable
private fun EpisodeStrip(episodes: List<Episode>, playing: Int, pick: (Episode) -> Unit) {
    val list = rememberLazyListState()
    val focus = remember { FocusRequester() }
    val at = episodes.indexOfFirst { it.index == playing }.coerceAtLeast(0)
    LaunchedEffect(Unit) {
        list.scrollToItem(at)
        withFrameNanos {}
        runCatching { focus.requestFocus() }
    }
    LazyRow(state = list, contentPadding = PaddingValues(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(episodes, key = { it.index }) { ep ->
            Chip(
                episodeTitle(ep), Modifier.width(120.dp).then(if (ep.index == playing) Modifier.focusRequester(focus) else Modifier),
                selected = ep.index == playing,
            ) { pick(ep) }
        }
    }
}
