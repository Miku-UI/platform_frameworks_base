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

package com.android.systemui.statusbar.ui.viewmodel

import android.app.StatusBarManager.DISABLE_NONE
import android.app.StatusBarManager.DISABLE_SYSTEM_INFO
import android.platform.test.flag.junit.FlagsParameterization
import android.view.View
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.desktop.domain.interactor.enableUsingDesktopStatusBar
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.flags.andSceneContainer
import com.android.systemui.keyguard.data.repository.fakeDeviceEntryFaceAuthRepository
import com.android.systemui.keyguard.data.repository.fakeKeyguardRepository
import com.android.systemui.keyguard.data.repository.fakeKeyguardTransitionRepository
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.scene.data.repository.sceneContainerRepository
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.statusbar.disableflags.data.repository.fakeDisableFlagsRepository
import com.android.systemui.statusbar.disableflags.shared.model.DisableFlagsModel
import com.android.systemui.statusbar.events.data.repository.systemStatusEventAnimationRepository
import com.android.systemui.statusbar.events.shared.model.SystemEventAnimationState
import com.android.systemui.statusbar.notification.data.repository.FakeHeadsUpRowRepository
import com.android.systemui.statusbar.notification.stack.data.repository.headsUpNotificationRepository
import com.android.systemui.statusbar.pipeline.shared.ui.model.VisibilityModel
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

@SmallTest
@RunWith(ParameterizedAndroidJunit4::class)
class KeyguardStatusBarViewModelTest(flags: FlagsParameterization) : SysuiTestCase() {
    private val kosmos = testKosmosNew()

    val Kosmos.underTest by
        Kosmos.Fixture {
            keyguardStatusBarViewModelFactory.create().apply { activateIn(testScope) }
        }

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
    fun isVisible_lockscreen_true() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.isVisible)
            kosmos.sceneContainerRepository.instantlyTransitionTo(Scenes.Lockscreen)

            assertThat(latest).isTrue()
        }

    @Test
    fun isVisible_communal_true() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.isVisible)
            kosmos.sceneContainerRepository.instantlyTransitionTo(Scenes.Communal)

            assertThat(latest).isTrue()
        }

    @Test
    fun isVisible_dozing_false() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.isVisible)
            kosmos.sceneContainerRepository.instantlyTransitionTo(Scenes.Lockscreen)

            fakeKeyguardRepository.setIsDozing(true)

            assertThat(latest).isFalse()
        }

    @Test
    fun isVisible_sceneShade_false() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.isVisible)

            kosmos.sceneContainerRepository.instantlyTransitionTo(Scenes.Shade)

            assertThat(latest).isFalse()
        }

    @Test
    fun isVisible_notificationsShadeOverlay_false() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.isVisible)

            kosmos.sceneContainerRepository.instantlyTransitionTo(Scenes.Lockscreen)
            kosmos.sceneContainerRepository.showOverlay(Overlays.NotificationsShade)

            assertThat(latest).isFalse()
        }

    @Test
    fun isVisible_quickSettingsShadeOverlay_false() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.isVisible)

            kosmos.sceneContainerRepository.instantlyTransitionTo(Scenes.Lockscreen)
            kosmos.sceneContainerRepository.showOverlay(Overlays.QuickSettingsShade)

            assertThat(latest).isFalse()
        }

    @Test
    fun isVisible_sceneBouncer_false() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.isVisible)

            kosmos.sceneContainerRepository.instantlyTransitionTo(Scenes.Lockscreen)
            kosmos.sceneContainerRepository.showOverlay(Overlays.Bouncer)

            assertThat(latest).isFalse()
        }

    @Test
    fun isVisible_useDesktopStatusBarEnabled_false() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.isVisible)
            kosmos.sceneContainerRepository.instantlyTransitionTo(Scenes.Lockscreen)

            assertThat(latest).isTrue()

            kosmos.enableUsingDesktopStatusBar()

            assertThat(latest).isFalse()
        }

    @Test
    @EnableSceneContainer
    fun isVisible_headsUpShown_true() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.isVisible)

            // WHEN HUN displayed on the bypass lock screen
            headsUpNotificationRepository.setNotifications(
                FakeHeadsUpRowRepository("key 0", isPinned = true)
            )
            fakeKeyguardTransitionRepository.emitInitialStepsFromOff(
                KeyguardState.LOCKSCREEN,
                testSetup = true,
            )
            kosmos.sceneContainerRepository.instantlyTransitionTo(Scenes.Lockscreen)
            fakeDeviceEntryFaceAuthRepository.isBypassEnabled.value = true

            // THEN KeyguardStatusBar is still visible
            assertThat(latest).isTrue()
        }

    @Test
    fun isVisible_sceneLockscreen_andNotDozing_andNotShowingHeadsUpStatusBar_true() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.isVisible)

            sceneContainerRepository.instantlyTransitionTo(Scenes.Lockscreen)
            fakeKeyguardRepository.setIsDozing(false)

            assertThat(latest).isTrue()
        }

    @Test
    fun isSystemInfoVisible_allowedByDisableFlags_visible() =
        kosmos.runTest {
            // GIVEN system info is enabled
            fakeDisableFlagsRepository.disableFlags.value =
                DisableFlagsModel(disable1 = DISABLE_NONE, disable2 = DISABLE_NONE, animate = false)

            // THEN it is visible
            assertThat(underTest.systemInfoCombinedVis.baseVisibility.visibility)
                .isEqualTo(View.VISIBLE)
        }

    @Test
    fun isSystemInfoVisible_notAllowedByDisableFlags_invisible() =
        kosmos.runTest {
            // GIVEN system info is disabled
            fakeDisableFlagsRepository.disableFlags.value =
                DisableFlagsModel(
                    disable1 = DISABLE_SYSTEM_INFO,
                    disable2 = DISABLE_NONE,
                    animate = false,
                )
            assertThat(fakeDisableFlagsRepository.disableFlags.value.isSystemInfoEnabled)
                .isEqualTo(false)
            // THEN it is invisible
            assertThat(underTest.systemInfoCombinedVis.baseVisibility.visibility)
                .isEqualTo(View.INVISIBLE)
        }

    @Test
    fun systemInfoCombineVis_animationsPassThrough() =
        kosmos.runTest {

            // GIVEN normal state
            fakeDisableFlagsRepository.disableFlags.value =
                DisableFlagsModel(disable1 = DISABLE_NONE, disable2 = DISABLE_NONE, animate = true)
            systemStatusEventAnimationRepository.animationState.value =
                SystemEventAnimationState.Idle

            // VERIFY initial state
            assertThat(underTest.systemInfoCombinedVis.baseVisibility)
                .isEqualTo(VisibilityModel(visibility = View.VISIBLE, shouldAnimateChange = true))
            assertThat(underTest.systemInfoCombinedVis.animationState)
                .isEqualTo(SystemEventAnimationState.Idle)

            // WHEN animating in
            systemStatusEventAnimationRepository.animationState.value =
                SystemEventAnimationState.AnimatingIn

            // THEN visibility remains visible, but shouldAnimateChange becomes false
            assertThat(underTest.systemInfoCombinedVis.baseVisibility)
                .isEqualTo(VisibilityModel(visibility = View.VISIBLE, shouldAnimateChange = false))
            assertThat(underTest.systemInfoCombinedVis.animationState)
                .isEqualTo(SystemEventAnimationState.AnimatingIn)

            // WHEN running chip animation
            systemStatusEventAnimationRepository.animationState.value =
                SystemEventAnimationState.RunningChipAnim

            // THEN state updates
            assertThat(underTest.systemInfoCombinedVis.animationState)
                .isEqualTo(SystemEventAnimationState.RunningChipAnim)

            // WHEN animating out
            systemStatusEventAnimationRepository.animationState.value =
                SystemEventAnimationState.AnimatingOut

            // THEN state updates
            assertThat(underTest.systemInfoCombinedVis.animationState)
                .isEqualTo(SystemEventAnimationState.AnimatingOut)
            assertThat(underTest.systemInfoCombinedVis.baseVisibility)
                .isEqualTo(VisibilityModel(visibility = View.VISIBLE, shouldAnimateChange = false))
        }
}
