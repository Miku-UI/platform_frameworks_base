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

import static com.android.window.flags.Flags.FLAG_ENABLE_APP_RESTART_AFTER_UPDATE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Intent;
import android.os.PersistableBundle;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;
import android.util.ArraySet;

import androidx.test.filters.SmallTest;

import com.android.server.wm.utils.StubOrganizer;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Unit tests for {@link PackageUpdateManager}.
 *
 * Build/run with:
 * atest FrameworksServicesTests:com.android.server.wm.PackageUpdateManagerTest
 */
@SmallTest
@Presubmit
@RunWith(WindowTestRunner.class)
public class PackageUpdateManagerTests extends WindowTestsBase {

    private static final String PKG_1 = "com.test.app1";
    private static final String PKG_2 = "com.test.app2";
    private static final String AFFINITY_1 = "affinity1";
    private static final String AFFINITY_2 = "affinity2";
    private static final ComponentName COMPONENT_1 = new ComponentName(PKG_1, "TestActivity1");
    private static final ComponentName COMPONENT_2 = new ComponentName(PKG_1, "TestActivity2");

    private PackageUpdateManager mManager;
    private PersistableBundle mBundle1;
    private PersistableBundle mBundle2;

    @Before
    public void setUp() {
        // Create the class under test
        mManager = new PackageUpdateManager(mAtm);

        // Set up test bundles
        mBundle1 = new PersistableBundle();
        mBundle1.putString("key", "value1");

        mBundle2 = new PersistableBundle();
        mBundle2.putString("key", "value2");
    }

    @Test
    @EnableFlags(FLAG_ENABLE_APP_RESTART_AFTER_UPDATE)
    public void testAddAndGetPersistentTask_success() {
        final Task task = setUpTask(PKG_1, AFFINITY_1, COMPONENT_1);

        mManager.addPersistentTaskForPackage(PKG_1, task.mTaskId, mBundle1);

        PersistableBundle result = mManager.getPersistentStateForTask(task);
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(mBundle1);
    }

    @Test
    @EnableFlags(FLAG_ENABLE_APP_RESTART_AFTER_UPDATE)
    public void testGetPersistentTask_stateIsRemovedAfterGet() {
        final Task task = setUpTask(PKG_1, AFFINITY_1, COMPONENT_1);

        mManager.addPersistentTaskForPackage(PKG_1, task.mTaskId, mBundle1);

        // First get should succeed
        PersistableBundle result1 = mManager.getPersistentStateForTask(task);
        assertThat(result1).isNotNull();

        // Second get for the *same task* should fail, as it's been removed
        PersistableBundle result2 = mManager.getPersistentStateForTask(task);
        assertThat(result2).isNull();
    }

    @Test
    @EnableFlags(FLAG_ENABLE_APP_RESTART_AFTER_UPDATE)
    public void testGetPersistentTask_packageNotFound() {
        final Task task = setUpTask(PKG_1, AFFINITY_1, COMPONENT_1);
        final Task taskNotAdded = setUpTask(PKG_2, AFFINITY_1, COMPONENT_1);
        mManager.addPersistentTaskForPackage(PKG_1, task.mTaskId, mBundle1);

        PersistableBundle result = mManager.getPersistentStateForTask(taskNotAdded);

        assertThat(result).isNull();
    }

    @Test
    @EnableFlags(FLAG_ENABLE_APP_RESTART_AFTER_UPDATE)
    public void testAddAndGet_MultipleTasksSamePackage() {
        final Task task1 = setUpTask(PKG_1, AFFINITY_1, COMPONENT_1);
        final Task task2 = setUpTask(PKG_1, AFFINITY_2, COMPONENT_2);
        mManager.addPersistentTaskForPackage(PKG_1, task1.mTaskId, mBundle1);
        mManager.addPersistentTaskForPackage(PKG_1, task2.mTaskId, mBundle2);

        // Get state for the first task
        PersistableBundle result1 = mManager.getPersistentStateForTask(task1);
        assertThat(result1).isNotNull();
        assertThat(result1).isEqualTo(mBundle1);

        // Get state for the second task
        PersistableBundle result2 = mManager.getPersistentStateForTask(task2);
        assertThat(result2).isNotNull();
        assertThat(result2).isEqualTo(mBundle2);
    }

    @Test
    @EnableFlags(FLAG_ENABLE_APP_RESTART_AFTER_UPDATE)
    public void testAddAndGet_MultiplePackages() {
        final Task task1 = setUpTask(PKG_1, AFFINITY_1, COMPONENT_1);
        final Task task2 = setUpTask(PKG_2, AFFINITY_1, COMPONENT_1);
        mManager.addPersistentTaskForPackage(PKG_1, task1.mTaskId, mBundle1);
        mManager.addPersistentTaskForPackage(PKG_2, task2.mTaskId, mBundle2);

        PersistableBundle result1 = mManager.getPersistentStateForTask(task1);
        PersistableBundle result2 = mManager.getPersistentStateForTask(task2);

        assertThat(result1).isNotNull();
        assertThat(result1).isEqualTo(mBundle1);
        assertThat(result2).isNotNull();
        assertThat(result2).isEqualTo(mBundle2);
    }

    @Test
    @DisableFlags(FLAG_ENABLE_APP_RESTART_AFTER_UPDATE)
    public void testMethods_WhenFlagIsDisabled() {
        final Task task1 = setUpTask(PKG_1, AFFINITY_1, COMPONENT_1);
        mManager.addPersistentTaskForPackage(PKG_1, task1.mTaskId, mBundle1);

        PersistableBundle result = mManager.getPersistentStateForTask(task1);

        assertThat(result).isNull();
    }

    @Test
    @EnableFlags(FLAG_ENABLE_APP_RESTART_AFTER_UPDATE)
    public void testOnPackageUpdateFinished_notifiesController() {
        PackageUpdateOrganizer o = new PackageUpdateOrganizer();
        mWm.mAtmService.mTaskOrganizerController.registerTaskOrganizer(o);
        final Task task1 = setUpTask(PKG_1, AFFINITY_1, COMPONENT_1);
        final Task task2 = setUpTask(PKG_1, AFFINITY_2, COMPONENT_2);
        ArraySet<Task> tasks = new ArraySet<>();
        tasks.add(task1);
        tasks.add(task2);
        mManager.addUpdatingTasksForPackage(PKG_1, tasks);

        mManager.onPackageUpdateFinished(PKG_1, 123);

        assertThat(o.mUpdatedTaskIds).containsExactly(task1.mTaskId, task2.mTaskId);
    }

    @Test
    @EnableFlags(FLAG_ENABLE_APP_RESTART_AFTER_UPDATE)
    public void testOnPackageUpdateFinished_notifiesControllerForAttachedTasksOnly() {
        // Set up an organizer to capture notifications.
        PackageUpdateOrganizer o = new PackageUpdateOrganizer();
        mWm.mAtmService.mTaskOrganizerController.registerTaskOrganizer(o);

        // Create two tasks for the same package.
        final Task attachedTask = setUpTask(PKG_1, AFFINITY_1, COMPONENT_1);
        final Task unattachedTask = setUpTask(PKG_1, AFFINITY_2, COMPONENT_2);

        // Add both tasks to the manager as "updating".
        ArraySet<Task> tasks = new ArraySet<>();
        tasks.add(attachedTask);
        tasks.add(unattachedTask);
        mManager.addUpdatingTasksForPackage(PKG_1, tasks);

        // Make one task unattached by removing it from its parent display area.
        // This simulates a task that is in recents but not in the active hierarchy.
        unattachedTask.getParent().removeChild(unattachedTask);

        // Trigger the package update finished logic.
        mManager.onPackageUpdateFinished(PKG_1, 123);

        // Verify that the controller is only notified for the attached task.
        // The unattached task should be ignored because of MATCH_ATTACHED_TASK_ONLY.
        assertThat(o.mUpdatedTaskIds).containsExactly(attachedTask.mTaskId);
    }

    @Test
    @EnableFlags(FLAG_ENABLE_APP_RESTART_AFTER_UPDATE)
    public void testOnRecentTaskRemoved_removesPersistentState() {
        final Task task1 = setUpTask(PKG_1, AFFINITY_1, COMPONENT_1);
        final Task task2 = setUpTask(PKG_1, AFFINITY_2, COMPONENT_2);
        mManager.addPersistentTaskForPackage(PKG_1, task1.mTaskId, mBundle1);
        mManager.addPersistentTaskForPackage(PKG_1, task2.mTaskId, mBundle2);

        // Remove the first task
        mManager.onRecentTaskRemoved(task1, false, false, null);

        // Verify the state for the first task is removed, but the second still exists
        assertThat(mManager.getPersistentStateForTask(task1)).isNull();
        assertThat(mManager.getPersistentStateForTask(task2)).isEqualTo(mBundle2);
    }

    @Test
    @EnableFlags(FLAG_ENABLE_APP_RESTART_AFTER_UPDATE)
    public void testOnRecentTaskRemoved_movesStateToReplacingTask() {
        final Task task = setUpTask(PKG_1, AFFINITY_1, COMPONENT_1);
        final Task replacingTask = setUpTask(PKG_1, AFFINITY_1, COMPONENT_1);
        mManager.addPersistentTaskForPackage(PKG_1, task.mTaskId, mBundle1);

        // Remove the original task and provide a replacement
        mManager.onRecentTaskRemoved(task, false, false, replacingTask);

        // Verify the state is moved to the replacing task
        assertThat(mManager.getPersistentStateForTask(task)).isNull();
        assertThat(mManager.getPersistentStateForTask(replacingTask)).isEqualTo(mBundle1);
    }

    @Test
    @EnableFlags(FLAG_ENABLE_APP_RESTART_AFTER_UPDATE)
    public void testOnRecentTaskRemoved_doesNotMoveStateForDifferentPackage() {
        final Task task = setUpTask(PKG_1, AFFINITY_1, COMPONENT_1);
        final Task replacingTask = setUpTask(PKG_2, AFFINITY_1, COMPONENT_1); // Different pkg
        mManager.addPersistentTaskForPackage(PKG_1, task.mTaskId, mBundle1);

        // Remove the original task and provide a replacement from a different package
        mManager.onRecentTaskRemoved(task, false, false, replacingTask);

        // Verify the state is not moved to the replacing task
        assertThat(mManager.getPersistentStateForTask(task)).isNull();
        assertThat(mManager.getPersistentStateForTask(replacingTask)).isNull();
    }

    @Test
    @EnableFlags(FLAG_ENABLE_APP_RESTART_AFTER_UPDATE)
    public void testOnRecentTaskRemoved_doesNotMoveStateForDifferentAffinity() {
        final Task task = setUpTask(PKG_1, AFFINITY_1, COMPONENT_1);
        final Task replacingTask = setUpTask(PKG_1, AFFINITY_2, COMPONENT_1); // Different affinity
        mManager.addPersistentTaskForPackage(PKG_1, task.mTaskId, mBundle1);

        mManager.onRecentTaskRemoved(task, false, false, replacingTask);

        assertThat(mManager.getPersistentStateForTask(task)).isNull();
        assertThat(mManager.getPersistentStateForTask(replacingTask)).isNull();
    }

    @Test
    @EnableFlags(FLAG_ENABLE_APP_RESTART_AFTER_UPDATE)
    public void testOnRecentTaskRemoved_doesNotMoveStateForDifferentIntent() {
        final Task task = setUpTask(PKG_1, AFFINITY_1, COMPONENT_1);
        final Task replacingTask = setUpTask(PKG_1, AFFINITY_1, COMPONENT_2); // Different intent
        mManager.addPersistentTaskForPackage(PKG_1, task.mTaskId, mBundle1);

        mManager.onRecentTaskRemoved(task, false, false, replacingTask);

        assertThat(mManager.getPersistentStateForTask(task)).isNull();
        assertThat(mManager.getPersistentStateForTask(replacingTask)).isNull();
    }

    static class PackageUpdateOrganizer extends StubOrganizer {
        List<Integer> mUpdatedTaskIds = new ArrayList<>();

        @Override
        public void onPackageUpdateFinished(
                List<ActivityManager.RunningTaskInfo> updatedTasks) {
            mUpdatedTaskIds = updatedTasks.stream().map(
                    it -> it.taskId).collect(Collectors.toList());
        }
    }

    private Task setUpTask(String pkg, String affinity, ComponentName component) {
        final Task rootTask = createTask(mDisplayContent);
        when(rootTask.getBasePackageName()).thenReturn(pkg);
        rootTask.affinity = affinity;
        rootTask.intent = new Intent();
        rootTask.intent.setComponent(component);
        return rootTask;
    }
}
