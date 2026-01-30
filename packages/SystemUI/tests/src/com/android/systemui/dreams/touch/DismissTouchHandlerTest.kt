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

package com.android.systemui.dreams.touch

import android.view.GestureDetector.OnGestureListener
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.ambient.touch.TouchHandler
import com.android.systemui.shared.system.InputChannelCompat
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class DismissTouchHandlerTest : SysuiTestCase() {
    @Mock private lateinit var touchHandlerCallback: DismissTouchHandler.DismissCallback

    @Mock private lateinit var touchSession: TouchHandler.TouchSession

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
    }

    private fun prepareTouchHandler(touchHandler: TouchHandler): OnGestureListener {
        touchHandler.onSessionStart(touchSession)
        val listenerCaptor = argumentCaptor<OnGestureListener>()
        verify(touchSession).registerGestureListener(listenerCaptor.capture())
        return listenerCaptor.lastValue
    }

    private fun sendDownEvent(
        gestureListener: OnGestureListener
    ): InputChannelCompat.InputEventListener {
        val event = mock<MotionEvent>()
        whenever(event.action).thenReturn(MotionEvent.ACTION_DOWN)
        val listenerCaptor = argumentCaptor<InputChannelCompat.InputEventListener>()
        assertThat(gestureListener.onDown(event)).isTrue()
        verify(touchSession).registerInputListener(listenerCaptor.capture())
        return listenerCaptor.lastValue
    }

    @Test
    fun dismissTouchHandlerConsumesTouch() {
        val touchHandler = DismissTouchHandler(touchHandlerCallback)
        val listener = prepareTouchHandler(touchHandler)

        assertThat(sendDownEvent(listener)).isNotNull()
    }

    @Test
    fun dismissTouchHandlerInformsCallback() {
        val touchHandler = DismissTouchHandler(touchHandlerCallback)
        val listener = sendDownEvent(prepareTouchHandler(touchHandler))
        val event = mock<MotionEvent>()
        whenever(event.action).thenReturn(MotionEvent.ACTION_UP)
        listener.onInputEvent(event)
        verify(touchHandlerCallback).onDismissed()
    }
}
