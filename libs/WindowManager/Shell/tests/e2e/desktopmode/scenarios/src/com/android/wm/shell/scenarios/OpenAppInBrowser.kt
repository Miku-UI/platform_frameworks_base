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

import android.app.Instrumentation
import android.tools.Rotation
import android.tools.device.apphelpers.BrowserAppHelper
import android.tools.device.apphelpers.GmailAppHelper
import android.tools.traces.parsers.WindowManagerStateHelper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.android.server.wm.flicker.helpers.DesktopModeAppHelper
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

/**
 * Base scenario to test open app in browser from the app handle menu in full screen mode and app
 * header menu in desktop mode.
 */
@Ignore("Test Base Class")
abstract class OpenAppInBrowser(val rotation: Rotation = Rotation.ROTATION_0) :
    TestScenarioBase(rotation) {

    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val wmHelper = WindowManagerStateHelper(instrumentation)
    private val device = UiDevice.getInstance(instrumentation)
    private val mailApp = DesktopModeAppHelper(GmailAppHelper(instrumentation))
    private val browserApp = BrowserAppHelper(instrumentation)

    @Before
    fun setup() {
        mailApp.launchViaIntent(wmHelper)
    }

    @Test
    open fun triggerInFullScreen() {
        mailApp.clickOpenAppInBrowserButton(wmHelper, device, isDesktop = false)
        wmHelper.StateSyncBuilder().withFullScreenApp(browserApp).waitForAndVerify()
    }

    @Test
    open fun triggerInDesktopMode() {
        mailApp.enterDesktopMode(wmHelper, device)
        mailApp.clickOpenAppInBrowserButton(wmHelper, device, isDesktop = true)
        wmHelper
            .StateSyncBuilder()
            .withAppTransitionIdle()
            .withTopVisibleApp(browserApp)
            .waitForAndVerify()
    }

    @After
    fun teardown() {
        // The test opens mail website. We want to make sure to clear storage
        // (and remove all opened windows) to prevent affecting other tests.
        browserApp.clearStorage()
        browserApp.exit(wmHelper)
        mailApp.exit(wmHelper)
    }
}
