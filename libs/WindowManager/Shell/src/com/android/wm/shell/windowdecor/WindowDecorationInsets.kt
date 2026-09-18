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

package com.android.wm.shell.windowdecor

import android.graphics.Insets
import android.graphics.Rect
import android.os.Binder
import android.view.InsetsBoundingRect
import android.view.InsetsSource
import android.view.WindowInsets
import android.window.WindowContainerToken
import android.window.WindowContainerTransaction
import com.android.window.flags.Flags

/** Adds, removes, and updates caption insets. */
data class WindowDecorationInsets
private constructor(
    private val token: WindowContainerToken,
    private val owner: Binder,
    private val frame: Frame,
    private val boundingRects: List<Rect> = emptyList(),
    private val insetsBoundingRects: List<InsetsBoundingRect> = emptyList(),
    @InsetsSource.Flags private val flags: Int = 0,
    private val shouldAddCaptionInset: Boolean = false,
    private val appBoundsExclusion: AppBoundsExclusion? = null,
) {
    private sealed class Frame {
        abstract val height: Int

        data class Absolute(val rect: Rect, override val height: Int = rect.height()) : Frame()

        data class Relative(override val height: Int) : Frame()
    }

    private data class AppBoundsExclusion(val taskFrame: Rect)

    constructor(
        token: WindowContainerToken,
        owner: Binder,
        frame: Rect,
        taskFrame: Rect? = null,
        boundingRects: List<Rect> = emptyList(),
        insetsBoundingRects: List<InsetsBoundingRect> = emptyList(),
        @InsetsSource.Flags flags: Int = 0,
        shouldAddCaptionInset: Boolean = false,
        excludedFromAppBounds: Boolean = false,
    ) : this(
        token,
        owner,
        Frame.Relative(frame.height()),
        boundingRects,
        insetsBoundingRects,
        flags,
        shouldAddCaptionInset,
        if (excludedFromAppBounds) AppBoundsExclusion(checkNotNull(taskFrame)) else null,
    )

    /** Updates the caption insets. */
    fun update(wct: WindowContainerTransaction) {
        if (!shouldAddCaptionInset) return
        logD(
            TAG,
            "update insets for wc=%s with frame=%s, rects=%s, appBoundsExclusion=%s",
            token,
            frame,
            if (com.android.window.flags.Flags.improveFluidResizingPerformance()) {
                insetsBoundingRects
            } else {
                boundingRects
            },
            appBoundsExclusion,
        )
        if (com.android.window.flags.Flags.improveFluidResizingPerformance()) {
            val rects: Array<InsetsBoundingRect>? =
                if (insetsBoundingRects.isEmpty()) null else insetsBoundingRects.toTypedArray()
            when (frame) {
                is Frame.Absolute -> {
                    wct.addInsetsSource(
                        token,
                        owner,
                        INDEX,
                        WindowInsets.Type.captionBar(),
                        frame.rect,
                        rects,
                        flags,
                    )
                    wct.addInsetsSource(
                        token,
                        owner,
                        INDEX,
                        WindowInsets.Type.mandatorySystemGestures(),
                        frame.rect,
                        rects,
                        /* flags= */ 0,
                    )
                }
                is Frame.Relative -> {
                    val insets = Insets.of(0, frame.height, 0, 0)
                    wct.addInsetsSource(
                        token,
                        owner,
                        INDEX,
                        WindowInsets.Type.captionBar(),
                        insets,
                        rects,
                        flags,
                    )
                    wct.addInsetsSource(
                        token,
                        owner,
                        INDEX,
                        WindowInsets.Type.mandatorySystemGestures(),
                        insets,
                        rects,
                        /* flags= */ 0,
                    )
                }
            }
        } else {
            val rects: Array<Rect>? =
                if (boundingRects.isEmpty()) null else boundingRects.toTypedArray()
            when (frame) {
                is Frame.Absolute -> {
                    wct.addInsetsSource(
                        token,
                        owner,
                        INDEX,
                        WindowInsets.Type.captionBar(),
                        frame.rect,
                        rects,
                        flags,
                    )
                    wct.addInsetsSource(
                        token,
                        owner,
                        INDEX,
                        WindowInsets.Type.mandatorySystemGestures(),
                        frame.rect,
                        rects,
                        /* flags= */ 0,
                    )
                }
                is Frame.Relative -> {
                    val insets = Insets.of(0, frame.height, 0, 0)
                    wct.addInsetsSource(
                        token,
                        owner,
                        INDEX,
                        WindowInsets.Type.captionBar(),
                        insets,
                        rects,
                        flags,
                    )
                    wct.addInsetsSource(
                        token,
                        owner,
                        INDEX,
                        WindowInsets.Type.mandatorySystemGestures(),
                        insets,
                        rects,
                        /* flags= */ 0,
                    )
                }
            }
        }
        if (!Flags.refactorCaptionSandboxingToCore()) {
            appBoundsExclusion?.let { exclusion ->
                val appBounds = Rect(exclusion.taskFrame).apply { top += frame.height }
                wct.setAppBounds(token, appBounds)
            }
        }
    }

    /** Removes the caption insets. */
    fun remove(wct: WindowContainerTransaction) {
        wct.removeInsetsSource(token, owner, INDEX, WindowInsets.Type.captionBar())
        wct.removeInsetsSource(token, owner, INDEX, WindowInsets.Type.mandatorySystemGestures())
        if (!Flags.refactorCaptionSandboxingToCore()) {
            appBoundsExclusion?.let { wct.setAppBounds(token, Rect()) }
        }
    }

    companion object {
        private const val TAG = "WindowDecorationInsets"
        /** Index for caption insets source. */
        private const val INDEX = 0
    }
}
