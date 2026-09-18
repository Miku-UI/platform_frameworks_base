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

package com.android.settingslib.widget

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class ResponsiveActionButtonLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {
    private val MAX_LINES_HORIZONTAL = 4

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        orientation = HORIZONTAL
        restoreHorizontalLayoutParams()
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        if (shouldSwitchToVertical()) {
            orientation = VERTICAL
            applyVerticalLayoutParams()

            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }

    private fun shouldSwitchToVertical(): Boolean {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val textView = findTextViewRecursive(child)

            if (textView != null && textView.lineCount >= MAX_LINES_HORIZONTAL) {
                return true
            }
        }

        return false
    }

    private fun restoreHorizontalLayoutParams() {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val params = child.layoutParams as LayoutParams

            params.width = 0
            params.height = LayoutParams.WRAP_CONTENT
            params.weight = 1f
            child.layoutParams = params
        }
    }

    private fun applyVerticalLayoutParams() {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val params = child.layoutParams as LayoutParams

            params.width = LayoutParams.MATCH_PARENT
            params.height = LayoutParams.WRAP_CONTENT
            params.weight = 0f
            child.layoutParams = params
        }
    }

    private fun findTextViewRecursive(view: View): TextView? {
        if (view is TextView && view !is Button) {
            return view
        } else if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val found = findTextViewRecursive(view.getChildAt(i))
                if (found != null) {
                    return found
                }
            }
        }
        return null
    }
}