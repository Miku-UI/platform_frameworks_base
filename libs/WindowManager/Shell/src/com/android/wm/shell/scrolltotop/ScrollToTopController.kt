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

package com.android.wm.shell.scrolltotop

import android.app.ActivityTaskManager.INVALID_TASK_ID
import android.os.RemoteException
import android.util.Log
import android.view.IWindowManager
import com.android.wm.shell.common.ShellExecutor
import com.android.wm.shell.splitscreen.SplitScreenController
import java.util.Optional

/** Controller for the Scroll To Top feature. */
class ScrollToTopController(
    private val mMainExecutor: ShellExecutor,
    private val mWindowManager: IWindowManager,
    private val mSplitScreenController: Optional<SplitScreenController>,
) {
    private val mImpl = ScrollToTopImpl()

    fun asScrollToTop(): ScrollToTop {
        return mImpl
    }

    private fun onScrollToTop(displayId: Int, x: Int) {
        val taskId =
            mSplitScreenController
                .orElse(null)
                ?.takeIf { it.isSplitScreenFocused }
                ?.getTaskInSplitAt(x, displayId) ?: INVALID_TASK_ID

        try {
            mWindowManager.dispatchScrollToTop(displayId, x, taskId)
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to dispatch scroll to top", e)
        }
    }

    private inner class ScrollToTopImpl : ScrollToTop {
        override fun onScrollToTop(displayId: Int, x: Int) {
            mMainExecutor.execute { this@ScrollToTopController.onScrollToTop(displayId, x) }
        }
    }

    companion object {
        private const val TAG = "ScrollToTopController"
    }
}
