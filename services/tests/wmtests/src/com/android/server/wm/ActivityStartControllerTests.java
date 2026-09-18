/*
 * Copyright (C) 2018 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.server.wm;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.eq;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.mock;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.never;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.spy;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.times;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertThat;

import android.app.ActivityManager;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.os.Binder;
import android.platform.test.annotations.Presubmit;

import androidx.test.filters.SmallTest;

import com.android.server.wm.ActivityStarter.Factory;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for the {@link ActivityStartController} class.
 *
 * Build/Install/Run:
 *  atest WmTests:ActivityStartControllerTests
 */
@SmallTest
@Presubmit
@RunWith(WindowTestRunner.class)
public class ActivityStartControllerTests extends WindowTestsBase {
    private ActivityStartController mController;
    private Factory mFactory;
    private ActivityStarter mStarter;

    private static final String TEST_TYPE = "testType";

    @Before
    public void setUp() throws Exception {
        mFactory = mock(Factory.class);
        mController = new ActivityStartController(mAtm, mAtm.mTaskSupervisor, mFactory);
        mStarter = spy(new ActivityStarter(mController, mAtm, mAtm.mTaskSupervisor,
                mock(ActivityStartInterceptor.class), mock(UserHelper.class)));
        doReturn(mStarter).when(mFactory).obtain();
    }

    /**
     * Ensures that when an [Activity] is started in a [TaskFragment] the associated
     * [ActivityStarter.Request] has the intent's resolved type correctly set.
     */
    @Test
    public void testStartActivityInTaskFragment_setsActivityStarterRequestResolvedType() {
        final Intent intent = new Intent();
        intent.setType(TEST_TYPE);

        mController.startActivityInTaskFragment(
                mock(TaskFragment.class),
                intent,
                null /* activityOptions */,
                null /* resultTo */ ,
                Binder.getCallingPid(),
                Binder.getCallingUid(),
                null /* errorCallbackToken */
        );

        assertThat(mStarter.mRequest.resolvedType).isEqualTo(TEST_TYPE);
    }

    /**
     * Ensures instances are recycled after execution.
     */
    @Test
    public void testRecycling() {
        final Intent intent = new Intent();
        final ActivityStarter optionStarter = new ActivityStarter(mController, mAtm,
                mAtm.mTaskSupervisor, mock(ActivityStartInterceptor.class), mock(UserHelper.class));
        optionStarter
                .setIntent(intent)
                .setReason("Test")
                .execute();
        verify(mFactory, times(1)).recycle(eq(optionStarter));
    }

    /**
     * Verifies that starting a home activity doesn't cause a NullPointerException when the
     * associated TaskDisplayArea has no root home task. This is the regression test for the
     * crash fixed in the change.
     */
    @Test
    public void testStartHomeActivity_nullRootHomeTask_noNpe() {
        // Get the default TaskDisplayArea and spy on it to control its methods.
        final TaskDisplayArea taskDisplayArea = spy(mDefaultDisplay.getDefaultTaskDisplayArea());
        // Set up the spy to return a null root home task, simulating the crash condition.
        doReturn(null).when(taskDisplayArea).getRootHomeTask();

        // Mock the starter to return a successful result.
        doReturn(ActivityManager.START_SUCCESS).when(mStarter).execute();

        // Create dummy intent and activity info.
        final Intent intent = new Intent();
        final ActivityInfo aInfo = new ActivityInfo();
        aInfo.applicationInfo = new ApplicationInfo();
        aInfo.name = "TestHomeActivity";
        aInfo.packageName = "com.android.test.home";

        // Execute the method under test. This should not throw a NullPointerException.
        mController.startHomeActivity(intent, aInfo, "test", taskDisplayArea, true /* onTop */);

        // Verify that since the root home task is null, we don't attempt to schedule a resume.
        verify(mAtm.mTaskSupervisor, never()).scheduleResumeTopActivities();
    }

    /**
     * Verifies that if a home activity start is successful and its root task was already in the
     * process of resuming, a new resume pass is scheduled.
     */
    @Test
    public void testStartHomeActivity_resumesWhenInResumeTopActivity() {
        // Get the default TaskDisplayArea and spy on it.
        final TaskDisplayArea taskDisplayArea = spy(mDefaultDisplay.getDefaultTaskDisplayArea());
        // Create a mock root home task.
        final Task rootHomeTask = mock(Task.class);
        // Set the condition that triggers the resume.
        rootHomeTask.mInResumeTopActivity = true;
        // Set up the spy to return our mock task.
        doReturn(rootHomeTask).when(taskDisplayArea).getRootHomeTask();

        // Mock the starter to return a successful result.
        doReturn(ActivityManager.START_SUCCESS).when(mStarter).execute();

        // Create dummy intent and activity info.
        final Intent intent = new Intent();
        final ActivityInfo aInfo = new ActivityInfo();
        aInfo.applicationInfo = new ApplicationInfo();
        aInfo.name = "TestHomeActivity";
        aInfo.packageName = "com.android.test.home";

        // Execute the method under test.
        mController.startHomeActivity(intent, aInfo, "test", taskDisplayArea, true /* onTop */);

        // Verify that since the start was successful and the task was in resume, we schedule
        // another resume.
        verify(mAtm.mTaskSupervisor, times(1)).scheduleResumeTopActivities();
    }
}
