package ani.dantotsu.download.manga

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import ani.dantotsu.download.DownloadsManager
import ani.dantotsu.download.DownloadsManager.Companion.compareName
import ani.dantotsu.media.Media
import ani.dantotsu.media.manga.MangaChapter
import ani.dantotsu.parsers.DynamicMangaParser
import ani.dantotsu.settings.saving.PrefManager
import ani.dantotsu.util.Logger
import ani.dantotsu.util.StoragePermissions.Companion.hasDirAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Read-ahead downloading for manga. A per-media "ahead count" (0 = off) defines how many
 * upcoming chapters should always be stored locally; [topUp] enforces it by queueing
 * whatever is missing from that window. The reader calls it on every chapter change, so
 * finishing a chapter pulls the next one in and the buffer slides along with the reader.
 */
object MangaAutoDownloader {

    private fun key(mediaId: Int) = "${mediaId}_auto_download_ahead"

    fun aheadCount(mediaId: Int): Int = PrefManager.getCustomVal(key(mediaId), 0)

    fun setAheadCount(mediaId: Int, count: Int) {
        if (count > 0) PrefManager.setCustomVal(key(mediaId), count)
        else PrefManager.removeCustomVal(key(mediaId))
    }

    fun isDownloaded(
        media: Media,
        chapter: MangaChapter,
        downloadsManager: DownloadsManager
    ): Boolean = downloadsManager.mangaDownloadedTypes.any {
        media.compareName(it.titleName) &&
                (it.chapterName == chapter.title || it.chapterName == chapter.number)
    }

    fun isQueued(media: Media, chapter: MangaChapter): Boolean =
        MangaServiceDataSingleton.downloadQueue.any {
            it.title == media.mainName() && it.chapter == (chapter.title ?: chapter.number)
        }

    // Chapters handed to the service. The queue drops a task once the service picks it up,
    // so without this guard an in-flight chapter would be re-queued by the next top-up and
    // two jobs would write the same folder. Cleared once everything settles (see topUp), so
    // a chapter whose download failed is retried rather than being skipped forever.
    private val enqueued = mutableSetOf<String>()

    /**
     * Ensure the first [aheadCount] chapters of [upcoming] (the not-yet-read chapters in
     * reading order) are downloaded or queued. No-op when auto download is off for this
     * media or the download directory is not accessible.
     */
    suspend fun topUp(
        context: Context,
        media: Media,
        parser: DynamicMangaParser,
        upcoming: List<MangaChapter>,
        downloadsManager: DownloadsManager
    ) {
        val count = aheadCount(media.id)
        if (count <= 0) return
        if (!hasDirAccess(context)) return

        // Nothing pending anywhere means every handover has resolved one way or the other,
        // so the guard can be reset and failed chapters become eligible again.
        if (MangaServiceDataSingleton.downloadQueue.isEmpty() &&
            !MangaServiceDataSingleton.isServiceRunning
        ) enqueued.clear()

        var queued = 0
        upcoming.take(count).forEach { chapter ->
            val key = "${media.id}_${chapter.uniqueNumber()}"
            if (isDownloaded(media, chapter, downloadsManager)) return@forEach
            if (isQueued(media, chapter) || key in enqueued) return@forEach
            if (enqueue(context, media, parser, chapter)) {
                enqueued.add(key)
                queued++
            }
        }
        if (queued > 0)
            Logger.log("MangaAutoDownloader: queued $queued chapter(s) for ${media.mainName()}")
    }

    /** Fetch the page list and hand one chapter to [MangaDownloaderService]. */
    suspend fun enqueue(
        context: Context,
        media: Media,
        parser: DynamicMangaParser,
        chapter: MangaChapter
    ): Boolean {
        val images = try {
            parser.imageList(chapter.sChapter)
        } catch (e: Exception) {
            Logger.log("MangaAutoDownloader: failed to load pages for ${chapter.number}: $e")
            return false
        }
        if (images.isEmpty()) return false

        MangaServiceDataSingleton.downloadQueue.offer(
            MangaDownloaderService.DownloadTask(
                title = media.mainName(),
                chapter = chapter.title ?: chapter.number,
                scanlator = chapter.scanlator ?: "Unknown",
                imageData = images,
                sourceMedia = media,
                retries = 25,
                simultaneousDownloads = 2
            )
        )
        if (!MangaServiceDataSingleton.isServiceRunning) {
            withContext(Dispatchers.Main) {
                ContextCompat.startForegroundService(
                    context, Intent(context, MangaDownloaderService::class.java)
                )
            }
            MangaServiceDataSingleton.isServiceRunning = true
        }
        return true
    }
}
