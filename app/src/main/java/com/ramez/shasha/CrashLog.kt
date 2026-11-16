package com.ramez.shasha

import android.content.Context
import android.util.Log
import java.io.File
import java.util.Date

/** Saves the details of a crash so the next launch can show what went wrong. */
object CrashLog {
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                File(context.filesDir, "crash.txt").writeText(
                    "Shasha ${BuildConfig.VERSION_NAME} · ${Date()}\n" + Log.getStackTraceString(error)
                )
            } catch (_: Throwable) { }
            previous?.uncaughtException(thread, error)
        }
    }

    fun read(context: Context): String? =
        File(context.filesDir, "crash.txt").takeIf { it.exists() }?.readText()

    fun clear(context: Context) { File(context.filesDir, "crash.txt").delete() }
}
