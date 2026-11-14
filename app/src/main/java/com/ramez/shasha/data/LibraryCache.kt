package com.ramez.shasha.data

import android.content.Context
import java.io.File
import java.security.MessageDigest

/**
 * Keeps the provider's lists (channels, movies, series, categories) on the TV
 * so the app opens instantly; a fresh copy is fetched quietly in the background.
 */
class LibraryCache(context: Context) {
    private val root = File(context.filesDir, "library")

    private fun dir(account: Account): File {
        val id = MessageDigest.getInstance("SHA-1")
            .digest("${account.base}|${account.username}".toByteArray())
            .joinToString("") { "%02x".format(it) }.take(16)
        return File(root, id).apply { mkdirs() }
    }

    fun read(account: Account, action: String): String? =
        File(dir(account), "$action.json").takeIf { it.exists() && it.length() > 2 }?.readText()

    fun write(account: Account, action: String, text: String) {
        val f = File(dir(account), "$action.json")
        val tmp = File(f.parentFile, "$action.json.tmp")
        tmp.writeText(text)
        tmp.renameTo(f)
        File(dir(account), "updated").writeText(System.currentTimeMillis().toString())
    }

    /** When the cached copy was last refreshed, or 0 if never. */
    fun updatedAt(account: Account): Long =
        File(dir(account), "updated").takeIf { it.exists() }?.readText()?.trim()?.toLongOrNull() ?: 0L

    fun clearAll() { root.deleteRecursively() }
}
