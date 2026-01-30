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

package com.android.server.companion.datatransfer.continuity.tasks;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doReturn;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static com.google.common.truth.Truth.assertThat;

import android.app.ActivityTaskManager;
import android.app.ActivityManager.RunningTaskInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ActivityInfo;
import android.content.ComponentName;
import android.os.RemoteException;
import android.platform.test.annotations.Presubmit;
import android.testing.AndroidTestingRunner;

import com.android.server.companion.datatransfer.continuity.messages.RemoteTaskInfo;
import com.android.server.wm.ActivityTaskManagerInternal;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

@Presubmit
@RunWith(AndroidTestingRunner.class)
public class RunningTaskFetcherTest {

    private static final String LAUNCHER_PACKAGE_NAME = "com.example.launcher";

    @Mock private ActivityTaskManager mockActivityTaskManager;
    @Mock private ActivityTaskManagerInternal mockActivityTaskManagerInternal;
    @Mock private PackageManager mockPackageManager;
    @Mock private PackageMetadataCache mockPackageMetadataCache;

    private RunningTaskFetcher runningTaskFetcher;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        ResolveInfo launcherResolveInfo = new ResolveInfo();
        launcherResolveInfo.activityInfo = new ActivityInfo();
        launcherResolveInfo.activityInfo.packageName = LAUNCHER_PACKAGE_NAME;
        when(mockPackageManager.resolveActivity(any(), anyInt()))
            .thenReturn(launcherResolveInfo);

        runningTaskFetcher = new RunningTaskFetcher(
            mockActivityTaskManager,
            mockActivityTaskManagerInternal,
            mockPackageManager,
            mockPackageMetadataCache);
    }

    @Test
    public void testGetRunningTasks_returnsRunningTasks() {
        FakeTask[] tasks = {
            new FakeTask(
                1,
                "com.example.app1",
                100,
                new PackageMetadata("app1", new byte[0]),
                true),
            new FakeTask(
                2,
                "com.example.app2",
                200,
                new PackageMetadata("app2", new byte[0]),
                false),
            new FakeTask(
                3,
                LAUNCHER_PACKAGE_NAME,
                300,
                new PackageMetadata("app3", new byte[0]),
                true)
        };
        setupRunningTasks(tasks);

        List<RemoteTaskInfo> remoteTasks = runningTaskFetcher.getRunningTasks();

        assertThat(remoteTasks).containsExactly(
            tasks[0].toRemoteTaskInfo(), tasks[1].toRemoteTaskInfo());
        assertThat(remoteTasks).hasSize(2);
        assertThat(remoteTasks.get(0)).isEqualTo(tasks[0].toRemoteTaskInfo());
        assertThat(remoteTasks.get(1)).isEqualTo(tasks[1].toRemoteTaskInfo());
    }

    @Test
    public void testGetRunningTasks_filtersTasksWithoutPackageMetadata() {
        FakeTask[] tasks = {
            new FakeTask(
                1,
                "com.example.app1",
                100,
                new PackageMetadata("app1", new byte[0]),
                true),
            new FakeTask(2, "com.example.app2", 200, null, true),
        };
        setupRunningTasks(tasks);

        List<RemoteTaskInfo> remoteTasks = runningTaskFetcher.getRunningTasks();

        assertThat(remoteTasks).containsExactly(tasks[0].toRemoteTaskInfo());
        assertThat(remoteTasks).hasSize(1);
        assertThat(remoteTasks.get(0)).isEqualTo(tasks[0].toRemoteTaskInfo());
    }

    @Test
    public void testGetRunningTaskById_returnsRunningTask() {
        FakeTask[] tasks = {
            new FakeTask(
                1,
                "com.example.app1",
                100,
                new PackageMetadata("app1", new byte[0]),
                true),
            new FakeTask(
                2,
                "com.example.app2",
                200,
                new PackageMetadata("app2", new byte[0]),
                false),
        };
        setupRunningTasks(tasks);

        RemoteTaskInfo remoteTask = runningTaskFetcher.getRunningTaskById(tasks[0].taskId());

        assertThat(remoteTask).isEqualTo(tasks[0].toRemoteTaskInfo());
    }

    @Test
    public void testGetRunningTaskById_taskNotFound_returnsNull() {
        FakeTask[] tasks = {
            new FakeTask(
                1,
                "com.example.app1",
                100,
                new PackageMetadata("app1", new byte[0]),
                true),
            new FakeTask(
                2,
                "com.example.app2",
                200,
                new PackageMetadata("app2", new byte[0]),
                false),
        };
        setupRunningTasks(tasks);

        RemoteTaskInfo remoteTask = runningTaskFetcher.getRunningTaskById(3);

        assertThat(remoteTask).isNull();
    }

    @Test
    public void testGetRunningTaskById_taskWithoutPackageMetadata_returnsNull() {
        FakeTask[] tasks = {
            new FakeTask(2, "com.example.app2", 200, null, true),
        };
        setupRunningTasks(tasks);

        RemoteTaskInfo remoteTask = runningTaskFetcher.getRunningTaskById(tasks[0].taskId());

        assertThat(remoteTask).isNull();
    }

    @Test
    public void testGetRunningTaskById_launcherPackage_returnsNull() {
        FakeTask[] tasks = {
            new FakeTask(
                1, LAUNCHER_PACKAGE_NAME, 200, new PackageMetadata("launcher", new byte[0]), false),
        };
        setupRunningTasks(tasks);

        RemoteTaskInfo remoteTask = runningTaskFetcher.getRunningTaskById(tasks[0].taskId());

        assertThat(remoteTask).isNull();
    }

    private record FakeTask(
        int taskId,
        String packageName,
        long lastActiveTime,
        PackageMetadata packageMetadata,
        boolean isHandoffEnabled) {

        public RemoteTaskInfo toRemoteTaskInfo() {
            return new RemoteTaskInfo(
                taskId,
                packageMetadata.label(),
                lastActiveTime,
                packageMetadata.icon(),
                isHandoffEnabled);
        }
    }

    private void setupRunningTasks(FakeTask... tasks) {
        List<RunningTaskInfo> runningTaskInfos = new ArrayList<>();
        for (FakeTask task : tasks) {
            runningTaskInfos.add(setupTask(task));
        }

        doReturn(runningTaskInfos).when(mockActivityTaskManager).getTasks(Integer.MAX_VALUE, true);
    }

    private RunningTaskInfo setupTask(FakeTask task) {
        RunningTaskInfo taskInfo = new RunningTaskInfo();
        taskInfo.taskId = task.taskId;
        taskInfo.baseActivity = new ComponentName(task.packageName, "com.example.app.MainActivity");
        taskInfo.lastActiveTime = task.lastActiveTime;
        when(mockPackageMetadataCache.getMetadataForPackage(task.packageName))
            .thenReturn(task.packageMetadata);
        when(mockActivityTaskManagerInternal.isHandoffEnabledForTask(task.taskId))
            .thenReturn(task.isHandoffEnabled);

        return taskInfo;
    }
}