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

package com.android.test.transactionflinger.activities

import android.graphics.Color
import com.android.test.transactionflinger.Scene
import com.android.test.transactionflinger.checkerboardScene
import com.android.test.transactionflinger.scene
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Scene that draws a 2x2 checkboard background, toggling a blur every second
 */
class BlurOnOffActivity : SceneActivity() {

    override fun obtainScene(): Scene {
        return scene {
            externalScene {
                checkerboardScene(2, 2)
            }
            scene {
                content { data, width, height ->
                    // SurfaceControl blurs don't work unless we draw a transparent buffer.
                    // https://www.youtube.com/watch?v=76p_ncbffCE
                    drawColor(Color.TRANSPARENT, data, width, height)
                }
                properties { data ->
                    val animationTime =
                        ((data.preferredFrameTimeline.deadlineNanos - startTime) % 2.seconds.inWholeNanoseconds).nanoseconds
                    if (animationTime < 1.seconds) {
                        backgroundBlurRadius = 0
                    } else {
                        backgroundBlurRadius = 10
                    }
                }
            }
        }
    }
}