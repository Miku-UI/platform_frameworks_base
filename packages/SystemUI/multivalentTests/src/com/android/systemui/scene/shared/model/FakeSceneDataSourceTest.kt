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

package com.android.systemui.scene.shared.model

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.OverlayKey
import com.android.compose.animation.scene.SceneKey
import com.android.systemui.SysuiTestCase
import com.android.systemui.kosmos.runTest
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class FakeSceneDataSourceTest : SysuiTestCase() {

    private val kosmos = testKosmos()
    private val underTest = kosmos.fakeSceneDataSource

    private val currentScene: SceneKey
        get() = underTest.transitionState.currentScene

    private val currentOverlays: Set<OverlayKey>
        get() = underTest.transitionState.currentOverlays

    @Test
    fun unpaused() =
        kosmos.runTest {
            assertThat(underTest.isPaused).isFalse()
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(currentOverlays).isEmpty()

            underTest.showOverlay(Overlays.Bouncer)
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(currentOverlays).isEqualTo(setOf(Overlays.Bouncer))

            underTest.changeScene(Scenes.Gone)
            assertThat(currentScene).isEqualTo(Scenes.Gone)
            assertThat(currentOverlays).isEqualTo(setOf(Overlays.Bouncer))

            underTest.hideOverlay(Overlays.Bouncer)
            assertThat(currentScene).isEqualTo(Scenes.Gone)
            assertThat(currentOverlays).isEmpty()

            underTest.changeScene(Scenes.Shade)
            assertThat(currentScene).isEqualTo(Scenes.Shade)
            assertThat(currentOverlays).isEmpty()

            underTest.instantlyTransitionTo(Scenes.QuickSettings)
            assertThat(currentScene).isEqualTo(Scenes.QuickSettings)
            assertThat(currentOverlays).isEmpty()

            underTest.changeScene(Scenes.Lockscreen)
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(currentOverlays).isEmpty()

            underTest.showOverlay(Overlays.NotificationsShade)
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(currentOverlays).isEqualTo(setOf(Overlays.NotificationsShade))

            underTest.replaceOverlay(
                from = Overlays.NotificationsShade,
                to = Overlays.QuickSettingsShade,
            )
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(currentOverlays).isEqualTo(setOf(Overlays.QuickSettingsShade))
        }

    @Test
    fun withPauses() =
        kosmos.runTest {
            assertThat(underTest.isPaused).isFalse()
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(currentOverlays).isEmpty()

            underTest.pause()
            assertThat(underTest.isPaused).isTrue()
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(currentOverlays).isEmpty()

            underTest.showOverlay(Overlays.Bouncer)
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(currentOverlays).isEmpty()

            underTest.unpause(
                expectedScene = Scenes.Lockscreen,
                expectedOverlays = setOf(Overlays.Bouncer),
            )
            assertThat(underTest.isPaused).isFalse()

            underTest.pause()
            assertThat(underTest.isPaused).isTrue()
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(currentOverlays).isEqualTo(setOf(Overlays.Bouncer))

            underTest.changeScene(Scenes.Gone)
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(currentOverlays).isEqualTo(setOf(Overlays.Bouncer))

            underTest.hideOverlay(Overlays.Bouncer)
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(currentOverlays).isEqualTo(setOf(Overlays.Bouncer))

            underTest.unpause(expectedScene = Scenes.Gone, expectedOverlays = emptySet())
            assertThat(underTest.isPaused).isFalse()

            underTest.changeScene(Scenes.Shade)
            assertThat(currentScene).isEqualTo(Scenes.Shade)
            assertThat(currentOverlays).isEmpty()

            underTest.instantlyTransitionTo(Scenes.QuickSettings)
            assertThat(currentScene).isEqualTo(Scenes.QuickSettings)
            assertThat(currentOverlays).isEmpty()

            underTest.changeScene(Scenes.Lockscreen)
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(currentOverlays).isEmpty()

            underTest.pause()
            assertThat(underTest.isPaused).isTrue()
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(currentOverlays).isEmpty()

            underTest.showOverlay(Overlays.NotificationsShade)
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(currentOverlays).isEmpty()

            underTest.replaceOverlay(
                from = Overlays.NotificationsShade,
                to = Overlays.QuickSettingsShade,
            )
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            assertThat(currentOverlays).isEmpty()

            underTest.unpause(
                expectedScene = Scenes.Lockscreen,
                expectedOverlays = setOf(Overlays.QuickSettingsShade),
            )
            assertThat(underTest.isPaused).isFalse()
        }
}
