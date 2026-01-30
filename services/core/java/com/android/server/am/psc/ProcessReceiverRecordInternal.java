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

package com.android.server.am.psc;

import com.android.internal.annotations.GuardedBy;
import com.android.server.am.ProcessList;

/** Internal base class for managing broadcast receiver state within a process. */
public abstract class ProcessReceiverRecordInternal {
    /** The ActivityManagerService object, which can only be used as a lock object. */
    private final Object mServiceLock;

    /** Whether the process is currently receiving a broadcast. */
    @GuardedBy("mServiceLock")
    private boolean mIsReceivingBroadcast;

    /** The scheduling group assigned to the process when it is receiving a broadcast. */
    @GuardedBy("mServiceLock")
    private int mBroadcastReceiverSchedGroup = ProcessList.SCHED_GROUP_UNDEFINED;

    public ProcessReceiverRecordInternal(Object serviceLock) {
        mServiceLock = serviceLock;
    }

    @GuardedBy("mServiceLock")
    public boolean isReceivingBroadcast() {
        return mIsReceivingBroadcast;
    }

    @GuardedBy("mServiceLock")
    public void setIsReceivingBroadcast(boolean receivingBroadcast) {
        mIsReceivingBroadcast = receivingBroadcast;
    }

    @GuardedBy("mServiceLock")
    public void setBroadcastReceiverSchedGroup(int priority) {
        mBroadcastReceiverSchedGroup = priority;
    }

    @GuardedBy("mServiceLock")
    public int getBroadcastReceiverSchedGroup() {
        return mBroadcastReceiverSchedGroup;
    }
}
