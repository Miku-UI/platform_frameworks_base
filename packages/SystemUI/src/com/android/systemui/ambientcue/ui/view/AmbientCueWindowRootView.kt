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

package com.android.systemui.ambientcue.ui.view

import android.content.Context
import android.graphics.Rect
import android.graphics.Region
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.tracing.trace
import com.android.compose.theme.PlatformTheme
import com.android.internal.jank.InteractionJankMonitor
import com.android.systemui.ambientcue.ui.compose.AmbientCueContainer
import com.android.systemui.ambientcue.ui.utils.AmbientCueJankMonitor
import com.android.systemui.ambientcue.ui.viewmodel.AmbientCueViewModel
import com.android.systemui.compose.ComposeInitializer
import com.android.systemui.dagger.qualifiers.Application
import javax.inject.Inject

/** A root view of the AmbientCue SysUI window. */
class AmbientCueWindowRootView
@Inject
constructor(
    private val windowManager: WindowManager,
    @Application applicationContext: Context,
    ambientCueViewModelFactory: AmbientCueViewModel.Factory,
    interactionJankMonitor: InteractionJankMonitor,
) : FrameLayout(applicationContext) {
    init {
        layoutParams =
            ViewGroup.LayoutParams(
                /* width = */ LayoutParams.MATCH_PARENT,
                /* height = */ LayoutParams.WRAP_CONTENT,
            )

        val composeView =
            ComposeView(context).apply {
                layoutParams =
                    LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                        gravity = Gravity.CENTER_VERTICAL
                        isClickable = true
                        isFocusable = true
                        isEnabled = true
                        defaultFocusHighlightEnabled = true
                        fitsSystemWindows = false
                    }
                val ambientCueJankMonitor = AmbientCueJankMonitor(interactionJankMonitor, this)
                setContent {
                    PlatformTheme {
                        AmbientCueContainer(
                            modifier = Modifier.fillMaxSize(),
                            ambientCueViewModelFactory = ambientCueViewModelFactory,
                            onShouldInterceptTouches = { interceptTouches, touchableRegion ->
                                val region =
                                    if (touchableRegion == null) null
                                    else
                                        Region(
                                            Rect(
                                                /* left = */ touchableRegion.left.toInt(),
                                                /* top = */ touchableRegion.top.toInt(),
                                                /* right = */ touchableRegion.right.toInt(),
                                                /* bottom = */ touchableRegion.bottom.toInt(),
                                            )
                                        )
                                this@AmbientCueWindowRootView.viewRootImpl?.setTouchableRegion(
                                    region
                                )
                                windowManager.updateViewLayout(
                                    this@AmbientCueWindowRootView,
                                    AmbientCueUtils.getAmbientCueLayoutParams(
                                        spyTouches = !interceptTouches
                                    ),
                                )
                            },
                            onAnimationStateChange = { cujType, animationState ->
                                ambientCueJankMonitor.onAnimationStateChange(
                                    cujType,
                                    animationState,
                                )
                            },
                        )
                    }
                }
            }

        addView(composeView)
    }

    override fun onAttachedToWindow() {
        trace("AmbientCue onAttachedToWindow") {
            super.onAttachedToWindow()
            ComposeInitializer.onAttachedToWindow(this)
        }
    }

    override fun onDetachedFromWindow() {
        trace("AmbientCue onDetachedFromWindow") {
            super.onDetachedFromWindow()
            ComposeInitializer.onDetachedFromWindow(this)
        }
    }
}
