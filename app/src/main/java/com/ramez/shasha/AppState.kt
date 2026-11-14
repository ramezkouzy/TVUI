package com.ramez.shasha

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ramez.shasha.data.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

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

    var xtream by mutableStateOf(prefs.account?.let { Xtream(it) })
        private set

    val stack = mutableStateListOf<Screen>(if (prefs.account == null) Screen.Login else Screen.Home)
    val current: Screen get() = stack.last()

    var tab by mutableStateOf(Tab.Movies)

    var loading by mutableStateOf(false)
    var loadError by mutableStateOf<String?>(null)

    var liveCategories by mutableStateOf<List<Category>>(emptyList())
    var liveChannels by mutableStateOf<List<LiveChannel>>(emptyList())
    var movieCategories by mutableStateOf<List<Category>>(emptyList())
    var movies by mutableStateOf<List<Movie>>(emptyList())
    var seriesCategories by mutableStateOf<List<Category>>(emptyList())
    var series by mutableStateOf<List<Series>>(emptyList())

    /** Channels the player can flip through with up/down. */
    var livePlaylist by mutableStateOf<List<LiveChannel>>(emptyList())

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
        prefs.account = null
        xtream = null
        liveChannels = emptyList(); movies = emptyList(); series = emptyList()
        stack.clear(); stack.add(Screen.Login)
    }

    suspend fun loadLibrary() {
        val x = xtream ?: return
        if (loading) return
        loading = true
        loadError = null
        try {
            coroutineScope {
                val lc = async { runCatching { x.liveCategories() }.getOrDefault(emptyList()) }
                val ls = async { runCatching { x.liveChannels() }.getOrDefault(emptyList()) }
                val mc = async { runCatching { x.movieCategories() }.getOrDefault(emptyList()) }
                val ms = async { runCatching { x.movies() }.getOrDefault(emptyList()) }
                val sc = async { runCatching { x.seriesCategories() }.getOrDefault(emptyList()) }
                val ss = async { runCatching { x.series() }.getOrDefault(emptyList()) }
                liveCategories = lc.await(); liveChannels = ls.await()
                movieCategories = mc.await(); movies = ms.await()
                seriesCategories = sc.await(); series = ss.await()
            }
            if (liveChannels.isEmpty() && movies.isEmpty() && series.isEmpty()) {
                loadError = "Nothing came back from the server. Try again, or check your account in Settings."
            }
        } catch (e: Exception) {
            loadError = e.message ?: "Couldn't load your channels"
        } finally {
            loading = false
        }
    }

    fun playLive(channels: List<LiveChannel>, index: Int) {
        val x = xtream ?: return
        livePlaylist = channels
        val c = channels[index]
        push(Screen.Player(PlayRequest(key = "live:${c.id}", url = x.liveUrl(c), title = c.name, isLive = true, image = c.icon, channelIndex = index)))
    }
}
