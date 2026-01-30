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

package com.android.systemui.shade.ui.viewmodel

import android.testing.TestableLooper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.Back
import com.android.compose.animation.scene.ObservableTransitionState
import com.android.compose.animation.scene.SceneKey
import com.android.compose.animation.scene.Swipe
import com.android.compose.animation.scene.SwipeDirection
import com.android.compose.animation.scene.UserActionResult
import com.android.systemui.SysuiTestCase
import com.android.systemui.authentication.data.repository.fakeAuthenticationRepository
import com.android.systemui.authentication.shared.model.AuthenticationMethodModel.None
import com.android.systemui.authentication.shared.model.AuthenticationMethodModel.Pin
import com.android.systemui.deviceentry.data.repository.fakeDeviceEntryRepository
import com.android.systemui.deviceentry.domain.interactor.deviceEntryInteractor
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.keyguard.data.repository.fakeDeviceEntryFingerprintAuthRepository
import com.android.systemui.keyguard.domain.interactor.keyguardEnabledInteractor
import com.android.systemui.keyguard.shared.model.SuccessFingerprintAuthenticationStatus
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.collectValues
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.qs.panels.ui.viewmodel.editModeViewModel
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.domain.resolver.homeSceneFamilyResolver
import com.android.systemui.scene.domain.startable.sceneContainerStartable
import com.android.systemui.scene.shared.model.SceneFamilies
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.scene.shared.model.TransitionKeys.ToSplitShade
import com.android.systemui.shade.domain.interactor.disableDualShade
import com.android.systemui.shade.domain.interactor.enableSingleShade
import com.android.systemui.shade.domain.interactor.enableSplitShade
import com.android.systemui.shade.domain.startable.shadeStartable
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.flow.flowOf
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
@TestableLooper.RunWithLooper
@EnableSceneContainer
class ShadeUserActionsViewModelTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()

    private val underTest: ShadeUserActionsViewModel by lazy { kosmos.shadeUserActionsViewModel }

    @Before
    fun setUp() {
        with(kosmos) {
            sceneContainerStartable.start()
            disableDualShade()
            underTest.activateIn(testScope)
        }
    }

    @Test
    fun upOrBackTransitionSceneKey_deviceLocked_lockScreen() =
        kosmos.runTest {
            val actions by collectLastValue(underTest.actions)
            val homeScene by collectLastValue(homeSceneFamilyResolver.resolvedScene)
            fakeAuthenticationRepository.setAuthenticationMethod(Pin)

            assertThat((actions?.get(Swipe.Up) as? UserActionResult.ChangeScene)?.toScene)
                .isEqualTo(SceneFamilies.Home)
            assertThat((actions?.get(Back) as? UserActionResult.ChangeScene)?.toScene)
                .isEqualTo(SceneFamilies.Home)
            assertThat(homeScene).isEqualTo(Scenes.Lockscreen)
        }

    @Test
    fun upOrBackTransitionSceneKey_deviceUnlocked_gone() =
        kosmos.runTest {
            val actions by collectLastValue(underTest.actions)
            val homeScene by collectLastValue(homeSceneFamilyResolver.resolvedScene)
            fakeAuthenticationRepository.setAuthenticationMethod(Pin)
            setDeviceEntered(true)

            assertThat((actions?.get(Swipe.Up) as? UserActionResult.ChangeScene)?.toScene)
                .isEqualTo(SceneFamilies.Home)
            assertThat((actions?.get(Back) as? UserActionResult.ChangeScene)?.toScene)
                .isEqualTo(SceneFamilies.Home)
            assertThat(homeScene).isEqualTo(Scenes.Gone)
        }

    @Test
    fun upOrBackTransitionSceneKey_keyguardDisabled_gone() =
        kosmos.runTest {
            val actions by collectLastValue(underTest.actions)
            val homeScene by collectLastValue(kosmos.homeSceneFamilyResolver.resolvedScene)
            fakeAuthenticationRepository.setAuthenticationMethod(Pin)
            keyguardEnabledInteractor.notifyKeyguardEnabled(false)

            assertThat((actions?.get(Swipe.Up) as? UserActionResult.ChangeScene)?.toScene)
                .isEqualTo(SceneFamilies.Home)
            assertThat((actions?.get(Back) as? UserActionResult.ChangeScene)?.toScene)
                .isEqualTo(SceneFamilies.Home)
            assertThat(homeScene).isEqualTo(Scenes.Gone)
        }

    @Test
    fun upOrBackTransitionSceneKey_authMethodSwipe_lockscreenNotDismissed_goesToLockscreen() =
        kosmos.runTest {
            val actions by collectLastValue(underTest.actions)
            val homeScene by collectLastValue(kosmos.homeSceneFamilyResolver.resolvedScene)
            fakeDeviceEntryRepository.setLockscreenEnabled(true)
            fakeAuthenticationRepository.setAuthenticationMethod(None)
            sceneInteractor.changeScene(Scenes.Lockscreen, "reason")

            assertThat((actions?.get(Swipe.Up) as? UserActionResult.ChangeScene)?.toScene)
                .isEqualTo(SceneFamilies.Home)
            assertThat((actions?.get(Back) as? UserActionResult.ChangeScene)?.toScene)
                .isEqualTo(SceneFamilies.Home)
            assertThat(homeScene).isEqualTo(Scenes.Lockscreen)
        }

    @Test
    fun upOrBackTransitionSceneKey_authMethodSwipe_lockscreenDismissed_goesToGone() =
        kosmos.runTest {
            val actions by collectLastValue(underTest.actions)
            val homeScene by collectLastValue(kosmos.homeSceneFamilyResolver.resolvedScene)
            fakeDeviceEntryRepository.setLockscreenEnabled(true)
            fakeAuthenticationRepository.setAuthenticationMethod(None)
            sceneInteractor.changeScene(Scenes.Gone, "reason")

            assertThat((actions?.get(Swipe.Up) as? UserActionResult.ChangeScene)?.toScene)
                .isEqualTo(SceneFamilies.Home)
            assertThat((actions?.get(Back) as? UserActionResult.ChangeScene)?.toScene)
                .isEqualTo(SceneFamilies.Home)
            assertThat(homeScene).isEqualTo(Scenes.Gone)
        }

    @Test
    fun upOrBackTransitionKey_splitShadeEnabled_isGoneToSplitShade() =
        kosmos.runTest {
            val actions by collectLastValue(underTest.actions)
            enableSplitShade()

            assertThat(actions?.get(Swipe.Up)?.transitionKey).isEqualTo(ToSplitShade)
            assertThat(actions?.get(Back)?.transitionKey).isEqualTo(ToSplitShade)
        }

    @Test
    fun upOrBackTransitionKey_splitShadeDisable_isNull() =
        kosmos.runTest {
            val actions by collectLastValue(underTest.actions)
            enableSingleShade()

            assertThat(actions?.get(Swipe.Up)?.transitionKey).isNull()
            assertThat(actions?.get(Back)?.transitionKey).isNull()
        }

    @Test
    fun downTransitionSceneKey_inSplitShade_null() =
        kosmos.runTest {
            enableSplitShade()
            shadeStartable.start()
            val actions by collectLastValue(underTest.actions)
            assertThat((actions?.get(Swipe.Down) as? UserActionResult.ChangeScene)?.toScene)
                .isNull()
        }

    @Test
    fun downTransitionSceneKey_notSplitShade_quickSettings() =
        kosmos.runTest {
            enableSingleShade()
            shadeStartable.start()
            val actions by collectLastValue(underTest.actions)
            assertThat((actions?.get(Swipe.Down) as? UserActionResult.ChangeScene)?.toScene)
                .isEqualTo(Scenes.QuickSettings)
        }

    @Test
    fun upOrBackTransitionSceneKey_editing_noTransition() =
        kosmos.runTest {
            val actions by collectLastValue(underTest.actions)

            editModeViewModel.startEditing()
            assertThat(
                    actions!!.keys.filterIsInstance<Swipe>().filter {
                        it.direction == SwipeDirection.Up
                    }
                )
                .isEmpty()
            assertThat(actions!!.keys.filterIsInstance<Back>()).isEmpty()
        }

    @Test
    fun upOrBackTransitionSceneKey_backToCommunal() =
        kosmos.runTest {
            val actions by collectLastValue(underTest.actions)
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            sceneInteractor.changeScene(Scenes.Communal, "")
            assertThat(currentScene).isEqualTo(Scenes.Communal)
            sceneInteractor.changeScene(Scenes.Shade, "")
            assertThat(currentScene).isEqualTo(Scenes.Shade)

            assertThat(actions?.get(Swipe.Up)).isEqualTo(UserActionResult(Scenes.Communal))
            assertThat(actions?.get(Back)).isEqualTo(UserActionResult(Scenes.Communal))
        }

    @Test
    fun upOrBackTransitionSceneKey_neverGoesBackToShadeScene() =
        kosmos.runTest {
            val actions by collectValues(underTest.actions)
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            sceneInteractor.changeScene(Scenes.Shade, "")
            assertThat(currentScene).isEqualTo(Scenes.Shade)

            sceneInteractor.changeScene(Scenes.QuickSettings, "")
            assertThat(currentScene).isEqualTo(Scenes.QuickSettings)

            actions.forEachIndexed { index, map ->
                assertWithMessage(
                        "Actions on index $index is incorrectly mapping back to the Shade scene!"
                    )
                    .that((map[Swipe.Up] as? UserActionResult.ChangeScene)?.toScene)
                    .isNotEqualTo(Scenes.Shade)

                assertWithMessage(
                        "Actions on index $index is incorrectly mapping back to the Shade scene!"
                    )
                    .that((map[Back] as? UserActionResult.ChangeScene)?.toScene)
                    .isNotEqualTo(Scenes.Shade)
            }
        }

    private fun Kosmos.setDeviceEntered(isEntered: Boolean) {
        if (isEntered) {
            // Unlock the device marking the device has entered.
            fakeDeviceEntryFingerprintAuthRepository.setAuthenticationStatus(
                SuccessFingerprintAuthenticationStatus(0, true)
            )
        }
        setScene(if (isEntered) Scenes.Gone else Scenes.Lockscreen)
        assertThat(deviceEntryInteractor.isDeviceEntered.value).isEqualTo(isEntered)
    }

    private fun Kosmos.setScene(key: SceneKey) {
        sceneInteractor.changeScene(key, "test")
        sceneInteractor.setTransitionState(flowOf(ObservableTransitionState.Idle(key)))
    }
}
