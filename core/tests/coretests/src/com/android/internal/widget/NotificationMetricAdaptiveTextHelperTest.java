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

import static com.google.common.truth.Truth.assertThat;

import android.app.Flags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;
import android.platform.test.flag.junit.SetFlagsRule;
import android.text.TextPaint;
import android.view.View;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
@SmallTest
@Presubmit
@EnableFlags(Flags.FLAG_METRIC_VALUE_ALTERNATIVE_STRINGS)
public class NotificationMetricAdaptiveTextHelperTest {
    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    private final NotificationMetricAdaptiveTextHelper mHelper =
            new NotificationMetricAdaptiveTextHelper();
    private final TextPaint mTextPaint = new TextPaint(); // with density = 1.0f by default.

    @Test
    public void chooseReplacement_noAlternatives_noReplacement() {
        mHelper.setTextVariants(List.of("This is the only option"));

        NotificationMetricAdaptiveTextHelper.Replacement replacement =
                mHelper.chooseReplacement(mTextPaint, measureExactly(50), 0);

        assertThat(replacement).isNull();
    }

    @Test
    public void chooseReplacement_enoughSpace_choosesDefault() {
        mHelper.setTextVariants(List.of(
                "Default is quite long", // 108 px, fits
                "Reasonable", // 63 px, also fits but less preferred
                "Tiny" // 23 px, also fits but less preferred
        ));

        NotificationMetricAdaptiveTextHelper.Replacement replacement =
                mHelper.chooseReplacement(mTextPaint, measureExactly(140), 0);

        assertThat(replacement).isNotNull();
        assertThat(replacement.index()).isEqualTo(0);
    }

    @Test
    public void chooseReplacement_notEnoughSpace_choosesFitting() {
        mHelper.setTextVariants(List.of(
                "Default is too long", // 99 px
                "Reasonable", // 63 px, fits
                "Tiny" // 23 px, also fits but less preferred
        ));

        NotificationMetricAdaptiveTextHelper.Replacement replacement =
                mHelper.chooseReplacement(mTextPaint, measureExactly(90), 0);

        assertThat(replacement).isNotNull();
        assertThat(replacement.index()).isEqualTo(1);
    }

    @Test
    public void chooseReplacement_tinySpace_givesUpAndChoosesDefault() {
        mHelper.setTextVariants(List.of(
                "Default is too long", // 99 px
                "Reasonable", // 63 px
                "Tiny" // 23 px
        ));

        NotificationMetricAdaptiveTextHelper.Replacement replacement =
                mHelper.chooseReplacement(mTextPaint, measureExactly(10), 0);

        assertThat(replacement).isNotNull();
        assertThat(replacement.index()).isEqualTo(0);
    }

    @Test
    public void chooseReplacement_considersPadding() {
        mHelper.setTextVariants(List.of(
                "Default is too long", // 99 px
                "Reasonable", // 63 px
                "Tiny" // 23 px
        ));

        NotificationMetricAdaptiveTextHelper.Replacement replacement =
                mHelper.chooseReplacement(mTextPaint, measureExactly(110), 20);

        assertThat(replacement).isNotNull();
        assertThat(replacement.index()).isEqualTo(1);
    }

    private static int measureExactly(int px) {
        return View.MeasureSpec.makeMeasureSpec(px, View.MeasureSpec.EXACTLY);
    }
}
