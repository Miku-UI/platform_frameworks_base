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
package com.android.systemui.deviceentry.domain.interactor

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.Back
import com.android.compose.animation.scene.OverlayKey
import com.android.compose.animation.scene.Swipe
import com.android.compose.animation.scene.UserAction
import com.android.compose.animation.scene.UserActionResult
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.statusbar.pipeline.mobile.data.repository.fakeMobileConnectionsRepository
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class RestrictedModeInteractorTest : SysuiTestCase() {
    private val kosmos = testKosmos()

    val underTest: RestrictedModeInteractor by lazy { kosmos.restrictedModeInteractor }

    @Test
    fun filterUserActions_filtersOutAllActionThatNavigateAwayFromBouncer() =
        kosmos.runTest {
            val unfilteredFlow = MutableStateFlow<Map<UserAction, UserActionResult>>(emptyMap())
            val filteredFlow by collectLastValue(underTest.filteredUserActions(unfilteredFlow))

            fakeMobileConnectionsRepository.isAnySimSecure.value = false
            kosmos.sceneInteractor.changeScene(Scenes.Lockscreen, "test")
            runCurrent()

            val unfilteredActions =
                mapOf(
                    Back to Scenes.Shade,
                    Swipe.Up to UserActionResult.HideOverlay(Overlays.Bouncer),
                    Swipe.Left to Scenes.Lockscreen,
                    Swipe.Right to UserActionResult.ShowOverlay(Overlays.Bouncer),
                    Swipe.Start to Scenes.Dream,
                )
            unfilteredFlow.value = unfilteredActions
            runCurrent()

            assertThat(filteredFlow).isEqualTo(unfilteredActions)

            // Now the filtered results should be active and remove the HideOverlay(Bouncer)
            fakeMobileConnectionsRepository.isAnySimSecure.value = true

            val expectedFilteredActions =
                mapOf(
                    Back to Scenes.Shade,
                    Swipe.Left to Scenes.Lockscreen,
                    Swipe.Right to UserActionResult.ShowOverlay(Overlays.Bouncer),
                    Swipe.Start to Scenes.Dream,
                )

            assertThat(filteredFlow).isEqualTo(expectedFilteredActions)
        }

    @Test
    fun modifyOverlays_notActive_doesNotHideBouncerOverlayWhenOccluded() =
        kosmos.runTest {
            fakeMobileConnectionsRepository.isAnySimSecure.value = false

            kosmos.sceneInteractor.showOverlay(Overlays.Bouncer, "test")
            runCurrent()
            assertThat(kosmos.sceneInteractor.currentOverlays.value)
                .isEqualTo(setOf(Overlays.Bouncer))

            underTest.modifyOverlaysOnSceneChange(Scenes.Occluded)
            runCurrent()
            assertThat(kosmos.sceneInteractor.currentOverlays.value)
                .isEqualTo(setOf(Overlays.Bouncer))
        }

    @Test
    fun modifyOverlays_active_HidesBouncerOverlayWhenOccluded() =
        kosmos.runTest {
            fakeMobileConnectionsRepository.isAnySimSecure.value = true

            kosmos.sceneInteractor.showOverlay(Overlays.Bouncer, "test")
            runCurrent()
            assertThat(kosmos.sceneInteractor.currentOverlays.value)
                .isEqualTo(setOf(Overlays.Bouncer))

            underTest.modifyOverlaysOnSceneChange(Scenes.Occluded)
            runCurrent()
            assertThat(kosmos.sceneInteractor.currentOverlays.value)
                .isEqualTo(emptySet<OverlayKey>())
        }
}
