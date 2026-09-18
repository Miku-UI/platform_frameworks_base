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

import android.widget.ImageView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import com.android.compose.animation.scene.HoistedSceneTransitionLayoutState
import com.android.compose.animation.scene.SceneKey
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.common.shared.model.Text
import com.android.systemui.headline.ui.preview.R

/** A fake implementation of [HeadlineViewModel]. */
class FakeHeadlineViewModel(
    override val items: List<HeadlineItem> = fakeHeadlineItems(),
    override val state: HoistedSceneTransitionLayoutState =
        HoistedSceneTransitionLayoutState(defaultInitialScene(items)),
    private val onItemClicked: (HeadlineItem) -> Unit = {
        // TODO(b/450236706): Call state.animateOrSnapTo(item.key.toSceneKey()) by default.
    },
    private val iconViewStore: (key: String) -> ImageView? = { null },
) : HeadlineViewModel {
    constructor(
        items: List<HeadlineItem> = fakeHeadlineItems(),
        currentItem: HeadlineItem? = items.first(),
        onItemClicked: (HeadlineItem) -> Unit = {},
        iconViewStore: (key: String) -> ImageView? = { null },
    ) : this(
        items = items,
        state =
            HoistedSceneTransitionLayoutState(
                currentItem?.key?.toSceneKey() ?: HeadlineViewModel.GoneScene
            ),
        onItemClicked = onItemClicked,
        iconViewStore = iconViewStore,
    )

    override fun onItemClicked(item: HeadlineItem) {
        onItemClicked.invoke(item)
    }

    override fun iconView(key: String): ImageView? = iconViewStore.invoke(key)

    override var uiBounds: Rect by mutableStateOf(Rect.Zero)

    companion object {
        private fun defaultInitialScene(items: List<HeadlineItem>): SceneKey =
            (items.firstOrNull()?.key ?: HeadlineItemKey.None).toSceneKey()
    }
}

fun fakeHeadlineItems(): List<FakeHeadlineItem> {
    return listOf(
        FakeHeadlineItem("timer", R.drawable.timer, "4:42"),
        FakeHeadlineItem("spotify", R.drawable.music_note, "Espresso"),
        FakeHeadlineItem("car", R.drawable.directions_car, "3 min"),
        FakeHeadlineItem("phone", R.drawable.phone, "incoming call"),
    )
}

class FakeHeadlineItem(
    key: Any = "spotify",
    override val startContents: List<HeadlineItemContent>,
    override val endContents: List<HeadlineItemContent>,
) : HeadlineItem {
    constructor(
        key: Any = "spotify",
        icon: Int = R.drawable.music_note,
        text: String = "Espresso",
    ) : this(
        key,
        listOf(HeadlineItemContent.IconItem(Icon.Resource(icon, ContentDescription.Loaded(null)))),
        listOf(HeadlineItemContent.TextBasedContent.TextItem(Text.Loaded(text))),
    )

    override val key: HeadlineItemKey = HeadlineItemKey(key)

    override fun toString(): String {
        return "FakeHeadlineItem(key=$key)"
    }
}
