package com.shqiptv.app.player

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.shqiptv.app.data.MediaItem
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(
    item: MediaItem,
    playlist: List<MediaItem>,
    onItemChange: (MediaItem) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    var controlsVisible by remember { mutableStateOf(true) }
    var controlsTick by remember { mutableIntStateOf(0) }
    val player = remember {
        ExoPlayer.Builder(context)
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(12_000, 35_000, 900, 1_800)
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .build()
            )
            .build().apply { playWhenReady = true; repeatMode = Player.REPEAT_MODE_OFF }
    }

    LaunchedEffect(item.streamUrl) {
        player.setMediaItem(ExoMediaItem.fromUri(item.streamUrl))
        player.prepare()
        player.play()
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    LaunchedEffect(controlsTick) { controlsVisible = true; delay(4_000); controlsVisible = false }
    DisposableEffect(Unit) { onDispose { player.release() } }
    BackHandler(onBack = onBack)

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .focusRequester(focusRequester).focusable()
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                val current = playlist.indexOfFirst { it.id == item.id }
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> {
                        if (playlist.isNotEmpty()) onItemChange(playlist[(current - 1).mod(playlist.size)])
                        controlsTick++; true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                        if (playlist.isNotEmpty()) onItemChange(playlist[(current + 1).mod(playlist.size)])
                        controlsTick++; true
                    }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        if (player.isPlaying) player.pause() else player.play()
                        controlsTick++; true
                    }
                    else -> { controlsTick++; false }
                }
            }
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { PlayerView(it).apply { useController = false; this.player = player } },
            update = { it.player = player },
        )
        AnimatedVisibility(controlsVisible, Modifier.align(Alignment.BottomCenter)) {
            Column(
                Modifier.fillMaxWidth().background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color(0xE605070D)))
                ).padding(horizontal = 52.dp, vertical = 38.dp)
            ) {
                androidx.compose.foundation.text.BasicText(
                    item.name,
                    style = androidx.compose.ui.text.TextStyle(Color.White, 28.sp)
                )
                Spacer(Modifier.height(8.dp))
                androidx.compose.foundation.text.BasicText(
                    item.nowPlaying,
                    style = androidx.compose.ui.text.TextStyle(Color(0xFFB8C1D9), 17.sp)
                )
                Spacer(Modifier.height(14.dp))
                androidx.compose.foundation.text.BasicText(
                    "▲ ▼  Change channel     OK  Pause / Play     Back  Return",
                    style = androidx.compose.ui.text.TextStyle(Color(0xFF8792AE), 14.sp)
                )
            }
        }
    }
}
