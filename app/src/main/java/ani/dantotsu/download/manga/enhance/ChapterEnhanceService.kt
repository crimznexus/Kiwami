package ani.dantotsu.download.manga.enhance

import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import ani.dantotsu.R
import ani.dantotsu.download.DownloadsManager.Companion.getSubDirectory
import ani.dantotsu.media.MediaType
import ani.dantotsu.snackString
import ani.dantotsu.util.Logger
import eu.kanade.tachiyomi.data.notification.Notifications.CHANNEL_DOWNLOADER_PROGRESS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.Serializable
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.coroutines.coroutineContext

/**
 * Foreground service that enhances queued downloaded chapters one after another with
 * [ChapterEnhancer], showing progress in a notification that can cancel the work.
 */
class ChapterEnhanceService : Service() {

    /** A downloaded chapter, by its download folder names. */
    data class Request(
        val mediaName: String,
        val titleName: String,
        val chapterName: String,
    ) : Serializable

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var worker: Job? = null
    private var enhancer: PageEnhancer? = null
    private lateinit var notifications: NotificationManagerCompat
    private lateinit var builder: NotificationCompat.Builder
    private var wakeLock: PowerManager.WakeLock? = null

    private val cancelReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            queue.clear()
            worker?.cancel()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notifications = NotificationManagerCompat.from(this)
        val cancel = PendingIntent.getBroadcast(
            this, 0, Intent(ACTION_CANCEL).setPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        builder = NotificationCompat.Builder(this, CHANNEL_DOWNLOADER_PROGRESS).apply {
            setContentTitle(getString(R.string.enhance_notification_title))
            setSmallIcon(R.drawable.ic_download_24)
            setOnlyAlertOnce(true)
            setOngoing(true)
            setProgress(0, 0, true)
            addAction(0, getString(R.string.cancel), cancel)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, builder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, builder.build())
        }
        ContextCompat.registerReceiver(
            this, cancelReceiver, IntentFilter(ACTION_CANCEL), ContextCompat.RECEIVER_NOT_EXPORTED
        )
        // A chapter can take many minutes; keep the CPU running with the screen off.
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Kiwami:PageEnhancer")
            .apply { acquire(4 * 60 * 60 * 1000L) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (worker?.isActive != true) worker = scope.launch { drainQueue() }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        enhancer?.close()
        current = null
        unregisterReceiver(cancelReceiver)
        wakeLock?.takeIf { it.isHeld }?.release()
        super.onDestroy()
    }

    private suspend fun drainQueue() {
        try {
            while (true) {
                val request = queue.poll() ?: break
                current = request
                try {
                    enhanceChapter(request)
                    notifyDone(getString(R.string.enhance_done, request.chapterName))
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    // OutOfMemoryError included: report it and move on to the next chapter.
                    Logger.log("Enhancing ${request.chapterName} failed: $e")
                    notifyDone(getString(R.string.enhance_failed, request.chapterName))
                } finally {
                    current = null
                }
            }
        } finally {
            withContext(Dispatchers.Main + kotlinx.coroutines.NonCancellable) {
                // A chapter queued while the loop was finishing would otherwise be stranded.
                if (queue.isEmpty()) stopSelf() else worker = scope.launch { drainQueue() }
            }
        }
    }

    private suspend fun enhanceChapter(request: Request) {
        val dir = chapterDir(this, request.titleName, request.chapterName)
            ?: throw IllegalStateException("chapter folder not found")
        val chapter = ChapterEnhancer(this)
        if (chapter.isComplete(dir)) return
        val enhancer = enhancer ?: PageEnhancer.create(this).also { enhancer = it }
        Logger.log("Enhancing ${request.chapterName} on ${enhancer.backend}")
        val job = coroutineContext
        var lastShown = -1f
        chapter.enhance(dir, enhancer, onPage = { index, total, fraction ->
            // Notification updates are rate limited by the system; send about ten per page.
            if (fraction == 0f || fraction - lastShown >= 0.1f) {
                lastShown = fraction
                showProgress(request, index, total, fraction, enhancer.backend)
            }
        }, checkCancelled = { job.ensureActive() })
    }

    private fun showProgress(request: Request, page: Int, pages: Int, fraction: Float, backend: String) {
        val total = pages * 100
        builder.setContentText(
            getString(R.string.enhance_progress, request.chapterName, page + 1, pages, backend)
        ).setProgress(total, (page * 100 + fraction * 100).toInt(), false)
        notify(NOTIFICATION_ID, builder)
    }

    private fun notifyDone(text: String) {
        val done = NotificationCompat.Builder(this, CHANNEL_DOWNLOADER_PROGRESS)
            .setContentTitle(getString(R.string.enhance_notification_title))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_circle_check)
            .setAutoCancel(true)
        notify(DONE_NOTIFICATION_ID, done)
    }

    @Suppress("MissingPermission") // Without the permission the notification is just dropped.
    private fun notify(id: Int, builder: NotificationCompat.Builder) {
        try {
            notifications.notify(id, builder.build())
        } catch (e: SecurityException) {
            Logger.log("Enhancer notification not shown: $e")
        }
    }

    companion object {
        private const val ACTION_CANCEL = "app.kiwami.action.CANCEL_ENHANCE"
        private const val NOTIFICATION_ID = 0x4B1E
        private const val DONE_NOTIFICATION_ID = 0x4B1F

        private val queue = ConcurrentLinkedQueue<Request>()

        @Volatile
        private var current: Request? = null

        fun isPending(request: Request) = current == request || request in queue

        fun enqueue(context: Context, request: Request) {
            if (isPending(request)) return
            queue.add(request)
            ContextCompat.startForegroundService(
                context, Intent(context, ChapterEnhanceService::class.java)
            )
            snackString(context.getString(R.string.enhance_queued, request.chapterName))
        }

        private fun chapterDir(context: Context, title: String, chapter: String): DocumentFile? =
            getSubDirectory(context, MediaType.MANGA, false, title, chapter)
                ?.takeIf { it.exists() && it.isDirectory }

        /** Whether every page of this downloaded chapter has been enhanced. */
        suspend fun isEnhanced(context: Context, title: String, chapter: String): Boolean =
            withContext(Dispatchers.IO) {
                chapterDir(context, title, chapter)?.let { ChapterEnhancer(context).isComplete(it) } ?: false
            }
    }
}
