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

package android.service.personalcontext.insight;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.mock;

import android.app.PendingIntent;
import android.content.Intent;
import android.service.personalcontext.hint.ContextHintTestUtils;
import android.service.personalcontext.hint.InsightReferenceHint;

import androidx.test.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link ActionableInsight}. */
@RunWith(AndroidJUnit4.class)
public class ActionableInsightTest {
    @Test
    public void testPendingIntentAction_ParcelUnparcel() {
        final InsightActionDetails actionDetails =
                new InsightActionDetails.Builder().setPendingIntent(PendingIntent.getBroadcast(
                                InstrumentationRegistry.getTargetContext(), 0,
                                new Intent(), PendingIntent.FLAG_IMMUTABLE))
                        .build();
        final InsightDisplayDetails displayDetails =
                new InsightDisplayDetails.Builder("title")
                        .setContentDescription("content description")
                        .setSubtitle("subtitle")
                        .build();

        final ActionableInsight originalInsight =
                new ActionableInsight.Builder(actionDetails, displayDetails).build();

        final ContextInsight outputInsight =
                ContextInsightTestUtils.assertParcelUnparcel(originalInsight);

        final ActionableInsight outputActionableInsight = (ActionableInsight) outputInsight;

        assertThat(outputActionableInsight.getDisplayDetails())
                .isEqualTo(originalInsight.getDisplayDetails());
        assertThat(outputActionableInsight.getActionDetails())
                .isEqualTo(originalInsight.getActionDetails());
    }

    @Test
    public void testReturnHintReportHasCorrectInsightId() {
        final InsightActionDetails actionDetails =
                new InsightActionDetails.Builder().setPendingIntent(mock(PendingIntent.class))
                        .build();
        final InsightDisplayDetails displayDetails =
                new InsightDisplayDetails.Builder("title")
                        .setContentDescription("content description")
                        .setSubtitle("subtitle")
                        .build();

        final ActionableInsight insight =
                new ActionableInsight.Builder(actionDetails, displayDetails).build();

        InsightReferenceHint hint = ContextHintTestUtils.assertParcelUnparcel(
                insight.createReturnHintReport().getInsightReferenceHint());

        assertThat(hint.getInsightId()).isEqualTo(insight.getInsightId());
    }
}
