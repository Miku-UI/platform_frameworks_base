/*
 * Copyright (C) 2025 The Android Open Source Project
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

import android.os.UserHandle
import android.platform.test.annotations.RequiresFlagsEnabled
import android.view.IWindowManager
import android.view.WindowManagerGlobal
import androidx.test.uiautomator.By.desc
import androidx.test.uiautomator.Condition
import androidx.test.uiautomator.UiDevice
import com.android.compatibility.common.util.UiAutomatorUtils2.waitFindObject
import com.android.window.flags.Flags
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import platform.test.desktop.DisplayDevice
import platform.test.desktop.DisplayPeripheral
import platform.test.desktop.DisplaySize
import platform.test.desktop.PeripheralType

@Ignore("Test Base Class")
@RequiresFlagsEnabled(Flags.FLAG_ENABLE_DESKTOP_WINDOWING_MODE)
abstract class ScaleDensityForExternalDisplay : SettingsConnectedDisplayTestBase() {

    private val wm: IWindowManager = requireNotNull(WindowManagerGlobal.getWindowManagerService())

    private val settingsResources =
        instrumentation.context.packageManager.getResourcesForApplication(SETTINGS_PACKAGE_NAME)
    private val increaseDensityDescription = getSettingsString(INCREASE_DENSITY_DESCRIPTION_RES)
    private val decreaseDensityDescription = getSettingsString(DECREASE_DENSITY_DESCRIPTION_RES)

    @Before
    fun setUp() {
        wm.clearForcedDisplayDensityForUser(addedDisplayId, UserHandle.myUserId())
    }

    @After
    fun tearDown() {
        wm.clearForcedDisplayDensityForUser(addedDisplayId, UserHandle.myUserId())
    }

    @Test
    fun increaseDensity() {
        val initialDensity = wm.getBaseDisplayDensity(addedDisplayId)

        waitFindObject(desc(increaseDensityDescription)).click()
        val currentDensity =
            waitForDensityCondition(
                addedDisplayId,
                "Density Increased",
                predicate = { it > initialDensity },
            )

        assertThat(initialDensity).isLessThan(currentDensity)
    }

    @Test
    fun decreaseDensity() {
        val initialDensity = wm.getBaseDisplayDensity(addedDisplayId)

        waitFindObject(desc(decreaseDensityDescription)).click()
        val currentDensity =
            waitForDensityCondition(
                addedDisplayId,
                "Density Decreased",
                predicate = { it < initialDensity },
            )

        assertThat(initialDensity).isGreaterThan(currentDensity)
    }

    @Test
    fun restoreDensityAfterReconnection() {
        val initialDensity = wm.getBaseDisplayDensity(addedDisplayId)

        waitFindObject(desc(increaseDensityDescription)).click()

        val lastDensity =
            waitForDensityCondition(
                addedDisplayId,
                "Density Increased before disconnect",
                predicate = { it > initialDensity },
            )

        peripheralDeviceRule.disconnectAll()
        device.waitForIdle()

        val response =
            peripheralDeviceRule.requestPeripherals(
                DisplayPeripheral(PeripheralType.SIMULATED, DisplaySize.SIZE_1080P)
            )
        val idAfterReconnection =
            response.devices.filterIsInstance<DisplayDevice>().first().displayId

        device.waitForIdle()
        val densityAfterReconnection = wm.getBaseDisplayDensity(idAfterReconnection)

        assertThat(lastDensity).isEqualTo(densityAfterReconnection)
    }

    private fun getSettingsString(resName: String): String {
        val identifier = settingsResources.getIdentifier(resName, "string", SETTINGS_PACKAGE_NAME)
        return settingsResources.getString(identifier)
    }

    /**
     * Waits for the base display density of the given displayId to meet a predicate.
     *
     * @param displayId The ID of the display to check.
     * @param conditionName A descriptive name for the condition being waited for.
     * @param timeoutMs The maximum time to wait in milliseconds.
     * @param predicate A function that takes the current density and returns true if the condition
     *   is met.
     * @return The density value that met the condition.
     */
    private fun waitForDensityCondition(
        displayId: Int,
        conditionName: String,
        timeoutMs: Long = WAIT_FOR_DENSITY_TIMEOUT,
        predicate: (currentDensity: Int) -> Boolean,
    ): Int {
        var lastDensity = -1
        val densityCondition =
            object : Condition<UiDevice, Boolean> {
                override fun apply(device: UiDevice): Boolean {
                    try {
                        val currentDensity = wm.getBaseDisplayDensity(displayId)
                        lastDensity = currentDensity
                        return predicate(currentDensity)
                    } catch (e: Exception) {
                        return false
                    }
                }
            }

        val fulfilled = device.wait(densityCondition, timeoutMs)
        if (!fulfilled) {
            fail(
                "Condition '$conditionName' not met within $timeoutMs ms for display $displayId. " +
                    "Last density: $lastDensity"
            )
        }
        return lastDensity
    }

    private companion object {
        const val SETTINGS_PACKAGE_NAME = "com.android.settings"
        const val INCREASE_DENSITY_DESCRIPTION_RES = "screen_zoom_make_larger_desc"
        const val DECREASE_DENSITY_DESCRIPTION_RES = "screen_zoom_make_smaller_desc"
        const val WAIT_FOR_DENSITY_TIMEOUT: Long = 3000
    }
}
