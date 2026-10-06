package ani.dantotsu.download.manga.enhance

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.Closeable
import java.nio.FloatBuffer

/**
 * One way of running the x4 model on a tile: input is planar RGB in 0..1 (`3 * h * w`
 * floats), output the same layout at four times the size.
 */
interface TileModel : Closeable {
    /** Only takes tiles of one fixed size (see [PageEnhancer]); otherwise any size works. */
    val fixedShape: Boolean

    fun run(tile: FloatArray, w: Int, h: Int): FloatArray
}

/** ONNX Runtime, on the CPU or through NNAPI. */
class OrtTileModel(
    private val env: OrtEnvironment,
    private val session: OrtSession,
    override val fixedShape: Boolean,
) : TileModel {
    override fun run(tile: FloatArray, w: Int, h: Int): FloatArray =
        OnnxTensor.createTensor(env, FloatBuffer.wrap(tile, 0, 3 * w * h), longArrayOf(1, 3, h.toLong(), w.toLong()))
            .use { tensor ->
                session.run(mapOf("input" to tensor)).use { result ->
                    val buffer = (result[0] as OnnxTensor).floatBuffer
                    FloatArray(buffer.remaining()).also { buffer.get(it) }
                }
            }

    override fun close() = session.close()
}

/**
 * ncnn, on the GPU through Vulkan or on its ARM-optimised CPU code (native code in
 * src/main/cpp). The handle belongs to the native side and is freed by [close].
 */
class NcnnTileModel private constructor(private var handle: Long) : TileModel {
    override val fixedShape = false

    override fun run(tile: FloatArray, w: Int, h: Int): FloatArray {
        check(handle != 0L) { "closed" }
        return nativeRun(handle, tile, w, h) ?: throw IllegalStateException("ncnn failed on a ${w}x$h tile")
    }

    override fun close() {
        if (handle != 0L) nativeDestroy(handle)
        handle = 0L
    }

    companion object {
        private val loaded: Boolean by lazy {
            try {
                System.loadLibrary("kiwami_enhance")
                true
            } catch (e: UnsatisfiedLinkError) {
                false
            }
        }

        /** Whether this device has a GPU ncnn can use through Vulkan. */
        val hasGpu: Boolean get() = loaded && nativeGpuCount() > 0

        /** The model from [param] / [bin] (ncnn format), or null if it cannot be loaded. */
        fun create(param: ByteArray, bin: ByteArray, gpu: Boolean): NcnnTileModel? {
            if (!loaded || (gpu && !hasGpu)) return null
            val handle = nativeCreate(param, bin, gpu)
            return if (handle == 0L) null else NcnnTileModel(handle)
        }

        @JvmStatic private external fun nativeGpuCount(): Int
        @JvmStatic private external fun nativeCreate(param: ByteArray, bin: ByteArray, gpu: Boolean): Long
        @JvmStatic private external fun nativeRun(handle: Long, tile: FloatArray, w: Int, h: Int): FloatArray?
        @JvmStatic private external fun nativeDestroy(handle: Long)
    }
}
