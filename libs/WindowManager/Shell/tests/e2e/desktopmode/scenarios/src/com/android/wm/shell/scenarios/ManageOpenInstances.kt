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
import android.tools.helpers.findObject
import android.tools.traces.parsers.WindowManagerStateHelper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.android.launcher3.tapl.LauncherInstrumentation
import com.android.server.wm.flicker.helpers.DesktopModeAppHelper
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

/**
 * Base scenario to test manage open instances from taskbar, app header and app menu when more than
 * 3 instances of the same app are opened in Desktop Mode.
 */
@Ignore("Test Base Class")
abstract class ManageOpenInstances(val rotation: Rotation = Rotation.ROTATION_0) :
    TestScenarioBase(rotation) {

    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val tapl = LauncherInstrumentation()
    private val wmHelper = WindowManagerStateHelper(instrumentation)
    private val device = UiDevice.getInstance(instrumentation)
    private val browserAppHelper = BrowserAppHelper(instrumentation)
    val browserDesktopAppHelper = DesktopModeAppHelper(browserAppHelper)

    @Before
    fun setup() {
        browserAppHelper.launchViaIntent(
            wmHelper,
            BrowserAppHelper.getSpecialBrowserIntent(BrowserAppHelper.EBAY_INTENT),
        )
        browserAppHelper.closePopupsIfNeeded(device)
        browserDesktopAppHelper.enterDesktopMode(wmHelper, device)
        tapl.showTaskbarIfHidden()
        openNewWindowFromTaskbarMenu()
        openNewWindowFromTaskbarMenu()
        wmHelper.StateSyncBuilder().withAppTransitionIdle().waitForAndVerify()
    }

    @Test
    open fun triggerFromTaskbar() {
        tapl.launchedAppState.taskbar.getAppIcon(browserAppHelper.appName).openMenu()
        clickFirstWindowFromManageWindows()
        wmHelper.StateSyncBuilder().withAppTransitionIdle().waitForAndVerify()
        findObject(By.descContains(BrowserAppHelper.SHARE_BUTTON_DESC))
    }

    @Test
    open fun triggerFromAppMenu() {
        browserAppHelper.openThreeDotsMenu()
        browserAppHelper.clickManageWindowsInMenu()
        wmHelper.StateSyncBuilder().withAppTransitionIdle().waitForAndVerify()
        findObject(By.text(MANAGE_WINDOWS_DIALOG_TEXT))
    }

    @Test
    open fun triggerFromAppHeader() {
        browserDesktopAppHelper.clickOpenMenuButton(wmHelper)
        findObject(By.res(SYSTEMUI_PACKAGE, MANAGE_WINDOWS_FROM_HEADER_ID)).also { it.click() }
        wmHelper.StateSyncBuilder().withAppTransitionIdle().waitForAndVerify()
        // We need to verify that open instances container exist by its class name and package name,
        // because the layer name is empty.
        findObject(By.clazz(OPEN_INSTANCES_CONTAINER_CLASS).pkg(SYSTEMUI_PACKAGE))
    }

    @After
    fun teardown() {
        // The test launches new windows. We want to make sure to clear storage (and remove all
        // opened windows) to prevent hitting the Chrome window limit.
        browserAppHelper.clearStorage()
        browserDesktopAppHelper.exit(wmHelper)
    }

    private fun clickFirstWindowFromManageWindows() {
        findObject(By.descContains(MANAGE_WINDOWS_FROM_TASKBAR_DESC)).also { it.click() }
        // Selects the initial opening window by its index, which prevents regressions by future
        // alterations to the content description.
        val openWindowObject = device.wait(Until.findObjects(By.clazz(OPEN_WINDOW_CLASS)), TIMEOUT)
        openWindowObject.get(FIRST_OPEN_WINDOW_INDEX).also { it.click() }
    }

    private fun openNewWindowFromTaskbarMenu() {
        tapl.launchedAppState.taskbar.getAppIcon(browserAppHelper.appName).openMenu()
        findObject(By.descContains(NEW_WINDOW_DESC)).also { it.click() }
    }

    private companion object {
        // The index of the first open window in the manage windows container
        const val FIRST_OPEN_WINDOW_INDEX = 2
        const val MANAGE_WINDOWS_FROM_HEADER_ID = "manage_windows_button"
        const val MANAGE_WINDOWS_FROM_TASKBAR_DESC = "Manage Windows"
        const val MANAGE_WINDOWS_DIALOG_TEXT = "Manage windows"
        const val NEW_WINDOW_DESC = "New Window"
        const val OPEN_INSTANCES_CONTAINER_CLASS = "android.widget.ScrollView"
        const val OPEN_WINDOW_CLASS = "android.view.SurfaceView"
        const val SYSTEMUI_PACKAGE = "com.android.systemui"
        const val TIMEOUT: Long = 5000L
    }
}
