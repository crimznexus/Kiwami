package ani.dantotsu.download.manga.enhance

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PageEnhancerTest {

    @Test
    fun enhancesALowResolutionPageToTwiceItsWidth() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val page = instrumentation.context.assets.open("low_quality_page.jpg")
            .use { BitmapFactory.decodeStream(it) }

        val start = System.nanoTime()
        val enhancer = PageEnhancer.create(instrumentation.targetContext)
        val loaded = System.nanoTime()
        val result = enhancer.use { it.enhance(page) }
        val done = System.nanoTime()

        assertNotNull(result)
        assertEquals(page.width * 2, result!!.width)
        assertEquals(page.height * 2, result.height)
        Log.i(
            "PageEnhancerTest",
            "backend=${enhancer.backend} load+benchmark=${(loaded - start) / 1_000_000}ms " +
                    "page ${page.width}x${page.height} -> ${result.width}x${result.height} " +
                    "in ${(done - loaded) / 1_000_000}ms " +
                    "(model work ${(enhancer.lastWorkShare * 100).toInt()}% of a full grid, model ${enhancer.modelNs / 1_000_000}ms)"
        )
        // Kept for a visual check: adb pull from the app's cache directory.
        File(instrumentation.targetContext.cacheDir, "enhanced_test_page.jpg").outputStream().use {
            result.compress(Bitmap.CompressFormat.JPEG, 92, it)
        }
    }

    @Test
    fun skipsTheBlankGuttersOfAWebtoonPage() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val page = instrumentation.context.assets.open("webtoon_page.jpg").use { BitmapFactory.decodeStream(it) }
        val start = System.nanoTime()
        val result = PageEnhancer.create(instrumentation.targetContext).use { enhancer ->
            val loaded = System.nanoTime()
            enhancer.enhance(page)!!.also {
                Log.i(
                    "PageEnhancerTest",
                    "webtoon ${page.width}x${page.height} -> ${it.width}x${it.height} in " +
                            "${(System.nanoTime() - loaded) / 1_000_000}ms (model work " +
                            "${(enhancer.lastWorkShare * 100).toInt()}% of a full grid, model ${enhancer.modelNs / 1_000_000}ms)"
                )
                assertTrue(enhancer.lastWorkShare < 1.0)
            }
        }
        assertEquals(page.width * 2, result.width)
        File(instrumentation.targetContext.cacheDir, "enhanced_webtoon_page.jpg").outputStream().use {
            result.compress(Bitmap.CompressFormat.JPEG, 92, it)
        }
    }
}
