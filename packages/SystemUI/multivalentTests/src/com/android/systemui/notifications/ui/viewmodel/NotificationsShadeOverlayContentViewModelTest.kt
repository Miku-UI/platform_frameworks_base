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

package com.android.systemui.notifications.ui.viewmodel

import android.app.StatusBarManager.DISABLE2_QUICK_SETTINGS
import android.content.res.Configuration
import android.content.res.mainResources
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.testing.TestableLooper
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.content.state.TransitionState
import com.android.systemui.Flags.FLAG_NOTIFICATION_SHADE_BLUR
import com.android.systemui.SysuiTestCase
import com.android.systemui.authentication.data.repository.FakeAuthenticationRepository
import com.android.systemui.authentication.domain.interactor.AuthenticationResult
import com.android.systemui.authentication.domain.interactor.authenticationInteractor
import com.android.systemui.desktop.domain.interactor.DesktopInteractor
import com.android.systemui.desktop.domain.interactor.desktopInteractor
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.keyguard.ui.transitions.blurConfig
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.Kosmos.Fixture
import com.android.systemui.kosmos.applicationCoroutineScope
import com.android.systemui.kosmos.backgroundScope
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testDispatcher
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.media.controls.domain.pipeline.interactor.mediaCarouselInteractor
import com.android.systemui.media.controls.shared.model.MediaData
import com.android.systemui.media.remedia.data.repository.mediaPipelineRepository
import com.android.systemui.media.remedia.ui.viewmodel.factory.mediaViewModelFactory
import com.android.systemui.power.domain.interactor.PowerInteractor.Companion.setAsleepForTest
import com.android.systemui.power.domain.interactor.PowerInteractor.Companion.setAwakeForTest
import com.android.systemui.power.domain.interactor.powerInteractor
import com.android.systemui.res.R
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.domain.startable.sceneContainerStartable
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.shade.domain.interactor.enableDualShade
import com.android.systemui.shade.domain.interactor.enableSingleShade
import com.android.systemui.shade.domain.interactor.enableSplitShade
import com.android.systemui.shade.domain.interactor.shadeInteractor
import com.android.systemui.shade.domain.interactor.shadeModeInteractor
import com.android.systemui.shade.ui.viewmodel.notificationsShadeOverlayContentViewModel
import com.android.systemui.shade.ui.viewmodel.shadeHeaderViewModelFactory
import com.android.systemui.statusbar.core.StatusBarForDesktop
import com.android.systemui.statusbar.disableflags.data.repository.fakeDisableFlagsRepository
import com.android.systemui.statusbar.disableflags.domain.interactor.disableFlagsInteractor
import com.android.systemui.statusbar.notification.stack.data.repository.notificationPlaceholderRepository
import com.android.systemui.statusbar.notification.stack.data.repository.notificationViewHeightRepository
import com.android.systemui.statusbar.notification.stack.domain.interactor.NotificationStackAppearanceInteractor
import com.android.systemui.statusbar.notification.stack.domain.interactor.notificationStackAppearanceInteractor
import com.android.systemui.statusbar.notification.stack.ui.viewmodel.notificationsPlaceholderViewModelFactory
import com.android.systemui.statusbar.policy.configurationController
import com.android.systemui.testKosmos
import com.android.systemui.window.data.repository.fakeWindowRootViewBlurRepository
import com.android.systemui.window.domain.interactor.windowRootViewBlurInteractor
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.update
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
@TestableLooper.RunWithLooper
@EnableSceneContainer
class NotificationsShadeOverlayContentViewModelTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val Kosmos.underTest by Fixture { notificationsShadeOverlayContentViewModel }

    @Before
    fun setUp() =
        with(kosmos) {
            sceneContainerStartable.start()
            enableDualShade()
            underTest.activateIn(testScope)
        }

    @Test
    fun showHeader_desktopStatusBarDisabled_true() =
        kosmos.runTest {
            setUseDesktopStatusBar(false)

            assertThat(underTest.showHeader).isTrue()
        }

    @Test
    @EnableFlags(StatusBarForDesktop.FLAG_NAME)
    fun showHeader_desktopStatusBarEnabled_statusBarForDesktopEnabled_false() =
        kosmos.runTest {
            setUseDesktopStatusBar(true)

            assertThat(underTest.showHeader).isFalse()
        }

    @Test
    @DisableFlags(StatusBarForDesktop.FLAG_NAME)
    fun showHeader_desktopStatusBarEnabled_statusBarForDesktopDisabled_true() =
        kosmos.runTest {
            setUseDesktopStatusBar(true)

            assertThat(underTest.showHeader).isTrue()
        }

    @Test
    @DisableFlags(StatusBarForDesktop.FLAG_NAME)
    fun alignmentOnWideScreens_statusBarForDesktopDisabled_start() =
        kosmos.runTest {
            setUseDesktopStatusBar(false)

            assertThat(underTest.alignmentOnWideScreens).isEqualTo(Alignment.Start)
        }

    @Test
    @EnableFlags(StatusBarForDesktop.FLAG_NAME)
    fun alignmentOnWideScreens_configDisabled_statusBarForDesktopEnabled_start() =
        kosmos.runTest {
            enableDualShade(wideLayout = true)
            overrideResource(R.bool.config_notificationShadeOnTopEnd, false)

            val underTest = createTestInstance().apply { activateIn(testScope) }
            assertThat(underTest.alignmentOnWideScreens).isEqualTo(Alignment.Start)
        }

    @Test
    @EnableFlags(StatusBarForDesktop.FLAG_NAME)
    fun alignmentOnWideScreens_configEnabled_statusBarForDesktopEnabled_end() =
        kosmos.runTest {
            enableDualShade(wideLayout = true)
            overrideResource(R.bool.config_notificationShadeOnTopEnd, true)
            notificationStackAppearanceInteractor.notificationStackHorizontalAlignment

            val underTest = createTestInstance().apply { activateIn(testScope) }
            assertThat(underTest.alignmentOnWideScreens).isEqualTo(Alignment.End)
        }

    @Test
    fun onScrimClicked_hidesShade() =
        kosmos.runTest {
            val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)
            sceneInteractor.showOverlay(Overlays.NotificationsShade, "test")
            assertThat(currentOverlays).contains(Overlays.NotificationsShade)

            underTest.onScrimClicked()

            assertThat(currentOverlays).doesNotContain(Overlays.NotificationsShade)
        }

    @Test
    fun deviceLocked_hidesShade() =
        kosmos.runTest {
            val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)
            unlockDevice()
            sceneInteractor.showOverlay(Overlays.NotificationsShade, "test")
            assertThat(currentOverlays).contains(Overlays.NotificationsShade)

            lockDevice()

            assertThat(currentOverlays).doesNotContain(Overlays.NotificationsShade)
        }

    @Test
    fun shadeNotTouchable_hidesShade() =
        kosmos.runTest {
            val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)
            val isShadeTouchable by collectLastValue(shadeInteractor.isShadeTouchable)
            assertThat(isShadeTouchable).isTrue()
            sceneInteractor.showOverlay(Overlays.NotificationsShade, "test")
            assertThat(currentOverlays).contains(Overlays.NotificationsShade)

            lockDevice()
            assertThat(isShadeTouchable).isFalse()
            assertThat(currentOverlays).doesNotContain(Overlays.NotificationsShade)
        }

    @Test
    fun showMedia_activeMedia_true() =
        kosmos.runTest {
            mediaPipelineRepository.addCurrentUserMediaEntry(MediaData(active = true))

            assertThat(underTest.showMedia).isTrue()
        }

    @Test
    fun showMedia_InactiveMedia_false() =
        kosmos.runTest {
            mediaPipelineRepository.addCurrentUserMediaEntry(MediaData(active = false))

            assertThat(underTest.showMedia).isFalse()
        }

    @Test
    fun showMedia_noMedia_false() =
        kosmos.runTest {
            mediaPipelineRepository.addCurrentUserMediaEntry(MediaData(active = true))
            mediaPipelineRepository.clearCurrentUserMedia()

            assertThat(underTest.showMedia).isFalse()
        }

    @Test
    fun showMedia_qsDisabled_false() =
        kosmos.runTest {
            mediaPipelineRepository.addCurrentUserMediaEntry(MediaData(active = true))
            fakeDisableFlagsRepository.disableFlags.update {
                it.copy(disable2 = DISABLE2_QUICK_SETTINGS)
            }

            assertThat(underTest.showMedia).isFalse()
        }

    @Test
    @DisableFlags(FLAG_NOTIFICATION_SHADE_BLUR)
    fun transparencyEnabled_shadeBlurFlagOff_isDisabled() =
        kosmos.runTest {
            fakeWindowRootViewBlurRepository.isBlurSupported.value = true

            assertThat(underTest.isTransparencyEnabled).isFalse()
        }

    @Test
    @EnableFlags(FLAG_NOTIFICATION_SHADE_BLUR)
    fun transparencyEnabled_shadeBlurFlagOn_blurSupported_isEnabled() =
        kosmos.runTest {
            fakeWindowRootViewBlurRepository.isBlurSupported.value = true

            assertThat(underTest.isTransparencyEnabled).isTrue()
        }

    @Test
    @EnableFlags(FLAG_NOTIFICATION_SHADE_BLUR)
    fun transparencyEnabled_shadeBlurFlagOn_blurUnsupported_isDisabled() =
        kosmos.runTest {
            fakeWindowRootViewBlurRepository.isBlurSupported.value = false

            assertThat(underTest.isTransparencyEnabled).isFalse()
        }

    @Test
    @EnableFlags(FLAG_NOTIFICATION_SHADE_BLUR)
    fun calculateTargetBlurRadius() =
        kosmos.runTest {
            // Only bouncer shown: no blur.
            fakeWindowRootViewBlurRepository.isBlurSupported.value = true
            assertThat(
                    underTest.calculateTargetBlurRadius(
                        transitionState =
                            TransitionState.Idle(
                                currentScene = Scenes.Lockscreen,
                                currentOverlays = setOf(Overlays.Bouncer),
                            )
                    )
                )
                .isEqualTo(0f)

            // Notifications shade and bouncer shown: apply blur.
            assertThat(
                    underTest.calculateTargetBlurRadius(
                        transitionState =
                            TransitionState.Idle(
                                currentScene = Scenes.Lockscreen,
                                currentOverlays =
                                    setOf(Overlays.Bouncer, Overlays.NotificationsShade),
                            )
                    )
                )
                .isEqualTo(blurConfig.maxBlurRadiusPx)

            // No bouncer shown: no blur.
            assertThat(
                    underTest.calculateTargetBlurRadius(
                        transitionState =
                            TransitionState.Idle(
                                currentScene = Scenes.Lockscreen,
                                currentOverlays = setOf(Overlays.NotificationsShade),
                            )
                    )
                )
                .isEqualTo(0)

            // Blur not supported: no blur.
            fakeWindowRootViewBlurRepository.isBlurSupported.value = false
            assertThat(
                    underTest.calculateTargetBlurRadius(
                        transitionState =
                            TransitionState.Idle(
                                currentScene = Scenes.Lockscreen,
                                currentOverlays =
                                    setOf(Overlays.Bouncer, Overlays.NotificationsShade),
                            )
                    )
                )
                .isEqualTo(0f)
        }

    @Test
    fun shadeModeChanged_single_switchesToShadeScene() =
        kosmos.runTest {
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)

            enableDualShade()
            shadeInteractor.expandNotificationsShade("test")
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(currentOverlays).contains(Overlays.NotificationsShade)

            enableSingleShade()
            assertThat(currentScene).isEqualTo(Scenes.Shade)
            assertThat(currentOverlays).doesNotContain(Overlays.NotificationsShade)
        }

    @Test
    fun shadeModeChanged_split_switchesToShadeScene() =
        kosmos.runTest {
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)

            enableDualShade()
            shadeInteractor.expandNotificationsShade("test")
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(currentOverlays).contains(Overlays.NotificationsShade)

            enableSplitShade()
            assertThat(currentScene).isEqualTo(Scenes.Shade)
            assertThat(currentOverlays).doesNotContain(Overlays.NotificationsShade)
        }

    @Test
    fun shadeModeChanged_betweenNonDualModes_remainsOnShadeScene() =
        kosmos.runTest {
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)

            // GIVEN the shade is an overlay in dual shade mode
            enableDualShade()
            shadeInteractor.expandNotificationsShade("test")
            assertThat(currentOverlays).contains(Overlays.NotificationsShade)

            // WHEN switching to a non-dual (single) shade mode
            enableSingleShade()

            // THEN the scene snaps to Shade
            assertThat(currentScene).isEqualTo(Scenes.Shade)
            assertThat(currentOverlays).doesNotContain(Overlays.NotificationsShade)

            // WHEN switching to another non-dual (split) shade mode
            enableSplitShade()

            // THEN the scene remains on Shade
            assertThat(currentScene).isEqualTo(Scenes.Shade)

            // WHEN switching back to the first non-dual (single) shade mode
            enableSingleShade()

            // THEN the scene still remains on Shade
            assertThat(currentScene).isEqualTo(Scenes.Shade)
        }

    private fun Kosmos.lockDevice() {
        val currentScene by collectLastValue(sceneInteractor.currentScene)
        powerInteractor.setAsleepForTest()

        assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
    }

    private suspend fun Kosmos.unlockDevice() {
        val currentScene by collectLastValue(sceneInteractor.currentScene)
        powerInteractor.setAwakeForTest()
        assertThat(authenticationInteractor.authenticate(FakeAuthenticationRepository.DEFAULT_PIN))
            .isEqualTo(AuthenticationResult.SUCCEEDED)

        assertThat(currentScene).isEqualTo(Scenes.Gone)
    }

    @Test
    fun onShadeBoundsChanged_forwardsToShadeOverlayInteractor() =
        kosmos.runTest {
            var shadeBounds: android.graphics.Rect? = null
            shadeInteractor.addShadeOverlayBoundsListener { shadeBounds = it }
            assertThat(shadeBounds).isNull()

            val bounds = Rect(0f, 0f, 100f, 100f)
            val expectedShadeBounds = android.graphics.Rect(0, 0, 100, 100)
            underTest.onShadeOverlayBoundsChanged(bounds)

            assertThat(shadeBounds).isEqualTo(expectedShadeBounds)
        }

    private fun Kosmos.setUseDesktopStatusBar(enable: Boolean) {
        enableDualShade(wideLayout = true)
        overrideResource(R.bool.config_useDesktopStatusBar, enable)
        configurationController.onConfigurationChanged(Configuration())
    }

    // TODO(441100057): Remove once DesktopInteractor.isNotificationShadeOnTopEnd supports runtime
    //  config updates.
    private fun Kosmos.createTestInstance(): NotificationsShadeOverlayContentViewModel {
        val desktopInteractor =
            DesktopInteractor(
                resources = mainResources,
                scope = backgroundScope,
                configurationController = configurationController,
            )
        return NotificationsShadeOverlayContentViewModel(
            mainDispatcher = testDispatcher,
            shadeHeaderViewModelFactory = shadeHeaderViewModelFactory,
            notificationsPlaceholderViewModelFactory = notificationsPlaceholderViewModelFactory,
            notificationStackAppearanceInteractor =
                NotificationStackAppearanceInteractor(
                    applicationScope = applicationCoroutineScope,
                    viewHeightRepository = notificationViewHeightRepository,
                    placeholderRepository = notificationPlaceholderRepository,
                    sceneInteractor = sceneInteractor,
                    shadeModeInteractor = shadeModeInteractor,
                    desktopInteractor = desktopInteractor,
                ),
            sceneInteractor = sceneInteractor,
            shadeInteractor = shadeInteractor,
            shadeModeInteractor = shadeModeInteractor,
            disableFlagsInteractor = disableFlagsInteractor,
            mediaCarouselInteractor = mediaCarouselInteractor,
            blurConfig = blurConfig,
            windowRootViewBlurInteractor = windowRootViewBlurInteractor,
            desktopInteractor = desktopInteractor,
            mediaViewModelFactory = mediaViewModelFactory,
        )
    }
}
