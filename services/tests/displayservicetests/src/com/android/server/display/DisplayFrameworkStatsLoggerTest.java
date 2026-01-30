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

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.hardware.display.DisplayManagerGlobal;
import android.util.SparseIntArray;
import android.view.DisplayInfo;
import android.view.DisplayInfo.DisplayInfoGroup;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.internal.util.FrameworkStatsLog;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Tests for {@link com.android.server.display.DisplayFrameworkStatsLogger}.
 *
 * <p>Build with: atest DisplayFrameworkStatsLoggerTest
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class DisplayFrameworkStatsLoggerTest {

    @InjectMocks private DisplayFrameworkStatsLogger mLogger;

    @Mock private FrameworkStatsLog mFrameworkStatsLogMock;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testLogDisplayEvent_displayAdded_writesToStatsLog() {
        final int event = DisplayManagerGlobal.EVENT_DISPLAY_ADDED;
        final SparseIntArray uidMap =
                new SparseIntArray() {
                    {
                        put(1001, 1);
                        put(1002, 3);
                    }
                };
        final int expectedProtoType =
                FrameworkStatsLog.DISPLAY_EVENT_CALLBACK_OCCURRED__EVENT_TYPE__TYPE_DISPLAY_ADDED;

        mLogger.logDisplayEvent(event, uidMap);

        verify(mFrameworkStatsLogMock)
                .write(
                        FrameworkStatsLog.DISPLAY_EVENT_CALLBACK_OCCURRED,
                        expectedProtoType,
                        uidMap.copyKeys(), 2);
    }

    @Test
    public void testLogDisplayEvent_brightnessChanged_writesToStatsLog() {
        final int event = DisplayManagerGlobal.EVENT_DISPLAY_BRIGHTNESS_CHANGED;
        final SparseIntArray uidMap =
                new SparseIntArray() {
                    {
                        put(1005, 1);
                    }
                };
        final int expectedProtoType =
                FrameworkStatsLog
                    .DISPLAY_EVENT_CALLBACK_OCCURRED__EVENT_TYPE__TYPE_DISPLAY_BRIGHTNESS_CHANGED;

        mLogger.logDisplayEvent(event, uidMap);

        verify(mFrameworkStatsLogMock)
                .write(
                        FrameworkStatsLog.DISPLAY_EVENT_CALLBACK_OCCURRED,
                        expectedProtoType,
                        uidMap.copyKeys(), 1);
    }

    @Test
    public void testLogDisplayEvent_unknownEvent_writesUnknownTypeToStatsLog() {
        final int event = -1;
        final SparseIntArray uidMap =
                new SparseIntArray() {
                    {
                        put(9999, 6);
                    }
                };
        final int expectedProtoType =
                FrameworkStatsLog.DISPLAY_EVENT_CALLBACK_OCCURRED__EVENT_TYPE__TYPE_UNKNOWN;

        mLogger.logDisplayEvent(event, uidMap);

        verify(mFrameworkStatsLogMock)
                .write(
                        FrameworkStatsLog.DISPLAY_EVENT_CALLBACK_OCCURRED,
                        expectedProtoType,
                        uidMap.copyKeys(), 1);
    }

    @Test
    public void logDisplayInfoChanged_singleChange() {
        int changedGroups = DisplayInfoGroup.COLOR_AND_BRIGHTNESS.getMask();

        mLogger.logDisplayInfoChanged(changedGroups,
                DisplayInfo.DisplayInfoChangeSource.DISPLAY_SWAP);

        int expectedSource =
                FrameworkStatsLog.DISPLAY_INFO_CHANGED__EVENT_SOURCE__EVENT_SOURCE_DISPLAY_SWAP;
        verify(mFrameworkStatsLogMock)
                .write(FrameworkStatsLog.DISPLAY_INFO_CHANGED,
                    1, 0, 0, 0, 0, 1, 0, expectedSource);
    }

    @Test
    public void logDisplayInfoChanged_multipleChanges() {
        int changedGroups = DisplayInfoGroup.BASIC_PROPERTIES.getMask()
                | DisplayInfoGroup.STATE.getMask()
                | DisplayInfoGroup.ORIENTATION_AND_ROTATION.getMask();

        mLogger.logDisplayInfoChanged(changedGroups,
                DisplayInfo.DisplayInfoChangeSource.DISPLAY_MANAGER);

        int expectedSource =
                FrameworkStatsLog.DISPLAY_INFO_CHANGED__EVENT_SOURCE__EVENT_SOURCE_DISPLAY_MANAGER;
        verify(mFrameworkStatsLogMock)
                .write(FrameworkStatsLog.DISPLAY_INFO_CHANGED,
                        3, 1, 0, 1, 0, 0, 1, expectedSource);
    }

    @Test
    public void logDisplayInfoChanged_allChanges() {
        int changedGroupsMask = 0;
        for (DisplayInfoGroup group : DisplayInfoGroup.values()) {
            changedGroupsMask |= group.getMask();
        }

        mLogger.logDisplayInfoChanged(changedGroupsMask, DisplayInfo.DisplayInfoChangeSource.OTHER);

        int expectedSource =
                FrameworkStatsLog.DISPLAY_INFO_CHANGED__EVENT_SOURCE__EVENT_SOURCE_OTHER;
        verify(mFrameworkStatsLogMock)
                .write(FrameworkStatsLog.DISPLAY_INFO_CHANGED,
                        6, 1, 1, 1, 1, 1, 1, expectedSource);
    }

    @Test
    public void testDisplayInfoChanged_zeroInput_doesNotLog() {
        mLogger.logDisplayInfoChanged(0, DisplayInfo.DisplayInfoChangeSource.WINDOW_MANAGER);

        verify(mFrameworkStatsLogMock, never())
                .write(anyInt(), anyInt(), anyInt(), anyInt(),
                        anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
    }

}
