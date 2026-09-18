/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static android.app.FullscreenRequestHandler.REQUEST_ALLOW_MODE_ENTER;
import static android.app.FullscreenRequestHandler.REQUEST_ALLOW_MODE_EXIT;
import static android.app.FullscreenRequestHandler.REQUEST_ALLOW_MODE_INHERIT;
import static android.app.FullscreenRequestHandler.REQUEST_ALLOW_MODE_NONE;
import static android.app.TaskInfo.SELF_MOVABLE_ALLOWED;
import static android.app.TaskInfo.SELF_MOVABLE_DEFAULT;
import static android.app.TaskInfo.SELF_MOVABLE_DENIED;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD;
import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW;
import static android.app.WindowConfiguration.WINDOWING_MODE_UNDEFINED;
import static android.window.DisplayAreaOrganizer.FEATURE_DEFAULT_TASK_CONTAINER;
import static android.window.WindowContainerTransaction.HierarchyOp.HIERARCHY_OP_TYPE_APP_COMPAT_REACHABILITY;
import static android.window.WindowContainerTransaction.HierarchyOp.LAUNCH_KEY_TASK_ID;
import static android.window.WindowContainerTransaction.HierarchyOp.REACHABILITY_EVENT_X;
import static android.window.WindowContainerTransaction.HierarchyOp.REACHABILITY_EVENT_Y;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import android.content.Intent;
import android.graphics.Rect;
import android.os.Binder;
import android.os.Bundle;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;
import android.window.IDisplayAreaOrganizer;
import android.window.ITaskOrganizer;
import android.window.WindowContainerToken;
import android.window.WindowContainerTransaction;
import android.window.WindowContainerTransaction.HierarchyOp;

import androidx.annotation.NonNull;
import androidx.test.filters.SmallTest;

import com.android.window.flags.Flags;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

/**
 * Test class for {@link WindowContainerTransaction}.
 *
 * Build/Install/Run:
 *  atest WmTests:WindowContainerTransactionTests
 */
@SmallTest
@Presubmit
@RunWith(WindowTestRunner.class)
public class WindowContainerTransactionTests extends WindowTestsBase {
    private final Rect mSafeRegionBounds = new Rect(50, 50, 200, 300);
    private final ITaskOrganizer mMockTaskOrg = mock(ITaskOrganizer.class);
    private final IDisplayAreaOrganizer mMockDAOrg = mock(IDisplayAreaOrganizer.class);

    @Before
    public void setupOrganizers() {
        when(mMockTaskOrg.asBinder()).thenReturn(new Binder());
        when(mMockDAOrg.asBinder()).thenReturn(new Binder());
        mAtm.mTaskOrganizerController.registerTaskOrganizer(mMockTaskOrg);
        mAtm.mWindowOrganizerController.mDisplayAreaOrganizerController.registerOrganizer(
                mMockDAOrg, FEATURE_DEFAULT_TASK_CONTAINER);
    }

    @Test
    public void testRemoveTask() {
        final Task rootTask = createOrganizerTask(mDisplayContent);
        final Task task = createTaskInRootTask(rootTask, 0 /* userId */);
        final ActivityRecord activity = createActivityRecord(mDisplayContent, task);

        WindowContainerTransaction wct = new WindowContainerTransaction();
        WindowContainerToken token = task.getTaskInfo().token;
        wct.removeTask(token);
        applyTransaction(wct);

        // There is still an activity to be destroyed, so the task is not removed immediately.
        assertNotNull(task.getParent());
        assertTrue(rootTask.hasChild());
        assertTrue(task.hasChild());
        assertTrue(activity.finishing);
        // Simulate idle to destroy mFinishingActivities
        mSupervisor.processStoppingAndFinishingActivities(null /* launchedActivity */,
                false /* processPausingActivities */, "test");
        activity.destroyed("testRemoveContainer");
        // Assert that the container was removed after the activity is destroyed.
        assertNull(task.getParent());
        assertEquals(0, task.getChildCount());
        assertNull(activity.getParent());
        verify(mAtm.getLockTaskController(), atLeast(1)).clearLockedTask(task);
        // Created-by-organizer tasks are not removed by WM
        assertTrue(rootTask.isAttached());
    }

    @Test
    public void testRemoveRootTask() {
        final Task rootTask = createOrganizerTask(mDisplayContent);
        final Task task = createTaskInRootTask(rootTask, 0 /* userId */);
        final ActivityRecord activity = createActivityRecord(mDisplayContent, task);
        final TaskDisplayArea taskDisplayArea = mDisplayContent.getDefaultTaskDisplayArea();

        WindowContainerTransaction wct = new WindowContainerTransaction();
        WindowContainerToken token = rootTask.getTaskInfo().token;
        wct.removeRootTask(token);
        applyTransaction(wct);

        // There is still an activity to be destroyed, so the task is not removed immediately.
        assertNotNull(task.getParent());
        assertTrue(rootTask.hasChild());
        assertTrue(task.hasChild());
        assertTrue(activity.finishing);
        // Simulate idle to destroy mFinishingActivities.
        mSupervisor.processStoppingAndFinishingActivities(null /* launchedActivity */,
                false /* processPausingActivities */, "test");
        activity.destroyed("testRemoveRootTask");
        // Assert that the container was removed after the activity is destroyed.
        assertNull(task.getParent());
        assertEquals(0, task.getChildCount());
        assertNull(activity.getParent());
        assertNull(taskDisplayArea.getTask(task1 -> task1.mTaskId == rootTask.mTaskId));
        verify(mAtm.getLockTaskController(), atLeast(1)).clearLockedTask(task);
        verify(mAtm.getLockTaskController(), atLeast(1)).clearLockedTask(rootTask);
    }

    @Test
    public void testDesktopMode_tasksAreBroughtToFront() {
        final TestDesktopOrganizer desktopOrganizer = new TestDesktopOrganizer(mAtm);
        TaskDisplayArea tda = desktopOrganizer.mDefaultTDA;
        List<ActivityRecord> activityRecords = new ArrayList<>();
        int numberOfTasks = 4;
        desktopOrganizer.createFreeformTasksWithActivities(desktopOrganizer,
                activityRecords, numberOfTasks);

        final Task task = createTask(mDisplayContent);
        final ActivityRecord activity = createActivityRecord(mDisplayContent, task);
        task.setWindowingMode(WINDOWING_MODE_FULLSCREEN);

        // Bring home to front of the tasks
        desktopOrganizer.bringHomeToFront();

        // Bring tasks in front of the home
        WindowContainerTransaction wct = new WindowContainerTransaction();
        desktopOrganizer.bringDesktopTasksToFront(wct);
        applyTransaction(wct);

        // Verify tasks are resumed and in correct z-order
        verify(mRootWindowContainer, times(2)).ensureActivitiesVisible();
        for (int i = 0; i < numberOfTasks - 1; i++) {
            assertTrue(tda.mChildren
                    .indexOf(desktopOrganizer.mTasks.get(i).getRootTask())
                    < tda.mChildren.indexOf(desktopOrganizer.mTasks.get(i + 1).getRootTask()));
        }
    }

    @Test
    public void testDesktopMode_moveTaskToDesktop() {
        final TestDesktopOrganizer desktopOrganizer = new TestDesktopOrganizer(mAtm);
        TaskDisplayArea tda = desktopOrganizer.mDefaultTDA;
        List<ActivityRecord> activityRecords = new ArrayList<>();
        int numberOfTasks = 4;
        desktopOrganizer.createFreeformTasksWithActivities(desktopOrganizer,
                activityRecords, numberOfTasks);

        final Task task = createTask(mDisplayContent);
        final ActivityRecord activity = createActivityRecord(mDisplayContent, task);
        task.setWindowingMode(WINDOWING_MODE_FULLSCREEN);

        // Bring home to front of the tasks
        desktopOrganizer.bringHomeToFront();

        // Bring tasks in front of the home and newly moved task to on top of them
        WindowContainerTransaction wct = new WindowContainerTransaction();
        desktopOrganizer.bringDesktopTasksToFront(wct);
        desktopOrganizer.addMoveToDesktopChanges(wct, task, true);
        wct.setBounds(task.getTaskInfo().token, desktopOrganizer.getDefaultDesktopTaskBounds());
        applyTransaction(wct);

        // Verify tasks are resumed
        verify(mRootWindowContainer, times(2)).ensureActivitiesVisible();

        // Tasks are in correct z-order
        for (int i = 0; i < numberOfTasks - 1; i++) {
            assertTrue(tda.mChildren
                    .indexOf(desktopOrganizer.mTasks.get(i).getRootTask())
                    < tda.mChildren.indexOf(desktopOrganizer.mTasks.get(i + 1).getRootTask()));
        }
        // New task is on top of other tasks
        assertTrue(tda.mChildren
                .indexOf(desktopOrganizer.mTasks.get(3).getRootTask())
                < tda.mChildren.indexOf(task));

        // New task is in freeform and has specified bounds
        assertEquals(WINDOWING_MODE_FREEFORM, task.getWindowingMode());
        assertEquals(desktopOrganizer.getDefaultDesktopTaskBounds(), task.getBounds());
    }

    @Test
    public void testDesktopMode_moveTaskToFullscreen() {
        final TestDesktopOrganizer desktopOrganizer = new TestDesktopOrganizer(mAtm);
        List<ActivityRecord> activityRecords = new ArrayList<>();
        int numberOfTasks = 4;
        desktopOrganizer.createFreeformTasksWithActivities(desktopOrganizer,
                activityRecords, numberOfTasks);

        Task taskToMove = desktopOrganizer.mTasks.get(numberOfTasks - 1);

        // Bring tasks in front of the home and newly moved task to on top of them
        WindowContainerTransaction wct = new WindowContainerTransaction();
        desktopOrganizer.addMoveToFullscreen(wct, taskToMove, false);
        applyTransaction(wct);

        // New task is in freeform
        assertEquals(WINDOWING_MODE_FULLSCREEN, taskToMove.getWindowingMode());
    }

    @Test
    @EnableFlags(Flags.FLAG_SAFE_REGION_LETTERBOXING_V1)
    public void testSetSafeRegionBoundsOnTaskDisplayArea() {
        final Task rootTask = createOrganizerTask(mDisplayContent);
        final Task task = createTaskInRootTask(rootTask, 0 /* userId */);
        final ActivityRecord activity = createActivityRecord(mDisplayContent, task);
        final TaskDisplayArea taskDisplayArea = mDisplayContent.getDefaultTaskDisplayArea();

        final WindowContainerTransaction wct = new WindowContainerTransaction();
        final WindowContainerToken token = taskDisplayArea.mRemoteToken.toWindowContainerToken();
        // Set safe region bounds on the task display area
        wct.setSafeRegionBounds(token, mSafeRegionBounds);
        applyTransaction(wct);

        assertEquals(activity.getSafeRegionBounds(), mSafeRegionBounds);
        assertEquals(task.getSafeRegionBounds(), mSafeRegionBounds);
        assertEquals(rootTask.getSafeRegionBounds(), mSafeRegionBounds);
        assertEquals(taskDisplayArea.getSafeRegionBounds(), mSafeRegionBounds);
    }

    @Test
    @EnableFlags(Flags.FLAG_SAFE_REGION_LETTERBOXING_V1)
    public void testSetSafeRegionBoundsOnRootTask() {
        final Task rootTask = createOrganizerTask(mDisplayContent);
        final Task task = createTaskInRootTask(rootTask, 0 /* userId */);
        final ActivityRecord activity = createActivityRecord(mDisplayContent, task);
        final TaskDisplayArea taskDisplayArea = mDisplayContent.getDefaultTaskDisplayArea();

        final WindowContainerTransaction wct = new WindowContainerTransaction();
        final WindowContainerToken token = rootTask.mRemoteToken.toWindowContainerToken();
        // Set safe region bounds on the root task
        wct.setSafeRegionBounds(token, mSafeRegionBounds);
        applyTransaction(wct);

        assertEquals(activity.getSafeRegionBounds(), mSafeRegionBounds);
        assertEquals(task.getSafeRegionBounds(), mSafeRegionBounds);
        assertEquals(rootTask.getSafeRegionBounds(), mSafeRegionBounds);
        assertNull(taskDisplayArea.getSafeRegionBounds());
    }

    @Test
    @EnableFlags(Flags.FLAG_SAFE_REGION_LETTERBOXING_V1)
    public void testSetSafeRegionBoundsOnTask() {
        final Task rootTask = createOrganizerTask(mDisplayContent);
        final Task task = createTaskInRootTask(rootTask, 0 /* userId */);
        final ActivityRecord activity = createActivityRecord(mDisplayContent, task);
        final TaskDisplayArea taskDisplayArea = mDisplayContent.getDefaultTaskDisplayArea();

        final WindowContainerTransaction wct = new WindowContainerTransaction();
        final WindowContainerToken token = task.mRemoteToken.toWindowContainerToken();
        // Set safe region bounds on the task
        wct.setSafeRegionBounds(token, mSafeRegionBounds);
        applyTransaction(wct);

        assertEquals(activity.getSafeRegionBounds(), mSafeRegionBounds);
        assertEquals(task.getSafeRegionBounds(), mSafeRegionBounds);
        assertNull(rootTask.getSafeRegionBounds());
        assertNull(taskDisplayArea.getSafeRegionBounds());
    }

    @Test
    @EnableFlags(Flags.FLAG_SAFE_REGION_LETTERBOXING_V1)
    public void testSetSafeRegionBoundsOnTask_resetSafeRegionBounds() {
        final Task rootTask = createOrganizerTask(mDisplayContent);
        final Task task = createTaskInRootTask(rootTask, 0 /* userId */);
        final ActivityRecord activity = createActivityRecord(mDisplayContent, task);
        final TaskDisplayArea taskDisplayArea = mDisplayContent.getDefaultTaskDisplayArea();

        final WindowContainerTransaction wct = new WindowContainerTransaction();
        final WindowContainerToken token = task.mRemoteToken.toWindowContainerToken();
        // Set safe region bounds on the task
        wct.setSafeRegionBounds(token, mSafeRegionBounds);
        applyTransaction(wct);

        assertEquals(activity.getSafeRegionBounds(), mSafeRegionBounds);
        assertEquals(task.getSafeRegionBounds(), mSafeRegionBounds);
        assertNull(rootTask.getSafeRegionBounds());
        assertNull(taskDisplayArea.getSafeRegionBounds());

        // Reset safe region bounds on the task
        wct.setSafeRegionBounds(token, /* safeRegionBounds */null);
        applyTransaction(wct);

        assertNull(activity.getSafeRegionBounds());
        assertNull(task.getSafeRegionBounds());
        assertNull(rootTask.getSafeRegionBounds());
        assertNull(taskDisplayArea.getSafeRegionBounds());
    }

    @Test
    @EnableFlags(Flags.FLAG_SAFE_REGION_LETTERBOXING_V1)
    public void testSetSafeRegionBoundsOnRootTaskAndTask() {
        final Task rootTask = createOrganizerTask(mDisplayContent);
        final Task task = createTaskInRootTask(rootTask, 0 /* userId */);
        final ActivityRecord activity = createActivityRecord(mDisplayContent, task);
        final TaskDisplayArea taskDisplayArea = mDisplayContent.getDefaultTaskDisplayArea();

        final WindowContainerTransaction wct = new WindowContainerTransaction();
        final WindowContainerToken token = rootTask.mRemoteToken.toWindowContainerToken();
        // Set safe region bounds on the root task
        wct.setSafeRegionBounds(token, mSafeRegionBounds);
        // Set different safe region bounds on task
        final Rect tempSafeRegionBounds = new Rect(30, 30, 200, 200);
        wct.setSafeRegionBounds(task.mRemoteToken.toWindowContainerToken(), tempSafeRegionBounds);
        applyTransaction(wct);

        assertEquals(activity.getSafeRegionBounds(), tempSafeRegionBounds);
        assertEquals(task.getSafeRegionBounds(), tempSafeRegionBounds);
        assertEquals(rootTask.getSafeRegionBounds(), mSafeRegionBounds);
        assertNull(taskDisplayArea.getSafeRegionBounds());
    }

    @Test
    @EnableFlags(Flags.FLAG_SAFE_REGION_LETTERBOXING_V1)
    public void testSetSafeRegionBoundsOnRootTaskAndTask_resetSafeRegionBoundsOnTask() {
        final Task rootTask = createOrganizerTask(mDisplayContent);
        final Task task = createTaskInRootTask(rootTask, 0 /* userId */);
        final ActivityRecord activity = createActivityRecord(mDisplayContent, task);
        final TaskDisplayArea taskDisplayArea = mDisplayContent.getDefaultTaskDisplayArea();

        final WindowContainerTransaction wct = new WindowContainerTransaction();
        final WindowContainerToken token = rootTask.mRemoteToken.toWindowContainerToken();
        // Set safe region bounds on the root task
        wct.setSafeRegionBounds(token, mSafeRegionBounds);
        // Set different safe region bounds on task
        final Rect mTmpSafeRegionBounds = new Rect(30, 30, 200, 200);
        wct.setSafeRegionBounds(task.mRemoteToken.toWindowContainerToken(), mTmpSafeRegionBounds);
        applyTransaction(wct);

        // Task and activity will use different safe region bounds
        assertEquals(activity.getSafeRegionBounds(), mTmpSafeRegionBounds);
        assertEquals(task.getSafeRegionBounds(), mTmpSafeRegionBounds);
        assertEquals(rootTask.getSafeRegionBounds(), mSafeRegionBounds);
        assertNull(taskDisplayArea.getSafeRegionBounds());

        // Reset safe region bounds on task
        wct.setSafeRegionBounds(task.mRemoteToken.toWindowContainerToken(),
                /* safeRegionBounds */null);
        applyTransaction(wct);

        assertEquals(activity.getSafeRegionBounds(), mSafeRegionBounds);
        assertEquals(task.getSafeRegionBounds(), mSafeRegionBounds);
        assertEquals(rootTask.getSafeRegionBounds(), mSafeRegionBounds);
        assertNull(taskDisplayArea.getSafeRegionBounds());
    }

    @Test
    public void testSetTaskForceExcludedFromRecents() {
        final Task rootTask = createOrganizerTask(mDisplayContent);
        final Task task = createTaskInRootTask(rootTask, 0 /* userId */);
        final WindowContainerTransaction wct = new WindowContainerTransaction();
        final WindowContainerToken token = task.mRemoteToken.toWindowContainerToken();

        wct.setTaskForceExcludedFromRecents(token, true /* forceExcluded */);
        applyTransaction(wct);

        assertTrue(task.isForceExcludedFromRecents());
    }

    @Test
    public void testSetTaskForceExcludedFromRecents_resetsTaskForceExcludedFromRecents() {
        final Task rootTask = createTask(mDisplayContent);
        final Task task = createTaskInRootTask(rootTask, 0 /* userId */);
        final WindowContainerTransaction wct = new WindowContainerTransaction();
        final WindowContainerToken token = task.mRemoteToken.toWindowContainerToken();
        wct.setTaskForceExcludedFromRecents(token, true /* forceExcluded */);
        applyTransaction(wct);

        // Re-include the task using WCT.
        wct.setTaskForceExcludedFromRecents(token, false /* forceExcluded */);
        applyTransaction(wct);

        assertFalse(task.isForceExcludedFromRecents());
    }

    @Test
    public void testDesktopMode_moveTaskToFront() {
        final TestDesktopOrganizer desktopOrganizer = new TestDesktopOrganizer(mAtm);
        TaskDisplayArea tda = desktopOrganizer.mDefaultTDA;
        List<ActivityRecord> activityRecords = new ArrayList<>();
        int numberOfTasks = 5;
        desktopOrganizer.createFreeformTasksWithActivities(desktopOrganizer,
                activityRecords, numberOfTasks);

        // Bring task 2 on top of other tasks
        WindowContainerTransaction wct = new WindowContainerTransaction();
        wct.reorder(desktopOrganizer.mTasks.get(2).getTaskInfo().token, true /* onTop */);
        applyTransaction(wct);

        // Tasks are in correct z-order
        assertTrue(tda.mChildren.indexOf(desktopOrganizer.mTasks.get(0).getRootTask())
                < tda.mChildren.indexOf(desktopOrganizer.mTasks.get(1).getRootTask()));
        assertTrue(tda.mChildren.indexOf(desktopOrganizer.mTasks.get(1).getRootTask())
                < tda.mChildren.indexOf(desktopOrganizer.mTasks.get(3).getRootTask()));
        assertTrue(tda.mChildren.indexOf(desktopOrganizer.mTasks.get(3).getRootTask())
                < tda.mChildren.indexOf(desktopOrganizer.mTasks.get(4).getRootTask()));
        assertTrue(tda.mChildren.indexOf(desktopOrganizer.mTasks.get(4).getRootTask())
                < tda.mChildren.indexOf(desktopOrganizer.mTasks.get(2).getRootTask()));
    }

    @Test
    public void testAppCompat_setReachabilityOffsets() {
        final Task task = createTask(/* taskId */ 37);
        final WindowContainerToken containerToken = task.getTaskInfo().token;
        spyOn(containerToken);
        final Binder asBinder = new Binder();
        doReturn(asBinder).when(containerToken).asBinder();
        final WindowContainerTransaction wct = new WindowContainerTransaction();
        wct.setReachabilityOffset(containerToken, /* taskId */ task.mTaskId, 10, 20);

        final List<HierarchyOp> hierarchyOps = wct.getHierarchyOps().stream()
                .filter(op -> op.getType() == HIERARCHY_OP_TYPE_APP_COMPAT_REACHABILITY)
                .toList();

        assertEquals(1, hierarchyOps.size());
        final HierarchyOp appCompatOp = hierarchyOps.getFirst();
        assertNotNull(appCompatOp);
        final Bundle appCompatOptions = appCompatOp.getAppCompatOptions();

        assertEquals(task.mTaskId, appCompatOptions.getInt(LAUNCH_KEY_TASK_ID));
        assertEquals(10, appCompatOptions.getInt(REACHABILITY_EVENT_X));
        assertEquals(20, appCompatOptions.getInt(REACHABILITY_EVENT_Y));
        assertSame(asBinder, appCompatOp.getContainer());
    }

    @Test
    public void testSetLaunchNextToBubble() {
        final Task task = createTask(mDisplayContent);

        WindowContainerTransaction wct = new WindowContainerTransaction();
        WindowContainerToken token = task.getTaskInfo().token;
        wct.setLaunchNextToBubble(token, true /* launchNextToBubble */);
        applyTransaction(wct);

        assertTrue(task.mLaunchNextToBubble);

        wct = new WindowContainerTransaction();
        wct.setLaunchNextToBubble(token, false /* launchNextToBubble */);
        applyTransaction(wct);

        assertFalse(task.mLaunchNextToBubble);
    }

    @Test
    public void testSetPreserveLeafTaskIfRelaunch() {
        final Task rootTask = createOrganizerTask(mDisplayContent, WINDOWING_MODE_MULTI_WINDOW,
                ACTIVITY_TYPE_STANDARD);
        rootTask.mTaskOrganizer = mock(ITaskOrganizer.class);
        final WindowContainerToken token = rootTask.mRemoteToken.toWindowContainerToken();

        final WindowContainerTransaction wct = new WindowContainerTransaction();
        wct.setPreserveLeafTaskIfRelaunch(token, true);
        applyTransaction(wct);

        assertTrue(rootTask.mPreserveLeafTaskIfRelaunch);

        wct.setPreserveLeafTaskIfRelaunch(token, false);
        applyTransaction(wct);

        assertFalse(rootTask.mPreserveLeafTaskIfRelaunch);
    }

    @Test
    public void testSetReparentLeafTaskIfRelaunchFromHome() {
        final Task rootTask = createOrganizerTask(mDisplayContent, WINDOWING_MODE_MULTI_WINDOW,
                ACTIVITY_TYPE_STANDARD);
        rootTask.mTaskOrganizer = mock(ITaskOrganizer.class);
        final WindowContainerToken token = rootTask.mRemoteToken.toWindowContainerToken();

        final WindowContainerTransaction wct = new WindowContainerTransaction();
        wct.setReparentLeafTaskIfRelaunchFromHome(token, true);
        applyTransaction(wct);

        assertTrue(rootTask.mReparentLeafTaskIfRelaunchFromHome);

        wct.setReparentLeafTaskIfRelaunchFromHome(token, false);
        applyTransaction(wct);

        assertFalse(rootTask.mReparentLeafTaskIfRelaunchFromHome);
    }

    @Test
    public void testSetDisablePip() {
        final Task task = createTask(mDisplayContent);
        assertFalse(task.isDisablePip());

        WindowContainerTransaction wct = new WindowContainerTransaction();
        WindowContainerToken token = task.getTaskInfo().token;
        wct.setDisablePip(token, true /* disablePip */);
        applyTransaction(wct);

        assertTrue(task.isDisablePip());

        wct = new WindowContainerTransaction();
        wct.setDisablePip(token, false /* disablePip */);
        applyTransaction(wct);

        assertFalse(task.isDisablePip());
    }

    @Test
    public void testSetDisableLaunchAdjacent() {
        final Task task = createTask(mDisplayContent);
        assertFalse(task.isLaunchAdjacentDisabled());

        WindowContainerTransaction wct = new WindowContainerTransaction();
        final WindowContainerToken token = task.getTaskInfo().token;
        wct.setDisableLaunchAdjacent(token, true /* disabled */);
        applyTransaction(wct);

        assertTrue(task.isLaunchAdjacentDisabled());

        wct = new WindowContainerTransaction();
        wct.setDisableLaunchAdjacent(token, false /* disabled */);
        applyTransaction(wct);

        assertFalse(task.isLaunchAdjacentDisabled());
    }

    @Test
    public void testSetSelfMovable() {
        final Task task = createTask(mDisplayContent);

        WindowContainerTransaction wct = new WindowContainerTransaction();
        WindowContainerToken token = task.getTaskInfo().token;
        wct.setSelfMovable(token, SELF_MOVABLE_ALLOWED /* selfMovable */);
        applyTransaction(wct);

        assertEquals(SELF_MOVABLE_ALLOWED, task.getSelfMovable());

        wct = new WindowContainerTransaction();
        wct.setSelfMovable(token, SELF_MOVABLE_DENIED /* selfMovable */);
        applyTransaction(wct);

        assertEquals(SELF_MOVABLE_DENIED, task.getSelfMovable());

        wct = new WindowContainerTransaction();
        wct.setSelfMovable(token, SELF_MOVABLE_DEFAULT /* selfMovable */);
        applyTransaction(wct);

        assertEquals(SELF_MOVABLE_DEFAULT, task.getSelfMovable());
    }

    @Test
    public void testSetIsTaskMoveAllowed() {
        final TaskDisplayArea tda = mDisplayContent.getDefaultTaskDisplayArea();

        WindowContainerTransaction wct = new WindowContainerTransaction();
        WindowContainerToken token = tda.mRemoteToken.toWindowContainerToken();
        wct.setIsTaskMoveAllowed(token, true /* isTaskMoveAllowed */);
        applyTransaction(wct);

        assertTrue(tda.getIsTaskMoveAllowed());

        wct = new WindowContainerTransaction();
        wct.setIsTaskMoveAllowed(token, false /* isTaskMoveAllowed */);
        applyTransaction(wct);

        assertFalse(tda.getIsTaskMoveAllowed());
    }

    @Test
    public void testSetDisallowOverrideBoundsForChildren() {
        final Rect overrideBounds = new Rect(10, 10, 100, 100);
        final Rect emptyBounds = new Rect();
        final Task parentTask = createOrganizerTask(mDisplayContent);
        final Task childTask = new TaskBuilder(mSupervisor)
                .setTaskDisplayArea(parentTask.getTaskDisplayArea())
                .setParentTask(parentTask)
                .build();

        // Verifies the override bounds once set.
        childTask.setBounds(overrideBounds);
        assertEquals(overrideBounds, childTask.getRequestedOverrideBounds());

        // Disallowed the override bounds from the ancestor.
        WindowContainerTransaction wct = new WindowContainerTransaction();
        wct.setDisallowOverrideBoundsForChildren(parentTask.getTaskInfo().token, true);
        applyTransaction(wct);

        if (Flags.idempotentWctResolution()) {
            assertEquals(emptyBounds, childTask.getResolvedOverrideBounds());
            // But the requested override bounds are NOT cleared.
            assertEquals(overrideBounds, childTask.getRequestedOverrideBounds());

            // Verifies the override bounds can be set even if the ancestor disallowed.
            overrideBounds.offset(10, 10);
            childTask.setBounds(overrideBounds);
            assertEquals(overrideBounds, childTask.getRequestedOverrideBounds());
            assertEquals(emptyBounds, childTask.getResolvedOverrideBounds());
        } else {
            assertEquals(emptyBounds, childTask.getRequestedOverrideBounds());

            // Verifies the override bounds cannot be set if the ancestor disallowed.
            childTask.setBounds(overrideBounds);
            assertEquals(emptyBounds, childTask.getRequestedOverrideBounds());
        }
    }

    @Test
    public void testSetDisallowOverrideWindowingModeForChildren() {
        final Task parentTask = new TaskBuilder(mSupervisor)
                .setCreatedByOrganizer(true)
                .build();
        final Task childTask = new TaskBuilder(mSupervisor)
                .setTaskDisplayArea(parentTask.getTaskDisplayArea())
                .setParentTask(parentTask)
                .build();

        // Verifies the override windowing mode once set.
        childTask.setWindowingMode(WINDOWING_MODE_MULTI_WINDOW);
        assertEquals(WINDOWING_MODE_MULTI_WINDOW, childTask.getRequestedOverrideWindowingMode());

        // Disallowed the override windowing mode from the ancestor.
        WindowContainerTransaction wct = new WindowContainerTransaction();
        wct.setDisallowOverrideWindowingModeForChildren(parentTask.getTaskInfo().token, true);
        applyTransaction(wct);

        if (Flags.idempotentWctResolution()) {
            assertEquals(WINDOWING_MODE_FULLSCREEN, childTask.getWindowingMode());
            // But the requested override windowing mode is NOT cleared.
            assertEquals(WINDOWING_MODE_MULTI_WINDOW,
                    childTask.getRequestedOverrideWindowingMode());

            // Verifies the override windowing mode can be set even if the ancestor disallowed.
            childTask.setWindowingMode(WINDOWING_MODE_FREEFORM);
            assertEquals(WINDOWING_MODE_FREEFORM, childTask.getRequestedOverrideWindowingMode());
            assertEquals(WINDOWING_MODE_FULLSCREEN, childTask.getWindowingMode());
        } else {
            assertEquals(WINDOWING_MODE_UNDEFINED, childTask.getRequestedOverrideWindowingMode());

            // Verifies the override windowing mode cannot be set if the ancestor disallowed.
            childTask.setWindowingMode(WINDOWING_MODE_MULTI_WINDOW);
            assertEquals(WINDOWING_MODE_UNDEFINED, childTask.getRequestedOverrideWindowingMode());
        }
    }

    @Test
    public void testReparentTasks_clearWindowingMode() {
        final Task parentTask1 = new TaskBuilder(mSupervisor)
                .setCreatedByOrganizer(true)
                .build();
        final Task parentTask2 = new TaskBuilder(mSupervisor)
                .setCreatedByOrganizer(true)
                .build();
        final Task childTask = new TaskBuilder(mSupervisor)
                .setParentTask(parentTask1)
                .setWindowingMode(WINDOWING_MODE_FULLSCREEN)
                .build();

        // Verifies the override windowing mode is NOT cleared.
        applyTransaction(
                new WindowContainerTransaction().reparentTasks(parentTask1.getTaskInfo().token,
                        parentTask2.getTaskInfo().token, null /* windowingModes */,
                        null /* activityTypes */, true /* onTop */, false /* reparentTopOnly */,
                        false /* clearWindowingMode */));
        assertEquals(parentTask2, childTask.getParent());
        assertEquals(WINDOWING_MODE_FULLSCREEN, childTask.getRequestedOverrideWindowingMode());

        // Verifies the override windowing mode is cleared.
        applyTransaction(
                new WindowContainerTransaction().reparentTasks(parentTask2.getTaskInfo().token,
                        parentTask1.getTaskInfo().token, null /* windowingModes */,
                        null /* activityTypes */, true /* onTop */, false /* reparentTopOnly */,
                        true /* clearWindowingMode */));
        assertEquals(parentTask1, childTask.getParent());
        assertEquals(WINDOWING_MODE_UNDEFINED, childTask.getRequestedOverrideWindowingMode());
    }

    @Test
    public void testReparentTasks_reparentTopOnly() {
        // Setup: parentTask1 has two children, parentTask2 is empty.
        final Task parentTask1 = new TaskBuilder(mSupervisor)
                .setCreatedByOrganizer(true)
                .build();
        final Task parentTask2 = new TaskBuilder(mSupervisor)
                .setCreatedByOrganizer(true)
                .build();
        // Unused child task 1.
        new TaskBuilder(mSupervisor)
                .setParentTask(parentTask1)
                .build();
        final Task childTask2 = new TaskBuilder(mSupervisor)
                .setParentTask(parentTask1)
                .build();
        final Task childTask3 = new TaskBuilder(mSupervisor)
                .setParentTask(parentTask1)
                .build();
        // `childTask3` is on top of `parentTask1`.
        // `parentTask1` has children 3, 2, 1 from top to bottom.
        parentTask1.positionChildAtTop(childTask3);

        assertEquals(3, parentTask1.getChildCount());
        assertEquals(0, parentTask2.getChildCount());
        assertSame(childTask3, parentTask1.getTopChild());

        // Reparent only the top task from parentTask1 to parentTask2.
        WindowContainerTransaction wct = new WindowContainerTransaction();
        wct.reparentTasks(parentTask1.getTaskInfo().token,
                parentTask2.getTaskInfo().token, null /* windowingModes */,
                null /* activityTypes */, true /* onTop */, true /* reparentTopOnly */);
        applyTransaction(wct);

        // Verification: childTask3 moved to parentTask2, childTask1 and childTask2 remain.
        // `parentTask1` has children 2, 1 and `parentTask2` has the child 3 from top to
        // bottom.
        assertEquals(2, parentTask1.getChildCount());
        assertEquals(1, parentTask2.getChildCount());
        assertSame(childTask2, parentTask1.getTopChild());
        assertSame(childTask3, parentTask2.getTopChild());

        // Reparent all remaining tasks from parentTask1 to parentTask2.
        wct = new WindowContainerTransaction();
        wct.reparentTasks(parentTask1.getTaskInfo().token,
                parentTask2.getTaskInfo().token, null /* windowingModes */,
                null /* activityTypes */, true /* onTop */, false /* reparentTopOnly */);
        applyTransaction(wct);

        // Verification: childTask1 and childTask2 also moved to parentTask2.
        assertEquals(0, parentTask1.getChildCount());
        assertEquals(3, parentTask2.getChildCount());
        // Note that since `onTop` is `true`, children 2 and 1 should preserve their order
        // in `parentTask2`. So `parentTask2` has now children 2, 1 and 3, from top to bottom.
        assertSame(childTask2, parentTask2.getTopChild());
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_APP_RESTART_AFTER_UPDATE)
    public void setHandlePackageUpdateForRootContainer_withRootTaskLeafTask_onlyUpdatesRootTask() {
        final Task rootTask = createTask(mDisplayContent, WINDOWING_MODE_MULTI_WINDOW,
                ACTIVITY_TYPE_STANDARD);
        final Task leafTask = new TaskBuilder(mSupervisor)
                .setParentTask(rootTask)
                .build();
        leafTask.setParent(rootTask);

        // The default state is false.
        assertFalse("Leaf task should initially not handle package updates",
                leafTask.mHandlePackageUpdate);
        assertFalse("Root task should initially not handle package updates",
                rootTask.mHandlePackageUpdate);

        // Set handlePackageUpdate to true.
        final WindowContainerTransaction wct = new WindowContainerTransaction();
        final WindowContainerToken token = rootTask.getTaskInfo().token;
        wct.setHandlePackageUpdateForRootContainer(token, true /* handlePackageUpdate */);
        wct.setHandlePackageUpdateForRootContainer(leafTask.getTaskInfo().token,
                true /* handlePackageUpdate */);
        applyTransaction(wct);

        assertTrue("Root task should be updated to handle package updates",
                rootTask.mHandlePackageUpdate);
        assertFalse("Leaf task should not handle package updates", leafTask.mHandlePackageUpdate);
    }

    @Test
    @EnableFlags(Flags.FLAG_DELEGATE_REQUEST_FULLSCREEN_HANDLING_TO_SHELL)
    public void setFullscreenRequestAllowMode_modeIsInherit_ancestorsInherit_resolvesDisallowed() {
        final Task task = createTask(mDisplayContent);

        WindowContainerTransaction wct = new WindowContainerTransaction();
        final WindowContainerToken token = task.getTaskInfo().token;
        wct.setFullscreenRequestAllowMode(token, REQUEST_ALLOW_MODE_INHERIT);
        applyTransaction(wct);

        assertEquals(REQUEST_ALLOW_MODE_NONE, task.getFullscreenRequestAllowMode());
    }

    @Test
    @EnableFlags(Flags.FLAG_DELEGATE_REQUEST_FULLSCREEN_HANDLING_TO_SHELL)
    public void setFullscreenRequestAllowMode_modeIsInherit_ancestorsAllowEntry_resolvesAllowed() {
        final Task rootTask = createTask(mDisplayContent, WINDOWING_MODE_MULTI_WINDOW,
                ACTIVITY_TYPE_STANDARD);
        rootTask.setFullscreenRequestAllowMode(REQUEST_ALLOW_MODE_ENTER);
        final Task leafTask = new TaskBuilder(mSupervisor)
                .setParentTask(rootTask)
                .build();
        leafTask.setParent(rootTask);

        WindowContainerTransaction wct = new WindowContainerTransaction();
        final WindowContainerToken token = leafTask.getTaskInfo().token;
        wct.setFullscreenRequestAllowMode(token, REQUEST_ALLOW_MODE_INHERIT);
        applyTransaction(wct);

        assertEquals(REQUEST_ALLOW_MODE_ENTER, leafTask.getFullscreenRequestAllowMode());
    }

    @Test
    @EnableFlags(Flags.FLAG_DELEGATE_REQUEST_FULLSCREEN_HANDLING_TO_SHELL)
    public void setFullscreenRequestAllowMode_modeIsAllowEnter_resolvesAllowEnter() {
        final Task task = createTask(mDisplayContent);

        WindowContainerTransaction wct = new WindowContainerTransaction();
        final WindowContainerToken token = task.getTaskInfo().token;
        wct.setFullscreenRequestAllowMode(token, REQUEST_ALLOW_MODE_ENTER);
        applyTransaction(wct);

        assertEquals(REQUEST_ALLOW_MODE_ENTER, task.getFullscreenRequestAllowMode());
    }

    @Test
    @EnableFlags(Flags.FLAG_DELEGATE_REQUEST_FULLSCREEN_HANDLING_TO_SHELL)
    public void setFullscreenRequestAllowMode_modeIsAllowExit_resolvesAllowExit() {
        final Task task = createTask(mDisplayContent);

        WindowContainerTransaction wct = new WindowContainerTransaction();
        final WindowContainerToken token = task.getTaskInfo().token;
        wct.setFullscreenRequestAllowMode(token, REQUEST_ALLOW_MODE_EXIT);
        applyTransaction(wct);

        assertEquals(REQUEST_ALLOW_MODE_EXIT, task.getFullscreenRequestAllowMode());
    }

    @Test
    @EnableFlags(Flags.FLAG_DELEGATE_REQUEST_FULLSCREEN_HANDLING_TO_SHELL)
    public void setFullscreenRequestAllowMode_modeIsAllowNone_resolvesAllowNone() {
        final Task task = createTask(mDisplayContent);

        WindowContainerTransaction wct = new WindowContainerTransaction();
        final WindowContainerToken token = task.getTaskInfo().token;
        wct.setFullscreenRequestAllowMode(token, REQUEST_ALLOW_MODE_NONE);
        applyTransaction(wct);

        assertEquals(REQUEST_ALLOW_MODE_NONE, task.getFullscreenRequestAllowMode());
    }

    private Task createTask(int taskId) {
        return new Task.Builder(mAtm)
                .setTaskId(taskId)
                .setIntent(new Intent())
                .setRealActivity(ActivityBuilder.getDefaultComponent())
                .setEffectiveUid(10050)
                .buildInner();
    }

    private void applyTransaction(@NonNull WindowContainerTransaction t) {
        if (!t.isEmpty()) {
            mWm.mAtmService.mWindowOrganizerController.applyTransaction(t);
        }
    }
}
