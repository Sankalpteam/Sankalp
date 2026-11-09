package com.indoone.settings.diagnostics

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps the last unexpected close so the next launch can show it.
 *
 * The app had no crash reporting and nothing recorded an exception, so when it
 * closed on its own there was no way to see why - not for the person holding the
 * phone and not from the code either. This writes the details to a file and
 * MainActivity shows them once on the next launch.
 */
object CrashLogStore {
    private const val FILE_NAME = "last-crash.txt"
    private const val SHOWN_STACK_LINES = 8

    fun record(context: Context, detail: String) {
        runCatching { file(context).writeText(detail) }
    }

    fun last(context: Context): String? = runCatching {
        val stored = file(context)
        if (stored.exists() && stored.length() > 0L) stored.readText() else null
    }.getOrNull()

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }

    fun buildDetail(context: Context, throwable: Throwable): String = buildString {
        appendLine("Sankalp build: ${buildNumber(context)}")
        appendLine(
            "When: " +
                SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.US).format(Date()),
        )
        appendLine("Phone: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}")
        appendLine()
        append(throwable.stackTraceToString())
    }

    /** The short form shown on screen: enough to identify the fault, not a wall of text. */
    fun summary(detail: String): String =
        detail.lines().take(SHOWN_STACK_LINES).joinToString("\n")

    fun buildNumber(context: Context): String = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
        code.toString()
    }.getOrElse { "unknown" }

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)
}
