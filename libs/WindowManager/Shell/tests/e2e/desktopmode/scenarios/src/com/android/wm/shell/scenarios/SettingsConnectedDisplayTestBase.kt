/*
 * Copyright 2025 The Android Open Source Project
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

import android.app.ActivityOptions
import android.app.Instrumentation
import android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN
import android.hardware.display.DisplayManager
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.platform.test.rule.ScreenRecordRule
import android.platform.uiautomatorhelpers.DeviceHelpers.waitForObj
import android.platform.uiautomatorhelpers.DurationUtils.platformAdjust
import android.tools.NavBar
import android.tools.Rotation
import android.tools.device.apphelpers.SettingsHelper
import android.tools.traces.parsers.WindowManagerStateHelper
import android.view.Display.DEFAULT_DISPLAY
import android.view.Display.INVALID_DISPLAY
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import com.android.launcher3.tapl.LauncherInstrumentation
import com.android.server.display.feature.flags.Flags.FLAG_ENABLE_DISPLAY_CONTENT_MODE_MANAGEMENT
import com.android.server.wm.flicker.helpers.DesktopModeAppHelper
import com.android.settings.flags.Flags
import com.android.window.flags.Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE
import com.android.wm.shell.Utils
import com.android.wm.shell.shared.desktopmode.DesktopState
import java.time.Duration
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import platform.test.desktop.DisplayDevice
import platform.test.desktop.DisplayPeripheral
import platform.test.desktop.DisplaySize
import platform.test.desktop.Peripheral
import platform.test.desktop.PeripheralDeviceTestRule
import platform.test.desktop.PeripheralType

/** Base test class for connected display settings CUJ. */
@Ignore("Base Test Class")
@RequiresFlagsEnabled(
    FLAG_ENABLE_DISPLAY_CONTENT_MODE_MANAGEMENT,
    FLAG_ENABLE_DESKTOP_WINDOWING_MODE,
)
abstract class SettingsConnectedDisplayTestBase(
    private val peripheralRequest: Peripheral =
        DisplayPeripheral(PeripheralType.SIMULATED, DisplaySize.SIZE_1080P)
) {

    val instrumentation: Instrumentation = getInstrumentation()
    val tapl = LauncherInstrumentation()
    val wmHelper = WindowManagerStateHelper(instrumentation)
    val device = UiDevice.getInstance(instrumentation)
    val settingsApp = DesktopModeAppHelper(SettingsHelper(instrumentation))
    val displayManager = instrumentation.context.getSystemService(DisplayManager::class.java)
    var addedDisplayId: Int = INVALID_DISPLAY

    @get:Rule(order = 0) val checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()
    @get:Rule(order = 1)
    val testSetupRule = Utils.testSetupRule(NavBar.MODE_GESTURAL, Rotation.ROTATION_0)
    @get:Rule(order = 2) val peripheralDeviceRule = PeripheralDeviceTestRule()
    @get:Rule(order = 3)
    val screenRecordRule = ScreenRecordRule(/* keepTestLevelRecordingOnSuccess= */ false)

    @Before
    fun baseSetup() {
        val desktopState = DesktopState.fromContext(instrumentation.context)
        assumeTrue(desktopState.isDesktopModeSupportedOnDisplay(DEFAULT_DISPLAY))

        // Start with fullscreen to ensure all components are visible
        val options = ActivityOptions.makeBasic()
        options.setLaunchWindowingMode(WINDOWING_MODE_FULLSCREEN)
        settingsApp.launchViaIntent(wmHelper, options = options)

        val response = peripheralDeviceRule.requestPeripherals(peripheralRequest)

        val displayDevice =
            response.devices.filterIsInstance<DisplayDevice>().firstOrNull()
                ?: error("Failed to connect simulated display peripheral")
        addedDisplayId = displayDevice.displayId

        wmHelper.StateSyncBuilder().withDesktopModeOnDisplay(addedDisplayId).waitForAndVerify()
        openExternalDisplayPage(addedDisplayId)
    }

    @After
    fun baseTeardown() {
        settingsApp.exit(wmHelper)
    }

    /**
     * Display settings are split per-display which can be navigated differently:
     * - config_show_top_level_device_category (true): Specific external display page can be
     *   navigated directly from the display list
     * - config_show_top_level_device_category (false): External display page is navigated through
     *   floating toolbar selection
     */
    fun selectDisplay(displayId: Int) {
        val displayName =
            if (displayId == DEFAULT_DISPLAY) {
                BUILTIN_DISPLAY_NAME
            } else {
                displayManager.getDisplay(displayId).name
            }
        waitForObj(By.text(displayName), UIAUTOMATOR_TIMEOUT) {
                "Can't find the toolbar for display#$displayId ($displayName)"
            }
            .click()
    }

    private fun openExternalDisplayPageFromConnectedDevicesPage() {
        waitForObj(By.text(CONNECTED_DEVICES_TEXT), UIAUTOMATOR_TIMEOUT) {
                "Can't find 'Connected devices' setting"
            }
            .click()
        waitForObj(By.text(EXTERNAL_DISPLAY_TEXT), UIAUTOMATOR_TIMEOUT) {
                "Can't find 'External displays' setting"
            }
            .click()
    }

    private fun openExternalDisplayPageDesktopFromDevicePage() {
        waitForObj(By.text(DEVICE_TEXT), UIAUTOMATOR_TIMEOUT) { "Can't find 'Device' setting" }
            .click()
        waitForObj(By.text(DISPLAY_TEXT), UIAUTOMATOR_TIMEOUT) { "Can't find 'Display' setting" }
            .click()
    }

    private fun openExternalDisplayPage(displayId: Int) {
        if (shouldShowTopLevelDeviceCategory()) {
            openExternalDisplayPageDesktopFromDevicePage()
        } else {
            openExternalDisplayPageFromConnectedDevicesPage()
        }
        selectDisplay(displayId)
    }

    private fun shouldShowTopLevelDeviceCategory(): Boolean {
        val flagValue = Flags.showTopLevelDeviceCategory()
        val showCategory = Utils.getSettingsBoolean(CONFIG_SHOW_TOP_LEVEL_DEVICE) ?: false
        return flagValue && showCategory
    }

    companion object {
        const val BUILTIN_DISPLAY_NAME = "Built-in display"
        const val CONFIG_SHOW_TOP_LEVEL_DEVICE = "config_show_top_level_device_category"
        const val CONNECTED_DEVICES_TEXT = "Connected devices"
        const val EXTERNAL_DISPLAY_TEXT = "External displays"
        const val DEVICE_TEXT = "Device"
        const val DISPLAY_TEXT = "Display"
        val UIAUTOMATOR_TIMEOUT = Duration.ofSeconds(10).platformAdjust()
    }
}
