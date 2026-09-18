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

package com.android.systemui.shade.ui.viewmodel

import android.content.Intent
import android.content.res.Configuration
import android.content.testableContext
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.provider.AlarmClock
import android.provider.Settings
import android.telephony.SubscriptionManager.PROFILE_CLASS_UNSET
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.ObservableTransitionState
import com.android.compose.animation.scene.OverlayKey
import com.android.systemui.Flags.FLAG_DUAL_SHADE
import com.android.systemui.SysuiTestCase
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.plugins.activityStarter
import com.android.systemui.privacy.PrivacyApplication
import com.android.systemui.privacy.PrivacyItem
import com.android.systemui.privacy.PrivacyType
import com.android.systemui.res.R
import com.android.systemui.scene.SceneHelper.setDeviceEntered
import com.android.systemui.scene.SceneHelper.setScene
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.shade.data.repository.fakePrivacyChipRepository
import com.android.systemui.shade.domain.interactor.disableDualShade
import com.android.systemui.shade.domain.interactor.enableDualShade
import com.android.systemui.shade.domain.interactor.enableSingleShade
import com.android.systemui.shade.ui.composable.ChipHighlightModel
import com.android.systemui.statusbar.pipeline.mobile.data.model.SubscriptionModel
import com.android.systemui.statusbar.pipeline.mobile.domain.interactor.fakeMobileIconsInteractor
import com.android.systemui.statusbar.policy.configurationController
import com.android.systemui.testKosmos
import com.android.systemui.util.mockito.argThat
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.flowOf
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatcher
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.verify

@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableSceneContainer
class ShadeHeaderViewModelTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val Kosmos.underTest by
        Kosmos.Fixture { shadeHeaderViewModelFactory.create(ignoreTestHarness = true) }

    @Before
    fun setUp() {
        kosmos.underTest.activateIn(kosmos.testScope)
    }

    @Test
    fun mobileSubIds_update() =
        kosmos.runTest {
            fakeMobileIconsInteractor.filteredSubscriptions.value = listOf(SUB_1)

            assertThat(underTest.mobileSubIds).containsExactly(1)

            fakeMobileIconsInteractor.filteredSubscriptions.value = listOf(SUB_1, SUB_2)

            assertThat(underTest.mobileSubIds).containsExactly(1, 2)
        }

    @Test
    fun onClockClicked_enableDesktopStatusBarFalse_launchesClock() =
        kosmos.runTest {
            setUseDesktopStatusBar(enable = false)
            underTest.onClockClicked()

            verify(activityStarter)
                .postStartActivityDismissingKeyguard(
                    argThat(IntentMatcherAction(AlarmClock.ACTION_SHOW_ALARMS)),
                    anyInt(),
                )
        }

    @Test
    fun onClockClicked_enableDesktopStatusBarTrueAndSingleShade_launchesClock() =
        kosmos.runTest {
            setUseDesktopStatusBar(enable = true)
            enableSingleShade()

            underTest.onClockClicked()

            verify(activityStarter)
                .postStartActivityDismissingKeyguard(
                    argThat(IntentMatcherAction(AlarmClock.ACTION_SHOW_ALARMS)),
                    anyInt(),
                )
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun onClockClicked_enableDesktopStatusBarTrueAndDualShade_openNotifShade() =
        kosmos.runTest {
            setUseDesktopStatusBar(enable = true)
            enableDualShade()
            setDeviceEntered(true)
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)

            underTest.onClockClicked()

            assertThat(currentScene).isEqualTo(Scenes.Gone)
            assertThat(currentOverlays).contains(Overlays.NotificationsShade)
            assertThat(currentOverlays).doesNotContain(Overlays.QuickSettingsShade)
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun onClockClicked_enablesetDesktopStatusBarTrueOnNotifShade_closesShade() =
        kosmos.runTest {
            setUseDesktopStatusBar(enable = true)
            enableDualShade()
            setDeviceEntered(true)
            setOverlay(Overlays.NotificationsShade)
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)

            underTest.onClockClicked()

            assertThat(currentScene).isEqualTo(Scenes.Gone)
            assertThat(currentOverlays).isEmpty()
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun onClockClicked_enableDesktopStatusBarTrueOnQSShade_openNotifShade() =
        kosmos.runTest {
            setUseDesktopStatusBar(enable = true)
            enableDualShade()
            setDeviceEntered(true)
            setOverlay(Overlays.QuickSettingsShade)
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)

            underTest.onClockClicked()

            assertThat(currentScene).isEqualTo(Scenes.Gone)
            assertThat(currentOverlays).contains(Overlays.NotificationsShade)
            assertThat(currentOverlays).doesNotContain(Overlays.QuickSettingsShade)
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun enableDesktopStatusBarTrue_inactiveChipHighlightReturnsTransparent() =
        kosmos.runTest {
            setUseDesktopStatusBar(enable = true)

            assertThat(underTest.inactiveChipHighlight).isEqualTo(ChipHighlightModel.Transparent)
        }

    @Test
    fun enableDesktopStatusBarTrue_inactiveChipHighlightReturnsWeak() =
        kosmos.runTest {
            setUseDesktopStatusBar(enable = false)

            assertThat(underTest.inactiveChipHighlight).isEqualTo(ChipHighlightModel.Weak)
        }

    @Test
    fun onShadeCarrierGroupClicked_launchesNetworkSettings() =
        kosmos.runTest {
            underTest.onShadeCarrierGroupClicked()

            verify(activityStarter)
                .postStartActivityDismissingKeyguard(
                    argThat(IntentMatcherAction(Settings.ACTION_WIRELESS_SETTINGS)),
                    anyInt(),
                )
        }

    @Test
    @DisableFlags(FLAG_DUAL_SHADE)
    fun onSystemIconChipClicked_locked_collapsesShadeToLockscreen() =
        kosmos.runTest {
            disableDualShade()
            setDeviceEntered(false)
            setScene(Scenes.Shade)

            underTest.onSystemIconChipClicked()

            assertThat(sceneInteractor.currentScene.value).isEqualTo(Scenes.Lockscreen)
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun onSystemIconChipClicked_lockedOnQsShade_collapsesShadeToLockscreen() =
        kosmos.runTest {
            enableDualShade()
            setDeviceEntered(false)
            setOverlay(Overlays.QuickSettingsShade)
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)

            underTest.onSystemIconChipClicked()

            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(currentOverlays).isEmpty()
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun onSystemIconChipClicked_lockedOnNotifShade_expandsQsShade() =
        kosmos.runTest {
            enableDualShade()
            setDeviceEntered(false)
            setOverlay(Overlays.NotificationsShade)
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)

            underTest.onSystemIconChipClicked()

            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(currentOverlays).contains(Overlays.QuickSettingsShade)
            assertThat(currentOverlays).doesNotContain(Overlays.NotificationsShade)
        }

    @Test
    @DisableFlags(FLAG_DUAL_SHADE)
    fun onSystemIconChipClicked_unlocked_collapsesShadeToGone() =
        kosmos.runTest {
            disableDualShade()
            setDeviceEntered(true)
            setScene(Scenes.Shade)

            underTest.onSystemIconChipClicked()

            assertThat(sceneInteractor.currentScene.value).isEqualTo(Scenes.Gone)
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun onSystemIconChipClicked_unlockedOnQsShade_collapsesShadeToGone() =
        kosmos.runTest {
            enableDualShade()
            setDeviceEntered(true)
            setOverlay(Overlays.QuickSettingsShade)
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)

            underTest.onSystemIconChipClicked()

            assertThat(currentScene).isEqualTo(Scenes.Gone)
            assertThat(currentOverlays).isEmpty()
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun onSystemIconChipClicked_unlockedOnNotifShade_expandsQsShade() =
        kosmos.runTest {
            enableDualShade()
            setDeviceEntered(true)
            setOverlay(Overlays.NotificationsShade)
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)

            underTest.onSystemIconChipClicked()

            assertThat(currentScene).isEqualTo(Scenes.Gone)
            assertThat(currentOverlays).contains(Overlays.QuickSettingsShade)
            assertThat(currentOverlays).doesNotContain(Overlays.NotificationsShade)
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun onNotificationIconChipClicked_lockedOnNotifShade_collapsesShadeToLockscreen_opensClock() =
        kosmos.runTest {
            enableDualShade()
            setDeviceEntered(false)
            setOverlay(Overlays.NotificationsShade)
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)

            underTest.onNotificationIconChipClicked()

            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(currentOverlays).isEmpty()
            verify(activityStarter)
                .postStartActivityDismissingKeyguard(
                    argThat(IntentMatcherAction(AlarmClock.ACTION_SHOW_ALARMS)),
                    anyInt(),
                )
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun onNotificationIconChipClicked_lockedOnQsShade_expandsNotifShade() =
        kosmos.runTest {
            enableDualShade()
            setDeviceEntered(false)
            setOverlay(Overlays.QuickSettingsShade)
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)

            underTest.onNotificationIconChipClicked()

            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(currentOverlays).contains(Overlays.NotificationsShade)
            assertThat(currentOverlays).doesNotContain(Overlays.QuickSettingsShade)
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun onNotificationIconChipClicked_unlockedOnNotifShade_collapsesShadeToGone_opensClock() =
        kosmos.runTest {
            enableDualShade()
            setDeviceEntered(true)
            setOverlay(Overlays.NotificationsShade)
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)

            underTest.onNotificationIconChipClicked()

            assertThat(currentScene).isEqualTo(Scenes.Gone)
            assertThat(currentOverlays).isEmpty()
            verify(activityStarter)
                .postStartActivityDismissingKeyguard(
                    argThat(IntentMatcherAction(AlarmClock.ACTION_SHOW_ALARMS)),
                    anyInt(),
                )
        }

    @Test
    @EnableFlags(FLAG_DUAL_SHADE)
    fun onNotificationIconChipClicked_unlockedOnQsShade_expandsNotifShade() =
        kosmos.runTest {
            enableDualShade()
            setDeviceEntered(true)
            setOverlay(Overlays.QuickSettingsShade)
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)

            underTest.onNotificationIconChipClicked()

            assertThat(currentScene).isEqualTo(Scenes.Gone)
            assertThat(currentOverlays).contains(Overlays.NotificationsShade)
            assertThat(currentOverlays).doesNotContain(Overlays.QuickSettingsShade)
        }

    @Test
    fun isPrivacyChipVisible_updates() =
        kosmos.runTest {
            fakePrivacyChipRepository.setPrivacyItems(emptyList())

            assertThat(underTest.isPrivacyChipVisible).isFalse()

            fakePrivacyChipRepository.setPrivacyItems(
                listOf(
                    PrivacyItem(
                        privacyType = PrivacyType.TYPE_CAMERA,
                        application = PrivacyApplication("", 0),
                    )
                )
            )

            assertThat(underTest.isPrivacyChipVisible).isTrue()
        }

    @Test
    fun isPrivacyChipEnabled_noIndicationEnabled() =
        kosmos.runTest {
            fakePrivacyChipRepository.setIsMicCameraIndicationEnabled(false)
            fakePrivacyChipRepository.setIsLocationIndicationEnabled(false)

            assertThat(underTest.isPrivacyChipEnabled).isFalse()
        }

    @Test
    fun isPrivacyChipEnabled_micCameraIndicationEnabled() =
        kosmos.runTest {
            fakePrivacyChipRepository.setIsMicCameraIndicationEnabled(true)
            fakePrivacyChipRepository.setIsLocationIndicationEnabled(false)

            assertThat(underTest.isPrivacyChipEnabled).isTrue()
        }

    @Test
    fun isPrivacyChipEnabled_locationIndicationEnabled() =
        kosmos.runTest {
            fakePrivacyChipRepository.setIsMicCameraIndicationEnabled(false)
            fakePrivacyChipRepository.setIsLocationIndicationEnabled(true)

            assertThat(underTest.isPrivacyChipEnabled).isTrue()
        }

    @Test
    fun isPrivacyChipEnabled_allIndicationEnabled() =
        kosmos.runTest {
            fakePrivacyChipRepository.setIsMicCameraIndicationEnabled(true)
            fakePrivacyChipRepository.setIsLocationIndicationEnabled(true)

            assertThat(underTest.isPrivacyChipEnabled).isTrue()
        }

    companion object {
        private val SUB_1 =
            SubscriptionModel(
                subscriptionId = 1,
                isOpportunistic = false,
                carrierName = "Carrier 1",
                profileClass = PROFILE_CLASS_UNSET,
            )
        private val SUB_2 =
            SubscriptionModel(
                subscriptionId = 2,
                isOpportunistic = false,
                carrierName = "Carrier 2",
                profileClass = PROFILE_CLASS_UNSET,
            )
    }

    private fun Kosmos.setOverlay(key: OverlayKey) {
        sceneInteractor.showOverlay(key, "test")
        sceneInteractor.setTransitionState(
            flowOf(
                ObservableTransitionState.Idle(
                    currentScene = sceneInteractor.currentScene.value,
                    currentOverlays = setOf(key),
                )
            )
        )
    }

    private fun Kosmos.setUseDesktopStatusBar(enable: Boolean) {
        testableContext.orCreateTestableResources.addOverride(
            R.bool.config_useDesktopStatusBar,
            enable,
        )
        configurationController.onConfigurationChanged(Configuration())
    }
}

private class IntentMatcherAction(private val action: String) : ArgumentMatcher<Intent> {
    override fun matches(argument: Intent?): Boolean {
        return argument?.action == action
    }
}
