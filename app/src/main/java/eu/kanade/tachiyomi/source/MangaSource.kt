package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.util.lang.awaitSingle
import rx.Observable

/**
 * A basic interface for creating a source. It could be an online source, a local source, etc.
 */
interface MangaSource {

    /**
     * ID for the source. Must be unique.
     */
    val id: Long

    /**
     * Name of the source.
     */
    val name: String

    val lang: String
        get() = ""

    /**
     * Returns an observable with the updated details for a manga.
     *
     * @param manga the manga to update.
     */
    @Deprecated(
        "Use the 1.x API instead",
        ReplaceWith("getMangaDetails"),
    )
    fun fetchMangaDetails(manga: SManga): Observable<SManga> =
        throw IllegalStateException("Not used")

    /**
     * Returns an observable with all the available chapters for a manga.
     *
     * @param manga the manga to update.
     */
    @Deprecated(
        "Use the 1.x API instead",
        ReplaceWith("getChapterList"),
    )
    fun fetchChapterList(manga: SManga): Observable<List<SChapter>> =
        throw IllegalStateException("Not used")

    /**
     * Returns an observable with the list of pages a chapter has. Pages should be returned
     * in the expected order; the index is ignored.
     *
     * @param chapter the chapter.
     */
    @Deprecated(
        "Use the 1.x API instead",
        ReplaceWith("getPageList"),
    )
    fun fetchPageList(chapter: SChapter): Observable<List<Page>> = Observable.empty()

    /**
     * [1.x API] Get the updated details for a manga.
     */
    @Suppress("DEPRECATION")
    suspend fun getMangaDetails(manga: SManga): SManga {
        return fetchMangaDetails(manga).awaitSingle()
    }

    /**
     * [1.x API] Get all the available chapters for a manga.
     */
    @Suppress("DEPRECATION")
    suspend fun getChapterList(manga: SManga): List<SChapter> {
        return fetchChapterList(manga).awaitSingle()
    }

    /**
     * [1.x API] Get the list of pages a chapter has. Pages should be returned
     * in the expected order; the index is ignored.
     */
    @Suppress("DEPRECATION")
    suspend fun getPageList(chapter: SChapter): List<Page> {
        return fetchPageList(chapter).awaitSingle()
    }

    /**
     * [lib 1.6 API] Get a manga's details and/or chapters in one call. Lib 1.6 extensions
     * implement only this (not [getMangaDetails] / [getChapterList]), so callers must use it;
     * the default keeps older extensions working through the per-part methods.
     *
     * @param chapters the chapters already known, returned as-is when [fetchChapters] is false.
     */
    suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val details = if (fetchDetails) getMangaDetails(manga) else manga
        val chapterList = if (fetchChapters) getChapterList(manga) else chapters
        return SMangaUpdate(details, chapterList)
    }
}
