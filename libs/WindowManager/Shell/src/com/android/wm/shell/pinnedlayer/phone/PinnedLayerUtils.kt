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

package com.android.wm.shell.pinnedlayer.phone

import android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM
import android.graphics.Rect
import android.window.WindowContainerToken
import android.window.WindowContainerTransaction
import com.android.wm.shell.pinnedlayer.phone.PinnedLayerUtils.getLayerPinnedWct
import com.android.wm.shell.pinnedlayer.phone.PinnedLayerUtils.getLayerUnpinnedWct

/** A utility class that contains pinned layer utilities like WCT operations. */
object PinnedLayerUtils {

    /**
     * Populates a [WindowContainerTransaction] with pinning operations.
     *
     * @param windowContainerToken a token that represents a window on which hierarchy operations
     *   should be applied.
     * @param bounds a [Rect] that represents bounds which a window should have after pinning.
     * @return [WindowContainerTransaction] that encapsulates enter hierarchy operations.
     */
    fun getLayerPinnedWct(
        windowContainerToken: WindowContainerToken,
        bounds: Rect? = null,
    ): WindowContainerTransaction {
        return WindowContainerTransaction()
            .setWindowingMode(windowContainerToken, WINDOWING_MODE_FREEFORM)
            .reparent(windowContainerToken, null, true)
            .apply { if (bounds != null) setBounds(windowContainerToken, bounds) }
            .setAlwaysOnTop(windowContainerToken, true)
            .setDisablePip(windowContainerToken, true)
    }

    /**
     * Populates a [WindowContainerTransaction] that unpins a window from display. Minimizes a
     * [windowContainerToken] by default.
     *
     * @param windowContainerToken a window token that unpinning operation targets.
     * @param isMinimizing whether a window should be minimized or closed.
     * @return [WindowContainerTransaction] that holds unpinning hierarchy operations.
     */
    // TODO(b/449681882): Split into getMinimizedPinnedWct and getClosedPinnedWct.
    fun getLayerUnpinnedWct(
        windowContainerToken: WindowContainerToken,
        isMinimizing: Boolean = true,
    ): WindowContainerTransaction {
        return WindowContainerTransaction()
            .setAlwaysOnTop(windowContainerToken, /* alwaysOnTop= */ false)
            .apply {
                if (isMinimizing) {
                    reorder(windowContainerToken, /* onTop= */ false)
                } else {
                    removeTask(windowContainerToken)
                }
            }
    }

    /**
     * Populates a [WindowContainerTransaction] that removes all pin related properties from the
     * target window container. The difference with [getLayerUnpinnedWct] is current method cleans
     * up properties set with [getLayerPinnedWct].
     *
     * @param windowContainerToken a window token that clean operations target.
     * @return [WindowContainerTransaction] that holds clean hierarchy operations.
     * @see [getLayerUnpinnedWct]
     */
    // TODO(b/449681882): Rename to getCleanPinnedLayerWct.
    fun getRemovedFromLayerWct(
        windowContainerToken: WindowContainerToken
    ): WindowContainerTransaction {
        return WindowContainerTransaction()
            .setAlwaysOnTop(windowContainerToken, false)
            .setDisablePip(windowContainerToken, false)
    }
}
