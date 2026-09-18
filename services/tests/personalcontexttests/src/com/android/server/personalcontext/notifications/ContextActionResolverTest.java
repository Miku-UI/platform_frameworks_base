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

package com.android.server.personalcontext.notifications;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.annotation.NonNull;
import android.app.PendingIntent;
import android.app.RemoteAction;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.service.personalcontext.insight.ActionableInsight;
import android.service.personalcontext.insight.InsightActionDetails;
import android.service.personalcontext.insight.InsightDisplayDetails;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.server.personalcontext.notifications.ContextActionResolver.ActionType;
import com.android.server.personalcontext.notifications.ContextActionResolver.ResolutionResult;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class ContextActionResolverTest {

    private static final Icon mIcon =
            Icon.createWithBitmap(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888));
    private static final InsightDisplayDetails mDisplayDetails =
            new InsightDisplayDetails.Builder("Test", mIcon).build();

    @Mock private Context mContext;
    @Mock private PackageManager mPackageManager;
    @Mock private PendingIntent mPendingIntent;
    private PendingIntentFactory mPendingIntentFactory;

    private ActionableInsight mActionableInsight;
    private ContextActionResolver mUnderTest;
    private Intent mTestIntent;
    private ResolveInfo mTestResolveInfo;

    private class FakePendingIntentFactory implements PendingIntentFactory {
        @Override
        public PendingIntent create(
                @NonNull Context context,
                int requestCode,
                @NonNull Intent intent,
                int flags,
                @NonNull ActionType actionType) {
            return mPendingIntent;
        }
    }

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mPendingIntentFactory = new FakePendingIntentFactory();
        mUnderTest = new ContextActionResolver(mContext, mPackageManager, mPendingIntentFactory);

        mTestIntent = new Intent("TEST_ACTION");
        mTestResolveInfo = new ResolveInfo();
    }

    @Test
    public void resolveActionIntent_remoteActionResolvesToActivity_returnsCorrectResult() {
        setUpRemoteAction(ActionType.ACTIVITY, /* hasResolveInfo= */ true);

        ResolutionResult result = mUnderTest.resolveActionIntent(mActionableInsight, true);

        assertRemoteActionResult(result, ActionType.ACTIVITY);
    }

    @Test
    public void resolveActionIntent_remoteActionResolvesToService_returnsCorrectResult() {
        setUpRemoteAction(ActionType.SERVICE, /* hasResolveInfo= */ true);

        ResolutionResult result = mUnderTest.resolveActionIntent(mActionableInsight, true);

        assertRemoteActionResult(result, ActionType.SERVICE);
    }

    @Test
    public void resolveActionIntent_remoteActionResolvesToBroadcast_returnsCorrectResult() {
        setUpRemoteAction(ActionType.BROADCAST, /* hasResolveInfo= */ true);

        ResolutionResult result = mUnderTest.resolveActionIntent(mActionableInsight, true);

        assertRemoteActionResult(result, ActionType.BROADCAST);
    }

    @Test
    public void resolveActionIntent_remoteActionHasNoResolveInfo_returnsNull() {
        setUpRemoteAction(ActionType.ACTIVITY, /* hasResolveInfo= */ false);

        ResolutionResult result = mUnderTest.resolveActionIntent(mActionableInsight, true);

        assertThat(result).isNull();
    }

    @Test
    public void resolveActionIntent_remoteActionHasNullPendingIntent_returnsNull() {
        // This state is only possible when un-parceling, so we must mock RemoteAction.
        RemoteAction remoteAction = mock(RemoteAction.class);
        when(remoteAction.getActionIntent()).thenReturn(null);

        InsightActionDetails actionDetails =
                new InsightActionDetails.Builder().setRemoteAction(remoteAction).build();
        mActionableInsight = new ActionableInsight.Builder(actionDetails, mDisplayDetails).build();

        ResolutionResult result = mUnderTest.resolveActionIntent(mActionableInsight, true);

        assertThat(result).isNull();
    }

    @Test
    public void resolveActionIntent_pendingIntentGetIntentReturnsNull_returnsNull() {
        mActionableInsight = createInsightWithRemoteAction(mPendingIntent);
        when(mPendingIntent.getIntent()).thenReturn(null);

        ResolutionResult result = mUnderTest.resolveActionIntent(mActionableInsight, true);

        assertThat(result).isNull();
    }

    @Test
    public void resolveActionIntent_needsComponentInfoFalse_doesNotQueryPackageManager() {
        setUpRemoteAction(ActionType.ACTIVITY, /* hasResolveInfo= */ false);

        ResolutionResult result = mUnderTest.resolveActionIntent(mActionableInsight, false);

        assertThat(result).isNotNull();
        assertThat(result.pendingIntent).isEqualTo(mPendingIntent);
        assertThat(result.resolveInfo).isNull();
        assertThat(result.actionType).isEqualTo(ActionType.ACTIVITY);
        verify(mPackageManager, never()).queryIntentActivities(any(), anyInt());
        verify(mPackageManager, never()).queryIntentServices(any(), anyInt());
        verify(mPackageManager, never()).queryBroadcastReceivers(any(), anyInt());
    }

    private ActionableInsight createInsightWithRemoteAction(PendingIntent pendingIntent) {
        RemoteAction remoteAction = new RemoteAction(mIcon, "Test", "Test action", pendingIntent);
        InsightActionDetails actionDetails =
                new InsightActionDetails.Builder().setRemoteAction(remoteAction).build();
        return new ActionableInsight.Builder(actionDetails, mDisplayDetails).build();
    }

    private void setUpRemoteAction(ActionType type, boolean hasResolveInfo) {
        mActionableInsight = createInsightWithRemoteAction(mPendingIntent);
        when(mPendingIntent.getIntent()).thenReturn(mTestIntent);

        when(mPendingIntent.isActivity()).thenReturn(type == ActionType.ACTIVITY);
        when(mPendingIntent.isService()).thenReturn(type == ActionType.SERVICE);
        when(mPendingIntent.isBroadcast()).thenReturn(type == ActionType.BROADCAST);

        List<ResolveInfo> resolveInfos =
                hasResolveInfo ? List.of(mTestResolveInfo) : Collections.emptyList();

        switch (type) {
            case ACTIVITY -> when(mPackageManager.queryIntentActivities(any(), anyInt()))
                    .thenReturn(resolveInfos);
            case SERVICE -> when(mPackageManager.queryIntentServices(any(), anyInt()))
                    .thenReturn(resolveInfos);
            case BROADCAST -> when(mPackageManager.queryBroadcastReceivers(any(), anyInt()))
                    .thenReturn(resolveInfos);
            default -> {
                // No-op
            }
        }
    }

    private void mockPackageManagerResolvers(
            List<ResolveInfo> activityInfos,
            List<ResolveInfo> serviceInfos,
            List<ResolveInfo> broadcastInfos) {
        when(mPackageManager.queryIntentActivities(any(), anyInt()))
                .thenReturn(activityInfos != null ? activityInfos : Collections.emptyList());
        when(mPackageManager.queryIntentServices(any(), anyInt()))
                .thenReturn(serviceInfos != null ? serviceInfos : Collections.emptyList());
        when(mPackageManager.queryBroadcastReceivers(any(), anyInt()))
                .thenReturn(broadcastInfos != null ? broadcastInfos : Collections.emptyList());
    }

    private void assertRemoteActionResult(ResolutionResult result, ActionType expectedType) {
        assertThat(result).isNotNull();
        assertThat(result.pendingIntent).isEqualTo(mPendingIntent);
        assertThat(result.resolveInfo).isEqualTo(mTestResolveInfo);
        assertThat(result.actionType).isEqualTo(expectedType);
    }
}
