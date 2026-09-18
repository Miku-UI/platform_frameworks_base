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

package com.android.systemui.plugins.keyguard.ui.composable.elements

import com.android.compose.animation.scene.ContentKey
import com.android.compose.animation.scene.ElementKey
import com.android.compose.animation.scene.HighestZIndexContentPicker
import com.android.compose.animation.scene.MovableElementKey
import com.android.compose.animation.scene.OverlayKey
import com.android.compose.animation.scene.SceneKey
import com.android.compose.animation.scene.StaticElementContentPicker
import com.android.compose.animation.scene.content.state.TransitionState

/** Keys for scenes and overlays that our movable elements may appear in */
object LockscreenMovableParentKeys {
    /** Non-nested top-level scene */
    val Lockscreen = SceneKey("lockscreen")

    /**
     * The notifications shade is not part of lockscreen, but we render the small clock within it,
     * so we must be able to reference it when creating movable element keys for the clock.
     */
    val NotificationsShade = OverlayKey("notifications_shade")

    /** Subscenes used by the UpperRegion layouts */
    object UpperRegion {
        object NarrowLayout {
            val LargeClock = SceneKey("UpperRegion-NarrowLayout-LargeClock")
            val SmallClock = SceneKey("UpperRegion-NarrowLayout-SmallClock")
        }

        object WideLayout {
            val CenteredClock = SceneKey("UpperRegion-WideLayout-CenteredClock")

            object TwoColumn {
                val LargeClock = SceneKey("UpperRegion-WideLayout-TwoColumns-LargeClock")
                val SmallClock = SceneKey("UpperRegion-WideLayout-TwoColumns-SmallClock")
            }
        }
    }
}

/**
 * Defines several compose element keys which are useful for sharing a composable between the host
 * process and the client. These are similar to the view ids used previously.
 */
object LockscreenElementKeys {
    /**
     * Picker to use with our MovableElementKeys
     *
     * Note: Only use when actually necessary. Prefer regular ElementKeys. MovableElement is
     * typically needed when an AndroidView needs to animate between two of our subscenes, but two
     * copies of the wrapped view cannot be created at the same time.
     */
    val ContentPicker =
        // TODO(b/b/454283910 ): Replace this by DefaultElementContentPicker and stop sharing the
        // same picker for all elements. Each element should have its own picker, with the exact set
        // of scenes in which the element *is* composed.
        object : StaticElementContentPicker {
            override val contents: Set<ContentKey> =
                setOf(
                    LockscreenMovableParentKeys.Lockscreen,
                    LockscreenMovableParentKeys.NotificationsShade,
                    LockscreenMovableParentKeys.UpperRegion.NarrowLayout.LargeClock,
                    LockscreenMovableParentKeys.UpperRegion.NarrowLayout.SmallClock,
                    LockscreenMovableParentKeys.UpperRegion.WideLayout.CenteredClock,
                    LockscreenMovableParentKeys.UpperRegion.WideLayout.TwoColumn.LargeClock,
                    LockscreenMovableParentKeys.UpperRegion.WideLayout.TwoColumn.SmallClock,
                )

            override fun contentDuringTransition(
                element: ElementKey,
                transition: TransitionState.Transition,
                fromContentZIndex: Long,
                toContentZIndex: Long,
            ): ContentKey {
                return HighestZIndexContentPicker.contentDuringTransition(
                    element,
                    transition,
                    fromContentZIndex,
                    toContentZIndex,
                )
            }
        }

    /** Root element of the entire lockcsreen */
    val Root = ElementKey("LockscreenRoot")
    val BehindScrim = ElementKey("LockscreenBehindScrim")

    object Region {
        /** The upper region includes everything above the lock icon */
        val Upper = ElementKey("LockscreenUpperRegion")

        /** The lower region includes everything below the lock icon */
        val Lower = ElementKey("LockscreenLowerRegion")

        /** The clock regions include the clock, smartspace, and the date/weather view */
        object Clock {
            val Large = ElementKey("LargeClockRegion")
            val Small = ElementKey("SmallClockRegion")
        }
    }

    /** The UMO's lockscreen element */
    val MediaCarousel = ElementKey("LockscreenMediaCarousel")

    object Notifications {
        /** The notification stack display on lockscreen */
        val Stack = ElementKey("LockscreenNotificationStack")

        object AOD {
            /** Icon shelf for AOD display */
            val IconShelf = MovableElementKey("AODNotificationIconShelf", ContentPicker)

            /** Notifications for the AOD Promoted Region */
            val Promoted = ElementKey("AODPromotedNotifications")
        }
    }

    /** Lock Icon / UDFPS */
    val LockIcon = ElementKey("LockIcon")

    /** Customize affordance pop-up */
    val SettingsMenu = ElementKey("SettingsMenu")

    /** Lockscreen-specific Status Bar element */
    val StatusBar = ElementKey("LockscreenStatusBar")

    /** Standard indication area element */
    val IndicationArea = ElementKey("IndicationArea")

    /** Ambient Indication Area (vendor defined, not included in AOSP) */
    val AmbientIndicationArea = ElementKey("AmbientIndicationArea")

    /** Element keys for the start and end shortcuts */
    object Shortcuts {
        val Start = ElementKey("ShortcutStart")
        val End = ElementKey("ShortcutEnd")
    }

    /** Element Keys for composables which wrap clock views */
    object Clock {
        val Large = MovableElementKey("LargeClock", ContentPicker)
        val Small = MovableElementKey("SmallClock", ContentPicker)
    }

    /** Smartspace provided lockscreen elements */
    object Smartspace {
        /** The card view is the large smartspace view which shows contextual information. */
        val Cards = MovableElementKey("SmartspaceCards", ContentPicker)

        /**
         * DWA is the parent element for date/weather/alarm elements. The relative layout of these
         * elements is currently controlled by smartspace on the view side. The large / small views
         * are further broken out into different keys so that we can differentiate between them for
         * animations.
         */
        object DWA {
            object LargeClock {
                val Above = MovableElementKey("SmartspaceDWA-LargeClock-Above", ContentPicker)
                val Below = MovableElementKey("SmartspaceDWA-LargeClock-Below", ContentPicker)
            }

            object SmallClock {
                val Column = MovableElementKey("SmartspaceDWA-SmallClock-Column", ContentPicker)
                val Row = MovableElementKey("SmartspaceDWA-SmallClock-Row", ContentPicker)
            }
        }
    }
}
