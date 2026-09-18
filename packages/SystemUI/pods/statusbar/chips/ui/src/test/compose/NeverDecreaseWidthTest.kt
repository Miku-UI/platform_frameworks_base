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

package com.android.systemui.statusbar.chips.ui.compose

import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.systemui.SysuiTestCase
import com.google.common.truth.Truth.assertThat
import java.util.Locale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class NeverDecreaseWidthTest : SysuiTestCase() {
    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun neverDecreaseWidth_widthIncreases_whenContentSizeIncreases() {
        val text = mutableStateOf("....")

        composeTestRule.setContent {
            Text(
                text = text.value,
                modifier =
                    Modifier.testTag(TEST_TAG)
                        .neverDecreaseWidth(
                            density = DEFAULT_DENSITY,
                            locale = DEFAULT_LOCALE,
                            textLength = text.value.length,
                        ),
            )
        }

        composeTestRule.onNodeWithTag(TEST_TAG).assertWidthIsAtLeast(1.dp)
        var previousWidth = getWidth()

        // WHEN the text updates to a wider string ("w" is wider than ".")
        text.value = "...w"

        // THEN the width increases
        assertThat(getWidth()).isGreaterThan(previousWidth)

        previousWidth = getWidth()
        text.value = "..ww"
        assertThat(getWidth()).isGreaterThan(previousWidth)

        previousWidth = getWidth()
        text.value = ".www"
        assertThat(getWidth()).isGreaterThan(previousWidth)

        previousWidth = getWidth()
        text.value = "wwww"
        assertThat(getWidth()).isGreaterThan(previousWidth)
    }

    @Test
    fun neverDecreaseWidth_minWidthDoesNotChange_whenContentSizeDecreases() {
        val text = mutableStateOf("wwww")

        composeTestRule.setContent {
            Text(
                text = text.value,
                modifier =
                    Modifier.testTag(TEST_TAG)
                        .neverDecreaseWidth(
                            density = DEFAULT_DENSITY,
                            locale = DEFAULT_LOCALE,
                            textLength = text.value.length,
                        ),
            )
        }

        composeTestRule.onNodeWithTag(TEST_TAG).assertWidthIsAtLeast(1.dp)
        var previousWidth = getWidth()

        // WHEN the text updates to a narrower string ("." is less wide than "w")
        text.value = "www."

        // THEN the width stays the same
        assertThat(getWidth()).isEqualTo(previousWidth)

        previousWidth = getWidth()
        text.value = "ww.."
        assertThat(getWidth()).isEqualTo(previousWidth)

        previousWidth = getWidth()
        text.value = "w..."
        assertThat(getWidth()).isEqualTo(previousWidth)

        previousWidth = getWidth()
        text.value = "...."
        assertThat(getWidth()).isEqualTo(previousWidth)
    }

    @Test
    fun neverDecreaseWidth_minWidthResets_onLocaleChange() {
        val text = mutableStateOf("wwww")
        val locale = mutableStateOf(Locale.ENGLISH)

        composeTestRule.setContent {
            Text(
                text = text.value,
                modifier =
                    Modifier.testTag(TEST_TAG)
                        .neverDecreaseWidth(
                            density = DEFAULT_DENSITY,
                            locale = locale.value,
                            textLength = text.value.length,
                        ),
            )
        }

        composeTestRule.onNodeWithTag(TEST_TAG).assertWidthIsAtLeast(1.dp)
        val previousWidth = getWidth()

        // First, update to a narrower string and ensure the minWidth is maintained
        text.value = "...."
        assertThat(getWidth()).isEqualTo(previousWidth)

        // WHEN the locale changes
        locale.value = Locale.CANADA_FRENCH

        // THEN the minimum width is reset to a smaller value
        assertThat(getWidth()).isLessThan(previousWidth)
    }

    @Test
    fun neverDecreaseWidth_minWidthResets_onDensityChange() {
        val text = mutableStateOf("wwww")
        val density = mutableStateOf(Density(density = 160f, fontScale = 4f))

        composeTestRule.setContent {
            Text(
                text = text.value,
                modifier =
                    Modifier.testTag(TEST_TAG)
                        .neverDecreaseWidth(
                            density = density.value,
                            locale = DEFAULT_LOCALE,
                            textLength = text.value.length,
                        ),
            )
        }

        composeTestRule.onNodeWithTag(TEST_TAG).assertWidthIsAtLeast(1.dp)
        val previousWidth = getWidth()

        // First, update to a narrower string and ensure the minWidth is maintained
        text.value = "...."
        assertThat(getWidth()).isEqualTo(previousWidth)

        // WHEN the font size decreases
        density.value = Density(density = 160f, fontScale = 1f)

        // THEN the minimum width is reset to a smaller value
        assertThat(getWidth()).isLessThan(previousWidth)
    }

    @Test
    fun neverDecreaseWidth_minWidthResets_onNumCharactersChange() {
        val text = mutableStateOf("wwww")

        composeTestRule.setContent {
            Text(
                text = text.value,
                modifier =
                    Modifier.testTag(TEST_TAG)
                        .neverDecreaseWidth(
                            density = DEFAULT_DENSITY,
                            locale = DEFAULT_LOCALE,
                            textLength = text.value.length,
                        ),
            )
        }

        composeTestRule.onNodeWithTag(TEST_TAG).assertWidthIsAtLeast(1.dp)
        val previousWidth = getWidth()

        // First, update to a narrower string and ensure the minWidth is maintained
        text.value = "...."
        assertThat(getWidth()).isEqualTo(previousWidth)

        // WHEN the string has fewer characters
        text.value = "..."

        // THEN the minimum width is reset to a smaller value
        assertThat(getWidth()).isLessThan(previousWidth)
    }

    companion object {
        private const val TEST_TAG = "testText"
        private val DEFAULT_DENSITY = Density(density = 160f, fontScale = 1f)
        private val DEFAULT_LOCALE = Locale.ENGLISH
    }

    private fun getWidth(): Dp {
        return composeTestRule.onNodeWithTag(TEST_TAG).getBoundsInRoot().width
    }
}
