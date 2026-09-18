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
package com.android.server.companion.datatransfer.crossdevicesync.common;

import android.app.modes.ContextualMode;
import android.app.modes.ContextualModeManager;
import android.app.modes.ContextualModeManager.ContextualModeListener;
import android.app.modes.ContextualModesMutation;
import android.content.Context;
import android.os.UserHandle;
import android.util.Log;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** Default implementation of {@link ContextualModeManagerProxy}. */
public class ContextualModeManagerProxyImpl implements ContextualModeManagerProxy {
    private static final String TAG = "CtxModeManagerProxy";

    private final ContextualModeManager mContextualModeManager;

    public ContextualModeManagerProxyImpl(Context context) {
        mContextualModeManager = context.getSystemService(ContextualModeManager.class);
    }

    @Override
    public boolean isModeSyncSupported() {
        return mContextualModeManager.isModeSyncSupported();
    }

    @Override
    public boolean isModeSyncEnabled(UserHandle userHandle) {
        try {
            return mContextualModeManager.isModeSyncEnabled(userHandle);
        } catch (Exception e) {
            // Mode manager will throw exception if the user doesn't exist. Catch it here instead
            // of crashing the app.
            Log.e(TAG, "Failed to check mode sync enabled status.", e);
            return false;
        }
    }

    @Override
    public void setModeSyncEnabled(UserHandle userHandle, boolean enabled) {
        try {
            mContextualModeManager.setModeSyncEnabled(userHandle, enabled);
        } catch (Exception e) {
            // Mode manager will throw exception if the user doesn't exist. Catch it here instead
            // of crashing the app.
            Log.e(TAG, "Failed to set mode sync enabled status.", e);
        }
    }

    @Override
    public List<ContextualMode> getModes(UserHandle userHandle) {
        try {
            return mContextualModeManager.getModes(userHandle);
        } catch (Exception e) {
            // Mode manager will throw exception if the user doesn't exist. Catch it here instead
            // of crashing the app.
            Log.e(TAG, "Failed to get contextual modes.", e);
            return List.of();
        }
    }

    @Override
    public void mutateModes(UserHandle userHandle, ContextualModesMutation mutation) {
        try {
            mContextualModeManager.mutateModes(userHandle, mutation);
        } catch (Exception e) {
            // Mode manager will throw exception if the user doesn't exist. Catch it here instead
            // of crashing the app.
            Log.e(TAG, "Failed to mutate contextual modes.", e);
        }
    }

    @Override
    public void registerModeListener(
            UserHandle userHandle, Executor executor, ContextualModeListener listener) {
        try {
            mContextualModeManager.registerModeListener(userHandle, executor, listener);
        } catch (Exception e) {
            // Mode manager will throw exception if the user doesn't exist. Catch it here instead
            // of crashing the app.
            Log.e(TAG, "Failed to register mode listener.", e);
        }
    }

    @Override
    public void unregisterModeListener(ContextualModeListener listener) {
        mContextualModeManager.unregisterModeListener(listener);
    }

    @Override
    public void registerModeSyncEnabledListener(
            UserHandle userHandle, Executor executor, Consumer<Boolean> listener) {
        try {
            mContextualModeManager.registerModeSyncEnabledListener(userHandle, executor, listener);
        } catch (Exception e) {
            // Mode manager will throw exception if the user doesn't exist. Catch it here instead
            // of crashing the app.
            Log.e(TAG, "Failed to register mode sync enabled listener.", e);
        }
    }

    @Override
    public void unregisterModeSyncEnabledListener(Consumer<Boolean> listener) {
        mContextualModeManager.unregisterModeSyncEnabledListener(listener);
    }
}
