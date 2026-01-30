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

package android.companion.datatransfer.continuity;

import android.companion.datatransfer.continuity.IHandoffRequestCallback;
import android.companion.datatransfer.continuity.IRemoteTaskListener;
import android.companion.datatransfer.continuity.RemoteTask;

/**
 * Interface for communication with the task continuity service.
 * @hide
 */
oneway interface ITaskContinuityManager {

    @EnforcePermission("READ_REMOTE_TASKS")
    void registerRemoteTaskListener(IRemoteTaskListener listener);

    @EnforcePermission("READ_REMOTE_TASKS")
    void unregisterRemoteTaskListener(IRemoteTaskListener listener);

    @EnforcePermission("REQUEST_TASK_HANDOFF")
    void requestHandoff(
        in int associationId,
        in int remoteTaskId,
        in IHandoffRequestCallback callback);

}
