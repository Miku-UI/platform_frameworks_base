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

package com.android.server.companion.datatransfer.crossdevicesync.common;

import static android.provider.Settings.Global.Wearable.TETHERED_CONFIG_RESTRICTED;
import static android.provider.Settings.Global.Wearable.TETHERED_CONFIG_STANDALONE;
import static android.provider.Settings.Global.Wearable.TETHERED_CONFIG_UNKNOWN;
import static android.provider.Settings.Global.Wearable.TETHER_CONFIG_STATE;

import android.content.Context;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.provider.Settings;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/** Implementation of {@link DeviceUtils}. */
public class DeviceUtilsImpl implements DeviceUtils {
    private static final String TAG = "WatchChecker";
    private static final boolean DEBUG = DebugConfig.DEBUG_COMMON;

    private final Context mContext;
    private final boolean mIsWatch;
    private final List<Listener> mListeners = new ArrayList<>();
    private final ContentObserver mContentObserver;

    public DeviceUtilsImpl(Context context, Executor mainExecutor) {
        mContext = context;
        mIsWatch = mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH);
        mContentObserver =
                new ContentObserver(mainExecutor, /* unused= */ 0) {
                    @Override
                    public void onChange(boolean selfChange) {
                        boolean isKids = isKidsWatch();
                        if (DEBUG) {
                            Log.d(TAG, "Kids watch state changed to " + isKids);
                        }
                        for (Listener listener : mListeners) {
                            listener.onKidsWatchChanged(isKids);
                        }
                    }
                };
    }

    @Override
    public boolean isWatch() {
        return mIsWatch;
    }

    @Override
    public boolean isKidsWatch() {
        if (!mIsWatch) {
            return false;
        }
        int config =
                Settings.Global.getInt(
                        mContext.getContentResolver(),
                        TETHER_CONFIG_STATE,
                        TETHERED_CONFIG_UNKNOWN);
        return config == TETHERED_CONFIG_RESTRICTED || config == TETHERED_CONFIG_STANDALONE;
    }

    @Override
    public void registerKidsWatchChangeListener(Listener listener) {
        if (!mIsWatch) {
            return;
        }
        if (mListeners.isEmpty()) {
            mContext.getContentResolver()
                    .registerContentObserver(
                            Settings.Global.getUriFor(TETHER_CONFIG_STATE),
                            /* notifyForDescendants= */ false,
                            mContentObserver);
        }
        mListeners.add(listener);
    }

    @Override
    public void unregisterKidsWatchChangeListener(Listener listener) {
        if (!mIsWatch) {
            return;
        }
        mListeners.remove(listener);
        if (mListeners.isEmpty()) {
            mContext.getContentResolver().unregisterContentObserver(mContentObserver);
        }
    }
}
