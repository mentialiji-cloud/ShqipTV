package com.eagletv.player

import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun PlaylistScreen(store: EagleStore, client: XtreamClient, onOpen: (Playlist) -> Unit, onAdded: (Playlist) -> Unit) {
    var adding by remember { mutableStateOf(store.playlists().isEmpty()) }
    var playlists by remember { mutableStateOf(store.playlists()) }
    if (!adding) {
        Column(Modifier.fillMaxSize().padding(64.dp)) {
            BrandHeader("Choose a playlist", "Your IPTV accounts stay saved on this TV")
            Spacer(Modifier.height(32.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                items(playlists, key = { it.id }) { item ->
                    FocusTile(onClick = { onOpen(item) }, modifier = Modifier.width(280.dp).height(150.dp)) {
                        Column(Modifier.padding(24.dp)) {
                            IconBox(Icons.Default.PlaylistPlay)
                            Spacer(Modifier.height(16.dp))
                            Text(item.name, color = SoftWhite, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Text(item.normalizedServer, color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                item {
                    FocusTile(onClick = { adding = true }, modifier = Modifier.width(220.dp).height(150.dp)) {
                        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            androidx.tv.material3.Icon(Icons.Default.Add, null, tint = SoftWhite, modifier = Modifier.size(34.dp))
                            Text("Add playlist", color = SoftWhite, fontSize = 18.sp)
                        }
                    }
                }
            }
        }
    } else {
        AddPlaylistForm(client, onCancel = { if (playlists.isNotEmpty()) adding = false }, onSave = {
            store.savePlaylist(it); playlists = store.playlists(); onAdded(it)
        })
    }
}

@Composable
private fun AddPlaylistForm(client: XtreamClient, onCancel: () -> Unit, onSave: (Playlist) -> Unit) {
    var name by remember { mutableStateOf("My IPTV") }
    var server by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF121A2C), Ink)))) {
        Row(Modifier.fillMaxSize().padding(64.dp), horizontalArrangement = Arrangement.spacedBy(70.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(0.8f)) {
                IconBox(Icons.Default.LiveTv, 72.dp)
                Spacer(Modifier.height(22.dp))
                Text("EagleTV", color = SoftWhite, fontSize = 46.sp, fontWeight = FontWeight.Black)
                Text("Fast television, built for your remote.", color = Muted, fontSize = 20.sp)
                Spacer(Modifier.height(24.dp))
                Text("EagleTV is a player. It does not provide channels.", color = Muted, fontSize = 14.sp)
            }
            Column(Modifier.weight(1.2f).clip(RoundedCornerShape(22.dp)).background(Panel).padding(30.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Add Xtream Codes", color = SoftWhite, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                TvField("Playlist name", name) { name = it }
                TvField("Server URL — http://example.com:8080", server) { server = it }
                TvField("Username", username) { username = it }
                TvField("Password", password, password = true) { password = it }
                error?.let { Text(it, color = EagleRed, fontSize = 14.sp) }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActionButton(if (busy) "Connecting…" else "Connect", enabled = !busy && server.isNotBlank() && username.isNotBlank() && password.isNotBlank()) {
                        busy = true; error = null
                        scope.launch {
                            val candidate = Playlist(name = name.ifBlank { "My IPTV" }, server = server, username = username, password = password)
                            val issue = client.authenticate(candidate)
                            busy = false
                            if (issue == null) onSave(candidate) else error = issue
                        }
                    }
                    ActionButton("Back", secondary = true, onClick = onCancel)
                }
            }
        }
    }
}

@Composable
private fun TvField(label: String, value: String, password: Boolean = false, onChange: (String) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = value, onValueChange = onChange, singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        textStyle = androidx.compose.ui.text.TextStyle(color = SoftWhite, fontSize = 17.sp),
        modifier = Modifier.fillMaxWidth().height(54.dp).onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(10.dp)).background(if (focused) PanelLight else Ink).border(if (focused) 2.dp else 1.dp, if (focused) EagleRed else Color(0xFF303A4B), RoundedCornerShape(10.dp)).padding(16.dp),
        decorationBox = { inner -> Box(contentAlignment = Alignment.CenterStart) { if (value.isEmpty()) Text(label, color = Muted, fontSize = 16.sp); inner() } }
    )
}

@Composable
fun EagleShell(playlist: Playlist, client: XtreamClient, store: EagleStore, destination: Destination, onNavigate: (Destination) -> Unit, onProfiles: () -> Unit) {
    if (destination == Destination.LIVE) {
        ClassicLiveScreen(playlist, client, store, onHome = { onNavigate(Destination.HOME) })
        return
    }
    Row(Modifier.fillMaxSize()) {
        NavigationRail(destination, playlist.name, onNavigate, onProfiles)
        Box(Modifier.weight(1f).fillMaxHeight()) {
            when (destination) {
                Destination.HOME -> HomeScreen(playlist, client, store)
                Destination.LIVE -> Unit
                Destination.MOVIES -> BrowserScreen("Movies", "movie", playlist, client, store)
                Destination.SERIES -> BrowserScreen("Series", "series", playlist, client, store)
                Destination.FAVORITES -> BrowserScreen("Favorites", "favorites", playlist, client, store)
                Destination.SEARCH -> SearchScreen(playlist, client, store)
                Destination.SETTINGS -> SettingsScreen(store, onProfiles)
            }
        }
    }
}

@Composable
private fun NavigationRail(active: Destination, profile: String, onNavigate: (Destination) -> Unit, onProfiles: () -> Unit) {
    val entries = listOf(
        Triple(Destination.HOME, "Home", Icons.Default.Home), Triple(Destination.LIVE, "Live TV", Icons.Default.LiveTv),
        Triple(Destination.MOVIES, "Movies", Icons.Default.Movie), Triple(Destination.SERIES, "Series", Icons.Default.VideoLibrary),
        Triple(Destination.FAVORITES, "Favorites", Icons.Default.Favorite), Triple(Destination.SEARCH, "Search", Icons.Default.Search),
        Triple(Destination.SETTINGS, "Settings", Icons.Default.Settings)
    )
    Column(Modifier.width(208.dp).fillMaxHeight().background(Color(0xFF0D111B)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { IconBox(Icons.Default.PlayArrow); Spacer(Modifier.width(12.dp)); Text("EagleTV", color = SoftWhite, fontWeight = FontWeight.Black, fontSize = 23.sp); Spacer(Modifier.width(8.dp)); AlbanianFlag() }
        Spacer(Modifier.height(32.dp))
        entries.forEach { (dest, label, icon) -> NavButton(label, icon, active == dest) { onNavigate(dest) }; Spacer(Modifier.height(5.dp)) }
        Spacer(Modifier.weight(1f))
        FocusTile(onClick = onProfiles, modifier = Modifier.fillMaxWidth().height(60.dp), active = false) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.tv.material3.Icon(Icons.Default.AccountCircle, null, tint = Muted); Spacer(Modifier.width(10.dp));
                Column { Text(profile, color = SoftWhite, fontSize = 14.sp, maxLines = 1); Text("Switch playlist", color = Muted, fontSize = 11.sp) }
            }
        }
    }
}

@Composable private fun NavButton(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    FocusTile(onClick, Modifier.fillMaxWidth().height(51.dp), active = selected) {
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.tv.material3.Icon(icon, null, tint = if (selected) Color.White else Muted, modifier = Modifier.size(22.dp)); Spacer(Modifier.width(13.dp)); Text(label, color = if (selected) Color.White else Muted, fontSize = 16.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        }
    }
}

@Composable
private fun ClassicLiveScreen(playlist: Playlist, client: XtreamClient, store: EagleStore, onHome: () -> Unit) {
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var channels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var categoryId by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<Channel?>(null) }
    var epg by remember { mutableStateOf<List<Program>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var favorites by remember { mutableStateOf(store.favorites()) }
    var fullscreen by remember { mutableStateOf(false) }
    val preset = remember { store.buffer() }
    val shown = remember(channels, categoryId, favorites) {
        when (categoryId) {
            "__favorites" -> channels.filter { it.id in favorites }
            null -> channels
            else -> channels.filter { it.categoryId == categoryId }
        }
    }

    LaunchedEffect(Unit) {
        runCatching {
            coroutineScope {
                val categoryRequest = async { client.categories(playlist, "live") }
                val channelRequest = async { client.liveChannels(playlist) }
                categories = categoryRequest.await()
                channels = channelRequest.await()
                selected = channels.firstOrNull()
            }
        }.onFailure { error = it.message ?: "Unable to load playlist" }
        loading = false
    }
    LaunchedEffect(selected?.id) {
        epg = emptyList()
        val channel = selected ?: return@LaunchedEffect
        delay(280)
        epg = client.epg(playlist, channel.id)
    }

    val url = selected?.let { client.streamUrl(playlist, it) }
    if (fullscreen && url != null) {
        FullScreenPlayer(url, preset) { fullscreen = false }
        return
    }

    BackHandler(onBack = onHome)
    Column(Modifier.fillMaxSize().background(Ink)) {
        Row(
            Modifier.fillMaxWidth().height(66.dp).background(Color(0xFF0B1629)).padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Eagle", color = SoftWhite, fontSize = 28.sp, fontWeight = FontWeight.Black)
            Text("TV", color = Color(0xFF2484FF), fontSize = 28.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.width(10.dp)); AlbanianFlag()
            Spacer(Modifier.width(24.dp)); Box(Modifier.width(1.dp).height(26.dp).background(Color(0xFF40516B)))
            Spacer(Modifier.width(24.dp)); Text("LIVE TV", color = SoftWhite, fontSize = 17.sp, letterSpacing = 3.sp)
            Spacer(Modifier.weight(1f)); Text(SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date()), color = Muted, fontSize = 17.sp)
        }

        if (loading) { LoadingState(); return@Column }
        if (error != null) { EmptyState(error!!, Icons.Default.WifiOff); return@Column }

        Row(Modifier.weight(1f)) {
            LazyColumn(Modifier.width(245.dp).fillMaxHeight().background(Color(0xFF0A1322)).padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                item { ClassicCategoryRow("★  Favorites", categoryId == "__favorites") { categoryId = "__favorites"; selected = channels.firstOrNull { it.id in favorites } } }
                item { ClassicCategoryRow("☰  All channels", categoryId == null) { categoryId = null; selected = channels.firstOrNull() } }
                items(categories, key = { it.id }) { category ->
                    ClassicCategoryRow(category.name, categoryId == category.id) {
                        categoryId = category.id
                        selected = channels.firstOrNull { it.categoryId == category.id }
                    }
                }
            }
            LazyColumn(Modifier.width(620.dp).fillMaxHeight().background(Color(0xFF0D1728)), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                items(shown, key = { it.id }) { channel ->
                    ClassicChannelRow(
                        channel = channel,
                        selected = selected?.id == channel.id,
                        favorite = channel.id in favorites,
                        onFocus = { selected = channel },
                        onOpen = { selected = channel; store.addRecentChannel(channel.id); fullscreen = true }
                    )
                }
            }
            ClassicDetailsPanel(selected, epg, Modifier.weight(1f), onPlay = { if (url != null) { selected?.let { store.addRecentChannel(it.id) }; fullscreen = true } }, onFavorite = { selected?.let { favorites = store.toggleFavorite(it.id) } })
        }
    }
}

@Composable
private fun ClassicCategoryRow(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().height(54.dp).onFocusChanged { focused = it.isFocused }.remoteClick(onClick)
            .background(if (selected || focused) Color(0xFF1268F3) else Color.Transparent)
            .padding(horizontal = 17.dp),
        verticalAlignment = Alignment.CenterVertically
    ) { Text(label, color = if (selected || focused) Color.White else Color(0xFFC1CBDA), fontSize = 16.sp, fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

@Composable
private fun ClassicChannelRow(channel: Channel, selected: Boolean, favorite: Boolean, onFocus: () -> Unit, onOpen: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().height(64.dp).onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus() }.remoteClick(onOpen)
            .background(if (focused) Color(0xFF1268F3) else if (selected) Color(0xFF172943) else Color.Transparent)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(channel.id.toString(), color = if (focused) Color.White else Muted, fontSize = 13.sp, modifier = Modifier.width(48.dp))
        Box(Modifier.size(42.dp).background(Color(0xFF07101D)), contentAlignment = Alignment.Center) {
            if (!channel.logo.isNullOrBlank()) AsyncImage(channel.logo, null, Modifier.fillMaxSize().padding(4.dp))
            else androidx.tv.material3.Icon(Icons.Default.LiveTv, null, tint = Muted, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(channel.name, color = SoftWhite, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (favorite) androidx.tv.material3.Icon(Icons.Default.Favorite, null, tint = if (focused) Color.White else EagleRed, modifier = Modifier.size(17.dp))
    }
}

@Composable
private fun ClassicDetailsPanel(item: Channel?, epg: List<Program>, modifier: Modifier, onPlay: () -> Unit, onFavorite: () -> Unit) {
    val now = epg.firstOrNull()
    val next = epg.getOrNull(1)
    Column(modifier.fillMaxHeight().background(Color(0xFF07111F))) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 8.2f).background(Color(0xFF101D31)), contentAlignment = Alignment.Center) {
            if (!item?.logo.isNullOrBlank()) AsyncImage(item?.logo, null, Modifier.size(150.dp).padding(14.dp))
            else androidx.tv.material3.Icon(Icons.Default.LiveTv, null, tint = Color(0xFF40516B), modifier = Modifier.size(84.dp))
        }
        Column(Modifier.padding(24.dp)) {
            Text("Now Playing", color = Color(0xFF3A91FF), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(now?.title ?: item?.name ?: "Choose a channel", color = SoftWhite, fontSize = 27.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (now != null) Text(timeRange(now), color = Muted, fontSize = 14.sp)
            Spacer(Modifier.height(18.dp)); Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { ActionButton("Watch", onClick = onPlay); ActionButton("Favorite", secondary = true, onClick = onFavorite) }
            Spacer(Modifier.height(22.dp)); Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF28374B)))
            Spacer(Modifier.height(18.dp)); Text("Up Next", color = Color(0xFF3A91FF), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(7.dp)); Text(next?.title ?: "Guide information unavailable", color = SoftWhite, fontSize = 21.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (next != null) Text(timeRange(next), color = Muted, fontSize = 14.sp)
        }
    }
}

@Composable
private fun AlbanianFlag() { Text("🇦🇱", fontSize = 22.sp) }

@Composable
private fun HomeScreen(playlist: Playlist, client: XtreamClient, store: EagleStore) {
    var recent by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf<Channel?>(null) }
    var fullscreen by remember { mutableStateOf(false) }
    val preset = remember { store.buffer() }

    LaunchedEffect(playlist.id) {
        val ids = store.recentChannels()
        if (ids.isNotEmpty()) {
            val channels = runCatching { client.liveChannels(playlist) }.getOrDefault(emptyList()).associateBy { it.id }
            recent = ids.mapNotNull(channels::get)
            selected = recent.firstOrNull()
        }
        loading = false
    }

    val url = selected?.let { client.streamUrl(playlist, it) }
    if (fullscreen && url != null) {
        FullScreenPlayer(url, preset) { fullscreen = false }
        return
    }

    Box(Modifier.fillMaxSize()) {
        Image(painterResource(R.drawable.eagletv_castle_home), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xE8151B28), Color(0xA6192130), Color(0x54141A23)))))
        Column(Modifier.fillMaxSize().padding(horizontal = 46.dp, vertical = 38.dp)) {
            Text("Good ${dayPart()}", color = Color.White.copy(alpha = .78f), fontSize = 17.sp)
            Text("Welcome back", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.weight(.42f))
            Text("Recently Watched", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Black)
            Text("Continue from where you left off", color = Color.White.copy(alpha = .72f), fontSize = 14.sp)
            Spacer(Modifier.height(17.dp))
            when {
                loading -> Text("Loading recent channels…", color = Color.White.copy(alpha = .72f), fontSize = 17.sp)
                recent.isEmpty() -> Box(Modifier.fillMaxWidth().height(175.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xA6222934)).border(1.dp, Color.White.copy(alpha = .22f), RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        androidx.tv.material3.Icon(Icons.Default.History, null, tint = Color.White.copy(alpha = .75f), modifier = Modifier.size(38.dp))
                        Spacer(Modifier.height(10.dp)); Text("Your recently watched channels will appear here", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("Open Live TV and play any channel to begin", color = Color.White.copy(alpha = .68f), fontSize = 14.sp)
                    }
                }
                else -> LazyRow(horizontalArrangement = Arrangement.spacedBy(15.dp)) {
                    items(recent, key = { it.id }) { channel ->
                        FocusTile(onClick = { selected = channel; store.addRecentChannel(channel.id); fullscreen = true }, modifier = Modifier.width(228.dp).height(148.dp)) {
                            Box(Modifier.fillMaxSize().background(Color(0xD91A2230)).padding(16.dp)) {
                                Box(Modifier.size(62.dp).clip(RoundedCornerShape(12.dp)).background(Color.White), contentAlignment = Alignment.Center) {
                                    if (!channel.logo.isNullOrBlank()) AsyncImage(channel.logo, null, Modifier.fillMaxSize().padding(7.dp)) else androidx.tv.material3.Icon(Icons.Default.LiveTv, null, tint = Ink)
                                }
                                Column(Modifier.align(Alignment.BottomStart)) {
                                    Text(channel.name, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("Press OK to watch", color = Color.White.copy(alpha = .62f), fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.weight(.35f))
        }
    }
}

@Composable
private fun BrowserScreen(title: String, kind: String, playlist: Playlist, client: XtreamClient, store: EagleStore) {
    var categories by remember(kind) { mutableStateOf<List<Category>>(emptyList()) }
    var allItems by remember(kind) { mutableStateOf<List<Channel>>(emptyList()) }
    var category by remember(kind) { mutableStateOf<String?>(null) }
    var selected by remember(kind) { mutableStateOf<Channel?>(null) }
    var epg by remember { mutableStateOf<List<Program>>(emptyList()) }
    var loading by remember(kind) { mutableStateOf(true) }
    var error by remember(kind) { mutableStateOf<String?>(null) }
    var favorites by remember { mutableStateOf(store.favorites()) }
    var fullscreen by remember { mutableStateOf(false) }
    val preset = remember { store.buffer() }
    val shown = remember(allItems, category, kind, favorites) {
        when { kind == "favorites" -> allItems.filter { it.id in favorites }; category == null -> allItems; else -> allItems.filter { it.categoryId == category } }
    }
    LaunchedEffect(kind) {
        loading = true; error = null
        runCatching {
            coroutineScope {
                val cat = async { if (kind == "favorites") client.categories(playlist, "live") else client.categories(playlist, if (kind == "movie") "vod" else kind) }
                val list = async { when (kind) { "movie" -> client.movies(playlist); "series" -> client.series(playlist); else -> client.liveChannels(playlist) } }
                categories = cat.await(); allItems = list.await(); selected = allItems.firstOrNull()
            }
        }.onFailure { error = it.message ?: "Unable to load playlist" }
        loading = false
    }
    LaunchedEffect(selected?.id) { epg = selected?.takeIf { it.streamType == "live" }?.let { client.epg(playlist, it.id) }.orEmpty() }
    val playable = selected?.takeIf { it.streamType != "series" }
    val url = playable?.let { client.streamUrl(playlist, it) }
    if (fullscreen && url != null) { FullScreenPlayer(url, preset) { fullscreen = false }; return }

    Column(Modifier.fillMaxSize().padding(26.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = SoftWhite, fontSize = 29.sp, fontWeight = FontWeight.Black); Spacer(Modifier.weight(1f)); Text("${shown.size} items", color = Muted, fontSize = 14.sp)
        }
        Spacer(Modifier.height(17.dp))
        if (loading) { LoadingState(); return@Column }
        if (error != null) { EmptyState(error!!, Icons.Default.WifiOff); return@Column }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CategoryColumn(categories, category, shownAllLabel = if (kind == "favorites") "Favorites" else "All", onSelect = { category = it; selected = allItems.firstOrNull { item -> it == null || item.categoryId == it } })
            ChannelColumn(shown, selected?.id, favorites, onFocused = { selected = it }, onOpen = { selected = it; if (it.streamType != "series") { if (it.streamType == "live") store.addRecentChannel(it.id); fullscreen = true } }, onFavorite = { favorites = store.toggleFavorite(it.id) })
            DetailsPanel(selected, epg, url, preset, favorites, Modifier.weight(1f), onPlay = { if (url != null) { selected?.takeIf { it.streamType == "live" }?.let { store.addRecentChannel(it.id) }; fullscreen = true } }, onFavorite = { selected?.let { favorites = store.toggleFavorite(it.id) } })
        }
    }
}

@Composable
private fun CategoryColumn(items: List<Category>, selected: String?, shownAllLabel: String, onSelect: (String?) -> Unit) {
    LazyColumn(Modifier.width(215.dp).fillMaxHeight().clip(RoundedCornerShape(14.dp)).background(Panel).padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        item { ListButton(shownAllLabel, selected == null) { onSelect(null) } }
        items(items, key = { it.id }) { ListButton(it.name, selected == it.id) { onSelect(it.id) } }
    }
}

@Composable
private fun ChannelColumn(items: List<Channel>, selectedId: Int?, favorites: Set<Int>, onFocused: (Channel) -> Unit, onOpen: (Channel) -> Unit, onFavorite: (Channel) -> Unit) {
    if (items.isEmpty()) { Box(Modifier.width(370.dp).fillMaxHeight().background(Panel), contentAlignment = Alignment.Center) { Text("No items", color = Muted) }; return }
    LazyColumn(Modifier.width(385.dp).fillMaxHeight().clip(RoundedCornerShape(14.dp)).background(Panel).padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(items, key = { "${it.streamType}-${it.id}" }) { item ->
            ChannelRow(item, selectedId == item.id, item.id in favorites, onFocus = { onFocused(item) }, onClick = { onOpen(item) }, onFavorite = { onFavorite(item) })
        }
    }
}

@Composable
private fun DetailsPanel(item: Channel?, epg: List<Program>, url: String?, preset: BufferPreset, favorites: Set<Int>, modifier: Modifier, onPlay: () -> Unit, onFavorite: () -> Unit) {
    Column(modifier.fillMaxHeight().clip(RoundedCornerShape(14.dp)).background(Panel)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 8.2f).background(PanelLight), contentAlignment = Alignment.Center) { AsyncImage(item?.logo, null, Modifier.size(120.dp)) }
        Column(Modifier.padding(20.dp)) {
            Text(item?.name ?: "Choose an item", color = SoftWhite, fontSize = 23.sp, fontWeight = FontWeight.Bold, maxLines = 2)
            if (item != null) {
                Spacer(Modifier.height(11.dp)); Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (url != null) ActionButton("Watch", onClick = onPlay)
                    ActionButton(if (item.id in favorites) "Favorited" else "Favorite", secondary = true, onClick = onFavorite)
                }
                Spacer(Modifier.height(15.dp))
                if (!item.plot.isNullOrBlank()) Text(item.plot, color = Muted, fontSize = 14.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                epg.take(3).forEachIndexed { index, p ->
                    Spacer(Modifier.height(if (index == 0) 10.dp else 7.dp))
                    Text((if (index == 0) "NOW  " else "NEXT  ") + p.title, color = if (index == 0) EagleRed else SoftWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(timeRange(p), color = Muted, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun SearchScreen(playlist: Playlist, client: XtreamClient, store: EagleStore) {
    var query by remember { mutableStateOf("") }
    var data by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { runCatching { data = coroutineScope { val a = async { client.liveChannels(playlist) }; val b = async { client.movies(playlist) }; val c = async { client.series(playlist) }; a.await() + b.await() + c.await() } }; loading = false }
    val result = remember(query, data) { if (query.length < 2) emptyList() else data.filter { it.name.contains(query, true) }.take(120) }
    Column(Modifier.fillMaxSize().padding(36.dp)) {
        Text("Search", color = SoftWhite, fontSize = 30.sp, fontWeight = FontWeight.Black); Spacer(Modifier.height(16.dp)); TvField("Search channels, movies, and series", query) { query = it }
        Spacer(Modifier.height(20.dp)); if (loading) LoadingState() else LazyVerticalGrid(GridCells.Adaptive(210.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(result, key = { "${it.streamType}-${it.id}" }) { item -> PosterCard(item) }
        }
    }
}

@Composable
private fun SettingsScreen(store: EagleStore, onProfiles: () -> Unit) {
    var selected by remember { mutableStateOf(store.buffer()) }
    Column(Modifier.fillMaxSize().padding(38.dp)) {
        Text("Settings", color = SoftWhite, fontSize = 30.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(26.dp)); Text("Playback buffer", color = SoftWhite, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Text("Lower values switch faster. Higher values help unstable connections.", color = Muted, fontSize = 14.sp)
        Spacer(Modifier.height(14.dp)); Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BufferPreset.entries.forEach { value -> FocusTile({ selected = value; store.setBuffer(value) }, Modifier.width(145.dp).height(86.dp), active = selected == value) { Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) { Text(value.label, color = SoftWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold); Text(if (value == BufferPreset.NONE) "Fastest" else if (value == BufferPreset.HIGH) "Most stable" else "", color = Muted, fontSize = 11.sp) } } }
        }
        Spacer(Modifier.height(34.dp)); Text("Playlists", color = SoftWhite, fontSize = 21.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(12.dp)); ActionButton("Manage playlists", onClick = onProfiles)
        Spacer(Modifier.height(38.dp)); Text("EagleTV 1.0 beta", color = Muted, fontSize = 13.sp); Text("Player only — no channels are included.", color = Muted, fontSize = 13.sp)
    }
}

@Composable private fun ChannelRow(item: Channel, selected: Boolean, favorite: Boolean, onFocus: () -> Unit, onClick: () -> Unit, onFavorite: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().height(66.dp).onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus() }.remoteClick(onClick).clip(RoundedCornerShape(9.dp)).background(if (focused || selected) PanelLight else Color.Transparent).border(if (focused) 2.dp else 0.dp, if (focused) EagleRed else Color.Transparent, RoundedCornerShape(9.dp)).padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(Ink), contentAlignment = Alignment.Center) { if (!item.logo.isNullOrBlank()) AsyncImage(item.logo, null, Modifier.fillMaxSize().padding(5.dp)) else androidx.tv.material3.Icon(if (item.streamType == "live") Icons.Default.LiveTv else Icons.Default.Movie, null, tint = Muted) }
        Spacer(Modifier.width(11.dp)); Column(Modifier.weight(1f)) { Text(item.name, color = SoftWhite, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(item.streamType.replaceFirstChar { it.uppercase() }, color = Muted, fontSize = 11.sp) }
        if (favorite) androidx.tv.material3.Icon(Icons.Default.Favorite, null, tint = EagleRed, modifier = Modifier.size(17.dp))
    }
}

@Composable private fun PosterCard(item: Channel) { FocusTile({}, Modifier.height(120.dp)) { Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) { AsyncImage(item.logo, null, Modifier.size(76.dp).clip(RoundedCornerShape(8.dp)).background(Ink)); Spacer(Modifier.width(12.dp)); Column { Text(item.name, color = SoftWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2); Text(item.streamType, color = Muted, fontSize = 12.sp) } } } }

@Composable private fun ListButton(label: String, selected: Boolean, onClick: () -> Unit) { FocusTile(onClick, Modifier.fillMaxWidth().height(47.dp), active = selected) { Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) { Text(label, color = if (selected) Color.White else Muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) } } }

@Composable private fun FocusTile(onClick: () -> Unit, modifier: Modifier = Modifier, active: Boolean = false, content: @Composable BoxScope.() -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val color = if (active || focused) EagleRed else PanelLight
    Box(modifier.onFocusChanged { focused = it.isFocused }.remoteClick(onClick).clip(RoundedCornerShape(13.dp)).background(color).border(if (focused) 3.dp else 0.dp, Color.White, RoundedCornerShape(13.dp)), content = content)
}

private fun Modifier.remoteClick(onClick: () -> Unit): Modifier =
    onPreviewKeyEvent { event ->
        val keyCode = event.nativeKeyEvent.keyCode
        val isSelect = keyCode == AndroidKeyEvent.KEYCODE_DPAD_CENTER || keyCode == AndroidKeyEvent.KEYCODE_ENTER || keyCode == AndroidKeyEvent.KEYCODE_NUMPAD_ENTER
        if (!isSelect) false else {
            if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) onClick()
            true
        }
    }.clickable(onClick = onClick).focusable()

@Composable private fun ActionButton(label: String, enabled: Boolean = true, secondary: Boolean = false, onClick: () -> Unit) { FocusTile(if (enabled) onClick else ({}), Modifier.height(47.dp).widthIn(min = 112.dp), active = !secondary && enabled) { Box(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) { Text(label, color = if (enabled) SoftWhite else Muted, fontSize = 15.sp, fontWeight = FontWeight.Bold) } } }
@Composable private fun HeroCard(title: String, subtitle: String, icon: ImageVector, color: Color, modifier: Modifier, onClick: () -> Unit) { FocusTile(onClick, modifier.height(220.dp)) { Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(color, color.copy(alpha = .45f)))).padding(24.dp)) { androidx.tv.material3.Icon(icon, null, tint = Color.White.copy(.88f), modifier = Modifier.size(58.dp).align(Alignment.TopEnd)); Column(Modifier.align(Alignment.BottomStart)) { Text(title, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black); Text(subtitle, color = Color.White.copy(.75f), fontSize = 15.sp) } } } }
@Composable private fun SmallCard(title: String, icon: ImageVector, onClick: () -> Unit) { FocusTile(onClick, Modifier.width(230.dp).height(94.dp)) { Row(Modifier.fillMaxSize().padding(18.dp), verticalAlignment = Alignment.CenterVertically) { androidx.tv.material3.Icon(icon, null, tint = EagleRed, modifier = Modifier.size(30.dp)); Spacer(Modifier.width(14.dp)); Text(title, color = SoftWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold) } } }
@Composable private fun IconBox(icon: ImageVector, size: androidx.compose.ui.unit.Dp = 46.dp) { Box(Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(EagleRed), contentAlignment = Alignment.Center) { androidx.tv.material3.Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * .58f)) } }
@Composable private fun BrandHeader(title: String, subtitle: String) { Row(verticalAlignment = Alignment.CenterVertically) { IconBox(Icons.Default.PlayArrow, 58.dp); Spacer(Modifier.width(18.dp)); Column { Text(title, color = SoftWhite, fontSize = 32.sp, fontWeight = FontWeight.Black); Text(subtitle, color = Muted, fontSize = 16.sp) } } }
@Composable private fun LoadingState() { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Loading your library…", color = Muted, fontSize = 18.sp) } }
@Composable private fun EmptyState(text: String, icon: ImageVector) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { androidx.tv.material3.Icon(icon, null, tint = Muted, modifier = Modifier.size(44.dp)); Spacer(Modifier.height(12.dp)); Text(text, color = Muted, fontSize = 17.sp) } } }
private fun dayPart(): String { val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY); return when (h) { in 5..11 -> "morning"; in 12..17 -> "afternoon"; else -> "evening" } }
private fun timeRange(p: Program): String { val f = SimpleDateFormat("h:mm a", Locale.getDefault()); return if (p.start > 0) "${f.format(Date(p.start))} – ${f.format(Date(p.end))}" else "" }
