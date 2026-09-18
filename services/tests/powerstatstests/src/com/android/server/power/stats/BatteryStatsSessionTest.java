/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.server.power.stats;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.internal.os.BatteryStatsHistory;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class BatteryStatsSessionTest {

    @Test
    public void testSessionData() {
        BatteryStatsHistory history = mock(BatteryStatsHistory.class);
        long monotonicStartTime = 12345L;
        long startClockTime = 67890L;
        long batteryTimeRemainingMs = 1000L;
        long chargeTimeRemainingMs = 2000L;
        long estimatedBatteryCapacityMah = 3000L;
        String[] customEnergyConsumerNames = {"CUSTOM1", "CUSTOM2"};

        BatteryStatsSession session = new BatteryStatsSession(
                history,
                monotonicStartTime,
                startClockTime,
                batteryTimeRemainingMs,
                chargeTimeRemainingMs,
                estimatedBatteryCapacityMah,
                customEnergyConsumerNames
        );

        assertSame(history, session.getHistory());
        assertEquals(monotonicStartTime, session.getMonotonicStartTime());
        assertEquals(startClockTime, session.getStartClockTime());
        assertEquals(batteryTimeRemainingMs, session.getBatteryTimeRemainingMs());
        assertEquals(chargeTimeRemainingMs, session.getChargeTimeRemainingMs());
        assertEquals(estimatedBatteryCapacityMah, session.getEstimatedBatteryCapacity());
        assertSame(customEnergyConsumerNames, session.getCustomEnergyConsumerNames());
    }
}

