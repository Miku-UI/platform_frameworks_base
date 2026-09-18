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

import android.os.Process;
import android.util.Log;

import com.android.server.utils.SnapshotCache;
import com.android.server.utils.WatchedSparseArray;
import com.android.server.utils.Watcher;

/**
 * A wrapper over {@link WatchedSparseArray} that tracks the current (PCC UID -> SettingBase)
 * mapping. The PCC UID is currently directly derived from the App ID.
 */
final class PccIdSettingMap {
    private static final int PCC_UID_OFFSET = Process.FIRST_PCC_UID - Process.FIRST_APPLICATION_UID;

    private final WatchedSparseArray<SettingBase> mSettings;
    private final SnapshotCache<WatchedSparseArray<SettingBase>> mSettingsSnapshot;

    PccIdSettingMap() {
        mSettings = new WatchedSparseArray<>();
        mSettingsSnapshot = new SnapshotCache.Auto<>(
                mSettings, mSettings, "PccIdSettingMap.mSettings");
    }

    PccIdSettingMap(PccIdSettingMap orig) {
        mSettings = orig.mSettingsSnapshot.snapshot();
        mSettingsSnapshot = new SnapshotCache.Sealed<>();
    }

    public int acquireAndRegisterNewPccId(PackageSetting p) {
        int appId = p.getAppId();

        if (!Process.isApplicationUid(appId)) {
            return -1;
        }

        // The PCC UID is a direct mapping from the App UID
        int pccUid = appId + PCC_UID_OFFSET;

        if (mSettings.get(pccUid) != null) {
            PackageManagerService.reportSettingsProblem(Log.WARN,
                    "Adding duplicate pcc id: " + pccUid
                            + " for app id: " + appId
                            + " name=" + p.getPackageName());
            return -1;
        }
        mSettings.put(pccUid, p);

        return pccUid;
    }

    /** Returns true if registration is successful, false otherwise. */
    public boolean registerExistingPccId(int pccUid, PackageSetting setting, String name) {
        if (mSettings.get(pccUid) != null) {
            PackageManagerService.reportSettingsProblem(Log.WARN,
                    "Adding duplicate pcc id: " + pccUid
                            + " for app id: " + setting.getAppId()
                            + " name=" + name);
            return false;
        }
        mSettings.put(pccUid, setting);
        return true;
    }

    public SettingBase getSetting(int pccUid) {
        return mSettings.get(pccUid);
    }

    public void removeSetting(int pccUid) {
        mSettings.remove(pccUid);
    }

    public PccIdSettingMap snapshot() {
        return new PccIdSettingMap(this);
    }

    public void registerObserver(Watcher observer) {
        mSettings.registerObserver(observer);
    }
}
