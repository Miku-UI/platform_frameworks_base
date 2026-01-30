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

package com.android.systemui.shade

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.ObservableTransitionState.Idle
import com.android.compose.animation.scene.OverlayKey
import com.android.systemui.SysuiTestCase
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.kosmos.Kosmos
import com.android.systemui.kosmos.collectLastValue
import com.android.systemui.kosmos.runTest
import com.android.systemui.kosmos.useUnconfinedTestDispatcher
import com.android.systemui.scene.data.repository.setSceneTransition
import com.android.systemui.scene.domain.interactor.sceneInteractor
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.shade.domain.interactor.enableDualShade
import com.android.systemui.statusbar.commandline.commandRegistry
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertThat
import java.io.PrintWriter
import java.io.StringWriter
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@EnableSceneContainer
@RunWith(AndroidJUnit4::class)
class ShadeInstantExpansionCommandsTest : SysuiTestCase() {
    private val kosmos = testKosmos().useUnconfinedTestDispatcher()

    @Test
    fun commandShadeShowNotifications_singleShade() =
        kosmos.runTest {
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            shadeInstantExpansionCommands.start()
            commandRegistry.onShellCommand(
                PrintWriter(StringWriter()),
                arrayOf("expand-notifications-instant"),
            )
            assertThat(currentScene).isEqualTo(Scenes.Shade)
        }

    @Test
    fun commandShadeShowNotifications_dualShade() =
        kosmos.runTest {
            enableDualShade()
            val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)
            shadeInstantExpansionCommands.start()
            commandRegistry.onShellCommand(
                PrintWriter(StringWriter()),
                arrayOf("expand-notifications-instant"),
            )
            assertThat(currentOverlays).containsExactly(Overlays.NotificationsShade)
        }

    @Test
    fun commandShadeShowQs_singleShade() =
        kosmos.runTest {
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            shadeInstantExpansionCommands.start()
            commandRegistry.onShellCommand(
                PrintWriter(StringWriter()),
                arrayOf("expand-settings-instant"),
            )
            assertThat(currentScene).isEqualTo(Scenes.QuickSettings)
        }

    @Test
    fun commandShadeShowQs_dualShade() =
        kosmos.runTest {
            enableDualShade()
            val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)
            shadeInstantExpansionCommands.start()
            commandRegistry.onShellCommand(
                PrintWriter(StringWriter()),
                arrayOf("expand-settings-instant"),
            )
            assertThat(currentOverlays).containsExactly(Overlays.QuickSettingsShade)
        }

    @Test
    fun commandShadeHideNotifications_singleShade() =
        kosmos.runTest {
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            shadeInstantExpansionCommands.start()
            sceneInteractor.changeScene(Scenes.Shade, "test")
            setSceneTransition(Idle(Scenes.Shade))
            commandRegistry.onShellCommand(PrintWriter(StringWriter()), arrayOf("collapse-instant"))
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
        }

    @Test
    fun commandShadeHideNotifications_dualShade() =
        kosmos.runTest {
            enableDualShade()
            val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)
            shadeInstantExpansionCommands.start()
            setOverlay(Overlays.NotificationsShade)
            commandRegistry.onShellCommand(PrintWriter(StringWriter()), arrayOf("collapse-instant"))
            assertThat(currentOverlays).isEmpty()
        }

    @Test
    fun commandShadeHideQs_singleShade() =
        kosmos.runTest {
            val currentScene by collectLastValue(sceneInteractor.currentScene)
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
            shadeInstantExpansionCommands.start()
            sceneInteractor.changeScene(Scenes.QuickSettings, "test")
            setSceneTransition(Idle(Scenes.QuickSettings))
            commandRegistry.onShellCommand(PrintWriter(StringWriter()), arrayOf("collapse-instant"))
            assertThat(currentScene).isEqualTo(Scenes.Lockscreen)
        }

    @Test
    fun commandShadeHideQs_dualShade() =
        kosmos.runTest {
            enableDualShade()
            val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)
            setOverlay(Overlays.QuickSettingsShade)
            shadeInstantExpansionCommands.start()
            commandRegistry.onShellCommand(PrintWriter(StringWriter()), arrayOf("collapse-instant"))
            assertThat(currentOverlays).isEmpty()
        }

    private fun Kosmos.setOverlay(overlay: OverlayKey) {
        val currentScene by collectLastValue(sceneInteractor.currentScene)
        val currentOverlays by collectLastValue(sceneInteractor.currentOverlays)
        sceneInteractor.showOverlay(overlay, "test")
        setSceneTransition(Idle(checkNotNull(currentScene), checkNotNull(currentOverlays)))
    }
}
