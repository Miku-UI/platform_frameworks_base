/*
 * Copyright 2025 The Android Open Source Project
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

package com.android.keyguard

const val DISABLED_FOREGROUND_ALPHA = 0.38f
const val DISABLED_BACKGROUND_ALPHA = 0.18f

/**
 * Changes its presentation for certain animations in progress.
 */
interface NumPadAnimatable {
    /**
     * Sets the alpha of the foreground and background
     */
    fun setAlpha(fgAlpha: Float, bgAlpha: Float)

    /**
     * Helper for converting a float alpha to an int.
     */
    fun i(alpha: Float): Int {
        return (255 * alpha).toInt()
    }
}