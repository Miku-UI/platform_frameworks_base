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

package com.android.systemui.keyguard.ui.viewmodel

import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.Flags.FLAG_HUB_EDIT_MODE_TRANSITION
import com.android.systemui.SysuiTestCase
import com.android.systemui.communal.domain.interactor.communalSceneInteractor
import com.android.systemui.communal.shared.model.EditModeState
import com.android.systemui.keyguard.ui.transitions.BlurConfig
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class GlanceableHubToEditModeTransitionViewModelTest : SysuiTestCase() {
    val kosmos = testKosmos()
    val blurConfig = BlurConfig(minBlurRadiusPx = 0f, maxBlurRadiusPx = 100f)

    lateinit var underTest: GlanceableHubToEditModeTransitionViewModel

    @Before
    fun setUp() {
        underTest =
            GlanceableHubToEditModeTransitionViewModel(blurConfig, kosmos.communalSceneInteractor)
    }

    @EnableFlags(FLAG_HUB_EDIT_MODE_TRANSITION)
    @Test
    fun testWindowBlurRadius_doesNotEmitUntilValueChanges() =
        with(kosmos) {
            runTest {
                val windowBlurRadius by collectLastValue(underTest.windowBlurRadius)
                assertThat(windowBlurRadius).isNull()

                setEditModeState(null)
                assertThat(windowBlurRadius).isNull()

                setEditModeState(EditModeState.STARTING)
                assertThat(windowBlurRadius).isNull()

                setEditModeState(EditModeState.CREATED)
                assertThat(windowBlurRadius).isNull()

                setEditModeState(EditModeState.READY_TO_SHOW)
                assertThat(windowBlurRadius).isEqualTo(0f)
            }
        }

    @EnableFlags(FLAG_HUB_EDIT_MODE_TRANSITION)
    @Test
    fun testWindowBlurRadius_minBlurWhenEditModeIsShowing() =
        with(kosmos) {
            runTest {
                val windowBlurRadius by collectLastValue(underTest.windowBlurRadius)
                // Set edit mode state as showing as part of the set up just to get the flow started
                setEditModeState(EditModeState.SHOWING)

                setEditModeState(null)
                assertThat(windowBlurRadius).isEqualTo(100f)

                setEditModeState(EditModeState.STARTING)
                assertThat(windowBlurRadius).isEqualTo(100f)

                setEditModeState(EditModeState.CREATED)
                assertThat(windowBlurRadius).isEqualTo(100f)

                // Edit mode is ready to show, snap the blur down to minimum.
                setEditModeState(EditModeState.READY_TO_SHOW)
                assertThat(windowBlurRadius).isEqualTo(0f)

                setEditModeState(EditModeState.SHOWING)
                assertThat(windowBlurRadius).isEqualTo(0f)

                setEditModeState(null)
                assertThat(windowBlurRadius).isEqualTo(100f)
            }
        }

    @DisableFlags(FLAG_HUB_EDIT_MODE_TRANSITION)
    @Test
    fun testWindowBlurRadius_flagDisabled_doesNotEmit() =
        with(kosmos) {
            runTest {
                val windowBlurRadius by collectLastValue(underTest.windowBlurRadius)
                assertThat(windowBlurRadius).isNull()

                setEditModeState(null)
                assertThat(windowBlurRadius).isNull()

                setEditModeState(EditModeState.STARTING)
                assertThat(windowBlurRadius).isNull()

                setEditModeState(EditModeState.CREATED)
                assertThat(windowBlurRadius).isNull()

                setEditModeState(EditModeState.READY_TO_SHOW)
                assertThat(windowBlurRadius).isNull()

                setEditModeState(EditModeState.SHOWING)
                assertThat(windowBlurRadius).isNull()

                setEditModeState(null)
                assertThat(windowBlurRadius).isNull()
            }
        }

    private fun Kosmos.setEditModeState(state: EditModeState?) {
        communalSceneInteractor.setEditModeState(state)
        runCurrent()
    }
}
