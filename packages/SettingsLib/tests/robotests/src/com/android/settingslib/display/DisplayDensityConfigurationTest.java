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

package com.android.settingslib.display;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class DisplayDensityConfigurationTest {

    @Test
    public void calculateBaseDensity_withValidDpi_returnsCorrectDensity() {
        // Case where xDpi and yDpi are valid (100)
        // ppi = 100
        // pixels = 100 * (10.4 / 25.4) = 40.94488
        // dpi = 40.94488 * 160 / 48 = 136.4829
        // return (int) (136.4829 + 0.5) = 136
        assertThat(DisplayDensityConfiguration.calculateBaseDensity(100, 100, 1920, 1080))
                .isEqualTo(136);
    }

    @Test
    public void calculateBaseDensity_withInvalidDpi_usesResolutionBasedPpi() {
        // Case where xDpi and yDpi are 0, uses width/height
        // ppi = sqrt(1920^2 + 1080^2) / 24 = 2202.907 / 24 = 91.7878
        // pixels = 91.7878 * (10.4 / 25.4) = 37.5824
        // dpi = 37.5824 * 160 / 48 = 125.2747
        // return (int) (125.2747 + 0.5) = 125
        assertThat(DisplayDensityConfiguration.calculateBaseDensity(0, 0, 1920, 1080))
                .isEqualTo(125);
    }

    @Test
    public void calculateBaseDensity_withSmallDisplay_returnsMinDensity() {
        // Case where calculated density is below min density (100)
        // ppi = 50
        // pixels = 50 * (10.4 / 25.4) = 20.47244
        // dpi = 20.47244 * 160 / 48 = 68.2414
        // return max((int) (68.2414 + 0.5), 100) = 100
        assertThat(DisplayDensityConfiguration.calculateBaseDensity(50, 50, 640, 480))
                .isEqualTo(100);
    }

    @Test
    public void calculateBaseDensity_withDifferentXYDpi_returnsCorrectDensity() {
        // ppi = sqrt((120^2 + 80^2) / 2) = sqrt((14400 + 6400) / 2) = sqrt(10400) = 101.98039
        // pixels = 101.98039 * (10.4 / 25.4) = 41.7562
        // dpi = 41.7562 * 160 / 48 = 139.187
        // return (int) (139.187 + 0.5) = 139
        assertThat(DisplayDensityConfiguration.calculateBaseDensity(120, 80, 1920, 1080))
                .isEqualTo(139);
    }

    @Test
    public void calculateBaseDensity_atMinDensityBoundary_returnsMinDensity() {
        // We want calculated dpi to be exactly 100 or slightly below/above.
        // dpi = ppi * (10.4 / 25.4) * (160 / 48) = ppi * 1.364829
        // If ppi = 73.269, dpi = 100.00
        // If ppi = 73, dpi = 73 * 1.364829 = 99.63
        // (int) (99.63 + 0.5) = 100
        assertThat(DisplayDensityConfiguration.calculateBaseDensity(73, 73, 1920, 1080))
                .isEqualTo(100);

        // If ppi = 72, dpi = 72 * 1.364829 = 98.267
        // (int) (98.267 + 0.5) = 98
        // max(98, 100) = 100
        assertThat(DisplayDensityConfiguration.calculateBaseDensity(72, 72, 1920, 1080))
                .isEqualTo(100);
    }
}
