package eu.kanade.tachiyomi.animesource.model

/**
 * The anime-specific name newer anime extensions use for
 * [eu.kanade.tachiyomi.source.model.UpdateStrategy]. Kiwami stores the manga-side enum; this
 * exists so those extensions find the class they were compiled against.
 */
enum class AnimeUpdateStrategy {
    ALWAYS_UPDATE,
    ONLY_FETCH_ONCE,
}
