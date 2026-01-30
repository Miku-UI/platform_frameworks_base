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

package com.android.server.tv.watchdogservice;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManagerInternal;
import android.app.NotificationManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.RemoteException;
import android.os.UserHandle;

import androidx.test.runner.AndroidJUnit4;

import com.google.common.collect.ImmutableSet;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
public final class TvWatchdogHelperTest {

    private static final int USER_ID = 0;
    private static final String PACKAGE_NAME = "com.example.test";
    private static final String OTHER_PACKAGE_NAME = "com.example.other";
    private static final String USER_PACKAGE_ID = USER_ID + ":" + PACKAGE_NAME;
    private static final int NOTIFICATION_ID = 123;
    private static final boolean DEBUG_ENABLED = true;

    @Rule public MockitoRule rule = MockitoJUnit.rule();

    @Mock private Context mMockContext;
    @Mock private Context mMockUserContext;
    @Mock private TvWatchdogHelper.Callback mMockCallback;
    @Mock private NotificationManager mMockNotificationManager;
    @Mock private PackageManager mMockPackageManager;
    @Mock private ContentResolver mMockContentResolver;
    @Mock private ActivityManagerInternal mMockActivityManagerInternal;
    private TvWatchdogHelper mHelper;

    @Before
    public void setUp() throws Exception {
        when(mMockContext.getSystemServiceName(NotificationManager.class))
                .thenReturn(Context.NOTIFICATION_SERVICE);
        when(mMockContext.getSystemService(Context.NOTIFICATION_SERVICE))
                .thenReturn(mMockNotificationManager);
        when(mMockContext.createPackageContextAsUser(anyString(), anyInt(), any()))
                .thenReturn(mMockUserContext);
        when(mMockContext.getPackageName()).thenReturn(PACKAGE_NAME);
        when(mMockUserContext.getContentResolver()).thenReturn(mMockContentResolver);
        when(mMockUserContext.getPackageManager()).thenReturn(mMockPackageManager);
        when(mMockActivityManagerInternal.getCurrentUserId()).thenReturn(USER_ID);

        mHelper =
                spy(
                        new TvWatchdogHelper(
                                mMockContext,
                                mMockCallback,
                                DEBUG_ENABLED,
                                mMockActivityManagerInternal));
    }

    @Test
    public void onPackageEnabledLocked_removesPackageFromSettings() {
        doReturn(PACKAGE_NAME)
                .when(mHelper)
                .getSecureStringSettingForUser(
                        eq(mMockContentResolver),
                        eq(TvWatchdogSettings.Secure.KEY_PACKAGES_DISABLED_ON_RESOURCE_OVERUSE),
                        eq(USER_ID));
        doNothing()
                .when(mHelper)
                .putSecureStringSetting(
                        eq(mMockContentResolver),
                        eq(TvWatchdogSettings.Secure.KEY_PACKAGES_DISABLED_ON_RESOURCE_OVERUSE),
                        anyString());

        mHelper.onPackageEnabledLocked(PACKAGE_NAME, USER_ID);

        verify(mHelper)
                .putSecureStringSetting(
                        eq(mMockContentResolver),
                        eq(TvWatchdogSettings.Secure.KEY_PACKAGES_DISABLED_ON_RESOURCE_OVERUSE),
                        eq(""));
    }

    @Test
    public void onPackageEnabledLocked_removesFromExistingList() {
        String initialSetting = PACKAGE_NAME + ";" + OTHER_PACKAGE_NAME;
        doReturn(initialSetting)
                .when(mHelper)
                .getSecureStringSettingForUser(
                        eq(mMockContentResolver),
                        eq(TvWatchdogSettings.Secure.KEY_PACKAGES_DISABLED_ON_RESOURCE_OVERUSE),
                        eq(USER_ID));
        doNothing()
                .when(mHelper)
                .putSecureStringSetting(
                        eq(mMockContentResolver),
                        eq(TvWatchdogSettings.Secure.KEY_PACKAGES_DISABLED_ON_RESOURCE_OVERUSE),
                        anyString());

        mHelper.onPackageEnabledLocked(PACKAGE_NAME, USER_ID);

        verify(mHelper)
                .putSecureStringSetting(
                        eq(mMockContentResolver),
                        eq(TvWatchdogSettings.Secure.KEY_PACKAGES_DISABLED_ON_RESOURCE_OVERUSE),
                        eq(OTHER_PACKAGE_NAME));
    }

    @Test
    public void onPackageDisabledLocked_addsPackageToSettings() {
        doReturn("")
                .when(mHelper)
                .getSecureStringSettingForUser(
                        eq(mMockContentResolver),
                        eq(TvWatchdogSettings.Secure.KEY_PACKAGES_DISABLED_ON_RESOURCE_OVERUSE),
                        eq(USER_ID));
        doNothing()
                .when(mHelper)
                .putSecureStringSetting(
                        eq(mMockContentResolver),
                        eq(TvWatchdogSettings.Secure.KEY_PACKAGES_DISABLED_ON_RESOURCE_OVERUSE),
                        anyString());

        mHelper.onPackageDisabledLocked(PACKAGE_NAME, USER_ID);

        verify(mHelper)
                .putSecureStringSetting(
                        eq(mMockContentResolver),
                        eq(TvWatchdogSettings.Secure.KEY_PACKAGES_DISABLED_ON_RESOURCE_OVERUSE),
                        eq(PACKAGE_NAME));
    }

    @Test
    public void onPackageDisabledLocked_addsToExistingList() {
        doReturn(OTHER_PACKAGE_NAME)
                .when(mHelper)
                .getSecureStringSettingForUser(
                        eq(mMockContentResolver),
                        eq(TvWatchdogSettings.Secure.KEY_PACKAGES_DISABLED_ON_RESOURCE_OVERUSE),
                        eq(USER_ID));
        doNothing()
                .when(mHelper)
                .putSecureStringSetting(
                        eq(mMockContentResolver),
                        eq(TvWatchdogSettings.Secure.KEY_PACKAGES_DISABLED_ON_RESOURCE_OVERUSE),
                        anyString());

        mHelper.onPackageDisabledLocked(PACKAGE_NAME, USER_ID);

        ArgumentCaptor<String> settingCaptor = ArgumentCaptor.forClass(String.class);
        verify(mHelper)
                .putSecureStringSetting(
                        eq(mMockContentResolver),
                        eq(TvWatchdogSettings.Secure.KEY_PACKAGES_DISABLED_ON_RESOURCE_OVERUSE),
                        settingCaptor.capture());
        Set<String> updatedPackages = ImmutableSet.copyOf(settingCaptor.getValue().split(";"));
        assertThat(updatedPackages).containsExactly(PACKAGE_NAME, OTHER_PACKAGE_NAME);
    }

    @Test
    public void onPackageDisabledLocked_doesNotAddDuplicate() {
        doReturn(PACKAGE_NAME)
                .when(mHelper)
                .getSecureStringSettingForUser(
                        eq(mMockContentResolver),
                        eq(TvWatchdogSettings.Secure.KEY_PACKAGES_DISABLED_ON_RESOURCE_OVERUSE),
                        eq(USER_ID));

        mHelper.onPackageDisabledLocked(PACKAGE_NAME, USER_ID);

        verify(mHelper, never())
                .putSecureStringSetting(any(ContentResolver.class), anyString(), anyString());
    }

    @Test
    public void onPackageDisabledLocked_handlesContextCreationFailure() throws Exception {
        when(mMockContext.createPackageContextAsUser(anyString(), anyInt(), any(UserHandle.class)))
                .thenThrow(new NameNotFoundException("Failed to create context"));

        mHelper.onPackageDisabledLocked(PACKAGE_NAME, USER_ID);

        verify(mHelper, never())
                .getSecureStringSettingForUser(any(ContentResolver.class), anyString(), anyInt());
        verify(mHelper, never())
                .putSecureStringSetting(any(ContentResolver.class), anyString(), anyString());
    }

    @Test
    public void getApplicationEnabledSettingForUser_handlesNameNotFoundException()
            throws Exception {
        when(mMockContext.createPackageContextAsUser(anyString(), anyInt(), any(UserHandle.class)))
                .thenThrow(new NameNotFoundException("Package not found"));

        int setting = mHelper.getApplicationEnabledSettingForUser(PACKAGE_NAME, USER_ID);

        assertThat(setting).isEqualTo(PackageManager.COMPONENT_ENABLED_STATE_DISABLED);
    }

    @Test
    public void getApplicationEnabledSettingForUser_returnsCorrectSetting() throws Exception {
        when(mMockPackageManager.getApplicationEnabledSetting(PACKAGE_NAME))
                .thenReturn(PackageManager.COMPONENT_ENABLED_STATE_ENABLED);

        int setting = mHelper.getApplicationEnabledSettingForUser(PACKAGE_NAME, USER_ID);

        assertThat(setting).isEqualTo(PackageManager.COMPONENT_ENABLED_STATE_ENABLED);
        verify(mMockPackageManager).getApplicationEnabledSetting(PACKAGE_NAME);
    }

    @Test
    public void setApplicationEnabledSettingForUser_callsPackageManager() throws RemoteException {
        final int newState = PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        final int flags = PackageManager.DONT_KILL_APP;
        final String callingPackage = "com.android.test.caller";

        mHelper.setApplicationEnabledSettingForUser(
                PACKAGE_NAME, newState, flags, USER_ID, callingPackage);

        verify(mMockPackageManager)
                .setApplicationEnabledSetting(eq(PACKAGE_NAME), eq(newState), eq(flags));
    }

    @Test
    public void sendUserNotifications_sendsForNewPackage() {
        when(mMockCallback.reserveNotificationSlot(USER_PACKAGE_ID)).thenReturn(NOTIFICATION_ID);
        List<String> packages = Arrays.asList(PACKAGE_NAME);
        doReturn(true).when(mHelper).postNotification(anyInt(), anyString(), any());

        List<String> notifiedPackages = mHelper.sendUserNotifications(USER_ID, packages);

        assertThat(notifiedPackages).containsExactly(PACKAGE_NAME);
        verify(mHelper).postNotification(eq(NOTIFICATION_ID), eq(PACKAGE_NAME), any());
    }

    @Test
    public void sendUserNotifications_dropsDuplicateNotification() {
        when(mMockCallback.reserveNotificationSlot(USER_PACKAGE_ID)).thenReturn(-1);
        List<String> packages = Arrays.asList(PACKAGE_NAME);

        List<String> notifiedPackages = mHelper.sendUserNotifications(USER_ID, packages);

        assertThat(notifiedPackages).isEmpty();
        verify(mHelper, never()).postNotification(anyInt(), anyString(), any(UserHandle.class));
    }

    @Test
    public void sendUserNotifications_cancelsSlotOnFailureToPost() {
        when(mMockCallback.reserveNotificationSlot(USER_PACKAGE_ID)).thenReturn(NOTIFICATION_ID);
        doReturn(false).when(mHelper).postNotification(anyInt(), anyString(), any());
        List<String> packages = Arrays.asList(PACKAGE_NAME);

        List<String> notifiedPackages = mHelper.sendUserNotifications(USER_ID, packages);

        assertThat(notifiedPackages).isEmpty();
        verify(mMockCallback).cancelNotificationSlot(USER_PACKAGE_ID, NOTIFICATION_ID);
    }

    @Test
    public void sendUserNotifications_abortsForNonCurrentUser() {
        final int currentUserId = USER_ID + 1;
        when(mMockActivityManagerInternal.getCurrentUserId()).thenReturn(currentUserId);

        List<String> result = mHelper.sendUserNotifications(USER_ID, Arrays.asList(PACKAGE_NAME));

        assertThat(result).isNull();
        verify(mHelper, never()).postNotification(anyInt(), anyString(), any(UserHandle.class));
    }
}
