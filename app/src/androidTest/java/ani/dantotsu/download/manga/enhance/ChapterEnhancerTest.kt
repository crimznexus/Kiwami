package ani.dantotsu.download.manga.enhance

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.documentfile.provider.DocumentFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.AfterClass
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ChapterEnhancerTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var folder: File
    private lateinit var pageBytes: ByteArray

    @Before
    fun setUp() {
        folder = File(context.cacheDir, "chapter-test").apply { deleteRecursively(); mkdirs() }
        // A small slice of the test page keeps the model runs short.
        val full = instrumentation.context.assets.open("low_quality_page.jpg").use { BitmapFactory.decodeStream(it) }
        val slice = Bitmap.createBitmap(full, 0, 0, 200, 150)
        pageBytes = java.io.ByteArrayOutputStream().also { slice.compress(Bitmap.CompressFormat.JPEG, 60, it) }.toByteArray()
    }

    private fun page(name: String) = File(folder, name).apply { writeBytes(pageBytes) }
    private fun width(name: String) = BitmapFactory.decodeFile(File(folder, name).path).width
    private fun visibleFiles() = folder.list()!!.filter { !it.startsWith(".") }.sorted()

    @Test
    fun enhancesEveryPageOnceAndLeavesNoTemporaryFiles() {
        listOf("1.jpg", "2.jpg", "10.jpg").forEach(::page)
        val dir = DocumentFile.fromFile(folder)
        val chapter = ChapterEnhancer(context)
        val seen = mutableListOf<Int>()

        chapter.enhance(dir, enhancer, onPage = { i, _, f -> if (f == 0f) seen += i })

        assertEquals(listOf(0, 1, 2), seen)
        listOf("1.jpg", "2.jpg", "10.jpg").forEach { assertEquals(400, width(it)) }
        assertTrue(chapter.isComplete(dir))
        assertEquals(listOf("1.jpg", "10.jpg", "2.jpg"), visibleFiles())
        assertFalse(folder.list()!!.any { it.startsWith(ChapterEnhancer.TEMP_PREFIX) })

        // A second run must not touch the already enhanced pages.
        val before = File(folder, "1.jpg").readBytes()
        chapter.enhance(dir, enhancer, onPage = { _, _, _ -> throw AssertionError("re-enhanced") })
        assertArrayEquals(before, File(folder, "1.jpg").readBytes())
    }

    @Test
    fun finishesAnInterruptedSwapAndDiscardsAPartialCopy() {
        page("1.jpg"); page("2.jpg")
        // Page 1 died mid-copy: its enhanced copy was complete (marked pending), the page itself
        // half overwritten. Page 2 died while its copy was still being written.
        val enhancedCopy = java.io.ByteArrayOutputStream().also {
            Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 90, it)
        }.toByteArray()
        File(folder, ChapterEnhancer.TEMP_PREFIX + "1.jpg").writeBytes(enhancedCopy)
        File(folder, "1.jpg").writeBytes(pageBytes.copyOf(100))
        File(folder, ChapterEnhancer.TEMP_PREFIX + "2.jpg").writeBytes(byteArrayOf(1, 2, 3))
        File(folder, ChapterEnhancer.MARKER).writeText("pending:1.jpg")

        val enhanced = mutableListOf<Int>()
        ChapterEnhancer(context).enhance(
            DocumentFile.fromFile(folder), enhancer,
            onPage = { i, _, f -> if (f == 0f) enhanced += i }
        )

        assertArrayEquals(enhancedCopy, File(folder, "1.jpg").readBytes())
        assertEquals(listOf(1), enhanced) // page 1 was finished by recovery, not re-run
        assertEquals(400, width("2.jpg"))
        assertEquals(listOf("1.jpg", "2.jpg"), visibleFiles())
        assertFalse(folder.list()!!.any { it.startsWith(ChapterEnhancer.TEMP_PREFIX) })
    }

    companion object {
        private lateinit var enhancer: PageEnhancer

        @BeforeClass
        @JvmStatic
        fun loadModel() {
            enhancer = PageEnhancer.create(InstrumentationRegistry.getInstrumentation().targetContext)
        }

        @AfterClass
        @JvmStatic
        fun closeModel() = enhancer.close()
    }
}
