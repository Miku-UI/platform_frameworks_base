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

package com.android.wm.shell.windowdecor

import android.app.ActivityManager.RunningTaskInfo
import android.graphics.Rect
import android.graphics.Region
import android.os.Looper
import android.testing.AndroidTestingRunner
import android.view.Display
import android.view.Display.DEFAULT_DISPLAY
import android.view.SurfaceControl
import android.view.WindowInsets
import android.window.WindowContainerToken
import android.window.WindowContainerTransaction
import android.window.WindowContainerTransaction.HierarchyOp.HIERARCHY_OP_TYPE_ADD_INSETS_FRAME_PROVIDER
import android.window.WindowContainerTransaction.HierarchyOp.HIERARCHY_OP_TYPE_REMOVE_INSETS_FRAME_PROVIDER
import androidx.test.filters.SmallTest
import com.android.testing.wm.util.StubTransaction
import com.android.wm.shell.ShellTaskOrganizer
import com.android.wm.shell.ShellTestCase
import com.android.wm.shell.TestHandler
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.desktopmode.DesktopTestHelpers.createFreeformTask
import com.android.wm.shell.transition.Transitions
import com.android.wm.shell.windowdecor.caption.CaptionController
import com.android.wm.shell.windowdecor.caption.TestCaptionController
import com.android.wm.shell.windowdecor.common.viewhost.WindowDecorViewHost
import com.android.wm.shell.windowdecor.common.viewhost.WindowDecorViewHostSupplier
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.doReturn
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Tests for [WindowDecoration2].
 *
 * Build/Install/Run: atest WMShellUnitTests:WindowDecoration2Tests
 */
@SmallTest
@RunWith(AndroidTestingRunner::class)
class WindowDecoration2Tests : ShellTestCase() {

    private val mockDisplayController = mock<DisplayController>()
    private val mockTaskSurface = mock<SurfaceControl>()
    private val mockSurfaceControl = mock<SurfaceControl>()
    private val mockShellTaskOrganizer = mock<ShellTaskOrganizer>()
    private val mockTransitions = mock<Transitions>()
    private val mockDisplay = mock<Display>()
    private val mockCaptionController = mock<CaptionController<WindowDecorLinearLayout>>()
    private val mockCaptionRelayoutResult = mock<CaptionController.CaptionRelayoutResult>()
    private val mockViewHost = mock<WindowDecorViewHost>()
    private val mockViewHostSupplier = mock<WindowDecorViewHostSupplier<WindowDecorViewHost>>()

    private val testHandler = TestHandler(Looper.getMainLooper())
    private val testDispatcher = StandardTestDispatcher(TestCoroutineScheduler())
    private val testScope = TestScope(testDispatcher)
    private val stubStartTransaction = StubTransaction()
    private val stubFinishTransaction = StubTransaction()

    @Before
    fun setUp() {
        whenever(mockDisplayController.getDisplay(DEFAULT_DISPLAY)).thenReturn(mockDisplay)
        whenever(mockCaptionController.relayout(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(mockCaptionRelayoutResult)
        doReturn(mockSurfaceControl).whenever(mockViewHost).surfaceControl
        doReturn(mockViewHost).whenever(mockViewHostSupplier).acquire(any(), any())
    }

    @Test
    fun releaseViewsIfNeeded_viewsReleasedWhenFontWeightChanges() =
        testScope.runTest {
            val taskInfo1 =
                createFreeformTask().apply {
                    isVisible = true
                    configuration.fontWeightAdjustment = 300
                }
            TestWindowDecoration(taskInfo1).use {
                it.relayout(
                    taskInfo = taskInfo1,
                    captionType = CaptionController.CaptionType.APP_HEADER,
                    hasGlobalFocus = true,
                    displayExclusionRegion = Region.obtain(),
                )
                val firstRelayoutController = it.captionController as TestCaptionController

                val taskInfo2 =
                    createFreeformTask().apply {
                        isVisible = true
                        configuration.fontWeightAdjustment = 500
                    }
                it.relayout(
                    taskInfo = taskInfo2,
                    captionType = CaptionController.CaptionType.APP_HEADER,
                    hasGlobalFocus = true,
                    displayExclusionRegion = Region.obtain(),
                )
                val secondRelayoutController = it.captionController as TestCaptionController

                assertThat(firstRelayoutController).isNotEqualTo(secondRelayoutController)
                assertThat(firstRelayoutController.closed).isTrue()
                assertThat(secondRelayoutController.closed).isFalse()
            }
        }

    @Test
    fun captionTypeChanged_swapInsetSources_shouldHappenInASingleTransaction() =
        testScope.runTest {
            val taskInfo = createFreeformTask().apply { isVisible = true }
            TestWindowDecoration(taskInfo).use {
                it.relayout(
                    taskInfo = taskInfo,
                    captionType = CaptionController.CaptionType.APP_HEADER,
                    hasGlobalFocus = true,
                    isCaptionVisible = true,
                    displayExclusionRegion = Region.obtain(),
                )
                val appHeaderController = it.captionController as TestCaptionController

                val pinnedWct = WindowContainerTransaction()
                it.relayout(
                    taskInfo = taskInfo,
                    captionType = CaptionController.CaptionType.APP_PINNED,
                    hasGlobalFocus = true,
                    isCaptionVisible = true,
                    wct = pinnedWct,
                    displayExclusionRegion = Region.obtain(),
                )
                val appPinnedController = it.captionController as TestCaptionController

                assertThat(appHeaderController).isNotEqualTo(appPinnedController)
                assertThat(appHeaderController.closed).isTrue()
                assertThat(appPinnedController.closed).isFalse()
                pinnedWct.assertRemoveInsetsSource(taskInfo.token)
                pinnedWct.assertAddInsetsSource(taskInfo.token)
            }
        }

    private fun WindowContainerTransaction.assertAddInsetsSource(token: WindowContainerToken) {
        assertThat(
                hierarchyOps.any { op ->
                    op.type == HIERARCHY_OP_TYPE_ADD_INSETS_FRAME_PROVIDER &&
                        op.container == token.asBinder() &&
                        op.insetsFrameProvider?.type == WindowInsets.Type.captionBar()
                }
            )
            .isTrue()
    }

    private fun WindowContainerTransaction.assertRemoveInsetsSource(token: WindowContainerToken) {
        assertThat(
                hierarchyOps.any { op ->
                    op.type == HIERARCHY_OP_TYPE_REMOVE_INSETS_FRAME_PROVIDER &&
                        op.container == token.asBinder() &&
                        op.insetsFrameProvider?.type == WindowInsets.Type.captionBar()
                }
            )
            .isTrue()
    }

    private inner class TestWindowDecoration(taskInfo: RunningTaskInfo) :
        WindowDecoration2<WindowDecorLinearLayout>(
            taskInfo,
            context,
            mockDisplayController,
            mockTaskSurface,
            { mockSurfaceControl },
            mockShellTaskOrganizer,
            testHandler,
            testScope,
            mockTransitions,
        ) {

        override fun calculateValidDragArea(): Rect? = null

        override fun createCaptionController(
            captionType: CaptionController.CaptionType
        ): CaptionController<WindowDecorLinearLayout>? =
            if (captionType == CaptionController.CaptionType.NO_CAPTION) {
                null
            } else {
                TestCaptionController(
                    captionType,
                    taskInfo,
                    mockViewHostSupplier,
                    testScope = testScope,
                )
            }

        override fun relayout(
            taskInfo: RunningTaskInfo,
            hasGlobalFocus: Boolean,
            displayExclusionRegion: Region,
        ) {}

        /** Creates a [RelayoutParams] object and calls [WindowDecoration2.relayout]. */
        fun relayout(
            taskInfo: RunningTaskInfo,
            captionType: CaptionController.CaptionType = CaptionController.CaptionType.NO_CAPTION,
            hasGlobalFocus: Boolean = true,
            displayExclusionRegion: Region = Region.obtain(),
            isCaptionVisible: Boolean = false,
            wct: WindowContainerTransaction = WindowContainerTransaction(),
        ) {
            relayout(
                RelayoutParams(
                    runningTaskInfo = taskInfo,
                    captionType = captionType,
                    hasGlobalFocus = hasGlobalFocus,
                    displayExclusionRegion = displayExclusionRegion,
                    isCaptionVisible = isCaptionVisible,
                ),
                stubStartTransaction,
                stubFinishTransaction,
                wct,
                mockTaskSurface,
            )
        }
    }
}
