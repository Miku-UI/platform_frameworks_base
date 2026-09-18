/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.server.companion.devicepresence;

import android.annotation.NonNull;
import android.annotation.UserIdInt;
import android.os.ParcelUuid;
import android.os.PersistableBundle;
import android.util.Slog;

import com.android.server.companion.utils.PersistableBundleStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * This store manages the cache and disk data for observable uuids.
 */
public class ObservableUuidStore extends PersistableBundleStore {
    private static final String TAG = "CDM_ObservableUuidStore";
    private static final String FILE_NAME = "observable_uuids";

    public String getTag() {
        return TAG;
    }

    public String getFileName() {
        return FILE_NAME;
    }

    public ObservableUuidStore() {
        super();
    }

    /** Read the list of observable uuids. */
    public List<ObservableUuid> readObservableUuids(@UserIdInt int userId) {
        synchronized (mLock) {
            List<ObservableUuid> result = new ArrayList<>();
            PersistableBundle bundle = readData(userId);
            for (String key : bundle.keySet()) {
                PersistableBundle item = bundle.getPersistableBundle(key);
                if (item != null) {
                    result.add(new ObservableUuid(item));
                }
            }
            return result;
        }
    }

    /**
     * @return A list of ObservableUuids per package.
     */
    public List<ObservableUuid> readObservableUuidsForPackage(
            @UserIdInt int userId, @NonNull String packageName) {
        synchronized (mLock) {
            List<ObservableUuid> result = new ArrayList<>();
            PersistableBundle bundle = readData(userId);
            for (String key : bundle.keySet()) {
                PersistableBundle item = bundle.getPersistableBundle(key);
                if (item != null && Objects.equals(item.getString(ObservableUuid.KEY_PACKAGE_NAME),
                        packageName)) {
                    result.add(new ObservableUuid(item));
                }
            }
            return result;
        }
    }

    /** Add the observable uuid. */
    public void addObservableUuid(@UserIdInt int userId, ObservableUuid uuid) {
        synchronized (mLock) {
            List<ObservableUuid> uuids = readObservableUuids(userId);
            uuids.removeIf(uuid1 -> uuid1.uuid().equals(
                    uuid.uuid()) && uuid1.packageName().equals(uuid.packageName()));
            uuids.add(uuid);
            writeObservableUuids(userId, uuids);
        }
    }

    /** Write the list of observable uuids. */
    public void writeObservableUuids(@UserIdInt int userId, List<ObservableUuid> uuids) {
        synchronized (mLock) {
            PersistableBundle result = new PersistableBundle();
            for (int i = 0; i < uuids.size(); i++) {
                PersistableBundle item = uuids.get(i).toPersistableBundle();
                result.putPersistableBundle(String.valueOf(i), item);
            }
            writeData(userId, result);
        }
    }

    /**
     * Remove the observable uuid.
     */
    public void removeObservableUuid(@UserIdInt int userId, ParcelUuid uuid, String packageName) {
        Slog.i(TAG, "Removing uuid=[" + uuid.getUuid() + "] from store...");

        List<ObservableUuid> cachedObservableUuids;

        synchronized (mLock) {
            // Remove requests from cache
            cachedObservableUuids = readObservableUuids(userId);
            cachedObservableUuids.removeIf(
                    uuid1 -> uuid1.packageName().equals(packageName)
                            && uuid1.uuid().equals(uuid));
            writeObservableUuids(userId, cachedObservableUuids);
        }
    }

    /**
     * Check if a UUID is being observed by the package.
     */
    public boolean isUuidBeingObserved(ParcelUuid uuid, int userId, String packageName) {
        final List<ObservableUuid> uuidsBeingObserved = readObservableUuidsForPackage(userId,
                packageName);
        for (ObservableUuid observableUuid : uuidsBeingObserved) {
            if (observableUuid.uuid().equals(uuid)) {
                return true;
            }
        }
        return false;
    }
}
