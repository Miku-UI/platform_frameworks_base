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
package com.android.server.modes;

import static android.app.modes.ContextualMode.STATE_ACTIVE;
import static android.app.modes.ContextualMode.STATE_INACTIVE;
import static android.app.modes.ContextualMode.STATE_UNKNOWN;
import static android.app.modes.ContextualMode.TYPE_BEDTIME;
import static android.app.modes.ContextualMode.TYPE_DRIVING;
import static android.app.modes.ContextualMode.TYPE_MANUAL_DO_NOT_DISTURB;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.Manifest.permission;
import android.annotation.Nullable;
import android.app.ActivityManagerInternal;
import android.app.AutomaticZenRule;
import android.app.NotificationManager;
import android.app.modes.ContextualMode;
import android.app.modes.ContextualModesMutation;
import android.app.modes.IContextualModeListener;
import android.app.modes.IContextualModeManager;
import android.app.modes.IContextualModeSyncListener;
import android.content.Context;
import android.content.res.Resources;
import android.net.Uri;
import android.os.PermissionEnforcer;
import android.os.UserHandle;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.provider.Settings;
import android.provider.Settings.Secure;
import android.service.notification.Flags;
import android.test.mock.MockContentResolver;
import android.util.Pair;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.internal.util.test.FakeSettingsProvider;
import com.android.server.LocalServices;
import com.android.server.notification.NotificationManagerInternal;
import com.android.server.notification.ZenModeHelper;
import com.android.server.pm.UserManagerInternal;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@SmallTest
@RunWith(AndroidJUnit4.class)
@EnableFlags(Flags.FLAG_ENABLE_DND_SYNC)
public class ContextualModeManagerServiceTest {
    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Rule public final MockitoRule mockito = MockitoJUnit.rule();

    @Mock private NotificationManagerInternal mNotificationManagerInternal;
    @Mock private UserManagerInternal mUserManagerInternal;
    @Mock private ActivityManagerInternal mActivityManagerInternal;
    @Mock private Resources mResources;
    @Mock private PermissionEnforcer mPermissionEnforcer;

    private Context mContext;
    private ContextualModeManagerService mService;
    private IContextualModeManager mBinderService;
    private MockContentResolver mContentResolver;

    @Before
    public void setUp() {
        LocalServices.addService(NotificationManagerInternal.class, mNotificationManagerInternal);
        LocalServices.addService(UserManagerInternal.class, mUserManagerInternal);
        LocalServices.addService(ActivityManagerInternal.class, mActivityManagerInternal);
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        permission.MANAGE_CONTEXTUAL_MODES, permission.WRITE_SECURE_SETTINGS);
        mContext = ApplicationProvider.getApplicationContext();
        FakeSettingsProvider fakeSettingsProvider =
                new FakeSettingsProvider(
                        (userId, uri) ->
                                mService.notifyModeSyncEnabledChanged(UserHandle.of(userId)));
        mContentResolver = new MockContentResolver(mContext);
        mContentResolver.addProvider(Settings.AUTHORITY, fakeSettingsProvider);
        when(mUserManagerInternal.exists(mContext.getUserId())).thenReturn(true);
        when(mActivityManagerInternal.getCurrentUserId()).thenReturn(mContext.getUserId());
        when(mResources.getBoolean(com.android.internal.R.bool.config_supportContextualModeSync))
                .thenReturn(true);
        when(mNotificationManagerInternal.hasZenModeConfig(mContext.getUser())).thenReturn(true);
        startNewService();
    }

    @After
    public void tearDown() {
        LocalServices.removeAllServicesForTest();
        FakeSettingsProvider.clearSettingsProvider();
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .dropShellPermissionIdentity();
    }

    @Test
    public void testIsModeSyncSupported_requiresNoPermission() throws Exception {
        denyAllPermissions();

        mBinderService.isModeSyncSupported();
    }

    @EnableFlags(com.android.crossdevicesync.flags.Flags.FLAG_START_SYNC_SERVICE_ON_BOOT)
    @Test
    public void testIsModeSyncSupported_configEnabled() throws Exception {
        assertThat(mBinderService.isModeSyncSupported()).isTrue();
    }

    @EnableFlags(com.android.crossdevicesync.flags.Flags.FLAG_START_SYNC_SERVICE_ON_BOOT)
    @Test
    public void testIsModeSyncSupported_configDisabled() throws Exception {
        when(mResources.getBoolean(com.android.internal.R.bool.config_supportContextualModeSync))
                .thenReturn(false);
        startNewService();

        assertThat(mBinderService.isModeSyncSupported()).isFalse();
    }

    @DisableFlags(com.android.crossdevicesync.flags.Flags.FLAG_START_SYNC_SERVICE_ON_BOOT)
    @Test
    public void testIsModeSyncSupported_flagDisabled() throws Exception {
        assertThat(mBinderService.isModeSyncSupported()).isFalse();
    }

    @Test
    public void testIsModeSyncEnabled_forSelf_requiresNoPermission() throws Exception {
        denyAllPermissions();

        mBinderService.isModeSyncEnabled(mContext.getUser());
    }

    @Test
    public void testIsModeSyncEnabled_forDifferentUser_requiresMultiUserPermission()
            throws Exception {
        UserHandle user = setUpUser(1);

        mBinderService.isModeSyncEnabled(user);

        denyPermission(permission.INTERACT_ACROSS_USERS);

        assertThrows(SecurityException.class, () -> mBinderService.isModeSyncEnabled(user));
    }

    @EnableFlags(com.android.crossdevicesync.flags.Flags.FLAG_START_SYNC_SERVICE_ON_BOOT)
    @Test
    public void testIsModeSyncEnabled_configDisabled() throws Exception {
        when(mResources.getBoolean(com.android.internal.R.bool.config_supportContextualModeSync))
                .thenReturn(false);
        startNewService();
        Secure.putInt(mContentResolver, Secure.CONTEXTUAL_MODE_SYNC_ENABLED, 1);

        assertThat(mBinderService.isModeSyncEnabled(mContext.getUser())).isFalse();
    }

    @EnableFlags(com.android.crossdevicesync.flags.Flags.FLAG_START_SYNC_SERVICE_ON_BOOT)
    @Test
    public void testIsModeSyncEnabled_settingDisabled() throws Exception {
        Secure.putInt(mContentResolver, Secure.CONTEXTUAL_MODE_SYNC_ENABLED, 0);

        assertThat(mBinderService.isModeSyncEnabled(mContext.getUser())).isFalse();
    }

    @EnableFlags(com.android.crossdevicesync.flags.Flags.FLAG_START_SYNC_SERVICE_ON_BOOT)
    @Test
    public void testIsModeSyncEnabled_settingEnabled() throws Exception {
        Secure.putInt(mContentResolver, Secure.CONTEXTUAL_MODE_SYNC_ENABLED, 1);

        assertThat(mBinderService.isModeSyncEnabled(mContext.getUser())).isTrue();
    }

    @EnableFlags(com.android.crossdevicesync.flags.Flags.FLAG_START_SYNC_SERVICE_ON_BOOT)
    @Test
    public void testIsModeSyncEnabled_defaultEnabled() throws Exception {
        Secure.putString(mContentResolver, Secure.CONTEXTUAL_MODE_SYNC_ENABLED, null);

        assertThat(mBinderService.isModeSyncEnabled(mContext.getUser())).isTrue();
    }

    @DisableFlags(com.android.crossdevicesync.flags.Flags.FLAG_START_SYNC_SERVICE_ON_BOOT)
    @Test
    public void testIsModeSyncEnabled_flagDisabled() throws Exception {
        Secure.putInt(mContentResolver, Secure.CONTEXTUAL_MODE_SYNC_ENABLED, 1);

        assertThat(mBinderService.isModeSyncEnabled(mContext.getUser())).isFalse();
    }

    @Test
    public void testIsModeSyncEnabled_unknownUser() {
        assertThrows(
                IllegalArgumentException.class,
                () -> mBinderService.isModeSyncEnabled(UserHandle.of(1)));
    }

    @Test
    public void testIsModeSyncEnabled_allUser() {
        assertThrows(
                IllegalArgumentException.class,
                () -> mBinderService.isModeSyncEnabled(UserHandle.ALL));
    }

    @EnableFlags(com.android.crossdevicesync.flags.Flags.FLAG_START_SYNC_SERVICE_ON_BOOT)
    @Test
    public void testIsModeSyncEnabled_currentUser() throws Exception {
        Secure.putInt(mContentResolver, Secure.CONTEXTUAL_MODE_SYNC_ENABLED, 1);

        assertThat(mBinderService.isModeSyncEnabled(UserHandle.CURRENT)).isTrue();
    }

    @Test
    public void testSetModeSyncEnabled_requiresWriteSecureSettingsPermission() throws Exception {
        denyPermission(permission.INTERACT_ACROSS_USERS);

        mBinderService.setModeSyncEnabled(mContext.getUser(), true);

        denyPermission(permission.WRITE_SECURE_SETTINGS);

        assertThrows(
                SecurityException.class,
                () -> mBinderService.setModeSyncEnabled(mContext.getUser(), true));
    }

    @Test
    public void testSetModeSyncEnabled_differentUser_requiresMultiUserPermission()
            throws Exception {
        UserHandle user = setUpUser(1);

        mBinderService.setModeSyncEnabled(user, true);

        denyPermission(permission.INTERACT_ACROSS_USERS);

        assertThrows(SecurityException.class, () -> mBinderService.setModeSyncEnabled(user, true));
    }

    @EnableFlags(com.android.crossdevicesync.flags.Flags.FLAG_START_SYNC_SERVICE_ON_BOOT)
    @Test
    public void testSetModeSyncEnabled() throws Exception {
        mBinderService.setModeSyncEnabled(mContext.getUser(), true);

        assertThat(mBinderService.isModeSyncEnabled(mContext.getUser())).isTrue();

        mBinderService.setModeSyncEnabled(mContext.getUser(), false);

        assertThat(mBinderService.isModeSyncEnabled(mContext.getUser())).isFalse();
    }

    @Test
    public void testSetModeSyncEnabled_unknownUser() {
        assertThrows(
                IllegalArgumentException.class,
                () -> mBinderService.setModeSyncEnabled(UserHandle.of(1), true));
    }

    @Test
    public void testSetModeSyncEnabled_allUser() {
        assertThrows(
                IllegalArgumentException.class,
                () -> mBinderService.setModeSyncEnabled(UserHandle.ALL, true));
    }

    @EnableFlags(com.android.crossdevicesync.flags.Flags.FLAG_START_SYNC_SERVICE_ON_BOOT)
    @Test
    public void testSetModeSyncEnabled_currentUser() throws Exception {
        mBinderService.setModeSyncEnabled(UserHandle.CURRENT, true);

        assertThat(mBinderService.isModeSyncEnabled(UserHandle.CURRENT)).isTrue();
    }

    @Test
    public void testGetModes_requiresModesPermission() throws Exception {
        // INTERACT_ACROSS_USERS permission should not be required.
        denyPermission(permission.INTERACT_ACROSS_USERS);

        mBinderService.getModes(mContext.getUser());

        denyPermission(permission.MANAGE_CONTEXTUAL_MODES);

        assertThrows(SecurityException.class, () -> mBinderService.getModes(mContext.getUser()));
    }

    @Test
    public void testGetModes_differentUser_requiresMultiUserPermission() throws Exception {
        UserHandle user = setUpUser(1);

        mBinderService.getModes(user);

        denyPermission(permission.INTERACT_ACROSS_USERS);

        assertThrows(SecurityException.class, () -> mBinderService.getModes(user));
    }

    @Test
    public void testGetModes() throws Exception {
        UserHandle user1 = UserHandle.of(1);
        UserHandle user2 = UserHandle.of(2);
        mockForUser(user1)
                .setManualZenRuleActive(/* isActive= */ true)
                .setAutomaticZenRuleActive(
                        "id", AutomaticZenRule.TYPE_BEDTIME, /* isActive= */ true)
                .commit();
        mockForUser(user2)
                .setAutomaticZenRuleActive(
                        "id", AutomaticZenRule.TYPE_BEDTIME, /* isActive= */ false)
                .commit();
        // User 3 has no modes.
        UserHandle user3 = setUpUser(3);

        // Verify user1 modes.
        List<ContextualMode> modes1 = mBinderService.getModes(user1);
        assertThat(modes1).hasSize(2);
        verifyHasMode(modes1, TYPE_MANUAL_DO_NOT_DISTURB, STATE_ACTIVE);
        verifyHasMode(modes1, TYPE_BEDTIME, STATE_ACTIVE);

        // Verify user2 modes.
        List<ContextualMode> modes2 = mBinderService.getModes(user2);
        assertThat(modes2).hasSize(2);
        verifyHasMode(modes2, TYPE_MANUAL_DO_NOT_DISTURB, STATE_INACTIVE);
        verifyHasMode(modes2, TYPE_BEDTIME, STATE_INACTIVE);

        // Verify user3 hsa no modes.
        assertThat(mBinderService.getModes(user3)).isEmpty();

        // Verify accessing current user modes.
        List<ContextualMode> modesCurrent = mBinderService.getModes(user2);
        assertThat(modesCurrent).isEqualTo(modes2);

        // Verify that a non-existing user will throw.
        assertThrows(
                IllegalArgumentException.class, () -> mBinderService.getModes(UserHandle.of(10)));

        // Verify that accessing USER_ALL will throw.
        assertThrows(IllegalArgumentException.class, () -> mBinderService.getModes(UserHandle.ALL));
    }

    @Test
    public void testMutateModes_requiresModesPermission() throws Exception {
        ContextualModesMutation mutation = new ContextualModesMutation.Builder().build();
        // INTERACT_ACROSS_USERS permission should not be required.
        denyPermission(permission.INTERACT_ACROSS_USERS);

        mBinderService.mutateModes(mContext.getUser(), mutation);

        denyPermission(permission.MANAGE_CONTEXTUAL_MODES);

        assertThrows(
                SecurityException.class,
                () -> mBinderService.mutateModes(mContext.getUser(), mutation));
    }

    @Test
    public void testMutateModes_differentUser_requiresMultiUserPermission() throws Exception {
        UserHandle user = setUpUser(1);
        ContextualModesMutation mutation = new ContextualModesMutation.Builder().build();

        mBinderService.mutateModes(user, mutation);

        denyPermission(permission.INTERACT_ACROSS_USERS);

        assertThrows(SecurityException.class, () -> mBinderService.mutateModes(user, mutation));
    }

    @Test
    public void testMutateModes_modesUpdated() throws Exception {
        ContextualMode manualDnd =
                new ContextualMode.Builder("id1")
                        .setType(TYPE_MANUAL_DO_NOT_DISTURB)
                        .setState(STATE_ACTIVE)
                        .build();
        ContextualMode btm =
                new ContextualMode.Builder("id2")
                        .setType(TYPE_BEDTIME)
                        .setState(STATE_INACTIVE)
                        .build();
        ContextualModesMutation mutation =
                new ContextualModesMutation.Builder()
                        .addUpdatedMode(manualDnd)
                        .addUpdatedMode(btm)
                        .build();
        UserHandle user = UserHandle.of(1);
        // Initial mode has BTM.
        mockForUser(user).setAutomaticZenRuleActive(btm.getId(), TYPE_BEDTIME, true).commit();

        mBinderService.mutateModes(user, mutation);

        // Verify modes are changed.
        verify(mNotificationManagerInternal).setManualZenRuleActive(user, true);
        verify(mNotificationManagerInternal).setAutomaticZenRuleActive(user, btm.getId(), false);
    }

    @Test
    public void testMutateModes_updateNonExistingMode_throwsException() {
        ContextualMode btm =
                new ContextualMode.Builder("id")
                        .setType(TYPE_BEDTIME)
                        .setState(STATE_INACTIVE)
                        .build();
        ContextualModesMutation mutation =
                new ContextualModesMutation.Builder().addUpdatedMode(btm).build();

        // User has no BTM, so update will fail.
        assertThrows(
                IllegalArgumentException.class,
                () -> mBinderService.mutateModes(mContext.getUser(), mutation));
    }

    @Test
    public void testMutateModes_illegalUser() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mBinderService.mutateModes(
                                UserHandle.of(1), new ContextualModesMutation.Builder().build()));

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mBinderService.mutateModes(
                                UserHandle.ALL, new ContextualModesMutation.Builder().build()));
    }

    @Test
    public void testMutateModes_modeTypeMismatch_throwsException() {
        ContextualMode btm =
                new ContextualMode.Builder("id")
                        .setType(TYPE_BEDTIME)
                        .setState(STATE_INACTIVE)
                        .build();
        ContextualModesMutation mutation =
                new ContextualModesMutation.Builder().addUpdatedMode(btm).build();
        UserHandle user = UserHandle.of(1);
        // Same id but type is different.
        mockForUser(user).setAutomaticZenRuleActive(btm.getId(), TYPE_DRIVING, true).commit();

        assertThrows(
                IllegalArgumentException.class, () -> mBinderService.mutateModes(user, mutation));
    }

    @Test
    public void testMutateModes_updateStateToUnknown_throwsException() {
        ContextualMode manaulDnd =
                new ContextualMode.Builder("id")
                        .setType(TYPE_MANUAL_DO_NOT_DISTURB)
                        .setState(STATE_UNKNOWN)
                        .build();
        ContextualModesMutation mutation =
                new ContextualModesMutation.Builder().addUpdatedMode(manaulDnd).build();

        assertThrows(
                IllegalArgumentException.class,
                () -> mBinderService.mutateModes(mContext.getUser(), mutation));
    }

    @Test
    public void testMutateModes_duplicateId_throwsException() {
        ContextualModesMutation mutation =
                new ContextualModesMutation.Builder()
                        .addUpdatedMode(
                                new ContextualMode.Builder("id")
                                        .setType(TYPE_MANUAL_DO_NOT_DISTURB)
                                        .setState(STATE_ACTIVE)
                                        .build())
                        .addUpdatedMode(
                                new ContextualMode.Builder("id")
                                        .setType(TYPE_MANUAL_DO_NOT_DISTURB)
                                        .setState(STATE_ACTIVE)
                                        .build())
                        .build();

        assertThrows(
                IllegalArgumentException.class,
                () -> mBinderService.mutateModes(mContext.getUser(), mutation));
    }

    @Test
    public void testMutateModes_userHasNoZenMode_throwsException() {
        // Setup a user with no modes.
        UserHandle user = setUpUser(1);

        // Updating DND will fail.
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mBinderService.mutateModes(
                                user,
                                new ContextualModesMutation.Builder()
                                        .addUpdatedMode(
                                                new ContextualMode.Builder("id")
                                                        .setType(TYPE_MANUAL_DO_NOT_DISTURB)
                                                        .setState(STATE_ACTIVE)
                                                        .build())
                                        .build()));
    }

    @EnableFlags(com.android.crossdevicesync.flags.Flags.FLAG_START_SYNC_SERVICE_ON_BOOT)
    @Test
    public void testRegisterModeSyncListener_forSelf_requiresNoPermission() throws Exception {
        denyAllPermissions();

        mBinderService.registerModeSyncListener(UserHandle.SYSTEM, new TestModeSyncListener());
    }

    @EnableFlags(com.android.crossdevicesync.flags.Flags.FLAG_START_SYNC_SERVICE_ON_BOOT)
    @Test
    public void testRegisterModeSyncListener_forDifferentUser_requiresMultiUserPermission()
            throws Exception {
        UserHandle user = setUpUser(1);

        mBinderService.registerModeSyncListener(user, new TestModeSyncListener());

        denyPermission(permission.INTERACT_ACROSS_USERS);

        assertThrows(
                SecurityException.class,
                () -> mBinderService.registerModeSyncListener(user, new TestModeSyncListener()));
    }

    @Test
    public void testUnregisterModeSyncListener_requiresNoPermission() throws Exception {
        denyAllPermissions();

        mBinderService.unregisterModeSyncListener(new TestModeSyncListener());
    }

    @EnableFlags(com.android.crossdevicesync.flags.Flags.FLAG_START_SYNC_SERVICE_ON_BOOT)
    @Test
    public void testModeSyncEnabledListener() throws Exception {
        TestModeSyncListener listener = new TestModeSyncListener();

        mBinderService.registerModeSyncListener(mContext.getUser(), listener);
        assertThat(listener.mEnabled).isNull();

        mBinderService.setModeSyncEnabled(mContext.getUser(), true);
        assertThat(listener.mEnabled).isTrue();

        mBinderService.setModeSyncEnabled(mContext.getUser(), false);
        assertThat(listener.mEnabled).isFalse();

        mBinderService.unregisterModeSyncListener(listener);
        mBinderService.setModeSyncEnabled(mContext.getUser(), true);
        assertThat(listener.mEnabled).isFalse();
    }

    @DisableFlags(com.android.crossdevicesync.flags.Flags.FLAG_START_SYNC_SERVICE_ON_BOOT)
    @Test
    public void testModeSyncEnabledListener_flagDisabled() throws Exception {
        TestModeSyncListener listener = new TestModeSyncListener();

        mBinderService.registerModeSyncListener(mContext.getUser(), listener);
        assertThat(listener.mEnabled).isNull();

        mBinderService.setModeSyncEnabled(mContext.getUser(), true);
        assertThat(listener.mEnabled).isNull();

        mBinderService.setModeSyncEnabled(mContext.getUser(), false);
        assertThat(listener.mEnabled).isNull();
    }

    @EnableFlags(com.android.crossdevicesync.flags.Flags.FLAG_START_SYNC_SERVICE_ON_BOOT)
    @Test
    public void testModeSyncEnabledListener_illegalUser() {
        TestModeSyncListener listener = new TestModeSyncListener();

        assertThrows(
                IllegalArgumentException.class,
                () -> mBinderService.registerModeSyncListener(UserHandle.of(1), listener));
        assertThrows(
                IllegalArgumentException.class,
                () -> mBinderService.registerModeSyncListener(UserHandle.ALL, listener));
    }

    @Test
    public void testRegisterModeListener_requiresModesPermission() throws Exception {
        TestModeListener listener = new TestModeListener();
        // INTERACT_ACROSS_USERS permission should not be required.
        denyPermission(permission.INTERACT_ACROSS_USERS);

        mBinderService.registerModeListener(mContext.getUser(), listener);

        denyPermission(permission.MANAGE_CONTEXTUAL_MODES);

        assertThrows(
                SecurityException.class,
                () -> mBinderService.registerModeListener(mContext.getUser(), listener));
    }

    @Test
    public void testRegisterModeListener_forDifferentUser_requiresMultiUserPermission()
            throws Exception {
        UserHandle user = setUpUser(1);
        TestModeListener listener = new TestModeListener();

        mBinderService.registerModeListener(user, listener);

        denyPermission(permission.INTERACT_ACROSS_USERS);

        assertThrows(
                SecurityException.class, () -> mBinderService.registerModeListener(user, listener));
    }

    @Test
    public void testUnregisterModeListener_requiresPermission() throws Exception {
        TestModeListener listener = new TestModeListener();

        mBinderService.unregisterModeListener(listener);

        denyPermission(permission.MANAGE_CONTEXTUAL_MODES);

        assertThrows(
                SecurityException.class, () -> mBinderService.unregisterModeListener(listener));
    }

    @Test
    public void testModeListener_modeChanged() throws Exception {
        UserHandle user1 = setUpUser(1);
        UserHandle user2 = setUpUser(2);
        TestModeListener l1 = new TestModeListener();
        TestModeListener l2 = new TestModeListener();

        mBinderService.registerModeListener(user1, l1);
        mBinderService.registerModeListener(user2, l2);
        assertThat(l1.mChangeCallbackCount).isEqualTo(0);
        assertThat(l1.mRemoveCallbackCount).isEqualTo(0);
        assertThat(l2.mChangeCallbackCount).isEqualTo(0);
        assertThat(l2.mRemoveCallbackCount).isEqualTo(0);

        // User 1 enables DND.
        mockForUser(user1).setManualZenRuleActive(/* isActive= */ true).commit();
        assertThat(l1.mChangeCallbackCount).isEqualTo(1);
        assertThat(l2.mChangeCallbackCount).isEqualTo(0);
        verifyHasMode(l1.mModes, TYPE_MANUAL_DO_NOT_DISTURB, STATE_ACTIVE);

        // User 2 enables DND.
        mockForUser(user2).setManualZenRuleActive(/* isActive= */ true).commit();
        assertThat(l1.mChangeCallbackCount).isEqualTo(1);
        assertThat(l2.mChangeCallbackCount).isEqualTo(1);
        verifyHasMode(l2.mModes, TYPE_MANUAL_DO_NOT_DISTURB, STATE_ACTIVE);
    }

    @Test
    public void testModeListener_modeAddedThenChanged() throws Exception {
        UserHandle user1 = setUpUser(1);
        UserHandle user2 = setUpUser(2);
        TestModeListener l1 = new TestModeListener();
        TestModeListener l2 = new TestModeListener();
        ModesMocker mocker1 = mockForUser(user1);
        ModesMocker mocker2 = mockForUser(user2);
        mBinderService.registerModeListener(user1, l1);
        mBinderService.registerModeListener(user2, l2);

        // User 1 adds a BTM.
        mocker1.setAutomaticZenRuleActive("id", TYPE_BEDTIME, /* isActive= */ false).commit();

        assertThat(l1.mChangeCallbackCount).isEqualTo(1);
        assertThat(l2.mChangeCallbackCount).isEqualTo(0);
        verifyHasMode(l1.mModes, TYPE_BEDTIME, STATE_INACTIVE);

        // User 1 adds Driving mode.
        mocker1.setAutomaticZenRuleActive("id", TYPE_DRIVING, /* isActive= */ true).commit();

        assertThat(l1.mChangeCallbackCount).isEqualTo(2);
        assertThat(l2.mChangeCallbackCount).isEqualTo(0);
        verifyHasMode(l1.mModes, TYPE_DRIVING, STATE_ACTIVE);

        // User 2 adds BTM.
        mocker2.setAutomaticZenRuleActive("id", TYPE_BEDTIME, /* isActive= */ true).commit();

        assertThat(l1.mChangeCallbackCount).isEqualTo(2);
        assertThat(l2.mChangeCallbackCount).isEqualTo(1);
        verifyHasMode(l2.mModes, TYPE_BEDTIME, STATE_ACTIVE);

        // User 2 disables BTM.
        mocker2.setAutomaticZenRuleActive("id", TYPE_BEDTIME, /* isActive= */ false).commit();

        assertThat(l1.mChangeCallbackCount).isEqualTo(2);
        assertThat(l2.mChangeCallbackCount).isEqualTo(2);
        verifyHasMode(l2.mModes, TYPE_BEDTIME, STATE_INACTIVE);
    }

    @Test
    public void testModeListener_batchUpdate() throws Exception {
        TestModeListener l = new TestModeListener();
        mBinderService.registerModeListener(mContext.getUser(), l);

        mockForUser(mContext.getUser())
                .setManualZenRuleActive(/* isActive= */ true)
                .setAutomaticZenRuleActive("btm", TYPE_BEDTIME, /* isActive= */ false)
                .setAutomaticZenRuleActive("driving", TYPE_DRIVING, /* isActive= */ true)
                .commit();

        assertThat(l.mChangeCallbackCount).isEqualTo(1);
        assertThat(l.mModes).hasSize(3);
        verifyHasMode(l.mModes, TYPE_MANUAL_DO_NOT_DISTURB, STATE_ACTIVE);
        verifyHasMode(l.mModes, TYPE_BEDTIME, STATE_INACTIVE);
        verifyHasMode(l.mModes, TYPE_DRIVING, STATE_ACTIVE);
    }

    @Test
    public void testModeListener_modeRemoved() throws Exception {
        TestModeListener l = new TestModeListener();
        mBinderService.registerModeListener(mContext.getUser(), l);
        ModesMocker mocker =
                mockForUser(mContext.getUser())
                        .setManualZenRuleActive(/* isActive= */ true)
                        .setAutomaticZenRuleActive("btm", TYPE_BEDTIME, /* isActive= */ false)
                        .setAutomaticZenRuleActive("driving", TYPE_DRIVING, /* isActive= */ true)
                        .commit();
        assertThat(l.mRemoveCallbackCount).isEqualTo(0);

        mocker.removeAutomaticZenMode("btm").commit();
        assertThat(l.mRemoveCallbackCount).isEqualTo(1);
        assertThat(l.mRemovedModes).contains("btm");

        mocker.removeAutomaticZenMode("driving").commit();
        assertThat(l.mRemoveCallbackCount).isEqualTo(2);
        assertThat(l.mRemovedModes).contains("driving");
    }

    @Test
    public void testModeListener_zenModeConfigDeleted() throws Exception {
        String dndId = mBinderService.getModes(mContext.getUser()).get(0).getId();
        TestModeListener l = new TestModeListener();
        mBinderService.registerModeListener(mContext.getUser(), l);
        assertThat(l.mRemoveCallbackCount).isEqualTo(0);

        // Simulate config removal.
        when(mNotificationManagerInternal.hasZenModeConfig(mContext.getUser())).thenReturn(false);
        triggerZenConfigChanged(mContext.getUser());

        assertThat(l.mRemoveCallbackCount).isEqualTo(1);
        assertThat(l.mRemovedModes).contains(dndId);
    }

    @Test
    public void testModeListener_illegalUser() {
        TestModeListener l = new TestModeListener();

        assertThrows(
                IllegalArgumentException.class,
                () -> mBinderService.registerModeListener(UserHandle.of(1), l));
        assertThrows(
                IllegalArgumentException.class,
                () -> mBinderService.registerModeListener(UserHandle.ALL, l));
    }

    @Test
    public void testModeListener_currentUser() throws Exception {
        TestModeListener l = new TestModeListener();
        mBinderService.registerModeListener(UserHandle.CURRENT, l);
        assertThat(l.mChangeCallbackCount).isEqualTo(0);
        assertThat(l.mRemoveCallbackCount).isEqualTo(0);

        mockForUser(mContext.getUser()).setManualZenRuleActive(/* isActive= */ true).commit();

        assertThat(l.mChangeCallbackCount).isEqualTo(1);
        verifyHasMode(l.mModes, TYPE_MANUAL_DO_NOT_DISTURB, STATE_ACTIVE);
    }

    @Test
    public void testUnregisterModeListener_noNewCallbacks() throws Exception {
        TestModeListener l = new TestModeListener();
        mBinderService.registerModeListener(UserHandle.CURRENT, l);
        mBinderService.unregisterModeListener(l);

        mockForUser(mContext.getUser()).setManualZenRuleActive(/* isActive= */ true).commit();

        assertThat(l.mChangeCallbackCount).isEqualTo(0);
    }

    @Test
    public void testRegisterModeListenerAndGetMode_listenerNotReceivingCallback() throws Exception {
        // Starts with DND ON.
        mockForUser(mContext.getUser()).setManualZenRuleActive(/* isActive= */ true).commit();
        TestModeListener listener = new TestModeListener();
        mBinderService.registerModeListener(mContext.getUser(), listener);

        // Get modes.
        assertThat(mBinderService.getModes(mContext.getUser())).hasSize(1);

        // Verify that listener is not triggered.
        assertThat(listener.mChangeCallbackCount).isEqualTo(0);
        assertThat(listener.mRemoveCallbackCount).isEqualTo(0);
    }

    @Test
    public void testGetModeAndRegisterModeListener_listenerNotReceivingCallback() throws Exception {
        // Starts with DND ON.
        mockForUser(mContext.getUser()).setManualZenRuleActive(/* isActive= */ true).commit();
        // Trigger a getter call.
        assertThat(mBinderService.getModes(mContext.getUser())).hasSize(1);

        // Register listener.
        TestModeListener listener = new TestModeListener();
        mBinderService.registerModeListener(mContext.getUser(), listener);

        // Verify that listener is not triggered.
        assertThat(listener.mChangeCallbackCount).isEqualTo(0);
        assertThat(listener.mRemoveCallbackCount).isEqualTo(0);
    }

    @Test
    public void testRegisterModeListener_listenerNotReceivingCallback() throws Exception {
        // Starts with DND ON.
        mockForUser(mContext.getUser()).setManualZenRuleActive(/* isActive= */ true).commit();

        // Register listener.
        TestModeListener listener = new TestModeListener();
        mBinderService.registerModeListener(mContext.getUser(), listener);

        // Verify that listener is not triggered.
        assertThat(listener.mChangeCallbackCount).isEqualTo(0);
        assertThat(listener.mRemoveCallbackCount).isEqualTo(0);
    }

    private void startNewService() {
        mService =
                new ContextualModeManagerService(
                        mContext, mPermissionEnforcer, mResources, mContentResolver, Runnable::run);
        mBinderService = mService.getBinderService();
        mService.onStart();
    }

    private void denyPermission(String permission) {
        doThrow(SecurityException.class)
                .when(mPermissionEnforcer)
                .enforcePermission(eq(permission), anyInt(), anyInt());
    }

    private void denyAllPermissions() {
        doThrow(SecurityException.class)
                .when(mPermissionEnforcer)
                .enforcePermission(anyString(), anyInt(), anyInt());
    }

    private UserHandle setUpUser(int userId) {
        UserHandle userHandle = UserHandle.of(userId);
        when(mUserManagerInternal.exists(userId)).thenReturn(true);
        return userHandle;
    }

    private void verifyHasMode(List<ContextualMode> modes, int type, int state) {
        Set<String> ids = new HashSet<>();
        for (ContextualMode mode : modes) {
            assertThat(mode.getId()).isNotNull();
            assertThat(ids.add(mode.getId())).isTrue();
            if (mode.getType() == type && mode.getState() == state) {
                return;
            }
        }
        fail();
    }

    private ModesMocker mockForUser(UserHandle user) {
        return new ModesMocker(user);
    }

    private void triggerZenConfigChanged(UserHandle user) {
        ArgumentCaptor<ZenModeHelper.Callback> captor =
                ArgumentCaptor.forClass(ZenModeHelper.Callback.class);
        try {
            verify(mNotificationManagerInternal, atLeastOnce())
                    .addZenModeCallback(captor.capture());
            captor.getValue().onConfigChanged(user);
        } catch (Throwable t) {
            // Do nothing.
        }
    }

    private class ModesMocker {
        private final UserHandle mUser;
        private final Map<String, Pair<AutomaticZenRule, Boolean>> mAutomaticZenRuleStates =
                new HashMap<>();
        private boolean mManualZenRuleActive;

        ModesMocker(UserHandle user) {
            mUser = user;
        }

        ModesMocker setManualZenRuleActive(boolean isActive) {
            mManualZenRuleActive = isActive;
            return this;
        }

        ModesMocker setAutomaticZenRuleActive(String id, int type, boolean isActive) {
            AutomaticZenRule rule =
                    new AutomaticZenRule(
                            "name",
                            /* owner= */ null,
                            /* configurationActivity= */ null,
                            /* conditionId= */ Uri.EMPTY,
                            /* policy= */ null,
                            /* interruptionFilter= */ NotificationManager
                                    .INTERRUPTION_FILTER_PRIORITY,
                            /* enabled= */ true);
            rule.setType(type);
            return setAutomaticZenRuleActive(id, rule, isActive);
        }

        ModesMocker setAutomaticZenRuleActive(String id, AutomaticZenRule rule, boolean isActive) {
            Pair<AutomaticZenRule, Boolean> pair = Pair.create(rule, isActive);
            mAutomaticZenRuleStates.put(id, pair);
            return this;
        }

        ModesMocker removeAutomaticZenMode(String id) {
            mAutomaticZenRuleStates.remove(id);
            return this;
        }

        ModesMocker commit() {
            when(mNotificationManagerInternal.hasZenModeConfig(mUser)).thenReturn(true);
            when(mNotificationManagerInternal.isManualZenRuleActive(mUser))
                    .thenReturn(mManualZenRuleActive);
            Map<String, AutomaticZenRule> ruleMap = new HashMap<>();
            for (var entry : mAutomaticZenRuleStates.entrySet()) {
                String id = entry.getKey();
                AutomaticZenRule rule = entry.getValue().first;
                boolean isActive = entry.getValue().second;
                ruleMap.put(id, rule);
                when(mNotificationManagerInternal.isAutomaticZenRuleActive(mUser, id))
                        .thenReturn(isActive);
            }
            when(mNotificationManagerInternal.getAutomaticZenRules(mUser)).thenReturn(ruleMap);
            when(mUserManagerInternal.exists(mUser.getIdentifier())).thenReturn(true);

            triggerZenConfigChanged(mUser);
            return this;
        }
    }

    private static class TestModeSyncListener extends IContextualModeSyncListener.Stub {
        @Nullable Boolean mEnabled;

        @Override
        public void onModeSyncEnabledChanged(boolean enabled) {
            mEnabled = enabled;
        }
    }

    private static class TestModeListener extends IContextualModeListener.Stub {
        final Set<String> mRemovedModes = new HashSet<>();
        @Nullable List<ContextualMode> mModes;
        int mChangeCallbackCount;
        int mRemoveCallbackCount;

        @Override
        public void onModesChanged(List<ContextualMode> modes) {
            mModes = modes;
            mChangeCallbackCount++;
        }

        @Override
        public void onModeRemoved(String modeId) {
            mRemovedModes.add(modeId);
            mRemoveCallbackCount++;
        }
    }
}
