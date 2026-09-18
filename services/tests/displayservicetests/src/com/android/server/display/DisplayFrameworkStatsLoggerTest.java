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

import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;

import android.hardware.display.DisplayManagerGlobal;
import android.util.SparseIntArray;
import android.view.DisplayInfo;
import android.view.DisplayInfo.DisplayInfoGroup;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.dx.mockito.inline.extended.StaticMockitoSession;
import com.android.internal.util.FrameworkStatsLog;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

import java.util.List;

/**
 * Tests for {@link com.android.server.display.DisplayFrameworkStatsLogger}.
 *
 * <p>Build with: atest DisplayFrameworkStatsLoggerTest
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class DisplayFrameworkStatsLoggerTest {

    @InjectMocks private DisplayFrameworkStatsLogger mLogger;

    private StaticMockitoSession mFrameworkStatsLogMock;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mFrameworkStatsLogMock = mockitoSession().mockStatic(
                FrameworkStatsLog.class).startMocking();
    }

    @After
    public void teardown() {
        mFrameworkStatsLogMock.finishMocking();
    }

    @Test
    public void testLogDisplayEvents_displayAdded_writesToStatsLog() {
        // Set up a map where two UIDs were notified of the ADDED event.
        final SparseIntArray uidMap = new SparseIntArray();
        uidMap.put(1001, DisplayManagerGlobal.EVENT_DISPLAY_ADDED);
        // UID 1002 was notified of ADDED and something else.
        uidMap.put(1002, DisplayManagerGlobal.EVENT_DISPLAY_ADDED
                | DisplayManagerGlobal.EVENT_DISPLAY_REMOVED);

        final int expectedProtoType =
                FrameworkStatsLog.DISPLAY_EVENT_CALLBACK_OCCURRED__EVENT_TYPE__TYPE_DISPLAY_ADDED;

        mLogger.logDisplayEvents(uidMap);

        // Verify that a log was written for the ADDED event with a count of 2.
        ExtendedMockito.verify(() -> FrameworkStatsLog.write(
                FrameworkStatsLog.DISPLAY_EVENT_CALLBACK_OCCURRED,
                expectedProtoType,
                new int[0],
                2));
    }

    @Test
    public void testLogDisplayEvents_singleBrightnessChanged_writesToStatsLog() {
        final SparseIntArray uidMap = new SparseIntArray();
        uidMap.put(1005, DisplayManagerGlobal.EVENT_DISPLAY_BRIGHTNESS_CHANGED);
        final int expectedProtoType =
                FrameworkStatsLog
                    .DISPLAY_EVENT_CALLBACK_OCCURRED__EVENT_TYPE__TYPE_DISPLAY_BRIGHTNESS_CHANGED;

        mLogger.logDisplayEvents(uidMap);

        ExtendedMockito.verify(() ->
                FrameworkStatsLog.write(
                        FrameworkStatsLog.DISPLAY_EVENT_CALLBACK_OCCURRED,
                        expectedProtoType,
                        new int[0],
                        1));
    }

    @Test
    public void testLogDisplayEvents_unknownEvent_writesUnknownTypeToStatsLog() {
        // Use a high-order bit that doesn't map to a known event type.
        final int unknownEvent = 1 << 30;
        final SparseIntArray uidMap = new SparseIntArray();
        uidMap.put(9999, unknownEvent);

        final int expectedProtoType =
                FrameworkStatsLog.DISPLAY_EVENT_CALLBACK_OCCURRED__EVENT_TYPE__TYPE_UNKNOWN;

        mLogger.logDisplayEvents(uidMap);

        ExtendedMockito.verify(() ->
                FrameworkStatsLog.write(
                        FrameworkStatsLog.DISPLAY_EVENT_CALLBACK_OCCURRED,
                        expectedProtoType,
                        new int[0],
                        1));
    }

    // Verify the core aggregation logic of the logDisplayEvents method.
    @Test
    public void testLogDisplayEvents_multipleEvents_writesEachToStatsLog() {
        // Set up a map with overlapping event notifications.
        final SparseIntArray uidMap = new SparseIntArray();
        uidMap.put(1001, DisplayManagerGlobal.EVENT_DISPLAY_ADDED);
        uidMap.put(1002, DisplayManagerGlobal.EVENT_DISPLAY_ADDED
                | DisplayManagerGlobal.EVENT_DISPLAY_REMOVED);
        uidMap.put(1003, DisplayManagerGlobal.EVENT_DISPLAY_REMOVED);
        uidMap.put(1004, DisplayManagerGlobal.EVENT_DISPLAY_REMOVED);
        ArgumentCaptor<Integer> protoTypeCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> countCaptor = ArgumentCaptor.forClass(Integer.class);

        mLogger.logDisplayEvents(uidMap);

        ExtendedMockito.verify(() ->
                FrameworkStatsLog.write(
                eq(FrameworkStatsLog.DISPLAY_EVENT_CALLBACK_OCCURRED),
                protoTypeCaptor.capture(),
                eq(new int[0]),
                countCaptor.capture()
        ), times(2));

        // Assert that both events were logged.
        assertEquals(
                List.of(
                    FrameworkStatsLog
                            .DISPLAY_EVENT_CALLBACK_OCCURRED__EVENT_TYPE__TYPE_DISPLAY_ADDED,
                    FrameworkStatsLog
                            .DISPLAY_EVENT_CALLBACK_OCCURRED__EVENT_TYPE__TYPE_DISPLAY_REMOVED
                ),
                protoTypeCaptor.getAllValues());

        // Assert that both were logged with correct count
        assertEquals(List.of(2, 3), countCaptor.getAllValues());
    }

    @Test
    public void logDisplayInfoChanged_singleChange() {
        int changedGroups = DisplayInfoGroup.COLOR_AND_BRIGHTNESS.getMask();

        mLogger.logDisplayInfoChanged(changedGroups,
                DisplayInfo.DisplayInfoChangeSource.DISPLAY_SWAP);

        int expectedSource =
                FrameworkStatsLog.DISPLAY_INFO_CHANGED__EVENT_SOURCE__EVENT_SOURCE_DISPLAY_SWAP;
        ExtendedMockito.verify(() -> FrameworkStatsLog.write(FrameworkStatsLog.DISPLAY_INFO_CHANGED,
                    1, 0, 0, 0, 0, 1, 0, expectedSource));
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
        ExtendedMockito.verify(() ->
                FrameworkStatsLog.write(FrameworkStatsLog.DISPLAY_INFO_CHANGED,
                        3, 1, 0, 1, 0, 0, 1, expectedSource));
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

        ExtendedMockito.verify(() ->
                FrameworkStatsLog.write(FrameworkStatsLog.DISPLAY_INFO_CHANGED,
                        6, 1, 1, 1, 1, 1, 1, expectedSource));
    }

}
