package ani.dantotsu

import ani.dantotsu.others.AppUpdater
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdaterTest {

    @Test
    fun comparesVersionsIgnoringBuildSuffixes() {
        assertTrue(AppUpdater.isNewer("1.0.4", "1.0.3-fdroid"))
        assertTrue(AppUpdater.isNewer("1.1.0", "1.0.9"))
        assertTrue(AppUpdater.isNewer("2.0", "1.9.9"))
        assertFalse(AppUpdater.isNewer("1.0.3", "1.0.3-fdroid"))
        assertFalse(AppUpdater.isNewer("1.0.3", "1.0.4"))
    }

    @Test
    fun picksTheApkForThePhonesCpu() {
        val urls = listOf(
            "https://x/Kiwami-arm64-v8a-release.apk",
            "https://x/Kiwami-armeabi-v7a-release.apk",
            "https://x/Kiwami-universal-release.apk",
            "https://x/notes.txt"
        )
        assertEquals(urls[0], AppUpdater.pickApk(urls, arrayOf("arm64-v8a", "armeabi-v7a")))
        assertEquals(urls[1], AppUpdater.pickApk(urls, arrayOf("armeabi-v7a")))
        assertEquals(urls[2], AppUpdater.pickApk(urls, arrayOf("x86_64")))
    }
}
