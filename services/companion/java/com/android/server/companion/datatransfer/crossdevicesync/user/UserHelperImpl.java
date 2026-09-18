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
package com.android.server.companion.datatransfer.crossdevicesync.user;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.UserInfo;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;
import android.util.Pair;

import com.android.internal.annotations.GuardedBy;
import com.android.server.companion.datatransfer.crossdevicesync.common.DebugConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/** Default implementation of {@link UserHelper}. */
public class UserHelperImpl implements UserHelper {
    private static final String TAG = "UserHelper";
    private static final boolean DEBUG = DebugConfig.DEBUG_COMMON;
    private final Object mLock = new Object();
    private final Context mContext;
    private final UserManager mUserManager;

    @GuardedBy("mLock")
    private final List<Pair<Executor, UserListener>> mListeners = new ArrayList<>();

    private final BroadcastReceiver mReceiver =
            new BroadcastReceiver() {

                @Override
                public void onReceive(Context context, Intent intent) {
                    synchronized (mLock) {
                        String action = intent.getAction();
                        if (Intent.ACTION_USER_ADDED.equals(action)) {
                            UserHandle user =
                                    intent.getParcelableExtra(Intent.EXTRA_USER, UserHandle.class);
                            if (user == null) {
                                Log.e(TAG, "Received null user handle in ACTION_USER_ADDED");
                                return;
                            }
                            mListeners.forEach(
                                    pair ->
                                            pair.first.execute(
                                                    () -> pair.second.onUserAdded(user)));
                        } else if (Intent.ACTION_USER_REMOVED.equals(action)) {
                            UserHandle user =
                                    intent.getParcelableExtra(Intent.EXTRA_USER, UserHandle.class);
                            if (user == null) {
                                Log.e(TAG, "Received null user handle in ACTION_USER_REMOVED");
                                return;
                            }
                            mListeners.forEach(
                                    pair ->
                                            pair.first.execute(
                                                    () -> pair.second.onUserRemoved(user)));
                        }
                    }
                }
            };

    public UserHelperImpl(Context context) {
        mContext = context;
        mUserManager = context.getSystemService(UserManager.class);
    }

    @Override
    public List<UserInfo> getAliveUsers() {
        return mUserManager.getAliveUsers();
    }

    @Override
    public void registerUserListener(Executor executor, UserListener listener) {
        synchronized (mLock) {
            boolean wasEmpty = mListeners.isEmpty();
            mListeners.add(new Pair<>(executor, listener));
            if (wasEmpty) {
                registerBroadcastReceiver();
                if (DEBUG) {
                    Log.d(TAG, "Registered user broadcast receiver.");
                }
            }
        }
    }

    @Override
    public void unregisterUserListener(UserListener listener) {
        synchronized (mLock) {
            if (mListeners.isEmpty()) {
                return;
            }
            mListeners.removeIf(pair -> pair.second == listener);
            if (mListeners.isEmpty()) {
                mContext.unregisterReceiver(mReceiver);
                if (DEBUG) {
                    Log.d(TAG, "Unregistered user broadcast receiver.");
                }
            }
        }
    }

    private void registerBroadcastReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_USER_ADDED);
        filter.addAction(Intent.ACTION_USER_REMOVED);
        mContext.registerReceiver(mReceiver, filter, Context.RECEIVER_EXPORTED);
    }
}
