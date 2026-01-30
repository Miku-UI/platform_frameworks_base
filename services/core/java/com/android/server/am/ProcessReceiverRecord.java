/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.server.am;

import android.util.ArraySet;

import com.android.internal.annotations.GuardedBy;
import com.android.server.am.psc.ProcessReceiverRecordInternal;

import java.io.PrintWriter;

/**
 * The state info of all broadcast receivers in the process.
 */
final class ProcessReceiverRecord extends ProcessReceiverRecordInternal {
    private final ActivityManagerService mService;

    /**
     * All IIntentReceivers that are registered from this process.
     */
    private final ArraySet<ReceiverList> mReceivers = new ArraySet<>();

    int numberOfReceivers() {
        return mReceivers.size();
    }

    void addReceiver(ReceiverList receiver) {
        mReceivers.add(receiver);
    }

    void removeReceiver(ReceiverList receiver) {
        mReceivers.remove(receiver);
    }

    ProcessReceiverRecord(ActivityManagerService service) {
        super(service);

        mService = service;
    }

    @GuardedBy("mService")
    void onCleanupApplicationRecordLocked() {
        // Unregister any mReceivers.
        for (int i = mReceivers.size() - 1; i >= 0; i--) {
            mService.removeReceiverLocked(mReceivers.valueAt(i));
        }
        mReceivers.clear();
    }

    void dump(PrintWriter pw, String prefix, long nowUptime) {
        pw.print(prefix);
        pw.print("mIsReceivingBroadcast=");
        pw.println(isReceivingBroadcast());

        pw.print(prefix);
        pw.print("mBroadcastReceiverSchedGroup=");
        pw.println(getBroadcastReceiverSchedGroup());
        if (mReceivers.size() > 0) {
            pw.print(prefix); pw.println("mReceivers:");
            for (int i = 0, size = mReceivers.size(); i < size; i++) {
                pw.print(prefix); pw.print("  - "); pw.println(mReceivers.valueAt(i));
            }
        }
    }
}
