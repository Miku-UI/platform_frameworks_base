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

import static android.app.WindowConfiguration.ACTIVITY_TYPE_ASSISTANT;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_HOME;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_UNDEFINED;
import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW;
import static android.app.WindowConfiguration.WINDOWING_MODE_PINNED;
import static android.app.WindowConfiguration.WINDOWING_MODE_UNDEFINED;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn;
import static com.android.server.wm.TaskFragment.FLAG_FORCE_HIDDEN_FOR_TASK_ORG;
import static com.android.server.wm.TaskFragment.TASK_FRAGMENT_VISIBILITY_INVISIBLE;
import static com.android.server.wm.TaskFragment.TASK_FRAGMENT_VISIBILITY_VISIBLE;
import static com.android.server.wm.TaskFragment.TASK_FRAGMENT_VISIBILITY_VISIBLE_BEHIND_TRANSLUCENT;
import static com.android.server.wm.WindowContainer.POSITION_TOP;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import android.annotation.NonNull;
import android.app.WindowConfiguration;
import android.graphics.Rect;
import android.os.Binder;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;

import androidx.test.filters.MediumTest;

import com.android.window.flags.Flags;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test class for {@link WindowContainerVisibilityHelperImpl}.
 *
 * Build/Install/Run:
 * atest WmTests:WindowContainerVisibilityHelperTest
 */
@MediumTest
@Presubmit
@RunWith(WindowTestRunner.class)
public class WindowContainerVisibilityHelperTest extends WindowTestsBase {

    private WindowContainerVisibilityHelper mHelper;
    private TaskDisplayArea mDefaultTaskDisplayArea;

    @Before
    public void setUp() throws Exception {
        mHelper = mAtm.mVisibilityHelper;
        mDefaultTaskDisplayArea = mRootWindowContainer.getDefaultTaskDisplayArea();
    }

    @Test
    public void testShouldBeVisible_Fullscreen() {
        final Task homeRootTask = getHomeRootTaskAndMoveToTop(mDefaultTaskDisplayArea);
        final Task pinnedRootTask = createTaskWithActivity(mDefaultTaskDisplayArea,
                WINDOWING_MODE_PINNED, ACTIVITY_TYPE_STANDARD, true /* onTop */);
        // Add an activity to the pinned root task so it isn't considered empty for visibility
        // check.
        new ActivityBuilder(mAtm)
                .setTask(pinnedRootTask)
                .build();

        assertTrue(homeRootTask.shouldBeVisible(null /* starting */));
        assertTrue(pinnedRootTask.shouldBeVisible(null /* starting */));

        final Task fullscreenRootTask = createTaskWithActivity(mDefaultTaskDisplayArea,
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD, true /* onTop */);
        // Home root task shouldn't be visible behind an opaque fullscreen root task, but pinned
        // root task should be visible since it is always on-top.
        doReturn(false).when(fullscreenRootTask).isTranslucent(any());
        assertFalse(homeRootTask.shouldBeVisible(null /* starting */));
        assertTrue(pinnedRootTask.shouldBeVisible(null /* starting */));
        assertTrue(fullscreenRootTask.shouldBeVisible(null /* starting */));

        // Home root task should be visible behind a translucent fullscreen root task.
        doReturn(true).when(fullscreenRootTask).isTranslucent(any());
        assertTrue(homeRootTask.shouldBeVisible(null /* starting */));
        assertTrue(pinnedRootTask.shouldBeVisible(null /* starting */));
    }

    @Test
    public void testShouldBeVisible_SplitScreen() {
        // Fullscreen root task for this test.
        final Task fullScreenRootTask = createTaskWithActivity(
                mDefaultTaskDisplayArea, WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD,
                true /* onTop */);

        final TestSplitOrganizer organizer = new TestSplitOrganizer(mAtm);
        final Task splitScreenPrimary = createTaskWithActivity(mDefaultTaskDisplayArea,
                WINDOWING_MODE_UNDEFINED, ACTIVITY_TYPE_STANDARD, true /* onTop */);
        final Task splitScreenSecondary = createTaskWithActivity(mDefaultTaskDisplayArea,
                WINDOWING_MODE_UNDEFINED, ACTIVITY_TYPE_STANDARD, true /* onTop */);
        organizer.putTaskToPrimary(splitScreenPrimary, true /* onTop */);
        organizer.putTaskToSecondary(splitScreenSecondary, true /* onTop */);
        splitScreenPrimary.moveToFront("testShouldBeVisible_SplitScreen");
        splitScreenSecondary.moveToFront("testShouldBeVisible_SplitScreen");

        // Fullscreen root task shouldn't be visible if both halves of split-screen are opaque.
        doReturn(false).when(organizer.mPrimary).isTranslucent(any());
        doReturn(false).when(organizer.mSecondary).isTranslucent(any());
        doReturn(false).when(splitScreenPrimary).isTranslucent(any());
        doReturn(false).when(splitScreenSecondary).isTranslucent(any());
        assertFalse(fullScreenRootTask.shouldBeVisible(null /* starting */));
        assertTrue(organizer.mPrimary.shouldBeVisible(null /* starting */));
        assertTrue(organizer.mSecondary.shouldBeVisible(null /* starting */));
        assertTrue(splitScreenPrimary.shouldBeVisible(null /* starting */));
        assertTrue(splitScreenSecondary.shouldBeVisible(null /* starting */));

        // Fullscreen root task shouldn't be visible if one of the halves of split-screen
        // is translucent.
        doReturn(true).when(splitScreenPrimary).isTranslucent(any());
        assertFalse(fullScreenRootTask.shouldBeVisible(null /* starting */));
        assertTrue(organizer.mPrimary.shouldBeVisible(null /* starting */));
        assertTrue(organizer.mSecondary.shouldBeVisible(null /* starting */));
        assertTrue(splitScreenPrimary.shouldBeVisible(null /* starting */));
        assertTrue(splitScreenSecondary.shouldBeVisible(null /* starting */));

        final Task splitScreenSecondary2 = createTaskWithActivity(mDefaultTaskDisplayArea,
                WINDOWING_MODE_UNDEFINED, ACTIVITY_TYPE_STANDARD, true /* onTop */);
        organizer.putTaskToSecondary(splitScreenSecondary2, true /* onTop */);
        // First split-screen secondary shouldn't be visible behind another opaque split-split
        // secondary.
        doReturn(false).when(splitScreenSecondary2).isTranslucent(any());
        assertTrue(organizer.mSecondary.shouldBeVisible(null /* starting */));
        assertFalse(splitScreenSecondary.shouldBeVisible(null /* starting */));
        assertTrue(splitScreenSecondary2.shouldBeVisible(null /* starting */));
        assertEquals(TASK_FRAGMENT_VISIBILITY_VISIBLE,
                organizer.mSecondary.getVisibility(null /* starting */));
        assertEquals(TASK_FRAGMENT_VISIBILITY_INVISIBLE,
                splitScreenSecondary.getVisibility(null /* starting */));
        assertEquals(TASK_FRAGMENT_VISIBILITY_VISIBLE,
                splitScreenSecondary2.getVisibility(null /* starting */));

        // First split-screen secondary should be visible behind another translucent split-screen
        // secondary.
        doReturn(true).when(splitScreenSecondary2).isTranslucent(any());
        assertTrue(organizer.mSecondary.shouldBeVisible(null /* starting */));
        assertTrue(splitScreenSecondary.shouldBeVisible(null /* starting */));
        assertTrue(splitScreenSecondary2.shouldBeVisible(null /* starting */));
        assertEquals(TASK_FRAGMENT_VISIBILITY_VISIBLE,
                organizer.mSecondary.getVisibility(null /* starting */));
        assertEquals(TASK_FRAGMENT_VISIBILITY_VISIBLE_BEHIND_TRANSLUCENT,
                splitScreenSecondary.getVisibility(null /* starting */));
        assertEquals(TASK_FRAGMENT_VISIBILITY_VISIBLE,
                splitScreenSecondary2.getVisibility(null /* starting */));

        final Task assistantRootTask = createTaskWithActivity(mDefaultTaskDisplayArea,
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_ASSISTANT, true /* onTop */);

        // Split-screen root tasks shouldn't be visible behind an opaque fullscreen root task.
        doReturn(false).when(assistantRootTask).isTranslucent(any());
        assertTrue(assistantRootTask.shouldBeVisible(null /* starting */));
        assertFalse(organizer.mPrimary.shouldBeVisible(null /* starting */));
        assertFalse(organizer.mSecondary.shouldBeVisible(null /* starting */));
        assertFalse(splitScreenPrimary.shouldBeVisible(null /* starting */));
        assertFalse(splitScreenSecondary.shouldBeVisible(null /* starting */));
        assertFalse(splitScreenSecondary2.shouldBeVisible(null /* starting */));
        assertEquals(TASK_FRAGMENT_VISIBILITY_VISIBLE,
                assistantRootTask.getVisibility(null /* starting */));
        assertEquals(TASK_FRAGMENT_VISIBILITY_INVISIBLE,
                splitScreenPrimary.getVisibility(null /* starting */));
        assertEquals(TASK_FRAGMENT_VISIBILITY_INVISIBLE,
                splitScreenSecondary.getVisibility(null /* starting */));
        assertEquals(TASK_FRAGMENT_VISIBILITY_INVISIBLE,
                splitScreenSecondary2.getVisibility(null /* starting */));

        // Split-screen root tasks should be visible behind a translucent fullscreen root task.
        doReturn(true).when(assistantRootTask).isTranslucent(any());
        assertTrue(assistantRootTask.shouldBeVisible(null /* starting */));
        assertTrue(organizer.mPrimary.shouldBeVisible(null /* starting */));
        assertTrue(organizer.mSecondary.shouldBeVisible(null /* starting */));
        assertTrue(splitScreenPrimary.shouldBeVisible(null /* starting */));
        assertTrue(splitScreenSecondary.shouldBeVisible(null /* starting */));
        assertTrue(splitScreenSecondary2.shouldBeVisible(null /* starting */));
        assertEquals(TASK_FRAGMENT_VISIBILITY_VISIBLE,
                assistantRootTask.getVisibility(null /* starting */));
        assertEquals(TASK_FRAGMENT_VISIBILITY_VISIBLE_BEHIND_TRANSLUCENT,
                organizer.mPrimary.getVisibility(null /* starting */));
        assertEquals(TASK_FRAGMENT_VISIBILITY_VISIBLE_BEHIND_TRANSLUCENT,
                organizer.mSecondary.getVisibility(null /* starting */));
        assertEquals(TASK_FRAGMENT_VISIBILITY_VISIBLE_BEHIND_TRANSLUCENT,
                splitScreenPrimary.getVisibility(null /* starting */));
        assertEquals(TASK_FRAGMENT_VISIBILITY_VISIBLE_BEHIND_TRANSLUCENT,
                splitScreenSecondary.getVisibility(null /* starting */));
        assertEquals(TASK_FRAGMENT_VISIBILITY_VISIBLE_BEHIND_TRANSLUCENT,
                splitScreenSecondary2.getVisibility(null /* starting */));
    }

    @Test
    public void testGetVisibility_MultiLevel() {
        TestSplitOrganizer organizer = new TestSplitOrganizer(mAtm);
        final Task splitPrimary = createTaskWithActivity(mDefaultTaskDisplayArea,
                WINDOWING_MODE_UNDEFINED, ACTIVITY_TYPE_UNDEFINED, true /* onTop */);
        final Task splitSecondary = createTaskWithActivity(mDefaultTaskDisplayArea,
                WINDOWING_MODE_UNDEFINED, ACTIVITY_TYPE_UNDEFINED, true /* onTop */);

        doReturn(false).when(splitPrimary).isTranslucent(any());
        doReturn(false).when(splitSecondary).isTranslucent(any());

        // Re-parent tasks to split.
        organizer.putTaskToPrimary(splitPrimary, true /* onTop */);
        organizer.putTaskToSecondary(splitSecondary, true /* onTop */);
        // Reparented tasks should be visible.
        assertEquals(TASK_FRAGMENT_VISIBILITY_VISIBLE,
                splitPrimary.getVisibility(null /* starting */));
        assertEquals(TASK_FRAGMENT_VISIBILITY_VISIBLE,
                splitSecondary.getVisibility(null /* starting */));

        // Add fullscreen translucent task that partially occludes split tasks
        final Task translucentRootTask = createTaskWithActivityAndOverrideTranslucent(
                WINDOWING_MODE_FULLSCREEN, true /* translucent */);
        // Fullscreen translucent task should be visible
        assertEquals(TASK_FRAGMENT_VISIBILITY_VISIBLE,
                translucentRootTask.getVisibility(null /* starting */));
        // Split tasks should be visible behind translucent
        assertEquals(TASK_FRAGMENT_VISIBILITY_VISIBLE_BEHIND_TRANSLUCENT,
                splitPrimary.getVisibility(null /* starting */));
        assertEquals(TASK_FRAGMENT_VISIBILITY_VISIBLE_BEHIND_TRANSLUCENT,
                splitSecondary.getVisibility(null /* starting */));

        // Hide split-secondary
        organizer.mSecondary.setForceHidden(FLAG_FORCE_HIDDEN_FOR_TASK_ORG, true /* set */);
        // Home split secondary and home task should be invisible.
        assertEquals(TASK_FRAGMENT_VISIBILITY_INVISIBLE,
                splitSecondary.getVisibility(null /* starting */));

        // Put another task on top of primary split.
        final Task topSplitPrimary = new TaskBuilder(mSupervisor).setParentTask(organizer.mPrimary)
                .setCreateActivity(true).build();
        doReturn(false).when(topSplitPrimary).isTranslucent(any());
        // Convert the fullscreen translucent task to opaque.
        doReturn(false).when(translucentRootTask).isTranslucent(any());
        translucentRootTask.moveToFront("test");
        // The tasks of primary split are occluded by the fullscreen opaque task.
        assertEquals(TASK_FRAGMENT_VISIBILITY_INVISIBLE,
                organizer.mPrimary.getVisibility(null /* starting */));
        assertEquals(TASK_FRAGMENT_VISIBILITY_INVISIBLE,
                topSplitPrimary.getVisibility(null /* starting */));
        // Make primary split root transient-hide.
        spyOn(splitPrimary.mTransitionController);
        doReturn(true).when(splitPrimary.mTransitionController).isTransientVisible(
                organizer.mPrimary);
        // The split root and its top become visible.
        assertEquals(TASK_FRAGMENT_VISIBILITY_VISIBLE,
                organizer.mPrimary.getVisibility(null /* starting */));
        assertEquals(TASK_FRAGMENT_VISIBILITY_VISIBLE,
                topSplitPrimary.getVisibility(null /* starting */));
        // The bottom of primary split becomes invisible because it is occluded by topSplitPrimary.
        assertEquals(TASK_FRAGMENT_VISIBILITY_INVISIBLE,
                splitPrimary.getVisibility(null /* starting */));
    }

    @Test
    public void testGetVisibility_FullscreenBehindTranslucent() {
        final Task bottomRootTask =
                createTaskWithActivityAndOverrideTranslucent(WINDOWING_MODE_FULLSCREEN,
                        false /* translucent */);
        final Task translucentRootTask =
                createTaskWithActivityAndOverrideTranslucent(WINDOWING_MODE_FULLSCREEN,
                        true /* translucent */);

        assertEquals(TASK_FRAGMENT_VISIBILITY_VISIBLE_BEHIND_TRANSLUCENT,
                bottomRootTask.getVisibility(null /* starting */));
        assertEquals(TASK_FRAGMENT_VISIBILITY_VISIBLE,
                translucentRootTask.getVisibility(null /* starting */));
    }

    @Test
    public void testGetVisibility_FullscreenBehindTranslucentAndOpaque() {
        final Task bottomRootTask =
                createTaskWithActivityAndOverrideTranslucent(WINDOWING_MODE_FULLSCREEN,
                        false /* translucent */);
        final Task translucentRootTask =
                createTaskWithActivityAndOverrideTranslucent(WINDOWING_MODE_FULLSCREEN,
                        true /* translucent */);
        final Task opaqueRootTask =
                createTaskWithActivityAndOverrideTranslucent(WINDOWING_MODE_FULLSCREEN,
                        false /* translucent */);

        assertEquals(TASK_FRAGMENT_VISIBILITY_INVISIBLE,
                bottomRootTask.getVisibility(null /* starting */));
        assertEquals(TASK_FRAGMENT_VISIBILITY_INVISIBLE,
                translucentRootTask.getVisibility(null /* starting */));
        assertEquals(TASK_FRAGMENT_VISIBILITY_VISIBLE,
                opaqueRootTask.getVisibility(null /* starting */));
    }

    @Test
    public void testGetVisibility_FullscreenBehindOpaqueAndTranslucent() {
        final Task bottomRootTask =
                createTaskWithActivityAndOverrideTranslucent(WINDOWING_MODE_FULLSCREEN,
                        false /* translucent */);
        final Task opaqueRootTask =
                createTaskWithActivityAndOverrideTranslucent(WINDOWING_MODE_FULLSCREEN,
                        false /* translucent */);
        final Task translucentRootTask =
                createTaskWithActivityAndOverrideTranslucent(WINDOWING_MODE_FULLSCREEN,
                        true /* translucent */);

        assertEquals(TASK_FRAGMENT_VISIBILITY_INVISIBLE,
                bottomRootTask.getVisibility(null /* starting */));
        assertEquals(TASK_FRAGMENT_VISIBILITY_VISIBLE_BEHIND_TRANSLUCENT,
                opaqueRootTask.getVisibility(null /* starting */));
        assertEquals(TASK_FRAGMENT_VISIBILITY_VISIBLE,
                translucentRootTask.getVisibility(null /* starting */));
    }

    @Test
    public void testGetVisibility_FullscreenTranslucentBehindTranslucent() {
        final Task bottomTranslucentRootTask =
                createTaskWithActivityAndOverrideTranslucent(WINDOWING_MODE_FULLSCREEN,
                        true /* translucent */);
        final Task translucentRootTask =
                createTaskWithActivityAndOverrideTranslucent(WINDOWING_MODE_FULLSCREEN,
                        true /* translucent */);

        assertEquals(TASK_FRAGMENT_VISIBILITY_VISIBLE_BEHIND_TRANSLUCENT,
                bottomTranslucentRootTask.getVisibility(null /* starting */));
        assertEquals(TASK_FRAGMENT_VISIBILITY_VISIBLE,
                translucentRootTask.getVisibility(null /* starting */));
    }

    @Test
    public void testGetVisibility_FullscreenTranslucentBehindOpaque() {
        final Task bottomTranslucentRootTask =
                createTaskWithActivityAndOverrideTranslucent(WINDOWING_MODE_FULLSCREEN,
                        true /* translucent */);
        final Task opaqueRootTask =
                createTaskWithActivityAndOverrideTranslucent(WINDOWING_MODE_FULLSCREEN,
                        false /* translucent */);

        assertEquals(TASK_FRAGMENT_VISIBILITY_INVISIBLE,
                bottomTranslucentRootTask.getVisibility(null /* starting */));
        assertEquals(TASK_FRAGMENT_VISIBILITY_VISIBLE,
                opaqueRootTask.getVisibility(null /* starting */));
    }

    @Test
    public void testGetVisibility_FullscreenBehindTranslucentAndPip() {
        final Task bottomRootTask =
                createTaskWithActivityAndOverrideTranslucent(WINDOWING_MODE_FULLSCREEN,
                        false /* translucent */);
        final Task translucentRootTask =
                createTaskWithActivityAndOverrideTranslucent(WINDOWING_MODE_FULLSCREEN,
                        true /* translucent */);
        final Task pinnedRootTask = createTaskWithActivity(mDefaultTaskDisplayArea,
                WINDOWING_MODE_PINNED, ACTIVITY_TYPE_STANDARD, true /* onTop */);

        assertEquals(TASK_FRAGMENT_VISIBILITY_VISIBLE_BEHIND_TRANSLUCENT,
                bottomRootTask.getVisibility(null /* starting */));
        assertEquals(TASK_FRAGMENT_VISIBILITY_VISIBLE,
                translucentRootTask.getVisibility(null /* starting */));
        // Add an activity to the pinned root task so it isn't considered empty for visibility
        // check.
        new ActivityBuilder(mAtm)
                .setTask(pinnedRootTask)
                .build();
        assertEquals(TASK_FRAGMENT_VISIBILITY_VISIBLE,
                pinnedRootTask.getVisibility(null /* starting */));
    }

    @Test
    public void testShouldBeVisible_Finishing() {
        final Task homeRootTask = getHomeRootTaskAndMoveToTop(mDefaultTaskDisplayArea);
        ActivityRecord topRunningHomeActivity = homeRootTask.topRunningActivity();
        if (topRunningHomeActivity == null) {
            topRunningHomeActivity = new ActivityBuilder(mAtm)
                    .setTask(homeRootTask)
                    .build();
        }

        final Task translucentRootTask = createTaskWithActivity(mDefaultTaskDisplayArea,
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD, true /* onTop */);
        doReturn(true).when(translucentRootTask).isTranslucent(any());

        assertTrue(homeRootTask.shouldBeVisible(null /* starting */));
        assertTrue(translucentRootTask.shouldBeVisible(null /* starting */));

        topRunningHomeActivity.finishing = true;
        final ActivityRecord topRunningTranslucentActivity =
                translucentRootTask.topRunningActivity();
        topRunningTranslucentActivity.finishing = true;

        // Home root task should be visible even there are no running activities.
        assertTrue(homeRootTask.shouldBeVisible(null /* starting */));
        // Home should be visible if we are starting an activity within it.
        assertTrue(homeRootTask.shouldBeVisible(topRunningHomeActivity /* starting */));
        // The translucent root task shouldn't be visible since its activity marked as finishing.
        assertFalse(translucentRootTask.shouldBeVisible(null /* starting */));
    }

    @Test
    public void testShouldBeVisible_FullscreenBehindTranslucentInHomeRootTask() {
        final Task homeRootTask = getHomeRootTaskAndMoveToTop(mDefaultTaskDisplayArea);

        final ActivityRecord firstActivity = new ActivityBuilder(mAtm)
                .setParentTask(homeRootTask)
                .setCreateTask(true)
                .build();
        final Task task = firstActivity.getTask();
        final ActivityRecord secondActivity = new ActivityBuilder(mAtm)
                .setTask(task)
                .build();

        doReturn(false).when(secondActivity).occludesParent(false /* includingFinishing */);
        homeRootTask.ensureActivitiesVisible(null /* starting */);

        assertTrue(firstActivity.shouldBeVisible());
    }

    @Test
    public void testShouldBeVisible_behindOccludedActivityInEmbeddedTaskFragment() {
        final Task task = createTaskWithActivity(mDefaultTaskDisplayArea,
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD, true /* onTop */);
        final TaskFragment embeddedTf = new TaskFragmentBuilder(mAtm)
                .setParentTask(task)
                .createActivityCount(2)
                .build();
        embeddedTf.setWindowingMode(WINDOWING_MODE_MULTI_WINDOW);
        embeddedTf.setBounds(new Rect(500, 0, 1000, 1000));

        assertTrue(embeddedTf.getTopMostActivity().shouldBeVisible());
        assertFalse(embeddedTf.getBottomMostActivity().shouldBeVisible());
    }

    @Test
    public void testVisibilityBehindOpaqueTaskFragment_withTranslucentTaskFragmentInTask() {
        final Task topTask = createTask(mDisplayContent);
        final Rect top = new Rect();
        final Rect bottom = new Rect();
        topTask.getBounds().splitVertically(top, bottom);

        final TaskFragment taskFragmentA = createTaskFragmentWithActivity(topTask);
        final TaskFragment taskFragmentB = createTaskFragmentWithActivity(topTask);
        final TaskFragment taskFragmentC = createTaskFragmentWithActivity(topTask);

        // B and C split the task window. A is behind B. C is translucent.
        taskFragmentA.setBounds(top);
        taskFragmentB.setBounds(top);
        taskFragmentC.setBounds(bottom);
        taskFragmentA.setWindowingMode(WINDOWING_MODE_MULTI_WINDOW);
        taskFragmentB.setWindowingMode(WINDOWING_MODE_MULTI_WINDOW);
        taskFragmentC.setWindowingMode(WINDOWING_MODE_MULTI_WINDOW);
        taskFragmentB.setAdjacentTaskFragments(
                new TaskFragment.AdjacentSet(taskFragmentB, taskFragmentC));
        doReturn(true).when(taskFragmentC).isTranslucent(any());

        // Ensure the activity below is visible
        topTask.ensureActivitiesVisible(null /* starting */);

        // B and C should be visible. A should be invisible.
        assertEquals(TASK_FRAGMENT_VISIBILITY_INVISIBLE,
                taskFragmentA.getVisibility(null /* starting */));
        assertEquals(TASK_FRAGMENT_VISIBILITY_VISIBLE,
                taskFragmentB.getVisibility(null /* starting */));
        assertEquals(TASK_FRAGMENT_VISIBILITY_VISIBLE,
                taskFragmentC.getVisibility(null /* starting */));
    }

    @Test
    public void testVisibilityBehindTranslucentTaskFragment() {
        final Task topTask = createTask(mDisplayContent);
        final Rect top = new Rect();
        final Rect bottom = new Rect();
        topTask.getBounds().splitVertically(top, bottom);

        final TaskFragment taskFragmentA = createTaskFragmentWithActivity(topTask);
        final TaskFragment taskFragmentB = createTaskFragmentWithActivity(topTask);
        final TaskFragment taskFragmentC = createTaskFragmentWithActivity(topTask);

        // B and C split the task window. A is behind B. B is translucent.
        taskFragmentA.setBounds(top);
        taskFragmentB.setBounds(top);
        taskFragmentC.setBounds(bottom);
        taskFragmentA.setWindowingMode(WINDOWING_MODE_MULTI_WINDOW);
        taskFragmentB.setWindowingMode(WINDOWING_MODE_MULTI_WINDOW);
        taskFragmentC.setWindowingMode(WINDOWING_MODE_MULTI_WINDOW);
        taskFragmentB.setAdjacentTaskFragments(
                new TaskFragment.AdjacentSet(taskFragmentB, taskFragmentC));
        doReturn(true).when(taskFragmentB).isTranslucent(any());

        // Ensure the activity below is visible
        topTask.ensureActivitiesVisible(null /* starting */);

        // A, B and C should be visible.
        assertEquals(TASK_FRAGMENT_VISIBILITY_VISIBLE,
                taskFragmentC.getVisibility(null /* starting */));
        assertEquals(TASK_FRAGMENT_VISIBILITY_VISIBLE,
                taskFragmentB.getVisibility(null /* starting */));
        assertEquals(TASK_FRAGMENT_VISIBILITY_VISIBLE_BEHIND_TRANSLUCENT,
                taskFragmentA.getVisibility(null /* starting */));
    }

    @Test
    public void testVisibility_behindEmptyTaskThatFillsParentBounds_visible() {
        // A fullscreen task with an opaque activity.
        final Task bottomTask = createTask(mDisplayContent.getDefaultTaskDisplayArea(),
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD);
        createActivityRecord(bottomTask);
        // Above it, an empty fullscreen task.
        createTask(mDisplayContent.getDefaultTaskDisplayArea(),
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD);

        assertEquals(TASK_FRAGMENT_VISIBILITY_VISIBLE,
                bottomTask.getVisibility(null /* starting */));
    }

    @Test
    public void testVisibility_behindOpaqueTaskFillingParentBounds_invisible() {
        // A fullscreen task with an opaque activity.
        final Task bottomTask = createTask(mDisplayContent.getDefaultTaskDisplayArea(),
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD);
        createActivityRecord(bottomTask);
        // Above it, an opaque fullscreen task.
        final Task topTask = createTask(mDisplayContent.getDefaultTaskDisplayArea(),
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD);
        final ActivityRecord topActivity = createActivityRecord(topTask);
        topActivity.setOccludesParent(true);

        assertEquals(TASK_FRAGMENT_VISIBILITY_INVISIBLE,
                bottomTask.getVisibility(topActivity /* starting */));
    }

    @Test
    public void testVisibility_behindTranslucentTaskFillingParentBounds_visibleBehindTranslucent() {
        // A fullscreen task with an opaque activity.
        final Task bottomTask = createTask(mDisplayContent.getDefaultTaskDisplayArea(),
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD);
        createActivityRecord(bottomTask);
        // Above it, a translucent fullscreen task.
        final Task topTask = createTask(mDisplayContent.getDefaultTaskDisplayArea(),
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD);
        final ActivityRecord topActivity = createActivityRecord(topTask);
        topActivity.setOccludesParent(false);

        assertEquals(TASK_FRAGMENT_VISIBILITY_VISIBLE_BEHIND_TRANSLUCENT,
                bottomTask.getVisibility(topActivity /* starting */));
    }

    @Test
    public void testVisibility_behindOpaqueNestedFreeformTasksNotFillingParenBounds_visible() {
        // A fullscreen task with an opaque activity.
        final Task bottomTask = createTask(mDisplayContent.getDefaultTaskDisplayArea(),
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD);
        createActivityRecord(bottomTask);
        // Above it, a freeform root task with a freeform child task with an opaque activity.
        final Task topTask = createTask(mDisplayContent.getDefaultTaskDisplayArea(),
                WINDOWING_MODE_FREEFORM, ACTIVITY_TYPE_STANDARD);
        final Task topTaskChild = createTaskInRootTask(topTask, 0 /* userId */);
        topTaskChild.setWindowingMode(WINDOWING_MODE_FREEFORM);
        topTaskChild.setBounds(1, 1, 2, 2); // It does not fill its parent.
        final ActivityRecord topActivity = createActivityRecord(topTaskChild);
        topActivity.setOccludesParent(true);

        // The freeform root should not affect the bottom's visibility because it does not fill
        // its parent.
        assertEquals(TASK_FRAGMENT_VISIBILITY_VISIBLE,
                bottomTask.getVisibility(topActivity /* starting */));
    }

    @Test
    public void testVisibility_behindOpaqueNestedFreeformTasksThatFillParenBounds_invisible() {
        // A fullscreen task with an opaque activity.
        final Task bottomTask = createTask(mDisplayContent.getDefaultTaskDisplayArea(),
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD);
        createActivityRecord(bottomTask);
        // Above it, a freeform root task with a freeform child task with an opaque activity.
        final Task topTask = createTask(mDisplayContent.getDefaultTaskDisplayArea(),
                WINDOWING_MODE_FREEFORM, ACTIVITY_TYPE_STANDARD);
        final Task topTaskChild = createTaskInRootTask(topTask, 0 /* userId */);
        topTaskChild.setWindowingMode(WINDOWING_MODE_FREEFORM);
        topTaskChild.setBounds(null); // Fills parent.
        final ActivityRecord topActivity = createActivityRecord(topTaskChild);
        topActivity.setOccludesParent(true);

        // The freeform root should not affect the bottom's visibility because it does not fill
        // its parent.
        assertEquals(TASK_FRAGMENT_VISIBILITY_INVISIBLE,
                bottomTask.getVisibility(topActivity /* starting */));
    }

    @Test
    public void testVisibility_behindTranslucentNestedFreeformFillingBounds_visBehindTranslucent() {
        // A fullscreen task with an opaque activity.
        final Task bottomTask = createTask(mDisplayContent.getDefaultTaskDisplayArea(),
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD);
        createActivityRecord(bottomTask);
        // Above it, a freeform root task with a freeform child task with a translucent
        // activity.
        final Task topTask = createTask(mDisplayContent.getDefaultTaskDisplayArea(),
                WINDOWING_MODE_FREEFORM, ACTIVITY_TYPE_STANDARD);
        final Task topTaskChild = createTaskInRootTask(topTask, 0 /* userId */);
        topTaskChild.setWindowingMode(WINDOWING_MODE_FREEFORM);
        topTaskChild.setBounds(null);
        final ActivityRecord topActivity = createActivityRecord(topTaskChild);
        topActivity.setOccludesParent(false);

        // The freeform root should not affect the bottom's visibility because it does not fill
        // its parent.
        assertEquals(TASK_FRAGMENT_VISIBILITY_VISIBLE_BEHIND_TRANSLUCENT,
                bottomTask.getVisibility(topActivity /* starting */));
    }

    @Test
    public void testVisibility_behindAtLeastOneNonFillingAdjacentTaskFragments_invisible() {
        // A fullscreen task with an opaque activity.
        final Task bottomTask = createTask(mDisplayContent.getDefaultTaskDisplayArea(),
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD);
        createActivityRecord(bottomTask);
        // Above it, two adjacent task fragments but one is non-filling.
        final Task topTask = createTask(mDisplayContent.getDefaultTaskDisplayArea(),
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD);
        final Rect top = new Rect();
        final Rect bottom = new Rect();
        topTask.getBounds().splitVertically(top, bottom);
        final TaskFragment topAdjacentTaskFragment1 = createTaskFragmentWithActivity(topTask);
        topAdjacentTaskFragment1.setWindowingMode(WINDOWING_MODE_MULTI_WINDOW);
        topAdjacentTaskFragment1.setBounds(top);
        topAdjacentTaskFragment1.getTopMostActivity().setVisible(true);
        topAdjacentTaskFragment1.getTopMostActivity().visibleIgnoringKeyguard = true;
        final TaskFragment topAdjacentTaskFragment2 = createTaskFragmentWithActivity(topTask);
        topAdjacentTaskFragment2.setWindowingMode(WINDOWING_MODE_MULTI_WINDOW);
        topAdjacentTaskFragment2.setBounds(bottom);
        topAdjacentTaskFragment2.getTopMostActivity().setVisible(true);
        topAdjacentTaskFragment2.getTopMostActivity().visibleIgnoringKeyguard = true;
        topAdjacentTaskFragment2.setAdjacentTaskFragments(
                new TaskFragment.AdjacentSet(topAdjacentTaskFragment2, topAdjacentTaskFragment1));

        // Make one non-filling.
        topAdjacentTaskFragment1.getTopMostActivity().setOccludesParent(false);

        if (Flags.partialTranslucentActivityEmbedding()) {
            assertEquals(TASK_FRAGMENT_VISIBILITY_INVISIBLE,
                    bottomTask.getVisibility(null /* starting */));
        } else {
            assertEquals(TASK_FRAGMENT_VISIBILITY_VISIBLE_BEHIND_TRANSLUCENT,
                    bottomTask.getVisibility(null /* starting */));
        }
    }

    @Test
    public void testVisibility_behindFillingAdjacentTaskFragments_invisible() {
        // A fullscreen task with an opaque activity.
        final Task bottomTask = createTask(mDisplayContent.getDefaultTaskDisplayArea(),
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD);
        createActivityRecord(bottomTask);
        // Above it, two adjacent task fragments that are filling.
        final Task topTask = createTask(mDisplayContent.getDefaultTaskDisplayArea(),
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD);
        final Rect top = new Rect();
        final Rect bottom = new Rect();
        topTask.getBounds().splitVertically(top, bottom);
        final TaskFragment topAdjacentTaskFragment1 = createTaskFragmentWithActivity(topTask);
        topAdjacentTaskFragment1.setWindowingMode(WINDOWING_MODE_MULTI_WINDOW);
        topAdjacentTaskFragment1.setBounds(top);
        topAdjacentTaskFragment1.getTopMostActivity().setVisible(true);
        topAdjacentTaskFragment1.getTopMostActivity().visibleIgnoringKeyguard = true;
        final TaskFragment topAdjacentTaskFragment2 = createTaskFragmentWithActivity(topTask);
        topAdjacentTaskFragment2.setWindowingMode(WINDOWING_MODE_MULTI_WINDOW);
        topAdjacentTaskFragment2.setBounds(bottom);
        topAdjacentTaskFragment2.getTopMostActivity().setVisible(true);
        topAdjacentTaskFragment2.getTopMostActivity().visibleIgnoringKeyguard = true;
        topAdjacentTaskFragment2.setAdjacentTaskFragments(
                new TaskFragment.AdjacentSet(topAdjacentTaskFragment2, topAdjacentTaskFragment1));

        assertEquals(TASK_FRAGMENT_VISIBILITY_INVISIBLE,
                bottomTask.getVisibility(null /* starting */));
    }

    @Test
    public void testVisibility_sandwichInAdjacentTaskFragments() {
        // A fullscreen task with an opaque activity.
        final Task bottomTask = createTask(mDisplayContent.getDefaultTaskDisplayArea(),
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD);
        createActivityRecord(bottomTask);
        // Above it, two adjacent task fragments that sandwiched another task fragment.
        final Task topTask = createTask(mDisplayContent.getDefaultTaskDisplayArea(),
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD);
        final Rect top = new Rect();
        final Rect bottom = new Rect();
        topTask.getBounds().splitVertically(top, bottom);
        final TaskFragment topAdjacentTaskFragment1 = createTaskFragmentWithActivity(topTask);
        topAdjacentTaskFragment1.setWindowingMode(WINDOWING_MODE_MULTI_WINDOW);
        topAdjacentTaskFragment1.setBounds(top);
        topAdjacentTaskFragment1.getTopMostActivity().setVisible(true);
        topAdjacentTaskFragment1.getTopMostActivity().visibleIgnoringKeyguard = true;
        final TaskFragment sandwichTaskFragment = createTaskFragmentWithActivity(topTask);
        sandwichTaskFragment.setWindowingMode(WINDOWING_MODE_MULTI_WINDOW);
        sandwichTaskFragment.setBounds(bottom);
        sandwichTaskFragment.getTopMostActivity().setVisible(false);
        sandwichTaskFragment.getTopMostActivity().visibleIgnoringKeyguard = false;
        final TaskFragment topAdjacentTaskFragment2 = createTaskFragmentWithActivity(topTask);
        topAdjacentTaskFragment2.setWindowingMode(WINDOWING_MODE_MULTI_WINDOW);
        topAdjacentTaskFragment2.setBounds(bottom);
        topAdjacentTaskFragment2.getTopMostActivity().setVisible(true);
        topAdjacentTaskFragment2.getTopMostActivity().visibleIgnoringKeyguard = true;
        topAdjacentTaskFragment2.setAdjacentTaskFragments(
                new TaskFragment.AdjacentSet(topAdjacentTaskFragment2, topAdjacentTaskFragment1));

        // The task behind and the sandwiched task fragment should both be invisible.
        assertEquals(TASK_FRAGMENT_VISIBILITY_INVISIBLE,
                bottomTask.getVisibility(null /* starting */));
        assertEquals(TASK_FRAGMENT_VISIBILITY_INVISIBLE,
                sandwichTaskFragment.getVisibility(null /* starting */));

        // Makes top adjacent TF translucent.
        topAdjacentTaskFragment2.getTopMostActivity().setOccludesParent(false);
        sandwichTaskFragment.getTopMostActivity().setVisible(true);
        sandwichTaskFragment.getTopMostActivity().visibleIgnoringKeyguard = true;

        // The task behind remains invisible because the bottom most adjacent set contains opaque TF
        assertEquals(TASK_FRAGMENT_VISIBILITY_INVISIBLE,
                bottomTask.getVisibility(null /* starting */));
        // The sandwiched task fragment should be visible behind the translucent task fragment.
        assertEquals(TASK_FRAGMENT_VISIBILITY_VISIBLE_BEHIND_TRANSLUCENT,
                sandwichTaskFragment.getVisibility(null /* starting */));

        // Makes the other adjacent TF translucent.
        topAdjacentTaskFragment2.getTopMostActivity().setOccludesParent(true);
        topAdjacentTaskFragment1.getTopMostActivity().setOccludesParent(false);
        sandwichTaskFragment.getTopMostActivity().setVisible(false);
        sandwichTaskFragment.getTopMostActivity().visibleIgnoringKeyguard = false;

        // The task behind remains invisible because the bottom most adjacent set contains opaque TF
        if (Flags.partialTranslucentActivityEmbedding()) {
            assertEquals(TASK_FRAGMENT_VISIBILITY_INVISIBLE,
                    bottomTask.getVisibility(null /* starting */));
        } else {
            assertEquals(TASK_FRAGMENT_VISIBILITY_VISIBLE_BEHIND_TRANSLUCENT,
                    bottomTask.getVisibility(null /* starting */));
        }
        // The sandwiched task fragment should be invisible
        assertEquals(TASK_FRAGMENT_VISIBILITY_INVISIBLE,
                sandwichTaskFragment.getVisibility(null /* starting */));

        if (Flags.partialTranslucentActivityEmbedding()) {
            // Makes both adjacent TFs translucent.
            topAdjacentTaskFragment2.getTopMostActivity().setOccludesParent(false);
            topAdjacentTaskFragment1.getTopMostActivity().setOccludesParent(false);
            sandwichTaskFragment.getTopMostActivity().setVisible(true);
            sandwichTaskFragment.getTopMostActivity().visibleIgnoringKeyguard = true;

            // The task behind remains invisible because the sandwich TF is opaque in between the
            // bottom most adjacent set
            assertEquals(TASK_FRAGMENT_VISIBILITY_INVISIBLE,
                    bottomTask.getVisibility(null /* starting */));
            // The sandwiched task fragment should be visible behind the translucent task fragment.
            assertEquals(TASK_FRAGMENT_VISIBILITY_VISIBLE_BEHIND_TRANSLUCENT,
                    sandwichTaskFragment.getVisibility(null /* starting */));
        }
    }

    @Test
    public void testVisibility_twoAdjacentTaskFragments() {
        // A fullscreen task with an opaque activity.
        final Task bottomTask = createTask(mDisplayContent.getDefaultTaskDisplayArea(),
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD);
        createActivityRecord(bottomTask);
        // Above it, another fullscreen task with two pair of adjacent task fragments
        final Task topTask = createTask(mDisplayContent.getDefaultTaskDisplayArea(),
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD);
        final Rect leftBounds = new Rect();
        final Rect rightBounds = new Rect();
        topTask.getBounds().splitVertically(leftBounds, rightBounds);
        final TaskFragment adjacentTaskFragment1 = createTaskFragmentWithActivity(topTask);
        adjacentTaskFragment1.setWindowingMode(WINDOWING_MODE_MULTI_WINDOW);
        adjacentTaskFragment1.setBounds(leftBounds);
        adjacentTaskFragment1.getTopMostActivity().setVisible(false);
        adjacentTaskFragment1.getTopMostActivity().visibleIgnoringKeyguard = false;
        final TaskFragment adjacentTaskFragment2 = createTaskFragmentWithActivity(topTask);
        adjacentTaskFragment2.setWindowingMode(WINDOWING_MODE_MULTI_WINDOW);
        adjacentTaskFragment2.setBounds(rightBounds);
        adjacentTaskFragment2.getTopMostActivity().setVisible(false);
        adjacentTaskFragment2.getTopMostActivity().visibleIgnoringKeyguard = false;
        adjacentTaskFragment2.setAdjacentTaskFragments(
                new TaskFragment.AdjacentSet(adjacentTaskFragment2, adjacentTaskFragment1));
        final TaskFragment adjacentTaskFragment3 = createTaskFragmentWithActivity(topTask);
        adjacentTaskFragment3.setWindowingMode(WINDOWING_MODE_MULTI_WINDOW);
        adjacentTaskFragment3.getTopMostActivity().setVisible(true);
        adjacentTaskFragment3.getTopMostActivity().visibleIgnoringKeyguard = true;
        final TaskFragment adjacentTaskFragment4 = createTaskFragmentWithActivity(topTask);
        adjacentTaskFragment4.setWindowingMode(WINDOWING_MODE_MULTI_WINDOW);
        adjacentTaskFragment4.getTopMostActivity().setVisible(true);
        adjacentTaskFragment4.getTopMostActivity().visibleIgnoringKeyguard = true;
        adjacentTaskFragment4.setAdjacentTaskFragments(
                new TaskFragment.AdjacentSet(adjacentTaskFragment4, adjacentTaskFragment3));

        // Makes the top most adjacent TF have different bounds
        leftBounds.right -= 10;
        rightBounds.left -= 10;
        adjacentTaskFragment3.setBounds(leftBounds);
        adjacentTaskFragment4.setBounds(rightBounds);

        // The task behind should both be invisibisTranslucentle.
        assertEquals(TASK_FRAGMENT_VISIBILITY_INVISIBLE,
                bottomTask.getVisibility(null /* starting */));

        // Makes top adjacent TF translucent.
        adjacentTaskFragment4.getTopMostActivity().setOccludesParent(false);
        // The second adjacent TFs should now be visible behind translucent.
        adjacentTaskFragment2.getTopMostActivity().setVisible(true);
        adjacentTaskFragment2.getTopMostActivity().visibleIgnoringKeyguard = true;
        adjacentTaskFragment1.getTopMostActivity().setVisible(true);
        adjacentTaskFragment1.getTopMostActivity().visibleIgnoringKeyguard = true;

        // The task behind remains invisible.
        assertEquals(TASK_FRAGMENT_VISIBILITY_INVISIBLE,
                bottomTask.getVisibility(null /* starting */));
        // The TF behinds the translucent TF should be visible.
        assertEquals(TASK_FRAGMENT_VISIBILITY_VISIBLE_BEHIND_TRANSLUCENT,
                adjacentTaskFragment2.getVisibility(null /* starting */));
        // The adjacent TF should also be visible
        assertEquals(TASK_FRAGMENT_VISIBILITY_VISIBLE_BEHIND_TRANSLUCENT,
                adjacentTaskFragment1.getVisibility(null /* starting */));
    }

    @Test
    public void testVisibility_belowForceOpaqueRootTask_invisible() {
        final Task bottomTask = createTask(mDisplayContent.getDefaultTaskDisplayArea(),
                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_STANDARD, true /* onTop */,
                true /* createActivity */, false /* twoLevelTask */,
                false /* forceOpaque */, false /* shouldIgnoreInsets */,
                false /* disableAppCompatRoundedCorners */);

        // Create the top task that contains two adjacent tasks.
        final Task topTask = new TaskBuilder(mSupervisor)
                .setActivityType(ACTIVITY_TYPE_STANDARD)
                .setWindowingMode(WINDOWING_MODE_FULLSCREEN)
                .setCreatedByOrganizer(true)
                .setForceOpaque(true)
                .build();
        final Rect leftBounds = new Rect();
        final Rect rightBounds = new Rect();
        topTask.getBounds().splitVertically(leftBounds, rightBounds);
        final Task adjacentTask = new TaskBuilder(mSupervisor)
                .setParentTask(topTask)
                .setActivityType(ACTIVITY_TYPE_STANDARD)
                .setWindowingMode(WINDOWING_MODE_MULTI_WINDOW)
                .setCreateActivity(true)
                .build();
        final Task adjacentEmptyTask = new TaskBuilder(mSupervisor)
                .setParentTask(topTask)
                .setActivityType(ACTIVITY_TYPE_STANDARD)
                .setWindowingMode(WINDOWING_MODE_MULTI_WINDOW)
                .setCreateActivity(false)
                .build();
        adjacentTask.setBounds(leftBounds);
        adjacentEmptyTask.setBounds(rightBounds);
        adjacentTask.setAdjacentTaskFragments(
                new TaskFragment.AdjacentSet(adjacentTask, adjacentEmptyTask)
        );
        // Make the top activity of the adjacent task opaque.
        final ActivityRecord topActivity = adjacentTask.getTopMostActivity();
        topActivity.setVisible(true);
        topActivity.visibleIgnoringKeyguard = true;
        topActivity.setOccludesParent(true);

        assertEquals(TASK_FRAGMENT_VISIBILITY_INVISIBLE,
                bottomTask.getVisibility(null /* starting */));
    }

    @Test
    public void testShouldBeVisible_invisibleForEmptyTaskFragment() {
        final Task task = new TaskBuilder(mSupervisor).setCreateActivity(true)
                .setWindowingMode(WINDOWING_MODE_FULLSCREEN).build();
        final TaskFragment taskFragment = new TaskFragmentBuilder(mAtm)
                .setParentTask(task)
                .build();

        // Empty taskFragment should be invisible
        assertFalse(taskFragment.shouldBeVisible(null));

        // Should be invisible even if it is ACTIVITY_TYPE_HOME.
        when(taskFragment.getActivityType()).thenReturn(ACTIVITY_TYPE_HOME);
        assertFalse(taskFragment.shouldBeVisible(null));
    }

    @Test
    public void testShouldBeVisible_behindVisibilityBarrier() {
        final Task bottomTask = createTaskWithActivity(mDefaultTaskDisplayArea,
                WINDOWING_MODE_MULTI_WINDOW, ACTIVITY_TYPE_STANDARD, true /* onTop */,
                false /* twoLevelTask */);
        final Task visibilityBarrier = new Task.Builder(mAtm)
                .setIsVisibilityBarrier(true)
                .build();
        mDefaultTaskDisplayArea.addChild(visibilityBarrier, POSITION_TOP);
        final Task topTask = createTaskWithActivity(mDefaultTaskDisplayArea,
                WINDOWING_MODE_MULTI_WINDOW, ACTIVITY_TYPE_STANDARD, true /* onTop */,
                false /* twoLevelTask */);

        bottomTask.setBounds(new Rect(0, 0, 500, 1000));
        topTask.setBounds(new Rect(500, 0, 1000, 1000));

        assertThat(topTask.shouldBeVisible(null)).isTrue();
        assertThat(visibilityBarrier.shouldBeVisible(null)).isFalse();
        assertThat(bottomTask.shouldBeVisible(null)).isFalse();

        visibilityBarrier.removeImmediately();

        assertThat(bottomTask.shouldBeVisible(null)).isTrue();
    }

    @Test
    public void testShouldBeVisible_rootTaskWithAllChildBehindVisibilityBarrier() {
        final Task rootTask = createTaskWithActivity(mDefaultTaskDisplayArea,
                WINDOWING_MODE_MULTI_WINDOW, ACTIVITY_TYPE_STANDARD, true /* onTop */,
                true /* twoLevelTask */);
        final Task leafTask = rootTask.getTopLeafTask();

        assertThat(rootTask.shouldBeVisible(null)).isTrue();
        assertThat(leafTask.shouldBeVisible(null)).isTrue();

        final Task visibilityBarrier = new Task.Builder(mAtm)
                .setIsVisibilityBarrier(true)
                .build();
        rootTask.addChild(visibilityBarrier, POSITION_TOP);

        assertThat(rootTask.shouldBeVisible(null)).isFalse();
        assertThat(leafTask.shouldBeVisible(null)).isFalse();
        assertThat(visibilityBarrier.shouldBeVisible(null)).isFalse();
    }

    @Test
    public void testShouldBeVisible_forceLeafTaskNonOccluding() {
        final Task rootTask = createTaskWithActivity(mDefaultTaskDisplayArea,
                WINDOWING_MODE_MULTI_WINDOW, ACTIVITY_TYPE_STANDARD, true /* onTop */,
                true /* twoLevelTask */);
        final Task bottomTask = rootTask.getTopLeafTask();
        final ActivityRecord topR = createActivityRecordWithParentTask(rootTask);
        topR.visibleIgnoringKeyguard = true;
        final Task topTask = topR.getTask();

        assertThat(rootTask.shouldBeVisible(null)).isTrue();
        assertThat(topTask.shouldBeVisible(null)).isTrue();
        assertThat(bottomTask.shouldBeVisible(null)).isFalse();

        rootTask.setForceLeafTasksNonOccluding(true);

        assertThat(topTask.shouldBeVisible(null)).isTrue();
        assertThat(bottomTask.shouldBeVisible(null)).isTrue();
    }

    @Test
    public void testShouldBeVisible_forceLeafTaskNonOccludingBehindVisibilityBarrier() {
        final Task rootTask = createTaskWithActivity(mDefaultTaskDisplayArea,
                WINDOWING_MODE_MULTI_WINDOW, ACTIVITY_TYPE_STANDARD, true /* onTop */,
                true /* twoLevelTask */);
        rootTask.setForceLeafTasksNonOccluding(true);
        final Task bottomTask = rootTask.getTopLeafTask();
        final ActivityRecord topR = createActivityRecordWithParentTask(rootTask);
        topR.visibleIgnoringKeyguard = true;
        final Task topTask = topR.getTask();

        assertThat(rootTask.shouldBeVisible(null)).isTrue();
        assertThat(topTask.shouldBeVisible(null)).isTrue();
        assertThat(bottomTask.shouldBeVisible(null)).isTrue();

        final Task visibilityBarrier = new Task.Builder(mAtm)
                .setIsVisibilityBarrier(true)
                .build();
        rootTask.addChild(visibilityBarrier, POSITION_TOP);

        assertThat(rootTask.shouldBeVisible(null)).isFalse();
        assertThat(topTask.shouldBeVisible(null)).isFalse();
        assertThat(bottomTask.shouldBeVisible(null)).isFalse();
    }

    @Test
    public void testOpaque_leafTask_occludingActivity_isOpaque() {
        final ActivityRecord activity = new ActivityBuilder(mAtm).setCreateTask(true).build();
        activity.setOccludesParent(true);
        final TaskFragment tf = activity.getTaskFragment();

        assertIsOpaque(tf, true);
    }

    @Test
    public void testOpaque_leafTask_nonOccludingActivity_isTranslucent() {
        final ActivityRecord activity = new ActivityBuilder(mAtm).setCreateTask(true).build();
        activity.setOccludesParent(false);
        final TaskFragment tf = activity.getTaskFragment();

        assertIsOpaque(tf, false);
    }

    @Test
    public void testOpaque_rootTask_translucentFillingChild_isTranslucent() {
        final Task rootTask = new TaskBuilder(mSupervisor).setOnTop(true).build();
        createLeafTaskWithActivity(/* parent */ rootTask,
                WINDOWING_MODE_FREEFORM, /* opaque */ false, /* filling */ true);

        assertIsOpaque(rootTask, false);
    }

    @Test
    public void testOpaque_rootTask_opaqueAndNotFillingChild_isTranslucent() {
        final Task rootTask = new TaskBuilder(mSupervisor).setOnTop(true).build();
        createLeafTaskWithActivity(/* parent */ rootTask,
                WINDOWING_MODE_FREEFORM, /* opaque */ true, /* filling */ false);

        assertIsOpaque(rootTask, false);
    }

    @Test
    public void testOpaque_rootTask_opaqueAndFillingChild_isOpaque() {
        final Task rootTask = new TaskBuilder(mSupervisor).setOnTop(true).build();
        createLeafTaskWithActivity(/* parent */ rootTask,
                WINDOWING_MODE_FREEFORM, /* opaque */ true, /* filling */ true);

        assertIsOpaque(rootTask, true);
    }

    @Test
    public void testOpaque_rootTask_nonFillingOpaqueAdjacentChildren_isOpaque() {
        final Task rootTask = new TaskBuilder(mSupervisor).setOnTop(true).build();
        final TaskFragment tf1 = createLeafTaskWithActivity(/* parent */ rootTask,
                WINDOWING_MODE_MULTI_WINDOW, /* opaque */ true, /* filling */ false);
        final TaskFragment tf2 = createLeafTaskWithActivity(/* parent */ rootTask,
                WINDOWING_MODE_MULTI_WINDOW, /* opaque */ true, /* filling */ false);
        tf1.setAdjacentTaskFragments(new TaskFragment.AdjacentSet(tf1, tf2));

        assertIsOpaque(rootTask, true);
    }

    @Test
    public void testOpaque_rootTask_nonFillingOpaqueAdjacentChildren_multipleAdjacent_isOpaque() {
        final Task rootTask = new TaskBuilder(mSupervisor).setOnTop(true).build();
        final TaskFragment tf1 = createLeafTaskWithActivity(/* parent */ rootTask,
                WINDOWING_MODE_MULTI_WINDOW, /* opaque */ true, /* filling */ false);
        final TaskFragment tf2 = createLeafTaskWithActivity(/* parent */ rootTask,
                WINDOWING_MODE_MULTI_WINDOW, /* opaque */ true, /* filling */ false);
        final TaskFragment tf3 = createLeafTaskWithActivity(/* parent */ rootTask,
                WINDOWING_MODE_MULTI_WINDOW, /* opaque */ true, /* filling */ false);
        tf1.setAdjacentTaskFragments(new TaskFragment.AdjacentSet(tf1, tf2, tf3));

        assertIsOpaque(rootTask, true);
    }

    @Test
    public void testOpaque_nonLeafTaskFragmentWithDirectActivity_opaque() {
        final ActivityRecord directChildActivity = new ActivityBuilder(mAtm).setCreateTask(true)
                .build();
        directChildActivity.setOccludesParent(true);
        final Task nonLeafTask = directChildActivity.getTask();
        final TaskFragment directChildFragment = new TaskFragment(mAtm, new Binder(),
                true /* createdByOrganizer */, false /* isEmbedded */);
        nonLeafTask.addChild(directChildFragment, 0);

        assertIsOpaque(nonLeafTask, true);
    }

    @Test
    public void testOpaque_nonLeafTaskFragmentWithDirectActivity_transparent() {
        final ActivityRecord directChildActivity = new ActivityBuilder(mAtm).setCreateTask(true)
                .build();
        directChildActivity.setOccludesParent(false);
        final Task nonLeafTask = directChildActivity.getTask();
        final TaskFragment directChildFragment = new TaskFragment(mAtm, new Binder(),
                true /* createdByOrganizer */, false /* isEmbedded */);
        nonLeafTask.addChild(directChildFragment, 0);

        assertIsOpaque(nonLeafTask, false);
    }

    @Test
    public void testOpaque_leafTaskUpdated() {
        final Task rootTask = new TaskBuilder(mSupervisor).setCreatedByOrganizer(true).build();
        final TaskFragment opaqueTask = createLeafTaskWithActivity(rootTask,
                WINDOWING_MODE_FREEFORM, /* opaque */ true, /* filling */ true);
        final Task childTask = new TaskBuilder(mSupervisor).setParentTask(rootTask).build();
        final ActivityRecord directChildActivity = new ActivityBuilder(mAtm).setTask(childTask)
                .build();

        directChildActivity.setOccludesParent(false);

        assertIsOpaque(childTask, false);
        assertIsOpaque(opaqueTask, true);

        directChildActivity.setOccludesParent(true);

        assertIsOpaque(childTask, true);
    }

    @Test
    public void testOpaque_forceOpaque() {
        final Task forceOpaqueRootTask = new TaskBuilder(mSupervisor)
                .setActivityType(ACTIVITY_TYPE_STANDARD)
                .setWindowingMode(WINDOWING_MODE_FULLSCREEN)
                .setCreatedByOrganizer(true)
                .setForceOpaque(true)
                .build();
        final Rect leftBounds = new Rect();
        final Rect rightBounds = new Rect();
        forceOpaqueRootTask.getBounds().splitVertically(leftBounds, rightBounds);
        final Task adjacentTask = new TaskBuilder(mSupervisor)
                .setParentTask(forceOpaqueRootTask)
                .setActivityType(ACTIVITY_TYPE_STANDARD)
                .setWindowingMode(WINDOWING_MODE_MULTI_WINDOW)
                .setCreateActivity(true)
                .build();
        final Task adjacentEmptyTask = new TaskBuilder(mSupervisor)
                .setParentTask(forceOpaqueRootTask)
                .setActivityType(ACTIVITY_TYPE_STANDARD)
                .setWindowingMode(WINDOWING_MODE_MULTI_WINDOW)
                .setCreateActivity(false)
                .build();
        adjacentTask.setBounds(leftBounds);
        adjacentEmptyTask.setBounds(rightBounds);
        adjacentTask.setAdjacentTaskFragments(
                new TaskFragment.AdjacentSet(adjacentTask, adjacentEmptyTask)
        );

        assertIsOpaque(forceOpaqueRootTask, true);

        final Task forceOpaqueTaskWithTranslucentActivity = new TaskBuilder(mSupervisor)
                .setCreatedByOrganizer(true)
                .setForceOpaque(true)
                .setCreateActivity(true)
                .build();
        final ActivityRecord activity = forceOpaqueTaskWithTranslucentActivity.getTopMostActivity();
        activity.setOccludesParent(false);

        assertIsOpaque(forceOpaqueTaskWithTranslucentActivity, true);
    }

    @Test
    public void testOpaque_rootTaskWithAllChildBehindVisibilityBarrier() {
        final Task rootTask = createTaskWithActivity(mDefaultTaskDisplayArea,
                WINDOWING_MODE_MULTI_WINDOW, ACTIVITY_TYPE_STANDARD, true /* onTop */,
                true /* twoLevelTask */);
        final Task leafTask = rootTask.getTopLeafTask();

        assertIsOpaque(rootTask, true);
        assertIsOpaque(leafTask, true);

        final Task visibilityBarrier = new Task.Builder(mAtm)
                .setIsVisibilityBarrier(true)
                .build();
        rootTask.addChild(visibilityBarrier, POSITION_TOP);

        assertIsOpaque(rootTask, false);
        assertIsOpaque(leafTask, true);
        assertIsOpaque(visibilityBarrier, false);
    }

    @Test
    public void testOpaque_forceLeafTaskNonOccluding() {
        final Task rootTask = createTaskWithActivity(mDefaultTaskDisplayArea,
                WINDOWING_MODE_MULTI_WINDOW, ACTIVITY_TYPE_STANDARD, true /* onTop */,
                true /* twoLevelTask */);
        final Task bottomTask = rootTask.getTopLeafTask();
        final Task topTask = createActivityRecordWithParentTask(rootTask).getTask();

        assertIsOpaque(rootTask, true);
        assertIsOpaque(topTask, true);
        assertIsOpaque(bottomTask, true);

        rootTask.setForceLeafTasksNonOccluding(true);

        assertIsOpaque(rootTask, false);
        assertIsOpaque(topTask, false);
        assertIsOpaque(bottomTask, false);
    }

    @Test
    @EnableFlags(Flags.FLAG_PARTIAL_TRANSLUCENT_ACTIVITY_EMBEDDING)
    public void testPartialTranslucentAdjTaskFragments_bottomMost_opaque() {
        final Task rootTask = new TaskBuilder(mSupervisor).setCreatedByOrganizer(true).build();
        rootTask.setBounds(0, 0, 1000, 1000);
        final TaskFragment tf1 = createTaskFragmentWithActivity(rootTask);
        tf1.setWindowingMode(WINDOWING_MODE_MULTI_WINDOW);
        tf1.setBounds(0, 0, 500, 1000);
        tf1.getTopMostActivity().setOccludesParent(true);

        final TaskFragment tf2 = createTaskFragmentWithActivity(rootTask);
        tf2.setWindowingMode(WINDOWING_MODE_MULTI_WINDOW);
        tf2.setBounds(500, 0, 1000, 1000);
        tf2.getTopMostActivity().setOccludesParent(false);

        tf1.setAdjacentTaskFragments(new TaskFragment.AdjacentSet(tf1, tf2));

        // Bottom-most partial translucent adj TF pair should make the Task opaque.
        assertIsOpaque(rootTask, true);
    }

    @Test
    @EnableFlags(Flags.FLAG_PARTIAL_TRANSLUCENT_ACTIVITY_EMBEDDING)
    public void testPartialTranslucentAdjTaskFragments_notBottomMost_translucent() {
        final Task rootTask = new TaskBuilder(mSupervisor).setCreatedByOrganizer(true).build();
        rootTask.setBounds(0, 0, 1000, 1000);
        final TaskFragment bottomTf = createTaskFragmentWithActivity(rootTask);
        bottomTf.setWindowingMode(WINDOWING_MODE_MULTI_WINDOW);
        // To test if the adj TF set is treated as opaque, we make the bottom TF translucent.
        bottomTf.getTopMostActivity().setOccludesParent(false);

        final TaskFragment tf1 = createTaskFragmentWithActivity(rootTask);
        tf1.setWindowingMode(WINDOWING_MODE_MULTI_WINDOW);
        tf1.setBounds(0, 0, 500, 1000);
        tf1.getTopMostActivity().setOccludesParent(true);

        final TaskFragment tf2 = createTaskFragmentWithActivity(rootTask);
        tf2.setWindowingMode(WINDOWING_MODE_MULTI_WINDOW);
        tf2.setBounds(500, 0, 1000, 1000);
        tf2.getTopMostActivity().setOccludesParent(false);

        tf1.setAdjacentTaskFragments(new TaskFragment.AdjacentSet(tf1, tf2));

        // Not bottom-most (index 1 and 2), so it should NOT trigger the special logic.
        assertIsOpaque(rootTask, false);
    }

    private Task createTaskWithActivityAndOverrideTranslucent(
            @WindowConfiguration.WindowingMode int windowingMode, boolean translucent) {
        final Task rootTask = createTaskWithActivity(mDefaultTaskDisplayArea, windowingMode,
                ACTIVITY_TYPE_STANDARD, true /* onTop */);
        doReturn(translucent).when(rootTask).isTranslucent(any());
        return rootTask;
    }

    /**
     * Note: this test asserts for the default check, which is
     *  - ignoringKeyguard = true
     *  - ignoringInvisibleActivity = false
     *  - ignoringFinishing = true
     */
    private void assertIsOpaque(@NonNull WindowContainer windowContainer, boolean isOpaque) {
        assertThat(mHelper.isOpaque(windowContainer, null /* starting */,
                true /* ignoringKeyguard */, false /* ignoringInvisibleActivity */,
                true /* ignoringFinishing */))
                .isEqualTo(isOpaque);
    }
}
