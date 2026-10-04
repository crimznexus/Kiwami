package ani.dantotsu.download.manga.enhance

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.providers.NNAPIFlags
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import ani.dantotsu.util.Logger
import java.io.Closeable
import java.nio.FloatBuffer
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
    private val env: OrtEnvironment,
    private val session: OrtSession,
    /** Which execution path won the start-up benchmark, for logs and the notification. */
    val backend: String,
) : Closeable {

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

        // The model's cost grows with its input size, so wide pages are shrunk before it runs:
        // its x4 output still covers the target, and blurry pages lose little real detail.
        val p = max(outW / (4.0 * w), min(1.0, MODEL_INPUT_WIDTH.toDouble() / w)).coerceAtMost(1.0)
        val pw = (w * p).roundToInt().coerceAtLeast(1)
        val ph = (h * p).roundToInt().coerceAtLeast(1)
        val input = if (pw == w && ph == h) page else Bitmap.createScaledBitmap(page, pw, ph, true)
        val pixels = IntArray(pw * ph)
        input.getPixels(pixels, 0, pw, 0, 0, pw, ph)
        if (input !== page) input.recycle()

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
        val plane = TILE * TILE
        val sx = outW.toDouble() / pw
        val sy = outH.toDouble() / ph

        val tilesX = (pw + INNER - 1) / INNER
        val tilesY = (ph + INNER - 1) / INNER
        var done = 0
        for (ty in 0 until tilesY) for (tx in 0 until tilesX) {
            checkCancelled()
            val x0 = tx * INNER
            val y0 = ty * INNER
            val iw = min(INNER, pw - x0)
            val ih = min(INNER, ph - y0)

            // Tile with PAD pixels of context on every side, edges clamped.
            for (yy in 0 until TILE) {
                val srcY = (y0 - PAD + yy).coerceIn(0, ph - 1) * pw
                for (xx in 0 until TILE) {
                    val c = pixels[srcY + (x0 - PAD + xx).coerceIn(0, pw - 1)]
                    val i = yy * TILE + xx
                    tileIn[i] = ((c shr 16) and 0xFF) / 255f
                    tileIn[plane + i] = ((c shr 8) and 0xFF) / 255f
                    tileIn[2 * plane + i] = (c and 0xFF) / 255f
                }
            }
            val result = run(tileIn)

            // Keep only the tile's own area, dropping the context border.
            val ow = iw * factor
            val oh = ih * factor
            val full = TILE * SCALE
            val outPlane = full * full
            for (yy in 0 until oh) for (xx in 0 until ow) {
                var r = 0f; var g = 0f; var b = 0f
                for (dy in 0 until pool) for (dx in 0 until pool) {
                    val i = (PAD * SCALE + yy * pool + dy) * full + PAD * SCALE + xx * pool + dx
                    r += result[i]; g += result[outPlane + i]; b += result[2 * outPlane + i]
                }
                val n = (pool * pool).toFloat()
                tilePixels[yy * ow + xx] = (0xFF shl 24) or
                        (channel(r / n) shl 16) or (channel(g / n) shl 8) or channel(b / n)
            }
            tileOut.setPixels(tilePixels, 0, ow, 0, 0, ow, oh)
            // Destination edges come from the shared grid, so neighbouring tiles always meet.
            val dst = Rect(
                (x0 * sx).roundToInt(), (y0 * sy).roundToInt(),
                ((x0 + iw) * sx).roundToInt(), ((y0 + ih) * sy).roundToInt()
            )
            canvas.drawBitmap(tileOut, Rect(0, 0, ow, oh), dst, paint)
            done++
            onProgress(done.toFloat() / (tilesX * tilesY))
        }
        tileOut.recycle()
        return out
    }

    private fun run(tile: FloatArray): FloatArray =
        OnnxTensor.createTensor(env, FloatBuffer.wrap(tile), longArrayOf(1, 3, TILE.toLong(), TILE.toLong()))
            .use { tensor ->
                session.run(mapOf("input" to tensor)).use { result ->
                    val buffer = (result[0] as OnnxTensor).floatBuffer
                    FloatArray(buffer.remaining()).also { buffer.get(it) }
                }
            }

    override fun close() = session.close()

    companion object {
        private const val MODEL_ASSET = "enhance/realesr-animevideov3.onnx"
        private const val SCALE = 4
        private const val TILE = 192
        private const val PAD = 8
        private const val INNER = TILE - 2 * PAD
        private const val MAX_WIDTH = 1600
        /** Model input width wide pages are reduced to (see enhance). */
        private const val MODEL_INPUT_WIDTH = 600
        /** Largest page, before or after enhancing: 16 MP is 64 MB as ARGB_8888. */
        private const val MAX_PIXELS = 16_000_000L

        private fun channel(v: Float) = (v * 255f + 0.5f).toInt().coerceIn(0, 255)

        /**
         * Loads the model and keeps whichever execution path is fastest on this device:
         * NNAPI (GPU/NPU, fp16) is much faster where it is supported but slower or broken on
         * some phones, so both it and the CPU path are timed on a real tile.
         */
        fun create(context: Context): PageEnhancer {
            val model = context.assets.open(MODEL_ASSET).use { it.readBytes() }
            val env = OrtEnvironment.getEnvironment()
            val cores = Runtime.getRuntime().availableProcessors().coerceIn(1, 8)
            val candidates = listOf<Pair<String, (OrtSession.SessionOptions) -> Unit>>(
                "NNAPI" to { it.addNnapi(EnumSet.of(NNAPIFlags.USE_FP16)) },
                "CPU" to {
                    it.addXnnpack(mapOf("intra_op_num_threads" to cores.toString()))
                    it.setIntraOpNumThreads(1)
                },
                "CPU (basic)" to { it.setIntraOpNumThreads(cores) },
            )
            var best: PageEnhancer? = null
            var bestTime = Long.MAX_VALUE
            for ((name, configure) in candidates) {
                val enhancer = try {
                    val options = OrtSession.SessionOptions().apply {
                        // Fixed tile shape: NNAPI cannot take dynamic dimensions.
                        setSymbolicDimensionValue("h", TILE.toLong())
                        setSymbolicDimensionValue("w", TILE.toLong())
                        configure(this)
                    }
                    PageEnhancer(env, env.createSession(model, options), name)
                } catch (e: Exception) {
                    Logger.log("PageEnhancer: $name unavailable: $e")
                    continue
                }
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
            return best ?: throw IllegalStateException("No usable backend for the page enhancer")
        }
    }
}
