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

package com.android.server.appbinding;

import android.annotation.NonNull;
import android.app.supervision.flags.Flags;
import android.content.ComponentName;
import android.content.Context;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.IBinder;
import android.os.IInterface;

import com.android.internal.annotations.GuardedBy;
import com.android.server.am.PersistentConnection;
import com.android.server.appbinding.finders.AppServiceFinder;
import com.android.server.utils.Slogf;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Queue;
import java.util.function.Consumer;

/**
 * Establishes a persistent connection to a given service component for a given user
 * Each connection is associated with an AppServiceFinder to facilitate the service exchange if the
 * default app changes.
 */
public class AppServiceConnection extends PersistentConnection<IInterface> {
    public static final String TAG = "AppServiceConnection";
    private final AppBindingConstants mConstants;
    private final AppServiceFinder mFinder;
    private final String mPackageName;
    private final ConditionVariable mConditionVariable = new ConditionVariable();
    private final Handler mHandler;
    @GuardedBy("mLock")
    private final Queue<Consumer<AppServiceConnection>> mCallbacks = new ArrayDeque<>();
    private final Object mLock = new Object();

    AppServiceConnection(Context context, int userId, AppBindingConstants constants,
            Handler handler, AppServiceFinder finder, String packageName,
            @NonNull ComponentName componentName) {
        super(TAG, context, handler, userId, componentName,
                constants.SERVICE_RECONNECT_BACKOFF_SEC,
                constants.SERVICE_RECONNECT_BACKOFF_INCREASE,
                constants.SERVICE_RECONNECT_MAX_BACKOFF_SEC,
                constants.SERVICE_STABLE_CONNECTION_THRESHOLD_SEC);
        mFinder = finder;
        mConstants = constants;
        mPackageName = packageName;
        mHandler = handler;
    }

    @Override
    protected int getBindFlags() {
        return mFinder.getBindFlags(mConstants);
    }

    @Override
    protected IInterface asInterface(IBinder obj) {
        final IInterface service = mFinder.asInterface(obj);
        if (service == null) {
            Slogf.w(TAG, "Service for %s is null.", getComponentName());
        }
        return service;
    }

    @Override
    protected void onConnected(@NonNull IInterface service) {
        if (Flags.enableAppServiceConnectionCallback()) {
            scheduleCallbacks();
        } else {
            mConditionVariable.open();
        }
    }

    @Override
    protected void onDisconnected() {
        if (!Flags.enableAppServiceConnectionCallback()) {
            mConditionVariable.close();
        }
    }

    public AppServiceFinder getFinder() {
        return mFinder;
    }

    public String getPackageName() {
        return mPackageName;
    }

    /**
     * Adds a callback to the queue and tries to schedule it immediately
     *
     * @param callback The action to be executed
     */
    public void addCallback(Consumer<AppServiceConnection> callback) {
        if (Flags.enableAppServiceConnectionCallback()) {
            synchronized (mLock) {
                mCallbacks.add(callback);
            }
            scheduleCallbacks();
        }
    }

    /**
     * Schedules all callbacks in the queue to be executed in the background if the connection is
     * already established
     */
    private void scheduleCallbacks() {
        if (isConnected()) {
            ArrayList<Consumer<AppServiceConnection>> callbacks;
            synchronized (mLock) {
                callbacks = new ArrayList<>(mCallbacks);
                mCallbacks.clear();
            }

            for (Consumer<AppServiceConnection> action : callbacks) {
                mHandler.post(() -> {
                    action.accept(this);
                });
            }
        }
    }

    /**
     * Establishes the service connection and blocks until the service is connected
     * or a timeout occurs.
     *
     * @return true if the service connected successfully within the timeout, false otherwise.
     */
    public boolean awaitConnection() {
        if (!Flags.enableAppServiceConnectionCallback()) {
            long timeoutMs = mConstants.SERVICE_RECONNECT_MAX_BACKOFF_SEC * 10 * 1000L;
            return mConditionVariable.block(timeoutMs) && isConnected();
        }
        return isConnected();
    }
}
