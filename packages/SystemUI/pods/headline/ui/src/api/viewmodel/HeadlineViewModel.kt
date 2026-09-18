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

package com.android.systemui.headline.ui.viewmodel

import android.annotation.CurrentTimeMillisLong
import android.widget.ImageView
import androidx.compose.ui.geometry.Rect
import com.android.compose.animation.scene.HoistedSceneTransitionLayoutState
import com.android.compose.animation.scene.SceneKey
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.common.shared.model.Text
import com.android.systemui.statusbar.chips.ui.model.OngoingActivityChipModel.Content
import com.android.systemui.util.time.SystemClock

/** ViewModel for the Headline. */
public interface HeadlineViewModel {
    /**
     * The STL state containing the current scene (item) and ongoing transitions.
     *
     * The current scene should be either the special [GoneScene] or the result of calling
     * [HeadlineItemKey.toSceneKey] on one of the [items].
     */
    public val state: HoistedSceneTransitionLayoutState

    /**
     * The different items in the Headline
     *
     * Important: The ViewModel is responsible for ensuring that [items] contains all items whose
     * [associated scene][HeadlineItemKey.toSceneKey] is referenced by [state]. This means that when
     * removing an item from this list, one must first wait for any animation involving that item
     * before actually removing it from the list.
     */
    public val items: List<HeadlineItem>

    /**
     * Called when an item is clicked, whether it was shown as the current item/scene/pill or shown
     * as a dot indicator.
     */
    public fun onItemClicked(item: HeadlineItem)

    /**
     * Returns the [ImageView] associated with the [key] of a [HeadlineItemContent.ImageViewItem],
     * or null if none exists.
     */
    public fun iconView(key: String): ImageView?

    public var uiBounds: Rect

    public companion object {
        /**
         * A special scene that indicates that Headline should be collapsed and should not show any
         * item.
         */
        public val GoneScene: SceneKey =
            HeadlineItemKey.None.toSceneKey(debugName = "HeadlineViewModel.GoneScene")
    }

    interface Factory {
        fun create(): HeadlineViewModel
    }
}

/** A single item in the Headline. */
public interface HeadlineItem {
    /** The unique key for this item. */
    public val key: HeadlineItemKey

    /**
     * The contents shown to the left (in LTR) or right (in RTL) of the Headline.
     *
     * The first content in this list will be aligned with the start edge of the Headline, i.e. it
     * will be either the left-most content in the Headline (in LTR) or the right-most content (in
     * RTL).
     *
     * This is typically:
     * - listOf(Icon(...))
     * - listOf(Icon(...), Text(...))
     * - listOf(Icon(...), Icon(...)*)
     */
    public val startContents: List<HeadlineItemContent>

    /**
     * The contents shown to the right (in LTR) or left (in RTL) of the Headline.
     *
     * The last content in this list will be aligned with the end edge of the Headline, i.e. it will
     * be either the right-most content in the Headline (in LTR) or the left-most content (in RTL).
     *
     * This is typically:
     * - listOf(Icon(...))
     * - listOf(Text(...), Icon(...))
     * - listOf(Icon(...), Icon(...)*)
     */
    public val endContents: List<HeadlineItemContent>
}

public sealed interface HeadlineItemContent {

    sealed interface TextBasedContent : HeadlineItemContent {

        public data class TextItem(val text: Text) : TextBasedContent

        public data class TimerItem(val timer: Content.Timer) : TextBasedContent

        public data class ShortTimeDelta(
            @CurrentTimeMillisLong val time: Long,
            val timeSource: SystemClock,
        ) : TextBasedContent
    }

    public data class IconItem(val icon: Icon) : HeadlineItemContent

    public data class ImageViewItem(val iconKey: String) : HeadlineItemContent
}

/** A key associated to a [HeadlineItem]. */
@JvmInline
public value class HeadlineItemKey(public val key: Any) {
    /** Convert this [HeadlineItemKey] into the associated [SceneKey]. */
    public fun toSceneKey(): SceneKey = toSceneKey(key.toString())

    internal fun toSceneKey(debugName: String): SceneKey {
        return SceneKey(debugName, identity = this)
    }

    override fun toString(): String {
        return "HeadlineItemKey(key=$key)"
    }

    public companion object {
        /**
         * A special key that can be used to indicate that there is no item selected and the
         * Headline should be collapsed.
         */
        public val None: HeadlineItemKey =
            HeadlineItemKey(
                object : Any() {
                    override fun toString(): String {
                        return "HeadlineItemKey.None"
                    }
                }
            )
    }
}

/** Convert this [SceneKey] into the associated [HeadlineItemKey]. */
public fun SceneKey.toHeadlineItemKey(): HeadlineItemKey {
    return (identity as? HeadlineItemKey)
        ?: error("$this was not created using HeadlineItemKey.toScene()")
}
