/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.server.companion.datatransfer.crossdevicesync.feature.airplanemode;

import android.annotation.NonNull;
import android.content.Context;
import android.database.ContentObserver;
import android.net.ConnectivityManager;
import android.provider.Settings;
import android.util.Log;

import com.android.server.companion.datatransfer.crossdevicesync.common.DebugConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/** Implementation of {@link AirplaneModeController}. */
public class AirplaneModeControllerImpl implements AirplaneModeController {
    private static final String TAG = "AirplaneModeController";
    private static final boolean DEBUG = DebugConfig.DEBUG_FEATURE;

    private final Context mContext;
    private final List<Listener> mListeners = new ArrayList<>();
    private final ContentObserver mAirplaneModeContentObserver;
    private final ContentObserver mAirplaneModeSyncContentObserver;

    public AirplaneModeControllerImpl(Context context, Executor mainExecutor) {
        mContext = context;
        mAirplaneModeContentObserver =
                new ContentObserver(mainExecutor, /* unused= */ 0) {
                    @Override
                    public void onChange(boolean selfChange) {
                        boolean apmEnabled = isAirplaneModeEnabled();
                        logD("Local APM state changed to " + apmEnabled);
                        for (Listener listener : mListeners) {
                            listener.onAirplaneModeChanged(apmEnabled);
                        }
                    }
                };
        mAirplaneModeSyncContentObserver =
                new ContentObserver(mainExecutor, /* unused= */ 0) {
                    @Override
                    public void onChange(boolean selfChange) {
                        boolean apmSyncEnabled = isAirplaneModeSyncEnabled();
                        logD("Local APM sync enabled state changed to " + apmSyncEnabled);
                        for (Listener listener : mListeners) {
                            listener.onAirplaneModeSyncEnabledStateChanged(apmSyncEnabled);
                        }
                    }
                };
    }

    @Override
    public boolean isAirplaneModeEnabled() {
        return Settings.Global.getInt(
                        mContext.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0)
                == 1;
    }

    @Override
    public boolean isAirplaneModeSyncEnabled() {
        return Settings.Global.getInt(
                        mContext.getContentResolver(), Settings.Global.AIRPLANE_MODE_SYNC, 1)
                == 1;
    }

    @Override
    public void updateAirplaneModeState(boolean enabled) {
        mContext.getSystemService(ConnectivityManager.class).setAirplaneMode(enabled);
    }

    @Override
    public void registerAirplaneModeChangedListener(@NonNull Listener listener) {
        if (mListeners.isEmpty()) {
            mContext.getContentResolver()
                    .registerContentObserver(
                            Settings.Global.getUriFor(Settings.Global.AIRPLANE_MODE_ON),
                            /* notifyForDescendants= */ false,
                            mAirplaneModeContentObserver);
            mContext.getContentResolver()
                    .registerContentObserver(
                            Settings.Global.getUriFor(Settings.Global.AIRPLANE_MODE_SYNC),
                            /* notifyForDescendants= */ false,
                            mAirplaneModeSyncContentObserver);
        }
        mListeners.add(listener);
    }

    @Override
    public void unregisterAirplaneModeChangedListener(@NonNull Listener listener) {
        mListeners.remove(listener);
        if (mListeners.isEmpty()) {
            mContext.getContentResolver().unregisterContentObserver(mAirplaneModeContentObserver);
            mContext.getContentResolver()
                    .unregisterContentObserver(mAirplaneModeSyncContentObserver);
        }
    }

    private static void logD(String log) {
        if (DEBUG) {
            Log.d(TAG, log);
        }
    }
}
