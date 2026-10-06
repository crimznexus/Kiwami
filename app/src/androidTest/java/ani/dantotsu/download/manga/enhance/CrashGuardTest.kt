package ani.dantotsu.download.manga.enhance

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * An engine whose trial killed the process (left its "trying" record behind) must not be
 * tried again: a GPU driver crash would otherwise crash the app on every enhancement.
 */
@RunWith(AndroidJUnit4::class)
class CrashGuardTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs = context.getSharedPreferences(PageEnhancer.PREFS_NAME, Context.MODE_PRIVATE)

    @After
    fun reset() {
        prefs.edit().clear().commit()
    }

    @Test
    fun skipsAnEngineThatCrashedTheAppLastTime() {
        prefs.edit().clear()
            .putString(PageEnhancer.KEY_TRYING, "GPU (Vulkan)@${PageEnhancer.backendVersion()}")
            .commit()

        PageEnhancer.create(context).use { enhancer ->
            assertNotEquals("GPU (Vulkan)", enhancer.backend)
        }
        assertFalse("the trial record must be cleared", prefs.contains(PageEnhancer.KEY_TRYING))

        // ...and it stays skipped on later runs, not just the first one after the crash.
        PageEnhancer.create(context).use { enhancer ->
            assertNotEquals("GPU (Vulkan)", enhancer.backend)
        }
    }
}
