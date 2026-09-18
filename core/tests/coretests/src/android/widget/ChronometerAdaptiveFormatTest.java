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
 * Tests for {@link ChronometerAdaptiveFormat}.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
@Presubmit
public class ChronometerAdaptiveFormatTest {
    @Rule public LocaleRule localeRule = new LocaleRule(Locale.ENGLISH);

    @Test
    public void format_daysAndHours() {
        Duration duration = Duration.ofDays(2).plusHours(3).plusMinutes(4);
        assertThat(ChronometerAdaptiveFormat.format(duration)).isEqualTo("2d 3h");
    }

    @Test
    public void format_hoursAndMinutes() {
        Duration duration = Duration.ofHours(3).plusMinutes(4).plusSeconds(5);
        assertThat(ChronometerAdaptiveFormat.format(duration)).isEqualTo("3h 4m");
    }

    @Test
    public void format_minutesAndSeconds() {
        // Minutes and Seconds
        Duration duration = Duration.ofMinutes(4).plusSeconds(30);
        assertThat(ChronometerAdaptiveFormat.format(duration)).isEqualTo("4m");

        duration = Duration.ofMinutes(4).plusSeconds(5);
        assertThat(ChronometerAdaptiveFormat.format(duration)).isEqualTo("4m");

        duration = Duration.ofMinutes(3).plusSeconds(5);
        assertThat(ChronometerAdaptiveFormat.format(duration)).isEqualTo("3m");

        duration = Duration.ofMinutes(2).plusSeconds(5);
        assertThat(ChronometerAdaptiveFormat.format(duration)).isEqualTo("2m 5s");

        duration = Duration.ofMinutes(2);
        assertThat(ChronometerAdaptiveFormat.format(duration)).isEqualTo("2m 0s");
    }

    @Test
    public void format_seconds() {
        Duration duration = Duration.ofSeconds(5);
        assertThat(ChronometerAdaptiveFormat.format(duration)).isEqualTo("5s");
    }

    @Test
    public void format_withZeroSeconds() {
        Duration duration = Duration.ZERO;
        assertThat(ChronometerAdaptiveFormat.format(duration)).isEqualTo("0s");

        duration = Duration.ofMinutes(4);
        assertThat(ChronometerAdaptiveFormat.format(duration)).isEqualTo("4m");

        duration = Duration.ofMinutes(3);
        assertThat(ChronometerAdaptiveFormat.format(duration)).isEqualTo("3m");

        duration = Duration.ofMinutes(2);
        assertThat(ChronometerAdaptiveFormat.format(duration)).isEqualTo("2m 0s");

        duration = Duration.ofMinutes(1);
        assertThat(ChronometerAdaptiveFormat.format(duration)).isEqualTo("1m 0s");
    }

    @Test
    public void formatVariants_withVariants() {
        Duration daysAndHours = Duration.ofDays(2).plusHours(3).plusMinutes(4).plusSeconds(30);
        assertThat(ChronometerAdaptiveFormat.formatVariants(daysAndHours))
                .containsExactly("2d 3h", "2d").inOrder();

        Duration hoursAndMinutes = Duration.ofHours(3).plusMinutes(4).plusSeconds(30);
        assertThat(ChronometerAdaptiveFormat.formatVariants(hoursAndMinutes))
                .containsExactly("3h 4m", "3h").inOrder();
    }

    @Test
    public void formatVariants_withoutVariants() {
        Duration daysAndHours = Duration.ofDays(2);
        assertThat(ChronometerAdaptiveFormat.formatVariants(daysAndHours))
                .containsExactly("2d").inOrder();

        Duration hoursAndMinutes = Duration.ofHours(3);
        assertThat(ChronometerAdaptiveFormat.formatVariants(hoursAndMinutes))
                .containsExactly("3h").inOrder();
    }

    @Test
    public void format_negative_throws() {
        // Negative time
        Duration duration = Duration.ofSeconds(-5);
        assertThrows(IllegalArgumentException.class,
                () -> ChronometerAdaptiveFormat.format(duration));
    }
}
