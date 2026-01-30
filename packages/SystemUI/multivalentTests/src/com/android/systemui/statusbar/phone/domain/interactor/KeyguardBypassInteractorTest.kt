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

package com.android.systemui.statusbar.phone.domain.interactor

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.bouncer.data.repository.keyguardBouncerRepository
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.keyguard.data.repository.configureKeyguardBypass
import com.android.systemui.keyguard.domain.interactor.KeyguardBypassInteractor
import com.android.systemui.keyguard.domain.interactor.keyguardBypassInteractor
import com.android.systemui.keyguard.domain.interactor.keyguardQuickAffordanceInteractor
import com.android.systemui.keyguard.domain.interactor.pulseExpansionInteractor
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.shade.domain.interactor.enableSingleShade
import com.android.systemui.shade.shadeTestUtil
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
@EnableSceneContainer
class KeyguardBypassInteractorTest : SysuiTestCase() {

    private val kosmos = testKosmos().useUnconfinedTestDispatcher()

    private val underTest: KeyguardBypassInteractor by lazy { kosmos.keyguardBypassInteractor }

    @Before
    fun setUp() {
        kosmos.configureKeyguardBypass(isBypassAvailable = true)
    }

    @Test
    fun canBypass_bypassNotAvailable_isFalse() =
        kosmos.runTest {
            configureKeyguardBypass(isBypassAvailable = false)
            val canBypass by collectLastValue(underTest.canBypass)

            sceneInteractor.changeScene(Scenes.Lockscreen, "reason")

            assertThat(canBypass).isFalse()
        }

    @Test
    fun canBypass_onPrimaryBouncerShowing_isTrue() =
        kosmos.runTest {
            val canBypass by collectLastValue(underTest.canBypass)

            sceneInteractor.changeScene(Scenes.Lockscreen, "reason")
            sceneInteractor.showOverlay(Overlays.Bouncer, "reason")

            assertThat(canBypass).isTrue()
        }

    @Test
    fun canBypass_onAlternateBouncerShowing_isTrue() =
        kosmos.runTest {
            val canBypass by collectLastValue(underTest.canBypass)

            sceneInteractor.changeScene(Scenes.Lockscreen, "reason")
            keyguardBouncerRepository.setAlternateVisible(true)

            assertThat(canBypass).isTrue()
        }

    @Test
    fun canBypass_notOnLockscreenScene_isFalse() =
        kosmos.runTest {
            enableSingleShade()
            val canBypass by collectLastValue(underTest.canBypass)
            val currentScene by collectLastValue(kosmos.sceneInteractor.currentScene)

            sceneInteractor.changeScene(Scenes.Shade, "reason")

            assertThat(currentScene).isNotEqualTo(Scenes.Lockscreen)
            assertThat(canBypass).isFalse()
        }

    @Test
    fun canBypass_onLaunchingAffordance_isFalse() =
        kosmos.runTest {
            val canBypass by collectLastValue(underTest.canBypass)

            sceneInteractor.changeScene(Scenes.Lockscreen, "reason")
            keyguardQuickAffordanceInteractor.setLaunchingAffordance(true)

            assertThat(canBypass).isFalse()
        }

    @Test
    fun canBypass_onPulseExpanding_isFalse() =
        kosmos.runTest {
            val canBypass by collectLastValue(underTest.canBypass)

            sceneInteractor.changeScene(Scenes.Lockscreen, "reason")
            pulseExpansionInteractor.setPulseExpanding(true)

            assertThat(canBypass).isFalse()
        }

    @Test
    fun canBypass_onQsExpanded_isFalse() =
        kosmos.runTest {
            enableSingleShade()
            val canBypass by collectLastValue(underTest.canBypass)

            sceneInteractor.changeScene(Scenes.QuickSettings, "reason")
            shadeTestUtil.setQsExpansion(1f)

            assertThat(canBypass).isFalse()
        }
}
