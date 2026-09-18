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

package com.android.systemui.ambientcue.domain.interactor

import android.content.applicationContext
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.FlagsParameterization
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.ObservableTransitionState
import com.android.systemui.Flags.FLAG_DUAL_SHADE
import com.android.systemui.SysuiTestCase
import com.android.systemui.ambientcue.data.repository.ambientCueRepository
import com.android.systemui.ambientcue.data.repository.fake
import com.android.systemui.flags.andSceneContainer
import com.android.systemui.keyguard.data.repository.fakeKeyguardRepository
import com.android.systemui.keyguard.shared.model.StatusBarState
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.plugins.cuebar.ActionModel
import com.android.systemui.plugins.cuebar.IconModel
import com.android.systemui.res.R
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.shade.domain.interactor.enableDualShade
import com.android.systemui.testKosmos
import com.android.systemui.util.time.fakeSystemClock
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.flowOf
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

@RunWith(ParameterizedAndroidJunit4::class)
@SmallTest
class AmbientCueInteractorTest(flags: FlagsParameterization) : SysuiTestCase() {
    private val kosmos = testKosmos()
    private val clock = kosmos.fakeSystemClock

    companion object {
        @JvmStatic
        @Parameters(name = "{0}")
        fun getParams(): List<FlagsParameterization> {
            return FlagsParameterization.allCombinationsOf().andSceneContainer()
        }
    }

    init {
        mSetFlagsRule.setFlagsParameterization(flags)
    }

    @Test
    fun isDeactivated_setTrue_true() =
        kosmos.runTest {
            val isDeactivated by collectLastValue(ambientCueRepository.isDeactivated)
            ambientCueInteractor.setDeactivated(true)
            assertThat(isDeactivated).isTrue()
        }

    @Test
    fun isDeactivated_setFalse_False() =
        kosmos.runTest {
            val isDeactivated by collectLastValue(ambientCueRepository.isDeactivated)
            ambientCueInteractor.setDeactivated(false)
            assertThat(isDeactivated).isFalse()
        }

    @Test
    fun actions_setActions_actionsUpdated() =
        kosmos.runTest {
            val actions by collectLastValue(ambientCueInteractor.actions)
            val testActions =
                listOf(
                    ActionModel(
                        icon =
                            IconModel(
                                small =
                                    applicationContext.resources.getDrawable(
                                        R.drawable.ic_content_paste_spark,
                                        applicationContext.theme,
                                    ),
                                large =
                                    applicationContext.resources.getDrawable(
                                        R.drawable.ic_content_paste_spark,
                                        applicationContext.theme,
                                    ),
                                iconId = "test.icon",
                            ),
                        label = "Sunday Morning",
                        attribution = null,
                        onPerformAction = {},
                        onPerformLongClick = {},
                    )
                )
            ambientCueRepository.fake.setActions(testActions)
            assertThat(actions).isEqualTo(testActions)
        }

    @Test
    fun isImeVisible_setTrue_true() =
        kosmos.runTest {
            val isImeVisible by collectLastValue(ambientCueInteractor.isImeVisible)
            ambientCueInteractor.setImeVisible(true)
            assertThat(isImeVisible).isTrue()
        }

    @Test
    fun isImeVisible_setFalse_false() =
        kosmos.runTest {
            val isImeVisible by collectLastValue(ambientCueInteractor.isImeVisible)
            ambientCueInteractor.setImeVisible(false)
            assertThat(isImeVisible).isFalse()
        }

    @Test
    fun isGestureNav_setTrue_true() =
        kosmos.runTest {
            val isGestureNav by collectLastValue(ambientCueInteractor.isGestureNav)
            ambientCueRepository.fake.setIsGestureNav(true)
            assertThat(isGestureNav).isTrue()
        }

    @Test
    fun isGestureNav_setFalse_false() =
        kosmos.runTest {
            val isGestureNav by collectLastValue(ambientCueInteractor.isGestureNav)
            ambientCueRepository.fake.setIsGestureNav(false)
            assertThat(isGestureNav).isFalse()
        }

    @Test
    fun isTaskBarVisible_setTrue_true() =
        kosmos.runTest {
            val isTaskBarVisible by collectLastValue(ambientCueInteractor.isTaskBarVisible)
            ambientCueRepository.fake.setTaskBarVisible(true)
            assertThat(isTaskBarVisible).isTrue()
        }

    @Test
    fun isTaskBarVisible_setFalse_false() =
        kosmos.runTest {
            val isTaskBarVisible by collectLastValue(ambientCueInteractor.isTaskBarVisible)
            ambientCueRepository.fake.setTaskBarVisible(false)
            assertThat(isTaskBarVisible).isFalse()
        }

    @Test
    fun isOccludedBySystemUi_collapsedShade_noKeyguard_false() =
        kosmos.runTest {
            val isOccludedBySystemUi by collectLastValue(ambientCueInteractor.isOccludedBySystemUi)
            fakeKeyguardRepository.setKeyguardShowing(false)
            fakeKeyguardRepository.setStatusBarState(StatusBarState.SHADE)
            assertThat(isOccludedBySystemUi).isFalse()
        }

    @Test
    fun isOccludedBySystemUi_whenKeyguardVisible_true() =
        kosmos.runTest {
            val isOccludedBySystemUi by collectLastValue(ambientCueInteractor.isOccludedBySystemUi)
            fakeKeyguardRepository.setKeyguardShowing(true)
            assertThat(isOccludedBySystemUi).isTrue()
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun isOccludedBySystemUi_whenExpandedShade_true() =
        kosmos.runTest {
            val isOccludedBySystemUi by collectLastValue(ambientCueInteractor.isOccludedBySystemUi)

            if (SceneContainerFlag.isEnabled) {
                enableDualShade()

                // Simulate the SceneInteractor being idle with the NotificationsShade overlay
                // present and Lockscreen as the underlying scene. This makes shadeExpansion > 0.
                sceneInteractor.setTransitionState(
                    flowOf(
                        ObservableTransitionState.Idle(
                            currentScene = Scenes.Lockscreen,
                            currentOverlays = setOf(Overlays.NotificationsShade),
                        )
                    )
                )
                runCurrent()
            } else {
                // SHADE_LOCKED forces the expansion to 1f in ShadeInteractor
                fakeKeyguardRepository.setStatusBarState(StatusBarState.SHADE_LOCKED)
            }

            assertThat(isOccludedBySystemUi).isTrue()
        }

    @Test
    fun dismissGroupIds_calculatesTtl_and_addsToRepository() =
        kosmos.runTest {
            val startTime = Instant.EPOCH
            clock.setCurrentTime(startTime)

            val idsToDismiss = setOf("group1", "group2")
            ambientCueInteractor.dismissGroupIds(idsToDismiss)

            // Verify Repo received the IDs AND the correct calculated TTL (Start + 24h)
            val dismissedMap = ambientCueRepository.fake.dismissedGroups.value

            assertThat(dismissedMap.keys).containsExactlyElementsIn(idsToDismiss)
            val expectedExpiry = startTime.plus(AmbientCueInteractor.DISMISSAL_TTL)
            assertThat(dismissedMap["group1"]).isEqualTo(expectedExpiry)
            assertThat(dismissedMap["group2"]).isEqualTo(expectedExpiry)
        }

    @Test
    fun actions_filtersDismissedItems() =
        kosmos.runTest {
            val actions by collectLastValue(ambientCueInteractor.actions)
            val action1 = createAction("action1", "group1") // To be dismissed
            val action2 = createAction("action2", "group2") // Kept
            ambientCueRepository.fake.setActions(listOf(action1, action2))

            // Pre-load repository with dismissal for "group1" valid for 1 more hour
            val validExpiry = clock.currentTime().plus(Duration.ofHours(1))
            ambientCueRepository.fake.addDismissedGroups(setOf("group1"), validExpiry)
            runCurrent()

            // Verify Interactor filters it out
            assertThat(actions).hasSize(1)
            assertThat(actions!!.first().label).isEqualTo("action2")
        }

    @Test
    fun actions_showsExpiredDismissedItems() =
        kosmos.runTest {
            val actions by collectLastValue(ambientCueInteractor.actions)
            val action1 = createAction("action1", "group1")
            ambientCueRepository.fake.setActions(listOf(action1))

            // Pre-load repository with dismissal that expired 1 second ago
            val expiredTime = clock.currentTime().minusSeconds(1)
            ambientCueRepository.fake.addDismissedGroups(setOf("group1"), expiredTime)
            runCurrent()

            // Verify Interactor allows it because TTL passed
            assertThat(actions).hasSize(1)
            assertThat(actions!!.first().label).isEqualTo("action1")
        }

    // Helper for creating simple ActionModels
    private fun createAction(label: String, groupId: String?): ActionModel {
        return ActionModel(
            icon = mock(),
            label = label,
            attribution = null,
            onPerformAction = {},
            onPerformLongClick = {},
            dismissalGroupId = groupId,
        )
    }
}
