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

import static android.content.pm.UserInfo.FLAG_ADMIN;
import static android.content.pm.UserInfo.FLAG_FULL;
import static android.os.UserHandle.USER_SYSTEM;
import static android.os.UserManager.USER_TYPE_SYSTEM_HEADLESS;
import static android.provider.Settings.Global.DEVICE_PROVISIONED;
import static android.provider.Settings.Secure.BUGREPORT_IN_POWER_MENU;
import static android.provider.Settings.Secure.USER_SETUP_COMPLETE;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.server.pm.GenericAllowlist.ALLOWLIST_MODE_DISABLED;
import static com.android.server.pm.GenericAllowlist.ALLOWLIST_MODE_ENABLED;
import static com.android.server.pm.GenericAllowlist.STATUS_ALLOWED_ALLOWLISTING_DISABLED_WHILE_DEVICE_IS_PROVISIONING;
import static com.android.server.pm.HsumBootUserInitializer.getFullAdminFilter;
import static com.android.server.pm.HsumBootUserInitializerInitMethodTest.createUser;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.UserInfo;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.os.UserManager;
import android.provider.Settings;
import android.util.Log;

import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.server.pm.GenericAllowlist;
import com.android.server.pm.UserActivitiesAllowlist;

import com.google.common.truth.Expect;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class HsuDeviceProvisionerTest {

    private static final String TAG = HsuDeviceProvisionerTest.class.getSimpleName();

    private static final String SETUP_WIZARD_PKG = "com.google.android.setupwizard";
    private static final String SETUP_WIZARD_ACTIVITY = "SetupWizardActivity";

    private static final @UserIdInt int ADMIN_USER_ID = 8;

    @Rule public final Expect expect = Expect.create();
    @Rule
    public final ExtendedMockitoRule extendedMockito =
            new ExtendedMockitoRule.Builder(this)
                    .mockStatic(UserManager.class)
                    .mockStatic(Settings.Global.class)
                    .mockStatic(Settings.Secure.class)
                    .build();
    @Mock private ContentResolver mMockContentResolver;
    @Mock private UserManagerService mMockUms;
    @Mock private Context mMockContext;
    @Mock private PackageManager mMockPackageManager;
    private UserActivitiesAllowlist mHsuActivitiesAllowlist;

    // Using a spy so we can test that "entry point" methods (like init() and onChange()) calls
    // "feature" methods (like copySecureSettingFromFirstAdmin()); these methods are then tested in
    // isolation
    private HsuDeviceProvisioner mSpy;

    private final UserInfo mAdminUser = createUser(ADMIN_USER_ID, FLAG_ADMIN | FLAG_FULL);

    @Before
    public void setFixtures() {
        when(mMockContext.getContentResolver()).thenReturn(mMockContentResolver);
        when(mMockContext.getPackageManager()).thenReturn(mMockPackageManager);
        mHsuActivitiesAllowlist = spy(new UserActivitiesAllowlist(
                GenericAllowlist.ALLOWLIST_MODE_DISABLED, new String[0]));
        mockHsuActivitiesAllowlist(mHsuActivitiesAllowlist);
        mSpy = spy(new HsuDeviceProvisioner(mMockContext, new Handler(Looper.getMainLooper()),
                mMockUms));
    }

    @Test
    public void testInit_notProvisioned() {
        mockIsDeviceProvisioned(false);
        // isDeviceUpgrading() shouldn't be called, but if it's, we return true to verify
        // onDeviceUpgrading() is not called
        mockIsDeviceUpgrading(true);

        mSpy.init();

        verifyContentObserverRegistered();
        verifyOnDeviceUpgradingNeverCalled();
    }

    @Test
    public void testInit_provisioned_deviceNotUpgrading() {
        mockIsDeviceProvisioned(true);
        mockIsDeviceUpgrading(false);
        ignoreOnDeviceUpgrading();

        mSpy.init();

        verifyContentObserverNeverRegistered();
        verifyOnDeviceUpgradingNeverCalled();
    }

    @Test
    public void testInit_provisioned_deviceUpgrading() {
        mockIsDeviceProvisioned(true);
        mockIsDeviceUpgrading(true);
        ignoreOnDeviceUpgrading();

        mSpy.init();

        verifyContentObserverNeverRegistered();
        verifyOnDeviceUpgradingCalled();
    }

    @Test
    public void testOnDeviceUpgrading() {
        ignoreCopySecureSettingFromFirstAdmin();

        mSpy.onDeviceUpgrading();

        verifyCopySecureSettingFromFirstAdminCalled();
    }

    @Test
    public void testOnChange_notProvisioned() {
        mockIsDeviceProvisioned(false);

        mSpy.onChange(true);

        verifyContentObserverNeverUnregistered();
        verifyNoSecureSettingsSet();
        verifyCopySecureSettingFromFirstAdminNeverCalled();
        verifyDisableSetupWizardHomeForSystemUserNeverCalled();
    }

    @Test
    public void testOnChange_provisioned() {
        mockIsDeviceProvisioned(true);

        mSpy.onChange(true);

        verifyContentObserverUnregistered(mSpy);
        verifySettingCopied(USER_SETUP_COMPLETE, 1);
        verifyCopySecureSettingFromFirstAdminCalled();
        verifyDisableSetupWizardHomeForSystemUserCalled();
    }

    @Test
    public void testCopySecureSettingFromFirstAdmin_noUsers() {
        mockGetAdmins();

        mSpy.copySecureSettingFromFirstAdmin();

        verifyCopySecureSettingFromSourceUserNeverCalled();
    }

    @Test
    public void testCopySecureSettingFromFirstAdmin_withUsers() {
        mockGetAdmins(mAdminUser);

        mSpy.copySecureSettingFromFirstAdmin();

        verifyCopySecureSettingFromSourceUser(ADMIN_USER_ID);
    }

    @Test
    public void testCopySecureSettingFromSourceUser_systemUser() {
        mockSettingValue(BUGREPORT_IN_POWER_MENU, 1, USER_SYSTEM);

        mSpy.copySecureSettingFromSourceUser(USER_SYSTEM);

        verifySettingNeverCopiedForUser(BUGREPORT_IN_POWER_MENU);
    }

    @Test
    public void testCopySecureSettingFromSourceUser_nonSystemUser() {
        mockSettingValue(BUGREPORT_IN_POWER_MENU, 1, ADMIN_USER_ID);

        mSpy.copySecureSettingFromSourceUser(ADMIN_USER_ID);

        verifySettingCopiedForUser(BUGREPORT_IN_POWER_MENU, 1, USER_SYSTEM);
    }

    @Test
    public void testDisableSetupWizardHomeForSystemUser_noMatches() {

        mSpy.disableSetupWizardHomeForSystemUser();

        verifyDisableSuwNeverCalled();
    }

    @Test
    public void testDisableSetupWizardHomeForSystemUser_matches() {
        mockQuerySetupWizardHomeActivity();

        mSpy.disableSetupWizardHomeForSystemUser();

        verifyDisableSuwCalled();
    }

    @Test
    public void testDeviceNotProvisioned_hsuActivitiesAllowlistOverridden() {
        mockIsDeviceProvisioned(false);

        mSpy.init();

        // Verify that the allowlist overridden status is not set when the device is to be
        // provisioned.
        verifyHsuActivitiesAllowlistOverriddenDisallowedStatus(
                STATUS_ALLOWED_ALLOWLISTING_DISABLED_WHILE_DEVICE_IS_PROVISIONING);
    }

    @Test
    public void testDeviceBeingProvisioned_initialStatusNotOverridden_overriddenStatusRemains() {
        mockIsDeviceProvisioned(false);

        mSpy.init();
        verifyHsuActivitiesAllowlistOverriddenDisallowedStatus(
                STATUS_ALLOWED_ALLOWLISTING_DISABLED_WHILE_DEVICE_IS_PROVISIONING);

        mockIsDeviceProvisioned(true);
        mSpy.onChange(false);

        // Verify that the allowlist overridden status is still null, when the device is
        // provisioned.
        verifyHsuActivitiesAllowlistOverriddenDisallowedStatus(null);
    }

    @Test
    public void testDeviceProvisioned_overriddenStatusNotSet() {
        mockIsDeviceProvisioned(true);

        mSpy.init();

        // For device already provisioned, init() should not set the overridden status.
        verifyHsuActivitiesAllowlistSetOverriddenDisallowedStatusNeverCalled();
    }

    @Test
    public void testDeviceBeingProvisioned_hsuActivitiesAllowlistIsNull() {
        mockIsDeviceProvisioned(false);
        mockHsuActivitiesAllowlist(null);

        mSpy = spy(new HsuDeviceProvisioner(mMockContext, new Handler(Looper.getMainLooper()),
                mMockUms));

        mSpy.init();

        mSpy.onChange(false);
        // Assert: no exception is thrown when the allowlist is null, and init() and onChange() are
        // called.
    }

    @Test
    public void testDeviceProvisioned_hsuActivitiesAllowlistIsNull() {
        mockIsDeviceProvisioned(true);
        mockHsuActivitiesAllowlist(null);

        mSpy = spy(new HsuDeviceProvisioner(mMockContext, new Handler(Looper.getMainLooper()),
                mMockUms));

        mSpy.init();

        // Assert: no exception is thrown when the allowlist is null, and init() is called.
    }

    private void mockIsDeviceProvisioned(boolean value) {
        Log.v(TAG, "mockIsDeviceProvisioned(" + value + ")");
        doReturn(value ? 1 : 0).when(() -> Settings.Global.getInt(any(), eq(DEVICE_PROVISIONED)));
    }

    private void mockIsDeviceUpgrading(boolean value) {
        Log.v(TAG, "mockIsDeviceUpgrading(" + value + ")");
        when(mMockPackageManager.isDeviceUpgrading()).thenReturn(value);
    }

    private void mockSettingValue(String settingName, int value, @UserIdInt int userId) {
        Log.d(TAG, "mockSettingValue(" + settingName + ", " + value + ", " + userId + ")");
        doReturn(value).when(() -> Settings.Secure.getIntForUser(
                                eq(mMockContentResolver), eq(settingName), anyInt(), eq(userId)));
    }

    private void mockGetAdmins(UserInfo... users) {
        UserFilter filter = getFullAdminFilter();
        List<UserInfo> list = Arrays.asList(users);
        Log.d(TAG, "mockGetAdmins(): mMockUms.getUserFilter(" + filter + ") will return " + list);
        when(mMockUms.getUsers(filter)).thenReturn(list);
    }

    private void mockHsuActivitiesAllowlist(@Nullable UserActivitiesAllowlist allowlist) {
        Log.d(TAG, "mockHsuActivitiesAllowlist(): mMockUms.getActivitiesAllowlist("
                + USER_TYPE_SYSTEM_HEADLESS + ") will return " + allowlist);
        when(mMockUms.getActivitiesAllowlist(USER_TYPE_SYSTEM_HEADLESS)).thenReturn(allowlist);
    }

    private void verifyNoSecureSettingsSet() {
        verify(() -> Settings.Secure.putInt(any(), any(), anyInt()), never());
    }

    private void verifyContentObserverUnregistered(ContentObserver contentObserver) {
        verify(mMockContentResolver).unregisterContentObserver(contentObserver);
    }

    private void verifyContentObserverNeverRegistered() {
        verify(mMockContentResolver, never()).registerContentObserver(any(), anyBoolean(), any());
    }

    private void verifyContentObserverNeverUnregistered() {
        verify(mMockContentResolver, never()).unregisterContentObserver(any());
    }

    private void verifyHsuActivitiesAllowlistOverriddenDisallowedStatus(
            Integer overriddenDisallowedStatus) {
        expect.that(mHsuActivitiesAllowlist.getOverriddenDisallowedStatus())
                .isEqualTo(overriddenDisallowedStatus);
    }

    private void verifyHsuActivitiesAllowlistSetOverriddenDisallowedStatusNeverCalled() {
        verify(mHsuActivitiesAllowlist, never()).overrideDisallowedStatus(any());
    }

    private ContentObserver verifyContentObserverRegistered() {
        ArgumentCaptor<ContentObserver> captor = ArgumentCaptor.forClass(ContentObserver.class);
        verify(mMockContentResolver)
                .registerContentObserver(
                        eq(Settings.Global.getUriFor(Settings.Global.DEVICE_PROVISIONED)),
                        eq(false),
                        captor.capture());
        return captor.getValue();
    }

    private void verifyCopySecureSettingFromSourceUserNeverCalled() {
        verify(mSpy, never()).copySecureSettingFromSourceUser(anyInt());
    }

    private void verifyCopySecureSettingFromSourceUser(@UserIdInt int userId) {
        verify(mSpy).copySecureSettingFromSourceUser(userId);
    }

    private void verifyOnDeviceUpgradingCalled() {
        verify(mSpy).onDeviceUpgrading();
    }

    private void verifyOnDeviceUpgradingNeverCalled() {
        verify(mSpy, never()).onDeviceUpgrading();
    }

    private void verifyCopySecureSettingFromFirstAdminCalled() {
        verify(mSpy).copySecureSettingFromFirstAdmin();
    }

    private void verifyCopySecureSettingFromFirstAdminNeverCalled() {
        verify(mSpy, never()).copySecureSettingFromFirstAdmin();
    }

    private void verifyDisableSetupWizardHomeForSystemUserCalled() {
        verify(mSpy).disableSetupWizardHomeForSystemUser();
    }

    private void verifyDisableSetupWizardHomeForSystemUserNeverCalled() {
        verify(mSpy, never()).disableSetupWizardHomeForSystemUser();
    }

    private void ignoreOnDeviceUpgrading() {
        doNothing().when(mSpy).onDeviceUpgrading();
    }

    private void ignoreCopySecureSettingFromFirstAdmin() {
        doNothing().when(mSpy).copySecureSettingFromFirstAdmin();
    }

    private ResolveInfo createFakeResolveInfo(String packageName, String activityName) {
        ResolveInfo resolveInfo = new ResolveInfo();
        resolveInfo.activityInfo = new ActivityInfo();
        resolveInfo.activityInfo.packageName = packageName;
        resolveInfo.activityInfo.name = activityName;
        resolveInfo.activityInfo.applicationInfo = new ApplicationInfo();
        resolveInfo.activityInfo.applicationInfo.packageName = packageName;
        return resolveInfo;
    }

    private void mockQuerySetupWizardHomeActivity() {
        List<ResolveInfo> matches = Collections.singletonList(
            createFakeResolveInfo(SETUP_WIZARD_PKG, SETUP_WIZARD_ACTIVITY)
        );
        when(mMockPackageManager.queryIntentActivities(
            any(Intent.class), anyInt())).thenReturn(matches);
    }

    private void verifyDisableSuwCalled() {
        ComponentName expectedComponent =
            new ComponentName(SETUP_WIZARD_PKG, SETUP_WIZARD_ACTIVITY);
        verify(mMockPackageManager).setComponentEnabledSetting(
            expectedComponent,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP);
    }

    private void verifyDisableSuwNeverCalled() {
        verify(mMockPackageManager, never()).setComponentEnabledSetting(any(), anyInt(), anyInt());
    }

    private void verifySettingNeverCopiedForUser(String settingName) {
        verify(() ->
                Settings.Secure.putIntForUser(any(), eq(settingName), anyInt(), anyInt()), never());
    }

    private void verifySettingCopied(String settingName, int value) {
        verify(() -> Settings.Secure.putInt(mMockContentResolver, settingName, value));
    }

    private void verifySettingCopiedForUser(String settingName, int value, @UserIdInt int userId) {
        verify(() ->
                Settings.Secure.putIntForUser(mMockContentResolver, settingName, value, userId));
    }
}
