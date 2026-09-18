/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static android.app.KeyguardManager.ACTION_CONFIRM_DEVICE_CREDENTIAL_WITH_USER;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_HOME;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_RECENTS;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD;
import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW;
import static android.app.WindowConfiguration.WINDOWING_MODE_PINNED;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.window.DisplayAreaOrganizer.FEATURE_VENDOR_FIRST;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn;
import static com.android.server.display.feature.flags.Flags.FLAG_ENABLE_DISPLAY_CONTENT_MODE_MANAGEMENT;
import static com.android.server.wm.ActivityRecord.State.FINISHING;
import static com.android.server.wm.ActivityRecord.State.PAUSED;
import static com.android.server.wm.ActivityRecord.State.PAUSING;
import static com.android.server.wm.ActivityRecord.State.RESUMED;
import static com.android.server.wm.ActivityRecord.State.STOPPED;
import static com.android.server.wm.ActivityRecord.State.STOPPING;
import static com.android.server.wm.ActivityTaskSupervisor.ON_TOP;
import static com.android.server.wm.RootWindowContainer.MATCH_ATTACHED_TASK_OR_RECENT_TASKS_AND_RESTORE;
import static com.android.server.wm.WindowContainer.POSITION_BOTTOM;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.refEq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.testng.internal.junit.ArrayAsserts.assertArrayEquals;

import android.app.ActivityOptions;
import android.app.WindowConfiguration;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Rect;
import android.os.RemoteException;
import android.os.UserHandle;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;
import android.util.Pair;
import android.view.DisplayInfo;

import androidx.test.filters.MediumTest;

import com.android.internal.app.ResolverActivity;
import com.android.window.flags.Flags;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * Tests for {@link RootWindowContainer}.
 *
 * Build/Install/Run:
 *  atest WmTests:RootWindowContainerTests
 */
@MediumTest
@Presubmit
@RunWith(WindowTestRunner.class)
public class RootWindowContainerTests extends WindowTestsBase {

    @Before
    public void setUp() throws Exception {
        doNothing().when(mAtm).sleepIfNeededLocked();
        doNothing().when(mAtm).wakeIfNeededLocked();
    }

    @Test
    @DisableFlags(Flags.FLAG_DISABLE_DISPLAY_FORCE_FREEFORM_ON_PC)
    public void testUpdateDefaultTaskDisplayAreaWindowingModeOnSettingsRetrieved_freeform() {
        assertEquals(WindowConfiguration.WINDOWING_MODE_FULLSCREEN,
                mWm.getDefaultDisplayContentLocked().getDefaultTaskDisplayArea()
                        .getWindowingMode());

        mWm.mIsPc = true;
        mWm.mAtmService.mSupportsFreeformWindowManagement = true;

        mWm.mRoot.onSettingsRetrieved();

        assertEquals(WindowConfiguration.WINDOWING_MODE_FREEFORM,
                mWm.getDefaultDisplayContentLocked().getDefaultTaskDisplayArea()
                        .getWindowingMode());
    }

    @Test
    @EnableFlags(Flags.FLAG_DISABLE_DISPLAY_FORCE_FREEFORM_ON_PC)
    public void testUpdateDefaultTaskDisplayAreaWindowingModeOnSettingsRetrieved_fullscreen() {
        assertEquals(WindowConfiguration.WINDOWING_MODE_FULLSCREEN,
                mWm.getDefaultDisplayContentLocked().getDefaultTaskDisplayArea()
                        .getWindowingMode());

        mWm.mIsPc = true;
        mWm.mAtmService.mSupportsFreeformWindowManagement = true;

        mWm.mRoot.onSettingsRetrieved();

        assertEquals(WINDOWING_MODE_FULLSCREEN,
                mWm.getDefaultDisplayContentLocked().getDefaultTaskDisplayArea()
                        .getWindowingMode());
    }

    /**
     * This test ensures that an existing single instance activity with alias name can be found by
     * the same activity info. So {@link ActivityStarter#getReusableTask} won't miss it that leads
     * to create an unexpected new instance.
     */
    @Test
    public void testFindActivityByTargetComponent() {
        final ComponentName aliasComponent = ComponentName.createRelative(
                DEFAULT_COMPONENT_PACKAGE_NAME, ".AliasActivity");
        final ComponentName targetComponent = ComponentName.createRelative(
                aliasComponent.getPackageName(), ".TargetActivity");
        final ActivityRecord activity = new ActivityBuilder(mWm.mAtmService)
                .setComponent(aliasComponent)
                .setTargetActivity(targetComponent.getClassName())
                .setLaunchMode(ActivityInfo.LAUNCH_SINGLE_INSTANCE)
                .setCreateTask(true)
                .build();

        assertEquals(activity, mWm.mRoot.findActivity(activity.intent, activity.info,
                false /* compareIntentFilters */));
    }

    @Test
    public void testFindTask_includeLaunchedFromBubbled() {
        final ComponentName component = ComponentName.createRelative(
                DEFAULT_COMPONENT_PACKAGE_NAME, ".BubbledActivity");
        final ActivityOptions opts = ActivityOptions.makeBasic();
        opts.setTaskAlwaysOnTop(true);
        opts.setLaunchedFromBubble(true);
        final ActivityRecord activity = new ActivityBuilder(mWm.mAtmService)
                .setComponent(component)
                .setActivityOptions(opts)
                .setCreateTask(true)
                .build();

        assertEquals(activity, mWm.mRoot.findTask(activity, activity.getTaskDisplayArea(),
                true /* includeLaunchedFromBubble */));
    }

    @Test
    public void testFindTask_ignoreLaunchedFromBubbled() {
        final ComponentName component = ComponentName.createRelative(
                DEFAULT_COMPONENT_PACKAGE_NAME, ".BubbledActivity");
        final ActivityOptions opts = ActivityOptions.makeBasic();
        opts.setTaskAlwaysOnTop(true);
        opts.setLaunchedFromBubble(true);
        final ActivityRecord activity = new ActivityBuilder(mWm.mAtmService)
                .setComponent(component)
                .setActivityOptions(opts)
                .setCreateTask(true)
                .build();

        assertNull(mWm.mRoot.findTask(activity, activity.getTaskDisplayArea(),
                false /* includeLaunchedFromBubble */));
    }

    @Test
    public void testAllPausedActivitiesComplete() {
        DisplayContent displayContent = mWm.mRoot.getDisplayContent(DEFAULT_DISPLAY);
        ActivityRecord activity = createActivityRecord(displayContent);
        Task task = activity.getTask();
        task.setPausingActivity(activity);

        activity.setState(PAUSING, "test PAUSING");
        assertThat(mWm.mRoot.allPausedActivitiesComplete()).isFalse();

        activity.setState(PAUSED, "test PAUSED");
        assertThat(mWm.mRoot.allPausedActivitiesComplete()).isTrue();

        activity.setState(STOPPED, "test STOPPED");
        assertThat(mWm.mRoot.allPausedActivitiesComplete()).isTrue();

        activity.setState(STOPPING, "test STOPPING");
        assertThat(mWm.mRoot.allPausedActivitiesComplete()).isTrue();

        activity.setState(FINISHING, "test FINISHING");
        assertThat(mWm.mRoot.allPausedActivitiesComplete()).isTrue();
    }

    @Test
    public void testAllResumedActivitiesIdle() {
        final ActivityRecord activity1 = new ActivityBuilder(mAtm).setCreateTask(true).build();
        final ActivityRecord activity2 = new ActivityBuilder(mAtm).setCreateTask(true).build();
        final WindowProcessController proc2 = activity2.app;
        activity1.setState(RESUMED, "test");
        activity2.detachFromProcess();
        assertThat(mWm.mRoot.allResumedActivitiesIdle()).isFalse();

        activity1.idle = true;
        assertThat(mWm.mRoot.allResumedActivitiesIdle()).isFalse();

        activity2.setProcess(proc2);
        activity2.setState(RESUMED, "test");
        activity2.idle = true;
        assertThat(mWm.mRoot.allResumedActivitiesIdle()).isTrue();

        activity1.idle = false;
        activity1.setVisibleRequested(false);
        assertThat(mWm.mRoot.allResumedActivitiesIdle()).isTrue();

        final TaskFragment taskFragment = new TaskFragmentBuilder(mAtm)
                .setParentTask(activity2.getTask()).build();
        final ActivityRecord activity3 = new ActivityBuilder(mAtm).build();
        taskFragment.addChild(activity3);
        taskFragment.setVisibleRequested(true);
        activity3.setState(RESUMED, "test");
        activity3.idle = true;
        assertThat(mWm.mRoot.allResumedActivitiesIdle()).isTrue();

        activity3.idle = false;
        assertThat(mWm.mRoot.allResumedActivitiesIdle()).isFalse();

        activity2.idle = false;
        activity3.idle = true;
        assertThat(mWm.mRoot.allResumedActivitiesIdle()).isFalse();
    }

    @Test
    public void testTaskLayerRank() {
        final Task rootTask = new TaskBuilder(mSupervisor).build();
        final Task task1 = new TaskBuilder(mSupervisor).setParentTask(rootTask).build();
        final ActivityRecord activity1 = new ActivityBuilder(mAtm).setTask(task1).build();
        activity1.setVisibleRequested(true);
        mWm.mRoot.rankTaskLayers();

        assertEquals(1, task1.mLayerRank);
        // Only tasks that directly contain activities have a ranking.
        assertEquals(Task.LAYER_RANK_INVISIBLE, rootTask.mLayerRank);

        final Task task2 = new TaskBuilder(mSupervisor).build();
        final ActivityRecord activity2 = new ActivityBuilder(mAtm).setTask(task2).build();
        activity2.setVisibleRequested(true);
        mWm.mRoot.rankTaskLayers();

        // Note that ensureActivitiesVisible is disabled in SystemServicesTestRule, so both the
        // activities have the visible rank.
        assertEquals(2, task1.mLayerRank);
        // The task2 is the top task, so it has a lower rank as a higher priority oom score.
        assertEquals(1, task2.mLayerRank);

        task2.moveToBack("test", null /* task */);
        // RootWindowContainer#invalidateTaskLayers should post to update.
        waitHandlerIdle(mWm.mH);

        assertEquals(1, task1.mLayerRank);
        assertEquals(2, task2.mLayerRank);

        // The rank should be updated to invisible when all activities in the task become invisible.
        activity1.setVisibleRequested(false);
        assertEquals(Task.LAYER_RANK_INVISIBLE, task1.mLayerRank);
        assertFalse(activity1.app.hasActivityInVisibleTask());
    }

    @Test
    public void testTaskLayerRankFreeform() {
        final Task[] freeformTasks = new Task[3];
        final WindowProcessController[] processes = new WindowProcessController[3];
        for (int i = 0; i < freeformTasks.length; i++) {
            freeformTasks[i] = new TaskBuilder(mSupervisor)
                    .setWindowingMode(WINDOWING_MODE_FREEFORM).setCreateActivity(true).build();
            final ActivityRecord r = freeformTasks[i].getTopMostActivity();
            r.setState(RESUMED, "test");
            processes[i] = r.app;
        }
        resizeDisplay(mDisplayContent, 2400, 2000);
        // ---------
        // | 2 | 1 |
        // ---------
        // | 0 |   |
        // ---------
        freeformTasks[2].setBounds(0, 0, 1000, 1000);
        freeformTasks[1].setBounds(1000, 0, 2000, 1000);
        freeformTasks[0].setBounds(0, 1000, 1000, 2000);
        mRootWindowContainer.rankTaskLayers();
        assertEquals(1, freeformTasks[2].mLayerRank);
        assertEquals(2, freeformTasks[1].mLayerRank);
        assertEquals(3, freeformTasks[0].mLayerRank);
        assertFalse("Top doesn't need perceptible hint", (processes[2].getActivityStateFlags()
                & WindowProcessController.ACTIVITY_STATE_FLAG_PERCEPTIBLE_FREEFORM) != 0);
        assertTrue((processes[1].getActivityStateFlags()
                & WindowProcessController.ACTIVITY_STATE_FLAG_PERCEPTIBLE_FREEFORM) != 0);
        assertTrue((processes[0].getActivityStateFlags()
                & WindowProcessController.ACTIVITY_STATE_FLAG_PERCEPTIBLE_FREEFORM) != 0);

        // Make task2 occlude half of task0.
        clearInvocations(mAtm);
        freeformTasks[2].setBounds(0, 0, 1000, 1500);
        waitHandlerIdle(mWm.mH);
        // The process of task0 will demote from perceptible to visible.
        final int stateFlags0 = processes[0].getActivityStateFlags();
        assertTrue((stateFlags0
                & WindowProcessController.ACTIVITY_STATE_FLAG_VISIBLE_MULTI_WINDOW_MODE) != 0);
        assertFalse((stateFlags0
                & WindowProcessController.ACTIVITY_STATE_FLAG_PERCEPTIBLE_FREEFORM) != 0);
        verify(mAtm).updateOomAdj();
    }

    @Test
    public void testForceStopPackage() {
        final Task task = new TaskBuilder(mSupervisor).setCreateActivity(true).build();
        final ActivityRecord activity = task.getTopMostActivity();
        final WindowProcessController wpc = activity.app;
        final ActivityRecord[] activities = {
                activity,
                new ActivityBuilder(mWm.mAtmService).setTask(task).setUseProcess(wpc).build(),
                new ActivityBuilder(mWm.mAtmService).setTask(task).setUseProcess(wpc).build()
        };
        activities[0].detachFromProcess();
        activities[1].finishing = true;
        activities[1].destroyImmediately("test");
        spyOn(wpc);
        doReturn(true).when(wpc).isRemoved();

        mWm.mAtmService.mInternal.onForceStopPackage(wpc.mInfo.packageName, true /* doit */,
                false /* evenPersistent */, wpc.mUserId);
        // The activity without process should be removed.
        assertEquals(2, task.getChildCount());

        wpc.handleAppDied();
        // The activities with process should be removed because WindowProcessController#isRemoved.
        assertFalse(task.hasChild());
        assertFalse(wpc.hasActivities());
    }

    @Test
    public void testAttachApplication() {
        final ActivityRecord activity = new ActivityBuilder(mAtm).setProcessName("testAttach")
                .setCreateTask(true).build();
        final ActivityRecord topActivity = new ActivityBuilder(mAtm).setProcessName("testAttach")
                .setUseProcess(activity.app).setTask(activity.getTask()).build();
        activity.detachFromProcess();
        topActivity.detachFromProcess();
        mAtm.startProcessAsync(topActivity, false /* knownToBeDead */,
                true /* isTop */, "test" /* hostingType */);
        // Even if the activity is added after topActivity, the start order should still follow
        // z-order, i.e. the topActivity will be started first.
        mAtm.startProcessAsync(activity, false /* knownToBeDead */,
                false /* isTop */, "test" /* hostingType */);
        assertEquals(2, mAtm.mStartingProcessActivities.size());
        assertEquals("Top record must be at the tail to start first",
                topActivity, mAtm.mStartingProcessActivities.get(1));
        final WindowProcessController proc = mSystemServicesTestRule.addProcess(
                activity.packageName, activity.processName,
                6789 /* pid */, activity.info.applicationInfo.uid);
        mAtm.mInternal.preBindApplication(proc, proc.mInfo);
        assertTrue(proc.registeredForActivityConfigChanges());
        assertFalse(proc.mHasEverAttached);
        try {
            mRootWindowContainer.attachApplication(proc);
            verify(mSupervisor).realStartActivityLocked(eq(topActivity), eq(proc),
                    anyBoolean(), anyBoolean());
        } catch (RemoteException e) {
            e.rethrowAsRuntimeException();
        }

        // Verify that onProcessRemoved won't clear the launching activities if an attached process
        // is died. Because in real case, it should be handled from WindowProcessController's
        // and ActivityRecord's handleAppDied to decide whether to remove the activities.
        assertTrue(proc.mHasEverAttached);
        assertTrue(mAtm.mStartingProcessActivities.isEmpty());
        mAtm.mStartingProcessActivities.add(activity);
        mAtm.mInternal.onProcessRemoved(proc.mName, proc.mUid);
        assertFalse(mAtm.mStartingProcessActivities.isEmpty());
    }

    /**
     * This test ensures that we do not try to restore a task based off an invalid task id. We
     * should expect {@code null} to be returned in this case.
     */
    @Test
    public void testRestoringInvalidTask() {
        mRootWindowContainer.getDefaultDisplay().removeAllTasks();
        Task task = mRootWindowContainer.anyTaskForId(0 /*taskId*/,
                MATCH_ATTACHED_TASK_OR_RECENT_TASKS_AND_RESTORE, null, false /* onTop */);
        assertNull(task);
    }

    /**
     * This test ensures that an existing task in the pinned root task is moved to the fullscreen
     * activity root task when a new task is added.
     */
    @Test
    public void testReplacingTaskInPinnedRootTask() {
        Task fullscreenTask = mRootWindowContainer.getDefaultTaskDisplayArea().createRootTask(
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD, true /* onTop */);
        final ActivityRecord firstActivity = new ActivityBuilder(mAtm)
                .setTask(fullscreenTask).build();
        final ActivityRecord secondActivity = new ActivityBuilder(mAtm)
                .setTask(fullscreenTask).build();

        fullscreenTask.moveToFront("testReplacingTaskInPinnedRootTask");

        // Ensure full screen root task has both tasks.
        ensureTaskPlacement(fullscreenTask, firstActivity, secondActivity);

        // Move first activity to pinned root task.
        mRootWindowContainer.moveActivityToPinnedRootTaskForTest(firstActivity, "initialMove");

        final TaskDisplayArea taskDisplayArea = fullscreenTask.getDisplayArea();
        Task pinnedRootTask = taskDisplayArea.getRootPinnedTask();
        // Ensure a task has moved over.
        ensureTaskPlacement(pinnedRootTask, firstActivity);
        ensureTaskPlacement(fullscreenTask, secondActivity);

        // Move second activity to pinned root task.
        mRootWindowContainer.moveActivityToPinnedRootTaskForTest(secondActivity, "secondMove");

        // Need to get root tasks again as a new instance might have been created.
        pinnedRootTask = taskDisplayArea.getRootPinnedTask();
        fullscreenTask = taskDisplayArea.getRootTask(WINDOWING_MODE_FULLSCREEN,
                ACTIVITY_TYPE_STANDARD);
        // Ensure root tasks have swapped tasks.
        ensureTaskPlacement(pinnedRootTask, secondActivity);
        ensureTaskPlacement(fullscreenTask, firstActivity);
    }

    @Test
    public void testMultipleActivitiesTaskEnterPip() {
        // Enable shell transition because the order of setting windowing mode is different.
        registerTestTransitionPlayer();
        final ActivityRecord transientActivity = new ActivityBuilder(mAtm)
                .setCreateTask(true).build();
        final ActivityRecord activity1 = new ActivityBuilder(mAtm).setCreateTask(true).build();
        final ActivityRecord activity2 = new ActivityBuilder(mAtm)
                .setTask(activity1.getTask()).build();
        activity2.setState(RESUMED, "test");

        // Assume the top activity switches to a transient-launch, e.g. recents.
        transientActivity.setState(RESUMED, "test");
        transientActivity.getTask().moveToFront("test");

        mRootWindowContainer.moveActivityToPinnedRootTaskForTest(activity2, "test");
        assertEquals("Created PiP task must not change focus", transientActivity.getTask(),
                mRootWindowContainer.getTopDisplayFocusedRootTask());
        final Task newPipTask = activity2.getTask();
        assertEquals(newPipTask, mDisplayContent.getDefaultTaskDisplayArea().getRootPinnedTask());
        assertNotEquals(newPipTask, activity1.getTask());
        assertFalse("Created PiP task must not be in recents", newPipTask.inRecents);
        assertThat(newPipTask.autoRemoveRecents).isTrue();
        assertThat(activity1.getTask().autoRemoveRecents).isFalse();
    }

    /**
     * When there is only one activity in the Task, and the activity is requesting to enter PIP, the
     * whole Task should enter PIP.
     */
    @Test
    public void testSingleActivityTaskEnterPip() {
        final Task fullscreenTask = mRootWindowContainer.getDefaultTaskDisplayArea().createRootTask(
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD, true /* onTop */);
        final ActivityRecord activity = new ActivityBuilder(mAtm)
                .setTask(fullscreenTask)
                .build();
        final Task task = activity.getTask();

        // Move activity to pinned root task.
        mRootWindowContainer.moveActivityToPinnedRootTaskForTest(activity, "test");

        // Ensure a task has moved over.
        ensureTaskPlacement(task, activity);
        assertTrue(task.inPinnedWindowingMode());
        assertFalse("Entering PiP activity must not affect SysUiFlags",
                activity.canAffectSystemUiFlags());

        // The activity with fixed orientation should not apply letterbox when entering PiP.
        final int requestedOrientation = task.getConfiguration().orientation
                == Configuration.ORIENTATION_PORTRAIT
                ? Configuration.ORIENTATION_LANDSCAPE : Configuration.ORIENTATION_PORTRAIT;
        doReturn(requestedOrientation).when(activity).getRequestedConfigurationOrientation();
        doReturn(false).when(activity).handlesOrientationChangeFromDescendant(anyInt());
        final Rect bounds = new Rect(task.getBounds());
        bounds.scale(0.5f);
        task.setBounds(bounds);
        assertFalse(activity.mAppCompatController.getAspectRatioPolicy()
                .isLetterboxedForFixedOrientationAndAspectRatio());
        assertThat(task.autoRemoveRecents).isFalse();
    }

    /**
     * When there is only one activity in the Task, and the activity is requesting to enter PIP, the
     * whole Task should enter PIP even if the activity is in a TaskFragment.
     */
    @Test
    public void testSingleActivityInTaskFragmentEnterPip() {
        final Task fullscreenTask = mRootWindowContainer.getDefaultTaskDisplayArea().createRootTask(
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD, true /* onTop */);
        final TaskFragment taskFragment = new TaskFragmentBuilder(mAtm)
                .setParentTask(fullscreenTask)
                .createActivityCount(1)
                .build();
        final ActivityRecord activity = taskFragment.getTopMostActivity();
        final Task task = activity.getTask();

        // Move activity to pinned root task.
        mRootWindowContainer.moveActivityToPinnedRootTaskForTest(activity, "test");

        // Ensure a task has moved over.
        ensureTaskPlacement(task, activity);
        assertTrue(task.inPinnedWindowingMode());
        assertThat(task.autoRemoveRecents).isFalse();
    }

    /**
     * When there is one TaskFragment with two activities in the Task, the activity requests to
     * enter PIP, that activity will be move to PIP root task.
     */
    @Test
    public void testMultipleActivitiesInTaskFragmentEnterPip() {
        final Task fullscreenTask = mRootWindowContainer.getDefaultTaskDisplayArea().createRootTask(
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD, true /* onTop */);
        final TaskFragment taskFragment = new TaskFragmentBuilder(mAtm)
                .setParentTask(fullscreenTask)
                .createActivityCount(2)
                .build();
        final ActivityRecord firstActivity = taskFragment.getTopMostActivity();
        final ActivityRecord secondActivity = taskFragment.getBottomMostActivity();

        // Move first activity to pinned root task.
        mRootWindowContainer.moveActivityToPinnedRootTaskForTest(firstActivity, "test");

        final TaskDisplayArea taskDisplayArea = fullscreenTask.getDisplayArea();
        final Task pinnedRootTask = taskDisplayArea.getRootPinnedTask();

        // Ensure a task has moved over.
        ensureTaskPlacement(pinnedRootTask, firstActivity);
        ensureTaskPlacement(fullscreenTask, secondActivity);
        assertTrue(pinnedRootTask.inPinnedWindowingMode());
        assertEquals(WINDOWING_MODE_FULLSCREEN, fullscreenTask.getWindowingMode());
        assertThat(pinnedRootTask.autoRemoveRecents).isTrue();
        assertThat(secondActivity.getTask().autoRemoveRecents).isFalse();
    }

    @Test
    public void testMovingEmbeddedActivityToPip() {
        final Rect taskBounds = new Rect(0, 0, 800, 1000);
        final Rect taskFragmentBounds = new Rect(0, 0, 400, 1000);
        final Task task = mRootWindowContainer.getDefaultTaskDisplayArea().createRootTask(
                WINDOWING_MODE_MULTI_WINDOW, ACTIVITY_TYPE_STANDARD, true /* onTop */);
        task.setBounds(taskBounds);
        assertEquals(taskBounds, task.getBounds());
        final TaskFragment taskFragment = new TaskFragmentBuilder(mAtm)
                .setParentTask(task)
                .createActivityCount(2)
                .setBounds(taskFragmentBounds)
                .build();
        assertEquals(taskFragmentBounds, taskFragment.getBounds());
        final ActivityRecord topActivity = taskFragment.getTopMostActivity();

        // Move the top activity to pinned root task.
        mRootWindowContainer.moveActivityToPinnedRootTaskForTest(topActivity, "test");

        final Task pinnedRootTask = task.getDisplayArea().getRootPinnedTask();

        // Ensure the initial bounds of the PiP Task is the same as the TaskFragment.
        ensureTaskPlacement(pinnedRootTask, topActivity);
        assertEquals(taskFragmentBounds, pinnedRootTask.getBounds());
    }

    @Test
    public void testMoveActivityToPinnedRootTask_singleActivity_recordsSnapshot() {
        // Create a task with a single activity.
        final Task task = new TaskBuilder(mSupervisor).setCreateActivity(true).build();
        final ActivityRecord activity = task.getTopMostActivity();
        spyOn(mWm.mTaskSnapshotController);

        // Move the activity to the pinned root task.
        mRootWindowContainer.moveActivityToPinnedRootTaskForTest(activity, "test");

        // Verify that a snapshot of the task was recorded.
        verify(mWm.mTaskSnapshotController).recordSnapshot(eq(task));
    }

    @Test
    public void testMoveActivityToPinnedRootTask_multiActivity_recordsSnapshot() {
        // Create a task with two activities.
        final Task originalTask = new TaskBuilder(mSupervisor).setCreateActivity(true).build();
        new ActivityBuilder(mAtm).setTask(originalTask).build();
        final ActivityRecord topActivity = originalTask.getTopMostActivity();
        spyOn(mWm.mTaskSnapshotController);

        // Move the top activity to the pinned root task.
        mRootWindowContainer.moveActivityToPinnedRootTaskForTest(topActivity, "test");

        // Verify that a snapshot of the original task was recorded.
        // In the multi-activity case, a new task is created for the PiP activity, but the snapshot
        // should be taken of the original task before the activity is reparented.
        verify(mWm.mTaskSnapshotController).recordSnapshot(eq(originalTask));
    }

    private static void ensureTaskPlacement(Task task, ActivityRecord... activities) {
        final ArrayList<ActivityRecord> taskActivities = new ArrayList<>();

        task.forAllActivities((Consumer<ActivityRecord>) taskActivities::add, false);

        assertEquals("Expecting " + Arrays.deepToString(activities) + " got " + taskActivities,
                taskActivities.size(), activities != null ? activities.length : 0);

        if (activities == null) {
            return;
        }

        for (ActivityRecord activity : activities) {
            assertTrue(taskActivities.contains(activity));
        }
    }

    @Test
    public void testApplySleepTokens() {
        final DisplayContent display = mRootWindowContainer.getDefaultDisplay();
        final KeyguardController keyguard = mSupervisor.getKeyguardController();
        final Task task = new TaskBuilder(mSupervisor)
                .setDisplay(display)
                .setOnTop(false)
                .build();

        // Make sure we wake and resume in the case the display is turning on and the keyguard is
        // not showing.
        verifySleepTokenBehavior(display, keyguard, task, true /*displaySleeping*/,
                false /* displayShouldSleep */, true /* isFocusedTask */,
                false /* keyguardShowing */, true /* expectWakeFromSleep */,
                true /* expectResumeTopActivity */);

        // Make sure we wake and don't resume when the display is turning on and the keyguard is
        // showing.
        verifySleepTokenBehavior(display, keyguard, task, true /*displaySleeping*/,
                false /* displayShouldSleep */, true /* isFocusedTask */,
                true /* keyguardShowing */, true /* expectWakeFromSleep */,
                false /* expectResumeTopActivity */);

        // Make sure we wake and don't resume when the display is turning on and the keyguard is
        // not showing as unfocused.
        verifySleepTokenBehavior(display, keyguard, task, true /*displaySleeping*/,
                false /* displayShouldSleep */, false /* isFocusedTask */,
                false /* keyguardShowing */, true /* expectWakeFromSleep */,
                false /* expectResumeTopActivity */);

        // Should not do anything if the display state hasn't changed.
        verifySleepTokenBehavior(display, keyguard, task, false /*displaySleeping*/,
                false /* displayShouldSleep */, true /* isFocusedTask */,
                false /* keyguardShowing */, false /* expectWakeFromSleep */,
                false /* expectResumeTopActivity */);
    }

    private void verifySleepTokenBehavior(DisplayContent display, KeyguardController keyguard,
            Task task, boolean displaySleeping, boolean displayShouldSleep,
            boolean isFocusedTask, boolean keyguardShowing, boolean expectWakeFromSleep,
            boolean expectResumeTopActivity) {
        reset(task);

        doReturn(displayShouldSleep).when(display).shouldSleep();
        doReturn(displaySleeping).when(display).isSleeping();
        doReturn(keyguardShowing).when(keyguard).isKeyguardOrAodShowing(anyInt());

        doReturn(isFocusedTask).when(task).isFocusedRootTaskOnDisplay();
        doReturn(isFocusedTask ? task : null).when(display).getFocusedRootTask();
        TaskDisplayArea defaultTaskDisplayArea = display.getDefaultTaskDisplayArea();
        doReturn(isFocusedTask ? task : null).when(defaultTaskDisplayArea).getFocusedRootTask();
        if (displayShouldSleep) {
            display.sleepIfNeeded();
        } else {
            display.wakeIfNeeded();
        }
        verify(task, times(expectWakeFromSleep ? 1 : 0)).awakeFromSleeping();
        verify(task, times(expectResumeTopActivity ? 1 : 0)).resumeTopActivityUncheckedLocked(
                isNull() /* target */, isNull() /* targetOptions */, eq(false) /* deferPause */);
        waitHandlerIdle(mAtm.mH);
    }

    @Test
    public void testAwakeFromSleepingWithAppConfiguration() {
        final DisplayContent display = mRootWindowContainer.getDefaultDisplay();
        display.setIgnoreOrientationRequest(false);
        final ActivityRecord activity = new ActivityBuilder(mAtm).setCreateTask(true).build();
        activity.moveFocusableActivityToTop("test");
        assertTrue(activity.getRootTask().isFocusedRootTaskOnDisplay());
        ActivityRecordTests.setRotatedScreenOrientationSilently(activity);

        final Configuration rotatedConfig = new Configuration();
        display.computeScreenConfiguration(rotatedConfig, display.getDisplayRotation()
                .rotationForOrientation(activity.getOrientation(), display.getRotation()));
        assertNotEquals(activity.getConfiguration().orientation, rotatedConfig.orientation);
        // Assume the activity was shown in different orientation. For example, the top activity is
        // landscape and the portrait lockscreen is shown.
        activity.setLastReportedConfiguration(mAtm.getGlobalConfiguration(), rotatedConfig);
        activity.setState(STOPPED, "sleep");

        display.setIsSleeping(true);
        doReturn(false).when(display).shouldSleep();
        // Allow to resume when awaking.
        setBooted(mAtm);
        display.wakeIfNeeded();

        // The display orientation should be changed by the activity so there is no relaunch.
        verify(activity, never()).relaunchActivityLocked(anyBoolean(), anyInt());
        assertEquals(rotatedConfig.orientation, display.getConfiguration().orientation);
    }

    /**
     * Verifies that removal of activity with task and root task is done correctly.
     */
    @Test
    public void testRemovingRootTaskOnAppCrash() {
        final TaskDisplayArea defaultTaskDisplayArea = mRootWindowContainer
                .getDefaultTaskDisplayArea();
        final int originalRootTaskCount = defaultTaskDisplayArea.getRootTaskCount();
        final Task rootTask = defaultTaskDisplayArea.createRootTask(
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD, false /* onTop */);
        final ActivityRecord firstActivity = new ActivityBuilder(mAtm).setTask(rootTask).build();

        assertEquals(originalRootTaskCount + 1, defaultTaskDisplayArea.getRootTaskCount());

        // Let's pretend that the app has crashed.
        firstActivity.app.setThread(null);
        final Task finishedTask = mRootWindowContainer.finishTopCrashedActivities(
                firstActivity.app, "test");

        // Verify that the root task was removed.
        assertEquals(originalRootTaskCount, defaultTaskDisplayArea.getRootTaskCount());
        assertEquals(rootTask, finishedTask);
    }

    /**
     * Verifies that removal of activities with task and root task is done correctly when there are
     * several task display areas.
     */
    @Test
    public void testRemovingRootTaskOnAppCrash_multipleDisplayAreas() {
        final TaskDisplayArea defaultTaskDisplayArea = mRootWindowContainer
                .getDefaultTaskDisplayArea();
        final int originalRootTaskCount = defaultTaskDisplayArea.getRootTaskCount();
        final Task rootTask = defaultTaskDisplayArea.createRootTask(
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD, false /* onTop */);
        final ActivityRecord firstActivity = new ActivityBuilder(mAtm).setTask(rootTask).build();
        assertEquals(originalRootTaskCount + 1, defaultTaskDisplayArea.getRootTaskCount());

        final DisplayContent dc = defaultTaskDisplayArea.getDisplayContent();
        final TaskDisplayArea secondTaskDisplayArea = createTaskDisplayArea(
                dc, mRootWindowContainer.mWmService, "TestTaskDisplayArea", FEATURE_VENDOR_FIRST);
        final Task secondRootTask = secondTaskDisplayArea.createRootTask(
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD, false /* onTop */);
        new ActivityBuilder(mAtm).setTask(secondRootTask).setUseProcess(firstActivity.app).build();
        assertEquals(1, secondTaskDisplayArea.getRootTaskCount());

        // Let's pretend that the app has crashed.
        firstActivity.app.setThread(null);
        mRootWindowContainer.finishTopCrashedActivities(firstActivity.app, "test");

        // Verify that the root tasks were removed.
        assertEquals(originalRootTaskCount, defaultTaskDisplayArea.getRootTaskCount());
        assertEquals(0, secondTaskDisplayArea.getRootTaskCount());
    }

    @Test
    public void testFocusability() {
        final TaskDisplayArea defaultTaskDisplayArea = mRootWindowContainer
                .getDefaultTaskDisplayArea();
        final Task task = defaultTaskDisplayArea.createRootTask(
                WINDOWING_MODE_MULTI_WINDOW, ACTIVITY_TYPE_STANDARD, true /* onTop */);
        final ActivityRecord activity = new ActivityBuilder(mAtm).setTask(task).build();

        // Created tasks are focusable by default.
        assertTrue(task.isTopActivityFocusable());
        assertTrue(activity.isFocusable());

        // If the task is made unfocusable, its activities should inherit that.
        task.setFocusable(false);
        assertFalse(task.isTopActivityFocusable());
        assertFalse(activity.isFocusable());

        final Task pinnedTask = defaultTaskDisplayArea.createRootTask(
                WINDOWING_MODE_PINNED, ACTIVITY_TYPE_STANDARD, true /* onTop */);
        final ActivityRecord pinnedActivity = new ActivityBuilder(mAtm)
                .setTask(pinnedTask).build();

        // We should not be focusable when in pinned mode
        assertFalse(pinnedTask.isTopActivityFocusable());
        assertFalse(pinnedActivity.isFocusable());
    }

    /**
     * Verify that home root task would be moved to front when the top activity is Recents.
     */
    @Test
    public void testFindTaskToMoveToFrontWhenRecentsOnTop() {
        // Create root task/task on default display.
        final Task targetRootTask = new TaskBuilder(mSupervisor)
                .setCreateActivity(true)
                .setOnTop(false)
                .build();
        final Task targetTask = targetRootTask.getBottomMostTask();

        // Create Recents on top of the display.
        final Task rootTask = new TaskBuilder(mSupervisor)
                .setCreateActivity(true)
                .setActivityType(ACTIVITY_TYPE_RECENTS)
                .build();

        final String reason = "findTaskToMoveToFront";
        mSupervisor.findTaskToMoveToFront(targetTask, 0, ActivityOptions.makeBasic(), reason,
                false);

        final TaskDisplayArea taskDisplayArea = mRootWindowContainer.getDefaultTaskDisplayArea();
        verify(taskDisplayArea).moveHomeRootTaskToFront(contains(reason));
    }

    /**
     * Verify that home root task won't be moved to front if the top activity on other display is
     * Recents.
     */
    @Test
    public void testFindTaskToMoveToFrontWhenRecentsOnOtherDisplay() {
        // Create tasks on default display.
        final TaskDisplayArea taskDisplayArea = mRootWindowContainer.getDefaultTaskDisplayArea();
        final Task targetRootTask = taskDisplayArea.createRootTask(WINDOWING_MODE_FULLSCREEN,
                ACTIVITY_TYPE_STANDARD, false /* onTop */);
        final Task targetTask = new TaskBuilder(mSupervisor).setParentTask(targetRootTask)
                .build();

        // Create Recents on secondary display.
        final TestDisplayContent secondDisplay = addNewDisplayContentAt(
                DisplayContent.POSITION_TOP);
        final Task rootTask = secondDisplay.getDefaultTaskDisplayArea()
                .createRootTask(WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_RECENTS, true /* onTop */);
        new ActivityBuilder(mAtm).setTask(rootTask).build();

        final String reason = "findTaskToMoveToFront";
        mSupervisor.findTaskToMoveToFront(targetTask, 0, ActivityOptions.makeBasic(), reason,
                false);

        verify(taskDisplayArea, never()).moveHomeRootTaskToFront(contains(reason));
    }

    /**
     * Verify if a root task is not at the topmost position, it should be able to resume its
     * activity if the root task is the top focused.
     */
    @Test
    public void testResumeActivityWhenNonTopmostRootTaskIsTopFocused() {
        // Create a root task at bottom.
        final TaskDisplayArea taskDisplayArea = mRootWindowContainer.getDefaultTaskDisplayArea();
        final Task rootTask = spy(taskDisplayArea.createRootTask(WINDOWING_MODE_FULLSCREEN,
                ACTIVITY_TYPE_STANDARD, false /* onTop */));
        final ActivityRecord activity = new ActivityBuilder(mAtm).setTask(rootTask).build();
        taskDisplayArea.positionChildAt(POSITION_BOTTOM, rootTask, false /*includingParents*/);

        // Assume the task is not at the topmost position (e.g. behind always-on-top root tasks)
        // but it is the current top focused task.
        assertFalse(rootTask.isTopRootTaskInDisplayArea());
        doReturn(rootTask).when(mRootWindowContainer).getTopDisplayFocusedRootTask();

        // Use the task as target to resume.
        mRootWindowContainer.resumeFocusedTasksTopActivities(rootTask, activity);

        // Verify the target task should resume its activity.
        verify(rootTask, times(1)).resumeTopActivityUncheckedLocked(
                eq(activity), eq(null /* targetOptions */), eq(false));
    }

    /**
     * Verify that home activity will be started on a display even if another display has a
     * focusable activity.
     */
    @Test
    public void testResumeFocusedRootTasksStartsHomeActivity_NoActivities() {
        final TaskDisplayArea taskDisplayArea = mRootWindowContainer.getDefaultTaskDisplayArea();
        taskDisplayArea.getRootHomeTask().removeIfPossible();
        taskDisplayArea.createRootTask(WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_HOME, ON_TOP);

        doReturn(true).when(mRootWindowContainer).resumeHomeActivity(any(), any(), any());

        setBooted(mAtm);

        // Trigger resume on all displays
        mRootWindowContainer.resumeFocusedTasksTopActivities();

        // Verify that home activity was started on the default display
        verify(mRootWindowContainer).resumeHomeActivity(any(), any(), eq(taskDisplayArea));
    }

    /**
     * Verify that home activity will be started on a display even if another display has a
     * focusable activity.
     */
    @Test
    public void testResumeFocusedRootTasksStartsHomeActivity_ActivityOnSecondaryScreen() {
        final TaskDisplayArea taskDisplayArea = mRootWindowContainer.getDefaultTaskDisplayArea();
        taskDisplayArea.getRootHomeTask().removeIfPossible();
        taskDisplayArea.createRootTask(WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_HOME, ON_TOP);

        // Create an activity on secondary display.
        final TestDisplayContent secondDisplay = addNewDisplayContentAt(
                DisplayContent.POSITION_TOP);
        final Task rootTask = secondDisplay.getDefaultTaskDisplayArea().createRootTask(
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD, true /* onTop */);
        new ActivityBuilder(mAtm).setTask(rootTask).build();

        doReturn(true).when(mRootWindowContainer).resumeHomeActivity(any(), any(), any());

        setBooted(mAtm);

        // Trigger resume on all displays
        mRootWindowContainer.resumeFocusedTasksTopActivities();

        // Verify that home activity was started on the default display
        verify(mRootWindowContainer).resumeHomeActivity(any(), any(), eq(taskDisplayArea));
    }

    /**
     * Verify that a lingering transition is being executed in case the activity to be resumed is
     * already resumed
     */
    @Test
    public void testResumeActivityLingeringTransition() {
        // Create a root task at top.
        final TaskDisplayArea taskDisplayArea = mRootWindowContainer.getDefaultTaskDisplayArea();
        final Task rootTask = spy(taskDisplayArea.createRootTask(WINDOWING_MODE_FULLSCREEN,
                ACTIVITY_TYPE_STANDARD, false /* onTop */));
        final ActivityRecord activity = new ActivityBuilder(mAtm)
                .setTask(rootTask).setOnTop(true).build();
        activity.setState(RESUMED, "test");

        // Assume the task is at the topmost position
        assertTrue(rootTask.isTopRootTaskInDisplayArea());

        // Use the task as target to resume.
        mRootWindowContainer.resumeFocusedTasksTopActivities();

        // Verify the lingering app transition is being executed because it's already resumed
        verify(rootTask, times(1)).executeAppTransition(any());
    }

    @Test
    public void testResumeActivityLingeringTransition_notExecuted() {
        // Create a root task at bottom.
        final TaskDisplayArea taskDisplayArea = mRootWindowContainer.getDefaultTaskDisplayArea();
        final Task rootTask = spy(taskDisplayArea.createRootTask(WINDOWING_MODE_FULLSCREEN,
                ACTIVITY_TYPE_STANDARD, false /* onTop */));
        final ActivityRecord activity = new ActivityBuilder(mAtm)
                .setTask(rootTask).setOnTop(true).build();
        activity.setState(RESUMED, "test");
        taskDisplayArea.positionChildAt(POSITION_BOTTOM, rootTask, false /*includingParents*/);

        // Assume the task is at the topmost position
        assertFalse(rootTask.isTopRootTaskInDisplayArea());
        doReturn(taskDisplayArea.getHomeActivity()).when(taskDisplayArea).topRunningActivity(
                anyBoolean());

        // Use the task as target to resume.
        mRootWindowContainer.resumeFocusedTasksTopActivities();

        // Verify the lingering app transition is being executed because it's already resumed
        verify(rootTask, never()).executeAppTransition(any());
    }

    @Test
    public void testResumeFocusedTasksTopActivities_skipsRemovingDisplay() {
        final DisplayContent secondDisplay = addNewDisplayContentAt(DisplayContent.POSITION_TOP);
        spyOn(secondDisplay);
        doReturn(true).when(secondDisplay).isRemoving();

        createActivityRecord(secondDisplay);

        mRootWindowContainer.resumeFocusedTasksTopActivities();

        // Verify the loop continued early by ensuring topRunningActivity() was never called
        verify(secondDisplay, never()).topRunningActivity();
    }

    @Test
    public void testResumeFocusedTasksTopActivities_skipsInvalidDisplay() {
        final DisplayContent secondDisplay = addNewDisplayContentAt(DisplayContent.POSITION_TOP);
        spyOn(secondDisplay);
        doReturn(true).when(secondDisplay).isRemovedOrInvalid();

        createActivityRecord(secondDisplay);

        mRootWindowContainer.resumeFocusedTasksTopActivities();

        // Verify the loop continued early by ensuring topRunningActivity() was never called
        verify(secondDisplay, never()).topRunningActivity();
    }

    @Test
    @EnableFlags(FLAG_ENABLE_DISPLAY_CONTENT_MODE_MANAGEMENT)
    public void testResumeFocusedTasksTopActivities_skipsDisplayCannotHostTasks() {
        final DisplayContent secondDisplay = addNewDisplayContentAt(DisplayContent.POSITION_TOP);
        spyOn(secondDisplay.mDisplay);
        doReturn(false).when(secondDisplay.mDisplay).canHostTasks();

        createActivityRecord(secondDisplay);

        mRootWindowContainer.resumeFocusedTasksTopActivities();

        // Verify the loop continued early by ensuring topRunningActivity() was never called
        verify(secondDisplay, never()).topRunningActivity();
    }

    /**
     * Tests that home activities can be started on the displays that supports system decorations.
     */
    @Test
    public void testStartHomeOnAllDisplays() {
        mockResolveHomeActivity(true /* primaryHome */, false /* forceSystemProvided */);
        mockResolveSecondaryHomeActivity();

        // Create secondary displays.
        final TestDisplayContent secondDisplay =
                new TestDisplayContent.Builder(mAtm, 1000, 1500)
                        .setSystemDecorations(true).build();

        doReturn(true).when(mRootWindowContainer).canStartHomeOnDisplayArea(any(), any(),
                anyBoolean());

        mRootWindowContainer.startHomeOnAllDisplays(0, "testStartHome");

        assertTrue(mRootWindowContainer.getDefaultDisplay().getTopRootTask().isActivityTypeHome());
        assertNotNull(secondDisplay.getTopRootTask());
        assertTrue(secondDisplay.getTopRootTask().isActivityTypeHome());
    }

    /**
     * Tests that home activities won't be started before booting when display added.
     */
    @Test
    public void testNotStartHomeBeforeBoot() {
        final int displayId = 1;
        doReturn(false).when(mAtm).isBooting();
        doReturn(false).when(mAtm).isBooted();
        mRootWindowContainer.onDisplayAdded(displayId);
        verify(mRootWindowContainer, never()).startHomeOnDisplay(anyInt(), any(), anyInt());
    }

    /**
     * Tests whether home can be started if being instrumented.
     */
    @Test
    public void testCanStartHomeWhenInstrumented() {
        final ActivityInfo info = new ActivityInfo();
        info.applicationInfo = new ApplicationInfo();
        final WindowProcessController app = mock(WindowProcessController.class);
        doReturn(app).when(mAtm).getProcessController(any(), anyInt());

        // Can not start home if we don't want to start home while home is being instrumented.
        doReturn(true).when(app).isInstrumenting();
        final TaskDisplayArea defaultTaskDisplayArea = mRootWindowContainer
                .getDefaultTaskDisplayArea();
        assertFalse(mRootWindowContainer.canStartHomeOnDisplayArea(info, defaultTaskDisplayArea,
                false /* allowInstrumenting*/));

        // Can start home for other cases.
        assertTrue(mRootWindowContainer.canStartHomeOnDisplayArea(info, defaultTaskDisplayArea,
                true /* allowInstrumenting*/));

        doReturn(false).when(app).isInstrumenting();
        assertTrue(mRootWindowContainer.canStartHomeOnDisplayArea(info, defaultTaskDisplayArea,
                false /* allowInstrumenting*/));
        assertTrue(mRootWindowContainer.canStartHomeOnDisplayArea(info, defaultTaskDisplayArea,
                true /* allowInstrumenting*/));
    }

    /**
     * Tests whether home can be started if it's not allowed by policy.
     */
    @Test
    public void testCanStartHome_returnsFalse_ifDisallowedByPolicy() {
        final ActivityInfo info = new ActivityInfo();
        info.applicationInfo = new ApplicationInfo();
        final WindowProcessController app = mock(WindowProcessController.class);
        doReturn(app).when(mAtm).getProcessController(any(), anyInt());
        doReturn(false).when(app).isInstrumenting();
        final TaskDisplayArea taskDisplayArea = mRootWindowContainer.getDefaultTaskDisplayArea();
        doReturn(false).when(taskDisplayArea).canHostHomeTask();

        assertFalse(mRootWindowContainer.canStartHomeOnDisplayArea(info, taskDisplayArea,
                false /* allowInstrumenting*/));
    }


    /**
     * Tests that secondary home activity should not be resolved if device is still locked.
     */
    @Test
    public void testStartSecondaryHomeOnDisplayWithUserKeyLocked() {
        // Create secondary displays.
        final TestDisplayContent secondDisplay =
                new TestDisplayContent.Builder(mAtm, 1000, 1500)
                        .setSystemDecorations(true).build();

        // Use invalid user id to let StorageManager.isCeStorageUnlocked() return false.
        final int currentUser = mRootWindowContainer.mCurrentUser;
        mRootWindowContainer.mCurrentUser = -1;

        mRootWindowContainer.startHomeOnDisplay(0 /* userId */, "testStartSecondaryHome",
                secondDisplay.mDisplayId, true /* allowInstrumenting */, true /* fromHomeKey */);

        try {
            verify(mRootWindowContainer, never()).resolveSecondaryHomeActivity(anyInt(), any());
        } finally {
            mRootWindowContainer.mCurrentUser = currentUser;
        }
    }

    /**
     * Tests that secondary home activity should not be resolved if display does not support system
     * decorations.
     */
    @Test
    public void testStartSecondaryHomeOnDisplayWithoutSysDecorations() {
        // Create secondary displays.
        final TestDisplayContent secondDisplay =
                new TestDisplayContent.Builder(mAtm, 1000, 1500)
                        .setSystemDecorations(false).build();

        mRootWindowContainer.startHomeOnDisplay(0 /* userId */, "testStartSecondaryHome",
                secondDisplay.mDisplayId, true /* allowInstrumenting */, true /* fromHomeKey */);

        verify(mRootWindowContainer, never()).resolveSecondaryHomeActivity(anyInt(), any());
    }

    /**
     * Tests that secondary home activity should not be resolved if display cannot host tasks.
     */
    @Test
    public void testStartSecondaryHomeOnDisplayCannotHostTasks() {
        // Create secondary displays.
        final TestDisplayContent secondDisplay =
                new TestDisplayContent.Builder(mAtm, 1000, 1500).build();
        spyOn(secondDisplay.mDisplay);
        doReturn(false).when(secondDisplay.mDisplay).canHostTasks();

        mRootWindowContainer.startHomeOnDisplay(0 /* userId */, "testStartSecondaryHome",
                secondDisplay.mDisplayId, true /* allowInstrumenting */, true /* fromHomeKey */);

        verify(mRootWindowContainer, never()).resolveSecondaryHomeActivity(anyInt(), any());
    }

    /**
     * Tests that when starting {@link ResolverActivity} for home, it should use the standard
     * activity type (in a new root task) so the order of back stack won't be broken.
     */
    @Test
    public void testStartResolverActivityForHome() {
        final ActivityInfo info = new ActivityInfo();
        info.applicationInfo = new ApplicationInfo();
        info.applicationInfo.packageName = "android";
        info.name = ResolverActivity.class.getName();
        doReturn(info).when(mRootWindowContainer).resolveHomeActivity(anyInt(), any());

        mRootWindowContainer.startHomeOnDisplay(0 /* userId */, "test", DEFAULT_DISPLAY);
        final ActivityRecord resolverActivity = mRootWindowContainer.topRunningActivity();

        assertEquals(info, resolverActivity.info);
        assertEquals(ACTIVITY_TYPE_STANDARD, resolverActivity.getRootTask().getActivityType());
    }

    /**
     * Tests that secondary home should be selected if primary home not set.
     */
    @Test
    public void testResolveSecondaryHomeActivityWhenPrimaryHomeNotSet() {
        // Setup: primary home not set.
        final Intent primaryHomeIntent = mAtm.getHomeIntent();
        final ActivityInfo aInfoPrimary = new ActivityInfo();
        aInfoPrimary.name = ResolverActivity.class.getName();
        doReturn(aInfoPrimary).when(mRootWindowContainer).resolveHomeActivity(anyInt(),
                refEq(primaryHomeIntent));
        // Setup: set secondary home.
        mockResolveHomeActivity(false /* primaryHome */, false /* forceSystemProvided */);

        // Run the test.
        final Pair<ActivityInfo, Intent> resolvedInfo = mRootWindowContainer
                .resolveSecondaryHomeActivity(0 /* userId */, mock(TaskDisplayArea.class));
        final ActivityInfo aInfoSecondary = getFakeHomeActivityInfo(false /* primaryHome*/);
        assertEquals(aInfoSecondary.name, resolvedInfo.first.name);
        assertEquals(aInfoSecondary.applicationInfo.packageName,
                resolvedInfo.first.applicationInfo.packageName);
    }

    /**
     * Tests that the default secondary home activity is always picked when it is in forced by
     * config_useSystemProvidedLauncherForSecondary.
     */
    @Test
    public void testResolveSecondaryHomeActivityForced() {
        // SetUp: set primary home.
        mockResolveHomeActivity(true /* primaryHome */, false /* forceSystemProvided */);
        // SetUp: set secondary home and force it.
        mockResolveHomeActivity(false /* primaryHome */, true /* forceSystemProvided */);
        final Intent secondaryHomeIntent =
                mAtm.getSecondaryHomeIntent(null /* preferredPackage */);
        final List<ResolveInfo> resolutions = new ArrayList<>();
        final ResolveInfo resolveInfo = new ResolveInfo();
        final ActivityInfo aInfoSecondary = getFakeHomeActivityInfo(false /* primaryHome*/);
        resolveInfo.activityInfo = aInfoSecondary;
        resolutions.add(resolveInfo);
        doReturn(resolutions).when(mRootWindowContainer).resolveActivities(anyInt(),
                refEq(secondaryHomeIntent));
        doReturn(true).when(mRootWindowContainer).canStartHomeOnDisplayArea(any(), any(),
                anyBoolean());

        // Run the test.
        final Pair<ActivityInfo, Intent> resolvedInfo = mRootWindowContainer
                .resolveSecondaryHomeActivity(0 /* userId */, mock(TaskDisplayArea.class));
        assertEquals(aInfoSecondary.name, resolvedInfo.first.name);
        assertEquals(aInfoSecondary.applicationInfo.packageName,
                resolvedInfo.first.applicationInfo.packageName);
    }

    /**
     * Tests that secondary home should be selected if primary home not support secondary displays
     * or there is no matched activity in the same package as selected primary home.
     */
    @Test
    public void testResolveSecondaryHomeActivityWhenPrimaryHomeNotSupportMultiDisplay() {
        // Setup: there is no matched activity in the same package as selected primary home.
        mockResolveHomeActivity(true /* primaryHome */, false /* forceSystemProvided */);
        final List<ResolveInfo> resolutions = new ArrayList<>();
        doReturn(resolutions).when(mRootWindowContainer).resolveActivities(anyInt(), any());
        // Setup: set secondary home.
        mockResolveHomeActivity(false /* primaryHome */, false /* forceSystemProvided */);

        // Run the test.
        final Pair<ActivityInfo, Intent> resolvedInfo = mRootWindowContainer
                .resolveSecondaryHomeActivity(0 /* userId */, mock(TaskDisplayArea.class));
        final ActivityInfo aInfoSecondary = getFakeHomeActivityInfo(false /* primaryHome*/);
        assertEquals(aInfoSecondary.name, resolvedInfo.first.name);
        assertEquals(aInfoSecondary.applicationInfo.packageName,
                resolvedInfo.first.applicationInfo.packageName);
    }
    /**
     * Tests that primary home activity should be selected if it already support secondary displays.
     */
    @Test
    public void testResolveSecondaryHomeActivityWhenPrimaryHomeSupportMultiDisplay() {
        // SetUp: set primary home.
        mockResolveHomeActivity(true /* primaryHome */, false /* forceSystemProvided */);
        // SetUp: put primary home info on 2nd item
        final List<ResolveInfo> resolutions = new ArrayList<>();
        final ResolveInfo infoFake1 = new ResolveInfo();
        infoFake1.activityInfo = new ActivityInfo();
        infoFake1.activityInfo.name = "fakeActivity1";
        infoFake1.activityInfo.applicationInfo = new ApplicationInfo();
        infoFake1.activityInfo.applicationInfo.packageName = "fakePackage1";
        final ResolveInfo infoFake2 = new ResolveInfo();
        final ActivityInfo aInfoPrimary = getFakeHomeActivityInfo(true /* primaryHome */);
        infoFake2.activityInfo = aInfoPrimary;
        resolutions.add(infoFake1);
        resolutions.add(infoFake2);
        doReturn(resolutions).when(mRootWindowContainer).resolveActivities(anyInt(), any());

        doReturn(true).when(mRootWindowContainer).canStartHomeOnDisplayArea(any(), any(),
                anyBoolean());

        // Run the test.
        final Pair<ActivityInfo, Intent> resolvedInfo = mRootWindowContainer
                .resolveSecondaryHomeActivity(0 /* userId */, mock(TaskDisplayArea.class));
        assertEquals(aInfoPrimary.name, resolvedInfo.first.name);
        assertEquals(aInfoPrimary.applicationInfo.packageName,
                resolvedInfo.first.applicationInfo.packageName);
    }

    /**
     * Tests that the first one that matches should be selected if there are multiple activities.
     */
    @Test
    public void testResolveSecondaryHomeActivityWhenOtherActivitySupportMultiDisplay() {
        // SetUp: set primary home.
        mockResolveHomeActivity(true /* primaryHome */, false /* forceSystemProvided */);
        // Setup: prepare two eligible activity info.
        final List<ResolveInfo> resolutions = new ArrayList<>();
        final ResolveInfo infoFake1 = new ResolveInfo();
        infoFake1.activityInfo = new ActivityInfo();
        infoFake1.activityInfo.name = "fakeActivity1";
        infoFake1.activityInfo.applicationInfo = new ApplicationInfo();
        infoFake1.activityInfo.applicationInfo.packageName = "fakePackage1";
        final ResolveInfo infoFake2 = new ResolveInfo();
        infoFake2.activityInfo = new ActivityInfo();
        infoFake2.activityInfo.name = "fakeActivity2";
        infoFake2.activityInfo.applicationInfo = new ApplicationInfo();
        infoFake2.activityInfo.applicationInfo.packageName = "fakePackage2";
        resolutions.add(infoFake1);
        resolutions.add(infoFake2);
        doReturn(resolutions).when(mRootWindowContainer).resolveActivities(anyInt(), any());

        doReturn(true).when(mRootWindowContainer).canStartHomeOnDisplayArea(any(), any(),
                anyBoolean());

        // Use the first one of matched activities in the same package as selected primary home.
        final Pair<ActivityInfo, Intent> resolvedInfo = mRootWindowContainer
                .resolveSecondaryHomeActivity(0 /* userId */, mock(TaskDisplayArea.class));

        assertEquals(infoFake1.activityInfo.applicationInfo.packageName,
                resolvedInfo.first.applicationInfo.packageName);
        assertEquals(infoFake1.activityInfo.name, resolvedInfo.first.name);
    }

    @Test
    public void testGetLaunchRootTaskOnSecondaryTaskDisplayArea() {
        // Adding another TaskDisplayArea to the default display.
        final DisplayContent display = mRootWindowContainer.getDefaultDisplay();
        final TaskDisplayArea taskDisplayArea = new TaskDisplayArea(mWm, "TDA",
                FEATURE_VENDOR_FIRST, false /* createdByOrganizer */, true /* canHostHomeTask */);
        display.addChild(taskDisplayArea, POSITION_BOTTOM);

        // Making sure getting the root task from the preferred TDA and the preferred windowing mode
        LaunchParamsController.LaunchParams launchParams =
                new LaunchParamsController.LaunchParams();
        launchParams.mPreferredTaskDisplayArea = taskDisplayArea;
        launchParams.mWindowingMode = WINDOWING_MODE_FREEFORM;
        Task root = mRootWindowContainer.getOrCreateRootTask(null /* r */, null /* options */,
                null /* candidateTask */, null /* sourceTask */, true /* onTop */, launchParams,
                0 /* launchParams */, 0 /* originalCallerUid */);
        assertEquals(taskDisplayArea, root.getTaskDisplayArea());
        assertEquals(WINDOWING_MODE_FREEFORM, root.getWindowingMode());

        // Making sure still getting the root task from the preferred TDA when passing in a
        // launching activity.
        ActivityRecord r = new ActivityBuilder(mAtm).build();
        root = mRootWindowContainer.getOrCreateRootTask(r, null /* options */,
                null /* candidateTask */, null /* sourceTask */, true /* onTop */, launchParams,
                0 /* launchParams */, 0 /* originalCallerUid */);
        assertEquals(taskDisplayArea, root.getTaskDisplayArea());
        assertEquals(WINDOWING_MODE_FREEFORM, root.getWindowingMode());
    }

    @Test
    public void testGetOrCreateRootTaskOnDisplayWithCandidateRootTask() {
        // Create a root task with an activity on secondary display.
        final TestDisplayContent secondaryDisplay = new TestDisplayContent.Builder(mAtm, 300,
                600).build();
        final Task task = new TaskBuilder(mSupervisor)
                .setDisplay(secondaryDisplay).build();
        final ActivityRecord activity = new ActivityBuilder(mAtm).setTask(task).build();

        // Make sure the root task is valid and can be reused on default display.
        final Task rootTask = mRootWindowContainer.getDefaultTaskDisplayArea().getOrCreateRootTask(
                activity, null /* options */, task, null /* sourceTask */, null /* launchParams */,
                0 /* launchFlags */, ACTIVITY_TYPE_STANDARD, true /* onTop */,
                0 /* originalCallerUid */);
        assertEquals(task, rootTask);
    }

    @Test
    public void testGetOrCreateRootTask_withPreferredRootTask_returnsPreferredRootTask() {
        // Arrange: Create a preferred root task and set it in the launch parameters.
        final Task preferredRootTask = new TaskBuilder(mSupervisor).build();
        final ActivityRecord activity = new ActivityBuilder(mAtm).build();

        final LaunchParamsController.LaunchParams launchParams =
                new LaunchParamsController.LaunchParams();
        launchParams.mPreferredRootTask = preferredRootTask;

        // Act: Call the method under test.
        final Task resultTask = mRootWindowContainer.getOrCreateRootTask(
                activity,
                null /* options */,
                null /* candidateTask */,
                null /* sourceTask */,
                true /* onTop */,
                launchParams,
                0 /* launchFlags */,
                0 /* originalCallerUid */);

        // Assert: Verify that the returned task is the preferred one.
        assertEquals(preferredRootTask, resultTask);
    }

    @Test
    public void testSwitchUser_missingHomeRootTask() {
        final Task fullscreenTask = mRootWindowContainer.getDefaultTaskDisplayArea().createRootTask(
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD, true /* onTop */);
        doReturn(fullscreenTask).when(mRootWindowContainer).getTopDisplayFocusedRootTask();

        final TaskDisplayArea taskDisplayArea = mRootWindowContainer.getDefaultTaskDisplayArea();
        Task rootHomeTask = taskDisplayArea.getRootHomeTask();
        if (rootHomeTask != null) {
            rootHomeTask.removeImmediately();
        }
        assertNull(taskDisplayArea.getRootHomeTask());

        int currentUser = mRootWindowContainer.mCurrentUser;
        int otherUser = currentUser + 1;

        mRootWindowContainer.switchUser(otherUser, null);

        assertNotNull(taskDisplayArea.getRootHomeTask());
        assertEquals(taskDisplayArea.getTopRootTask(), taskDisplayArea.getRootHomeTask());
    }

    @Test
    public void testSwitchUser_withVisibleRootTasks_storesAllVisibleRootTasksForCurrentUser() {
        // Set up root tasks
        final Task rootTask1 = mRootWindowContainer.getDefaultTaskDisplayArea().createRootTask(
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD, true /* onTop */);
        final Task rootTask2 = mRootWindowContainer.getDefaultTaskDisplayArea().createRootTask(
                WINDOWING_MODE_FREEFORM, ACTIVITY_TYPE_STANDARD, true /* onTop */);
        final Task rootTask3 = mRootWindowContainer.getDefaultTaskDisplayArea().createRootTask(
                WINDOWING_MODE_FREEFORM, ACTIVITY_TYPE_STANDARD, true /* onTop */);
        doReturn(rootTask3).when(mRootWindowContainer).getTopDisplayFocusedRootTask();

        // Set up child tasks inside root tasks and set some of them visible
        final Task task1 = new TaskBuilder(mSupervisor).setOnTop(true).setParentTask(
                rootTask1).build();
        final Task task2 = new TaskBuilder(mSupervisor).setOnTop(true).setParentTask(
                rootTask2).build();
        final Task task3 = new TaskBuilder(mSupervisor).setOnTop(true).setParentTask(
                rootTask3).build();
        rootTask1.mUserId = mRootWindowContainer.mCurrentUser;
        rootTask2.mUserId = mRootWindowContainer.mCurrentUser;
        rootTask3.mUserId = mRootWindowContainer.mCurrentUser;
        doReturn(false).when(task1).isVisible();
        doReturn(true).when(task2).isVisible();
        doReturn(true).when(task3).isVisible();

        // Switch to a different user
        int currentUser = mRootWindowContainer.mCurrentUser;
        int otherUser = currentUser + 1;
        mRootWindowContainer.switchUser(otherUser, null);

        // Verify that the previous user persists it's previous visible root tasks
        assertArrayEquals(
                new int[]{rootTask2.mTaskId, rootTask3.mTaskId},
                mRootWindowContainer.mUserVisibleRootTasks.get(currentUser).toArray()
        );
    }

    @Test
    public void testLockAllProfileTasks() {
        final int profileUid = UserHandle.PER_USER_RANGE + UserHandle.MIN_SECONDARY_USER_ID;
        final int profileUserId = UserHandle.getUserId(profileUid);
        // Create an activity belonging to the profile user.
        final ActivityRecord activity = new ActivityBuilder(mAtm).setCreateTask(true)
                .setUid(profileUid).build();
        final Task task = activity.getTask();

        // Create another activity belonging to current user on top.
        final ActivityRecord topActivity = new ActivityBuilder(mAtm).setTask(task).build();
        topActivity.intent.setAction(Intent.ACTION_MAIN);

        // Make sure the listeners will be notified for putting the task to locked state
        TaskChangeNotificationController controller = mAtm.getTaskChangeNotificationController();
        spyOn(controller);
        mWm.mRoot.lockAllProfileTasks(profileUserId);
        verify(controller).notifyTaskProfileLocked(any(), eq(profileUserId));

        // Create the work lock activity on top of the task
        final ActivityRecord workLockActivity = new ActivityBuilder(mAtm).setTask(task).build();
        workLockActivity.intent.setAction(ACTION_CONFIRM_DEVICE_CREDENTIAL_WITH_USER);
        doReturn(workLockActivity.mActivityComponent).when(mAtm).getSysUiServiceComponentLocked();

        // Make sure the listener won't be notified again.
        clearInvocations(controller);
        mWm.mRoot.lockAllProfileTasks(profileUserId);
        verify(controller, never()).notifyTaskProfileLocked(any(), anyInt());
    }

    @EnableFlags(FLAG_ENABLE_DISPLAY_CONTENT_MODE_MANAGEMENT)
    @Test
    public void testOnDisplayBelongToTopologyChanged() {
        final DisplayInfo displayInfo = new DisplayInfo();
        displayInfo.copyFrom(mDisplayInfo);
        displayInfo.displayId = DEFAULT_DISPLAY + 1;
        final DisplayContent dc = createNewDisplay(displayInfo);
        final int displayId = dc.getDisplayId();

        doReturn(dc).when(mRootWindowContainer).getDisplayContentOrCreate(displayId);
        doReturn(true).when(mWm.mDisplayWindowSettings).shouldShowSystemDecorsLocked(dc);

        mRootWindowContainer.onDisplayAdded(displayId);
        verify(mWm.mDisplayManagerInternal, times(1)).onDisplayBelongToTopologyChanged(anyInt(),
                anyBoolean());
    }

    @Test
    public void testGetTopVisibleActivities_fullscreen() {
        // Make every Task opaque.
        final WindowContainerVisibilityHelper visibilityHelper = mAtm.mVisibilityHelper;
        spyOn(visibilityHelper);
        doReturn(true).when(visibilityHelper).isOpaque(
                any(), any(), anyBoolean(), anyBoolean(), anyBoolean());

        final DisplayContent display = mRootWindowContainer.getDefaultDisplay();
        final ActivityRecord bottomR = createActivityRecord(display);
        final ActivityRecord topR = createActivityRecord(display);
        // The Task behind another should be invisible.
        bottomR.setVisibleRequested(false);

        final List<ActivityRecord> result = mRootWindowContainer.getTopVisibleActivities(
                display.mDisplayId);

        assertEquals(1, result.size());
        assertEquals(topR, result.get(0));
    }

    @Test
    public void testGetTopVisibleActivities_splitScreen() {
        // Make every Task opaque.
        final WindowContainerVisibilityHelper visibilityHelper = mAtm.mVisibilityHelper;
        spyOn(visibilityHelper);
        doReturn(true).when(visibilityHelper).isOpaque(
                any(), any(), anyBoolean(), anyBoolean(), anyBoolean());

        final DisplayContent display = mRootWindowContainer.getDefaultDisplay();
        final ActivityRecord splitActivity0 = createActivityRecordWithParentTask(display,
                WINDOWING_MODE_MULTI_WINDOW, ACTIVITY_TYPE_STANDARD);
        final ActivityRecord splitActivity1 = createActivityRecordWithParentTask(
                splitActivity0.getRootTask());
        final Task splitTask0 = splitActivity0.getTask();
        final Task splitTask1 = splitActivity1.getTask();
        splitTask0.setAdjacentTaskFragments(new TaskFragment.AdjacentSet(splitTask0, splitTask1));
        splitTask0.setBounds(0, 0, 500, 500);
        splitTask1.setBounds(500, 0, 1000, 500);

        final List<ActivityRecord> result = mRootWindowContainer.getTopVisibleActivities(
                display.mDisplayId);

        assertEquals(2, result.size());
        assertEquals(splitActivity1, result.get(0));
        assertEquals(splitActivity0, result.get(1));
    }

    @Test
    public void testGetTopVisibleActivities_rootTaskWithMultiLeafTasks() {
        final DisplayContent display = mRootWindowContainer.getDefaultDisplay();
        final Task deskRoot = createTask(display, WINDOWING_MODE_MULTI_WINDOW,
                ACTIVITY_TYPE_STANDARD);
        final ActivityRecord activity0 = createActivityRecordWithParentTask(deskRoot);
        final ActivityRecord activity1 = createActivityRecordWithParentTask(deskRoot);
        final ActivityRecord activity2 = createActivityRecordWithParentTask(deskRoot);

        final List<ActivityRecord> result = mRootWindowContainer.getTopVisibleActivities(
                display.mDisplayId);

        assertEquals(3, result.size());
        assertEquals(activity2, result.get(0));
        assertEquals(activity1, result.get(1));
        assertEquals(activity0, result.get(2));
    }

    @Test
    public void testGetTopVisibleActivities_activityEmbedding() {
        final DisplayContent display = mRootWindowContainer.getDefaultDisplay();
        final Task task = createTask(display, WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD);
        final TaskFragment tf0 = createTaskFragmentWithActivity(task);
        final TaskFragment tf1 = createTaskFragmentWithActivity(task);
        tf0.setWindowingMode(WINDOWING_MODE_MULTI_WINDOW);
        tf1.setWindowingMode(WINDOWING_MODE_MULTI_WINDOW);
        tf0.setBounds(0, 0, 500, 500);
        tf1.setBounds(500, 0, 1000, 500);
        tf0.setAdjacentTaskFragments(new TaskFragment.AdjacentSet(tf0, tf1));
        final ActivityRecord activity0 = tf0.getTopMostActivity();
        final ActivityRecord activity1 = tf1.getTopMostActivity();
        postCreateActivitySetup(activity0, display);
        postCreateActivitySetup(activity1, display);

        final List<ActivityRecord> result = mRootWindowContainer.getTopVisibleActivities(
                display.mDisplayId);

        assertEquals(2, result.size());
        assertEquals(activity1, result.get(0));
        assertEquals(activity0, result.get(1));
    }

    @Test
    public void testGetTopVisibleActivities_focusedAndNonFocusedRootTasks() {
        // Make every Task opaque.
        final WindowContainerVisibilityHelper visibilityHelper = mAtm.mVisibilityHelper;
        spyOn(visibilityHelper);
        doReturn(true).when(visibilityHelper).isOpaque(
                any(), any(), anyBoolean(), anyBoolean(), anyBoolean());

        final DisplayContent display = mRootWindowContainer.getDefaultDisplay();
        resizeDisplay(display, 1000, 1000);

        // Create an invisible fullscreen root task. It is created first, so it will be at the
        // bottom and occluded by the tasks created later.
        final Task invisibleRootTask = createTask(display, WINDOWING_MODE_FULLSCREEN,
                ACTIVITY_TYPE_STANDARD);
        createActivityRecordWithParentTask(invisibleRootTask);
        invisibleRootTask.setVisibleRequested(false);

        // Create a non-focused fullscreen root task with two activities.
        final Task nonFocusedRootTask = createTask(display, WINDOWING_MODE_FULLSCREEN,
                ACTIVITY_TYPE_STANDARD);
        final ActivityRecord nonFocusedActivity1 = createActivityRecordWithParentTask(
                nonFocusedRootTask);
        final ActivityRecord nonFocusedActivity2 = createActivityRecordWithParentTask(
                nonFocusedRootTask);
        nonFocusedRootTask.setVisibleRequested(true);

        // Create a focused multi-window root task with two activities. It is created last, so it
        // will be on top.
        final Task focusedRootTask = createTask(display, WINDOWING_MODE_MULTI_WINDOW,
                ACTIVITY_TYPE_STANDARD);
        final ActivityRecord focusedActivity1 = createActivityRecordWithParentTask(focusedRootTask);
        final ActivityRecord focusedActivity2 = createActivityRecordWithParentTask(focusedRootTask);
        focusedRootTask.setBounds(500, 0, 1000, 1000);
        focusedRootTask.setVisibleRequested(true);

        // Set the focused root task on the display.
        doReturn(focusedRootTask.getRootTask()).when(display).getFocusedRootTask();

        // Call the method under test.
        final List<ActivityRecord> result = mRootWindowContainer.getTopVisibleActivities(
                display.mDisplayId);

        // Verify the results.
        // Total visible activities should be 4.
        assertEquals(4, result.size());

        // The activities from the focused root task should be first, in top-to-bottom order.
        assertEquals(focusedActivity2, result.get(0));
        assertEquals(focusedActivity1, result.get(1));

        // The activities from the non-focused visible root task should be next, in top-to-bottom
        // order.
        assertEquals(nonFocusedActivity2, result.get(2));
        assertEquals(nonFocusedActivity1, result.get(3));
    }

    @Test
    @EnableFlags(Flags.FLAG_HOME_ACTIVITY_ALWAYS_PRESENT)
    public void testStartHomeOnDisplaysWithNoHome() {
        mockResolveHomeActivity(true /* primaryHome */, false /* forceSystemProvided */);
        final ActivityInfo resolvedHomeInfo = getFakeHomeActivityInfo(true /* primaryHome */);
        doReturn(true).when(mRootWindowContainer).isHomeAlwaysPresentAllowed(any(), anyInt());

        // Create a display with no home activity.
        final TestDisplayContent displayWithNoHome = new TestDisplayContent.Builder(mAtm, 1000,
                1500).build();
        final TaskDisplayArea tdaWithNoHome = displayWithNoHome.getDefaultTaskDisplayArea();
        doReturn(true).when(tdaWithNoHome).canHostHomeTask();
        // Ensure there are no tasks on the display.
        tdaWithNoHome.removeIfPossible();

        // Create a display with a home activity.
        final TestDisplayContent displayWithHome = new TestDisplayContent.Builder(mAtm, 1000,
                1500).build();
        final TaskDisplayArea tdaWithHome = displayWithHome.getDefaultTaskDisplayArea();
        doReturn(false).when(tdaWithHome).canHostHomeTask();
        new ActivityBuilder(mAtm)
                .setComponent(new ComponentName(resolvedHomeInfo.applicationInfo.packageName,
                        resolvedHomeInfo.name))
                .setTask(tdaWithHome.createRootTask(
                        WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_HOME, true /* onTop */)).build();

        mRootWindowContainer.startHomeOnDisplaysIfNeeded("test");

        // Verify that home was started on the display with no home.
        verify(mRootWindowContainer).startHomeOnTaskDisplayArea(anyInt(),
                anyString(), any(), anyBoolean(),
                anyBoolean(), anyBoolean());
        // Verify that home was NOT started on the display that already had a home.
        verify(mRootWindowContainer, never()).startHomeOnTaskDisplayArea(anyInt(),
                anyString(), eq(tdaWithHome), anyBoolean(),
                anyBoolean(), anyBoolean());
    }

    @Test
    @EnableFlags(Flags.FLAG_HOME_ACTIVITY_ALWAYS_PRESENT)
    public void testNeedsHomeLaunchOnDisplay_noExistingHome() {
        final TestDisplayContent display = new TestDisplayContent.Builder(mAtm, 1000, 1500).build();
        final TaskDisplayArea taskDisplayArea = display.getDefaultTaskDisplayArea();
        doReturn(true).when(taskDisplayArea).canHostHomeTask();
        // No existing home activity, so resolveHomeActivity returns null or no activity is found.
        doReturn(null).when(mRootWindowContainer).resolveHomeActivity(anyInt(), any());

        assertTrue(mRootWindowContainer.needsHomeLaunchOnDisplay(0, taskDisplayArea));
    }

    @Test
    @EnableFlags(Flags.FLAG_HOME_ACTIVITY_ALWAYS_PRESENT)
    public void testNeedsHomeLaunchOnDisplay_withExistingHome_samePackage() {
        final TestDisplayContent display = new TestDisplayContent.Builder(mAtm, 1000, 1500).build();
        final TaskDisplayArea taskDisplayArea = display.getDefaultTaskDisplayArea();
        doReturn(true).when(taskDisplayArea).canHostHomeTask();
        final String existingPkg = "com.android.server.wm.test.existing.app";
        // Create an existing home activity
        new ActivityBuilder(mAtm)
                .setComponent(new ComponentName(existingPkg, ".ExistingHomeActivity"))
                .setTask(display.getDefaultTaskDisplayArea().createRootTask(
                        WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_HOME, true /* onTop */)).build();

        // Mock that the resolved home activity has the same package name
        final ActivityInfo resolvedHomeInfo = new ActivityInfo();
        resolvedHomeInfo.applicationInfo = new ApplicationInfo();
        resolvedHomeInfo.applicationInfo.packageName = existingPkg;
        resolvedHomeInfo.name = ".DifferentHomeActivityExistingPackage";
        resolvedHomeInfo.packageName = existingPkg;
        doReturn(resolvedHomeInfo).when(mRootWindowContainer).resolveHomeActivity(anyInt(), any());

        assertFalse(mRootWindowContainer.needsHomeLaunchOnDisplay(0, taskDisplayArea));
    }

    @Test
    @EnableFlags(Flags.FLAG_HOME_ACTIVITY_ALWAYS_PRESENT)
    public void testNeedsHomeLaunchOnDisplay_withExistingHome_differentPackage() {
        final TaskDisplayArea taskDisplayArea = mRootWindowContainer.getDefaultTaskDisplayArea();
        taskDisplayArea.getRootHomeTask().removeIfPossible();

        final String existingPkg = "com.android.server.wm.test.existing.app";
        final ComponentName existingHomeComponent = new ComponentName(existingPkg,
                ".ExistingHomeActivity");
        final ActivityRecord existingHomeForUserOnDisplay = new ActivityBuilder(mAtm)
                .setComponent(existingHomeComponent)
                .setTask(taskDisplayArea.createRootTask(
                        WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_HOME, true /* onTop */)).build();
        doReturn(existingHomeForUserOnDisplay).when(taskDisplayArea).getHomeActivityForUser(
                anyInt());

        // Mock that the resolved home activity has a different package name.
        final ActivityInfo resolvedHomeInfo = new ActivityInfo();
        resolvedHomeInfo.applicationInfo = new ApplicationInfo();
        resolvedHomeInfo.applicationInfo.packageName = "com.android.server.wm.test.resolved.app";
        resolvedHomeInfo.applicationInfo.privateFlags |= ApplicationInfo.PRIVATE_FLAG_PRIVILEGED;
        resolvedHomeInfo.name = ".ResolvedHomeActivity";
        resolvedHomeInfo.packageName = "com.android.server.wm.test.resolved.app";
        doReturn(resolvedHomeInfo).when(mRootWindowContainer).resolveHomeActivity(anyInt(), any());
        doReturn(true).when(mRootWindowContainer).isHomeAlwaysPresentAllowed(any(), anyInt());

        mRootWindowContainer.startHomeOnDisplaysIfNeeded("test");

        assertTrue(mRootWindowContainer.needsHomeLaunchOnDisplay(0, taskDisplayArea));
        // Capture the TaskDisplayArea arguments passed to startHomeOnTaskDisplayArea.
        ArgumentCaptor<TaskDisplayArea> tdaCaptor = ArgumentCaptor.forClass(
                TaskDisplayArea.class);
        verify(mRootWindowContainer, atLeastOnce()).startHomeOnTaskDisplayArea(anyInt(),
                anyString(), tdaCaptor.capture(), anyBoolean(), anyBoolean(), anyBoolean());
        List<TaskDisplayArea> capturedTdas = tdaCaptor.getAllValues();
        // Verify that home was started on the display with the different home package.
        assertTrue(capturedTdas.stream().anyMatch(
                tda -> tda == taskDisplayArea));
    }

    @Test
    @EnableFlags(Flags.FLAG_HOME_ACTIVITY_ALWAYS_PRESENT)
    public void testNeedsHomeLaunchOnDisplay_canHostHomeTaskFalse() {
        final TestDisplayContent display = new TestDisplayContent.Builder(mAtm, 1000, 1500).build();
        final TaskDisplayArea taskDisplayArea = display.getDefaultTaskDisplayArea();
        doReturn(false).when(taskDisplayArea).canHostHomeTask();

        assertFalse(mRootWindowContainer.needsHomeLaunchOnDisplay(0, taskDisplayArea));
    }

    @Test
    @EnableFlags(Flags.FLAG_HOME_ACTIVITY_ALWAYS_PRESENT)
    public void testIsHomeAlwaysPresent_Allowed_returnsTrue() {
        final ActivityInfo aInfo = new ActivityInfo();
        aInfo.applicationInfo = new ApplicationInfo();
        aInfo.applicationInfo.packageName = "com.android.test.home";

        doReturn(true).when(mRootWindowContainer).isHomeAlwaysPresentAllowed(eq(aInfo), anyInt());

        assertTrue(mRootWindowContainer.isHomeAlwaysPresentAllowed(aInfo, 0));
    }

    @Test
    @EnableFlags(Flags.FLAG_HOME_ACTIVITY_ALWAYS_PRESENT)
    public void testIsHomeAlwaysPresent_Allowed_returnsFalse() {
        final ActivityInfo aInfo = new ActivityInfo();
        aInfo.applicationInfo = new ApplicationInfo();
        aInfo.applicationInfo.packageName = "com.android.test.home";

        doReturn(false).when(mRootWindowContainer).isHomeAlwaysPresentAllowed(eq(aInfo), anyInt());

        assertFalse(mRootWindowContainer.isHomeAlwaysPresentAllowed(aInfo, 0));
    }

    @Test
    @EnableFlags(Flags.FLAG_HOME_ACTIVITY_ALWAYS_PRESENT)
    public void testIsHomeAlwaysPresent_Allowed_packageNotFound() {
        final ActivityInfo aInfo = new ActivityInfo();
        aInfo.applicationInfo = new ApplicationInfo();
        aInfo.applicationInfo.packageName = "com.android.test.home";

        doReturn(false).when(mRootWindowContainer).isHomeAlwaysPresentAllowed(eq(aInfo), anyInt());

        assertFalse(mRootWindowContainer.isHomeAlwaysPresentAllowed(aInfo, 0));
    }

    @Test
    public void testGetTopVisibleActivities_behindTranslucent() {
        final DisplayContent display = mRootWindowContainer.getDefaultDisplay();
        // Create two visible requested activities in one Task to simulate one behind another
        // translucent.
        final Task task = createTask(display, WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD);
        final ActivityRecord activity0 = createActivityRecord(task);
        final ActivityRecord activity1 = createActivityRecord(task);

        final List<ActivityRecord> result = mRootWindowContainer.getTopVisibleActivities(
                display.mDisplayId);

        assertEquals(2, result.size());
        assertEquals(activity1, result.get(0));
        assertEquals(activity0, result.get(1));
    }

    @Test
    public void testGetTopVisibleActivityAssistInfos_wrapsGetTopVisibleActivities() {
        final DisplayContent display = mRootWindowContainer.getDefaultDisplay();
        final ActivityRecord r1 = createActivityRecord(display);
        final ActivityRecord r2 = createActivityRecord(display);
        final List<ActivityRecord> visibleActivities = new ArrayList<>();
        visibleActivities.add(r1);
        visibleActivities.add(r2);

        spyOn(mRootWindowContainer);
        doReturn(visibleActivities).when(mRootWindowContainer).getTopVisibleActivities(
                display.mDisplayId);

        final List<ActivityAssistInfo> result = mRootWindowContainer
                .getTopVisibleActivityAssistInfos(display.mDisplayId);

        assertEquals(2, result.size());
        assertEquals(r1.token, result.get(0).getActivityToken());
        assertEquals(r2.token, result.get(1).getActivityToken());

        verify(mRootWindowContainer).getTopVisibleActivities(display.mDisplayId);
    }

    @Test
    public void testUpdateFocusedWindowLocked_skipsRemovingOrRemovedDisplay() {
        // Create a second display and spy on it and the default display.
        final TestDisplayContent secondDisplay =
                addNewDisplayContentAt(DisplayContent.POSITION_TOP);
        spyOn(mDisplayContent);
        spyOn(secondDisplay);

        // Case 1: Test that a "removing" display is skipped.
        doReturn(true).when(secondDisplay).isRemoving();

        // Update focused window.
        mRootWindowContainer.updateFocusedWindowLocked(
                WindowManagerService.UPDATE_FOCUS_NORMAL, true /* updateInputWindows */);

        // Verify that updateFocusedWindowLocked is called on the default display.
        verify(mDisplayContent).updateFocusedWindowLocked(anyInt(), anyBoolean(), anyInt());
        // Verify that updateFocusedWindowLocked is NOT called on the removing display.
        verify(secondDisplay, never()).updateFocusedWindowLocked(anyInt(), anyBoolean(), anyInt());

        // Reset mocks for the next case.
        reset(mDisplayContent, secondDisplay);

        // Case 2: Test that a "removed" display is skipped.
        doReturn(false).when(secondDisplay).isRemoving(); // ensure previous mock is gone
        doReturn(true).when(secondDisplay).isRemovedOrInvalid();

        // Update focused window again.
        mRootWindowContainer.updateFocusedWindowLocked(
                WindowManagerService.UPDATE_FOCUS_NORMAL, true /* updateInputWindows */);

        // Verify that updateFocusedWindowLocked is called on the default display.
        verify(mDisplayContent).updateFocusedWindowLocked(anyInt(), anyBoolean(), anyInt());
        // Verify that updateFocusedWindowLocked is NOT called on the removed display.
        verify(secondDisplay, never()).updateFocusedWindowLocked(anyInt(), anyBoolean(), anyInt());
    }

    /**
     * Mock {@link RootWindowContainer#resolveHomeActivity} for returning consistent activity
     * info for test cases.
     *
     * @param primaryHome Indicate to use primary home intent as parameter, otherwise, use
     *                    secondary home intent.
     * @param forceSystemProvided Indicate to force using system provided home activity.
     */
    private void mockResolveHomeActivity(boolean primaryHome, boolean forceSystemProvided) {
        ActivityInfo targetActivityInfo = getFakeHomeActivityInfo(primaryHome);
        Intent targetIntent;
        if (primaryHome) {
            targetIntent = mAtm.getHomeIntent();
        } else {
            Resources resources = mContext.getResources();
            spyOn(resources);
            doReturn(targetActivityInfo.applicationInfo.packageName).when(resources).getString(
                    com.android.internal.R.string.config_secondaryHomePackage);
            doReturn(forceSystemProvided).when(resources).getBoolean(
                    com.android.internal.R.bool.config_useSystemProvidedLauncherForSecondary);
            targetIntent = mAtm.getSecondaryHomeIntent(null /* preferredPackage */);
        }
        doReturn(targetActivityInfo).when(mRootWindowContainer).resolveHomeActivity(anyInt(),
                refEq(targetIntent));
    }

    /**
     * Mock {@link RootWindowContainer#resolveSecondaryHomeActivity} for returning consistent
     * activity info for test cases.
     */
    private void mockResolveSecondaryHomeActivity() {
        final Intent secondaryHomeIntent = mAtm
                .getSecondaryHomeIntent(null /* preferredPackage */);
        final ActivityInfo aInfoSecondary = getFakeHomeActivityInfo(false);
        doReturn(Pair.create(aInfoSecondary, secondaryHomeIntent)).when(mRootWindowContainer)
                .resolveSecondaryHomeActivity(anyInt(), any());
    }

    private ActivityInfo getFakeHomeActivityInfo(boolean primaryHome) {
        final ActivityInfo aInfo = new ActivityInfo();
        aInfo.name = primaryHome ? "fakeHomeActivity" : "fakeSecondaryHomeActivity";
        aInfo.applicationInfo = new ApplicationInfo();
        aInfo.applicationInfo.packageName =
                primaryHome ? "fakeHomePackage" : "fakeSecondaryHomePackage";
        return  aInfo;
    }
}
