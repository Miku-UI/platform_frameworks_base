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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.app.HandoffActivityData;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.PersistableBundle;
import android.os.UserHandle;
import android.platform.test.annotations.Presubmit;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import com.android.server.LocalServices;
import com.android.server.companion.datatransfer.continuity.messages.HandoffActivityDataMessage;
import com.android.server.wm.ActivityTaskManagerInternal;
import java.util.List;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@Presubmit
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
public class HandoffActivityStarterTest {

    @Mock private Context mMockContext;
    @Mock private PackageManager mMockPackageManager;
    @Mock private ActivityTaskManagerInternal mMockActivityTaskManagerInternal;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mMockContext.getPackageManager()).thenReturn(mMockPackageManager);
        LocalServices.addService(ActivityTaskManagerInternal.class, mMockActivityTaskManagerInternal);
    }

    @After
    public void tearDown() {
        LocalServices.removeServiceForTest(ActivityTaskManagerInternal.class);
    }

    @Test
    public void start_emptyList_returnsFalse() {
        boolean result = HandoffActivityStarter.start(mMockContext, List.of());
        assertThat(result).isFalse();
        verify(mMockActivityTaskManagerInternal, never())
                .startActivityWithConfig(any(), any(), any(), any(), anyInt());
    }

    @Test
    public void start_singleActivity_startsSuccessfully() throws Exception {
        // Create a HandoffActivityData mapped to an installed package.
        HandoffActivityDataMessage activityData = createTestHandoffActivity(true, false);

        when(mMockActivityTaskManagerInternal.startActivityWithConfig(
                        any(), any(), any(), any(), anyInt()))
                .thenReturn(ActivityManager.START_SUCCESS);

        // Start the activity.
        boolean result = HandoffActivityStarter.start(mMockContext, List.of(activityData));

        // Verify the activity was started.
        assertThat(result).isTrue();
        List<Intent> attempts = getActivityStartAttempts(1);
        verifyActivityStartAttempted(attempts.get(0), List.of(activityData));
    }

    @Test
    public void start_multipleActivities_startsSuccessfully() throws Exception {
        // Create test HandoffActivityData mapped to the installed packages.
        List<HandoffActivityDataMessage> handoffActivityData =
                List.of(
                        createTestHandoffActivity(true, false),
                        createTestHandoffActivity(true, false));

        // Make attempts to start activities return success.
        when(mMockActivityTaskManagerInternal.startActivityWithConfig(
                        any(), any(), any(), any(), anyInt()))
                .thenReturn(ActivityManager.START_SUCCESS);

        boolean result = HandoffActivityStarter.start(mMockContext, handoffActivityData);

        // Verify the activities were started.
        assertThat(result).isTrue();
        List<Intent> attempts = getActivityStartAttempts(1);
        verifyActivityStartAttempted(attempts.get(0), List.of(handoffActivityData.get(0)));
    }

    @Test
    public void start_nonTopActivityNotInstalled_onlyStartsTopActivity() throws Exception {
        // Create test HandoffActivityData. The top activity is installed, but the second activity
        // is not.
        List<HandoffActivityDataMessage> handoffActivityData =
                List.of(
                        createTestHandoffActivity(false, false),
                        createTestHandoffActivity(true, false));

        // Make any attempts to start activities return success.
        when(mMockActivityTaskManagerInternal.startActivityWithConfig(
                        any(), any(), any(), any(), anyInt()))
                .thenReturn(ActivityManager.START_SUCCESS);

        boolean result = HandoffActivityStarter.start(mMockContext, handoffActivityData);

        // Verify only one launch attempt was made, and it is only for the top activity.
        assertThat(result).isTrue();
        List<Intent> attempts = getActivityStartAttempts(1);
        verifyActivityStartAttempted(attempts.get(0), List.of(handoffActivityData.get(1)));
    }

    @Test
    public void start_topActivityNotInstalled_fallsBackToWeb() throws Exception {
        // Create a list of test HandoffActivityData. The top activity is not installed, but has
        // a fallback URI.
        List<HandoffActivityDataMessage> handoffActivityData =
                List.of(
                        createTestHandoffActivity(true, false),
                        createTestHandoffActivity(false, true));

        when(mMockActivityTaskManagerInternal.startActivityWithConfig(
                        any(), any(), any(), any(), anyInt()))
                .thenReturn(ActivityManager.START_SUCCESS);

        boolean result = HandoffActivityStarter.start(mMockContext, handoffActivityData);

        // Verify only one launch attempt was made, and it is for the fallback URI.
        assertThat(result).isTrue();
        List<Intent> attempts = getActivityStartAttempts(1);
        verifyActivityStartAttempted(
                attempts.get(0), handoffActivityData.get(1).activity().getFallbackUri());
    }

    @Test
    public void start_topActivityNotInstalledAndNoFallbackURI_returnsFalse() throws Exception {
        // Create test HandoffActivityData. The top activity is not installed, and has no fallback
        // URI.
        List<HandoffActivityDataMessage> handoffActivityData =
                List.of(
                        createTestHandoffActivity(true, false),
                        createTestHandoffActivity(false, false));

        boolean result = HandoffActivityStarter.start(mMockContext, handoffActivityData);

        // Verify no launch attempts were made.
        assertThat(result).isFalse();
        verify(mMockActivityTaskManagerInternal, never())
                .startActivityWithConfig(any(), any(), any(), any(), anyInt());
    }

    @Test
    public void start_startActivityFailsForAllActivities_reattemptsWithTopActivity()
            throws Exception {

        List<HandoffActivityDataMessage> handoffActivityData =
                List.of(
                        createTestHandoffActivity(true, false),
                        createTestHandoffActivity(true, false));

        // Make the first attempt to start activities fail, and the second attempt succeed.
        when(mMockActivityTaskManagerInternal.startActivityWithConfig(
                        any(), any(), any(), any(), anyInt()))
                .thenReturn(ActivityManager.START_ABORTED, ActivityManager.START_SUCCESS);

        boolean result = HandoffActivityStarter.start(mMockContext, handoffActivityData);

        // Verify two launch attempts were made - one for all activities, and one for the top
        // activity.
        assertThat(result).isTrue();
        List<Intent> attempts = getActivityStartAttempts(2);
        verifyActivityStartAttempted(attempts.get(0), List.of(handoffActivityData.get(0)));
        verifyActivityStartAttempted(attempts.get(1), List.of(handoffActivityData.get(1)));
    }

    @Test
    public void start_startActivityFailsForBothActivities_fallsBackToWeb() throws Exception {
        List<HandoffActivityDataMessage> handoffActivityData =
                List.of(
                        createTestHandoffActivity(true, false),
                        createTestHandoffActivity(true, true));

        // Make the first two attempts to start activities fail, and the third attempt succeed.
        when(mMockActivityTaskManagerInternal.startActivityWithConfig(
                        any(), any(), any(), any(), anyInt()))
                .thenReturn(
                        ActivityManager.START_ABORTED,
                        ActivityManager.START_ABORTED,
                        ActivityManager.START_SUCCESS);

        boolean result = HandoffActivityStarter.start(mMockContext, handoffActivityData);

        // Verify three launch attempts were made - one for all activities, one for the top
        // activity, and one for the fallback URI.
        assertThat(result).isTrue();
        List<Intent> attempts = getActivityStartAttempts(3);
        verifyActivityStartAttempted(attempts.get(0), List.of(handoffActivityData.get(0)));
        verifyActivityStartAttempted(attempts.get(1), List.of(handoffActivityData.get(1)));
        verifyActivityStartAttempted(
                attempts.get(2), handoffActivityData.get(1).activity().getFallbackUri());
    }

    @Test
    public void start_noActivityCanLaunchAndNoFallbackURI_returnsFalse() throws Exception {

        List<HandoffActivityDataMessage> handoffActivityData =
                List.of(
                        createTestHandoffActivity(true, false),
                        createTestHandoffActivity(true, false));

        // Make all attempts to start activities fail.
        when(mMockActivityTaskManagerInternal.startActivityWithConfig(
                        any(), any(), any(), any(), anyInt()))
                .thenReturn(ActivityManager.START_ABORTED, ActivityManager.START_ABORTED);

        boolean result = HandoffActivityStarter.start(mMockContext, handoffActivityData);

        // Verify two launch attempts were made - one for all activities, and one for the top
        // activity.
        assertThat(result).isFalse();
        List<Intent> attempts = getActivityStartAttempts(2);
        verifyActivityStartAttempted(attempts.get(0), List.of(handoffActivityData.get(0)));
        verifyActivityStartAttempted(attempts.get(1), List.of(handoffActivityData.get(1)));
    }

    @Test
    public void start_webFallbackOnly_usesFallbackURI() throws Exception {
        Uri fallbackUri = Uri.parse("https://www.example.com");
        HandoffActivityData handoffActivityData = HandoffActivityData.createWebHandoff(fallbackUri);
        // Create a test HandoffActivityData with no component name, but a fallback URI.
        HandoffActivityDataMessage handoffActivityDataMessage =
                new HandoffActivityDataMessage(handoffActivityData, List.of());

        when(mMockActivityTaskManagerInternal.startActivityWithConfig(
                        any(), any(), any(), any(), anyInt()))
                .thenReturn(ActivityManager.START_SUCCESS);

        boolean result =
                HandoffActivityStarter.start(mMockContext, List.of(handoffActivityDataMessage));

        // Verify only one launch attempt was made, and it is for the fallback URI.
        assertThat(result).isTrue();
        List<Intent> attempts = getActivityStartAttempts(1);
        verifyActivityStartAttempted(attempts.get(0), fallbackUri);
    }

    private static void verifyActivityStartAttempted(Intent actual, Uri expectedUri) {
        assertThat(actual.getAction()).isEqualTo(Intent.ACTION_VIEW);
        assertThat(actual.getData()).isEqualTo(expectedUri);
    }

    private static void verifyActivityStartAttempted(
            Intent actual, List<HandoffActivityDataMessage> expected) {

        assertThat(expected).isNotEmpty();
        HandoffActivityDataMessage expectedMessage = expected.get(0);

        assertThat(actual.getComponent())
                .isEqualTo(expectedMessage.activity().getComponentName());
        assertThat(actual.getExtras().size())
                .isEqualTo(expectedMessage.activity().getExtras().size());
        for (String key : actual.getExtras().keySet()) {
            assertThat(actual.getExtras().getString(key))
                    .isEqualTo(expectedMessage.activity().getExtras().getString(key));
        }
    }

    private List<Intent> getActivityStartAttempts(int expectedCount) {
        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mMockActivityTaskManagerInternal, times(expectedCount))
                .startActivityWithConfig(any(), any(), intentCaptor.capture(), any(), anyInt());
        return intentCaptor.getAllValues();
    }

    private HandoffActivityDataMessage createTestHandoffActivity(
            boolean installed, boolean hasFallbackUri) throws Exception {

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
        return new HandoffActivityDataMessage(builder.build(), List.of());
    }
}
