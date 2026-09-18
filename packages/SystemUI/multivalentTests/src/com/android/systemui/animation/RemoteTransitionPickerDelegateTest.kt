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

package com.android.systemui.animation

import android.os.IBinder
import android.view.SurfaceControl
import android.window.IRemoteTransition
import android.window.IRemoteTransitionFinishedCallback
import android.window.TransitionInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
@SmallTest
class RemoteTransitionPickerDelegateTest : SysuiTestCase() {
    private val mockPicker = mock<Function1<TransitionInfo, IRemoteTransition>>()
    private val firstRemoteTransition = mock<IRemoteTransition>()
    private val secondRemoteTransition = mock<IRemoteTransition>()
    private val remoteTransitionPickerDelegate = RemoteTransitionPickerDelegate(mockPicker)

    private val firstBinder = mock<IBinder>()
    private val firstTransitionInfo = mock<TransitionInfo>()
    private val firstSct = mock<SurfaceControl.Transaction>()
    private val firstFinishedCallback = mock<IRemoteTransitionFinishedCallback>()
    private val secondBinder = mock<IBinder>()
    private val secondTransitionInfo = mock<TransitionInfo>()
    private val secondSct = mock<SurfaceControl.Transaction>()
    private val secondFinishedCallback = mock<IRemoteTransitionFinishedCallback>()

    @Before
    fun setup() {
        whenever(mockPicker.invoke(any())).thenReturn(firstRemoteTransition)
    }

    @Test
    fun startAnimation_callsRemote() {
        remoteTransitionPickerDelegate.startAnimation(
            firstBinder,
            firstTransitionInfo,
            firstSct,
            firstFinishedCallback,
        )

        verify(firstRemoteTransition)
            .startAnimation(eq(firstBinder), eq(firstTransitionInfo), eq(firstSct), any())
    }

    @Test
    fun singleTransition_callsSameRemote() {
        remoteTransitionPickerDelegate.startAnimation(
            firstBinder,
            firstTransitionInfo,
            firstSct,
            firstFinishedCallback,
        )

        remoteTransitionPickerDelegate.mergeAnimation(
            secondBinder,
            secondTransitionInfo,
            secondSct,
            firstBinder,
            secondFinishedCallback,
        )
        remoteTransitionPickerDelegate.onTransitionConsumed(firstBinder, false)

        verify(firstRemoteTransition)
            .startAnimation(eq(firstBinder), eq(firstTransitionInfo), eq(firstSct), any())
        verify(firstRemoteTransition)
            .mergeAnimation(
                secondBinder,
                secondTransitionInfo,
                secondSct,
                firstBinder,
                secondFinishedCallback,
            )
        verify(firstRemoteTransition).onTransitionConsumed(firstBinder, false)
    }

    @Test
    fun severalTransitions_usesCorrectRemote() {
        whenever(mockPicker.invoke(any())).thenReturn(firstRemoteTransition)
        remoteTransitionPickerDelegate.startAnimation(
            firstBinder,
            firstTransitionInfo,
            firstSct,
            firstFinishedCallback,
        )
        remoteTransitionPickerDelegate.onTransitionConsumed(firstBinder, false)
        whenever(mockPicker.invoke(any())).thenReturn(secondRemoteTransition)

        remoteTransitionPickerDelegate.startAnimation(
            secondBinder,
            secondTransitionInfo,
            secondSct,
            secondFinishedCallback,
        )
        remoteTransitionPickerDelegate.onTransitionConsumed(secondBinder, false)

        verify(firstRemoteTransition)
            .startAnimation(eq(firstBinder), eq(firstTransitionInfo), eq(firstSct), any())
        verify(firstRemoteTransition, never()).startAnimation(eq(secondBinder), any(), any(), any())
        verify(firstRemoteTransition).onTransitionConsumed(firstBinder, false)
        verify(firstRemoteTransition, never()).onTransitionConsumed(eq(secondBinder), anyBoolean())
        verify(secondRemoteTransition)
            .startAnimation(eq(secondBinder), eq(secondTransitionInfo), eq(secondSct), any())
        verify(secondRemoteTransition, never()).startAnimation(eq(firstBinder), any(), any(), any())
        verify(secondRemoteTransition).onTransitionConsumed(secondBinder, false)
        verify(secondRemoteTransition, never()).onTransitionConsumed(eq(firstBinder), anyBoolean())
    }

    @Test
    fun finishCallback_clearsRemoteTransitionAndDelegatesFinish() {
        val callbackCaptor = argumentCaptor<IRemoteTransitionFinishedCallback>()
        remoteTransitionPickerDelegate.startAnimation(
            firstBinder,
            firstTransitionInfo,
            firstSct,
            firstFinishedCallback,
        )
        verify(firstRemoteTransition)
            .startAnimation(
                eq(firstBinder),
                eq(firstTransitionInfo),
                eq(firstSct),
                callbackCaptor.capture(),
            )

        // Before finish, the remote is set and should receive calls
        remoteTransitionPickerDelegate.onTransitionConsumed(firstBinder, false)
        verify(firstRemoteTransition).onTransitionConsumed(firstBinder, false)

        // WHEN the transition is finished
        callbackCaptor.firstValue.onTransitionFinished(null, null)

        // THEN the original finish callback is invoked
        verify(firstFinishedCallback).onTransitionFinished(null, null)

        // AND the remote transition is cleared, so it no longer receives calls
        remoteTransitionPickerDelegate.onTransitionConsumed(firstBinder, true)
        verify(firstRemoteTransition, never()).onTransitionConsumed(firstBinder, true)
    }
}
