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

import android.companion.datatransfer.continuity.RemoteTask;
import android.platform.test.annotations.Presubmit;
import android.testing.AndroidTestingRunner;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;

@Presubmit
@RunWith(AndroidTestingRunner.class)
public class RemoteTaskListenerHolderTest {

    private RemoteTaskListenerHolder mRemoteTaskListenerHolder = new RemoteTaskListenerHolder();

    @Test
    public void addListener_notifiesWithEmptyListIfNoTasks() {
        FakeRemoteTaskListener listener = new FakeRemoteTaskListener();
        mRemoteTaskListenerHolder.addListener(listener);
        listener.verifyListenerEvents(List.of());
    }

    @Test
    public void addListener_notifiesWithLastBroadcastedTasks() {
        List<RemoteTask> tasks = List.of(createRemoteTask(1, 1));
        FakeRemoteTaskListener listener = new FakeRemoteTaskListener();
        mRemoteTaskListenerHolder.notifyAllRemoteTasksChanged(tasks);
        mRemoteTaskListenerHolder.addListener(listener);
        listener.verifyListenerEvents(tasks);
    }

    @Test
    public void notifyAllRemoteTasksChanged_notifiesAllListeners() {
        List<RemoteTask> tasks = List.of(createRemoteTask(1, 1));
        FakeRemoteTaskListener listener1 = new FakeRemoteTaskListener();
        FakeRemoteTaskListener listener2 = new FakeRemoteTaskListener();
        mRemoteTaskListenerHolder.addListener(listener1);
        mRemoteTaskListenerHolder.addListener(listener2);
        mRemoteTaskListenerHolder.notifyAllRemoteTasksChanged(tasks);
        listener1.verifyListenerEvents(List.of(), tasks);
        listener2.verifyListenerEvents(List.of(), tasks);
    }

    @Test
    public void
            notifyRemoteTasksChangedForAssociation_replacesTaskForAssociationAndNotifiesAllListeners() {
        int associationId1 = 1;
        int associationId2 = 2;
        List<RemoteTask> firstAssociationTasks = List.of(createRemoteTask(associationId1, 1));
        List<RemoteTask> secondAssociationTasks = List.of(createRemoteTask(associationId2, 1));
        List<RemoteTask> initialTasks = new ArrayList<>();
        initialTasks.addAll(firstAssociationTasks);
        initialTasks.addAll(secondAssociationTasks);
        FakeRemoteTaskListener listener1 = new FakeRemoteTaskListener();
        FakeRemoteTaskListener listener2 = new FakeRemoteTaskListener();
        mRemoteTaskListenerHolder.addListener(listener1);
        mRemoteTaskListenerHolder.addListener(listener2);
        mRemoteTaskListenerHolder.notifyAllRemoteTasksChanged(initialTasks);
        firstAssociationTasks =
                List.of(createRemoteTask(associationId1, 2), createRemoteTask(associationId1, 3));
        mRemoteTaskListenerHolder.notifyRemoteTasksChangedForAssociation(
                associationId1, firstAssociationTasks);
        List<RemoteTask> updatedTasks = new ArrayList<>();
        updatedTasks.addAll(firstAssociationTasks);
        updatedTasks.addAll(secondAssociationTasks);
        listener1.verifyListenerEvents(List.of(), initialTasks, updatedTasks);
        listener2.verifyListenerEvents(List.of(), initialTasks, updatedTasks);
    }

    @Test
    public void removeListener_doesNotNotifyRemovedListener() {
        List<RemoteTask> tasks = List.of(createRemoteTask(1, 1));
        FakeRemoteTaskListener listener1 = new FakeRemoteTaskListener();
        FakeRemoteTaskListener listener2 = new FakeRemoteTaskListener();
        mRemoteTaskListenerHolder.addListener(listener1);
        mRemoteTaskListenerHolder.addListener(listener2);
        mRemoteTaskListenerHolder.removeListener(listener1);
        mRemoteTaskListenerHolder.notifyAllRemoteTasksChanged(tasks);
        listener1.verifyListenerEvents(List.of());
        listener2.verifyListenerEvents(List.of(), List.of(createRemoteTask(1, 1)));
    }

    @Test
    public void notifyTaskHandedOff_removesTaskAndNotifiesAllListeners() {
        List<RemoteTask> tasks = List.of(createRemoteTask(1, 1), createRemoteTask(2, 1));
        FakeRemoteTaskListener listener1 = new FakeRemoteTaskListener();
        FakeRemoteTaskListener listener2 = new FakeRemoteTaskListener();
        mRemoteTaskListenerHolder.addListener(listener1);
        mRemoteTaskListenerHolder.addListener(listener2);
        mRemoteTaskListenerHolder.notifyAllRemoteTasksChanged(tasks);
        mRemoteTaskListenerHolder.notifyTaskHandedOff(1, 1);
        listener1.verifyListenerEvents(List.of(), tasks, List.of(createRemoteTask(2, 1)));
        listener2.verifyListenerEvents(List.of(), tasks, List.of(createRemoteTask(2, 1)));
    }

    @Test
    public void notifyTaskHandedOff_doesNotNotifyIfTaskNotFound() {
        List<RemoteTask> tasks = List.of(createRemoteTask(1, 1), createRemoteTask(2, 1));
        FakeRemoteTaskListener listener1 = new FakeRemoteTaskListener();
        FakeRemoteTaskListener listener2 = new FakeRemoteTaskListener();
        mRemoteTaskListenerHolder.addListener(listener1);
        mRemoteTaskListenerHolder.addListener(listener2);
        mRemoteTaskListenerHolder.notifyAllRemoteTasksChanged(tasks);
        mRemoteTaskListenerHolder.notifyTaskHandedOff(1, 2);
        listener1.verifyListenerEvents(List.of(), tasks);
        listener2.verifyListenerEvents(List.of(), tasks);
    }

    private RemoteTask createRemoteTask(int associationId, int taskId) {
        return new RemoteTask.Builder(associationId, taskId).build();
    }
}
