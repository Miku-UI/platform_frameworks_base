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

package com.android.server.pm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.os.Process;
import android.platform.test.annotations.Presubmit;

import com.android.server.utils.Watchable;
import com.android.server.utils.Watcher;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@Presubmit
@RunWith(JUnit4.class)
public class PccIdSettingMapTest {

    private static final int APP_ID_1 = Process.FIRST_APPLICATION_UID;
    private static final int APP_ID_2 = Process.FIRST_APPLICATION_UID + 1;
    private static final int PCC_UID_1 = Process.FIRST_PCC_UID;
    private static final int PCC_UID_2 = Process.FIRST_PCC_UID + 1;

    @Mock
    private PackageSetting mMockPkgSetting1;
    @Mock
    private PackageSetting mMockPkgSetting2;

    private PccIdSettingMap mPccIdSettingMap;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mPccIdSettingMap = new PccIdSettingMap();
        when(mMockPkgSetting1.getAppId()).thenReturn(APP_ID_1);
        when(mMockPkgSetting1.getPackageName()).thenReturn("test.package1");
        when(mMockPkgSetting2.getAppId()).thenReturn(APP_ID_2);
        when(mMockPkgSetting2.getPackageName()).thenReturn("test.package2");
        when(mMockPkgSetting1.snapshot()).thenReturn(mMockPkgSetting1);
        when(mMockPkgSetting2.snapshot()).thenReturn(mMockPkgSetting2);
    }

    @Test
    public void testAcquireAndRegisterNewPccId_success() {
        int pccUid = mPccIdSettingMap.acquireAndRegisterNewPccId(mMockPkgSetting1);
        assertEquals(PCC_UID_1, pccUid);
        assertEquals(mMockPkgSetting1, mPccIdSettingMap.getSetting(pccUid));
    }

    @Test
    public void testAcquireAndRegisterNewPccId_invalidAppId() {
        when(mMockPkgSetting1.getAppId()).thenReturn(Process.SYSTEM_UID);
        int pccUid = mPccIdSettingMap.acquireAndRegisterNewPccId(mMockPkgSetting1);
        assertEquals(-1, pccUid);
    }

    @Test
    public void testAcquireAndRegisterNewPccId_duplicate() {
        mPccIdSettingMap.acquireAndRegisterNewPccId(mMockPkgSetting1);
        int pccUid = mPccIdSettingMap.acquireAndRegisterNewPccId(mMockPkgSetting1);
        assertEquals(-1, pccUid);
    }

    @Test
    public void testRegisterExistingPccId_success() {
        assertTrue(mPccIdSettingMap.registerExistingPccId(PCC_UID_1, mMockPkgSetting1, "test1"));
        assertEquals(mMockPkgSetting1, mPccIdSettingMap.getSetting(PCC_UID_1));
    }

    @Test
    public void testRegisterExistingPccId_duplicate() {
        mPccIdSettingMap.registerExistingPccId(PCC_UID_1, mMockPkgSetting1, "test1");
        assertFalse(mPccIdSettingMap.registerExistingPccId(PCC_UID_1,
                    mMockPkgSetting2, "test2_dup"));
        assertEquals(mMockPkgSetting1, mPccIdSettingMap.getSetting(PCC_UID_1));
    }

    @Test
    public void testRemoveSetting() {
        mPccIdSettingMap.acquireAndRegisterNewPccId(mMockPkgSetting1);
        assertEquals(mMockPkgSetting1, mPccIdSettingMap.getSetting(PCC_UID_1));
        mPccIdSettingMap.removeSetting(PCC_UID_1);
        assertNull(mPccIdSettingMap.getSetting(PCC_UID_1));
    }

    @Test
    public void testSnapshot() {
        mPccIdSettingMap.acquireAndRegisterNewPccId(mMockPkgSetting1);
        PccIdSettingMap snapshot = mPccIdSettingMap.snapshot();

        // Verify snapshot has the data
        assertEquals(mMockPkgSetting1, snapshot.getSetting(PCC_UID_1));

        // Modify original, snapshot should be unchanged
        mPccIdSettingMap.removeSetting(PCC_UID_1);
        mPccIdSettingMap.acquireAndRegisterNewPccId(mMockPkgSetting2);

        assertNull(mPccIdSettingMap.getSetting(PCC_UID_1));
        assertEquals(mMockPkgSetting2, mPccIdSettingMap.getSetting(PCC_UID_2));
        assertEquals(mMockPkgSetting1, snapshot.getSetting(PCC_UID_1));
        assertNull(snapshot.getSetting(PCC_UID_2));

        // Modify snapshot should throw exception because it's sealed
        try {
            snapshot.removeSetting(PCC_UID_1);
            fail("Snapshot should be immutable");
        } catch (IllegalStateException e) {
            // expected
        }
    }

    @Test
    public void testRegisterObserver() {
        Watcher watcher = mock(Watcher.class);
        mPccIdSettingMap.registerObserver(watcher);

        mPccIdSettingMap.acquireAndRegisterNewPccId(mMockPkgSetting1);
        verify(watcher, times(1)).onChange(any(Watchable.class));

        mPccIdSettingMap.registerExistingPccId(PCC_UID_2, mMockPkgSetting2, "test2");
        verify(watcher, times(2)).onChange(any(Watchable.class));

        mPccIdSettingMap.removeSetting(PCC_UID_1);
        verify(watcher, times(3)).onChange(any(Watchable.class));
    }
}
