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

package com.android.systemui.statusbar.notification.stack.domain.interactor

import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.ObservableTransitionState
import com.android.systemui.Flags.FLAG_DUAL_SHADE
import com.android.systemui.SysuiTestCase
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.advanceTimeBy
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.scene.data.repository.sceneContainerRepository
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.shade.domain.interactor.enableDualShade
import com.android.systemui.shade.domain.interactor.enableSingleShade
import com.android.systemui.shade.domain.interactor.enableSplitShade
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableSceneContainer
class LockscreenNotificationsInteractorTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val Kosmos.underTest: LockscreenNotificationsInteractor by
        Kosmos.Fixture { lockscreenNotificationsInteractor }

    private val maxNotifications = 5 // something that's not -1
    private val calculateMaxNotifications: (Int, Boolean) -> Int = { _, _ -> maxNotifications }

    @Test
    fun singleShade_IdleOnLockScreen_isConstrained() =
        kosmos.runTest {
            enableSingleShade()

            val lockScreenConfig by
                collectLastValue(underTest.getLockscreenDisplayConfig(calculateMaxNotifications))

            setTransitionState(ObservableTransitionState.Idle(Scenes.Lockscreen))

            isConstrained(lockScreenConfig)
        }

    @Test
    @DisableFlags(FLAG_DUAL_SHADE)
    fun splitShade_IdleOnLockScreen_isConstrained() =
        kosmos.runTest {
            enableSplitShade()

            val lockScreenConfig by
                collectLastValue(underTest.getLockscreenDisplayConfig(calculateMaxNotifications))

            setTransitionState(ObservableTransitionState.Idle(Scenes.Lockscreen))

            isConstrained(lockScreenConfig)
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun dualShadeShade_IdleOnLockScreen_isConstrained() =
        kosmos.runTest {
            enableDualShade()

            val lockScreenConfig by
                collectLastValue(underTest.getLockscreenDisplayConfig(calculateMaxNotifications))

            setTransitionState(ObservableTransitionState.Idle(Scenes.Lockscreen))

            isConstrained(lockScreenConfig)
        }

    @Test
    fun singleShade_IdleOnShade_noLimit() =
        kosmos.runTest {
            enableSingleShade()

            val lockScreenConfig by
                collectLastValue(underTest.getLockscreenDisplayConfig(calculateMaxNotifications))

            setTransitionState(ObservableTransitionState.Idle(Scenes.Shade))

            assertNoLimit(lockScreenConfig)
        }

    @Test
    @DisableFlags(FLAG_DUAL_SHADE)
    fun splitShade_IdleOnShade_noLimit() =
        kosmos.runTest {
            enableSplitShade()

            val lockScreenConfig by
                collectLastValue(underTest.getLockscreenDisplayConfig(calculateMaxNotifications))

            setTransitionState(ObservableTransitionState.Idle(Scenes.Shade))

            assertNoLimit(lockScreenConfig)
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun dualShadeShade_NotificationShadeOverLockScreen_noLimit() =
        kosmos.runTest {
            enableDualShade()

            val lockScreenConfig by
                collectLastValue(underTest.getLockscreenDisplayConfig(calculateMaxNotifications))

            setTransitionState(
                ObservableTransitionState.Idle(
                    currentScene = Scenes.Lockscreen,
                    currentOverlays = setOf(Overlays.NotificationsShade),
                )
            )

            assertNoLimit(lockScreenConfig)
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun dualShadeShade_QuickSettingsOverLockScreen_isConstrained() =
        kosmos.runTest {
            enableDualShade()

            val lockScreenConfig by
                collectLastValue(underTest.getLockscreenDisplayConfig(calculateMaxNotifications))

            setTransitionState(
                ObservableTransitionState.Idle(
                    currentScene = Scenes.Lockscreen,
                    currentOverlays = setOf(Overlays.QuickSettingsShade),
                )
            )

            isConstrained(lockScreenConfig)
        }

    @Test
    fun singleShade_TransitionToShade_noUserInputAndNoProgres_isConstrained() =
        kosmos.runTest {
            enableSingleShade()

            val lockScreenConfig by
                collectLastValue(underTest.getLockscreenDisplayConfig(calculateMaxNotifications))

            val expectedFraction = 0.1f
            setTransitionState(
                ObservableTransitionState.Transition.ChangeScene(
                    fromScene = Scenes.Lockscreen,
                    toScene = Scenes.Shade,
                    currentScene = flowOf(Scenes.Lockscreen),
                    currentOverlays = emptySet(),
                    progress = MutableStateFlow(expectedFraction),
                    isInitiatedByUserInput = true,
                    isUserInputOngoing = flowOf(true),
                    previewProgress = flowOf(0f),
                    isInPreviewStage = flowOf(false),
                )
            )

            isConstrained(lockScreenConfig)
        }

    @Test
    fun singleShade_TransitionToShade_noUserInputAndProgres_noLimit() =
        kosmos.runTest {
            enableSingleShade()

            val lockScreenConfig by
                collectLastValue(underTest.getLockscreenDisplayConfig(calculateMaxNotifications))

            val expectedFraction = 0.5f
            setTransitionState(
                ObservableTransitionState.Transition.ChangeScene(
                    fromScene = Scenes.Lockscreen,
                    toScene = Scenes.Shade,
                    currentScene = flowOf(Scenes.Lockscreen),
                    currentOverlays = emptySet(),
                    progress = MutableStateFlow(expectedFraction),
                    isInitiatedByUserInput = true,
                    isUserInputOngoing = flowOf(false),
                    previewProgress = flowOf(0f),
                    isInPreviewStage = flowOf(false),
                )
            )

            assertNoLimit(lockScreenConfig)
        }

    @Test
    fun singleShade_TransitionToShade_userInputOngoingAndProgres_isConstrained() =
        kosmos.runTest {
            enableSingleShade()

            val lockScreenConfig by
                collectLastValue(underTest.getLockscreenDisplayConfig(calculateMaxNotifications))

            val expectedFraction = 0.5f
            setTransitionState(
                ObservableTransitionState.Transition.ChangeScene(
                    fromScene = Scenes.Lockscreen,
                    toScene = Scenes.Shade,
                    currentScene = flowOf(Scenes.Lockscreen),
                    currentOverlays = emptySet(),
                    progress = MutableStateFlow(expectedFraction),
                    isInitiatedByUserInput = true,
                    isUserInputOngoing = flowOf(true),
                    previewProgress = flowOf(0f),
                    isInPreviewStage = flowOf(false),
                )
            )

            isConstrained(lockScreenConfig)
        }

    @Test
    fun singleShade_TransitionFromCommunalToLockscreen_isConstrained() =
        kosmos.runTest {
            enableSingleShade()

            val lockScreenConfig by
                collectLastValue(underTest.getLockscreenDisplayConfig(calculateMaxNotifications))

            val expectedFraction = 0.5f
            setTransitionState(
                ObservableTransitionState.Transition.ChangeScene(
                    fromScene = Scenes.Communal,
                    toScene = Scenes.Lockscreen,
                    currentScene = flowOf(Scenes.Communal),
                    currentOverlays = emptySet(),
                    progress = MutableStateFlow(expectedFraction),
                    isInitiatedByUserInput = true,
                    isUserInputOngoing = flowOf(true),
                    previewProgress = flowOf(0f),
                    isInPreviewStage = flowOf(false),
                )
            )

            isConstrained(lockScreenConfig)
        }

    private fun Kosmos.setTransitionState(transitionState: ObservableTransitionState) {
        sceneContainerRepository.setTransitionState(flowOf(transitionState))
        // workaround to wait for the transition
        advanceTimeBy(50)
    }

    private fun assertNoLimit(lockScreenConfig: LockscreenDisplayConfig?) {
        assertThat(lockScreenConfig).isNotNull()
        assertThat(lockScreenConfig!!.isOnLockscreen).isFalse()
        assertThat(lockScreenConfig.maxNotifications).isEqualTo(-1)
    }

    private fun isConstrained(lockScreenConfig: LockscreenDisplayConfig?) {
        assertThat(lockScreenConfig).isNotNull()
        assertThat(lockScreenConfig!!.isOnLockscreen).isTrue()
        assertThat(lockScreenConfig.maxNotifications).isEqualTo(maxNotifications)
    }
}
