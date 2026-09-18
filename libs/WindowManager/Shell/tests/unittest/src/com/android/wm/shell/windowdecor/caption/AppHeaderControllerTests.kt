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

package com.android.wm.shell.windowdecor.caption

import android.app.ActivityManager.RunningTaskInfo
import android.app.assist.AssistContent
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Looper
import android.platform.test.annotations.EnableFlags
import android.testing.AndroidTestingRunner
import android.testing.TestableLooper
import android.view.Display
import android.view.SurfaceControl
import android.window.WindowContainerTransaction
import com.android.testing.wm.util.StubTransaction
import com.android.window.flags.Flags
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.TestHandler
import com.android.wm.shell.TestShellExecutor
import com.android.wm.shell.apptoweb.AppToWebRepositoryImpl
import com.android.wm.shell.apptoweb.AssistContentRequester
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.common.MultiInstanceHelper
import com.android.wm.shell.desktopmode.DesktopUserRepositories
import com.android.wm.shell.shared.desktopmode.FakeDesktopConfig
import com.android.wm.shell.shared.desktopmode.FakeDesktopState
import com.android.wm.shell.splitscreen.SplitScreenController
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.transition.Transitions
import com.android.wm.shell.windowdecor.HandleMenu
import com.android.wm.shell.windowdecor.LayoutMenu
import com.android.wm.shell.windowdecor.LayoutMenuFactory
import com.android.wm.shell.windowdecor.WindowDecoration2.RelayoutParams
import com.android.wm.shell.windowdecor.WindowDecorationActions
import com.android.wm.shell.windowdecor.WindowDecorationTestHelper.TestWindowDecorTaskResourceLoader
import com.android.wm.shell.windowdecor.WindowDecorationTestHelper.createOpaqueAppHeaderTask
import com.android.wm.shell.windowdecor.WindowManagerWrapper
import com.android.wm.shell.windowdecor.caption.CaptionController.CaptionType
import com.android.wm.shell.windowdecor.common.DecorThemeUtil
import com.android.wm.shell.windowdecor.common.viewhost.WindowDecorViewHost
import com.android.wm.shell.windowdecor.common.viewhost.WindowDecorViewHostSupplier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever

/**
 * Tests for [appHeaderController].
 *
 * Build/Install/Run: atest WMShellUnitTests:AppHeaderControllerTests
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidTestingRunner::class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
class AppHeaderControllerTests : ShellTestCase() {
    private val mockSplitScreenController = mock<SplitScreenController>()
    private val mockDisplayController = mock<DisplayController>()
    private val mockWindowDecorationActions = mock<WindowDecorationActions>()
    private val mockTransitions = mock<Transitions>()
    private val mockTaskOrganizer = mock<ShellTaskOrganizer>()
    private val mockWindowManagerWrapper = mock<WindowManagerWrapper>()
    private val mockMultiInstanceHelper = mock<MultiInstanceHelper>()
    private val mockHandleMenuFactory = spy(HandleMenu.HandleMenuFactory)
    private val mockLayoutMenuFactory = mock<LayoutMenuFactory>()
    private val mockDisplay = mock<Display>()
    private val mockViewHost = mock<WindowDecorViewHost>()
    private val mockViewHostSupplier = mock<WindowDecorViewHostSupplier<WindowDecorViewHost>>()
    private val mockAssistContentRequester = mock<AssistContentRequester>()
    private val mockAssistContent = mock<AssistContent>()

    private val testDispatcher = StandardTestDispatcher(TestCoroutineScheduler())
    private val testHandler = TestHandler(Looper.getMainLooper())
    private val testExecutor = TestShellExecutor()
    private val testScope = TestScope(testDispatcher)
    private val desktopState = FakeDesktopState()
    private val desktopConfig = FakeDesktopConfig()
    private val testTaskResourceLoader = TestWindowDecorTaskResourceLoader()
    private val shellInit = ShellInit(testExecutor)
    private val taskSurface = SurfaceControl()
    private val decorationSurface = SurfaceControl()

    private lateinit var userRepositories: DesktopUserRepositories
    private lateinit var appToWebRepository: AppToWebRepositoryImpl
    private lateinit var appHeaderController: AppHeaderController
    private lateinit var taskInfo: RunningTaskInfo

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        userRepositories =
            DesktopUserRepositories(
                shellInit,
                mock(),
                mock(),
                mock(),
                testScope.backgroundScope,
                testScope.backgroundScope,
                mock(),
                desktopState,
                desktopConfig,
            )
        appToWebRepository =
            AppToWebRepositoryImpl(
                context = context,
                assistContentRequester = mockAssistContentRequester,
                genericLinksParser = mock(),
                appToWebDatastoreRepository = mock(),
                mainCoroutineScope = testScope,
                bgCoroutineScope = testScope.backgroundScope,
                shellTaskOrganizer = mockTaskOrganizer,
                launcherApps = mock(),
                shellInit = shellInit,
                shellController = mock(),
                shellCommandHandler = mock(),
            )
        whenever(mockAssistContentRequester.requestAssistContent(anyInt(), any())).thenAnswer {
            invocation ->
            val callback = invocation.arguments[1] as AssistContentRequester.Callback
            callback.onAssistContentAvailable(mockAssistContent)
        }
        whenever(mockDisplayController.getDisplay(anyInt())).thenReturn(mockDisplay)
        whenever(mockViewHostSupplier.acquire(any(), any())).thenReturn(mockViewHost)
        shellInit.init()

        taskInfo = createOpaqueAppHeaderTask()
        appHeaderController = createAppHeaderController(taskInfo).apply { relayout(taskInfo) }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        testScope.cancel()
        testExecutor.flushAll()
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_WINDOW_DECORATION_REFACTOR)
    fun createHandleMenu_notCreatedWhenHandleMenuJobCreationActive() = runTest {
        appHeaderController.createHandleMenu(minimumInstancesFound = true)
        appHeaderController.createHandleMenu(minimumInstancesFound = true)
        testScope.advanceUntilIdle()

        // Verify menu was only created once
        mockHandleMenuFactory.verifyHandleMenuCreated()
    }

    @Test
    fun relayout_taskLosesFocus_closesMenus() = runTest {
        // Start focused
        val paramsFocused =
            RelayoutParams(
                runningTaskInfo = taskInfo,
                captionType = CaptionType.APP_HEADER,
                hasGlobalFocus = true,
                isCaptionVisible = true,
            )
        appHeaderController.relayout(
            paramsFocused,
            decorationSurface,
            mockDisplay,
            context,
            StubTransaction(),
            StubTransaction(),
            WindowContainerTransaction(),
        )

        // Open layout menu
        val mockLayoutMenu = mock<LayoutMenu>()
        whenever(
                mockLayoutMenuFactory.create(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            )
            .thenReturn(mockLayoutMenu)
        appHeaderController.createLayoutMenu()
        assertTrue(appHeaderController.isLayoutMenuActive)

        // Lose focus
        val paramsUnfocused =
            RelayoutParams(
                runningTaskInfo = taskInfo,
                captionType = CaptionType.APP_HEADER,
                hasGlobalFocus = false,
                isCaptionVisible = true,
            )
        appHeaderController.relayout(
            paramsUnfocused,
            decorationSurface,
            mockDisplay,
            context,
            StubTransaction(),
            StubTransaction(),
            WindowContainerTransaction(),
        )

        // Verify menu is closed
        assertFalse(appHeaderController.isLayoutMenuActive)
    }

    @Test
    fun openHandleMenu_isBrowserApp() = runTest {
        val packageName = "com.android.test_browser"
        val mockPackageManager = mock<PackageManager>()

        val resolveInfo = ResolveInfo()
        resolveInfo.activityInfo = mock()
        resolveInfo.handleAllWebDataURI = true

        mContext.setMockPackageManager(mockPackageManager)
        whenever(
                mockPackageManager.queryIntentActivitiesAsUser(
                    argThat { getPackage() == packageName },
                    any<Int>(),
                    any<Int>(),
                )
            )
            .thenReturn(listOf(resolveInfo))

        taskInfo.baseIntent = Intent()
        taskInfo.baseIntent.component = ComponentName.createRelative(packageName, ".MainActivity")

        appHeaderController.createHandleMenu(minimumInstancesFound = true)
        testScope.advanceUntilIdle()

        mockHandleMenuFactory.verifyHandleMenuCreated(appToWebData = { it?.isBrowserApp ?: false })
    }

    @Test
    fun openHandleMenu_isNotBrowserApp() = runTest {
        val packageName = "com.android.test_app"
        val mockPackageManager = mock<PackageManager>()

        val resolveInfo = ResolveInfo()
        resolveInfo.activityInfo = mock()
        resolveInfo.handleAllWebDataURI = false

        mContext.setMockPackageManager(mockPackageManager)
        whenever(
                mockPackageManager.queryIntentActivitiesAsUser(
                    argThat { getPackage() == packageName },
                    any<Int>(),
                    any<Int>(),
                )
            )
            .thenReturn(listOf(resolveInfo))

        taskInfo.baseIntent = Intent()
        taskInfo.baseIntent.component = ComponentName.createRelative(packageName, ".MainActivity")

        appHeaderController.createHandleMenu(minimumInstancesFound = true)
        testScope.advanceUntilIdle()

        mockHandleMenuFactory.verifyHandleMenuCreated(
            appToWebData = { !(it?.isBrowserApp ?: true) }
        )
    }

    private fun createAppHeaderController(taskInfo: RunningTaskInfo) =
        AppHeaderController(
            taskInfo = taskInfo,
            windowDecorViewHostSupplier = mockViewHostSupplier,
            userContext = context,
            transitions = mockTransitions,
            displayController = mockDisplayController,
            taskResourceLoader = testTaskResourceLoader,
            splitScreenController = mockSplitScreenController,
            desktopUserRepositories = userRepositories,
            taskOrganizer = mockTaskOrganizer,
            taskSurface = taskSurface,
            decorationSurface = decorationSurface,
            mainHandler = testHandler,
            mainDispatcher = mock(),
            mainScope = testScope,
            bgScope = testScope,
            windowManagerWrapper = mockWindowManagerWrapper,
            multiInstanceHelper = mockMultiInstanceHelper,
            desktopModeUiEventLogger = mock(),
            desktopState = desktopState,
            windowDecorationActions = mockWindowDecorationActions,
            decorWindowContext = context,
            gestureInterceptor = mock(),
            appToWebRepository = appToWebRepository,
            handleMenuFactory = mockHandleMenuFactory,
            layoutMenuFactory = mockLayoutMenuFactory,
            rootTaskDisplayAreaOrganizer = mock(),
            windowDecorCaptionRepository = mock(),
            onLongClickListener = mock(),
            onCaptionGenericMotionListener = mock(),
            syncQueue = mock(),
            focusTransitionObserver = mock(),
            decorThemeUtilFactory = DecorThemeUtil.Factory(),
        )

    private fun AppHeaderController.relayout(taskInfo: RunningTaskInfo) {
        relayout(
            params = RelayoutParams(taskInfo, CaptionType.APP_HEADER),
            parentContainer = decorationSurface,
            display = mock(),
            decorWindowContext = context,
            startT = StubTransaction(),
            finishT = StubTransaction(),
            wct = WindowContainerTransaction(),
        )
    }
}
