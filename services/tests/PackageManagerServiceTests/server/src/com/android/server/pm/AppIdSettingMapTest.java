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
import static org.junit.Assert.assertNotNull;
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
public class AppIdSettingMapTest {

    private static final int NON_SYSTEM_APP_ID_1 = Process.FIRST_APPLICATION_UID;
    private static final int NON_SYSTEM_APP_ID_2 = Process.FIRST_APPLICATION_UID + 1;
    private static final int SYSTEM_APP_ID_1 = Process.SYSTEM_UID;
    private static final int SYSTEM_APP_ID_2 = Process.PHONE_UID;

    @Mock
    private SettingBase mMockSetting1;
    @Mock
    private SettingBase mMockSetting2;
    @Mock
    private SettingBase mMockSetting3;

    private AppIdSettingMap mAppIdSettingMap;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mAppIdSettingMap = new AppIdSettingMap();
        when(mMockSetting1.snapshot()).thenReturn(mMockSetting1);
        when(mMockSetting2.snapshot()).thenReturn(mMockSetting2);
        when(mMockSetting3.snapshot()).thenReturn(mMockSetting3);
    }

    @Test
    public void testRegisterExistingAppId_nonSystem_success() {
        assertTrue(mAppIdSettingMap.registerExistingAppId(NON_SYSTEM_APP_ID_1,
                   mMockSetting1, "test1"));
        assertEquals(mMockSetting1, mAppIdSettingMap.getSetting(NON_SYSTEM_APP_ID_1));
    }

    @Test
    public void testRegisterExistingAppId_system_success() {
        assertTrue(mAppIdSettingMap.registerExistingAppId(SYSTEM_APP_ID_1,
                   mMockSetting1, "system1"));
        assertEquals(mMockSetting1, mAppIdSettingMap.getSetting(SYSTEM_APP_ID_1));
    }

    @Test
    public void testRegisterExistingAppId_nonSystem_duplicate() {
        mAppIdSettingMap.registerExistingAppId(NON_SYSTEM_APP_ID_1, mMockSetting1, "test1");
        assertFalse(mAppIdSettingMap.registerExistingAppId(NON_SYSTEM_APP_ID_1,
                    mMockSetting2, "test2_dup"));
        assertEquals(mMockSetting1, mAppIdSettingMap.getSetting(NON_SYSTEM_APP_ID_1));
    }

    @Test
    public void testRegisterExistingAppId_system_duplicate() {
        mAppIdSettingMap.registerExistingAppId(SYSTEM_APP_ID_1, mMockSetting1, "system1");
        assertFalse(mAppIdSettingMap.registerExistingAppId(SYSTEM_APP_ID_1,
                    mMockSetting2, "system2_dup"));
        assertEquals(mMockSetting1, mAppIdSettingMap.getSetting(SYSTEM_APP_ID_1));
    }

    @Test
    public void testGetSetting() {
        assertNull(mAppIdSettingMap.getSetting(NON_SYSTEM_APP_ID_1));
        assertNull(mAppIdSettingMap.getSetting(SYSTEM_APP_ID_1));

        mAppIdSettingMap.registerExistingAppId(NON_SYSTEM_APP_ID_1, mMockSetting1, "test1");
        mAppIdSettingMap.registerExistingAppId(SYSTEM_APP_ID_1, mMockSetting2, "system1");

        assertEquals(mMockSetting1, mAppIdSettingMap.getSetting(NON_SYSTEM_APP_ID_1));
        assertEquals(mMockSetting2, mAppIdSettingMap.getSetting(SYSTEM_APP_ID_1));
        assertNull(mAppIdSettingMap.getSetting(NON_SYSTEM_APP_ID_2));
        assertNull(mAppIdSettingMap.getSetting(SYSTEM_APP_ID_2));
    }

    @Test
    public void testRemoveSetting() {
        mAppIdSettingMap.registerExistingAppId(NON_SYSTEM_APP_ID_1, mMockSetting1, "test1");
        mAppIdSettingMap.registerExistingAppId(SYSTEM_APP_ID_1, mMockSetting2, "system1");

        assertNotNull(mAppIdSettingMap.getSetting(NON_SYSTEM_APP_ID_1));
        assertNotNull(mAppIdSettingMap.getSetting(SYSTEM_APP_ID_1));

        mAppIdSettingMap.removeSetting(NON_SYSTEM_APP_ID_1);
        mAppIdSettingMap.removeSetting(SYSTEM_APP_ID_1);

        assertNull(mAppIdSettingMap.getSetting(NON_SYSTEM_APP_ID_1));
        assertNull(mAppIdSettingMap.getSetting(SYSTEM_APP_ID_1));
    }

    @Test
    public void testRemoveSetting_nonExistent() {
        mAppIdSettingMap.removeSetting(NON_SYSTEM_APP_ID_1);
        assertNull(mAppIdSettingMap.getSetting(NON_SYSTEM_APP_ID_1));
    }

    @Test
    public void testReplaceSetting() {
        mAppIdSettingMap.registerExistingAppId(NON_SYSTEM_APP_ID_1, mMockSetting1, "test1");
        mAppIdSettingMap.registerExistingAppId(SYSTEM_APP_ID_1, mMockSetting2, "system1");

        assertEquals(mMockSetting1, mAppIdSettingMap.getSetting(NON_SYSTEM_APP_ID_1));
        assertEquals(mMockSetting2, mAppIdSettingMap.getSetting(SYSTEM_APP_ID_1));

        mAppIdSettingMap.replaceSetting(NON_SYSTEM_APP_ID_1, mMockSetting3);
        mAppIdSettingMap.replaceSetting(SYSTEM_APP_ID_1, mMockSetting3);

        assertEquals(mMockSetting3, mAppIdSettingMap.getSetting(NON_SYSTEM_APP_ID_1));
        assertEquals(mMockSetting3, mAppIdSettingMap.getSetting(SYSTEM_APP_ID_1));
    }

    @Test
    public void testReplaceSetting_nonExistent() {
        // This should log a warning but not crash for non-system.
        mAppIdSettingMap.replaceSetting(NON_SYSTEM_APP_ID_1, mMockSetting1);
        assertNull(mAppIdSettingMap.getSetting(NON_SYSTEM_APP_ID_1));

        // For system, it should just add it.
        mAppIdSettingMap.replaceSetting(SYSTEM_APP_ID_1, mMockSetting1);
        assertEquals(mMockSetting1, mAppIdSettingMap.getSetting(SYSTEM_APP_ID_1));
    }

    @Test
    public void testAcquireAndRegisterNewAppId() {
        int appId1 = mAppIdSettingMap.acquireAndRegisterNewAppId(mMockSetting1);
        assertEquals(NON_SYSTEM_APP_ID_1, appId1);
        assertEquals(mMockSetting1, mAppIdSettingMap.getSetting(appId1));

        int appId2 = mAppIdSettingMap.acquireAndRegisterNewAppId(mMockSetting2);
        assertEquals(NON_SYSTEM_APP_ID_2, appId2);
        assertEquals(mMockSetting2, mAppIdSettingMap.getSetting(appId2));
    }

    @Test
    public void testAcquireAndRegisterNewAppId_withGaps() {
        mAppIdSettingMap.registerExistingAppId(NON_SYSTEM_APP_ID_1, mMockSetting1, "test1");
        mAppIdSettingMap.registerExistingAppId(NON_SYSTEM_APP_ID_1 + 2, mMockSetting2, "test2");

        int newAppId = mAppIdSettingMap.acquireAndRegisterNewAppId(mMockSetting3);
        assertEquals(NON_SYSTEM_APP_ID_2, newAppId);
        assertEquals(mMockSetting3, mAppIdSettingMap.getSetting(newAppId));
    }

    @Test
    public void testSnapshot() {
        mAppIdSettingMap.registerExistingAppId(NON_SYSTEM_APP_ID_1, mMockSetting1, "test1");
        mAppIdSettingMap.registerExistingAppId(SYSTEM_APP_ID_1, mMockSetting2, "system1");

        AppIdSettingMap snapshot = mAppIdSettingMap.snapshot();

        // Verify snapshot has the data
        assertEquals(mMockSetting1, snapshot.getSetting(NON_SYSTEM_APP_ID_1));
        assertEquals(mMockSetting2, snapshot.getSetting(SYSTEM_APP_ID_1));

        // Modify original, snapshot should be unchanged
        mAppIdSettingMap.replaceSetting(NON_SYSTEM_APP_ID_1, mMockSetting3);
        mAppIdSettingMap.removeSetting(SYSTEM_APP_ID_1);

        assertEquals(mMockSetting3, mAppIdSettingMap.getSetting(NON_SYSTEM_APP_ID_1));
        assertNull(mAppIdSettingMap.getSetting(SYSTEM_APP_ID_1));

        assertEquals(mMockSetting1, snapshot.getSetting(NON_SYSTEM_APP_ID_1));
        assertEquals(mMockSetting2, snapshot.getSetting(SYSTEM_APP_ID_1));

        // Modify snapshot should throw exception because it's sealed
        try {
            snapshot.replaceSetting(NON_SYSTEM_APP_ID_1, mMockSetting2);
            fail("Snapshot should be immutable");
        } catch (IllegalStateException e) {
            // expected
        }
        try {
            snapshot.replaceSetting(SYSTEM_APP_ID_1, mMockSetting1);
            fail("Snapshot should be immutable");
        } catch (IllegalStateException e) {
            // expected
        }
    }

    @Test
    public void testRegisterObserver() {
        Watcher watcher = mock(Watcher.class);
        mAppIdSettingMap.registerObserver(watcher);

        // Non-system modifications
        mAppIdSettingMap.registerExistingAppId(NON_SYSTEM_APP_ID_1, mMockSetting1, "test1");
        verify(watcher, times(2)).onChange(any(Watchable.class)); // add(null) and set()

        mAppIdSettingMap.replaceSetting(NON_SYSTEM_APP_ID_1, mMockSetting2);
        verify(watcher, times(3)).onChange(any(Watchable.class));

        mAppIdSettingMap.removeSetting(NON_SYSTEM_APP_ID_1);
        verify(watcher, times(4)).onChange(any(Watchable.class));

        mAppIdSettingMap.acquireAndRegisterNewAppId(mMockSetting3);
        verify(watcher, times(5)).onChange(any(Watchable.class));

        // System modifications
        mAppIdSettingMap.registerExistingAppId(SYSTEM_APP_ID_1, mMockSetting1, "system1");
        verify(watcher, times(6)).onChange(any(Watchable.class));

        mAppIdSettingMap.replaceSetting(SYSTEM_APP_ID_1, mMockSetting2);
        verify(watcher, times(7)).onChange(any(Watchable.class));

        mAppIdSettingMap.removeSetting(SYSTEM_APP_ID_1);
        verify(watcher, times(8)).onChange(any(Watchable.class));
    }
}
