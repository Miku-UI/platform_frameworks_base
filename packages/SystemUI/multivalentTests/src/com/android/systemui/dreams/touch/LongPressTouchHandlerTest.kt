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

import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.ambient.touch.TouchHandler
import com.android.systemui.dreams.DreamOverlayContainerView
import com.android.systemui.dreams.ui.viewmodel.dreamDialogController
import com.android.systemui.dreams.ui.viewmodel.fake
import com.android.systemui.haptics.fake
import com.android.systemui.haptics.vibratorHelper
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

@SmallTest
@RunWith(AndroidJUnit4::class)
class LongPressTouchHandlerTest : SysuiTestCase() {
    private val kosmos = testKosmos().useUnconfinedTestDispatcher()

    private val Kosmos.session: TouchHandler.TouchSession by Kosmos.Fixture { mock() }

    private val Kosmos.gestureListenerCaptor by
        Kosmos.Fixture { argumentCaptor<GestureDetector.OnGestureListener>() }

    private val Kosmos.containerView: DreamOverlayContainerView by
        Kosmos.Fixture { DreamOverlayContainerView(context, null) }

    private val Kosmos.underTest: LongPressTouchHandler by
        Kosmos.Fixture {
            LongPressTouchHandler(
                vibratorHelper,
                containerView,
                dreamDialogController,
                dreamTouchHandlerLogger,
            )
        }

    @Test
    fun onSessionStart_registersListener() =
        kosmos.runTest {

            // WHEN a new session starts
            underTest.onSessionStart(session)

            // THEN a listener is registered
            verify(session).registerGestureListener(gestureListenerCaptor.capture())
            assertThat(gestureListenerCaptor.firstValue).isNotNull()
        }

    @Test
    fun onLongPress_showsSwitcher() =
        kosmos.runTest {
            underTest.onSessionStart(session)
            verify(session).registerGestureListener(gestureListenerCaptor.capture())
            val listener = gestureListenerCaptor.firstValue

            // WHEN a long press occurs
            listener.onLongPress(mock())

            // THEN the switcher is shown
            assertThat(dreamDialogController.fake.dialogShowing).isTrue()
        }

    @Test
    fun onLongPress_pilfers() =
        kosmos.runTest {
            underTest.onSessionStart(session)
            verify(session).registerGestureListener(gestureListenerCaptor.capture())
            val listener = gestureListenerCaptor.firstValue

            // WHEN a long press occurs
            listener.onLongPress(mock())

            // THEN the session pilfers pointers
            verify(session).pilfer()
        }

    @Test
    fun onLongPress_vibrates() =
        kosmos.runTest {
            underTest.onSessionStart(session)
            verify(session).registerGestureListener(gestureListenerCaptor.capture())
            val listener = gestureListenerCaptor.firstValue

            // WHEN a long press occurs
            listener.onLongPress(mock())

            // THEN the device vibrates
            assertThat(
                    vibratorHelper.fake.timesVibratedWithHapticFeedbackConstant(
                        HapticFeedbackConstants.LONG_PRESS
                    )
                )
                .isEqualTo(1)
        }
}
