/**
 * Copyright (c) 2020, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.window;

import android.app.ActivityManager;
import android.content.pm.ParceledListSlice;
import android.window.ITaskOrganizer;
import android.window.TaskAppearedInfo;
import android.window.WindowContainerToken;
import android.window.WindowContainerTransaction;
import android.view.SurfaceControl;

/** @hide */
interface ITaskOrganizerController {

    /**
     * Register a TaskOrganizer to manage all the tasks with supported windowing modes.
     *
     * @return a list of the tasks that should be managed by the organizer, not including tasks
     *         created via {@link #createRootTask}.
     */
    ParceledListSlice<TaskAppearedInfo> registerTaskOrganizer(ITaskOrganizer organizer);

    /**
     * Unregisters a previously registered task organizer.
     */
    void unregisterTaskOrganizer(ITaskOrganizer organizer);

    /**
    * Creates a persistent root task in WM for a particular windowing-mode.
    *
    * It may be removed using {@link #deleteRootTask} or through
    * {@link WindowContainerTransaction#removeRootTask}.
    */
    void createRootTask(int displayId, int windowingMode, IBinder launchCookie,
            boolean removeWithTaskOrganizer, boolean reparentOnDisplayRemoval,
            in @nullable String name);

    /** Deletes a persistent root task in WM */
    boolean deleteRootTask(in WindowContainerToken task);

    /** Gets direct child tasks (ordered from top-to-bottom) */
    List<ActivityManager.RunningTaskInfo> getChildTasks(in WindowContainerToken parent,
            in int[] activityTypes);

    /** Gets all root tasks on a display (ordered from top-to-bottom) */
    List<ActivityManager.RunningTaskInfo> getRootTasks(int displayId, in int[] activityTypes);

    /**
     * Get the {@link WindowContainerToken} of the task which contains the current IME layering
     * target
     */
    @nullable WindowContainerToken getImeLayeringTarget(int display);

    /**
     * Restarts the top activity in the given task by killing its process if it is visible.
     */
    void restartTaskTopActivityProcessIfVisible(in WindowContainerToken task);

    /**
     * Set layers to be excluded when taking a task snapshot.
     *
     * Warning: MUST NOT pass layers that are managed by the Window Manager (e.g., from a Task or
     * Activity). Doing so may cause the corresponding layer to be destroyed when
     * clearExcludeLayersFromTaskSnapshot is called.
     */
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(value = "
            + "android.Manifest.permission.MANAGE_ACTIVITY_TASKS)")
    void setExcludeLayersFromTaskSnapshot(in WindowContainerToken task,
            in SurfaceControl[] layers);

    /**
     * Clears all layers that were registered for exclusion via setExcludeLayersFromTaskSnapshot.
     */
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(value = "
            + "android.Manifest.permission.MANAGE_ACTIVITY_TASKS)")
    void clearExcludeLayersFromTaskSnapshot(in WindowContainerToken task);
}
