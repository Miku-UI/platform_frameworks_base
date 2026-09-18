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
import android.content.Context;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;
import android.platform.test.flag.junit.SetFlagsRule;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View.MeasureSpec;
import android.view.View.MeasureSpec.MeasureSpecMode;
import android.view.ViewGroup;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
@SmallTest
@Presubmit
@EnableFlags(Flags.FLAG_METRIC_VALUE_ALTERNATIVE_STRINGS)
public class NotificationMetricAdaptiveTextViewTest {
    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    private NotificationMetricAdaptiveTextView mView;

    @Before
    public void setUp() {
        final Context context = getInstrumentation().getContext();
        mView = new NotificationMetricAdaptiveTextView(context);
        mView.setLayoutParams(new ViewGroup.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
    }

    @Test
    public void setTextVariants_choosesDefault() {
        mView.setTextVariants(List.of("1", "2"));

        assertThat(mView.getText()).isEqualTo("1");
    }

    @Test
    public void setText_overwritesSetTextVariants() {
        mView.setTextVariants(List.of("1", "2"));
        mView.setText("3");

        assertThat(mView.getText()).isEqualTo("3");
    }

    @Test
    public void setTextVariants_overwritesSetText() {
        mView.setText("1");
        mView.setTextVariants(List.of("2", "3"));

        assertThat(mView.getText()).isEqualTo("2");
    }

    @Test
    public void measure_widthUnspecified_choosesFirst() {
        mView.setTextVariants(List.of("Default long", "short"));

        mView.measure(makeMeasureSpec(0, UNSPECIFIED), makeMeasureSpec(0, UNSPECIFIED));

        assertThat(mView.getText().toString()).isEqualTo("Default long");
    }

    @Test
    public void measure_exactly_choosesFitting() {
        measure_withLimitingSpec_choosesFitting(EXACTLY);
    }

    @Test
    public void measure_atMost_choosesFitting() {
        measure_withLimitingSpec_choosesFitting(MeasureSpec.AT_MOST);
    }

    private void measure_withLimitingSpec_choosesFitting(@MeasureSpecMode int specMode) {
        mView.setTextVariants(List.of(
                "Default is too long", // 115 dp
                "Reasonable", // 78 dp, fits
                "Tiny" // 27 dp, also fits but less preferred
        ));

        mView.measure(makeMeasureSpec(toPx(100), specMode), makeMeasureSpec(0, UNSPECIFIED));

        assertThat(mView.getText().toString()).isEqualTo("Reasonable");
    }

    @Test
    public void measure_again_canSwitchToBetter() {
        mView.setTextVariants(List.of(
                "Default is much longer", // 141 dp
                "Reasonable", // 78 dp
                "Tiny" // 27 dp
        ));

        mView.measure(makeMeasureSpec(toPx(50), EXACTLY), makeMeasureSpec(0, UNSPECIFIED));
        assertThat(mView.getText().toString()).isEqualTo("Tiny");

        mView.measure(makeMeasureSpec(toPx(100), EXACTLY), makeMeasureSpec(0, UNSPECIFIED));
        assertThat(mView.getText().toString()).isEqualTo("Reasonable");

        mView.measure(makeMeasureSpec(toPx(180), EXACTLY), makeMeasureSpec(0, UNSPECIFIED));
        assertThat(mView.getText().toString()).isEqualTo("Default is much longer");
    }

    @Test
    public void measure_allVariantsTooLong_choosesDefault() {
        mView.setTextVariants(List.of(
                "Default is huge. It would never fit",
                "Shorter but still too long",
                "Shortest is also long"
        ));

        mView.measure(makeMeasureSpec(toPx(20), EXACTLY), makeMeasureSpec(0, UNSPECIFIED));
        assertThat(mView.getText().toString()).isEqualTo("Default is huge. It would never fit");
    }

    private int toPx(int dips) {
        DisplayMetrics metrics = mView.getContext().getResources().getDisplayMetrics();
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dips, metrics);
    }
}
