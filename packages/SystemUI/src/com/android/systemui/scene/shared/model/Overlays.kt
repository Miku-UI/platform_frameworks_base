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

package com.android.systemui.scene.shared.model

import com.android.compose.animation.scene.OverlayKey
import com.android.systemui.plugins.keyguard.ui.composable.elements.LockscreenMovableParentKeys

/**
 * Keys of all known overlays.
 *
 * PLEASE KEEP THE KEYS SORTED ALPHABETICALLY.
 */
object Overlays {
    /**
     * The bouncer is the overlay that displays authentication challenges like PIN, password, or
     * pattern.
     */
    @JvmField val Bouncer = OverlayKey("bouncer")

    /**
     * The notifications shade overlay primarily shows a scrollable list of notifications.
     *
     * It's used only in the dual shade configuration, where there are two separate shades: one for
     * notifications (this overlay) and another for [QuickSettingsShade].
     *
     * It's not used in the single/accordion configuration (swipe down once to reveal the shade,
     * swipe down again the to expand quick settings) or in the "split" shade configuration (on
     * large screens or unfolded foldables, where notifications and quick settings are shown
     * side-by-side in their own columns).
     *
     * This key is defined within the plugin lib so that plugins can use MovableElement keys which
     * are part of this scene. This is necessary due to the dependency order of the plugin lib and
     * the need to use MovableElement for animating our legacy views.
     */
    @JvmField val NotificationsShade = LockscreenMovableParentKeys.NotificationsShade

    /**
     * The quick actions overlay hosts QuickActionPanels. These are anchored panels that are shown
     * when a [QuickActionChip] is selected in the status bar. Examples include: Media Controls,
     * Video Conferencing controls. This feature is only available on large screens.
     */
    @JvmField val QuickActions = OverlayKey("quick_actions")

    /**
     * The quick settings shade overlay shows the quick settings tiles UI.
     *
     * It's used only in the dual shade configuration, where there are two separate shades: one for
     * quick settings (this overlay) and another for [NotificationsShade].
     *
     * It's not used in the single/accordion configuration (swipe down once to reveal the shade,
     * swipe down again the to expand quick settings) or in the "split" shade configuration (on
     * large screens or unfolded foldables, where notifications and quick settings are shown
     * side-by-side in their own columns).
     */
    @JvmField val QuickSettingsShade = OverlayKey("quick_settings_shade")
}
