/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.server.contentcapture;

import static android.Manifest.permission.SET_CONTENT_PROTECTION_ALLOWLIST;
import static android.view.contentprotection.flags.Flags.FLAG_SET_CONTENT_PROTECTION_ALLOWLIST_ENABLED;
import static android.view.contentprotection.flags.Flags.FLAG_TRY_CALLBACK_ON_CALLING_USER_ENABLED;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertThrows;

import android.annotation.NonNull;
import android.annotation.SuppressLint;
import android.annotation.UserIdInt;
import android.content.ComponentName;
import android.content.ContentCaptureOptions;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ParceledListSlice;
import android.content.pm.UserInfo;
import android.os.IBinder;
import android.os.Process;
import android.os.UserHandle;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.service.contentcapture.ContentCaptureServiceInfo;
import android.view.contentcapture.ContentCaptureEvent;
import android.view.contentcapture.ContentCaptureManager;
import android.view.contentcapture.IContentCaptureOptionsCallback;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.server.LocalServices;
import com.android.server.contentprotection.ContentProtectionAllowlistManager;
import com.android.server.contentprotection.ContentProtectionConsentManager;
import com.android.server.contentprotection.RemoteContentProtectionService;
import com.android.server.pm.UserManagerInternal;

import com.google.common.collect.ImmutableList;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Test for {@link ContentCaptureManagerService}.
 *
 * <p>Run with: {@code atest
 * FrameworksServicesTests:com.android.server.contentcapture.ContentCaptureManagerServiceTest}
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
@SuppressWarnings("GuardedBy") // Service not really running, no need to expose locks
@SuppressLint({"VisibleForTests", "MissingPermission"}) // Unit test
public class ContentCaptureManagerServiceTest {

    private static final int USER_ID = 1234;

    private static final int SECOND_USER_ID = 9876;

    private static final String PACKAGE_NAME = "com.test.package.1";

    private static final String SECOND_PACKAGE_NAME = "com.test.package.2";

    private static final String THIRD_PACKAGE_NAME = "com.test.package.3";

    private static final ComponentName COMPONENT_NAME =
            new ComponentName(PACKAGE_NAME, "TestClass");

    private static final ContentCaptureEvent EVENT =
            new ContentCaptureEvent(/* sessionId= */ 100, /* type= */ 200);

    private static final ParceledListSlice<ContentCaptureEvent> PARCELED_EVENTS =
            new ParceledListSlice<>(ImmutableList.of(EVENT));

    private static final ContentCaptureOptions CONTENT_CAPTURE_OPTIONS =
            new ContentCaptureOptions(ContentCaptureManager.LOGGING_LEVEL_OFF);

    private static final Context sContext = ApplicationProvider.getApplicationContext();

    @Rule public final MockitoRule mMockitoRule = MockitoJUnit.rule();

    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Mock private UserManagerInternal mMockUserManagerInternal;

    @Mock private ContentProtectionAllowlistManager mMockContentProtectionAllowlistManager;

    @Mock private ContentCaptureServiceInfo mMockContentCaptureServiceInfo;

    @Mock private RemoteContentProtectionService mMockRemoteContentProtectionService;

    @Mock private ContentProtectionConsentManager mMockContentProtectionConsentManager;

    @Mock private IContentCaptureOptionsCallback mMockContentCaptureOptionsCallback;

    @Mock private IBinder mMockBinder;

    @Mock private Context mMockContext;

    @Mock private PackageManager mMockPackageManager;

    private boolean mDevCfgEnableContentProtectionReceiver;

    private List<List<String>> mDevCfgContentProtectionRequiredGroups = List.of(List.of("a"));

    private List<List<String>> mDevCfgContentProtectionOptionalGroups = Collections.emptyList();

    private int mContentProtectionAllowlistManagersCreated;

    private int mContentProtectionServiceInfosCreated;

    private int mRemoteContentProtectionServicesCreated;

    private int mContentProtectionConsentManagersCreated;

    private String mConfigDefaultContentProtectionService = COMPONENT_NAME.flattenToString();

    private boolean mContentProtectionServiceInfoConstructorShouldThrow;

    private ContentCaptureManagerService mContentCaptureManagerService;

    @UserIdInt private int mFakeCallingUserId = UserHandle.USER_SYSTEM;

    private int mGetCallingUserIdCallCount = 0;

    @Before
    public void setup() {
        when(mMockUserManagerInternal.getUserInfos()).thenReturn(new UserInfo[0]);
        LocalServices.removeServiceForTest(UserManagerInternal.class);
        LocalServices.addService(UserManagerInternal.class, mMockUserManagerInternal);
        mContentCaptureManagerService = new TestContentCaptureManagerService();
    }

    @Test
    public void constructor_contentProtection_disabled_noManagers() {
        assertThat(mContentProtectionAllowlistManagersCreated).isEqualTo(0);
        assertThat(mContentProtectionConsentManagersCreated).isEqualTo(0);
        assertThat(mContentProtectionServiceInfosCreated).isEqualTo(0);
        verifyNoMoreInteractions(mMockContentProtectionAllowlistManager);
        verifyNoMoreInteractions(mMockContentProtectionConsentManager);
    }

    @Test
    public void constructor_contentProtection_enabled_createsManagers() {
        mDevCfgEnableContentProtectionReceiver = true;

        mContentCaptureManagerService = new TestContentCaptureManagerService();

        assertThat(mContentProtectionAllowlistManagersCreated).isEqualTo(1);
        assertThat(mContentProtectionConsentManagersCreated).isEqualTo(1);
        assertThat(mContentProtectionServiceInfosCreated).isEqualTo(0);
        verify(mMockContentProtectionAllowlistManager).start(anyLong());
        verify(mMockContentProtectionAllowlistManager, never()).stop();
        verifyNoMoreInteractions(mMockContentProtectionConsentManager);
    }

    @Test
    public void setFineTuneParamsFromDeviceConfig_contentProtection_disabled_to_disabled() {
        mContentCaptureManagerService.setFineTuneParamsFromDeviceConfig();

        assertThat(mContentProtectionAllowlistManagersCreated).isEqualTo(0);
        assertThat(mContentProtectionConsentManagersCreated).isEqualTo(0);
        assertThat(mContentProtectionServiceInfosCreated).isEqualTo(0);
        verifyNoMoreInteractions(mMockContentProtectionAllowlistManager);
        verifyNoMoreInteractions(mMockContentProtectionConsentManager);
    }

    @Test
    public void setFineTuneParamsFromDeviceConfig_contentProtection_disabled_to_enabled() {
        mDevCfgEnableContentProtectionReceiver = true;

        mContentCaptureManagerService.setFineTuneParamsFromDeviceConfig();

        assertThat(mContentProtectionAllowlistManagersCreated).isEqualTo(1);
        assertThat(mContentProtectionConsentManagersCreated).isEqualTo(1);
        assertThat(mContentProtectionServiceInfosCreated).isEqualTo(0);
        verify(mMockContentProtectionAllowlistManager).start(anyLong());
        verify(mMockContentProtectionAllowlistManager, never()).stop();
        verifyNoMoreInteractions(mMockContentProtectionConsentManager);
    }

    @Test
    public void setFineTuneParamsFromDeviceConfig_contentProtection_enabled_to_enabled() {
        mDevCfgEnableContentProtectionReceiver = true;
        mContentCaptureManagerService.setFineTuneParamsFromDeviceConfig();

        mContentCaptureManagerService.setFineTuneParamsFromDeviceConfig();

        assertThat(mContentProtectionAllowlistManagersCreated).isEqualTo(1);
        assertThat(mContentProtectionConsentManagersCreated).isEqualTo(1);
        assertThat(mContentProtectionServiceInfosCreated).isEqualTo(0);
        verify(mMockContentProtectionAllowlistManager).start(anyLong());
        verify(mMockContentProtectionAllowlistManager, never()).stop();
        verifyNoMoreInteractions(mMockContentProtectionConsentManager);
    }

    @Test
    public void setFineTuneParamsFromDeviceConfig_contentProtection_enabled_to_disabled() {
        mDevCfgEnableContentProtectionReceiver = true;
        mContentCaptureManagerService.setFineTuneParamsFromDeviceConfig();
        mDevCfgEnableContentProtectionReceiver = false;

        mContentCaptureManagerService.setFineTuneParamsFromDeviceConfig();

        assertThat(mContentProtectionAllowlistManagersCreated).isEqualTo(1);
        assertThat(mContentProtectionConsentManagersCreated).isEqualTo(1);
        assertThat(mContentProtectionServiceInfosCreated).isEqualTo(0);
        verify(mMockContentProtectionAllowlistManager).start(anyLong());
        verify(mMockContentProtectionAllowlistManager).stop();
        verifyNoMoreInteractions(mMockContentProtectionConsentManager);
    }

    @Test
    public void setFineTuneParamsFromDeviceConfig_contentProtection_enabled_componentNameNull() {
        mDevCfgEnableContentProtectionReceiver = true;
        mConfigDefaultContentProtectionService = null;

        mContentCaptureManagerService.setFineTuneParamsFromDeviceConfig();

        assertThat(mContentProtectionAllowlistManagersCreated).isEqualTo(0);
        assertThat(mContentProtectionConsentManagersCreated).isEqualTo(0);
        assertThat(mContentProtectionServiceInfosCreated).isEqualTo(0);
        verifyNoMoreInteractions(mMockContentProtectionAllowlistManager);
        verifyNoMoreInteractions(mMockContentProtectionConsentManager);
    }

    @Test
    public void setFineTuneParamsFromDeviceConfig_contentProtection_enabled_componentNameBlank() {
        mDevCfgEnableContentProtectionReceiver = true;
        mConfigDefaultContentProtectionService = "   ";

        mContentCaptureManagerService.setFineTuneParamsFromDeviceConfig();

        assertThat(mContentProtectionAllowlistManagersCreated).isEqualTo(0);
        assertThat(mContentProtectionConsentManagersCreated).isEqualTo(0);
        assertThat(mContentProtectionServiceInfosCreated).isEqualTo(0);
        verifyNoMoreInteractions(mMockContentProtectionAllowlistManager);
        verifyNoMoreInteractions(mMockContentProtectionConsentManager);
    }

    @Test
    public void setFineTuneParamsFromDeviceConfig_contentProtection_disabled_componentNameNull() {
        mDevCfgEnableContentProtectionReceiver = true;
        mContentCaptureManagerService.setFineTuneParamsFromDeviceConfig();
        mDevCfgEnableContentProtectionReceiver = false;
        mConfigDefaultContentProtectionService = null;

        mContentCaptureManagerService.setFineTuneParamsFromDeviceConfig();

        assertThat(mContentProtectionAllowlistManagersCreated).isEqualTo(1);
        assertThat(mContentProtectionConsentManagersCreated).isEqualTo(1);
        assertThat(mContentProtectionServiceInfosCreated).isEqualTo(0);
        verify(mMockContentProtectionAllowlistManager).start(anyLong());
        verify(mMockContentProtectionAllowlistManager).stop();
        verifyNoMoreInteractions(mMockContentProtectionConsentManager);
    }

    @Test
    public void setFineTuneParamsFromDeviceConfig_contentProtection_disabled_componentNameBlank() {
        mDevCfgEnableContentProtectionReceiver = true;
        mContentCaptureManagerService.setFineTuneParamsFromDeviceConfig();
        mDevCfgEnableContentProtectionReceiver = false;
        mConfigDefaultContentProtectionService = "   ";

        mContentCaptureManagerService.setFineTuneParamsFromDeviceConfig();

        assertThat(mContentProtectionAllowlistManagersCreated).isEqualTo(1);
        assertThat(mContentProtectionConsentManagersCreated).isEqualTo(1);
        assertThat(mContentProtectionServiceInfosCreated).isEqualTo(0);
        verify(mMockContentProtectionAllowlistManager).start(anyLong());
        verify(mMockContentProtectionAllowlistManager).stop();
        verifyNoMoreInteractions(mMockContentProtectionConsentManager);
    }

    @Test
    public void getOptions_contentCaptureDisabled_contentProtectionDisabled() {
        ContentCaptureOptions actual =
                mContentCaptureManagerService.mGlobalContentCaptureOptions.getOptions(
                        USER_ID, PACKAGE_NAME);

        assertThat(actual).isNull();
        verify(mMockContentProtectionConsentManager, never()).isConsentGranted(anyInt());
        verify(mMockContentProtectionAllowlistManager, never()).isAllowed(anyString());
    }

    @Test
    public void getOptions_contentCaptureDisabled_contentProtectionEnabled() {
        when(mMockContentProtectionConsentManager.isConsentGranted(USER_ID)).thenReturn(true);
        when(mMockContentProtectionAllowlistManager.isAllowed(PACKAGE_NAME)).thenReturn(true);
        mDevCfgEnableContentProtectionReceiver = true;
        mContentCaptureManagerService = new TestContentCaptureManagerService();

        ContentCaptureOptions actual =
                mContentCaptureManagerService.mGlobalContentCaptureOptions.getOptions(
                        USER_ID, PACKAGE_NAME);

        assertThat(actual).isNotNull();
        assertThat(actual.enableReceiver).isFalse();
        assertThat(actual.contentProtectionOptions).isNotNull();
        assertThat(actual.contentProtectionOptions.enableReceiver).isTrue();
        assertThat(actual.whitelistedComponents).isNull();
    }

    @Test
    public void getOptions_contentCaptureEnabled_contentProtectionDisabled() {
        mContentCaptureManagerService.mGlobalContentCaptureOptions.setWhitelist(
                USER_ID, ImmutableList.of(PACKAGE_NAME), /* components= */ null);

        ContentCaptureOptions actual =
                mContentCaptureManagerService.mGlobalContentCaptureOptions.getOptions(
                        USER_ID, PACKAGE_NAME);

        assertThat(actual).isNotNull();
        assertThat(actual.enableReceiver).isTrue();
        assertThat(actual.contentProtectionOptions).isNotNull();
        assertThat(actual.contentProtectionOptions.enableReceiver).isFalse();
        assertThat(actual.whitelistedComponents).isNull();
        verify(mMockContentProtectionConsentManager, never()).isConsentGranted(anyInt());
        verify(mMockContentProtectionAllowlistManager, never()).isAllowed(anyString());
    }

    @Test
    public void getOptions_contentCaptureEnabled_contentProtectionEnabled() {
        when(mMockContentProtectionConsentManager.isConsentGranted(USER_ID)).thenReturn(true);
        when(mMockContentProtectionAllowlistManager.isAllowed(PACKAGE_NAME)).thenReturn(true);
        mDevCfgEnableContentProtectionReceiver = true;
        mContentCaptureManagerService = new TestContentCaptureManagerService();
        mContentCaptureManagerService.mGlobalContentCaptureOptions.setWhitelist(
                USER_ID, ImmutableList.of(PACKAGE_NAME), /* components= */ null);

        ContentCaptureOptions actual =
                mContentCaptureManagerService.mGlobalContentCaptureOptions.getOptions(
                        USER_ID, PACKAGE_NAME);

        assertThat(actual).isNotNull();
        assertThat(actual.enableReceiver).isTrue();
        assertThat(actual.contentProtectionOptions).isNotNull();
        assertThat(actual.contentProtectionOptions.enableReceiver).isTrue();
        assertThat(actual.whitelistedComponents).isNull();
    }

    @Test
    @DisableFlags(FLAG_SET_CONTENT_PROTECTION_ALLOWLIST_ENABLED)
    public void getOptions_liteModeForContentProtection_flagDisabled_samePackage() {
        mDevCfgEnableContentProtectionReceiver = true;
        when(mMockContentProtectionConsentManager.isConsentGranted(USER_ID)).thenReturn(true);
        mContentCaptureManagerService = new TestContentCaptureManagerService();

        ContentCaptureOptions actual =
                mContentCaptureManagerService.mGlobalContentCaptureOptions.getOptions(
                        USER_ID, COMPONENT_NAME.getPackageName());

        assertThat(actual).isNull();
        verify(mMockContentProtectionAllowlistManager).isAllowed(COMPONENT_NAME.getPackageName());
    }

    @Test
    @EnableFlags(FLAG_SET_CONTENT_PROTECTION_ALLOWLIST_ENABLED)
    public void getOptions_liteModeForContentProtection_flagEnabled_samePackage() {
        mDevCfgEnableContentProtectionReceiver = true;
        when(mMockContentProtectionConsentManager.isConsentGranted(USER_ID)).thenReturn(true);
        mContentCaptureManagerService = new TestContentCaptureManagerService();

        ContentCaptureOptions actual =
                mContentCaptureManagerService.mGlobalContentCaptureOptions.getOptions(
                        USER_ID, COMPONENT_NAME.getPackageName());

        assertThat(actual).isNotNull();
        assertThat(actual.enableReceiver).isFalse();
        assertThat(actual.contentProtectionOptions).isNotNull();
        assertThat(actual.contentProtectionOptions.enableReceiver).isFalse();
        assertThat(actual.whitelistedComponents).isNull();
        verify(mMockContentProtectionAllowlistManager).isAllowed(COMPONENT_NAME.getPackageName());
    }

    @Test
    @EnableFlags(FLAG_SET_CONTENT_PROTECTION_ALLOWLIST_ENABLED)
    public void getOptions_liteModeForContentProtection_flagEnabled_differentPackage() {
        mDevCfgEnableContentProtectionReceiver = true;
        when(mMockContentProtectionConsentManager.isConsentGranted(USER_ID)).thenReturn(true);
        mContentCaptureManagerService = new TestContentCaptureManagerService();

        ContentCaptureOptions actual =
                mContentCaptureManagerService.mGlobalContentCaptureOptions.getOptions(
                        USER_ID, SECOND_PACKAGE_NAME);

        assertThat(actual).isNull();
        verify(mMockContentProtectionAllowlistManager).isAllowed(SECOND_PACKAGE_NAME);
    }

    @Test
    public void isWhitelisted_packageName_contentCaptureDisabled_contentProtectionDisabled() {
        boolean actual =
                mContentCaptureManagerService.mGlobalContentCaptureOptions.isWhitelisted(
                        USER_ID, PACKAGE_NAME);

        assertThat(actual).isFalse();
        verify(mMockContentProtectionConsentManager, never()).isConsentGranted(anyInt());
        verify(mMockContentProtectionAllowlistManager, never()).isAllowed(anyString());
    }

    @Test
    public void isWhitelisted_packageName_contentCaptureDisabled_contentProtectionEnabled() {
        when(mMockContentProtectionConsentManager.isConsentGranted(USER_ID)).thenReturn(true);
        when(mMockContentProtectionAllowlistManager.isAllowed(PACKAGE_NAME)).thenReturn(true);
        mDevCfgEnableContentProtectionReceiver = true;
        mContentCaptureManagerService = new TestContentCaptureManagerService();

        boolean actual =
                mContentCaptureManagerService.mGlobalContentCaptureOptions.isWhitelisted(
                        USER_ID, PACKAGE_NAME);

        assertThat(actual).isTrue();
    }

    @Test
    public void isWhitelisted_packageName_contentCaptureEnabled_contentProtectionDisabled() {
        mContentCaptureManagerService.mGlobalContentCaptureOptions.setWhitelist(
                USER_ID, ImmutableList.of(PACKAGE_NAME), /* components= */ null);

        boolean actual =
                mContentCaptureManagerService.mGlobalContentCaptureOptions.isWhitelisted(
                        USER_ID, PACKAGE_NAME);

        assertThat(actual).isTrue();
        verify(mMockContentProtectionConsentManager, never()).isConsentGranted(anyInt());
        verify(mMockContentProtectionAllowlistManager, never()).isAllowed(anyString());
    }

    @Test
    public void isWhitelisted_packageName_contentCaptureEnabled_contentProtectionEnabled() {
        mDevCfgEnableContentProtectionReceiver = true;
        mContentCaptureManagerService = new TestContentCaptureManagerService();
        mContentCaptureManagerService.mGlobalContentCaptureOptions.setWhitelist(
                USER_ID, ImmutableList.of(PACKAGE_NAME), /* components= */ null);

        boolean actual =
                mContentCaptureManagerService.mGlobalContentCaptureOptions.isWhitelisted(
                        USER_ID, PACKAGE_NAME);

        assertThat(actual).isTrue();
        verify(mMockContentProtectionConsentManager, never()).isConsentGranted(anyInt());
        verify(mMockContentProtectionAllowlistManager, never()).isAllowed(anyString());
    }

    @Test
    public void isWhitelisted_componentName_contentCaptureDisabled_contentProtectionDisabled() {
        boolean actual =
                mContentCaptureManagerService.mGlobalContentCaptureOptions.isWhitelisted(
                        USER_ID, COMPONENT_NAME);

        assertThat(actual).isFalse();
        verify(mMockContentProtectionConsentManager, never()).isConsentGranted(anyInt());
        verify(mMockContentProtectionAllowlistManager, never()).isAllowed(anyString());
    }

    @Test
    public void isWhitelisted_componentName_contentCaptureDisabled_contentProtectionEnabled() {
        when(mMockContentProtectionConsentManager.isConsentGranted(USER_ID)).thenReturn(true);
        when(mMockContentProtectionAllowlistManager.isAllowed(PACKAGE_NAME)).thenReturn(true);
        mDevCfgEnableContentProtectionReceiver = true;
        mContentCaptureManagerService = new TestContentCaptureManagerService();

        boolean actual =
                mContentCaptureManagerService.mGlobalContentCaptureOptions.isWhitelisted(
                        USER_ID, COMPONENT_NAME);

        assertThat(actual).isTrue();
    }

    @Test
    public void isWhitelisted_componentName_contentCaptureEnabled_contentProtectionDisabled() {
        mContentCaptureManagerService.mGlobalContentCaptureOptions.setWhitelist(
                USER_ID, /* packageNames= */ null, ImmutableList.of(COMPONENT_NAME));

        boolean actual =
                mContentCaptureManagerService.mGlobalContentCaptureOptions.isWhitelisted(
                        USER_ID, COMPONENT_NAME);

        assertThat(actual).isTrue();
        verify(mMockContentProtectionConsentManager, never()).isConsentGranted(anyInt());
        verify(mMockContentProtectionAllowlistManager, never()).isAllowed(anyString());
    }

    @Test
    public void isWhitelisted_componentName_contentCaptureEnabled_contentProtectionEnabled() {
        mDevCfgEnableContentProtectionReceiver = true;
        mContentCaptureManagerService = new TestContentCaptureManagerService();
        mContentCaptureManagerService.mGlobalContentCaptureOptions.setWhitelist(
                USER_ID, /* packageNames= */ null, ImmutableList.of(COMPONENT_NAME));

        boolean actual =
                mContentCaptureManagerService.mGlobalContentCaptureOptions.isWhitelisted(
                        USER_ID, COMPONENT_NAME);

        assertThat(actual).isTrue();
        verify(mMockContentProtectionConsentManager, never()).isConsentGranted(anyInt());
        verify(mMockContentProtectionAllowlistManager, never()).isAllowed(anyString());
    }

    @Test
    public void isContentProtectionReceiverEnabled_true() {
        when(mMockContentProtectionConsentManager.isConsentGranted(USER_ID)).thenReturn(true);
        when(mMockContentProtectionAllowlistManager.isAllowed(PACKAGE_NAME)).thenReturn(true);
        mDevCfgEnableContentProtectionReceiver = true;
        mContentCaptureManagerService = new TestContentCaptureManagerService();

        boolean actual =
                mContentCaptureManagerService.mGlobalContentCaptureOptions.isWhitelisted(
                        USER_ID, PACKAGE_NAME);

        assertThat(actual).isTrue();
    }

    @Test
    public void isContentProtectionReceiverEnabled_false_withoutManagers() {
        boolean actual =
                mContentCaptureManagerService.mGlobalContentCaptureOptions.isWhitelisted(
                        USER_ID, PACKAGE_NAME);

        assertThat(actual).isFalse();
        verify(mMockContentProtectionConsentManager, never()).isConsentGranted(anyInt());
        verify(mMockContentProtectionAllowlistManager, never()).isAllowed(anyString());
    }

    @Test
    public void isContentProtectionReceiverEnabled_false_disabledWithFlag() {
        mDevCfgEnableContentProtectionReceiver = true;
        mContentCaptureManagerService = new TestContentCaptureManagerService();
        mContentCaptureManagerService.mDevCfgEnableContentProtectionReceiver = false;

        boolean actual =
                mContentCaptureManagerService.mGlobalContentCaptureOptions.isWhitelisted(
                        USER_ID, PACKAGE_NAME);

        assertThat(actual).isFalse();
        verify(mMockContentProtectionConsentManager, never()).isConsentGranted(anyInt());
        verify(mMockContentProtectionAllowlistManager, never()).isAllowed(anyString());
    }

    @Test
    public void isContentProtectionReceiverEnabled_false_emptyGroups() {
        mDevCfgEnableContentProtectionReceiver = true;
        mDevCfgContentProtectionRequiredGroups = Collections.emptyList();
        mDevCfgContentProtectionOptionalGroups = Collections.emptyList();
        mContentCaptureManagerService = new TestContentCaptureManagerService();

        boolean actual =
                mContentCaptureManagerService.mGlobalContentCaptureOptions.isWhitelisted(
                        USER_ID, PACKAGE_NAME);

        assertThat(actual).isFalse();
        verify(mMockContentProtectionConsentManager, never()).isConsentGranted(anyInt());
        verify(mMockContentProtectionAllowlistManager, never()).isAllowed(anyString());
    }

    @Test
    @DisableFlags(FLAG_TRY_CALLBACK_ON_CALLING_USER_ENABLED)
    public void onLoginDetected_disabledAfterConstructor_userFlagDisabled() {
        mDevCfgEnableContentProtectionReceiver = true;
        mContentCaptureManagerService = new TestContentCaptureManagerService();
        mContentCaptureManagerService.mDevCfgEnableContentProtectionReceiver = false;

        mContentCaptureManagerService
                .getContentCaptureManagerServiceStub()
                .onLoginDetected(PARCELED_EVENTS);

        assertThat(mContentProtectionServiceInfosCreated).isEqualTo(0);
        assertThat(mRemoteContentProtectionServicesCreated).isEqualTo(0);
        verifyNoMoreInteractions(mMockRemoteContentProtectionService);
    }

    @Test
    @EnableFlags(FLAG_TRY_CALLBACK_ON_CALLING_USER_ENABLED)
    public void onLoginDetected_disabledAfterConstructor_userFlagEnabled() {
        mDevCfgEnableContentProtectionReceiver = true;
        mContentCaptureManagerService = new TestContentCaptureManagerService();
        mContentCaptureManagerService.mDevCfgEnableContentProtectionReceiver = false;

        mContentCaptureManagerService
                .getContentCaptureManagerServiceStub()
                .onLoginDetected(PARCELED_EVENTS);

        assertThat(mContentProtectionServiceInfosCreated).isEqualTo(0);
        assertThat(mRemoteContentProtectionServicesCreated).isEqualTo(0);
        verifyNoMoreInteractions(mMockRemoteContentProtectionService);
    }

    @Test
    @DisableFlags(FLAG_TRY_CALLBACK_ON_CALLING_USER_ENABLED)
    public void onLoginDetected_invalidPermissions_userFlagDisabled() {
        mDevCfgEnableContentProtectionReceiver = true;
        mContentProtectionServiceInfoConstructorShouldThrow = true;
        mContentCaptureManagerService = new TestContentCaptureManagerService();

        mContentCaptureManagerService
                .getContentCaptureManagerServiceStub()
                .onLoginDetected(PARCELED_EVENTS);

        assertThat(mContentProtectionServiceInfosCreated).isEqualTo(1);
        assertThat(mRemoteContentProtectionServicesCreated).isEqualTo(0);
        verifyNoMoreInteractions(mMockRemoteContentProtectionService);
    }

    @Test
    @EnableFlags(FLAG_TRY_CALLBACK_ON_CALLING_USER_ENABLED)
    public void onLoginDetected_invalidPermissions_userFlagEnabled() {
        mDevCfgEnableContentProtectionReceiver = true;
        mContentProtectionServiceInfoConstructorShouldThrow = true;
        mContentCaptureManagerService = new TestContentCaptureManagerService();

        mContentCaptureManagerService
                .getContentCaptureManagerServiceStub()
                .onLoginDetected(PARCELED_EVENTS);

        assertThat(mContentProtectionServiceInfosCreated).isEqualTo(1);
        assertThat(mRemoteContentProtectionServicesCreated).isEqualTo(0);
        verifyNoMoreInteractions(mMockRemoteContentProtectionService);
    }

    @Test
    @DisableFlags(FLAG_TRY_CALLBACK_ON_CALLING_USER_ENABLED)
    public void onLoginDetected_running_userFlagDisabled() {
        mDevCfgEnableContentProtectionReceiver = true;
        mContentCaptureManagerService = new TestContentCaptureManagerService();

        mContentCaptureManagerService
                .getContentCaptureManagerServiceStub()
                .onLoginDetected(PARCELED_EVENTS);

        assertThat(mContentProtectionServiceInfosCreated).isEqualTo(1);
        assertThat(mRemoteContentProtectionServicesCreated).isEqualTo(1);
        verify(mMockRemoteContentProtectionService).onLoginDetected(PARCELED_EVENTS);
    }

    @Test
    @EnableFlags(FLAG_TRY_CALLBACK_ON_CALLING_USER_ENABLED)
    public void onLoginDetected_running_userFlagEnabled() {
        mDevCfgEnableContentProtectionReceiver = true;
        mContentCaptureManagerService = new TestContentCaptureManagerService();

        mContentCaptureManagerService
                .getContentCaptureManagerServiceStub()
                .onLoginDetected(PARCELED_EVENTS);

        assertThat(mContentProtectionServiceInfosCreated).isEqualTo(1);
        assertThat(mRemoteContentProtectionServicesCreated).isEqualTo(1);
        verify(mMockRemoteContentProtectionService).onLoginDetected(PARCELED_EVENTS);
    }

    @Test
    @DisableFlags(FLAG_SET_CONTENT_PROTECTION_ALLOWLIST_ENABLED)
    public void setContentProtectionAllowlist_flagDisabled() {
        mDevCfgEnableContentProtectionReceiver = true;
        mContentCaptureManagerService = new TestContentCaptureManagerService(mMockContext);

        assertThrows(UnsupportedOperationException.class, () ->
                mContentCaptureManagerService
                        .getContentCaptureManagerServiceStub()
                        .setContentProtectionAllowlist(List.of()));

        verify(mMockContentProtectionAllowlistManager).start(anyLong());
        verifyNoMoreInteractions(mMockContentProtectionAllowlistManager);
        verifyNoInteractions(mMockContentProtectionConsentManager);
        assertThat(mContentProtectionServiceInfosCreated).isEqualTo(0);
        assertThat(mRemoteContentProtectionServicesCreated).isEqualTo(0);
        verify(mMockContext, never()).enforceCallingPermission(anyString(), anyString());
        verifyNoInteractions(mMockPackageManager);
        verifyNoInteractions(mMockContentCaptureOptionsCallback);
    }

    @Test
    @EnableFlags(FLAG_SET_CONTENT_PROTECTION_ALLOWLIST_ENABLED)
    public void setContentProtectionAllowlist_invalidPermissions() {
        mDevCfgEnableContentProtectionReceiver = true;
        doThrow(SecurityException.class).when(mMockContext)
                .enforceCallingPermission(eq(SET_CONTENT_PROTECTION_ALLOWLIST), anyString());
        mContentCaptureManagerService = new TestContentCaptureManagerService(mMockContext);

        assertThrows(SecurityException.class, () ->
                mContentCaptureManagerService
                        .getContentCaptureManagerServiceStub()
                        .setContentProtectionAllowlist(List.of()));

        verify(mMockContentProtectionAllowlistManager).start(anyLong());
        verifyNoMoreInteractions(mMockContentProtectionAllowlistManager);
        verifyNoInteractions(mMockContentProtectionConsentManager);
        assertThat(mContentProtectionServiceInfosCreated).isEqualTo(0);
        assertThat(mRemoteContentProtectionServicesCreated).isEqualTo(0);
        verify(mMockContext).enforceCallingPermission(
                eq(SET_CONTENT_PROTECTION_ALLOWLIST), anyString());
        verifyNoInteractions(mMockPackageManager);
        verifyNoInteractions(mMockContentCaptureOptionsCallback);
    }

    @Test
    @EnableFlags(FLAG_SET_CONTENT_PROTECTION_ALLOWLIST_ENABLED)
    public void setContentProtectionAllowlist_receiverDisabled() {
        mDevCfgEnableContentProtectionReceiver = false;
        when(mMockContext.checkCallingPermission(SET_CONTENT_PROTECTION_ALLOWLIST))
                .thenReturn(PackageManager.PERMISSION_GRANTED);
        mContentCaptureManagerService = new TestContentCaptureManagerService(mMockContext);

        mContentCaptureManagerService
                .getContentCaptureManagerServiceStub()
                .setContentProtectionAllowlist(List.of());

        verifyNoInteractions(mMockContentProtectionAllowlistManager);
        verifyNoInteractions(mMockContentProtectionConsentManager);
        assertThat(mContentProtectionServiceInfosCreated).isEqualTo(0);
        assertThat(mRemoteContentProtectionServicesCreated).isEqualTo(0);
        verify(mMockContext).enforceCallingPermission(
                eq(SET_CONTENT_PROTECTION_ALLOWLIST), anyString());
        verifyNoInteractions(mMockPackageManager);
        verifyNoInteractions(mMockContentCaptureOptionsCallback);
    }

    @Test
    @EnableFlags(FLAG_SET_CONTENT_PROTECTION_ALLOWLIST_ENABLED)
    public void setContentProtectionAllowlist_noReceiver() {
        mDevCfgEnableContentProtectionReceiver = true;
        mConfigDefaultContentProtectionService = "";
        when(mMockContext.checkCallingPermission(SET_CONTENT_PROTECTION_ALLOWLIST))
                .thenReturn(PackageManager.PERMISSION_GRANTED);
        mContentCaptureManagerService = new TestContentCaptureManagerService(mMockContext);

        mContentCaptureManagerService
                .getContentCaptureManagerServiceStub()
                .setContentProtectionAllowlist(List.of());

        verifyNoInteractions(mMockContentProtectionAllowlistManager);
        verifyNoInteractions(mMockContentProtectionConsentManager);
        assertThat(mContentProtectionServiceInfosCreated).isEqualTo(0);
        assertThat(mRemoteContentProtectionServicesCreated).isEqualTo(0);
        verify(mMockContext).enforceCallingPermission(
                eq(SET_CONTENT_PROTECTION_ALLOWLIST), anyString());
        verifyNoInteractions(mMockPackageManager);
        verifyNoInteractions(mMockContentCaptureOptionsCallback);
    }

    @Test
    @EnableFlags(FLAG_SET_CONTENT_PROTECTION_ALLOWLIST_ENABLED)
    public void setContentProtectionAllowlist_invalidCaller() {
        mDevCfgEnableContentProtectionReceiver = true;
        when(mMockContext.checkCallingPermission(SET_CONTENT_PROTECTION_ALLOWLIST))
                .thenReturn(PackageManager.PERMISSION_GRANTED);
        when(mMockContext.getPackageManager()).thenReturn(mMockPackageManager);
        when(mMockPackageManager.getNameForUid(Process.myUid())).thenReturn(SECOND_PACKAGE_NAME);
        mContentCaptureManagerService = new TestContentCaptureManagerService(mMockContext);

        assertThrows(SecurityException.class, () ->
                mContentCaptureManagerService
                        .getContentCaptureManagerServiceStub()
                        .setContentProtectionAllowlist(List.of()));

        verify(mMockContentProtectionAllowlistManager).start(anyLong());
        verifyNoMoreInteractions(mMockContentProtectionAllowlistManager);
        verifyNoInteractions(mMockContentProtectionConsentManager);
        assertThat(mContentProtectionServiceInfosCreated).isEqualTo(0);
        assertThat(mRemoteContentProtectionServicesCreated).isEqualTo(0);
        verify(mMockContext).enforceCallingPermission(
                eq(SET_CONTENT_PROTECTION_ALLOWLIST), anyString());
        verify(mMockPackageManager).getNameForUid(Process.myUid());
        verify(mMockPackageManager).registerPackageMonitorCallback(any(), anyInt());
        verifyNoMoreInteractions(mMockPackageManager);
        verifyNoInteractions(mMockContentCaptureOptionsCallback);
    }

    @Test
    @EnableFlags(FLAG_SET_CONTENT_PROTECTION_ALLOWLIST_ENABLED)
    public void setContentProtectionAllowlist_withoutCallback() {
        mDevCfgEnableContentProtectionReceiver = true;
        when(mMockContext.checkCallingPermission(SET_CONTENT_PROTECTION_ALLOWLIST))
                .thenReturn(PackageManager.PERMISSION_GRANTED);
        when(mMockContext.getPackageManager()).thenReturn(mMockPackageManager);
        when(mMockPackageManager.getNameForUid(Process.myUid())).thenReturn(PACKAGE_NAME);
        mContentCaptureManagerService = new TestContentCaptureManagerService(mMockContext);
        List<String> packages = List.of(SECOND_PACKAGE_NAME);

        mContentCaptureManagerService
                .getContentCaptureManagerServiceStub()
                .setContentProtectionAllowlist(packages);

        verify(mMockContentProtectionAllowlistManager).start(anyLong());
        verify(mMockContentProtectionAllowlistManager).setContentProtectionAllowlist(packages);
        verifyNoMoreInteractions(mMockContentProtectionAllowlistManager);
        verifyNoInteractions(mMockContentProtectionConsentManager);
        assertThat(mContentProtectionServiceInfosCreated).isEqualTo(0);
        assertThat(mRemoteContentProtectionServicesCreated).isEqualTo(0);
        verify(mMockContext).enforceCallingPermission(
                eq(SET_CONTENT_PROTECTION_ALLOWLIST), anyString());
        verify(mMockPackageManager).getNameForUid(Process.myUid());
        verify(mMockPackageManager).registerPackageMonitorCallback(any(), anyInt());
        verifyNoMoreInteractions(mMockPackageManager);
        verifyNoInteractions(mMockContentCaptureOptionsCallback);
    }

    @Test
    @EnableFlags(FLAG_SET_CONTENT_PROTECTION_ALLOWLIST_ENABLED)
    public void setContentProtectionAllowlist_withCallback_samePackage() throws Exception {
        mDevCfgEnableContentProtectionReceiver = true;
        when(mMockContext.checkCallingPermission(SET_CONTENT_PROTECTION_ALLOWLIST))
                .thenReturn(PackageManager.PERMISSION_GRANTED);
        when(mMockContext.getPackageManager()).thenReturn(mMockPackageManager);
        when(mMockPackageManager.getNameForUid(Process.myUid())).thenReturn(PACKAGE_NAME);
        when(mMockContentCaptureOptionsCallback.asBinder()).thenReturn(mMockBinder);
        mContentCaptureManagerService = new TestContentCaptureManagerService(mMockContext);
        mContentCaptureManagerService.getContentCaptureManagerServiceStub()
                .registerContentCaptureOptionsCallback(PACKAGE_NAME,
                        mMockContentCaptureOptionsCallback);
        List<String> packages = List.of(SECOND_PACKAGE_NAME, THIRD_PACKAGE_NAME);
        when(mMockContentProtectionAllowlistManager.setContentProtectionAllowlist(packages))
                .thenReturn(Set.of(PACKAGE_NAME, SECOND_PACKAGE_NAME));
        verify(mMockContentProtectionConsentManager).isConsentGranted(mFakeCallingUserId);
        verify(mMockContentCaptureOptionsCallback).setContentCaptureOptions(any());

        mContentCaptureManagerService
                .getContentCaptureManagerServiceStub()
                .setContentProtectionAllowlist(packages);

        verify(mMockContentProtectionAllowlistManager).start(anyLong());
        verify(mMockContentProtectionAllowlistManager).setContentProtectionAllowlist(packages);
        verifyNoMoreInteractions(mMockContentProtectionAllowlistManager);
        verify(mMockContentProtectionConsentManager, times(2)).isConsentGranted(mFakeCallingUserId);
        assertThat(mContentProtectionServiceInfosCreated).isEqualTo(0);
        assertThat(mRemoteContentProtectionServicesCreated).isEqualTo(0);
        verify(mMockContext).enforceCallingPermission(
                eq(SET_CONTENT_PROTECTION_ALLOWLIST), anyString());
        verify(mMockPackageManager).getNameForUid(Process.myUid());
        verify(mMockPackageManager).registerPackageMonitorCallback(any(), anyInt());
        verifyNoMoreInteractions(mMockPackageManager);
        verify(mMockContentCaptureOptionsCallback, times(2)).setContentCaptureOptions(any());
    }

    @Test
    @EnableFlags(FLAG_SET_CONTENT_PROTECTION_ALLOWLIST_ENABLED)
    public void setContentProtectionAllowlist_withCallback_differentPackage() throws Exception {
        mDevCfgEnableContentProtectionReceiver = true;
        when(mMockContext.checkCallingPermission(SET_CONTENT_PROTECTION_ALLOWLIST))
                .thenReturn(PackageManager.PERMISSION_GRANTED);
        when(mMockContext.getPackageManager()).thenReturn(mMockPackageManager);
        when(mMockPackageManager.getNameForUid(Process.myUid())).thenReturn(PACKAGE_NAME);
        when(mMockContentCaptureOptionsCallback.asBinder()).thenReturn(mMockBinder);
        mContentCaptureManagerService = new TestContentCaptureManagerService(mMockContext);
        mContentCaptureManagerService.getContentCaptureManagerServiceStub()
                .registerContentCaptureOptionsCallback(PACKAGE_NAME,
                        mMockContentCaptureOptionsCallback);
        List<String> packages = List.of(SECOND_PACKAGE_NAME, THIRD_PACKAGE_NAME);
        when(mMockContentProtectionAllowlistManager.setContentProtectionAllowlist(packages))
                .thenReturn(Set.of(SECOND_PACKAGE_NAME));
        verify(mMockContentProtectionConsentManager).isConsentGranted(mFakeCallingUserId);
        verify(mMockContentCaptureOptionsCallback).setContentCaptureOptions(any());

        mContentCaptureManagerService
                .getContentCaptureManagerServiceStub()
                .setContentProtectionAllowlist(packages);

        verify(mMockContentProtectionAllowlistManager).start(anyLong());
        verify(mMockContentProtectionAllowlistManager).setContentProtectionAllowlist(packages);
        verifyNoMoreInteractions(mMockContentProtectionAllowlistManager);
        verifyNoMoreInteractions(mMockContentProtectionConsentManager);
        assertThat(mContentProtectionServiceInfosCreated).isEqualTo(0);
        assertThat(mRemoteContentProtectionServicesCreated).isEqualTo(0);
        verify(mMockContext).enforceCallingPermission(
                eq(SET_CONTENT_PROTECTION_ALLOWLIST), anyString());
        verify(mMockPackageManager).getNameForUid(Process.myUid());
        verify(mMockPackageManager).registerPackageMonitorCallback(any(), anyInt());
        verifyNoMoreInteractions(mMockPackageManager);
        verify(mMockContentCaptureOptionsCallback).setContentCaptureOptions(any());
    }

    @Test
    @EnableFlags(FLAG_SET_CONTENT_PROTECTION_ALLOWLIST_ENABLED)
    public void setContentProtectionAllowlist_withCallback_noChanges() throws Exception {
        mDevCfgEnableContentProtectionReceiver = true;
        when(mMockContext.checkCallingPermission(SET_CONTENT_PROTECTION_ALLOWLIST))
                .thenReturn(PackageManager.PERMISSION_GRANTED);
        when(mMockContext.getPackageManager()).thenReturn(mMockPackageManager);
        when(mMockPackageManager.getNameForUid(Process.myUid())).thenReturn(PACKAGE_NAME);
        when(mMockContentCaptureOptionsCallback.asBinder()).thenReturn(mMockBinder);
        mContentCaptureManagerService = new TestContentCaptureManagerService(mMockContext);
        mContentCaptureManagerService.getContentCaptureManagerServiceStub()
                .registerContentCaptureOptionsCallback(PACKAGE_NAME,
                        mMockContentCaptureOptionsCallback);
        List<String> packages = List.of(PACKAGE_NAME, SECOND_PACKAGE_NAME);
        when(mMockContentProtectionAllowlistManager.setContentProtectionAllowlist(packages))
                .thenReturn(Set.of());
        verify(mMockContentProtectionConsentManager).isConsentGranted(mFakeCallingUserId);
        verify(mMockContentCaptureOptionsCallback).setContentCaptureOptions(any());

        mContentCaptureManagerService
                .getContentCaptureManagerServiceStub()
                .setContentProtectionAllowlist(packages);

        verify(mMockContentProtectionAllowlistManager).start(anyLong());
        verify(mMockContentProtectionAllowlistManager).setContentProtectionAllowlist(packages);
        verifyNoMoreInteractions(mMockContentProtectionAllowlistManager);
        verifyNoMoreInteractions(mMockContentProtectionConsentManager);
        assertThat(mContentProtectionServiceInfosCreated).isEqualTo(0);
        assertThat(mRemoteContentProtectionServicesCreated).isEqualTo(0);
        verify(mMockContext).enforceCallingPermission(
                eq(SET_CONTENT_PROTECTION_ALLOWLIST), anyString());
        verify(mMockPackageManager).getNameForUid(Process.myUid());
        verify(mMockPackageManager).registerPackageMonitorCallback(any(), anyInt());
        verifyNoMoreInteractions(mMockPackageManager);
        verify(mMockContentCaptureOptionsCallback).setContentCaptureOptions(any());
    }

    @Test
    public void parseContentProtectionGroupsConfig_null() {
        ContentCaptureManagerService service = new ContentCaptureManagerService(sContext);

        assertThat(service.parseContentProtectionGroupsConfig(null)).isEmpty();
    }

    @Test
    public void parseContentProtectionGroupsConfig_empty() {
        ContentCaptureManagerService service = new ContentCaptureManagerService(sContext);

        assertThat(service.parseContentProtectionGroupsConfig("")).isEmpty();
    }

    @Test
    public void parseContentProtectionGroupsConfig_singleValue() {
        ContentCaptureManagerService service = new ContentCaptureManagerService(sContext);

        assertThat(service.parseContentProtectionGroupsConfig("a"))
                .isEqualTo(List.of(List.of("a")));
    }

    @Test
    public void parseContentProtectionGroupsConfig_multipleValues() {
        ContentCaptureManagerService service = new ContentCaptureManagerService(sContext);

        assertThat(service.parseContentProtectionGroupsConfig("a,b"))
                .isEqualTo(List.of(List.of("a", "b")));
    }

    @Test
    public void parseContentProtectionGroupsConfig_multipleGroups() {
        ContentCaptureManagerService service = new ContentCaptureManagerService(sContext);

        assertThat(service.parseContentProtectionGroupsConfig("a,b;c;d,e"))
                .isEqualTo(List.of(List.of("a", "b"), List.of("c"), List.of("d", "e")));
    }

    @Test
    public void parseContentProtectionGroupsConfig_emptyValues() {
        ContentCaptureManagerService service = new ContentCaptureManagerService(sContext);

        assertThat(service.parseContentProtectionGroupsConfig("a;b;;;c;,d,,e,;,;"))
                .isEqualTo(List.of(List.of("a"), List.of("b"), List.of("c"), List.of("d", "e")));
    }

    @Test
    @DisableFlags(FLAG_SET_CONTENT_PROTECTION_ALLOWLIST_ENABLED)
    public void updateOptions_callbackCookie_flagDisabled_samePackage_sameUser()
            throws Exception {
        when(mMockContentCaptureOptionsCallback.asBinder()).thenReturn(mMockBinder);
        mContentCaptureManagerService.getContentCaptureManagerServiceStub()
                .registerContentCaptureOptionsCallback(PACKAGE_NAME,
                        mMockContentCaptureOptionsCallback);
        verify(mMockContentCaptureOptionsCallback, never()).setContentCaptureOptions(any());

        mContentCaptureManagerService.updateOptions(PACKAGE_NAME, CONTENT_CAPTURE_OPTIONS);

        verify(mMockContentCaptureOptionsCallback)
                .setContentCaptureOptions(CONTENT_CAPTURE_OPTIONS);
        assertThat(mGetCallingUserIdCallCount).isEqualTo(1);
    }

    @Test
    @DisableFlags(FLAG_SET_CONTENT_PROTECTION_ALLOWLIST_ENABLED)
    public void updateOptions_callbackCookie_flagDisabled_samePackage_differentUser()
            throws Exception {
        mFakeCallingUserId = SECOND_USER_ID;
        when(mMockContentCaptureOptionsCallback.asBinder()).thenReturn(mMockBinder);
        mContentCaptureManagerService.getContentCaptureManagerServiceStub()
                .registerContentCaptureOptionsCallback(PACKAGE_NAME,
                        mMockContentCaptureOptionsCallback);
        verify(mMockContentCaptureOptionsCallback, never()).setContentCaptureOptions(any());
        mFakeCallingUserId = USER_ID;

        mContentCaptureManagerService.updateOptions(PACKAGE_NAME, CONTENT_CAPTURE_OPTIONS);

        verify(mMockContentCaptureOptionsCallback)
                .setContentCaptureOptions(CONTENT_CAPTURE_OPTIONS);
        assertThat(mGetCallingUserIdCallCount).isEqualTo(1);
    }

    @Test
    @DisableFlags(FLAG_SET_CONTENT_PROTECTION_ALLOWLIST_ENABLED)
    public void updateOptions_callbackCookie_flagDisabled_differentPackage_sameUser()
            throws Exception {
        when(mMockContentCaptureOptionsCallback.asBinder()).thenReturn(mMockBinder);
        mContentCaptureManagerService.getContentCaptureManagerServiceStub()
                .registerContentCaptureOptionsCallback(PACKAGE_NAME,
                        mMockContentCaptureOptionsCallback);

        mContentCaptureManagerService.updateOptions(SECOND_PACKAGE_NAME, CONTENT_CAPTURE_OPTIONS);

        verify(mMockContentCaptureOptionsCallback, never()).setContentCaptureOptions(any());
        assertThat(mGetCallingUserIdCallCount).isEqualTo(1);
    }

    @Test
    @DisableFlags(FLAG_SET_CONTENT_PROTECTION_ALLOWLIST_ENABLED)
    public void updateOptions_callbackCookie_flagDisabled_differentPackage_differentUser()
            throws Exception {
        mFakeCallingUserId = SECOND_USER_ID;
        when(mMockContentCaptureOptionsCallback.asBinder()).thenReturn(mMockBinder);
        mContentCaptureManagerService.getContentCaptureManagerServiceStub()
                .registerContentCaptureOptionsCallback(PACKAGE_NAME,
                        mMockContentCaptureOptionsCallback);
        mFakeCallingUserId = USER_ID;

        mContentCaptureManagerService.updateOptions(SECOND_PACKAGE_NAME, CONTENT_CAPTURE_OPTIONS);

        verify(mMockContentCaptureOptionsCallback, never()).setContentCaptureOptions(any());
        assertThat(mGetCallingUserIdCallCount).isEqualTo(1);
    }

    @Test
    @EnableFlags(FLAG_SET_CONTENT_PROTECTION_ALLOWLIST_ENABLED)
    public void updateOptions_callbackCookie_flagEnabled_samePackage_sameUser() throws Exception {
        when(mMockContentCaptureOptionsCallback.asBinder()).thenReturn(mMockBinder);
        mContentCaptureManagerService.getContentCaptureManagerServiceStub()
                .registerContentCaptureOptionsCallback(PACKAGE_NAME,
                        mMockContentCaptureOptionsCallback);
        verify(mMockContentCaptureOptionsCallback, never()).setContentCaptureOptions(any());

        mContentCaptureManagerService.updateOptions(PACKAGE_NAME, CONTENT_CAPTURE_OPTIONS);

        verify(mMockContentCaptureOptionsCallback)
                .setContentCaptureOptions(CONTENT_CAPTURE_OPTIONS);
        assertThat(mGetCallingUserIdCallCount).isEqualTo(1);
    }

    @Test
    @EnableFlags(FLAG_SET_CONTENT_PROTECTION_ALLOWLIST_ENABLED)
    public void updateOptions_callbackCookie_flagEnabled_samePackage_differentUser()
            throws Exception {
        mFakeCallingUserId = SECOND_USER_ID;
        when(mMockContentCaptureOptionsCallback.asBinder()).thenReturn(mMockBinder);
        mContentCaptureManagerService.getContentCaptureManagerServiceStub()
                .registerContentCaptureOptionsCallback(PACKAGE_NAME,
                        mMockContentCaptureOptionsCallback);
        verify(mMockContentCaptureOptionsCallback, never()).setContentCaptureOptions(any());
        mFakeCallingUserId = USER_ID;

        mContentCaptureManagerService.updateOptions(PACKAGE_NAME, CONTENT_CAPTURE_OPTIONS);

        verify(mMockContentCaptureOptionsCallback)
                .setContentCaptureOptions(CONTENT_CAPTURE_OPTIONS);
        assertThat(mGetCallingUserIdCallCount).isEqualTo(1);
    }

    @Test
    @EnableFlags(FLAG_SET_CONTENT_PROTECTION_ALLOWLIST_ENABLED)
    public void updateOptions_callbackCookie_flagEnabled_differentPackage_sameUser()
            throws Exception {
        when(mMockContentCaptureOptionsCallback.asBinder()).thenReturn(mMockBinder);
        mContentCaptureManagerService.getContentCaptureManagerServiceStub()
                .registerContentCaptureOptionsCallback(PACKAGE_NAME,
                        mMockContentCaptureOptionsCallback);

        mContentCaptureManagerService.updateOptions(SECOND_PACKAGE_NAME, CONTENT_CAPTURE_OPTIONS);

        verify(mMockContentCaptureOptionsCallback, never()).setContentCaptureOptions(any());
        assertThat(mGetCallingUserIdCallCount).isEqualTo(1);
    }

    @Test
    @EnableFlags(FLAG_SET_CONTENT_PROTECTION_ALLOWLIST_ENABLED)
    public void updateOptions_callbackCookie_flagEnabled_differentPackage_differentUser()
            throws Exception {
        mFakeCallingUserId = SECOND_USER_ID;
        when(mMockContentCaptureOptionsCallback.asBinder()).thenReturn(mMockBinder);
        mContentCaptureManagerService.getContentCaptureManagerServiceStub()
                .registerContentCaptureOptionsCallback(PACKAGE_NAME,
                        mMockContentCaptureOptionsCallback);
        mFakeCallingUserId = USER_ID;

        mContentCaptureManagerService.updateOptions(SECOND_PACKAGE_NAME, CONTENT_CAPTURE_OPTIONS);

        verify(mMockContentCaptureOptionsCallback, never()).setContentCaptureOptions(any());
        assertThat(mGetCallingUserIdCallCount).isEqualTo(1);
    }

    private class TestContentCaptureManagerService extends ContentCaptureManagerService {

        TestContentCaptureManagerService() {
            this(sContext);
        }

        TestContentCaptureManagerService(@NonNull Context context) {
            super(context);
            this.mDevCfgContentProtectionRequiredGroups =
                    ContentCaptureManagerServiceTest.this.mDevCfgContentProtectionRequiredGroups;
            this.mDevCfgContentProtectionOptionalGroups =
                    ContentCaptureManagerServiceTest.this.mDevCfgContentProtectionOptionalGroups;
        }

        @Override
        protected boolean getDeviceConfigEnableContentProtectionReceiver() {
            return ContentCaptureManagerServiceTest.this.mDevCfgEnableContentProtectionReceiver;
        }

        @Override
        protected ContentProtectionAllowlistManager createContentProtectionAllowlistManager(
                long timeoutMs) {
            mContentProtectionAllowlistManagersCreated++;
            return mMockContentProtectionAllowlistManager;
        }

        @Override
        protected String getContentProtectionServiceFlatComponentName() {
            return mConfigDefaultContentProtectionService;
        }

        @Override
        protected ContentCaptureServiceInfo createContentProtectionServiceInfo(
                @NonNull ComponentName componentName) {
            mContentProtectionServiceInfosCreated++;
            if (mContentProtectionServiceInfoConstructorShouldThrow) {
                throw new RuntimeException("TEST RUNTIME EXCEPTION");
            }
            return mMockContentCaptureServiceInfo;
        }

        @Override
        protected RemoteContentProtectionService createRemoteContentProtectionService(
                @NonNull ComponentName componentName,
                long autoDisconnectTimeoutMs,
                @UserIdInt int userId) {
            assertThat(userId).isEqualTo(mFakeCallingUserId);
            mRemoteContentProtectionServicesCreated++;
            return mMockRemoteContentProtectionService;
        }

        @Override
        protected ContentProtectionConsentManager createContentProtectionConsentManager() {
            mContentProtectionConsentManagersCreated++;
            return mMockContentProtectionConsentManager;
        }

        @Override
        protected void assertCalledByPackageOwner(@NonNull String packageName) {
            assertThat(packageName).isEqualTo(PACKAGE_NAME);
        }

        @Override
        @UserIdInt
        protected int getCallingUserId() {
            assertThat(super.getCallingUserId()).isEqualTo(UserHandle.USER_SYSTEM);
            mGetCallingUserIdCallCount++;
            return mFakeCallingUserId;
        }
    }
}
