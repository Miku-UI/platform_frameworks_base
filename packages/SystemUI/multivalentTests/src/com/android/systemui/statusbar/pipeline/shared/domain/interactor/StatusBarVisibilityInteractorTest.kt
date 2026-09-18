/*
 * Copyright (C) 2026 The Android Open Source Project
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

import android.content.res.Configuration
import android.platform.test.flag.junit.FlagsParameterization
import android.view.Display.DEFAULT_DISPLAY
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.flags.andSceneContainer
import com.android.systemui.keyguard.data.repository.fakeKeyguardTransitionRepository
import com.android.systemui.keyguard.data.repository.keyguardOcclusionRepository
import com.android.systemui.keyguard.shared.model.KeyguardState
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.res.R
import com.android.systemui.scene.data.repository.sceneContainerRepository
import com.android.systemui.scene.shared.flag.SceneContainerFlag
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.shade.data.repository.fakeShadeDisplaysRepository
import com.android.systemui.shade.shadeTestUtil
import com.android.systemui.statusbar.notification.data.repository.UnconfinedFakeHeadsUpRowRepository
import com.android.systemui.statusbar.notification.headsup.PinnedStatus
import com.android.systemui.statusbar.notification.stack.data.repository.headsUpNotificationRepository
import com.android.systemui.statusbar.pipeline.shared.domain.HomeStatusBarHelper.launchSecureCamera
import com.android.systemui.statusbar.pipeline.shared.domain.HomeStatusBarHelper.transitionKeyguardToGone
import com.android.systemui.statusbar.pipeline.shared.ui.viewmodel.homeStatusBarViewModelFactory
import com.android.systemui.statusbar.policy.configurationController
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test
import org.junit.runner.RunWith
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

@RunWith(ParameterizedAndroidJunit4::class)
@SmallTest
class StatusBarVisibilityInteractorTest(flags: FlagsParameterization) : SysuiTestCase() {
    init {
        mSetFlagsRule.setFlagsParameterization(flags)
    }

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val Kosmos.underTest by Kosmos.Fixture { statusBarVisibilityInteractor }

    @Test
    @EnableSceneContainer
    fun isHomeStatusBarAllowed_sceneLockscreen_notOccluded_false() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.isHomeStatusBarAllowed)

            kosmos.sceneContainerRepository.instantlyTransitionTo(Scenes.Lockscreen)
            kosmos.keyguardOcclusionRepository.setOccludedFromRemoteAnimation(
                false,
                taskInfo = null,
            )

            assertThat(latest).isFalse()
        }

    @Test
    @EnableSceneContainer
    fun isHomeStatusBarAllowed_sceneLockscreen_occluded_true() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.isHomeStatusBarAllowed)

            kosmos.sceneContainerRepository.instantlyTransitionTo(Scenes.Lockscreen)
            kosmos.keyguardOcclusionRepository.setOccludedFromRemoteAnimation(true, taskInfo = null)

            assertThat(latest).isTrue()
        }

    @Test
    @EnableSceneContainer
    fun isHomeStatusBarAllowed_overlayBouncer_false() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.isHomeStatusBarAllowed)

            kosmos.sceneContainerRepository.instantlyTransitionTo(Scenes.Lockscreen)
            kosmos.sceneContainerRepository.showOverlay(Overlays.Bouncer)

            assertThat(latest).isFalse()
        }

    @Test
    @EnableSceneContainer
    fun isHomeStatusBarAllowed_sceneCommunal_false() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.isHomeStatusBarAllowed)

            kosmos.sceneContainerRepository.instantlyTransitionTo(Scenes.Communal)

            assertThat(latest).isFalse()
        }

    @Test
    @EnableSceneContainer
    fun isHomeStatusBarAllowed_sceneShade_false() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.isHomeStatusBarAllowed)

            kosmos.sceneContainerRepository.instantlyTransitionTo(Scenes.Shade)

            assertThat(latest).isFalse()
        }

    @Test
    @EnableSceneContainer
    fun isHomeStatusBarAllowed_sceneGone_true() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.isHomeStatusBarAllowed)

            kosmos.sceneContainerRepository.instantlyTransitionTo(Scenes.Gone)

            assertThat(latest).isTrue()
        }

    @Test
    @EnableSceneContainer
    fun isHomeStatusBarAllowed_sceneGoneWithNotificationsShadeOverlay_false() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.isHomeStatusBarAllowed)

            kosmos.sceneContainerRepository.instantlyTransitionTo(Scenes.Gone)
            kosmos.sceneContainerRepository.showOverlay(Overlays.NotificationsShade)
            kosmos.shadeTestUtil.setShadeExpansion(1f)
            runCurrent()

            assertThat(latest).isFalse()
        }

    @Test
    @EnableSceneContainer
    fun isHomeStatusBarAllowed_QsVisibleButInExternalDisplay_defaultStatusBarVisible() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.isHomeStatusBarAllowed)

            kosmos.sceneContainerRepository.instantlyTransitionTo(Scenes.Gone)
            kosmos.sceneContainerRepository.showOverlay(Overlays.QuickSettingsShade)
            kosmos.fakeShadeDisplaysRepository.setDisplayId(EXTERNAL_DISPLAY)
            kosmos.fakeShadeDisplaysRepository.setPendingDisplayId(EXTERNAL_DISPLAY)
            runCurrent()

            assertThat(latest).isTrue()
        }

    @Test
    @EnableSceneContainer
    fun isHomeStatusBarAllowed_qsVisibleInThisDisplay_thisStatusBarInvisible() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.isHomeStatusBarAllowed)

            kosmos.sceneContainerRepository.instantlyTransitionTo(Scenes.Gone)
            kosmos.sceneContainerRepository.showOverlay(Overlays.QuickSettingsShade)
            kosmos.shadeTestUtil.setQsExpansion(1f)
            kosmos.fakeShadeDisplaysRepository.setDisplayId(DEFAULT_DISPLAY)
            runCurrent()

            assertThat(latest).isFalse()
        }

    @Test
    @EnableSceneContainer
    fun isHomeStatusBarAllowed_qsExpandedOnDefaultDisplay_statusBarInAnotherDisplay_visible() =
        kosmos.runTest {
            val underTest = homeStatusBarViewModelFactory(EXTERNAL_DISPLAY)
            val latest by collectLastValue(underTest.isHomeStatusBarAllowed)

            kosmos.sceneContainerRepository.instantlyTransitionTo(Scenes.Gone)
            kosmos.sceneContainerRepository.showOverlay(Overlays.QuickSettingsShade)
            runCurrent()

            assertThat(latest).isTrue()
        }

    @Test
    fun isHomeStatusBarAllowed_onLockscreen_invisible() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.isHomeStatusBarAllowed)

            if (SceneContainerFlag.isEnabled) {
                kosmos.sceneContainerRepository.instantlyTransitionTo(Scenes.Lockscreen)
            } else {
                kosmos.fakeKeyguardTransitionRepository.transitionTo(
                    KeyguardState.GONE,
                    KeyguardState.LOCKSCREEN,
                )
            }
            runCurrent()
            assertThat(latest).isFalse()
        }

    @Test
    @EnableSceneContainer
    fun isHomeStatusBarAllowed_onExternalDisplayWithLocksceren_invisible() =
        kosmos.runTest {
            val underTest = homeStatusBarViewModelFactory(EXTERNAL_DISPLAY)
            val latest by collectLastValue(underTest.isHomeStatusBarAllowed)

            kosmos.sceneContainerRepository.instantlyTransitionTo(Scenes.Lockscreen)
            runCurrent()

            assertThat(latest).isFalse()
        }

    @Test
    @EnableSceneContainer
    fun isHomeStatusBarAllowed_onExternalDisplay_whenNotificationShadeIsVisibleOnDefaultDisplay_isTrue() =
        kosmos.runTest {
            val underTest = homeStatusBarViewModelFactory(EXTERNAL_DISPLAY)
            val latest by collectLastValue(underTest.isHomeStatusBarAllowed)

            sceneContainerRepository.instantlyTransitionTo(Scenes.Gone)
            sceneContainerRepository.showOverlay(Overlays.NotificationsShade)
            fakeShadeDisplaysRepository.setDisplayId(DEFAULT_DISPLAY)

            assertThat(latest).isTrue()
        }

    @Test
    @EnableSceneContainer
    fun isHomeStatusBarAllowed_onDefaultDisplay_whenShadeIsVisibleOnDefaultDisplay_isFalse() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.isHomeStatusBarAllowed)

            sceneContainerRepository.instantlyTransitionTo(Scenes.Gone)
            sceneContainerRepository.showOverlay(Overlays.QuickSettingsShade)
            kosmos.shadeTestUtil.setQsExpansion(1f)
            fakeShadeDisplaysRepository.setDisplayId(DEFAULT_DISPLAY)

            assertThat(latest).isFalse()
        }

    @Test
    @EnableSceneContainer
    fun isHomeStatusBarAllowed_onExternalDisplay_whenShadeIsVisibleOnDefaultDisplay_isTrue() =
        kosmos.runTest {
            val underTest = homeStatusBarViewModelFactory(EXTERNAL_DISPLAY)
            val latest by collectLastValue(underTest.isHomeStatusBarAllowed)

            sceneContainerRepository.instantlyTransitionTo(Scenes.Gone)
            sceneContainerRepository.showOverlay(Overlays.QuickSettingsShade)
            fakeShadeDisplaysRepository.setDisplayId(DEFAULT_DISPLAY)

            assertThat(latest).isTrue()
        }

    @Test
    fun isShadeWindowOnThisDisplay_thisDisplayIsPending_true() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.isShadeWindowOnThisDisplay)

            kosmos.fakeShadeDisplaysRepository.setPendingDisplayId(DEFAULT_DISPLAY)

            assertThat(latest).isTrue()
        }

    @Test
    fun isShadeWindowOnThisDisplay_otherDisplayIsPending_false() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.isShadeWindowOnThisDisplay)

            kosmos.fakeShadeDisplaysRepository.setPendingDisplayId(EXTERNAL_DISPLAY)

            assertThat(latest).isFalse()
        }

    @Test
    fun canShowOngoingActivityChips_statusBarHidden_noSecureCamera_noHun_false() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.canShowOngoingActivityChips)

            // home status bar not allowed
            kosmos.sceneContainerRepository.instantlyTransitionTo(Scenes.Lockscreen)
            kosmos.keyguardOcclusionRepository.setOccludedFromRemoteAnimation(
                false,
                taskInfo = null,
            )

            assertThat(latest).isFalse()
        }

    @Test
    fun canShowOngoingActivityChips_statusBarNotHidden_noSecureCamera_noHun_true() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.canShowOngoingActivityChips)

            transitionKeyguardToGone()

            assertThat(latest).isTrue()
        }

    @Test
    fun canShowOngoingActivityChips_statusBarNotHidden_secureCamera_noHun_false() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.canShowOngoingActivityChips)

            launchSecureCamera()

            assertThat(latest).isFalse()
        }

    @Test
    fun canShowOngoingActivityChips_statusBarNotHidden_noSecureCamera_hunBySystem_true() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.canShowOngoingActivityChips)

            transitionKeyguardToGone()

            headsUpNotificationRepository.setNotifications(
                UnconfinedFakeHeadsUpRowRepository(
                    key = "key",
                    pinnedStatus = MutableStateFlow(PinnedStatus.PinnedBySystem),
                )
            )

            assertThat(latest).isTrue()
        }

    @Test
    fun canShowOngoingActivityChips_statusBarNotHidden_noSecureCamera_hunByUser_true() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.canShowOngoingActivityChips)

            transitionKeyguardToGone()

            headsUpNotificationRepository.setNotifications(
                UnconfinedFakeHeadsUpRowRepository(
                    key = "key",
                    pinnedStatus = MutableStateFlow(PinnedStatus.PinnedByUser),
                )
            )

            assertThat(latest).isTrue()
        }

    @Test
    fun canShowOngoingActivityChips_statusBarNotAllowedByScene_useDesktopStatusBar_true() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.canShowOngoingActivityChips)

            // GIVEN home status bar is NOT allowed by Scene(e.g. on lockscreen)
            kosmos.sceneContainerRepository.instantlyTransitionTo(Scenes.Lockscreen)
            kosmos.keyguardOcclusionRepository.setOccludedFromRemoteAnimation(
                false,
                taskInfo = null,
            )

            // WHEN desktop status bar is in use
            overrideResource(R.bool.config_useDesktopStatusBar, true)
            kosmos.configurationController.onConfigurationChanged(Configuration())

            // THEN chips can still be shown
            assertThat(latest).isTrue()
        }

    @Test
    fun canShowOngoingActivityChips_secureCamera_useDesktopStatusBar_true() =
        kosmos.runTest {
            val latest by collectLastValue(underTest.canShowOngoingActivityChips)

            // GIVEN secure camera IS active (which usually hides chips)
            launchSecureCamera()

            // WHEN desktop status bar is in use
            overrideResource(R.bool.config_useDesktopStatusBar, true)
            kosmos.configurationController.onConfigurationChanged(Configuration())

            // THEN chips can still be shown
            assertThat(latest).isTrue()
        }

    companion object {
        @JvmStatic
        @Parameters(name = "{0}")
        fun getParams(): List<FlagsParameterization> {
            return FlagsParameterization.allCombinationsOf().andSceneContainer()
        }

        const val EXTERNAL_DISPLAY = 1
    }
}
