package com.ramez.shasha.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object Http {
    const val USER_AGENT = "Mozilla/5.0 (Linux; Android 9) Shasha/1.0"
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun get(url: String, headers: Map<String, String> = emptyMap()): String {
        val b = Request.Builder().url(url).header("User-Agent", USER_AGENT)
        headers.forEach { (k, v) -> b.header(k, v) }
        client.newCall(b.build()).execute().use { r ->
            if (!r.isSuccessful) throw Exception("HTTP ${r.code}")
            return r.body?.string() ?: ""
        }
    }
}

class Xtream(val account: Account) {

    private fun api(action: String? = null, extra: Map<String, String> = emptyMap()): String {
        val base = "${account.base}/player_api.php".toHttpUrlOrNull()
            ?: throw Exception("That server address doesn't look right")
        val url = base.newBuilder()
            .addQueryParameter("username", account.username)
            .addQueryParameter("password", account.password)
            .apply {
                if (action != null) addQueryParameter("action", action)
                extra.forEach { (k, v) -> addQueryParameter(k, v) }
            }
            .build()
        return Http.get(url.toString())
    }

    suspend fun login(): Boolean = withContext(Dispatchers.IO) {
        val o = JSONObject(api())
        val info = o.obj("user_info") ?: return@withContext false
        info.i("auth") == 1 || info.s("status").equals("Active", ignoreCase = true)
    }

    /** Raw JSON for a library list; kept as text so it can be cached on disk. */
    suspend fun raw(action: String): String = withContext(Dispatchers.IO) { api(action) }

    suspend fun liveCategories() = parseCategories(raw(ACTION_LIVE_CATS))
    suspend fun movieCategories() = parseCategories(raw(ACTION_MOVIE_CATS))
    suspend fun seriesCategories() = parseCategories(raw(ACTION_SERIES_CATS))
    suspend fun liveChannels() = parseLive(raw(ACTION_LIVE))
    suspend fun movies() = parseMovies(raw(ACTION_MOVIES))
    suspend fun series() = parseSeries(raw(ACTION_SERIES))

    companion object {
        const val ACTION_LIVE_CATS = "get_live_categories"
        const val ACTION_MOVIE_CATS = "get_vod_categories"
        const val ACTION_SERIES_CATS = "get_series_categories"
        const val ACTION_LIVE = "get_live_streams"
        const val ACTION_MOVIES = "get_vod_streams"
        const val ACTION_SERIES = "get_series"
        val ALL = listOf(ACTION_LIVE_CATS, ACTION_LIVE, ACTION_MOVIE_CATS, ACTION_MOVIES, ACTION_SERIES_CATS, ACTION_SERIES)

        private fun array(text: String): JSONArray {
            val t = text.trim()
            return if (t.startsWith("[")) JSONArray(t) else JSONArray()
        }

        fun parseCategories(text: String): List<Category> =
            array(text).objects().map { Category(it.s("category_id"), Names.category(it.s("category_name"))) }

        fun parseLive(text: String): List<LiveChannel> = array(text).objects().map {
            LiveChannel(
                id = it.i("stream_id"),
                num = it.i("num"),
                name = Names.channel(it.s("name")),
                icon = it.s("stream_icon"),
                categoryId = it.s("category_id"),
            )
        }

        fun parseMovies(text: String): List<Movie> = array(text).objects().map {
            val raw = it.s("name")
            Movie(
                id = it.i("stream_id"),
                name = Names.title(raw),
                poster = it.s("stream_icon"),
                categoryId = it.s("category_id"),
                rating = it.s("rating"),
                added = it.l("added"),
                ext = it.s("container_extension").ifEmpty { "mp4" },
                year = it.s("year").take(4).ifEmpty { Names.yearOf(raw) },
            )
        }

        fun parseSeries(text: String): List<Series> = array(text).objects().map {
            val raw = it.s("name")
            Series(
                id = it.i("series_id"),
                name = Names.title(raw),
                cover = it.s("cover"),
                categoryId = it.s("category_id"),
                plot = it.s("plot"),
                year = it.s("year").ifEmpty { it.s("releaseDate").take(4) }.ifEmpty { Names.yearOf(raw) },
                lastModified = it.l("last_modified"),
            )
        }
    }

    suspend fun movieInfo(movie: Movie): MovieInfo = withContext(Dispatchers.IO) {
        val o = JSONObject(api("get_vod_info", mapOf("vod_id" to movie.id.toString())))
        val info = o.obj("info") ?: JSONObject()
        val data = o.obj("movie_data") ?: JSONObject()
        MovieInfo(
            plot = info.s("plot").ifEmpty { info.s("description") },
            cast = info.s("cast").ifEmpty { info.s("actors") },
            director = info.s("director"),
            genre = info.s("genre"),
            duration = info.s("duration"),
            year = info.s("releasedate").ifEmpty { info.s("release_date") }.take(4),
            backdrop = info.firstString("backdrop_path"),
            poster = info.s("movie_image").ifEmpty { movie.poster },
            tmdbId = info.s("tmdb_id").ifEmpty { info.s("tmdb") },
            ext = data.s("container_extension").ifEmpty { movie.ext },
        )
    }

    suspend fun seriesInfo(series: Series): SeriesInfo = withContext(Dispatchers.IO) {
        val o = JSONObject(api("get_series_info", mapOf("series_id" to series.id.toString())))
        val info = o.obj("info") ?: JSONObject()
        val seasons = mutableMapOf<Int, List<Episode>>()
        val eps = o.opt("episodes")
        fun parse(arr: JSONArray?, seasonKey: Int) {
            if (arr == null) return
            val list = arr.objects().map { e ->
                val ei = e.obj("info") ?: JSONObject()
                Episode(
                    id = e.s("id"),
                    title = Names.episode(e.s("title"), series.name, e.i("episode_num")),
                    season = e.i("season").takeIf { it > 0 } ?: seasonKey,
                    number = e.i("episode_num"),
                    ext = e.s("container_extension").ifEmpty { "mp4" },
                    plot = ei.s("plot"),
                    image = ei.s("movie_image"),
                )
            }.sortedBy { it.number }
            if (list.isNotEmpty()) seasons[seasonKey] = list
        }
        if (eps is JSONObject) {
            eps.keys().forEach { k -> parse(eps.optJSONArray(k), k.toIntOrNull() ?: 0) }
        } else if (eps is JSONArray) {
            // Some panels send episodes as an array of arrays.
            for (idx in 0 until eps.length()) parse(eps.optJSONArray(idx), idx + 1)
        }
        SeriesInfo(
            plot = info.s("plot").ifEmpty { series.plot },
            cast = info.s("cast"),
            genre = info.s("genre"),
            year = info.s("releaseDate").take(4).ifEmpty { series.year },
            backdrop = info.firstString("backdrop_path"),
            tmdbId = info.s("tmdb").ifEmpty { info.s("tmdb_id") },
            seasons = seasons.toSortedMap(),
        )
    }

    suspend fun shortEpg(channelId: Int): List<EpgItem> = withContext(Dispatchers.IO) {
        try {
            val o = JSONObject(api("get_short_epg", mapOf("stream_id" to channelId.toString(), "limit" to "2")))
            val arr = o.optJSONArray("epg_listings") ?: return@withContext emptyList()
            arr.objects().map {
                EpgItem(
                    title = decode(it.s("title")),
                    start = it.l("start_timestamp") * 1000,
                    end = it.l("stop_timestamp") * 1000,
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun decode(b64: String): String = try {
        String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8).trim()
    } catch (e: Exception) {
        b64
    }

    fun liveUrl(c: LiveChannel) = "${account.base}/live/${account.username}/${account.password}/${c.id}.ts"
    fun movieUrl(id: Int, ext: String) = "${account.base}/movie/${account.username}/${account.password}/$id.$ext"
    fun episodeUrl(e: Episode) = "${account.base}/series/${account.username}/${account.password}/${e.id}.${e.ext}"
}

/** Strips provider clutter like "AR | ", "[4K]", "(2006)" so a title can be searched. */
object TitleCleaner {
    private val prefix = Regex("^\\s*(\\|?[A-Z]{2,4}\\|?\\s*[-|:]\\s*)+")
    private val brackets = Regex("[\\[(][^\\])]*[\\])]")
    private val yearRx = Regex("(19|20)\\d{2}")
    private val junk = Regex("\\b(4K|UHD|FHD|HD|SD|HEVC|H265|MULTI|SUB|VOSTFR)\\b", RegexOption.IGNORE_CASE)

    fun year(name: String): String = yearRx.findAll(name).lastOrNull()?.value ?: ""

    fun clean(name: String): String {
        var s = name.replace(prefix, "")
        s = s.replace(brackets, " ")
        s = s.replace(yearRx, " ")
        s = s.replace(junk, " ")
        s = s.replace(Regex("[|_.\\-]+"), " ")
        return s.replace(Regex("\\s+"), " ").trim()
    }
}
