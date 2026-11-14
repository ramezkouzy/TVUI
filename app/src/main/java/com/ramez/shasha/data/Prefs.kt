package com.ramez.shasha.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class Prefs(context: Context) {
    private val p: SharedPreferences = context.getSharedPreferences("shasha", Context.MODE_PRIVATE)

    var account: Account?
        get() {
            val s = p.getString("server", null) ?: return null
            return Account(s, p.getString("user", "") ?: "", p.getString("pass", "") ?: "")
        }
        set(v) {
            p.edit().apply {
                if (v == null) {
                    remove("server"); remove("user"); remove("pass")
                } else {
                    putString("server", v.server); putString("user", v.username); putString("pass", v.password)
                }
            }.apply()
        }

    var osApiKey: String
        get() = p.getString("os_key", "") ?: ""
        set(v) = p.edit().putString("os_key", v.trim()).apply()

    var osUser: String
        get() = p.getString("os_user", "") ?: ""
        set(v) = p.edit().putString("os_user", v.trim()).apply()

    var osPass: String
        get() = p.getString("os_pass", "") ?: ""
        set(v) = p.edit().putString("os_pass", v).apply()

    var osToken: String
        get() = p.getString("os_token", "") ?: ""
        set(v) = p.edit().putString("os_token", v).apply()

    /** 0 = small, 1 = medium, 2 = large */
    var subtitleSize: Int
        get() = p.getInt("sub_size", 1)
        set(v) = p.edit().putInt("sub_size", v).apply()

    var subtitlesOnByDefault: Boolean
        get() = p.getBoolean("sub_auto", true)
        set(v) = p.edit().putBoolean("sub_auto", v).apply()

    // ---- Resume positions ("Continue watching") ----

    fun resumeList(): List<ResumeEntry> {
        val arr = try { JSONArray(p.getString("resume", "[]")) } catch (e: Exception) { JSONArray() }
        return arr.objects().mapNotNull { fromJson(it) }.sortedByDescending { it.updatedAt }
    }

    fun resumeFor(key: String): ResumeEntry? = resumeList().firstOrNull { it.key == key }

    fun saveResume(entry: ResumeEntry) {
        val list = resumeList().filter { it.key != entry.key }.toMutableList()
        val nearlyDone = entry.durationMs > 0 && entry.positionMs > entry.durationMs * 0.93
        if (!nearlyDone && entry.positionMs > 60_000) list.add(0, entry)
        val arr = JSONArray()
        list.take(30).forEach { arr.put(toJson(it)) }
        p.edit().putString("resume", arr.toString()).apply()
    }

    fun removeResume(key: String) {
        val arr = JSONArray()
        resumeList().filter { it.key != key }.forEach { arr.put(toJson(it)) }
        p.edit().putString("resume", arr.toString()).apply()
    }

    // ---- Subtitle choice per title (remembers timing offset too) ----

    fun subtitleOffset(key: String): Long = p.getLong("off_$key", 0L)
    fun setSubtitleOffset(key: String, ms: Long) = p.edit().putLong("off_$key", ms).apply()

    private fun toJson(e: ResumeEntry) = JSONObject().apply {
        put("key", e.key); put("title", e.title); put("image", e.image)
        put("pos", e.positionMs); put("dur", e.durationMs); put("at", e.updatedAt)
        val r = e.request
        put("r_url", r.url); put("r_title", r.title); put("r_img", r.image); put("r_tmdb", r.tmdbId)
        put("r_name", r.searchName); put("r_year", r.year); put("r_s", r.season); put("r_e", r.episode)
    }

    private fun fromJson(o: JSONObject): ResumeEntry? {
        val key = o.s("key").ifEmpty { return null }
        val req = PlayRequest(
            key = key, url = o.s("r_url"), title = o.s("r_title"), isLive = false,
            image = o.s("r_img"), tmdbId = o.s("r_tmdb"), searchName = o.s("r_name"),
            year = o.s("r_year"), season = o.i("r_s"), episode = o.i("r_e"),
        )
        return ResumeEntry(key, o.s("title"), o.s("image"), o.l("pos"), o.l("dur"), o.l("at"), req)
    }
}
