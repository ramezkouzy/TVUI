@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.ramez.shasha.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
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
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.ramez.shasha.AppState
import com.ramez.shasha.Screen
import com.ramez.shasha.data.*

@Composable
fun MovieDetailScreen(state: AppState, movie: Movie) {
    var info by remember { mutableStateOf<MovieInfo?>(null) }
    var subStatus by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    val playFocus = remember { FocusRequester() }
    val key = "movie:${movie.id}"
    val resume = remember(state.resumeVersion) { state.prefs.resumeFor(key) }

    LaunchedEffect(movie.id) {
        playFocus.tryFocus()
        info = runCatching { state.xtream?.movieInfo(movie) }.getOrNull()
        val req = state.moviePlayRequest(movie, info)
        subStatus = when {
            state.subtitleCache.get(key) != null -> true to "English subtitles ready · saved on this TV"
            !state.openSubtitles.configured -> false to "Add your OpenSubtitles key in Settings to get English subtitles"
            else -> try {
                val found = state.openSubtitles.search(req)
                if (found.isNotEmpty()) true to "English subtitles found online · ${found.size} option${if (found.size == 1) "" else "s"}"
                else false to "No English subtitles found online for this title"
            } catch (e: Exception) {
                false to "Couldn't check subtitles: ${e.message}"
            }
        }
    }

    fun play(fromStart: Boolean) {
        if (fromStart) { state.prefs.removeResume(key); state.resumeVersion++ }
        state.push(Screen.Player(state.moviePlayRequest(movie, info)))
    }

    val i = info
    Row(Modifier.fillMaxSize()) {
        Column(
            Modifier.width(900.dp).fillMaxHeight().padding(start = 80.dp, end = 48.dp, top = 90.dp, bottom = 60.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            T(movie.name, size = 50.sp, weight = FontWeight.Bold, maxLines = 3, lineHeight = 56.sp)
            val meta = listOfNotNull(
                i?.year?.ifBlank { null } ?: movie.year.ifBlank { null },
                i?.genre?.ifBlank { null },
                i?.duration?.ifBlank { null },
                movie.rating.takeIf { it.isNotBlank() && it != "0" }?.let { "★ $it" },
            )
            if (meta.isNotEmpty()) T(meta.joinToString("  ·  "), size = 19.sp, color = C.text2)
            if (!i?.plot.isNullOrBlank()) T(i!!.plot, size = 20.sp, color = C.text2, maxLines = 5, lineHeight = 30.sp)
            val credits = listOfNotNull(
                i?.director?.ifBlank { null }?.let { "Directed by $it" },
                i?.cast?.ifBlank { null },
            )
            if (credits.isNotEmpty()) T(credits.joinToString("\n"), size = 17.sp, color = C.muted, maxLines = 3, lineHeight = 26.sp)

            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(top = 10.dp)) {
                if (resume != null) {
                    val left = ((resume.durationMs - resume.positionMs) / 60000).coerceAtLeast(1)
                    AppButton("Resume · $left min left", onClick = { play(false) }, primary = true, icon = Icons.Default.PlayArrow, modifier = Modifier.focusRequester(playFocus))
                    AppButton("Start over", onClick = { play(true) })
                } else {
                    AppButton("Play", onClick = { play(false) }, primary = true, icon = Icons.Default.PlayArrow, modifier = Modifier.focusRequester(playFocus))
                }
                FavoriteButton(state, key)
            }

            val s = subStatus
            Row(
                Modifier.padding(top = 8.dp).clip(RoundedCornerShape(18.dp)).background(C.surface).padding(horizontal = 22.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(if (s?.first == true) Icons.Default.Check else Icons.Default.Info, contentDescription = null,
                    tint = if (s?.first == true) C.accent else C.muted, modifier = Modifier.size(24.dp))
                T(s?.second ?: "Checking for English subtitles…", size = 18.sp, weight = FontWeight.Medium)
            }
        }
        Box(Modifier.weight(1f).fillMaxHeight().background(C.placeholder)) {
            val img = i?.backdrop?.ifBlank { null } ?: i?.poster?.ifBlank { null } ?: movie.poster
            if (img.isNotBlank()) AsyncImage(model = img, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
fun SeriesDetailScreen(state: AppState, series: Series) {
    var info by remember { mutableStateOf<SeriesInfo?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var season by remember { mutableStateOf(0) }
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(series.id) {
        try {
            val loaded = state.xtream!!.seriesInfo(series)
            info = loaded
            season = loaded.seasons.keys.firstOrNull() ?: 0
        } catch (e: Exception) {
            error = e.message ?: "Couldn't load episodes"
        }
    }
    LaunchedEffect(info) { if (info != null) firstFocus.tryFocus() }

    val i = info
    Row(Modifier.fillMaxSize()) {
        Column(
            Modifier.width(1060.dp).fillMaxHeight().padding(start = 80.dp, end = 40.dp, top = 70.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            T(series.name, size = 46.sp, weight = FontWeight.Bold, maxLines = 2, lineHeight = 52.sp)
            val meta = listOfNotNull(i?.year?.ifBlank { null } ?: series.year.ifBlank { null }, i?.genre?.ifBlank { null })
            if (meta.isNotEmpty()) T(meta.joinToString("  ·  "), size = 19.sp, color = C.text2)
            val plot = i?.plot ?: series.plot
            if (plot.isNotBlank()) T(plot, size = 19.sp, color = C.text2, maxLines = 3, lineHeight = 28.sp)
            FavoriteButton(state, "series:${series.id}")

            when {
                error != null -> Message("Couldn't load episodes", error!!)
                i == null -> T("Loading episodes…", size = 19.sp, color = C.muted)
                i.seasons.isEmpty() -> T("No episodes available.", size = 19.sp, color = C.muted)
                else -> {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                        items(i.seasons.keys.toList()) { n ->
                            Pill("Season $n", season == n, { season = n })
                        }
                    }
                    val eps = i.seasons[season] ?: emptyList()
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 60.dp)) {
                        items(eps.size, key = { eps[it].id }) { idx ->
                            val e = eps[idx]
                            val req = PlayRequest(
                                key = "ep:${e.id}",
                                url = state.xtream!!.episodeUrl(e),
                                title = "${series.name} · S${e.season} E${e.number}",
                                isLive = false,
                                image = e.image.ifBlank { series.cover },
                                tmdbId = i.tmdbId,
                                searchName = TitleCleaner.clean(series.name),
                                year = i.year,
                                season = e.season,
                                episode = e.number,
                            )
                            val r = state.prefs.resumeFor(req.key)
                            ListRow(
                                selected = false,
                                onClick = { state.push(Screen.Player(req)) },
                                modifier = if (idx == 0) Modifier.focusRequester(firstFocus) else Modifier,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                    T("${e.number}", size = 20.sp, weight = FontWeight.SemiBold, modifier = Modifier.width(40.dp))
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        T(e.title.ifBlank { "Episode ${e.number}" }, size = 19.sp, weight = FontWeight.Medium, maxLines = 1)
                                        if (r != null && r.durationMs > 0) {
                                            T("${((r.durationMs - r.positionMs) / 60000).coerceAtLeast(1)} min left", size = 15.sp, color = C.accent)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxHeight().background(C.placeholder)) {
            val img = i?.backdrop?.ifBlank { null } ?: series.cover
            if (img.isNotBlank()) AsyncImage(model = img, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
fun FavoriteButton(state: AppState, key: String) {
    val fav = state.isFavorite(key)
    AppButton(
        if (fav) "In My list" else "Add to My list",
        onClick = { state.toggleFavorite(key) },
        icon = if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
    )
}
