package com.ramez.shasha

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ramez.shasha.data.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

val TEXT_SCALES = listOf(1.0f, 1.15f, 1.3f, 1.5f)
val TEXT_SCALE_LABELS = listOf("Small", "Medium", "Large", "Extra large")

private const val REFRESH_AFTER_MS = 6 * 60 * 60 * 1000L

/** Survives the screen being rebuilt when you leave the app and come back. */
object AppHolder {
    var state: AppState? = null
}

sealed class Screen {
    data object Login : Screen()
    data object Home : Screen()
    data class MovieDetail(val movie: Movie) : Screen()
    data class SeriesDetail(val series: Series) : Screen()
    data class Player(val request: PlayRequest) : Screen()
}

enum class Tab(val label: String) { Live("Live TV"), Movies("Movies"), Series("Series"), Search("Search"), Settings("Settings") }

class AppState(context: Context) {
    val prefs = Prefs(context)
    val subtitleCache = SubtitleCache(context)
    val openSubtitles = OpenSubtitles(prefs)
    val libraryCache = LibraryCache(context)

    var xtream by mutableStateOf(prefs.account?.let { Xtream(it) })
        private set

    val stack = mutableStateListOf<Screen>(if (prefs.account == null) Screen.Login else Screen.Home)
    val current: Screen get() = stack.last()

    var tab by mutableStateOf(Tab.Movies)

    var loading by mutableStateOf(false)
    var refreshing by mutableStateOf(false)
    var loadError by mutableStateOf<String?>(null)

    var liveCategories by mutableStateOf<List<Category>>(emptyList())
    var liveChannels by mutableStateOf<List<LiveChannel>>(emptyList())
    var movieCategories by mutableStateOf<List<Category>>(emptyList())
    var movies by mutableStateOf<List<Movie>>(emptyList())
    var seriesCategories by mutableStateOf<List<Category>>(emptyList())
    var series by mutableStateOf<List<Series>>(emptyList())

    /** Channels the player can flip through with up/down. */
    var livePlaylist by mutableStateOf<List<LiveChannel>>(emptyList())

    var favorites by mutableStateOf(prefs.favorites)
        private set

    fun isFavorite(key: String) = key in favorites
    fun toggleFavorite(key: String) {
        favorites = if (key in favorites) favorites - key else favorites + key
        prefs.favorites = favorites
    }

    var recents by mutableStateOf(prefs.recents())
        private set

    /** Movies and channels are remembered directly; an episode remembers its show. */
    fun recordRecent(r: PlayRequest) {
        val key = when {
            r.isLive -> r.key
            r.key.startsWith("ep:") -> if (r.seriesId > 0) "series:${r.seriesId}" else return
            else -> r.key
        }
        recents = prefs.addRecent(key)
    }

    var textScale by mutableStateOf(prefs.textScale)
    var folderView by mutableStateOf(prefs.folderView)

    // Folder view memory, so coming back from a movie lands where you left off.
    var movieFolder by mutableStateOf<String?>(null)
    var seriesFolder by mutableStateOf<String?>(null)
    val movieGrid = androidx.compose.foundation.lazy.grid.LazyGridState()
    val seriesGrid = androidx.compose.foundation.lazy.grid.LazyGridState()
    var movieGridFocus by mutableStateOf(-1)
    var seriesGridFocus by mutableStateOf(-1)

    /** Bumped whenever resume positions change so rows refresh. */
    var resumeVersion by mutableStateOf(0)

    fun push(s: Screen) { stack.add(s) }
    fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }

    suspend fun signIn(account: Account): String? {
        val x = Xtream(account)
        return try {
            if (!x.login()) "Sign-in failed. Check the username and password."
            else {
                prefs.account = account
                xtream = x
                stack.clear(); stack.add(Screen.Home)
                null
            }
        } catch (e: Exception) {
            "Couldn't reach the server: ${e.message ?: "unknown error"}"
        }
    }

    fun signOut() {
        loadJob?.cancel()
        prefs.account = null
        xtream = null
        libraryCache.clearAll()
        liveChannels = emptyList(); movies = emptyList(); series = emptyList()
        stack.clear(); stack.add(Screen.Login)
    }

    /**
     * Shows the saved copy immediately, then refreshes from the provider in the
     * background when the copy is older than a few hours (or when forced).
     */
    // Loading runs on the app's own scope, not a screen's, so leaving the app or the
    // screen being rebuilt can't cut it off halfway and leave it "stuck loading".
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var loadJob: Job? = null

    fun refreshLibrary(force: Boolean = false) {
        if (loadJob?.isActive == true) {
            if (!force) return
            loadJob?.cancel()
        }
        loadJob = appScope.launch {
            try {
                loadLibrary(force)
            } finally {
                loading = false
                refreshing = false
            }
        }
    }

    private suspend fun loadLibrary(force: Boolean) {
        val x = xtream ?: return
        val account = x.account

        if (movies.isEmpty() && liveChannels.isEmpty() && series.isEmpty()) {
            loading = true
            try {
                val cached = withContext(Dispatchers.IO) {
                    runCatching { parseAll { action -> libraryCache.read(account, action) } }.getOrNull()
                }
                if (cached != null) apply(cached)
            } finally {
                loading = false
            }
        }

        val age = System.currentTimeMillis() - libraryCache.updatedAt(account)
        val hasData = movies.isNotEmpty() || liveChannels.isNotEmpty() || series.isNotEmpty()
        if (!force && hasData && age < REFRESH_AFTER_MS) return

        if (hasData) refreshing = true else loading = true
        loadError = null
        try {
            // Each list is saved as soon as it arrives (keeps memory low on the Fire Stick).
            // Only lists that actually came back are kept, so a hiccup never wipes the library.
            withContext(Dispatchers.IO) {
                coroutineScope {
                    Xtream.ALL.map { action ->
                        async {
                            val text = runCatching { x.raw(action) }.getOrNull()
                            if (text != null) {
                                val t = text.trimStart()
                                if (t.startsWith("[") && t.length > 2) libraryCache.write(account, action, text)
                            }
                        }
                    }.forEach { it.await() }
                }
            }
            val parsed = withContext(Dispatchers.IO) {
                parseAll { action -> libraryCache.read(account, action) }
            }
            apply(parsed)
            if (movies.isEmpty() && liveChannels.isEmpty() && series.isEmpty()) {
                loadError = "Nothing came back from the server. Try again, or check your account in Settings."
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (!hasData) loadError = e.message ?: "Couldn't load your channels"
        } finally {
            loading = false
            refreshing = false
        }
    }

    private class Library(
        val liveCats: List<Category>, val live: List<LiveChannel>,
        val movieCats: List<Category>, val movies: List<Movie>,
        val seriesCats: List<Category>, val series: List<Series>,
    )

    private fun parseAll(read: (String) -> String?): Library {
        fun <T> p(action: String, f: (String) -> List<T>): List<T> = read(action)?.let { runCatching { f(it) }.getOrNull() } ?: emptyList()
        return Library(
            p(Xtream.ACTION_LIVE_CATS, { Xtream.parseCategories(it) }), p(Xtream.ACTION_LIVE, { Xtream.parseLive(it) }),
            p(Xtream.ACTION_MOVIE_CATS, { Xtream.parseCategories(it) }), p(Xtream.ACTION_MOVIES, { Xtream.parseMovies(it) }),
            p(Xtream.ACTION_SERIES_CATS, { Xtream.parseCategories(it) }), p(Xtream.ACTION_SERIES, { Xtream.parseSeries(it) }),
        )
    }

    private fun apply(l: Library) {
        if (l.live.isNotEmpty()) { liveCategories = l.liveCats; liveChannels = l.live }
        if (l.movies.isNotEmpty()) { movieCategories = l.movieCats; movies = l.movies }
        if (l.series.isNotEmpty()) { seriesCategories = l.seriesCats; series = l.series }
    }

    fun playLive(channels: List<LiveChannel>, index: Int) {
        val x = xtream ?: return
        livePlaylist = channels
        val c = channels[index]
        push(Screen.Player(PlayRequest(key = "live:${c.id}", url = x.liveUrl(c), title = c.name, isLive = true, image = c.icon, channelIndex = index)))
    }
}
