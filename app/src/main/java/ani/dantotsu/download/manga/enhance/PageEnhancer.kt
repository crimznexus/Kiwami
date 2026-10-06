package ani.dantotsu.download.manga.enhance

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.providers.NNAPIFlags
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import ani.dantotsu.BuildConfig
import ani.dantotsu.util.Logger
import java.io.Closeable
import java.util.EnumSet
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Upscales and cleans manga/manhwa pages with Real-ESRGAN's animevideov3 model
 * (assets/enhance, a compact x4 network).
 *
 * Pages are run through the model in fixed-size tiles (so memory stays bounded and NNAPI,
 * which needs static shapes, can be used) with a few pixels of overlap to hide seams. The
 * x4 result is then scaled to the target size, normally 2x and at most [MAX_WIDTH] wide.
 */
class PageEnhancer private constructor(
    private val model: TileModel,
    /** Which execution path won the start-up benchmark, for logs and the notification. */
    val backend: String,
) : Closeable {
    /** The backend only takes [TILE]-sized input (NNAPI); otherwise tiles are cut to fit. */
    private val fixedShape get() = model.fixedShape

    /**
     * Enhanced copy of [page], or null when the page is too large to process safely (it is
     * then left as it is). [onProgress] receives the fraction of tiles done.
     */
    fun enhance(page: Bitmap, onProgress: (Float) -> Unit = {}, checkCancelled: () -> Unit = {}): Bitmap? {
        val w = page.width
        val h = page.height
        if (w <= 0 || h <= 0 || w.toLong() * h > MAX_PIXELS) return null

        // Final size: 2x for low-res pages, but no wider than MAX_WIDTH (and never narrower
        // than the original), then shrunk if the result would use too much memory.
        var outW = min(2 * w, MAX_WIDTH).coerceAtLeast(w)
        var outH = (h.toLong() * outW / w).toInt()
        if (outW.toLong() * outH > MAX_PIXELS) {
            outW = sqrt(MAX_PIXELS.toDouble() * w / h).toInt().coerceAtLeast(1)
            outH = (h.toLong() * outW / w).toInt()
        }
        // Pages that would barely grow are already sharp enough: leave them as they are.
        if (outW < w * MIN_GROWTH) return null

        // The model's cost grows with its input size, so wide pages are shrunk before it runs:
        // its x4 output still covers the target, and blurry pages lose little real detail.
        val p = max(outW / (4.0 * w), min(1.0, MODEL_INPUT_WIDTH.toDouble() / w)).coerceAtMost(1.0)
        val pw = (w * p).roundToInt().coerceAtLeast(1)
        val ph = (h * p).roundToInt().coerceAtLeast(1)
        val input = if (pw == w && ph == h) page else Bitmap.createScaledBitmap(page, pw, ph, true)
        val pixels = IntArray(pw * ph)
        input.getPixels(pixels, 0, pw, 0, 0, pw, ph)

        // Pool the x4 output to x2 first when the target is at most half of it; drawing a
        // single bilinear step down by more than 2x would alias.
        val pool = if (outW <= 2 * pw) 2 else 1
        val factor = SCALE / pool

        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        val tileOut = Bitmap.createBitmap(INNER * factor, INNER * factor, Bitmap.Config.ARGB_8888)
        val tileIn = FloatArray(3 * TILE * TILE)
        val tilePixels = IntArray(INNER * factor * INNER * factor)
        val sx = outW.toDouble() / pw
        val sy = outH.toDouble() / ph

        // Webtoon pages are panels separated by blank gutters. The blank bands are only
        // scaled (the model has nothing to restore there), and the content between them is
        // tiled on its own, so tiles line up with the panels instead of straddling a gutter.
        val content = contentBands(pixels, pw, ph)
        var gapStart = 0
        for ((top, bottom) in content + (ph to ph)) {
            if (top > gapStart) {
                canvas.drawBitmap(
                    input, Rect(0, gapStart, pw, top),
                    Rect(0, (gapStart * sy).roundToInt(), outW, (top * sy).roundToInt()), paint
                )
            }
            gapStart = bottom
        }

        val tilesX = (pw + INNER - 1) / INNER
        val total = content.sumOf { (top, bottom) -> (bottom - top + INNER - 1) / INNER } * tilesX
        var done = 0
        var work = 0L
        modelNs = 0L
        for ((top, bottom) in content) for (y0 in top until bottom step INNER) for (tx in 0 until tilesX) {
            checkCancelled()
            val x0 = tx * INNER
            val iw = min(INNER, pw - x0)
            val ih = min(INNER, bottom - y0)
            // Edge tiles are cut to what they cover, except where the backend needs one fixed
            // shape (NNAPI), which pads them out to the full tile instead.
            val tw = if (fixedShape) TILE else iw + 2 * PAD
            val th = if (fixedShape) TILE else ih + 2 * PAD
            val plane = tw * th

            // Destination edges come from the shared grid, so neighbouring tiles always meet.
            val dst = Rect(
                (x0 * sx).roundToInt(), (y0 * sy).roundToInt(),
                ((x0 + iw) * sx).roundToInt(), ((y0 + ih) * sy).roundToInt()
            )

            // Tile with PAD pixels of context on every side, edges clamped. Track the colour
            // range on the way: a flat tile (plain backgrounds) has nothing for the model to
            // restore, so it is just scaled, which looks the same and costs nothing.
            var minR = 255; var maxR = 0; var minG = 255; var maxG = 0; var minB = 255; var maxB = 0
            for (yy in 0 until th) {
                val srcY = (y0 - PAD + yy).coerceIn(0, ph - 1) * pw
                for (xx in 0 until tw) {
                    val c = pixels[srcY + (x0 - PAD + xx).coerceIn(0, pw - 1)]
                    val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
                    if (r < minR) minR = r; if (r > maxR) maxR = r
                    if (g < minG) minG = g; if (g > maxG) maxG = g
                    if (b < minB) minB = b; if (b > maxB) maxB = b
                    val i = yy * tw + xx
                    tileIn[i] = r / 255f
                    tileIn[plane + i] = g / 255f
                    tileIn[2 * plane + i] = b / 255f
                }
            }
            if (maxR - minR <= FLAT_RANGE && maxG - minG <= FLAT_RANGE && maxB - minB <= FLAT_RANGE) {
                canvas.drawBitmap(input, Rect(x0, y0, x0 + iw, y0 + ih), dst, paint)
                done++
                onProgress(done.toFloat() / total)
                continue
            }
            val t0 = System.nanoTime()
            val result = run(tileIn, tw, th)
            modelNs += System.nanoTime() - t0
            work += plane.toLong()

            // Keep only the tile's own area, dropping the context border.
            val ow = iw * factor
            val oh = ih * factor
            val fullW = tw * SCALE
            val outPlane = fullW * th * SCALE
            for (yy in 0 until oh) for (xx in 0 until ow) {
                var r = 0f; var g = 0f; var b = 0f
                for (dy in 0 until pool) for (dx in 0 until pool) {
                    val i = (PAD * SCALE + yy * pool + dy) * fullW + PAD * SCALE + xx * pool + dx
                    r += result[i]; g += result[outPlane + i]; b += result[2 * outPlane + i]
                }
                val n = (pool * pool).toFloat()
                tilePixels[yy * ow + xx] = (0xFF shl 24) or
                        (channel(r / n) shl 16) or (channel(g / n) shl 8) or channel(b / n)
            }
            tileOut.setPixels(tilePixels, 0, ow, 0, 0, ow, oh)
            canvas.drawBitmap(tileOut, Rect(0, 0, ow, oh), dst, paint)
            done++
            onProgress(done.toFloat() / total)
        }
        tileOut.recycle()
        if (input !== page) input.recycle()
        lastWorkShare = work.toDouble() / (tilesX.toLong() * ((ph + INNER - 1) / INNER) * TILE * TILE)
        return out
    }

    /**
     * For logs and tests: the model's work on the last page as a share of what a plain grid
     * of full tiles over the whole page would cost (1.0 = no savings).
     */
    var lastWorkShare = 1.0
        private set

    /** Time spent inside the model on the last page. */
    var modelNs = 0L
        private set

    /**
     * Row ranges (top inclusive, bottom exclusive) holding content: everything except runs of
     * at least [MIN_BAND] blank rows ([MIN_BAND_FIXED] for fixed-shape backends).
     */
    private fun contentBands(pixels: IntArray, pw: Int, ph: Int): List<Pair<Int, Int>> {
        val blank = BooleanArray(ph) { y ->
            var minR = 255; var maxR = 0; var minG = 255; var maxG = 0; var minB = 255; var maxB = 0
            for (x in 0 until pw) {
                val c = pixels[y * pw + x]
                val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
                if (r < minR) minR = r; if (r > maxR) maxR = r
                if (g < minG) minG = g; if (g > maxG) maxG = g
                if (b < minB) minB = b; if (b > maxB) maxB = b
            }
            maxR - minR <= FLAT_RANGE && maxG - minG <= FLAT_RANGE && maxB - minB <= FLAT_RANGE
        }
        val bands = mutableListOf<Pair<Int, Int>>()
        var top = 0
        var y = 0
        while (y < ph) {
            if (!blank[y]) { y++; continue }
            var end = y
            while (end < ph && blank[end]) end++
            // With fixed-size tiles a split costs a partly empty tile row on each side, so only
            // long gaps are worth it; cut-to-fit tiles make short ones worth skipping too.
            if (end - y >= (if (fixedShape) MIN_BAND_FIXED else MIN_BAND)) {
                if (y > top) bands += top to y
                top = end
            }
            y = end
        }
        if (top < ph) bands += top to ph
        return bands
    }

    private fun run(tile: FloatArray, tw: Int = TILE, th: Int = TILE): FloatArray = model.run(tile, tw, th)

    override fun close() = model.close()

    companion object {
        private const val MODEL_ASSET = "enhance/realesr-animevideov3.onnx"
        // The same network in ncnn's format, from the official realesrgan-ncnn-vulkan release.
        private const val NCNN_PARAM_ASSET = "enhance/realesr-animevideov3-x4.param"
        private const val NCNN_BIN_ASSET = "enhance/realesr-animevideov3-x4.bin"
        private const val SCALE = 4
        // 216 = 200 + 2 * PAD, so a page reduced to MODEL_INPUT_WIDTH (600) is exactly three
        // tiles wide; 192 needed four, the last one mostly empty.
        private const val TILE = 216
        private const val PAD = 8
        private const val INNER = TILE - 2 * PAD
        private const val MAX_WIDTH = 1600
        /** Smallest size increase worth running the model for; sharper pages are left as is. */
        private const val MIN_GROWTH = 1.15
        /** Largest per-channel spread (of 255) for a tile to count as flat. */
        private const val FLAT_RANGE = 4
        /** Blank rows (in the model's input scale) needed to treat a gap as a gutter. */
        private const val MIN_BAND = 24
        private const val MIN_BAND_FIXED = 96
        /** Model input width wide pages are reduced to (see enhance). */
        private const val MODEL_INPUT_WIDTH = 600
        /** Largest page, before or after enhancing: 16 MP is 64 MB as ARGB_8888. */
        private const val MAX_PIXELS = 16_000_000L

        private fun channel(v: Float) = (v * 255f + 0.5f).toInt().coerceIn(0, 255)

        private const val PREFS = "page_enhancer"
        private const val KEY_BACKEND = "backend"

        /**
         * Loads the model on the fastest execution path for this device: the GPU through
         * Vulkan (ncnn) where there is one, otherwise the best CPU path. Paths are timed on a
         * real tile once and the winner is remembered (per app version), so later runs skip
         * that benchmark, which takes several seconds.
         */
        fun create(context: Context): PageEnhancer {
            val assets = Assets(context)
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            // A new app version or a changed set of backends means benchmarking again.
            val version = "${BuildConfig.VERSION_CODE}/${BACKENDS.joinToString(",")}"
            prefs.getString(KEY_BACKEND, null)?.split("@", limit = 2)?.takeIf { it.size == 2 }?.let { (name, savedVersion) ->
                if (savedVersion == version) {
                    open(assets, name)?.let { enhancer ->
                        try {
                            enhancer.run(FloatArray(3 * TILE * TILE) { 0.5f })
                            return enhancer
                        } catch (e: Exception) {
                            Logger.log("PageEnhancer: remembered $name failed, benchmarking again: $e")
                            enhancer.close()
                        }
                    }
                }
            }

            var best: PageEnhancer? = null
            var bestTime = Long.MAX_VALUE
            for (name in BACKENDS) {
                val enhancer = open(assets, name) ?: continue
                val time = try {
                    val tile = FloatArray(3 * TILE * TILE) { 0.5f }
                    enhancer.run(tile) // warm-up; NNAPI compiles on first use
                    val start = System.nanoTime()
                    enhancer.run(tile)
                    System.nanoTime() - start
                } catch (e: Exception) {
                    Logger.log("PageEnhancer: $name failed its test run: $e")
                    enhancer.close()
                    continue
                }
                Logger.log("PageEnhancer: $name takes ${time / 1_000_000} ms per tile")
                if (time < bestTime) {
                    best?.close()
                    best = enhancer
                    bestTime = time
                } else enhancer.close()
            }
            best ?: throw IllegalStateException("No usable backend for the page enhancer")
            prefs.edit().putString(KEY_BACKEND, "${best.backend}@$version").apply()
            return best
        }

        private const val GPU = "GPU (Vulkan)"
        private const val NCNN_CPU = "CPU (ncnn)"
        private val BACKENDS = listOf(GPU, NCNN_CPU, "NNAPI", "CPU", "CPU (basic)")

        /** Model files, read from the APK only when a backend needs them. */
        private class Assets(private val context: Context) {
            val onnx by lazy { read(MODEL_ASSET) }
            val ncnnParam by lazy { read(NCNN_PARAM_ASSET) }
            val ncnnBin by lazy { read(NCNN_BIN_ASSET) }
            private fun read(name: String) = context.assets.open(name).use { it.readBytes() }
        }

        /** The model on the named execution path, or null when this device cannot run it. */
        private fun open(assets: Assets, name: String): PageEnhancer? {
            if (name == GPU || name == NCNN_CPU) {
                return try {
                    NcnnTileModel.create(assets.ncnnParam, assets.ncnnBin, gpu = name == GPU)
                        ?.let { PageEnhancer(it, name) }
                } catch (e: Exception) {
                    Logger.log("PageEnhancer: $name unavailable: $e")
                    null
                }
            }
            val env = OrtEnvironment.getEnvironment()
            val cores = Runtime.getRuntime().availableProcessors().coerceIn(1, 8)
            val fixed = name == "NNAPI"
            return try {
                val options = OrtSession.SessionOptions().apply {
                    // NNAPI cannot take dynamic dimensions, so it gets one fixed tile shape;
                    // the CPU paths keep them free so edge tiles can be cut to fit.
                    if (fixed) {
                        setSymbolicDimensionValue("h", TILE.toLong())
                        setSymbolicDimensionValue("w", TILE.toLong())
                    }
                    when (name) {
                        "NNAPI" -> addNnapi(EnumSet.of(NNAPIFlags.USE_FP16))
                        "CPU" -> {
                            addXnnpack(mapOf("intra_op_num_threads" to cores.toString()))
                            setIntraOpNumThreads(1)
                        }
                        else -> setIntraOpNumThreads(cores)
                    }
                }
                PageEnhancer(OrtTileModel(env, env.createSession(assets.onnx, options), fixed), name)
            } catch (e: Exception) {
                Logger.log("PageEnhancer: $name unavailable: $e")
                null
            }
        }
    }
}
