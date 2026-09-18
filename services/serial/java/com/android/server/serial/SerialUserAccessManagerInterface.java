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

package com.android.server.serial;

import android.annotation.Nullable;
import android.os.IBinder;

/**
 * This interface manages app accesses to serial devices.
 */
public interface SerialUserAccessManagerInterface {

    /** Requests access to a serial port. */
    void requestAccess(String requestedPortName, int pid, int uid, String requestingPackageName,
            AccessToPortDecidedCallback callback);

    /** Grants access to a serial port. */
    void grantAccess(String portName, int uid, boolean persistent, @Nullable IBinder token);

    /** Revokes access to a serial port. */
    void revokeAccess(String portName, int uid, boolean persistent, @Nullable IBinder token);

    /** The user is stopping. This is the last chance to write to system per-user storage. */
    void onUserStopping();

    /** Called when a serial port is removed. */
    void onPortRemoved(String name);

    /** Clear user access for tests */
    void clearUserAccess(int userId);
}
