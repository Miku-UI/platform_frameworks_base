/*
 * Copyright 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
0 * Unless required by applicable law or agreed to in writing, software
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
 * A scene that draws translates an animating layer on top of a 4x4 checkerboard.
 */
class MovingLayerActivity : SceneActivity() {

    override fun obtainScene(): Scene {
        return scene {
            externalScene {
                checkerboardScene(4, 4)
            }
            scene {
                content { data, width, height ->
                    drawColor(Color.MAGENTA, data, width, height)
                }
                properties { data ->
                    val animationTime =
                        ((data.preferredFrameTimeline.deadlineNanos - startTime) % 2.seconds.inWholeNanoseconds).nanoseconds

                    val translation = if (animationTime < 1.seconds) {
                        (animationTime.inWholeMilliseconds.toDouble() / 1.seconds.inWholeMilliseconds) * 0.5
                    } else {
                        ((2.seconds - animationTime).inWholeMilliseconds.toDouble() / 1.seconds.inWholeMilliseconds) * 0.5
                    }

                    x = translation
                    y = translation
                }
                width = 0.5
                height = 0.5
            }
        }
    }
}