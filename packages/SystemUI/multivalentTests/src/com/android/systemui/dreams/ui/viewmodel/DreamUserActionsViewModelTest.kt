/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.dreams.ui.viewmodel

import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import androidx.compose.ui.input.pointer.PointerType
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.Edge
import com.android.compose.animation.scene.Swipe
import com.android.compose.animation.scene.UserActionResult
import com.android.systemui.Flags.FLAG_DUAL_SHADE
import com.android.systemui.SysuiTestCase
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.power.domain.interactor.PowerInteractor.Companion.setAsleepForTest
import com.android.systemui.power.domain.interactor.PowerInteractor.Companion.setAwakeForTest
import com.android.systemui.power.domain.interactor.powerInteractor
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.scene.shared.model.TransitionKeys.ToSplitShade
import com.android.systemui.shade.domain.interactor.enableDualShade
import com.android.systemui.shade.domain.interactor.enableSingleShade
import com.android.systemui.shade.domain.interactor.enableSplitShade
import com.android.systemui.testKosmos
import com.google.common.truth.Expect
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableSceneContainer
class DreamUserActionsViewModelTest : SysuiTestCase() {

    @get:Rule val expect: Expect = Expect.create()

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()

    private val Kosmos.underTest: DreamUserActionsViewModel by
        Kosmos.Fixture { dreamUserActionsViewModelFactory.create() }

    @Before
    fun setUp() {
        with(kosmos) { underTest.activateIn(testScope) }
    }

    @Test
    fun actions_singleShade() =
        kosmos.runTest {
            enableSingleShade()

            val actions by collectLastValue(underTest.actions)

            setUpState(isShadeTouchable = true)
            assertThat(actions).isNotEmpty()
            expect.that(actions?.get(Swipe.Up)).isNull()
            expect.that(actions?.get(Swipe.Down)).isEqualTo(UserActionResult(Scenes.Shade))
            expect.that(actions?.get(Swipe.Down(fromSource = Edge.Top))).isNull()
            expect.that(actions?.get(Swipe.Start)).isNull()
            expect.that(actions?.get(Swipe.End)).isNull()

            setUpState(isShadeTouchable = false)
            assertThat(actions).isEmpty()
        }

    @Test
    @DisableFlags(FLAG_DUAL_SHADE)
    fun actions_splitShade() =
        kosmos.runTest {
            enableSplitShade()

            val actions by collectLastValue(underTest.actions)

            setUpState(isShadeTouchable = true)
            assertThat(actions).isNotEmpty()
            expect.that(actions?.get(Swipe.Up)).isNull()
            expect
                .that(actions?.get(Swipe.Down))
                .isEqualTo(UserActionResult(Scenes.Shade, ToSplitShade))
            expect.that(actions?.get(Swipe.Start)).isNull()
            expect.that(actions?.get(Swipe.End)).isNull()

            setUpState(isShadeTouchable = false)
            assertThat(actions).isEmpty()
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun actions_dualShade() =
        kosmos.runTest {
            enableDualShade()

            val actions by collectLastValue(underTest.actions)

            setUpState(isShadeTouchable = true)
            assertThat(actions).isNotEmpty()
            expect.that(actions?.get(Swipe.Up)).isNull()
            expect
                .that(actions?.get(Swipe.Down(pointerType = PointerType.Eraser)))
                .isEqualTo(UserActionResult.ShowOverlay(Overlays.NotificationsShade))
            expect
                .that(actions?.get(Swipe.Down(pointerType = PointerType.Stylus)))
                .isEqualTo(UserActionResult.ShowOverlay(Overlays.NotificationsShade))
            expect
                .that(actions?.get(Swipe.Down(pointerType = PointerType.Touch)))
                .isEqualTo(UserActionResult.ShowOverlay(Overlays.NotificationsShade))
            expect.that(actions?.get(Swipe.Start)).isNull()
            expect.that(actions?.get(Swipe.End)).isNull()

            setUpState(isShadeTouchable = false)
            assertThat(actions).isEmpty()
        }

    private fun Kosmos.setUpState(isShadeTouchable: Boolean) {
        if (isShadeTouchable) {
            powerInteractor.setAwakeForTest()
        } else {
            powerInteractor.setAsleepForTest()
        }
    }
}
