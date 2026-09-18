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

package com.android.systemui.statusbar.chips.ui.model

import android.annotation.CurrentTimeMillisLong
import android.annotation.StringRes
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import com.android.internal.logging.InstanceId
import com.android.systemui.animation.ComposableControllerFactory
import com.android.systemui.animation.Expandable
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.res.R
import com.android.systemui.util.time.SystemClock

/** Model representing the display of an ongoing activity as a chip in the status bar. */
sealed class OngoingActivityChipModel {
    /** Condensed name representing the model, used for logs. */
    abstract val logName: String

    /** Object used to manage the behavior of this chip during activity launch and returns. */
    abstract val transitionManager: TransitionManager?

    /**
     * This chip shouldn't be shown.
     *
     * @property shouldAnimate true if the transition from [Active] to [Inactive] should be
     *   animated, and false if that transition should *not* be animated (i.e. the chip view should
     *   immediately disappear).
     */
    data class Inactive(
        val shouldAnimate: Boolean = true,
        override val transitionManager: TransitionManager? = null,
    ) : OngoingActivityChipModel() {
        override val logName = "Inactive(anim=$shouldAnimate)"
    }

    /** This chip should be shown with the given information. */
    data class Active(
        /**
         * A key that uniquely identifies this chip. Used for better visual effects, like animation,
         * and UI event logging.
         *
         * Should be guaranteed to be unique from all other chips.
         */
        val key: String,
        /**
         * If this chip is associated with a notification, this is just the notification key. For
         * chips not associated with a notification, this is null.
         */
        val notificationKey: String? = null,
        /** Notification claims to represent the app's active screenshare */
        val isScreenShareNotification: Boolean = false,
        /** The package name of the app managing this chip. */
        val managingPackageName: String? = null,
        /**
         * True if this chip is critical for privacy so we should keep it visible at all times, and
         * false otherwise.
         */
        val isImportantForPrivacy: Boolean = false,
        /** The icon to show on the chip. If null, no icon will be shown. */
        val icon: ChipIcon?,
        /** The content shown in the chip next to the icon. */
        val content: Content,
        /** What colors to use for the chip. */
        val colors: ColorsModel,
        /** Data class that determines how clicks on the chip should be handled. */
        val clickBehavior: ClickBehavior,
        override val transitionManager: TransitionManager? = null,
        /**
         * Whether this chip should be hidden. This can be the case depending on system states (like
         * which apps are in the foreground and whether there is an ongoing transition.
         */
        val isHidden: Boolean = false,
        /** Whether the transition from hidden to shown should be animated. */
        val shouldAnimate: Boolean = true,
        /** A decorative icon to show on the end side of the chip. */
        val decorativeIcon: DecorativeIcon? = null,
        /**
         * An optional per-chip ID used for logging. Should stay the same throughout the lifetime of
         * a single chip.
         */
        val instanceId: InstanceId? = null,
    ) : OngoingActivityChipModel() {
        init {
            if (content == Content.IconOnly && icon == null) {
                throw IllegalArgumentException("Cannot use Content.IconOnly with a null icon")
            }
            if (content is Content.Countdown) {
                require(icon == null)
                require(clickBehavior is ClickBehavior.None)
            }
        }

        override val logName: String
            get() = "Active(key=$key).${content.logName}"
    }

    /** The content shown in the chip next to the icon. */
    sealed class Content {
        /** Condensed name representing the model, used for logs. */
        abstract val logName: String

        /** This chip shows only an icon and nothing else. */
        data object IconOnly : Content() {
            override val logName = "IconOnly"
        }

        /** The chip shows a timer. */
        data class Timer(
            /**
             * The timer's value (as a chronometer from/to a point in time, or a paused duration).
             */
            val value: Chronometer,

            /** How the [value] should be formatted. */
            val format: Format = Format.CHRONOMETER,

            /**
             * The [SystemClock] used to track the current time for this timer. Only used in the
             * Compose version of the chips.
             */
            val timeSource: SystemClock,
        ) : Content() {
            override val logName = "Timer(value=$value format=$format)"

            enum class Format {
                /** "Normal" chronometer appearance (e.g. 04:30) */
                CHRONOMETER,

                /**
                 * Adaptive/textual chronometer format. See
                 * [android.widget.ChronometerAdaptiveFormat.format]
                 */
                ADAPTIVE,
            }
        }

        /**
         * The chip shows the time delta between now and [time] in a short format, e.g. "15min" or
         * "1hr ago".
         */
        data class ShortTimeDelta(
            /**
             * The time of the event that this chip represents. Relative to
             * [SystemClock.currentTimeMillis] because that's what's required by
             * [android.widget.DateTimeView].
             *
             * TODO(b/372657935): When the Compose chips are launched, we should convert this to be
             *   relative to [com.android.systemui.util.time.SystemClock.elapsedRealtime] so that
             *   this model and the [Timer] model use the same units.
             */
            @CurrentTimeMillisLong val time: Long,

            /**
             * The [SystemClock] that should be used to track the current time for this timer. Only
             * used in the Compose version of the chips.
             */
            val timeSource: SystemClock,
        ) : Content() {
            override val logName = "ShortTimeDelta(time=$time)"
        }

        /**
         * This chip shows a countdown using [secondsUntilStarted]. Used to inform users that an
         * event is about to start. Typically, a [Countdown] chip will turn into a [Timer] chip.
         */
        data class Countdown(
            /** The number of seconds until an event is started. */
            val secondsUntilStarted: Long
        ) : Content() {
            override val logName = "Countdown($secondsUntilStarted)"
        }

        /** This chip shows the specified [text] in the chip. */
        data class Text(val text: String) : Content() {
            override val logName = "Text($text)"
        }

        /**
         * This chip shows one of the specified [textVariants] in the chip. According to the
         * available space, the first one tha fits will be shown.
         */
        data class TextVariants(val textVariants: List<String>) : Content() {
            override val logName = "TextVariants($textVariants)"
        }
    }

    /** Represents an icon to show on the chip. */
    sealed class ChipIcon(
        /** True if this icon will have padding embedded within its view. */
        open val hasEmbeddedPadding: Boolean
    ) {
        /**
         * The icon is a custom icon, which is set on a notification, and can be looked up using the
         * provided [notificationKey]. The icon was likely created by an external app.
         */
        data class StatusBarNotificationIcon(
            val notificationKey: String,
            val contentDescription: ContentDescription,
        ) : ChipIcon(hasEmbeddedPadding = true)

        /**
         * This icon is a single color and it came from basic resource or drawable icon that System
         * UI created internally.
         */
        data class SingleColorIcon(val impl: Icon) : ChipIcon(hasEmbeddedPadding = false)
    }

    /** Defines the behavior of the chip when it is clicked. */
    sealed interface ClickBehavior {
        /**
         * Custom semantic / accessibility label for the onClick action. See [Modifier.clickable].
         */
        @get:StringRes val customOnClickLabel: Int?

        /** No specific click behavior. */
        data object None : ClickBehavior {
            override val customOnClickLabel = null
        }

        /** The chip expands into a dialog or activity on click. */
        data class ExpandAction(val onClick: (Expandable) -> Unit) : ClickBehavior {
            override val customOnClickLabel = null
        }

        /** Clicking the chip will show the heads up notification associated with the chip. */
        data class ShowHeadsUpNotification(val onClick: () -> Unit) : ClickBehavior {
            override val customOnClickLabel =
                R.string.status_bar_chip_custom_a11y_action_expand_notification
        }

        /** Clicking the chip will hide the heads up notification associated with the chip. */
        data class HideHeadsUpNotification(val onClick: () -> Unit) : ClickBehavior {
            override val customOnClickLabel =
                R.string.status_bar_chip_custom_a11y_action_collapse_notification
        }
    }

    /** Defines the behavior of the chip with respect to activity launch and return transitions. */
    data class TransitionManager(
        /** The factory used to create the controllers that animate the chip. */
        val controllerFactory: ComposableControllerFactory? = null,
        /**
         * Used to create a registration for this chip using [controllerFactory]. Must be
         * idempotent.
         */
        val registerTransition: () -> Unit = {},
        /** Used to remove the existing registration for this chip, if any. */
        val unregisterTransition: () -> Unit = {},
        /**
         * Whether the chip should be made invisible (0 opacity) while still being composed. This is
         * necessary to avoid flickers at the beginning of return transitions, when the chip must
         * not be visible but must be composed in order for the animation to start.
         */
        val hideChipForTransition: Boolean = false,
    )

    /** Represents a decorative icon to show on the right side of the chip. */
    data class DecorativeIcon(val icon: Icon, val backgroundShape: Shape, val colors: ColorsModel)
}
