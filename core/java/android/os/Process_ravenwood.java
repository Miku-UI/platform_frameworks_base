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
package android.os;


public class Process_ravenwood {

    private static volatile ThreadLocal<Integer> sThreadPriority;

    static {
        reset();
    }

    public static void reset() {
        // Reset the thread local variable
        sThreadPriority = ThreadLocal.withInitial(() -> Process.THREAD_PRIORITY_DEFAULT);
    }

    /**
     * Called by {@link Process#setThreadPriority(int, int)}
     */
    public static void setThreadPriority(int tid, int priority) {
        if (Process.myTid() == tid) {
            setThreadPriority(priority);
        } else {
            throw new UnsupportedOperationException(
                    "Cross-thread priority management not yet available in Ravenwood");
        }
    }

    /**
     * Called by {@link Process#setThreadPriority(int)}
     * The real implementation uses an Android-specific API.
     */
    public static void setThreadPriority(int priority) {
        if (priority < -20 || priority > 19) {
            throw new IllegalArgumentException("Priority/niceness " + priority + " is invalid");
        }
        sThreadPriority.set(priority);
    }

    /**
     * Called by {@link Process#getThreadPriority(int)}
     */
    public static int getThreadPriority(int tid) {
        if (Process.myTid() == tid) {
            return sThreadPriority.get();
        } else {
            throw new UnsupportedOperationException(
                    "Cross-thread priority management not yet available in Ravenwood");
        }
    }
}
