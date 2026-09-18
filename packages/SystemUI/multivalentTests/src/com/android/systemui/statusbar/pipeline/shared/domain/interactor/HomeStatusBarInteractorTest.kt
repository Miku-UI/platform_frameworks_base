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

package com.android.systemui.statusbar.pipeline.shared.domain.interactor

import android.app.StatusBarManager.CAMERA_LAUNCH_SOURCE_POWER_DOUBLE_TAP
import android.app.StatusBarManager.DISABLE2_NONE
import android.app.StatusBarManager.DISABLE_CLOCK
import android.app.StatusBarManager.DISABLE_NONE
import android.app.StatusBarManager.DISABLE_NOTIFICATION_ICONS
import android.app.StatusBarManager.DISABLE_SYSTEM_INFO
import android.platform.test.flag.junit.FlagsParameterization
import android.telephony.CarrierConfigManager
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.flags.andSceneContainer
import com.android.systemui.keyguard.data.repository.keyguardOcclusionRepository
import com.android.systemui.keyguard.domain.interactor.keyguardInteractor
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.statusbar.disableflags.data.repository.fakeDisableFlagsRepository
import com.android.systemui.statusbar.disableflags.shared.model.DisableFlagsModel
import com.android.systemui.statusbar.pipeline.airplane.data.repository.airplaneModeRepository
import com.android.systemui.statusbar.pipeline.airplane.data.repository.fake
import com.android.systemui.statusbar.pipeline.mobile.data.model.SystemUiCarrierConfig
import com.android.systemui.statusbar.pipeline.mobile.data.repository.carrierConfigRepository
import com.android.systemui.statusbar.pipeline.mobile.data.repository.configWithOverride
import com.android.systemui.statusbar.pipeline.mobile.data.repository.fake
import com.android.systemui.statusbar.pipeline.mobile.domain.interactor.fakeMobileIconsInteractor
import com.android.systemui.statusbar.pipeline.shared.connectivityConstants
import com.android.systemui.statusbar.pipeline.shared.domain.HomeStatusBarHelper.launchSecureCamera
import com.android.systemui.statusbar.pipeline.shared.domain.HomeStatusBarHelper.setStatusBarWindowState
import com.android.systemui.statusbar.pipeline.shared.domain.HomeStatusBarHelper.transitionKeyguardToGone
import com.android.systemui.statusbar.pipeline.shared.fake
import com.android.systemui.statusbar.window.shared.model.StatusBarWindowState
import com.android.systemui.testKosmosNew
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import org.junit.runner.RunWith
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

@SmallTest
@RunWith(ParameterizedAndroidJunit4::class)
class HomeStatusBarInteractorTest(flags: FlagsParameterization) : SysuiTestCase() {
    init {
        mSetFlagsRule.setFlagsParameterization(flags)
    }

    val kosmos = testKosmosNew()
    val testScope = kosmos.testScope
    private val disableFlagsRepo by lazy { kosmos.fakeDisableFlagsRepository }
    private val Kosmos.underTest by Kosmos.Fixture { kosmos.homeStatusBarInteractor }

    @Test
    fun visibilityViaDisableFlags_allDisabled() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.visibilityViaDisableFlags)

            disableFlagsRepo.disableFlags.value =
                DisableFlagsModel(
                    DISABLE_CLOCK or DISABLE_NOTIFICATION_ICONS or DISABLE_SYSTEM_INFO,
                    DISABLE2_NONE,
                    animate = false,
                )

            assertThat(latest!!.isClockAllowed).isFalse()
            assertThat(latest!!.areNotificationIconsAllowed).isFalse()
            assertThat(latest!!.isSystemInfoAllowed).isFalse()
        }

    @Test
    fun visibilityViaDisableFlags_allEnabled() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.visibilityViaDisableFlags)

            disableFlagsRepo.disableFlags.value =
                DisableFlagsModel(DISABLE_NONE, DISABLE2_NONE, animate = false)

            assertThat(latest!!.isClockAllowed).isTrue()
            assertThat(latest!!.areNotificationIconsAllowed).isTrue()
            assertThat(latest!!.isSystemInfoAllowed).isTrue()
        }

    @Test
    fun visibilityViaDisableFlags_animateFalse() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.visibilityViaDisableFlags)

            disableFlagsRepo.disableFlags.value =
                DisableFlagsModel(DISABLE_NONE, DISABLE2_NONE, animate = false)

            assertThat(latest!!.animate).isFalse()
        }

    @Test
    fun visibilityViaDisableFlags_animateTrue() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.visibilityViaDisableFlags)

            disableFlagsRepo.disableFlags.value =
                DisableFlagsModel(DISABLE_NONE, DISABLE2_NONE, animate = true)

            assertThat(latest!!.animate).isTrue()
        }

    @Test
    fun shouldShowOperatorName_trueIfCarrierConfigSaysSoAndDeviceHasData() =
        kosmos.runTest {
            // GIVEN default data subId is 1
            fakeMobileIconsInteractor.defaultDataSubId.value = 1
            // GIVEN Config is enabled
            carrierConfigRepository.fake.configsById[1] =
                SystemUiCarrierConfig(
                    1,
                    configWithOverride(
                        CarrierConfigManager.KEY_SHOW_OPERATOR_NAME_IN_STATUSBAR_BOOL,
                        true,
                    ),
                )

            // GIVEN airplane mode is off
            airplaneModeRepository.fake.isAirplaneMode.value = false

            // GIVEN hasDataCapabilities is true
            connectivityConstants.fake.hasDataCapabilities = true

            val latest by collectLastValue(underTest.shouldShowOperatorName)

            // THEN we should show the operator name
            assertThat(latest).isTrue()
        }

    @Test
    fun shouldShowOperatorName_falseNoDataCapabilities() =
        kosmos.runTest {
            // GIVEN default data subId is 1
            fakeMobileIconsInteractor.defaultDataSubId.value = 1
            // GIVEN Config is enabled
            carrierConfigRepository.fake.configsById[1] =
                SystemUiCarrierConfig(
                    1,
                    configWithOverride(
                        CarrierConfigManager.KEY_SHOW_OPERATOR_NAME_IN_STATUSBAR_BOOL,
                        true,
                    ),
                )

            // GIVEN airplane mode is off
            airplaneModeRepository.fake.isAirplaneMode.value = true

            // WHEN hasDataCapabilities is false
            connectivityConstants.fake.hasDataCapabilities = false

            val latest by collectLastValue(underTest.shouldShowOperatorName)

            // THEN we should not show the operator name
            assertThat(latest).isFalse()
        }

    @Test
    fun shouldShowOperatorName_falseWhenConfigIsOff() =
        kosmos.runTest {
            // GIVEN default data subId is 1
            fakeMobileIconsInteractor.defaultDataSubId.value = 1
            // GIVEN airplane mode is off
            airplaneModeRepository.fake.isAirplaneMode.value = false

            // WHEN Config is disabled
            carrierConfigRepository.fake.configsById[1] =
                SystemUiCarrierConfig(
                    1,
                    configWithOverride(
                        CarrierConfigManager.KEY_SHOW_OPERATOR_NAME_IN_STATUSBAR_BOOL,
                        false,
                    ),
                )

            val latest by collectLastValue(underTest.shouldShowOperatorName)

            // THEN we should not show the operator name
            assertThat(latest).isFalse()
        }

    @Test
    fun shouldShowOperatorName_falseIfAirplaneMode() =
        kosmos.runTest {
            // GIVEN default data subId is 1
            fakeMobileIconsInteractor.defaultDataSubId.value = 1
            // GIVEN Config is enabled
            carrierConfigRepository.fake.configsById[1] =
                SystemUiCarrierConfig(
                    1,
                    configWithOverride(
                        CarrierConfigManager.KEY_SHOW_OPERATOR_NAME_IN_STATUSBAR_BOOL,
                        true,
                    ),
                )

            // WHEN airplane mode is on
            airplaneModeRepository.fake.isAirplaneMode.value = true

            val latest by collectLastValue(underTest.shouldShowOperatorName)

            // THEN we should not show the operator name
            assertThat(latest).isFalse()
        }

    @Test
    fun secureCamera_firstPartOfLaunch_shouldHideTrue() =
        kosmos.runTest {
            setStatusBarWindowState(StatusBarWindowState.Showing)

            val latest by collectLastValue(underTest.shouldHideStatusBarForSecureCamera)

            // In the first part of the secure camera launch, the gesture is invoked but we aren't
            // yet fully occluding and the status bar window is still showing
            keyguardInteractor.onCameraLaunchDetected(
                CAMERA_LAUNCH_SOURCE_POWER_DOUBLE_TAP,
                isSecureCamera = true,
            )

            assertThat(latest).isTrue()
        }

    @Test
    fun secureCamera_secondPartOfLaunch_shouldHideTrue() =
        kosmos.runTest {
            setStatusBarWindowState(StatusBarWindowState.Showing)

            val latest by collectLastValue(underTest.shouldHideStatusBarForSecureCamera)

            // In the second part of the secure camera launch, the gesture is invoked and we know
            // we're occluding but the status bar window is still showing
            launchSecureCamera()

            assertThat(latest).isTrue()
        }

    @Test
    fun secureCamera_lastPartOfLaunch_shouldHideTrue() =
        kosmos.runTest {
            setStatusBarWindowState(StatusBarWindowState.Showing)

            val latest by collectLastValue(underTest.shouldHideStatusBarForSecureCamera)

            // In the last part of the secure camera launch, the gesture is invoked and we know
            // we're occluding and the status bar window is hidden
            launchSecureCamera()
            setStatusBarWindowState(StatusBarWindowState.Hidden)

            assertThat(latest).isTrue()
        }

    @Test
    fun secureCamera_andStatusBarWindowShowing_shouldHideFalse() =
        kosmos.runTest {
            setStatusBarWindowState(StatusBarWindowState.Showing)

            val latest by collectLastValue(underTest.shouldHideStatusBarForSecureCamera)

            // Initial launch
            launchSecureCamera()
            setStatusBarWindowState(StatusBarWindowState.Hidden)

            assertThat(latest).isTrue()

            // WHEN user swipes down to show status bar
            setStatusBarWindowState(StatusBarWindowState.Showing)

            // THEN we don't hide the icons
            assertThat(latest).isFalse()

            // WHEN the status bar disappears after a few seconds
            setStatusBarWindowState(StatusBarWindowState.Hidden)

            // THEN we hide the icons
            assertThat(latest).isTrue()
        }

    @Test
    fun secureCamera_showsIconsOnceCameraClosedAndAuthenticated() =
        kosmos.runTest {
            setStatusBarWindowState(StatusBarWindowState.Showing)

            val latest by collectLastValue(underTest.shouldHideStatusBarForSecureCamera)

            // Initial launch
            launchSecureCamera()
            setStatusBarWindowState(StatusBarWindowState.Hidden)

            assertThat(latest).isTrue()

            // WHEN keyguard gets unlocked
            keyguardOcclusionRepository.setOccludedFromRemoteAnimation(
                onTop = false,
                taskInfo = null,
            )
            transitionKeyguardToGone()

            // THEN the icons can show again
            assertThat(latest).isFalse()
        }

    companion object {
        @JvmStatic
        @Parameters(name = "{0}")
        fun getParams(): List<FlagsParameterization> {
            return FlagsParameterization.allCombinationsOf().andSceneContainer()
        }
    }
}
