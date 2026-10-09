package ani.dantotsu.download.manga.enhance

import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Times whole pages on every backend the device can run. The start-up benchmark compares a
 * single full tile, which hides that fixed-shape backends (NNAPI) cannot cut edge tiles or
 * skip short gutters; this shows the real per-page cost. Logs only, never fails.
 */
@RunWith(AndroidJUnit4::class)
class BackendBenchmarkTest {

    @Test
    fun timeEveryBackendOnWholePages() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val pages = listOf("low_quality_page.jpg", "webtoon_page.jpg").map { name ->
            name to instrumentation.context.assets.open(name).use { BitmapFactory.decodeStream(it) }
        }
        for (backend in PageEnhancer.BACKENDS) {
            val enhancer = PageEnhancer.createWith(instrumentation.targetContext, backend)
            if (enhancer == null) {
                Log.i(TAG, "$backend: not available")
                continue
            }
            enhancer.use {
                it.enhance(pages[0].second) // warm-up (NNAPI and Vulkan compile on first use)
                val times = pages.map { (name, page) ->
                    val start = System.nanoTime()
                    it.enhance(page)!!.recycle()
                    "$name ${(System.nanoTime() - start) / 1_000_000} ms"
                }
                Log.i(TAG, "$backend: ${times.joinToString(", ")}")
            }
        }
    }

    private companion object {
        const val TAG = "BackendBenchmark"
    }
}
