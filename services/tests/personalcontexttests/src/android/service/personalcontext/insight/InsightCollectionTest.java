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


import android.app.PendingIntent;
import android.content.Intent;
import android.service.personalcontext.Token;
import android.service.personalcontext.hint.BundleHint;
import android.service.personalcontext.hint.ContextHintTestUtils;
import android.service.personalcontext.hint.PublishedContextHint;

import androidx.test.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;


/** Tests for {@link InsightCollection}. */
@RunWith(AndroidJUnit4.class)
public class InsightCollectionTest {
    private PendingIntent mPendingIntent;

    @Before
    public void setUp() {
        mPendingIntent = PendingIntent.getBroadcast(
                InstrumentationRegistry.getTargetContext(), 0, new Intent(),
                PendingIntent.FLAG_IMMUTABLE);
    }

    @Test
    public void testParcelUnparcel_sameInsightType() {
        final InsightActionDetails actionDetails1 =
                new InsightActionDetails.Builder()
                        .setPendingIntent(mPendingIntent)
                        .build();
        final InsightDisplayDetails displayDetails1 =
                new InsightDisplayDetails.Builder("title1")
                        .setContentDescription("content description 1")
                        .build();
        final ActionableInsight actionableInsight1 =
                new ActionableInsight.Builder(actionDetails1, displayDetails1).build();

        final InsightActionDetails actionDetails2 =
                new InsightActionDetails.Builder()
                        .setPendingIntent(mPendingIntent)
                        .build();
        final InsightDisplayDetails displayDetails2 =
                new InsightDisplayDetails.Builder("title2")
                        .setContentDescription("content description 2")
                        .build();
        final ActionableInsight actionableInsight2 =
                new ActionableInsight.Builder(actionDetails2, displayDetails2).build();

        final InsightCollection originalInsight =
                new InsightCollection.Builder()
                        .addInsight(actionableInsight1)
                        .addInsight(actionableInsight2)
                        .build();

        final ContextInsight outputInsight =
                ContextInsightTestUtils.assertParcelUnparcel(originalInsight);

        assertThat(outputInsight).isInstanceOf(InsightCollection.class);
        final InsightCollection outputCollection = (InsightCollection) outputInsight;

        assertThat(outputCollection.getInsights())
                .containsExactly(actionableInsight1, actionableInsight2)
                .inOrder();
        assertThat(outputCollection).isEqualTo(originalInsight);
    }

    @Test
    public void testParcelUnparcel_differentInsightTypes() {
        final InsightActionDetails actionDetails =
                new InsightActionDetails.Builder()
                        .setPendingIntent(mPendingIntent)
                        .build();
        final InsightDisplayDetails displayDetails =
                new InsightDisplayDetails.Builder("title")
                        .setContentDescription("content description")
                        .build();

        final ActionableInsight actionableInsight =
                new ActionableInsight.Builder(actionDetails, displayDetails).build();
        final DisplayInsight displayInsight = new DisplayInsight.Builder(displayDetails).build();

        final InsightCollection originalInsight =
                new InsightCollection.Builder()
                        .addInsight(actionableInsight)
                        .addInsight(displayInsight)
                        .build();

        final ContextInsight outputInsight =
                ContextInsightTestUtils.assertParcelUnparcel(originalInsight);

        assertThat(outputInsight).isInstanceOf(InsightCollection.class);
        final InsightCollection outputCollection = (InsightCollection) outputInsight;

        assertThat(outputCollection.getInsights())
                .containsExactly(actionableInsight, displayInsight)
                .inOrder();
        assertThat(outputCollection).isEqualTo(originalInsight);
    }

    @Test
    public void testIterator() {
        final ActionableInsight actionableInsight =
                new ActionableInsight.Builder(
                                new InsightActionDetails.Builder()
                                        .setPendingIntent(mPendingIntent).build(),
                                new InsightDisplayDetails.Builder("title").build())
                        .build();
        final DisplayInsight displayInsight =
                new DisplayInsight.Builder(new InsightDisplayDetails.Builder("title").build())
                        .build();

        final InsightCollection collection =
                new InsightCollection.Builder()
                        .addInsight(actionableInsight)
                        .addInsight(displayInsight)
                        .build();

        List<ContextInsight> insightsFromIterator = new ArrayList<>();
        for (ContextInsight insight : collection) {
            insightsFromIterator.add(insight);
        }

        assertThat(insightsFromIterator)
                .containsExactly(actionableInsight, displayInsight)
                .inOrder();
    }

    @Test
    public void testBuilder_aggregatesHintsAndTokens() throws Exception {
        final PublishedContextHint signedHint1 =
                new PublishedContextHint.Builder(
                                new BundleHint.Builder().build(),
                                ContextHintTestUtils.generateSignedHintKey())
                        .build();
        final Token token1 = new Token();

        final ActionableInsight actionableInsight =
                new ActionableInsight.Builder(
                                new InsightActionDetails.Builder()
                                        .setPendingIntent(mPendingIntent).build(),
                                new InsightDisplayDetails.Builder("title").build())
                        .addOriginHint(signedHint1)
                        .addToken(token1)
                        .build();

        final PublishedContextHint signedHint2 =
                new PublishedContextHint.Builder(
                                new BundleHint.Builder().build(),
                                ContextHintTestUtils.generateSignedHintKey())
                        .build();
        final Token token2 = new Token();

        final DisplayInsight displayInsight =
                new DisplayInsight.Builder(new InsightDisplayDetails.Builder("title").build())
                        .addOriginHint(signedHint2)
                        .addToken(token2)
                        .build();

        final InsightCollection collection =
                new InsightCollection.Builder()
                        .addInsight(actionableInsight)
                        .addInsight(displayInsight)
                        .build();

        assertThat(collection.getOriginHints()).containsExactly(signedHint1, signedHint2);
        assertThat(collection.getTokens()).containsExactly(token1, token2);
    }
}
