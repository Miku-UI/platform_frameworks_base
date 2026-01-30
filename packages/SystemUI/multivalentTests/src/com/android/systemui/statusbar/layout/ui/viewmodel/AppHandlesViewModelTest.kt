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

package com.android.systemui.statusbar.layout.ui.viewmodel

import android.content.testableContext
import android.graphics.Rect
import android.graphics.Region
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.concurrency.fakeExecutor
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.backgroundScope
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.statusbar.layout.StatusBarAppHandleTracking
import com.android.systemui.testKosmos
import com.android.wm.shell.windowdecor.viewholder.AppHandleIdentifier
import com.android.wm.shell.windowdecor.viewholder.AppHandleIdentifier.AppHandleWindowingMode.APP_HANDLE_WINDOWING_MODE_BUBBLE
import com.android.wm.shell.windowdecor.viewholder.AppHandleIdentifier.AppHandleWindowingMode.APP_HANDLE_WINDOWING_MODE_FULLSCREEN
import com.android.wm.shell.windowdecor.viewholder.AppHandleIdentifier.AppHandleWindowingMode.APP_HANDLE_WINDOWING_MODE_SPLIT_SCREEN
import com.google.common.truth.Truth.assertThat
import java.util.Optional
import kotlin.test.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class AppHandlesViewModelTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()

    private val Kosmos.underTest by Kosmos.Fixture { kosmos.appHandlesViewModel }

    @Before
    fun setUp() {
        kosmos.underTest.activateIn(kosmos.testScope)
    }

    @Test
    @EnableFlags(StatusBarAppHandleTracking.FLAG_NAME)
    fun appHandleBounds_noAppHandlesProvided_empty() =
        kosmos.runTest {
            val viewModelWithNoAppHandles =
                AppHandlesViewModel(
                    backgroundScope = backgroundScope,
                    sysuiMainExecutor = fakeExecutor,
                    appHandles = Optional.empty(),
                    thisDisplayId = testableContext.displayId,
                )

            assertThat(viewModelWithNoAppHandles.appHandleBounds).isEmpty()
        }

    @Test
    @EnableFlags(StatusBarAppHandleTracking.FLAG_NAME)
    fun appHandleBounds_empty() =
        kosmos.runTest {
            fakeAppHandles.setAppHandles(emptyMap())

            assertThat(underTest.appHandleBounds).isEmpty()
        }

    @Test
    @EnableFlags(StatusBarAppHandleTracking.FLAG_NAME)
    fun appHandleBounds_notForThisDisplay_empty() =
        kosmos.runTest {
            val taskId = 10
            val rect = Rect(1, 2, 3, 4)
            fakeAppHandles.setAppHandles(
                mapOf(
                    taskId to
                        AppHandleIdentifier(
                            rect = rect,
                            displayId = testableContext.displayId + 2,
                            taskId = taskId,
                            windowingMode = APP_HANDLE_WINDOWING_MODE_FULLSCREEN,
                        )
                )
            )

            assertThat(underTest.appHandleBounds).isEmpty()
        }

    @Test
    @EnableFlags(StatusBarAppHandleTracking.FLAG_NAME)
    fun appHandleBounds_forThisDisplay_hasBounds() =
        kosmos.runTest {
            val taskId = 10
            val rect = Rect(1, 2, 3, 4)
            fakeAppHandles.setAppHandles(
                mapOf(
                    taskId to
                        AppHandleIdentifier(
                            rect = rect,
                            displayId = testableContext.displayId,
                            taskId = taskId,
                            windowingMode = APP_HANDLE_WINDOWING_MODE_FULLSCREEN,
                        )
                )
            )

            assertThat(underTest.appHandleBounds).containsExactly(rect)
        }

    @Test
    @EnableFlags(StatusBarAppHandleTracking.FLAG_NAME)
    fun appHandleBounds_multipleForThisDisplay_hasAll() =
        kosmos.runTest {
            val taskId1 = 10
            val rect1 = Rect(1, 2, 3, 4)

            val taskId2 = 20
            val rect2 = Rect(10, 20, 30, 40)

            fakeAppHandles.setAppHandles(
                mapOf(
                    taskId1 to
                        AppHandleIdentifier(
                            rect = rect1,
                            displayId = testableContext.displayId,
                            taskId = taskId1,
                            windowingMode = APP_HANDLE_WINDOWING_MODE_BUBBLE,
                        ),
                    taskId2 to
                        AppHandleIdentifier(
                            rect = rect2,
                            displayId = testableContext.displayId,
                            taskId = taskId2,
                            windowingMode = APP_HANDLE_WINDOWING_MODE_SPLIT_SCREEN,
                        ),
                )
            )

            assertThat(underTest.appHandleBounds).containsExactly(rect1, rect2)
        }

    @Test
    @DisableFlags(StatusBarAppHandleTracking.FLAG_NAME)
    fun appHandleBounds_emptyIfFlagDisabled() =
        kosmos.runTest {
            val taskId = 10
            val rect = Rect(1, 2, 3, 4)
            fakeAppHandles.setAppHandles(
                mapOf(
                    taskId to
                        AppHandleIdentifier(
                            rect = rect,
                            displayId = testableContext.displayId,
                            taskId = taskId,
                            windowingMode = APP_HANDLE_WINDOWING_MODE_FULLSCREEN,
                        )
                )
            )

            assertThat(underTest.appHandleBounds).isEmpty()
        }

    @Test
    @DisableFlags(StatusBarAppHandleTracking.FLAG_NAME)
    fun touchableExclusionRegion_emptyIfFlagDisabled() =
        kosmos.runTest {
            val taskId = 10
            val rect = Rect(1, 2, 3, 4)
            fakeAppHandles.setAppHandles(
                mapOf(
                    taskId to
                        AppHandleIdentifier(
                            rect = rect,
                            displayId = testableContext.displayId,
                            taskId = taskId,
                            windowingMode = APP_HANDLE_WINDOWING_MODE_FULLSCREEN,
                        )
                )
            )

            assertThat(underTest.touchableExclusionRegion.isEmpty)
        }

    @Test
    @EnableFlags(StatusBarAppHandleTracking.FLAG_NAME)
    fun touchableExclusionRegion_emptyRegionIfNoAppHandleBoundsReported() =
        kosmos.runTest {
            fakeAppHandles.setAppHandles(emptyMap())
            assertThat(underTest.touchableExclusionRegion.isEmpty)
        }

    @Test
    @EnableFlags(StatusBarAppHandleTracking.FLAG_NAME)
    fun touchableExclusionRegion_regionContainsSingleAppHandleBounds() =
        kosmos.runTest {
            val taskId = 10
            val rect = Rect(1, 2, 3, 4)
            fakeAppHandles.setAppHandles(
                mapOf(
                    taskId to
                        AppHandleIdentifier(
                            rect = rect,
                            displayId = testableContext.displayId,
                            taskId = taskId,
                            windowingMode = APP_HANDLE_WINDOWING_MODE_FULLSCREEN,
                        )
                )
            )

            underTest.touchableExclusionRegion.containsExactly(rect)
        }

    @Test
    @EnableFlags(StatusBarAppHandleTracking.FLAG_NAME)
    fun touchableExclusionRegion_regionContainsMultipleAppHandleBounds() =
        kosmos.runTest {
            val taskId1 = 10
            val rect1 = Rect(1, 2, 3, 4)

            val taskId2 = 20
            val rect2 = Rect(5, 6, 7, 8)
            fakeAppHandles.setAppHandles(
                mapOf(
                    taskId1 to
                        AppHandleIdentifier(
                            rect = rect1,
                            displayId = testableContext.displayId,
                            taskId = taskId1,
                            windowingMode = APP_HANDLE_WINDOWING_MODE_SPLIT_SCREEN,
                        ),
                    taskId2 to
                        AppHandleIdentifier(
                            rect = rect2,
                            displayId = testableContext.displayId,
                            taskId = taskId2,
                            windowingMode = APP_HANDLE_WINDOWING_MODE_SPLIT_SCREEN,
                        ),
                )
            )

            underTest.touchableExclusionRegion.containsExactly(rect1, rect2)
        }

    @Test
    @EnableFlags(StatusBarAppHandleTracking.FLAG_NAME)
    fun touchableExclusionRegion_notForThisDisplay_empty() =
        kosmos.runTest {
            val taskId = 10
            val rect = Rect(1, 2, 3, 4)
            fakeAppHandles.setAppHandles(
                mapOf(
                    taskId to
                        AppHandleIdentifier(
                            rect = rect,
                            displayId = testableContext.displayId + 2,
                            taskId = taskId,
                            windowingMode = APP_HANDLE_WINDOWING_MODE_FULLSCREEN,
                        )
                )
            )

            assertThat(underTest.touchableExclusionRegion.isEmpty)
        }

    /** Checks that region is equal to the union of the given rects. */
    private fun Region.containsExactly(vararg rects: Rect) {
        val intersect = Region.obtain()
        rects.forEach { intersect.op(it, intersect, Region.Op.UNION) }
        assertEquals(intersect, this)
        intersect.recycle()
    }
}
