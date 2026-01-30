/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.phone

import android.content.res.Configuration
import android.content.testableContext
import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.ObservableTransitionState.Idle
import com.android.compose.animation.scene.OverlayKey
import com.android.internal.policy.SystemBarUtils
import com.android.systemui.SysuiTestCase
import com.android.systemui.communal.data.repository.fakeCommunalSceneRepository
import com.android.systemui.communal.shared.model.CommunalScenes
import com.android.systemui.flags.DisableSceneContainer
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.keyguard.data.repository.fakeDeviceEntryFingerprintAuthRepository
import com.android.systemui.keyguard.shared.model.SuccessFingerprintAuthenticationStatus
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.res.R
import com.android.systemui.scene.data.repository.sceneContainerRepository
import com.android.systemui.scene.data.repository.setSceneTransition
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.shade.domain.interactor.enableDualShade
import com.android.systemui.shade.domain.interactor.shadeInteractor
import com.android.systemui.shade.domain.interactor.shadeMode
import com.android.systemui.shade.notificationShadeWindowView
import com.android.systemui.shade.shared.model.ShadeMode
import com.android.systemui.statusbar.policy.configurationController
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.flowOf
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.whenever

@SmallTest
@RunWith(AndroidJUnit4::class)
class ShadeTouchableRegionManagerTest : SysuiTestCase() {
    private val kosmos = testKosmos().useUnconfinedTestDispatcher()
    private val Kosmos.underTest by Kosmos.Fixture { kosmos.shadeTouchableRegionManager }

    @Before
    fun setUp() {
        kosmos.notificationShadeWindowView.apply {
            whenever(width).thenReturn(1000)
            whenever(height).thenReturn(1000)
        }
        kosmos.underTest.setup(kosmos.notificationShadeWindowView)
    }

    @Test
    @EnableSceneContainer
    fun entireScreenTouchable_sceneContainerEnabled_isRemoteUserInteractionOngoing() =
        kosmos.runTest {
            sceneContainerRepository.setTransitionState(flowOf(Idle(currentScene = Scenes.Gone)))
            assertThat(underTest.shouldMakeEntireScreenTouchable()).isFalse()

            sceneContainerRepository.isRemoteUserInputOngoing.value = true
            assertThat(underTest.shouldMakeEntireScreenTouchable()).isTrue()

            sceneContainerRepository.isRemoteUserInputOngoing.value = false
            assertThat(underTest.shouldMakeEntireScreenTouchable()).isFalse()
        }

    @Test
    @DisableSceneContainer
    fun entireScreenTouchable_sceneContainerDisabled_isRemoteUserInteractionOngoing() =
        kosmos.runTest {
            assertThat(underTest.shouldMakeEntireScreenTouchable()).isFalse()

            sceneContainerRepository.isRemoteUserInputOngoing.value = true

            assertThat(underTest.shouldMakeEntireScreenTouchable()).isFalse()
        }

    @Test
    @EnableSceneContainer
    fun entireScreenTouchable_sceneContainerEnabled_isIdleOnGone() =
        kosmos.runTest {
            sceneContainerRepository.setTransitionState(flowOf(Idle(currentScene = Scenes.Gone)))
            assertThat(underTest.shouldMakeEntireScreenTouchable()).isFalse()

            sceneContainerRepository.setTransitionState(flowOf(Idle(currentScene = Scenes.Shade)))
            assertThat(underTest.shouldMakeEntireScreenTouchable()).isTrue()

            sceneContainerRepository.setTransitionState(flowOf(Idle(currentScene = Scenes.Gone)))
            assertThat(underTest.shouldMakeEntireScreenTouchable()).isFalse()
        }

    @Test
    @DisableSceneContainer
    fun entireScreenTouchable_sceneContainerDisabled_isIdleOnGone() =
        kosmos.runTest {
            assertThat(underTest.shouldMakeEntireScreenTouchable()).isFalse()

            sceneContainerRepository.setTransitionState(flowOf(Idle(currentScene = Scenes.Shade)))

            assertThat(underTest.shouldMakeEntireScreenTouchable()).isFalse()
        }

    @Test
    @DisableSceneContainer
    fun entireScreenTouchable_communalVisible() =
        kosmos.runTest {
            assertThat(underTest.shouldMakeEntireScreenTouchable()).isFalse()

            kosmos.fakeCommunalSceneRepository.instantlyTransitionTo(CommunalScenes.Communal)

            assertThat(underTest.shouldMakeEntireScreenTouchable()).isTrue()

            kosmos.fakeCommunalSceneRepository.instantlyTransitionTo(CommunalScenes.Blank)

            assertThat(underTest.shouldMakeEntireScreenTouchable()).isFalse()
        }

    @Test
    @EnableSceneContainer
    fun entireScreenTouchable_desktopMode() =
        kosmos.runTest {
            enableStatusBarForDesktop()
            openShadeOverlay(Overlays.QuickSettingsShade)

            assertThat(underTest.shouldMakeEntireScreenTouchable()).isFalse()
        }

    @Test
    @EnableSceneContainer
    fun calculateTouchableRegionForDesktop_sceneGone_withShadeBounds() =
        kosmos.runTest {
            val bounds = Rect(0, 0, 100, 100)
            enableStatusBarForDesktop()
            lockDevice()
            unlockDevice()
            openShadeOverlay(Overlays.QuickSettingsShade)
            shadeInteractor.setShadeOverlayBounds(bounds)

            val rects = underTest.calculateTouchableRegionForDesktop()

            assertThat(rects).containsExactly(bounds)
        }

    @Test
    @EnableSceneContainer
    fun calculateTouchableRegionForDesktop_sceneVisible_withoutShadeBounds() =
        kosmos.runTest {
            enableStatusBarForDesktop()
            lockDevice()
            shadeInteractor.setShadeOverlayBounds(null)
            val statusBarHeight = SystemBarUtils.getStatusBarHeight(mContext)
            val expectedRect = Rect(0, statusBarHeight, 1000, 1000)

            val rects = underTest.calculateTouchableRegionForDesktop()

            assertThat(rects).containsExactly(expectedRect)
        }

    private fun Kosmos.openShadeOverlay(overlay: OverlayKey) {
        val shadeMode by collectLastValue(shadeMode)
        val currentScene by collectLastValue(sceneInteractor.currentScene)
        val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)
        val initialScene = checkNotNull(currentScene)
        assertThat(shadeMode).isEqualTo(ShadeMode.Dual)

        sceneInteractor.showOverlay(overlay, "test")
        setSceneTransition(Idle(initialScene, checkNotNull(currentOverlays)))
        assertThat(currentScene).isEqualTo(initialScene)
        assertThat(currentOverlays).contains(overlay)
    }

    private fun Kosmos.closeShadeOverlay(overlay: OverlayKey) {
        sceneInteractor.hideOverlay(overlay, "test")
    }

    private fun Kosmos.enableStatusBarForDesktop() {
        enableDualShade()
        testableContext.orCreateTestableResources.addOverride(
            R.bool.config_useDesktopStatusBar,
            true,
        )
        configurationController.onConfigurationChanged(Configuration())
    }

    private fun Kosmos.unlockDevice() {
        fakeDeviceEntryFingerprintAuthRepository.setAuthenticationStatus(
            SuccessFingerprintAuthenticationStatus(0, true)
        )
        sceneInteractor.changeScene(Scenes.Gone, "unlock")
        sceneContainerRepository.setTransitionState(flowOf(Idle(Scenes.Gone)))
    }

    private fun Kosmos.lockDevice() {
        sceneContainerRepository.instantlyTransitionTo(Scenes.Lockscreen)
    }
}
