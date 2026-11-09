package com.indoone.settings.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import org.json.JSONArray
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

object AppUpdateManager {
    /**
     * A download that failed, and whether trying again could help.
     *
     * A 504 from the CDN is worth another attempt; a 404 is not. Treating the two the same is
     * how an update either gives up on a bad afternoon or burns a person's data retrying a
     * file that is not there.
     */
    private class DownloadFailure(message: String, val worthRetrying: Boolean) : Exception(message)

    data class UpdateInfo(
        val versionCode: Long,
        val downloadUrl: String,
        val sizeBytes: Long,
    )

    private const val RELEASES_URL =
        "https://api.github.com/repos/Sankalpteam/Sankalp/releases?per_page=20"
    private const val RELEASE_TAG_PREFIX = "terminal-build-"
    private const val APK_ASSET_NAME = "indoone-terminal.apk"
    private const val UPDATE_DIR = "updates"
    private const val PROGRESS_BUFFER_BYTES = 64 * 1024
    private const val PROGRESS_INTERVAL_MS = 250L

    private val httpClient = OkHttpClient()

    /**
     * The APK download gets its own client, pinned to HTTP/1.1.
     *
     * On HTTP/2 one stream is paced by the connection's flow-control window, so the
     * throughput settles at roughly window / round-trip time. That window is 64 KB, so
     * on a mobile link whose round trip is a few hundred milliseconds the download
     * crawls at a couple of hundred KB/s — which is exactly the speed reported on
     * mobile data. Over Wi-Fi the same code looks fast because the round trip there is
     * a few milliseconds, so the same 64 KB refills almost instantly.
     *
     * HTTP/1.1 hands pacing back to TCP window scaling, which the platform sizes far
     * more generously than 64 KB. That is why a plain browser download is fast on the
     * same phone and the same network.
     */
    private val downloadClient = OkHttpClient.Builder()
        .protocols(listOf(Protocol.HTTP_1_1))
        .retryOnConnectionFailure(true)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * One place formats sizes, so the dialog and the notification cannot disagree
     * about how big the download is.
     */
    fun formatMegabytes(bytes: Long): String {
        if (bytes <= 0L) return "0 MB"
        return String.format(Locale.US, "%.1f MB", bytes.toDouble() / (1024.0 * 1024.0))
    }

    fun currentVersionCode(context: Context): Long {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
    }

    fun apkFile(context: Context): File =
        File(File(context.cacheDir, UPDATE_DIR), APK_ASSET_NAME)

    /**
     * True when the system will let us open the package installer. Android only
     * allows installing packages after the user has allowed it for this app.
     */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()

    /**
     * Opens the "install unknown apps" screen. This is only ever called from a
     * button the user pressed: jumping here on its own made the app look like it
     * was closing the moment an update was started.
     */
    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    /**
     * The intent that installs a downloaded APK. When the app is not allowed to
     * install packages yet this is the settings screen instead, so a caller always
     * has something useful to open.
     */
    fun installerIntent(context: Context, apkFile: File): Intent {
        if (!canInstall(context)) {
            return Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile,
        )

        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun installApk(context: Context, apkFile: File) {
        runCatching { context.startActivity(installerIntent(context, apkFile)) }
    }

    suspend fun checkForUpdate(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        val installedVersionCode = currentVersionCode(context)

        val request = Request.Builder()
            .url(RELEASES_URL)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Indoone-Android-Updater")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Update check failed: HTTP ${response.code}")
            }

            val body = response.body?.string().orEmpty()
            val releases = JSONArray(body)

            for (index in 0 until releases.length()) {
                val release = releases.optJSONObject(index) ?: continue
                if (release.optBoolean("draft") || release.optBoolean("prerelease")) continue

                val tag = release.optString("tag_name")
                if (!tag.startsWith(RELEASE_TAG_PREFIX)) continue

                val versionCode = tag.removePrefix(RELEASE_TAG_PREFIX).toLongOrNull() ?: continue
                val assets = release.optJSONArray("assets") ?: continue

                for (assetIndex in 0 until assets.length()) {
                    val asset = assets.optJSONObject(assetIndex) ?: continue
                    if (asset.optString("name") != APK_ASSET_NAME) continue

                    val downloadUrl = asset.optString("browser_download_url")
                    if (versionCode > installedVersionCode && downloadUrl.isNotBlank()) {
                        return@withContext UpdateInfo(
                            versionCode = versionCode,
                            downloadUrl = downloadUrl,
                            sizeBytes = asset.optLong("size"),
                        )
                    }
                }
            }

            null
        }
    }

    /**
     * Downloads the APK into the cache and reports real byte progress.
     *
     * This is called from the worker, not from a screen, so the download belongs to
     * the work rather than to the dialog. Leaving the app does not stop it.
     *
     * Progress is throttled by time rather than by percentage. Each report writes to
     * the work database and updates a notification from the same coroutine that is
     * draining the socket, and every pause lets the receive window close; on a mobile
     * link reopening it costs a full round trip. Reporting on every percent meant
     * around a hundred such pauses per download.
     */
    /**
     * The release page, for when the direct download will not come through.
     *
     * A CDN answering 504 is not something this app can fix, and a person who cannot install
     * an update has no way forward unless the app gives them one.
     */
    const val RELEASE_PAGE_URL = "https://github.com/Sankalpteam/Sankalp/releases/latest"

    /** How many times a failed download is tried again before the person is told. */
    private const val DOWNLOAD_ATTEMPTS = 3

    suspend fun downloadApk(
        context: Context,
        update: UpdateInfo,
        onProgress: suspend (downloadedBytes: Long, totalBytes: Long) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val updateDir = File(context.cacheDir, UPDATE_DIR).apply { mkdirs() }
        val apkFile = File(updateDir, APK_ASSET_NAME)

        var lastFailure: Throwable? = null

        for (attempt in 1..DOWNLOAD_ATTEMPTS) {
            try {
                return@withContext downloadOnce(context, update, apkFile, onProgress)
            } catch (error: DownloadFailure) {
                lastFailure = error

                // A 4xx is an answer, not an accident: retrying a 404 or a 403 only wastes the
                // person's data. A 5xx, or a connection that died mid-file, is worth another try.
                if (!error.worthRetrying) break

                if (attempt < DOWNLOAD_ATTEMPTS) {
                    delay(attempt * 2_000L)
                }
            }
        }

        throw lastFailure ?: error("APK download failed.")
    }

    /**
     * One attempt. A partial file is kept, so the next attempt resumes instead of restarting -
     * but only while those bytes belong to the same build.
     *
     * The cache holds one file name for every release, so a Range request could resume onto
     * the tail of an older APK and produce a file of exactly the right length that no phone
     * can parse: "Parsing failed. The system was unable to parse this APK file." The sidecar
     * is what stops that, and the parser check at the end is what catches anything else.
     */
    private suspend fun downloadOnce(
        context: Context,
        update: UpdateInfo,
        apkFile: File,
        onProgress: suspend (downloadedBytes: Long, totalBytes: Long) -> Unit,
    ): File {
        val sidecar = versionSidecar(apkFile)
        if (recordedVersion(sidecar) != update.versionCode) {
            // Whatever is on disk is another build's bytes, or nobody's. Never resume onto them.
            apkFile.delete()
            sidecar.delete()
        }

        var alreadyHave = if (apkFile.exists()) apkFile.length() else 0L

        // A file that is already the right size is not necessarily the right file: it may be
        // a finished download of an older build, or a corrupt one. Only trust it if the phone
        // itself can read it as the version we asked for.
        if (alreadyHave > 0L && update.sizeBytes > 0L && alreadyHave >= update.sizeBytes) {
            if (isInstallable(context, apkFile, update)) return apkFile

            apkFile.delete()
            sidecar.delete()
            alreadyHave = 0L
        }

        val request = Request.Builder()
            .url(update.downloadUrl)
            .header("User-Agent", "Indoone-Android-Updater")
            .apply { if (alreadyHave > 0L) header("Range", "bytes=$alreadyHave-") }
            .build()

        downloadClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw DownloadFailure(
                    "APK download failed: HTTP ${response.code}",
                    worthRetrying = response.code >= 500,
                )
            }

            val body = response.body ?: throw DownloadFailure("APK download returned no file.", true)

            // 206 means the server honoured the Range and is sending the rest; anything else
            // means it started again, so the old bytes are thrown away rather than prepended.
            val resuming = response.code == 206 && alreadyHave > 0L
            if (!resuming && apkFile.exists()) apkFile.delete()

            val totalBytes = (if (resuming) alreadyHave else 0L) +
                (body.contentLength().takeIf { it > 0L } ?: update.sizeBytes)
            onProgress(if (resuming) alreadyHave else 0L, totalBytes)

            try {
                body.byteStream().use { input ->
                    // Append when resuming: truncating here would throw away the bytes the
                    // Range asked to keep, and the file would end up holding only the tail.
                    java.io.FileOutputStream(apkFile, resuming).use { output ->
                        val buffer = ByteArray(PROGRESS_BUFFER_BYTES)
                        var copied = if (resuming) alreadyHave else 0L
                        var lastReportedAt = 0L

                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break

                            output.write(buffer, 0, read)
                            copied += read

                            // A few reports a second is plenty for the figure the user
                            // reads, and it keeps the read loop moving.
                            val now = System.currentTimeMillis()
                            if (now - lastReportedAt >= PROGRESS_INTERVAL_MS) {
                                lastReportedAt = now
                                onProgress(copied, totalBytes)
                            }
                        }

                        onProgress(copied, totalBytes)
                    }
                }
            } catch (io: java.io.IOException) {
                // The connection died mid-file. The bytes already on disk are kept, so the
                // next attempt asks for the rest instead of starting again.
                throw DownloadFailure(
                    io.message ?: "The download was interrupted.",
                    worthRetrying = true,
                )
            }
        }

        if (!apkFile.exists() || apkFile.length() == 0L) {
            throw DownloadFailure("Downloaded APK is empty.", true)
        }

        // The phone refuses a package it cannot parse, and it says so only after the person
        // has waited through the download and opened the installer. Ask the same parser here.
        if (!isInstallable(context, apkFile, update)) {
            apkFile.delete()
            sidecar.delete()
            throw DownloadFailure(
                "The downloaded update could not be read. Starting the download again.",
                worthRetrying = true,
            )
        }

        runCatching { sidecar.writeText(update.versionCode.toString()) }

        return apkFile
    }

    /** Which build the bytes in the cache belong to, kept beside the file. */
    private fun versionSidecar(apkFile: File): File =
        File(apkFile.parentFile, "${apkFile.name}.version")

    private fun recordedVersion(sidecar: File): Long? =
        runCatching { sidecar.readText().trim().toLongOrNull() }.getOrNull()

    /**
     * Whether the platform can parse this file as an APK of the version we asked for.
     *
     * getPackageArchiveInfo runs the same parser the installer runs, which is the only opinion
     * that counts: a file can be exactly the advertised size and still be unreadable.
     */
    private fun isInstallable(context: Context, apkFile: File, update: UpdateInfo): Boolean {
        if (!apkFile.exists() || apkFile.length() == 0L) return false
        if (update.sizeBytes > 0L && apkFile.length() != update.sizeBytes) return false

        val archive = context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
            ?: return false

        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            archive.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            archive.versionCode.toLong()
        }

        return versionCode == update.versionCode
    }
}
