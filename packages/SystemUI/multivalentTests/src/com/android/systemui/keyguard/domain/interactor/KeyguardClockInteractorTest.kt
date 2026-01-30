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

package com.android.systemui.keyguard.domain.interactor

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.coroutines.collectLastValue
import com.android.systemui.flags.DisableSceneContainer
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.flags.Flags
import com.android.systemui.flags.fakeFeatureFlagsClassic
import com.android.systemui.keyguard.data.repository.fakeKeyguardClockRepository
import com.android.systemui.keyguard.data.repository.fakeKeyguardRepository
import com.android.systemui.keyguard.data.repository.fakeKeyguardTransitionRepository
import com.android.systemui.keyguard.data.repository.keyguardClockRepository
import com.android.systemui.keyguard.data.repository.keyguardRepository
import com.android.systemui.keyguard.shared.model.ClockSize
import com.android.systemui.keyguard.shared.model.ClockSizeSetting
import com.android.systemui.keyguard.shared.model.DozeStateModel
import com.android.systemui.keyguard.shared.model.DozeTransitionModel
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.media.controls.shared.model.MediaData
import com.android.systemui.media.remedia.data.repository.mediaPipelineRepository
import com.android.systemui.shade.domain.interactor.enableSingleShade
import com.android.systemui.shade.domain.interactor.enableSplitShade
import com.android.systemui.statusbar.notification.data.repository.activeNotificationListRepository
import com.android.systemui.statusbar.notification.data.repository.setActiveNotifs
import com.android.systemui.statusbar.notification.stack.data.repository.headsUpNotificationRepository
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class KeyguardClockInteractorTest : SysuiTestCase() {

    private val kosmos = testKosmos()
    private val Kosmos.underTest by Kosmos.Fixture { kosmos.keyguardClockInteractor }

    @Test
    @DisableSceneContainer
    fun clockSize_sceneContainerFlagOff_basedOnRepository() =
        kosmos.runTest {
            val value by collectLastValue(underTest.clockSize)
            fakeKeyguardClockRepository.setSelectedClockSize(ClockSizeSetting.DYNAMIC)
            keyguardClockRepository.setClockSize(ClockSize.LARGE)
            assertThat(value).isEqualTo(ClockSize.LARGE)

            keyguardClockRepository.setClockSize(ClockSize.SMALL)
            assertThat(value).isEqualTo(ClockSize.SMALL)
        }

    @Test
    @DisableSceneContainer
    fun clockSize_sceneContainerFlagOff_smallClockSettingSelected_SMALL() =
        kosmos.runTest {
            val value by collectLastValue(underTest.clockSize)
            fakeKeyguardClockRepository.setSelectedClockSize(ClockSizeSetting.SMALL)
            keyguardClockRepository.setClockSize(ClockSize.LARGE)

            assertThat(value).isEqualTo(ClockSize.SMALL)
        }

    @Test
    @EnableSceneContainer
    fun clockSize_forceSmallClock_SMALL() =
        kosmos.runTest {
            val value by collectLastValue(underTest.clockSize)
            fakeKeyguardClockRepository.setClockSize(ClockSize.SMALL)
            fakeFeatureFlagsClassic.set(Flags.LOCKSCREEN_ENABLE_LANDSCAPE, true)
            fakeKeyguardTransitionRepository.transitionTo(
                KeyguardState.AOD,
                KeyguardState.LOCKSCREEN,
            )
            assertThat(value).isEqualTo(ClockSize.SMALL)
        }

    @Test
    @EnableSceneContainer
    fun clockSize_sceneContainerFlagOn_singleShade_hasNotifs_SMALL() =
        kosmos.runTest {
            val value by collectLastValue(underTest.clockSize)
            enableSingleShade()
            activeNotificationListRepository.setActiveNotifs(1)

            assertThat(value).isEqualTo(ClockSize.SMALL)
        }

    @Test
    @EnableSceneContainer
    fun clockSize_sceneContainerFlagOn_singleShade_hasMedia_SMALL() =
        kosmos.runTest {
            val value by collectLastValue(underTest.clockSize)
            enableSingleShade()
            val userMedia = MediaData().copy(active = true)
            mediaPipelineRepository.addCurrentUserMediaEntry(userMedia)

            assertThat(value).isEqualTo(ClockSize.SMALL)
        }

    @Test
    @EnableSceneContainer
    fun clockSize_sceneContainerFlagOn_splitShade_isMediaVisible_SMALL() =
        kosmos.runTest {
            val value by collectLastValue(underTest.clockSize)
            val userMedia = MediaData().copy(active = true)
            enableSplitShade()
            mediaPipelineRepository.addCurrentUserMediaEntry(userMedia)
            keyguardRepository.setIsDozing(false)

            assertThat(value).isEqualTo(ClockSize.SMALL)
        }

    @Test
    @EnableSceneContainer
    fun clockSize_sceneContainerFlagOn_splitShade_noMedia_LARGE() =
        kosmos.runTest {
            val value by collectLastValue(underTest.clockSize)
            enableSplitShade()
            keyguardRepository.setIsDozing(false)

            assertThat(value).isEqualTo(ClockSize.LARGE)
        }

    @Test
    @EnableSceneContainer
    fun clockSize_sceneContainerFlagOn_splitShade_isDozing_LARGE() =
        kosmos.runTest {
            val value by collectLastValue(underTest.clockSize)
            val userMedia = MediaData().copy(active = true)
            enableSplitShade()
            mediaPipelineRepository.addCurrentUserMediaEntry(userMedia)
            keyguardRepository.setIsDozing(true)

            assertThat(value).isEqualTo(ClockSize.LARGE)
        }

    @Test
    @EnableSceneContainer
    fun clockSize_sceneContainerFlagOn_splitShade_smallClockSettingSelectd_SMALL() =
        kosmos.runTest {
            val value by collectLastValue(underTest.clockSize)
            val userMedia = MediaData().copy(active = true)
            fakeKeyguardClockRepository.setSelectedClockSize(ClockSizeSetting.SMALL)
            enableSplitShade()
            mediaPipelineRepository.addCurrentUserMediaEntry(userMedia)
            keyguardRepository.setIsDozing(true)

            assertThat(value).isEqualTo(ClockSize.SMALL)
        }

    @Test
    @EnableSceneContainer
    fun clockShouldBeCentered_sceneContainerFlagOn_singleShade_true() =
        kosmos.runTest {
            val value by collectLastValue(underTest.clockShouldBeCentered)
            enableSingleShade()
            assertThat(value).isTrue()
        }

    @Test
    @EnableSceneContainer
    fun clockShouldBeCentered_sceneContainerFlagOn_splitShade_noActiveNotifications_true() =
        kosmos.runTest {
            val value by collectLastValue(underTest.clockShouldBeCentered)
            enableSplitShade()
            activeNotificationListRepository.setActiveNotifs(0)
            assertThat(value).isTrue()
        }

    @Test
    @EnableSceneContainer
    fun clockShouldBeCentered_sceneContainerFlagOn_splitShade_hasPulsingNotifications_false() =
        kosmos.runTest {
            val value by collectLastValue(underTest.clockShouldBeCentered)
            enableSplitShade()
            activeNotificationListRepository.setActiveNotifs(1)
            headsUpNotificationRepository.isHeadsUpAnimatingAway.value = true
            keyguardRepository.setIsDozing(true)
            assertThat(value).isFalse()
        }

    @Test
    @EnableSceneContainer
    fun clockShouldBeCentered_sceneContainerFlagOn_splitShade_onAod_true() =
        kosmos.runTest {
            val value by collectLastValue(underTest.clockShouldBeCentered)
            enableSplitShade()
            activeNotificationListRepository.setActiveNotifs(1)
            fakeKeyguardTransitionRepository.transitionTo(
                KeyguardState.LOCKSCREEN,
                KeyguardState.AOD,
            )
            assertThat(value).isTrue()
        }

    @Test
    @EnableSceneContainer
    fun clockShouldBeCentered_sceneContainerFlagOn_splitShade_offAod_false() =
        kosmos.runTest {
            val value by collectLastValue(underTest.clockShouldBeCentered)
            enableSplitShade()
            activeNotificationListRepository.setActiveNotifs(1)
            fakeKeyguardTransitionRepository.transitionTo(
                KeyguardState.AOD,
                KeyguardState.LOCKSCREEN,
            )
            assertThat(value).isFalse()
        }

    @Test
    @DisableSceneContainer
    fun clockShouldBeCentered_sceneContainerFlagOff_singleShade_true() =
        kosmos.runTest {
            val value by collectLastValue(underTest.clockShouldBeCentered)
            enableSingleShade()

            assertThat(value).isTrue()
        }

    @Test
    @DisableSceneContainer
    fun clockShouldBeCentered_sceneContainerFlagOff_splitShade_lockscreen_withNotifs_false() =
        kosmos.runTest {
            val value by collectLastValue(underTest.clockShouldBeCentered)
            enableSplitShade()
            activeNotificationListRepository.setActiveNotifs(1)
            fakeKeyguardTransitionRepository.transitionTo(
                KeyguardState.AOD,
                KeyguardState.LOCKSCREEN,
            )

            assertThat(value).isFalse()
        }

    @Test
    @DisableSceneContainer
    fun clockShouldBeCentered_sceneContainerFlagOff_splitShade_lockscreen_withoutNotifs_true() =
        kosmos.runTest {
            val value by collectLastValue(underTest.clockShouldBeCentered)
            enableSplitShade()
            activeNotificationListRepository.setActiveNotifs(0)
            fakeKeyguardTransitionRepository.transitionTo(
                KeyguardState.AOD,
                KeyguardState.LOCKSCREEN,
            )
            assertThat(value).isTrue()
        }

    @Test
    @DisableSceneContainer
    fun clockShouldBeCentered_sceneContainerFlagOff_splitShade_LsToAod_withNotifs_true() =
        kosmos.runTest {
            val value by collectLastValue(underTest.clockShouldBeCentered)
            enableSplitShade()
            activeNotificationListRepository.setActiveNotifs(1)
            fakeKeyguardTransitionRepository.transitionTo(
                KeyguardState.OFF,
                KeyguardState.LOCKSCREEN,
            )
            assertThat(value).isFalse()
            fakeKeyguardTransitionRepository.transitionTo(
                KeyguardState.LOCKSCREEN,
                KeyguardState.AOD,
            )
            assertThat(value).isTrue()
        }

    @Test
    @DisableSceneContainer
    fun clockShouldBeCentered_sceneContainerFlagOff_splitShade_AodToLs_withNotifs_false() =
        kosmos.runTest {
            val value by collectLastValue(underTest.clockShouldBeCentered)
            enableSplitShade()
            activeNotificationListRepository.setActiveNotifs(1)
            fakeKeyguardTransitionRepository.transitionTo(
                KeyguardState.LOCKSCREEN,
                KeyguardState.AOD,
            )
            assertThat(value).isTrue()
            fakeKeyguardTransitionRepository.transitionTo(
                KeyguardState.AOD,
                KeyguardState.LOCKSCREEN,
            )
            assertThat(value).isFalse()
        }

    @Test
    @DisableSceneContainer
    fun clockShouldBeCentered_sceneContainerFlagOff_splitShade_Aod_withPulsingNotifs_false() =
        kosmos.runTest {
            val value by collectLastValue(underTest.clockShouldBeCentered)
            enableSplitShade()
            fakeKeyguardTransitionRepository.transitionTo(
                KeyguardState.LOCKSCREEN,
                KeyguardState.AOD,
            )
            assertThat(value).isTrue()
            fakeKeyguardRepository.setDozeTransitionModel(
                DozeTransitionModel(
                    from = DozeStateModel.DOZE_AOD,
                    to = DozeStateModel.DOZE_PULSING,
                )
            )
            assertThat(value).isFalse()
        }

    @Test
    @DisableSceneContainer
    fun clockShouldBeCentered_sceneContainerFlagOff_splitShade_LStoGone_withoutNotifs_true() =
        kosmos.runTest {
            val value by collectLastValue(underTest.clockShouldBeCentered)
            enableSplitShade()
            activeNotificationListRepository.setActiveNotifs(0)
            fakeKeyguardTransitionRepository.transitionTo(
                KeyguardState.OFF,
                KeyguardState.LOCKSCREEN,
            )
            assertThat(value).isTrue()
            fakeKeyguardTransitionRepository.transitionTo(
                KeyguardState.LOCKSCREEN,
                KeyguardState.GONE,
            )
            activeNotificationListRepository.setActiveNotifs(1)
            assertThat(value).isTrue()
        }

    @Test
    @DisableSceneContainer
    fun clockShouldBeCentered_sceneContainerFlagOff_splitShade_AodOn_GoneToAOD() =
        kosmos.runTest {
            val value by collectLastValue(underTest.clockShouldBeCentered)
            fakeKeyguardTransitionRepository.transitionTo(
                KeyguardState.AOD,
                KeyguardState.LOCKSCREEN,
            )
            enableSplitShade()
            activeNotificationListRepository.setActiveNotifs(0)
            assertThat(value).isTrue()

            fakeKeyguardTransitionRepository.transitionTo(
                KeyguardState.LOCKSCREEN,
                KeyguardState.GONE,
            )
            activeNotificationListRepository.setActiveNotifs(1)
            assertThat(value).isTrue()

            fakeKeyguardTransitionRepository.transitionTo(KeyguardState.GONE, KeyguardState.AOD)
            assertThat(value).isTrue()
        }

    @Test
    @DisableSceneContainer
    fun clockShouldBeCentered_sceneContainerFlagOff_splitShade_AodOff_GoneToDoze() =
        kosmos.runTest {
            val value by collectLastValue(underTest.clockShouldBeCentered)
            enableSplitShade()
            fakeKeyguardTransitionRepository.transitionTo(
                KeyguardState.DOZING,
                KeyguardState.LOCKSCREEN,
            )
            activeNotificationListRepository.setActiveNotifs(0)
            assertThat(value).isTrue()

            fakeKeyguardTransitionRepository.transitionTo(
                KeyguardState.LOCKSCREEN,
                KeyguardState.GONE,
            )
            activeNotificationListRepository.setActiveNotifs(1)
            assertThat(value).isTrue()

            fakeKeyguardTransitionRepository.transitionTo(KeyguardState.GONE, KeyguardState.DOZING)
            activeNotificationListRepository.setActiveNotifs(1)
            assertThat(value).isTrue()

            fakeKeyguardTransitionRepository.transitionTo(
                KeyguardState.DOZING,
                KeyguardState.LOCKSCREEN,
            )
            activeNotificationListRepository.setActiveNotifs(0)
            assertThat(value).isTrue()
        }
}
