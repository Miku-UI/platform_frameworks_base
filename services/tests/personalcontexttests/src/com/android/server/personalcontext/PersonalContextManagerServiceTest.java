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

package com.android.server.personalcontext;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import android.Manifest;
import android.app.role.RoleManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManagerInternal;
import android.content.pm.UserInfo;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.os.Process;
import android.os.UserHandle;
import android.os.test.FakePermissionEnforcer;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.provider.Settings;
import android.service.personalcontext.Flags;
import android.service.personalcontext.PersonalContextManager;
import android.service.personalcontext.embedded.InsightSurfaceClientInfo;
import android.service.personalcontext.hint.BundleHint;
import android.service.personalcontext.hint.ContextHint;
import android.service.personalcontext.hint.ContextHintWrapper;
import android.service.personalcontext.insight.BundleInsight;
import android.service.personalcontext.insight.ContextInsightWrapper;
import android.testing.TestableContext;
import android.util.ArrayMap;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.internal.util.test.LocalServiceKeeperRule;
import com.android.server.SystemService;
import com.android.server.contentcapture.ContentCaptureManagerInternal;
import com.android.server.personalcontext.embedded.EmbeddedInsightRenderer;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class PersonalContextManagerServiceTest {
    private static final int USER_ID_1 = 10;
    private static final int USER_ID_2 = 11;

    private static final String TEST_PACKAGE_NAME = "test.package";
    private static final ParcelUuid TEST_COMPONENT_UUID = new ParcelUuid(UUID.randomUUID());

    private static final UserInfo USER_INFO_1 = new UserInfo(USER_ID_1, "user1", 0);
    private static final UserInfo USER_INFO_2 = new UserInfo(USER_ID_2, "user2", 0);
    private static final UserInfo SYSTEM_USER_INFO =
            new UserInfo(UserHandle.USER_SYSTEM, "system", 0);

    @Rule public LocalServiceKeeperRule mLocalServiceKeeperRule = new LocalServiceKeeperRule();
    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Rule
    public final PersonalContextTestableContext mContext =
            new PersonalContextTestableContext(getInstrumentation().getContext());

    @Mock private PackageManager mPackageManager;
    @Mock private ContentResolver mContentResolver;
    @Mock private PackageManagerInternal mPackageManagerInternal;
    @Mock private ContentCaptureManagerInternal mContentCaptureManagerInternal;
    @Mock private RoleManager mRoleManager;
    @Mock private OperatingModeProvider mOperatingModeProvider;
    @Mock private AccessController mAccessController;
    @Mock private EmbeddedInsightRenderer mEmbeddedInsightRenderer;

    @Mock private PackageMonitorProxy mPackageMonitorProxy;

    private FakePermissionEnforcer mFakePermissionEnforcer;

    private PersonalContextManagerService mService;
    private PersonalContextManagerService.BinderService mBinderService;
    private PersonalContextManagerInternal mLocalService;
    private SystemService.TargetUser mUser1;
    private SystemService.TargetUser mUser2;
    private SystemService.TargetUser mSystemUser;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mLocalServiceKeeperRule.overrideLocalService(
                PackageManagerInternal.class, mPackageManagerInternal);
        mLocalServiceKeeperRule.overrideLocalService(
                ContentCaptureManagerInternal.class, mContentCaptureManagerInternal);

        mContext.setMockPackageManager(mPackageManager);
        mContext.addMockSystemService(RoleManager.class, mRoleManager);

        mContext.addMockUserContext(UserHandle.of(USER_ID_1), mPackageManager);
        mContext.addMockUserContext(UserHandle.of(USER_ID_2), mPackageManager);
        mContext.addMockUserContext(UserHandle.SYSTEM, mPackageManager);

        // Set default per-app setting to enabled.
        Settings.Secure.putIntForUser(
                mContext.getContentResolver(),
                Settings.Secure.PERSONAL_CONTEXT_MODE_ENABLED_DEFAULT,
                1,
                USER_ID_1);

        // By Default, allow all behavior through
        when(mAccessController.isPackageAllowed(any(), anyInt())).thenReturn(true);
        when(mAccessController.isServiceAllowed(any(), anyInt())).thenReturn(true);
        when(mAccessController.isAnyPackageForUidAllowed(anyInt(), anyInt())).thenReturn(true);

        mContext.getTestablePermissions()
                .setPermission(Manifest.permission.INTERACT_ACROSS_USERS, PERMISSION_GRANTED);
        mFakePermissionEnforcer = new FakePermissionEnforcer();
        mFakePermissionEnforcer.grant(Manifest.permission.INTERACT_ACROSS_USERS);
        mFakePermissionEnforcer.grant(Manifest.permission.INTERACT_ACROSS_USERS_FULL);
        mFakePermissionEnforcer.grant(Manifest.permission.CHANGE_PERSONAL_CONTEXT_MODE);
        mFakePermissionEnforcer.grant(Manifest.permission.PERSONAL_CONTEXT_HOST_INSIGHT_SURFACE);
        mFakePermissionEnforcer.grant(Manifest.permission.PERSONAL_CONTEXT_PUBLISH_HINTS);
        mFakePermissionEnforcer.grant(Manifest.permission.PERSONAL_CONTEXT_READ_SETTINGS);
        mFakePermissionEnforcer.grant(Manifest.permission.PERSONAL_CONTEXT_WRITE_SETTINGS);
        mFakePermissionEnforcer.grant(Manifest.permission.PERSONAL_CONTEXT_PUBLISH_INSIGHTS);
        mContext.addMockSystemService(Context.PERMISSION_ENFORCER_SERVICE, mFakePermissionEnforcer);

        mService =
                spy(
                        new PersonalContextManagerService(
                                mContext,
                                (userContext,
                                        mOperatingModeProvider,
                                        mAccessController,
                                        executor) -> mEmbeddedInsightRenderer,
                                (context, eventListener, user) -> mAccessController,
                                mPackageMonitorProxy));

        mLocalService = mService.new LocalService();

        mBinderService =
                new PersonalContextManagerService.BinderService(mService, mPackageManagerInternal);

        mUser1 = new SystemService.TargetUser(USER_INFO_1);
        mUser2 = new SystemService.TargetUser(USER_INFO_2);
        mSystemUser = new SystemService.TargetUser(SYSTEM_USER_INFO);

        mBinderService.setEnabled(USER_ID_1, true);
        mBinderService.setEnabled(USER_ID_2, true);
    }

    @Test
    public void testOnUserStarting_createsComponentManager() {
        mService.onUserStarting(mUser1);

        assertThat(mService.getComponentManagerForUser(USER_ID_1)).isNotNull();
    }

    @Test
    public void testOnUserStarting_registersSettingContentObserver() {
        mService.onUserStarting(mUser1);
        mService.onUserUnlocked(mUser1);

        verify(mContentResolver)
                .registerContentObserver(
                        eq(Settings.Secure.getUriFor(Settings.Secure.PERSONAL_CONTEXT_ENABLED)),
                        eq(false),
                        any(ContentObserver.class),
                        eq(UserHandle.USER_ALL));
    }

    @Test
    public void testOnUserUnlocked_registersComponentsAndMonitor() {
        mService.onUserStarting(mUser1);
        mService.onUserUnlocked(mUser1);

        ContextComponentManager user1Manager = mService.getComponentManagerForUser(USER_ID_1);
        assertThat(user1Manager).isNotNull();

        // Verify that it tried to register components.
        verify(mPackageManager, atLeast(3)).queryIntentServices(any(), anyInt());
    }

    @Test
    public void testOnUserUnlocked_systemUser_registersInternalComponents() {
        Settings.Secure.putIntForUser(
                mContext.getContentResolver(),
                Settings.Secure.PERSONAL_CONTEXT_ENABLED,
                1,
                UserHandle.USER_SYSTEM);

        mService.onUserStarting(mSystemUser);
        mService.onUserUnlocked(mSystemUser);

        ContextComponentManager systemManager =
                mService.getComponentManagerForUser(UserHandle.USER_SYSTEM);
        assertThat(systemManager).isNotNull();
        assertThat(systemManager.getRenderers()).isNotEmpty();
    }

    @Test
    public void testOnUserUnlocked_withoutStarting_isResilient() {
        // Should not crash, and should create the manager.
        mService.onUserUnlocked(mUser1);

        ContextComponentManager user1Manager = mService.getComponentManagerForUser(USER_ID_1);
        assertThat(user1Manager).isNotNull();
    }

    @Test
    public void testOnUserStopping_cleansUp() {
        mService.onUserStarting(mUser1);
        mService.onUserUnlocked(mUser1);

        assertThat(mService.getComponentManagerForUser(USER_ID_1)).isNotNull();

        mService.onUserStopping(mUser1);

        assertThat(mService.getComponentManagerForUser(USER_ID_1)).isNull();
        verify(mContentResolver).unregisterContentObserver(any(ContentObserver.class));
    }

    @Test
    public void testMultipleUserLifecycle() {
        // Start and unlock user 1
        mService.onUserStarting(mUser1);
        mService.onUserUnlocked(mUser1);

        assertThat(mService.getComponentManagerForUser(USER_ID_1)).isNotNull();
        assertThat(mService.getComponentManagerForUser(USER_ID_2)).isNull();

        // Start and unlock user 2
        mService.onUserStarting(mUser2);
        mService.onUserUnlocked(mUser2);

        assertThat(mService.getComponentManagerForUser(USER_ID_1)).isNotNull();
        assertThat(mService.getComponentManagerForUser(USER_ID_2)).isNotNull();

        // Stop user 1
        mService.onUserStopping(mUser1);

        assertThat(mService.getComponentManagerForUser(USER_ID_1)).isNull();
        assertThat(mService.getComponentManagerForUser(USER_ID_2)).isNotNull();

        // Stop user 2
        mService.onUserStopping(mUser2);

        assertThat(mService.getComponentManagerForUser(USER_ID_1)).isNull();
        assertThat(mService.getComponentManagerForUser(USER_ID_2)).isNull();
    }

    @Test
    public void testNotEnabled_doesNotRegisterClients() {
        mService.onUserStarting(mUser1);
        mService.onUserUnlocked(mUser1);
        mBinderService.setEnabled(USER_ID_1, false);

        final InsightSurfaceClientInfo clientInfo = mock(InsightSurfaceClientInfo.class);
        mBinderService.registerInsightSurfaceClient(clientInfo, USER_ID_1);

        verify(mEmbeddedInsightRenderer, never()).registerInsightSurfaceClient(clientInfo);
    }

    @Test
    public void testBecomesEnabled_allowsRegisteringClients() {
        mService.onUserStarting(mUser1);
        mService.onUserUnlocked(mUser1);
        mBinderService.setEnabled(USER_ID_1, false);

        final InsightSurfaceClientInfo clientInfo = mock(InsightSurfaceClientInfo.class);
        mBinderService.registerInsightSurfaceClient(clientInfo, USER_ID_1);
        verify(mEmbeddedInsightRenderer, never()).registerInsightSurfaceClient(clientInfo);

        mBinderService.setEnabled(USER_ID_1, true);
        mBinderService.registerInsightSurfaceClient(clientInfo, USER_ID_1);
        verify(mEmbeddedInsightRenderer).registerInsightSurfaceClient(clientInfo);

    }

    @Test
    public void testSetDisabled_unregistersComponents() {
        mService.onUserStarting(mUser1);
        mService.onUserUnlocked(mUser1);
        final ContextComponentManager componentManager =
                mService.getComponentManagerForUser(USER_ID_1);
        assertThat(componentManager).isNotNull();
        assertThat(componentManager.getRenderers()).isNotEmpty();

        mBinderService.setEnabled(USER_ID_1, false);
        mService.handleIsEnabledSettingChanged(USER_ID_1);

        assertThat(componentManager.getRenderers()).isEmpty();
    }

    @Test
    public void testIsPersonalContextModeEnabled_modeUnset_returnsDefault() {
        when(mPackageManagerInternal.getPersonalContextMode(any(), anyInt(), anyInt()))
                .thenReturn(PackageManager.PERSONAL_CONTEXT_MODE_UNSET);

        // Set default to disabled.
        Settings.Secure.putIntForUser(
                mContext.getContentResolver(),
                Settings.Secure.PERSONAL_CONTEXT_MODE_ENABLED_DEFAULT,
                0,
                USER_ID_1);
        boolean result = mBinderService.isPersonalContextModeEnabled(TEST_PACKAGE_NAME, USER_ID_1);
        assertThat(result).isFalse();

        // Set default to enabled.
        Settings.Secure.putIntForUser(
                mContext.getContentResolver(),
                Settings.Secure.PERSONAL_CONTEXT_MODE_ENABLED_DEFAULT,
                1,
                USER_ID_1);

        result = mBinderService.isPersonalContextModeEnabled(TEST_PACKAGE_NAME, USER_ID_1);
        assertThat(result).isTrue();
    }

    @Test
    public void testIsPersonalContextModeEnabled_modeOn_returnsTrue() {
        when(mPackageManagerInternal.getPersonalContextMode(any(), anyInt(), anyInt()))
                .thenReturn(PackageManager.PERSONAL_CONTEXT_MODE_USER_ON);

        boolean result = mBinderService.isPersonalContextModeEnabled(TEST_PACKAGE_NAME, USER_ID_1);
        assertThat(result).isTrue();

        verify(mPackageManagerInternal)
                .getPersonalContextMode(TEST_PACKAGE_NAME, Process.myUid(), USER_ID_1);
    }

    @Test
    public void testIsPersonalContextModeEnabled_modeOff_returnsFalse() {
        when(mPackageManagerInternal.getPersonalContextMode(any(), anyInt(), anyInt()))
                .thenReturn(PackageManager.PERSONAL_CONTEXT_MODE_USER_OFF);

        boolean result = mBinderService.isPersonalContextModeEnabled(TEST_PACKAGE_NAME, USER_ID_1);
        assertThat(result).isFalse();

        verify(mPackageManagerInternal)
                .getPersonalContextMode(TEST_PACKAGE_NAME, Process.myUid(), USER_ID_1);
    }

    @Test
    public void testSetPersonalContextModeEnabled_enabled_sendsOn() {
        mBinderService.setPersonalContextModeEnabled(
                TEST_PACKAGE_NAME, USER_ID_1, /* enabled= */ true);

        verify(mPackageManagerInternal)
                .setPersonalContextMode(
                        TEST_PACKAGE_NAME,
                        Process.myUid(),
                        USER_ID_1,
                        PackageManager.PERSONAL_CONTEXT_MODE_USER_ON);
    }

    @Test
    public void testSetPersonalContextModeEnabled_disabled_sendsOff() {
        mBinderService.setPersonalContextModeEnabled(
                TEST_PACKAGE_NAME, USER_ID_1, /* enabled= */ false);

        verify(mPackageManagerInternal)
                .setPersonalContextMode(
                        TEST_PACKAGE_NAME,
                        Process.myUid(),
                        USER_ID_1,
                        PackageManager.PERSONAL_CONTEXT_MODE_USER_OFF);
    }

    @Test
    @EnableFlags(android.service.personalcontext.Flags.FLAG_ENFORCE_PERSONAL_CONTEXT_PERMISSIONS)
    public void testSetPersonalContextModeEnabled_permissionsDenied_throwsSecurityException() {
        mFakePermissionEnforcer.revoke(Manifest.permission.CHANGE_PERSONAL_CONTEXT_MODE);

        assertThrows(
                SecurityException.class,
                () ->
                        mBinderService.setPersonalContextModeEnabled(
                                TEST_PACKAGE_NAME, USER_ID_1, /* enabled= */ false));

        verifyNoInteractions(mPackageManagerInternal);
    }

    @Test
    @EnableFlags(Flags.FLAG_ENFORCE_PERSONAL_CONTEXT_PERMISSIONS)
    public void testPublishTriggeringHint_permissionDenied_throwsSecurityException() {
        mService.onUserStarting(mUser1);

        doThrow(new SecurityException("exception")).when(mAccessController).enforcePermissions(
                anyInt(), anyInt(), eq(AccessController.ACCESS_PUBLISH_HINTS_PERMISSION));

        BundleHint hint = new BundleHint.Builder().build();
        ContextHintWrapper hintWrapper = new ContextHintWrapper(hint);
        List<ContextHintWrapper> hints = List.of(hintWrapper);

        assertThrows(
                SecurityException.class,
                () -> mBinderService.publishTriggeringHint(hints, List.of(), List.of(), USER_ID_1));
    }

    // Re-enable or fix test when we fix up error handling
    @Ignore("b/504908793")
    @Test
    @EnableFlags(Flags.FLAG_ENFORCE_PERSONAL_CONTEXT_PERMISSIONS)
    public void testPublishTriggeringHint_deepPackageDisabled_throwsIllegalStateException() {
        mService.onUserStarting(mUser1);

        final String enabledPackageName = "com.android.cts.test";
        when(mPackageManagerInternal.getPersonalContextMode(
                        eq(enabledPackageName), anyInt(), anyInt()))
                .thenReturn(PackageManager.PERSONAL_CONTEXT_MODE_USER_ON);

        final String disabledPackageName = "com.android.cts.test2";
        when(mPackageManagerInternal.getPersonalContextMode(
                        eq(disabledPackageName), anyInt(), anyInt()))
                .thenReturn(PackageManager.PERSONAL_CONTEXT_MODE_USER_OFF);

        // Source package has per-app setting enabled, publishing hint succeeds.
        ContextHint enabledHint = mock(ContextHint.class);
        when(enabledHint.getSourcePackageName()).thenReturn(enabledPackageName);
        mBinderService.publishTriggeringHint(
                List.of(new ContextHintWrapper(enabledHint)), List.of(), List.of(), USER_ID_1);

        // Source package has per-app setting disabled, publishing hint throws an exception.
        ContextHint disabledHint = mock(ContextHint.class);
        when(disabledHint.getSourcePackageName()).thenReturn(disabledPackageName);
        assertThrows(
                IllegalStateException.class,
                () ->
                        mBinderService.publishTriggeringHint(
                                List.of(new ContextHintWrapper(disabledHint)),
                                List.of(),
                                List.of(),
                                USER_ID_1));
    }

    @Test
    @EnableFlags(Flags.FLAG_ENFORCE_PERSONAL_CONTEXT_ALLOWLIST_ACCESS_CONTROL)
    public void testPublishTriggeringHint_accessDenied_throwsSecurityException() {
        BundleHint hint = new BundleHint.Builder().build();
        ContextHintWrapper hintWrapper = new ContextHintWrapper(hint);
        List<ContextHintWrapper> hints = List.of(hintWrapper);

        when(mAccessController.isAnyPackageForUidAllowed(anyInt(), anyInt())).thenReturn(false);
        mService.onUserStarting(mUser1);

        assertThrows(
                SecurityException.class,
                () -> mBinderService.publishTriggeringHint(hints, List.of(), List.of(), USER_ID_1));
    }

    @Test
    @EnableFlags(Flags.FLAG_ENFORCE_PERSONAL_CONTEXT_PERMISSIONS)
    public void testRegisterInsightSurfaceClient_permissionsDenied_throwsSecurityException() {
        mService.onUserStarting(mUser1);
        doThrow(new SecurityException()).when(mAccessController).enforcePermissions(anyInt(),
                anyInt(), eq(AccessController.ACCESS_HOST_INSIGHT_SURFACE_PERMISSION));

        assertThrows(
                SecurityException.class,
                () ->
                        mBinderService.registerInsightSurfaceClient(
                                mock(InsightSurfaceClientInfo.class), USER_ID_1));
    }

    @Test
    @DisableFlags(Flags.FLAG_ENFORCE_PERSONAL_CONTEXT_PERMISSIONS)
    public void testRegisterInsightSurfaceClient_permissionFlagDisabled() {
        mService.onUserStarting(mUser1);
        mFakePermissionEnforcer.revoke(Manifest.permission.PERSONAL_CONTEXT_HOST_INSIGHT_SURFACE);

        assertDoesNotThrow(
                () ->
                        mBinderService.registerInsightSurfaceClient(
                                mock(InsightSurfaceClientInfo.class), USER_ID_1));
    }

    @Test
    @EnableFlags(Flags.FLAG_ENFORCE_PERSONAL_CONTEXT_ALLOWLIST_ACCESS_CONTROL)
    public void testRegisterInsightSurfaceClient_accessDenied_throwsSecurityException() {
        when(mAccessController.isAnyPackageForUidAllowed(anyInt(), anyInt())).thenReturn(false);
        mService.onUserStarting(mUser1);
        assertThrows(
                SecurityException.class,
                () ->
                        mBinderService.registerInsightSurfaceClient(
                                mock(InsightSurfaceClientInfo.class), USER_ID_1));
    }

    @Test
    public void testRegisterInsightSurfaceClient() {
        mService.onUserStarting(mUser1);
        assertDoesNotThrow(
                () ->
                        mBinderService.registerInsightSurfaceClient(
                                mock(InsightSurfaceClientInfo.class), USER_ID_1));
    }

    @Test
    @EnableFlags(Flags.FLAG_ENFORCE_PERSONAL_CONTEXT_ALLOWLIST_ACCESS_CONTROL)
    public void testPublishInsightSurfaceHints_accessDenied_throwsException() {
        when(mAccessController.isAnyPackageForUidAllowed(
                anyInt(), eq(AccessController.ACCESS_PUBLISH_HINTS_ALLOWLIST))).thenReturn(false);
        doThrow(new SecurityException()).when(mAccessController).enforcePermissions(anyInt(),
                anyInt(), eq(AccessController.ACCESS_PUBLISH_HINTS_ALLOWLIST
                        | AccessController.ACCESS_PUBLISH_HINTS_PERMISSION));
        BundleHint hint = new BundleHint.Builder().build();
        ContextHintWrapper hintWrapper = new ContextHintWrapper(hint);
        List<ContextHintWrapper> hints = List.of(hintWrapper);

        // Set the operating mode to default
        mService.setOperatingMode(USER_ID_1, PersonalContextManager.OPERATING_MODE_DEFAULT);

        mService.onUserStarting(mUser1);

        assertThrows(
                SecurityException.class,
                () -> mBinderService.publishInsightSurfaceHints(
                        hints, mock(InsightSurfaceClientInfo.class), USER_ID_1));
    }

    @Test
    public void testPublishInsightSurfaceHints() {
        mService.onUserStarting(mUser1);
        BundleHint hint = new BundleHint.Builder().build();
        ContextHintWrapper hintWrapper = new ContextHintWrapper(hint);
        List<ContextHintWrapper> hints = List.of(hintWrapper);

        mBinderService.publishInsightSurfaceHints(
                hints, mock(InsightSurfaceClientInfo.class), USER_ID_1);

        verify(mService)
                .publishInsightSurfaceHints(eq(USER_ID_1), anyInt(), eq(Set.of(hint)), any());
    }

    @Test
    @EnableFlags(Flags.FLAG_ENFORCE_PERSONAL_CONTEXT_PERMISSIONS)
    public void testPublishInsightSurfaceHints_permissionDenied_throwsSecurityException() {
        mService.onUserStarting(mUser1);
        doAnswer(invocation -> {
            final @AccessController.Access int flags = invocation.getArgument(2);

            if ((flags & AccessController.ACCESS_PUBLISH_HINTS_PERMISSION)
                    == AccessController.ACCESS_PUBLISH_HINTS_PERMISSION) {
                throw new SecurityException();
            }

            return null;
        }).when(mAccessController).enforcePermissions(anyInt(), anyInt(), anyInt());

        BundleHint hint = new BundleHint.Builder().build();
        ContextHintWrapper hintWrapper = new ContextHintWrapper(hint);
        List<ContextHintWrapper> hints = List.of(hintWrapper);

        assertThrows(
                SecurityException.class,
                () ->
                        mBinderService.publishInsightSurfaceHints(
                                hints, mock(InsightSurfaceClientInfo.class), USER_ID_1));
    }

    // TODO(b/495521356): remove test when permission is removed entirely.
    @Test
    public void testIsEnabled_doesNotEnforcePermission() {
        mBinderService.setEnabled(mContext.getUserId(), /* enabled= */ false);
        mFakePermissionEnforcer.revoke(Manifest.permission.PERSONAL_CONTEXT_READ_SETTINGS);

        // Read succeeds.
        assertThat(mBinderService.isEnabled(mContext.getUserId())).isFalse();
    }

    @Test
    @EnableFlags(Flags.FLAG_ENFORCE_PERSONAL_CONTEXT_PERMISSIONS)
    public void testSetEnabled_permissionsDenied_throwsSecurityException() {
        mBinderService.setEnabled(mContext.getUserId(), /* enabled= */ false);
        assertThat(mBinderService.isEnabled(mContext.getUserId())).isFalse();

        mFakePermissionEnforcer.revoke(Manifest.permission.PERSONAL_CONTEXT_WRITE_SETTINGS);

        assertThrows(
                SecurityException.class,
                () -> mBinderService.setEnabled(mContext.getUserId(), /* enabled= */ true));

        assertThat(mBinderService.isEnabled(mContext.getUserId())).isFalse();
    }

    @Test
    @DisableFlags(Flags.FLAG_ENFORCE_PERSONAL_CONTEXT_PERMISSIONS)
    public void testSetEnabled_permissionFlagDisabled() {
        mBinderService.setEnabled(mContext.getUserId(), /* enabled= */ false);
        assertThat(mBinderService.isEnabled(mContext.getUserId())).isFalse();

        mFakePermissionEnforcer.revoke(Manifest.permission.PERSONAL_CONTEXT_WRITE_SETTINGS);

        assertDoesNotThrow(
                () -> mBinderService.setEnabled(mContext.getUserId(), /* enabled= */ true));

        assertThat(mBinderService.isEnabled(mContext.getUserId())).isTrue();
    }

    @Test
    public void testSetEnabled_enablesSetting() {
        mBinderService.setEnabled(mContext.getUserId(), /* enabled= */ true);

        assertSecureSetting(mContext, Settings.Secure.PERSONAL_CONTEXT_ENABLED, 1);
    }

    @Test
    public void testSetEnabled_disablesSetting() {
        mBinderService.setEnabled(mContext.getUserId(), /* enabled= */ false);

        assertSecureSetting(mContext, Settings.Secure.PERSONAL_CONTEXT_ENABLED, 0);
    }

    @Test
    public void testSetPersonalContextModeEnabled_valueChanged_sendsBroadcast() {
        when(mPackageManagerInternal.setPersonalContextMode(any(), anyInt(), anyInt(), anyInt()))
                .thenReturn(true);

        final String contentCapturePackageName = "test.content.capture";
        when(mContentCaptureManagerInternal.getContentCaptureServicePackageNameForUser(anyInt()))
                .thenReturn(contentCapturePackageName);

        final String sysUiIntelligencePackageName1 = "test.sysui.intelligence1";
        final String sysUiIntelligencePackageName2 = "test.sysui.intelligence2";
        when(mRoleManager.getRoleHoldersAsUser(
                        eq(PersonalContextManagerService.ROLE_SYSTEM_UI_INTELLIGENCE), any()))
                .thenReturn(List.of(sysUiIntelligencePackageName1, sysUiIntelligencePackageName2));

        mBinderService.setPersonalContextModeEnabled(
                TEST_PACKAGE_NAME, USER_ID_1, /* enabled= */ true);

        // Broadcast is sent to app, content capture service, and sysui intelligence service.
        final List<String> expectedBroadcastReceivers =
                List.of(
                        TEST_PACKAGE_NAME,
                        contentCapturePackageName,
                        sysUiIntelligencePackageName1,
                        sysUiIntelligencePackageName2);
        assertPersonalContextModeChangeBroadcasts(expectedBroadcastReceivers);
    }

    @Test
    public void testSetPersonalContextModeEnabled_samePackage_dedupesBroadcast() {
        when(mPackageManagerInternal.setPersonalContextMode(any(), anyInt(), anyInt(), anyInt()))
                .thenReturn(true);

        // The configured content capture package and the system UI intelligence role are the same
        // package name.
        final String contentCapturePackageName = "test.content.capture";
        when(mContentCaptureManagerInternal.getContentCaptureServicePackageNameForUser(anyInt()))
                .thenReturn(contentCapturePackageName);
        when(mRoleManager.getRoleHoldersAsUser(
                        eq(PersonalContextManagerService.ROLE_SYSTEM_UI_INTELLIGENCE), any()))
                .thenReturn(List.of(contentCapturePackageName));

        mBinderService.setPersonalContextModeEnabled(
                TEST_PACKAGE_NAME, USER_ID_1, /* enabled= */ true);

        // Only one broadcast is sent to the content cpature package, not two.
        final List<String> expectedBroadcastReceivers =
                List.of(TEST_PACKAGE_NAME, contentCapturePackageName);

        assertPersonalContextModeChangeBroadcasts(expectedBroadcastReceivers);
    }

    @Test
    public void testSetPersonalContextModeEnabled_valueNotChanged_doesNotSendBroadcast() {
        // Returning false indicates the setting value did not change.
        when(mPackageManagerInternal.setPersonalContextMode(any(), anyInt(), anyInt(), anyInt()))
                .thenReturn(false);

        mBinderService.setPersonalContextModeEnabled(
                TEST_PACKAGE_NAME, USER_ID_1, /* enabled= */ true);

        assertThat(mContext.getBroadcastIntents()).isEmpty();
    }

    @Test
    public void testPublishTriggeringHint() {
        mService.onUserStarting(mUser1);
        BundleHint hint = new BundleHint.Builder().build();
        ContextHintWrapper hintWrapper = new ContextHintWrapper(hint);
        List<ContextHintWrapper> hints = List.of(hintWrapper);
        mBinderService.publishTriggeringHint(hints, List.of(), List.of(), USER_ID_1);

        verify(mService)
                .startRefinerWorkflow(eq(USER_ID_1), anyInt(), eq(Set.of(hint)), any(), any());
    }

    @Test
    public void testPublishTriggeringHint_nullRenderTokens() {
        mService.onUserStarting(mUser1);
        BundleHint hint = new BundleHint.Builder().build();
        ContextHintWrapper hintWrapper = new ContextHintWrapper(hint);
        List<ContextHintWrapper> hints = List.of(hintWrapper);
        mBinderService.publishTriggeringHint(hints, List.of(), List.of(), USER_ID_1);

        verify(mService)
                .startRefinerWorkflow(eq(USER_ID_1), anyInt(), eq(Set.of(hint)), any(), any());
    }

    @Test
    @EnableFlags(android.service.personalcontext.Flags.FLAG_ENFORCE_PERSONAL_CONTEXT_PERMISSIONS)
    public void testPublishInsight_permissionDenied_throwsSecurityException() {
        mService.onUserStarting(mUser1);
        doAnswer(invocation -> {
            @AccessController.Access int flags = invocation.getArgument(2);

            if ((flags & AccessController.ACCESS_PUBLISH_INSIGHTS_PERMISSION)
                    == AccessController.ACCESS_PUBLISH_INSIGHTS_PERMISSION) {
                throw new SecurityException();
            }
            return null;
        }).when(mAccessController).enforcePermissions(anyInt(), anyInt(), anyInt());

        BundleInsight insight = new BundleInsight.Builder().build();
        ContextInsightWrapper wrapper = new ContextInsightWrapper(insight);
        List<ContextInsightWrapper> insights = List.of(wrapper);

        assertThrows(
                SecurityException.class,
                () -> mBinderService.publishInsight(insights, TEST_COMPONENT_UUID, USER_ID_1));
    }

    @Test
    @EnableFlags(Flags.FLAG_ENFORCE_PERSONAL_CONTEXT_ALLOWLIST_ACCESS_CONTROL)
    public void testPublishInsight_accessDenied_throwsException() {
        BundleInsight insight = new BundleInsight.Builder().build();
        ContextInsightWrapper wrapper = new ContextInsightWrapper(insight);
        List<ContextInsightWrapper> insights = List.of(wrapper);

        when(mAccessController.isAnyPackageForUidAllowed(anyInt(), anyInt())).thenReturn(false);
        mService.onUserStarting(mUser1);

        assertThrows(
                Exception.class,
                () -> mBinderService.publishInsight(insights, TEST_COMPONENT_UUID, USER_ID_1));

        verify(mService, never())
                .startPublishedInsightWorkflow(
                        eq(USER_ID_1), eq(TEST_COMPONENT_UUID.getUuid()), eq(Set.of(insight)));
    }

    @Test
    public void testPublishInsight_accessGranted_succeeds() {
        BundleInsight insight = new BundleInsight.Builder().build();
        ContextInsightWrapper wrapper = new ContextInsightWrapper(insight);
        List<ContextInsightWrapper> insights = List.of(wrapper);

        mService.onUserStarting(mUser1);
        mBinderService.publishInsight(insights, TEST_COMPONENT_UUID, USER_ID_1);

        verify(mService)
                .startPublishedInsightWorkflow(
                        eq(USER_ID_1), eq(TEST_COMPONENT_UUID.getUuid()), eq(Set.of(insight)));
    }

    @Test
    public void testLocalService_publishTriggeringHint() {
        Set<ContextHint> hints = new HashSet<>();
        hints.add(new BundleHint.Builder().build());
        mLocalService.publishTriggeringHint(hints, null, USER_ID_1);

        verify(mService).startRefinerWorkflow(eq(USER_ID_1), anyInt(), eq(hints), any(), any());
    }

    @Test
    public void testLocalService_publishTriggeringHint_personalContextDisabled() {
        // Per-app personal context setting is disabled.
        when(mPackageManagerInternal.getPersonalContextMode(any(), anyInt(), anyInt()))
                .thenReturn(PackageManager.PERSONAL_CONTEXT_MODE_USER_OFF);

        Set<ContextHint> hints = new HashSet<>();
        hints.add(new BundleHint.Builder().build());
        mLocalService.publishTriggeringHint(hints, null, USER_ID_1);

        // startRefinerWorkflow runs but does not throw an exception.
        verify(mService).startRefinerWorkflow(eq(USER_ID_1), anyInt(), eq(hints), any(), any());
    }

    private static void assertSecureSetting(Context context, String key, int value) {
        assertWithMessage("%s should be %s", key, value)
                .that(
                        Settings.Secure.getIntForUser(
                                context.getContentResolver(), key, 1, context.getUserId()))
                .isEqualTo(value);
    }

    private static void assertDoesNotThrow(ThrowingRunnable runnable) {
        try {
            runnable.run();
        } catch (Throwable e) {
            fail("Should not have thrown " + e);
        }
    }

    private void assertPersonalContextModeChangeBroadcasts(
            List<String> expectedBroadcastReceivers) {
        final List<Intent> broadcasts = mContext.getBroadcastIntents();
        assertThat(broadcasts).hasSize(expectedBroadcastReceivers.size());

        final List<String> actualBroadcastReceivers = new ArrayList<>();

        for (Intent intent : broadcasts) {
            assertThat(intent.getAction()).isEqualTo(Intent.ACTION_PERSONAL_CONTEXT_MODE_CHANGED);
            // This is the package for which the setting changed.
            assertThat(intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME))
                    .isEqualTo(TEST_PACKAGE_NAME);

            // This is the package to which the intent was broadcast.
            assertThat(intent.getPackage()).isNotNull();
            actualBroadcastReceivers.add(intent.getPackage());
        }

        assertThat(actualBroadcastReceivers).containsExactlyElementsIn(expectedBroadcastReceivers);
    }

    private final class PersonalContextTestableContext extends TestableContext {
        private final ArrayMap<UserHandle, Context> mMockUserContexts = new ArrayMap<>();
        private final List<Intent> mBroadcastIntents = new ArrayList<>();

        PersonalContextTestableContext(Context base) {
            super(base);
        }

        private void addMockUserContext(UserHandle userHandle, PackageManager packageManager) {
            Context userContext = mock(Context.class);
            when(userContext.getPackageManager()).thenReturn(packageManager);
            when(userContext.getContentResolver()).thenReturn(mContentResolver);
            when(userContext.getUser()).thenReturn(userHandle);
            mMockUserContexts.put(userHandle, userContext);
        }

        @Override
        public Context createContextAsUser(UserHandle user, int flags) {
            return mMockUserContexts.get(user);
        }

        @Override
        public void sendBroadcastAsUser(
                Intent intent, UserHandle user, String receiverPermission, Bundle options) {
            mBroadcastIntents.add(intent);
        }

        public List<Intent> getBroadcastIntents() {
            return mBroadcastIntents;
        }
    }
}
