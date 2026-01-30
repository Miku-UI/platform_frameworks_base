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
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
@SmallTest
class RemoteTransitionDelegateTest : SysuiTestCase() {
    private val mockPicker = mock<Function1<TransitionInfo, IRemoteTransition>>()
    private val firstRemoteTransition = mock<IRemoteTransition>()
    private val secondRemoteTransition = mock<IRemoteTransition>()
    private val remoteTransitionDelegate = RemoteTransitionDelegate(mockPicker)

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
        remoteTransitionDelegate.startAnimation(
            firstBinder,
            firstTransitionInfo,
            firstSct,
            firstFinishedCallback,
        )

        verify(firstRemoteTransition)
            .startAnimation(firstBinder, firstTransitionInfo, firstSct, firstFinishedCallback)
    }

    @Test
    fun singleTransition_callsSameRemote() {
        remoteTransitionDelegate.startAnimation(
            firstBinder,
            firstTransitionInfo,
            firstSct,
            firstFinishedCallback,
        )

        remoteTransitionDelegate.mergeAnimation(
            secondBinder,
            secondTransitionInfo,
            secondSct,
            firstBinder,
            secondFinishedCallback,
        )
        remoteTransitionDelegate.onTransitionConsumed(firstBinder, false)

        verify(firstRemoteTransition)
            .startAnimation(firstBinder, firstTransitionInfo, firstSct, firstFinishedCallback)
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
        remoteTransitionDelegate.startAnimation(
            firstBinder,
            firstTransitionInfo,
            firstSct,
            firstFinishedCallback,
        )
        remoteTransitionDelegate.onTransitionConsumed(firstBinder, false)
        whenever(mockPicker.invoke(any())).thenReturn(secondRemoteTransition)

        remoteTransitionDelegate.startAnimation(
            secondBinder,
            secondTransitionInfo,
            secondSct,
            secondFinishedCallback,
        )
        remoteTransitionDelegate.onTransitionConsumed(secondBinder, false)

        verify(firstRemoteTransition)
            .startAnimation(firstBinder, firstTransitionInfo, firstSct, firstFinishedCallback)
        verify(firstRemoteTransition, never()).startAnimation(eq(secondBinder), any(), any(), any())
        verify(firstRemoteTransition).onTransitionConsumed(firstBinder, false)
        verify(firstRemoteTransition, never()).onTransitionConsumed(eq(secondBinder), anyBoolean())
        verify(secondRemoteTransition)
            .startAnimation(secondBinder, secondTransitionInfo, secondSct, secondFinishedCallback)
        verify(secondRemoteTransition, never()).startAnimation(eq(firstBinder), any(), any(), any())
        verify(secondRemoteTransition).onTransitionConsumed(secondBinder, false)
        verify(secondRemoteTransition, never()).onTransitionConsumed(eq(firstBinder), anyBoolean())
    }
}
