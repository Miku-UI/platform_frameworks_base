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
package com.android.server.timezonedetector.ftzd;

import static com.android.server.timezonedetector.TimeZoneDetectorStrategy.ORIGIN_LOCATION;
import static com.android.server.timezonedetector.TimeZoneDetectorStrategy.ORIGIN_TELEPHONY;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.List;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
public class FusedSignalsTest {

    private static final String TZ_LA = "America/Los_Angeles";
    private static final String TZ_NY = "America/New_York";
    private static final String TZ_LONDON = "Europe/London";

    @Test
    public void testConstructor_singleZoneId_telephonyOrigin() {
        FusedSignals fusedSignals = new FusedSignals(TZ_LA, ORIGIN_TELEPHONY);
        assertEquals(TZ_LA, fusedSignals.getTimeZoneId());
        assertTrue(fusedSignals.getOrigins().contains(ORIGIN_TELEPHONY));
        assertEquals(1, fusedSignals.getOrigins().size());
    }

    @Test
    public void testConstructor_multipleZoneIds_locationOrigin() {
        List<String> zones = List.of(TZ_NY, TZ_LA);
        FusedSignals fusedSignals = new FusedSignals(zones, ORIGIN_LOCATION);
        assertEquals(TZ_NY, fusedSignals.getTimeZoneId());
        assertTrue(fusedSignals.getOrigins().contains(ORIGIN_LOCATION));
    }

    @Test
    public void testConstructor_singleZoneId_noOrigin() {
        FusedSignals fusedSignals = new FusedSignals(TZ_LA, null);
        assertEquals(TZ_LA, fusedSignals.getTimeZoneId());
        assertTrue(fusedSignals.getOrigins().isEmpty());
    }

    @Test
    public void testConstructor_multipleZoneIds_noOrigin() {
        // Verifies constructor with multiple zone IDs and no origin.
        List<String> zones = List.of(TZ_NY, TZ_LA);
        FusedSignals fusedSignals = new FusedSignals(zones, null);

        // The first zone ID in the list should be the primary one.
        assertEquals(TZ_NY, fusedSignals.getTimeZoneId());
        // No origin should be recorded.
        assertTrue(fusedSignals.getOrigins().isEmpty());
        // All provided zones should be in the candidate set.
        assertEquals(Set.of(TZ_NY, TZ_LA), fusedSignals.getZoneIdCandidates());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructor_emptyZoneIdCandidates_throwsException() {
        // Verifies that the constructor throws an exception when the candidate list is empty.
        new FusedSignals(Collections.emptyList(), ORIGIN_TELEPHONY);
    }

    @Test
    public void testCopyConstructor_keepOrigins() {
        FusedSignals original = new FusedSignals(TZ_LA, ORIGIN_TELEPHONY);
        original.setQualityForOrigin(ORIGIN_TELEPHONY, 50);

        FusedSignals copy = FusedSignals.copy(original);
        assertEquals(original.getTimeZoneId(), copy.getTimeZoneId());
        assertEquals(original.getOrigins(), copy.getOrigins());
        assertTrue(copy.hasOrigin(ORIGIN_TELEPHONY, 50));
    }

    @Test
    public void testCopyConstructor_dontKeepOrigins() {
        FusedSignals original = new FusedSignals(TZ_LA, ORIGIN_TELEPHONY);
        original.setQualityForOrigin(ORIGIN_TELEPHONY, 50);

        FusedSignals copy = FusedSignals.copyWithoutOrigins(original);
        assertEquals(original.getTimeZoneId(), copy.getTimeZoneId());
        assertTrue(copy.getOrigins().isEmpty());
    }

    @Test
    public void testUpdate() {
        FusedSignals fusedSignals = new FusedSignals(TZ_LA, ORIGIN_TELEPHONY);
        fusedSignals.update(ORIGIN_LOCATION, List.of(TZ_NY, TZ_LONDON));

        assertEquals(2, fusedSignals.getOrigins().size());
        assertTrue(fusedSignals.getOrigins().contains(ORIGIN_TELEPHONY));
        assertTrue(fusedSignals.getOrigins().contains(ORIGIN_LOCATION));
        assertEquals(3, fusedSignals.getZoneIdCandidates().size());
        assertTrue(fusedSignals.getZoneIdCandidates().contains(TZ_LA));
        assertTrue(fusedSignals.getZoneIdCandidates().contains(TZ_NY));
        assertTrue(fusedSignals.getZoneIdCandidates().contains(TZ_LONDON));
    }

    @Test
    public void testUpdate_existingOrigin() {
        FusedSignals fusedSignals = new FusedSignals(TZ_LA, ORIGIN_TELEPHONY);
        assertEquals(1, fusedSignals.getOrigins().size());
        fusedSignals.update(ORIGIN_TELEPHONY, Collections.singletonList(TZ_NY));
        assertEquals(1, fusedSignals.getOrigins().size());
        assertEquals(2, fusedSignals.getZoneIdCandidates().size());
        assertTrue(fusedSignals.getZoneIdCandidates().contains(TZ_LA));
        assertTrue(fusedSignals.getZoneIdCandidates().contains(TZ_NY));
    }

    @Test
    public void testUpdate_returnsSameInstance() {
        // Verifies that update() returns the same instance for chaining.
        FusedSignals fusedSignals = new FusedSignals(TZ_LA, ORIGIN_TELEPHONY);
        FusedSignals returned = fusedSignals.update(ORIGIN_LOCATION, List.of(TZ_NY));
        assertSame(fusedSignals, returned);
    }

    @Test
    public void testAddOrigin_withExplicitTimestamp() {
        FusedSignals fusedSignals = new FusedSignals(TZ_LA, ORIGIN_TELEPHONY);
        long timestamp = 123456789L;

        fusedSignals.addOrigin(ORIGIN_LOCATION, List.of(TZ_NY), timestamp);

        assertTrue(fusedSignals.hasOrigin(ORIGIN_LOCATION, 0));
        assertTrue(fusedSignals.getZoneIdCandidates().contains(TZ_NY));
        assertEquals(
                timestamp,
                fusedSignals.getOriginInfoForOrigin(ORIGIN_LOCATION).getTimestampDetected());
        assertEquals(
                timestamp,
                fusedSignals.getOriginInfoForOrigin(ORIGIN_LOCATION).getTimestampLastUpdated());
    }

    @Test
    public void testSetQualityForOrigin() {
        FusedSignals fusedSignals = new FusedSignals(TZ_LA, ORIGIN_TELEPHONY);
        fusedSignals.setQualityForOrigin(ORIGIN_TELEPHONY, 50);
        assertTrue(fusedSignals.hasOrigin(ORIGIN_TELEPHONY, 50));

        // Set quality for non-existent origin should not fail
        fusedSignals.setQualityForOrigin(ORIGIN_LOCATION, 80);
        assertFalse(fusedSignals.hasOrigin(ORIGIN_LOCATION, 80));
    }

    @Test
    public void testHasNoOrigins() {
        FusedSignals fusedSignals = new FusedSignals(TZ_LA, null);
        assertTrue(fusedSignals.hasNoOrigins(0));

        fusedSignals.update(ORIGIN_TELEPHONY, Collections.singletonList(TZ_LA));
        fusedSignals.setQualityForOrigin(ORIGIN_TELEPHONY, 40);
        assertTrue(fusedSignals.hasNoOrigins(50));
        assertFalse(fusedSignals.hasNoOrigins(40));
    }

    @Test
    public void testHasSingleOrigin() {
        FusedSignals fusedSignals = new FusedSignals(TZ_LA, ORIGIN_TELEPHONY);
        fusedSignals.setQualityForOrigin(ORIGIN_TELEPHONY, 60);

        assertTrue(fusedSignals.hasSingleOrigin(ORIGIN_TELEPHONY, 50));
        assertFalse(fusedSignals.hasSingleOrigin(ORIGIN_LOCATION, 50));

        fusedSignals.update(ORIGIN_LOCATION, Collections.singletonList(TZ_NY));
        fusedSignals.setQualityForOrigin(ORIGIN_LOCATION, 70);
        assertFalse(fusedSignals.hasSingleOrigin(ORIGIN_TELEPHONY, 50));
        assertFalse(fusedSignals.hasSingleOrigin(ORIGIN_LOCATION, 50));
    }

    @Test
    public void testHasOrigin() {
        FusedSignals fusedSignals = new FusedSignals(TZ_LA, ORIGIN_TELEPHONY);
        fusedSignals.setQualityForOrigin(ORIGIN_TELEPHONY, 60);

        assertTrue(fusedSignals.hasOrigin(ORIGIN_TELEPHONY, 60));
        assertTrue(fusedSignals.hasOrigin(ORIGIN_TELEPHONY, 50));
        assertFalse(fusedSignals.hasOrigin(ORIGIN_TELEPHONY, 70));
        assertFalse(fusedSignals.hasOrigin(ORIGIN_LOCATION, 0));
    }

    @Test
    public void testClearOrigins() {
        FusedSignals fusedSignals = new FusedSignals(TZ_LA, ORIGIN_TELEPHONY);
        fusedSignals.update(ORIGIN_LOCATION, Collections.singletonList(TZ_NY));
        assertFalse(fusedSignals.getOrigins().isEmpty());

        fusedSignals.clearOrigins();
        assertTrue(fusedSignals.getOrigins().isEmpty());
    }

    @Test
    public void testClearOrigins_returnsSameInstance() {
        // Verifies that clearOrigins() returns the same instance for chaining.
        FusedSignals fusedSignals = new FusedSignals(TZ_LA, ORIGIN_TELEPHONY);
        FusedSignals returned = fusedSignals.clearOrigins();
        assertSame(fusedSignals, returned);
    }

    @Test
    public void testRemoveOrigin() {
        FusedSignals fusedSignals = new FusedSignals(TZ_LA, ORIGIN_TELEPHONY);
        fusedSignals.update(ORIGIN_LOCATION, Collections.singletonList(TZ_NY));
        assertEquals(2, fusedSignals.getOrigins().size());

        fusedSignals.removeOrigin(ORIGIN_TELEPHONY);
        assertEquals(1, fusedSignals.getOrigins().size());
        assertTrue(fusedSignals.getOrigins().contains(ORIGIN_LOCATION));
        assertFalse(fusedSignals.getOrigins().contains(ORIGIN_TELEPHONY));
    }

    @Test
    public void testRemoveOrigin_returnsSameInstance() {
        // Verifies that removeOrigin() returns the same instance for chaining.
        FusedSignals fusedSignals = new FusedSignals(TZ_LA, ORIGIN_TELEPHONY);
        FusedSignals returned = fusedSignals.removeOrigin(ORIGIN_TELEPHONY);
        assertSame(fusedSignals, returned);
    }

    @Test
    public void testGetTimeZoneId() {
        FusedSignals fusedSignals = new FusedSignals(TZ_LA, null);
        assertEquals(TZ_LA, fusedSignals.getTimeZoneId());
    }

    @Test
    public void testGetOrigins() {
        FusedSignals fusedSignals = new FusedSignals(TZ_LA, ORIGIN_TELEPHONY);
        fusedSignals.update(ORIGIN_LOCATION, Collections.singletonList(TZ_NY));

        Set<Integer> origins = fusedSignals.getOrigins();
        assertEquals(2, origins.size());
        assertTrue(origins.contains(ORIGIN_TELEPHONY));
        assertTrue(origins.contains(ORIGIN_LOCATION));
    }
}
