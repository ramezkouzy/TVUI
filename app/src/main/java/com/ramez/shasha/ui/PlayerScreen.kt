@file:OptIn(ExperimentalTvMaterial3Api::class)
@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.ramez.shasha.ui

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.*
import com.ramez.shasha.AppState
import com.ramez.shasha.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import androidx.media3.common.C as MC

private data class EmbeddedTrack(val label: String, val group: Tracks.Group)

private sealed class SubSource {
    data object Off : SubSource()
    data class Online(val label: String) : SubSource()
    data class Embedded(val index: Int) : SubSource()
}

@Composable
fun PlayerScreen(state: AppState, request: PlayRequest) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = state.prefs
    var req by remember { mutableStateOf(request) }

    val player = remember {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(Http.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(20_000)
            .setReadTimeoutMs(30_000)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(DefaultDataSource.Factory(context, http)))
            .build().apply {
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(MC.TRACK_TYPE_TEXT, true).build()
                playWhenReady = true
            }
    }

    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(true) }
    var buffering by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var triedHls by remember { mutableStateOf(false) }

    var cues by remember { mutableStateOf<List<Cue>>(emptyList()) }
    var source by remember { mutableStateOf<SubSource>(SubSource.Off) }
    var offset by remember { mutableLongStateOf(0L) }
    var sizeIdx by remember { mutableIntStateOf(prefs.subtitleSize) }
    var lift by remember { mutableIntStateOf(prefs.subtitleLift) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    var results by remember { mutableStateOf<List<SubtitleResult>?>(null) }
    var searching by remember { mutableStateOf(false) }
    var embedded by remember { mutableStateOf<List<EmbeddedTrack>>(emptyList()) }
    var toast by remember { mutableStateOf<String?>(null) }
    var toastAt by remember { mutableLongStateOf(0L) }

    var controlsAt by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var controlsVisible by remember { mutableStateOf(true) }
    var panelOpen by remember { mutableStateOf(false) }
    val rootFocus = remember { FocusRequester() }
    val panelFocus = remember { FocusRequester() }

    fun showToast(msg: String) { toast = msg; toastAt = System.currentTimeMillis() }
    fun showControls() { controlsVisible = true; controlsAt = System.currentTimeMillis() }

    fun saveResume() {
        if (req.isLive) return
        val d = player.duration
        if (d <= 0) return
        prefs.saveResume(ResumeEntry(req.key, req.title, req.image, player.currentPosition, d, System.currentTimeMillis(), req))
        state.resumeVersion++
    }

    fun useText(enabled: Boolean, group: Tracks.Group? = null) {
        val b = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(MC.TRACK_TYPE_TEXT, !enabled)
        if (group != null) b.setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0))
        player.trackSelectionParameters = b.build()
    }

    fun loadSrt(srt: String, label: String) {
        val parsed = Srt.parse(srt)
        if (parsed.isEmpty()) { showToast("That subtitle file was empty"); return }
        useText(false)
        cues = parsed
        source = SubSource.Online(label)
        showToast("English subtitles on")
    }

    fun pickResult(r: SubtitleResult) {
        scope.launch {
            showToast("Downloading subtitles…")
            try {
                val srt = state.openSubtitles.download(r)
                state.subtitleCache.put(req.key, srt)
                loadSrt(srt, r.release)
            } catch (e: Exception) {
                showToast("Couldn't download: ${e.message}")
            }
        }
    }

    fun search(query: String? = null) {
        if (!state.openSubtitles.configured) { showToast("Add your OpenSubtitles key in Settings first"); return }
        searching = true
        scope.launch {
            results = try { state.openSubtitles.search(req, query) } catch (e: Exception) { showToast("Search failed: ${e.message}"); emptyList() }
            searching = false
        }
    }

    fun zap(delta: Int) {
        val list = state.livePlaylist
        if (!req.isLive || list.isEmpty()) return
        val idx = ((req.channelIndex + delta) % list.size + list.size) % list.size
        val c = list[idx]
        val x = state.xtream ?: return
        req = PlayRequest(key = "live:${c.id}", url = x.liveUrl(c), title = c.name, isLive = true, image = c.icon, channelIndex = idx)
        showControls()
    }

    // Load media whenever the URL changes (first open, or channel change).
    LaunchedEffect(req.url) {
        error = null
        triedHls = false
        buffering = true
        val item = MediaItem.Builder().setUri(req.url).apply {
            if (req.url.contains(".m3u8")) setMimeType(MimeTypes.APPLICATION_M3U8)
        }.build()
        player.setMediaItem(item)
        if (!req.isLive) prefs.resumeFor(req.key)?.let { player.seekTo(it.positionMs) }
        player.prepare()
        player.play()
    }

    LaunchedEffect(req.key) { state.recordRecent(req) }

    // Subtitles: use a saved file, else search and pick the best match automatically.
    LaunchedEffect(req.key) {
        cues = emptyList(); source = SubSource.Off; results = null
        if (req.isLive) return@LaunchedEffect
        offset = prefs.subtitleOffset(req.key)
        val cached = state.subtitleCache.get(req.key)
        if (cached != null) { loadSrt(cached, "Saved on this TV"); return@LaunchedEffect }
        if (!state.openSubtitles.configured || !prefs.subtitlesOnByDefault) return@LaunchedEffect
        try {
            val found = state.openSubtitles.search(req)
            results = found
            val best = found.firstOrNull()
            if (best == null) { showToast("No English subtitles found · press ▼ for options"); return@LaunchedEffect }
            val srt = state.openSubtitles.download(best)
            state.subtitleCache.put(req.key, srt)
            loadSrt(srt, best.release)
        } catch (e: Exception) {
            showToast("Subtitles: ${e.message}")
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(s: Int) { buffering = s == Player.STATE_BUFFERING }
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onTracksChanged(tracks: Tracks) {
                embedded = tracks.groups.filter { it.type == MC.TRACK_TYPE_TEXT && it.length > 0 }.map { g ->
                    val f = g.getTrackFormat(0)
                    val lang = f.language?.let { Locale(it).getDisplayLanguage(Locale.ENGLISH) }?.ifBlank { null }
                    EmbeddedTrack(f.label ?: lang ?: "Built-in subtitles", g)
                }
            }
            override fun onPlayerError(e: PlaybackException) {
                // Some providers only serve live channels as HLS; retry once that way.
                if (req.isLive && !triedHls && req.url.endsWith(".ts")) {
                    triedHls = true
                    player.setMediaItem(MediaItem.Builder().setUri(req.url.removeSuffix(".ts") + ".m3u8").setMimeType(MimeTypes.APPLICATION_M3U8).build())
                    player.prepare(); player.play()
                } else {
                    error = "This stream won't play right now (${e.errorCodeName})."
                }
            }
        }
        player.addListener(listener)
        onDispose {
            saveResume()
            player.removeListener(listener)
            player.release()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_STOP) { saveResume(); player.pause() } }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    // Clock: position for the progress bar and subtitles; periodic resume save.
    LaunchedEffect(Unit) {
        var ticks = 0
        while (true) {
            position = player.currentPosition
            duration = player.duration.coerceAtLeast(0L)
            delay(100)
            if (++ticks % 100 == 0) saveResume()
        }
    }
    LaunchedEffect(controlsAt) { delay(4500); if (!panelOpen) controlsVisible = false }
    LaunchedEffect(toastAt) { delay(3500); toast = null }
    LaunchedEffect(panelOpen) {
        if (panelOpen) {
            controlsVisible = false
            if (results == null && !searching && state.openSubtitles.configured) search()
            delay(50); panelFocus.tryFocus()
        } else {
            rootFocus.tryFocus()
        }
    }

    BackHandler(enabled = panelOpen) { panelOpen = false }

    val active = remember(position, cues, offset) {
        val t = position - offset
        cues.firstOrNull { t >= it.start && t <= it.end }
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .focusRequester(rootFocus)
            .focusable()
            .onKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown || panelOpen) return@onKeyEvent false
                when (ev.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.MediaPlayPause, Key.Spacebar -> {
                        if (player.isPlaying) player.pause() else player.play(); showControls(); true
                    }
                    Key.MediaPlay -> { player.play(); showControls(); true }
                    Key.MediaPause -> { player.pause(); showControls(); true }
                    Key.DirectionLeft, Key.MediaRewind -> {
                        if (!req.isLive) player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0))
                        showControls(); true
                    }
                    Key.DirectionRight, Key.MediaFastForward -> {
                        if (!req.isLive) player.seekTo(player.currentPosition + if (ev.key == Key.MediaFastForward) 30_000 else 10_000)
                        showControls(); true
                    }
                    Key.DirectionUp -> { if (req.isLive) zap(-1) else showControls(); true }
                    Key.DirectionDown, Key.Menu -> { if (req.isLive) zap(1) else panelOpen = true; true }
                    else -> false
                }
            }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    this.player = player
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    isFocusable = false
                    isFocusableInTouchMode = false
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Our own subtitle line (for downloaded subtitles; built-in ones draw inside the video view).
        val shownText = active?.text ?: if (panelOpen && cues.isNotEmpty()) "English subtitles will appear here" else null
        if (shownText != null) {
            // One line, low on screen, so it stays clear of subtitles burned into the video.
            // Long lines shrink a little to fit before they're allowed to wrap.
            val line = shownText.replace(Regex("\\s*\\n\\s*"), " ")
            val baseDp = listOf(30f, 38f, 48f)[sizeIdx.coerceIn(0, 2)]
            val maxWidthDp = if (panelOpen) 1180f else 1800f
            val fitsChars = maxWidthDp / (baseDp * 0.5f)
            val shrink = if (line.length > fitsChars) (fitsChars / line.length).coerceAtLeast(0.72f) else 1f
            val fontSize = with(density) { (baseDp * shrink).dp.toSp() }
            val bottom = when {
                controlsVisible -> 190.dp
                else -> (lift * SUB_STEP_DP).dp
            }
            Box(
                Modifier.align(Alignment.BottomCenter)
                    .padding(bottom = bottom, end = if (panelOpen) 600.dp else 0.dp)
                    .widthIn(max = maxWidthDp.dp)
                    .background(Color(0xB3000000), RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Text(line, color = Color.White, fontSize = fontSize, fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center, maxLines = 2, lineHeight = fontSize * 1.2f)
            }
        }

        if (buffering && error == null) {
            T("Loading…", size = 22.sp, weight = FontWeight.Medium, color = C.text2, modifier = Modifier.align(Alignment.Center))
        }
        if (error != null) {
            Column(Modifier.align(Alignment.Center).background(C.surface, RoundedCornerShape(20.dp)).padding(36.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                T(error!!, size = 22.sp, weight = FontWeight.SemiBold)
                T(if (req.isLive) "Press up or down to try another channel, or Back to leave." else "Press Back to go back.", size = 18.sp, color = C.muted)
            }
        }

        if (controlsVisible && !panelOpen) {
            Column(
                Modifier.align(Alignment.BottomStart).fillMaxWidth()
                    .background(Color(0xCC000000))
                    .padding(horizontal = 72.dp, vertical = 40.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T(req.title, size = 26.sp, weight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f))
                    val hint = if (req.isLive) "▲▼ change channel" else "▼ subtitles  ·  ◀▶ skip 10s"
                    T(hint, size = 17.sp, color = C.muted)
                }
                if (!req.isLive && duration > 0) {
                    val frac = (position.toFloat() / duration).coerceIn(0f, 1f)
                    Box(Modifier.fillMaxWidth().height(7.dp).background(Color(0xFF3F3F46), RoundedCornerShape(4.dp))) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(frac).background(C.text, RoundedCornerShape(4.dp)))
                    }
                    Row {
                        T(clock(position), size = 18.sp, color = C.text2)
                        Spacer(Modifier.weight(1f))
                        T("−" + clock(duration - position), size = 18.sp, color = C.text2)
                    }
                } else if (req.isLive) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(10.dp).background(Color(0xFFF87171), RoundedCornerShape(50)))
                        T("LIVE", size = 16.sp, weight = FontWeight.Bold)
                        if (!playing) T("· Paused", size = 16.sp, color = C.muted)
                    }
                }
            }
        }

        if (toast != null) {
            Box(Modifier.align(Alignment.TopEnd).padding(40.dp).background(C.surface, RoundedCornerShape(14.dp)).padding(horizontal = 22.dp, vertical = 14.dp)) {
                T(toast!!, size = 18.sp, weight = FontWeight.Medium)
            }
        }

        if (panelOpen) {
            SubtitlePanel(
                modifier = Modifier.align(Alignment.CenterEnd),
                firstFocus = panelFocus,
                configured = state.openSubtitles.configured,
                source = source,
                embedded = embedded,
                results = results,
                searching = searching,
                defaultQuery = req.searchName,
                offset = offset,
                sizeIdx = sizeIdx,
                lift = lift,
                onLift = { lift = it.coerceIn(0, SUB_MAX_STEPS); prefs.subtitleLift = lift },
                onOff = { useText(false); cues = emptyList(); source = SubSource.Off },
                onEmbedded = { i -> cues = emptyList(); useText(true, embedded[i].group); source = SubSource.Embedded(i) },
                onPick = { pickResult(it) },
                onSearch = { search(it) },
                onOffset = { offset = it; prefs.setSubtitleOffset(req.key, it) },
                onSize = { sizeIdx = it; prefs.subtitleSize = it },
            )
        }
    }
}

private fun clock(ms: Long): String {
    val s = (ms.coerceAtLeast(0) / 1000)
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

@Composable
private fun SubtitlePanel(
    modifier: Modifier,
    firstFocus: FocusRequester,
    configured: Boolean,
    source: SubSource,
    embedded: List<EmbeddedTrack>,
    results: List<SubtitleResult>?,
    searching: Boolean,
    defaultQuery: String,
    offset: Long,
    sizeIdx: Int,
    lift: Int,
    onLift: (Int) -> Unit,
    onOff: () -> Unit,
    onEmbedded: (Int) -> Unit,
    onPick: (SubtitleResult) -> Unit,
    onSearch: (String?) -> Unit,
    onOffset: (Long) -> Unit,
    onSize: (Int) -> Unit,
) {
    var query by remember { mutableStateOf(defaultQuery) }
    LazyColumn(
        modifier.fillMaxHeight().width(600.dp).background(Color(0xF21C1C20)),
        contentPadding = PaddingValues(horizontal = 40.dp, vertical = 56.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { T("Subtitles", size = 32.sp, weight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp)) }
        item { SubtitleHeightRow(lift, onLift, Modifier.padding(bottom = 14.dp)) }

        item {
            OptionRow("Off", "No subtitles", source is SubSource.Off, onOff, Modifier.focusRequester(firstFocus))
        }
        if (source is SubSource.Online) item {
            OptionRow("English", "Loaded · ${source.label}", true, {})
        }
        items(embedded.size) { i ->
            OptionRow(embedded[i].label, "Built into the stream", source == SubSource.Embedded(i), { onEmbedded(i) })
        }

        item { Spacer(Modifier.height(14.dp)); T("Find English online", size = 20.sp, weight = FontWeight.SemiBold, color = C.text2) }
        when {
            !configured -> item { T("Add your OpenSubtitles API key in Settings to search for subtitles.", size = 17.sp, color = C.muted) }
            searching -> item { T("Searching…", size = 17.sp, color = C.muted) }
            results == null -> item { AppButton("Search", onClick = { onSearch(null) }) }
            results.isEmpty() -> item { T("Nothing found. Try searching the English title below.", size = 17.sp, color = C.muted) }
            else -> items(results.take(12).size) { i ->
                val r = results[i]
                val sub = buildString {
                    append("${r.downloads} downloads")
                    if (r.machineTranslated) append(" · machine translated")
                    if (r.hearingImpaired) append(" · SDH")
                }
                OptionRow(r.release.take(60).ifBlank { "English" }, sub, false, { onPick(r) })
            }
        }
        if (configured) item {
            Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                InputField("Search by another name", query, { query = it }, onDone = { onSearch(query) })
                AppButton("Search this name", onClick = { onSearch(query) })
            }
        }

        item {
            Row(Modifier.padding(top = 24.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    T("Timing", size = 20.sp, weight = FontWeight.SemiBold)
                    T("− shows them earlier, + later", size = 15.sp, color = C.muted)
                }
                AppButton("−", onClick = { onOffset(offset - 500) })
                T((if (offset > 0) "+" else if (offset < 0) "−" else "") + "%.1fs".format(Math.abs(offset) / 1000.0),
                    size = 20.sp, weight = FontWeight.SemiBold, modifier = Modifier.width(96.dp), )
                AppButton("+", onClick = { onOffset(offset + 500) })
            }
        }
        item {
            Row(Modifier.padding(top = 12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                T("Size", size = 20.sp, weight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                listOf("S", "M", "L").forEachIndexed { i, l -> Pill(l, sizeIdx == i, { onSize(i) }) }
            }
        }
    }
}

const val SUB_STEP_DP = 15
const val SUB_MAX_STEPS = 30

/** Lower / Higher buttons that move English subtitles live while the video plays. */
@Composable
fun SubtitleHeightRow(lift: Int, onLift: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            T("Height", size = 20.sp, weight = FontWeight.SemiBold)
            T("Moves the English line up or down", size = 15.sp, color = C.muted)
        }
        AppButton("Lower", onClick = { onLift(lift - 1) }, enabled = lift > 0)
        T("$lift", size = 20.sp, weight = FontWeight.SemiBold, modifier = Modifier.width(48.dp))
        AppButton("Higher", onClick = { onLift(lift + 1) }, enabled = lift < SUB_MAX_STEPS)
    }
}

@Composable
private fun OptionRow(title: String, sub: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) C.surface2 else Color.Transparent,
            contentColor = C.text,
            focusedContainerColor = C.text,
            focusedContentColor = C.onLight,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
    ) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                T(title, size = 19.sp, weight = FontWeight.SemiBold, maxLines = 1)
                T(sub, size = 15.sp, maxLines = 1)
            }
            if (selected) Icon(Icons.Default.Check, contentDescription = "Selected", modifier = Modifier.size(24.dp))
        }
    }
}
