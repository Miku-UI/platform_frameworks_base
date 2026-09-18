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

package com.android.server.power;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.os.WorkSource;
import android.os.WorkSource.WorkChain;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;

import com.android.server.power.feature.flags.Flags;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Set;

/**
 * Tests for {@link com.android.server.power.WakelockMapper}.
 */
public class WakelockMapperTest {
    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Mock
    private PowerManagerService.WakeLock mWakeLock1;
    @Mock
    private PowerManagerService.WakeLock mWakeLock2;

    private WakelockMapper mWakelockMapper;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mWakelockMapper = new WakelockMapper();
    }

    @Test
    @EnableFlags(Flags.FLAG_REMOVE_CACHED_UIDS_FROM_WAKELOCK)
    public void testAddAndRemoveWakeLock() {
        WorkSource workSource = new WorkSource(1001, "test");
        mWakeLock1.mWorkSource = workSource;

        mWakelockMapper.addWakeLock(mWakeLock1);
        Set<PowerManagerService.WakeLock> wakeLocks = mWakelockMapper.getWakeLocksForUid(1001);
        assertEquals(1, wakeLocks.size());
        assertTrue(wakeLocks.contains(mWakeLock1));

        mWakelockMapper.removeWakeLock(mWakeLock1);
        wakeLocks = mWakelockMapper.getWakeLocksForUid(1001);
        assertTrue(wakeLocks.isEmpty());
    }

    @Test
    @EnableFlags(Flags.FLAG_REMOVE_CACHED_UIDS_FROM_WAKELOCK)
    public void testAddAndRemoveWakeLock_withWorkChain() {
        WorkSource workSource = new WorkSource();
        WorkChain workChain = workSource.createWorkChain();
        workChain.addNode(1002, "tag");

        mWakeLock2.mWorkSource = workSource;

        mWakelockMapper.addWakeLock(mWakeLock2);
        Set<PowerManagerService.WakeLock> wakeLocks = mWakelockMapper.getWakeLocksForUid(1002);
        assertEquals(1, wakeLocks.size());
        assertTrue(wakeLocks.contains(mWakeLock2));

        mWakelockMapper.removeWakeLock(mWakeLock2);
        wakeLocks = mWakelockMapper.getWakeLocksForUid(1002);
        assertTrue(wakeLocks.isEmpty());
    }

    @Test
    @EnableFlags(Flags.FLAG_REMOVE_CACHED_UIDS_FROM_WAKELOCK)
    public void testGetWakeLocksForUid_createsDefensiveCopy() {
        WorkSource workSource = new WorkSource();
        WorkChain workChain = workSource.createWorkChain();
        workChain.addNode(1002, "tag");

        mWakeLock2.mWorkSource = workSource;
        mWakelockMapper.addWakeLock(mWakeLock2);

        Set<PowerManagerService.WakeLock> wakeLocks = mWakelockMapper.getWakeLocksForUid(1002);

        mWakelockMapper.removeWakeLock(mWakeLock2);

        assertEquals(1, wakeLocks.size());
        assertTrue(wakeLocks.contains(mWakeLock2));
    }

    @Test
    @EnableFlags(Flags.FLAG_REMOVE_CACHED_UIDS_FROM_WAKELOCK)
    public void testGetWakeLocksForUid_noWakeLocks() {
        Set<PowerManagerService.WakeLock> wakeLocks = mWakelockMapper.getWakeLocksForUid(1234);
        assertTrue(wakeLocks.isEmpty());
    }

    @Test
    @EnableFlags(Flags.FLAG_REMOVE_CACHED_UIDS_FROM_WAKELOCK)
    public void testSetUidCached() {
        int uid = 1001;
        assertFalse(mWakelockMapper.isUidCached(uid));

        mWakelockMapper.setUidCached(uid, true);
        assertTrue(mWakelockMapper.isUidCached(uid));

        mWakelockMapper.setUidCached(uid, false);
        assertFalse(mWakelockMapper.isUidCached(uid));
    }
}
