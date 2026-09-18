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

package com.android.systemui.surfaceeffects.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.android.systemui.surfaceeffects.core.turbulencenoise.TurbulenceNoiseAnimationConfig
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class TurbulenceNoiseImplTest : SysuiTestCase() {
    @get:Rule val composeTestRule = createComposeRule()
    var isEnabled by mutableStateOf(false)
    val config =
        TurbulenceNoiseAnimationConfig(
            maxDuration = MAX_DURATION,
            fadeInDuration = FADE_IN_DURATION,
            fadeOutDuration = FADE_OUT_DURATION,
        )
    private lateinit var node: TurbulenceNoiseNode

    @Before
    fun setup() {
        composeTestRule.setContent {
            Box(
                modifier =
                    Modifier.size(100.dp)
                        .testTag(TEST_TAG)
                        .simplexNoiseEffect(shaderConfig = config, isEnabled = isEnabled)
            )
        }

        node = composeTestRule.onNode(hasTestTag(TEST_TAG)).fetchSemanticsNode().turbulenceNoise
    }

    @Test
    fun progressAnimatable_autoStopWithMaxDuration() {
        isEnabled = true
        composeTestRule.mainClock.advanceTimeBy(config.fadeInDuration.toLong())
        assertThat(node.rawProgress).isWithin(PROGRESS_TOLERANCE).of(0.25F)
        assertThat(node.fadingProgress).isWithin(PROGRESS_TOLERANCE).of(1F)

        composeTestRule.mainClock.advanceTimeBy(
            (config.maxDuration / 2 - config.fadeInDuration).toLong()
        )
        assertThat(node.rawProgress).isWithin(PROGRESS_TOLERANCE).of(0.5F)
        assertThat(node.fadingProgress).isWithin(PROGRESS_TOLERANCE).of(1F)

        composeTestRule.mainClock.advanceTimeBy((config.maxDuration * 0.375).toLong())
        assertThat(node.rawProgress).isWithin(PROGRESS_TOLERANCE).of(0.875F)
        assertThat(node.fadingProgress).isWithin(PROGRESS_TOLERANCE).of(1F)
    }

    @Test
    fun progressAnimatable_manualFadeOut() {
        isEnabled = true
        composeTestRule.mainClock.advanceTimeBy(config.fadeInDuration.toLong())
        assertThat(node.rawProgress).isWithin(PROGRESS_TOLERANCE).of(0.25F)
        assertThat(node.fadingProgress).isWithin(PROGRESS_TOLERANCE).of(1F)

        composeTestRule.mainClock.advanceTimeBy(
            (config.maxDuration / 2 - config.fadeInDuration).toLong()
        )
        assertThat(node.rawProgress).isWithin(PROGRESS_TOLERANCE).of(0.5F)
        assertThat(node.fadingProgress).isWithin(PROGRESS_TOLERANCE).of(1F)

        isEnabled = false
        composeTestRule.mainClock.advanceTimeBy(config.fadeOutDuration.toLong())
        assertThat(node.rawProgress).isWithin(PROGRESS_TOLERANCE).of(0.75f)
        assertThat(node.fadingProgress).isWithin(PROGRESS_TOLERANCE).of(0f)
    }

    companion object {
        const val PROGRESS_TOLERANCE = 0.03f
        const val MAX_DURATION = 1000F
        const val FADE_IN_DURATION = 250F
        const val FADE_OUT_DURATION = 250F
        const val TEST_TAG = "TurbulenceNoiseTag"
    }
}

private val SemanticsNode.turbulenceNoise: TurbulenceNoiseNode
    get() {
        val turbulenceNoiseNodeElement =
            layoutInfo
                .getModifierInfo()
                .map { it.modifier }
                .filterIsInstance<TurbulenceNoiseNodeElement>()
                .firstOrNull()
        requireNotNull(turbulenceNoiseNodeElement) { "No TurbulenceNoiseNodeElement found." }
        requireNotNull(turbulenceNoiseNodeElement.node) { "No motionTestNode found." }

        return turbulenceNoiseNodeElement.node
    }
