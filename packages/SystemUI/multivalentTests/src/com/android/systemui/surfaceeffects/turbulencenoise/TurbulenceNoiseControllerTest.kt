/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.systemui.surfaceeffects.turbulencenoise

import android.graphics.Color
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.surfaceeffects.core.turbulencenoise.TurbulenceNoiseAnimationConfig
import com.android.systemui.surfaceeffects.core.turbulencenoise.TurbulenceNoiseShader.Companion.Type.SIMPLEX_NOISE
import com.android.systemui.surfaceeffects.view.turbulencenoise.TurbulenceNoiseController
import com.android.systemui.surfaceeffects.view.turbulencenoise.TurbulenceNoiseController.Companion.AnimationState.FADE_IN
import com.android.systemui.surfaceeffects.view.turbulencenoise.TurbulenceNoiseController.Companion.AnimationState.FADE_OUT
import com.android.systemui.surfaceeffects.view.turbulencenoise.TurbulenceNoiseController.Companion.AnimationState.MAIN
import com.android.systemui.surfaceeffects.view.turbulencenoise.TurbulenceNoiseController.Companion.AnimationState.NOT_PLAYING
import com.android.systemui.surfaceeffects.view.turbulencenoise.TurbulenceNoiseView
import com.android.systemui.util.concurrency.FakeExecutor
import com.android.systemui.util.time.FakeSystemClock
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class TurbulenceNoiseControllerTest : SysuiTestCase() {
    private val fakeSystemClock = FakeSystemClock()
    // FakeExecutor is needed to run animator.
    private val fakeExecutor = FakeExecutor(fakeSystemClock)

    @Test
    fun play_playsTurbulenceNoiseInOrder() {
        val config = TurbulenceNoiseAnimationConfig(maxDuration = 1000f)
        val turbulenceNoiseView = TurbulenceNoiseView(context, null)
        val turbulenceNoiseController = TurbulenceNoiseController(turbulenceNoiseView)

        assertThat(turbulenceNoiseController.state).isEqualTo(NOT_PLAYING)

        fakeExecutor.execute {
            turbulenceNoiseController.play(SIMPLEX_NOISE, config)

            assertThat(turbulenceNoiseController.state).isEqualTo(FADE_IN)

            fakeSystemClock.advanceTime(config.fadeInDuration.toLong())

            assertThat(turbulenceNoiseController.state).isEqualTo(MAIN)

            fakeSystemClock.advanceTime(config.maxDuration.toLong())

            assertThat(turbulenceNoiseController.state).isEqualTo(FADE_OUT)

            fakeSystemClock.advanceTime(config.fadeOutDuration.toLong())

            assertThat(turbulenceNoiseController.state).isEqualTo(NOT_PLAYING)
        }
    }

    @Test
    fun play_alreadyPlaying_ignoresNewAnimationRequest() {
        val config = TurbulenceNoiseAnimationConfig(maxDuration = 1000f)
        val turbulenceNoiseView = TurbulenceNoiseView(context, null)
        // Currently playing the main animation.
        val turbulenceNoiseController =
            TurbulenceNoiseController(turbulenceNoiseView).also { it.state = MAIN }

        fakeExecutor.execute {
            // Request another animation
            turbulenceNoiseController.play(SIMPLEX_NOISE, config)

            assertThat(turbulenceNoiseController.state).isEqualTo(MAIN)
        }
    }

    @Test
    fun finish_mainAnimationPlaying_playsFadeOutAnimation() {
        val config = TurbulenceNoiseAnimationConfig(maxDuration = 1000f)
        val turbulenceNoiseView = TurbulenceNoiseView(context, null)
        val turbulenceNoiseController =
            TurbulenceNoiseController(turbulenceNoiseView).also { it.state = MAIN }

        fakeExecutor.execute {
            turbulenceNoiseController.play(SIMPLEX_NOISE, config)

            fakeSystemClock.advanceTime(config.maxDuration.toLong() / 2)

            turbulenceNoiseController.finish()

            assertThat(turbulenceNoiseController.state).isEqualTo(FADE_OUT)
        }
    }

    @Test
    fun finish_nonMainAnimationPlaying_doesNotFinishAnimation() {
        val config = TurbulenceNoiseAnimationConfig(maxDuration = 1000f)
        val turbulenceNoiseView = TurbulenceNoiseView(context, null)
        val turbulenceNoiseController =
            TurbulenceNoiseController(turbulenceNoiseView).also { it.state = FADE_IN }

        fakeExecutor.execute {
            turbulenceNoiseController.play(SIMPLEX_NOISE, config)

            fakeSystemClock.advanceTime(config.maxDuration.toLong() / 2)

            turbulenceNoiseController.finish()

            assertThat(turbulenceNoiseController.state).isEqualTo(FADE_IN)
        }
    }

    @Test
    fun onAnimationFinished_resetsStateCorrectly() {
        val config = TurbulenceNoiseAnimationConfig(maxDuration = 1000f)
        val turbulenceNoiseView = TurbulenceNoiseView(context, null)
        val turbulenceNoiseController = TurbulenceNoiseController(turbulenceNoiseView)

        assertThat(turbulenceNoiseController.state).isEqualTo(NOT_PLAYING)
        assertThat(turbulenceNoiseView.visibility).isEqualTo(INVISIBLE)
        assertThat(turbulenceNoiseView.noiseConfig).isNull()

        fakeExecutor.execute {
            turbulenceNoiseController.play(SIMPLEX_NOISE, config)

            assertThat(turbulenceNoiseController.state).isEqualTo(FADE_IN)
            assertThat(turbulenceNoiseView.visibility).isEqualTo(VISIBLE)
            assertThat(turbulenceNoiseView.noiseConfig).isEqualTo(config)

            // Play all the animations.
            fakeSystemClock.advanceTime(
                config.fadeInDuration.toLong() +
                    config.maxDuration.toLong() +
                    config.fadeOutDuration.toLong()
            )

            assertThat(turbulenceNoiseController.state).isEqualTo(NOT_PLAYING)
            assertThat(turbulenceNoiseView.visibility).isEqualTo(INVISIBLE)
            assertThat(turbulenceNoiseView.noiseConfig).isNull()
        }
    }

    @Test
    fun updateColor_updatesCorrectColor() {
        val config = TurbulenceNoiseAnimationConfig(maxDuration = 1000f, color = Color.WHITE)
        val turbulenceNoiseView = TurbulenceNoiseView(context, null)
        val expectedColor = Color.RED

        val turbulenceNoiseController = TurbulenceNoiseController(turbulenceNoiseView)

        fakeExecutor.execute {
            turbulenceNoiseController.play(SIMPLEX_NOISE, config)

            turbulenceNoiseController.updateNoiseColor(expectedColor)

            fakeSystemClock.advanceTime(config.maxDuration.toLong())

            assertThat(config.color).isEqualTo(expectedColor)
        }
    }

    @Test
    fun play_initializesShader() {
        val expectedNoiseOffset = floatArrayOf(0.1f, 0.2f, 0.3f)
        val config =
            TurbulenceNoiseAnimationConfig(
                noiseOffsetX = expectedNoiseOffset[0],
                noiseOffsetY = expectedNoiseOffset[1],
                noiseOffsetZ = expectedNoiseOffset[2],
            )
        val turbulenceNoiseView = TurbulenceNoiseView(context, null)
        val turbulenceNoiseController = TurbulenceNoiseController(turbulenceNoiseView)

        fakeExecutor.execute {
            turbulenceNoiseController.play(SIMPLEX_NOISE, config)

            assertThat(turbulenceNoiseView.noiseConfig).isNotNull()
            val shader = turbulenceNoiseView.turbulenceNoiseShader!!
            assertThat(shader).isNotNull()
            assertThat(shader.noiseOffsetX).isEqualTo(expectedNoiseOffset[0])
            assertThat(shader.noiseOffsetY).isEqualTo(expectedNoiseOffset[1])
            assertThat(shader.noiseOffsetZ).isEqualTo(expectedNoiseOffset[2])
        }
    }
}
