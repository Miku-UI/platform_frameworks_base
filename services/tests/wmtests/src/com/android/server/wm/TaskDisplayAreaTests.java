/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static android.app.WindowConfiguration.ACTIVITY_TYPE_ASSISTANT;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_DREAM;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_HOME;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_RECENTS;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_UNDEFINED;
import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW;
import static android.app.WindowConfiguration.WINDOWING_MODE_PINNED;
import static android.app.WindowConfiguration.WINDOWING_MODE_UNDEFINED;
import static android.content.Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT;
import static android.content.pm.ActivityInfo.RESIZE_MODE_UNRESIZEABLE;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LOCKED;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_NOSENSOR;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
import static android.window.DisplayAreaOrganizer.FEATURE_VENDOR_FIRST;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyBoolean;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn;
import static com.android.server.wm.ActivityRecord.State.RESUMED;
import static com.android.server.wm.ActivityTaskSupervisor.ON_TOP;
import static com.android.server.wm.WindowContainer.POSITION_TOP;
import static com.android.window.flags.Flags.FLAG_ENABLE_DISPLAY_DISCONNECT_INTERACTION;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;

import android.app.ActivityOptions;
import android.app.TaskInfo;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;

import androidx.test.filters.SmallTest;

import com.android.server.wm.LaunchParamsController.LaunchParams;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.util.List;

/**
 * Tests for the {@link TaskDisplayArea} container.
 *
 * Build/Install/Run:
 *  atest WmTests:TaskDisplayAreaTests
 */
@SmallTest
@Presubmit
@RunWith(WindowTestRunner.class)
public class TaskDisplayAreaTests extends WindowTestsBase {

    @Test
    public void getLaunchRootTask_checksLaunchAdjacentFlagRoot() {
        final Task rootTask = createTask(
                mDisplayContent, WINDOWING_MODE_MULTI_WINDOW, ACTIVITY_TYPE_STANDARD);
        rootTask.mCreatedByOrganizer = true;
        final Task adjacentRootTask = createTask(
                mDisplayContent, WINDOWING_MODE_MULTI_WINDOW, ACTIVITY_TYPE_STANDARD);
        adjacentRootTask.mCreatedByOrganizer = true;
        final TaskDisplayArea taskDisplayArea = rootTask.getDisplayArea();
        adjacentRootTask.setAdjacentTaskFragments(
                new TaskFragment.AdjacentSet(adjacentRootTask, rootTask));

        taskDisplayArea.setLaunchAdjacentFlagRootTask(adjacentRootTask);
        Task actualRootTask = taskDisplayArea.getLaunchRootTask(
                WINDOWING_MODE_UNDEFINED, ACTIVITY_TYPE_STANDARD, null /* options */,
                null /* sourceTask */, FLAG_ACTIVITY_LAUNCH_ADJACENT);
        assertSame(adjacentRootTask, actualRootTask.getRootTask());

        taskDisplayArea.setLaunchAdjacentFlagRootTask(null);
        actualRootTask = taskDisplayArea.getLaunchRootTask(WINDOWING_MODE_UNDEFINED,
                ACTIVITY_TYPE_STANDARD, null /* options */, null /* sourceTask */,
                FLAG_ACTIVITY_LAUNCH_ADJACENT);
        assertNull(actualRootTask);
    }

    @Test
    public void getLaunchRootTask_checksFocusedRootTask() {
        final TaskDisplayArea taskDisplayArea = mRootWindowContainer.getDefaultTaskDisplayArea();
        final Task rootTask = createTaskWithActivity(
                taskDisplayArea,
                WINDOWING_MODE_MULTI_WINDOW, ACTIVITY_TYPE_STANDARD, ON_TOP, true);
        rootTask.mCreatedByOrganizer = true;

        final Task adjacentRootTask = createTask(
                mDisplayContent, WINDOWING_MODE_MULTI_WINDOW, ACTIVITY_TYPE_STANDARD);
        adjacentRootTask.mCreatedByOrganizer = true;
        adjacentRootTask.setAdjacentTaskFragments(
                new TaskFragment.AdjacentSet(adjacentRootTask, rootTask));

        taskDisplayArea.setLaunchRootTask(rootTask,
                new int[]{WINDOWING_MODE_MULTI_WINDOW}, new int[]{ACTIVITY_TYPE_STANDARD});

        Task actualRootTask = taskDisplayArea.getLaunchRootTask(
                WINDOWING_MODE_MULTI_WINDOW, ACTIVITY_TYPE_STANDARD, null /* options */,
                null /* sourceTask */, 0 /*launchFlags*/);
        assertTrue(actualRootTask.isFocusedRootTaskOnDisplay());
    }

    @Test
    public void getLaunchRootTask_fromLaunchAdjacentFlagRoot_checksAdjacentRoot() {
        final ActivityRecord activity = createNonAttachedActivityRecord(mDisplayContent);
        final Task rootTask = createTask(
                mDisplayContent, WINDOWING_MODE_MULTI_WINDOW, ACTIVITY_TYPE_STANDARD);
        rootTask.mCreatedByOrganizer = true;
        final Task adjacentRootTask = createTask(
                mDisplayContent, WINDOWING_MODE_MULTI_WINDOW, ACTIVITY_TYPE_STANDARD);
        adjacentRootTask.mCreatedByOrganizer = true;
        createActivityRecord(adjacentRootTask);
        final TaskDisplayArea taskDisplayArea = rootTask.getDisplayArea();
        adjacentRootTask.setAdjacentTaskFragments(
                new TaskFragment.AdjacentSet(adjacentRootTask, rootTask));

        taskDisplayArea.setLaunchAdjacentFlagRootTask(adjacentRootTask);
        final Task actualRootTask = taskDisplayArea.getLaunchRootTask(
                WINDOWING_MODE_UNDEFINED, ACTIVITY_TYPE_STANDARD, null /* options */,
                adjacentRootTask /* sourceTask */, FLAG_ACTIVITY_LAUNCH_ADJACENT);

        assertSame(rootTask, actualRootTask.getRootTask());
    }

    @Test
    public void getOrCreateLaunchRootRespectsResolvedWindowingMode() {
        final Task rootTask = createTask(
                mDisplayContent, WINDOWING_MODE_MULTI_WINDOW, ACTIVITY_TYPE_STANDARD);
        rootTask.mCreatedByOrganizer = true;
        final TaskDisplayArea taskDisplayArea = rootTask.getDisplayArea();
        taskDisplayArea.setLaunchRootTask(
                rootTask, new int[]{WINDOWING_MODE_FREEFORM}, new int[]{ACTIVITY_TYPE_STANDARD});

        final Task candidateRootTask = createTask(
                mDisplayContent, WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD);
        final ActivityRecord activity = createNonAttachedActivityRecord(mDisplayContent);
        final LaunchParams launchParams = new LaunchParams();
        launchParams.mWindowingMode = WINDOWING_MODE_FREEFORM;

        final Task actualRootTask = taskDisplayArea.getOrCreateRootTask(
                activity, null /* options */, candidateRootTask, null /* sourceTask */,
                launchParams, 0 /* launchFlags */, ACTIVITY_TYPE_STANDARD, true /* onTop */,
                0 /* originalCallerUid */);
        assertSame(rootTask, actualRootTask.getRootTask());
    }

    @Test
    public void getOrCreateLaunchRootUsesActivityOptionsWindowingMode() {
        final Task rootTask = createTask(
                mDisplayContent, WINDOWING_MODE_FREEFORM, ACTIVITY_TYPE_STANDARD);
        rootTask.mCreatedByOrganizer = true;
        final TaskDisplayArea taskDisplayArea = rootTask.getDisplayArea();
        taskDisplayArea.setLaunchRootTask(
                rootTask, new int[]{WINDOWING_MODE_FREEFORM}, new int[]{ACTIVITY_TYPE_STANDARD});

        final Task candidateRootTask = createTask(
                mDisplayContent, WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD);
        final ActivityRecord activity = createNonAttachedActivityRecord(mDisplayContent);
        final ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchWindowingMode(WINDOWING_MODE_FREEFORM);

        final Task actualRootTask = taskDisplayArea.getOrCreateRootTask(
                activity, options, candidateRootTask, null /* sourceTask */,
                null /* launchParams */, 0 /* launchFlags */, ACTIVITY_TYPE_STANDARD,
                true /* onTop */, 0 /* originalCallerUid */);
        assertSame(rootTask, actualRootTask.getRootTask());
        assertEquals(WINDOWING_MODE_FREEFORM, candidateRootTask.getWindowingMode());
    }

    @Test
    public void testRootTaskPositionChildAt() {
        Task pinnedTask = createTask(
                mDisplayContent, WINDOWING_MODE_PINNED, ACTIVITY_TYPE_STANDARD);
        // Root task should contain visible app window to be considered visible.
        assertFalse(pinnedTask.isVisible());
        final ActivityRecord pinnedApp = createNonAttachedActivityRecord(mDisplayContent);
        pinnedTask.addChild(pinnedApp, 0 /* addPos */);
        assertTrue(pinnedTask.isVisible());

        // Test that always-on-top root task can't be moved to position other than top.
        final Task rootTask1 = createTask(mDisplayContent);
        final Task rootTask2 = createTask(mDisplayContent);

        final WindowContainer taskContainer = rootTask1.getParent();

        final int rootTask1Pos = taskContainer.mChildren.indexOf(rootTask1);
        final int rootTask2Pos = taskContainer.mChildren.indexOf(rootTask2);
        final int pinnedTaskPos = taskContainer.mChildren.indexOf(pinnedTask);
        assertThat(pinnedTaskPos).isGreaterThan(rootTask2Pos);
        assertThat(rootTask2Pos).isGreaterThan(rootTask1Pos);

        taskContainer.positionChildAt(WindowContainer.POSITION_BOTTOM, pinnedTask, false);
        assertEquals(taskContainer.mChildren.get(rootTask1Pos), rootTask1);
        assertEquals(taskContainer.mChildren.get(rootTask2Pos), rootTask2);
        assertEquals(taskContainer.mChildren.get(pinnedTaskPos), pinnedTask);

        taskContainer.positionChildAt(1, pinnedTask, false);
        assertEquals(taskContainer.mChildren.get(rootTask1Pos), rootTask1);
        assertEquals(taskContainer.mChildren.get(rootTask2Pos), rootTask2);
        assertEquals(taskContainer.mChildren.get(pinnedTaskPos), pinnedTask);
    }

    @Test
    public void testRootTaskPositionBelowPinnedRootTask() {
        Task pinnedTask = createTask(
                mDisplayContent, WINDOWING_MODE_PINNED, ACTIVITY_TYPE_STANDARD);
        // Root task should contain visible app window to be considered visible.
        assertFalse(pinnedTask.isVisible());
        final ActivityRecord pinnedApp = createNonAttachedActivityRecord(mDisplayContent);
        pinnedTask.addChild(pinnedApp, 0 /* addPos */);
        assertTrue(pinnedTask.isVisible());

        // Test that no root task can be above pinned root task.
        final Task rootTask1 = createTask(mDisplayContent);

        final WindowContainer taskContainer = rootTask1.getParent();

        final int rootTaskPos = taskContainer.mChildren.indexOf(rootTask1);
        final int pinnedTaskPos = taskContainer.mChildren.indexOf(pinnedTask);
        assertThat(pinnedTaskPos).isGreaterThan(rootTaskPos);

        taskContainer.positionChildAt(WindowContainer.POSITION_TOP, rootTask1, false);
        assertEquals(taskContainer.mChildren.get(rootTaskPos), rootTask1);
        assertEquals(taskContainer.mChildren.get(pinnedTaskPos), pinnedTask);

        taskContainer.positionChildAt(taskContainer.mChildren.size() - 1, rootTask1, false);
        assertEquals(taskContainer.mChildren.get(rootTaskPos), rootTask1);
        assertEquals(taskContainer.mChildren.get(pinnedTaskPos), pinnedTask);
    }

    @Test
    public void testDisplayPositionWithPinnedRootTask() {
        // Make sure the display is trusted display which capable to move the root task to top.
        spyOn(mDisplayContent);
        doReturn(true).when(mDisplayContent).isTrusted();

        // Allow child root task to move to top.
        mDisplayContent.mDontMoveToTop = false;

        // The display contains pinned root task that was added in {@link #setUp}.
        final Task rootTask = createTask(mDisplayContent);
        final Task task = createTaskInRootTask(rootTask, 0 /* userId */);

        // Add another display at top.
        mWm.mRoot.positionChildAt(WindowContainer.POSITION_TOP, createNewDisplay(),
                false /* includingParents */);

        // Move the task of {@code mDisplayContent} to top.
        rootTask.positionChildAt(WindowContainer.POSITION_TOP, task, true /* includingParents */);
        final int indexOfDisplayWithPinnedRootTask = mWm.mRoot.mChildren.indexOf(mDisplayContent);

        assertEquals("The testing DisplayContent should be moved to top with task",
                mWm.mRoot.getChildCount() - 1, indexOfDisplayWithPinnedRootTask);
    }

    @Test
    public void testMovingChildTaskOnTop() {
        // Make sure the display is trusted display which capable to move the root task to top.
        spyOn(mDisplayContent);
        doReturn(true).when(mDisplayContent).isTrusted();

        // Allow child root task to move to top.
        mDisplayContent.mDontMoveToTop = false;

        // The display contains pinned root task that was added in {@link #setUp}.
        Task rootTask = createTask(mDisplayContent);
        Task task = createTaskInRootTask(rootTask, 0 /* userId */);

        // Add another display at top.
        mWm.mRoot.positionChildAt(WindowContainer.POSITION_TOP, createNewDisplay(),
                false /* includingParents */);

        // Ensure that original display ({@code mDisplayContent}) is not on top.
        assertEquals("Testing DisplayContent should not be on the top",
                mWm.mRoot.getChildCount() - 2, mWm.mRoot.mChildren.indexOf(mDisplayContent));

        // Move the task of {@code mDisplayContent} to top.
        rootTask.positionChildAt(WindowContainer.POSITION_TOP, task, true /* includingParents */);

        // Ensure that original display ({@code mDisplayContent}) is now on the top.
        assertEquals("The testing DisplayContent should be moved to top with task",
                mWm.mRoot.getChildCount() - 1, mWm.mRoot.mChildren.indexOf(mDisplayContent));
    }

    @Test
    public void testDontMovingChildTaskOnTop() {
        // Make sure the display is trusted display which capable to move the root task to top.
        spyOn(mDisplayContent);
        doReturn(true).when(mDisplayContent).isTrusted();

        // Allow child root task to move to top.
        mDisplayContent.mDontMoveToTop = true;

        // The display contains pinned root task that was added in {@link #setUp}.
        Task rootTask = createTask(mDisplayContent);
        Task task = createTaskInRootTask(rootTask, 0 /* userId */);

        // Add another display at top.
        mWm.mRoot.positionChildAt(WindowContainer.POSITION_TOP, createNewDisplay(),
                false /* includingParents */);

        // Ensure that original display ({@code mDisplayContent}) is not on top.
        assertEquals("Testing DisplayContent should not be on the top",
                mWm.mRoot.getChildCount() - 2, mWm.mRoot.mChildren.indexOf(mDisplayContent));

        // Try moving the task of {@code mDisplayContent} to top.
        rootTask.positionChildAt(WindowContainer.POSITION_TOP, task, true /* includingParents */);

        // Ensure that original display ({@code mDisplayContent}) hasn't moved and is not
        // on the top.
        assertEquals("The testing DisplayContent should not be moved to top with task",
                mWm.mRoot.getChildCount() - 2, mWm.mRoot.mChildren.indexOf(mDisplayContent));
    }

    @Test
    public void testReuseTaskAsRootTask() {
        final Task candidateTask = createTask(mDisplayContent);
        List<Integer> activityTypesWithReusableRootTask = List.of(ACTIVITY_TYPE_STANDARD,
                ACTIVITY_TYPE_RECENTS);
        for (Integer type : activityTypesWithReusableRootTask) {
            assertGetOrCreateRootTask(WINDOWING_MODE_FULLSCREEN, type, candidateTask,
                    true /* reuseCandidate */);
            assertGetOrCreateRootTask(WINDOWING_MODE_UNDEFINED, type, candidateTask,
                    true /* reuseCandidate */);
            assertGetOrCreateRootTask(WINDOWING_MODE_FREEFORM, type, candidateTask,
                    true /* reuseCandidate */);
            assertGetOrCreateRootTask(WINDOWING_MODE_MULTI_WINDOW, type, candidateTask,
                    true /* reuseCandidate */);
            assertGetOrCreateRootTask(WINDOWING_MODE_PINNED, type, candidateTask,
                    true /* reuseCandidate */);
        }

        final int windowingMode = WINDOWING_MODE_FULLSCREEN;
        assertGetOrCreateRootTask(windowingMode, ACTIVITY_TYPE_HOME, candidateTask,
                false /* reuseCandidate */);
        assertGetOrCreateRootTask(windowingMode, ACTIVITY_TYPE_ASSISTANT, candidateTask,
                false /* reuseCandidate */);
        assertGetOrCreateRootTask(windowingMode, ACTIVITY_TYPE_DREAM, candidateTask,
                false /* reuseCandidate */);
    }

    @Test
    public void testGetOrCreateRootTask_forcesReparentLeafTaskToTda() {
        final TaskDisplayArea tda = mDisplayContent.getDefaultTaskDisplayArea();
        final Task existingRoot = new TaskBuilder(mSupervisor).setTaskDisplayArea(tda).build();
        final Task candidateTask = new TaskBuilder(mSupervisor)
                .setParentTask(existingRoot)
                .setOnTop(true)
                .setCreateActivity(true)
                .build();
        final LaunchParamsController.LaunchParams params =
                new LaunchParamsController.LaunchParams();
        params.mIsRelaunchFromHomeToReparent = true;
        params.mWindowingMode = WINDOWING_MODE_FULLSCREEN;

        final Task resultRootTask = tda.getOrCreateRootTask(
                candidateTask.getTopNonFinishingActivity(), null /* options */, candidateTask,
                null /* sourceTask */, params, 0 /* launchFlags */, candidateTask.getActivityType(),
                true /* onTop */, 0 /* originalCallerUid */);

        assertNotEquals("Candidate task should be reparented to TDA", existingRoot, resultRootTask);
    }

    @Test
    public void testGetOrientation_nonResizableHomeTaskWithHomeActivityPendingVisibilityChange() {
        final RootWindowContainer rootWindowContainer = mWm.mAtmService.mRootWindowContainer;
        final TaskDisplayArea defaultTaskDisplayArea =
                rootWindowContainer.getDefaultTaskDisplayArea();

        final Task rootHomeTask = defaultTaskDisplayArea.getRootHomeTask();
        rootHomeTask.mResizeMode = RESIZE_MODE_UNRESIZEABLE;

        final Task primarySplitTask = new TaskBuilder(mSupervisor)
                .setTaskDisplayArea(defaultTaskDisplayArea)
                .setWindowingMode(WINDOWING_MODE_MULTI_WINDOW)
                .setActivityType(ACTIVITY_TYPE_STANDARD)
                .setOnTop(true)
                .setCreateActivity(true)
                .build();
        ActivityRecord primarySplitActivity = primarySplitTask.getTopNonFinishingActivity();
        assertNotNull(primarySplitActivity);
        primarySplitActivity.setState(RESUMED,
                "testGetOrientation_nonResizableHomeTaskWithHomeActivityPendingVisibilityChange");

        ActivityRecord homeActivity = rootHomeTask.getTopNonFinishingActivity();
        if (homeActivity == null) {
            homeActivity = new ActivityBuilder(mWm.mAtmService)
                    .setParentTask(rootHomeTask).setCreateTask(true).build();
        }
        homeActivity.setVisible(false);
        homeActivity.setVisibleRequested(true);
        assertFalse(rootHomeTask.isVisible());

        assertEquals(defaultTaskDisplayArea.getOrientation(), rootHomeTask.getOrientation());
    }

    @Test
    public void testIsLastFocused() {
        final TaskDisplayArea firstTaskDisplayArea = mDisplayContent.getDefaultTaskDisplayArea();
        final TaskDisplayArea secondTaskDisplayArea = createTaskDisplayArea(
                mDisplayContent, mRootWindowContainer.mWmService, "TestTaskDisplayArea",
                FEATURE_VENDOR_FIRST);
        final Task firstRootTask = firstTaskDisplayArea.createRootTask(
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD, false /* onTop */);
        final Task secondRootTask = secondTaskDisplayArea.createRootTask(
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD, false /* onTop */);
        final ActivityRecord firstActivity = new ActivityBuilder(mAtm)
                .setTask(firstRootTask).build();
        final ActivityRecord secondActivity = new ActivityBuilder(mAtm)
                .setTask(secondRootTask).build();

        // Activity on TDA1 is focused
        mDisplayContent.setFocusedApp(firstActivity);

        final int testOrientation = SCREEN_ORIENTATION_PORTRAIT;

        assertThat(firstTaskDisplayArea.canSpecifyOrientation(testOrientation)).isTrue();
        assertThat(secondTaskDisplayArea.canSpecifyOrientation(testOrientation)).isFalse();

        // No focused app, TDA1 is still recorded as last focused.
        mDisplayContent.setFocusedApp(null);

        assertThat(firstTaskDisplayArea.canSpecifyOrientation(testOrientation)).isTrue();
        assertThat(secondTaskDisplayArea.canSpecifyOrientation(testOrientation)).isFalse();

        // Activity on TDA2 is focused
        mDisplayContent.setFocusedApp(secondActivity);

        assertThat(firstTaskDisplayArea.canSpecifyOrientation(testOrientation)).isFalse();
        assertThat(secondTaskDisplayArea.canSpecifyOrientation(testOrientation)).isTrue();
    }

    @Test
    public void testCanSpecifyOrientation() {
        final TaskDisplayArea firstTaskDisplayArea = mDisplayContent.getDefaultTaskDisplayArea();
        final TaskDisplayArea secondTaskDisplayArea = createTaskDisplayArea(
                mDisplayContent, mRootWindowContainer.mWmService, "TestTaskDisplayArea",
                FEATURE_VENDOR_FIRST);
        final Task firstRootTask = firstTaskDisplayArea.createRootTask(
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD, false /* onTop */);
        final Task secondRootTask = secondTaskDisplayArea.createRootTask(
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD, false /* onTop */);
        final ActivityRecord firstActivity = new ActivityBuilder(mAtm)
                .setTask(firstRootTask).build();
        final ActivityRecord secondActivity = new ActivityBuilder(mAtm)
                .setTask(secondRootTask).build();
        firstTaskDisplayArea.setIgnoreOrientationRequest(true /* ignoreOrientationRequest */);
        secondTaskDisplayArea.setIgnoreOrientationRequest(false /* ignoreOrientationRequest */);

        final int testOrientation = SCREEN_ORIENTATION_PORTRAIT;

        // Activity on TDA1 is focused, but TDA1 cannot specify orientation because
        // ignoreOrientationRequest is true
        // Activity on TDA2 has ignoreOrientationRequest false but it doesn't have focus so it
        // cannot specify orientation
        mDisplayContent.setFocusedApp(firstActivity);

        assertThat(firstTaskDisplayArea.canSpecifyOrientation(testOrientation)).isFalse();
        assertThat(secondTaskDisplayArea.canSpecifyOrientation(testOrientation)).isFalse();

        // Activity on TDA1 is not focused, and so it cannot specify orientation
        // Activity on TDA2 is focused, and it can specify orientation because
        // ignoreOrientationRequest is false
        mDisplayContent.setFocusedApp(secondActivity);

        assertThat(firstTaskDisplayArea.canSpecifyOrientation(testOrientation)).isFalse();
        assertThat(secondTaskDisplayArea.canSpecifyOrientation(testOrientation)).isTrue();
    }

    @Test
    public void testCanSpecifyOrientationNoSensor() {
        final TaskDisplayArea firstTaskDisplayArea = mDisplayContent.getDefaultTaskDisplayArea();
        final TaskDisplayArea secondTaskDisplayArea = createTaskDisplayArea(
                mDisplayContent, mRootWindowContainer.mWmService, "TestTaskDisplayArea",
                FEATURE_VENDOR_FIRST);
        final Task firstRootTask = firstTaskDisplayArea.createRootTask(
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD, false /* onTop */);
        final Task secondRootTask = secondTaskDisplayArea.createRootTask(
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD, false /* onTop */);
        final ActivityRecord firstActivity = new ActivityBuilder(mAtm)
                .setTask(firstRootTask).build();
        final ActivityRecord secondActivity = new ActivityBuilder(mAtm)
                .setTask(secondRootTask).build();
        firstTaskDisplayArea.setIgnoreOrientationRequest(true /* ignoreOrientationRequest */);
        secondTaskDisplayArea.setIgnoreOrientationRequest(false /* ignoreOrientationRequest */);

        final int testOrientation = SCREEN_ORIENTATION_NOSENSOR;

        // ignoreOrientationRequest is always false for SCREEN_ORIENTATION_NOSENSOR so
        // only the TDAs with focus can specify orientations
        mDisplayContent.setFocusedApp(firstActivity);

        assertThat(firstTaskDisplayArea.canSpecifyOrientation(testOrientation)).isTrue();
        assertThat(secondTaskDisplayArea.canSpecifyOrientation(testOrientation)).isFalse();

        mDisplayContent.setFocusedApp(secondActivity);

        assertThat(firstTaskDisplayArea.canSpecifyOrientation(testOrientation)).isFalse();
        assertThat(secondTaskDisplayArea.canSpecifyOrientation(testOrientation)).isTrue();
    }

    @Test
    public void testCanSpecifyOrientationLocked() {
        final TaskDisplayArea firstTaskDisplayArea = mDisplayContent.getDefaultTaskDisplayArea();
        final TaskDisplayArea secondTaskDisplayArea = createTaskDisplayArea(
                mDisplayContent, mRootWindowContainer.mWmService, "TestTaskDisplayArea",
                FEATURE_VENDOR_FIRST);
        final Task firstRootTask = firstTaskDisplayArea.createRootTask(
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD, false /* onTop */);
        final Task secondRootTask = secondTaskDisplayArea.createRootTask(
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD, false /* onTop */);
        final ActivityRecord firstActivity = new ActivityBuilder(mAtm)
                .setTask(firstRootTask).build();
        final ActivityRecord secondActivity = new ActivityBuilder(mAtm)
                .setTask(secondRootTask).build();
        firstTaskDisplayArea.setIgnoreOrientationRequest(true /* ignoreOrientationRequest */);
        secondTaskDisplayArea.setIgnoreOrientationRequest(false /* ignoreOrientationRequest */);

        final int testOrientation = SCREEN_ORIENTATION_LOCKED;

        // ignoreOrientationRequest is always false for SCREEN_ORIENTATION_NOSENSOR so
        // only the TDAs with focus can specify orientations
        mDisplayContent.setFocusedApp(firstActivity);

        assertThat(firstTaskDisplayArea.canSpecifyOrientation(testOrientation)).isTrue();
        assertThat(secondTaskDisplayArea.canSpecifyOrientation(testOrientation)).isFalse();

        mDisplayContent.setFocusedApp(secondActivity);

        assertThat(firstTaskDisplayArea.canSpecifyOrientation(testOrientation)).isFalse();
        assertThat(secondTaskDisplayArea.canSpecifyOrientation(testOrientation)).isTrue();
    }

    @Test
    @UseTestDisplay
    public void testPrepareForRemoval_reparentToDefault() {
        final Task task = createTask(mDisplayContent);
        final TaskDisplayArea displayArea = task.getDisplayArea();
        displayArea.prepareForRemoval();
        assertTrue(displayArea.shouldKeepNoTask());
        assertTrue(displayArea.isRemoved());
        assertFalse(displayArea.hasChild());

        final RootWindowContainer rootWindowContainer = mWm.mAtmService.mRootWindowContainer;
        final TaskDisplayArea defaultTaskDisplayArea =
                rootWindowContainer.getDefaultTaskDisplayArea();
        assertTrue(defaultTaskDisplayArea.mChildren.contains(task));
    }

    @Test
    @UseTestDisplay
    public void testPrepareForRemoval_rootTaskCreatedByOrganizer() {
        final Task task = createTask(mDisplayContent);
        task.mCreatedByOrganizer = true;
        final TaskDisplayArea displayArea = task.getDisplayArea();
        displayArea.prepareForRemoval();
        assertTrue(displayArea.shouldKeepNoTask());
        assertTrue(displayArea.isRemoved());
        assertFalse(displayArea.hasChild());

        final RootWindowContainer rootWindowContainer = mWm.mAtmService.mRootWindowContainer;
        final TaskDisplayArea defaultTaskDisplayArea =
                rootWindowContainer.getDefaultTaskDisplayArea();
        assertFalse(defaultTaskDisplayArea.mChildren.contains(task));
    }

    @Test
    @UseTestDisplay
    @EnableFlags(FLAG_ENABLE_DISPLAY_DISCONNECT_INTERACTION)
    public void testPrepareForRemoval_rootTaskReparentsOnDisplayRemoval() {
        final Task task = createTask(mDisplayContent);
        task.mReparentOnDisplayRemoval = true;
        task.mCreatedByOrganizer = true;
        final TaskDisplayArea displayArea = task.getDisplayArea();
        displayArea.prepareForRemoval();
        assertTrue(displayArea.shouldKeepNoTask());
        assertTrue(displayArea.isRemoved());
        assertFalse(displayArea.hasChild());

        final RootWindowContainer rootWindowContainer = mWm.mAtmService.mRootWindowContainer;
        final TaskDisplayArea defaultTaskDisplayArea =
                rootWindowContainer.getDefaultTaskDisplayArea();
        assertTrue(defaultTaskDisplayArea.mChildren.contains(task));
    }

    @Test
    public void testPrepareForRemoval_childTaskDisplayAreaShouldKeepNoTask() {
        final TaskDisplayArea parentTDA = mDefaultDisplay.getDefaultTaskDisplayArea();
        final TaskDisplayArea childTDA = new TaskDisplayArea(mWm, "childTDA",
                FEATURE_VENDOR_FIRST, false /* createdByOrganizer */, true /* canHostHomeTask */);
        parentTDA.addChild(childTDA, POSITION_TOP);
        parentTDA.prepareForRemoval();

        assertTrue(parentTDA.shouldKeepNoTask());
        assertTrue(childTDA.shouldKeepNoTask());
    }

    private void assertGetOrCreateRootTask(int windowingMode, int activityType, Task candidateTask,
            boolean reuseCandidate) {
        final TaskDisplayArea taskDisplayArea = candidateTask.getDisplayArea();
        final Task rootTask = taskDisplayArea.getOrCreateRootTask(windowingMode, activityType,
                false /* onTop */, candidateTask /* candidateTask */, null /* sourceTask */,
                null /* activityOptions */, 0 /* launchFlags */,
                false /* forceReparentLeafTaskToTda */, 0 /* originalCallerUid */);
        assertEquals(reuseCandidate, rootTask == candidateTask);
    }

    @Test
    public void testGetOrCreateRootHomeTask_defaultDisplay() {
        TaskDisplayArea defaultTaskDisplayArea = mWm.mRoot.getDefaultTaskDisplayArea();

        // Remove the current home root task if it exists so a new one can be created below.
        Task homeTask = defaultTaskDisplayArea.getRootHomeTask();
        if (homeTask != null) {
            defaultTaskDisplayArea.removeChild(homeTask);
        }
        assertNull(defaultTaskDisplayArea.getRootHomeTask());

        assertNotNull(defaultTaskDisplayArea.getOrCreateRootHomeTask());
    }

    @Test
    public void testGetOrCreateRootHomeTask_supportedSecondaryDisplay() {
        DisplayContent display = createNewDisplay();
        doReturn(true).when(display).isSystemDecorationsSupported();

        // Remove the current home root task if it exists so a new one can be created below.
        TaskDisplayArea taskDisplayArea = display.getDefaultTaskDisplayArea();
        Task homeTask = taskDisplayArea.getRootHomeTask();
        if (homeTask != null) {
            taskDisplayArea.removeChild(homeTask);
        }
        assertNull(taskDisplayArea.getRootHomeTask());

        assertNotNull(taskDisplayArea.getOrCreateRootHomeTask());
    }

    @Test
    public void testGetOrCreateRootHomeTask_unsupportedSystemDecorations() {
        DisplayContent display = createNewDisplay();
        TaskDisplayArea taskDisplayArea = display.getDefaultTaskDisplayArea();
        doReturn(false).when(display).isSystemDecorationsSupported();

        assertNull(taskDisplayArea.getRootHomeTask());
        assertNull(taskDisplayArea.getOrCreateRootHomeTask());
    }

    @Test
    public void testGetOrCreateRootHomeTask_untrustedDisplay() {
        DisplayContent display = createNewDisplay();
        TaskDisplayArea taskDisplayArea = display.getDefaultTaskDisplayArea();
        doReturn(false).when(display).isTrusted();

        assertNull(taskDisplayArea.getRootHomeTask());
        assertNull(taskDisplayArea.getOrCreateRootHomeTask());
    }

    @Test
    public void testGetOrCreateRootHomeTask_dontMoveToTop() {
        DisplayContent display = createNewDisplay();
        display.mDontMoveToTop = true;
        TaskDisplayArea taskDisplayArea = display.getDefaultTaskDisplayArea();

        assertNull(taskDisplayArea.getRootHomeTask());
        assertNull(taskDisplayArea.getOrCreateRootHomeTask());
    }

    @Test
    public void testLastFocusedRootTaskIsUpdatedWhenMovingRootTask() {
        // Create a root task at bottom.
        final TaskDisplayArea taskDisplayAreas =
                mRootWindowContainer.getDefaultDisplay().getDefaultTaskDisplayArea();
        final Task rootTask =
                new TaskBuilder(mSupervisor).setOnTop(!ON_TOP).setCreateActivity(true).build();
        final Task prevFocusedRootTask = taskDisplayAreas.getFocusedRootTask();

        rootTask.moveToFront("moveRootTaskToFront");
        // After moving the root task to front, the previous focused should be the last focused.
        assertTrue(rootTask.isFocusedRootTaskOnDisplay());
        assertEquals(prevFocusedRootTask, taskDisplayAreas.getLastFocusedRootTask());

        rootTask.moveToBack("moveRootTaskToBack", null /* task */);
        // After moving the root task to back, the root task should be the last focused.
        assertEquals(rootTask, taskDisplayAreas.getLastFocusedRootTask());
    }

    /**
     * Test {@link TaskDisplayArea#mPreferredTopFocusableRootTask} will be cleared when
     * the root task is removed or moved to back, and the focused root task will be according to
     * z-order.
     */
    @Test
    public void testRootTaskShouldNotBeFocusedAfterMovingToBackOrRemoving() {
        // Create a display which only contains 2 root task.
        final DisplayContent display = addNewDisplayContentAt(POSITION_TOP);
        final Task rootTask1 = createTaskWithActivity(display.getDefaultTaskDisplayArea(),
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD, ON_TOP, true /* twoLevelTask */);
        final Task rootTask2 = createTaskWithActivity(display.getDefaultTaskDisplayArea(),
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD, ON_TOP, true /* twoLevelTask */);

        // Put rootTask1 and rootTask2 on top.
        rootTask1.moveToFront("moveRootTask1ToFront");
        rootTask2.moveToFront("moveRootTask2ToFront");
        assertTrue(rootTask2.isFocusedRootTaskOnDisplay());

        // rootTask1 should be focused after moving rootTask2 to back.
        rootTask2.moveToBack("moveRootTask2ToBack", null /* task */);
        assertTrue(rootTask1.isFocusedRootTaskOnDisplay());

        // rootTask2 should be focused after removing rootTask1.
        rootTask1.getDisplayArea().removeRootTask(rootTask1);
        assertTrue(rootTask2.isFocusedRootTaskOnDisplay());
    }

    /**
     * This test enforces that alwaysOnTop root task is placed at proper position.
     */
    @Test
    public void testAlwaysOnTopRootTaskLocation() {
        final TaskDisplayArea taskDisplayArea = mRootWindowContainer.getDefaultTaskDisplayArea();
        final Task alwaysOnTopRootTask = taskDisplayArea.createRootTask(WINDOWING_MODE_FREEFORM,
                ACTIVITY_TYPE_STANDARD, true /* onTop */);
        final ActivityRecord activity = new ActivityBuilder(mAtm)
                .setTask(alwaysOnTopRootTask).build();
        alwaysOnTopRootTask.setAlwaysOnTop(true);
        taskDisplayArea.positionChildAt(POSITION_TOP, alwaysOnTopRootTask,
                false /* includingParents */);
        assertTrue(alwaysOnTopRootTask.isAlwaysOnTop());
        assertEquals(alwaysOnTopRootTask, taskDisplayArea.getTopRootTask());

        final Task pinnedRootTask = taskDisplayArea.createRootTask(
                WINDOWING_MODE_PINNED, ACTIVITY_TYPE_STANDARD, true /* onTop */);
        assertEquals(pinnedRootTask, taskDisplayArea.getRootPinnedTask());
        assertEquals(pinnedRootTask, taskDisplayArea.getTopRootTask());

        final Task anotherAlwaysOnTopRootTask = taskDisplayArea.createRootTask(
                WINDOWING_MODE_FREEFORM, ACTIVITY_TYPE_STANDARD, true /* onTop */);
        anotherAlwaysOnTopRootTask.setAlwaysOnTop(true);
        taskDisplayArea.positionChildAt(POSITION_TOP, anotherAlwaysOnTopRootTask,
                false /* includingParents */);
        assertTrue(anotherAlwaysOnTopRootTask.isAlwaysOnTop());
        int topPosition = taskDisplayArea.getRootTaskCount() - 1;
        // Ensure the new alwaysOnTop root task is put below the pinned root task, but on top of the
        // existing alwaysOnTop root task.
        assertEquals(topPosition - 1, getTaskIndexOf(taskDisplayArea, anotherAlwaysOnTopRootTask));

        final Task nonAlwaysOnTopRootTask = taskDisplayArea.createRootTask(
                WINDOWING_MODE_FREEFORM, ACTIVITY_TYPE_STANDARD, true /* onTop */);
        assertEquals(taskDisplayArea, nonAlwaysOnTopRootTask.getDisplayArea());
        topPosition = taskDisplayArea.getRootTaskCount() - 1;
        // Ensure the non-alwaysOnTop root task is put below the three alwaysOnTop root tasks, but
        // above the existing other non-alwaysOnTop root tasks.
        assertEquals(topPosition - 3, getTaskIndexOf(taskDisplayArea, nonAlwaysOnTopRootTask));

        anotherAlwaysOnTopRootTask.setAlwaysOnTop(false);
        taskDisplayArea.positionChildAt(POSITION_TOP, anotherAlwaysOnTopRootTask,
                false /* includingParents */);
        assertFalse(anotherAlwaysOnTopRootTask.isAlwaysOnTop());
        // Ensure, when always on top is turned off for a root task, the root task is put just below
        // all other always on top root tasks.
        assertEquals(topPosition - 2, getTaskIndexOf(taskDisplayArea, anotherAlwaysOnTopRootTask));
        anotherAlwaysOnTopRootTask.setAlwaysOnTop(true);

        // Ensure always on top state changes properly when windowing mode changes.
        anotherAlwaysOnTopRootTask.setWindowingMode(WINDOWING_MODE_FULLSCREEN);
        assertFalse(anotherAlwaysOnTopRootTask.isAlwaysOnTop());
        assertEquals(topPosition - 2, getTaskIndexOf(taskDisplayArea, anotherAlwaysOnTopRootTask));
        anotherAlwaysOnTopRootTask.setWindowingMode(WINDOWING_MODE_FREEFORM);
        assertTrue(anotherAlwaysOnTopRootTask.isAlwaysOnTop());
        assertEquals(topPosition - 1, getTaskIndexOf(taskDisplayArea, anotherAlwaysOnTopRootTask));

        final Task dreamRootTask = taskDisplayArea.createRootTask(
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_DREAM, true /* onTop */);
        assertEquals(taskDisplayArea, dreamRootTask.getDisplayArea());
        assertTrue(dreamRootTask.isAlwaysOnTop());
        topPosition = taskDisplayArea.getRootTaskCount() - 1;
        // Ensure dream shows above all activities, including PiP
        assertEquals(dreamRootTask, taskDisplayArea.getTopRootTask());
        assertEquals(topPosition - 1, getTaskIndexOf(taskDisplayArea, pinnedRootTask));

        final Task assistRootTask = taskDisplayArea.createRootTask(
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_ASSISTANT, true /* onTop */);
        assertEquals(taskDisplayArea, assistRootTask.getDisplayArea());
        assertFalse(assistRootTask.isAlwaysOnTop());
        topPosition = taskDisplayArea.getRootTaskCount() - 1;

        // Ensure Assistant shows as a non-always-on-top activity when config_assistantOnTopOfDream
        // is false and on top of everything when true.
        final boolean isAssistantOnTop = mContext.getResources()
                .getBoolean(com.android.internal.R.bool.config_assistantOnTopOfDream);
        assertEquals(isAssistantOnTop ? topPosition : topPosition - 4,
                getTaskIndexOf(taskDisplayArea, assistRootTask));
    }

    /**
     * This test verifies proper launch root based on source and candidate task for split screen.
     * If a task is launching from a created-by-organizer task, it should be launched into the
     * same created-by-organizer task as well. Unless, the candidate task is already positioned in
     * the split.
     */
    @Test
    public void getLaunchRootTaskInSplit() {
        final Task rootTask = createTask(
                mDisplayContent, WINDOWING_MODE_MULTI_WINDOW, ACTIVITY_TYPE_STANDARD);
        rootTask.mCreatedByOrganizer = true;
        final Task adjacentRootTask = createTask(
                mDisplayContent, WINDOWING_MODE_MULTI_WINDOW, ACTIVITY_TYPE_STANDARD);
        adjacentRootTask.mCreatedByOrganizer = true;
        final Task candidateTask = createTaskInRootTask(rootTask, 0 /* userId*/);
        final TaskDisplayArea taskDisplayArea = rootTask.getDisplayArea();
        adjacentRootTask.setAdjacentTaskFragments(
                new TaskFragment.AdjacentSet(adjacentRootTask, rootTask));

        // Verify the launch root with candidate task
        Task actualRootTask = taskDisplayArea.getLaunchRootTask(WINDOWING_MODE_UNDEFINED,
                ACTIVITY_TYPE_STANDARD, null /* options */, adjacentRootTask /* sourceTask */,
                0 /* launchFlags */, candidateTask, 0 /* originalCallerUid */);
        assertSame(rootTask, actualRootTask);

        // Verify the launch root task without candidate task
        actualRootTask = taskDisplayArea.getLaunchRootTask(WINDOWING_MODE_UNDEFINED,
                ACTIVITY_TYPE_STANDARD, null /* options */, adjacentRootTask /* sourceTask */,
                0 /* launchFlags */);
        assertSame(adjacentRootTask, actualRootTask);

        final Task pinnedTask = createTask(
                mDisplayContent, WINDOWING_MODE_PINNED, ACTIVITY_TYPE_STANDARD);
        // Verify not adjusting launch target for pinned candidate task
        actualRootTask = taskDisplayArea.getLaunchRootTask(WINDOWING_MODE_UNDEFINED,
                ACTIVITY_TYPE_STANDARD, null /* options */, adjacentRootTask /* sourceTask */,
                0 /* launchFlags */, pinnedTask /* candidateTask */, 0 /* originalCallerUid */);
        assertNull(actualRootTask);
    }

    @Test
    public void testMovedRootTaskToFront() {
        final TaskDisplayArea tda = mDefaultDisplay.getDefaultTaskDisplayArea();
        final Task rootTask = createTask(tda, WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD,
                true /* onTop */, true /* createActivity */, true /* twoLevelTask */,
                false /* forceOpaque */, false /* shouldIgnoreInsets */,
                false /* disableAppCompatRoundedCorners */);
        final Task leafTask = rootTask.getTopLeafTask();

        clearInvocations(tda);
        tda.onTaskMoved(rootTask, true /* toTop */, false /* toBottom */);
        verify(tda).onLeafTaskMoved(eq(leafTask), anyBoolean(), anyBoolean());
    }

    /**
     * Verifies that when a pinned task is reparented to a new TaskDisplayArea, the
     * {@link TaskDisplayArea#mRootPinnedTask} reference is correctly updated. This is the scenario
     * for dragging a PiP window to another display.
     */
    @Test
    public void testReparentingPinnedTask_updatesRootPinnedTaskReference() {
        // Create a second display with its own TaskDisplayArea.
        final DisplayContent secondDisplay = createNewDisplay();
        final TaskDisplayArea firstTda = mDisplayContent.getDefaultTaskDisplayArea();
        final TaskDisplayArea secondTda = secondDisplay.getDefaultTaskDisplayArea();

        // Create a pinned task in the first TDA.
        final Task pinnedTask = createTask(firstTda, WINDOWING_MODE_PINNED, ACTIVITY_TYPE_STANDARD);
        createActivityRecord(pinnedTask);

        // Verify initial state: the first TDA has the pinned task, the second does not.
        assertThat(firstTda.getRootPinnedTask()).isEqualTo(pinnedTask);
        assertThat(secondTda.getRootPinnedTask()).isNull();

        // Reparent the task to the second TDA. This simulates dragging a PiP to another display.
        pinnedTask.reparent(secondTda, true /* onTop */);

        // Verify final state: the reference should be removed from the first TDA and added to the
        // second TDA.
        assertThat(firstTda.getRootPinnedTask()).isNull();
        assertThat(secondTda.getRootPinnedTask()).isEqualTo(pinnedTask);
    }

    @Test
    public void testGetLaunchRootTask_preserveLeafTaskDesktopRelaunch_returnsCandidateRoot() {
        final Task sourceDeskRoot = createTask(
                mDisplayContent, WINDOWING_MODE_FREEFORM, ACTIVITY_TYPE_STANDARD);
        sourceDeskRoot.mCreatedByOrganizer = true;
        final TaskDisplayArea tda = mDisplayContent.getDefaultTaskDisplayArea();
        tda.setLaunchRootTask(sourceDeskRoot,
                new int[]{WINDOWING_MODE_FREEFORM, WINDOWING_MODE_UNDEFINED} /* windowingModes */,
                new int[]{ACTIVITY_TYPE_UNDEFINED, ACTIVITY_TYPE_STANDARD} /* activityTypes */);
        final Task sourceTask = createTaskInRootTask(sourceDeskRoot, 0 /* userId */);

        final Task candidateRoot = createTask(
                mDisplayContent, WINDOWING_MODE_MULTI_WINDOW, ACTIVITY_TYPE_STANDARD);
        candidateRoot.mCreatedByOrganizer = true;
        candidateRoot.mPreserveLeafTaskIfRelaunch = true;
        final Task candidateTask = createTaskInRootTask(candidateRoot, 0 /* userId */);

        final Task launchRootTask = tda.getLaunchRootTask(WINDOWING_MODE_MULTI_WINDOW,
                ACTIVITY_TYPE_STANDARD, null /* options */, sourceTask, 0 /* launchFlags */,
                candidateTask, 0 /* originalCallerUid */);

        assertThat(launchRootTask).isEqualTo(candidateRoot);
    }

    @Test
    public void testGetLaunchRootTask_preserveLeafTaskSplitRelaunch_returnsCandidateRoot() {
        final TaskDisplayArea tda = mDisplayContent.getDefaultTaskDisplayArea();
        final Task sourceRoot = createTask(
                mDisplayContent, WINDOWING_MODE_MULTI_WINDOW, ACTIVITY_TYPE_STANDARD);
        sourceRoot.mCreatedByOrganizer = true;
        final Task adjacentRoot = createTask(
                mDisplayContent, WINDOWING_MODE_MULTI_WINDOW, ACTIVITY_TYPE_STANDARD);
        adjacentRoot.mCreatedByOrganizer = true;
        adjacentRoot.setAdjacentTaskFragments(
                new TaskFragment.AdjacentSet(adjacentRoot, sourceRoot));
        final Task sourceTask = createTaskInRootTask(sourceRoot, 0 /* userId */);

        final Task candidateRoot = createTask(
                mDisplayContent, WINDOWING_MODE_MULTI_WINDOW, ACTIVITY_TYPE_STANDARD);
        candidateRoot.mCreatedByOrganizer = true;
        candidateRoot.mPreserveLeafTaskIfRelaunch = true;
        final Task candidateTask = createTaskInRootTask(candidateRoot, 0 /* userId */);

        final Task launchRootTask = tda.getLaunchRootTask(WINDOWING_MODE_UNDEFINED,
                ACTIVITY_TYPE_STANDARD, null /* options */, sourceTask, 0 /* launchFlags */,
                candidateTask, 1000 /* originalCallerUid */);

        assertThat(launchRootTask).isEqualTo(candidateRoot);
    }

    @Test
    public void testGetLaunchRootTask_preserveLeafTaskSameAppSplitRelaunch_returnsSourceRoot() {
        final TaskDisplayArea tda = mDisplayContent.getDefaultTaskDisplayArea();
        final Task sourceRoot = createTask(
                mDisplayContent, WINDOWING_MODE_MULTI_WINDOW, ACTIVITY_TYPE_STANDARD);
        sourceRoot.mCreatedByOrganizer = true;
        final Task adjacentRoot = createTask(
                mDisplayContent, WINDOWING_MODE_MULTI_WINDOW, ACTIVITY_TYPE_STANDARD);
        adjacentRoot.mCreatedByOrganizer = true;
        adjacentRoot.setAdjacentTaskFragments(
                new TaskFragment.AdjacentSet(adjacentRoot, sourceRoot));
        final Task sourceTask = createTaskInRootTask(sourceRoot, 0 /* userId */);

        final Task candidateRoot = createTask(
                mDisplayContent, WINDOWING_MODE_MULTI_WINDOW, ACTIVITY_TYPE_STANDARD);
        candidateRoot.mCreatedByOrganizer = true;
        candidateRoot.mPreserveLeafTaskIfRelaunch = true;
        final Task candidateTask = createTaskInRootTask(candidateRoot, 0 /* userId */);

        final Task launchRootTask = tda.getLaunchRootTask(WINDOWING_MODE_UNDEFINED,
                ACTIVITY_TYPE_STANDARD, null /* options */, sourceTask, 0 /* launchFlags */,
                candidateTask, candidateTask.effectiveUid /* originalCallerUid */);

        assertThat(launchRootTask).isEqualTo(sourceRoot);
    }

    @Test
    @EnableFlags(com.android.window.flags.Flags.FLAG_ENABLE_BUBBLE_ROOT_TASK)
    public void getLaunchRootTask_launchFromDifferentDisplay_checksDisplayArea() {
        final Task rootTask = createTask(
                mDisplayContent, WINDOWING_MODE_MULTI_WINDOW, ACTIVITY_TYPE_STANDARD);
        rootTask.mCreatedByOrganizer = true;
        final Task sourceTask = createTaskInRootTask(rootTask, 0 /* userId */);
        final TaskDisplayArea taskDisplayArea = rootTask.getDisplayArea();

        // Create a secondary display.
        final DisplayContent secondDisplay = createNewDisplay();
        final TaskDisplayArea secondTaskDisplayArea = secondDisplay.getDefaultTaskDisplayArea();

        // Verify that launching from a different display returns null.
        Task actualRootTask = secondTaskDisplayArea.getLaunchRootTask(
                WINDOWING_MODE_UNDEFINED, ACTIVITY_TYPE_STANDARD, null /* options */,
                sourceTask /* sourceTask */, 0 /* launchFlags */);
        assertNull(actualRootTask);

        // Verify that launching from the same display returns the root task.
        actualRootTask = taskDisplayArea.getLaunchRootTask(
                WINDOWING_MODE_UNDEFINED, ACTIVITY_TYPE_STANDARD, null /* options */,
                sourceTask /* sourceTask */, 0 /* launchFlags */);
        assertSame(rootTask, actualRootTask);
    }

    @Test
    public void getLaunchRootTask_nullSourceTask_returnsNull() {
        final TaskDisplayArea tda = mDisplayContent.getDefaultTaskDisplayArea();
        final Task candidateTask = createTask(mDisplayContent);

        final Task launchRootTask = tda.getLaunchRootTask(WINDOWING_MODE_UNDEFINED,
                ACTIVITY_TYPE_STANDARD, null /* options */, null /* sourceTask */,
                0 /* launchFlags */, candidateTask, 0 /* originalCallerUid */);

        assertThat(launchRootTask).isNull();
    }

    @Test
    public void getLaunchRootTask_adjacentTasks_candidateNotAdjacent_returnsSourceOrganizerTask() {
        final TaskDisplayArea tda = mDisplayContent.getDefaultTaskDisplayArea();
        final Task sourceRoot = createTask(tda, WINDOWING_MODE_MULTI_WINDOW,
                ACTIVITY_TYPE_STANDARD);
        sourceRoot.mCreatedByOrganizer = true;
        final Task adjacentRoot = createTask(tda, WINDOWING_MODE_MULTI_WINDOW,
                ACTIVITY_TYPE_STANDARD);
        adjacentRoot.mCreatedByOrganizer = true;
        adjacentRoot.setAdjacentTaskFragments(
                new TaskFragment.AdjacentSet(adjacentRoot, sourceRoot));
        final Task sourceTask = createTaskInRootTask(sourceRoot, 0 /* userId */);

        // Candidate task is in a different, non-adjacent root task.
        final Task otherRoot = createTask(tda, WINDOWING_MODE_MULTI_WINDOW,
                ACTIVITY_TYPE_STANDARD);
        final Task candidateTask = createTaskInRootTask(otherRoot, 0 /* userId */);

        final Task launchRootTask = tda.getLaunchRootTask(WINDOWING_MODE_UNDEFINED,
                ACTIVITY_TYPE_STANDARD, null /* options */, sourceTask, 0 /* launchFlags */,
                candidateTask, 0 /* originalCallerUid */);

        // The launch root should fall back to the source's organizer task because the candidate
        // is not in an adjacent task and does not need to be preserved.
        assertThat(launchRootTask).isEqualTo(sourceRoot);
    }

    @Test
    @EnableFlags(com.android.window.flags.Flags.FLAG_ENABLE_BUBBLE_ROOT_TASK)
    public void getLaunchRootTask_bubbleRootTask_parentInSameTda_returnsParent() {
        final TaskDisplayArea tda = mDisplayContent.getDefaultTaskDisplayArea();
        final Task parentTask = createTask(tda, WINDOWING_MODE_MULTI_WINDOW,
                ACTIVITY_TYPE_STANDARD);
        parentTask.mCreatedByOrganizer = true;
        final Task sourceTask = createTaskInRootTask(parentTask, 0 /* userId */);

        final Task launchRootTask = tda.getLaunchRootTask(WINDOWING_MODE_UNDEFINED,
                ACTIVITY_TYPE_STANDARD, null /* options */, sourceTask, 0 /* launchFlags */,
                null /* candidateTask */, 0 /* originalCallerUid */);

        // The launch root should be the source task's parent because it's an organizer-created
        // task in the same TaskDisplayArea.
        assertThat(launchRootTask).isEqualTo(parentTask);
    }

    @Test
    public void testOnTaskMoved_topRootTaskToBack_notifiesNewTopTask() {
        final TaskDisplayArea tda = mDefaultDisplay.getDefaultTaskDisplayArea();

        // Create a root task with a leaf task and an activity.
        final Task initialTopRoot = new TaskBuilder(mSupervisor).setTaskDisplayArea(tda).build();
        final Task initialTopLeaf = new TaskBuilder(mSupervisor).setParentTask(initialTopRoot)
                .setCreateActivity(true).build();

        // Create another root task with two leaf tasks.
        final Task anotherRoot = new TaskBuilder(mSupervisor).setTaskDisplayArea(tda).build();
        final Task bottomLeaf = new TaskBuilder(mSupervisor).setParentTask(anotherRoot)
                .setCreateActivity(true).build();
        final Task nextTopLeaf = new TaskBuilder(mSupervisor).setParentTask(anotherRoot)
                .setCreateActivity(true).build();

        // Ensure the initialTopRoot is on top, making initialTopLeaf the top task.
        initialTopRoot.moveToFront("move initialTopRoot to front");

        spyOn(mAtm.getTaskChangeNotificationController());

        // Now move initialTopRoot to back.
        initialTopRoot.moveToBack("move initialTopRoot to back", null /* task */);

        // Verify that the notification controller was informed that the new top task
        // (nextTopLeaf) was moved to the front.
        final ArgumentCaptor<TaskInfo> taskInfoCaptor = ArgumentCaptor.forClass(TaskInfo.class);

        verify(mAtm.getTaskChangeNotificationController()).notifyTaskMovedToFront(
                taskInfoCaptor.capture());
        assertEquals(nextTopLeaf.mTaskId, taskInfoCaptor.getValue().taskId);
    }
}
