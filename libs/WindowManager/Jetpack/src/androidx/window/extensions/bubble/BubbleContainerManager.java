/*
 * Copyright 2025 The Android Open Source Project
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

package androidx.window.extensions.bubble;

import android.Manifest;
import android.annotation.CallbackExecutor;
import android.annotation.RequiresPermission;
import android.app.ActivityTaskManager;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.window.IMultitaskingController;
import android.window.IMultitaskingControllerCallback;
import android.window.IMultitaskingDelegate;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * The interface that apps use to request control over Bubbles - special windowing feature that
 * allows intermittent multi-tasking. The supported operations include abilities to create,
 * expand/collapse, update flyout message or remove the containers.
 */
public class BubbleContainerManager {
    private static final String TAG = BubbleContainerManager.class.getSimpleName();

    private static BubbleContainerManager sInstance;

    // Interface used to communicate with the controller in the server
    private IMultitaskingDelegate mControllerInterface;

    // Callback used to update the client about changes from the controller in the server
    @NonNull
    private final ControllerCallback mControllerCallback = new ControllerCallback();

    // Callback into the application code
    @NonNull
    private final List<CallbackRecord> mAppCallbacks = new ArrayList<>();

    /**
     * Obtain an instance of the class to make requests related to Bubbles system multi-tasking
     * feature control.
     * @return a new instance of an interface if it's available, {@code null} otherwise.
     */
    @RequiresPermission(Manifest.permission.REQUEST_SYSTEM_MULTITASKING_CONTROLS)
    @Nullable
    public static BubbleContainerManager getInstance() {
        if (!com.android.window.flags.Flags.enableExperimentalBubblesController()) {
            Log.d(TAG, "ExperimentalBubblesController flag is off, "
                    + "returning null BubbleContainerManager.");
            return null;
        }
        if (sInstance != null) {
            return sInstance;
        }
        try {
            final IMultitaskingController controller = ActivityTaskManager.getService()
                    .getWindowOrganizerController().getMultitaskingController();
            final BubbleContainerManager manager = new BubbleContainerManager();
            final IMultitaskingDelegate controllerInterface = controller.getClientInterface(
                    manager.mControllerCallback);
            Objects.requireNonNull(controllerInterface);
            sInstance = manager;
            sInstance.mControllerInterface = controllerInterface;
            return sInstance;
        } catch (RemoteException e) {
            Log.e(TAG, "Remote exception getting an instance of BubbleContainerManager",
                    new RuntimeException(e));
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Exception getting an instance of BubbleContainerManager", e);
            return null;
        }
    }

    /**
     * Request to create a new Bubble.
     * @param token a token uniquely identifying a bubble.
     * @param intent an Intent used to launch an Activity into a Bubble.
     * @param collapsed initial Bubble state.
     */
    public void createBubble(IBinder token, Intent intent, boolean collapsed) {
        try {
            mControllerInterface.createBubble(token, intent, collapsed);
        } catch (RemoteException e) {
            Log.e(TAG, "Remote exception creating a Bubble", new RuntimeException(e));
        }
    }

    /**
     * Update the state of an existing Bubble to either collapse or expand it.
     * @param token a token uniquely identifying a bubble.
     * @param collapsed new Bubble state.
     */
    public void updateBubbleState(IBinder token, boolean collapsed) {
        try {
            mControllerInterface.updateBubbleState(token, collapsed);
        } catch (RemoteException e) {
            Log.e(TAG, "Remote exception updating Bubble state", new RuntimeException(e));
        }
    }

    /**
     * Update the flyout message for an existing Bubble.
     * @param token a token uniquely identifying a bubble.
     * @param message new Bubble message.
     */
    public void updateBubbleMessage(IBinder token, String message) {
        try {
            mControllerInterface.updateBubbleMessage(token, message);
        } catch (RemoteException e) {
            Log.e(TAG, "Remote exception updating Bubble message", new RuntimeException(e));
        }
    }

    /**
     * Remove an existing Bubble.
     * @param token a token uniquely identifying a bubble.
     */
    public void removeBubble(IBinder token) {
        try {
            mControllerInterface.removeBubble(token);
        } catch (RemoteException e) {
            Log.e(TAG, "Remote exception removing Bubble", new RuntimeException(e));
        }
    }

    /**
     * Registers a callback to get notified about changes to the managed bubbles.
     */
    public void registerBubbleContainerCallback(@NonNull @CallbackExecutor Executor executor,
            @NonNull BubbleContainerCallback callback) {
        Objects.requireNonNull(executor);
        Objects.requireNonNull(callback);
        for (CallbackRecord record : mAppCallbacks) {
            if (record.mCallback.equals(callback)) {
                throw new IllegalArgumentException("Callback already registered");
            }
        }
        mAppCallbacks.add(new CallbackRecord(executor, callback));
    }

    /**
     * Unregisters a callback that was previously registered.
     * @see #registerBubbleContainerCallback(Executor, BubbleContainerCallback)
     */
    public void unregisterBubbleContainerCallback(@NonNull BubbleContainerCallback callback) {
        for (CallbackRecord record : mAppCallbacks) {
            if (record.mCallback.equals(callback)) {
                mAppCallbacks.remove(record);
                return;
            }
        }
        Log.e(TAG, "Didn't find a matching callback to remove");
    }

    private static class CallbackRecord {
        final Executor mExecutor;
        final BubbleContainerCallback mCallback;

        CallbackRecord(@NonNull Executor executor, @NonNull BubbleContainerCallback callback) {
            mExecutor = executor;
            mCallback = callback;
        }
    }

    private class ControllerCallback extends IMultitaskingControllerCallback.Stub {
        @Override
        public void onBubbleRemoved(IBinder token) {
            for (CallbackRecord callbackRecord : mAppCallbacks) {
                callbackRecord.mExecutor.execute(() -> {
                    callbackRecord.mCallback.onBubbleRemoved(token);
                });
            }
        }
    }
}
