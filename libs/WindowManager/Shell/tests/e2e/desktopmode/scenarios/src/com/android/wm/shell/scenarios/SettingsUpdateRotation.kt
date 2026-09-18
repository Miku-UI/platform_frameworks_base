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

import android.platform.uiautomatorhelpers.DeviceHelpers.waitForObj
import android.view.Display.INVALID_DISPLAY
import androidx.test.uiautomator.By
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertNotNull
import org.junit.After
import org.junit.Ignore
import org.junit.Test

/** Base scenario test to set rotation via Connected Display Settings */
@Ignore("Test Base Class")
abstract class SettingsUpdateRotation : SettingsConnectedDisplayTestBase() {

    @Test
    fun updateRotation() {
        val rotationPreference = getRotationPreference()
        rotationPreference.click()

        waitForObj(By.text(ROTATION_90_TEXT), UIAUTOMATOR_TIMEOUT) {
                "Rotation selection dialog did not appear"
            }
            .click()

        wmHelper.StateSyncBuilder().withAppTransitionIdle().waitForAndVerify()
        assertNotNull(
            rotationPreference.findObject(By.text(ROTATION_90_TEXT)),
            "Rotation value did not update to 90°",
        )
        assertThat(displayManager.getDisplay(addedDisplayId).rotation).isEqualTo(ROTATION_90_VAL)
    }

    @After
    fun teardown() {
        if (addedDisplayId == INVALID_DISPLAY) {
            return
        }
        instrumentation.uiAutomation.executeShellCommand(
            "wm user-rotation -d $addedDisplayId lock 0"
        )
    }

    private fun getRotationPreference() =
        waitForObj(
            By.clazz("android.widget.RelativeLayout").hasDescendant(By.text(ROTATION_TEXT))
        ) {
            "Could not find the 'Rotation' preference, have `selectDisplay(displayId)` been " +
                "called and display is external display?"
        }

    private companion object {
        const val ROTATION_TEXT = "Rotation"
        const val ROTATION_90_TEXT = "90°"
        const val ROTATION_90_VAL = 1
    }
}
