/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may not use this file except in compliance with the License.
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
package com.android.server.timezonedetector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class TimeZoneDisagreementCandidateTest {

    private static final int ORIGIN = 1;
    private static final List<String> ZONE_IDS = List.of("America/Los_Angeles");
    private static final long TIMESTAMP = System.currentTimeMillis();

    @Test
    public void testInitialization() {
        TimeZoneDisagreementCandidate candidate =
                new TimeZoneDisagreementCandidate(ORIGIN, ZONE_IDS, TIMESTAMP);

        assertEquals(ORIGIN, candidate.getOrigin());
        assertEquals(ZONE_IDS, candidate.getZoneIds());
        assertEquals(TIMESTAMP, candidate.getTimestamp());
        assertEquals(1, candidate.getOccurrenceCount());
    }

    @Test
    public void testMarkOccurrence() {
        TimeZoneDisagreementCandidate candidate =
                new TimeZoneDisagreementCandidate(ORIGIN, ZONE_IDS, TIMESTAMP);
        assertEquals(1, candidate.getOccurrenceCount());

        candidate.markOccurrence();
        assertEquals(2, candidate.getOccurrenceCount());

        candidate.markOccurrence();
        assertEquals(3, candidate.getOccurrenceCount());
    }

    @Test
    public void testDefensiveCopyOfZoneIds() {
        List<String> originalZoneIds = new ArrayList<>(ZONE_IDS);
        TimeZoneDisagreementCandidate candidate =
                new TimeZoneDisagreementCandidate(ORIGIN, originalZoneIds, TIMESTAMP);

        // Modify the original list
        originalZoneIds.add("America/New_York");

        // Verify that the candidate's list is unchanged
        assertEquals(ZONE_IDS, candidate.getZoneIds());
        assertNotSame(originalZoneIds, candidate.getZoneIds());
    }
}
