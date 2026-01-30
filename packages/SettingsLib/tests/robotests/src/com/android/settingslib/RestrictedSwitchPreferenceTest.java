/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.settingslib;

import static android.security.advancedprotection.AdvancedProtectionManager.ADVANCED_PROTECTION_SYSTEM_ENTITY;

import static com.android.settingslib.RestrictedLockUtils.EnforcedAdmin.createDefaultEnforcedAdminWithRestriction;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.app.admin.Authority;
import android.app.admin.DevicePolicyManager;
import android.app.admin.DevicePolicyResourcesManager;
import android.app.admin.DpcAuthority;
import android.app.admin.EnforcingAdmin;
import android.app.admin.SystemAuthority;
import android.app.admin.flags.Flags;
import android.app.ecm.EnhancedConfirmationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.UserHandle;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityManager;
import android.widget.ImageView;

import androidx.preference.PreferenceViewHolder;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class RestrictedSwitchPreferenceTest {

    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    private static final int SIZE = 50;
    private final Authority mAdvancedProtectionAuthority = new SystemAuthority(
            ADVANCED_PROTECTION_SYSTEM_ENTITY);

    private AutoCloseable mMockCloseable;
    private RestrictedSwitchPreference mPreference;
    private Context mContext;
    private PreferenceViewHolder mViewHolder;
    private View mRootView;
    private ImageView mImageView;
    @Mock
    private DevicePolicyManager mDevicePolicyManager;
    @Mock
    private DevicePolicyResourcesManager mDevicePolicyResourcesManager;
    @Mock
    private AccessibilityManager mAccessibilityManager;
    @Mock
    private EnhancedConfirmationManager mEcManager;

    @Before
    public void setUp() {
        mMockCloseable = MockitoAnnotations.openMocks(this);
        mContext = spy(ApplicationProvider.getApplicationContext());
        mPreference = new RestrictedSwitchPreference(mContext);
        mRootView = View.inflate(mContext, R.layout.restricted_switch_preference,
                null /* parent */);
        mViewHolder = PreferenceViewHolder.createInstanceForTests(mRootView);
        mImageView = (ImageView) mViewHolder.findViewById(android.R.id.icon);
        doReturn(mDevicePolicyResourcesManager).when(mDevicePolicyManager).getResources();
        doReturn(mDevicePolicyManager).when(mContext).getSystemService(DevicePolicyManager.class);
        doReturn(mAccessibilityManager).when(mContext)
                .getSystemService(Context.ACCESSIBILITY_SERVICE);
        doReturn(mEcManager).when(mContext)
                .getSystemService(Context.ECM_ENHANCED_CONFIRMATION_SERVICE);
    }

    @After
    public void tearDown() throws Exception {
        mMockCloseable.close();
    }

    @Test
    public void onBindViewHolder_setIconSize_shouldHaveCorrectLayoutParam() {
        mPreference.setIconSize(SIZE);

        mPreference.onBindViewHolder(mViewHolder);

        assertThat(mImageView.getLayoutParams().height).isEqualTo(SIZE);
        assertThat(mImageView.getLayoutParams().width).isEqualTo(SIZE);
    }

    @Test
    public void onBindViewHolder_notSetIconSize_shouldHaveCorrectLayoutParam() {
        mPreference.onBindViewHolder(mViewHolder);

        assertThat(mImageView.getLayoutParams().height).isEqualTo(
                ViewGroup.LayoutParams.WRAP_CONTENT);
        assertThat(mImageView.getLayoutParams().width).isEqualTo(
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    @Test
    public void performClick_withAccessibility_notDisabled_keepsDefaultSummary() {
        mPreference.setSummary("default summary");

        when(mAccessibilityManager.isEnabled()).thenReturn(true);

        assertThat(mPreference.getSummary().toString()).isEqualTo("default summary");

        mPreference.performClick();

        assertThat(mPreference.getSummary().toString()).isEqualTo("default summary");
    }

    @Test
    public void performClick_withAccessibility_disabledByAdmin_keepsDisabledSummary() {
        when(mDevicePolicyResourcesManager.getString(any(), any())).thenReturn("test");
        when(mContext.getString(
                com.android.settingslib.widget.restricted.R.string.disabled_by_admin))
                    .thenReturn("test");
        doNothing().when(mContext).startActivityAsUser(any(), any());
        when(mAccessibilityManager.isEnabled()).thenReturn(true);

        mPreference.setDisabledByAdmin(new RestrictedLockUtils.EnforcedAdmin());

        assertThat(mPreference.getSummary().toString()).isEqualTo("test");

        mPreference.performClick();

        assertThat(mPreference.getSummary().toString()).isEqualTo("test");
    }

    @Test
    public void performClick_withAccessibility_disabledByAdvancedProtection_keepsDefaultSummary() {
        mPreference.setSummary("default summary");

        when(mDevicePolicyManager.getEnforcingAdmin(anyInt(), any())).thenReturn(
                new EnforcingAdmin("pkg.test", mAdvancedProtectionAuthority, UserHandle.of(
                        UserHandle.myUserId()), new ComponentName("admin", "adminclass")));
        doNothing().when(mContext).startActivityAsUser(any(), any());
        when(mAccessibilityManager.isEnabled()).thenReturn(true);

        mPreference.setDisabledByAdmin(createDefaultEnforcedAdminWithRestriction("identifier"));

        assertThat(mPreference.getSummary().toString()).isEqualTo("default summary");

        mPreference.performClick();

        assertThat(mPreference.getSummary().toString()).isEqualTo("default summary");
    }

    @Test
    public void performClick_withAccessibility_disabledByEcm_keepsDisabledSummary()
            throws PackageManager.NameNotFoundException {
        when(mEcManager.isRestricted(any(), any())).thenReturn(true);
        when(mEcManager.createRestrictedSettingDialogIntent(any(), any()))
                .thenReturn(mock(Intent.class));
        when(mContext.getString(R.string.disabled_by_app_ops_text)).thenReturn("test");
        doNothing().when(mContext).startActivity(any());
        when(mAccessibilityManager.isEnabled()).thenReturn(true);

        mPreference.checkEcmRestrictionAndSetDisabled("setting", "pkg.test",
                /* settingEnabled */ false);

        assertThat(mPreference.getSummary().toString()).isEqualTo("test");

        mPreference.performClick();

        assertThat(mPreference.getSummary().toString()).isEqualTo("test");
    }

    @EnableFlags(Flags.FLAG_POLICY_TRANSPARENCY_REFACTOR_ENABLED)
    @Test
    public void setDisabledByAdmin_withAccessibility_keepsDisabledSummary() {
        when(mDevicePolicyResourcesManager.getString(any(), any())).thenReturn("test");
        when(mContext.getString(
                com.android.settingslib.widget.restricted.R.string.disabled_by_admin))
                .thenReturn("test");
        doNothing().when(mContext).startActivityAsUser(any(), any());
        when(mAccessibilityManager.isEnabled()).thenReturn(true);
        EnforcingAdmin enforcingAdmin = new EnforcingAdmin("pkg.test", DpcAuthority.DPC_AUTHORITY,
                UserHandle.of(UserHandle.myUserId()), new ComponentName("admin", "adminclass"));

        mPreference.setDisabledByAdmin(enforcingAdmin);

        assertThat(mPreference.getSummary().toString()).isEqualTo("test");

        mPreference.performClick();

        assertThat(mPreference.getSummary().toString()).isEqualTo("test");
    }
}
