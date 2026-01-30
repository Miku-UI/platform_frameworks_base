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
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RenderNode
import com.android.test.transactionflinger.Scene
import com.android.test.transactionflinger.scene
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds

/**
 * A scene that draws half the screen black and the other half white, alternating at 60fps.
 * The middle region of the screen is blurred.
 */
class BlurRegionActivity : SceneActivity() {
    override fun obtainScene(): Scene {
        return scene {
            scene {
                content { data, width, height ->
                    val animationTime =
                        ((data.preferredFrameTimeline.deadlineNanos - startTime) % 33.milliseconds.inWholeNanoseconds).nanoseconds

                    val channel = if (animationTime < 16.milliseconds) {
                        0
                    } else {
                        255
                    }
                    val renderNode = RenderNode("cogsapp")
                    renderNode.setPosition(Rect(0, 0, width, height))
                    val paint = Paint()
                    paint.color = Color.argb(255, channel, channel, channel)
                    renderNode.beginRecording(width, height).drawPaint(paint)
                    renderNode.endRecording()
                    renderNode
                }
                x = 0.0
                y = 0.0
                width = 1.0
                height = 0.5
            }
            scene {
                content { data, width, height ->
                    val animationTime =
                        ((data.preferredFrameTimeline.deadlineNanos - startTime) % 33.milliseconds.inWholeNanoseconds).nanoseconds

                    val channel = if (animationTime < 16.milliseconds) {
                        255
                    } else {
                        0
                    }
                    val renderNode = RenderNode("cogsapp")
                    renderNode.setPosition(Rect(0, 0, width, height))
                    val paint = Paint()
                    paint.color = Color.argb(255, channel, channel, channel)
                    renderNode.beginRecording(width, height).drawPaint(paint)
                    renderNode.endRecording()
                    renderNode
                }
                x = 0.0
                y = 0.5
                width = 1.0
                height = 0.5
            }
            scene {
                content { data, width, height ->
                    // SurfaceControl blurs don't work unless we draw a transparent buffer.
                    // https://www.youtube.com/watch?v=76p_ncbffCE
                    drawColor(Color.TRANSPARENT, data, width, height)
                }
                properties { data ->
                    blurRegion = Scene.BlurRegion(
                        10.0, 1.0,
                        0.25, 0.25, 0.75, 0.75, 0.0,
                        0.0, 0.0, 0.0
                    )
                }
            }
        }
    }
}