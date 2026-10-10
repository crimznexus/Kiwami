package ani.dantotsu

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import ani.dantotsu.settings.SettingsAccountActivity
import ani.dantotsu.settings.SettingsActivity
import ani.dantotsu.settings.SettingsAboutActivity
import org.junit.Test
import org.junit.runner.RunWith

/** The screens that lost their Dantotsu-only entries still open. */
@RunWith(AndroidJUnit4::class)
class SettingsScreensTest {

    @Test
    fun settingsOpen() {
        ActivityScenario.launch(SettingsActivity::class.java).use { Thread.sleep(1500) }
    }

    @Test
    fun accountSettingsOpen() {
        ActivityScenario.launch(SettingsAccountActivity::class.java).use { Thread.sleep(1500) }
    }

    @Test
    fun aboutSettingsOpen() {
        ActivityScenario.launch(SettingsAboutActivity::class.java).use { Thread.sleep(1500) }
    }
}
