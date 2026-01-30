/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.ravenwood;

/**
 * Class to push down runtime states from {@link android.platform.test.ravenwood.RavenwoodDriver}
 * to libcore-fake. We need it because libcore-fake can't see junit-impl-src.
 */
public class RavenwoodVmState {
    private static final Object sLock = new Object();
    private static boolean sInitialized;
    private static int sUid;
    private static int sPid;
    private static int sTargetSdkLevel;

    /**
     * Called by {@link android.platform.test.ravenwood.RavenwoodDriver}
     * to push down information.
     */
    public static void init(int uid, int pid, int targetSdkLevel) {
        synchronized (sLock) {
            if (sInitialized) {
                throw new RuntimeException("RavenwoodRuntimeState already initialized");
            }
            sUid = uid;
            sPid = pid;
            sTargetSdkLevel = targetSdkLevel;
            sInitialized = true;
        }
    }

    // @GuardedBy("sLock")
    private static void ensureInitializedLock() {
        if (!sInitialized) {
            throw new RuntimeException("RavenwoodRuntimeState not initialized");
        }
    }

    /** @return test UID */
    public static int getUid() {
        synchronized (sLock) {
            ensureInitializedLock();
            return sUid;
        }
    }

    /** @return process PID */
    public static int getPid() {
        synchronized (sLock) {
            ensureInitializedLock();
            return sPid;
        }
    }

    /** @return test target SDK level */
    public static int getTargetSdkLevel() {
        synchronized (sLock) {
            ensureInitializedLock();
            return sTargetSdkLevel;
        }
    }
}
