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

package com.android.systemui.model

import androidx.test.filters.SmallTest
import com.android.compose.animation.scene.OverlayKey
import com.android.compose.animation.scene.SceneKey
import com.android.systemui.SysuiTestCase
import com.android.systemui.flags.EnableSceneContainer
import com.android.systemui.kosmos.runTest
import com.android.systemui.scene.shared.model.Overlays
import com.android.systemui.scene.shared.model.Scenes
import com.android.systemui.shade.shared.model.ShadeMode
import com.android.systemui.shared.system.QuickStepContract
import com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_BOUNCER_SHOWING
import com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_COMMUNAL_HUB_SHOWING
import com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_NOTIFICATION_PANEL_EXPANDED
import com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_NOTIFICATION_PANEL_VISIBLE
import com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_QUICK_SETTINGS_EXPANDED
import com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING
import com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING_OCCLUDED
import com.android.systemui.testKosmos
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.junit.runner.RunWith
import platform.test.runner.parameterized.ParameterizedAndroidJunit4
import platform.test.runner.parameterized.Parameters

@SmallTest
@RunWith(ParameterizedAndroidJunit4::class)
@EnableSceneContainer
class SceneContainerPluginFlagEvaluationTest(private val params: Params) : SysuiTestCase() {

    private val kosmos = testKosmos()

    companion object {
        private val ALL_FLAGS =
            setOf(
                SYSUI_STATE_NOTIFICATION_PANEL_VISIBLE,
                SYSUI_STATE_NOTIFICATION_PANEL_EXPANDED,
                SYSUI_STATE_QUICK_SETTINGS_EXPANDED,
                SYSUI_STATE_BOUNCER_SHOWING,
                SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING,
                SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING_OCCLUDED,
                SYSUI_STATE_COMMUNAL_HUB_SHOWING,
            )

        @Parameters(name = "{0}")
        @JvmStatic
        fun parameters(): List<Params> {
            return listOf(
                Params(
                    currentScene = Scenes.Lockscreen,
                    currentOverlays = setOf(),
                    expectedFlags =
                        setOf(
                            SYSUI_STATE_NOTIFICATION_PANEL_VISIBLE,
                            SYSUI_STATE_NOTIFICATION_PANEL_EXPANDED,
                            SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING,
                        ),
                ),
                Params(
                    currentScene = Scenes.Lockscreen,
                    currentOverlays = setOf(Overlays.Bouncer),
                    expectedFlags =
                        setOf(SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING, SYSUI_STATE_BOUNCER_SHOWING),
                ),
                Params(
                    currentScene = Scenes.Gone,
                    currentOverlays = setOf(),
                    expectedFlags = setOf(),
                ),
                Params(
                    currentScene = Scenes.Shade,
                    sceneBehind = Scenes.Gone,
                    currentOverlays = setOf(),
                    expectedFlags =
                        setOf(
                            SYSUI_STATE_NOTIFICATION_PANEL_VISIBLE,
                            SYSUI_STATE_NOTIFICATION_PANEL_EXPANDED,
                        ),
                ),
                Params(
                    currentScene = Scenes.Shade,
                    sceneBehind = Scenes.Gone,
                    currentOverlays = setOf(),
                    shadeMode = ShadeMode.Split,
                    expectedFlags =
                        setOf(
                            SYSUI_STATE_NOTIFICATION_PANEL_VISIBLE,
                            SYSUI_STATE_QUICK_SETTINGS_EXPANDED,
                        ),
                ),
                Params(
                    currentScene = Scenes.QuickSettings,
                    sceneBehind = Scenes.Gone,
                    currentOverlays = setOf(),
                    expectedFlags =
                        setOf(
                            SYSUI_STATE_NOTIFICATION_PANEL_VISIBLE,
                            SYSUI_STATE_QUICK_SETTINGS_EXPANDED,
                        ),
                ),
                Params(
                    currentScene = Scenes.Shade,
                    sceneBehind = Scenes.Lockscreen,
                    currentOverlays = setOf(),
                    expectedFlags =
                        setOf(
                            SYSUI_STATE_NOTIFICATION_PANEL_VISIBLE,
                            SYSUI_STATE_NOTIFICATION_PANEL_EXPANDED,
                            SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING,
                        ),
                ),
                Params(
                    currentScene = Scenes.Shade,
                    sceneBehind = Scenes.Lockscreen,
                    currentOverlays = setOf(Overlays.Bouncer),
                    expectedFlags =
                        setOf(
                            SYSUI_STATE_NOTIFICATION_PANEL_VISIBLE,
                            SYSUI_STATE_NOTIFICATION_PANEL_EXPANDED,
                            SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING,
                            SYSUI_STATE_BOUNCER_SHOWING,
                        ),
                ),
                Params(
                    currentScene = Scenes.QuickSettings,
                    sceneBehind = Scenes.Lockscreen,
                    currentOverlays = setOf(),
                    expectedFlags =
                        setOf(
                            SYSUI_STATE_NOTIFICATION_PANEL_VISIBLE,
                            SYSUI_STATE_QUICK_SETTINGS_EXPANDED,
                            SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING,
                        ),
                ),
                Params(
                    currentScene = Scenes.QuickSettings,
                    sceneBehind = Scenes.Lockscreen,
                    currentOverlays = setOf(Overlays.Bouncer),
                    expectedFlags =
                        setOf(
                            SYSUI_STATE_NOTIFICATION_PANEL_VISIBLE,
                            SYSUI_STATE_QUICK_SETTINGS_EXPANDED,
                            SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING,
                            SYSUI_STATE_BOUNCER_SHOWING,
                        ),
                ),
                Params(
                    currentScene = Scenes.Gone,
                    currentOverlays = setOf(Overlays.NotificationsShade),
                    expectedFlags =
                        setOf(
                            SYSUI_STATE_NOTIFICATION_PANEL_VISIBLE,
                            SYSUI_STATE_NOTIFICATION_PANEL_EXPANDED,
                        ),
                ),
                Params(
                    currentScene = Scenes.Gone,
                    currentOverlays = setOf(Overlays.QuickSettingsShade),
                    expectedFlags =
                        setOf(
                            SYSUI_STATE_NOTIFICATION_PANEL_VISIBLE,
                            SYSUI_STATE_QUICK_SETTINGS_EXPANDED,
                        ),
                ),
                Params(
                    currentScene = Scenes.Lockscreen,
                    currentOverlays = setOf(Overlays.NotificationsShade),
                    expectedFlags =
                        setOf(
                            SYSUI_STATE_NOTIFICATION_PANEL_VISIBLE,
                            SYSUI_STATE_NOTIFICATION_PANEL_EXPANDED,
                            SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING,
                        ),
                ),
                Params(
                    currentScene = Scenes.Lockscreen,
                    currentOverlays = setOf(Overlays.NotificationsShade, Overlays.Bouncer),
                    expectedFlags =
                        setOf(
                            SYSUI_STATE_NOTIFICATION_PANEL_VISIBLE,
                            SYSUI_STATE_NOTIFICATION_PANEL_EXPANDED,
                            SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING,
                            SYSUI_STATE_BOUNCER_SHOWING,
                        ),
                ),
                Params(
                    currentScene = Scenes.Lockscreen,
                    currentOverlays = setOf(Overlays.QuickSettingsShade),
                    expectedFlags =
                        setOf(
                            SYSUI_STATE_NOTIFICATION_PANEL_VISIBLE,
                            SYSUI_STATE_QUICK_SETTINGS_EXPANDED,
                            SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING,
                        ),
                ),
                Params(
                    currentScene = Scenes.Lockscreen,
                    currentOverlays = setOf(Overlays.QuickSettingsShade, Overlays.Bouncer),
                    expectedFlags =
                        setOf(
                            SYSUI_STATE_NOTIFICATION_PANEL_VISIBLE,
                            SYSUI_STATE_QUICK_SETTINGS_EXPANDED,
                            SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING,
                            SYSUI_STATE_BOUNCER_SHOWING,
                        ),
                ),
                Params(
                    currentScene = Scenes.Occluded,
                    currentOverlays = setOf(),
                    expectedFlags = setOf(SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING_OCCLUDED),
                ),
                Params(
                    currentScene = Scenes.Occluded,
                    currentOverlays = setOf(Overlays.NotificationsShade),
                    expectedFlags =
                        setOf(
                            SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING_OCCLUDED,
                            SYSUI_STATE_NOTIFICATION_PANEL_VISIBLE,
                            SYSUI_STATE_NOTIFICATION_PANEL_EXPANDED,
                        ),
                ),
                Params(
                    currentScene = Scenes.Shade,
                    sceneBehind = Scenes.Occluded,
                    currentOverlays = setOf(),
                    expectedFlags =
                        setOf(
                            SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING_OCCLUDED,
                            SYSUI_STATE_NOTIFICATION_PANEL_VISIBLE,
                            SYSUI_STATE_NOTIFICATION_PANEL_EXPANDED,
                        ),
                ),
                Params(
                    currentScene = Scenes.QuickSettings,
                    sceneBehind = Scenes.Occluded,
                    currentOverlays = setOf(),
                    expectedFlags =
                        setOf(
                            SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING_OCCLUDED,
                            SYSUI_STATE_NOTIFICATION_PANEL_VISIBLE,
                            SYSUI_STATE_QUICK_SETTINGS_EXPANDED,
                        ),
                ),
                Params(
                    currentScene = Scenes.Occluded,
                    currentOverlays = setOf(Overlays.QuickSettingsShade),
                    expectedFlags =
                        setOf(
                            SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING_OCCLUDED,
                            SYSUI_STATE_NOTIFICATION_PANEL_VISIBLE,
                            SYSUI_STATE_QUICK_SETTINGS_EXPANDED,
                        ),
                ),
                Params(
                    currentScene = Scenes.Dream,
                    currentOverlays = setOf(),
                    expectedFlags = setOf(SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING_OCCLUDED),
                ),
                Params(
                    currentScene = Scenes.Dream,
                    currentOverlays = setOf(Overlays.NotificationsShade),
                    expectedFlags =
                        setOf(
                            SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING_OCCLUDED,
                            SYSUI_STATE_NOTIFICATION_PANEL_VISIBLE,
                            SYSUI_STATE_NOTIFICATION_PANEL_EXPANDED,
                        ),
                ),
                Params(
                    currentScene = Scenes.Shade,
                    sceneBehind = Scenes.Dream,
                    currentOverlays = setOf(),
                    expectedFlags =
                        setOf(
                            SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING_OCCLUDED,
                            SYSUI_STATE_NOTIFICATION_PANEL_VISIBLE,
                            SYSUI_STATE_NOTIFICATION_PANEL_EXPANDED,
                        ),
                ),
                Params(
                    currentScene = Scenes.QuickSettings,
                    sceneBehind = Scenes.Dream,
                    currentOverlays = setOf(),
                    expectedFlags =
                        setOf(
                            SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING_OCCLUDED,
                            SYSUI_STATE_NOTIFICATION_PANEL_VISIBLE,
                            SYSUI_STATE_QUICK_SETTINGS_EXPANDED,
                        ),
                ),
                Params(
                    currentScene = Scenes.Dream,
                    currentOverlays = setOf(Overlays.QuickSettingsShade),
                    expectedFlags =
                        setOf(
                            SYSUI_STATE_STATUS_BAR_KEYGUARD_SHOWING_OCCLUDED,
                            SYSUI_STATE_NOTIFICATION_PANEL_VISIBLE,
                            SYSUI_STATE_QUICK_SETTINGS_EXPANDED,
                        ),
                ),
            )
        }
    }

    @Test
    fun flags() =
        kosmos.runTest {
            val missingFlags = mutableSetOf<Long>()
            val extraneousFlags = mutableSetOf<Long>()

            ALL_FLAGS.forEach { flag ->
                val expected = flag in params.expectedFlags
                val actual =
                    SceneContainerPluginImpl.EvaluatorByFlag[flag]!!.invoke(
                        SceneContainerPlugin.SceneContainerPluginState(
                            scene = params.currentScene,
                            sceneBehind = params.sceneBehind,
                            overlays = params.currentOverlays,
                            isVisible = true,
                            shadeMode = params.shadeMode,
                        )
                    )
                if (expected && !actual) {
                    missingFlags.add(flag)
                } else if (!expected && actual) {
                    extraneousFlags.add(flag)
                }
            }

            assertWithMessage(
                    buildString {
                        appendLine("Incorrect flag evaluation(s)")
                        appendLine("    Test params: $params")

                        if (missingFlags.isNotEmpty()) {
                            appendLine("    Missing flags: ${missingFlags.mapToString()}")
                        }
                        if (extraneousFlags.isNotEmpty()) {
                            appendLine("    Extraneous flags: ${extraneousFlags.mapToString()}")
                        }
                    }
                )
                .that(missingFlags.isEmpty() && extraneousFlags.isEmpty())
                .isTrue()
        }

    private fun Set<Long>.mapToString(): String {
        if (isEmpty()) {
            return "none"
        }

        return joinToString { QuickStepContract.getSystemUiStateString(it) }
    }

    data class Params(
        val currentScene: SceneKey,
        val sceneBehind: SceneKey? = null,
        val currentOverlays: Set<OverlayKey>,
        val shadeMode: ShadeMode = ShadeMode.Single,
        val expectedFlags: Set<Long>,
    ) {
        override fun toString(): String {
            return "currentScene=${currentScene.debugName}${sceneBehind?.let { ", sceneBehind=${it.debugName}" } ?: ""}, currentOverlays=[${currentOverlays.joinToString { it.debugName }}, shadeMode=$shadeMode]"
        }
    }
}
