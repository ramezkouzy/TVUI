package com.ramez.shasha

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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.ramez.shasha.ui.*

class MainActivity : ComponentActivity() {
    private lateinit var state: AppState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        state = AppHolder.state ?: AppState(applicationContext).also { AppHolder.state = it }
        // TVs report a "density" that makes a 1080p screen only 960 units wide, which
        // doubled every size. Lay out on a fixed 1920-wide canvas instead, so the app
        // looks the same on 720p, 1080p and 4K screens.
        val metrics = resources.displayMetrics
        val canvasDensity = maxOf(metrics.widthPixels, metrics.heightPixels) / 1920f
        setContent {
            val textScale = TEXT_SCALES.getOrElse(state.textScale) { 1.15f }
            CompositionLocalProvider(LocalDensity provides Density(canvasDensity, fontScale = textScale)) {
                ShashaTheme {
                    Box(Modifier.fillMaxSize().background(C.bg)) {
                        App(state, onExit = { finish() })
                    }
                }
            }
        }
    }
}

@Composable
fun App(state: AppState, onExit: () -> Unit) {
    val screen = state.current

    // Opens from the saved copy instantly; refreshes in the background when it's stale.
    LaunchedEffect(state.xtream) {
        if (state.xtream != null) state.loadLibrary()
    }

    BackHandler(enabled = state.stack.size > 1) { state.pop() }

    when (screen) {
        is Screen.Login -> LoginScreen(state)
        is Screen.Home -> HomeScreen(state, onExit)
        is Screen.MovieDetail -> key(screen.movie.id) { MovieDetailScreen(state, screen.movie) }
        is Screen.SeriesDetail -> key(screen.series.id) { SeriesDetailScreen(state, screen.series) }
        is Screen.Player -> key(screen.request.key) { PlayerScreen(state, screen.request) }
    }
}
