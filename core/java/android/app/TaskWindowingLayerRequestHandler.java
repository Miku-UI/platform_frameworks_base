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
package android.app;

import static com.android.internal.annotations.VisibleForTesting.Visibility.PACKAGE;

import android.annotation.CallbackExecutor;
import android.annotation.NonNull;
import android.os.Bundle;
import android.os.IRemoteCallback;
import android.os.OutcomeReceiver;
import android.os.RemoteException;

import com.android.internal.annotations.VisibleForTesting;

import java.util.concurrent.Executor;

/**
 * Class to encapsulate handling {@link ActivityManager.AppTask#requestWindowingLayer} requests and
 * related utilities.
 * @hide
 */
public class TaskWindowingLayerRequestHandler {


    /**
     * The key used for specifying the final result of a windowing layer request in the
     * {@link android.os.Bundle} returned by the server.
     */
    public static final String REMOTE_CALLBACK_RESULT_KEY = "result";

    /**
     * The request had been approved.
     *
     * <p>
     * The task layer has been changed accordingly to the request.
     */
    public static final int RESULT_APPROVED = 0;

    /**
     * The request has been rejected because the windowing system was in an inappropriate state to
     * handle the request.
     */
    public static final int RESULT_FAILED_BAD_STATE = 1;

    /**
     * Requests the windowing layer via {@link IAppTask}.
     */
    @VisibleForTesting(visibility = PACKAGE)
    public static void requestWindowingLayer(
            @ActivityManager.AppTask.WindowingLayer int layer,
            @NonNull @CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<Void, Exception> callback,
            @NonNull IAppTask appTaskImpl) {
        try {
            appTaskImpl.requestWindowingLayer(layer, createRemoteCallback(executor, callback));
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    private static IRemoteCallback createRemoteCallback(
            @NonNull @CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<Void, Exception> callback
    ) {
        return new IRemoteCallback.Stub() {
            @Override
            public void sendResult(Bundle data) {
                final int result = data.getInt(REMOTE_CALLBACK_RESULT_KEY);
                switch (result) {
                    case RESULT_APPROVED:
                        executor.execute(() -> callback.onResult(null));
                        break;
                    case RESULT_FAILED_BAD_STATE:
                        executor.execute(() -> callback.onError(new IllegalStateException(
                                "The current system windowing state is not appropriate to fulfill"
                                        + " the request.")));
                        break;
                    default:
                        executor.execute(() -> callback.onError(new IllegalStateException(
                                "Unknown error, code=" + result)));
                        break;
                }
            }
        };
    }
}
