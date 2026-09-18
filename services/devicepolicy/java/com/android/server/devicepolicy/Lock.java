
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

package com.android.server.devicepolicy;

import android.util.IndentingPrintWriter;
import com.android.server.LockGuard;
import com.android.internal.util.StatLogger;
import com.android.server.utils.Slogf;
import java.io.PrintWriter;

/**
 * Lock object with some additional logging. Usage:
 *   private final Lock mLock;
 *   ...
 *
 *   synchronized(mLock.guard()) {
 *
 *   }
 */
final class Lock{

    private static final String LOG_TAG = DevicePolicyManagerService.LOG_TAG;

    private static final boolean ENABLE_LOCK_GUARD = true;

    interface Stats {
        int LOCK_GUARD_GUARD = 0;

        int COUNT = LOCK_GUARD_GUARD + 1;
    }

    private final StatLogger mStatLogger = new StatLogger(new String[] {
            "LockGuard.guard()",
    });

    private final Object mLockDoNoUseDirectly = LockGuard.installNewLock(
            LockGuard.INDEX_DPMS, /* doWtf=*/ true);

    public Object getLockObject() {
        if (ENABLE_LOCK_GUARD) {
            final long start = mStatLogger.getTime();
            LockGuard.guard(LockGuard.INDEX_DPMS);
            mStatLogger.logDurationStat(Stats.LOCK_GUARD_GUARD, start);
        }
        return mLockDoNoUseDirectly;
    }

    /**
     * Check if the current thread holds the DPMS lock, and if not, do a WTF.
     *
     * (Doing this check too much may be costly, so don't call it in a hot path.)
     */
    public void ensureLocked() {
        if (Thread.holdsLock(mLockDoNoUseDirectly)) {
            return;
        }
        Slogf.wtfStack(LOG_TAG, "Not holding DPMS lock.");
    }

    /**
     * Calls wtfStack() if called with the DPMS lock held.
     */
    public void wtfIfInLock() {
        if (Thread.holdsLock(mLockDoNoUseDirectly)) {
            Slogf.wtfStack(LOG_TAG, "Shouldn't be called with DPMS lock held");
        }
    }

    public void dump(IndentingPrintWriter pw) {
        mStatLogger.dump(pw);
    }

};