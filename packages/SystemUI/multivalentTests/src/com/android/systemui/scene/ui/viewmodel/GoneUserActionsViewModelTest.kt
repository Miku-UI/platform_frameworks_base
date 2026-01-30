/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.scene.ui.viewmodel

import android.testing.TestableLooper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.Edge
import com.android.compose.animation.scene.Swipe
import com.android.compose.animation.scene.UserAction
import com.android.compose.animation.scene.UserActionResult
import com.android.systemui.SysuiTestCase
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.scene.shared.model.TransitionKeys.ToSplitShade
import com.android.systemui.shade.domain.interactor.enableDualShade
import com.android.systemui.shade.domain.interactor.enableSingleShade
import com.android.systemui.shade.domain.interactor.enableSplitShade
import com.android.systemui.shade.domain.interactor.shadeModeInteractor
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
@TestableLooper.RunWithLooper
@EnableSceneContainer
class GoneUserActionsViewModelTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private lateinit var underTest: GoneUserActionsViewModel

    @Before
    fun setUp() {
        underTest = GoneUserActionsViewModel(shadeModeInteractor = kosmos.shadeModeInteractor)
        underTest.activateIn(kosmos.testScope)
    }

    @Test
    fun downTransitionKey_splitShadeEnabled_isGoneToSplitShade() =
        kosmos.runTest {
            val userActions by collectLastValue(underTest.actions)
            enableSplitShade()

            assertThat(userActions?.get(Swipe.Down)?.transitionKey).isEqualTo(ToSplitShade)
        }

    @Test
    fun downTransitionKey_splitShadeDisabled_isNull() =
        kosmos.runTest {
            val userActions by collectLastValue(underTest.actions)
            enableSingleShade()

            assertThat(userActions?.get(Swipe.Down)?.transitionKey).isNull()
        }

    @Test
    fun downTransitionKey_dualShadeEnabled_isNull() =
        kosmos.runTest {
            val userActions by collectLastValue(underTest.actions)
            enableDualShade(wideLayout = true)

            assertThat(userActions?.get(Swipe.Down)?.transitionKey).isNull()
        }

    @Test
    fun swipeDownWithTwoFingers_singleShade_goesToQuickSettings() =
        kosmos.runTest {
            val userActions by collectLastValue(underTest.actions)
            enableSingleShade()

            assertThat(userActions?.get(swipeDownFromTopWithTwoFingers()))
                .isEqualTo(UserActionResult(Scenes.QuickSettings))
        }

    @Test
    fun swipeDownWithTwoFingers_splitShade_goesToShade() =
        kosmos.runTest {
            val userActions by collectLastValue(underTest.actions)
            enableSplitShade()

            assertThat(userActions?.get(swipeDownFromTopWithTwoFingers()))
                .isEqualTo(UserActionResult(Scenes.Shade, ToSplitShade))
        }

    @Test
    fun swipeDownWithTwoFingers_dualShadeEnabled_isNull() =
        kosmos.runTest {
            val userActions by collectLastValue(underTest.actions)
            enableDualShade()

            assertThat(userActions?.get(swipeDownFromTopWithTwoFingers())).isNull()
        }

    private fun swipeDownFromTopWithTwoFingers(): UserAction {
        return Swipe.Down(pointerCount = 2, fromSource = Edge.Top)
    }
}
