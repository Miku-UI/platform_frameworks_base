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
package com.android.server.companion.powerexemption;

import android.annotation.NonNull;
import android.annotation.UserIdInt;
import android.os.PersistableBundle;
import android.util.ArraySet;

import com.android.server.companion.utils.PersistableBundleStore;

import java.util.Collections;
import java.util.Set;

/**
 * Persists the set of packages that have been allowlisted by the Companion Device Manager.
 * <p>
 * This ensures that CDM only revokes exemptions that CDM itself granted, distinguishing
 * them from user-granted/rejected exemptions.
 * <p>
 * Data is stored per-user using {@link PersistableBundleStore}.
 */
public class CompanionExemptionStore extends PersistableBundleStore {
    private static final String TAG = "CDM_CompanionExemptionStore";
    private static final String FILE_NAME = "companion_allowlist_state";

    // Key for packages CDM added to the allowlist
    private static final String KEY_CDM_ALLOWLISTED_PACKAGES = "allowlisted_packages";
    // Key for packages the User has manually touched (Allowed or Restricted)
    private static final String KEY_USER_OVERRIDDEN_PACKAGES = "user_overridden_packages";

    @Override
    protected String getTag() {
        return TAG;
    }

    @Override
    protected String getFileName() {
        return FILE_NAME;
    }

    /**
     * Records that CDM granted an exemption to this package.
     */
    public void addCdmAllowlisted(@UserIdInt int userId, @NonNull String packageName) {
        addToSet(userId, KEY_CDM_ALLOWLISTED_PACKAGES, packageName);
    }

    /**
     * Removes the record that CDM granted an exemption (e.g. on disconnect or user override).
     */
    public void removeCdmAllowlisted(@UserIdInt int userId, @NonNull String packageName) {
        removeFromSet(userId, KEY_CDM_ALLOWLISTED_PACKAGES, packageName);
    }

    /**
     * Returns true if CDM believes it owns the exemption for this package.
     */
    public boolean isCdmAllowlisted(@UserIdInt int userId, @NonNull String packageName) {
        return containsInSet(userId, KEY_CDM_ALLOWLISTED_PACKAGES, packageName);
    }

    /**
     * Records that the user manually modified this package's state.
     * CDM will ignore this package in the future.
     */
    public void addUserOverridden(@UserIdInt int userId, @NonNull String packageName) {
        addToSet(userId, KEY_USER_OVERRIDDEN_PACKAGES, packageName);
    }

    /**
     * Removes the user override record (e.g. on app uninstall).
     */
    public void removeUserOverridden(@UserIdInt int userId, @NonNull String packageName) {
        removeFromSet(userId, KEY_USER_OVERRIDDEN_PACKAGES, packageName);
    }

    /**
     * Returns true if the user has manually managed this package.
     */
    public boolean isUserOverridden(@UserIdInt int userId, @NonNull String packageName) {
        return containsInSet(userId, KEY_USER_OVERRIDDEN_PACKAGES, packageName);
    }

    /**
     * Removes all state associated with a package across all lists.
     * Call this when an app is uninstalled or data is cleared.
     */
    public void removePackage(@UserIdInt int userId, @NonNull String packageName) {
        synchronized (mLock) {
            PersistableBundle bundle = readData(userId);
            boolean changed = false;

            changed |= removeFromBundleArray(bundle, KEY_CDM_ALLOWLISTED_PACKAGES, packageName);
            changed |= removeFromBundleArray(bundle, KEY_USER_OVERRIDDEN_PACKAGES, packageName);

            if (changed) {
                writeData(userId, bundle);
            }
        }
    }

    private void addToSet(@UserIdInt int userId, String key, String packageName) {
        synchronized (mLock) {
            PersistableBundle bundle = readData(userId);
            Set<String> set = getSetFromBundle(bundle, key);

            if (set.add(packageName)) {
                writeSetToBundle(bundle, key, set);
                writeData(userId, bundle);
            }
        }
    }

    private void removeFromSet(@UserIdInt int userId, String key, String packageName) {
        synchronized (mLock) {
            PersistableBundle bundle = readData(userId);
            Set<String> set = getSetFromBundle(bundle, key);

            if (set.remove(packageName)) {
                writeSetToBundle(bundle, key, set);
                writeData(userId, bundle);
            }
        }
    }

    private boolean containsInSet(@UserIdInt int userId, String key, String packageName) {
        synchronized (mLock) {
            // readData returns a copy, so this is thread-safe
            return getSetFromBundle(readData(userId), key).contains(packageName);
        }
    }

    /**
     * Helper to modify a set directly within the bundle for atomic bulk operations.
     * Returns true if the bundle was modified.
     */
    private boolean removeFromBundleArray(PersistableBundle bundle, String key, String value) {
        Set<String> set = getSetFromBundle(bundle, key);
        if (set.remove(value)) {
            writeSetToBundle(bundle, key, set);
            return true;
        }
        return false;
    }

    @NonNull
    private Set<String> getSetFromBundle(PersistableBundle bundle, String key) {
        String[] array = bundle.getStringArray(key);
        if (array == null || array.length == 0) {
            return new ArraySet<>();
        }
        Set<String> set = new ArraySet<>(array.length);
        Collections.addAll(set, array);
        return set;
    }

    private void writeSetToBundle(PersistableBundle bundle, String key, Set<String> set) {
        String[] array = new String[set.size()];
        set.toArray(array);
        bundle.putStringArray(key, array);
    }
}
