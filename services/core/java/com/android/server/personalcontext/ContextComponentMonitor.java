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

package com.android.server.personalcontext;

import android.content.Context;
import android.os.Looper;
import android.os.UserHandle;
import android.util.Log;
import android.util.Slog;

import com.android.internal.content.PackageMonitor;

import java.util.concurrent.Executor;

/**
 * Monitors packages that are installed, uninstalled, and modified for re-registering components.
 * @hide
 */
final class ContextComponentMonitor extends PackageMonitor {
    private static final String TAG = "ContextComponentMonitor";

    private final ContextComponentManager mComponentManager;
    private final Executor mExecutor;

    private boolean mRegistered;

    ContextComponentMonitor(ContextComponentManager componentManager, Executor executor) {
        mComponentManager = componentManager;
        mExecutor = executor;
    }

    @Override
    public void register(Context context, Looper thread, UserHandle user,
            boolean externalStorage) {
        super.register(context, thread, user, externalStorage);
        mRegistered = true;
    }

    @Override
    public void unregister() {
        if (!mRegistered) {
            return;
        }
        super.unregister();
    }

    @Override
    public boolean onPackageChanged(String packageName, int uid, String[] components) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Slog.d(TAG, "Package " + packageName + " changed, reregistering components");
        }

        mExecutor.execute(() -> {
            mComponentManager.unregisterComponentsForPackage(packageName);
            mComponentManager.registerComponentsForPackage(packageName);
        });

        return false;
    }

    @Override
    public void onPackageUpdateFinished(String packageName, int uid) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Slog.d(TAG, "Package " + packageName + " updated, reregistering components");
        }

        mExecutor.execute(() -> {
            mComponentManager.unregisterComponentsForPackage(packageName);
            mComponentManager.registerComponentsForPackage(packageName);
        });
    }

    @Override
    public void onPackageAdded(String packageName, int uid) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Slog.d(TAG, "Package " + packageName + " added, registering components");
        }

        mExecutor.execute(() -> {
            mComponentManager.registerComponentsForPackage(packageName);
        });
    }

    @Override
    public void onPackageRemoved(String packageName, int uid) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Slog.d(TAG, "Package " + packageName + " removed, unregistering components");
        }

        mExecutor.execute(() -> {
            mComponentManager.unregisterComponentsForPackage(packageName);
        });
    }
}
