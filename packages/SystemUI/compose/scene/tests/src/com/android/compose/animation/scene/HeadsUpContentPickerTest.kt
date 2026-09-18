/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.compose.animation.scene

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.compose.test.transition
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HeadsUpContentPickerTest {
    private val collapsed = SceneKey("collapsed")
    private val expanded = SceneKey("expanded")
    private val other = SceneKey("other")

    private val element = ElementKey("element")
    private val picker = HeadsUpContentPicker(
        sceneWithShadeCollapsed = collapsed,
        sceneWithShadeExpanded = expanded)

    @Test
    fun lowCollapsedShadeToHighExpandedShade_pickExpandedShade() {
        val result = picker.contentDuringTransition(
            element = element,
            transition = transition(from = collapsed, to = expanded),
            fromContentZIndex = 0,
            toContentZIndex = 1
        )

        // Pick scene with expanded shade even if it has higher Z
        assertThat(result).isEqualTo(expanded)
    }

    @Test
    fun lowSceneToHighExpandedShade_pickLow() {
        val result = picker.contentDuringTransition(
            element = element,
            transition = transition(from = other, to = expanded),
            fromContentZIndex = 0,
            toContentZIndex = 1
        )
        assertThat(result).isEqualTo(other)
    }

    @Test
    fun highExpandedShadeToLowScene_pickLow() {
        val result = picker.contentDuringTransition(
            element = element,
            transition = transition(from = expanded, to = other),
            fromContentZIndex = 1,
            toContentZIndex = 0
        )
        assertThat(result).isEqualTo(other)
    }
}
