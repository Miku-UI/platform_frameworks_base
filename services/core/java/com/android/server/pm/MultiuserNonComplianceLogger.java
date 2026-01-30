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
package com.android.server.pm;

import android.annotation.Nullable;
import android.content.ComponentName;
import android.content.pm.PackageManagerInternal;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.Process;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.util.ArraySet;
import android.util.SparseIntArray;

import com.android.server.LocalServices;

import java.io.PrintWriter;

/**
 * Class used to report events that indicate the potential existence of non-multiuser-compliant
 * features, such as the API calls to check for "main user" (deprecated) and the activities and
 * notifications on headless system user.
 */
final class MultiuserNonComplianceLogger {

    private static final String PROP_ENABLE_IT = "fw.user.log_non_compliance";
    private static final int PROP_ENABLED = 1;
    private static final int PROP_DEFAULT = -1;

    private final Handler mHandler;

    // TODO(b/414326600): merge collections below and/or use the proper proto / structure

    // Key is "absolute" uid  / app id (i.e., stripping out the user id part), value is count.
    @Nullable
    private final SparseIntArray mGetMainUserCalls;

    // Key is "absolute" uid  / app id (i.e., stripping out the user id part), value is count.
    @Nullable
    private final SparseIntArray mIsMainUserCalls;

    // Activities launched while the current user is the headless system user.
    @Nullable // Only set on debuggable builds
    private final ArraySet<ComponentName> mLaunchedHsuActivities;

    // Set on demand, Should not be used directly (but through getPackageManagerInternal() instead).
    @Nullable
    private PackageManagerInternal mPmInternal;

    MultiuserNonComplianceLogger(Handler handler) {
        mHandler = handler;
        if (Build.isDebuggable()
                || SystemProperties.getInt(PROP_ENABLE_IT, PROP_DEFAULT) == PROP_ENABLED) {
            mGetMainUserCalls = new SparseIntArray();
            mIsMainUserCalls = new SparseIntArray();
            mLaunchedHsuActivities = new ArraySet<>();
        } else {
            mGetMainUserCalls = null;
            mIsMainUserCalls = null;
            mLaunchedHsuActivities = null;
        }
    }

    // TODO(b/414326600): add unit tests (once the proper formats are determined).
    void logGetMainUserCall() {
        logMainUserCall(mGetMainUserCalls);
    }

    // TODO(b/414326600): add unit tests (once the proper formats are determined).
    void logIsMainUserCall() {
        logMainUserCall(mIsMainUserCalls);
    }

    private void logMainUserCall(@Nullable SparseIntArray calls) {
        if (calls == null) {
            return;
        }

        // Must set before posting to the handler (otherwise it would always return the system UID)
        int uid = Binder.getCallingUid();

        mHandler.post(() -> {
            int canonicalUid = UserHandle.getAppId(uid);
            int newCount = calls.get(canonicalUid, 0) + 1;
            calls.put(canonicalUid, newCount);
        });
    }

    // TODO(b/414326600): add unit tests (once the proper formats are determined).
    void logLaunchedHsuActivity(ComponentName activity) {
        if (mLaunchedHsuActivities == null) {
            return;
        }
        mHandler.post(() -> mLaunchedHsuActivities.add(activity));
    }

    // NOTE: output format might changed, so it should not be used for automated testing purposes
    // (a proto version will be provided when it's ready)
    void dump(PrintWriter pw) {
        // TODO(b/414326600): add unit tests (once the proper formats are determined).
        dump(pw, "getMainUser", mGetMainUserCalls);
        pw.println();
        dump(pw, "isMainUser", mIsMainUserCalls);
        pw.println();
        dumpLaunchedHsuActivities(pw);
    }

    private void dump(PrintWriter pw, String method, @Nullable SparseIntArray calls) {
        if (calls == null) {
            pw.printf("Not logging %s() calls\n", method);
            return;
        }

        // TODO(b/414326600): should dump in the mHandler thread (as its state is written in that
        // thread), but it would require blocking the caller until it's done


        // TODO(b/414326600): should also dump on proto, but we need to wait until the format is
        // properly defined (for example, we might want to log a generic "user violation" that would
        // include other metrics such as stuff that shouldn't be called when the current user is the
        // headless system user)
        int size = calls.size();
        if (size == 0) {
            pw.printf("Good News, Everyone!: no app called %s()\n", method);
            return;
        }
        pw.printf("%d apps called %s():\n", size, method);
        var pm = getPackageManagerInternal();
        for (int i = 0; i < size; i++) {
            int canonicalUid = calls.keyAt(i);
            int count = calls.valueAt(i);
            String pkgName = getPackageNameForLoggingPurposes(pm, canonicalUid);
            // uid is the canonical UID, but including "canonical" would add extra churn / bytes
            pw.printf("  %s (uid %d): %d calls\n", pkgName, canonicalUid, count);
        }
    }

    private void dumpLaunchedHsuActivities(PrintWriter pw) {
        if (mLaunchedHsuActivities == null) {
            pw.println("Not logging launched HSU activities");
            return;
        }
        // TODO(b/414326600): should dump in the mHandler thread (as its state is written in that
        // thread), but it would require blocking the caller until it's done
        int size = mLaunchedHsuActivities.size();
        if (size == 0) {
            pw.println("Good News, Everyone!: no activity launched on HSU!");
            return;
        }
        // TODO(b/414326600): for now they're always launched, but once the allowlist mechanism is
        // implemented, it should print the real action
        pw.printf("%d activities launched on HSU:\n", size);
        for (int i = 0; i < size; i++) {
            pw.printf("  %s\n", mLaunchedHsuActivities.valueAt(i).flattenToShortString());
        }
    }

    // TODO(b/414326600): add unit tests
    void reset(PrintWriter pw) {
        // TODO(b/414326600): should reset in the mHandler thread (as its state is written in that
        // thread), but it would require blocking the caller until it's done

        if (mGetMainUserCalls != null) {
            mGetMainUserCalls.clear();
        }
        if (mIsMainUserCalls != null) {
            mIsMainUserCalls.clear();
        }
        if (mLaunchedHsuActivities != null) {
            mLaunchedHsuActivities.clear();
        }
        pw.println("Reset");
    }

    /** Retrieves the internal package manager interface. */
    private PackageManagerInternal getPackageManagerInternal() {
        // Don't need to synchronize; worst-case scenario LocalServices will be called twice.
        if (mPmInternal == null) {
            mPmInternal = LocalServices.getService(PackageManagerInternal.class);
        }
        return mPmInternal;
    }

    // TODO(b/414326600): this method is taking a simplest aproach to get the uid, but it's not
    // handling corner cases like an app not available on user 0 or multiple apps with the same uid.
    // This is fine for now, but the final solution need to take those scenarios in account.
    private static String getPackageNameForLoggingPurposes(PackageManagerInternal pm, int uid) {
        if (uid == Process.SYSTEM_UID) {
            // Many apps might be running as system (because they declare sharedUserId in the
            // manifest), so we wouldn't know for sure which one calls it here
            return "system";
        }
        var pkg = pm.getPackage(uid);
        // TODO(b/414326600): if it's from system, it might be useful to log the method that's
        // calling it, but that's expensive (so we should guard using a system property) and we'd
        // need to change the type of maps as well - for now, the solution is to look
        // at logcat (which logs the full stacktrace the tag is VERBOSE).
        // TODO(b/414326600): figure out proper way to handle null (for example, it'is also null
        // for root UID).
        return pkg == null ? "system" :  pkg.getPackageName();
    }
}
