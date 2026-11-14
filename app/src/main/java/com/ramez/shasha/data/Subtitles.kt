package com.ramez.shasha.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.nio.charset.Charset

data class Cue(val start: Long, val end: Long, val text: String)

data class SubtitleResult(
    val fileId: Long,
    val release: String,
    val downloads: Int,
    val machineTranslated: Boolean,
    val hearingImpaired: Boolean,
)

/** Parses SRT (and close-enough WebVTT) into cues. */
object Srt {
    private val time = Regex("(\\d{1,2}):(\\d{2}):(\\d{2})[,.](\\d{1,3})")
    private val tags = Regex("<[^>]+>|\\{[^}]*\\}")

    private fun ms(m: MatchResult): Long {
        val (h, mi, s, f) = m.destructured
        val frac = f.padEnd(3, '0').take(3).toLong()
        return h.toLong() * 3_600_000 + mi.toLong() * 60_000 + s.toLong() * 1000 + frac
    }

    fun parse(raw: String): List<Cue> {
        val text = raw.removePrefix("﻿").replace("\r\n", "\n").replace('\r', '\n')
        val cues = mutableListOf<Cue>()
        for (block in text.split(Regex("\n\\s*\n"))) {
            val lines = block.trim().lines()
            val idx = lines.indexOfFirst { it.contains("-->") }
            if (idx < 0) continue
            val times = time.findAll(lines[idx]).toList()
            if (times.size < 2) continue
            val body = lines.drop(idx + 1).joinToString("\n") { it.replace(tags, "").trim() }.trim()
            if (body.isNotEmpty()) cues.add(Cue(ms(times[0]), ms(times[1]), body))
        }
        return cues.sortedBy { it.start }
    }

    /** Subtitle files are often not UTF-8; fall back to Windows-1252 when UTF-8 looks broken. */
    fun decode(bytes: ByteArray): String {
        val utf8 = String(bytes, Charsets.UTF_8)
        return if (utf8.contains('�')) String(bytes, Charset.forName("windows-1252")) else utf8
    }
}

class OpenSubtitles(private val prefs: Prefs) {
    private val base = "https://api.opensubtitles.com/api/v1"
    private val json = "application/json".toMediaType()

    val configured: Boolean get() = prefs.osApiKey.isNotBlank()

    private fun request(url: String): Request.Builder =
        Request.Builder().url(url)
            .header("Api-Key", prefs.osApiKey)
            .header("User-Agent", "Shasha v1.0")
            .header("Accept", "application/json")
            .apply { if (prefs.osToken.isNotBlank()) header("Authorization", "Bearer ${prefs.osToken}") }

    private fun call(r: Request): JSONObject {
        Http.client.newCall(r).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful) {
                val msg = try { JSONObject(body).s("message") } catch (e: Exception) { "" }
                throw Exception(msg.ifEmpty { "Subtitle service error (${resp.code})" })
            }
            return JSONObject(body)
        }
    }

    suspend fun login(): Boolean = withContext(Dispatchers.IO) {
        if (prefs.osUser.isBlank() || prefs.osPass.isBlank()) return@withContext false
        val body = JSONObject().put("username", prefs.osUser).put("password", prefs.osPass)
        val o = call(request("$base/login").post(body.toString().toRequestBody(json)).build())
        prefs.osToken = o.s("token")
        prefs.osToken.isNotBlank()
    }

    suspend fun search(req: PlayRequest, overrideQuery: String? = null): List<SubtitleResult> =
        withContext(Dispatchers.IO) {
            val url = "$base/subtitles".toHttpUrl().newBuilder().addQueryParameter("languages", "en")
            val q = overrideQuery?.trim().orEmpty()
            when {
                q.isNotEmpty() -> url.addQueryParameter("query", q)
                req.tmdbId.isNotBlank() && req.tmdbId != "0" ->
                    if (req.season > 0) url.addQueryParameter("parent_tmdb_id", req.tmdbId)
                    else url.addQueryParameter("tmdb_id", req.tmdbId)
                else -> {
                    url.addQueryParameter("query", req.searchName)
                    if (req.year.isNotBlank() && req.season == 0) url.addQueryParameter("year", req.year)
                }
            }
            if (req.season > 0) {
                url.addQueryParameter("season_number", req.season.toString())
                url.addQueryParameter("episode_number", req.episode.toString())
            }
            val o = call(request(url.build().toString()).get().build())
            val data = o.optJSONArray("data") ?: return@withContext emptyList()
            data.objects().mapNotNull { item ->
                val a = item.obj("attributes") ?: return@mapNotNull null
                val file = a.optJSONArray("files")?.objects()?.firstOrNull() ?: return@mapNotNull null
                SubtitleResult(
                    fileId = file.l("file_id"),
                    release = a.s("release").ifEmpty { file.s("file_name") },
                    downloads = a.i("download_count"),
                    machineTranslated = a.optBoolean("machine_translated") || a.optBoolean("ai_translated"),
                    hearingImpaired = a.optBoolean("hearing_impaired"),
                )
            }.sortedWith(compareBy<SubtitleResult> { it.machineTranslated }.thenByDescending { it.downloads })
        }

    suspend fun download(result: SubtitleResult): String = withContext(Dispatchers.IO) {
        if (prefs.osToken.isBlank() && prefs.osUser.isNotBlank()) runCatching { login() }
        val body = JSONObject().put("file_id", result.fileId).put("sub_format", "srt")
        val o = call(request("$base/download").post(body.toString().toRequestBody(json)).build())
        val link = o.s("link").ifEmpty { throw Exception(o.s("message").ifEmpty { "No download link" }) }
        Http.client.newCall(Request.Builder().url(link).build()).execute().use { r ->
            if (!r.isSuccessful) throw Exception("Download failed (${r.code})")
            Srt.decode(r.body?.bytes() ?: ByteArray(0))
        }
    }
}

/** Keeps downloaded subtitles on the device so each title is only fetched once. */
class SubtitleCache(context: Context) {
    private val dir = File(context.filesDir, "subs").apply { mkdirs() }
    private fun file(key: String) = File(dir, key.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".srt")
    fun get(key: String): String? = file(key).takeIf { it.exists() }?.readText()
    fun put(key: String, srt: String) = file(key).writeText(srt)
    fun clear(key: String) { file(key).delete() }
}
