/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package lockedregioncodeinjection;

/**
 * Represent a specific class that is used for synchronization. A pre and post method can be
 * specified to by the user to be called right after monitor_enter and after monitor_exit
 * respectively.
 */
public class LockTarget {
    public static final LockTarget NO_TARGET = new LockTarget("", null, null);

    // The lock which must be instrumented, in Java internal form (L<path>;).
    private final String mTargetDesc;
    // The methods to be called when the lock is taken (released).  For non-scoped locks,
    // these are fully qualified static methods.  For scoped locks, these are the
    // unqualified names of a member method of the target lock.
    private final String mPre;
    private final String mPost;
    private final String mTraceBeforeAcquire;
    private final String mTraceAfterAcquire;
    private final String mTraceBeforeRelease;
    private final String mTraceAfterRelease;
    // If true, the pre and post methods are virtual on the target class.  The pre and post methods
    // are both called while the lock is held.  If this field is false then the pre and post methods
    // take no parameters and the post method is called after the lock is released.  This is legacy
    // behavior.
    private final boolean mScoped;

    public LockTarget(String targetDesc, String pre, String post, String traceBeforeAcquire,
            String traceAfterAcquire, String traceBeforeRelease, String traceAfterRelease,
            boolean scoped) {
        this.mTargetDesc = targetDesc;
        this.mPre = pre;
        this.mPost = post;
        this.mTraceBeforeAcquire = traceBeforeAcquire;
        this.mTraceAfterAcquire = traceAfterAcquire;
        this.mTraceBeforeRelease = traceBeforeRelease;
        this.mTraceAfterRelease = traceAfterRelease;
        this.mScoped = scoped;
    }

    public LockTarget(String targetDesc, String pre, String post, boolean scoped) {
        this(targetDesc, pre, post, null, null, null, null, scoped);
    }

    public LockTarget(String targetDesc, String pre, String post) {
        this(targetDesc, pre, post, false);
    }

    public String getTargetDesc() {
        return mTargetDesc;
    }

    public String getPre() {
        return mPre;
    }

    public String getPreOwner() {
        if (mPre == null) {
            return null;
        }
        if (mScoped) {
            return mTargetDesc.substring(1, mTargetDesc.length() - 1);
        } else {
            return mPre.substring(0, mPre.lastIndexOf('.'));
        }
    }

    public String getPreMethod() {
        return mPre == null ? null : mPre.substring(mPre.lastIndexOf('.') + 1);
    }

    public String getPost() {
        return mPost;
    }

    public String getPostOwner() {
        if (mPost == null) {
            return null;
        }
        if (mScoped) {
            return mTargetDesc.substring(1, mTargetDesc.length() - 1);
        } else {
            return mPost.substring(0, mPost.lastIndexOf('.'));
        }
    }

    public String getPostMethod() {
        return mPost == null ? null : mPost.substring(mPost.lastIndexOf('.') + 1);
    }

    public String getTraceBeforeAcquire() {
        return mTraceBeforeAcquire;
    }

    public String getTraceBeforeAcquireOwner() {
        return mTraceBeforeAcquire == null ? null
                : mTraceBeforeAcquire.substring(0, mTraceBeforeAcquire.lastIndexOf('.'));
    }

    public String getTraceBeforeAcquireMethod() {
        return mTraceBeforeAcquire == null ? null
                : mTraceBeforeAcquire.substring(mTraceBeforeAcquire.lastIndexOf('.') + 1);
    }

    public String getTraceAfterAcquire() {
        return mTraceAfterAcquire;
    }

    public String getTraceAfterAcquireOwner() {
        return mTraceAfterAcquire == null ? null
                : mTraceAfterAcquire.substring(0, mTraceAfterAcquire.lastIndexOf('.'));
    }

    public String getTraceAfterAcquireMethod() {
        return mTraceAfterAcquire == null ? null
                : mTraceAfterAcquire.substring(mTraceAfterAcquire.lastIndexOf('.') + 1);
    }

    public String getTraceBeforeRelease() {
        return mTraceBeforeRelease;
    }

    public String getTraceBeforeReleaseOwner() {
        return mTraceBeforeRelease == null ? null
                : mTraceBeforeRelease.substring(0, mTraceBeforeRelease.lastIndexOf('.'));
    }

    public String getTraceBeforeReleaseMethod() {
        return mTraceBeforeRelease == null ? null
                : mTraceBeforeRelease.substring(mTraceBeforeRelease.lastIndexOf('.') + 1);
    }

    public String getTraceAfterRelease() {
        return mTraceAfterRelease;
    }

    public String getTraceAfterReleaseOwner() {
        return mTraceAfterRelease == null ? null
                : mTraceAfterRelease.substring(0, mTraceAfterRelease.lastIndexOf('.'));
    }

    public String getTraceAfterReleaseMethod() {
        return mTraceAfterRelease == null ? null
                : mTraceAfterRelease.substring(mTraceAfterRelease.lastIndexOf('.') + 1);
    }

    public boolean getScoped() {
        return mScoped;
    }

    @Override
    public String toString() {
        return mTargetDesc + ":" + mPre + ":" + mPost;
    }
}
