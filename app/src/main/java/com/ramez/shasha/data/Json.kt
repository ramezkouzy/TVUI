package com.ramez.shasha.data

import org.json.JSONArray
import org.json.JSONObject

// Xtream panels are inconsistent: the same field can be a string, a number,
// null, or missing. These helpers read whatever is there without throwing.

fun JSONObject.s(key: String): String {
    if (!has(key) || isNull(key)) return ""
    return when (val v = opt(key)) {
        is String -> v.trim()
        is Number, is Boolean -> v.toString()
        else -> ""
    }
}

fun JSONObject.i(key: String): Int = s(key).toDoubleOrNull()?.toInt() ?: 0
fun JSONObject.l(key: String): Long = s(key).toDoubleOrNull()?.toLong() ?: 0L
fun JSONObject.d(key: String): Double = s(key).toDoubleOrNull() ?: 0.0

fun JSONObject.obj(key: String): JSONObject? = optJSONObject(key)

fun JSONArray.objects(): List<JSONObject> =
    (0 until length()).mapNotNull { optJSONObject(it) }

/** backdrop_path is sometimes a string, sometimes an array of strings. */
fun JSONObject.firstString(key: String): String {
    if (!has(key) || isNull(key)) return ""
    return when (val v = opt(key)) {
        is String -> v.trim()
        is JSONArray -> if (v.length() > 0) v.optString(0, "").trim() else ""
        else -> ""
    }
}
