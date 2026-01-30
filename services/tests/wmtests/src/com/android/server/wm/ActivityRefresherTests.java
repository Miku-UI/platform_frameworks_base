/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.server.wm;

import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;
import static android.app.servertransaction.ActivityLifecycleItem.ON_PAUSE;
import static android.app.servertransaction.ActivityLifecycleItem.ON_STOP;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn;
import static com.android.server.wm.AppCompatCameraOverrides.NONE;
import static com.android.server.wm.AppCompatCameraOverrides.IN_PROGRESS;
import static com.android.server.wm.AppCompatCameraOverrides.REQUESTED;
import static com.android.window.flags.Flags.FLAG_ENABLE_CAMERA_COMPAT_SANDBOX_DISPLAY_ROTATION_ON_EXTERNAL_DISPLAYS_BUGFIX;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.servertransaction.RefreshCallbackItem;
import android.app.servertransaction.ResumeActivityItem;
import android.content.ComponentName;
import android.content.res.Configuration;
import android.os.Handler;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;

import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for {@link ActivityRefresher}.
 *
 * <p>Build/Install/Run:
 *  atest WmTests:ActivityRefresherTests
 */
@SmallTest
@Presubmit
@RunWith(WindowTestRunner.class)
public class ActivityRefresherTests extends WindowTestsBase {
    private Handler mMockHandler;
    private AppCompatConfiguration mAppCompatConfiguration;

    private ActivityRecord mActivity;
    private ActivityRefresher mActivityRefresher;

    private ActivityRefresher.Evaluator mEvaluatorFalse =
            (activity, newConfig, lastReportedConfig) -> false;

    private ActivityRefresher.Evaluator mEvaluatorTrue =
            (activity, newConfig, lastReportedConfig) -> true;

    private final Configuration mNewConfig = new Configuration();
    private final Configuration mOldConfig = new Configuration();

    @Before
    public void setUp() throws Exception {
        mAppCompatConfiguration = mDisplayContent.mWmService.mAppCompatConfiguration;
        spyOn(mAppCompatConfiguration);
        when(mAppCompatConfiguration.isCameraCompatForceRotateTreatmentEnabled())
                .thenReturn(true);
        when(mAppCompatConfiguration.isCameraCompatRefreshEnabled())
                .thenReturn(true);
        when(mAppCompatConfiguration.isCameraCompatRefreshCycleThroughStopEnabled())
                .thenReturn(true);

        mMockHandler = mock(Handler.class);
        when(mMockHandler.postDelayed(any(Runnable.class), anyLong())).thenAnswer(
                invocation -> {
                    ((Runnable) invocation.getArgument(0)).run();
                    return null;
                });

        mActivityRefresher = new ActivityRefresher(mDisplayContent.mWmService, mMockHandler);
    }

    @Test
    @DisableFlags(FLAG_ENABLE_CAMERA_COMPAT_SANDBOX_DISPLAY_ROTATION_ON_EXTERNAL_DISPLAYS_BUGFIX)
    public void testShouldRefreshActivity_pollMechanism_refreshDisabled() throws Exception {
        when(mAppCompatConfiguration.isCameraCompatRefreshEnabled())
                .thenReturn(false);
        configureActivityAndDisplay();
        mActivityRefresher.addEvaluator(mEvaluatorTrue);

        mActivityRefresher.onActivityConfigurationChanging(mActivity, mNewConfig, mOldConfig);

        assertNoActivityRefreshedDeprecated();
    }

    @Test
    @EnableFlags(FLAG_ENABLE_CAMERA_COMPAT_SANDBOX_DISPLAY_ROTATION_ON_EXTERNAL_DISPLAYS_BUGFIX)
    public void testShouldRefreshActivity_pushMechanism_refreshDisabled() {
        when(mAppCompatConfiguration.isCameraCompatRefreshEnabled()).thenReturn(false);
        configureActivityAndDisplay();

        mActivityRefresher.requestRefresh(mActivity);
        mActivityRefresher.refreshActivityIfEnabled(mActivity);

        assertActivityRefreshRequested(/* refreshRequested */ true);
        assertNoActivityRefreshed();
    }

    @Test
    @DisableFlags(FLAG_ENABLE_CAMERA_COMPAT_SANDBOX_DISPLAY_ROTATION_ON_EXTERNAL_DISPLAYS_BUGFIX)
    public void testShouldRefreshActivity_pollMechanism_refreshDisabledForActivity() {
        configureActivityAndDisplay();
        when(mActivity.mAppCompatController.getCameraOverrides()
                .shouldRefreshActivityForCameraCompat()).thenReturn(false);
        mActivityRefresher.addEvaluator(mEvaluatorTrue);

        mActivityRefresher.onActivityConfigurationChanging(mActivity, mNewConfig, mOldConfig);

        assertNoActivityRefreshedDeprecated();
    }

    @Test
    @EnableFlags(FLAG_ENABLE_CAMERA_COMPAT_SANDBOX_DISPLAY_ROTATION_ON_EXTERNAL_DISPLAYS_BUGFIX)
    public void testShouldRefreshActivity_pushMechanism_refreshDisabledForActivity() {
        configureActivityAndDisplay();
        when(mActivity.mAppCompatController.getCameraOverrides()
                .shouldRefreshActivityForCameraCompat()).thenReturn(false);

        mActivityRefresher.requestRefresh(mActivity);
        mActivityRefresher.refreshActivityIfEnabled(mActivity);

        assertNoActivityRefreshed();
    }

    @Test
    @DisableFlags(FLAG_ENABLE_CAMERA_COMPAT_SANDBOX_DISPLAY_ROTATION_ON_EXTERNAL_DISPLAYS_BUGFIX)
    public void testShouldRefreshActivity_noRefreshTriggerers() {
        configureActivityAndDisplay();

        mActivityRefresher.onActivityConfigurationChanging(mActivity, mNewConfig, mOldConfig);

        assertNoActivityRefreshedDeprecated();
    }

    @Test
    @DisableFlags(FLAG_ENABLE_CAMERA_COMPAT_SANDBOX_DISPLAY_ROTATION_ON_EXTERNAL_DISPLAYS_BUGFIX)
    public void testShouldRefreshActivity_refreshTriggerersReturnFalse() {
        configureActivityAndDisplay();
        mActivityRefresher.addEvaluator(mEvaluatorFalse);

        mActivityRefresher.onActivityConfigurationChanging(mActivity, mNewConfig, mOldConfig);

        assertNoActivityRefreshedDeprecated();
    }

    @Test
    @DisableFlags(FLAG_ENABLE_CAMERA_COMPAT_SANDBOX_DISPLAY_ROTATION_ON_EXTERNAL_DISPLAYS_BUGFIX)
    public void testShouldRefreshActivity_anyRefreshTriggerersReturnTrue() {
        configureActivityAndDisplay();
        mActivityRefresher.addEvaluator(mEvaluatorFalse);
        mActivityRefresher.addEvaluator(mEvaluatorTrue);

        mActivityRefresher.onActivityConfigurationChanging(mActivity, mNewConfig, mOldConfig);

        assertActivityRefreshedDeprecated(/* cycleThroughStop */ true);
    }

    @Test
    @EnableFlags(FLAG_ENABLE_CAMERA_COMPAT_SANDBOX_DISPLAY_ROTATION_ON_EXTERNAL_DISPLAYS_BUGFIX)
    public void testShouldRefreshActivity_refreshNotRequested_activityNotRefreshed() {
        configureActivityAndDisplay();

        mActivityRefresher.refreshActivityIfEnabled(mActivity);

        assertActivityRefreshRequested(/* refreshRequested */ false);
    }

    @Test
    @DisableFlags(FLAG_ENABLE_CAMERA_COMPAT_SANDBOX_DISPLAY_ROTATION_ON_EXTERNAL_DISPLAYS_BUGFIX)
    public void testOnActivityConfigurationChanging_pollMechanism_cycleThroughStopDisabled() {
        mActivityRefresher.addEvaluator(mEvaluatorTrue);
        when(mAppCompatConfiguration.isCameraCompatRefreshCycleThroughStopEnabled())
                .thenReturn(false);
        configureActivityAndDisplay();

        mActivityRefresher.onActivityConfigurationChanging(mActivity, mNewConfig, mOldConfig);

        assertActivityRefreshedDeprecated(/* cycleThroughStop */ false);
    }

    @Test
    @EnableFlags(FLAG_ENABLE_CAMERA_COMPAT_SANDBOX_DISPLAY_ROTATION_ON_EXTERNAL_DISPLAYS_BUGFIX)
    public void testOnActivityConfigurationChanging_pushMechanism_cycleThroughStopDisabled() {
        when(mAppCompatConfiguration.isCameraCompatRefreshCycleThroughStopEnabled())
                .thenReturn(false);
        configureActivityAndDisplay();

        mActivityRefresher.requestRefresh(mActivity);
        mActivityRefresher.refreshActivityIfEnabled(mActivity);

        assertActivityRefreshRequested(/* refreshRequested */ true);
        assertActivityRefreshed(/* cycleThroughStop */ false);
    }

    @Test
    public void testOnActivityConfigurationChanging_cycleThroughPauseEnabledForApp() {
        configureActivityAndDisplay();
        doReturn(true)
                .when(mActivity.mAppCompatController.getCameraOverrides())
                    .shouldRefreshActivityViaPauseForCameraCompat();

        mActivityRefresher.requestRefresh(mActivity);
        mActivityRefresher.refreshActivityIfEnabled(mActivity);

        assertActivityRefreshRequested(/* refreshRequested */ true);
        assertActivityRefreshed(/* cycleThroughStop */ false);
    }

    @Test
    @EnableFlags(FLAG_ENABLE_CAMERA_COMPAT_SANDBOX_DISPLAY_ROTATION_ON_EXTERNAL_DISPLAYS_BUGFIX)
    public void testOnActivityRefreshed_activityRelaunched_activityNotRefreshedAlso() {
        configureActivityAndDisplay();
        mActivityRefresher.requestRefresh(mActivity);

        mActivityRefresher.onActivityRefreshed(mActivity);
        mActivityRefresher.refreshActivityIfEnabled(mActivity);

        assertActivityRefreshRequested(/* refreshRequested */ true);
        assertNoActivityRefreshed();
    }

    @Test
    @DisableFlags(FLAG_ENABLE_CAMERA_COMPAT_SANDBOX_DISPLAY_ROTATION_ON_EXTERNAL_DISPLAYS_BUGFIX)
    public void testOnActivityRefreshed_pollMechanism_setIsRefreshRequestedToFalse() {
        configureActivityAndDisplay();
        mActivityRefresher.addEvaluator(mEvaluatorTrue);
        doReturn(true)
                .when(mActivity.mAppCompatController.getCameraOverrides())
                .shouldRefreshActivityViaPauseForCameraCompat();

        mActivityRefresher.onActivityRefreshed(mActivity);

        assertNoActivityRefreshedDeprecated();
    }

    @Test
    public void testActivityRefresh_activityRefreshedTrue() {
        configureActivityAndDisplay();
        mActivityRefresher.requestRefresh(mActivity);

        mActivityRefresher.refreshActivityIfEnabled(mActivity);

        assertActivityRefreshed();
    }

    private void assertActivityRefreshRequested(boolean refreshRequested) {
        verify(mActivity.mAppCompatController.getCameraOverrides(),
                times(refreshRequested ? 1 : 0)).setActivityRefreshState(REQUESTED);
    }

    private void assertActivityRefreshed() {
        assertActivityRefreshed(/* cycleThroughStop*/ true);
    }

    private void assertNoActivityRefreshedDeprecated() {
        verify(mActivity.mAppCompatController.getCameraOverrides(), never())
                .setIsRefreshRequested(true);
    }

    private void assertActivityRefreshedDeprecated(boolean cycleThroughStop) {
        verify(mActivity.mAppCompatController.getCameraOverrides()).setIsRefreshRequested(true);
        assertRefreshTransactions(/* activityRefreshed */ true, cycleThroughStop);
        // Activity should complete the cycle, as there are no delays in tests.
        assertFalse(mActivity.mAppCompatController.getCameraOverrides().isRefreshRequested());
    }

    private void assertNoActivityRefreshed() {
        verify(mActivity.mAppCompatController.getCameraOverrides(), never())
                .setActivityRefreshState(IN_PROGRESS);
    }

    private void assertActivityRefreshed(boolean cycleThroughStop) {
        verify(mActivity.mAppCompatController.getCameraOverrides()).setActivityRefreshState(
                IN_PROGRESS);
        assertRefreshTransactions(/* activityRefreshed */ true, cycleThroughStop);
        // Activity should complete the cycle, as there are no delays in tests.
        assertEquals(NONE,
                mActivity.mAppCompatController.getCameraOverrides().getActivityRefreshState());
    }

    private void assertRefreshTransactions(boolean activityRefreshed, boolean cycleThroughStop) {
        final RefreshCallbackItem refreshCallbackItem =
                new RefreshCallbackItem(mActivity.token, cycleThroughStop ? ON_STOP : ON_PAUSE);
        final ResumeActivityItem resumeActivityItem = new ResumeActivityItem(mActivity.token,
                /* isForward */ false, /* shouldSendCompatFakeFocus */ false);

        verify(mActivity.mAtmService.getLifecycleManager(), times(activityRefreshed ? 1 : 0))
                .scheduleTransactionItems(mActivity.app.getThread(),
                        refreshCallbackItem, resumeActivityItem);
    }

    private void configureActivityAndDisplay() {
        mActivity = new TaskBuilder(mSupervisor)
                .setCreateActivity(true)
                .setDisplay(mDisplayContent)
                // Set the component to be that of the test class in order to enable compat changes
                .setComponent(ComponentName.createRelative(mContext,
                        ActivityRefresherTests.class.getName()))
                .setWindowingMode(WINDOWING_MODE_FREEFORM)
                .build()
                .getTopMostActivity();

        spyOn(mActivity.mAppCompatController.getCameraOverrides());
        doReturn(true).when(mActivity).inFreeformWindowingMode();
        doReturn(true).when(mActivity.mAppCompatController
                .getCameraOverrides()).shouldRefreshActivityForCameraCompat();
    }
}
