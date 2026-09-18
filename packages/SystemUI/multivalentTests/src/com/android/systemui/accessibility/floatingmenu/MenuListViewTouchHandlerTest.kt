/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.systemui.accessibility.floatingmenu

import android.testing.TestableLooper
import android.view.MotionEvent
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.accessibility.dialog.AccessibilityTarget
import com.android.systemui.SysuiTestCase
import com.android.systemui.accessibility.MotionEventHelper
import com.android.systemui.kosmos.runTest
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

/** Tests for [MenuListViewTouchHandler]. */
@RunWith(AndroidJUnit4::class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
@SmallTest
class MenuListViewTouchHandlerTest : SysuiTestCase() {
    private val stubTargets: List<AccessibilityTarget> = listOf(mock<AccessibilityTarget>())

    private val motionEventHelper = MotionEventHelper()
    private val kosmos = testKosmosNew()

    @Before
    fun setUp() {
        with(kosmos) {
            menuView.translationX = 0f
            menuView.translationY = 0f

            menuView.targetFeaturesView.adapter =
                AccessibilityTargetAdapter(stubTargets, context, mock())
            // this prevents vertical move actions from being skipped
            menuView.targetFeaturesView.overScrollMode = View.OVER_SCROLL_NEVER
        }
    }

    @Test
    fun onActionDownEvent_shouldCancelAnimations() =
        kosmos.runTest {
            interceptTouchEvent(MotionEvent.ACTION_DOWN, offset = 0f, eventTime = 1)

            assertThat(menuAnimationController.areAnimationsRunning()).isFalse()
        }

    @Test
    fun onActionMoveEvent_notConsumedEvent_shouldMoveToPosition() =
        kosmos.runTest {
            // since dragToInteractView has not set up any circles,
            // a move event should never be consumed.
            val offset = 100f
            interceptTouchEvent(MotionEvent.ACTION_DOWN, offset = 0f, eventTime = 1)
            interceptTouchEvent(MotionEvent.ACTION_MOVE, offset, eventTime = 3)

            assertThat(menuView.translationX).isEqualTo(offset)
            assertThat(menuView.translationY).isEqualTo(offset)
        }

    @Test
    fun onActionMoveEvent_shouldShowInteractView() =
        kosmos.runTest {
            val offset = 100f
            interceptTouchEvent(MotionEvent.ACTION_DOWN, offset = 0f, eventTime = 1)
            interceptTouchEvent(MotionEvent.ACTION_MOVE, offset, eventTime = 3)

            assertThat(dragToInteractView.isShowing).isTrue()
        }

    @Test
    fun dragAndDrop_shouldFlingMenuThenSpringToEdge() =
        kosmos.runTest {
            val offset = 100f
            interceptTouchEvent(MotionEvent.ACTION_DOWN, offset = 0f, eventTime = 1)
            interceptTouchEvent(MotionEvent.ACTION_MOVE, offset, eventTime = 3)
            interceptTouchEvent(MotionEvent.ACTION_UP, offset, eventTime = 5)

            assertThat(menuAnimationController.areAnimationsRunning()).isTrue()
            assertThat(menuView.isMoveToTucked).isFalse()
        }

    @Test
    fun dragMenuOutOfBoundsAndDrop_moveToLeftEdge_shouldMoveToEdgeAndHide() =
        kosmos.runTest {
            val offset = -100f
            interceptTouchEvent(MotionEvent.ACTION_DOWN, offset = 0f, eventTime = 1)
            interceptTouchEvent(MotionEvent.ACTION_MOVE, offset, eventTime = 3)
            interceptTouchEvent(MotionEvent.ACTION_UP, offset, eventTime = 5)

            assertThat(menuAnimationController.areAnimationsRunning()).isTrue()
            assertThat(menuView.isMoveToTucked).isTrue()
        }

    @Test
    fun receiveActionDownMotionEvent_verifyOnActionDownEnd() =
        kosmos.runTest {
            val onActionDownEnd: Runnable = mock {}
            menuListViewTouchHandler.setOnActionDownEndListener(onActionDownEnd)

            interceptTouchEvent(MotionEvent.ACTION_DOWN, offset = 0f, eventTime = 1)

            verify(onActionDownEnd).run()
        }

    @After
    fun tearDown() =
        with(kosmos) {
            motionEventHelper.recycleEvents()
            menuAnimationController.mPositionAnimations.values.forEach { it.cancel() }
        }

    fun interceptTouchEvent(eventAction: Int, offset: Float, eventTime: Long) =
        with(kosmos) {
            val x: Float = menuView.translationX + offset
            val y: Float = menuView.translationY + offset
            val stubEvent =
                motionEventHelper.obtainMotionEvent(/* downTime= */ 0, eventTime, eventAction, x, y)
            menuListViewTouchHandler.onInterceptTouchEvent(menuView.targetFeaturesView, stubEvent)
        }
}
