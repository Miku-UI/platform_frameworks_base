/*
 * Copyright 2025 The Android Open Source Project
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

package android.app.timezonedetector;

import static android.app.time.ParcelableTestSupport.assertRoundTripParcelable;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.platform.test.annotations.Presubmit;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.TimeZone;

@RunWith(AndroidJUnit4.class)
@SmallTest
@Presubmit
public class NitzSignalTest {

    private static final long RECEIPT_ELAPSED_MILLIS = 1234L;
    private static final long AGE_MILLIS = 5678L;
    private static final int ZONE_OFFSET = 3600000;
    private static final Integer DST_OFFSET = 3600000;
    private static final long CURRENT_TIME_MILLIS = 987654321L;
    private static final TimeZone EMU_HOST_TZ = TimeZone.getTimeZone("America/Los_Angeles");

    @Test
    public void testEquals() {
        NitzSignal signal1 =
                new NitzSignal(
                        RECEIPT_ELAPSED_MILLIS,
                        AGE_MILLIS,
                        ZONE_OFFSET,
                        DST_OFFSET,
                        CURRENT_TIME_MILLIS,
                        EMU_HOST_TZ);
        NitzSignal signal2 =
                new NitzSignal(
                        RECEIPT_ELAPSED_MILLIS,
                        AGE_MILLIS,
                        ZONE_OFFSET,
                        DST_OFFSET,
                        CURRENT_TIME_MILLIS,
                        EMU_HOST_TZ);
        assertEquals(signal1, signal2);
        assertEquals(signal1.hashCode(), signal2.hashCode());

        NitzSignal signal3 =
                new NitzSignal(
                        RECEIPT_ELAPSED_MILLIS + 1,
                        AGE_MILLIS,
                        ZONE_OFFSET,
                        DST_OFFSET,
                        CURRENT_TIME_MILLIS,
                        EMU_HOST_TZ);
        assertNotEquals(signal1, signal3);

        NitzSignal signal4 =
                new NitzSignal(
                        RECEIPT_ELAPSED_MILLIS,
                        AGE_MILLIS + 1,
                        ZONE_OFFSET,
                        DST_OFFSET,
                        CURRENT_TIME_MILLIS,
                        EMU_HOST_TZ);
        assertNotEquals(signal1, signal4);

        NitzSignal signal5 =
                new NitzSignal(
                        RECEIPT_ELAPSED_MILLIS,
                        AGE_MILLIS,
                        ZONE_OFFSET + 1,
                        DST_OFFSET,
                        CURRENT_TIME_MILLIS,
                        EMU_HOST_TZ);
        assertNotEquals(signal1, signal5);

        NitzSignal signal6 =
                new NitzSignal(
                        RECEIPT_ELAPSED_MILLIS,
                        AGE_MILLIS,
                        ZONE_OFFSET,
                        null,
                        CURRENT_TIME_MILLIS,
                        EMU_HOST_TZ);
        assertNotEquals(signal1, signal6);

        NitzSignal signal7 =
                new NitzSignal(
                        RECEIPT_ELAPSED_MILLIS,
                        AGE_MILLIS,
                        ZONE_OFFSET,
                        DST_OFFSET,
                        CURRENT_TIME_MILLIS + 1,
                        EMU_HOST_TZ);
        assertNotEquals(signal1, signal7);

        NitzSignal signal8 =
                new NitzSignal(
                        RECEIPT_ELAPSED_MILLIS,
                        AGE_MILLIS,
                        ZONE_OFFSET,
                        DST_OFFSET,
                        CURRENT_TIME_MILLIS,
                        TimeZone.getTimeZone("Europe/London"));
        assertNotEquals(signal1, signal8);
    }

    @Test
    public void testGetters() {
        NitzSignal signal =
                new NitzSignal(
                        RECEIPT_ELAPSED_MILLIS,
                        AGE_MILLIS,
                        ZONE_OFFSET,
                        DST_OFFSET,
                        CURRENT_TIME_MILLIS,
                        EMU_HOST_TZ);

        assertEquals(RECEIPT_ELAPSED_MILLIS, signal.getReceiptElapsedMillis());
        assertEquals(AGE_MILLIS, signal.getAgeMillis());
        assertEquals(ZONE_OFFSET, signal.getZoneOffset());
        assertEquals(DST_OFFSET, signal.getDstOffset());
        assertEquals(CURRENT_TIME_MILLIS, signal.getCurrentTimeMillis());
        assertEquals(EMU_HOST_TZ, signal.getEmulatorHostTimeZone());
    }

    @Test
    public void testParcelable() {
        NitzSignal signalWithDst =
                new NitzSignal(
                        RECEIPT_ELAPSED_MILLIS,
                        AGE_MILLIS,
                        ZONE_OFFSET,
                        DST_OFFSET,
                        CURRENT_TIME_MILLIS,
                        EMU_HOST_TZ);
        assertRoundTripParcelable(signalWithDst);

        NitzSignal signalWithoutDst =
                new NitzSignal(
                        RECEIPT_ELAPSED_MILLIS,
                        AGE_MILLIS,
                        ZONE_OFFSET,
                        null,
                        CURRENT_TIME_MILLIS,
                        null);
        assertRoundTripParcelable(signalWithoutDst);
    }

    @Test
    public void testToString() {
        NitzSignal signal =
                new NitzSignal(
                        RECEIPT_ELAPSED_MILLIS,
                        AGE_MILLIS,
                        ZONE_OFFSET,
                        DST_OFFSET,
                        CURRENT_TIME_MILLIS,
                        EMU_HOST_TZ);
        String str = signal.toString();
        // A basic check that the toString() method doesn't crash and contains some info.
        assertTrue(str.contains(String.valueOf(RECEIPT_ELAPSED_MILLIS)));
        assertTrue(str.contains(String.valueOf(CURRENT_TIME_MILLIS)));
        assertTrue(str.contains(EMU_HOST_TZ.getID()));
    }
}
