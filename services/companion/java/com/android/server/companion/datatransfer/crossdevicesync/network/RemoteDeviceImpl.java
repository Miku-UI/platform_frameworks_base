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
package com.android.server.companion.datatransfer.crossdevicesync.network;

import static java.util.Objects.requireNonNull;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.companion.AssociationInfo;
import android.util.IndentingPrintWriter;

/** A class representing a remote device. */
class RemoteDeviceImpl implements NetworkManager.RemoteDevice {
    private static final int FLAG_HAS_TRANSPORT = 1;
    private static final int FLAG_BLE_APPEARED = 1 << 1;
    private static final int FLAG_BT_CONNECTED = 1 << 2;
    private static final int FLAG_SELF_MANAGED_APPEARED = 1 << 3;
    private static final int FLAG_SELF_MANAGED_NEARBY = 1 << 4;
    private static final int FLAG_ASSOCIATION_REMOVED = 1 << 5;

    private final int mAssociationId;
    private final int mUserId;
    @Nullable private final CharSequence mDisplayName;
    @Nullable private final String mDeviceProfile;
    private int mFlags;
    private AssociationInfo mAssociationInfoCache;

    RemoteDeviceImpl(AssociationInfo associationInfo) {
        mAssociationId = associationInfo.getId();
        mUserId = associationInfo.getUserId();
        mDisplayName = associationInfo.getDisplayName();
        mDeviceProfile = associationInfo.getDeviceProfile();
        mAssociationInfoCache = associationInfo;
    }

    @Override
    public int getAssociationId() {
        return mAssociationId;
    }

    @Override
    public AssociationInfo getAssociationInfoCache() {
        return mAssociationInfoCache;
    }

    /** Update the cached {@link AssociationInfo}. */
    public void setAssociationInfoCache(@NonNull AssociationInfo associationInfo) {
        mAssociationInfoCache = requireNonNull(associationInfo);
    }

    @Nullable
    @Override
    public CharSequence getDisplayName() {
        return mDisplayName;
    }

    @Override
    public int getUserId() {
        return mUserId;
    }

    @Nullable
    @Override
    public String getDeviceProfile() {
        return mDeviceProfile;
    }

    @Override
    public boolean hasTransport() {
        return (mFlags & FLAG_HAS_TRANSPORT) != 0;
    }

    /** Sets whether the device has a transport connected. */
    public boolean setHasTransport(boolean hasTransport) {
        return setFlag(FLAG_HAS_TRANSPORT, hasTransport);
    }

    @Override
    public boolean isBleAppeared() {
        return (mFlags & FLAG_BLE_APPEARED) != 0;
    }

    /** Sets whether the device has appeared via BLE. */
    public boolean setBleAppeared(boolean appeared) {
        return setFlag(FLAG_BLE_APPEARED, appeared);
    }

    @Override
    public boolean isBtConnected() {
        return (mFlags & FLAG_BT_CONNECTED) != 0;
    }

    /** Sets whether the device is connected via Bluetooth. */
    public boolean setBtConnected(boolean connected) {
        return setFlag(FLAG_BT_CONNECTED, connected);
    }

    @Override
    public boolean isSelfManagedAppeared() {
        return (mFlags & FLAG_SELF_MANAGED_APPEARED) != 0;
    }

    /** Sets whether the device has self reported appeared. */
    public boolean setSelfManagedAppeared(boolean appeared) {
        return setFlag(FLAG_SELF_MANAGED_APPEARED, appeared);
    }

    @Override
    public boolean isSelfManagedNearby() {
        return (mFlags & FLAG_SELF_MANAGED_NEARBY) != 0;
    }

    /** Sets whether the device is self reported nearby. */
    public boolean setSelfManagedNearby(boolean nearby) {
        return setFlag(FLAG_SELF_MANAGED_NEARBY, nearby);
    }

    @Override
    public boolean isAssociationRemoved() {
        return (mFlags & FLAG_ASSOCIATION_REMOVED) != 0;
    }

    /** Sets whether the association for this device has been removed. */
    public boolean setAssociationRemoved(boolean removed) {
        return setFlag(FLAG_ASSOCIATION_REMOVED, removed);
    }

    private boolean isFlagSet(int flag) {
        return (mFlags & flag) != 0;
    }

    private boolean setFlag(int flag, boolean value) {
        if (isFlagSet(flag) == value) {
            return false;
        }
        if (value) {
            mFlags |= flag;
        } else {
            mFlags &= ~flag;
        }
        return true;
    }

    /** Dumps the state of this object to the given writer. */
    public void dump(IndentingPrintWriter pw) {
        pw.println("RemoteDevice:");
        pw.increaseIndent();
        pw.println("associationId=" + mAssociationId);
        pw.println("userId=" + mUserId);
        pw.println("displayName='" + mDisplayName + "'");
        pw.println("deviceProfile='" + mDeviceProfile + "'");
        pw.println("associationInfoCache=" + mAssociationInfoCache);
        pw.println("hasTransport=" + hasTransport());
        pw.println("isBleAppeared=" + isBleAppeared());
        pw.println("isBtConnected=" + isBtConnected());
        pw.println("isSelfManagedAppeared=" + isSelfManagedAppeared());
        pw.println("isSelfManagedNearby=" + isSelfManagedNearby());
        pw.println("isAssociationRemoved=" + isAssociationRemoved());
        pw.decreaseIndent();
    }

    @Override
    public String toString() {
        return "RemoteDevice {"
                + "associationId="
                + mAssociationId
                + ", displayName='"
                + mDisplayName
                + "', deviceProfile='"
                + mDeviceProfile
                + ", userId="
                + getUserId()
                + "', hasTransport="
                + hasTransport()
                + ", isBleAppeared="
                + isBleAppeared()
                + ", isBtConnected="
                + isBtConnected()
                + ", isSelfManagedAppeared="
                + isSelfManagedAppeared()
                + ", isSelfManagedNearby="
                + isSelfManagedNearby()
                + ", isAssociationRemoved="
                + isAssociationRemoved()
                + "}";
    }
}
