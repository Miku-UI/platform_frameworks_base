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

package com.android.systemui.qs.ui.viewmodel

import android.content.res.Configuration
import android.content.testableContext
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import androidx.compose.ui.geometry.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.content.state.TransitionState
import com.android.systemui.Flags.FLAG_NOTIFICATION_SHADE_BLUR
import com.android.systemui.SysuiTestCase
import com.android.systemui.authentication.data.repository.FakeAuthenticationRepository
import com.android.systemui.authentication.domain.interactor.AuthenticationResult
import com.android.systemui.authentication.domain.interactor.authenticationInteractor
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.keyguard.ui.transitions.blurConfig
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runCurrent
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.testScope
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.lifecycle.activateIn
import com.android.systemui.power.domain.interactor.PowerInteractor.Companion.setAsleepForTest
import com.android.systemui.power.domain.interactor.PowerInteractor.Companion.setAwakeForTest
import com.android.systemui.power.domain.interactor.powerInteractor
import com.android.systemui.qs.composefragment.dagger.usingMediaInComposeFragment
import com.android.systemui.qs.panels.data.repository.qsPanelAppearanceRepository
import com.android.systemui.res.R
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.domain.startable.sceneContainerStartable
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.shade.domain.interactor.enableDualShade
import com.android.systemui.shade.domain.interactor.enableSingleShade
import com.android.systemui.shade.domain.interactor.enableSplitShade
import com.android.systemui.shade.domain.interactor.shadeInteractor
import com.android.systemui.statusbar.core.StatusBarForDesktop
import com.android.systemui.statusbar.notification.stack.domain.interactor.notificationStackAppearanceInteractor
import com.android.systemui.statusbar.notification.stack.shared.model.ShadeScrimBounds
import com.android.systemui.statusbar.notification.stack.shared.model.ShadeScrimShape
import com.android.systemui.statusbar.policy.configurationController
import com.android.systemui.testKosmos
import com.android.systemui.window.data.repository.fakeWindowRootViewBlurRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.launch
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableSceneContainer
class QuickSettingsShadeOverlayContentViewModelTest : SysuiTestCase() {

    private val kosmos =
        testKosmos().useUnconfinedTestDispatcher().apply {
            usingMediaInComposeFragment = false // This is not for the compose fragment
        }

    private val Kosmos.underTest by
        Kosmos.Fixture { quickSettingsShadeOverlayContentViewModelFactory.create() }

    @Before
    fun setUp() =
        with(kosmos) {
            sceneContainerStartable.start()
            enableDualShade()
            runCurrent()
            underTest.activateIn(testScope)
        }

    @Test
    fun onScrimClicked_hidesShade() =
        kosmos.runTest {
            val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)
            sceneInteractor.showOverlay(Overlays.QuickSettingsShade, "test")
            assertThat(currentOverlays).contains(Overlays.QuickSettingsShade)

            underTest.onScrimClicked()

            assertThat(currentOverlays).doesNotContain(Overlays.QuickSettingsShade)
        }

    @Test
    fun deviceLocked_hidesShade() =
        kosmos.runTest {
            val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)
            unlockDevice()
            sceneInteractor.showOverlay(Overlays.QuickSettingsShade, "test")
            assertThat(currentOverlays).contains(Overlays.QuickSettingsShade)

            lockDevice()

            assertThat(currentOverlays).isEmpty()
        }

    @Test
    fun shadeNotTouchable_hidesShade() =
        kosmos.runTest {
            val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)
            val isShadeTouchable by collectLastValue(kosmos.shadeInteractor.isShadeTouchable)
            assertThat(isShadeTouchable).isTrue()
            sceneInteractor.showOverlay(Overlays.QuickSettingsShade, "test")
            assertThat(currentOverlays).contains(Overlays.QuickSettingsShade)

            lockDevice()
            assertThat(isShadeTouchable).isFalse()
            assertThat(currentOverlays).doesNotContain(Overlays.QuickSettingsShade)
        }

    @Test
    fun shadeModeChanged_single_switchesToQuickSettingsScene() =
        kosmos.runTest {
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)

            testScope.backgroundScope.launch { underTest.detectShadeModeChanges() }
            enableDualShade()
            shadeInteractor.expandQuickSettingsShade("test")
            runCurrent()
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(currentOverlays).contains(Overlays.QuickSettingsShade)

            enableSingleShade()
            runCurrent()
            assertThat(currentScene).isEqualTo(Scenes.QuickSettings)
            assertThat(currentOverlays).doesNotContain(Overlays.QuickSettingsShade)
        }

    @Test
    fun shadeModeChanged_split_switchesToShadeScene() =
        kosmos.runTest {
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)

            testScope.backgroundScope.launch { underTest.detectShadeModeChanges() }
            enableDualShade()
            shadeInteractor.expandQuickSettingsShade("test")
            runCurrent()
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(currentOverlays).contains(Overlays.QuickSettingsShade)

            enableSplitShade()
            runCurrent()
            assertThat(currentScene).isEqualTo(Scenes.Shade)
            assertThat(currentOverlays).doesNotContain(Overlays.QuickSettingsShade)
        }

    @Test
    fun onPanelShapeChanged() =
        kosmos.runTest {
            var actual: ShadeScrimShape? = null
            val disposable =
                notificationStackAppearanceInteractor.qsPanelShapeInWindow.observe { shape ->
                    actual = shape
                }

            val expected =
                ShadeScrimShape(
                    bounds = ShadeScrimBounds(left = 10f, top = 0f, right = 710f, bottom = 600f),
                    topRadius = 0,
                    bottomRadius = 100,
                )

            underTest.onPanelShapeInWindowChanged(expected)

            assertThat(actual).isEqualTo(expected)
            assertThat(kosmos.qsPanelAppearanceRepository.qsPanelShape.value).isEqualTo(expected)

            disposable.dispose()
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

    private fun Kosmos.setUseDesktopStatusBar(enable: Boolean) {
        testableContext.orCreateTestableResources.addOverride(
            R.bool.config_useDesktopStatusBar,
            enable,
        )
        configurationController.onConfigurationChanged(Configuration())
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

            // Quick Settings shade and bouncer shown: apply blur.
            assertThat(
                    underTest.calculateTargetBlurRadius(
                        transitionState =
                            TransitionState.Idle(
                                currentScene = Scenes.Lockscreen,
                                currentOverlays =
                                    setOf(Overlays.Bouncer, Overlays.QuickSettingsShade),
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
                                currentOverlays = setOf(Overlays.QuickSettingsShade),
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
                                    setOf(Overlays.Bouncer, Overlays.QuickSettingsShade),
                            )
                    )
                )
                .isEqualTo(0f)
        }

    private fun Kosmos.lockDevice() {
        val currentScene by collectLastValue(sceneInteractor.currentScene)
        powerInteractor.setAsleepForTest()
        runCurrent()

        assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
    }

    private suspend fun Kosmos.unlockDevice() {
        val currentScene by collectLastValue(sceneInteractor.currentScene)
        powerInteractor.setAwakeForTest()
        runCurrent()
        assertThat(authenticationInteractor.authenticate(FakeAuthenticationRepository.DEFAULT_PIN))
            .isEqualTo(AuthenticationResult.SUCCEEDED)

        assertThat(currentScene).isEqualTo(Scenes.Gone)
    }
}
