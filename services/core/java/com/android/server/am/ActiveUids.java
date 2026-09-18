/*
 * Copyright (C) 2018 The Android Open Source Project
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

import android.app.ActivityManager;
import android.os.UserHandle;
import android.util.proto.ProtoOutputStream;

import com.android.server.am.psc.ActiveUidsInternal;

import java.io.PrintWriter;

/**
 * Class for tracking active uids for running processes.
 * Note: This class is not thread-safe, caller should handle the synchronization.
 *
 * TODO(b/425766486): Maybe rename it to "UidRecordMap", so we can reuse it for other purposes than
 *   "active" UIDs.
 */
public final class ActiveUids extends ActiveUidsInternal {
    /**
     * Interface for observing changes in UID active/inactive states based on state changes of
     * processes running for that UID.
     */
    public interface Observer {
        /**
         * Called when a UID becomes active.
         * @param uid The UID that became active.
         * @param procState The current process state of the UID.
         */
        void onUidActive(int uid, int procState);

        /**
         * Called when a UID becomes inactive.
         * @param uid The UID that became inactive.
         */
        void onUidInactive(int uid);
    }

    private final Observer mObserver;

    ActiveUids(Observer observer) {
        mObserver = observer;
    }

    void put(int uid, UidRecord value) {
        super.put(uid, value);
        if (mObserver != null) {
            mObserver.onUidActive(uid, value.getCurProcState());
        }
    }

    @Override
    public void remove(int uid) {
        super.remove(uid);
        if (mObserver != null) {
            mObserver.onUidInactive(uid);
        }
    }

    @Override
    public UidRecord get(int uid) {
        return (UidRecord) super.get(uid);
    }

    @Override
    public UidRecord valueAt(int index) {
        return (UidRecord) super.valueAt(index);
    }

    boolean dump(final PrintWriter pw, String dumpPackage, int dumpAppId,
            String header, boolean needSep) {
        boolean printed = false;
        for (int i = 0; i < size(); i++) {
            final UidRecord uidRec = valueAt(i);
            if (dumpPackage != null && UserHandle.getAppId(uidRec.getUid()) != dumpAppId) {
                continue;
            }
            if (!printed) {
                printed = true;
                if (needSep) {
                    pw.println();
                }
                pw.print("  "); pw.println(header);
            }
            pw.print("    UID "); UserHandle.formatUid(pw, uidRec.getUid());
            pw.print(": "); pw.println(uidRec);
            pw.print("      curProcState="); pw.print(uidRec.getCurProcState());
            pw.print(" curCapability=");
            ActivityManager.printCapabilitiesFull(pw, uidRec.getCurCapability());
            pw.println();
            uidRec.forEachProcess(app -> {
                pw.print("      proc=");
                pw.println(app);
            });
        }
        return printed;
    }

    void dumpProto(ProtoOutputStream proto, String dumpPackage, int dumpAppId, long fieldId) {
        for (int i = 0; i < size(); i++) {
            UidRecord uidRec = valueAt(i);
            if (dumpPackage != null && UserHandle.getAppId(uidRec.getUid()) != dumpAppId) {
                continue;
            }
            uidRec.dumpDebug(proto, fieldId);
        }
    }
}
