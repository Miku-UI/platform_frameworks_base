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

import static android.hardware.usb.InternalPciTunnelControlDisableReason.PCI_TUNNEL_CONTROL_DISABLE_REASON_APM;
import static android.hardware.usb.InternalPciTunnelControlDisableReason.PCI_TUNNEL_CONTROL_DISABLE_REASON_ENTERPRISE;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.UserInfo;
import android.hardware.usb.UsbManager;
import android.os.UserHandle;
import android.os.UserManager;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.server.LocalServices;
import com.android.server.usb.flags.Flags;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Tests for {@link com.android.server.usb.Usb4Manager} atest UsbTests:Usb4ManagerTest */
@RunWith(AndroidJUnit4.class)
@EnableFlags({
    Flags.FLAG_ENABLE_USB4_TBT,
    Flags.FLAG_DEFAULT_ALLOW_PCI_TUNNELS,
    android.hardware.usb.flags.Flags.FLAG_ENABLE_PCI_TUNNEL_CONTROL
})
public class Usb4ManagerTest {
    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    private Context mContext = InstrumentationRegistry.getInstrumentation().getContext();
    private SharedPreferences mSettings;
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
        LocalServices.removeAllServicesForTest();
        MockitoAnnotations.initMocks(this);

        when(mUserManager.getUserInfo(TEST_USER_ID)).thenReturn(TEST_USER_INFO);
        when(mUserManager.getUserInfo(TEST_USER_ID_NOT_FULL)).thenReturn(TEST_USER_INFO_NOT_FULL);
        when(mUserManager.getUserInfo(TEST_USER_ID_NOT_ADMIN)).thenReturn(TEST_USER_INFO_NOT_ADMIN);

        when(mUsb4ManagerNative.checkPciTunnelsSupported()).thenReturn(true);

        File prefsFile = new File(mContext.getCacheDir(), "tmpPrefs.xml");
        mSettings = spy(mContext.getSharedPreferences(prefsFile, Context.MODE_PRIVATE));
        mSettings.edit().clear().commit();

        mUsb4Manager = new Usb4Manager(mContext, mUserManager, mUsb4ManagerNative, mSettings);
    }

    /** Test that enabling PCI tunnels succeeds for a full admin user. */
    @Test
    public void testSetPciTunnelingEnabled_successWithFullAdminUser() {
        mUsb4Manager.onUpdateLoggedInState(true, TEST_USER_ID);
        mUsb4Manager.setPciTunnelingEnabled(true);
        verify(mUsb4ManagerNative, atLeast(1)).enablePciTunnels(true);
        assertTrue(mUsb4Manager.isPciTunnelingEnabled());
    }

    /** Test that enabling PCI tunnels fails for a user that is not full or not admin. */
    @Test
    public void testSetPciTunnelingEnabled_failsInvalidUser() {
        mUsb4Manager.onUpdateLoggedInState(true, TEST_USER_ID_NOT_FULL);
        assertThrows(IllegalStateException.class, () -> mUsb4Manager.setPciTunnelingEnabled(true));

        mUsb4Manager.onUpdateLoggedInState(true, TEST_USER_ID_NOT_ADMIN);
        assertThrows(IllegalStateException.class, () -> mUsb4Manager.setPciTunnelingEnabled(true));
    }

    /** Test that enabling PCI tunnels fails if tunnels are not supported. */
    @Test
    public void testSetPciTunnelingEnabled_failsWhenPciTunnelsNotSupported() {
        when(mUsb4ManagerNative.checkPciTunnelsSupported()).thenReturn(false);

        // Initially, we shouldn't be able to update tunnel control due to invalid user.
        assertEquals(
                mUsb4Manager.getPciTunnelingControlAllowedStatus(),
                UsbManager.PCI_TUNNEL_CTRL_DISALLOWED_FOR_NONADMIN_USER);

        // After switching users, tunnel control should be updated to unsupported.
        mUsb4Manager.onUpdateLoggedInState(true, TEST_USER_ID);
        assertEquals(
                mUsb4Manager.getPciTunnelingControlAllowedStatus(),
                UsbManager.PCI_TUNNEL_CTRL_UNSUPPORTED);
        assertThrows(IllegalStateException.class, () -> mUsb4Manager.setPciTunnelingEnabled(true));

        // Disabling PCI tunnels is still allowed when not supported.
        mUsb4Manager.setPciTunnelingEnabled(false);
        verify(mUsb4ManagerNative, atLeast(1)).enablePciTunnels(false);
    }

    /** Test an invalid disable reason on setPciTunnelingControlAllowed */
    @Test
    public void testSetPciTunnelControlAllowed_failsOnInvalidReasons() {
        assertThrows(
                IllegalArgumentException.class,
                () -> mUsb4Manager.setPciTunnelingControlAllowed(false, 1234));
    }

    /** Test that setPciTunnelingControlAllowed accepts valid reasons and disables tunneling. */
    @Test
    public void testSetPciTunnelControlAllowed_acceptsAndDisablesOnValidReasons() {
        // Start with tunnels enabled.
        mUsb4Manager.onUpdateLoggedInState(true, TEST_USER_ID);
        mUsb4Manager.setPciTunnelingEnabled(true);
        assertTrue(mUsb4Manager.isPciTunnelingEnabled());

        // Update disable reasons.
        assertTrue(
                mUsb4Manager.setPciTunnelingControlAllowed(
                        false, PCI_TUNNEL_CONTROL_DISABLE_REASON_APM));
        assertEquals(
                mUsb4Manager.getPciTunnelingControlAllowedStatus(),
                UsbManager.PCI_TUNNEL_CTRL_DISALLOWED_BY_APM);
        assertFalse(mUsb4Manager.isPciTunnelingEnabled());

        assertTrue(
                mUsb4Manager.setPciTunnelingControlAllowed(
                        false, PCI_TUNNEL_CONTROL_DISABLE_REASON_ENTERPRISE));
        assertEquals(
                mUsb4Manager.getPciTunnelingControlAllowedStatus(),
                UsbManager.PCI_TUNNEL_CTRL_DISALLOWED_BY_ENTERPRISE_POLICY);
        assertFalse(mUsb4Manager.isPciTunnelingEnabled());

        // Remove reasons.
        assertTrue(
                mUsb4Manager.setPciTunnelingControlAllowed(
                        true, PCI_TUNNEL_CONTROL_DISABLE_REASON_APM));
        assertEquals(
                mUsb4Manager.getPciTunnelingControlAllowedStatus(),
                UsbManager.PCI_TUNNEL_CTRL_DISALLOWED_BY_ENTERPRISE_POLICY);
        assertTrue(
                mUsb4Manager.setPciTunnelingControlAllowed(
                        true, PCI_TUNNEL_CONTROL_DISABLE_REASON_ENTERPRISE));

        // Revert to supported but don't restore tunnel enable state.
        assertEquals(
                mUsb4Manager.getPciTunnelingControlAllowedStatus(),
                UsbManager.PCI_TUNNEL_CTRL_SUPPORTED);
        assertFalse(mUsb4Manager.isPciTunnelingEnabled());
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

    /** Test that checks pci tunnel preferences are persisted and loaded correctly on init. */
    @Test
    public void testPciTunnelPreferencePersistence() throws InterruptedException {
        assertTrue(mUsb4Manager.isPciTunnelingEnabled());
        mSettings.edit().clear().putBoolean(Usb4Manager.ENABLE_PCI_TUNNELS_PREF, false).commit();

        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            latch.countDown();
            return result;
        }).when(mSettings).getBoolean(anyString(), anyBoolean());

        mUsb4Manager = new Usb4Manager(mContext, mUserManager, mUsb4ManagerNative, mSettings);
        // Wait for async work to read from persisted values.
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertFalse(mUsb4Manager.isPciTunnelingEnabled());

        mUsb4Manager.onUpdateLoggedInState(true, TEST_USER_ID);
        mUsb4Manager.setPciTunnelingEnabled(true);
        assertTrue(mUsb4Manager.isPciTunnelingEnabled());

        assertTrue(mSettings.getBoolean(Usb4Manager.ENABLE_PCI_TUNNELS_PREF, false));
    }
}
