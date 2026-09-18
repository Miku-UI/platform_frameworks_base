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

package androidx.window.extensions.layout;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.ArrayMap;
import android.util.Log;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Client for connecting to a service for getting engagement mode.
 *
 * This client discovers and binds to a system service that implements the
 * {@link #SERVICE_INTERFACE} action.
 */
class EngagementModeClientImpl implements EngagementModeClient {
    private static final String TAG = "EngagementModeClient";
    private static final String SERVICE_INTERFACE =
            "androidx.window.extensions.layout.EngagementModeService";
    @VisibleForTesting
    static final long INITIAL_RECONNECT_DELAY_MS = 10L;
    private static final long MAX_RECONNECT_DELAY_MS = 5000L;
    private static final int RECONNECT_MULTIPLIER = 10;

    private final Context mContext;
    private final Object mLock = new Object();

    @GuardedBy("mLock")
    private final ArrayMap<Consumer<Integer>, Executor> mUpdateCallbacks = new ArrayMap<>();

    @GuardedBy("mLock")
    @Nullable
    private IEngagementModeService mService = null;

    @GuardedBy("mLock")
    private boolean mIsBound = false;

    private final Handler mHandler;

    @GuardedBy("mLock")
    private long mReconnectDelayMs = INITIAL_RECONNECT_DELAY_MS;

    @GuardedBy("mLock")
    private int mCurrentEngagementModeFlags = DEFAULT_ENGAGEMENT_MODE;

    EngagementModeClientImpl(@NonNull Context context, @NonNull Handler handler) {
        mContext = context.getApplicationContext();
        mHandler = handler;
    }

    @Override
    public int getEngagementModeFlags() {
        synchronized (mLock) {
            return mCurrentEngagementModeFlags;
        }
    }

    @Override
    public void addUpdateCallback(@NonNull Executor executor, @NonNull Consumer<Integer> callback) {
        boolean shouldConnect = false;
        synchronized (mLock) {
            final boolean wasEmpty = mUpdateCallbacks.isEmpty();
            if (mUpdateCallbacks.put(callback, executor) == null && wasEmpty) {
                shouldConnect = true;
            }
        }

        if (shouldConnect) {
            connectToService(); // Connect to service outside of lock to prevent deadlock
        }
    }

    @Override
    public void removeUpdateCallback(@NonNull Consumer<Integer> callback) {
        synchronized (mLock) {
            if (mUpdateCallbacks.remove(callback) != null && mUpdateCallbacks.isEmpty()) {
                // Last listener is gone, cancel any pending reconnect attempts.
                mHandler.removeCallbacksAndMessages(null);

                if (mIsBound) {
                    mContext.unbindService(mConnection);
                    mService = null;
                    mIsBound = false;
                }
                // Reset to the default state. This prevents new listeners from getting a stale
                // value when they first register.
                mCurrentEngagementModeFlags = DEFAULT_ENGAGEMENT_MODE;
            }
        }
    }

    private void connectToService() {
        final Intent intent = new Intent(SERVICE_INTERFACE);
        final PackageManager pm = mContext.getPackageManager();
        final ResolveInfo resolveInfo =
                pm.resolveService(intent, PackageManager.MATCH_DEFAULT_ONLY);
        if (resolveInfo == null || resolveInfo.serviceInfo == null) {
            Log.i(TAG, "No EngagementModeService found");
            return;
        }
        final String packageName = resolveInfo.serviceInfo.packageName;
        if (!verifyProviderIsSystemApp(packageName)) {
            Log.e(TAG, "EngagementModeService is not a system app on " + packageName);
            return;
        }
        intent.setPackage(packageName);

        try {
            mContext.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException binding to EngagementModeService", e);
        }
    }

    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            synchronized (mLock) {
                // Check if all listeners were removed before binding is done. If so, unbind
                // immediately and clean up to prevent a leak.
                if (mUpdateCallbacks.isEmpty()) {
                    mContext.unbindService(mConnection);
                    mService = null;
                    mIsBound = false;
                    return;
                }

                // Set service as bound.
                mService = IEngagementModeService.Stub.asInterface(service);
                mIsBound = true;

                // Reset the reconnect delay on a successful connection.
                mReconnectDelayMs = INITIAL_RECONNECT_DELAY_MS;

                try {
                    mService.registerCallback(mCallback);
                } catch (RemoteException e) {
                    Log.e(TAG, "Error interacting with EngagementModeService on connect", e);
                    // If registration fails, the connection is useless. Clean up.
                    mService = null;
                    mIsBound = false;
                }
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            Map<Consumer<Integer>, Executor> callbacksToNotify = null;
            synchronized (mLock) {
                mService = null;
                mIsBound = false;

                // Revert to the default state on disconnect to ensure a safe, known value is
                // reported while the service is down.
                if (mCurrentEngagementModeFlags != DEFAULT_ENGAGEMENT_MODE) {
                    mCurrentEngagementModeFlags = DEFAULT_ENGAGEMENT_MODE;
                    callbacksToNotify = new ArrayMap<>(mUpdateCallbacks);
                }

                // Only attempt to reconnect if there are still active listeners. This prevents
                // zombie reconnects if all listeners have unregistered while the service was down.
                if (!mUpdateCallbacks.isEmpty()) {
                    Log.w(TAG, "EngagementModeService disconnected, scheduling reconnect in "
                            + mReconnectDelayMs + "ms");
                    // Service crashed or was killed. Try to reconnect after a delay.
                    mHandler.postDelayed(() -> connectToService(), mReconnectDelayMs);
                    // Increase the delay for the next attempt.
                    mReconnectDelayMs = Math.min(mReconnectDelayMs * RECONNECT_MULTIPLIER,
                            MAX_RECONNECT_DELAY_MS);
                }
            }
            if (callbacksToNotify != null) {
                callbacksToNotify.forEach((callback, executor) -> executor.execute(
                        () -> callback.accept(DEFAULT_ENGAGEMENT_MODE)));
            }
        }
    };

    private final IEngagementModeCallback.Stub mCallback = new IEngagementModeCallback.Stub() {
        @Override
        public void onEngagementModeChanged(int engagementModeFlags) {
            Map<Consumer<Integer>, Executor> callbacksToNotify;
            synchronized (mLock) {
                if (mCurrentEngagementModeFlags == engagementModeFlags) {
                    return;
                }
                mCurrentEngagementModeFlags = engagementModeFlags;
                callbacksToNotify = new ArrayMap<>(mUpdateCallbacks);
            }
            callbacksToNotify.forEach((callback, executor) -> executor.execute(
                    () -> callback.accept(engagementModeFlags)));
        }
    };

    private boolean verifyProviderIsSystemApp(String packageName) {
        try {
            final PackageManager pm = mContext.getPackageManager();
            final PackageInfo packageInfo = pm.getPackageInfo(packageName, 0);
            if ((packageInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                Log.e(TAG, "EngagementModeService must be a system app");
                return false;
            }
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Could not find package info for " + packageName, e);
            return false;
        }
    }
}
