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

import android.graphics.Rect
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.statusbar.policy.Clock
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

@SmallTest
@RunWith(AndroidJUnit4::class)
class StatusBarBoundsViewModelTest : SysuiTestCase() {
    private var clockBounds = Rect()
    private var startContainerBounds = Rect()

    private val kosmos =
        testKosmos().useUnconfinedTestDispatcher().apply {
            mockStatusBarClockView =
                mock<Clock> {
                    on { getBoundsOnScreen(any()) } doAnswer
                        {
                            val boundsOutput = it.arguments[0] as Rect
                            boundsOutput.set(clockBounds)
                            return@doAnswer
                        }
                }
            mockStatusBarStartSideContainerView =
                mock<View> {
                    on { getBoundsOnScreen(any()) } doAnswer
                        {
                            val boundsOutput = it.arguments[0] as Rect
                            boundsOutput.set(startContainerBounds)
                            return@doAnswer
                        }
                }
        }

    private val Kosmos.underTest by Kosmos.Fixture { kosmos.statusBarBoundsViewModel }

    private val clockLayoutChangeListener: View.OnLayoutChangeListener
        get() {
            val captor = argumentCaptor<View.OnLayoutChangeListener>()
            verify(kosmos.mockStatusBarClockView).addOnLayoutChangeListener(captor.capture())
            return captor.firstValue
        }

    private val startContainerLayoutChangeListener: View.OnLayoutChangeListener
        get() {
            val captor = argumentCaptor<View.OnLayoutChangeListener>()
            verify(kosmos.mockStatusBarStartSideContainerView)
                .addOnLayoutChangeListener(captor.capture())
            return captor.firstValue
        }

    @Before
    fun setUp() {
        kosmos.underTest.activateIn(kosmos.testScope)
    }

    @Test
    fun startSideContainerBounds_getsUpdatedBounds() =
        kosmos.runTest {
            val firstRect = Rect(1, 2, 3, 4)
            startContainerBounds = firstRect
            startContainerLayoutChangeListener.onLayoutChange(
                mockStatusBarStartSideContainerView,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
            )

            assertThat(underTest.startSideContainerBounds).isEqualTo(firstRect)

            val newRect = Rect(5, 6, 7, 8)
            startContainerBounds = newRect
            startContainerLayoutChangeListener.onLayoutChange(
                mockStatusBarStartSideContainerView,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
            )

            assertThat(underTest.startSideContainerBounds).isEqualTo(newRect)
        }

    @Test
    fun clockBounds_getsUpdatedWhenClockBoundsChanged() =
        kosmos.runTest {
            val firstRect = Rect(1, 2, 3, 4)
            clockBounds = firstRect
            clockLayoutChangeListener.onLayoutChange(mockStatusBarClockView, 0, 0, 0, 0, 0, 0, 0, 0)

            assertThat(underTest.clockBounds).isEqualTo(firstRect)

            val newRect = Rect(5, 6, 7, 8)
            clockBounds = newRect
            clockLayoutChangeListener.onLayoutChange(mockStatusBarClockView, 0, 0, 0, 0, 0, 0, 0, 0)

            assertThat(underTest.clockBounds).isEqualTo(newRect)
        }

    @Test
    fun dateBounds_getsUpdatedWhenUpdateDateBoundsCalled() =
        kosmos.runTest {
            val firstRect = Rect(1, 2, 3, 4)
            underTest.updateDateBounds(firstRect)

            assertThat(underTest.dateBounds).isEqualTo(firstRect)

            val newRect = Rect(5, 6, 7, 8)
            underTest.updateDateBounds(newRect)

            assertThat(underTest.dateBounds).isEqualTo(newRect)
        }
}
