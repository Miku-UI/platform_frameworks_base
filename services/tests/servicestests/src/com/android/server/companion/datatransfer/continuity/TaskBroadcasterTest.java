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

package com.android.server.companion.datatransfer.continuity;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.eq;

import android.app.ActivityManager.RunningTaskInfo;
import android.app.ActivityTaskManager;
import android.companion.datatransfer.continuity.RemoteTask;
import android.os.RemoteException;
import android.platform.test.annotations.Presubmit;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import com.android.server.companion.datatransfer.continuity.connectivity.TaskContinuityMessenger;

import com.android.server.companion.datatransfer.continuity.messages.ContinuityDeviceConnected;
import com.android.server.companion.datatransfer.continuity.messages.RemoteTaskAddedMessage;
import com.android.server.companion.datatransfer.continuity.messages.RemoteTaskInfo;
import com.android.server.companion.datatransfer.continuity.messages.RemoteTaskRemovedMessage;
import com.android.server.companion.datatransfer.continuity.messages.RemoteTaskUpdatedMessage;
import com.android.server.companion.datatransfer.continuity.tasks.RunningTaskFetcher;
import com.android.server.wm.ActivityTaskManagerInternal;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

@Presubmit
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper(setAsMainLooper = true)
public class TaskBroadcasterTest {

    @Mock private ActivityTaskManager mMockActivityTaskManager;
    @Mock private ActivityTaskManagerInternal mMockActivityTaskManagerInternal;
    @Mock private RunningTaskFetcher mMockRunningTaskFetcher;
    @Mock private TaskContinuityMessenger mMockTaskContinuityMessenger;

    private TaskBroadcaster mTaskBroadcaster;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mTaskBroadcaster = new TaskBroadcaster(
            mMockTaskContinuityMessenger,
            mMockActivityTaskManager,
            mMockActivityTaskManagerInternal,
            mMockRunningTaskFetcher);
    }

    @Test
    public void testOnAllDevicesDisconnected_doesNothingIfNoDeviceConnected() {
        mTaskBroadcaster.onAllDevicesDisconnected();
        verify(mMockActivityTaskManager, never()).registerTaskStackListener(mTaskBroadcaster);
        verify(mMockActivityTaskManagerInternal, never())
            .registerHandoffEnablementListener(mTaskBroadcaster);
    }

    @Test
    public void testOnAllDevicesDisconnected_unregistersListener() {
        // Connect a device, verify the listener is registered.
        mTaskBroadcaster.onDeviceConnected(1);
        verify(mMockActivityTaskManager, times(1)).registerTaskStackListener(mTaskBroadcaster);
        verify(mMockActivityTaskManagerInternal, times(1))
            .registerHandoffEnablementListener(mTaskBroadcaster);

        // Disconnect all devices, verify the listener is unregistered.
        mTaskBroadcaster.onAllDevicesDisconnected();
        verify(mMockActivityTaskManager, times(1)).unregisterTaskStackListener(mTaskBroadcaster);
        verify(mMockActivityTaskManagerInternal, times(1))
            .unregisterHandoffEnablementListener(mTaskBroadcaster);
    }

    @Test
    public void testOnDeviceConnected_sendsMessageToDevice() throws RemoteException {
        RemoteTaskInfo expectedRemoteTaskInfo = new RemoteTaskInfo(
            100 /* taskId */,
            "test" /* label */,
            100 /* lastActiveTime */,
            new byte[0] /* icon */,
            true /* isHandoffEnabled */);
        when(mMockRunningTaskFetcher.getRunningTasks())
            .thenReturn(List.of(expectedRemoteTaskInfo));

        int associationId = 1;
        mTaskBroadcaster.onDeviceConnected(associationId);

        // Verify the message is sent.
        ContinuityDeviceConnected expectedMessage
            = new ContinuityDeviceConnected(List.of(expectedRemoteTaskInfo));
        verify(mMockTaskContinuityMessenger, times(1)).sendMessage(
            eq(associationId),
            eq(expectedMessage));

        // Verify a listener was registered.
        verify(mMockActivityTaskManager, times(1)).registerTaskStackListener(mTaskBroadcaster);
    }

    @Test
    public void testOnTaskCreated_sendsMessageToAllAssociations() throws RemoteException {
        RemoteTaskInfo expectedRemoteTaskInfo = new RemoteTaskInfo(
            123 /* taskId */,
            "newTask" /* label */,
            0 /* lastActiveTime */,
            new byte[0] /* icon */,
            true /* isHandoffEnabled */);
        when(mMockRunningTaskFetcher.getRunningTaskById(expectedRemoteTaskInfo.id()))
            .thenReturn(expectedRemoteTaskInfo);

        // Notify TaskBroadcaster of the new task.
        mTaskBroadcaster.onTaskCreated(expectedRemoteTaskInfo.id(), null);

        // Verify sendMessage is called
        RemoteTaskAddedMessage expectedMessage = new RemoteTaskAddedMessage(expectedRemoteTaskInfo);
        verify(mMockTaskContinuityMessenger, times(1)).sendMessage(eq(expectedMessage));
    }

    @Test
    public void testOnTaskCreated_taskNotFound_doesNotSendMessage() throws RemoteException {
        int taskId = 123;
        when(mMockRunningTaskFetcher.getRunningTaskById(taskId))
            .thenReturn(null);

        mTaskBroadcaster.onTaskCreated(taskId, null);

        verify(mMockTaskContinuityMessenger, never()).sendMessage(any());
    }

    @Test
    public void testOnTaskRemoved_sendsMessageToAllAssociations() throws RemoteException {
        int taskId = 123;

        mTaskBroadcaster.onTaskRemoved(taskId);

        // Verify sendMessage is called
        RemoteTaskRemovedMessage expectedMessage = new RemoteTaskRemovedMessage(taskId);
        verify(mMockTaskContinuityMessenger, times(1)).sendMessage(eq(expectedMessage));
    }

    @Test
    public void testOnHandoffEnabledChanged_sendsMessageToAllAssociations() throws RemoteException {
        RemoteTaskInfo expectedRemoteTaskInfo = new RemoteTaskInfo(
            1 /* taskId */,
            "task" /* label */,
            100 /* lastActiveTime */,
            new byte[0] /* icon */,
            true /* isHandoffEnabled */);
        when(mMockRunningTaskFetcher.getRunningTaskById(expectedRemoteTaskInfo.id()))
            .thenReturn(expectedRemoteTaskInfo);
        RunningTaskInfo taskInfo = new RunningTaskInfo();
        taskInfo.taskId = expectedRemoteTaskInfo.id();

        mTaskBroadcaster.onHandoffEnabledChanged(expectedRemoteTaskInfo.id(), true);

        // Verify sendMessage is called for each association.
        RemoteTaskUpdatedMessage expectedMessage
            = new RemoteTaskUpdatedMessage(expectedRemoteTaskInfo);
       verify(mMockTaskContinuityMessenger, times(1)).sendMessage(eq(expectedMessage));
    }

    @Test
    public void testOnTaskMovedToFront_sendsMessageToAllAssociations() throws RemoteException {
        RemoteTaskInfo expectedRemoteTaskInfo = new RemoteTaskInfo(
            1 /* taskId */,
            "task" /* label */,
            100 /* lastActiveTime */,
            new byte[0] /* icon */,
            true /* isHandoffEnabled */);
        when(mMockRunningTaskFetcher.getRunningTaskById(expectedRemoteTaskInfo.id()))
            .thenReturn(expectedRemoteTaskInfo);
        RunningTaskInfo taskInfo = new RunningTaskInfo();
        taskInfo.taskId = expectedRemoteTaskInfo.id();

        mTaskBroadcaster.onTaskMovedToFront(taskInfo);

        // Verify sendMessage is called for each association.
        RemoteTaskUpdatedMessage expectedMessage
            = new RemoteTaskUpdatedMessage(expectedRemoteTaskInfo);
       verify(mMockTaskContinuityMessenger, times(1)).sendMessage(eq(expectedMessage));
    }

    @Test
    public void testOnTaskMovedToFront_taskNotFound_doesNotSendMessage() throws RemoteException {
        RunningTaskInfo taskInfo = new RunningTaskInfo();
        taskInfo.taskId = 123;
        when(mMockRunningTaskFetcher.getRunningTaskById(taskInfo.taskId))
            .thenReturn(null);

        mTaskBroadcaster.onTaskMovedToFront(taskInfo);

        verify(mMockTaskContinuityMessenger, never()).sendMessage(any());
    }
}