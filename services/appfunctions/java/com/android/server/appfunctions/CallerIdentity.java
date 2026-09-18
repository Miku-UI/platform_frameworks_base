/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.server.appfunctions;

import android.annotation.NonNull;
import android.os.UserHandle;

import java.util.Objects;

/** Identifier for an app function caller. */
public final class CallerIdentity {

    private final String mCallingPackageName;
    private final UserHandle mUserHandle;
    private final int mCallingUid;
    private final int mCallingPid;

    public CallerIdentity(
            @NonNull String callingPackageName,
            @NonNull UserHandle userHandle,
            int callingUid,
            int callingPid) {
        mCallingPackageName = Objects.requireNonNull(callingPackageName);
        mUserHandle = Objects.requireNonNull(userHandle);
        mCallingUid = callingUid;
        mCallingPid = callingPid;
    }

    /** Returns the calling UID. */
    public int getCallingUid() {
        return mCallingUid;
    }

    /** Returns the calling PID. */
    public int getCallingPid() {
        return mCallingPid;
    }

    /** Returns the calling package name. */
    @NonNull
    public String getCallingPackageName() {
        return mCallingPackageName;
    }

    /** Returns the target user handle. */
    @NonNull
    public UserHandle getUserHandle() {
        return mUserHandle;
    }

    @Override
    public String toString() {
        return "CallerIdentity{"
                + "callingPackageName='"
                + mCallingPackageName
                + '\''
                + ", userHandle="
                + mUserHandle
                + ", callingUid="
                + mCallingUid
                + ", callingPid="
                + mCallingPid
                + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CallerIdentity)) {
            return false;
        }
        CallerIdentity that = (CallerIdentity) o;
        return mCallingUid == that.mCallingUid
                && mCallingPid == that.mCallingPid
                && mCallingPackageName.equals(that.mCallingPackageName)
                && mUserHandle.equals(that.mUserHandle);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mCallingUid, mCallingPid, mCallingPackageName, mUserHandle);
    }
}
