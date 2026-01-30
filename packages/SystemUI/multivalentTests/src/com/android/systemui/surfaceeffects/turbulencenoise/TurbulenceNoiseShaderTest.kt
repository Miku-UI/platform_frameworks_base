/*
 * Copyright (C) 2023 The Android Open Source Project
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
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.surfaceeffects.turbulencenoise.TurbulenceNoiseShader.Companion.Type.SIMPLEX_NOISE
import com.android.systemui.surfaceeffects.turbulencenoise.TurbulenceNoiseShader.Companion.Type.SIMPLEX_NOISE_FRACTAL
import com.android.systemui.surfaceeffects.turbulencenoise.TurbulenceNoiseShader.Companion.Type.SIMPLEX_NOISE_SIMPLE
import com.android.systemui.surfaceeffects.turbulencenoise.TurbulenceNoiseShader.Companion.Type.SIMPLEX_NOISE_SPARKLE
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class TurbulenceNoiseShaderTest : SysuiTestCase() {

    @Test
    fun compilesSimplexNoise() {
        TurbulenceNoiseShader(baseType = SIMPLEX_NOISE)
    }

    @Test
    fun compilesSimplexSimpleNoise() {
        TurbulenceNoiseShader(baseType = SIMPLEX_NOISE_SIMPLE)
    }

    @Test
    fun compilesFractalNoise() {
        TurbulenceNoiseShader(baseType = SIMPLEX_NOISE_FRACTAL)
    }

    @Test
    fun compilesSparkleNoise() {
        TurbulenceNoiseShader(baseType = SIMPLEX_NOISE_SPARKLE)
    }

    @Test
    fun simplexSimpleShader_settersDoNotCrash() {
        // Verifies that setting uniforms on the SIMPLEX_NOISE_SIMPLE shader does not crash.
        // This is important as the shader code was modified to use the in_opacity uniform.
        val shader = TurbulenceNoiseShader(baseType = SIMPLEX_NOISE_SIMPLE)

        // Set opacity and other uniforms to ensure the shader's public API is stable.
        shader.setOpacity(0.5f)
        shader.setColor(Color.RED)
        shader.setScreenColor(Color.BLUE)
        shader.setSize(100f, 200f)
        shader.setNoiseMove(0.1f, 0.2f, 0.3f)
        shader.setBackgroundColor(Color.BLACK)
        shader.setGridCount(2.0f)
        shader.setPixelDensity(1.5f)
        shader.setLumaMatteFactors(0.8f, 0.2f)
        shader.setInverseNoiseLuminosity(true)

        // Verify noise offsets are updated correctly as they are the only public state.
        assertThat(shader.noiseOffsetX).isEqualTo(0.1f)
        assertThat(shader.noiseOffsetY).isEqualTo(0.2f)
        assertThat(shader.noiseOffsetZ).isEqualTo(0.3f)
    }

    @Test
    fun applyConfig_setsAllUniforms() {
        // Verifies that the applyConfig convenience method correctly sets all relevant properties
        // and does not crash.
        val shader = TurbulenceNoiseShader(baseType = SIMPLEX_NOISE_SIMPLE)
        val config =
            TurbulenceNoiseAnimationConfig(
                width = 200f,
                height = 300f,
                color = Color.GREEN,
                screenColor = Color.YELLOW,
                pixelDensity = 2.5f,
                gridCount = 2f,
                lumaMatteBlendFactor = 0.5f,
                lumaMatteOverallBrightness = 0.2f,
                shouldInverseNoiseLuminosity = true,
                noiseOffsetX = 1f,
                noiseOffsetY = 2f,
                noiseOffsetZ = 3f
            )

        shader.applyConfig(config)

        // Only noise offsets are publicly readable, so we can verify them.
        // The other setters are verified implicitly by not crashing.
        assertThat(shader.noiseOffsetX).isEqualTo(1f)
        assertThat(shader.noiseOffsetY).isEqualTo(2f)
        assertThat(shader.noiseOffsetZ).isEqualTo(3f)
    }
}
