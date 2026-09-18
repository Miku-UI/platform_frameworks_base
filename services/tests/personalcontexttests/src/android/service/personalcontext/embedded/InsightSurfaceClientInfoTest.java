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

package android.service.personalcontext.embedded;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.verify;

import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Bundle;
import android.os.RemoteException;
import android.service.personalcontext.hint.BundleHint;
import android.service.personalcontext.hint.PublishedContextHint;
import android.service.personalcontext.insight.BundleInsight;
import android.service.personalcontext.insight.ContextInsight;
import android.service.personalcontext.insight.ContextInsightWrapper;
import android.service.personalcontext.insight.InsightCollection;
import android.view.SurfaceControlViewHost.SurfacePackage;
import android.view.View;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.security.GeneralSecurityException;
import java.util.Random;
import java.util.UUID;

import javax.crypto.spec.SecretKeySpec;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class InsightSurfaceClientInfoTest {
    @Mock private IInsightSurfaceClient mClient;
    @Mock private IInsightSurfaceSession mSession;
    @Mock private SurfacePackage mSurfacePackage;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testInsightSurfaceClientInfoCreation() {
        final UUID clientId = UUID.randomUUID();
        final int widthMeasureSpec = 1;
        final int heightMeasureSpec = 2;
        final int displayId = 3;
        final Color backgroundColor = Color.valueOf(Color.RED);
        final int nestedScrollingAxes = View.SCROLL_AXIS_HORIZONTAL | View.SCROLL_AXIS_VERTICAL;
        final boolean nestedScrollAxisLocked = true;
        final boolean shouldBlur = true;
        final int themeResourceId = 7;
        final String packageName = "package.name";
        final Configuration configuration = new Configuration();

        final InsightSurfaceClientInfo clientInfo =
                new InsightSurfaceClientInfo(
                        clientId,
                        displayId,
                        widthMeasureSpec,
                        heightMeasureSpec,
                        backgroundColor,
                        nestedScrollingAxes,
                        nestedScrollAxisLocked,
                        shouldBlur,
                        themeResourceId,
                        packageName,
                        configuration,
                        mClient);

        assertThat(clientInfo.getId()).isEqualTo(clientId);
        assertThat(clientInfo.getMeasureSpecWidth()).isEqualTo(widthMeasureSpec);
        assertThat(clientInfo.getMeasureSpecHeight()).isEqualTo(heightMeasureSpec);
        assertThat(clientInfo.getDisplayId()).isEqualTo(displayId);
        assertThat(clientInfo.getConfiguration()).isEqualTo(configuration);
        assertThat(clientInfo.getBackgroundColor()).isEqualTo(backgroundColor);
        assertThat(clientInfo.getNestedScrollAxes()).isEqualTo(nestedScrollingAxes);
        assertThat(clientInfo.shouldBlur()).isEqualTo(shouldBlur);
        assertThat(clientInfo.getThemeResourceId()).isEqualTo(themeResourceId);
        assertThat(clientInfo.getPackageName()).isEqualTo(packageName);
        assertThat(clientInfo.getNestedScrollAxisLocked()).isEqualTo(nestedScrollAxisLocked);
    }

    @Test
    public void testOnSurfaceCreated() throws RemoteException {
        final InsightSurfaceClientInfo clientInfo =
                new InsightSurfaceClientInfo(
                        UUID.randomUUID(),
                        0,
                        0,
                        0,
                        Color.valueOf(Color.BLACK),
                        View.SCROLL_AXIS_NONE,
                        false,
                        false,
                        Resources.ID_NULL,
                        "package.name",
                        new Configuration(),
                        mClient);
        clientInfo.onSurfaceCreated(mSurfacePackage, mSession);
        verify(mClient).onSurfaceCreated(mSurfacePackage, mSession);
    }

    @Test
    public void testOnReceiveInsight() throws RemoteException, GeneralSecurityException {
        final String key = "key";
        final String value = "value";
        final String hintKey = "hintKey";
        final String hintValue = "hintValue";

        final InsightSurfaceClientInfo clientInfo =
                new InsightSurfaceClientInfo(
                        UUID.randomUUID(),
                        0,
                        0,
                        0,
                        Color.valueOf(Color.BLACK),
                        View.SCROLL_AXIS_NONE,
                        false,
                        false,
                        Resources.ID_NULL,
                        "package.name",
                        new Configuration(),
                        mClient);

        final Bundle insightData = new Bundle();
        insightData.putString(key, value);
        final Bundle hintData = new Bundle();
        hintData.putString(hintKey, hintValue);

        final BundleHint hint = new BundleHint.Builder().setDataBundle(hintData).build();
        final PublishedContextHint signedHint =
                new PublishedContextHint.Builder(hint, generateSignedHintKey()).build();

        final BundleInsight originalInsight = new BundleInsight.Builder()
                .setDataBundle(insightData).addOriginHint(signedHint).build();
        clientInfo.onReceiveInsight(originalInsight);

        final ArgumentCaptor<ContextInsightWrapper> insightArgumentCaptor =
                ArgumentCaptor.forClass(ContextInsightWrapper.class);
        verify(mClient).onReceiveInsight(insightArgumentCaptor.capture());

        final ContextInsight receivedInsight = insightArgumentCaptor.getValue().getContextInsight();

        assertThat(receivedInsight.getInsightType()).isEqualTo(ContextInsight.INSIGHT_TYPE_BUNDLE);

        final BundleInsight asBundleInsight = (BundleInsight) receivedInsight;
        assertThat(asBundleInsight.getDataBundle().getString(key)).isEqualTo(value);

        // Make sure the origin hints have been stripped.
        assertThat(receivedInsight.getOriginHints()).isEmpty();
    }

    @Test
    public void testOnReceiveCollectionInsight() throws RemoteException, GeneralSecurityException {
        final InsightSurfaceClientInfo clientInfo =
                new InsightSurfaceClientInfo(
                        UUID.randomUUID(),
                        0,
                        0,
                        0,
                        Color.valueOf(Color.BLACK),
                        View.SCROLL_AXIS_NONE,
                        false,
                        false,
                        Resources.ID_NULL,
                        "package.name",
                        new Configuration(),
                        mClient);

        final BundleHint hint1 = new BundleHint.Builder().build();
        final BundleHint hint2 = new BundleHint.Builder().build();

        final PublishedContextHint signedHint1 =
                new PublishedContextHint.Builder(hint1, generateSignedHintKey()).build();
        final PublishedContextHint signedHint2 =
                new PublishedContextHint.Builder(hint2, generateSignedHintKey()).build();

        final BundleInsight insight1 =
                new BundleInsight.Builder().addOriginHint(signedHint1).build();
        final BundleInsight insight2 =
                new BundleInsight.Builder().addOriginHint(signedHint2).build();

        final InsightCollection insightCollection = new InsightCollection.Builder()
                .addInsight(insight1).addInsight(insight2).build();

        clientInfo.onReceiveInsight(insightCollection);

        final ArgumentCaptor<ContextInsightWrapper> insightArgumentCaptor =
                ArgumentCaptor.forClass(ContextInsightWrapper.class);
        verify(mClient).onReceiveInsight(insightArgumentCaptor.capture());

        final ContextInsight receivedInsight = insightArgumentCaptor.getValue().getContextInsight();

        // Make sure the origin hints have been stripped from the received insight.
        assertThat(receivedInsight.getOriginHints()).isEmpty();

        // Make sure the origin hints have been stripped from the insights in the collection.
        final InsightCollection asInsightCollection = (InsightCollection) receivedInsight;
        assertThat(asInsightCollection.getInsights().get(0).getOriginHints()).isEmpty();
        assertThat(asInsightCollection.getInsights().get(1).getOriginHints()).isEmpty();
    }

    @Test
    public void testShouldBlurUpdate() {
        final InsightSurfaceClientInfo clientInfo =
                new InsightSurfaceClientInfo(
                        UUID.randomUUID(),
                        0,
                        0,
                        0,
                        Color.valueOf(Color.BLACK),
                        View.SCROLL_AXIS_NONE,
                        false,
                        false,
                        Resources.ID_NULL,
                        "package.name",
                        new Configuration(),
                        mClient);
        assertThat(clientInfo.shouldBlur()).isFalse();
        final InsightSurfaceClientUpdate update = new InsightSurfaceClientUpdate.Builder()
                .setShouldBlur(true)
                .build();
        final InsightSurfaceClientInfo updatedInfo = clientInfo.createInfoFromUpdate(update);
        assertThat(updatedInfo.shouldBlur()).isTrue();
    }


    /** Generates a key to use when signing hints. */
    private static SecretKeySpec generateSignedHintKey() {
        byte[] key = new byte[64];
        new Random().nextBytes(key);
        return new SecretKeySpec(key, PublishedContextHint.HMAC_ALGORITHM);
    }
}
