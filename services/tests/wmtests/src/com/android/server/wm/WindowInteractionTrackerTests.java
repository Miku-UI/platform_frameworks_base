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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import android.platform.test.annotations.Presubmit;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@Presubmit
@RunWith(WindowTestRunner.class)
public class WindowInteractionTrackerTests extends WindowTestsBase{

    @Test
    public void testInitialization_emptyTracker() {
        WindowInteractionTracker tracker = new WindowInteractionTracker(3);
        assertNotNull(tracker.getRecentlyInteractedWindows());
        assertTrue(tracker.getRecentlyInteractedWindows().isEmpty());
        assertNull(tracker.peek());
    }

    @Test
    public void testAdd_multipleItems_withinCapacity() {
        WindowInteractionTracker tracker = new WindowInteractionTracker(3);
        WindowState ws1 = mock(WindowState.class);
        WindowState ws2 = mock(WindowState.class);

        tracker.add(ws1);
        tracker.add(ws2);

        List<WindowState> history = tracker.getRecentlyInteractedWindows();
        assertNotNull(history);
        assertEquals(2, history.size());
        // Most recent should be first
        assertEquals(ws2, history.get(0));
        assertEquals(ws1, history.get(1));
        assertEquals(ws2, tracker.peek());
    }

    @Test
    public void testAdd_exceedsCapacity() {
        WindowInteractionTracker tracker = new WindowInteractionTracker(2);
        WindowState ws1 = mock(WindowState.class);
        WindowState ws2 = mock(WindowState.class);
        WindowState ws3 = mock(WindowState.class);

        tracker.add(ws1); // History: [ws1]
        tracker.add(ws2); // History: [ws2, ws1]
        tracker.add(ws3); // History: [ws3, ws2] -> ws1 should be removed

        List<WindowState> history = tracker.getRecentlyInteractedWindows();
        assertNotNull(history);
        assertEquals(2, history.size());
        assertEquals(ws3, history.get(0)); // Most recent
        assertEquals(ws2, history.get(1));
        assertEquals(ws3, tracker.peek());
    }

    @Test
    public void testPeek_emptyTracker() {
        WindowInteractionTracker tracker = new WindowInteractionTracker(3);
        assertNull(tracker.peek());
    }

    @Test
    public void testTracker_sizeOne() {
        WindowInteractionTracker tracker = new WindowInteractionTracker(1);
        WindowState ws1 = mock(WindowState.class);
        WindowState ws2 = mock(WindowState.class);

        tracker.add(ws1); // History: [ws1]
        assertEquals(ws1, tracker.peek());
        assertEquals(1, tracker.getRecentlyInteractedWindows().size());
        assertEquals(ws1, tracker.getRecentlyInteractedWindows().get(0));


        tracker.add(ws2); // History: [ws2], ws1 removed
        assertEquals(ws2, tracker.peek());
        assertEquals(1, tracker.getRecentlyInteractedWindows().size());
        assertEquals(ws2, tracker.getRecentlyInteractedWindows().get(0));
    }
}
