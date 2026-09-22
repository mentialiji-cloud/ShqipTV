package com.eagletv.player

import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun EaglePlayer(url: String?, preset: BufferPreset, modifier: Modifier = Modifier, preview: Boolean = false) {
    val context = LocalContext.current
    var retry by remember { mutableIntStateOf(0) }
    val player = remember(preset) {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(preset.minMs, preset.maxMs, preset.playbackMs, preset.playbackMs)
            .setBackBuffer(0, false)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent("EagleTV/1.2")
            .setConnectTimeoutMs(10_000)
            .setReadTimeoutMs(20_000)
            .setAllowCrossProtocolRedirects(true)
        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
            .build().apply {
                playWhenReady = true
                volume = if (preview) .35f else 1f
                setHandleAudioBecomingNoisy(true)
            }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) { retry++ }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener); player.release() }
    }
    LaunchedEffect(url, retry) {
        if (url.isNullOrBlank()) { player.stop(); return@LaunchedEffect }
        if (retry > 0) delay(850)
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.playWhenReady = true
    }
    AndroidView(
        modifier = modifier.background(Color.Black),
        factory = { PlayerView(it).apply { useController = !preview; keepScreenOn = true; this.player = player } },
        update = { it.player = player }
    )
}

private enum class GuideLayer { HIDDEN, CHANNELS, CATEGORIES }

@Composable
fun LiveFullScreenPlayer(
    initial: Channel,
    channels: List<Channel>,
    categories: List<Category>,
    playlist: Playlist,
    client: XtreamClient,
    preset: BufferPreset,
    currentCategory: String?,
    onChannelChanged: (Channel) -> Unit,
    onClose: () -> Unit
) {
    var playing by remember { mutableStateOf(initial) }
    var categoryId by remember { mutableStateOf(currentCategory) }
    var layer by remember { mutableStateOf(GuideLayer.HIDDEN) }
    val rootFocus = remember { FocusRequester() }
    val channelFocus = remember { FocusRequester() }
    val categoryFocus = remember { FocusRequester() }
    val shown = remember(channels, categoryId) { categoryId?.let { id -> channels.filter { it.categoryId == id } } ?: channels }
    val url = remember(playing) { client.streamUrl(playlist, playing) }

    BackHandler { if (layer != GuideLayer.HIDDEN) layer = GuideLayer.HIDDEN else onClose() }
    LaunchedEffect(layer, categoryId, shown) {
        delay(40)
        when (layer) {
            GuideLayer.HIDDEN -> rootFocus.requestFocus()
            GuideLayer.CHANNELS -> runCatching { channelFocus.requestFocus() }
            GuideLayer.CATEGORIES -> runCatching { categoryFocus.requestFocus() }
        }
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black).focusRequester(rootFocus).focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || event.nativeKeyEvent.repeatCount > 0) return@onPreviewKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                    AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER, AndroidKeyEvent.KEYCODE_MENU -> {
                        if (layer == GuideLayer.HIDDEN) { layer = GuideLayer.CHANNELS; true } else false
                    }
                    AndroidKeyEvent.KEYCODE_DPAD_LEFT -> { layer = if (layer == GuideLayer.CHANNELS) GuideLayer.CATEGORIES else GuideLayer.CHANNELS; true }
                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> { layer = when (layer) { GuideLayer.CATEGORIES -> GuideLayer.CHANNELS; GuideLayer.CHANNELS -> GuideLayer.HIDDEN; else -> GuideLayer.HIDDEN }; true }
                    else -> false
                }
            }
    ) {
        EaglePlayer(url, preset, Modifier.fillMaxSize())
        if (layer != GuideLayer.HIDDEN) {
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xF20A0709), Color(0xD90F070A), Color(0x45000000)))))
            Row(Modifier.fillMaxSize().padding(26.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (layer == GuideLayer.CATEGORIES) {
                    LazyColumn(Modifier.width(280.dp).fillMaxHeight().clip(RoundedCornerShape(18.dp)).background(Color(0xC51A1115)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        item { FullGuideRow("All channels", categoryId == null, modifier = if (categoryId == null) Modifier.focusRequester(categoryFocus) else Modifier) { categoryId = null; layer = GuideLayer.CHANNELS } }
                        items(categories, key = { it.id }) { category -> FullGuideRow(category.name, category.id == categoryId, modifier = if (category.id == categoryId) Modifier.focusRequester(categoryFocus) else Modifier) { categoryId = category.id; layer = GuideLayer.CHANNELS } }
                    }
                }
                LazyColumn(Modifier.width(500.dp).fillMaxHeight().clip(RoundedCornerShape(18.dp)).background(Color(0xC51A1115)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    item { Text("TV Guide", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(12.dp)) }
                    items(shown, key = { it.id }) { channel ->
                        val target = shown.firstOrNull { it.id == playing.id }?.id ?: shown.firstOrNull()?.id
                        FullGuideRow(channel.name, channel.id == playing.id, channel.logo, if (channel.id == target) Modifier.focusRequester(channelFocus) else Modifier) { playing = channel; onChannelChanged(channel); layer = GuideLayer.HIDDEN }
                    }
                }
                Column(Modifier.weight(1f).align(Alignment.Bottom).clip(RoundedCornerShape(18.dp)).background(Color(0xB8170D11)).padding(22.dp)) {
                    Text("Now playing", color = EagleRed, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(playing.name, color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(8.dp)); Text("OK: guide  •  Left: channels/categories  •  Back: close", color = Color.White.copy(alpha = .66f), fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun FullGuideRow(label: String, selected: Boolean, logo: String? = null, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier.fillMaxWidth().height(58.dp).onFocusChanged { focused = it.isFocused }.clip(RoundedCornerShape(11.dp))
            .background(if (focused) EagleRed else if (selected) Color(0x994A1720) else Color.Transparent)
            .clickable(onClick = onClick).focusable().padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!logo.isNullOrBlank()) { AsyncImage(logo, null, Modifier.size(38.dp).clip(RoundedCornerShape(7.dp)).background(Color.White).padding(4.dp)); Spacer(Modifier.width(12.dp)) }
        Text(label, color = Color.White, fontSize = 16.sp, fontWeight = if (focused || selected) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun FullScreenPlayer(url: String, preset: BufferPreset, onClose: () -> Unit) {
    BackHandler { onClose() }
    Box(Modifier.fillMaxSize().background(Color.Black)) { EaglePlayer(url, preset, Modifier.fillMaxSize()) }
}
