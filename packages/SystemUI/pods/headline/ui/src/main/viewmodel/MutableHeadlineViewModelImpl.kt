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

package com.android.systemui.headline.ui.viewmodel

import android.widget.ImageView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateSet
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.util.fastAny
import com.android.compose.animation.scene.HoistedSceneTransitionLayoutState
import com.android.compose.animation.scene.SceneKey
import com.android.systemui.headline.ui.viewmodel.HeadlineViewModel.Companion.GoneScene
import javax.inject.Inject

class MutableHeadlineViewModelImpl @Inject constructor() : MutableHeadlineViewModel {
    override val state: HoistedSceneTransitionLayoutState =
        HoistedSceneTransitionLayoutState(
            initialScene = GoneScene,
            canChangeScene = { scene -> !keysToPrune.contains(scene.toHeadlineItemKey()) },
            onTransitionEnd = { pruneItems() },
            onSnap = { pruneItems() },
        )
    private val _items: SnapshotStateList<HeadlineItem> = mutableStateListOf()
    private val keysToPrune: SnapshotStateSet<HeadlineItemKey> = mutableStateSetOf()
    override val items: List<HeadlineItem> = _items

    override fun updateItems(items: List<HeadlineItem>) {
        val newKeys = items.map { it.key }.toSet()

        // Find the items that are deleted but still composed
        val composedDeletedItems =
            _items.filter { !newKeys.contains(it.key) && willItemSceneBeComposed(it) }

        _items.apply {
            clear()

            // Add all new items, which are sorted by priority
            addAll(items)

            // Add the deleted items while waiting for them to be pruned when safe to do so
            addAll(composedDeletedItems)
        }
        keysToPrune.addAll(composedDeletedItems.map { it.key })

        // Transition to the first new item
        animateOrSnapTo(items.firstOrNull()?.key?.toSceneKey() ?: GoneScene)
    }

    override fun onItemClicked(item: HeadlineItem) {
        animateOrSnapTo(item.key.toSceneKey())
    }

    override fun iconView(key: String): ImageView? {
        // Unsupported for this implementation
        return null
    }

    override var uiBounds: Rect by mutableStateOf(Rect.Zero)

    private fun animateOrSnapTo(scene: SceneKey?) {
        val targetScene = scene ?: GoneScene

        val uiBoundState = state.uiBoundState
        if (uiBoundState != null) {
            uiBoundState.setTargetScene(targetScene)
        } else {
            state.snapTo(targetScene)
        }
    }

    private fun pruneItems() {
        // Clear items that are safe to remove
        val keysSafeToPrune =
            items
                .filter { keysToPrune.contains(it.key) && !willItemSceneBeComposed(it) }
                .map { it.key }
                .toSet()

        _items.removeAll { keysSafeToPrune.contains(it.key) }
        keysToPrune.removeAll(keysSafeToPrune)
    }

    private fun willItemSceneBeComposed(item: HeadlineItem): Boolean {
        val scene = item.key.toSceneKey()
        return scene == state.currentScene ||
            state.currentTransitions.fastAny { it.fromContent == scene || it.toContent == scene }
    }
}
