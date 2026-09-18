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
import com.android.systemui.surfaceeffects.core.ripple.RippleAnimationConfig
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class RippleEffectImplTest : SysuiTestCase() {
    @get:Rule val composeTestRule = createComposeRule()
    var config by mutableStateOf(RippleAnimationConfig())
    lateinit var node: RippleEffectNode
    var isEnabled by mutableStateOf(false)

    @Before
    fun setup() {
        composeTestRule.setContent {
            Box(
                modifier =
                    Modifier.size(100.dp)
                        .testTag(TEST_TAG)
                        .rippleCircleEffect(
                            shaderConfig = config,
                            isEnabled = isEnabled,
                            onAnimationFinished = { isEnabled = false },
                        )
            )
        }
        node = composeTestRule.onNode(hasTestTag(TEST_TAG)).fetchSemanticsNode().rippleEffect
    }

    @Test
    fun rippleProgress_reachesIntermediateAndFinalValues_afterDuration() {
        isEnabled = true

        composeTestRule.mainClock.advanceTimeBy(config.duration / 3)
        assertThat(node.rawProgress).isWithin(PROGRESS_TOLERANCE).of(0.33F)

        composeTestRule.mainClock.advanceTimeBy(config.duration * 2 / 3)
        assertThat(node.rawProgress).isWithin(PROGRESS_TOLERANCE).of(1F)

        composeTestRule.waitForIdle()
        assertThat(isEnabled).isEqualTo(false)
    }

    @Test
    fun isEnabledUnsetsWhenRippleIsEnabled_rippleEffectDoesNotStop() {
        isEnabled = true
        composeTestRule.mainClock.advanceTimeByFrame()
        assertThat(node.rawProgress).isWithin(PROGRESS_TOLERANCE).of(0F)
        composeTestRule.mainClock.advanceTimeBy(config.duration / 3)
        assertThat(node.rawProgress).isWithin(PROGRESS_TOLERANCE).of(0.33F)

        isEnabled = false
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.mainClock.advanceTimeBy(config.duration / 3)
        assertThat(node.rawProgress).isWithin(PROGRESS_TOLERANCE).of(0.67F)
    }

    @Test
    fun isEnabledResetsAfterRippleFinishes_rippleEffectReplays() {
        isEnabled = true
        composeTestRule.mainClock.advanceTimeByFrame()
        assertThat(node.rawProgress).isWithin(PROGRESS_TOLERANCE).of(0F)
        composeTestRule.mainClock.advanceTimeBy(config.duration)
        assertThat(node.rawProgress).isWithin(PROGRESS_TOLERANCE).of(1F)

        isEnabled = false
        composeTestRule.mainClock.advanceTimeByFrame()
        isEnabled = true
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.mainClock.advanceTimeBy(config.duration / 3)
        assertThat(node.rawProgress).isWithin(PROGRESS_TOLERANCE).of(0.33F)
    }

    companion object {
        const val PROGRESS_TOLERANCE = 0.03f
        const val TEST_TAG = "RippleEffectTag"
    }
}

private val SemanticsNode.rippleEffect: RippleEffectNode
    get() {
        val rippleEffectNodeElement =
            layoutInfo
                .getModifierInfo()
                .map { it.modifier }
                .filterIsInstance<RippleEffectNodeElement>()
                .firstOrNull()
        requireNotNull(rippleEffectNodeElement) { "No RippleEffectNodeElement found." }
        requireNotNull(rippleEffectNodeElement.node) { "No RippleEffectNode found." }

        return rippleEffectNodeElement.node
    }
