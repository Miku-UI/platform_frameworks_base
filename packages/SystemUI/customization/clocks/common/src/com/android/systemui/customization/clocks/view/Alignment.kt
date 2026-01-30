/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.customization.clocks.view

import android.view.View
import com.android.systemui.customization.clocks.utils.FontUtils.set

data class DigitalAlignment(
    val horizontalAlignment: HorizontalAlignment? = null,
    val verticalAlignment: VerticalAlignment? = null,
)

enum class VerticalAlignment {
    TOP,
    BOTTOM,
    BASELINE,
    CENTER,
}

enum class HorizontalAlignment {
    LEFT {
        override fun resolveXAlignment(view: View) = XAlignment.LEFT
    },
    RIGHT {
        override fun resolveXAlignment(view: View) = XAlignment.RIGHT
    },
    START {
        override fun resolveXAlignment(view: View): XAlignment {
            return if (view.isLayoutRtl()) XAlignment.RIGHT else XAlignment.LEFT
        }
    },
    END {
        override fun resolveXAlignment(view: View): XAlignment {
            return if (view.isLayoutRtl()) XAlignment.LEFT else XAlignment.RIGHT
        }
    },
    CENTER {
        override fun resolveXAlignment(view: View) = XAlignment.CENTER
    };

    abstract fun resolveXAlignment(view: View): XAlignment
}

enum class XAlignment {
    LEFT,
    RIGHT,
    CENTER,
}
