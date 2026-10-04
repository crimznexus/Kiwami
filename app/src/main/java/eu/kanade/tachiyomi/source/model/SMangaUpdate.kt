package eu.kanade.tachiyomi.source.model

/**
 * Result of [eu.kanade.tachiyomi.source.MangaSource.getMangaUpdate]: the manga's details and its
 * chapters, fetched together. Part of extensions-lib 1.6; those extensions construct it directly.
 */
data class SMangaUpdate(
    val manga: SManga,
    val chapters: List<SChapter>,
)
