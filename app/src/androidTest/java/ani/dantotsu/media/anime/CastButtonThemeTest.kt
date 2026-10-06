package ani.dantotsu.media.anime

import android.content.res.Configuration
import android.view.ContextThemeWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ani.dantotsu.R
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The player's cast button (androidx.mediarouter) computes contrast from the theme's
 * colorPrimary and throws on a translucent one, which crashed the anime player under the
 * dark Liquid Glass theme. Build it under every app theme, light and dark.
 */
@RunWith(AndroidJUnit4::class)
class CastButtonThemeTest {

    @Test
    fun castButtonInflatesUnderEveryTheme() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // Screen themes only: the AppWidget ones style home-screen widgets, never the player.
        val themes = R.style::class.java.fields
            .filter { it.name.startsWith("Theme_Dantotsu") && !it.name.contains("AppWidget") }
            .associate { it.name to it.getInt(null) }
        assertTrue("no app themes found", themes.isNotEmpty())

        val failures = mutableListOf<String>()
        for (night in listOf(false, true)) {
            val config = Configuration(instrumentation.targetContext.resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                        (if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
            }
            val base = instrumentation.targetContext.createConfigurationContext(config)
            for ((name, style) in themes) {
                // ThemeManager applies the OLED variants only while dark mode is on.
                if (!night && name.endsWith("OLED")) continue
                // MediaRouter must be used on the main thread.
                instrumentation.runOnMainSync {
                    try {
                        CustomCastButton(ContextThemeWrapper(base, style))
                    } catch (e: Exception) {
                        failures += "$name (${if (night) "dark" else "light"}): $e"
                    }
                }
            }
        }
        if (failures.isNotEmpty()) fail(failures.joinToString("\n"))
    }
}
