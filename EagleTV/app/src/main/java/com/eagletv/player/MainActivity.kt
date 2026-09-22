package com.eagletv.player

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = EagleRed, background = Ink, surface = Panel)) {
                Box(Modifier.fillMaxSize().background(Ink)) { EagleApp() }
            }
        }
    }
}

val Ink = Color(0xFF080B12)
val Panel = Color(0xFF111622)
val PanelLight = Color(0xFF1A2232)
val EagleRed = Color(0xFFFF3B45)
val SoftWhite = Color(0xFFF5F7FA)
val Muted = Color(0xFF9BA6B7)

@Composable
private fun EagleApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = remember { EagleStore(context) }
    val client = remember { XtreamClient() }
    var playlist by remember { mutableStateOf(store.activePlaylist()) }
    var manageProfiles by remember { mutableStateOf(playlist == null) }
    var destination by remember { mutableStateOf(Destination.HOME) }

    if (manageProfiles || playlist == null) {
        PlaylistScreen(
            store = store,
            client = client,
            onOpen = { selected -> store.setActive(selected.id); playlist = selected; manageProfiles = false },
            onAdded = { selected -> playlist = selected; manageProfiles = false }
        )
    } else {
        val active = playlist!!
        BackHandler(enabled = destination != Destination.HOME) { destination = Destination.HOME }
        EagleShell(
            playlist = active,
            client = client,
            store = store,
            destination = destination,
            onNavigate = { destination = it },
            onProfiles = { manageProfiles = true }
        )
    }
}
