/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.wm.shell

import android.app.Instrumentation
import android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM
import android.platform.test.rule.EnsureDeviceSettingsRule
import android.platform.test.rule.NavigationModeRule
import android.platform.test.rule.PressHomeRule
import android.platform.test.rule.UnlockScreenRule
import android.tools.NavBar
import android.tools.Rotation
import android.tools.device.apphelpers.MessagingAppHelper
import android.tools.flicker.rules.ArtifactSaverRule
import android.tools.flicker.rules.ChangeDisplayOrientationRule
import android.tools.flicker.rules.LaunchAppRule
import android.tools.flicker.rules.RemoveAllTasksButHomeRule
import android.tools.traces.component.ComponentNameMatcher
import android.tools.traces.parsers.WindowManagerStateHelper
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.android.launcher3.tapl.BaseOverview
import com.android.launcher3.tapl.LauncherInstrumentation
import java.io.IOException
import org.junit.rules.RuleChain

object Utils {
    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)

    private val settingsResources =
        instrumentation.context.packageManager.getResourcesForApplication(SETTINGS_PACKAGE_NAME)

    /**
     * A helper method to initialize a [RuleChain] to set up [navigationMode] and screen [rotation].
     *
     * @param navigationMode the navigation mode to set up, either one of [NavBar.MODE_GESTURAL] or
     *   [NavBar.MODE_3BUTTON].
     * @param rotation the screen rotation to apply, which defaults to [Rotation.ROTATION_0]
     * @return the [RuleChain] to set up the [navigationMode] and [rotation].
     */
    fun testSetupRuleFunctional(navigationMode: NavBar, rotation: Rotation = Rotation.ROTATION_0) =
        testSetupRule(navigationMode, rotation, skipRulesForFlickerTest = true)

    /**
     * A helper method to initialize a [RuleChain] to set up [navigationMode] and screen [rotation].
     *
     * @param navigationMode the navigation mode to set up, either one of [NavBar.MODE_GESTURAL] or
     *   [NavBar.MODE_3BUTTON].
     * @param rotation the screen rotation to apply, which defaults to [Rotation.ROTATION_0]
     * @param skipFlickerRules whether to skip rules needed only for flicker tests
     * @return the [RuleChain] to set up the [navigationMode] and [rotation].
     */
    fun testSetupRule(
        navigationMode: NavBar,
        rotation: Rotation = Rotation.ROTATION_0,
        skipRulesForFlickerTest: Boolean = false,
    ): RuleChain {
        return RuleChain.outerRule(ArtifactSaverRule())
            .around(UnlockScreenRule())
            .around(NavigationModeRule(navigationMode.value, false))
            .around(
                if (skipRulesForFlickerTest) {
                    RuleChain.emptyRuleChain()
                } else {
                    LaunchAppRule(
                        MessagingAppHelper(instrumentation),
                        clearCacheAfterParsing = false,
                    )
                }
            )
            .around(RemoveAllTasksButHomeRule())
            .around(
                ChangeDisplayOrientationRule(
                    rotation,
                    resetOrientationAfterTest = false,
                    clearCacheAfterParsing = false,
                )
            )
            .around(PressHomeRule())
            .around(EnsureDeviceSettingsRule())
    }

    /**
     * Resets the frozen recent tasks list (ie. commits the quickswitch to the current task and
     * reorders the current task to the end of the recents list).
     */
    fun resetFreezeRecentTaskList() {
        try {
            device.executeShellCommand("wm reset-freeze-recent-tasks")
        } catch (e: IOException) {
            Log.e("TestUtils", "Failed to reset frozen recent tasks list", e)
        }
    }

    fun isInDesktopFirstMode(wmHelper: WindowManagerStateHelper, displayId: Int): Boolean =
        wmHelper.currentState.wmState
            .getDisplay(displayId)
            ?.getTaskDisplayArea(ComponentNameMatcher.LAUNCHER)
            ?.windowingMode == WINDOWING_MODE_FREEFORM

    /** Clears remembered bounds for all packages. */
    fun clearAllRememberedDesktopBounds() {
        try {
            device.executeShellCommand("wm shell desktopmode clearAllRememberedBounds")
        } catch (e: IOException) {
            Log.e("TestUtils", "Failed to clear all remembered bounds", e)
        }
    }

    /** Clears remembered first-run prompt acked packages for the given user and package. */
    fun clearAppToWebFirstRunPromptAcked(packageName: String) {
        try {
            device.executeShellCommand(
                "wm shell apptoweb clearAppToWebFirstRunPromptAcked $packageName"
            )
        } catch (e: IOException) {
            Log.e("TestUtils", "Failed to clear first-run prompt acked packages", e)
        }
    }

    /** Sets the app links user selection for the given user and package. */
    fun setAppLinksUserSelection(packageName: String) {
        try {
            device.executeShellCommand(
                "pm set-app-links-user-selection --user cur --package $packageName true all"
            )
        } catch (e: IOException) {
            Log.e("TestUtils", "Failed to set app links user selection", e)
        }
    }

    /** Sets the app links user selection for the given user and package. */
    fun setAppLinksAllowed(packageName: String) {
        try {
            device.executeShellCommand(
                "pm set-app-links-allowed --user cur --package $packageName true"
            )
        } catch (e: IOException) {
            Log.e("TestUtils", "Failed to set app links user selection", e)
        }
    }

    /**
     * Convenient function supporting overview switch on both cases where home screen is shown
     * behind freeform tasks or completely hidden behind a task.
     *
     * @see [LauncherInstrumentation.shouldShowHomeBehindDesktop]
     */
    fun switchToOverview(tapl: LauncherInstrumentation): BaseOverview {
        // If home screen is shown behind freeform tasks, overview couldn't be launched by
        // interacting with launched apps, i.e. swiping up will not work, and 3 nav bars don't
        // appear on the taskbar. Therefore, it has to be opened through keyboard shortcut
        return if (tapl.shouldShowHomeBehindDesktop()) {
            tapl.workspace.openOverviewFromActionPlusTabKeyboardShortcut()
        } else tapl.launchedAppState.switchToOverview()
    }

    /**
     * Returns the boolean value of the given settings resource.
     *
     * @param resName the name of the settings resource to retrieve
     * @return the boolean value of the settings resource, or null if the resource is not found
     */
    fun getSettingsBoolean(resName: String): Boolean? {
        val identifier = settingsResources.getIdentifier(resName, "bool", SETTINGS_PACKAGE_NAME)
        if (identifier == 0) {
            Log.w("TestUtils", "Boolean setting resource not found for '$resName'.")
            return null
        }

        return try {
            settingsResources.getBoolean(identifier)
        } catch (e: android.content.res.Resources.NotFoundException) {
            Log.w("TestUtils", "Boolean setting not found for '$resName'.", e)
            null
        }
    }

    /**
     * Returns the boolean value of the given resource name.
     *
     * @param resName the name of the resource to retrieve
     * @return the boolean value of the resource, or null if the resource is not found
     */
    fun getBooleanConfig(resName: String): Boolean? {
        val resources = instrumentation.context.resources
        val identifier = resources.getIdentifier(resName, "bool", "android")
        if (identifier == 0) {
            Log.w("TestUtils", "Boolean resource not found for '$resName'.")
            return null
        }
        return try {
            return resources.getBoolean(identifier)
        } catch (e: android.content.res.Resources.NotFoundException) {
            Log.w("TestUtils", "Boolean resource not found for '$resName'.", e)
            null
        }
    }

    private const val SETTINGS_PACKAGE_NAME = "com.android.settings"
}
