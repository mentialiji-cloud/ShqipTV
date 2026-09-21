package com.eagletv.player

import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun EaglePlayer(url: String?, preset: BufferPreset, modifier: Modifier = Modifier, preview: Boolean = false) {
    val context = LocalContext.current
    val player = remember(preset) {
        val control = DefaultLoadControl.Builder()
            .setBufferDurationsMs(preset.minMs, preset.maxMs, preset.playbackMs, preset.playbackMs)
            .build()
        ExoPlayer.Builder(context).setLoadControl(control).build().apply {
            playWhenReady = true
            volume = if (preview) 0f else 1f
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    LaunchedEffect(url) {
        if (url.isNullOrBlank()) return@LaunchedEffect
        if (preview) delay(650)
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.play()
    }
    AndroidView(
        modifier = modifier.background(Color.Black),
        factory = { PlayerView(it).apply { useController = !preview; this.player = player } },
        update = { it.player = player }
    )
}

@Composable
fun FullScreenPlayer(url: String, preset: BufferPreset, onClose: () -> Unit) {
    BackHandler { onClose() }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        EaglePlayer(url, preset, Modifier.fillMaxSize())
    }
}
