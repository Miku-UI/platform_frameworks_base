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

package com.android.server.usb;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.UserInfo;
import android.os.UserHandle;
import android.os.UserManager;
import android.platform.test.flag.junit.SetFlagsRule;
import androidx.test.runner.AndroidJUnit4;
import com.android.server.LocalServices;
import com.android.server.usb.flags.Flags;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Tests for {@link com.android.server.usb.Usb4Manager} atest UsbTests:Usb4ManagerTest */
@RunWith(AndroidJUnit4.class)
public class Usb4ManagerTest {
    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Mock private Context mContext;
    @Mock private UserManager mUserManager;
    @Mock private Usb4Manager.Usb4ManagerNative mUsb4ManagerNative;

    private static final int TEST_USER_ID = 10;
    private static final int TEST_USER_ID_NOT_FULL = 11;
    private static final int TEST_USER_ID_NOT_ADMIN = 12;
    private static final UserInfo TEST_USER_INFO =
            new UserInfo(TEST_USER_ID, "test", UserInfo.FLAG_FULL | UserInfo.FLAG_ADMIN);
    private static final UserInfo TEST_USER_INFO_NOT_FULL =
            new UserInfo(TEST_USER_ID_NOT_FULL, "test", UserInfo.FLAG_ADMIN);
    private static final UserInfo TEST_USER_INFO_NOT_ADMIN =
            new UserInfo(TEST_USER_ID_NOT_ADMIN, "test", UserInfo.FLAG_FULL);

    private Usb4Manager mUsb4Manager = null;

    @Before
    public void setUp() {
        mSetFlagsRule.enableFlags(Flags.FLAG_ENABLE_USB4);
        mSetFlagsRule.disableFlags(Flags.FLAG_DEFAULT_ALLOW_PCI_TUNNELS);
        LocalServices.removeAllServicesForTest();
        MockitoAnnotations.initMocks(this);

        when(mUserManager.getUserInfo(TEST_USER_ID)).thenReturn(TEST_USER_INFO);
        when(mUserManager.getUserInfo(TEST_USER_ID_NOT_FULL)).thenReturn(TEST_USER_INFO_NOT_FULL);
        when(mUserManager.getUserInfo(TEST_USER_ID_NOT_ADMIN)).thenReturn(TEST_USER_INFO_NOT_ADMIN);

        mUsb4Manager = new Usb4Manager(mContext, mUserManager, mUsb4ManagerNative);
    }

    /** Test that enabling PCI tunnels succeeds for a full admin user. */
    @Test
    public void testOnEnablePciTunnels_successWithFullAdminUser() {
        mUsb4Manager.onUpdateLoggedInState(true, TEST_USER_ID);
        mUsb4Manager.onEnablePciTunnels(true);
        verify(mUsb4ManagerNative).enablePciTunnels(true);
    }

    /** Test that enabling PCI tunnels fails for a user that is not full or not admin. */
    @Test
    public void testOnEnablePciTunnels_failsInvalidUser() {
        mUsb4Manager.onUpdateLoggedInState(true, TEST_USER_ID_NOT_FULL);
        assertThrows(IllegalStateException.class, () -> mUsb4Manager.onEnablePciTunnels(true));
        verify(mUsb4ManagerNative, never()).enablePciTunnels(true);

        mUsb4Manager.onUpdateLoggedInState(true, TEST_USER_ID_NOT_ADMIN);
        assertThrows(IllegalStateException.class, () -> mUsb4Manager.onEnablePciTunnels(true));
        verify(mUsb4ManagerNative, never()).enablePciTunnels(true);
    }

    /** Test that updating the screen locked state succeeds. */
    @Test
    public void testOnUpdateScreenLockedState_success() {
        mUsb4Manager.onUpdateScreenLockedState(true);
        verify(mUsb4ManagerNative).updateLockState(true);
    }

    /** Test that updating the logged in state succeeds. */
    @Test
    public void testOnUpdateLoggedInState_success() {
        mUsb4Manager.onUpdateLoggedInState(true, TEST_USER_ID);
        verify(mUsb4ManagerNative).updateLoggedInState(true, TEST_USER_ID);
    }

    /** Test that updating the logged in state to system user is ignored. */
    @Test
    public void testOnUpdateLoggedInState_systemUser_ignored() {
        mUsb4Manager.onUpdateLoggedInState(true, UserHandle.USER_SYSTEM);
        verify(mUsb4ManagerNative, never()).updateLoggedInState(true, UserHandle.USER_SYSTEM);
    }
}
