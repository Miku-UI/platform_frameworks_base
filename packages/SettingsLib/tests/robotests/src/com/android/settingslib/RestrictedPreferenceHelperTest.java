/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.atMostOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.admin.Authority;
import android.app.admin.DeviceAdminAuthority;
import android.app.admin.DevicePolicyIdentifiers;
import android.app.admin.DevicePolicyManager;
import android.app.admin.DevicePolicyResourcesManager;
import android.app.admin.DpcAuthority;
import android.app.admin.EnforcingAdmin;
import android.app.admin.PolicyEnforcementInfo;
import android.app.admin.RoleAuthority;
import android.app.admin.SystemAuthority;
import android.app.admin.UnknownAuthority;
import android.app.admin.flags.Flags;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.UserHandle;
import android.os.UserManager;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.platform.test.flag.junit.FlagsParameterization;
import android.platform.test.flag.junit.SetFlagsRule;
import android.view.View;
import android.widget.TextView;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.ParameterizedRobolectricTestRunner;

import java.util.Collections;
import java.util.List;

@RunWith(ParameterizedRobolectricTestRunner.class)
public class RestrictedPreferenceHelperTest {
    @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
    public static List<FlagsParameterization> getParams() {
        return FlagsParameterization.allCombinationsOf(
                Flags.FLAG_POLICY_TRANSPARENCY_REFACTOR_ENABLED);
    }

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Mock
    private Context mContext;
    @Mock
    private Preference mPreference;
    @Mock
    private DevicePolicyManager mDevicePolicyManager;
    @Mock
    private DevicePolicyResourcesManager mDevicePolicyResourcesManager;
    @Mock
    private RestrictedTopLevelPreference mRestrictedTopLevelPreference;

    private final String mPackage = "test.pkg";
    private final ComponentName mAdmin = new ComponentName("admin", "adminclass");
    private final Authority mAdvancedProtectionAuthority = new SystemAuthority(
            ADVANCED_PROTECTION_SYSTEM_ENTITY);

    private PreferenceViewHolder mViewHolder;
    private RestrictedPreferenceHelper mHelper;

    public RestrictedPreferenceHelperTest(FlagsParameterization flagsParameterization) {
        mSetFlagsRule.setFlagsParameterization(flagsParameterization);
    }

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        doReturn(mDevicePolicyResourcesManager).when(mDevicePolicyManager)
                .getResources();
        doReturn(mDevicePolicyManager).when(mContext)
                .getSystemService(DevicePolicyManager.class);
        mViewHolder = PreferenceViewHolder.createInstanceForTests(mock(View.class));
        mHelper = new RestrictedPreferenceHelper(mContext, mPreference, null);
    }

    @RequiresFlagsDisabled(android.security.Flags.FLAG_AAPM_API)
    @Test
    public void bindPreference_disabled_shouldDisplayDisabledSummary() {
        final TextView summaryView = mock(TextView.class, RETURNS_DEEP_STUBS);
        when(mViewHolder.itemView.findViewById(android.R.id.summary))
                .thenReturn(summaryView);
        when(summaryView.getContext().getText(R.string.disabled_by_admin_summary_text))
                .thenReturn("test");
        when(mDevicePolicyResourcesManager.getString(any(), any())).thenReturn("test");

        mHelper.useAdminDisabledSummary(true);
        mHelper.setDisabledByAdmin(new RestrictedLockUtils.EnforcedAdmin());
        mHelper.onBindViewHolder(mViewHolder);

        verify(summaryView).setText("test");
        verify(summaryView, never()).setVisibility(View.GONE);
    }

    @Test
    public void bindPreference_disabledByEcm_shouldDisplayDisabledSummary() {
        final TextView summaryView = mock(TextView.class, RETURNS_DEEP_STUBS);
        when(mViewHolder.itemView.findViewById(android.R.id.summary))
                .thenReturn(summaryView);

        mHelper.setDisabledByEcm(mock(Intent.class));
        mHelper.onBindViewHolder(mViewHolder);

        verify(mPreference).setSummary(R.string.disabled_by_app_ops_text);
        verify(summaryView, never()).setVisibility(View.GONE);
    }

    @RequiresFlagsEnabled(android.security.Flags.FLAG_AAPM_API)
    @Test
    public void bindPreference_disabled_byAdvancedProtection_shouldKeepExistingSummary() {
        final TextView summaryView = mock(TextView.class, RETURNS_DEEP_STUBS);
        final String userRestriction = UserManager.DISALLOW_UNINSTALL_APPS;
        final RestrictedLockUtils.EnforcedAdmin enforcedAdmin = new RestrictedLockUtils
                .EnforcedAdmin(/* component */ null, userRestriction, UserHandle.of(
                        UserHandle.myUserId()));
        final EnforcingAdmin advancedProtectionEnforcingAdmin = new EnforcingAdmin(mPackage,
                mAdvancedProtectionAuthority, UserHandle.of(UserHandle.myUserId()), mAdmin);

        when(mViewHolder.itemView.findViewById(android.R.id.summary))
                .thenReturn(summaryView);
        when(mDevicePolicyManager.getEnforcingAdmin(UserHandle.myUserId(), userRestriction))
                .thenReturn(advancedProtectionEnforcingAdmin);

        summaryView.setText("existing summary");
        mHelper.useAdminDisabledSummary(true);
        mHelper.setDisabledByAdmin(enforcedAdmin);
        mHelper.onBindViewHolder(mViewHolder);

        verify(summaryView, atMostOnce()).setText(any()); // To set it to existing summary
        verify(summaryView, never()).setVisibility(View.VISIBLE);
    }

    @RequiresFlagsEnabled(android.security.Flags.FLAG_AAPM_API)
    @Test
    public void bindPreference_disabled_byAdmin_shouldDisplayDisabledSummary() {
        final TextView summaryView = mock(TextView.class, RETURNS_DEEP_STUBS);
        final EnforcingAdmin nonAdvancedProtectionEnforcingAdmin = new EnforcingAdmin(mPackage,
                UnknownAuthority.UNKNOWN_AUTHORITY, UserHandle.of(UserHandle.myUserId()), mAdmin);
        final String userRestriction = UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY;

        when(mViewHolder.itemView.findViewById(android.R.id.summary))
                .thenReturn(summaryView);
        when(mDevicePolicyManager.getEnforcingAdmin(UserHandle.myUserId(), userRestriction))
                .thenReturn(nonAdvancedProtectionEnforcingAdmin);
        when(mContext.getString(R.string.disabled_by_admin_summary_text))
                .thenReturn("test");
        when(mDevicePolicyResourcesManager.getString(any(), any())).thenReturn("test");

        mHelper.useAdminDisabledSummary(true);
        mHelper.setDisabledByAdmin(new RestrictedLockUtils.EnforcedAdmin());
        mHelper.onBindViewHolder(mViewHolder);

        verify(summaryView).setText("test");
        verify(summaryView, never()).setVisibility(View.GONE);
    }

    @Test
    public void bindPreference_notDisabled_shouldNotHideSummary() {
        final TextView summaryView = mock(TextView.class, RETURNS_DEEP_STUBS);
        when(mViewHolder.itemView.findViewById(android.R.id.summary))
                .thenReturn(summaryView);
        when(summaryView.getContext().getText(R.string.disabled_by_admin_summary_text))
                .thenReturn("test");
        when(mDevicePolicyResourcesManager.getString(any(), any())).thenReturn("test");
        when(summaryView.getText()).thenReturn("test");

        mHelper.useAdminDisabledSummary(true);
        mHelper.setDisabledByAdmin(null);
        mHelper.onBindViewHolder(mViewHolder);

        verify(summaryView).setText(null);
        verify(summaryView, never()).setVisibility(View.GONE);
    }

    @Test
    public void setDisabledByAdmin_RestrictedPreference_shouldDisablePreference() {
        mHelper.setDisabledByAdmin(new RestrictedLockUtils.EnforcedAdmin());

        verify(mPreference).setEnabled(false);
    }

    @Test
    public void setDisabledByAdmin_TopLevelRestrictedPreference_shouldNotDisablePreference() {
        mHelper = new RestrictedPreferenceHelper(mContext,
                mRestrictedTopLevelPreference, /* attrs= */ null);

        mHelper.setDisabledByAdmin(new RestrictedLockUtils.EnforcedAdmin());

        verify(mRestrictedTopLevelPreference, never()).setEnabled(false);
    }

    /**
     * Tests if the instance of {@link RestrictedLockUtils.EnforcedAdmin} is received by
     * {@link RestrictedPreferenceHelper#setDisabledByAdmin(RestrictedLockUtils.EnforcedAdmin)} as a
     * copy or as a reference.
     */
    @DisableFlags(Flags.FLAG_POLICY_TRANSPARENCY_REFACTOR_ENABLED)
    @Test
    public void setDisabledByAdmin_disablePreference_receivedEnforcedAdminIsNotAReference() {
        RestrictedLockUtils.EnforcedAdmin enforcedAdmin =
                new RestrictedLockUtils.EnforcedAdmin(/* component */ null,
                        /* enforcedRestriction */ "some_restriction",
                        /* userHandle */ null);

        mHelper.setDisabledByAdmin(enforcedAdmin);

        // If `setDisabledByAdmin` stored `enforcedAdmin` as a reference, then the following
        // assignment would be propagated.
        enforcedAdmin.enforcedRestriction = null;
        assertThat(mHelper.mEnforcedAdmin.enforcedRestriction).isEqualTo("some_restriction");
        assertThat(mHelper.isDisabledByAdmin()).isTrue();
    }

    @RequiresFlagsEnabled(android.security.Flags.FLAG_AAPM_API)
    @Test
    public void setDisabledByAdmin_previousAndCurrentAdminsAreTheSame_returnsFalse() {
        RestrictedLockUtils.EnforcedAdmin enforcedAdmin =
                new RestrictedLockUtils.EnforcedAdmin(/* component */ null,
                        /* enforcedRestriction */ "some_restriction", /* userHandle */ null);

        mHelper.setDisabledByAdmin(enforcedAdmin);

        assertThat(mHelper.setDisabledByAdmin(enforcedAdmin)).isFalse();
    }

    @RequiresFlagsEnabled(android.security.Flags.FLAG_AAPM_API)
    @Test
    public void setDisabledByAdmin_previousAndCurrentAdminsAreDifferent_returnsTrue() {
        RestrictedLockUtils.EnforcedAdmin enforcedAdmin1 =
                new RestrictedLockUtils.EnforcedAdmin(/* component */ null,
                        /* enforcedRestriction */ "some_restriction", /* userHandle */ null);
        RestrictedLockUtils.EnforcedAdmin enforcedAdmin2 =
                new RestrictedLockUtils.EnforcedAdmin(new ComponentName("pkg", "cls"),
                        /* enforcedRestriction */ "some_restriction", /* userHandle */ null);

        mHelper.setDisabledByAdmin(enforcedAdmin1);

        assertThat(mHelper.setDisabledByAdmin(enforcedAdmin2)).isTrue();
    }

    @EnableFlags(Flags.FLAG_POLICY_TRANSPARENCY_REFACTOR_ENABLED)
    @Test
    public void setDisabledByEnforcingAdmin_disablePreference() {
        EnforcingAdmin enforcingAdmin = new EnforcingAdmin("package.name",
                DpcAuthority.DPC_AUTHORITY, UserHandle.of(UserHandle.myUserId()), mAdmin);

        mHelper.setDisabledByEnforcingAdmin(enforcingAdmin);

        verify(mPreference).setEnabled(false);
    }

    @RequiresFlagsEnabled(android.security.Flags.FLAG_AAPM_API)
    @Test
    public void isRestrictionEnforcedByAdvancedProtection_notEnforced_returnsFalse() {
        final Authority[] allNonAdvancedProtectionAuthorities = new Authority[] {
                UnknownAuthority.UNKNOWN_AUTHORITY,
                DeviceAdminAuthority.DEVICE_ADMIN_AUTHORITY,
                DpcAuthority.DPC_AUTHORITY,
                new RoleAuthority(Collections.singleton("some-role"))
        };
        final String userRestriction = UserManager.DISALLOW_UNINSTALL_APPS;

        for (Authority authority : allNonAdvancedProtectionAuthorities) {
            final EnforcingAdmin enforcingAdmin = new EnforcingAdmin(mPackage, authority,
                    UserHandle.of(UserHandle.myUserId()), mAdmin);

            when(mDevicePolicyManager.getEnforcingAdmin(UserHandle.myUserId(), userRestriction))
                    .thenReturn(enforcingAdmin);

            mHelper.setDisabledByAdmin(new RestrictedLockUtils.EnforcedAdmin(/* component */ null,
                    userRestriction, UserHandle.of(UserHandle.myUserId())));

            assertWithMessage(authority + " is not an advanced protection authority")
                    .that(mHelper.isRestrictionEnforcedByAdvancedProtection())
                    .isFalse();
        }
    }

    @RequiresFlagsEnabled(android.security.Flags.FLAG_AAPM_API)
    @Test
    public void isRestrictionEnforcedByAdvancedProtection_enforced_returnsTrue() {
        final EnforcingAdmin advancedProtectionEnforcingAdmin = new EnforcingAdmin(mPackage,
                mAdvancedProtectionAuthority, UserHandle.of(UserHandle.myUserId()), mAdmin);
        final String userRestriction = UserManager.DISALLOW_UNINSTALL_APPS;

        when(mDevicePolicyManager.getEnforcingAdmin(UserHandle.myUserId(), userRestriction))
                .thenReturn(advancedProtectionEnforcingAdmin);

        mHelper.setDisabledByAdmin(new RestrictedLockUtils.EnforcedAdmin(/* component */ null,
                userRestriction, UserHandle.of(UserHandle.myUserId())));

        assertThat(mHelper.isRestrictionEnforcedByAdvancedProtection()).isTrue();
    }


    @EnableFlags(Flags.FLAG_POLICY_TRANSPARENCY_REFACTOR_ENABLED)
    @Test
    public void checkRestrictionAndSetDisabled_policyTransparencyRefactorEnabled_disabledByAdmin() {
        final String restriction = "restriction";
        final PolicyEnforcementInfo policyEnforcementInfo = new PolicyEnforcementInfo(
                List.of(new EnforcingAdmin(mPackage, DpcAuthority.DPC_AUTHORITY,
                        UserHandle.SYSTEM)));
        when(mDevicePolicyManager.getEnforcingAdminsForPolicy(
                DevicePolicyIdentifiers.getIdentifierForUserRestriction(restriction),
                UserHandle.myUserId())).thenReturn(
                policyEnforcementInfo);

        mHelper.checkRestrictionAndSetDisabled(restriction, UserHandle.myUserId());

        assertThat(mHelper.isDisabledByAdmin()).isTrue();
    }


    @EnableFlags(Flags.FLAG_POLICY_TRANSPARENCY_REFACTOR_ENABLED)
    @Test
    public void checkAdminRestrictionEnforced_userRestriction_disabledByAdmin() {
        final String restriction = "restriction";
        final EnforcingAdmin admin = new EnforcingAdmin(mPackage, DpcAuthority.DPC_AUTHORITY,
                UserHandle.SYSTEM);
        final PolicyEnforcementInfo policyEnforcementInfo = new PolicyEnforcementInfo(
                List.of(admin));
        when(mDevicePolicyManager.getEnforcingAdminsForPolicy(
                DevicePolicyIdentifiers.getIdentifierForUserRestriction(restriction),
                UserHandle.myUserId())).thenReturn(
                policyEnforcementInfo);
        mHelper.setUserRestriction(restriction);

        assertThat(mHelper.checkAdminRestrictionEnforced()).isEqualTo(admin);
        assertThat(mHelper.isDisabledByAdmin()).isTrue();
    }

    @EnableFlags(Flags.FLAG_POLICY_TRANSPARENCY_REFACTOR_ENABLED)
    @Test
    public void checkAdminRestrictionEnforced_adminPolicy_disabledByAdmin() {
        final String restriction = "restriction";
        final EnforcingAdmin admin = new EnforcingAdmin(mPackage, DpcAuthority.DPC_AUTHORITY,
                UserHandle.SYSTEM);
        final PolicyEnforcementInfo policyEnforcementInfo = new PolicyEnforcementInfo(
                List.of(admin));
        when(mDevicePolicyManager.getEnforcingAdminsForPolicy(restriction,
                UserHandle.myUserId())).thenReturn(policyEnforcementInfo);
        mHelper.setAdminPolicyRestriction(restriction);

        assertThat(mHelper.checkAdminRestrictionEnforced()).isEqualTo(admin);
        assertThat(mHelper.isDisabledByAdmin()).isTrue();
    }

    @EnableFlags(Flags.FLAG_POLICY_TRANSPARENCY_REFACTOR_ENABLED)
    @Test
    public void checkRestrictionEnforced_userRestriction_disabledByAdmin() {
        final String restriction = "restriction";
        final EnforcingAdmin admin = new EnforcingAdmin(mPackage, DpcAuthority.DPC_AUTHORITY,
                UserHandle.SYSTEM, mAdmin);
        final PolicyEnforcementInfo policyEnforcementInfo = new PolicyEnforcementInfo(
                List.of(admin));
        when(mDevicePolicyManager.getEnforcingAdminsForPolicy(
                DevicePolicyIdentifiers.getIdentifierForUserRestriction(restriction),
                UserHandle.myUserId())).thenReturn(
                policyEnforcementInfo);

        mHelper.setUserRestriction(restriction);
        RestrictedLockUtils.EnforcedAdmin expectedAdmin = new RestrictedLockUtils.EnforcedAdmin(
                mAdmin, restriction, UserHandle.of(UserHandle.myUserId()));
        assertThat(mHelper.checkRestrictionEnforced()).isEqualTo(expectedAdmin);
        assertThat(mHelper.isDisabledByAdmin()).isTrue();
    }

    @EnableFlags(Flags.FLAG_POLICY_TRANSPARENCY_REFACTOR_ENABLED)
    @Test
    public void checkRestrictionEnforced_adminPolicy_disabledByAdmin() {
        final String restriction = "restriction";
        final EnforcingAdmin admin = new EnforcingAdmin(mPackage, DpcAuthority.DPC_AUTHORITY,
                UserHandle.SYSTEM, mAdmin);
        final PolicyEnforcementInfo policyEnforcementInfo = new PolicyEnforcementInfo(
                List.of(admin));
        when(mDevicePolicyManager.getEnforcingAdminsForPolicy(
                restriction,
                UserHandle.myUserId())).thenReturn(
                policyEnforcementInfo);

        mHelper.setAdminPolicyRestriction(restriction);
        RestrictedLockUtils.EnforcedAdmin expectedAdmin = new RestrictedLockUtils.EnforcedAdmin(
                mAdmin, restriction, UserHandle.of(UserHandle.myUserId()));
        assertThat(mHelper.checkRestrictionEnforced()).isEqualTo(expectedAdmin);
        assertThat(mHelper.isDisabledByAdmin()).isTrue();
    }

    @EnableFlags(Flags.FLAG_POLICY_TRANSPARENCY_REFACTOR_ENABLED)
    @Test
    public void checkPolicyAndSetDisabled_disabledByAdmin() {
        final String restriction = "restriction";
        final PolicyEnforcementInfo policyEnforcementInfo = new PolicyEnforcementInfo(
                List.of(new EnforcingAdmin(mPackage, DpcAuthority.DPC_AUTHORITY,
                        UserHandle.SYSTEM)));
        when(mDevicePolicyManager.getEnforcingAdminsForPolicy(restriction,
                UserHandle.myUserId())).thenReturn(policyEnforcementInfo);

        mHelper.checkPolicyAndSetDisabled(restriction, UserHandle.myUserId());

        assertThat(mHelper.isDisabledByAdmin()).isTrue();
    }
}
