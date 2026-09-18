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

package com.android.wm.shell.recents

import android.testing.AndroidTestingRunner
import android.view.Display.DEFAULT_DISPLAY
import androidx.test.filters.SmallTest
import com.android.wm.shell.sysui.ShellInit
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

/**
 * Tests for [PerDisplayRecentsTransitionStateListener].
 *
 * Build/Install/Run: atest WMShellUnitTests:PerDisplayRecentsTransitionStateListenerTests
 */
@RunWith(AndroidTestingRunner::class)
@SmallTest
class PerDisplayRecentsTransitionStateListenerTests {
    private val mockShellInit = mock<ShellInit>()
    private val mockRecentsTransitionHandler = mock<RecentsTransitionHandler>()

    private val perDisplayRecentsTransitionStateListener =
        PerDisplayRecentsTransitionStateListener(mockShellInit, mockRecentsTransitionHandler)

    @Test
    fun requestedState_setsAnimationActive() {
        perDisplayRecentsTransitionStateListener.onTransitionStateChanged(
            RecentsTransitionStateListener.TRANSITION_STATE_REQUESTED,
            DEFAULT_DISPLAY,
        )

        assertThat(
                perDisplayRecentsTransitionStateListener.isRecentsAnimationActive(DEFAULT_DISPLAY)
            )
            .isTrue()
    }

    @Test
    fun nonRunningState_setsAnimationNotActive() {
        perDisplayRecentsTransitionStateListener.onTransitionStateChanged(
            RecentsTransitionStateListener.TRANSITION_STATE_REQUESTED,
            DEFAULT_DISPLAY,
        )

        perDisplayRecentsTransitionStateListener.onTransitionStateChanged(
            RecentsTransitionStateListener.TRANSITION_STATE_NOT_RUNNING,
            DEFAULT_DISPLAY,
        )

        assertThat(
                perDisplayRecentsTransitionStateListener.isRecentsAnimationActive(DEFAULT_DISPLAY)
            )
            .isFalse()
    }

    @Test
    fun requestedAndAnimating_keepsAnimationActive() {
        perDisplayRecentsTransitionStateListener.onTransitionStateChanged(
            RecentsTransitionStateListener.TRANSITION_STATE_REQUESTED,
            DEFAULT_DISPLAY,
        )
        perDisplayRecentsTransitionStateListener.onTransitionStateChanged(
            RecentsTransitionStateListener.TRANSITION_STATE_ANIMATING,
            DEFAULT_DISPLAY,
        )

        assertThat(
                perDisplayRecentsTransitionStateListener.isRecentsAnimationActive(DEFAULT_DISPLAY)
            )
            .isTrue()
    }
}
