package com.shqiptv.app.ui

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import coil3.compose.AsyncImage
import com.shqiptv.app.AppState
import com.shqiptv.app.MainViewModel
import com.shqiptv.app.data.*
import com.shqiptv.app.player.PlayerScreen

private val Bg = Color(0xFF05070D)
private val Panel = Color(0xFF101522)
private val Panel2 = Color(0xFF171E2E)
private val Red = Color(0xFFE43131)
private val TextPrimary = Color(0xFFF6F7FB)
private val TextSecondary = Color(0xFF98A3BE)

private enum class Section(val title: String) { HOME("Home"), LIVE("Live TV"), GUIDE("Guide"), MOVIES("Movies"), SERIES("Series"), FAVORITES("Favorites"), SETTINGS("Settings") }

@Composable
fun ShqipTvApp(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var section by remember { mutableStateOf(Section.HOME) }
    var playing by remember { mutableStateOf<MediaItem?>(null) }

    if (playing != null) {
        val playlist = state.catalog.items(playing!!.kind).filter { it.streamUrl.isNotBlank() }
        PlayerScreen(playing!!, playlist, { playing = it }, { playing = null })
        return
    }

    Box(Modifier.fillMaxSize().background(Bg)) {
        when {
            state.provider == null -> LoginScreen(viewModel::connect)
            state.loading -> LoadingScreen(state.provider?.name.orEmpty())
            state.error != null -> ErrorScreen(state.error!!, viewModel::retry, viewModel::signOut)
            else -> MainShell(state, section, {
                section = it
                when (it) {
                    Section.MOVIES -> viewModel.loadKind(ContentKind.MOVIE)
                    Section.SERIES -> viewModel.loadKind(ContentKind.SERIES)
                    else -> Unit
                }
            }, { playing = it }, viewModel::toggleFavorite, viewModel::signOut)
        }
    }
}

@Composable
private fun LoginScreen(onConnect: (ProviderConfig) -> Unit) {
    var xtream by remember { mutableStateOf(true) }
    var name by remember { mutableStateOf("My IPTV") }
    var server by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var m3u by remember { mutableStateOf("") }
    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF090D18), Color(0xFF170607))))) {
        Column(Modifier.width(630.dp).align(Alignment.Center).padding(36.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(58.dp).clip(RoundedCornerShape(18.dp)).background(Red), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(36.dp))
                }
                Spacer(Modifier.width(18.dp))
                Column {
                    Label("SHQIP TV", 32.sp, FontWeight.Bold)
                    Label("Fast, simple television", 15.sp, color = TextSecondary)
                }
            }
            Spacer(Modifier.height(34.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FocusButton("Xtream Codes", selected = xtream) { xtream = true }
                FocusButton("M3U Playlist", selected = !xtream) { xtream = false }
            }
            Spacer(Modifier.height(22.dp))
            TvInput("Playlist name", name, { name = it })
            Spacer(Modifier.height(12.dp))
            if (xtream) {
                TvInput("Server URL", server, { server = it })
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvInput("Username", username, { username = it }, Modifier.weight(1f))
                    TvInput("Password", password, { password = it }, Modifier.weight(1f), password = true)
                }
            } else TvInput("M3U or M3U8 URL", m3u, { m3u = it })
            Spacer(Modifier.height(24.dp))
            FocusButton("ADD PLAYLIST", selected = true, modifier = Modifier.fillMaxWidth()) {
                onConnect(ProviderConfig(name, server, username, password, m3u))
            }
        }
    }
}

@Composable
private fun MainShell(
    state: AppState,
    section: Section,
    onSection: (Section) -> Unit,
    onPlay: (MediaItem) -> Unit,
    onFavorite: (String) -> Unit,
    onSignOut: () -> Unit,
) {
    Row(Modifier.fillMaxSize()) {
        NavigationRail(section, onSection)
        Box(Modifier.weight(1f).fillMaxHeight()) {
            when (section) {
                Section.HOME -> HomeScreen(state, onSection, onPlay)
                Section.LIVE -> BrowserScreen("Live TV", ContentKind.LIVE, state, onPlay, onFavorite)
                Section.GUIDE -> GuideScreen(state, onPlay)
                Section.MOVIES -> BrowserScreen("Movies", ContentKind.MOVIE, state, onPlay, onFavorite)
                Section.SERIES -> BrowserScreen("Series", ContentKind.SERIES, state, onPlay, onFavorite)
                Section.FAVORITES -> FavoritesScreen(state, onPlay, onFavorite)
                Section.SETTINGS -> SettingsScreen(state, onSignOut)
            }
        }
    }
}

@Composable
private fun NavigationRail(selected: Section, onSelect: (Section) -> Unit) {
    Column(
        Modifier.width(94.dp).fillMaxHeight().background(Color(0xFF080B13)).padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)).background(Red), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(34.dp))
        val icons = listOf(Icons.Default.Home, Icons.Default.LiveTv, Icons.Default.CalendarMonth, Icons.Default.Movie, Icons.Default.VideoLibrary, Icons.Default.Favorite, Icons.Default.Settings)
        Section.entries.forEachIndexed { index, section ->
            NavIcon(icons[index], section.title, selected == section) { onSelect(section) }
            Spacer(Modifier.height(9.dp))
        }
    }
}

@Composable
private fun NavIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val bg by animateColorAsState(if (focused || selected) Red else Color.Transparent)
    Box(
        Modifier.size(62.dp, 54.dp).clip(RoundedCornerShape(15.dp)).background(bg)
            .onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = if (focused || selected) Color.White else TextSecondary, modifier = Modifier.size(25.dp)) }
}

@Composable
private fun HomeScreen(state: AppState, onSection: (Section) -> Unit, onPlay: (MediaItem) -> Unit) {
    Column(Modifier.fillMaxSize().padding(36.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column { Label("Mirë se vini", 17.sp, color = TextSecondary); Label(state.provider?.name ?: "Shqip TV", 30.sp, FontWeight.Bold) }
            Label("SHQIP TV", 18.sp, FontWeight.Bold, Red)
        }
        Spacer(Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            HomeTile("LIVE TV", "${state.catalog.live.size} channels", Icons.Default.LiveTv, Modifier.weight(1f)) { onSection(Section.LIVE) }
            HomeTile("MOVIES", "${state.catalog.movies.size} titles", Icons.Default.Movie, Modifier.weight(1f)) { onSection(Section.MOVIES) }
            HomeTile("SERIES", "${state.catalog.series.size} shows", Icons.Default.VideoLibrary, Modifier.weight(1f)) { onSection(Section.SERIES) }
        }
        Spacer(Modifier.height(30.dp))
        Label("Popular channels", 22.sp, FontWeight.SemiBold)
        Spacer(Modifier.height(14.dp))
        LazyVerticalGrid(GridCells.Adaptive(210.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            items(state.catalog.live.take(12), key = { it.id }) { ChannelCard(it, it.id in state.favorites, { onPlay(it) }, {}) }
        }
    }
}

@Composable
private fun BrowserScreen(title: String, kind: ContentKind, state: AppState, onPlay: (MediaItem) -> Unit, onFavorite: (String) -> Unit) {
    val allItems = state.catalog.items(kind)
    val categories = remember(state.catalog, kind) { listOf(Category("*", "All", kind)) + state.catalog.categories.filter { it.kind == kind } }
    var selectedCategory by remember(kind) { mutableStateOf("*") }
    var search by remember(kind) { mutableStateOf("") }
    val visible = remember(allItems, selectedCategory, search) {
        allItems.asSequence().filter { selectedCategory == "*" || it.categoryId == selectedCategory }
            .filter { search.isBlank() || it.name.contains(search, ignoreCase = true) }.toList()
    }
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(245.dp).fillMaxHeight().background(Color(0xFF0B0F19)).padding(22.dp)) {
            Label(title.uppercase(), 24.sp, FontWeight.Bold)
            Spacer(Modifier.height(18.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(categories, key = { it.id }) { cat ->
                    CategoryRow(cat.name, cat.id == selectedCategory) { selectedCategory = cat.id }
                }
            }
        }
        Column(Modifier.weight(1f).padding(30.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TvInput("Search $title", search, { search = it }, Modifier.weight(1f))
                Spacer(Modifier.width(18.dp)); Label("${visible.size} items", 15.sp, color = TextSecondary)
            }
            Spacer(Modifier.height(20.dp))
            if (kind in state.loadingKinds) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Label("Loading $title…", 19.sp, color = TextSecondary) }
            } else if (kind == ContentKind.LIVE) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(visible, key = { it.id }) { media ->
                        ChannelRow(media, media.id in state.favorites, { onPlay(media) }) { onFavorite(media.id) }
                    }
                }
            } else {
                LazyVerticalGrid(GridCells.Adaptive(190.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(visible, key = { it.id }) { media ->
                        ChannelCard(media, media.id in state.favorites, { if (media.streamUrl.isNotBlank()) onPlay(media) }, { onFavorite(media.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideScreen(state: AppState, onPlay: (MediaItem) -> Unit) {
    Column(Modifier.fillMaxSize().padding(34.dp)) {
        Label("TV GUIDE", 28.sp, FontWeight.Bold); Spacer(Modifier.height(20.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.catalog.live, key = { it.id }) { ChannelRow(it, false, { onPlay(it) }, {}) }
        }
    }
}

@Composable
private fun FavoritesScreen(state: AppState, onPlay: (MediaItem) -> Unit, onFavorite: (String) -> Unit) {
    val favorites = remember(state.catalog, state.favorites) { (state.catalog.live + state.catalog.movies).filter { it.id in state.favorites } }
    Column(Modifier.fillMaxSize().padding(34.dp)) {
        Label("FAVORITES", 28.sp, FontWeight.Bold); Spacer(Modifier.height(20.dp))
        LazyVerticalGrid(GridCells.Adaptive(210.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            items(favorites, key = { it.id }) { ChannelCard(it, true, { onPlay(it) }, { onFavorite(it.id) }) }
        }
    }
}

@Composable
private fun SettingsScreen(state: AppState, onSignOut: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(42.dp)) {
        Label("SETTINGS", 30.sp, FontWeight.Bold); Spacer(Modifier.height(28.dp))
        Label("Playlist", 15.sp, color = TextSecondary); Spacer(Modifier.height(6.dp)); Label(state.provider?.name ?: "", 22.sp, FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp)); Label("${state.catalog.live.size} channels • ${state.catalog.movies.size} movies • ${state.catalog.series.size} series", 16.sp, color = TextSecondary)
        Spacer(Modifier.height(28.dp)); FocusButton("REMOVE PLAYLIST", selected = false, onClick = onSignOut)
        Spacer(Modifier.weight(1f)); Label("Shqip TV 2.0 • Built for Google TV", 14.sp, color = TextSecondary)
    }
}

@Composable
private fun LoadingScreen(name: String) = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) { Label("SHQIP TV", 32.sp, FontWeight.Bold, Red); Spacer(Modifier.height(14.dp)); Label("Loading $name…", 17.sp, color = TextSecondary) }
}

@Composable
private fun ErrorScreen(message: String, retry: () -> Unit, reset: () -> Unit) = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Column(Modifier.width(620.dp).clip(RoundedCornerShape(22.dp)).background(Panel).padding(34.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.Warning, null, tint = Red, modifier = Modifier.size(48.dp)); Spacer(Modifier.height(16.dp)); Label("Unable to connect", 26.sp, FontWeight.Bold)
        Spacer(Modifier.height(10.dp)); Label(message, 16.sp, color = TextSecondary); Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) { FocusButton("TRY AGAIN", true, onClick = retry); FocusButton("CHANGE LOGIN", false, onClick = reset) }
    }
}

@Composable
private fun HomeTile(title: String, detail: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    FocusSurface(modifier.height(145.dp), onClick) {
        Row(Modifier.fillMaxSize().padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(Red.copy(alpha = .18f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Red, modifier = Modifier.size(34.dp)) }
            Spacer(Modifier.width(18.dp)); Column { Label(title, 21.sp, FontWeight.Bold); Spacer(Modifier.height(6.dp)); Label(detail, 14.sp, color = TextSecondary) }
        }
    }
}

@Composable
private fun ChannelRow(media: MediaItem, favorite: Boolean, onClick: () -> Unit, onFavorite: () -> Unit) {
    FocusSurface(Modifier.fillMaxWidth().height(78.dp), onClick) {
        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(media.logo, null, Modifier.size(50.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(.06f)))
            Spacer(Modifier.width(16.dp)); Column(Modifier.weight(1f)) { Label(media.name, 18.sp, FontWeight.SemiBold); Label(media.nowPlaying, 13.sp, color = TextSecondary) }
            Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Favorite", tint = if (favorite) Red else TextSecondary, modifier = Modifier.size(23.dp).clickable(onClick = onFavorite))
            Spacer(Modifier.width(16.dp)); Icon(Icons.Default.PlayArrow, null, tint = Color.White)
        }
    }
}

@Composable
private fun ChannelCard(media: MediaItem, favorite: Boolean, onClick: () -> Unit, onFavorite: () -> Unit) {
    FocusSurface(Modifier.height(142.dp), onClick) {
        Box(Modifier.fillMaxSize()) {
            AsyncImage(media.logo, null, Modifier.fillMaxSize().padding(22.dp))
            Box(Modifier.fillMaxWidth().align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xF0101420)))).padding(12.dp)) {
                Label(media.name, 15.sp, FontWeight.SemiBold)
            }
            Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = if (favorite) Red else TextSecondary, modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).size(20.dp).clickable(onClick = onFavorite))
        }
    }
}

@Composable
private fun CategoryRow(text: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(12.dp)).background(if (focused || selected) Red else Color.Transparent).onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick).padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
        Label(text, 15.sp, if (selected) FontWeight.Bold else FontWeight.Normal, if (focused || selected) Color.White else TextSecondary)
    }
}

@Composable
private fun FocusSurface(modifier: Modifier = Modifier, onClick: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.045f else 1f)
    val border by animateColorAsState(if (focused) Red else Color.Transparent)
    Box(modifier.graphicsLayer { scaleX = scale; scaleY = scale }.clip(RoundedCornerShape(16.dp)).background(Panel).border(2.dp, border, RoundedCornerShape(16.dp)).onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick), content = content)
}

@Composable
private fun FocusButton(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(modifier.height(50.dp).clip(RoundedCornerShape(13.dp)).background(if (focused || selected) Red else Panel2).border(2.dp, if (focused) Color.White else Color.Transparent, RoundedCornerShape(13.dp)).onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick).padding(horizontal = 22.dp), contentAlignment = Alignment.Center) {
        Label(text, 15.sp, FontWeight.Bold)
    }
}

@Composable
private fun TvInput(label: String, value: String, onValue: (String) -> Unit, modifier: Modifier = Modifier, password: Boolean = false) {
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = value, onValueChange = onValue, singleLine = true,
        textStyle = TextStyle(TextPrimary, 17.sp), cursorBrush = SolidColor(Red),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = modifier.height(58.dp).clip(RoundedCornerShape(13.dp)).background(Panel).border(2.dp, if (focused) Red else Color(0xFF273047), RoundedCornerShape(13.dp)).onFocusChanged { focused = it.isFocused }.padding(horizontal = 18.dp),
        decorationBox = { inner -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) { if (value.isBlank()) Label(label, 16.sp, color = TextSecondary); inner() } }
    )
}

@Composable
private fun Label(text: String, size: androidx.compose.ui.unit.TextUnit, weight: FontWeight = FontWeight.Normal, color: Color = TextPrimary) {
    androidx.compose.foundation.text.BasicText(text, style = TextStyle(color = color, fontSize = size, fontWeight = weight), maxLines = 2, overflow = TextOverflow.Ellipsis)
}
