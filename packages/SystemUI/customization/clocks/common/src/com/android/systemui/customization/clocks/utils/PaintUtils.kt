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

package com.android.systemui.customization.clocks.utils

import android.graphics.Paint
import android.graphics.Rect
import com.android.systemui.customization.clocks.utils.FontUtils.set
import com.android.systemui.plugins.keyguard.VRectF

object PaintUtils {
    private val tempRect = Rect()

    fun Paint.getTextBounds(text: CharSequence): VRectF {
        this.getTextBounds(text, 0, text.length, tempRect)
        return VRectF(tempRect)
    }
}
