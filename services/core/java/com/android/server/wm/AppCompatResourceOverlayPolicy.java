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

package com.android.server.wm;

import static android.content.Context.DEVICE_ID_DEFAULT;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.om.OverlayConstraint;
import android.content.pm.ApplicationInfo;
import android.os.UserHandle;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Slog;

import com.android.server.LocalServices;
import com.android.server.companion.virtual.VirtualDeviceManagerInternal;
import com.android.server.om.OverlayManagerInternal;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tracks resource overlays and their constraints for an {@link ActivityRecord}, and infers whether
 * the addition/removal of an overlay should actually affect this {@link ActivityRecord} during a
 * configuration change, given its displayId and deviceId.
 */
// TODO(b/454293961): Explore if display-specific configuration changes can be applied for
// RROs with constraints, and if so, then remove this class.
final class AppCompatResourceOverlayPolicy {
    private static final String TAG = "AppCompatResourceOverlayPolicy";

    private final Map<String, List<OverlayConstraint>> mLastReportedOverlayPathsToConstraintsMap =
            new ArrayMap<>();
    private final ActivityRecord mActivityRecord;
    @Nullable
    private VirtualDeviceManagerInternal mVirtualDeviceManagerInternal;
    @Nullable
    private OverlayManagerInternal mOverlayManagerInternal;
    private int mDisplayId;
    private int mDeviceId;

    AppCompatResourceOverlayPolicy(@NonNull ActivityRecord activityRecord) {
        mActivityRecord = activityRecord;
        mVirtualDeviceManagerInternal =
                LocalServices.getService(VirtualDeviceManagerInternal.class);
        mOverlayManagerInternal = LocalServices.getService(OverlayManagerInternal.class);
        if (mOverlayManagerInternal == null) {
            Slog.e(TAG, "constructor: Failed to find OverlayManagerService");
        }
        if (mVirtualDeviceManagerInternal == null) {
            // Some devices don't have VirtualDeviceManagerService, so log as a warning.
            Slog.w(TAG, "constructor: Failed to find VirtualDeviceManagerService");
        }
        // Make note of the overlay paths and their constraints when an ActivityRecord is created.
        initializeReportedOverlayPaths();
    }

    void setDisplayId(int displayId) {
        if (mDisplayId == displayId) {
            return;
        }

        mDisplayId = displayId;
        if (mVirtualDeviceManagerInternal == null) {
            mVirtualDeviceManagerInternal =
                    LocalServices.getService(VirtualDeviceManagerInternal.class);
        }
        if (mVirtualDeviceManagerInternal != null) {
            mDeviceId = mVirtualDeviceManagerInternal.getDeviceIdForDisplayId(mDisplayId);
        } else {
            Slog.w(TAG, "setDisplayId: Failed to find VirtualDeviceManagerService");
            mDeviceId = DEVICE_ID_DEFAULT;
        }
    }

    boolean doResourceOverlayChangesAffectActivity() {
        if (mOverlayManagerInternal == null) {
            mOverlayManagerInternal =
                    LocalServices.getService(OverlayManagerInternal.class);
        }
        if (mOverlayManagerInternal == null) {
            Slog.e(TAG, "doResourceOverlayChangesAffectActivity: Failed to find "
                    + "OverlayManagerService");
            // If OverlayManagerService doesn't exist at this point, then we can't make any
            // decisions regarding overlay constraints, and we have no choice but to assume that
            // any new resource overlay applied at this point is going to affect the activity.
            return true;
        }

        final ApplicationInfo applicationInfo = mActivityRecord.info.applicationInfo;
        final Set<String> overlayPathsAdded = new ArraySet<>();
        final Set<String> overlayPathsRemoved = new ArraySet<>();
        calculateOverlayPathChanges(applicationInfo.overlayPaths, overlayPathsAdded,
                overlayPathsRemoved);

        boolean result = false;

        final Iterator<String> overlayPathsRemovedIterator = overlayPathsRemoved.iterator();
        while (overlayPathsRemovedIterator.hasNext()) {
            final List<OverlayConstraint> overlayConstraints =
                    mLastReportedOverlayPathsToConstraintsMap.remove(
                            overlayPathsRemovedIterator.next());
            result |= matchesAnyConstraint(overlayConstraints);
        }

        final int userId = UserHandle.getUserId(applicationInfo.uid);
        final Iterator<String> overlayPathsAddedIterator = overlayPathsAdded.iterator();
        while (overlayPathsAddedIterator.hasNext()) {
            final String overlayPath = overlayPathsAddedIterator.next();
            final List<OverlayConstraint> overlayConstraints =
                    mOverlayManagerInternal.getOverlayConstraints(overlayPath, userId);
            result |= matchesAnyConstraint(overlayConstraints);

            mLastReportedOverlayPathsToConstraintsMap.put(overlayPath, overlayConstraints);
        }

        Slog.v(TAG, "doResourceOverlayChangesAffectActivity: result: " + result
                + " componentName: " + mActivityRecord.mActivityComponent
                + " overlayPathsAdded: " + overlayPathsAdded
                + " overlayPathsRemoved: " + overlayPathsRemoved
                + " displayId: " + mDisplayId + " deviceId: " + mDeviceId);

        return result;
    }

    private void calculateOverlayPathChanges(@Nullable String[] currentOverlayPaths,
            @NonNull Set<String> overlayPathsAdded, @NonNull Set<String> overlayPathsRemoved) {
        final Set<String> allOverlayPaths = new ArraySet<>();
        if (currentOverlayPaths != null) {
            for (int i = 0; i < currentOverlayPaths.length; i++) {
                final String overlayPath = currentOverlayPaths[i];
                allOverlayPaths.add(overlayPath);
                if (!mLastReportedOverlayPathsToConstraintsMap.containsKey(overlayPath)) {
                    overlayPathsAdded.add(overlayPath);
                }
            }
        }
        final Iterator<String> lastReportedOverlayPathsIterator =
                mLastReportedOverlayPathsToConstraintsMap.keySet().iterator();
        while (lastReportedOverlayPathsIterator.hasNext()) {
            final String overlayPath = lastReportedOverlayPathsIterator.next();
            if (!allOverlayPaths.contains(overlayPath)) {
                overlayPathsRemoved.add(overlayPath);
            }
        }
    }

    private boolean matchesAnyConstraint(@NonNull List<OverlayConstraint> overlayConstraints) {
        if (overlayConstraints.isEmpty()) {
            // An overlay was added/removed without any constraints, so its addition/removal
            // affects everything. We have an actual change here.
            return true;
        }

        for (int i = 0; i < overlayConstraints.size(); i++) {
            final OverlayConstraint overlayConstraint = overlayConstraints.get(i);
            if (matchesConstraint(overlayConstraint)) {
                // An overlay was added/removed whose constraints match the current display/device.
                // We have an actual change here.
                return true;
            }
        }

        return false;
    }

    private boolean matchesConstraint(@NonNull OverlayConstraint overlayConstraint) {
        return (overlayConstraint.getType() == OverlayConstraint.TYPE_DEVICE_ID
                && overlayConstraint.getValue() == mDeviceId)
                || (overlayConstraint.getType() == OverlayConstraint.TYPE_DISPLAY_ID
                && overlayConstraint.getValue() == mDisplayId);
    }

    private void initializeReportedOverlayPaths() {
        final String[] overlayPaths = mActivityRecord.info.applicationInfo.overlayPaths;
        if (overlayPaths == null) {
            return;
        }

        final int userId = UserHandle.getUserId(mActivityRecord.info.getUid());
        for (int i = 0; i < overlayPaths.length; i++) {
            final String overlayPath = overlayPaths[i];
            mLastReportedOverlayPathsToConstraintsMap.put(overlayPath,
                    mOverlayManagerInternal != null
                            ? mOverlayManagerInternal.getOverlayConstraints(overlayPath, userId)
                            : Collections.emptyList());
        }
    }
}
