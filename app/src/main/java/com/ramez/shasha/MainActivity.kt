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
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.ramez.shasha.ui.*

class MainActivity : ComponentActivity() {
    private lateinit var state: AppState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLog.install(applicationContext)
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
    val context = androidx.compose.ui.platform.LocalContext.current
    var crash by remember { mutableStateOf(CrashLog.read(context)) }
    if (crash != null) {
        CrashScreen(crash!!) { CrashLog.clear(context); crash = null }
        return
    }
    val screen = state.current

    // Opens from the saved copy instantly; refreshes in the background when it's stale.
    LaunchedEffect(state.xtream) {
        if (state.xtream != null) state.refreshLibrary()
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

@Composable
private fun CrashScreen(report: String, onDismiss: () -> Unit) {
    val ok = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { ok.tryFocus() }
    androidx.compose.foundation.layout.Column(
        Modifier.fillMaxSize().padding(72.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(18.dp),
    ) {
        T("Shasha closed unexpectedly last time", size = 32.sp, weight = FontWeight.Bold)
        T("Take a photo of this screen and send it to Claude so it can be fixed.", size = 20.sp, color = C.muted)
        AppButton("OK, continue", onClick = onDismiss, primary = true, modifier = Modifier.focusRequester(ok))
        T(report.lines().take(22).joinToString("\n"), size = 15.sp, color = C.text2, lineHeight = 21.sp)
    }
}
