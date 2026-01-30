/*
 * Copyright (C) 2020 The Android Open Source Project
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

import android.annotation.BinderThread;
import android.annotation.CallSuper;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.annotation.SuppressLint;
import android.annotation.TestApi;
import android.app.ActivityManager;
import android.os.IBinder;
import android.os.RemoteException;
import android.view.SurfaceControl;

import com.android.internal.annotations.VisibleForTesting;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * Interface for ActivityTaskManager/WindowManager to delegate control of tasks.
 * @hide
 */
@TestApi
public class TaskOrganizer extends WindowOrganizer {

    /**
     * Data associated with a request to create a new root task.
     * @hide
     */
    public static class CreateRootTaskRequest {
        public int displayId;
        public int windowingMode;
        public boolean removeWithTaskOrganizer;
        public boolean reparentOnDisplayRemoval;
        public @Nullable IBinder launchCookie;
        public @Nullable String name;

        public CreateRootTaskRequest setDisplayId(int displayId) {
            this.displayId = displayId;
            return this;
        }

        public CreateRootTaskRequest setWindowingMode(int windowingMode) {
            this.windowingMode = windowingMode;
            return this;
        }

        public CreateRootTaskRequest setRemoveWithTaskOrganizer(boolean removeWithTaskOrganizer) {
            this.removeWithTaskOrganizer = removeWithTaskOrganizer;
            return this;
        }

        public CreateRootTaskRequest setReparentOnDisplayRemoval(boolean reparentOnDisplayRemoval) {
            this.reparentOnDisplayRemoval = reparentOnDisplayRemoval;
            return this;
        }

        public CreateRootTaskRequest setLaunchCookie(@NonNull IBinder launchCookie) {
            this.launchCookie = launchCookie;
            return this;
        }

        public CreateRootTaskRequest setName(@NonNull String name) {
            this.name = name;
            return this;
        }
    }

    private final ITaskOrganizerController mTaskOrganizerController;
    // Callbacks WM Core are posted on this executor if it isn't null, otherwise direct calls are
    // made on the incoming binder call.
    private final Executor mExecutor;

    public TaskOrganizer() {
        this(null /*taskOrganizerController*/, null /*executor*/);
    }

    /** @hide */
    @VisibleForTesting
    public TaskOrganizer(ITaskOrganizerController taskOrganizerController, Executor executor) {
        mExecutor = executor != null ? executor : Runnable::run;
        mTaskOrganizerController = taskOrganizerController != null
                ? taskOrganizerController : getController();
    }

    /**
     * Register a TaskOrganizer to manage tasks as they enter a supported windowing mode.
     *
     * @return a list of the tasks that should be managed by the organizer, not including tasks
     *         created via {@link #createRootTask}.
     */
    @RequiresPermission(android.Manifest.permission.MANAGE_ACTIVITY_TASKS)
    @CallSuper
    @NonNull
    public List<TaskAppearedInfo> registerOrganizer() {
        try {
            return mTaskOrganizerController.registerTaskOrganizer(mInterface).getList();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /** Unregisters a previously registered task organizer. */
    @RequiresPermission(android.Manifest.permission.MANAGE_ACTIVITY_TASKS)
    @CallSuper
    public void unregisterOrganizer() {
        try {
            mTaskOrganizerController.unregisterTaskOrganizer(mInterface);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Called when a Task is starting and the system would like to show a UI to indicate that an
     * application is starting. The client is responsible to add/remove the starting window if it
     * has create a starting window for the Task.
     *
     * @param info The information about the Task that's available
     * @hide
     */
    @BinderThread
    public void addStartingWindow(@NonNull StartingWindowInfo info) {}

    /**
     * Called when the Task want to remove the starting window.
     * @param removalInfo The information used to remove the starting window.
     * @hide
     */
    @BinderThread
    public void removeStartingWindow(@NonNull StartingWindowRemovalInfo removalInfo) {}

    /**
     * Called when the Task want to copy the splash screen.
     */
    @BinderThread
    public void copySplashScreenView(int taskId) {}

    /**
     * Notify the shell ({@link com.android.wm.shell.ShellTaskOrganizer} that the client has
     * removed the splash screen view.
     * @see com.android.wm.shell.ShellTaskOrganizer#onAppSplashScreenViewRemoved(int)
     * @see SplashScreenView#remove()
     */
    @BinderThread
    public void onAppSplashScreenViewRemoved(int taskId) {
    }

    /**
     * Called when a task with the registered windowing mode can be controlled by this task
     * organizer. For non-root tasks, the leash may initially be hidden so it is up to the organizer
     * to show this task.
     */
    @BinderThread
    public void onTaskAppeared(@NonNull ActivityManager.RunningTaskInfo taskInfo,
            @NonNull SurfaceControl leash) {}

    @BinderThread
    public void onTaskVanished(@NonNull ActivityManager.RunningTaskInfo taskInfo) {}

    @BinderThread
    public void onTaskInfoChanged(@NonNull ActivityManager.RunningTaskInfo taskInfo) {}

    @BinderThread
    public void onBackPressedOnTaskRoot(@NonNull ActivityManager.RunningTaskInfo taskInfo) {}

    /** @hide */
    @BinderThread
    public void onImeDrawnOnTask(int taskId) {}

    /** @hide */
    @BinderThread
    public void onTransitionReady(@NonNull IBinder iBinder, @NonNull TransitionInfo transitionInfo,
            @NonNull SurfaceControl.Transaction t, @NonNull SurfaceControl.Transaction finishT) {}

    /** @hide */
    @BinderThread
    public void requestStartTransition(@NonNull IBinder iBinder,
            @NonNull TransitionRequestInfo request) {}

    /**
     * @deprecated Use {@link #createRootTask(CreateRootTaskRequest)}
     * @hide
     */
    @Deprecated
    @RequiresPermission(android.Manifest.permission.MANAGE_ACTIVITY_TASKS)
    public void createRootTask(int displayId, int windowingMode, @Nullable IBinder launchCookie,
            boolean removeWithTaskOrganizer, boolean reparentOnDisplayRemoval) {
        createRootTask(new CreateRootTaskRequest()
                        .setDisplayId(displayId)
                        .setWindowingMode(windowingMode)
                        .setLaunchCookie(launchCookie)
                        .setRemoveWithTaskOrganizer(removeWithTaskOrganizer)
                        .setReparentOnDisplayRemoval(reparentOnDisplayRemoval));
    }

    /**
     * Creates a persistent root task in WM for a particular windowing-mode.
     * This call is deprecated, use {@link #createRootTask(CreateRootTaskRequest)}.
     */
    @RequiresPermission(android.Manifest.permission.MANAGE_ACTIVITY_TASKS)
    @Nullable
    public void createRootTask(int displayId, int windowingMode, @Nullable IBinder launchCookie) {
        // TODO(b/378565144): Deprecate this method and expose CreateRootTaskRequest as TestApi
        createRootTask(new CreateRootTaskRequest()
                .setDisplayId(displayId)
                .setWindowingMode(windowingMode)
                .setLaunchCookie(launchCookie));
    }

    /**
     * Creates a persistent root task in WM for a particular windowing-mode.
     * @param request The data for this request
     *
     * @hide
     */
    @RequiresPermission(android.Manifest.permission.MANAGE_ACTIVITY_TASKS)
    public void createRootTask(@NonNull CreateRootTaskRequest request) {
        try {
            mTaskOrganizerController.createRootTask(request.displayId, request.windowingMode,
                    request.launchCookie, request.removeWithTaskOrganizer,
                    request.reparentOnDisplayRemoval, request.name);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /** Deletes a persistent root task in WM */
    @RequiresPermission(android.Manifest.permission.MANAGE_ACTIVITY_TASKS)
    public boolean deleteRootTask(@NonNull WindowContainerToken task) {
        try {
            return mTaskOrganizerController.deleteRootTask(task);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /** Gets direct child tasks (ordered from top-to-bottom) */
    @RequiresPermission(android.Manifest.permission.MANAGE_ACTIVITY_TASKS)
    @Nullable
    @SuppressLint("NullableCollection")
    public List<ActivityManager.RunningTaskInfo> getChildTasks(
            @NonNull WindowContainerToken parent, @NonNull int[] activityTypes) {
        try {
            return mTaskOrganizerController.getChildTasks(parent, activityTypes);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /** Gets all root tasks on a display (ordered from top-to-bottom) */
    @RequiresPermission(android.Manifest.permission.MANAGE_ACTIVITY_TASKS)
    @Nullable
    @SuppressLint("NullableCollection")
    public List<ActivityManager.RunningTaskInfo> getRootTasks(
            int displayId, @NonNull int[] activityTypes) {
        try {
            return mTaskOrganizerController.getRootTasks(displayId, activityTypes);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Get the {@link WindowContainerToken} of the task which contains the current IME layering
     * target.
     *
     * @param displayId the ID of the display to get the IME layering target for.
     */
    @RequiresPermission(android.Manifest.permission.MANAGE_ACTIVITY_TASKS)
    @Nullable
    public WindowContainerToken getImeLayeringTarget(int displayId) {
        try {
            return mTaskOrganizerController.getImeLayeringTarget(displayId);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Callers should use {@link
     * WindowContainerTransaction#setInterceptBackPressedOnTaskRoot(WindowContainerToken, boolean)}.
     */
    @RequiresPermission(android.Manifest.permission.MANAGE_ACTIVITY_TASKS)
    public void setInterceptBackPressedOnTaskRoot(@NonNull WindowContainerToken task,
            boolean interceptBackPressed) {
        final WindowContainerTransaction wct = new WindowContainerTransaction();
        wct.setInterceptBackPressedOnTaskRoot(task, interceptBackPressed);
        applyTransaction(wct);
    }


    /**
     * Restarts the top activity in the given task by killing its process if it is visible.
     * @hide
     */
    @RequiresPermission(android.Manifest.permission.MANAGE_ACTIVITY_TASKS)
    public void restartTaskTopActivityProcessIfVisible(@NonNull WindowContainerToken task) {
        try {
            mTaskOrganizerController.restartTaskTopActivityProcessIfVisible(task);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Set layers to be excluded when taking a task snapshot.
     *
     * Warning: MUST NOT pass layers that are managed by the Window Manager (e.g., from a Task or
     * Activity). Doing so may cause the corresponding layer to be destroyed when
     * clearExcludeLayersFromTaskSnapshot is called.
     * @hide
     */
    @RequiresPermission(android.Manifest.permission.MANAGE_ACTIVITY_TASKS)
    public void setExcludeLayersFromTaskSnapshot(@NonNull WindowContainerToken task,
            SurfaceControl[] layers) {
        try {
            mTaskOrganizerController.setExcludeLayersFromTaskSnapshot(task, layers);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Clears all layers that were set for exclusion via setExcludeLayersFromTaskSnapshot.
     * @hide
     */
    @RequiresPermission(android.Manifest.permission.MANAGE_ACTIVITY_TASKS)
    public void clearExcludeLayersFromTaskSnapshot(@NonNull WindowContainerToken task) {
        try {
            mTaskOrganizerController.clearExcludeLayersFromTaskSnapshot(task);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Gets the executor to run callbacks on.
     * @hide
     */
    @NonNull
    public Executor getExecutor() {
        return mExecutor;
    }

    private final ITaskOrganizer mInterface = new ITaskOrganizer.Stub() {
        @Override
        public void addStartingWindow(StartingWindowInfo windowInfo) {
            mExecutor.execute(() -> TaskOrganizer.this.addStartingWindow(windowInfo));
        }

        @Override
        public void removeStartingWindow(StartingWindowRemovalInfo removalInfo) {
            mExecutor.execute(() -> TaskOrganizer.this.removeStartingWindow(removalInfo));
        }

        @Override
        public void copySplashScreenView(int taskId)  {
            mExecutor.execute(() -> TaskOrganizer.this.copySplashScreenView(taskId));
        }

        @Override
        public void onAppSplashScreenViewRemoved(int taskId) {
            mExecutor.execute(() -> TaskOrganizer.this.onAppSplashScreenViewRemoved(taskId));
        }

        @Override
        public void onTaskAppeared(ActivityManager.RunningTaskInfo taskInfo, SurfaceControl leash) {
            mExecutor.execute(() -> TaskOrganizer.this.onTaskAppeared(taskInfo, leash));
        }

        @Override
        public void onTaskVanished(ActivityManager.RunningTaskInfo taskInfo) {
            mExecutor.execute(() -> TaskOrganizer.this.onTaskVanished(taskInfo));
        }

        @Override
        public void onTaskInfoChanged(ActivityManager.RunningTaskInfo info) {
            mExecutor.execute(() -> TaskOrganizer.this.onTaskInfoChanged(info));
        }

        @Override
        public void onBackPressedOnTaskRoot(ActivityManager.RunningTaskInfo info) {
            mExecutor.execute(() -> TaskOrganizer.this.onBackPressedOnTaskRoot(info));
        }

        @Override
        public void onImeDrawnOnTask(int taskId) {
            mExecutor.execute(() -> TaskOrganizer.this.onImeDrawnOnTask(taskId));
        }

        @Override
        public void onTransitionReady(IBinder iBinder, TransitionInfo transitionInfo,
                SurfaceControl.Transaction t, SurfaceControl.Transaction finishT) {
            mExecutor.execute(() -> TaskOrganizer.this.onTransitionReady(
                    iBinder, transitionInfo, t, finishT));
        }

        @Override
        public void requestStartTransition(IBinder iBinder, TransitionRequestInfo request) {
            mExecutor.execute(() -> TaskOrganizer.this.requestStartTransition(iBinder, request));
        }
    };

    @RequiresPermission(android.Manifest.permission.MANAGE_ACTIVITY_TASKS)
    private ITaskOrganizerController getController() {
        try {
            return getWindowOrganizerController().getTaskOrganizerController();
        } catch (RemoteException e) {
            return null;
        }
    }
}
