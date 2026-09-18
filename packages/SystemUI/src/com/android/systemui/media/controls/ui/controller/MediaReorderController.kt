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

package com.android.systemui.media.controls.ui.controller

import android.util.Log
import com.android.systemui.Flags
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.statusbar.notification.collection.provider.VisualStabilityProvider
import javax.inject.Inject

@SysUISingleton
class MediaReorderController
@Inject
constructor(private val visualStabilityProvider: VisualStabilityProvider) {
    // Set up in MediaHierarchyManager
    internal lateinit var isCarouselCurrentlyVisible: () -> Boolean

    private lateinit var reorderCallback: () -> Unit

    fun isReorderingAllowed(): Boolean {
        if (!Flags.mediaControlsReorderFix()) {
            return visualStabilityProvider.isReorderingAllowed
        }

        if (this::isCarouselCurrentlyVisible.isInitialized) {
            return !isCarouselCurrentlyVisible()
        }

        Log.w(TAG, "checked visibility before initialization")
        return true
    }

    fun setCallback(callback: () -> Unit) {
        reorderCallback = callback
        if (!Flags.mediaControlsReorderFix()) {
            visualStabilityProvider.addPersistentReorderingAllowedListener { reorderCallback() }
        }
    }

    /** Called when carousel is made not visible */
    fun onReorderingAllowed() {
        if (this::reorderCallback.isInitialized && Flags.mediaControlsReorderFix()) {
            Log.d(TAG, "Notifying carousel to reorder")
            reorderCallback()
        }
    }

    companion object {
        private const val TAG = "MediaReorderCtrl"
    }
}
