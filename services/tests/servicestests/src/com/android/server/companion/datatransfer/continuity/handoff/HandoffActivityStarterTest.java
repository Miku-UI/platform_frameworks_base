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

package com.android.server.companion.datatransfer.continuity.handoff;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.HandoffActivityData;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.PersistableBundle;
import android.os.UserHandle;
import android.platform.test.annotations.Presubmit;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import com.android.server.companion.datatransfer.continuity.handoff.HandoffActivityStarter;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.UUID;

@Presubmit
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
public class HandoffActivityStarterTest {

    @Mock private Context mMockContext;
    @Mock private PackageManager mMockPackageManager;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mMockContext.getPackageManager()).thenReturn(mMockPackageManager);
    }

    @Test
    public void start_emptyList_returnsFalse() {
        boolean result = HandoffActivityStarter.start(mMockContext, List.of());
        assertThat(result).isFalse();
        verify(mMockContext, never()).startActivityAsUser(any(), any());
        verify(mMockContext, never()).startActivitiesAsUser(any(), any(), any());
    }

    @Test
    public void start_singleActivity_startsSuccessfully() throws Exception {
        // Create a HandoffActivityData mapped to an installed package.
        HandoffActivityData activityData = createTestHandoffActivity(true, false);

        // Start the activity.
        boolean result = HandoffActivityStarter.start(mMockContext, List.of(activityData));

        // Verify the activity was started.
        assertThat(result).isTrue();
        List<Intent[]> attempts = getActivityStartAttempts(1);
        verifyActivityStartAttempted(attempts.get(0), List.of(activityData));
    }

    @Test
    public void start_multipleActivities_startsSuccessfully() throws Exception {
        // Create test HandoffActivityData mapped to the installed packages.
        List<HandoffActivityData> handoffActivityData = List.of(
            createTestHandoffActivity(true, false),
            createTestHandoffActivity(true, false));

        // Make attempts to start activities return success.
        when(mMockContext.startActivitiesAsUser(any(), any(), any()))
                .thenReturn(ActivityManager.START_SUCCESS);

        boolean result = HandoffActivityStarter.start(mMockContext, handoffActivityData);

        // Verify the activities were started.
        assertThat(result).isTrue();
        List<Intent[]> attempts = getActivityStartAttempts(1);
        verifyActivityStartAttempted(attempts.get(0), handoffActivityData);
    }

    @Test
    public void start_nonTopActivityNotInstalled_onlyStartsTopActivity() throws Exception {
        // Create test HandoffActivityData. The top activity is installed, but the second activity
        // is not.
        List<HandoffActivityData> handoffActivityData = List.of(
            createTestHandoffActivity(false, false),
            createTestHandoffActivity(true, false));

        // Make any attempts to start activities return success.
        when(mMockContext.startActivitiesAsUser(any(), any(), any()))
            .thenReturn(ActivityManager.START_SUCCESS);

        boolean result = HandoffActivityStarter.start(mMockContext, handoffActivityData);

        // Verify only one launch attempt was made, and it is only for the top activity.
        assertThat(result).isTrue();
        List<Intent[]> attempts = getActivityStartAttempts(1);
        verifyActivityStartAttempted(attempts.get(0), List.of(handoffActivityData.get(1)));
    }

    @Test
    public void start_topActivityNotInstalled_fallsBackToWeb() throws Exception {
        // Create a list of test HandoffActivityData. The top activity is not installed, but has
        // a fallback URI.
        List<HandoffActivityData> handoffActivityData = List.of(
            createTestHandoffActivity(true, false),
            createTestHandoffActivity(false, true));

        boolean result = HandoffActivityStarter.start(mMockContext, handoffActivityData);

        // Verify only one launch attempt was made, and it is for the fallback URI.
        assertThat(result).isTrue();
        List<Intent[]> attempts = getActivityStartAttempts(1);
        verifyActivityStartAttempted(attempts.get(0), handoffActivityData.get(1).getFallbackUri());
    }

    @Test
    public void start_topActivityNotInstalledAndNoFallbackURI_returnsFalse() throws Exception {
        // Create test HandoffActivityData. The top activity is not installed, and has no fallback
        // URI.
        List<HandoffActivityData> handoffActivityData = List.of(
            createTestHandoffActivity(true, false),
            createTestHandoffActivity(false, false));

        boolean result = HandoffActivityStarter.start(mMockContext, handoffActivityData);

        // Verify no launch attempts were made.
        assertThat(result).isFalse();
        verify(mMockContext, never()).startActivitiesAsUser(any(), any(), any());
    }

    @Test
    public void start_startActivityFailsForAllActivities_reattemptsWithTopActivity()
        throws Exception {

        List<HandoffActivityData> handoffActivityData = List.of(
            createTestHandoffActivity(true, false),
            createTestHandoffActivity(true, false));

        // Make the first attempt to start activities fail, and the second attempt succeed.
        when(mMockContext.startActivitiesAsUser(any(), any(), any()))
                .thenReturn(
                    ActivityManager.START_ABORTED,
                    ActivityManager.START_SUCCESS);

        boolean result = HandoffActivityStarter.start(mMockContext, handoffActivityData);

        // Verify two launch attempts were made - one for all activities, and one for the top
        // activity.
        assertThat(result).isTrue();
        List<Intent[]> attempts = getActivityStartAttempts(2);
        verifyActivityStartAttempted(attempts.get(0), handoffActivityData);
        verifyActivityStartAttempted(attempts.get(1), List.of(handoffActivityData.get(1)));
    }

    @Test
    public void start_startActivityFailsForBothActivities_fallsBackToWeb() throws Exception {
        List<HandoffActivityData> handoffActivityData = List.of(
            createTestHandoffActivity(true, false),
            createTestHandoffActivity(true, true));

        // Make the first two attempts to start activities fail, and the third attempt succeed.
        when(mMockContext.startActivitiesAsUser(any(), any(), any()))
                .thenReturn(
                    ActivityManager.START_ABORTED,
                    ActivityManager.START_ABORTED,
                    ActivityManager.START_SUCCESS);

        boolean result = HandoffActivityStarter.start(mMockContext, handoffActivityData);

        // Verify three launch attempts were made - one for all activities, one for the top
        // activity, and one for the fallback URI.
        assertThat(result).isTrue();
        List<Intent[]> attempts = getActivityStartAttempts(3);
        verifyActivityStartAttempted(attempts.get(0), handoffActivityData);
        verifyActivityStartAttempted(attempts.get(1), List.of(handoffActivityData.get(1)));
        verifyActivityStartAttempted(attempts.get(2), handoffActivityData.get(1).getFallbackUri());
    }

    @Test
    public void start_noActivityCanLaunchAndNoFallbackURI_returnsFalse() throws Exception {

        List<HandoffActivityData> handoffActivityData = List.of(
            createTestHandoffActivity(true, false),
            createTestHandoffActivity(true, false));

        // Make all attempts to start activities fail.
        when(mMockContext.startActivitiesAsUser(any(), any(), any()))
                .thenReturn(
                    ActivityManager.START_ABORTED,
                    ActivityManager.START_ABORTED);

        boolean result = HandoffActivityStarter.start(mMockContext, handoffActivityData);

        // Verify two launch attempts were made - one for all activities, and one for the top
        // activity.
        assertThat(result).isFalse();
        List<Intent[]> attempts = getActivityStartAttempts(2);
        verifyActivityStartAttempted(attempts.get(0), handoffActivityData);
        verifyActivityStartAttempted(attempts.get(1), List.of(handoffActivityData.get(1)));
    }

    private static void verifyActivityStartAttempted(Intent[] actual, Uri expectedUri) {
        assertThat(actual).hasLength(1);
        assertThat(actual[0].getAction()).isEqualTo(Intent.ACTION_VIEW);
        assertThat(actual[0].getData()).isEqualTo(expectedUri);
    }

    private static void verifyActivityStartAttempted(
        Intent[] actual,
        List<HandoffActivityData> expected) {

        assertThat(actual).hasLength(expected.size());
        for (int i = 0; i < actual.length; i++) {
            assertThat(actual[i].getComponent()).isEqualTo(expected.get(i).getComponentName());
            assertThat(actual[i].getExtras().size()).isEqualTo(expected.get(i).getExtras().size());
            for (String key : actual[i].getExtras().keySet()) {
                assertThat(actual[i].getExtras().getString(key))
                    .isEqualTo(expected.get(i).getExtras().getString(key));
            }
        }
    }

    private List<Intent[]> getActivityStartAttempts(int expectedCount) {
        ArgumentCaptor<Intent[]> intentArrayCaptor = ArgumentCaptor.forClass(Intent[].class);
        verify(mMockContext, times(expectedCount)).startActivitiesAsUser(
            intentArrayCaptor.capture(),
            any(),
            any());
        return intentArrayCaptor.getAllValues();
    }

    private HandoffActivityData createTestHandoffActivity(
        boolean installed,
        boolean hasFallbackUri) throws Exception {

        String packageName = "com.example." + UUID.randomUUID().toString();
        ComponentName componentName = new ComponentName(packageName, packageName + ".Activity");
        if (installed) {
            when(mMockPackageManager.getActivityInfo(
                eq(componentName), eq(PackageManager.MATCH_DEFAULT_ONLY)))
                .thenReturn(new ActivityInfo());
        } else {
            when(mMockPackageManager.getActivityInfo(
                eq(componentName), eq(PackageManager.MATCH_DEFAULT_ONLY)))
                .thenThrow(new PackageManager.NameNotFoundException());
        }
        HandoffActivityData.Builder builder = new HandoffActivityData.Builder(componentName);
        PersistableBundle extras = new PersistableBundle();
        extras.putString("key", "value");
        builder.setExtras(extras);
        if (hasFallbackUri) {
            builder.setFallbackUri(Uri.parse("https://www.example.com"));
        }
        return builder.build();
    }
}