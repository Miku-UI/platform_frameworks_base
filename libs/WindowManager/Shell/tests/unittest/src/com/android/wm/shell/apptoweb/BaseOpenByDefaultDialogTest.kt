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

package com.android.wm.shell.apptoweb

import android.app.ActivityManager.RunningTaskInfo
import android.content.ComponentName
import android.content.Context
import android.content.pm.verify.domain.DomainVerificationManager
import android.graphics.Bitmap
import android.platform.test.annotations.EnableFlags
import android.view.Display
import android.view.SurfaceControl
import android.view.SurfaceControlViewHost
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.window.flags.Flags
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.TestRunningTaskInfoBuilder
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.compatui.DialogAnimationController
import com.android.wm.shell.compatui.DialogContainerSupplier
import com.android.wm.shell.transition.Transitions
import com.android.wm.shell.windowdecor.common.WindowDecorTaskResourceLoader
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.TestScope
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Tests for [BaseOpenByDefaultDialog].
 *
 * Build/Install/Run: atest WMShellUnitTests:BaseOpenByDefaultDialogTest
 */
@SmallTest
@RunWith(AndroidJUnit4::class)
class BaseOpenByDefaultDialogTest : ShellTestCase() {

    private val context = mock<Context>()
    private val userContext = mock<Context>()
    private val transitions = mock<Transitions>()
    private val taskSurface = mock<SurfaceControl>()
    private val displayController = mock<DisplayController>()
    private val taskResourceLoader = mock<WindowDecorTaskResourceLoader>()
    private val transaction = mock<SurfaceControl.Transaction>()
    private val listener = mock<DialogLifecycleListener>()
    private val domainVerificationManager = mock<DomainVerificationManager>()
    private val mockAnimationController = mock<DialogAnimationController<TestDialogView>>()
    private val surfaceControlViewHost = mock<SurfaceControlViewHost>()
    private val surfaceControlViewHostFactory = mock<SurfaceControlViewHostFactory>()
    private val display = mock<Display>()

    // Mock for T which must be View and DialogContainerSupplier
    abstract class TestDialogView(context: Context) : View(context), DialogContainerSupplier

    private val mockDialog = mock<TestDialogView>()

    private lateinit var taskInfo: RunningTaskInfo
    private lateinit var testDialog: TestableOpenByDefaultDialog

    private val testScope = TestScope()

    @Before
    fun setUp() {
        taskInfo =
            TestRunningTaskInfoBuilder()
                .setBaseActivity(ComponentName(PACKAGE_NAME, "test"))
                .build()
        // Ensure bounds are non-zero for layout params
        taskInfo.configuration.windowConfiguration.bounds.set(0, 0, 100, 100)
        taskInfo.displayId = 0

        whenever(userContext.getSystemService(DomainVerificationManager::class.java))
            .thenReturn(domainVerificationManager)
        whenever(surfaceControlViewHostFactory.invoke(any(), any(), any(), any()))
            .thenReturn(surfaceControlViewHost)
        whenever(displayController.getDisplay(anyInt())).thenReturn(display)

        testDialog = TestableOpenByDefaultDialog()
    }

    @Test
    fun showDialogWindow() {
        testDialog.show()

        assertNotNull(testDialog.viewHost)
    }

    @Test
    fun showDialogWindow_afterCloseMenu_doesNotShow() {
        testDialog.close()

        testDialog.show()

        assertNull(testDialog.viewHost)
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_OPEN_BY_DEFAULT_DIALOG_FOCUS_BUGFIX)
    fun showAndCloseMenu_requestInputFocus() {
        testDialog.show()
        verify(surfaceControlViewHost).requestInputFocus(true)

        testDialog.close()
        verify(surfaceControlViewHost).requestInputFocus(false)
    }

    private inner class TestableOpenByDefaultDialog :
        BaseOpenByDefaultDialog<TestDialogView>(
            context,
            userContext,
            transitions,
            taskInfo,
            taskSurface,
            displayController,
            taskResourceLoader,
            { transaction },
            testScope,
            listener,
            mockAnimationController,
            surfaceControlViewHostFactory,
        ) {
        override val dialogName = "TestDialog"

        override fun createDialog() {
            dialog = mockDialog
        }

        override fun onAnimationEnded() {}

        override fun bindAppInfo(appIconBitmap: Bitmap, appName: CharSequence) {}

        fun show() = super.showDialogWindow()

        fun close() = super.closeMenu()
    }

    companion object {
        private const val PACKAGE_NAME = "com.foo"
    }
}
