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

import android.annotation.FlaggedApi;
import android.annotation.IntDef;
import android.annotation.RequiresPermission;
import android.annotation.SystemApi;
import android.annotation.SystemService;
import android.annotation.NonNull;
import android.content.Context;
import android.os.RemoteException;
import android.util.ArrayMap;

import com.android.internal.annotations.GuardedBy;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.concurrent.Executor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * This class facilitates task continuity between devices owned by the same user.
 * This includes synchronizing lists of open tasks between a user's devices, as well as requesting
 * to hand off a task from one device to another. Handing a task off to a device will resume the
 * application on the receiving device, preserving the state of the task.
 *
 * @hide
 */
@FlaggedApi(android.companion.Flags.FLAG_ENABLE_TASK_CONTINUITY)
@SystemService(Context.TASK_CONTINUITY_SERVICE)
@SystemApi
public class TaskContinuityManager {
    private final Context mContext;
    private final ITaskContinuityManager mService;

    private final RemoteTaskListenerHolder mListenerHolder;

    /** @hide */
    @IntDef(prefix = {"HANDOFF_REQUEST_RESULT"}, value = {
        HANDOFF_REQUEST_RESULT_SUCCESS,
        HANDOFF_REQUEST_RESULT_FAILURE_TASK_NOT_FOUND,
        HANDOFF_REQUEST_RESULT_FAILURE_NO_DATA_PROVIDED_BY_TASK,
        HANDOFF_REQUEST_RESULT_FAILURE_SENDER_LOST_CONNECTION,
        HANDOFF_REQUEST_RESULT_FAILURE_TIMEOUT,
        HANDOFF_REQUEST_RESULT_FAILURE_DEVICE_NOT_FOUND,
    })

    @Retention(RetentionPolicy.SOURCE)
    public @interface HandoffRequestResultCode {}

    /**
     * Indicate a request for handoff completed successfully.
     */
    public static final int HANDOFF_REQUEST_RESULT_SUCCESS = 0;

    /**
     * Indicates a request for handoff failed because a remote task with the specified ID was not
     * found on the remote device.
     */
    public static final int HANDOFF_REQUEST_RESULT_FAILURE_TASK_NOT_FOUND = 1;

    /**
     * Indicates a request for handoff failed because the remote task did not provide any data to
     * hand itself off to the current device.
     */
    public static final int HANDOFF_REQUEST_RESULT_FAILURE_NO_DATA_PROVIDED_BY_TASK = 2;

    /**
     * Indicates a request for handoff failed because the connection to the remote device was lost
     * before the request could be completed.
     */
    public static final int HANDOFF_REQUEST_RESULT_FAILURE_SENDER_LOST_CONNECTION = 3;

    /**
     * Indicates a request for handoff failed because the request timed out before it could be
     * completed.
     */
    public static final int HANDOFF_REQUEST_RESULT_FAILURE_TIMEOUT = 4;

    /**
     * Indicates a request for handoff failed because the remote device was not found.
     */
    public static final int HANDOFF_REQUEST_RESULT_FAILURE_DEVICE_NOT_FOUND = 5;

    /**
     * Indicates a request for handoff failed because of an internal error outside of Handoff's data
     * transfer flow.
     */
    public static final int HANDOFF_REQUEST_RESULT_FAILURE_OTHER_INTERNAL_ERROR = 6;

    /** @hide */
    public TaskContinuityManager(
        @NonNull Context context,
        @NonNull ITaskContinuityManager service) {

        mContext = context;
        mService = service;
        mListenerHolder = new RemoteTaskListenerHolder(service);
    }

    /**
     * Listener to be notified when the list of remote tasks changes.
    */
    public interface RemoteTaskListener {
        /**
         * Invoked when the list of remote tasks changes.
         *
         * @param remoteTasks The list of remote tasks.
         */
        void onRemoteTasksChanged(@NonNull List<RemoteTask> remoteTasks);
    }

    /**
     * Callback to be invoked when a handoff request is completed.
     */
    public interface HandoffRequestCallback {

        /**
         * Invoked when a request to hand off a remote task has finished.
         *
         * @param associationId The ID of the association to which the remote device is connected.
         * @param remoteTaskId The ID of the task that was requested to be handed off.
         * @param resultCode The result code of the handoff request.
         */
        void onHandoffRequestFinished(
            int associationId,
            int remoteTaskId,
            @HandoffRequestResultCode int resultCode);
    }

    /**
     * Registers a listener to be notified when the list of remote tasks changes.
     *
     * @param executor The executor to be used to invoke the listener.
     * @param listener The listener to be registered.
     * @throws SecurityException if the caller does not hold the
     *      {@link android.Manifest.permission#READ_REMOTE_TASKS} permission.
     */
    @RequiresPermission(android.Manifest.permission.READ_REMOTE_TASKS)
    public void registerRemoteTaskListener(
        @NonNull Executor executor,
        @NonNull RemoteTaskListener listener) {

        Objects.requireNonNull(executor);
        Objects.requireNonNull(listener);

        try {
            mListenerHolder.registerListener(executor, listener);
            // TODO: joeantonetti - Send an initial notification to the listener after it's
            // attached.
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Unregisters a listener previously registered with
     * {@link #registerRemoteTaskListener}.
     *
     * @param listener The listener to be unregistered.
     * @throws SecurityException if the caller does not hold the
     *      {@link android.Manifest.permission#READ_REMOTE_TASKS} permission.
     */
    @RequiresPermission(android.Manifest.permission.READ_REMOTE_TASKS)
    public void unregisterRemoteTaskListener(@NonNull RemoteTaskListener listener) {
        Objects.requireNonNull(listener);

        try {
            mListenerHolder.unregisterListener(listener);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Requests a handoff of the specified remote task to the current device.
     *
     * @param associationId The ID of the association to which the remote device is connected. This
     *                      is the same ID returned by {@link RemoteTask#getDeviceId()}.
     * @param remoteTaskId The remote task to hand off.
     * @param executor The executor to be used to invoke the callback.
     * @param callback The callback to be invoked when the handoff request is finished.
     * @throws SecurityException if the caller does not hold the
     *      {@link android.Manifest.permission#REQUEST_TASK_HANDOFF} permission.
     */
    @RequiresPermission(android.Manifest.permission.REQUEST_TASK_HANDOFF)
    public void requestHandoff(
        int associationId,
        int remoteTaskId,
        @NonNull Executor executor,
        @NonNull HandoffRequestCallback callback) {

        Objects.requireNonNull(executor);
        Objects.requireNonNull(callback);

        try {
            HandoffRequestCallbackHolder callbackHolder
                = new HandoffRequestCallbackHolder(executor, callback);

            mService.requestHandoff(associationId, remoteTaskId, callbackHolder);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    private final class HandoffRequestCallbackHolder extends IHandoffRequestCallback.Stub {
        private final Executor mExecutor;
        private final HandoffRequestCallback mCallback;

        HandoffRequestCallbackHolder(
            @NonNull Executor executor,
            @NonNull HandoffRequestCallback callback) {

            mExecutor = executor;
            mCallback = callback;
        }

        @Override
        public void onHandoffRequestFinished(
            int associationId,
            int remoteTaskId,
            @HandoffRequestResultCode int resultCode) throws RemoteException {
            mExecutor.execute(
                () -> mCallback.onHandoffRequestFinished(associationId, remoteTaskId, resultCode));
        }
    }

    /**
     * Helper class which manages registered listeners and proxies them behind a single
     * IRemoteTaskListener, which is lazily registered with ITaskContinuityManager if there is
     * a single registered listener.
     */
    private final class RemoteTaskListenerHolder extends IRemoteTaskListener.Stub {

        @GuardedBy("mListeners")
        private final Map<RemoteTaskListener, Executor> mListeners = new ArrayMap<>();

        @GuardedBy("mListeners")
        private boolean mRegistered = false;

        @GuardedBy("mListeners")
        private final List<RemoteTask> mLastReceivedRemoteTasks = new ArrayList<>();

        public RemoteTaskListenerHolder(ITaskContinuityManager service) {}

        /**
         * Registers a listener to be notified of remote task changes.
         *
         * @param executor The executor on which the listener should be invoked.
         * @param listener The listener to register.
         */
        public void registerListener(
            @NonNull Executor executor,
            @NonNull RemoteTaskListener listener) throws RemoteException {

            Objects.requireNonNull(executor);
            Objects.requireNonNull(listener);

            synchronized(mListeners) {
                if (!mRegistered) {
                    mService.registerRemoteTaskListener(this);
                    mRegistered = true;
                } else {
                    executor.execute(() ->
                        listener.onRemoteTasksChanged(mLastReceivedRemoteTasks)
                    );
                }

                mListeners.put(listener, executor);
            }
        }

        /**
         * Unregisters a previously registered listener.
         *
         * @param listener The listener to unregister.
         */
        public void unregisterListener(
            @NonNull RemoteTaskListener listener) throws RemoteException {

            Objects.requireNonNull(listener);

            synchronized(mListeners) {
                mListeners.remove(listener);
                if (mListeners.isEmpty() && mRegistered) {
                    mRegistered = false;
                    mService.unregisterRemoteTaskListener(this);
                }
            }
        }

        @Override
        public void onRemoteTasksChanged(List<RemoteTask> remoteTasks) throws RemoteException {
            synchronized(mListeners) {
                mLastReceivedRemoteTasks.clear();
                mLastReceivedRemoteTasks.addAll(remoteTasks);

                for (Map.Entry<RemoteTaskListener, Executor> entry : mListeners.entrySet()) {
                    RemoteTaskListener listener = entry.getKey();
                    Executor executor = entry.getValue();
                    executor.execute(() -> listener.onRemoteTasksChanged(remoteTasks));
                }
            }
        }
    }
}
