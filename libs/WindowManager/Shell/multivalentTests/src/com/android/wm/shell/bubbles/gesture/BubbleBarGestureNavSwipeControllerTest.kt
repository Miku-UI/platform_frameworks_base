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

package com.android.wm.shell.bubbles.gesture

import android.content.Context
import android.view.ViewConfiguration
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.wm.shell.bubbles.BubbleData
import com.android.wm.shell.bubbles.BubblePositioner
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.verify
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never

/**
 * Tests for [BubbleBarGestureNavSwipeController].
 *
 * Build/Install/Run:
 * - atest WMShellRobolectricTests:BubbleBarGestureNavSwipeControllerTest
 */
@SmallTest
@RunWith(AndroidJUnit4::class)
class BubbleBarGestureNavSwipeControllerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val bubbleData = mock<BubbleData> { on { isExpanded } doReturn true }
    private val bubblePositioner =
        mock<BubblePositioner> {
            on { getExpandedViewHeightForBubbleBar(false) } doReturn EXPANDED_VIEW_HEIGHT
        }
    private val minFlingVelocity: Float =
        ViewConfiguration.get(context).scaledMinimumFlingVelocity.toFloat()

    /** Verifies that the bubble is dismissed if the swipe velocity is high enough */
    @Test
    fun dismissBubbleWhenSwipeUp_swipeVelocity() {
        val controller = BubbleBarGestureNavSwipeController(context, bubbleData, bubblePositioner)
        val listener = controller.swipeListener
        listener.onUp(-minFlingVelocity - 1, -minFlingVelocity - 1)
        verify(bubbleData).setExpanded(false)
    }

    /** Verifies that the bubble is dismissed if the swipe distance is high enough */
    @Test
    fun dismissBubbleWhenSwipeUp_swipeAmount() {
        val controller = BubbleBarGestureNavSwipeController(context, bubbleData, bubblePositioner)
        val listener = controller.swipeListener
        listener.onMove(-500f, -500f)
        listener.onUp(0f, 0f)
        verify(bubbleData).setExpanded(false)
    }

    /** Verifies that the bubble is not dismissed if the swipe velocity is not high enough */
    @Test
    fun bubbleNotDismissedWhenSwiped_swipeVelocityBelowThreshold() {
        val controller = BubbleBarGestureNavSwipeController(context, bubbleData, bubblePositioner)
        val listener = controller.swipeListener
        listener.onUp(-minFlingVelocity + 1, -minFlingVelocity + 1)
        verify(bubbleData, never()).setExpanded(false)
    }

    /** Verifies that the bubble is not dismissed if the swipe distance is not high enough */
    @Test
    fun bubbleNotDismissedWhenSwiped_swipeAmountBelowThreshold() {
        val controller = BubbleBarGestureNavSwipeController(context, bubbleData, bubblePositioner)
        val listener = controller.swipeListener
        listener.onMove(0f, 0f)
        listener.onUp(0f, 0f)
        verify(bubbleData, never()).setExpanded(false)
    }

    companion object {
        private const val EXPANDED_VIEW_HEIGHT = 1652
    }
}
