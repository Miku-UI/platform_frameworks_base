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

package android.os;

import android.util.ArrayMap;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.util.Preconditions;

import java.util.Map;

public class ServiceManager_ravenwood {
    private ServiceManager_ravenwood() {
    }

    private static final Object sLock = new Object();

    @GuardedBy("sLock")
    private static Map<String, IBinder> sCache;

    /** Called by RavenwoodDriver for initialization. */
    public static void init() {
        synchronized (sLock) {
            sCache = new ArrayMap<>();
        }
    }

    static IBinder getService(String name) {
        synchronized (ServiceManager.class) {
            return Preconditions.requireNonNullViaRavenwoodRule(sCache).get(name);
        }
    }

    static void addService(String name, IBinder service, boolean allowIsolated,
            int dumpPriority) {
        synchronized (ServiceManager.class) {
            Preconditions.requireNonNullViaRavenwoodRule(sCache).put(name, service);
        }
    }
}
