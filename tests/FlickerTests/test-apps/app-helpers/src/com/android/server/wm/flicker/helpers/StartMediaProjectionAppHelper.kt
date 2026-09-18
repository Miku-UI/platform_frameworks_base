/*
 * Copyright (C) 2024 The Android Open Source Project
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

import android.app.Instrumentation
import android.tools.device.apphelpers.StandardAppHelper
import android.tools.helpers.SYSTEMUI_PACKAGE
import android.tools.helpers.retryIfStaleObject
import android.tools.traces.component.ComponentNameMatcher
import android.tools.traces.parsers.WindowManagerStateHelper
import android.tools.traces.parsers.toFlickerComponent
import android.util.Log
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.UiObjectNotFoundException
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import com.android.server.wm.flicker.testapp.ActivityOptions

class StartMediaProjectionAppHelper
@JvmOverloads
constructor(
    instr: Instrumentation,
    launcherName: String = ActivityOptions.StartMediaProjectionActivity.LABEL,
    component: ComponentNameMatcher =
        ActivityOptions.StartMediaProjectionActivity.COMPONENT.toFlickerComponent()
) : StandardAppHelper(instr, launcherName, component) {
    fun startEntireScreenMediaProjection(wmHelper: WindowManagerStateHelper) {
        clickStartMediaProjectionButton()
        chooseEntireScreenOption()
        startScreenSharing()
        wmHelper.StateSyncBuilder().withAppTransitionIdle().waitForAndVerify()
    }

    fun startSingleAppMediaProjection(
        wmHelper: WindowManagerStateHelper,
        targetApp: StandardAppHelper
    ) {
        clickStartMediaProjectionButton()
        chooseSingleAppOption()
        startScreenSharing()
        selectTargetApp(targetApp.appName)
        wmHelper
            .StateSyncBuilder()
            .withAppTransitionIdle()
            .withWindowSurfaceAppeared(targetApp)
            .waitForAndVerify()
    }

    fun startSingleAppMediaProjectionWithExtraIntent(
        wmHelper: WindowManagerStateHelper,
        targetApp: StandardAppHelper
    ) {
        clickStartMediaProjectionWithExtraIntentButton()
        chooseSingleAppOption()
        startScreenSharing()
        selectTargetApp(targetApp.appName)
        wmHelper
            .StateSyncBuilder()
            .withAppTransitionIdle()
            .withHomeActivityVisible()
            .waitForAndVerify()
    }

    fun startSingleAppMediaProjectionFromRecents(
        wmHelper: WindowManagerStateHelper,
        targetApp: StandardAppHelper,
        recentTasksIndex: Int = 0,
    ) {
        clickStartMediaProjectionButton()
        chooseSingleAppOption()
        startScreenSharing()
        selectTargetAppRecent(recentTasksIndex)
        wmHelper
            .StateSyncBuilder()
            .withAppTransitionIdle()
            .withWindowSurfaceAppeared(targetApp)
            .waitForAndVerify()
    }

    fun startMediaProjectionAppSelector(wmHelper: WindowManagerStateHelper) {
        val mediaProjectionAppSelector =
            ComponentNameMatcher(SYSTEMUI_PACKAGE, APP_SELECTOR_CLASS_NAME)

        chooseSingleAppOption()
        startScreenSharing()
        wmHelper
            .StateSyncBuilder()
            .withWindowSurfaceAppeared(mediaProjectionAppSelector)
            .waitForAndVerify()
    }

    private fun clickStartMediaProjectionButton() {
        findObject(By.res(packageName, START_MEDIA_PROJECTION_BUTTON_ID)).also { it.click() }
    }

    private fun clickStartMediaProjectionWithExtraIntentButton() {
        findObject(By.res(packageName, START_MEDIA_PROJECTION_NEW_INTENT_BUTTON_ID)).also { it.click() }
    }

    private fun chooseEntireScreenOption() {
        findObject(By.res(SYSTEMUI_PACKAGE, SCREEN_SHARE_SPINNER_ID)).also { it.click() }
        // Screen recording is "Record entire screen" while screen sharing is "Share entire screen"
        findObject(By.textContains("entire screen")).also { it.click() }
    }

    private fun chooseSingleAppOption() {
        findObject(By.res(SYSTEMUI_PACKAGE, SCREEN_SHARE_SPINNER_ID)).also { it.click() }
        // Screen recording is "Record one app" while screen sharing is "Share one app"
        retryIfStaleObject { findObject(By.textContains("one app")).also { it.click() } }
    }

    private fun startScreenSharing() {
        retryIfStaleObject { findObject(By.res(ACCEPT_RESOURCE_ID)).also { it.click() } }
    }

    private fun selectTargetApp(targetAppName: String) {
        val targetApp = uiDevice.wait(Until.findObject(By.text(targetAppName)), TIMEOUT)
        if (targetApp != null) {
            targetApp.click()
            return
        }
        Log.d(TAG, "Unable to find target app immediately so will attempt to scroll")

        // Scroll to to find target app to launch then click app icon it to start capture
        val scrollable = UiScrollable(UiSelector().scrollable(true))
        try {
            scrollable.scrollForward()
            if (!scrollable.scrollIntoView(UiSelector().text(targetAppName))) {
                Log.e(TAG, "Didn't find target app when scrolling")
                return
            }
        } catch (e: UiObjectNotFoundException) {
            Log.d(TAG, "There was no scrolling (UI may not be scrollable)", e)
        }

        findObject(By.text(targetAppName)).also { it.click() }
    }

    private fun selectTargetAppRecent(recentTasksIndex: Int) {
        // Scroll to to find target app to launch then click app icon it to start capture
        val recentsTasksRecycler =
            findObject(By.res(SYSTEMUI_PACKAGE, MEDIA_PROJECTION_RECENT_TASKS))
        recentsTasksRecycler.children[recentTasksIndex].also { it.click() }
    }

    private fun findObject(selector: BySelector): UiObject2 =
        uiDevice.wait(Until.findObject(selector), TIMEOUT) ?: error("Can't find object $selector")

    private companion object {
        const val TAG: String = "StartMediaProjectionAppHelper"
        const val TIMEOUT: Long = 5000L
        const val ACCEPT_RESOURCE_ID: String = "android:id/button1"
        const val START_MEDIA_PROJECTION_BUTTON_ID: String = "button_start_mp"
        const val START_MEDIA_PROJECTION_NEW_INTENT_BUTTON_ID: String = "button_start_mp_new_intent"
        const val SCREEN_SHARE_SPINNER_ID: String = "screen_share_mode_options"
        const val MEDIA_PROJECTION_RECENT_TASKS: String = "media_projection_recent_tasks_recycler"
        const val APP_SELECTOR_CLASS_NAME: String =
            "com.android.systemui.mediaprojection.appselector.MediaProjectionAppSelectorActivity"
    }
}
