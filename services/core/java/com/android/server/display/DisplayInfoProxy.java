/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.server.display;

import static android.view.DisplayInfo.DisplayInfoGroup.displayInfoGroupsToString;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.hardware.display.DisplayManagerGlobal;
import android.view.DisplayInfo;

import androidx.annotation.IntRange;

import java.io.PrintWriter;

/**
 * A proxy class for {@link DisplayInfo} objects.
 * This class wraps access to {@link DisplayInfo} objects by {@link LogicalDisplay} to allow
 * invalidating caches when the display information changes.
 */
public class DisplayInfoProxy {
    private DisplayInfo mInfo;

    /**
     * The bitmask of {@link DisplayInfo} groups that have changed in the last update.
     * Related to {@link DisplayInfo#getBasicChangedGroups(DisplayInfo)}.
     */
    private int mDisplayInfoGroupsChanged = 0;

    /** The source of the last {@link DisplayInfo} change. */
    private DisplayInfo.DisplayInfoChangeSource mDisplayInfoChangeSource;

    public DisplayInfoProxy(@Nullable DisplayInfo info) {
        mInfo = info;
    }

    /**
     * Sets the current {@link DisplayInfo} and invalidates system-wide caches. This method does
     * not track changes or the source of changes, and should only be used if these are recorded
     * elsewhere. To update the cache when something has triggered a change in
     * the {@link DisplayInfo} object use {@link #set(int, DisplayInfo.DisplayInfoChangeSource)}
     * or {@link #set(DisplayInfo, DisplayInfo, DisplayInfo.DisplayInfoChangeSource)}.
     *
     * @param newInfo The new {@link DisplayInfo} to apply. Must not be null.
     */
    public void set(@NonNull DisplayInfo newInfo) {
        mInfo = newInfo;
        DisplayManagerGlobal.invalidateLocalDisplayInfoCaches();
    }

    /**
     * Invalidates the current display information by computing the differences between an old and
     * new state, and then setting the new state.
     *
     * @param oldInfo The previous {@link DisplayInfo} state. Must not be null.
     * @param newInfo The new {@link DisplayInfo} state. Must not be null. If it is null,
     *                use the other updateCache method with all groups marked as changed.
     * @param source The source of the change.
     */
    public void set(@NonNull DisplayInfo oldInfo, @NonNull DisplayInfo newInfo,
            DisplayInfo.DisplayInfoChangeSource source) {
        mDisplayInfoGroupsChanged = oldInfo.getBasicChangedGroups(newInfo);
        mDisplayInfoChangeSource = source;
        set(newInfo);
    }

    /**
     * Invalidates the current display information using a pre-computed set of changed groups.
     *
     * This is an optimization for targeted updates where re-computing the full
     * {@link DisplayInfo} is unnecessary.
     *
     * @param changedGroups A positive integer bitmask representing the {@link DisplayInfo} groups
     *                      that have changed.
     * @param source The source of the change.
     */
    public void set(@IntRange(from = 1) int changedGroups,
            DisplayInfo.DisplayInfoChangeSource source) {
        mDisplayInfoGroupsChanged = changedGroups;
        mDisplayInfoChangeSource = source;
        mInfo = null;
        DisplayManagerGlobal.invalidateLocalDisplayInfoCaches();
    }

    /**
     * Returns the current {@link DisplayInfo}.
     *
     * This info <b>must</b> be treated as immutable. Modifying the returned object is undefined
     * behavior that <b>will</b> result in inconsistent states across the system.
     *
     * @return the current {@link DisplayInfo}
     */
    @Nullable
    public DisplayInfo get() {
        return mInfo;
    }

    /** Gets the display info groups that have changed. */
    public int getDisplayInfoGroupsChanged() {
        return mDisplayInfoGroupsChanged;
    }

    /** Gets the source of the last update. */
    public DisplayInfo.DisplayInfoChangeSource getDisplayInfoChangeSource() {
        return mDisplayInfoChangeSource;
    }

    /** Dump the state of this object for debugging purposes. */
    public void dumpLocked(PrintWriter pw) {
        pw.println("mDisplayInfoGroupsChanged="
                + displayInfoGroupsToString(mDisplayInfoGroupsChanged));
        pw.println("mDisplayInfoChangeSource=" + mDisplayInfoChangeSource);
    }
}