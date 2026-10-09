package ani.dantotsu

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ani.dantotsu.media.MediaType
import eu.kanade.tachiyomi.extension.util.ExtensionInstaller
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** The extension APK must be fetched in-process (the system DownloadManager stalls on some phones). */
@RunWith(AndroidJUnit4::class)
class ExtensionDownloadTest {

    @Test
    fun downloadsAnApkQuickly() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val installer = ExtensionInstaller(context)
        val file = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), "Kiwami-armeabi-v7a-release.apk")
        file.delete()
        val start = System.nanoTime()
        val sub = installer.downloadAndInstall(
            "https://github.com/crimznexus/Kiwami/releases/download/v1.0.4/Kiwami-armeabi-v7a-release.apk",
            "test.pkg", "Test", MediaType.MANGA
        ).subscribe { Log.i("ExtDownloadTest", "step $it") }
        while (!file.exists() && (System.nanoTime() - start) < 120_000_000_000L) Thread.sleep(50)
        val size = file.length()
        val ms = (System.nanoTime() - start) / 1_000_000
        Log.i("ExtDownloadTest", "downloaded $size bytes in $ms ms")
        sub.unsubscribe()
        assertTrue(size > 1_000_000)
    }
}
