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

package com.android.server.companion.virtual.computercontrol;

import static android.companion.virtual.computercontrol.ComputerControlSession.EXTRA_AUTOMATING_PACKAGE_NAME;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.app.Activity;
import android.companion.virtual.computercontrol.IAutomatedPackageListener;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcel;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.os.UserHandle;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Pair;
import android.util.Slog;
import android.util.SparseArray;

import com.android.internal.annotations.GuardedBy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Keeps track of all packages running on computer control sessions and notifies listeners. */
public class AutomatedPackagesRepository {

    private static final String TAG = AutomatedPackagesRepository.class.getSimpleName();

    private static final ArraySet<String> EMPTY_SET = new ArraySet<>();

    private static final String AUTOMATED_APP_LAUNCH_WARNING_PACKAGE =
            "com.android.virtualdevicemanager";

    private static final ComponentName AUTOMATED_APP_LAUNCH_WARNING_ACTIVITY = new ComponentName(
            AUTOMATED_APP_LAUNCH_WARNING_PACKAGE,
            AUTOMATED_APP_LAUNCH_WARNING_PACKAGE + ".AutomatedAppLaunchWarningActivity");

    private final Handler mHandler;

    private final Object mLock = new Object();

    @GuardedBy("mLock")
    private final RemoteCallbackList<IAutomatedPackageListener> mAutomatedPackageListeners =
            new RemoteCallbackList<>();

    // Currently automated package names keyed by device owner and user id.
    // The listeners need to be notified if this changes.
    @GuardedBy("mLock")
    private final ArrayMap<String, SparseArray<ArraySet<String>>> mAutomatedPackages =
            new ArrayMap<>();

    // Full mapping of deviceId -> userId -> packageNames running on that device.
    // We need the deviceId for correctness, as there may be multiple devices with the same owner.
    @GuardedBy("mLock")
    private final SparseArray<SparseArray<ArraySet<String>>> mDevicePackages = new SparseArray<>();

    // Mapping of deviceId to the package name of the owner of that device.
    @GuardedBy("mLock")
    private final SparseArray<String> mDeviceOwnerPackageNames = new SparseArray<>();

    // Set of userId and package pairs that have been intercepted and the user chose to proceed
    // with launching. These should not be intercepted again.
    @GuardedBy("mLock")
    private final ArraySet<Pair<Integer, String>> mInterceptedLaunches = new ArraySet<>();

    public AutomatedPackagesRepository(Handler handler) {
        mHandler = handler;
    }

    /** Register a listener for automated package changes. */
    public void registerAutomatedPackageListener(IAutomatedPackageListener listener) {
        synchronized (mLock) {
            mAutomatedPackageListeners.register(listener);
        }
    }

    /** Unregister a listener for automated package changes. */
    public void unregisterAutomatedPackageListener(IAutomatedPackageListener listener) {
        synchronized (mLock) {
            mAutomatedPackageListeners.unregister(listener);
        }
    }

    /** Returns an intent for intercepting an automated app launch if needed. */
    @Nullable
    public Intent createAutomatedAppLaunchWarningIntent(
            @NonNull String packageName, @UserIdInt int userId,
            @Nullable String callingPackageName,
            @Nullable String launchVirtualDeviceOwnerPackageName,
            @NonNull Consumer<Integer> closeVirtualDevice) {
        final Pair<Integer, String> uidPackagePair = new Pair<>(userId, packageName);
        synchronized (mLock) {
            if (mInterceptedLaunches.remove(uidPackagePair)) {
                // This package/userId pair has already been intercepted and the user chose to
                // proceed with the launch.
                return null;
            }

            for (int i = 0; i < mDevicePackages.size(); ++i) {
                if (!mDevicePackages.valueAt(i).get(userId, EMPTY_SET).contains(packageName)) {
                    // This package/userId pair is not automated.
                    continue;
                }
                final String deviceOwner = mDeviceOwnerPackageNames.get(mDevicePackages.keyAt(i));
                if (Objects.equals(deviceOwner, callingPackageName)
                        || Objects.equals(deviceOwner, launchVirtualDeviceOwnerPackageName)) {
                    // The automating package initiated the launch or the new display is also owned
                    // by the same automating package.
                    continue;
                }

                final int deviceId = mDevicePackages.keyAt(i);
                final var resultReceiver = new StopAutomationResultReceiver(
                        uidPackagePair, () -> closeVirtualDevice.accept(deviceId));

                return new Intent()
                        .setComponent(AUTOMATED_APP_LAUNCH_WARNING_ACTIVITY)
                        .putExtra(Intent.EXTRA_PACKAGE_NAME, packageName)
                        .putExtra(EXTRA_AUTOMATING_PACKAGE_NAME, deviceOwner)
                        .putExtra(Intent.EXTRA_RESULT_RECEIVER, resultReceiver.prepareForIpc());
            }
        }
        return null;
    }

    /** Update the list of packages running on a device. */
    public void update(int deviceId, String deviceOwnerPackageName,
            ArraySet<Pair<Integer, String>> runningPackageUids) {
        synchronized (mLock) {
            updateLocked(deviceId, deviceOwnerPackageName, runningPackageUids);
        }
    }

    private void updateLocked(int deviceId, String deviceOwnerPackageName,
            ArraySet<Pair<Integer, String>> uidPackagePairs) {
        if (uidPackagePairs.isEmpty()) {
            mDeviceOwnerPackageNames.remove(deviceId);
            mDevicePackages.remove(deviceId);
        } else {
            mDeviceOwnerPackageNames.put(deviceId, deviceOwnerPackageName);
            mDevicePackages.put(deviceId, mapUserIdToPackages(uidPackagePairs));
        }

        // userId -> automatedPackages for this device owner.
        // This aggregates packages from all virtual devices associated with the current
        // deviceOwnerPackageName.
        final SparseArray<ArraySet<String>> deviceOwnerAutomatedPackages = new SparseArray<>();
        for (int i = 0; i < mDevicePackages.size(); ++i) {
            final int id = mDevicePackages.keyAt(i);
            final String ownerPackage = mDeviceOwnerPackageNames.get(id);
            if (!Objects.equals(deviceOwnerPackageName, ownerPackage)) {
                continue;
            }

            final SparseArray<ArraySet<String>> userPackageMap = mDevicePackages.valueAt(i);
            for (int j = 0; j < userPackageMap.size(); ++j) {
                final int userId = userPackageMap.keyAt(j);
                final ArraySet<String> packages = userPackageMap.valueAt(j);
                if (!deviceOwnerAutomatedPackages.contains(userId)) {
                    deviceOwnerAutomatedPackages.put(userId, new ArraySet<>());
                }
                deviceOwnerAutomatedPackages.get(userId).addAll(packages);
            }
        }

        SparseArray<ArraySet<String>> oldPackages = mAutomatedPackages.get(deviceOwnerPackageName);
        // Collect all user IDs from both old and new states.
        final ArraySet<Integer> allUserIds = new ArraySet<>();
        if (oldPackages != null) {
            for (int i = 0; i < oldPackages.size(); i++) {
                allUserIds.add(oldPackages.keyAt(i));
            }
        }
        for (int i = 0; i < deviceOwnerAutomatedPackages.size(); i++) {
            allUserIds.add(deviceOwnerAutomatedPackages.keyAt(i));
        }

        // For each user, compare the old and new package sets and notify if they differ.
        for (int i = 0; i < allUserIds.size(); i++) {
            final int userId = allUserIds.valueAt(i);
            final ArraySet<String> oldUserPackages =
                    (oldPackages == null) ? null : oldPackages.get(userId);
            final ArraySet<String> newUserPackages = deviceOwnerAutomatedPackages.get(userId);

            if (Objects.equals(oldUserPackages, newUserPackages)) {
                continue;
            }

            // A change occurred. Notify with the new list of packages.
            // If the new list is null, the user's packages were all removed, so notify with an
            // empty list.
            final List<String> packagesToReport = (newUserPackages == null)
                    ? Collections.emptyList()
                    : new ArrayList<>(newUserPackages);
            notifyAutomatedPackagesChanged(deviceOwnerPackageName, packagesToReport, userId);
        }

        // Update the main automated packages map with the newly computed state.
        if (deviceOwnerAutomatedPackages.size() == 0) {
            mAutomatedPackages.remove(deviceOwnerPackageName);
        } else {
            mAutomatedPackages.put(deviceOwnerPackageName, deviceOwnerAutomatedPackages);
        }
    }

    private SparseArray<ArraySet<String>> mapUserIdToPackages(
            ArraySet<Pair<Integer, String>> uidPackagePairs) {
        final SparseArray<ArraySet<String>> userIdToPackages = new SparseArray<>();
        for (int i = 0; i < uidPackagePairs.size(); ++i) {
            final Pair<Integer, String> uidAndPackage = uidPackagePairs.valueAt(i);
            final int uid = uidAndPackage.first;
            final int userId = UserHandle.getUserId(uid);
            if (!userIdToPackages.contains(userId)) {
                userIdToPackages.put(userId, new ArraySet<>());
            }
            final String packageName = uidAndPackage.second;
            if (packageName != null) {
                userIdToPackages.get(userId).add(packageName);
            }
        }
        return userIdToPackages;
    }

    private void notifyAutomatedPackagesChanged(
            String ownerPackageName, List<String> packageNames, int userId) {
        final UserHandle userHandle = UserHandle.of(userId);
        mHandler.post(() -> {
            synchronized (mLock) {
                mAutomatedPackageListeners.broadcast(listener -> {
                    try {
                        listener.onAutomatedPackagesChanged(
                                ownerPackageName, packageNames, userHandle);
                    } catch (RemoteException e) {
                        Slog.w(TAG, "Failed to invoke onAutomatedPackagesChanged listener: "
                                + e.getMessage());
                    }
                });
            }
        });
    }

    private final class StopAutomationResultReceiver extends ResultReceiver {

        private final Pair<Integer, String> mUidPackagePair;
        private final Runnable mCloseVirtualDevice;

        StopAutomationResultReceiver(@NonNull Pair<Integer, String> uidPackagePair,
                @NonNull Runnable closeVirtualDevice) {
            super(mHandler);
            mUidPackagePair = uidPackagePair;
            mCloseVirtualDevice = closeVirtualDevice;
        }

        @Override
        protected void onReceiveResult(int resultCode, Bundle data) {
            if (resultCode == Activity.RESULT_OK) {
                synchronized (mLock) {
                    mInterceptedLaunches.add(mUidPackagePair);
                }
                mHandler.post(mCloseVirtualDevice);
            }
        }

        /**
         * Convert this instance of a "locally-defined" ResultReceiver to an instance of
         * {@link android.os.ResultReceiver} itself, which the receiving process will be able to
         * unmarshall.
         */
        private ResultReceiver prepareForIpc() {
            final Parcel parcel = Parcel.obtain();
            writeToParcel(parcel, 0);
            parcel.setDataPosition(0);

            final ResultReceiver ipcFriendly = ResultReceiver.CREATOR.createFromParcel(parcel);
            parcel.recycle();

            return ipcFriendly;
        }
    }
}
