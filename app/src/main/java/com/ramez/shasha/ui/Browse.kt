package com.ramez.shasha.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ramez.shasha.AppState
import com.ramez.shasha.Screen
import kotlinx.coroutines.delay

data class GridEntry(
    val key: String,
    val title: String,
    val image: String,
    val subtitle: String = "",
    val progress: Float? = null,
    val onClick: () -> Unit,
)

class Folder(val id: String, val name: String, val count: Int, val entries: () -> List<GridEntry>)

/**
 * Folders on the left, a poster grid on the right (like Live TV).
 * Remembers the folder, scroll position and last poster when you come back.
 */
@Composable
fun FolderBrowser(
    folders: List<Folder>,
    selected: String?,
    onSelect: (String) -> Unit,
    grid: LazyGridState,
    focusIndex: Int,
    onFocusIndex: (Int) -> Unit,
    emptyText: String,
) {
    val current = folders.firstOrNull { it.id == selected } ?: folders.firstOrNull()
    val entries = remember(current?.id, current?.count, folders) { current?.entries?.invoke() ?: emptyList() }
    val folderFocus = remember { FocusRequester() }
    val gridFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(60)
        if (focusIndex >= 0 && focusIndex < entries.size) {
            grid.scrollToItem(focusIndex)
            delay(60)
            gridFocus.tryFocus()
        } else {
            folderFocus.tryFocus()
        }
    }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(36.dp)) {
        LazyColumn(Modifier.width(320.dp), verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(bottom = 60.dp)) {
            items(folders.size, key = { "${folders[it].id}#$it" }) { fi ->
                val f = folders[fi]
                val isSel = f.id == current?.id
                ListRow(
                    selected = isSel,
                    onClick = { onSelect(f.id); onFocusIndex(-1) },
                    modifier = if (isSel) Modifier.focusRequester(folderFocus) else Modifier,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        T(f.name, size = 18.sp, weight = if (isSel) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1, modifier = Modifier.weight(1f))
                        T("${f.count}", size = 15.sp, color = C.muted)
                    }
                }
            }
        }
        Column(Modifier.weight(1f)) {
            if (current != null) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                    T(current.name, size = 26.sp, weight = FontWeight.SemiBold, maxLines = 1)
                    T("${current.count} titles", size = 16.sp, color = C.muted)
                }
            }
            if (entries.isEmpty()) {
                T(emptyText, size = 19.sp, color = C.muted)
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    state = grid,
                    horizontalArrangement = Arrangement.spacedBy(26.dp),
                    verticalArrangement = Arrangement.spacedBy(26.dp),
                    contentPadding = PaddingValues(start = 20.dp, end = 24.dp, top = 22.dp, bottom = 80.dp),
                ) {
                    items(entries.size, key = { "${entries[it].key}#$it" }) { i ->
                        val e = entries[i]
                        MediaCard(
                            image = e.image, title = e.title, subtitle = e.subtitle, progress = e.progress,
                            width = 196.dp, height = 294.dp,
                            focusRequester = if (i == focusIndex) gridFocus else null,
                            onClick = { onFocusIndex(i); e.onClick() },
                        )
                    }
                }
            }
        }
    }

    // New folder: start the grid from the top.
    LaunchedEffect(current?.id) { if (focusIndex < 0) grid.scrollToItem(0) }
}

@Composable
fun MovieFolders(state: AppState) {
    val movies = state.movies
    val resume = remember(state.resumeVersion) { state.prefs.resumeList().filter { it.key.startsWith("movie:") } }
    val favs = remember(movies, state.favorites) { movies.filter { state.isFavorite("movie:${it.id}") } }
    val watched = remember(movies, state.recents) {
        val byId = movies.associateBy { it.id }
        state.recents.filter { it.startsWith("movie:") }.mapNotNull { byId[it.removePrefix("movie:").toIntOrNull()] }
    }
    val folders = remember(movies, state.movieCategories, resume, favs, watched) {
        val byCat = movies.groupBy { it.categoryId }
        fun entry(m: com.ramez.shasha.data.Movie) = GridEntry("m${m.id}", m.name, m.poster, m.year) { state.push(Screen.MovieDetail(m)) }
        buildList {
            if (resume.isNotEmpty()) add(Folder("continue", "Continue watching", resume.size) {
                resume.map { r ->
                    GridEntry(r.key, r.title, r.image, (if (r.durationMs <= 0) "Up next" else "${((r.durationMs - r.positionMs) / 60000).coerceAtLeast(1)} min left"),
                        if (r.durationMs > 0) r.positionMs.toFloat() / r.durationMs else null) { state.push(Screen.Player(r.request)) }
                }
            })
            add(Folder("mylist", "My list", favs.size) { favs.map(::entry) })
            add(Folder("watched", "Recently watched", watched.size) { watched.map(::entry) })
            add(Folder("recent", "Recently added", minOf(200, movies.size)) { movies.sortedByDescending { it.added }.take(200).map(::entry) })
            add(Folder("all", "All movies · A–Z", movies.size) { movies.sortedBy { it.name.lowercase() }.map(::entry) })
            state.movieCategories.forEach { c ->
                val list = byCat[c.id] ?: return@forEach
                add(Folder("c:${c.id}", c.name, list.size) { list.map(::entry) })
            }
        }
    }
    FolderBrowser(
        folders = folders,
        selected = state.movieFolder ?: (if (resume.isNotEmpty()) "continue" else "recent"),
        onSelect = { state.movieFolder = it },
        grid = state.movieGrid,
        focusIndex = state.movieGridFocus,
        onFocusIndex = { state.movieGridFocus = it },
        emptyText = "Nothing here yet.",
    )
}

@Composable
fun SeriesFolders(state: AppState) {
    val all = state.series
    val favs = remember(all, state.favorites) { all.filter { state.isFavorite("series:${it.id}") } }
    val resume = remember(state.resumeVersion) { state.prefs.resumeList().filter { it.key.startsWith("ep:") } }
    val watched = remember(all, state.recents) {
        val byId = all.associateBy { it.id }
        state.recents.filter { it.startsWith("series:") }.mapNotNull { byId[it.removePrefix("series:").toIntOrNull()] }
    }
    val folders = remember(all, state.seriesCategories, favs, resume, watched) {
        val byCat = all.groupBy { it.categoryId }
        fun entry(s: com.ramez.shasha.data.Series) = GridEntry("s${s.id}", s.name, s.cover, s.year) { state.push(Screen.SeriesDetail(s)) }
        buildList {
            if (resume.isNotEmpty()) add(Folder("continue", "Continue watching", resume.size) {
                resume.map { r ->
                    GridEntry(r.key, r.title, r.image, (if (r.durationMs <= 0) "Up next" else "${((r.durationMs - r.positionMs) / 60000).coerceAtLeast(1)} min left"),
                        if (r.durationMs > 0) r.positionMs.toFloat() / r.durationMs else null) { state.push(Screen.Player(r.request)) }
                }
            })
            add(Folder("mylist", "My list", favs.size) { favs.map(::entry) })
            add(Folder("watched", "Recently watched", watched.size) { watched.map(::entry) })
            add(Folder("recent", "Recently updated", minOf(200, all.size)) { all.sortedByDescending { it.lastModified }.take(200).map(::entry) })
            add(Folder("all", "All series · A–Z", all.size) { all.sortedBy { it.name.lowercase() }.map(::entry) })
            state.seriesCategories.forEach { c ->
                val list = byCat[c.id] ?: return@forEach
                add(Folder("c:${c.id}", c.name, list.size) { list.map(::entry) })
            }
        }
    }
    FolderBrowser(
        folders = folders,
        selected = state.seriesFolder ?: (if (resume.isNotEmpty()) "continue" else "recent"),
        onSelect = { state.seriesFolder = it },
        grid = state.seriesGrid,
        focusIndex = state.seriesGridFocus,
        onFocusIndex = { state.seriesGridFocus = it },
        emptyText = "Nothing here yet.",
    )
}
