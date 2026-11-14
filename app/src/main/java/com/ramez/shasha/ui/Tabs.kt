@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.ramez.shasha.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.ramez.shasha.AppState
import com.ramez.shasha.Screen
import com.ramez.shasha.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------------- Live TV ----------------

private const val FAV = "__favorites__"

@Composable
fun LiveTab(state: AppState) {
    val channels = state.liveChannels
    if (channels.isEmpty()) { Message("No live channels", "Your provider didn't return any channels."); return }

    val cats = remember(channels, state.liveCategories) {
        val used = channels.map { it.categoryId }.toSet()
        listOf(Category("", "All"), Category(FAV, "Favorites")) + state.liveCategories.filter { it.id in used }
    }
    var cat by remember { mutableStateOf("") }
    val shown = remember(cat, channels, state.favorites) {
        when (cat) {
            "" -> channels
            FAV -> channels.filter { state.isFavorite("live:${it.id}") }
            else -> channels.filter { it.categoryId == cat }
        }
    }
    var focused by remember { mutableStateOf<LiveChannel?>(null) }
    var epg by remember { mutableStateOf<List<EpgItem>>(emptyList()) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.tryFocus() }

    LaunchedEffect(focused?.id) {
        epg = emptyList()
        val c = focused ?: return@LaunchedEffect
        delay(350)
        epg = state.xtream?.shortEpg(c.id) ?: emptyList()
    }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(36.dp)) {
        LazyColumn(Modifier.width(250.dp), verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(bottom = 60.dp)) {
            items(cats, key = { "c" + it.id }) { c ->
                ListRow(selected = cat == c.id, onClick = { cat = c.id }) {
                    T(c.name, size = 18.sp, weight = if (cat == c.id) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
                }
            }
        }
        LazyColumn(Modifier.width(620.dp), verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(bottom = 60.dp)) {
            itemsIndexed(shown, key = { _, c -> c.id }) { i, c ->
                ListRow(
                    selected = false,
                    onClick = { state.playLive(shown, i) },
                    onLongClick = { state.toggleFavorite("live:${c.id}") },
                    modifier = Modifier.onFocusGained { focused = c }.then(if (i == 0) Modifier.focusRequester(first) else Modifier),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        Box(Modifier.size(54.dp, 36.dp).clip(RoundedCornerShape(6.dp)).background(C.surface2), contentAlignment = Alignment.Center) {
                            if (c.icon.isNotBlank()) AsyncImage(model = c.icon, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(3.dp))
                        }
                        T(if (c.num > 0) "${c.num}" else "", size = 16.sp, color = C.muted, modifier = Modifier.width(44.dp))
                        T(c.name, size = 19.sp, weight = FontWeight.Medium, maxLines = 1, modifier = Modifier.weight(1f))
                        if (state.isFavorite("live:${c.id}")) Icon(Icons.Default.Favorite, contentDescription = "Favorite", modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
        Column(Modifier.weight(1f).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (cat == FAV && shown.isEmpty()) {
                Message("No favorite channels yet", "Highlight a channel and hold OK to add it here.")
                return@Column
            }
            val c = focused ?: shown.firstOrNull()
            if (c != null && c.icon.isNotBlank()) {
                Box(Modifier.size(160.dp, 100.dp).clip(RoundedCornerShape(16.dp)).background(C.surface), contentAlignment = Alignment.Center) {
                    AsyncImage(model = c.icon, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(14.dp))
                }
            }
            if (c != null) {
                val now = epg.getOrNull(0)
                val next = epg.getOrNull(1)
                T("${c.name} · Now", size = 17.sp, color = C.muted)
                T(now?.title ?: "No guide information", size = 28.sp, weight = FontWeight.Bold, maxLines = 2)
                if (now != null) T("${hm(now.start)} – ${hm(now.end)}", size = 17.sp, color = C.muted)
                if (next != null) {
                    Spacer(Modifier.height(8.dp))
                    T("Up next · ${hm(next.start)}", size = 17.sp, color = C.muted)
                    T(next.title, size = 21.sp, weight = FontWeight.SemiBold, maxLines = 2)
                }
                Spacer(Modifier.height(12.dp))
                T(if (state.isFavorite("live:${c.id}")) "Hold OK to remove from favorites" else "Hold OK to add to favorites", size = 15.sp, color = C.muted)
            }
        }
    }
}

private val hmFormat = SimpleDateFormat("h:mm a", Locale.US)
fun hm(ms: Long): String = if (ms <= 0) "" else hmFormat.format(Date(ms))

@Composable
fun ListRow(selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, onLongClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) C.surface2 else Color.Transparent,
            contentColor = if (selected) C.text else C.text2,
            focusedContainerColor = C.text,
            focusedContentColor = C.onLight,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
    ) {
        Box(Modifier.padding(horizontal = 18.dp, vertical = 13.dp)) {
            content()
        }
    }
}

// ---------------- Search ----------------

@Composable
fun SearchTab(state: AppState) {
    var query by remember { mutableStateOf("") }
    val field = remember { FocusRequester() }
    LaunchedEffect(Unit) { field.tryFocus() }
    val q = query.trim()
    val movies = remember(q, state.movies) { if (q.length < 2) emptyList() else state.movies.filter { it.name.contains(q, true) }.take(40) }
    val series = remember(q, state.series) { if (q.length < 2) emptyList() else state.series.filter { it.name.contains(q, true) }.take(40) }
    val live = remember(q, state.liveChannels) { if (q.length < 2) emptyList() else state.liveChannels.filter { it.name.contains(q, true) }.take(40) }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(32.dp), contentPadding = PaddingValues(bottom = 80.dp)) {
        item { InputField("Search movies, series and channels (English or Arabic)", query, { query = it }, Modifier.width(820.dp), focusRequester = field) }
        if (q.length >= 2 && movies.isEmpty() && series.isEmpty() && live.isEmpty()) item { Message("No matches", "Try a shorter or different spelling.") }
        if (movies.isNotEmpty()) item { PosterRow("Movies", movies) { state.push(Screen.MovieDetail(it)) } }
        if (series.isNotEmpty()) item { SeriesRow("Series", series) { state.push(Screen.SeriesDetail(it)) } }
        if (live.isNotEmpty()) item {
            Column {
                RowTitle("Live channels")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(22.dp), contentPadding = PaddingValues(vertical = 12.dp, horizontal = 4.dp)) {
                    items(live.size) { i ->
                        val c = live[i]
                        MediaCard(image = c.icon, title = c.name, width = 220.dp, height = 124.dp, onClick = { state.playLive(live, i) })
                    }
                }
            }
        }
    }
}

// ---------------- Settings ----------------

@Composable
fun SettingsTab(state: AppState) {
    val p = state.prefs
    var key by remember { mutableStateOf(p.osApiKey) }
    var user by remember { mutableStateOf(p.osUser) }
    var pass by remember { mutableStateOf(p.osPass) }
    var status by remember { mutableStateOf<String?>(null) }
    var size by remember { mutableStateOf(p.subtitleSize) }
    var auto by remember { mutableStateOf(p.subtitlesOnByDefault) }
    val scope = rememberCoroutineScope()

    LazyColumn(Modifier.width(1100.dp), verticalArrangement = Arrangement.spacedBy(22.dp), contentPadding = PaddingValues(bottom = 80.dp)) {
        item { T("Display", size = 28.sp, weight = FontWeight.SemiBold) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                T("Text size", size = 20.sp, weight = FontWeight.Medium, modifier = Modifier.width(260.dp))
                com.ramez.shasha.TEXT_SCALE_LABELS.forEachIndexed { i, label ->
                    Pill(label, state.textScale == i, { state.textScale = i; p.textScale = i })
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                T("Movies & series view", size = 20.sp, weight = FontWeight.Medium, modifier = Modifier.width(260.dp))
                Pill("Folders", state.folderView, { state.folderView = true; p.folderView = true })
                Pill("Rows", !state.folderView, { state.folderView = false; p.folderView = false })
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
        item { T("English subtitles", size = 28.sp, weight = FontWeight.SemiBold) }
        item {
            T("Subtitles come from OpenSubtitles.com. Make a free account there, then go to your profile → API consumers → create one, and paste the API key here (easiest from your phone with the Fire TV app's keyboard). Adding your username and password raises the daily download limit.",
                size = 17.sp, color = C.muted, lineHeight = 26.sp)
        }
        item {
            InputField("OpenSubtitles API key (saves automatically)", key, {
                key = it; p.osApiKey = it; status = if (it.isBlank()) null else "API key saved."
            })
        }
        if (status != null) item { T(status!!, size = 17.sp, color = C.accent) }
        item { InputField("OpenSubtitles username (optional, raises the daily limit)", user, { user = it; p.osUser = it; p.osToken = "" }) }
        item { InputField("OpenSubtitles password (optional)", pass, { pass = it; p.osPass = it; p.osToken = "" }, password = true) }
        if (user.isNotBlank() && pass.isNotBlank()) item {
            AppButton("Sign in to OpenSubtitles", onClick = {
                status = "Signing in…"
                scope.launch {
                    status = try {
                        if (state.openSubtitles.login()) "Signed in to OpenSubtitles." else "Sign-in failed."
                    } catch (e: Exception) { "Sign-in failed: ${e.message}" }
                }
            })
        }
        item { Spacer(Modifier.height(16.dp)); T("Subtitle display", size = 28.sp, weight = FontWeight.SemiBold) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                T("Subtitle size", size = 20.sp, weight = FontWeight.Medium, modifier = Modifier.width(260.dp))
                listOf("Small", "Medium", "Large").forEachIndexed { i, label ->
                    Pill(label, size == i, { size = i; p.subtitleSize = i })
                }
            }
        }
        item {
            var raised by remember { mutableStateOf(p.subtitleRaised) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                T("Subtitle position", size = 20.sp, weight = FontWeight.Medium, modifier = Modifier.width(260.dp))
                Pill("Low (under built-in Arabic)", !raised, { raised = false; p.subtitleRaised = false })
                Pill("Raised", raised, { raised = true; p.subtitleRaised = true })
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                T("Turn on English automatically", size = 20.sp, weight = FontWeight.Medium, modifier = Modifier.width(360.dp))
                Pill("On", auto, { auto = true; p.subtitlesOnByDefault = true })
                Pill("Off", !auto, { auto = false; p.subtitlesOnByDefault = false })
            }
        }
        item { Spacer(Modifier.height(16.dp)); T("Account", size = 28.sp, weight = FontWeight.SemiBold) }
        item {
            val a = p.account
            T(if (a != null) "${a.username} · ${a.base}" else "Not signed in", size = 18.sp, color = C.muted)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                AppButton(if (state.refreshing) "Updating…" else "Refresh channels & movies", onClick = { scope.launch { state.loadLibrary(force = true) } })
                AppButton("Sign out", onClick = { state.signOut() })
            }
        }
        item {
            T("Shasha ${com.ramez.shasha.BuildConfig.VERSION_NAME}", size = 15.sp, color = C.muted)
        }
    }
}
