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

package com.android.systemui.window.logging

import com.android.compose.animation.scene.OverlayKey
import com.android.compose.animation.scene.SceneKey
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.log.LogBuffer
import com.android.systemui.log.core.LogLevel
import com.android.systemui.log.dagger.WindowBackgroundBlurLog
import com.android.systemui.window.shared.model.BlurEffect
import javax.inject.Inject

@SysUISingleton
class BlurLogger @Inject constructor(@WindowBackgroundBlurLog private val buffer: LogBuffer) {
    fun logRequestWindowBackgroundBlur(
        currentScene: SceneKey,
        currentOverlays: Set<OverlayKey>,
        blurEffect: BlurEffect,
        isBlurCurrentlySupported: Boolean,
    ) {
        buffer.log(
            TAG,
            LogLevel.DEBUG,
            {
                str1 = currentScene.toString()
                str2 = currentOverlays.toString()
                double1 = blurEffect.radius.toDouble()
                str3 = blurEffect.scale.toString()
                bool1 = isBlurCurrentlySupported
            },
            {
                "requestWindowBackgroundBlur: scene=$str1, overlays=$str2, " +
                    "blur=(radius=$double1, scale=$str3), supported=$bool1"
            },
        )
    }

    fun logSkipApplyBlur(
        blurEffect: BlurEffect,
        wasUpdateScheduledForThisFrame: Boolean,
        lastScheduledBlurEffect: BlurEffect,
    ) {
        buffer.log(
            TAG,
            LogLevel.DEBUG,
            {
                double1 = blurEffect.radius.toDouble()
                str3 = blurEffect.scale.toString()
                bool1 = wasUpdateScheduledForThisFrame
                str1 = lastScheduledBlurEffect.toString()
            },
            {
                "Skip applying blur (same as last applied): radius=$double1, scale=$str3 " +
                    "(scheduled=$bool1, lastScheduled=$str1)"
            },
        )
    }

    companion object {
        private const val TAG = "WindowBackgroundBlur"
    }
}
