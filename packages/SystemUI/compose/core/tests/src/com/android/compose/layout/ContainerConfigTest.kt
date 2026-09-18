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

package com.android.compose.layout

import android.graphics.Point
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ContainerConfigTest {

    companion object {
        val DEFAULT_CONTAINER_CONFIG =
            ContainerConfig(
                sizeFraction = 0.5f,
                minLongEdge = 900.dp,
                minShortEdge = 700.dp,
                maxLongEdge = 1200.dp,
                maxShortEdge = 850.dp,
            )

        val INFINITE_CONSTRAINTS =
            Constraints(
                minWidth = 0,
                maxWidth = Constraints.Infinity,
                minHeight = 0,
                maxHeight = Constraints.Infinity,
            )
    }

    // Use a density of 1.0f to make Dp to Px conversion straightforward (1.dp = 1px)
    val testDensity = Density(density = 1.0f)

    // --- Tests for ContainerLayout.HORIZONTAL ---

    @Test
    fun calculateContainerPlacement_horizontal_appliesFraction_withinBounds() {
        val config = DEFAULT_CONTAINER_CONFIG
        val inputConstraints = Constraints.fixed(2000, 1600)
        val result =
            config.calculateContainerPlacement(
                testDensity,
                inputConstraints,
                ContainerLayout.HORIZONTAL,
                bottomInset = 0,
                topInset = 0,
            )
        // Expected width: 1000 (2000 * 0.5), within (900, 1200)
        // Expected height: 800 (1600 * 0.5), within (700, 850)
        // Expected offset: Point(500, 400) (Centered: (2000-1000)/2, (1600-800)/2)
        assertThat(result.size).isEqualTo(IntSize(1000, 800))
        assertThat(result.offset).isEqualTo(Point(500, 400))
    }

    @Test
    fun calculateContainerPlacement_horizontal_coercesToMinBounds() {
        val config = DEFAULT_CONTAINER_CONFIG
        val inputConstraints = Constraints.fixed(1400, 1000)
        val result =
            config.calculateContainerPlacement(
                testDensity,
                inputConstraints,
                ContainerLayout.HORIZONTAL,
                bottomInset = 0,
                topInset = 0,
            )
        // Expected width: 900 (1400 * 0.5 = 700, coerced to minLongEdge 900)
        // Expected height: 700 (1000 * 0.5 = 500, coerced to minShortEdge 700)
        // Expected offset: Point(250, 150) (Centered: (1400-900)/2, (1000-700)/2)
        assertThat(result.size).isEqualTo(IntSize(900, 700))
        assertThat(result.offset).isEqualTo(Point(250, 150))
    }

    @Test
    fun calculateContainerPlacement_horizontal_coercesToMaxBounds() {
        val config = DEFAULT_CONTAINER_CONFIG
        val inputConstraints = Constraints.fixed(2500, 1800)
        val result =
            config.calculateContainerPlacement(
                testDensity,
                inputConstraints,
                ContainerLayout.HORIZONTAL,
                bottomInset = 0,
                topInset = 0,
            )
        // Expected width: 1200 (2500 * 0.5 = 1250, coerced to maxLongEdge 1200)
        // Expected height: 850 (1800 * 0.5 = 900, coerced to maxShortEdge 850)
        // Expected offset: Point(650, 475) (Centered: (2500-1200)/2, (1800-850)/2)
        assertThat(result.size).isEqualTo(IntSize(1200, 850))
        assertThat(result.offset).isEqualTo(Point(650, 475))
    }

    @Test
    fun calculateContainerPlacement_horizontal_cappedByInputConstraints() {
        val config = DEFAULT_CONTAINER_CONFIG
        val inputConstraints = Constraints.fixed(800, 600)
        val result =
            config.calculateContainerPlacement(
                testDensity,
                inputConstraints,
                ContainerLayout.HORIZONTAL,
                bottomInset = 0,
                topInset = 0,
            )
        // Expected width: 800 (min(900, 800))
        // Expected height: 600 (min(700, 600))
        // Expected offset: Point(0, 0) ((800-800)/2, (600-600)/2)
        assertThat(result.size).isEqualTo(IntSize(800, 600))
        assertThat(result.offset).isEqualTo(Point(0, 0))
    }

    @Test
    fun calculateContainerPlacement_horizontal_infiniteInputConstraints_returnsZeroOffset() {
        val config = DEFAULT_CONTAINER_CONFIG
        val result =
            config.calculateContainerPlacement(
                testDensity,
                INFINITE_CONSTRAINTS,
                ContainerLayout.HORIZONTAL,
                bottomInset = 0,
                topInset = 0,
            )
        // Expected width: 1200 (maxLongEdge)
        // Expected height: 850 (maxShortEdge)
        // Expected offset: Point(0, 0) (Unbounded constraints)
        assertThat(result.size).isEqualTo(IntSize(1200, 850))
        assertThat(result.offset).isEqualTo(Point(0, 0))
    }

    // --- Tests for ContainerLayout.VERTICAL ---

    @Test
    fun calculateContainerPlacement_vertical_appliesFraction_withinBounds() {
        val config = DEFAULT_CONTAINER_CONFIG
        val inputConstraints = Constraints.fixed(1600, 2000)
        val result =
            config.calculateContainerPlacement(
                testDensity,
                inputConstraints,
                ContainerLayout.VERTICAL,
                bottomInset = 0,
                topInset = 0,
            )
        // Expected width: 800 (1600 * 0.5), within (700, 850)
        // Expected height: 1000 (2000 * 0.5), within (900, 1200)
        // Expected offset: Point(400, 500) (Centered: (1600-800)/2, (2000-1000)/2)
        assertThat(result.size).isEqualTo(IntSize(800, 1000))
        assertThat(result.offset).isEqualTo(Point(400, 500))
    }

    @Test
    fun calculateContainerPlacement_vertical_coercesToMinBounds() {
        val config = DEFAULT_CONTAINER_CONFIG
        val inputConstraints = Constraints.fixed(1000, 1400)
        val result =
            config.calculateContainerPlacement(
                testDensity,
                inputConstraints,
                ContainerLayout.VERTICAL,
                bottomInset = 0,
                topInset = 0,
            )
        // Expected width: 700 (1000 * 0.5 = 500, coerced to minShortEdge 700)
        // Expected height: 900 (1400 * 0.5 = 700, coerced to minLongEdge 900)
        // Expected offset: Point(150, 250) (Centered: (1000-700)/2, (1400-900)/2)
        assertThat(result.size).isEqualTo(IntSize(700, 900))
        assertThat(result.offset).isEqualTo(Point(150, 250))
    }

    @Test
    fun calculateContainerPlacement_vertical_coercesToMaxBounds() {
        val config = DEFAULT_CONTAINER_CONFIG
        val inputConstraints = Constraints.fixed(1800, 2500)
        val result =
            config.calculateContainerPlacement(
                testDensity,
                inputConstraints,
                ContainerLayout.VERTICAL,
                bottomInset = 0,
                topInset = 0,
            )
        // Expected width: 850 (1800 * 0.5 = 900, coerced to maxShortEdge 850)
        // Expected height: 1200 (2500 * 0.5 = 1250, coerced to maxLongEdge 1200)
        // Expected offset: Point(475, 650) (Centered: (1800-850)/2, (2500-1200)/2)
        assertThat(result.size).isEqualTo(IntSize(850, 1200))
        assertThat(result.offset).isEqualTo(Point(475, 650))
    }

    // --- Tests for Offset Calculation with Insets ---

    @Test
    fun calculateContainerPlacement_bigBottomInset_capsSizeAndShiftsToTop() {
        val config = DEFAULT_CONTAINER_CONFIG
        val inputConstraints = Constraints.fixed(2000, 1600)
        val result =
            config.calculateContainerPlacement(
                testDensity,
                inputConstraints,
                ContainerLayout.HORIZONTAL,
                bottomInset = 1000,
                topInset = 0,
            )
        // Expected width: 1000 (2000 * 0.5)
        // Expected height: 600 (coerced to maxHeight - inset: 1600 - 1000))
        // Expected offset: Point(500, 0) (height + inset = maxHeight)
        assertThat(result.size).isEqualTo(IntSize(1000, 600))
        assertThat(result.offset).isEqualTo(Point(500, 0))
    }

    @Test
    fun calculateContainerPlacement_bottomInset_shiftsContainerUp() {
        val config = DEFAULT_CONTAINER_CONFIG
        val inputConstraints = Constraints.fixed(2000, 1600)
        val result =
            config.calculateContainerPlacement(
                testDensity,
                inputConstraints,
                ContainerLayout.HORIZONTAL,
                bottomInset = 500,
                topInset = 0,
            )
        // Expected width: 1000 (2000 * 0.5)
        // Expected height: 800 (1600 * 0.5)
        // Expected offset: Point(500, 300) (offset + height + inset = maxHeight)
        assertThat(result.size).isEqualTo(IntSize(1000, 800))
        assertThat(result.offset).isEqualTo(Point(500, 300))
    }

    @Test
    fun calculateContainerPlacement_topInset_shiftsContainerDown() {
        val config = DEFAULT_CONTAINER_CONFIG
        val inputConstraints = Constraints.fixed(2000, 1600)
        val result =
            config.calculateContainerPlacement(
                testDensity,
                inputConstraints,
                ContainerLayout.HORIZONTAL,
                bottomInset = 0,
                topInset = 500,
            )
        // Expected width: 1000 (2000 * 0.5)
        // Expected height: 800 (1600 * 0.5)
        // Expected offset: Point(500, 500) (Shifted down to topInset: 500)
        assertThat(result.size).isEqualTo(IntSize(1000, 800))
        assertThat(result.offset).isEqualTo(Point(500, 500))
    }

    @Test
    fun calculateContainerPlacement_bigBothInsets_fitsBetween() {
        val config = DEFAULT_CONTAINER_CONFIG
        val inputConstraints = Constraints.fixed(2000, 1600)
        val result =
            config.calculateContainerPlacement(
                testDensity,
                inputConstraints,
                ContainerLayout.HORIZONTAL,
                bottomInset = 700,
                topInset = 300,
            )
        // Expected width: 1000 (2000 * 0.5)
        // Expected height: 600 (min(800, 1600 - 1000))
        // Expected offset: Point(500, 300) (Fits within insets)
        assertThat(result.size).isEqualTo(IntSize(1000, 600))
        assertThat(result.offset).isEqualTo(Point(500, 300))
    }

    @Test
    fun calculateContainerPlacement_smallBothInsets_staysCentered() {
        val config = DEFAULT_CONTAINER_CONFIG
        val inputConstraints = Constraints.fixed(2000, 1600)
        val result =
            config.calculateContainerPlacement(
                testDensity,
                inputConstraints,
                ContainerLayout.HORIZONTAL,
                bottomInset = 60,
                topInset = 40,
            )
        // Expected width: 1000 (2000 * 0.5)
        // Expected height: 800 (1600 * 0.5)
        // Expected offset: Point(500, 400) (Centered)
        assertThat(result.size).isEqualTo(IntSize(1000, 800))
        assertThat(result.offset).isEqualTo(Point(500, 400))
    }
}
