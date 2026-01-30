/*
 * Copyright (C) 2020 The Android Open Source Project
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

import android.app.ActivityOptions as ActivityOptionsForIntent
import android.app.Instrumentation
import android.content.Context
import android.tools.Rotation
import android.tools.helpers.FIND_TIMEOUT
import android.tools.helpers.IME_PACKAGE
import android.tools.traces.component.ComponentNameMatcher
import android.tools.traces.component.IComponentMatcher
import android.tools.traces.parsers.WindowManagerStateHelper
import android.tools.traces.parsers.toFlickerComponent
import android.util.Log
import android.view.WindowInsets.Type.ime
import android.view.WindowInsets.Type.navigationBars
import android.view.WindowInsets.Type.statusBars
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.android.server.wm.flicker.testapp.ActivityOptions.Ime.AutoFocusActivity
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.regex.Pattern

class ImeShownOnAppStartHelper
@JvmOverloads
constructor(
    instr: Instrumentation,
    private val rotation: Rotation,
    private val imePackageName: String = IME_PACKAGE,
    launcherName: String = AutoFocusActivity.LABEL,
    component: ComponentNameMatcher =
        AutoFocusActivity.COMPONENT.toFlickerComponent()
) : ImeAppHelper(instr, launcherName, component) {
    override fun openIME(wmHelper: WindowManagerStateHelper) {
        // do nothing (the app is focused automatically)
        waitIMEShown(wmHelper)
    }

    override fun launchViaIntent(
        wmHelper: WindowManagerStateHelper,
        launchedAppComponentMatcherOverride: IComponentMatcher?,
        action: String?,
        stringExtras: Map<String, String>,
        waitConditionsBuilder: WindowManagerStateHelper.StateSyncBuilder,
        options: ActivityOptionsForIntent?,
    ) {
        super.launchViaIntent(
            wmHelper,
            launchedAppComponentMatcherOverride,
            action,
            stringExtras,
            waitConditionsBuilder,
            options,
        )
        waitIMEShown(wmHelper)
    }

    override fun open() {
        val expectedPackage =
            if (rotation.isRotated()) {
                imePackageName
            } else {
                packageName
            }
        open(expectedPackage)
    }

    fun dismissDialog(wmHelper: WindowManagerStateHelper) {
        val dialog = uiDevice.wait(Until.findObject(By.text("Dialog for test")), FIND_TIMEOUT)

        // Pressing back key to dismiss the dialog
        if (dialog != null) {
            uiDevice.pressBack()
            wmHelper.StateSyncBuilder().withAppTransitionIdle().waitForAndVerify()
        }
    }

    fun getInsetsVisibleFromDialog(type: Int): Boolean {
        val insetsVisibilityTextView =
            uiDevice.wait(Until.findObject(By.res("android:id/text1")), FIND_TIMEOUT)
        if (insetsVisibilityTextView != null) {
            val visibility = insetsVisibilityTextView.text.toString()
            val matcher =
                when (type) {
                    ime() -> {
                        Pattern.compile("IME\\: (VISIBLE|INVISIBLE)").matcher(visibility)
                    }
                    statusBars() -> {
                        Pattern.compile("StatusBar\\: (VISIBLE|INVISIBLE)").matcher(visibility)
                    }
                    navigationBars() -> {
                        Pattern.compile("NavBar\\: (VISIBLE|INVISIBLE)").matcher(visibility)
                    }
                    else -> null
                }
            if (matcher != null && matcher.find()) {
                return matcher.group(1) == "VISIBLE"
            }
        }
        return false
    }

    /**
     * Obtains the bottom value of [android.view.WindowInsets.Type.ime] from the `textView`
     * UI object.
     */
    fun retrieveImeBottomInset(): Int =
        try {
            uiDevice.wait(
                Until.findObject(
                    By.res(
                        packageName,
                        AutoFocusActivity.RES_ID_IME_BOTTOM_INSET,
                    )
                ),
                FIND_TIMEOUT,
            ).text.toIntOrNull() ?: -1
        } catch (e: Throwable) {
            // On failure, dump the screen hierarchy for debugging
            dumpWindowHierarchyToFile("failure_dump.xml")

            // Re-throw the exception so the test is still marked as failed
            throw e
        }

    /**
     * Dumps the current window hierarchy to a file on the device's external storage.
     *
     * @param fileName The name of the file to save the dump to (e.g., "hierarchy_dump.xml").
     */
    private fun dumpWindowHierarchyToFile(fileName: String) {
        val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = context.cacheDir.path
        val file = File(directory, fileName)
        Log.d(TAG, "Dumping window hierarchy to: " + file.absolutePath)

        try {
            FileOutputStream(file).use { outputStream ->
                uiDevice.dumpWindowHierarchy(outputStream)
                Log.d(TAG, "Successfully dumped window hierarchy to " + file.absolutePath)
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to dump window hierarchy", e)
        }
    }

    companion object {
        private const val TAG = "ImeAutoFocusHelper"
    }
}
