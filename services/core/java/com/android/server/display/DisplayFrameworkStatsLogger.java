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

import android.hardware.display.DisplayManager;
import android.hardware.display.DisplayManagerGlobal;
import android.util.SparseIntArray;
import android.view.DisplayInfo;
import android.view.DisplayInfo.DisplayInfoGroup;

import com.android.internal.util.FrameworkStatsLog;

public final class DisplayFrameworkStatsLogger {

    /** Logs DisplayEventCallbackOccurred push atom */
    public void logDisplayEvent(@DisplayManagerGlobal.DisplayEvent int event,
            SparseIntArray notifiedUids) {
        FrameworkStatsLog.write(
                FrameworkStatsLog.DISPLAY_EVENT_CALLBACK_OCCURRED,
                toProtoEventType(event),
                notifiedUids.copyKeys(),
                notifiedUids.size());
    }

    /** Logs DisplayInfoChanged push atom */
    public void logDisplayInfoChanged(int changedGroups,
            DisplayInfo.DisplayInfoChangeSource source) {
        FrameworkStatsLog.write(
                FrameworkStatsLog.DISPLAY_INFO_CHANGED,
                Integer.bitCount(changedGroups),
                (changedGroups & DisplayInfoGroup.BASIC_PROPERTIES.getMask()) > 0 ? 1 : 0,
                (changedGroups & DisplayInfoGroup.DIMENSIONS_AND_SHAPES.getMask()) > 0 ? 1 : 0,
                (changedGroups
                        & DisplayInfoGroup.ORIENTATION_AND_ROTATION.getMask()) > 0 ? 1 : 0,
                (changedGroups & DisplayInfoGroup.REFRESH_RATE_AND_MODE.getMask()) > 0 ? 1 : 0,
                (changedGroups & DisplayInfoGroup.COLOR_AND_BRIGHTNESS.getMask()) > 0 ? 1 : 0,
                (changedGroups & DisplayInfoGroup.STATE.getMask()) > 0 ? 1 : 0,
                toProtoEventSource(source));
    }

    /** Maps DisplayInfoChangeSource to atom */
    private int toProtoEventSource(DisplayInfo.DisplayInfoChangeSource source) {
        return switch (source) {
            case DISPLAY_MANAGER ->
                FrameworkStatsLog.DISPLAY_INFO_CHANGED__EVENT_SOURCE__EVENT_SOURCE_DISPLAY_MANAGER;
            case WINDOW_MANAGER ->
                FrameworkStatsLog.DISPLAY_INFO_CHANGED__EVENT_SOURCE__EVENT_SOURCE_WINDOW_MANAGER;
            case DISPLAY_SWAP ->
                FrameworkStatsLog.DISPLAY_INFO_CHANGED__EVENT_SOURCE__EVENT_SOURCE_DISPLAY_SWAP;
            case OTHER -> FrameworkStatsLog.DISPLAY_INFO_CHANGED__EVENT_SOURCE__EVENT_SOURCE_OTHER;
            default -> FrameworkStatsLog.DISPLAY_INFO_CHANGED__EVENT_SOURCE__EVENT_SOURCE_UNKNOWN;
        };
    }

    /**
     * Maps DisplayEvent to atom. Default case "unknown" is required when defining an atom.
     * Currently private display events {@link DisplayManager.PrivateEventType} are marked as
     * unknown.
     */
    private int toProtoEventType(@DisplayManagerGlobal.DisplayEvent int event) {
        return switch (event) {
            case DisplayManagerGlobal.EVENT_DISPLAY_ADDED ->
                FrameworkStatsLog
                    .DISPLAY_EVENT_CALLBACK_OCCURRED__EVENT_TYPE__TYPE_DISPLAY_ADDED;
            case DisplayManagerGlobal.EVENT_DISPLAY_REMOVED ->
                FrameworkStatsLog
                    .DISPLAY_EVENT_CALLBACK_OCCURRED__EVENT_TYPE__TYPE_DISPLAY_REMOVED;
            case DisplayManagerGlobal.EVENT_DISPLAY_BASIC_CHANGED ->
                FrameworkStatsLog
                    .DISPLAY_EVENT_CALLBACK_OCCURRED__EVENT_TYPE__TYPE_DISPLAY_CHANGED;
            case DisplayManagerGlobal.EVENT_DISPLAY_REFRESH_RATE_CHANGED ->
                FrameworkStatsLog
                    .DISPLAY_EVENT_CALLBACK_OCCURRED__EVENT_TYPE__TYPE_DISPLAY_REFRESH_RATE_CHANGED;
            case DisplayManagerGlobal.EVENT_DISPLAY_STATE_CHANGED ->
                FrameworkStatsLog
                    .DISPLAY_EVENT_CALLBACK_OCCURRED__EVENT_TYPE__TYPE_DISPLAY_STATE_CHANGED;
            case DisplayManagerGlobal.EVENT_DISPLAY_BRIGHTNESS_CHANGED ->
                FrameworkStatsLog
                    .DISPLAY_EVENT_CALLBACK_OCCURRED__EVENT_TYPE__TYPE_DISPLAY_BRIGHTNESS_CHANGED;
            default -> FrameworkStatsLog.DISPLAY_EVENT_CALLBACK_OCCURRED__EVENT_TYPE__TYPE_UNKNOWN;
        };
    }
}
