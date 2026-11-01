package ai.bowen.phoneagent

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

/** Deterministic smoke tests; does not claim third-party automation is implemented. */
@RunWith(AndroidJUnit4::class)
class PhoneAgentSmokeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)

    @Before
    fun launchFreshApp() {
        device.pressHome()
        val context = instrumentation.targetContext
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        assertNotNull("Launcher activity must exist", intent)
        intent!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
        assertTrue(
            "PhoneAgent diagnostic UI must be visible",
            device.wait(Until.hasObject(By.textContains("PhoneAgent diagnostic prototype")), 15000)
        )
    }

    @Test
    fun launcherShowsManualDiagnosticControls() {
        assertTrue(
            "Observe action must be available",
            device.wait(Until.hasObject(By.text(Pattern.compile("(?i)observe visible screen"))), 5000)
        )
    }

    @Test
    fun userCanNavigateToAndroidSettings() {
        val settingsButton = device.wait(
            Until.findObject(By.text(Pattern.compile("(?i)open android settings"))), 10000
        )
        assertNotNull("Settings button should be visible", settingsButton)
        settingsButton!!.click()
        assertTrue(
            "Tapping Settings must open Android Settings (not merely dispatch)",
            device.wait(Until.hasObject(By.pkg("com.android.settings")), 15000)
        )
    }
}
