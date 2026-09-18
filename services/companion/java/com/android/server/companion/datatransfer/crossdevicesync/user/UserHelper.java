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

import android.content.pm.UserInfo;
import android.os.UserHandle;

import java.util.List;
import java.util.concurrent.Executor;

/** A helper interface for accessing user information. */
public interface UserHelper {

    /** Get all alive users on the device. */
    List<UserInfo> getAliveUsers();

    /** Register a listener to monitor user changes. */
    void registerUserListener(Executor executor, UserListener listener);

    /** Unregister a user listener. */
    void unregisterUserListener(UserListener listener);

    /** Listener interface for monitoring user changes. */
    interface UserListener {
        /** Called when a user is added. */
        default void onUserAdded(UserHandle user) {}

        /** Called when a user is removed. */
        void onUserRemoved(UserHandle user);
    }
}
