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

package android.window;

import android.window.ITaskSnapshotListener;

/**
 * @hide
 */
interface ITaskSnapshotManager {
    /**
     * Fetches the snapshot for the task with the given id.
     *
     * @param taskId the id of the task to retrieve for
     * @param latestCaptureTime the elapsed time of the latest taskSnapshot captured
     * @param retrieveResolution the resolution we want to load.
     *
     * @throws RemoteException
     * @return a graphic buffer representing a screenshot of a task, it returns {@code null} if no
     *         screenshot can be found, but if latestCaptureTime is equals or greater than 0, then
     *         the client should reuse the existing snapshot.
     */
    android.window.TaskSnapshot getTaskSnapshot(int taskId, long latestCaptureTime,
            int retrieveResolution);

    /**
     * Requests for a new snapshot to be taken for the task with the given id, storing it in the
     * task snapshot cache only if requested.
     *
     * @param taskId the id of the task to take a snapshot of
     * @param updateCache Whether to store the new snapshot in the system's task snapshot cache.
     *                    If it is true, the snapshot can be either real content or app-theme mode
     *                    depending on the attributes of app. Otherwise, the snapshot will be taken
     *                    with real content.
     * @param lowResolution Whether to get the new snapshot in low resolution.
     * @throws RemoteException
     * @return a graphic buffer representing a screenshot of a task, or {@code null} if no
     *         corresponding task can be found.
     */
    android.window.TaskSnapshot takeTaskSnapshot(int taskId, boolean updateCache,
            boolean lowResolution);

    void registerTaskSnapshotListener(in ITaskSnapshotListener listener);
    void unregisterTaskSnapshotListener(in ITaskSnapshotListener listener);
}