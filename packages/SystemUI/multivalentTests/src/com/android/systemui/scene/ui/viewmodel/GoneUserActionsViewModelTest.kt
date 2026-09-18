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

import android.content.res.mainResources
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.testing.TestableLooper
import androidx.compose.ui.input.pointer.PointerType
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.Edge
import com.android.compose.animation.scene.Swipe
import com.android.compose.animation.scene.UserAction
import com.android.compose.animation.scene.UserActionResult
import com.android.compose.animation.scene.UserActionResult.ShowOverlay
import com.android.systemui.Flags.FLAG_DUAL_SHADE
import com.android.systemui.SysuiTestCase
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.res.R
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.scene.shared.model.TransitionKeys.ToSplitShade
import com.android.systemui.shade.domain.interactor.enableDualShade
import com.android.systemui.shade.domain.interactor.enableSingleShade
import com.android.systemui.shade.domain.interactor.enableSplitShade
import com.android.systemui.shade.domain.interactor.shadeModeInteractor
import com.android.systemui.testKosmos
import com.google.common.truth.Expect
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
@TestableLooper.RunWithLooper
@EnableSceneContainer
class GoneUserActionsViewModelTest : SysuiTestCase() {

    @get:Rule val expect: Expect = Expect.create()

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val Kosmos.underTest: GoneUserActionsViewModel by
        Kosmos.Fixture {
            GoneUserActionsViewModel(
                shadeModeInteractor = shadeModeInteractor,
                resources = mainResources,
            )
        }

    @Test
    @DisableFlags(FLAG_DUAL_SHADE)
    fun downTransitionKey_splitShadeEnabled_isGoneToSplitShade() =
        kosmos.runTest {
            underTest.activateIn(testScope)
            val userActions by collectLastValue(underTest.actions)
            enableSplitShade()

            assertThat(userActions?.get(Swipe.Down)?.transitionKey).isEqualTo(ToSplitShade)
        }

    @Test
    fun downTransitionKey_singleShade_isNull() =
        kosmos.runTest {
            underTest.activateIn(testScope)
            val userActions by collectLastValue(underTest.actions)
            enableSingleShade()

            assertThat(userActions?.get(Swipe.Down)?.transitionKey).isNull()
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun swipeDown_exceptWithMouse_dualShade_goesToNotificationsShade() =
        kosmos.runTest {
            underTest.activateIn(testScope)
            val userActions by collectLastValue(underTest.actions)
            enableDualShade(wideLayout = true)

            expect
                .that(userActions?.get(Swipe.Down(pointerType = PointerType.Eraser)))
                .isEqualTo(ShowOverlay(Overlays.NotificationsShade))
            expect
                .that(userActions?.get(Swipe.Down(pointerType = PointerType.Stylus)))
                .isEqualTo(ShowOverlay(Overlays.NotificationsShade))
            expect
                .that(userActions?.get(Swipe.Down(pointerType = PointerType.Touch)))
                .isEqualTo(ShowOverlay(Overlays.NotificationsShade))
            expect.that(userActions?.get(Swipe.Down(pointerType = PointerType.Mouse))).isNull()
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun swipeDownFromTopEdgeAndHalf_expectForMouse_dualShade_goesToQuickSettings() =
        kosmos.runTest {
            underTest.activateIn(testScope)
            val userActions by collectLastValue(underTest.actions)
            enableDualShade(wideLayout = true)

            expect
                .that(
                    userActions?.get(
                        Swipe.Down(
                            fromSource = SceneContainerArea.TopEdgeEndHalf,
                            pointerType = PointerType.Eraser,
                        )
                    )
                )
                .isEqualTo(ShowOverlay(Overlays.QuickSettingsShade))
            expect
                .that(
                    userActions?.get(
                        Swipe.Down(
                            fromSource = SceneContainerArea.TopEdgeEndHalf,
                            pointerType = PointerType.Stylus,
                        )
                    )
                )
                .isEqualTo(ShowOverlay(Overlays.QuickSettingsShade))
            expect
                .that(
                    userActions?.get(
                        Swipe.Down(
                            fromSource = SceneContainerArea.TopEdgeEndHalf,
                            pointerType = PointerType.Touch,
                        )
                    )
                )
                .isEqualTo(ShowOverlay(Overlays.QuickSettingsShade))
            expect
                .that(
                    userActions?.get(
                        Swipe.Down(
                            fromSource = SceneContainerArea.TopEdgeEndHalf,
                            pointerType = PointerType.Mouse,
                        )
                    )
                )
                .isNull()
        }

    @Test
    fun swipeDownWithTwoFingers_singleShade_goesToQuickSettings() =
        kosmos.runTest {
            underTest.activateIn(testScope)
            val userActions by collectLastValue(underTest.actions)
            enableSingleShade()

            assertThat(userActions?.get(swipeDownFromTopWithTwoFingers()))
                .isEqualTo(UserActionResult(Scenes.QuickSettings))
        }

    @Test
    @DisableFlags(FLAG_DUAL_SHADE)
    fun swipeDownWithTwoFingers_splitShade_goesToShade() =
        kosmos.runTest {
            underTest.activateIn(testScope)
            val userActions by collectLastValue(underTest.actions)
            enableSplitShade()

            assertThat(userActions?.get(swipeDownFromTopWithTwoFingers()))
                .isEqualTo(UserActionResult(Scenes.Shade, ToSplitShade))
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun swipeDownWithTwoFingers_dualShadeEnabled_configEnabled_goesToQuickSettings() =
        kosmos.runTest {
            overrideResource(R.bool.config_enableTwoFingerSwipeDownShade, true)
            underTest.activateIn(testScope)
            val userActions by collectLastValue(underTest.actions)
            enableDualShade()

            assertThat(userActions?.get(Swipe.Down(pointerCount = 2)))
                .isEqualTo(ShowOverlay(Overlays.QuickSettingsShade))
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun swipeDownWithTwoFingers_dualShadeEnabled_configDisabled_isNull() =
        kosmos.runTest {
            overrideResource(R.bool.config_enableTwoFingerSwipeDownShade, false)
            underTest.activateIn(testScope)
            val userActions by collectLastValue(underTest.actions)
            enableDualShade()

            assertThat(userActions?.get(Swipe.Down(pointerCount = 2))).isNull()
        }

    private fun swipeDownFromTopWithTwoFingers(): UserAction {
        return Swipe.Down(pointerCount = 2, fromSource = Edge.Top)
    }
}
