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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

import android.companion.datatransfer.continuity.RemoteTask;
import android.companion.datatransfer.continuity.IRemoteTaskListener;
import android.platform.test.annotations.Presubmit;
import android.testing.AndroidTestingRunner;

import com.android.server.companion.datatransfer.continuity.messages.RemoteTaskInfo;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Presubmit
@RunWith(AndroidTestingRunner.class)
public class RemoteTaskStoreTest {

    private class FakeRemoteTaskListener extends IRemoteTaskListener.Stub {
        List<List<RemoteTask>> remoteTasksReportedToListener = new ArrayList<>();

        @Override
        public void onRemoteTasksChanged(List<RemoteTask> remoteTasks) {
            remoteTasksReportedToListener.add(remoteTasks);
        }

        public void verifyReportedTasks(List<List<RemoteTask>> expectedTasks) {
            assertThat(remoteTasksReportedToListener).hasSize(expectedTasks.size());
            for (int i = 0; i < expectedTasks.size(); i++) {
                assertThat(remoteTasksReportedToListener.get(i))
                    .containsExactlyElementsIn(expectedTasks.get(i));
            }
        }
    }

    private RemoteTaskStore taskStore;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        taskStore = new RemoteTaskStore();
    }

    @Test
    public void addListener_notifiesListenerOfCurrentTasks() {
        FakeRemoteTaskListener listener = new FakeRemoteTaskListener();
        taskStore.addListener(listener);
        listener.verifyReportedTasks(List.of(Collections.emptyList()));
    }

    @Test
    public void addDevice_addsDeviceAndNotifiesListeners() {
        FakeRemoteTaskListener listener = new FakeRemoteTaskListener();
        taskStore.addListener(listener);

        // Simulate a new association being connected.
        int deviceId = 1;
        String deviceName = "name";
        taskStore.addDevice(deviceId, deviceName);
        listener.verifyReportedTasks(List.of(Collections.emptyList()));

        // Add tasks to the new association.
        RemoteTaskInfo remoteTaskInfo = new RemoteTaskInfo(1, "task1", 100L, new byte[0], true);
        RemoteTask remoteTask = remoteTaskInfo.toRemoteTask(deviceId, deviceName);

        taskStore.setTasks(deviceId, Collections.singletonList(remoteTaskInfo));
        listener.verifyReportedTasks(List.of(Collections.emptyList(), List.of(remoteTask)));

        // Verify the most recent task is added to the task store.
        assertThat(taskStore.getMostRecentTasks()).containsExactly(remoteTask);
    }

    @Test
    public void setTasks_doesNotSetIfDeviceNotAdded() {
        FakeRemoteTaskListener listener = new FakeRemoteTaskListener();
        taskStore.addListener(listener);

        RemoteTaskInfo remoteTaskInfo = new RemoteTaskInfo(1, "task1", 100L, new byte[0], true);

        // Add the task. Since ConnectedAssociationStore does not have this
        // association, this should be ignored.
        taskStore.setTasks(0, Collections.singletonList(remoteTaskInfo));

        assertThat(taskStore.getMostRecentTasks()).isEmpty();
        listener.verifyReportedTasks(List.of(Collections.emptyList()));
    }

    @Test
    public void removeTask_removesTask() {
        FakeRemoteTaskListener listener = new FakeRemoteTaskListener();
        taskStore.addListener(listener);

        // Setup an association.
        int deviceId = 1;
        String deviceName = "name";
        taskStore.addDevice(deviceId, deviceName);

        // Add two tasks
        RemoteTaskInfo mostRecentTaskInfo = new RemoteTaskInfo(1, "task1", 200, new byte[0], true);
        RemoteTask mostRecentTask = mostRecentTaskInfo.toRemoteTask(deviceId, deviceName);
        RemoteTaskInfo secondMostRecentTaskInfo
            = new RemoteTaskInfo(2, "task2", 100,  new byte[0], true);
        RemoteTask secondMostRecentTask
            = secondMostRecentTaskInfo.toRemoteTask(deviceId, deviceName);
        taskStore.setTasks(
            deviceId,
            Arrays.asList(mostRecentTaskInfo, secondMostRecentTaskInfo));

        assertThat(taskStore.getMostRecentTasks()).containsExactly(mostRecentTask);
        listener.verifyReportedTasks(List.of(Collections.emptyList(), List.of(mostRecentTask)));

        taskStore.removeTask(deviceId, mostRecentTaskInfo.id());
        listener.verifyReportedTasks(
            List.of(
                Collections.emptyList(),
                List.of(mostRecentTask),
                List.of(secondMostRecentTask)));
        assertThat(taskStore.getMostRecentTasks()).containsExactly(secondMostRecentTask);
    }

    @Test
    public void removeDevice_removesDeviceAndNotifiesListeners() {
        FakeRemoteTaskListener listener = new FakeRemoteTaskListener();
        taskStore.addListener(listener);

        // Create a fake association info, and have connected association store
        // return it.
        int deviceId = 1;
        String deviceName = "name";
        taskStore.addDevice(deviceId, deviceName);

        // Set tasks for the association.
        RemoteTaskInfo remoteTaskInfo = new RemoteTaskInfo(1, "task1", 100L, new byte[0], true);
        RemoteTask remoteTask = remoteTaskInfo.toRemoteTask(deviceId, deviceName);
        taskStore.setTasks(deviceId, Collections.singletonList(remoteTaskInfo));
        listener.verifyReportedTasks(List.of(Collections.emptyList(), List.of(remoteTask)));

        // Simulate the association being disconnected.
        taskStore.removeDevice(deviceId);

        // Verify the most recent task is added to the task store.
        assertThat(taskStore.getMostRecentTasks()).isEmpty();
        listener.verifyReportedTasks(
            List.of(Collections.emptyList(), List.of(remoteTask), Collections.emptyList()));
    }

    @Test
    public void addTask_addsTaskToAssociationAndNotifiesListeners() {
        FakeRemoteTaskListener listener = new FakeRemoteTaskListener();
        taskStore.addListener(listener);

        // Create a fake association info, and have connected association store return it.
        int deviceId = 1;
        String deviceName = "name";
        taskStore.addDevice(deviceId, deviceName);

        RemoteTaskInfo remoteTaskInfo = new RemoteTaskInfo(1, "task1", 100L, new byte[0], true);
        RemoteTask remoteTask = remoteTaskInfo.toRemoteTask(deviceId, deviceName);
        taskStore.setTasks(deviceId, Collections.singletonList(remoteTaskInfo));
        assertThat(taskStore.getMostRecentTasks()).containsExactly(remoteTask);
        listener.verifyReportedTasks(List.of(Collections.emptyList(), List.of(remoteTask)));

        // Add a new task to the association.
        RemoteTaskInfo newRemoteTaskInfo = new RemoteTaskInfo(2, "task2", 200L, new byte[0], true);
        RemoteTask newRemoteTask = newRemoteTaskInfo.toRemoteTask(deviceId, deviceName);
        taskStore.addTask(deviceId, newRemoteTaskInfo);

        // Verify the most recent tasks are added to the task store.
        assertThat(taskStore.getMostRecentTasks()).containsExactly(newRemoteTask);
        listener.verifyReportedTasks(
            List.of(
                Collections.emptyList(),
                List.of(remoteTask),
                List.of(newRemoteTask)));
    }

    @Test
    public void addTask_doesNotAddTaskIfDeviceNotAdded() {
        RemoteTaskInfo remoteTaskInfo = new RemoteTaskInfo(1, "task1", 100L, new byte[0], true);
        taskStore.addTask(1, remoteTaskInfo);
        assertThat(taskStore.getMostRecentTasks()).isEmpty();
    }

    @Test
    public void updateTask_updatesTaskAndNotifiesListeners() {
        FakeRemoteTaskListener listener = new FakeRemoteTaskListener();
        taskStore.addListener(listener);

        // Create a fake association info, and have connected association store return it.
        int deviceId = 1;
        String deviceName = "name";
        taskStore.addDevice(deviceId, deviceName);

        RemoteTaskInfo initialTaskInfo = new RemoteTaskInfo(1, "task1", 100L, new byte[0], true);
        RemoteTask initialTask = initialTaskInfo.toRemoteTask(deviceId, deviceName);
        taskStore.setTasks(deviceId, Collections.singletonList(initialTaskInfo));
        assertThat(taskStore.getMostRecentTasks()).containsExactly(initialTask);
        listener.verifyReportedTasks(List.of(Collections.emptyList(), List.of(initialTask)));

        RemoteTaskInfo updatedTaskInfo = new RemoteTaskInfo(
            initialTaskInfo.id(),
            "task1",
            200L,
            new byte[0],
            true);

        RemoteTask updatedTask = updatedTaskInfo.toRemoteTask(deviceId, deviceName);
        taskStore.updateTask(deviceId, updatedTaskInfo);
        assertThat(taskStore.getMostRecentTasks()).containsExactly(updatedTask);
        listener.verifyReportedTasks(
            List.of(Collections.emptyList(), List.of(initialTask), List.of(updatedTask)));
    }
}