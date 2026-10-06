package ani.dantotsu.download.manga.enhance

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/** The ncnn engine must produce the same picture as the ONNX Runtime one it can replace. */
@RunWith(AndroidJUnit4::class)
class NcnnTileModelTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val assets = instrumentation.targetContext.assets

    private fun read(name: String) = assets.open(name).use { it.readBytes() }

    /** A 120x96 slice of the test page as planar RGB in 0..1. */
    private fun tile(): Triple<FloatArray, Int, Int> {
        val page = instrumentation.context.assets.open("low_quality_page.jpg").use { BitmapFactory.decodeStream(it) }
        val w = 120
        val h = 96
        val pixels = IntArray(w * h)
        page.getPixels(pixels, 0, w, 200, 200, w, h)
        val plane = w * h
        val out = FloatArray(3 * plane)
        pixels.forEachIndexed { i, c ->
            out[i] = ((c shr 16) and 0xFF) / 255f
            out[plane + i] = ((c shr 8) and 0xFF) / 255f
            out[2 * plane + i] = (c and 0xFF) / 255f
        }
        return Triple(out, w, h)
    }

    private fun meanDiff(a: FloatArray, b: FloatArray): Double {
        assertEquals(a.size, b.size)
        return a.indices.sumOf { abs(a[it] - b[it]).toDouble() } / a.size
    }

    @Test
    fun matchesOnnxRuntimeOnCpuAndGpu() {
        val (input, w, h) = tile()
        val env = OrtEnvironment.getEnvironment()
        val reference = OrtTileModel(
            env, env.createSession(read("enhance/realesr-animevideov3.onnx"), OrtSession.SessionOptions()), false
        ).use { it.run(input, w, h) }

        val param = read("enhance/realesr-animevideov3-x4.param")
        val bin = read("enhance/realesr-animevideov3-x4.bin")
        val cpu = NcnnTileModel.create(param, bin, gpu = false)
        assertNotNull("ncnn CPU model failed to load", cpu)
        val cpuDiff = cpu!!.use { meanDiff(reference, it.run(input, w, h)) }
        Log.i("NcnnTileModelTest", "ncnn CPU vs ONNX Runtime: mean difference $cpuDiff")
        // fp16 weights and maths: small differences are expected, a wrong layout is not.
        assertTrue("ncnn CPU output differs (mean $cpuDiff)", cpuDiff < 0.01)

        if (NcnnTileModel.hasGpu) {
            val gpuDiff = NcnnTileModel.create(param, bin, gpu = true)!!.use { meanDiff(reference, it.run(input, w, h)) }
            Log.i("NcnnTileModelTest", "ncnn GPU vs ONNX Runtime: mean difference $gpuDiff")
            assertTrue("ncnn GPU output differs (mean $gpuDiff)", gpuDiff < 0.01)
        } else {
            Log.i("NcnnTileModelTest", "no Vulkan GPU on this device; GPU path not checked")
        }
    }
}
