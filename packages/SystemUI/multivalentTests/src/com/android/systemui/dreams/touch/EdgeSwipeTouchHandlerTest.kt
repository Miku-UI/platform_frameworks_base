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

package com.android.systemui.dreams.touch

import android.content.Context
import android.os.Handler
import android.os.fakeExecutorHandler
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.internal.policy.GestureNavigationSettingsObserver
import com.android.systemui.SysuiTestCase
import com.android.systemui.ambient.touch.TouchHandler
import com.android.systemui.dreams.DreamOverlayContainerView
import com.android.systemui.haptics.fake
import com.android.systemui.haptics.vibratorHelper
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testCase
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.navigationbar.gestural.GestureNavigationSettingsObserverFactory
import com.android.systemui.res.R
import com.android.systemui.settings.UserContextProvider
import com.android.systemui.shared.system.InputChannelCompat
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class EdgeSwipeTouchHandlerTest : SysuiTestCase() {
    private val kosmos = testKosmos().useUnconfinedTestDispatcher()

    private val Kosmos.session: TouchHandler.TouchSession by Kosmos.Fixture { mock() }

    private val Kosmos.gestureListenerCaptor by
        Kosmos.Fixture { argumentCaptor<GestureDetector.OnGestureListener>() }

    private val Kosmos.inputListenerCaptor by
        Kosmos.Fixture { argumentCaptor<InputChannelCompat.InputEventListener>() }

    private val Kosmos.containerView: DreamOverlayContainerView by
        Kosmos.Fixture {
            val view = mock<DreamOverlayContainerView>()
            // Set a non-zero size for the container view so right-edge calculations work
            whenever(view.width).thenReturn(SCREEN_WIDTH_PX)
            view
        }

    private val Kosmos.userContextProvider: UserContextProvider by
        Kosmos.Fixture {
            mock<UserContextProvider> { on { userContext } doReturn testCase.context }
        }

    private val Kosmos.gestureNavigationSettingsObserver: GestureNavigationSettingsObserver by
        Kosmos.Fixture {
            mock {
                on { getLeftSensitivity(any()) } doReturn EDGE_WIDTH_PX
                on { getRightSensitivity(any()) } doReturn EDGE_WIDTH_PX
            }
        }

    private val Kosmos.gestureNavigationSettingsObserverFactory:
        GestureNavigationSettingsObserverFactory by
        Kosmos.Fixture {
            object : GestureNavigationSettingsObserverFactory {
                override fun create(
                    mainHandler: Handler,
                    bgHandler: Handler,
                    context: Context,
                    onSettingsChanged: Runnable,
                ): GestureNavigationSettingsObserver {
                    return gestureNavigationSettingsObserver
                }
            }
        }

    private val Kosmos.underTest: EdgeSwipeTouchHandler by
        Kosmos.Fixture {
            EdgeSwipeTouchHandler(
                swipeDelegate = dreamSwipeDelegate,
                containerView = containerView,
                mainHandler = fakeExecutorHandler,
                backgroundHandler = fakeExecutorHandler,
                context = testCase.context,
                userContextProvider = userContextProvider,
                vibratorHelper = vibratorHelper,
                logger = dreamTouchHandlerLogger,
                gestureNavigationSettingsObserverFactory = gestureNavigationSettingsObserverFactory,
            )
        }

    @Before
    fun setUp() {
        // Set a default edge width resource to ensure consistent testing
        context.orCreateTestableResources.addOverride(
            R.dimen.dream_switcher_swipe_edge_width_default,
            EDGE_WIDTH_PX,
        )
    }

    @Test
    fun onSessionStart_registersListeners() =
        kosmos.runTest {
            underTest.onSessionStart(session)

            verify(session).registerGestureListener(gestureListenerCaptor.capture())
            verify(session).registerInputListener(inputListenerCaptor.capture())
            assertThat(gestureListenerCaptor.firstValue).isNotNull()
            assertThat(inputListenerCaptor.firstValue).isNotNull()
        }

    @Test
    fun onDown_withinLeftEdge_startsSwipe() =
        kosmos.runTest {
            underTest.onSessionStart(session)
            verify(session).registerGestureListener(gestureListenerCaptor.capture())
            val listener = gestureListenerCaptor.firstValue

            // Down event within left edge
            val downEvent =
                createMotionEvent(
                    x = EDGE_WIDTH_PX - 1f,
                    y = 100f,
                    action = MotionEvent.ACTION_DOWN,
                )
            val result = listener.onDown(downEvent)

            assertThat(result).isTrue()

            val calls = dreamSwipeDelegate.fake.swipeStartedCalls
            assertThat(calls).hasSize(1)
            assertThat(calls[0].isFromLeft).isTrue()
            assertThat(calls[0].startY).isEqualTo(100f)
        }

    @Test
    fun onDown_withinRightEdge_startsSwipe() =
        kosmos.runTest {
            underTest.onSessionStart(session)
            verify(session).registerGestureListener(gestureListenerCaptor.capture())
            val listener = gestureListenerCaptor.firstValue

            // Down event within right edge
            val downEvent =
                createMotionEvent(
                    x = SCREEN_WIDTH_PX - EDGE_WIDTH_PX + 1f,
                    y = 100f,
                    action = MotionEvent.ACTION_DOWN,
                )
            val result = listener.onDown(downEvent)

            assertThat(result).isTrue()

            val calls = dreamSwipeDelegate.fake.swipeStartedCalls
            assertThat(calls).hasSize(1)
            assertThat(calls[0].isFromLeft).isFalse() // From right
            assertThat(calls[0].startY).isEqualTo(100f)
        }

    @Test
    fun onDown_outsideEdges_ignores() =
        kosmos.runTest {
            underTest.onSessionStart(session)
            verify(session).registerGestureListener(gestureListenerCaptor.capture())
            val listener = gestureListenerCaptor.firstValue

            // Down event in the middle of the screen
            val downEvent =
                createMotionEvent(
                    x = SCREEN_WIDTH_PX / 2f,
                    y = 100f,
                    action = MotionEvent.ACTION_DOWN,
                )
            val result = listener.onDown(downEvent)

            assertThat(result).isFalse()
            assertThat(dreamSwipeDelegate.fake.swipeStartedCalls).isEmpty()
        }

    @Test
    fun onDown_swipeDelegateReturnsFalse_ignores() =
        kosmos.runTest {
            underTest.onSessionStart(session)
            verify(session).registerGestureListener(gestureListenerCaptor.capture())
            val listener = gestureListenerCaptor.firstValue

            // Delegate refuses swipe
            dreamSwipeDelegate.fake.shouldClaimSwipe = false

            val downEvent =
                createMotionEvent(
                    x = EDGE_WIDTH_PX - 1f,
                    y = 100f,
                    action = MotionEvent.ACTION_DOWN,
                )
            val result = listener.onDown(downEvent)

            assertThat(result).isFalse()
            // It was called, but handler returned false
            assertThat(dreamSwipeDelegate.fake.swipeStartedCalls).hasSize(1)
        }

    @Test
    fun onScroll_updatesProgress() =
        kosmos.runTest {
            underTest.onSessionStart(session)
            verify(session).registerGestureListener(gestureListenerCaptor.capture())
            val listener = gestureListenerCaptor.firstValue

            // Start swipe
            val startX = 10f
            val downEvent =
                createMotionEvent(x = startX, y = 100f, action = MotionEvent.ACTION_DOWN)
            listener.onDown(downEvent)

            // Scroll
            val moveEvent =
                createMotionEvent(x = startX + 50f, y = 100f, action = MotionEvent.ACTION_MOVE)
            listener.onScroll(downEvent, moveEvent, 50f, 0f)

            val progressCalls = dreamSwipeDelegate.fake.swipeProgressCalls
            assertThat(progressCalls).hasSize(1)
            assertThat(progressCalls[0].dx).isEqualTo(50f)
            // Threshold is typically 2x edge width
            assertThat(progressCalls[0].swipeThreshold).isEqualTo(EDGE_WIDTH_PX * 2f)
        }

    @Test
    fun onScroll_pastThreshold_vibrates() =
        kosmos.runTest {
            underTest.onSessionStart(session)
            verify(session).registerGestureListener(gestureListenerCaptor.capture())
            val listener = gestureListenerCaptor.firstValue

            val startX = 10f
            val downEvent =
                createMotionEvent(x = startX, y = 100f, action = MotionEvent.ACTION_DOWN)
            listener.onDown(downEvent)

            val threshold = EDGE_WIDTH_PX * 2f
            // Scroll past threshold
            val moveEvent =
                createMotionEvent(
                    x = startX + threshold + 10f,
                    y = 100f,
                    action = MotionEvent.ACTION_MOVE,
                )
            listener.onScroll(downEvent, moveEvent, threshold + 10f, 0f)

            assertThat(
                    vibratorHelper.fake.timesVibratedWithHapticFeedbackConstant(
                        HapticFeedbackConstants.SEGMENT_TICK
                    )
                )
                .isEqualTo(1)
        }

    @Test
    fun onScroll_vibratesOnlyOnce() =
        kosmos.runTest {
            underTest.onSessionStart(session)
            verify(session).registerGestureListener(gestureListenerCaptor.capture())
            val listener = gestureListenerCaptor.firstValue

            val startX = 10f
            val downEvent =
                createMotionEvent(x = startX, y = 100f, action = MotionEvent.ACTION_DOWN)
            listener.onDown(downEvent)

            val threshold = EDGE_WIDTH_PX * 2f

            // Scroll past threshold
            var moveEvent =
                createMotionEvent(
                    x = startX + threshold + 10f,
                    y = 100f,
                    action = MotionEvent.ACTION_MOVE,
                )
            listener.onScroll(downEvent, moveEvent, threshold + 10f, 0f)

            // Scroll more
            moveEvent =
                createMotionEvent(
                    x = startX + threshold + 20f,
                    y = 100f,
                    action = MotionEvent.ACTION_MOVE,
                )
            listener.onScroll(downEvent, moveEvent, 10f, 0f)

            // Should still only have vibrated once
            assertThat(
                    vibratorHelper.fake.timesVibratedWithHapticFeedbackConstant(
                        HapticFeedbackConstants.SEGMENT_TICK
                    )
                )
                .isEqualTo(1)
        }

    @Test
    fun onScroll_ignored_if_gestureNotAccepted() =
        kosmos.runTest {
            underTest.onSessionStart(session)
            verify(session).registerGestureListener(gestureListenerCaptor.capture())
            val listener = gestureListenerCaptor.firstValue

            // Down event outside edges (gesture rejected)
            val downEvent =
                createMotionEvent(
                    x = SCREEN_WIDTH_PX / 2f,
                    y = 100f,
                    action = MotionEvent.ACTION_DOWN,
                )
            listener.onDown(downEvent)

            // Scroll
            val moveEvent =
                createMotionEvent(
                    x = SCREEN_WIDTH_PX / 2f + 50f,
                    y = 100f,
                    action = MotionEvent.ACTION_MOVE,
                )
            listener.onScroll(downEvent, moveEvent, 50f, 0f)

            // Verify progress not reported
            assertThat(dreamSwipeDelegate.fake.swipeProgressCalls).isEmpty()
        }

    @Test
    fun onScroll_wrongDirection_doesNotVibrate() =
        kosmos.runTest {
            underTest.onSessionStart(session)
            verify(session).registerGestureListener(gestureListenerCaptor.capture())
            val listener = gestureListenerCaptor.firstValue

            val startX = 10f
            val downEvent =
                createMotionEvent(x = startX, y = 100f, action = MotionEvent.ACTION_DOWN)
            listener.onDown(downEvent)

            val threshold = EDGE_WIDTH_PX * 2f
            // Scroll left (negative dx) - away from center
            // dx = -threshold - 10f
            val moveEvent =
                createMotionEvent(
                    x = startX - threshold - 10f,
                    y = 100f,
                    action = MotionEvent.ACTION_MOVE,
                )
            listener.onScroll(downEvent, moveEvent, threshold + 10f, 0f)

            // Progress reported with raw dx
            val progressCalls = dreamSwipeDelegate.fake.swipeProgressCalls
            assertThat(progressCalls).hasSize(1)
            assertThat(progressCalls[0].dx).isEqualTo(-threshold - 10f)

            // No vibration because distance is negative
            assertThat(vibratorHelper.fake.totalVibrations).isEqualTo(0)
        }

    @Test
    fun onUp_commitsSwipe_ifPastThreshold() =
        kosmos.runTest {
            underTest.onSessionStart(session)
            verify(session).registerGestureListener(gestureListenerCaptor.capture())
            verify(session).registerInputListener(inputListenerCaptor.capture())
            val gestureListener = gestureListenerCaptor.firstValue
            val inputListener = inputListenerCaptor.firstValue

            val startX = 10f
            val downEvent =
                createMotionEvent(x = startX, y = 100f, action = MotionEvent.ACTION_DOWN)
            gestureListener.onDown(downEvent)

            val threshold = EDGE_WIDTH_PX * 2f

            // Up event past threshold
            val upEvent =
                createMotionEvent(
                    x = startX + threshold + 10f,
                    y = 100f,
                    action = MotionEvent.ACTION_UP,
                )
            inputListener.onInputEvent(upEvent)

            val endedCalls = dreamSwipeDelegate.fake.swipeEndedCalls
            assertThat(endedCalls).hasSize(1)
            assertThat(endedCalls[0].committed).isTrue() // Committed
        }

    @Test
    fun onUp_doesNotCommitSwipe_ifNotPastThreshold() =
        kosmos.runTest {
            underTest.onSessionStart(session)
            verify(session).registerGestureListener(gestureListenerCaptor.capture())
            verify(session).registerInputListener(inputListenerCaptor.capture())
            val gestureListener = gestureListenerCaptor.firstValue
            val inputListener = inputListenerCaptor.firstValue

            val startX = 10f
            val downEvent =
                createMotionEvent(x = startX, y = 100f, action = MotionEvent.ACTION_DOWN)
            gestureListener.onDown(downEvent)

            val threshold = EDGE_WIDTH_PX * 2f

            // Up event before threshold
            val upEvent =
                createMotionEvent(
                    x = startX + threshold - 10f,
                    y = 100f,
                    action = MotionEvent.ACTION_UP,
                )
            inputListener.onInputEvent(upEvent)

            val endedCalls = dreamSwipeDelegate.fake.swipeEndedCalls
            assertThat(endedCalls).hasSize(1)
            assertThat(endedCalls[0].committed).isFalse() // Not committed
        }

    @Test
    fun onUp_wrongDirection_doesNotCommit() =
        kosmos.runTest {
            underTest.onSessionStart(session)
            verify(session).registerGestureListener(gestureListenerCaptor.capture())
            verify(session).registerInputListener(inputListenerCaptor.capture())
            val gestureListener = gestureListenerCaptor.firstValue
            val inputListener = inputListenerCaptor.firstValue

            val startX = 10f
            val downEvent =
                createMotionEvent(x = startX, y = 100f, action = MotionEvent.ACTION_DOWN)
            gestureListener.onDown(downEvent)

            val threshold = EDGE_WIDTH_PX * 2f

            // Up event far left (negative dx)
            val upEvent =
                createMotionEvent(
                    x = startX - threshold - 10f,
                    y = 100f,
                    action = MotionEvent.ACTION_UP,
                )
            inputListener.onInputEvent(upEvent)

            val endedCalls = dreamSwipeDelegate.fake.swipeEndedCalls
            assertThat(endedCalls).hasSize(1)
            assertThat(endedCalls[0].committed).isFalse() // Not committed
        }

    @Test
    fun onCancel_cancelsSwipe() =
        kosmos.runTest {
            underTest.onSessionStart(session)
            verify(session).registerGestureListener(gestureListenerCaptor.capture())
            verify(session).registerInputListener(inputListenerCaptor.capture())
            val gestureListener = gestureListenerCaptor.firstValue
            val inputListener = inputListenerCaptor.firstValue

            val startX = 10f
            val downEvent =
                createMotionEvent(x = startX, y = 100f, action = MotionEvent.ACTION_DOWN)
            gestureListener.onDown(downEvent)

            // Cancel event
            val cancelEvent =
                createMotionEvent(x = startX + 100f, y = 100f, action = MotionEvent.ACTION_CANCEL)
            inputListener.onInputEvent(cancelEvent)

            val endedCalls = dreamSwipeDelegate.fake.swipeEndedCalls
            assertThat(endedCalls).hasSize(1)
            assertThat(endedCalls[0].committed).isFalse() // Not committed (cancelled)
        }

    private fun createMotionEvent(x: Float, y: Float, action: Int): MotionEvent {
        return MotionEvent.obtain(0, 0, action, x, y, 0)
    }

    private companion object {
        const val SCREEN_WIDTH_PX = 1000
        const val EDGE_WIDTH_PX = 50
    }
}
