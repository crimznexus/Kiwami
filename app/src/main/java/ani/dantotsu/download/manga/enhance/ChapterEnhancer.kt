package ani.dantotsu.download.manga.enhance

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.documentfile.provider.DocumentFile
import net.greypanther.natsort.CaseInsensitiveSimpleNaturalComparator

/**
 * Enhances every page of one downloaded chapter folder in place, crash-safely and resumably.
 *
 * Progress lives in a hidden marker file in the folder: the names of finished pages, a
 * `pending:` line while a page's enhanced copy is being swapped in, and [COMPLETE] at the
 * end. So an interrupted chapter resumes where it stopped, no page is enhanced twice, and
 * deleting the chapter's download removes the state with it.
 */
class ChapterEnhancer(private val context: Context) {

    private val resolver get() = context.contentResolver

    fun isComplete(dir: DocumentFile) = COMPLETE in readMarker(dir)

    /**
     * Enhance the pages of [dir] not yet done. [onPage] gets the page index, the page count
     * and the fraction of the current page done; [checkCancelled] throws to stop.
     */
    fun enhance(
        dir: DocumentFile,
        enhancer: PageEnhancer,
        onPage: (index: Int, total: Int, fraction: Float) -> Unit = { _, _, _ -> },
        checkCancelled: () -> Unit = {},
    ) {
        val state = readMarker(dir).toMutableSet()
        if (COMPLETE in state) return
        recoverInterruptedSwaps(dir, state)

        val comparator = CaseInsensitiveSimpleNaturalComparator.getInstance<String>()
        val pages = dir.listFiles()
            .filter { it.isFile && it.name?.startsWith(".") == false }
            .sortedWith { a, b -> comparator.compare(a.name!!, b.name!!) }

        pages.forEachIndexed { index, page ->
            checkCancelled()
            val name = page.name!!
            if (name in state) return@forEachIndexed
            onPage(index, pages.size, 0f)

            val original = resolver.openInputStream(page.uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                })
            }
            val enhanced = original?.let { bitmap ->
                enhancer.enhance(
                    bitmap,
                    onProgress = { onPage(index, pages.size, it) },
                    checkCancelled = checkCancelled,
                ).also { bitmap.recycle() }
            }
            // Pages that cannot be decoded or are too large are left as they are.
            if (enhanced != null) {
                swapIn(dir, page, enhanced, state)
                enhanced.recycle()
            }
            state += name
            writeMarker(dir, state)
        }
        state += COMPLETE
        writeMarker(dir, state)
    }

    /**
     * Replaces [page] with [bitmap] so that no crash can leave a broken page: the complete
     * result goes to a hidden copy, the marker records that copy as finished, and only then
     * is it copied over the page. [recoverInterruptedSwaps] completes a swap cut short.
     */
    private fun swapIn(dir: DocumentFile, page: DocumentFile, bitmap: Bitmap, state: MutableSet<String>) {
        val name = page.name!!
        val temp = dir.createFile("application/octet-stream", TEMP_PREFIX + name)
            ?: throw IllegalStateException("cannot create $TEMP_PREFIX$name")
        resolver.openOutputStream(temp.uri, "wt")!!.use {
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it)
        }
        state += PENDING + name
        writeMarker(dir, state)
        copy(temp, page)
        temp.delete()
        state -= PENDING + name
    }

    /**
     * A copy marked pending is complete, so it is copied over its page (which may have been
     * half overwritten). Any other copy may be partial and the page untouched: discard it.
     */
    private fun recoverInterruptedSwaps(dir: DocumentFile, state: MutableSet<String>) {
        val files = dir.listFiles()
        val pages = files.filter { it.isFile && it.name?.startsWith(".") == false }
        files.filter { it.name?.startsWith(TEMP_PREFIX) == true }.forEach { temp ->
            // The page whose name the copy carries (a provider may have added an extension).
            val stem = temp.name!!.removePrefix(TEMP_PREFIX)
            val page = pages.filter { stem.startsWith(it.name!!) }.maxByOrNull { it.name!!.length }
            val name = page?.name
            if (name != null && PENDING + name in state) {
                copy(temp, page)
                state -= PENDING + name
                state += name
            }
            temp.delete()
        }
        state.removeAll { it.startsWith(PENDING) }
        writeMarker(dir, state)
    }

    private fun copy(from: DocumentFile, to: DocumentFile) {
        resolver.openInputStream(from.uri)!!.use { input ->
            resolver.openOutputStream(to.uri, "wt")!!.use { input.copyTo(it) }
        }
    }

    // createFile may add an extension to the name (".bin" for this MIME type on some
    // providers), so the marker is looked up by prefix.
    private fun findMarker(dir: DocumentFile) =
        dir.listFiles().firstOrNull { it.name?.startsWith(MARKER) == true }

    private fun readMarker(dir: DocumentFile): Set<String> {
        val marker = findMarker(dir) ?: return emptySet()
        return resolver.openInputStream(marker.uri)?.bufferedReader()?.use {
            it.readLines().filter(String::isNotBlank).toSet()
        } ?: emptySet()
    }

    private fun writeMarker(dir: DocumentFile, lines: Set<String>) {
        val marker = findMarker(dir)
            ?: dir.createFile("application/octet-stream", MARKER)
            ?: throw IllegalStateException("cannot create $MARKER")
        resolver.openOutputStream(marker.uri, "wt")!!.bufferedWriter().use {
            it.write(lines.joinToString("\n"))
        }
    }

    companion object {
        const val MARKER = ".kiwami-enhanced"
        const val TEMP_PREFIX = ".enhancing-"
        private const val COMPLETE = "#complete"
        private const val PENDING = "pending:"
        private const val JPEG_QUALITY = 92
    }
}
