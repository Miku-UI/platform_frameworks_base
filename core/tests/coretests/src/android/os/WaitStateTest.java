/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License athasEqualMessages
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.os;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.os.WaitState;

import android.util.Log;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class WaitStateTest {
    private static final String TAG = "WaitStateTest";

    @Test
    public void testCountOperations() {
        long state = WaitState.initCounter();

        assertTrue(WaitState.isCounter(state));
        assertEquals(WaitState.getCount(state), 0);
        Log.d(TAG, "state = " + Long.toHexString(state));

        state = WaitState.incrementCounter(state);
        Log.d(TAG, "state = " + Long.toHexString(state));
        assertTrue(WaitState.isCounter(state));
        assertEquals(WaitState.getCount(state), 1);
        for (int i = 2; i < 10; i++) {
            state = WaitState.incrementCounter(state);
            assertEquals(WaitState.getCount(state), i);
            assertTrue(WaitState.isCounter(state));
        }
    }

    @Test
    public void testDeadlineOperations() {
        final long now = SystemClock.uptimeMillis();
        long state = WaitState.composeDeadline(now, false);
        assertFalse(WaitState.hasSyncBarrier(state));
        long tsMillis = WaitState.getTSMillis(state);
        assertEquals(tsMillis, now);

        state = WaitState.incrementDeadline(state);
        state = WaitState.incrementDeadline(state);
        tsMillis = WaitState.getTSMillis(state);
        assertEquals(tsMillis, now);


        /* Do the same but with sync barrier bit set */
        state = WaitState.composeDeadline(now, true);
        assertTrue(WaitState.hasSyncBarrier(state));
        tsMillis = WaitState.getTSMillis(state);
        assertEquals(tsMillis, now);

        state = WaitState.incrementDeadline(state);
        state = WaitState.incrementDeadline(state);
        tsMillis = WaitState.getTSMillis(state);
        assertEquals(tsMillis, now);
    }
}
