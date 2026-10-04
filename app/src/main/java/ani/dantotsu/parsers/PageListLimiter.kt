package ani.dantotsu.parsers

import ani.dantotsu.util.Logger
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * Paces page-list requests so batch downloads don't trip a source's rate limit.
 *
 * Queuing "next 25" used to fire every chapter's page-list request at once, and sources
 * that cap chapter requests answered most of them with an error body such as
 * `{"error":"Too many chapter requests...","retryAfter":60}`. The extension then failed
 * parsing that as a page list and the raw JSON exception reached the user.
 *
 * Background fetches ([forDownload]) now run one at a time per source with a minimum gap,
 * and wait out a rate limit before retrying. Interactive reading ([forReading]) skips the
 * queue so opening a chapter never waits behind downloads; it only translates a rate
 * limit into [RateLimitedException] so the UI can say something readable.
 */
object PageListLimiter {
    private const val MIN_GAP_MS = 1_500L
    private const val MAX_RETRIES = 3
    private const val DEFAULT_RETRY_AFTER_S = 60
    private const val MAX_RETRY_AFTER_S = 120

    private val locks = ConcurrentHashMap<Long, Mutex>()
    private val lastRequestAt = ConcurrentHashMap<Long, Long>()

    class RateLimitedException(val sourceName: String, val retryAfterSeconds: Int, cause: Throwable) :
        Exception("$sourceName is limiting requests. Wait about $retryAfterSeconds seconds and try again.", cause)

    suspend fun forReading(source: HttpSource, chapter: SChapter): List<Page> {
        lastRequestAt[source.id] = System.currentTimeMillis()
        return try {
            source.getPageList(chapter)
        } catch (e: Exception) {
            val retryAfter = retryAfterSeconds(e) ?: throw e
            throw RateLimitedException(source.name, retryAfter, e)
        }
    }

    suspend fun forDownload(source: HttpSource, chapter: SChapter): List<Page> =
        locks.getOrPut(source.id) { Mutex() }.withLock { fetchPaced(source, chapter) }

    private suspend fun fetchPaced(source: HttpSource, chapter: SChapter): List<Page> {
        var attempt = 0
        while (true) {
            val wait = (lastRequestAt[source.id] ?: 0L) + MIN_GAP_MS - System.currentTimeMillis()
            if (wait > 0) delay(wait)
            lastRequestAt[source.id] = System.currentTimeMillis()
            try {
                return source.getPageList(chapter)
            } catch (e: Exception) {
                val retryAfter = retryAfterSeconds(e) ?: throw e
                if (++attempt > MAX_RETRIES) throw RateLimitedException(source.name, retryAfter, e)
                Logger.log("${source.name} rate-limited page list; retrying in ${retryAfter}s ($attempt/$MAX_RETRIES)")
                delay(retryAfter * 1_000L)
            }
        }
    }

    /**
     * Seconds to wait if [e] is a rate-limit response, else null. Extensions surface it
     * either as an HTTP 429 or, when the server sends an error body with status 200, as a
     * parse failure that quotes that body, so both shapes are recognised.
     */
    private fun retryAfterSeconds(e: Throwable): Int? {
        val text = generateSequence(e) { it.cause }.mapNotNull { it.message }.joinToString(" ")
        val limited = Regex("""\b429\b""").containsMatchIn(text) ||
                text.contains("too many", ignoreCase = true) ||
                text.contains("rate limit", ignoreCase = true) ||
                text.contains("retryAfter", ignoreCase = true)
        if (!limited) return null
        val seconds = Regex(""""?retry[_-]?after"?\s*[:=]\s*(\d+)""", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1)?.toIntOrNull() ?: DEFAULT_RETRY_AFTER_S
        return seconds.coerceIn(1, MAX_RETRY_AFTER_S)
    }
}
