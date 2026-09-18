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

package com.android.systemui.statusbar.notification.data.model

import android.graphics.drawable.Animatable2
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.statusbar.notification.data.repository.AnimationState
import com.android.systemui.statusbar.notification.data.repository.SummarizationAnimationRepository
import com.android.systemui.statusbar.notification.domain.interactor.SummarizationInteractor
import javax.inject.Inject

/** View model for notification summarization features. */
@SysUISingleton
class SummarizationIconViewModel
@Inject
constructor(
    val repository: SummarizationAnimationRepository,
    val interactor: SummarizationInteractor,
) {
    val TAG = "SummIconViewModel"
    val DEBUG: Boolean = Log.isLoggable(TAG, Log.DEBUG)

    fun onTriggered(entryKeys: List<String>) {
        entryKeys.forEach { entryKey ->
            val drawable: AnimatedVectorDrawable? = getDrawableForEntry(entryKey)

            when (repository.animationHistory.get(entryKey)) {
                AnimationState.HAS_ANIMATED -> {
                    if (DEBUG) Log.d(TAG, "Unexpectedly already done")
                }
                AnimationState.NEEDS_ANIMATION -> {
                    if (drawable != null && !drawable.isRunning) {
                        drawable.registerAnimationCallback(
                            object : Animatable2.AnimationCallback() {
                                override fun onAnimationEnd(drawable: Drawable?) {
                                    interactor.onTriggered(entryKey)
                                }
                            }
                        )
                        drawable.start()
                    }
                }
                null -> {
                    if (DEBUG) Log.d(TAG, "Missing animation state for $entryKey")
                }
            }
        }
    }

    private fun getDrawableForEntry(entryKey: String): AnimatedVectorDrawable? {
        return repository.drawables[entryKey]
    }

    val animationReadyEntryKeys = interactor.animationReadyEntryKeys
}
