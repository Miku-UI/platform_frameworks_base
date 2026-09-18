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

package com.android.systemui.notifications.ui.composable

import com.android.compose.animation.scene.ContentKey
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.scene.shared.model.SceneContainerConfig
import javax.inject.Inject

/**
 * Helper class responsible solely for determining the z-order of scenes and overlays.
 *
 * Encapsulates the logic of sorting content keys based on the [SceneContainerConfig],
 * preventing duplication across different content pickers.
 */
@SysUISingleton
class ContentZOrderSorter @Inject constructor(
    private val config: SceneContainerConfig
) {
    /**
     * The keys of contents sorted by z-order (ascending).
     * The first element is at the bottom, the last element is on top.
     */
    private val zOrderedKeys: List<ContentKey> by lazy {
        config.sceneKeys + config.overlayKeys
    }

    /** Returns the content from [keys] with the highest z-index (top-most). */
    fun getHighestZ(keys: Set<ContentKey>): ContentKey? {
        // Iterate backwards to find the highest z-index element that exists in the provided set
        return zOrderedKeys.lastOrNull { it in keys }
    }

    /** Returns the content from [keys] with the lowest z-index (bottom-most). */
    fun getLowestZ(keys: Set<ContentKey>): ContentKey? {
        // Iterate forwards to find the lowest z-index element that exists in the provided set
        return zOrderedKeys.firstOrNull { it in keys }
    }
}
