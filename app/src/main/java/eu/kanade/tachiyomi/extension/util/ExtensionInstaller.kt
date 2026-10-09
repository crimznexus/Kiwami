package eu.kanade.tachiyomi.extension.util

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import ani.dantotsu.R
import ani.dantotsu.media.AddonType
import ani.dantotsu.media.MediaType
import ani.dantotsu.media.Type
import ani.dantotsu.toast
import ani.dantotsu.util.Logger
import com.jakewharton.rxrelay.PublishRelay
import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.extension.InstallStep
import eu.kanade.tachiyomi.extension.installer.Installer
import eu.kanade.tachiyomi.util.storage.getUriCompat
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

/**
 * The installer which installs, updates and uninstalls the extensions.
 *
 * @param context The application context.
 */
class ExtensionInstaller(private val context: Context) {

    /**
     * The currently requested downloads, with the package name (unique id) as key, and our own
     * download id. The system DownloadManager is not used: Samsung and other vendors park its
     * jobs ("restricted due to olaf") for minutes, so downloads run in-process instead.
     */
    private val activeDownloads = hashMapOf<String, Long>()

    private val calls = java.util.concurrent.ConcurrentHashMap<Long, okhttp3.Call>()
    private val files = java.util.concurrent.ConcurrentHashMap<Long, File>()
    private val nextId = java.util.concurrent.atomic.AtomicLong(1)
    private val http: okhttp3.OkHttpClient by lazy { Injekt.get<okhttp3.OkHttpClient>() }

    /**
     * Relay used to notify the installation step of every download.
     */
    private val downloadsRelay = PublishRelay.create<Pair<Long, InstallStep>>()

    private val extensionInstaller = Injekt.get<BasePreferences>().extensionInstaller()

    /**
     * Downloads the given extension APK and installs it, returning an observable of its step in
     * the installation process.
     *
     * @param url The url of the apk.
     * @param pkgName The package name of the extension.
     * @param name The name of the extension.
     * @param type The type of the extension.
     */
    fun <T : Type> downloadAndInstall(
        url: String,
        pkgName: String,
        name: String,
        type: T
    ): Observable<InstallStep> = Observable.defer {
        if (activeDownloads[pkgName] != null) deleteDownload(pkgName)

        val id = nextId.getAndIncrement()
        activeDownloads[pkgName] = id
        val fileName = url.toUri().lastPathSegment ?: "$pkgName.apk"

        val events = downloadsRelay.filter { it.first == id }.map { it.second }
        val start = Observable.fromCallable {
            Thread({ download(id, url, fileName, type) }, "ext-download-$pkgName").start()
        }.flatMap { Observable.empty<InstallStep>() }

        Observable.merge(events, start)
            .takeUntil { it.isCompleted() }
            .observeOn(AndroidSchedulers.mainThread())
            .doOnUnsubscribe { deleteDownload(pkgName) }
    }

    private fun download(id: Long, url: String, fileName: String, type: Type) {
        downloadsRelay.call(id to InstallStep.Pending)
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
        dir.mkdirs()
        val target = File(dir, fileName)
        val partial = File(dir, "$fileName.part")
        try {
            val call = http.newCall(okhttp3.Request.Builder().url(url).build())
            calls[id] = call
            files[id] = partial
            downloadsRelay.call(id to InstallStep.Downloading)
            call.execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                val body = response.body ?: error("Empty response")
                partial.outputStream().use { out -> body.byteStream().copyTo(out, 64 * 1024) }
            }
            if (id !in calls.keys) return // cancelled while downloading
            target.delete()
            if (!partial.renameTo(target)) error("Couldn't move the downloaded APK")
            files[id] = target
            installApk(type, id, target.getUriCompat(context))
        } catch (e: Exception) {
            partial.delete()
            if (id in calls.keys) {
                Logger.log(e)
                downloadsRelay.call(id to InstallStep.Error)
            }
        } finally {
            calls.remove(id)
        }
    }

    /**
     * Starts an intent to install the extension at the given uri.
     *
     * @param uri The uri of the extension to install.
     */
    fun installApk(type: Type, downloadId: Long, uri: Uri) {
        when (val installer = extensionInstaller.get()) {
            BasePreferences.ExtensionInstaller.LEGACY -> {
                val intent = Intent(context, ExtensionInstallActivity::class.java)
                    .setDataAndType(uri, APK_MIME)
                    .putExtra(EXTRA_DOWNLOAD_ID, downloadId)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (type is MediaType) {
                    intent.putExtra(EXTRA_EXTENSION_TYPE, type)
                } else if (type is AddonType) {
                    intent.putExtra(EXTRA_ADDON_TYPE, type)
                }

                context.startActivity(intent)
            }

            else -> {
                val intent =
                    ExtensionInstallService.getIntent(context, type, downloadId, uri, installer)
                try {
                    ContextCompat.startForegroundService(context, intent)
                } catch (e: RuntimeException) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is ForegroundServiceStartNotAllowedException) {
                        toast(context.getString(R.string.error_msg, context.getString(R.string.foreground_service_not_allowed)))
                    } else {
                        toast(context.getString(R.string.error_msg, e.message))
                    }
                    Logger.log(e)
                }
            }
        }
    }

    /**
     * Cancels extension install and remove from download manager and installer.
     */
    fun cancelInstall(pkgName: String) {
        val downloadId = activeDownloads.remove(pkgName) ?: return
        calls.remove(downloadId)?.cancel()
        files.remove(downloadId)?.delete()
        Installer.cancelInstallQueue(context, downloadId)
    }

    /**
     * Starts an intent to uninstall the extension by the given package name.
     *
     * @param pkgName The package name of the extension to uninstall
     */
    fun uninstallApk(pkgName: String) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            Intent(Intent.ACTION_DELETE).setData("package:$pkgName".toUri())
        else
            @Suppress("DEPRECATION")
            Intent(Intent.ACTION_UNINSTALL_PACKAGE, "package:$pkgName".toUri())

        context.startActivity(intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /**
     * Sets the step of the installation of an extension.
     *
     * @param downloadId The id of the download.
     * @param step New install step.
     */
    fun updateInstallStep(downloadId: Long, step: InstallStep) {
        downloadsRelay.call(downloadId to step)
    }

    /**
     * Deletes the download for the given package name.
     *
     * @param pkgName The package name of the download to delete.
     */
    private fun deleteDownload(pkgName: String) {
        val downloadId = activeDownloads.remove(pkgName)
        if (downloadId != null) {
            calls.remove(downloadId)?.cancel()
            files.remove(downloadId)?.delete()
        }
    }

    companion object {
        const val APK_MIME = "application/vnd.android.package-archive"
        const val EXTRA_DOWNLOAD_ID = "ExtensionInstaller.extra.DOWNLOAD_ID"
        const val EXTRA_EXTENSION_TYPE = "ExtensionInstaller.extra.EXTENSION_TYPE"
        const val EXTRA_ADDON_TYPE = "ExtensionInstaller.extra.ADDON_TYPE"
        const val FILE_SCHEME = "file://"
    }
}
