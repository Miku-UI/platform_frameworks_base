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

package com.android.internal.widget;

import static android.view.View.MeasureSpec.EXACTLY;
import static android.view.View.MeasureSpec.UNSPECIFIED;
import static android.view.View.MeasureSpec.makeMeasureSpec;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import android.app.Flags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;
import android.platform.test.flag.junit.SetFlagsRule;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.ViewGroup;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;

@RunWith(AndroidJUnit4.class)
@SmallTest
@Presubmit
@EnableFlags(Flags.FLAG_METRIC_VALUE_ALTERNATIVE_STRINGS)
public class NotificationMetricAdaptiveChronometerTest {
    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    private NotificationMetricAdaptiveChronometer mChronometer;

    @Before
    public void setUp() {
        mChronometer = new NotificationMetricAdaptiveChronometer(getInstrumentation().getContext());
        mChronometer.setLayoutParams(new ViewGroup.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
    }

    @Test
    public void tick_adaptiveWithEnoughSpace_usesFullString() {
        mChronometer.setUseAdaptiveFormat(true);
        mChronometer.setPausedDuration(Duration.ofDays(700).plusHours(18));
        assertThat(mChronometer.getText().toString()).isEqualTo("700d 18h");

        mChronometer.measure(makeMeasureSpec(toPx(1000), EXACTLY), makeMeasureSpec(0, UNSPECIFIED));

        assertThat(mChronometer.getText().toString()).isEqualTo("700d 18h");
    }

    @Test
    public void tick_adaptiveWithSmallSpace_usesSmallerString() {
        mChronometer.setUseAdaptiveFormat(true);
        mChronometer.setPausedDuration(Duration.ofDays(700).plusHours(18));
        assertThat(mChronometer.getText().toString()).isEqualTo("700d 18h");

        mChronometer.measure(makeMeasureSpec(toPx(40), EXACTLY), makeMeasureSpec(0, UNSPECIFIED));

        assertThat(mChronometer.getText().toString()).isEqualTo("700d");
    }

    private int toPx(int dips) {
        DisplayMetrics metrics = mChronometer.getContext().getResources().getDisplayMetrics();
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dips, metrics);
    }
}
