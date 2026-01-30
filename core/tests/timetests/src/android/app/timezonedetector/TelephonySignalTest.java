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

import java.util.Collections;
import java.util.Set;
import java.util.TimeZone;

@RunWith(AndroidJUnit4.class)
@SmallTest
@Presubmit
public class TelephonySignalTest {

    private static final String MCC = "123";
    private static final String MNC = "456";
    private static final String DEFAULT_COUNTRY_ISO = "us";
    private static final Set<String> COUNTRY_ISO_CODES = Set.of("us", "gb");
    private static final NitzSignal NITZ_SIGNAL =
            new NitzSignal(
                    /* receiptElapsedMillis= */ 1234L,
                    /* ageMillis= */ 5678L,
                    /* zoneOffset= */ 3600000,
                    /* dstOffset= */ 3600000,
                    /* currentTimeMillis= */ 987654321L,
                    /* emulatorHostTimeZone= */ TimeZone.getTimeZone("America/Los_Angeles"));

    @Test
    public void testEquals() {
        TelephonySignal signal1 =
                new TelephonySignal(MCC, MNC, DEFAULT_COUNTRY_ISO, COUNTRY_ISO_CODES, NITZ_SIGNAL);
        TelephonySignal signal2 =
                new TelephonySignal(MCC, MNC, DEFAULT_COUNTRY_ISO, COUNTRY_ISO_CODES, NITZ_SIGNAL);
        assertEquals(signal1, signal2);
        assertEquals(signal1.hashCode(), signal2.hashCode());

        TelephonySignal signal3 =
                new TelephonySignal(
                        "999", MNC, DEFAULT_COUNTRY_ISO, COUNTRY_ISO_CODES, NITZ_SIGNAL);
        assertNotEquals(signal1, signal3);

        TelephonySignal signal4 =
                new TelephonySignal(
                        MCC, "999", DEFAULT_COUNTRY_ISO, COUNTRY_ISO_CODES, NITZ_SIGNAL);
        assertNotEquals(signal1, signal4);

        TelephonySignal signal5 =
                new TelephonySignal(MCC, null, DEFAULT_COUNTRY_ISO, COUNTRY_ISO_CODES, NITZ_SIGNAL);
        assertNotEquals(signal1, signal5);

        TelephonySignal signal6 =
                new TelephonySignal(MCC, MNC, "gb", COUNTRY_ISO_CODES, NITZ_SIGNAL);
        assertNotEquals(signal1, signal6);

        TelephonySignal signal7 =
                new TelephonySignal(MCC, MNC, DEFAULT_COUNTRY_ISO, Set.of("fr"), NITZ_SIGNAL);
        assertNotEquals(signal1, signal7);

        NitzSignal otherNitz =
                new NitzSignal(
                        /* receiptElapsedMillis= */ 1L,
                        /* ageMillis= */ 2L,
                        /* zoneOffset= */ 3,
                        /* dstOffset= */ 4,
                        /* currentTimeMillis= */ 5L,
                        /* emulatorHostTimeZone= */ TimeZone.getTimeZone("Europe/London"));
        TelephonySignal signal8 =
                new TelephonySignal(MCC, MNC, DEFAULT_COUNTRY_ISO, COUNTRY_ISO_CODES, otherNitz);
        assertNotEquals(signal1, signal8);

        TelephonySignal signal9 =
                new TelephonySignal(MCC, MNC, DEFAULT_COUNTRY_ISO, COUNTRY_ISO_CODES, null);
        assertNotEquals(signal1, signal9);
    }

    @Test
    public void testGetters() {
        TelephonySignal signal =
                new TelephonySignal(MCC, MNC, DEFAULT_COUNTRY_ISO, COUNTRY_ISO_CODES, NITZ_SIGNAL);

        assertEquals(MCC, signal.getMcc());
        assertEquals(MNC, signal.getMnc());
        assertEquals(DEFAULT_COUNTRY_ISO, signal.getDefaultCountryIsoCode());
        assertEquals(COUNTRY_ISO_CODES, signal.getCountryIsoCodes());
        assertEquals(NITZ_SIGNAL, signal.getNitzSignal());
    }

    @Test
    public void testParcelable() {
        TelephonySignal signalWithNitz =
                new TelephonySignal(MCC, MNC, DEFAULT_COUNTRY_ISO, COUNTRY_ISO_CODES, NITZ_SIGNAL);
        assertRoundTripParcelable(signalWithNitz);

        TelephonySignal signalWithoutNitz =
                new TelephonySignal(
                        MCC, null, DEFAULT_COUNTRY_ISO, Collections.singleton("us"), null);
        assertRoundTripParcelable(signalWithoutNitz);
    }

    @Test
    public void testToString() {
        TelephonySignal signal =
                new TelephonySignal(MCC, MNC, DEFAULT_COUNTRY_ISO, COUNTRY_ISO_CODES, NITZ_SIGNAL);
        String str = signal.toString();
        // A basic check that the toString() method doesn't crash and contains some info.
        assertTrue(str.contains(MCC));
        assertTrue(str.contains(MNC));
        assertTrue(str.contains(DEFAULT_COUNTRY_ISO));
        assertTrue(str.contains(NITZ_SIGNAL.toString()));
    }
}
