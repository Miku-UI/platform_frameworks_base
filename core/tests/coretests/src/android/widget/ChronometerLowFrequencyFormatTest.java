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

package android.widget;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.platform.test.annotations.Presubmit;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.util.Locale;

/**
 * Tests for {@link ChronometerLowFrequencyFormat}.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
@Presubmit
public class ChronometerLowFrequencyFormatTest {

    @Rule public LocaleRule localeRule = new LocaleRule(Locale.ENGLISH);

    @Test
    public void testFormatChronometerLowFrequency() {
        assertThat(ChronometerLowFrequencyFormat.format(Duration.ZERO, false))
                .isEqualTo("00:--");
        assertThat(ChronometerLowFrequencyFormat.format(Duration.ofSeconds(30), false))
                .isEqualTo("00:--");
        assertThat(ChronometerLowFrequencyFormat.format(Duration.ofSeconds(59), false))
                .isEqualTo("00:--");
        assertThat(ChronometerLowFrequencyFormat.format(Duration.ofMinutes(1), false))
                .isEqualTo("01:--");
        assertThat(ChronometerLowFrequencyFormat.format(Duration.ofSeconds(90), false))
                .isEqualTo("01:--");
        assertThat(ChronometerLowFrequencyFormat.format(Duration.ofMinutes(5), false))
                .isEqualTo("05:--");
        assertThat(ChronometerLowFrequencyFormat.format(Duration.ofHours(1), false))
                .isEqualTo("1:00:--");
        assertThat(ChronometerLowFrequencyFormat.format(
                        Duration.ofHours(1).plusMinutes(1).plusSeconds(1), false))
                .isEqualTo("1:01:--");
    }

    @Test
    public void testFormatAdaptiveLowFrequency() {
        assertThat(ChronometerLowFrequencyFormat.format(Duration.ofSeconds(15), true))
                .isEqualTo("~ 1m");
        assertThat(ChronometerLowFrequencyFormat.format(Duration.ofMinutes(1), true))
                .isEqualTo("1m");
        assertThat(ChronometerLowFrequencyFormat.format(Duration.ofSeconds(90), true))
                .isEqualTo("1m");
        assertThat(ChronometerLowFrequencyFormat.format(Duration.ofMinutes(5), true))
                .isEqualTo("5m");
        assertThat(ChronometerLowFrequencyFormat.format(Duration.ofMinutes(10), true))
                .isEqualTo("10m");
        assertThat(ChronometerLowFrequencyFormat.format(Duration.ofHours(2), true))
                .isEqualTo("2h");
        assertThat(ChronometerLowFrequencyFormat.format(Duration.ofHours(2).plusMinutes(30), true))
                .isEqualTo("2h 30m");
        assertThat(ChronometerLowFrequencyFormat.format(Duration.ofDays(3), true))
                .isEqualTo("3d");
        assertThat(ChronometerLowFrequencyFormat.format(Duration.ofDays(3).plusHours(4), true))
                .isEqualTo("3d 4h");
        assertThat(ChronometerLowFrequencyFormat.format(
                        Duration.ofDays(3).plusHours(4).plusMinutes(5), true))
                .isEqualTo("3d 4h");
    }

    @Test
    public void testFormatAdaptiveVariants() {
        assertThat(ChronometerLowFrequencyFormat.formatVariants(Duration.ofSeconds(15), true))
                .containsExactly("~ 1m");
        assertThat(ChronometerLowFrequencyFormat.formatVariants(Duration.ofSeconds(90), true))
                .containsExactly("1m");
        assertThat(ChronometerLowFrequencyFormat.formatVariants(Duration.ofHours(2), true))
                .containsExactly("2h");
        assertThat(ChronometerLowFrequencyFormat.formatVariants(
                Duration.ofHours(2).plusMinutes(30), true))
                .containsExactly("2h 30m", "2h").inOrder();
        assertThat(ChronometerLowFrequencyFormat.formatVariants(
                Duration.ofDays(3).plusHours(4), true))
                .containsExactly("3d 4h", "3d").inOrder();
    }

    @Test
    public void testFormat_negativeInput() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ChronometerLowFrequencyFormat.format(Duration.ofSeconds(-1), false)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> ChronometerLowFrequencyFormat.format(Duration.ofMinutes(-1), true)
        );
    }
}