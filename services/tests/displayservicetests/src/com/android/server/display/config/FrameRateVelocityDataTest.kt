/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.server.display.config

import androidx.test.filters.SmallTest

import com.google.common.truth.Truth.assertThat

import org.junit.Test
import org.junit.Assert.assertThrows

@SmallTest
class FrameRateVelocityDataTest {
    @Test
    fun testFrameRateVelocityData_withConfiguration() {
        val displayConfiguration = createDisplayConfiguration {
            frameRateVelocityDataConfig(
                frameRateVelocityMapping = listOf(
                    Pair("120", "1200"),
                    Pair("80", "800"),
                    Pair("60", "600")
                )
            )
        }

        val mappings = FrameRateVelocityData.load(displayConfiguration)
        assertThat(mappings).isNotNull()
        assertThat(mappings.size).isEqualTo(3)
        assertThat(mappings[0].getFramePerSecond()).isEqualTo(60f)
        assertThat(mappings[0].getDpPerSecond()).isEqualTo(600f)
        assertThat(mappings[1].getFramePerSecond()).isEqualTo(80f)
        assertThat(mappings[1].getDpPerSecond()).isEqualTo(800f)
        assertThat(mappings[2].getFramePerSecond()).isEqualTo(120f)
        assertThat(mappings[2].getDpPerSecond()).isEqualTo(1200f)
    }

    @Test
    fun testFrameRateVelocityData_emptyConfiguration() {
        val displayConfiguration = createDisplayConfiguration()

        val mappings = FrameRateVelocityData.load(displayConfiguration)
        assertThat(mappings).isNotNull()
        assertThat(mappings.size).isEqualTo(3)
        assertThat(mappings[0].getFramePerSecond()).isEqualTo(60f)
        assertThat(mappings[0].getDpPerSecond()).isEqualTo(0f)
        assertThat(mappings[1].getFramePerSecond()).isEqualTo(80f)
        assertThat(mappings[1].getDpPerSecond()).isEqualTo(125f)
        assertThat(mappings[2].getFramePerSecond()).isEqualTo(120f)
        assertThat(mappings[2].getDpPerSecond()).isEqualTo(300f)
    }

    @Test
    fun testFrameRateVelocityData_sameDpPerSecond() {
        val displayConfiguration = createDisplayConfiguration {
            frameRateVelocityDataConfig(
                frameRateVelocityMapping = listOf(
                    Pair("120", "800"),
                    Pair("80", "800"),
                    Pair("60", "800")
                )
            )
        }

        assertThrows(IllegalStateException::class.java) {
            FrameRateVelocityData.load(displayConfiguration)
        }
    }

    @Test
    fun testFrameRateVelocityData_reversedOrder() {
        val displayConfiguration = createDisplayConfiguration {
            frameRateVelocityDataConfig(
                frameRateVelocityMapping = listOf(
                    Pair("120", "600"),
                    Pair("80", "800"),
                    Pair("60", "1200")
                )
            )
        }

        assertThrows(IllegalStateException::class.java) {
            FrameRateVelocityData.load(displayConfiguration)
        }
    }

    @Test
    fun testParseFrameRateVelocityMapping_EmptyString() {
        val result = FrameRateVelocityData.parseFrameRateVelocityMapping("")
        assertThat(result).isNotNull()
        assertThat(result.size).isEqualTo(0)
    }

    @Test
    fun testParseFrameRateVelocityMapping_ValidAscending() {
        val ascendingMapping = "60@0:80@125:120@300"
        val result = FrameRateVelocityData.parseFrameRateVelocityMapping(ascendingMapping)
        assertThat(result).isNotNull()
        assertThat(result.size).isEqualTo(3)
        assertThat(result[0].getFramePerSecond()).isEqualTo(60f)
        assertThat(result[0].getDpPerSecond()).isEqualTo(0f)
        assertThat(result[1].getFramePerSecond()).isEqualTo(80f)
        assertThat(result[1].getDpPerSecond()).isEqualTo(125f)
        assertThat(result[2].getFramePerSecond()).isEqualTo(120f)
        assertThat(result[2].getDpPerSecond()).isEqualTo(300f)
    }

    @Test
    fun testParseFrameRateVelocityMapping_ValidDescending() {
        // reordered to ascending by method
        val descendingMapping = "120@300:80@125:60@0"
        val result = FrameRateVelocityData.parseFrameRateVelocityMapping(descendingMapping)
        assertThat(result).isNotNull()
        assertThat(result.size).isEqualTo(3)
        assertThat(result[0].getFramePerSecond()).isEqualTo(60f)
        assertThat(result[0].getDpPerSecond()).isEqualTo(0f)
        assertThat(result[1].getFramePerSecond()).isEqualTo(80f)
        assertThat(result[1].getDpPerSecond()).isEqualTo(125f)
        assertThat(result[2].getFramePerSecond()).isEqualTo(120f)
        assertThat(result[2].getDpPerSecond()).isEqualTo(300f)
    }

    @Test
    fun testParseFrameRateVelocityMapping_ValidWithExtraDelimiters() {
        val result = FrameRateVelocityData.parseFrameRateVelocityMapping(":120@@300:::60@0::80@@@125:")
        assertThat(result).isNotNull()
        assertThat(result.size).isEqualTo(3)
        assertThat(result[0].getFramePerSecond()).isEqualTo(60f)
        assertThat(result[0].getDpPerSecond()).isEqualTo(0f)
        assertThat(result[1].getFramePerSecond()).isEqualTo(80f)
        assertThat(result[1].getDpPerSecond()).isEqualTo(125f)
        assertThat(result[2].getFramePerSecond()).isEqualTo(120f)
        assertThat(result[2].getDpPerSecond()).isEqualTo(300f)
    }

    @Test
    fun testParseFrameRateVelocityMapping_InvalidFormats() {
        // Malformed pair (missing velocity)
        var result = FrameRateVelocityData.parseFrameRateVelocityMapping("60@0:80")
        assertThat(result).isNotNull()
        assertThat(result.size).isEqualTo(0)

        // Malformed pair (missing framerate)
        result = FrameRateVelocityData.parseFrameRateVelocityMapping("@0:80@125")
        assertThat(result).isNotNull()
        assertThat(result.size).isEqualTo(0)
    }
}