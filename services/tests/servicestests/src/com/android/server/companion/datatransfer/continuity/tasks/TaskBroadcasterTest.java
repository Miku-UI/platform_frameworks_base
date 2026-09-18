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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager.RunningTaskInfo;
import android.app.AppOpsManager;
import android.app.HandoffActivityParams;
import android.content.ComponentName;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.RemoteException;
import android.platform.test.annotations.Presubmit;
import com.android.server.companion.datatransfer.continuity.TaskContinuityTest;
import com.android.server.companion.datatransfer.continuity.messages.HandoffOptions;
import com.android.server.companion.datatransfer.continuity.messages.RemoteTaskInfo;
import com.android.server.companion.datatransfer.continuity.messages.TaskContinuityMessage;
import com.android.server.companion.datatransfer.continuity.messages.TaskStackBroadcastMessage;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

@Presubmit
public class TaskBroadcasterTest extends TaskContinuityTest {

    private TaskBroadcaster mTaskBroadcaster;

    @Before
    public void setUp() {
        mTaskBroadcaster = new TaskBroadcaster(USER_ID, mMockContext, mMockTaskContinuityMessenger);
    }

    @Test
    public void testOnDeviceConnected_sendsMessageToDevice() throws RemoteException {
        FakeTask[] tasks = {
            new FakeTask(
                    1,
                    USER_ID,
                    "com.example.app1",
                    100,
                    new HandoffOptions(true, true),
                    AppOpsManager.MODE_ALLOWED),
            new FakeTask(
                    2,
                    USER_ID,
                    "com.example.app3",
                    100,
                    new HandoffOptions(true, false),
                    AppOpsManager.MODE_ALLOWED),
            new FakeTask(
                    3,
                    USER_ID,
                    "com.example.app2",
                    200,
                    new HandoffOptions(false, true),
                    AppOpsManager.MODE_ALLOWED),
            new FakeTask(
                    4,
                    1000,
                    "com.example.app4",
                    400,
                    new HandoffOptions(true, true),
                    AppOpsManager.MODE_IGNORED),
        };
        setupRunningTasks(tasks);
        mTaskBroadcaster.onDeviceConnected();
        verify(mMockTaskContinuityMessenger, times(1))
                .sendMessage(eq(createExpectedTaskContinuityMessage(tasks[0], tasks[1])));
    }

    @Test
    public void testOnTaskStackChanged_sendsMessageToDevice() throws RemoteException {
        FakeTask[] tasks = {
            new FakeTask(
                    1,
                    USER_ID,
                    "com.example.app1",
                    100,
                    new HandoffOptions(true, true),
                    AppOpsManager.MODE_ALLOWED),
            new FakeTask(
                    2,
                    USER_ID,
                    "com.example.app2",
                    200,
                    new HandoffOptions(false, true),
                    AppOpsManager.MODE_ALLOWED),
            new FakeTask(
                    4,
                    1000,
                    "com.example.app4",
                    400,
                    new HandoffOptions(true, true),
                    AppOpsManager.MODE_IGNORED),
        };
        setupRunningTasks(tasks);
        mTaskBroadcaster.onTaskStackChanged();
        verify(mMockTaskContinuityMessenger, times(1))
                .sendMessage(eq(createExpectedTaskContinuityMessage(tasks[0])));
    }

    @Test
    public void testOnHandoffEnabledChanged_sendTaskStackBroadcastMessage() throws RemoteException {
        FakeTask[] tasks = {
            new FakeTask(
                    1,
                    USER_ID,
                    "com.example.app1",
                    100,
                    new HandoffOptions(true, true),
                    AppOpsManager.MODE_ALLOWED),
            new FakeTask(
                    2,
                    USER_ID,
                    "com.example.app2",
                    200,
                    new HandoffOptions(false, true),
                    AppOpsManager.MODE_ALLOWED),
            new FakeTask(
                    4,
                    1000,
                    "com.example.app4",
                    400,
                    new HandoffOptions(true, true),
                    AppOpsManager.MODE_IGNORED),
        };
        setupRunningTasks(tasks);
        mTaskBroadcaster.onHandoffEnabledChanged(100, true);
        verify(mMockTaskContinuityMessenger, times(1))
                .sendMessage(eq(createExpectedTaskContinuityMessage(tasks[0])));
    }

    @Test
    public void testOnHandoffEnabledChanged_appOpsIgnored_doesNotBroadcastTask()
            throws RemoteException {
        FakeTask[] tasks = {
            new FakeTask(
                    1,
                    USER_ID,
                    "com.example.app1",
                    100,
                    new HandoffOptions(true, true),
                    AppOpsManager.MODE_IGNORED),
        };
        setupRunningTasks(tasks);
        mTaskBroadcaster.onHandoffEnabledChanged(100, true);
        verify(mMockTaskContinuityMessenger, times(1))
                .sendMessage(eq(createExpectedTaskContinuityMessage()));
    }

    @Test
    public void testOnHandoffEnabledChanged_appOpsDefault_broadcastsTask() throws RemoteException {
        FakeTask[] tasks = {
            new FakeTask(
                    1,
                    USER_ID,
                    "com.example.app1",
                    100,
                    new HandoffOptions(true, true),
                    AppOpsManager.MODE_DEFAULT),
        };
        setupRunningTasks(tasks);
        mTaskBroadcaster.onHandoffEnabledChanged(100, true);
        verify(mMockTaskContinuityMessenger, times(1))
                .sendMessage(eq(createExpectedTaskContinuityMessage(tasks[0])));
    }

    @Test
    public void testOnHandoffEnabledChanged_appOpsAllowed_broadcastsTask() throws RemoteException {
        FakeTask[] tasks = {
            new FakeTask(
                    1,
                    USER_ID,
                    "com.example.app1",
                    100,
                    new HandoffOptions(true, true),
                    AppOpsManager.MODE_ALLOWED),
        };
        setupRunningTasks(tasks);
        mTaskBroadcaster.onHandoffEnabledChanged(100, true);
        verify(mMockTaskContinuityMessenger, times(1))
                .sendMessage(eq(createExpectedTaskContinuityMessage(tasks[0])));
    }

    private record FakeTask(
            int taskId,
            int userId,
            String packageName,
            long lastActiveTime,
            HandoffOptions handoffOptions,
            int continueAcrossDevicesOpMode) {

        public RemoteTaskInfo toRemoteTaskInfo() {
            return new RemoteTaskInfo(taskId, packageName, true, lastActiveTime, handoffOptions);
        }
    }

    private TaskContinuityMessage createExpectedTaskContinuityMessage(FakeTask... tasks) {
        TaskStackBroadcastMessage.Builder taskStackBroadcastMessageBuilder =
                new TaskStackBroadcastMessage.Builder();
        for (FakeTask task : tasks) {
            taskStackBroadcastMessageBuilder.addRemoteTask(task.toRemoteTaskInfo());
        }

        return new TaskContinuityMessage.Builder()
                .setTaskStackBroadcastMessage(taskStackBroadcastMessageBuilder.build())
                .build();
    }

    private void setupRunningTasks(FakeTask... tasks) {
        List<RunningTaskInfo> runningTaskInfos = new ArrayList<>();
        for (FakeTask task : tasks) {
            runningTaskInfos.add(setupTask(task));
        }

        doReturn(runningTaskInfos).when(mMockActivityTaskManager).getTasks(Integer.MAX_VALUE, true);
    }

    private RunningTaskInfo setupTask(FakeTask task) {
        RunningTaskInfo taskInfo = new RunningTaskInfo();
        taskInfo.taskId = task.taskId;
        taskInfo.userId = task.userId;
        taskInfo.baseActivity = new ComponentName(task.packageName, "com.example.app.MainActivity");
        taskInfo.lastActiveTime = task.lastActiveTime;
        taskInfo.isFocused = true;
        when(mMockActivityTaskManagerInternal.isHandoffEnabledForTask(task.taskId))
                .thenReturn(task.handoffOptions.isHandoffEnabled());
        when(mMockActivityTaskManagerInternal.getHandoffActivityParamsForTask(task.taskId))
                .thenReturn(
                        new HandoffActivityParams.Builder()
                                .setAllowHandoffWithoutPackageInstalled(
                                        !task.handoffOptions.requirePackageInstalled())
                                .build());
        int uid = 10000 + task.taskId;
        try {
            when(mMockPackageManager.getPackageUid(task.packageName, 0)).thenReturn(uid);
        } catch (NameNotFoundException e) {
            // Do nothing.
        }

        when(mMockAppOpsManager.checkOpNoThrow(
                        AppOpsManager.OP_CONTINUE_ACROSS_DEVICES, uid, task.packageName))
                .thenReturn(task.continueAcrossDevicesOpMode);
        return taskInfo;
    }
}
