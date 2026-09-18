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

package com.android.wm.shell.bubbles.util

import android.app.WindowConfiguration
import android.graphics.Rect
import android.os.IBinder
import android.window.WindowContainerTransaction
import android.window.WindowContainerTransaction.Change.CHANGE_LAUNCH_NEXT_TO_BUBBLE
import com.android.wm.shell.shared.bubbles.BubbleFlagHelper
import com.google.common.truth.Truth.assertThat

object BubbleTestUtils {

    /** Verifies the [WindowContainerTransaction] to enter Bubble. */
    @JvmOverloads
    @JvmStatic
    fun verifyEnterBubbleTransaction(
        wct: WindowContainerTransaction,
        taskToken: IBinder,
        isAppBubble: Boolean,
        reparentToTda: Boolean = false,
        rootTaskToken: IBinder? = null,
    ) {
        // Verify hierarchy ops
        val ops = wct.hierarchyOps
        if (rootTaskToken != null) {
            assertThat(ops.any { op -> op.container == rootTaskToken && op.isAlwaysOnTop }).isTrue()
        } else {
            assertThat(ops.any { op -> op.container == taskToken && op.isAlwaysOnTop }).isTrue()
            if (reparentToTda) {
                assertThat(
                        ops.any { op ->
                            op.container == taskToken &&
                                op.isReparent &&
                                op.newParent == null &&
                                op.toTop
                        }
                    )
                    .isTrue()
            }
        }

        // Verify Change
        if (isAppBubble && com.android.window.flags.Flags.enableBubbleRootTask()) {
            assertThat(wct.changes[rootTaskToken]).isNotNull()
            val change = wct.changes[rootTaskToken]!!
            assertThat(change.windowSetMask and WindowConfiguration.WINDOW_CONFIG_BOUNDS)
                .isEqualTo(1)
        } else {
            assertThat(wct.changes[taskToken]).isNotNull()
            val change = wct.changes[taskToken]!!
            assertThat(rootTaskToken).isNull()
            assertThat(change.windowingMode)
                .isEqualTo(WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW)
            if (isAppBubble) {
                assertThat(change.launchNextToBubble).isTrue()
                assertThat(change.interceptBackPressed).isTrue()
            } else {
                assertThat(change.changeMask and CHANGE_LAUNCH_NEXT_TO_BUBBLE).isEqualTo(0)
            }
            assertThat(change.forceExcludedFromRecents).isTrue()
            assertThat(change.disablePip).isTrue()
            assertThat(change.disableLaunchAdjacent).isTrue()
        }
    }

    /** Verifies the [WindowContainerTransaction] to exit Bubble. */
    @JvmStatic
    fun verifyExitBubbleTransaction(
        wct: WindowContainerTransaction,
        taskToken: IBinder,
        captionInsetsOwner: IBinder? = null,
        resetBounds: Boolean = true,
    ) {
        // Verify hierarchy ops
        if (!BubbleFlagHelper.enableRootTaskForBubble()) {
            assertThat(
                    wct.hierarchyOps.any { op -> op.container == taskToken && !op.isAlwaysOnTop }
                )
                .isTrue()
        }

        // If there is a caption insets owner set, then that will add an hierarchy op after the
        // alwaysOnTop hierarchy op to remove the insets source.
        if (captionInsetsOwner != null) {
            assertThat(
                    wct.hierarchyOps.any { op ->
                        op.container == taskToken &&
                            op.insetsFrameProvider != null &&
                            op.caller == captionInsetsOwner
                    }
                )
                .isTrue()
        }

        // Verify Change
        if (!com.android.window.flags.Flags.enableBubbleRootTask()) {
            assertThat(wct.changes[taskToken]).isNotNull()
            val change = wct.changes[taskToken]!!
            assertThat(change.windowingMode).isEqualTo(WindowConfiguration.WINDOWING_MODE_UNDEFINED)
            assertThat(change.interceptBackPressed).isFalse()
            assertThat(change.forceExcludedFromRecents).isFalse()
            assertThat(change.disablePip).isFalse()
            assertThat(change.launchNextToBubble).isFalse()
            assertThat(change.disableLaunchAdjacent).isFalse()
            assertThat(change.configuration.windowConfiguration.bounds).isEqualTo(Rect())
        } else {
            if (resetBounds) {
                assertThat(wct.changes[taskToken]).isNotNull()
                val change = wct.changes[taskToken]!!
                assertThat(change.configuration.windowConfiguration.bounds).isEqualTo(Rect())
            } else {
                assertThat(wct.changes[taskToken]).isNull()
            }
        }
    }
}
