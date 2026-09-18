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

import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

import static com.google.common.truth.Truth.assertThat;

import android.app.EmptyActivity;
import android.app.Flags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;
import android.platform.test.flag.junit.SetFlagsRule;
import android.text.TextUtils;
import android.util.TypedValue;
import android.widget.FrameLayout;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.internal.R;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@RunWith(AndroidJUnit4.class)
@MediumTest
@Presubmit
@EnableFlags(Flags.FLAG_METRIC_VALUE_ALTERNATIVE_STRINGS)
public class NotificationMetricAdaptiveTextViewIntegrationTest {
    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Rule
    public ActivityScenarioRule<EmptyActivity> mScenarioRule = new ActivityScenarioRule<>(
            EmptyActivity.class);

    @Test
    public void draw_avoidsEllipsisUntilUnavoidable() {
        ActivityScenario<EmptyActivity> scenario = mScenarioRule.getScenario();

        scenario.onActivity(activity -> {
            FrameLayout layout = new FrameLayout(activity);
            activity.setContentView(layout);
            NotificationMetricAdaptiveTextView textView = new NotificationMetricAdaptiveTextView(
                    activity);
            textView.setMaxLines(1);
            textView.setId(R.id.text);
            textView.setEllipsize(TextUtils.TruncateAt.END);

            layout.addView(textView, new FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));

            textView.setTextVariants(List.of(
                    "Extremely long text really extremely long",
                    "Still quite long but less so",
                    "Medium size",
                    "Tiny"
            ));
        });

        ConsecutiveUniqueList<SeenText> seenTexts = new ConsecutiveUniqueList<>();

        // We want to test EVERY intermediate width to make sure there's never ellipsizing until
        // the TV is too small to fit any variant. If we jumped steps (say via "i -= 10") we could
        // be unlucky and skip the problematic cases.
        for (int i = 800; i >= 10; i--) {
            int width = i;

            // Force a new expected size.
            scenario.onActivity(activity -> {
                NotificationMetricAdaptiveTextView textView = activity.findViewById(R.id.text);
                textView.setLayoutParams(new FrameLayout.LayoutParams(width, WRAP_CONTENT));
            });

            // Wait for measure/layout/draw.
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            // Check what the chosen text was and whether it was ellipsized.
            scenario.onActivity(activity -> {
                NotificationMetricAdaptiveTextView textView = activity.findViewById(R.id.text);
                seenTexts.add(new SeenText(
                        textView.getText().toString(),
                        textView.getLayout().getEllipsisCount(0) > 0));
            });

            // Wait for SeenText collection.
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        }

        assertThat(seenTexts)
                .containsExactly(
                        new SeenText("Extremely long text really extremely long", false),
                        new SeenText("Still quite long but less so", false),
                        new SeenText("Medium size", false),
                        new SeenText("Tiny", false),
                        new SeenText("Extremely long text really extremely long", true))
                .inOrder();
    }

    @Test
    public void setText_changeFontProperties_choosesBestFitAgain() {
        ActivityScenario<EmptyActivity> scenario = mScenarioRule.getScenario();

        scenario.onActivity(activity -> {
            FrameLayout layout = new FrameLayout(activity);
            activity.setContentView(layout);
            NotificationMetricAdaptiveTextView textView = new NotificationMetricAdaptiveTextView(
                    activity);
            textView.setMaxLines(1);
            textView.setId(R.id.text);
            textView.setEllipsize(TextUtils.TruncateAt.END);

            // Start with enough width to fit long version at normal font size.
            layout.addView(textView, new FrameLayout.LayoutParams(800, WRAP_CONTENT));

            textView.setTextVariants(List.of(
                    "Extremely long text really extremely long",
                    "Tiny"
            ));
        });

        // Wait for measure/layout/draw.
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        scenario.onActivity(activity -> {
            // Verify long string is chosen.
            NotificationMetricAdaptiveTextView textView = activity.findViewById(R.id.text);
            assertThat(textView.getText().toString()).isEqualTo(
                    "Extremely long text really extremely long");

            // Now make the font size larger.
            textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, 60);
        });

        // Wait for measure/layout/draw.
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        scenario.onActivity(activity -> {
            // And verify the short string is now the chosen one.
            NotificationMetricAdaptiveTextView textView = activity.findViewById(R.id.text);
            assertThat(textView.getText().toString()).isEqualTo("Tiny");
        });
    }

    private static class ConsecutiveUniqueList<E> extends ArrayList<E> {
        @Override
        public boolean add(E e) {
            // Only add if the list is empty OR the last element is different.
            if (this.isEmpty() || !Objects.equals(this.get(this.size() - 1), e)) {
                return super.add(e);
            }
            return false;
        }
    }

    private record SeenText(String text, boolean ellipsized) { }
}
