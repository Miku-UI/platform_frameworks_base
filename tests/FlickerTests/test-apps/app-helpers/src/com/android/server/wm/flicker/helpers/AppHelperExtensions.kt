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

package com.android.server.wm.flicker.helpers

import android.tools.device.apphelpers.StandardAppHelper
import android.tools.helpers.FIND_TIMEOUT
import android.tools.traces.parsers.WindowManagerStateHelper
import android.tools.traces.parsers.WindowManagerStateHelper.StateSyncBuilder
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until

/**
 * Clicks a button inside the app under test and waits for a WM state sync.
 *
 * @param device the [UiDevice] to interact with the under test app.
 * @param wmHelper the [WindowManagerStateHelper] for state verification.
 * @param buttonResId the resource ID of the button to click.
 * @param withSyncCondition extension function to configure sync conditions.
 */
fun StandardAppHelper.clickButtonAndWaitForSync(
    device: UiDevice,
    wmHelper: WindowManagerStateHelper,
    buttonResId: String,
    withSyncCondition: StateSyncBuilder.() -> StateSyncBuilder,
) {
    val buttonSelector = By.res(packageName, buttonResId)
    val button = device.wait(Until.findObject(buttonSelector), FIND_TIMEOUT)
        ?: error("Button '$buttonResId' not found, device may be in an unexpected state.")

    button.click()

    device.wait(Until.gone(buttonSelector), FIND_TIMEOUT)
    wmHelper.StateSyncBuilder().withSyncCondition().waitForAndVerify()
}
