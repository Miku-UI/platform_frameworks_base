/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.internal.util.test;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNull;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import android.content.ContentProvider;
import android.os.Process;
import android.os.UserHandle;
import android.platform.test.annotations.DisabledOnRavenwood;
import android.platform.test.ravenwood.RavenwoodRule;
import android.provider.Settings;
import android.test.mock.MockContentResolver;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;

/**
 * Unit tests for FakeSettingsProvider.
 */
@RunWith(AndroidJUnit4.class)
@DisabledOnRavenwood(blockedBy = ContentProvider.class)
public class FakeSettingsProviderTest {
    @Rule
    public final RavenwoodRule mRavenwood = new RavenwoodRule();

    private static final String SYSTEM_SETTING = Settings.System.SCREEN_BRIGHTNESS;
    private static final String SECURE_SETTING = Settings.Secure.BLUETOOTH_NAME;
    private static final String GLOBAL_SETTING = Settings.Global.MOBILE_DATA_ALWAYS_ON;

    private MockContentResolver mCr;

    private ArrayList<String> mCallbacks;

    @Before
    public void setUp() throws Exception {
        FakeSettingsProvider.clearSettingsProvider();
        mCr = new MockContentResolver();
        mCallbacks = new ArrayList<>();
    }

    private void assertSystemSettingNotFound(String name) {
        try {
            Settings.System.getInt(mCr, name);
            fail("Setting " + name + " unexpectedly present.");
        } catch (Settings.SettingNotFoundException expected) {
            // Expected behaviour.
        }
    }

    @Test
    @SmallTest
    public void testBasicOperation() throws Exception {
        mCr.addProvider(Settings.AUTHORITY, new FakeSettingsProvider());
        assertSystemSettingNotFound(SYSTEM_SETTING);

        // Check that fake settings can be written and read back.
        Settings.System.putInt(mCr, SYSTEM_SETTING, 123);
        assertEquals(123, Settings.System.getInt(mCr, SYSTEM_SETTING));

        // Fake settings can be removed.
        Settings.System.putString(mCr, SYSTEM_SETTING, null);
        assertSystemSettingNotFound(SYSTEM_SETTING);

        // Removal is a no-op if the setting does not exist.
        Settings.System.putString(mCr, SYSTEM_SETTING, null);
        assertSystemSettingNotFound(SYSTEM_SETTING);

        // Corner case: removing a setting in a table that never existed.
        Settings.Secure.putString(mCr, SECURE_SETTING, null);
    }

    private void assertUserHandleUnsupported(UserHandle userHandle, String settingName) {
        try {
            Settings.Secure.putStringForUser(mCr, settingName, "currentUserSetting",
                    userHandle.getIdentifier());
            fail("UserHandle " + userHandle + " is unsupported and should throw");
        } catch (UnsupportedOperationException expected) {
            // Expected behaviour.
        }
    }

    @Test
    @SmallTest
    public void testMultiUserOperation() {
        mCr.addProvider(Settings.AUTHORITY, new FakeSettingsProvider());
        Settings.Secure.putStringForUser(mCr, SECURE_SETTING, "user0Setting", 0);
        Settings.Secure.putStringForUser(mCr, SECURE_SETTING, "user1Setting", 1);
        assertEquals("user0Setting", Settings.Secure.getStringForUser(mCr, SECURE_SETTING, 0));
        assertEquals("user1Setting", Settings.Secure.getStringForUser(mCr, SECURE_SETTING, 1));
        assertNull(Settings.Secure.getStringForUser(mCr, SECURE_SETTING, 2));

        final int currentUserId = UserHandle.getUserId(Process.myUid());
        Settings.Secure.putStringForUser(mCr, SECURE_SETTING, "currentUserSetting", currentUserId);
        assertEquals("currentUserSetting", Settings.Secure.getString(mCr, SECURE_SETTING));

        Settings.Secure.putString(mCr, SECURE_SETTING, "newValue");
        assertEquals("newValue",
                Settings.Secure.getStringForUser(mCr, SECURE_SETTING, currentUserId));

        Settings.Secure.putString(mCr, SECURE_SETTING, "newValue2");
        assertEquals("newValue2",
                Settings.Secure.getStringForUser(mCr, SECURE_SETTING, UserHandle.USER_CURRENT));

        Settings.Secure.putStringForUser(mCr, SECURE_SETTING, "newValue3", UserHandle.USER_CURRENT);
        assertEquals("newValue3", Settings.Secure.getString(mCr, SECURE_SETTING));

        assertUserHandleUnsupported(UserHandle.ALL, SECURE_SETTING);
        assertUserHandleUnsupported(UserHandle.CURRENT_OR_SELF, SECURE_SETTING);

        Settings.Global.putStringForUser(mCr, GLOBAL_SETTING, "globalSetting", 42);
        assertEquals("globalSetting", Settings.Global.getStringForUser(mCr, GLOBAL_SETTING, 1));
        assertEquals("globalSetting", Settings.Global.getStringForUser(mCr, GLOBAL_SETTING,
                UserHandle.USER_CURRENT));
        assertEquals("globalSetting", Settings.Global.getString(mCr, GLOBAL_SETTING));

        Settings.Global.putString(mCr, GLOBAL_SETTING, "newGlobalSetting");
        assertEquals("newGlobalSetting", Settings.Global.getStringForUser(mCr, GLOBAL_SETTING, 42));

    }

    private void assertCallbackReceived(String expectedCallback) {
        assertFalse("No callbacks received", mCallbacks.isEmpty());
        assertEquals(expectedCallback, mCallbacks.removeFirst());
    }

    private void assertNoCallbackReceived() {
        assertEquals(0, mCallbacks.size());
    }

    @Test
    @SmallTest
    public void testCallbacks() {
        mCr.addProvider(Settings.AUTHORITY, new FakeSettingsProvider((userId, uri) ->
                mCallbacks.add(userId + ":" + uri.toString())));

        Settings.Secure.putStringForUser(mCr, SECURE_SETTING, "value", 1);
        assertCallbackReceived("1:content://settings/secure/bluetooth_name");

        Settings.Secure.putStringForUser(mCr, SECURE_SETTING, "newvalue", 1);
        assertCallbackReceived("1:content://settings/secure/bluetooth_name");

        Settings.Secure.putStringForUser(mCr, SECURE_SETTING, "value", 2);
        assertCallbackReceived("2:content://settings/secure/bluetooth_name");

        // Callback is not called if value doesn't change.
        Settings.Secure.putStringForUser(mCr, SECURE_SETTING, "newvalue", 1);
        assertNoCallbackReceived();

        Settings.Secure.putStringForUser(mCr, SECURE_SETTING, null, 2);
        assertCallbackReceived("2:content://settings/secure/bluetooth_name");

        Settings.Secure.putStringForUser(mCr, SECURE_SETTING, null, 1);
        assertCallbackReceived("1:content://settings/secure/bluetooth_name");

        Settings.Secure.putStringForUser(mCr, SECURE_SETTING, null, 1);
        assertNoCallbackReceived();

        final int currentUserId = UserHandle.getUserId(Process.myUid());
        Settings.System.putString(mCr, SYSTEM_SETTING, "value");
        assertCallbackReceived(currentUserId + ":" + "content://settings/system/screen_brightness");

    }
}
