package ani.dantotsu.others

import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.fragment.app.FragmentActivity
import ani.dantotsu.BuildConfig
import ani.dantotsu.R
import ani.dantotsu.buildMarkwon
import ani.dantotsu.client
import ani.dantotsu.logError
import ani.dantotsu.openLinkInBrowser
import ani.dantotsu.settings.saving.PrefManager
import ani.dantotsu.snackString
import ani.dantotsu.toast
import ani.dantotsu.tryWithSuspend
import ani.dantotsu.util.Logger
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale

/** Checks Kiwami's GitHub releases and, on request, downloads and installs the newer APK. */
object AppUpdater {

    suspend fun check(activity: FragmentActivity, post: Boolean = false) {
        if (post) snackString(activity.getString(R.string.checking_for_update))
        val repo = activity.getString(R.string.repo)
        tryWithSuspend {
            val release = try {
                client.get("https://api.github.com/repos/$repo/releases/latest")
                    .parsed<GithubResponse>()
            } catch (e: Exception) {
                Logger.log("Update check failed: ${e.message}")
                if (post) snackString(activity.getString(R.string.no_update_found))
                return@tryWithSuspend
            }
            val version = release.tagName.removePrefix("v")
            val dontShow = PrefManager.getCustomVal("dont_ask_for_update_$version", false)
            if (!isNewer(version, BuildConfig.VERSION_NAME) || (dontShow && !post) || activity.isDestroyed) {
                if (post) snackString(activity.getString(R.string.no_update_found))
                return@tryWithSuspend
            }
            activity.runOnUiThread {
                CustomBottomDialog.newInstance().apply {
                    setTitleText("Update ${activity.getString(R.string.available)}: $version")
                    addView(TextView(activity).apply {
                        val markdown = try {
                            buildMarkwon(activity, false)
                        } catch (e: IllegalArgumentException) {
                            return@runOnUiThread
                        }
                        markdown.setMarkdown(this, release.body ?: "")
                    })
                    setCheck(activity.getString(R.string.dont_show_again, version), false) {
                        if (it) PrefManager.setCustomVal("dont_ask_for_update_$version", true)
                    }
                    setPositiveButton(activity.getString(R.string.lets_go)) {
                        val apk = pickApk(release.assets.orEmpty().map { a -> a.browserDownloadURL })
                        if (apk != null) activity.downloadUpdate(version, apk)
                        else openLinkInBrowser(release.htmlUrl)
                        dismiss()
                    }
                    setNegativeButton(activity.getString(R.string.cope)) { dismiss() }
                    show(activity.supportFragmentManager, "dialog")
                }
            }
        }
    }

    /** True when [latest] is a higher x.y.z than [current]; build suffixes like "-fdroid" are ignored. */
    internal fun isNewer(latest: String, current: String): Boolean {
        fun parts(v: String) = v.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val a = parts(latest)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** The APK built for this phone's CPU, else the universal one. */
    internal fun pickApk(urls: List<String>, abis: Array<String> = Build.SUPPORTED_ABIS): String? {
        val apks = urls.filter { it.endsWith(".apk") }
        for (abi in abis) apks.firstOrNull { it.contains(abi) }?.let { return it }
        return apks.firstOrNull { it.contains("universal") } ?: apks.firstOrNull()
    }

    private fun Activity.downloadUpdate(version: String, url: String) {
        toast(getString(R.string.downloading_update, version))
        val downloadManager = getSystemService<DownloadManager>()!!
        val request = DownloadManager.Request(Uri.parse(url))
            .setMimeType("application/vnd.android.package-archive")
            .setTitle("Downloading Kiwami $version")
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Kiwami $version.apk")
            .setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
            .setAllowedOverRoaming(true)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        val id = try {
            downloadManager.enqueue(request)
        } catch (e: Exception) {
            logError(e)
            openLinkInBrowser(url)
            return
        }
        ContextCompat.registerReceiver(
            this,
            object : BroadcastReceiver() {
                @SuppressLint("Range")
                override fun onReceive(context: Context?, intent: Intent?) {
                    val done = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) ?: -1
                    if (done != id) return
                    try {
                        downloadManager.getUriForDownloadedFile(id)?.let { openApk(this@downloadUpdate, it) }
                    } catch (e: Exception) {
                        logError(e)
                    }
                }
            },
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    private fun openApk(context: Context, uri: Uri) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                setDataAndType(uri, "application/vnd.android.package-archive")
            })
        } catch (e: Exception) {
            logError(e)
        }
    }

    @Serializable
    data class GithubResponse(
        @SerialName("html_url")
        val htmlUrl: String,
        @SerialName("tag_name")
        val tagName: String,
        val prerelease: Boolean,
        @SerialName("created_at")
        val createdAt: String,
        val body: String? = null,
        val assets: List<Asset>? = null
    ) {
        @Serializable
        data class Asset(
            @SerialName("browser_download_url")
            val browserDownloadURL: String
        )

        fun timeStamp(): Long {
            return dateFormat.parse(createdAt)!!.time
        }

        companion object {
            private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
        }
    }
}
