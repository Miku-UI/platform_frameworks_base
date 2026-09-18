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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Icon;
import android.service.personalcontext.insight.ActionableInsight;
import android.service.personalcontext.insight.InsightActionDetails;
import android.service.personalcontext.insight.InsightDisplayDetails;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.server.personalcontext.notifications.ContextActionResolver.ActionType;
import com.android.server.personalcontext.notifications.ContextActionResolver.ResolutionResult;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class NotificationActionFactoryTest {
    private Context mContext;
    @Mock private PackageManager mPackageManager;
    @Mock private ContextActionResolver mContextActionResolver;

    private static final String TEST_APP_NAME = "Test App";
    private static final int TEST_APP_RESOURCE_ID = 1234;
    private static final Icon FAKE_ICON = Icon.createWithResource("pkg", 123);

    private NotificationActionFactory mFactory;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mFactory = new NotificationActionFactory(mContext, mPackageManager, mContextActionResolver);
    }

    @Test
    public void testCreateNotificationAction_withTitleAndIcon() {
        mockActionResolverResolvesIntent(false, false);
        ActionableInsight insight = createActionableInsight("Test Title", FAKE_ICON);

        Notification.Action action = mFactory.createNotificationAction(insight);

        assertThat(action).isNotNull();
        assertThat(action.title.toString()).isEqualTo("Test Title");
        assertThat(action.getIcon()).isEqualTo(FAKE_ICON);
    }

    @Test
    public void testCreateNotificationAction_noTitle_usesDefaultTitle() {
        mockActionResolverResolvesIntent(true, true);
        ActionableInsight insight = createActionableInsight(null, FAKE_ICON);

        Notification.Action action = mFactory.createNotificationAction(insight);

        assertThat(action).isNotNull();
        CharSequence expectedTitle =
                mContext.getString(com.android.internal.R.string.open_app_name, TEST_APP_NAME);
        assertThat(action.title.toString()).isEqualTo(expectedTitle.toString());
    }

    @Test
    public void testCreateNotificationAction_noIcon_usesDefaultIcon() {
        mockActionResolverResolvesIntent(true, true);
        ActionableInsight insight = createActionableInsight("Test Title", null);

        Notification.Action action = mFactory.createNotificationAction(insight);

        assertThat(action).isNotNull();
        assertThat(action.getIcon().getResPackage()).isEqualTo("pkg");
        assertThat(action.getIcon().getResId()).isEqualTo(TEST_APP_RESOURCE_ID);
    }

    @Test
    public void testCreateNotificationAction_cannotResolveIntent_returnsNull() {
        when(mContextActionResolver.resolveActionIntent(any(ActionableInsight.class), anyBoolean()))
                .thenReturn(null);
        ActionableInsight insight = createActionableInsight("Test Title", FAKE_ICON);

        Notification.Action action = mFactory.createNotificationAction(insight);

        assertThat(action).isNull();
    }

    private ActionableInsight createActionableInsight(String title, Icon icon) {
        InsightDisplayDetails.Builder displayDetailsBuilder;
        if (title != null && icon != null) {
            displayDetailsBuilder = new InsightDisplayDetails.Builder(title, icon);
        } else if (title != null) {
            displayDetailsBuilder = new InsightDisplayDetails.Builder(title);
        } else {
            displayDetailsBuilder = new InsightDisplayDetails.Builder(icon);
        }
        InsightDisplayDetails displayDetails = displayDetailsBuilder.build();

        InsightActionDetails actionDetails =
                new InsightActionDetails.Builder().setPendingIntent(mock(PendingIntent.class))
                        .build();
        return new ActionableInsight.Builder(actionDetails, displayDetails).build();
    }

    private void mockActionResolverResolvesIntent(
            boolean needsComponentInfo, boolean includeResolveInfo) {
        ActivityInfo activityInfo = new ActivityInfo();
        activityInfo.packageName = "pkg";
        activityInfo.name = "TestActivity";
        activityInfo.applicationInfo = new ApplicationInfo();
        activityInfo.applicationInfo.packageName = "pkg";
        activityInfo.icon = TEST_APP_RESOURCE_ID;

        ResolveInfo resolveInfo = new ResolveInfo();
        resolveInfo.activityInfo = activityInfo;

        ResolutionResult result =
                new ResolutionResult(
                        PendingIntent.getActivity(
                                mContext, 0, new Intent("ACTION"), PendingIntent.FLAG_IMMUTABLE),
                        includeResolveInfo ? resolveInfo : null,
                        ActionType.ACTIVITY);

        when(mContextActionResolver.resolveActionIntent(
                        any(ActionableInsight.class), eq(needsComponentInfo)))
                .thenReturn(result);
        when(mPackageManager.getApplicationLabel(any(ApplicationInfo.class)))
                .thenReturn(TEST_APP_NAME);
    }
}
