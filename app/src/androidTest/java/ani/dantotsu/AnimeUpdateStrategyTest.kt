package ani.dantotsu

import eu.kanade.tachiyomi.animesource.model.AnimeUpdateStrategy
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SAnimeImpl
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import org.junit.Assert.assertEquals
import org.junit.Test

/** Newer anime extensions call setUpdate_strategy(AnimeUpdateStrategy); older ones use UpdateStrategy. */
class AnimeUpdateStrategyTest {

    @Test
    fun acceptsTheAnimeSpecificEnumThroughTheJvmSignatureExtensionsUse() {
        val anime = SAnimeImpl()
        SAnime::class.java.getMethod("setUpdate_strategy", AnimeUpdateStrategy::class.java)
            .invoke(anime, AnimeUpdateStrategy.ONLY_FETCH_ONCE)
        assertEquals(UpdateStrategy.ONLY_FETCH_ONCE, anime.update_strategy)
        anime.update_strategy = UpdateStrategy.ALWAYS_UPDATE
        assertEquals(UpdateStrategy.ALWAYS_UPDATE, anime.update_strategy)
    }
}
