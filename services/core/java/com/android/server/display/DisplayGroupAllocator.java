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

package com.android.server.display;

import android.content.Context;
import android.util.SparseArray;
import android.view.Display;

import com.android.server.wm.DesktopModeHelper;

/**
 * DisplayGroupAllocator helps select which display groups new displays should be added to,
 * when they are added to the system. It takes into account attributes of the display, as well
 * as which display groups already exist.
 */
class DisplayGroupAllocator {
    private static final String TAG = "DisplayGroupAllocator";

    private boolean mCanDeviceEnterDesktopMode;
    private boolean mCanDefaultDisplayEnterDesktopMode;
    private Context mContext;
    private final Injector mInjector;
    private int mReason;

    public static final String GROUP_TYPE_PRIMARY = "";
    public static final String GROUP_TYPE_SECONDARY = "secondary_mode";
    public static final int REASON_NON_DESKTOP = 0;
    public static final int REASON_PROJECTED = 1;
    public static final int REASON_EXTENDED = 2;
    public static final int REASON_FALLBACK = 3;


    DisplayGroupAllocator(Context context) {
        this(context, new Injector());
    }

    DisplayGroupAllocator(Context context, Injector injector) {
        mInjector = injector;
        mContext = context;
    }

    public void initLater(Context context) {
        mContext = context;
        mCanDeviceEnterDesktopMode = mInjector.canEnterDesktopMode(mContext);
        mCanDefaultDisplayEnterDesktopMode =
                mInjector.isDesktopModeSupportedOnInternalDisplay(mContext);
    }

    /**
     * Decides which group type is needed for a given display content mode.
     * Locked on DisplayManager mSyncRoot.
     * Sets mReason, for logging purposes.
     */
    public String decideRequiredGroupTypeLocked(LogicalDisplay display, int type) {
        mReason = getContentModeForDisplayLocked(display, type);
        return switch (mReason) {
            case REASON_NON_DESKTOP, REASON_EXTENDED, REASON_FALLBACK -> GROUP_TYPE_PRIMARY;
            case REASON_PROJECTED -> GROUP_TYPE_SECONDARY;
            default -> GROUP_TYPE_PRIMARY;
        };
    }


    /**
     * Returns the first suitable group for a display to be sorted into, given the required group
     * type.
     * @param requiredGroupType which content mode group this display should be used in
     * @param displayGroups array of existing display groups.
     * @return Id of group that display should be sorted into
     */
    public static int calculateGroupId(String requiredGroupType,
            SparseArray<DisplayGroup> displayGroups) {
        // go through display groups, find one group with the correct tag.
        final int size = displayGroups.size();
        int calculatedGroup = Display.INVALID_DISPLAY_GROUP;
        for (int i = 0; i < size; i++) {
            final DisplayGroup displayGroup = displayGroups.valueAt(i);
            if (displayGroup.getGroupName().equals(requiredGroupType)) {
                calculatedGroup = displayGroups.keyAt(i);
                break;
            }
        }
        return calculatedGroup;
    }

    /**
     * Calculates whether this display supports desktop mode
     */
    public boolean isDesktopModeSupportedOnDisplayLocked(LogicalDisplay display, int type) {
        if (!mCanDeviceEnterDesktopMode) {
            return false;
        }

        if (type == Display.TYPE_INTERNAL) {
            return mCanDefaultDisplayEnterDesktopMode;
        }

        if (type == Display.TYPE_EXTERNAL || type == Display.TYPE_OVERLAY) {
            return mInjector.canDisplayHostTasksLocked(display);
        }
        return false;
    }

    /**
     * Decides on the content mode that the display should be using.
     */
    public int getContentModeForDisplayLocked(LogicalDisplay display, int type) {
        // conditions for non desktop mode;
        // desktop mode not supported on device or this display
        // or display is internal, and should be in a different group to the
        // projected and extended mode displays.
        if (!mCanDeviceEnterDesktopMode || type == Display.TYPE_INTERNAL
                || type == Display.TYPE_OVERLAY) {
            return REASON_NON_DESKTOP;
        }

        // conditions for projected mode;
        // desktop mode supported on device, external display, but not internal
        if (mCanDeviceEnterDesktopMode
                && isDesktopModeSupportedOnDisplayLocked(display, type)
                && !mCanDefaultDisplayEnterDesktopMode) {
            return REASON_PROJECTED;
        }

        // conditions for extended mode;
        // desktop mode supported on both displays
        if (mCanDeviceEnterDesktopMode
                && isDesktopModeSupportedOnDisplayLocked(display, type)
                && mCanDefaultDisplayEnterDesktopMode) {
            return REASON_EXTENDED;
        }
        return REASON_FALLBACK;
    }

    public int getReason() {
        return mReason;
    }

    static class Injector {

        private boolean canEnterDesktopMode(Context context) {
            return DesktopModeHelper.canEnterDesktopMode(context);
        }

        boolean isDesktopModeSupportedOnInternalDisplay(Context context) {
            return DesktopModeHelper.isDesktopModeSupportedOnInternalDisplay(context);
        }

        boolean canDisplayHostTasksLocked(LogicalDisplay display) {
            return display.canHostTasksLocked();
        }
    }
}
