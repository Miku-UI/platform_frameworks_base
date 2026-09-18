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

package com.android.server.personalcontext.textclassifier;

import static com.android.server.personalcontext.util.InsightUtils.fakePublishInsight;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.app.PendingIntent;
import android.app.RemoteAction;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.service.personalcontext.hint.ContextHintTestUtils;
import android.service.personalcontext.hint.PublishedContextHint;
import android.service.personalcontext.hint.TextClassificationHint;
import android.service.personalcontext.insight.ActionableInsight;
import android.service.personalcontext.insight.BundleInsight;
import android.service.personalcontext.insight.ContextInsight;
import android.service.personalcontext.insight.InsightActionDetails;
import android.service.personalcontext.insight.InsightCollection;
import android.service.personalcontext.insight.InsightDisplayDetails;
import android.view.textclassifier.TextClassification;

import androidx.test.InstrumentationRegistry;

import com.android.server.textclassifier.personalcontext.PersonalContextBridge;
import com.android.server.textclassifier.personalcontext.PersonalContextBridge.TextClassificationKey;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class TextClassificationActionRendererTest {

    private static final Icon TEST_ICON = Icon.createWithResource("pkg", 123);

    @Mock PersonalContextBridge mPersonalContextBridge;

    private TextClassificationActionRenderer mRenderer;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mRenderer = new TextClassificationActionRenderer(mPersonalContextBridge);
    }

    @Test
    public void testRender_notActionableInsight_noMerge() {
        ContextInsight insight = new BundleInsight.Builder().build();
        mRenderer.render(fakePublishInsight(insight), null);

        verify(mPersonalContextBridge, never()).merge(any(), any());
    }

    @Test
    public void testRender_actionableInsightWithNoTextClassificationHint_noMerge() {
        InsightActionDetails actionDetails =
                new InsightActionDetails.Builder()
                        .setPendingIntent(mock(PendingIntent.class))
                        .build();
        InsightDisplayDetails displayDetails =
                new InsightDisplayDetails.Builder("title", TEST_ICON).build();
        ActionableInsight insight =
                new ActionableInsight.Builder(actionDetails, displayDetails).build();
        mRenderer.render(fakePublishInsight(insight), null);

        verify(mPersonalContextBridge, never()).merge(any(), any());
    }

    @Test
    public void testRender_actionableInsight_merge() throws Exception {
        ActionableInsight insight = buildActionableInsight("title", "sessionId");

        mRenderer.render(fakePublishInsight(insight), null);

        ArgumentCaptor<TextClassificationKey> keyCaptor =
                ArgumentCaptor.forClass(TextClassificationKey.class);
        ArgumentCaptor<TextClassification> responseCaptor =
                ArgumentCaptor.forClass(TextClassification.class);
        verify(mPersonalContextBridge, times(1))
                .merge(keyCaptor.capture(), responseCaptor.capture());

        TextClassificationKey key = keyCaptor.getValue();
        assertThat(key.sessionId()).isEqualTo("sessionId");
        TextClassification response = responseCaptor.getValue();
        assertThat(response.getActions()).hasSize(1);
        RemoteAction action = response.getActions().getFirst();
        assertThat(action.getTitle().toString()).isEqualTo("title");
        assertThat(action.getIcon()).isEqualTo(TEST_ICON);
    }

    @Test
    public void testRender_insightcollection_merge() throws Exception {
        ActionableInsight insight = buildActionableInsight("title", "sessionId");
        InsightCollection insightCollection =
                new InsightCollection.Builder().addInsight(insight).build();

        mRenderer.render(fakePublishInsight(insightCollection), null);

        ArgumentCaptor<TextClassificationKey> keyCaptor =
                ArgumentCaptor.forClass(TextClassificationKey.class);
        ArgumentCaptor<TextClassification> responseCaptor =
                ArgumentCaptor.forClass(TextClassification.class);
        verify(mPersonalContextBridge, times(1))
                .merge(keyCaptor.capture(), responseCaptor.capture());

        TextClassificationKey key = keyCaptor.getValue();
        assertThat(key.sessionId()).isEqualTo("sessionId");
        TextClassification response = responseCaptor.getValue();
        assertThat(response.getActions()).hasSize(1);
        RemoteAction action = response.getActions().getFirst();
        assertThat(action.getTitle().toString()).isEqualTo("title");
        assertThat(action.getIcon()).isEqualTo(TEST_ICON);
    }

    private static ActionableInsight buildActionableInsight(String title, String sessionId)
            throws Exception {
        TextClassificationHint hint =
                new TextClassificationHint.Builder(
                                new TextClassification.Request.Builder("test-text", 0, 4).build(),
                                sessionId)
                        .build();
        InsightActionDetails actionDetails =
                new InsightActionDetails.Builder()
                        .setRemoteAction(
                                new RemoteAction(
                                        TEST_ICON,
                                        title,
                                        "content-description",
                                        PendingIntent.getBroadcast(
                                                InstrumentationRegistry.getTargetContext(),
                                                0,
                                                new Intent(),
                                                PendingIntent.FLAG_IMMUTABLE)))
                        .build();
        InsightDisplayDetails displayDetails =
                new InsightDisplayDetails.Builder(title, TEST_ICON).build();
        ActionableInsight insight =
                new ActionableInsight.Builder(actionDetails, displayDetails)
                        .addOriginHint(
                                new PublishedContextHint.Builder(
                                                hint, ContextHintTestUtils.generateSignedHintKey())
                                        .build())
                        .build();
        return insight;
    }
}
