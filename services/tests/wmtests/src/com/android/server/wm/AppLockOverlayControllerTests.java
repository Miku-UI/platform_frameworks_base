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

package com.android.server.wm;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn;
import static com.android.server.wm.WindowContainer.POSITION_BOTTOM;
import static com.android.server.wm.WindowContainer.POSITION_TOP;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.content.ComponentName;
import android.content.Intent;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;
import android.security.Flags;
import android.view.Display;

import androidx.test.filters.MediumTest;

import com.android.internal.app.LockedAppActivity;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Build/Install/Run:
 * atest WmTests:AppLockOverlayControllerTests
 */
@EnableFlags({Flags.FLAG_APP_LOCK_APIS, Flags.FLAG_APP_LOCK_CORE})
@MediumTest
@Presubmit
@RunWith(WindowTestRunner.class)
public class AppLockOverlayControllerTests extends WindowTestsBase {

    private static final String SYSTEM_PACKAGE_NAME = "android";

    private AppLockOverlayController mAppLockOverlayController;

    @Before
    public void setUp() throws Exception {
        mAppLockOverlayController = mWm.mAppLockController.mAppLockOverlayController;
        spyOn(mAppLockOverlayController);
        spyOn(mRootWindowContainer);
    }

    @Test
    public void lockActivitiesTasksForAppLock_emptyTask_doesNothing() {
        // GIVEN an empty task
        final Task task = createLockedTask(/* isVisible= */ false);
        task.mChildren.clear();

        // WHEN we try to lock tasks for that package
        mAppLockOverlayController.lockActivitiesTasksForAppLock(TEST_PACKAGE_1,
                TEST_USER_ID_1);

        // THEN no overlay is added because the task doesn't have any children
        verify(mAppLockOverlayController, never()).addLockedByAppLockTaskOverlay(any(), any(),
                anyInt());
        verify(mAppLockOverlayController, never()).addLockedByAppLockActivityOverlay(any());
    }

    @Test
    public void lockActivitiesTasksForAppLock_withNullRealActivity_doesNothing() {
        // GIVEN a task with a null realActivity
        final Task task = createLockedTask(/* isVisible= */ false);
        task.realActivity = null;

        // WHEN we try to lock tasks for that package
        mAppLockOverlayController.lockActivitiesTasksForAppLock(TEST_PACKAGE_1,
                TEST_USER_ID_1);

        // THEN no overlay is added because the task has no realActivity to check
        verify(mAppLockOverlayController, never()).addLockedByAppLockTaskOverlay(any(), any(),
                anyInt());
        verify(mAppLockOverlayController, never()).addLockedByAppLockActivityOverlay(any());
    }

    @Test
    public void lockActivitiesTasksForAppLock_withVisibleTask_doesNothing() {
        // GIVEN a task that is visible and belongs to a locked package
        createLockedTask(/* isVisible= */ true);

        // WHEN we try to lock tasks for that package
        mAppLockOverlayController.lockActivitiesTasksForAppLock(TEST_PACKAGE_1,
                TEST_USER_ID_1);

        // THEN no overlay is added because the task is already visible
        verify(mAppLockOverlayController, never()).addLockedByAppLockTaskOverlay(any(), any(),
                anyInt());
        verify(mAppLockOverlayController, never()).addLockedByAppLockActivityOverlay(any());
    }

    @Test
    public void lockActivitiesTasksForAppLock_withInvisibleTask_locksTask() {
        // GIVEN an invisible task that belongs to a locked package
        final Task task = createLockedTask(/* isVisible= */ false);
        doNothing().when(mAppLockOverlayController).addLockedByAppLockTaskOverlay(any(), any(),
                anyInt());

        // WHEN we try to lock tasks for that package
        mAppLockOverlayController.lockActivitiesTasksForAppLock(TEST_PACKAGE_1,
                TEST_USER_ID_1);

        // THEN a task-level overlay is added
        verify(mAppLockOverlayController).addLockedByAppLockTaskOverlay(eq(task),
                eq(TEST_PACKAGE_1), eq(TEST_USER_ID_1));
        verify(mAppLockOverlayController, never()).addLockedByAppLockActivityOverlay(any());
    }

    @Test
    public void lockActivitiesTasksForAppLock_withInvisibleMixedTask_registersActivities() {
        // GIVEN an invisible task with multiple locked activities from the same package
        final TestMixedStateTask mixedStateTask = createMixedStateTask(/* isVisible=*/ false);
        final ActivityRecord topLockedActivity = new ActivityBuilder(mAtm)
                .setComponent(TEST_COMPONENT_1) // This activity's package is locked
                .setTask(mixedStateTask.mTask)
                .build();
        mixedStateTask.mTask.positionChildAt(POSITION_TOP, topLockedActivity,
                /* includingParents= */ false);
        // Ensure the task is not visible since adding a new activity may make it visible.
        mixedStateTask.mTask.setVisibleRequested(false);
        clearInvocations(mAppLockOverlayController);

        // WHEN we try to lock tasks for the locked package
        mAppLockOverlayController.lockActivitiesTasksForAppLock(TEST_PACKAGE_1,
                TEST_USER_ID_1);

        // THEN no overlay is added
        verify(mAppLockOverlayController, never()).addLockedByAppLockActivityOverlay(any());
        verify(mAppLockOverlayController, never()).addLockedByAppLockTaskOverlay(any(), any(),
                anyInt());
        // AND registers all activities belonging to the locked package
        verify(mAppLockOverlayController).registerActivity(eq(topLockedActivity));
        verify(mAppLockOverlayController).registerActivity(eq(mixedStateTask.mLockedActivity));
        verify(mAppLockOverlayController, never()).registerActivity(
                eq(mixedStateTask.mUnlockedActivity));
    }

    @Test
    public void lockActivitiesTasksForAppLock_withInvisibleTask_registersLockedActivities() {
        // GIVEN an invisible task where the top activity can show when locked
        final Task lockedTask = createLockedTask(/* isVisible= */ false);
        final int zOrderActivityTwoTop = 3;
        final int zOrderActivityOneMiddle = 2;
        final int zOrderActivityTwoBottom = 1;
        // 5 activities in the same task. Top one can showWhenLocked. 1, 3, 5 all belong to a locked
        // package and 2, 4 belong to an unlocked package
        final ActivityRecord topLockedActivityCanShowWhenLocked = new ActivityBuilder(mAtm)
                .setComponent(TEST_COMPONENT_1) // This activity's package is locked
                .setTask(lockedTask)
                .build();
        topLockedActivityCanShowWhenLocked.setShowWhenLocked(true);
        final ActivityRecord middleLockedActivity = new ActivityBuilder(mAtm)
                .setComponent(TEST_COMPONENT_1) // This activity's package is locked
                .setTask(lockedTask)
                .build();
        final ActivityRecord bottomLockedActivity = new ActivityBuilder(mAtm)
                .setComponent(TEST_COMPONENT_1) // This activity's package is locked
                .setTask(lockedTask)
                .build();
        final ActivityRecord topLockedActivity = new ActivityBuilder(mAtm)
                .setComponent(TEST_COMPONENT_2) // This activity's package is unlocked
                .setTask(lockedTask)
                .build();
        final ActivityRecord topUnlockedActivity = new ActivityBuilder(mAtm)
                .setComponent(TEST_COMPONENT_2) // This activity's package is unlocked
                .setTask(lockedTask)
                .build();

        // Position activities from top to bottom
        lockedTask.positionChildAt(POSITION_TOP, topLockedActivityCanShowWhenLocked, false);
        lockedTask.positionChildAt(zOrderActivityTwoTop, topUnlockedActivity, false);
        lockedTask.positionChildAt(zOrderActivityOneMiddle, middleLockedActivity, false);
        lockedTask.positionChildAt(zOrderActivityTwoBottom, topLockedActivity, false);
        lockedTask.positionChildAt(POSITION_BOTTOM, bottomLockedActivity, false);

        // Ensure the task is NOT visible so the controller proceeds to check activities
        lockedTask.setVisibleRequested(false);
        clearInvocations(mAppLockOverlayController);

        // WHEN we try to lock tasks for the locked package
        mAppLockOverlayController.lockActivitiesTasksForAppLock(TEST_PACKAGE_1, TEST_USER_ID_1);

        // THEN no overlay is added (because top activity is showWhenLocked)
        verify(mAppLockOverlayController, never()).addLockedByAppLockActivityOverlay(any());
        verify(mAppLockOverlayController, never()).addLockedByAppLockTaskOverlay(any(), any(),
                anyInt());
        // AND registers all activities belonging to the locked package
        verify(mAppLockOverlayController).registerActivity(eq(topLockedActivityCanShowWhenLocked));
        verify(mAppLockOverlayController).registerActivity(eq(bottomLockedActivity));
        verify(mAppLockOverlayController).registerActivity(eq(middleLockedActivity));
        verify(mAppLockOverlayController, never()).registerActivity(eq(topLockedActivity));
        verify(mAppLockOverlayController, never()).registerActivity(eq(topUnlockedActivity));
    }

    @Test
    public void lockActivitiesTasksForAppLock_withVisibleMixedTask_registersActivities() {
        // GIVEN a visible task with multiple locked activities from the same package
        final TestMixedStateTask mixedStateTask = createMixedStateTask(/* isVisible= */ true);
        final ActivityRecord topLockedActivity = new ActivityBuilder(mAtm)
                .setComponent(TEST_COMPONENT_1) // This activity's package is locked
                .setTask(mixedStateTask.mTask)
                .build();
        mixedStateTask.mTask.positionChildAt(POSITION_TOP, topLockedActivity,
                /* includingParents= */ false);
        final ActivityRecord topUnlockedActivity = new ActivityBuilder(mAtm)
                .setComponent(TEST_COMPONENT_2) // This activity's package is not locked
                .setTask(mixedStateTask.mTask)
                .build();
        final ActivityRecord middleCanShowWhenLockedActivity = new ActivityBuilder(mAtm)
                .setComponent(TEST_COMPONENT_1) // This activity's package is locked
                .setTask(mixedStateTask.mTask)
                .build();
        middleCanShowWhenLockedActivity.setShowWhenLocked(true);
        mixedStateTask.mTask.positionChildAt(POSITION_TOP, middleCanShowWhenLockedActivity,
                /* includingParents= */ false);
        mixedStateTask.mTask.positionChildAt(POSITION_TOP, topUnlockedActivity,
                /* includingParents= */ false);

        // Ensure the task is visible.
        mixedStateTask.mTask.setVisibleRequested(true);
        clearInvocations(mAppLockOverlayController);

        // WHEN we try to lock tasks for the locked package
        mAppLockOverlayController.lockActivitiesTasksForAppLock(TEST_PACKAGE_1, TEST_USER_ID_1);

        // THEN no overlay is added
        verify(mAppLockOverlayController, never()).addLockedByAppLockActivityOverlay(any());
        verify(mAppLockOverlayController, never()).addLockedByAppLockTaskOverlay(any(), any(),
                anyInt());
        // AND registers all activities belonging to the locked package
        verify(mAppLockOverlayController).registerActivity(eq(topLockedActivity));
        verify(mAppLockOverlayController).registerActivity(eq(mixedStateTask.mLockedActivity));
        verify(mAppLockOverlayController).registerActivity(middleCanShowWhenLockedActivity);
        verify(mAppLockOverlayController, never()).registerActivity(
                eq(mixedStateTask.mUnlockedActivity));
    }

    @Test
    public void lockActivitiesTasksForAppLock_withMixedTask_activityFinishing_doesNothing() {
        // GIVEN an invisible task with a mix of locked and unlocked activities
        final TestMixedStateTask mixedStateTask = createMixedStateTask(/* isVisible= */ false);
        final ActivityRecord lockedActivity = mixedStateTask.mLockedActivity;
        lockedActivity.finishing = true;

        // WHEN we try to lock tasks for the locked package
        mAppLockOverlayController.lockActivitiesTasksForAppLock(TEST_PACKAGE_1, TEST_USER_ID_1);

        // THEN no overlay is added and no activity is registered
        verify(mAppLockOverlayController, never()).addLockedByAppLockTaskOverlay(any(), any(),
                anyInt());
        verify(mAppLockOverlayController, never()).addLockedByAppLockActivityOverlay(any());
        verify(mAppLockOverlayController, never()).registerActivity(any());
    }

    @Test
    public void lockActivitiesTasksForAppLock_withMixedTask_canShowWhenLocked_registersActivity() {
        // GIVEN an invisible task with a mix of locked and unlocked activities
        final TestMixedStateTask mixedStateTask = createMixedStateTask(/* isVisible= */ false);
        final ActivityRecord lockedActivity = mixedStateTask.mLockedActivity;
        lockedActivity.setShowWhenLocked(true);

        // WHEN we try to lock tasks for the locked package
        mAppLockOverlayController.lockActivitiesTasksForAppLock(TEST_PACKAGE_1, TEST_USER_ID_1);

        // THEN no overlay is added and registers an activity because we may want to add an
        // overlay, just not for the showWhenLocked=true activity
        verify(mAppLockOverlayController, never()).addLockedByAppLockTaskOverlay(any(), any(),
                anyInt());
        verify(mAppLockOverlayController, never()).addLockedByAppLockActivityOverlay(any());
        verify(mAppLockOverlayController).registerActivity(eq(mixedStateTask.mLockedActivity));
        verify(mAppLockOverlayController, never()).registerActivity(
                eq(mixedStateTask.mUnlockedActivity));
    }

    @Test
    public void hasVisibleNonLockedTaskForPackage_withVisibleNonLockedTask_returnsTrue() {
        // GIVEN a visible task for the target package and user
        createLockedTask(/* isVisible= */ true);

        // WHEN checking for a visible task
        final boolean hasVisibleTask = mAppLockOverlayController.hasVisibleNonLockedTaskForPackage(
                TEST_PACKAGE_1, TEST_USER_ID_1);

        // THEN the result is true
        assertThat(hasVisibleTask).isTrue();
    }

    @Test
    public void hasVisibleNonLockedTaskForPackage_withInvisibleNonLockedTask_returnsFalse() {
        // GIVEN an invisible task for the target package and user
        createLockedTask(/* isVisible= */ false);

        // WHEN checking for a visible task
        final boolean hasVisibleTask = mAppLockOverlayController.hasVisibleNonLockedTaskForPackage(
                TEST_PACKAGE_1, TEST_USER_ID_1);

        // THEN the result is false
        assertThat(hasVisibleTask).isFalse();
    }

    @Test
    public void hasVisibleNonLockedTaskForPackage_withVisibleLockedTask_returnsFalse() {
        // GIVEN a visible task that is already locked (has overlay on top)
        final Task task = createLockedTask(/* isVisible= */ true);
        final ActivityRecord overlay = createOverlayActivity(task, TEST_PACKAGE_1, TEST_USER_ID_1);

        // Ensure the overlay is the top non-finishing activity
        task.positionChildAt(POSITION_TOP, overlay, /* includingParents= */ false);

        // WHEN checking for a visible non-locked task
        final boolean hasVisibleTask = mAppLockOverlayController.hasVisibleNonLockedTaskForPackage(
                TEST_PACKAGE_1, TEST_USER_ID_1);

        // THEN the result is false because the top activity is the lock overlay,
        // so the task is considered "locked"
        assertThat(hasVisibleTask).isFalse();
    }

    @Test
    public void hasVisibleNonLockedTaskForPackage_withDifferentUser_returnsFalse() {
        // GIVEN a visible task for a different user
        final Task task = createLockedTask(/* isVisible= */ true);
        task.mUserId = TEST_USER_ID_2;

        // WHEN checking for a visible task for the original user
        final boolean hasVisibleTask = mAppLockOverlayController.hasVisibleNonLockedTaskForPackage(
                TEST_PACKAGE_1, TEST_USER_ID_1);

        // THEN the result is false
        assertThat(hasVisibleTask).isFalse();
    }

    @Test
    public void hasVisibleNonLockedTaskForPackage_withDifferentPackage_returnsFalse() {
        // GIVEN a visible task for a different package
        final Task task = createLockedTask(/* isVisible= */ true);
        task.realActivity = TEST_COMPONENT_2; // Belongs to TEST_PACKAGE_2

        // WHEN checking for a visible task for the original package
        final boolean hasVisibleTask = mAppLockOverlayController.hasVisibleNonLockedTaskForPackage(
                TEST_PACKAGE_1, TEST_USER_ID_1);

        // THEN the result is false
        assertThat(hasVisibleTask).isFalse();
    }

    @Test
    public void hasVisibleNonLockedTaskForPackage_withNullRealActivity_returnsFalse() {
        // GIVEN a visible task with a null realActivity
        final Task task = createLockedTask(/* isVisible= */ true);
        task.realActivity = null;

        // WHEN checking for a visible task
        final boolean hasVisibleTask = mAppLockOverlayController.hasVisibleNonLockedTaskForPackage(
                TEST_PACKAGE_1, TEST_USER_ID_1);

        // THEN the result is false
        assertThat(hasVisibleTask).isFalse();
    }

    @Test
    public void hasVisibleNonLockedTaskForPackage_withOnlyFinishingActivities_returnsFalse() {
        // GIVEN a visible task where all activities are finishing
        final Task task = createLockedTask(/* isVisible= */ true);
        task.forAllActivities((Predicate<ActivityRecord>) activity -> activity.finishing = true);

        // WHEN checking for a visible task
        final boolean hasVisibleTask = mAppLockOverlayController.hasVisibleNonLockedTaskForPackage(
                TEST_PACKAGE_1, TEST_USER_ID_1);

        // THEN the result is false
        assertThat(hasVisibleTask).isFalse();
    }

    @Test
    public void addLockedByAppLockTaskOverlay_emptyTask_doesNothing() {
        // GIVEN an empty task
        final Task task = getTestTask();
        task.mChildren.clear();
        spyOn(mSupervisor);

        // WHEN a task overlay is added
        mAppLockOverlayController.addLockedByAppLockTaskOverlay(task, TEST_PACKAGE_1,
                TEST_USER_ID_1);
        waitHandlerIdle(mAtm.mH);

        // THEN nothing happens
        verify(mAtm, never()).startActivityAsUser(any(), any(), any(), any(), any(), any(), any(),
                anyInt(), anyInt(), any(), any(), eq(TEST_USER_ID_1));
        verify(mSupervisor, never()).removeTask(eq(task), anyBoolean(), anyBoolean(), anyString());
    }

    @Test
    public void addLockedByAppLockTaskOverlay_startsLockedAppActivity() {
        // GIVEN a task and a successful activity start result
        final Task task = getTestTask();
        doReturn(ActivityManager.START_SUCCESS).when(mAtm).startActivityAsUser(any(), any(), any(),
                any(), any(), any(), any(), anyInt(), anyInt(), any(), any(), anyInt());

        // WHEN a task overlay is added
        mAppLockOverlayController.addLockedByAppLockTaskOverlay(task, TEST_PACKAGE_1,
                TEST_USER_ID_1);
        waitHandlerIdle(mAtm.mH);

        // THEN the LockedAppActivity is started with the correct options
        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        ArgumentCaptor<android.os.Bundle> optionsCaptor =
                ArgumentCaptor.forClass(android.os.Bundle.class);
        verify(mAtm).startActivityAsUser(any(), any(), any(), intentCaptor.capture(), any(), any(),
                any(), anyInt(), anyInt(), any(), optionsCaptor.capture(), eq(TEST_USER_ID_1));

        final Intent intent = intentCaptor.getValue();
        final Intent expectedIntent = LockedAppActivity.createLockedAppActivityIntent(
                TEST_PACKAGE_1, TEST_USER_ID_1, /* target= */ null);
        assertThat(intent.getComponent()).isEqualTo(expectedIntent.getComponent());
        // Verify that task overlay intent has the required flags
        final int expectedFlags =
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_CLEAR_TOP;
        assertThat(intent.getFlags() & expectedFlags).isEqualTo(expectedFlags);

        final ActivityOptions options = new ActivityOptions(optionsCaptor.getValue());
        assertThat(options.getLaunchTaskId()).isEqualTo(task.mTaskId);
        assertThat(options.getTaskOverlay()).isTrue();
    }

    @Test
    public void addLockedByAppLockTaskOverlay_removesTaskOnOverlayLaunchFailure() {
        // GIVEN a task and a failed activity start result
        final Task task = getTestTask();
        doReturn(ActivityManager.START_CANCELED).when(mAtm).startActivityAsUser(any(), any(), any(),
                any(), any(), any(), any(), anyInt(), anyInt(), any(), any(), anyInt());
        spyOn(mSupervisor);

        // WHEN a task overlay is added
        mAppLockOverlayController.addLockedByAppLockTaskOverlay(task, TEST_PACKAGE_1,
                TEST_USER_ID_1);
        waitHandlerIdle(mAtm.mH);

        // THEN the task is removed as a fallback
        verify(mSupervisor).removeTask(eq(task), anyBoolean(), anyBoolean(), anyString());
    }

    @Test
    public void addLockedByAppLockTaskOverlay_withExistingOverlay_doesNothing() {
        // GIVEN a task with an existing overlay for the same package
        final Task task = getTestTask();
        createOverlayActivity(task, TEST_PACKAGE_1, TEST_USER_ID_1);
        spyOn(mSupervisor);

        // WHEN a task overlay is added
        mAppLockOverlayController.addLockedByAppLockTaskOverlay(task, TEST_PACKAGE_1,
                TEST_USER_ID_1);
        waitHandlerIdle(mAtm.mH);

        // THEN nothing happens because an overlay already exists
        verify(mAtm, never()).startActivityAsUser(any(), any(), any(), any(), any(), any(), any(),
                anyInt(), anyInt(), any(), any(), eq(TEST_USER_ID_1));
        verify(mSupervisor, never()).removeTask(eq(task), anyBoolean(), anyBoolean(), anyString());
    }

    @Test
    public void addLockedByAppLockActivityOverlay_withTargetOnTop_addsOverlayOnTop() {
        // GIVEN a task with a target activity
        final Task task = getTestTask();
        final ActivityRecord targetActivity = task.getTopMostActivity();

        // Mock startActivityAsUser to actually add the overlay activity to the task
        final ActivityRecord[] overlayHolder = mockStartActivityToAddTask(task, TEST_PACKAGE_1,
                TEST_USER_ID_1);

        // WHEN an activity overlay is added
        mAppLockOverlayController.addLockedByAppLockActivityOverlay(targetActivity);
        waitHandlerIdle(mAtm.mH);

        // THEN the overlay is positioned on top
        verify(mAtm).startActivityAsUser(any(), any(), any(), any(), any(), any(), any(), anyInt(),
                anyInt(), any(), any(), anyInt());
        final TaskFragment parent = targetActivity.getParent().asTaskFragment();
        assertThat(parent.mChildren.size()).isEqualTo(2);
        assertThat(parent.mChildren.get(0)).isEqualTo(targetActivity);
        assertThat(overlayHolder.length).isEqualTo(1);
        final ActivityRecord overlayActivity = overlayHolder[0];
        assertThat(overlayActivity).isNotNull();
        assertThat(parent.mChildren.get(1)).isEqualTo(overlayActivity);
        assertThat(overlayActivity.mActivityComponent).isNotNull();
        assertThat(overlayActivity.mActivityComponent.getPackageName()).isEqualTo(
                SYSTEM_PACKAGE_NAME);
        assertThat(overlayActivity.mActivityComponent.getClassName()).isEqualTo(
                LockedAppActivity.class.getName());

        // And both the overlay and target activities are registered
        verify(mAppLockOverlayController).registerActivity(eq(targetActivity));
        verify(mAppLockOverlayController).registerActivity(eq(overlayActivity));
    }

    @Test
    public void addLockedByAppLockActivityOverlay_withTargetNotOnTop_addsOverlayOnTop() {
        // GIVEN a task with a middle TaskFragment, and the target activity in the TaskFragment.
        final Task task = getTestTask();
        final TaskFragment taskFragment = new TaskFragmentBuilder(mAtm).setParentTask(task).build();
        new ActivityBuilder(mAtm).setTask(task).build();
        final ActivityRecord targetActivity = new ActivityBuilder(mAtm).setTask(task).build();
        targetActivity.reparent(taskFragment, POSITION_TOP);

        // Mock startActivityAsUser to add overlay to the Task.
        final ActivityRecord[] overlayHolder = mockStartActivityToAddTask(task, TEST_PACKAGE_1,
                TEST_USER_ID_1);

        // WHEN an activity overlay is added.
        mAppLockOverlayController.addLockedByAppLockActivityOverlay(targetActivity);
        waitHandlerIdle(mAtm.mH);

        // THEN the overlay stays at the top of the task.
        verify(mAtm).startActivityAsUser(any(), any(), any(), any(), any(), any(), any(), anyInt(),
                anyInt(), any(), any(), anyInt());
        assertThat(overlayHolder.length).isEqualTo(1);
        final ActivityRecord overlayActivity = overlayHolder[0];
        assertThat(overlayActivity).isNotNull();
        assertThat(task.getTopMostActivity()).isEqualTo(overlayActivity);

        // And both the overlay and target activities are registered
        verify(mAppLockOverlayController).registerActivity(eq(targetActivity));
        verify(mAppLockOverlayController).registerActivity(eq(overlayActivity));
    }

    @Test
    public void addLockedByAppLockActivityOverlay_activityFinishing_doesNothing() {
        // GIVEN a target activity that is already finishing
        final Task task = getTestTask();
        final ActivityRecord targetActivity = task.getTopMostActivity();
        targetActivity.finishing = true;
        spyOn(targetActivity);

        // WHEN an activity overlay is added
        mAppLockOverlayController.addLockedByAppLockActivityOverlay(targetActivity);
        waitHandlerIdle(mAtm.mH);

        // THEN the method returns early and does not attempt to finish the activity
        verify(targetActivity, never()).finishIfPossible(anyString(), anyBoolean());
    }

    @Test
    public void addLockedByAppLockActivityOverlay_withNoTask_finishesActivity() {
        // GIVEN an activity that is not attached to any task
        final ActivityRecord targetActivity = new ActivityBuilder(mAtm).setTask(null).build();
        spyOn(targetActivity);

        // WHEN an activity overlay is added
        mAppLockOverlayController.addLockedByAppLockActivityOverlay(targetActivity);
        waitHandlerIdle(mAtm.mH);

        // THEN the method returns early and does not attempt to finish the activity
        verify(targetActivity).finishIfPossible(eq("could-not-find-task"), anyBoolean());
    }

    @Test
    public void addLockedByAppLockActivityOverlay_withExistingOverlay_doesNothing() {
        // GIVEN a task with a target activity and an existing valid overlay on top of it
        final OverlayTargetPair pair = createValidOverlayTargetPair();
        final ActivityRecord targetActivity = pair.mTarget;

        spyOn(targetActivity);
        spyOn(mSupervisor);

        // WHEN an activity overlay is added for the target activity (which already has a valid
        // overlay)
        mAppLockOverlayController.addLockedByAppLockActivityOverlay(targetActivity);
        waitHandlerIdle(mAtm.mH);

        // THEN the method returns early and does not attempt to start a new activity or remove the
        // task
        verify(mAtm, never()).startActivityAsUser(any(), any(), any(), any(), any(), any(), any(),
                anyInt(), anyInt(), any(), any(), anyInt());
        verify(mSupervisor, never()).removeTask(eq(pair.mTask), anyBoolean(), anyBoolean(),
                anyString());
    }

    @Test
    public void addLockedByAppLockActivityOverlay_withExistingFinishingOverlay_doesNothing() {
        // GIVEN a task with a target activity and an existing overlay that is finishing
        final OverlayTargetPair pair = createValidOverlayTargetPair();
        final ActivityRecord targetActivity = pair.mTarget;
        final ActivityRecord existingOverlay = pair.mOverlay;
        existingOverlay.finishing = true; // Mark as finishing

        spyOn(mSupervisor);

        // WHEN an activity overlay is added for the target activity
        mAppLockOverlayController.addLockedByAppLockActivityOverlay(targetActivity);
        waitHandlerIdle(mAtm.mH);

        // THEN the method returns early and does not attempt to start a new activity
        verify(mAtm, never()).startActivityAsUser(any(), any(), any(), any(), any(), any(), any(),
                anyInt(), anyInt(), any(), any(), anyInt());
    }

    @Test
    public void addLockedByAppLockActivityOverlay_finishesTargetIfOverlayNotFound() {
        // GIVEN a task where the overlay activity cannot be found after launch
        final Task task = getTestTask();
        final ActivityRecord targetActivity = task.getTopMostActivity();

        // Mock startActivityAsUser to success but DO NOT add activity
        doReturn(ActivityManager.START_SUCCESS).when(mAtm).startActivityAsUser(any(), any(), any(),
                any(), any(), any(), any(), anyInt(), anyInt(), any(), any(), anyInt());

        spyOn(targetActivity);

        // WHEN an activity overlay is added
        mAppLockOverlayController.addLockedByAppLockActivityOverlay(targetActivity);
        waitHandlerIdle(mAtm.mH);

        // THEN the target activity is finished as a fallback
        verify(targetActivity).finishIfPossible(eq("could-not-find-overlay"), anyBoolean());
    }

    @Test
    public void addLockedByAppLockActivityOverlay_finishesActivityOnOverlayLaunchFailure() {
        // GIVEN a task with a target activity
        final Task task = getTestTask();
        final ActivityRecord targetActivity = task.getTopMostActivity();
        spyOn(targetActivity);

        // Mock startActivityAsUser to fail
        doReturn(ActivityManager.START_CANCELED).when(mAtm).startActivityAsUser(any(), any(), any(),
                any(), any(), any(), any(), anyInt(), anyInt(), any(), any(), anyInt());
        spyOn(mSupervisor);

        // WHEN an activity overlay is added but fails to start
        mAppLockOverlayController.addLockedByAppLockActivityOverlay(targetActivity);
        waitHandlerIdle(mAtm.mH);

        // THEN the target activity is finished as a security fallback
        verify(targetActivity).finishIfPossible(eq("app-lock-overlay-failed"), anyBoolean());
        // AND the task is NOT removed
        verify(mSupervisor, never()).removeTask(eq(task), anyBoolean(), anyBoolean(), anyString());
    }

    @Test
    public void addLockedByAppLockActivityOverlay_finishesOverlayIfTargetFinishing() {
        // GIVEN a task with a target activity
        final Task task = getTestTask();
        final ActivityRecord targetActivity = task.getTopMostActivity();

        // Capture the overlay identifier to verify it's finished later
        final StringBuilder overlayIdentifier = new StringBuilder();

        // Mock startActivityAsUser
        doAnswer(invocation -> {
            Intent intent = invocation.getArgument(3);
            overlayIdentifier.append(intent.getIdentifier());

            // Mark target as finishing *before* the overlay is created/processed
            targetActivity.finishing = true;

            synchronized (mAtm.mGlobalLock) {
                ActivityRecord overlay = new ActivityBuilder(mAtm)
                        .setComponent(new ComponentName("android",
                                LockedAppActivity.class.getName()))
                        .setTask(task).build();
                overlay.intent.setIdentifier(intent.getIdentifier());
                spyOn(overlay);
            }
            return ActivityManager.START_SUCCESS;
        }).when(mAtm).startActivityAsUser(any(), any(), any(), any(),
                any(), any(), any(), anyInt(), anyInt(), any(), any(), anyInt());

        // WHEN an activity overlay is added
        mAppLockOverlayController.addLockedByAppLockActivityOverlay(targetActivity);
        waitHandlerIdle(mAtm.mH);

        // THEN the overlay activity is finished
        final ActivityRecord overlay = task.getActivity(
                activity -> overlayIdentifier.toString().equals(activity.intent.getIdentifier()));
        verify(overlay).finishIfPossible(eq("target-finishing"), anyBoolean());
    }

    @Test
    public void getPackagesWithVisibleAppLockOverlay() {
        final Task task = getTestTask();
        // Create mock activities
        ActivityRecord overlayActivity1 = createOverlayActivity(task, TEST_PACKAGE_1,
                TEST_USER_ID_1);
        ActivityRecord overlayActivity2 = createOverlayActivity(task, TEST_PACKAGE_2,
                TEST_USER_ID_1);
        ActivityRecord regularActivity = new ActivityBuilder(mAtm)
                .setComponent(TEST_COMPONENT_3)
                .build();
        ActivityRecord overlayActivityUser2 = createOverlayActivity(task, "some.other.package.1",
                TEST_USER_ID_2);
        ActivityRecord overlayActivityNotFromSystem = new ActivityBuilder(mAtm)
                .setComponent(new ComponentName("some.other.package.2",
                        LockedAppActivity.class.getName()))
                .build();

        final List<ActivityRecord> visibleActivities = new ArrayList<>();
        visibleActivities.add(overlayActivity1);
        visibleActivities.add(overlayActivity2);
        visibleActivities.add(regularActivity);
        visibleActivities.add(overlayActivityUser2);
        visibleActivities.add(overlayActivityNotFromSystem);

        doReturn(visibleActivities).when(mRootWindowContainer).getTopVisibleActivities(
                Display.INVALID_DISPLAY);

        final Set<String> result = mAppLockOverlayController
                .getPackagesWithVisibleAppLockOverlay(TEST_USER_ID_1);

        assertThat(result).isNotNull();
        assertThat(result).containsExactly(TEST_PACKAGE_1, TEST_PACKAGE_2);
        assertThat(result).doesNotContain(TEST_PACKAGE_3);
        assertThat(result).doesNotContain("some.other.package.1");
        assertThat(result).doesNotContain("some.other.package.2");
        verify(mRootWindowContainer).getTopVisibleActivities(Display.INVALID_DISPLAY);
    }

    @Test
    public void registerActivity_listenerSelfUnregistersOnActivityRemoved() {
        // GIVEN a registered activity
        final ActivityRecord activity = getTestTask().getTopMostActivity();
        spyOn(activity);
        mAppLockOverlayController.registerActivity(activity);

        // WHEN the activity is removed
        activity.removeIfPossible();

        // THEN the listener is unregistered from the activity
        verify(activity).unregisterWindowContainerListener(any(WindowContainerListener.class));
    }

    @Test
    public void registerActivity_alreadyRegistered_doesNothing() {
        // GIVEN an activity that is already registered
        final ActivityRecord activity = getTestTask().getTopMostActivity();
        spyOn(activity);
        mAppLockOverlayController.registerActivity(activity);
        clearInvocations(activity);

        // WHEN we try to register it again
        mAppLockOverlayController.registerActivity(activity);

        // THEN no new listener is registered
        verify(activity, never()).registerWindowContainerListener(any());
    }

    @Test
    public void onOverlayActivityBecomesVisible_notAnOrphan_doesNothing() {
        // GIVEN a registered overlay that is NOT an orphan (it has a valid target)
        final OverlayTargetPair pair = createValidOverlayTargetPair();
        final ActivityRecord overlay = pair.mOverlay;
        overlay.setVisibleRequested(false);
        clearInvocations(overlay);

        // WHEN the overlay becomes visible
        overlay.setVisibleRequested(true);

        // THEN the overlay is NOT finished because it is not an orphan
        verify(overlay, never()).finishIfPossible(anyString(), anyBoolean());
    }

    @Test
    public void onOverlayActivityBecomesVisible_orphan_finishesOverlay() {
        // GIVEN a registered overlay whose target has been removed (is null)
        final Task task = getTestTask();
        final ActivityRecord overlay = createOverlayActivity(task, TEST_PACKAGE_1,
                TEST_USER_ID_1);
        mAppLockOverlayController.registerActivity(overlay);
        // Simulate the target being removed by not adding it to the overlay -> target map
        overlay.setVisibleRequested(false);
        clearInvocations(overlay);

        // WHEN the overlay becomes visible
        overlay.setVisibleRequested(true);

        // THEN the overlay is finished because it is an orphan
        verify(overlay).finishIfPossible(eq("orphaned-app-lock-overlay"), anyBoolean());
    }

    @Test
    public void onOverlayActivityBecomesVisible_targetNotLocked_finishesOverlay() {
        // GIVEN a registered overlay with a valid target, but the package is no longer locked
        final OverlayTargetPair pair = createValidOverlayTargetPair();
        final ActivityRecord overlay = pair.mOverlay;
        overlay.setVisibleRequested(false);
        clearInvocations(overlay);

        doReturn(false).when(mWm).isPackageLockedByAppLockLocked(pair.mTarget.packageName,
                pair.mTarget.mUserId);

        // WHEN the overlay becomes visible
        overlay.setVisibleRequested(true);

        // THEN the overlay is finished because its target is no longer locked
        verify(overlay).finishIfPossible(eq("orphaned-app-lock-overlay"), anyBoolean());
    }

    @Test
    public void onTargetActivityBecomesVisible_addsOverlay() {
        // GIVEN a registered locked activity that is not visible
        final TestMixedStateTask mixedStateTask = createMixedStateTask(/* isVisible= */ false);
        final ActivityRecord targetActivity = mixedStateTask.mLockedActivity;
        targetActivity.setVisibleRequested(false);

        mAppLockOverlayController.registerActivity(targetActivity);
        doNothing().when(mAppLockOverlayController).addLockedByAppLockActivityOverlay(any());
        clearInvocations(mAppLockOverlayController);

        // WHEN the target activity becomes visible
        targetActivity.setVisibleRequested(true);
        waitHandlerIdle(mAtm.mH);

        // THEN an overlay is added for it
        verify(mAppLockOverlayController).addLockedByAppLockActivityOverlay(eq(targetActivity));
    }

    @Test
    public void onTargetActivityBecomesVisible_canShowWhenLocked_doesNotAddOverlay() {
        // GIVEN a registered locked activity that is not visible and can show when locked
        final TestMixedStateTask mixedStateTask = createMixedStateTask(/* isVisible= */ false);
        final ActivityRecord targetActivity = mixedStateTask.mLockedActivity;
        targetActivity.setVisibleRequested(false);
        targetActivity.setShowWhenLocked(true);

        mAppLockOverlayController.registerActivity(targetActivity);
        clearInvocations(mAppLockOverlayController);

        // WHEN the target activity becomes visible
        targetActivity.setVisibleRequested(true);
        waitHandlerIdle(mAtm.mH);

        // THEN an overlay is NOT added
        verify(mAppLockOverlayController, never()).addLockedByAppLockActivityOverlay(any());
    }

    @Test
    public void onTargetActivityBecomesVisible_withOtherVisibleTask_doesNotAddOverlay() {
        // GIVEN a registered locked activity that is not visible
        final TestMixedStateTask mixedStateTask = createMixedStateTask(/* isVisible= */ false);
        final ActivityRecord targetActivity = mixedStateTask.mLockedActivity;
        targetActivity.setVisibleRequested(false);
        mAppLockOverlayController.registerActivity(targetActivity);

        // AND another visible task for the same package exists
        createLockedTask(/* isVisible= */ true);
        clearInvocations(mAppLockOverlayController);

        // WHEN the target activity becomes visible
        targetActivity.setVisibleRequested(true);

        // THEN an overlay is NOT added because there's another visible task for that package
        verify(mAppLockOverlayController, never()).addLockedByAppLockActivityOverlay(any());
    }

    @Test
    public void onActivityRemoved_finishesPartnerAndCleansUp() {
        // GIVEN a registered target activity and its overlay
        final OverlayTargetPair pair = createValidOverlayTargetPair();
        final ActivityRecord targetActivity = pair.mTarget;
        final ActivityRecord overlay = pair.mOverlay;

        // WHEN the target activity is removed
        targetActivity.removeIfPossible();

        // THEN the overlay is finished
        verify(overlay).finishIfPossible(eq("cleaning-up-app-lock-pair"), anyBoolean());
        // AND both listeners are unregistered
        verify(targetActivity).unregisterWindowContainerListener(
                any(WindowContainerListener.class));
        verify(overlay).unregisterWindowContainerListener(any(WindowContainerListener.class));
    }

    @Test
    public void isActivityLockedByAppLock_packageLocked_hasVisibleTask_returnsFalse() {
        final ActivityRecord targetActivity = new ActivityBuilder(mAtm)
                .setComponent(TEST_COMPONENT_1) // This activity's package is locked
                .setVisible(true)
                .setTask(createLockedTask(/* isVisible= */ true))
                .build();
        targetActivity.setVisibleRequested(true);

        assertThat(mAppLockOverlayController.isActivityLockedByAppLock(targetActivity)).isFalse();
    }

    @Test
    public void isActivityLockedByAppLock_activityFinishing_returnsFalse() {
        final ActivityRecord targetActivity = new ActivityBuilder(mAtm)
                .setComponent(TEST_COMPONENT_1) // This activity's package is locked
                .setVisible(false)
                .setTask(createLockedTask(/* isVisible= */ false))
                .build();
        targetActivity.finishing = true;

        assertThat(mAppLockOverlayController.isActivityLockedByAppLock(targetActivity)).isFalse();
    }

    @Test
    public void isActivityLockedByAppLock_packageNotLocked_returnsFalse() {
        final ActivityRecord activity = new ActivityBuilder(mAtm)
                .setComponent(TEST_COMPONENT_1)
                .setVisible(false)
                .setTask(createLockedTask(/* isVisible= */ false))
                .build();

        doReturn(false).when(mWm).isPackageLockedByAppLockLocked(activity.packageName,
                activity.mUserId);

        assertThat(mAppLockOverlayController.isActivityLockedByAppLock(activity)).isFalse();
    }

    @Test
    public void isActivityLockedByAppLock_packageLocked_canShowWhenLocked_returnsFalse() {
        final ActivityRecord targetActivity = new ActivityBuilder(mAtm)
                .setComponent(TEST_COMPONENT_1) // This activity's package is locked
                .setVisible(false)
                .setTask(createLockedTask(/* isVisible= */ false))
                .build();
        targetActivity.setShowWhenLocked(true);

        assertThat(mAppLockOverlayController.isActivityLockedByAppLock(targetActivity)).isFalse();
    }

    @Test
    public void isActivityLockedByAppLock_packageLocked_noVisibleTask_returnsTrue() {
        final ActivityRecord targetActivity = new ActivityBuilder(mAtm)
                .setComponent(TEST_COMPONENT_1) // This activity's package is locked
                .setVisible(false)
                .setTask(createLockedTask(/* isVisible= */ false))
                .build();

        assertThat(mAppLockOverlayController.isActivityLockedByAppLock(targetActivity)).isTrue();
    }

    @Test
    public void isAppLockOverlayValidForTarget_targetPackageNot_returnsFalse() {
        // GIVEN a valid overlay-target pair, but the target package is no longer locked
        final OverlayTargetPair pair = createValidOverlayTargetPair();
        doReturn(false).when(mWm).isPackageLockedByAppLockLocked(pair.mTarget.packageName,
                pair.mTarget.mUserId);

        // WHEN checking if the overlay is valid for the target
        final boolean isValid = mAppLockOverlayController.isAppLockOverlayValidForTarget(
                pair.mOverlay, pair.mTarget);

        // THEN it is not considered valid
        assertThat(isValid).isFalse();
    }

    @Test
    public void isAppLockOverlayValidForTarget_overlayForDifferentPackage_returnsFalse() {
        // GIVEN a valid overlay-target pair, but the overlay's package is different
        final OverlayTargetPair pair = createValidOverlayTargetPair();
        pair.mOverlay.intent.putExtra(Intent.EXTRA_PACKAGE_NAME, TEST_PACKAGE_2);

        // WHEN checking if the overlay is valid for the target
        final boolean isValid = mAppLockOverlayController.isAppLockOverlayValidForTarget(
                pair.mOverlay, pair.mTarget);

        // THEN it is not considered valid
        assertThat(isValid).isFalse();
    }

    @Test
    public void isAppLockOverlayValidForTarget_tasksMismatch_returnsFalse() {
        // GIVEN a valid overlay-target pair, but the overlay's task is different
        final OverlayTargetPair pair = createValidOverlayTargetPair();
        final Task otherTask = getTestTask();
        spyOn(pair.mTarget);
        doReturn(otherTask).when(pair.mTarget).getTask();

        // WHEN checking if the overlay is valid for the target
        final boolean isValid = mAppLockOverlayController.isAppLockOverlayValidForTarget(
                pair.mOverlay, pair.mTarget);

        // THEN it is not considered valid
        assertThat(isValid).isFalse();
    }

    @Test
    public void isAppLockOverlayValidForTarget_targetTaskNull_returnsFalse() {
        // GIVEN a valid overlay-target pair, but the target activity's task is null
        final OverlayTargetPair pair = createValidOverlayTargetPair();
        spyOn(pair.mTarget);
        doReturn(null).when(pair.mTarget).getTask();

        // WHEN checking if the overlay is valid for the target
        final boolean isValid = mAppLockOverlayController.isAppLockOverlayValidForTarget(
                pair.mOverlay, pair.mTarget);

        // THEN it is not considered valid
        assertThat(isValid).isFalse();
    }

    @Test
    public void isAppLockOverlayValidForTarget_mapReferenceMismatch_returnsFalse() {
        // GIVEN a valid overlay-target pair, but one of the map references is incorrect
        final OverlayTargetPair pair = createValidOverlayTargetPair();
        final ActivityRecord otherActivity = new ActivityBuilder(mAtm).build();
        mAppLockOverlayController.mOverlayToTargetMap.put(pair.mOverlay,
                new WeakReference<>(otherActivity));

        // WHEN checking if the overlay is valid for the target
        final boolean isValid = mAppLockOverlayController.isAppLockOverlayValidForTarget(
                pair.mOverlay, pair.mTarget);

        // THEN it is not considered valid
        assertThat(isValid).isFalse();
    }

    @Test
    public void isAppLockOverlayValidForTarget_targetNotInMap_returnsFalse() {
        // GIVEN a valid overlay-target pair, but the target is removed from the map
        final OverlayTargetPair pair = createValidOverlayTargetPair();
        mAppLockOverlayController.mTargetToOverlayMap.remove(pair.mTarget);

        // WHEN checking if the overlay is valid for the target
        final boolean isValid = mAppLockOverlayController.isAppLockOverlayValidForTarget(
                pair.mOverlay, pair.mTarget);

        // THEN it is not considered valid
        assertThat(isValid).isFalse();
    }

    @Test
    public void isAppLockOverlayValidForTarget_overlayNotInMap_returnsFalse() {
        // GIVEN a valid overlay-target pair, but the overlay is removed from the map
        final OverlayTargetPair pair = createValidOverlayTargetPair();
        mAppLockOverlayController.mOverlayToTargetMap.remove(pair.mOverlay);

        // WHEN checking if the overlay is valid for the target
        final boolean isValid = mAppLockOverlayController.isAppLockOverlayValidForTarget(
                pair.mOverlay, pair.mTarget);

        // THEN it is not considered valid
        assertThat(isValid).isFalse();
    }

    @Test
    public void isAppLockOverlayValidForTarget_targetFinishing_returnsFalse() {
        // GIVEN a valid overlay-target pair, but the target is finishing
        final OverlayTargetPair pair = createValidOverlayTargetPair();
        pair.mTarget.finishing = true;

        // WHEN checking if the overlay is valid for the target
        final boolean isValid = mAppLockOverlayController.isAppLockOverlayValidForTarget(
                pair.mOverlay, pair.mTarget);

        // THEN it is not considered valid
        assertThat(isValid).isFalse();
    }

    @Test
    public void isAppLockOverlayValidForTarget_overlayFinishing_returnsFalse() {
        // GIVEN a valid overlay-target pair, but the overlay is finishing
        final OverlayTargetPair pair = createValidOverlayTargetPair();
        pair.mOverlay.finishing = true;

        // WHEN checking if the overlay is valid for the target
        final boolean isValid = mAppLockOverlayController.isAppLockOverlayValidForTarget(
                pair.mOverlay, pair.mTarget);

        // THEN it is not considered valid
        assertThat(isValid).isFalse();
    }

    /** Helper to create a basic task for testing. */
    private Task getTestTask() {
        return new TaskBuilder(mSupervisor).setCreateActivity(true).build();
    }

    /** Helper to create a task associated with a locked package. */
    private Task createLockedTask(boolean isVisible) {
        final Task task = getTestTask();
        task.mUserId = TEST_USER_ID_1;
        task.realActivity = TEST_COMPONENT_1;
        if (!isVisible) {
            task.setVisibleRequested(false);
        }
        doReturn(true).when(mWm).isPackageLockedByAppLockLocked(TEST_PACKAGE_1, TEST_USER_ID_1);
        clearInvocations(mAppLockOverlayController);
        return task;
    }

    /**
     * Helper to create a task whose owner package is not locked, but contains both a locked and an
     * unlocked activity.
     */
    private TestMixedStateTask createMixedStateTask(boolean isVisible) {
        final Task task = new TaskBuilder(mSupervisor).setCreateActivity(false).build();
        task.realActivity = TEST_COMPONENT_2; // Main package is not locked
        task.mUserId = TEST_USER_ID_1;

        final ActivityRecord unlockedActivity = new ActivityBuilder(mAtm)
                .setComponent(TEST_COMPONENT_2)
                .setTask(task)
                .build();
        final ActivityRecord lockedActivity = new ActivityBuilder(mAtm)
                .setComponent(TEST_COMPONENT_1) // This activity's package is locked
                .setTask(task)
                .build();

        doReturn(false).when(mWm).isPackageLockedByAppLockLocked(TEST_PACKAGE_2, TEST_USER_ID_1);
        doReturn(true).when(mWm).isPackageLockedByAppLockLocked(TEST_PACKAGE_1, TEST_USER_ID_1);

        if (!isVisible) {
            task.setVisibleRequested(false);
        }
        clearInvocations(mAppLockOverlayController);

        return new TestMixedStateTask(task, lockedActivity, unlockedActivity);
    }

    /** Helper to create a mock overlay activity and add it to a task. */
    private ActivityRecord createOverlayActivity(Task task, String packageName, int userId) {
        final Intent intent = LockedAppActivity.createLockedAppActivityIntent(packageName, userId,
                /* target= */ null);
        final ActivityRecord overlay = new ActivityBuilder(mAtm)
                .setComponent(intent.getComponent())
                .setTask(task)
                .setIntentExtras(intent.getExtras())
                .build();
        spyOn(overlay);
        doReturn(true).when(overlay).isTaskOverlay();
        return overlay;
    }

    /**
     * Mocks a call to
     * {@link ActivityTaskManagerService#startActivityAsUser(IApplicationThread, String, String,
     * Intent, String, IBinder, String, int, int, ProfilerInfo, Bundle, int)} to add a new
     * {@link LockedAppActivity} to the specified task and position within the task.
     *
     * @return a one element array containing the overlay activity
     */
    private ActivityRecord[] mockStartActivityToAddTask(Task task, String packageName, int userId) {
        // Capture the overlay to verify its interactions
        final ActivityRecord[] overlayHolder = new ActivityRecord[1];
        doAnswer(invocation -> {
            Intent intent = invocation.getArgument(3);
            String identifier = intent.getIdentifier();
            // Create overlay and ensure it has the correct identifier and component
            synchronized (mAtm.mGlobalLock) {
                final ActivityRecord overlay = createOverlayActivity(task, packageName, userId);
                overlay.intent.setIdentifier(identifier);
                spyOn(overlay);
                overlayHolder[0] = overlay;
            }
            return ActivityManager.START_SUCCESS;
        }).when(mAtm).startActivityAsUser(any(), any(), any(), any(),
                any(), any(), any(), anyInt(), anyInt(), any(), any(), anyInt());
        return overlayHolder;
    }

    /** Helper to create a valid overlay and target activity pair. */
    private OverlayTargetPair createValidOverlayTargetPair() {
        final Task task = getTestTask();
        final ActivityRecord targetActivity = task.getTopMostActivity();
        // Add a dummy activity to keep the task alive when the target is removed.
        new ActivityBuilder(mAtm).setTask(task).build();
        final ActivityRecord overlay = createOverlayActivity(task, targetActivity.packageName,
                targetActivity.mUserId);
        task.positionChildAt(task.mChildren.indexOf(targetActivity) + 1, overlay, false);

        // Manually add to maps and register listeners for isAppLockOverlayValidForTargetLocked
        // tests, since addLockedByAppLockActivityOverlayLocked is not called here.
        mAppLockOverlayController.mOverlayToTargetMap.put(overlay,
                new WeakReference<>(targetActivity));
        mAppLockOverlayController.mTargetToOverlayMap.put(targetActivity,
                new WeakReference<>(overlay));
        mAppLockOverlayController.registerActivity(overlay);
        mAppLockOverlayController.registerActivity(targetActivity);

        doReturn(true).when(mWm).isPackageLockedByAppLockLocked(targetActivity.packageName,
                targetActivity.mUserId);
        clearInvocations(mAppLockOverlayController);
        return new OverlayTargetPair(overlay, targetActivity, task);
    }

    /** Data class to hold the result of creating an overlay-target pair. */
    private static class OverlayTargetPair {
        final ActivityRecord mOverlay;
        final ActivityRecord mTarget;
        final Task mTask;

        OverlayTargetPair(ActivityRecord overlay, ActivityRecord target, Task task) {
            this.mOverlay = overlay;
            this.mTarget = target;
            this.mTask = task;
        }
    }

    /** Data class to hold the result of creating a mixed-lock-state task. */
    private static class TestMixedStateTask {
        final Task mTask;
        final ActivityRecord mLockedActivity;
        final ActivityRecord mUnlockedActivity;

        TestMixedStateTask(Task task, ActivityRecord lockedActivity,
                ActivityRecord unlockedActivity) {
            this.mTask = task;
            this.mLockedActivity = lockedActivity;
            this.mUnlockedActivity = unlockedActivity;
        }
    }
}
