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

package com.android.systemui.plugins.keyguard

import android.view.View.MeasureSpec

@JvmInline
value class VMeasureSpec(val spec: Int) {
    val size: Int
        get() = MeasureSpec.getSize(spec)

    val mode: Mode
        get() {
            val modeSpec = MeasureSpec.getMode(spec)
            return Mode.entries.firstOrNull { it.value == modeSpec } ?: Mode.UNSPECIFIED
        }

    constructor(size: Int, mode: Mode) : this(size, mode.value)

    constructor(size: Int, mode: Int) : this(MeasureSpec.makeMeasureSpec(size, mode))

    override fun toString() = "Size=$size; Mode=$mode"

    enum class Mode(val value: Int) {
        UNSPECIFIED(MeasureSpec.UNSPECIFIED),
        EXACTLY(MeasureSpec.EXACTLY),
        AT_MOST(MeasureSpec.AT_MOST),
    }

    companion object {
        val UNSPECIFIED = unspecified(0)

        fun unspecified(size: Int) = VMeasureSpec(size, Mode.UNSPECIFIED)

        fun exactly(size: Int) = VMeasureSpec(size, Mode.EXACTLY)

        fun atMost(size: Int) = VMeasureSpec(size, Mode.AT_MOST)
    }
}
