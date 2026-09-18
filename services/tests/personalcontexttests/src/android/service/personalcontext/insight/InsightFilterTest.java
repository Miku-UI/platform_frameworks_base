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

package android.service.personalcontext.insight;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class InsightFilterTest {
    private static final String INSIGHT_CLASS_A =
            "android.service.personalcontext.insight.InsightFilterTest.A";
    private static final String INSIGHT_CLASS_B =
            "android.service.personalcontext.insight.InsightFilterTest.B";

    @Test
    public void testInsightFilterRequireRenderToken() {
        final ContextInsight insight = new BundleInsight.Builder().build();
        final InsightFilter filter = InsightFilter.REQUIRE_RENDER_TOKEN;

        assertThat(filter.isInterestedInInsight(insight)).isFalse();
    }

    @Test
    public void testInsightFilterAll() {
        final ContextInsight insight = new BundleInsight.Builder().build();
        final InsightFilter filter = new InsightFilter.Builder().build();

        assertThat(filter.isInterestedInInsight(insight)).isTrue();
    }

    @Test
    public void testInsightFilterClassMatch() {
        final ContextInsight insight = new BundleInsight.Builder()
                .setInsightTypeName(INSIGHT_CLASS_A)
                .build();
        final InsightFilter filter = new InsightFilter.Builder()
                .addInsightType(INSIGHT_CLASS_A)
                .build();

        assertThat(filter.isInterestedInInsight(insight)).isTrue();
    }

    @Test
    public void testInsightFilterClassNoMatch() {
        final ContextInsight insight = new BundleInsight.Builder()
                .setInsightTypeName(INSIGHT_CLASS_B)
                .build();
        final InsightFilter filter = new InsightFilter.Builder()
                .addInsightType(INSIGHT_CLASS_A)
                .build();

        assertThat(filter.isInterestedInInsight(insight)).isFalse();
    }
}
