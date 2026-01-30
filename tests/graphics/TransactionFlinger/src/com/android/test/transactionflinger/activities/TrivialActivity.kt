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
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Trivial activity. Not very interesting.
 */
class TrivialActivity : SceneActivity() {
    override fun obtainScene(): Scene {
        return scene {
            content { data, width, height ->

                val animationTime =
                    ((data.preferredFrameTimeline.deadlineNanos - startTime) % 2.seconds.inWholeNanoseconds).nanoseconds

                val red = if (animationTime < 1.seconds) {
                    (animationTime.inWholeMilliseconds * 255.0 / 1.seconds.inWholeMilliseconds).toInt()
                } else {
                    ((2.seconds - animationTime).inWholeMilliseconds * 255.0 / 1.seconds.inWholeMilliseconds).toInt()
                }
                val renderNode = RenderNode("cogsapp")
                renderNode.setPosition(Rect(0, 0, width, height))
                val paint = Paint()
                paint.color = Color.argb(255, red, 0, 0)
                renderNode.beginRecording(width, height).drawPaint(paint)
                renderNode.endRecording()
                renderNode
            }
        }
    }
}