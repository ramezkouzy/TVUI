@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.ramez.shasha.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import coil.compose.AsyncImage
import com.ramez.shasha.AppState
import com.ramez.shasha.Screen
import com.ramez.shasha.Tab
import com.ramez.shasha.data.*
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(state: AppState, onExit: () -> Unit) {
    val contentFocus = remember { FocusRequester() }

    Column(Modifier.fillMaxSize().padding(start = 64.dp, end = 64.dp, top = 28.dp)) {
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Pill("Live TV", state.tab == Tab.Live, { state.tab = Tab.Live })
                Pill("Movies", state.tab == Tab.Movies, { state.tab = Tab.Movies })
                Pill("Series", state.tab == Tab.Series, { state.tab = Tab.Series })
                Pill("Search", state.tab == Tab.Search, { state.tab = Tab.Search }, icon = Icons.Default.Search)
            }
            Spacer(Modifier.weight(1f))
            Pill("Settings", state.tab == Tab.Settings, { state.tab = Tab.Settings }, icon = Icons.Default.Settings)
        }
        Spacer(Modifier.height(24.dp))

        Box(Modifier.fillMaxSize()) {
            when {
                state.tab == Tab.Settings -> SettingsTab(state)
                state.loading && state.movies.isEmpty() && state.liveChannels.isEmpty() ->
                    Message("Loading your channels and movies…", "Big libraries can take a little while the first time.")
                state.loadError != null && state.movies.isEmpty() && state.liveChannels.isEmpty() ->
                    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        Message("Couldn't load your library", state.loadError ?: "")
                        val scope = rememberCoroutineScope()
                        AppButton("Try again", onClick = { scope.launch { state.loadLibrary() } }, primary = true)
                    }
                else -> when (state.tab) {
                    Tab.Movies -> MoviesTab(state)
                    Tab.Series -> SeriesTab(state)
                    Tab.Live -> LiveTab(state)
                    Tab.Search -> SearchTab(state)
                    Tab.Settings -> {}
                }
            }
        }
    }
}

@Composable
fun Message(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        T(title, size = 28.sp, weight = FontWeight.SemiBold)
        if (body.isNotEmpty()) T(body, size = 19.sp, color = C.muted)
    }
}

@Composable
fun RowTitle(text: String) {
    T(text, size = 24.sp, weight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 14.dp))
}

// ---------------- Movies ----------------

fun AppState.moviePlayRequest(m: Movie, info: MovieInfo? = null): PlayRequest {
    val x = xtream!!
    return PlayRequest(
        key = "movie:${m.id}",
        url = x.movieUrl(m.id, info?.ext ?: m.ext),
        title = m.name,
        isLive = false,
        image = m.poster,
        tmdbId = info?.tmdbId ?: "",
        searchName = TitleCleaner.clean(m.name),
        year = info?.year?.ifEmpty { null } ?: TitleCleaner.year(m.name),
    )
}

@Composable
fun MoviesTab(state: AppState) {
    val movies = state.movies
    val recent = remember(movies) { movies.sortedByDescending { it.added }.take(40) }
    val featured = recent.firstOrNull { it.poster.isNotBlank() } ?: recent.firstOrNull()
    val rows = remember(movies, state.movieCategories) {
        val byCat = movies.groupBy { it.categoryId }
        state.movieCategories.mapNotNull { c -> byCat[c.id]?.takeIf { it.isNotEmpty() }?.let { c to it } }
    }
    val resume = remember(state.resumeVersion) { state.prefs.resumeList() }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(featured?.id) { firstFocus.tryFocus() }

    if (movies.isEmpty()) { Message("No movies yet", "Your provider didn't return any movies."); return }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(36.dp), contentPadding = PaddingValues(bottom = 80.dp)) {
        if (featured != null) item(key = "hero") {
            Hero(
                title = featured.name,
                label = "Recently added",
                image = featured.poster,
                onPlay = { state.push(Screen.Player(state.moviePlayRequest(featured))) },
                onDetails = { state.push(Screen.MovieDetail(featured)) },
                playFocus = firstFocus,
            )
        }
        if (resume.isNotEmpty()) item(key = "continue") {
            Column {
                RowTitle("Continue watching")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(24.dp), contentPadding = PaddingValues(vertical = 12.dp, horizontal = 4.dp)) {
                    items(resume, key = { it.key }) { r ->
                        val left = ((r.durationMs - r.positionMs) / 60000).coerceAtLeast(1)
                        MediaCard(
                            image = r.image, title = r.title, width = 300.dp, height = 169.dp,
                            progress = if (r.durationMs > 0) r.positionMs.toFloat() / r.durationMs else null,
                            subtitle = "$left min left",
                            onClick = { state.push(Screen.Player(r.request)) },
                        )
                    }
                }
            }
        }
        item(key = "recent") {
            PosterRow("Recently added", recent) { state.push(Screen.MovieDetail(it)) }
        }
        rows.forEach { (cat, list) ->
            item(key = "cat:${cat.id}") {
                PosterRow(cat.name, list) { state.push(Screen.MovieDetail(it)) }
            }
        }
    }
}

@Composable
fun PosterRow(title: String, movies: List<Movie>, onClick: (Movie) -> Unit) {
    Column {
        RowTitle(title)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(22.dp), contentPadding = PaddingValues(vertical = 12.dp, horizontal = 4.dp)) {
            items(movies, key = { it.id }) { m ->
                MediaCard(image = m.poster, title = m.name, width = 168.dp, height = 252.dp, onClick = { onClick(m) })
            }
        }
    }
}

@Composable
fun Hero(title: String, label: String, image: String, onPlay: () -> Unit, onDetails: () -> Unit, playFocus: FocusRequester) {
    Box(
        Modifier.fillMaxWidth().height(360.dp).clip(RoundedCornerShape(28.dp)).background(C.placeholder)
    ) {
        if (image.isNotBlank()) {
            AsyncImage(
                model = image, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(560.dp),
                alpha = 0.9f,
            )
        }
        Column(
            Modifier.align(Alignment.BottomStart).padding(48.dp).widthIn(max = 760.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            T(label.uppercase(), size = 15.sp, weight = FontWeight.SemiBold, color = C.text2)
            T(title, size = 46.sp, weight = FontWeight.Bold, maxLines = 2, lineHeight = 50.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(top = 8.dp)) {
                AppButton("Play", onClick = onPlay, primary = true, icon = Icons.Default.PlayArrow, modifier = Modifier.focusRequester(playFocus))
                AppButton("Details", onClick = onDetails)
            }
        }
    }
}

// ---------------- Series ----------------

@Composable
fun SeriesTab(state: AppState) {
    val all = state.series
    val recent = remember(all) { all.sortedByDescending { it.lastModified }.take(40) }
    val rows = remember(all, state.seriesCategories) {
        val byCat = all.groupBy { it.categoryId }
        state.seriesCategories.mapNotNull { c -> byCat[c.id]?.takeIf { it.isNotEmpty() }?.let { c to it } }
    }
    if (all.isEmpty()) { Message("No series yet", "Your provider didn't return any series."); return }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.tryFocus() }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(36.dp), contentPadding = PaddingValues(bottom = 80.dp)) {
        item(key = "recent") {
            SeriesRow("Recently updated", recent, firstFocus = first) { state.push(Screen.SeriesDetail(it)) }
        }
        rows.forEach { (cat, list) ->
            item(key = "cat:${cat.id}") { SeriesRow(cat.name, list) { state.push(Screen.SeriesDetail(it)) } }
        }
    }
}

@Composable
fun SeriesRow(title: String, list: List<Series>, firstFocus: FocusRequester? = null, onClick: (Series) -> Unit) {
    Column {
        RowTitle(title)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(22.dp), contentPadding = PaddingValues(vertical = 12.dp, horizontal = 4.dp)) {
            items(list.size, key = { list[it].id }) { i ->
                val s = list[i]
                MediaCard(
                    image = s.cover, title = s.name, width = 168.dp, height = 252.dp,
                    focusRequester = if (i == 0) firstFocus else null,
                    onClick = { onClick(s) },
                )
            }
        }
    }
}
