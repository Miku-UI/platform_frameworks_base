/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.wm.shell.scenarios

import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.uiautomatorhelpers.DeviceHelpers
import android.platform.uiautomatorhelpers.DeviceHelpers.assertInvisible
import android.platform.uiautomatorhelpers.DeviceHelpers.assertVisible
import android.tools.Rotation
import android.tools.device.apphelpers.BrowserAppHelper
import android.tools.traces.parsers.WindowManagerStateHelper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import com.android.launcher3.tapl.LauncherInstrumentation
import com.android.server.wm.flicker.helpers.WebIntentAppHelper
import com.android.server.wm.flicker.testapp.ActivityOptions
import com.android.window.flags.Flags
import com.android.wm.shell.Utils
import com.android.wm.shell.flicker.utils.SYSTEM_UI_PACKAGE_NAME
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

@Ignore("Test Base Class")
@RequiresFlagsEnabled(
    Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE,
    Flags.FLAG_ENABLE_ENHANCED_APP_TO_WEB_TRANSITION,
)
abstract class AppToWebFirstRunDialog(val rotation: Rotation = Rotation.ROTATION_0) :
    TestScenarioBase(rotation) {

    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val systemUiResources =
        instrumentation.context.packageManager.getResourcesForApplication(SYSTEM_UI_PACKAGE_NAME)
    private val tapl = LauncherInstrumentation()
    private val wmHelper = WindowManagerStateHelper(instrumentation)
    private val device = UiDevice.getInstance(instrumentation)
    private val testApp = WebIntentAppHelper(instrumentation)
    private val browserApp = BrowserAppHelper(instrumentation)

    private val webIntent =
        Intent(Intent.ACTION_VIEW, Uri.parse(ActivityOptions.WebIntentActivity.URL))
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    @Before
    fun setup() {
        val isFeatureEnabled =
            systemUiResources.run {
                getBoolean(getIdentifier(FEATURE_CONFIG_NAME, "bool", SYSTEM_UI_PACKAGE_NAME))
            }
        assumeTrue(isFeatureEnabled)

        Utils.clearAppToWebFirstRunPromptAcked(testApp.packageName)
        Utils.setAppLinksAllowed(testApp.packageName)
        Utils.setAppLinksUserSelection(testApp.packageName)
    }

    @Test
    open fun askMeLaterButtonIsClicked() {
        // Open an app via WebIntent.
        context.startActivity(webIntent)
        verifyFirstRunDialogIsShown()

        // Click "Ask me later" button and close the app.
        clickButton(ASK_ME_LATER_TEXT)
        verifyFirstRunDialogIsNotShown()
        testApp.exit(wmHelper)
        verifyAppIsInvisible()

        // Open an app again.
        context.startActivity(webIntent)
        verifyFirstRunDialogIsShown()
    }

    @Test
    open fun stayInAppIsClicked() {
        // Open an app via WebIntent.
        context.startActivity(webIntent)
        verifyFirstRunDialogIsShown()

        // Verify "Stay in app" button.
        clickButton(STAY_IN_APP_TEXT)
        verifyFirstRunDialogIsNotShown()
        verifyAppIsVisible()
    }

    @Test
    open fun openInBrowserIsClicked() {
        // Open an app via WebIntent.
        context.startActivity(webIntent)
        verifyFirstRunDialogIsShown()

        // Verify "Open in browser" button.
        clickButton(OPEN_IN_BROWSER_TEXT)
        verifyFirstRunDialogIsNotShown()
        verifyAppIsInvisible()
        verifyBrowserIsVisible()

        browserApp.exit(wmHelper)
    }

    @After
    fun teardown() {
        testApp.exit(wmHelper)
        browserApp.exit(wmHelper)
    }

    private fun clickButton(text: String) =
        DeviceHelpers.waitForObj(By.text(text)) { "Can't find a $text button on first run dialog" }
            .click()

    private fun verifyFirstRunDialogIsShown() = By.text(STAY_IN_APP_TEXT).assertVisible()

    private fun verifyFirstRunDialogIsNotShown() = By.text(STAY_IN_APP_TEXT).assertInvisible()

    private fun verifyAppIsVisible() = By.pkg(testApp.packageName).assertVisible()

    private fun verifyAppIsInvisible() = By.pkg(testApp.packageName).assertInvisible()

    private fun verifyBrowserIsVisible() = By.pkg(browserApp.packageName).assertVisible()

    private companion object {
        const val ASK_ME_LATER_TEXT = "Ask me later"
        const val STAY_IN_APP_TEXT = "Stay in app"
        const val OPEN_IN_BROWSER_TEXT = "Open in browser"

        const val FEATURE_CONFIG_NAME = "config_appToWebActivePrompting"
    }
}
