package ani.dantotsu.util

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.view.View

/**
 * Android TV detection.
 *
 * The app is one build for both phones and TV, so anything that only makes sense with a
 * remote — visible focus highlights, D-pad entry points, dropping touch-only affordances —
 * keys off [isTv] rather than being compiled in separately.
 */
object TvUtils {

    /**
     * True on a television. [UiModeManager] is the authoritative signal; the leanback
     * feature check is a fallback for devices that report a normal UI mode but still ship
     * without a touchscreen (some set-top boxes and sticks).
     */
    fun isTv(context: Context): Boolean {
        val uiMode = (context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager)
            ?.currentModeType
        if (uiMode == Configuration.UI_MODE_TYPE_TELEVISION) return true

        val pm = context.packageManager
        return pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) &&
                !pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
    }

    /**
     * Whether the reading half of the app is offered at all.
     *
     * Manga is a hold-it-in-your-hands feature: page-at-a-time panning, pinch zoom and long
     * reading sessions all assume a device you can touch and hold close. None of that
     * survives a remote and a couch, so on TV the tab, its settings, its extensions and its
     * home rows are all dropped rather than shipped as dead ends.
     */
    fun supportsManga(context: Context): Boolean = !isTv(context)

    /**
     * Make [view] reachable and obviously selected with a D-pad. A remote has no cursor, so
     * the focused item has to announce itself: this lifts and enlarges it, which is the
     * convention TV users already read as "this is what Select will open".
     *
     * No-op off TV, so phone builds keep their existing flat card behaviour.
     */
    fun View.tvFocusable(scale: Float = 1.08f) {
        if (!isTv(context)) return
        isFocusable = true
        isFocusableInTouchMode = false
        // Focused cards must paint over their neighbours, or the enlarged edges get clipped
        // by whatever the list draws next.
        val baseElevation = elevation
        setOnFocusChangeListener { v, hasFocus ->
            v.animate()
                .scaleX(if (hasFocus) scale else 1f)
                .scaleY(if (hasFocus) scale else 1f)
                .setDuration(FOCUS_ANIM_MS)
                .start()
            v.elevation = if (hasFocus) baseElevation + FOCUS_ELEVATION else baseElevation
        }
    }

    private const val FOCUS_ANIM_MS = 150L
    private const val FOCUS_ELEVATION = 12f
}
