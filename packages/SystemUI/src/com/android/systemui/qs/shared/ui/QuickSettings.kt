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

package com.android.systemui.qs.shared.ui

import com.android.compose.animation.scene.ContentKey
import com.android.compose.animation.scene.ElementContentPicker
import com.android.compose.animation.scene.ElementKey
import com.android.compose.animation.scene.HighestZIndexContentPicker
import com.android.compose.animation.scene.ValueKey
import com.android.compose.animation.scene.content.state.TransitionState
import com.android.systemui.qs.pipeline.shared.TileSpec
import com.android.systemui.scene.shared.model.Scenes.QuickSettings
import com.android.systemui.scene.shared.model.Scenes.Shade

object QuickSettings {
    /** Element keys to be used by the compose implementation of QS for animations. */
    object Elements {
        val QuickSettingsContent = ElementKey("QuickSettingsContent")
        val QuickSettingsTiles = ElementKey("QuickSettingsTiles")
        val GridAnchor = ElementKey("QuickSettingsGridAnchor")
        val FooterActions = ElementKey("QuickSettingsFooterActions")
        val BrightnessSlider = ElementKey("BrightnessSlider")

        fun TileSpec.toElementKey() =
            ElementKey(this.spec, TileIdentity(this), contentPicker = SharedQsTileContentPicker)

        val TileElementMatcher = ElementKey.withIdentity { it is TileIdentity }

        val QuickQuickSettingsAndMedia = ElementKey("QuickQuickSettingsAndMedia")
        val SplitShadeQuickSettings = ElementKey("SplitShadeQuickSettings")
    }

    object SharedValues {
        val TilesSquishiness = ValueKey("QuickSettingsTileSquishiness")

        object SquishinessValues {
            val Default = 1f
            val LockscreenSceneStarting = 0.3f
            val GoneSceneStarting = 0.3f
            val OccludedSceneStarting = 0.3f
        }
    }

    /**
     * When we come close to Qs, we want the shared tiles to be placed by the Qs scene such that
     * gestures work and they are in sync with non-shared tiles.
     */
    const val SHARED_TILE_PICKER_THRESHOLD = 0.05f

    private object SharedQsTileContentPicker : ElementContentPicker {
        override fun contentDuringTransition(
            element: ElementKey,
            transition: TransitionState.Transition,
            fromContentZIndex: Long,
            toContentZIndex: Long,
        ): ContentKey {
            return when {
                transition.isTransitioning(Shade, QuickSettings) &&
                    transition.progress > 1f - SHARED_TILE_PICKER_THRESHOLD -> QuickSettings

                transition.isTransitioning(QuickSettings, Shade) &&
                    transition.progress < SHARED_TILE_PICKER_THRESHOLD -> QuickSettings

                else ->
                    HighestZIndexContentPicker.contentDuringTransition(
                        element,
                        transition,
                        fromContentZIndex,
                        toContentZIndex,
                    )
            }
        }
    }
}

private data class TileIdentity(val spec: TileSpec)
