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

import android.graphics.Color
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
import com.android.systemui.surfaceeffects.core.dwellrippleeffect.DwellEffectConfig
import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class DwellEffectImplTest : SysuiTestCase() {
    @get:Rule val composeTestRule = createComposeRule()
    var config by mutableStateOf(DwellEffectConfig())
    private var isExpanding by mutableStateOf(false)
    private lateinit var node: DwellEffectNode

    @Before
    fun setup() {
        composeTestRule.setContent {
            Box(
                modifier =
                    Modifier.size(100.dp)
                        .testTag(TEST_TAG)
                        .dwellEffectImpl(
                            shaderConfig = config,
                            isExpanding = isExpanding,
                            onAnimationFinished = {},
                        )
            )
        }
        node = composeTestRule.onNode(hasTestTag(TEST_TAG)).fetchSemanticsNode().dwellRippleEffect
    }

    @Test
    fun dwellRippleExpandsAndRetracts_fromIntermediateProgress() {
        isExpanding = true
        composeTestRule.mainClock.advanceTimeBy(
            (config.expandingAnimationConfig.duration / 2).toLong()
        )
        assertThat(node.progress).isWithin(PROGRESS_TOLERANCE).of(0.5F)

        isExpanding = false
        composeTestRule.mainClock.advanceTimeBy(
            (config.retractingAnimationConfig.duration / 2).toLong()
        )
        assertThat(node.progress).isWithin(PROGRESS_TOLERANCE).of(0.25F)

        isExpanding = true
        composeTestRule.mainClock.advanceTimeBy((config.expandingAnimationConfig.duration).toLong())
        assertThat(node.progress).isWithin(PROGRESS_TOLERANCE).of(1F)
    }

    @Test
    fun animatableDoesNotRestart_whenRippleConfigUpdates() {
        isExpanding = true
        composeTestRule.mainClock.advanceTimeBy(
            (config.expandingAnimationConfig.duration / 3).toLong()
        )
        assertThat(node.progress).isWithin(PROGRESS_TOLERANCE).of(0.33F)
        config = config.copy(color = Color.RED)

        composeTestRule.mainClock.advanceTimeBy(
            (config.expandingAnimationConfig.duration / 3).toLong()
        )
        assertThat(node.progress).isWithin(PROGRESS_TOLERANCE).of(0.66F)
    }

    companion object {
        const val PROGRESS_TOLERANCE = 0.03f
        const val TEST_TAG = "DwellEffectTag"
    }
}

private val SemanticsNode.dwellRippleEffect: DwellEffectNode
    get() {
        val dwellEffectNodeElement =
            layoutInfo
                .getModifierInfo()
                .map { it.modifier }
                .filterIsInstance<DwellEffectNodeElement>()
                .firstOrNull()
        requireNotNull(dwellEffectNodeElement) { "No DwellEffectNodeElement found." }
        requireNotNull(dwellEffectNodeElement.node) { "No DwellEffectNode found." }

        return dwellEffectNodeElement.node
    }
