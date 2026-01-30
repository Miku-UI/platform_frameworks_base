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

package com.android.systemui.notifications.ui.viewmodel

import android.testing.TestableLooper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.Back
import com.android.compose.animation.scene.Swipe
import com.android.compose.animation.scene.UserActionResult.HideOverlay
import com.android.compose.animation.scene.UserActionResult.ShowOverlay
import com.android.compose.animation.scene.UserActionResult.ShowOverlay.HideCurrentOverlays
import com.android.systemui.SysuiTestCase
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.ui.viewmodel.SceneContainerArea
import com.android.systemui.shade.domain.interactor.enableDualShade
import com.android.systemui.shade.ui.viewmodel.notificationsShadeOverlayActionsViewModel
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
@TestableLooper.RunWithLooper
@EnableSceneContainer
class NotificationsShadeOverlayActionsViewModelTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()

    private val underTest by lazy { kosmos.notificationsShadeOverlayActionsViewModel }
    private val actions by kosmos.testScope.collectLastValue(underTest.actions)

    @Before
    fun setUp() {
        underTest.activateIn(kosmos.testScope)
    }

    @Test
    fun up_hidesShade() =
        kosmos.runTest {
            assertThat((actions?.get(Swipe.Up) as? HideOverlay)?.overlay)
                .isEqualTo(Overlays.NotificationsShade)
            assertThat(actions?.get(Swipe.Down)).isNull()
        }

    @Test
    fun back_hidesShade() =
        kosmos.runTest {
            assertThat((actions?.get(Back) as? HideOverlay)?.overlay)
                .isEqualTo(Overlays.NotificationsShade)
        }

    @Test
    fun downFromEndHalf_wideScreen_switchesToQuickSettingsShade() =
        kosmos.runTest {
            enableDualShade(wideLayout = true)

            val action = actions?.get(Swipe.Down(fromSource = SceneContainerArea.EndHalf))
            assertThat((action as ShowOverlay).overlay).isEqualTo(Overlays.QuickSettingsShade)
            assertThat((action.hideCurrentOverlays as HideCurrentOverlays.Some).overlays)
                .containsExactly(Overlays.NotificationsShade)
        }

    @Test
    fun downFromEndHalf_narrowScreen_doesNothing() =
        kosmos.runTest {
            enableDualShade(wideLayout = false)

            val action = actions?.get(Swipe.Down(fromSource = SceneContainerArea.EndHalf))
            assertThat(action).isNull()
        }

    @Test
    fun downFromTopEdgeEndHalf_wideScreen_switchesToQuickSettingsShade() =
        kosmos.runTest {
            enableDualShade(wideLayout = true)

            val action = actions?.get(Swipe.Down(fromSource = SceneContainerArea.TopEdgeEndHalf))
            assertThat((action as ShowOverlay).overlay).isEqualTo(Overlays.QuickSettingsShade)
            assertThat((action.hideCurrentOverlays as HideCurrentOverlays.Some).overlays)
                .containsExactly(Overlays.NotificationsShade)
        }

    @Test
    fun downFromTopEdgeEndHalf_narrowScreen_switchesToQuickSettingsShade() =
        kosmos.runTest {
            enableDualShade(wideLayout = false)

            val action = actions?.get(Swipe.Down(fromSource = SceneContainerArea.TopEdgeEndHalf))
            assertThat((action as ShowOverlay).overlay).isEqualTo(Overlays.QuickSettingsShade)
            assertThat((action.hideCurrentOverlays as HideCurrentOverlays.Some).overlays)
                .containsExactly(Overlays.NotificationsShade)
        }
}
