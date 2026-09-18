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

package com.android.server.power;

import android.os.WorkSource;
import android.util.ArraySet;
import android.util.SparseArray;

import com.android.internal.annotations.GuardedBy;
import com.android.server.power.feature.flags.Flags;

import java.util.Collections;
import java.util.Set;

/**
 * A mapper class to track the relationship between UIDs and the wakelocks they are associated
 * with.
 */
public class WakelockMapper {
    private final Object mLock = new Object();

    @GuardedBy("mLock")
    private final SparseArray<Set<PowerManagerService.WakeLock>> mUidToWakeLocks =
            new SparseArray<>();

    @GuardedBy("mLock")
    private final Set<Integer> mCachedUids = new ArraySet<>();

    /**
     * Updates the cached state of a UID.
     *
     * @param uid    the UID to update.
     * @param cached true if the UID is cached, false otherwise.
     */
    public void setUidCached(int uid, boolean cached) {
        if (!Flags.removeCachedUidsFromWakelock()) {
            return;
        }
        synchronized (mLock) {
            if (cached) {
                mCachedUids.add(uid);
            } else {
                mCachedUids.remove(uid);
            }
        }
    }

    /**
     * Checks if a UID is cached.
     *
     * @param uid the UID to check.
     * @return true if the UID is cached, false otherwise.
     */
    public boolean isUidCached(int uid) {
        if (!Flags.removeCachedUidsFromWakelock()) {
            return false;
        }
        synchronized (mLock) {
            return mCachedUids.contains(uid);
        }
    }

    /**
     * Adds the wakelock to the associated worksource uids.
     *
     * @param wakeLock the wakelock to be added.
     */
    public void addWakeLock(PowerManagerService.WakeLock wakeLock) {
        if (wakeLock == null) {
            return;
        }
        addWakeLock(wakeLock, wakeLock.mWorkSource);
    }

    /**
     * Adds the wakelock to the supplied worksource uids.
     *
     * @param wakeLock the wakelock to be added.
     */
    public void addWakeLock(PowerManagerService.WakeLock wakeLock, WorkSource worksource) {
        if (worksource == null || !Flags.removeCachedUidsFromWakelock()) {
            return;
        }
        synchronized (mLock) {
            addWakeLockForUidLocked(wakeLock.mOwnerUid, wakeLock);

            for (int i = 0; i < worksource.size(); i++) {
                int uid = worksource.getUid(i);
                addWakeLockForUidLocked(uid, wakeLock);
            }

            if (worksource.getWorkChains() != null) {
                for (int i = 0; i < worksource.getWorkChains().size(); i++) {
                    int uid = worksource.getWorkChains().get(i).getAttributionUid();
                    addWakeLockForUidLocked(uid, wakeLock);
                }
            }
        }
    }

    /**
     * Removes the wakelock from the associated worksource uids.
     *
     * @param wakeLock the wakelock to be removed.
     */
    public void removeWakeLock(PowerManagerService.WakeLock wakeLock) {
        if (wakeLock == null) {
            return;
        }
        removeWakeLock(wakeLock, wakeLock.mWorkSource);
    }

    /**
     * Removes the wakelock from the supplied worksource uids.
     *
     * @param wakeLock the wakelock to be removed.
     */
    public void removeWakeLock(PowerManagerService.WakeLock wakeLock, WorkSource worksource) {
        if (worksource == null || !Flags.removeCachedUidsFromWakelock()) {
            return;
        }

        synchronized (mLock) {
            removeWakeLockForUidLocked(wakeLock.mOwnerUid, wakeLock);

            for (int i = 0; i < worksource.size(); i++) {
                int uid = worksource.getUid(i);
                removeWakeLockForUidLocked(uid, wakeLock);
            }

            if (worksource.getWorkChains() != null) {
                for (int i = 0; i < worksource.getWorkChains().size(); i++) {
                    int uid = worksource.getWorkChains().get(i).getAttributionUid();
                    removeWakeLockForUidLocked(uid, wakeLock);
                }
            }
        }
    }

    /**
     * Returns the wakelocks associated with the given UID.
     *
     * @param uid the UID to look up.
     * @return the set of wakelocks associated with the UID.
     */
    public Set<PowerManagerService.WakeLock> getWakeLocksForUid(int uid) {
        synchronized (mLock) {
            return new ArraySet<>(mUidToWakeLocks.get(uid, Collections.emptySet()));
        }
    }

    private void addWakeLockForUidLocked(int uid, PowerManagerService.WakeLock wakeLock) {
        Set<PowerManagerService.WakeLock> wakeLocks = mUidToWakeLocks.get(uid);
        if (wakeLocks == null) {
            wakeLocks = new ArraySet<>();
            mUidToWakeLocks.put(uid, wakeLocks);
        }
        wakeLocks.add(wakeLock);
    }

    private void removeWakeLockForUidLocked(int uid, PowerManagerService.WakeLock wakeLock) {
        Set<PowerManagerService.WakeLock> wakeLocks = mUidToWakeLocks.get(uid);
        if (wakeLocks != null) {
            wakeLocks.remove(wakeLock);
            if (wakeLocks.isEmpty()) {
                mUidToWakeLocks.remove(uid);
            }
        }
    }
}
