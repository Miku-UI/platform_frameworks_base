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

package com.android.server.wm.utils;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.view.DisplayInfo;

import java.util.EnumSet;

/**
 * Helper class to copy only subset of fields of DisplayInfo object or to perform
 * comparison operation between DisplayInfo objects only with a subset of fields.
 */
public class DisplayInfoOverrides {

    /**
     * Set of DisplayInfo fields that are overridden in DisplayManager using values from
     * WindowManager. Any changes to the fields in here need to update {@link #WM_OVERRIDE_GROUPS}.
     */
    public static final DisplayInfoFieldsUpdater WM_OVERRIDE_FIELDS = (out, source) -> {
        out.appWidth = source.appWidth;
        out.appHeight = source.appHeight;
        out.smallestNominalAppWidth = source.smallestNominalAppWidth;
        out.smallestNominalAppHeight = source.smallestNominalAppHeight;
        out.largestNominalAppWidth = source.largestNominalAppWidth;
        out.largestNominalAppHeight = source.largestNominalAppHeight;
        out.logicalWidth = source.logicalWidth;
        out.logicalHeight = source.logicalHeight;
        out.physicalXDpi = source.physicalXDpi;
        out.physicalYDpi = source.physicalYDpi;
        out.rotation = source.rotation;
        out.displayCutout = source.displayCutout;
        out.logicalDensityDpi = source.logicalDensityDpi;
        out.roundedCorners = source.roundedCorners;
        out.displayShape = source.displayShape;
    };

    /**
     * A bitmask of all {@link DisplayInfo.DisplayInfoGroup}s that can be affected by
     * {@link #WM_OVERRIDE_FIELDS}. Must be in sync with {@link #WM_OVERRIDE_FIELDS}
     * and {@link #WM_OVERRIDE_GROUPS_ENUMSET}.
     */
    public static final int WM_OVERRIDE_GROUPS =
            DisplayInfo.DisplayInfoGroup.ORIENTATION_AND_ROTATION.getMask()
                    | DisplayInfo.DisplayInfoGroup.DIMENSIONS_AND_SHAPES.getMask();

    /**
     * Same as {@link #WM_OVERRIDE_GROUPS}, but as an EnumSet.
     * This is used to optimize part of the code
     * in {@link LogicalDisplay#setDisplayInfoOverrideFromWindowManagerLocked}.
     */
    public static final EnumSet<DisplayInfo.DisplayInfoGroup> WM_OVERRIDE_GROUPS_ENUMSET =
            EnumSet.of(DisplayInfo.DisplayInfoGroup.ORIENTATION_AND_ROTATION,
                    DisplayInfo.DisplayInfoGroup.DIMENSIONS_AND_SHAPES);

    /**
     * Gets {@param base} DisplayInfo, overrides WindowManager-specific overrides using
     * {@param override} and writes the result to {@param out}
     */
    public static void copyDisplayInfoFields(@NonNull DisplayInfo out,
            @NonNull DisplayInfo base,
            @Nullable DisplayInfo override,
            @NonNull DisplayInfoFieldsUpdater fields) {
        out.copyFrom(base);

        if (override != null) {
            fields.setFields(out, override);
        }
    }

    /**
     * Callback interface that allows to specify a subset of fields of DisplayInfo object
     */
    public interface DisplayInfoFieldsUpdater {
        /**
         * Copies a subset of fields from {@param source} to {@param out}
         *
         * @param out    resulting DisplayInfo object
         * @param source source DisplayInfo to copy fields from
         */
        void setFields(@NonNull DisplayInfo out, @NonNull DisplayInfo source);
    }
}
