package ani.dantotsu.settings

import ani.dantotsu.connections.anilist.Anilist
import ani.dantotsu.settings.saving.PrefManager
import ani.dantotsu.settings.saving.PrefName

/**
 * Single gate for 18+ material. Nothing in the app should read [Anilist.adult] directly to
 * decide whether adult titles may be shown — ask [isAllowed] instead.
 *
 * Two independent switches must both be on:
 *  - [enabled]: the local opt-in, off until the user accepts the warning in Settings. This
 *    is per-install, so a shared or handed-down device does not inherit someone's choice
 *    just because their AniList account allows it.
 *  - [Anilist.adult]: the account's own `displayAdultContent` option, which also governs
 *    what the API is willing to return.
 */
object AdultContent {

    /** The local opt-in. Setting this to false also clears the adult-only explore filter. */
    var enabled: Boolean
        get() = PrefManager.getVal(PrefName.AdultContentEnabled)
        set(value) {
            PrefManager.setVal(PrefName.AdultContentEnabled, value)
            if (!value) PrefManager.setVal(PrefName.AdultOnly, false)
        }

    /** Whether adult titles may be surfaced anywhere in the UI. */
    val isAllowed: Boolean get() = enabled && Anilist.adult

    /**
     * True when the user opted in locally but their AniList account still has adult content
     * switched off — the case worth explaining, since the toggle otherwise looks broken.
     */
    val blockedByAnilist: Boolean get() = enabled && !Anilist.adult

    /**
     * The explore page's "adult only" filter. Reading it through here means a stale saved
     * value can never turn explore into an 18+ feed while the gate is shut.
     */
    val adultOnlyFilter: Boolean
        get() = isAllowed && PrefManager.getVal(PrefName.AdultOnly)
}
