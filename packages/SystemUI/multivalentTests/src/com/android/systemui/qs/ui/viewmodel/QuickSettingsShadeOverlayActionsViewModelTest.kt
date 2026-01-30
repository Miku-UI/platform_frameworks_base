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

package com.android.systemui.qs.ui.viewmodel

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
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.qs.panels.ui.viewmodel.editModeViewModel
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.ui.viewmodel.SceneContainerArea
import com.android.systemui.shade.domain.interactor.enableDualShade
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
@TestableLooper.RunWithLooper
@EnableSceneContainer
class QuickSettingsShadeOverlayActionsViewModelTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()

    private val underTest by lazy { kosmos.quickSettingsShadeOverlayActionsViewModel }
    private val actions by kosmos.testScope.collectLastValue(underTest.actions)

    @Before
    fun setUp() {
        underTest.activateIn(kosmos.testScope)
    }

    @Test
    fun up_hidesShade() =
        kosmos.runTest {
            assertThat((actions?.get(Swipe.Up) as? HideOverlay)?.overlay)
                .isEqualTo(Overlays.QuickSettingsShade)
            assertThat(actions?.get(Swipe.Down)).isNull()
        }

    @Test
    fun back_notEditing_hidesShade() =
        kosmos.runTest {
            val isEditing by collectLastValue(editModeViewModel.isEditing)
            assertThat(isEditing).isFalse()

            assertThat(actions?.get(Back)).isEqualTo(HideOverlay(Overlays.QuickSettingsShade))
        }

    @Test
    fun back_whileEditing_doesNotHideShade() =
        kosmos.runTest {
            editModeViewModel.startEditing()

            assertThat(actions?.get(Back)).isNull()
        }

    @Test
    fun upAboveEdge_whileEditing_doesNotHideShade() =
        kosmos.runTest {
            editModeViewModel.startEditing()

            assertThat(actions?.get(Swipe.Up)).isNull()
        }

    @Test
    fun upFromEdge_whileEditing_hidesShade() =
        kosmos.runTest {
            editModeViewModel.startEditing()

            val userAction = Swipe.Up(fromSource = SceneContainerArea.BottomEdge)
            assertThat(actions?.get(userAction)).isEqualTo(HideOverlay(Overlays.QuickSettingsShade))
        }

    @Test
    fun downFromStartHalf_wideScreen_switchesToNotificationsShade() =
        kosmos.runTest {
            enableDualShade(wideLayout = true)

            val action = actions?.get(Swipe.Down(fromSource = SceneContainerArea.StartHalf))
            assertThat((action as ShowOverlay).overlay).isEqualTo(Overlays.NotificationsShade)
            assertThat((action.hideCurrentOverlays as HideCurrentOverlays.Some).overlays)
                .containsExactly(Overlays.QuickSettingsShade)
        }

    @Test
    fun downFromStartHalf_narrowScreen_doesNothing() =
        kosmos.runTest {
            enableDualShade(wideLayout = false)

            val action = actions?.get(Swipe.Down(fromSource = SceneContainerArea.StartHalf))
            assertThat(action).isNull()
        }

    @Test
    fun downFromTopEdgeStartHalf_wideScreen_switchesToNotificationsShade() =
        kosmos.runTest {
            enableDualShade(wideLayout = true)

            val action = actions?.get(Swipe.Down(fromSource = SceneContainerArea.TopEdgeStartHalf))
            assertThat((action as ShowOverlay).overlay).isEqualTo(Overlays.NotificationsShade)
            assertThat((action.hideCurrentOverlays as HideCurrentOverlays.Some).overlays)
                .containsExactly(Overlays.QuickSettingsShade)
        }

    @Test
    fun downFromTopEdgeStartHalf_narrowScreen_switchesToNotificationsShade() =
        kosmos.runTest {
            enableDualShade(wideLayout = false)

            val action = actions?.get(Swipe.Down(fromSource = SceneContainerArea.TopEdgeStartHalf))
            assertThat((action as ShowOverlay).overlay).isEqualTo(Overlays.NotificationsShade)
            assertThat((action.hideCurrentOverlays as HideCurrentOverlays.Some).overlays)
                .containsExactly(Overlays.QuickSettingsShade)
        }
}
