/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.security.advancedprotection;

import static android.os.UserManager.DISALLOW_CELLULAR_2G;
import static android.os.UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY;
import static android.security.advancedprotection.AdvancedProtectionManager.ACTION_SHOW_ADVANCED_PROTECTION_SUPPORT_DIALOG;
import static android.security.advancedprotection.AdvancedProtectionManager.EXTRA_SUPPORT_DIALOG_FEATURE;
import static android.security.advancedprotection.AdvancedProtectionManager.EXTRA_SUPPORT_DIALOG_TYPE;
import static android.security.advancedprotection.AdvancedProtectionManager.FEATURE_ID_DISALLOW_CELLULAR_2G;
import static android.security.advancedprotection.AdvancedProtectionManager.FEATURE_ID_DISALLOW_INSECURE_WIFI_AUTOJOIN;
import static android.security.advancedprotection.AdvancedProtectionManager.FEATURE_ID_DISALLOW_INSTALL_UNKNOWN_SOURCES;
import static android.security.advancedprotection.AdvancedProtectionManager.FEATURE_ID_DISALLOW_USB;
import static android.security.advancedprotection.AdvancedProtectionManager.FEATURE_ID_DISALLOW_WEP;
import static android.security.advancedprotection.AdvancedProtectionManager.FEATURE_ID_ENABLE_MTE;
import static android.security.advancedprotection.AdvancedProtectionManager.FEATURE_ID_RESTRICT_NON_TOOL_A11Y_SERVICES;
import static android.security.advancedprotection.AdvancedProtectionManager.SUPPORT_DIALOG_TYPE_BLOCKED_INTERACTION;
import static android.security.advancedprotection.AdvancedProtectionManager.SUPPORT_DIALOG_TYPE_DISABLED_SETTING;
import static android.security.advancedprotection.AdvancedProtectionManager.SUPPORT_DIALOG_TYPE_UNKNOWN;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.content.Intent;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.flag.junit.SetFlagsRule;
import android.security.Flags;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class AdvancedProtectionManagerTest {
    private static final int FEATURE_ID_INVALID = -1;
    private static final int SUPPORT_DIALOG_TYPE_INVALID = -1;
    //TODO(b/378931989): Switch to android.app.admin.DevicePolicyIdentifiers.MEMORY_TAGGING_POLICY
    //when the appropriate flag is launched.
    private static final String MEMORY_TAGGING_POLICY = "memoryTagging";

    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Test
    public void testCreateSupportIntent_validFeature_validTypeUnknown_createsIntent() {
        Intent intent = AdvancedProtectionManager.createSupportIntent(
                FEATURE_ID_DISALLOW_CELLULAR_2G, SUPPORT_DIALOG_TYPE_UNKNOWN);

        assertEquals(ACTION_SHOW_ADVANCED_PROTECTION_SUPPORT_DIALOG, intent.getAction());
        assertEquals(FEATURE_ID_DISALLOW_CELLULAR_2G, intent.getIntExtra(
                EXTRA_SUPPORT_DIALOG_FEATURE, FEATURE_ID_INVALID));
        assertEquals(SUPPORT_DIALOG_TYPE_UNKNOWN, intent.getIntExtra(EXTRA_SUPPORT_DIALOG_TYPE,
                SUPPORT_DIALOG_TYPE_INVALID));
    }

    @Test
    public void testCreateSupportIntent_validFeature_validTypeBlockedInteraction_createsIntent() {
        Intent intent = AdvancedProtectionManager.createSupportIntent(
                FEATURE_ID_DISALLOW_CELLULAR_2G, SUPPORT_DIALOG_TYPE_BLOCKED_INTERACTION);

        assertEquals(ACTION_SHOW_ADVANCED_PROTECTION_SUPPORT_DIALOG, intent.getAction());
        assertEquals(FEATURE_ID_DISALLOW_CELLULAR_2G, intent.getIntExtra(
                EXTRA_SUPPORT_DIALOG_FEATURE, FEATURE_ID_INVALID));
        assertEquals(SUPPORT_DIALOG_TYPE_BLOCKED_INTERACTION, intent.getIntExtra(
                EXTRA_SUPPORT_DIALOG_TYPE, SUPPORT_DIALOG_TYPE_INVALID));
    }

    @Test
    public void testCreateSupportIntent_validFeature_validTypeDisabledSetting_createsIntent() {
        Intent intent = AdvancedProtectionManager.createSupportIntent(
                FEATURE_ID_DISALLOW_CELLULAR_2G, SUPPORT_DIALOG_TYPE_DISABLED_SETTING);

        assertEquals(ACTION_SHOW_ADVANCED_PROTECTION_SUPPORT_DIALOG, intent.getAction());
        assertEquals(FEATURE_ID_DISALLOW_CELLULAR_2G, intent.getIntExtra(
                EXTRA_SUPPORT_DIALOG_FEATURE, FEATURE_ID_INVALID));
        assertEquals(SUPPORT_DIALOG_TYPE_DISABLED_SETTING, intent.getIntExtra(
                EXTRA_SUPPORT_DIALOG_TYPE, SUPPORT_DIALOG_TYPE_INVALID));
    }

    @Test
    public void testCreateSupportIntent_validFeature_invalidType_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () ->
                AdvancedProtectionManager.createSupportIntent(FEATURE_ID_DISALLOW_CELLULAR_2G,
                        SUPPORT_DIALOG_TYPE_INVALID));
    }

    @Test
    public void testCreateSupportIntent_invalidFeature_validType_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () ->
                AdvancedProtectionManager.createSupportIntent(FEATURE_ID_INVALID,
                        SUPPORT_DIALOG_TYPE_BLOCKED_INTERACTION));
    }

    @Test
    public void testCreateSupportIntent_invalidFeature_invalidType_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () ->
                AdvancedProtectionManager.createSupportIntent(FEATURE_ID_INVALID,
                        SUPPORT_DIALOG_TYPE_INVALID));
    }

    @Test
    public void testCreateSupportIntentForPolicy_2g_typeUnknown_createsIntentForDisabledSetting() {
        Intent intent = AdvancedProtectionManager
                .createSupportIntentForPolicyIdentifierOrRestriction(
                        DISALLOW_CELLULAR_2G, SUPPORT_DIALOG_TYPE_UNKNOWN);

        assertEquals(ACTION_SHOW_ADVANCED_PROTECTION_SUPPORT_DIALOG, intent.getAction());
        assertEquals(FEATURE_ID_DISALLOW_CELLULAR_2G, intent.getIntExtra(
                EXTRA_SUPPORT_DIALOG_FEATURE, FEATURE_ID_INVALID));
        assertEquals(SUPPORT_DIALOG_TYPE_DISABLED_SETTING, intent.getIntExtra(
                EXTRA_SUPPORT_DIALOG_TYPE, SUPPORT_DIALOG_TYPE_INVALID));
    }

    @Test
    public void testCreateSupportIntentForPolicy_mte_typeUnknown_createsIntentForDisabledSetting() {
        Intent intent = AdvancedProtectionManager
                .createSupportIntentForPolicyIdentifierOrRestriction(
                        MEMORY_TAGGING_POLICY, SUPPORT_DIALOG_TYPE_UNKNOWN);

        assertEquals(ACTION_SHOW_ADVANCED_PROTECTION_SUPPORT_DIALOG, intent.getAction());
        assertEquals(FEATURE_ID_ENABLE_MTE, intent.getIntExtra(
                EXTRA_SUPPORT_DIALOG_FEATURE, FEATURE_ID_INVALID));
        assertEquals(SUPPORT_DIALOG_TYPE_DISABLED_SETTING, intent.getIntExtra(
                EXTRA_SUPPORT_DIALOG_TYPE, SUPPORT_DIALOG_TYPE_INVALID));
    }

    @Test
    public void
            testCreateSupportIntentForPolicy_unknownSources_typeUnknown_createsIntentForUnknown() {
        Intent intent = AdvancedProtectionManager
                .createSupportIntentForPolicyIdentifierOrRestriction(
                        DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY, SUPPORT_DIALOG_TYPE_UNKNOWN);

        assertEquals(ACTION_SHOW_ADVANCED_PROTECTION_SUPPORT_DIALOG, intent.getAction());
        assertEquals(FEATURE_ID_DISALLOW_INSTALL_UNKNOWN_SOURCES, intent.getIntExtra(
                EXTRA_SUPPORT_DIALOG_FEATURE, FEATURE_ID_INVALID));
        assertEquals(SUPPORT_DIALOG_TYPE_UNKNOWN, intent.getIntExtra(
                EXTRA_SUPPORT_DIALOG_TYPE, SUPPORT_DIALOG_TYPE_INVALID));
    }

    @Test
    @EnableFlags(Flags.FLAG_AAPM_FEATURE_DISABLE_INSECURE_WIFI_AUTOJOIN)
    public void testCreateSupportIntent_insecureWifiAutojoinFlagEnabled_createsIntent() {
        Intent intent = AdvancedProtectionManager.createSupportIntent(
                FEATURE_ID_DISALLOW_INSECURE_WIFI_AUTOJOIN, SUPPORT_DIALOG_TYPE_UNKNOWN);

        assertEquals(ACTION_SHOW_ADVANCED_PROTECTION_SUPPORT_DIALOG, intent.getAction());
        assertEquals(FEATURE_ID_DISALLOW_INSECURE_WIFI_AUTOJOIN, intent.getIntExtra(
                EXTRA_SUPPORT_DIALOG_FEATURE, FEATURE_ID_INVALID));
        assertEquals(SUPPORT_DIALOG_TYPE_UNKNOWN, intent.getIntExtra(EXTRA_SUPPORT_DIALOG_TYPE,
                SUPPORT_DIALOG_TYPE_INVALID));
    }

    @Test
    @DisableFlags(Flags.FLAG_AAPM_FEATURE_DISABLE_INSECURE_WIFI_AUTOJOIN)
    public void testCreateSupportIntent_insecureWifiAutojoinFlagDisabled_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () ->
                AdvancedProtectionManager.createSupportIntent(
                        FEATURE_ID_DISALLOW_INSECURE_WIFI_AUTOJOIN, SUPPORT_DIALOG_TYPE_UNKNOWN));
    }

    @Test
    public void featureIdToString_validId_returnsCorrectString() {
        assertEquals("DISALLOW_CELLULAR_2G",
                AdvancedProtectionManager.featureIdToString(FEATURE_ID_DISALLOW_CELLULAR_2G));
        assertEquals("DISALLOW_INSTALL_UNKNOWN_SOURCES",
                AdvancedProtectionManager.featureIdToString(
                        FEATURE_ID_DISALLOW_INSTALL_UNKNOWN_SOURCES));
        assertEquals("DISALLOW_USB",
                AdvancedProtectionManager.featureIdToString(FEATURE_ID_DISALLOW_USB));
        assertEquals("DISALLOW_WEP",
                AdvancedProtectionManager.featureIdToString(FEATURE_ID_DISALLOW_WEP));
        assertEquals("ENABLE_MTE",
                AdvancedProtectionManager.featureIdToString(FEATURE_ID_ENABLE_MTE));
        assertEquals("DISALLOW_NON_TOOL_ACCESSIBILITY_SERVICES",
                AdvancedProtectionManager.featureIdToString(
                        FEATURE_ID_RESTRICT_NON_TOOL_A11Y_SERVICES));
    }

    @Test
    @EnableFlags(Flags.FLAG_AAPM_FEATURE_DISABLE_INSECURE_WIFI_AUTOJOIN)
    public void featureIdToString_insecureWifiAutojoinFlagEnabled_returnsCorrectString() {
        assertEquals("DISALLOW_INSECURE_WIFI_AUTOJOIN",
                AdvancedProtectionManager.featureIdToString(
                        FEATURE_ID_DISALLOW_INSECURE_WIFI_AUTOJOIN));
    }


    @Test
    @DisableFlags(Flags.FLAG_AAPM_FEATURE_DISABLE_INSECURE_WIFI_AUTOJOIN)
    public void featureIdToString_insecureWifiAutojoinFlagDisabled_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> AdvancedProtectionManager.featureIdToString(
                        FEATURE_ID_DISALLOW_INSECURE_WIFI_AUTOJOIN));
    }

    @Test
    @EnableFlags(Flags.FLAG_EXTEND_AAPM_TO_A11Y_SERVICES)
    public void featureIdToString_extendAAPMToA11yServicesFlagEnabled_returnsCorrectString() {
        assertEquals("DISALLOW_NON_TOOL_ACCESSIBILITY_SERVICES",
                AdvancedProtectionManager.featureIdToString(
                        FEATURE_ID_RESTRICT_NON_TOOL_A11Y_SERVICES));
    }

    @Test
    @DisableFlags(Flags.FLAG_EXTEND_AAPM_TO_A11Y_SERVICES)
    public void featureIdToString_extendAAPMToA11yServicesFlagDisabled_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> AdvancedProtectionManager.featureIdToString(
                        FEATURE_ID_RESTRICT_NON_TOOL_A11Y_SERVICES));
    }

    @Test
    public void featureIdToString_invalidId_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> AdvancedProtectionManager.featureIdToString(FEATURE_ID_INVALID));
    }

    @Test
    public void featureStringToId_validString_returnsCorrectId() {
        assertEquals(FEATURE_ID_DISALLOW_CELLULAR_2G,
                AdvancedProtectionManager.featureStringToId("DISALLOW_CELLULAR_2G"));
        assertEquals(FEATURE_ID_DISALLOW_INSTALL_UNKNOWN_SOURCES,
                AdvancedProtectionManager.featureStringToId("DISALLOW_INSTALL_UNKNOWN_SOURCES"));
        assertEquals(FEATURE_ID_DISALLOW_USB,
                AdvancedProtectionManager.featureStringToId("DISALLOW_USB"));
        assertEquals(FEATURE_ID_DISALLOW_WEP,
                AdvancedProtectionManager.featureStringToId("DISALLOW_WEP"));
        assertEquals(FEATURE_ID_ENABLE_MTE,
                AdvancedProtectionManager.featureStringToId("ENABLE_MTE"));
        assertEquals(FEATURE_ID_RESTRICT_NON_TOOL_A11Y_SERVICES,
                AdvancedProtectionManager.featureStringToId(
                        "DISALLOW_NON_TOOL_ACCESSIBILITY_SERVICES"));
    }

    @Test
    @EnableFlags(Flags.FLAG_AAPM_FEATURE_DISABLE_INSECURE_WIFI_AUTOJOIN)
    public void featureStringToId_insecureWifiAutojoinFlagEnabled_returnsCorrectId() {
        assertEquals(FEATURE_ID_DISALLOW_INSECURE_WIFI_AUTOJOIN,
                AdvancedProtectionManager.featureStringToId("DISALLOW_INSECURE_WIFI_AUTOJOIN"));
    }

    @Test
    @DisableFlags(Flags.FLAG_AAPM_FEATURE_DISABLE_INSECURE_WIFI_AUTOJOIN)
    public void featureStringToId_insecureWifiAutojoinFlagDisabled_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> AdvancedProtectionManager.featureStringToId(
                        "DISALLOW_INSECURE_WIFI_AUTOJOIN"));
    }

    @Test
    public void featureStringToId_invalidString_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> AdvancedProtectionManager.featureStringToId("INVALID_STRING"));
    }

    @Test
    public void featureStringToId_nullString_throwsNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> AdvancedProtectionManager.featureStringToId(null));
    }

    @Test
    @EnableFlags(Flags.FLAG_EXTEND_AAPM_TO_A11Y_SERVICES)
    public void testCreateSupportIntent_restrictNonToolA11yServices_createsIntent() {
        Intent intent = AdvancedProtectionManager.createSupportIntent(
                FEATURE_ID_RESTRICT_NON_TOOL_A11Y_SERVICES, SUPPORT_DIALOG_TYPE_UNKNOWN);

        assertEquals(ACTION_SHOW_ADVANCED_PROTECTION_SUPPORT_DIALOG, intent.getAction());
        assertEquals(FEATURE_ID_RESTRICT_NON_TOOL_A11Y_SERVICES, intent.getIntExtra(
                EXTRA_SUPPORT_DIALOG_FEATURE, FEATURE_ID_INVALID));
        assertEquals(SUPPORT_DIALOG_TYPE_UNKNOWN, intent.getIntExtra(EXTRA_SUPPORT_DIALOG_TYPE,
                SUPPORT_DIALOG_TYPE_INVALID));
    }

    @Test
    @DisableFlags(Flags.FLAG_EXTEND_AAPM_TO_A11Y_SERVICES)
    public void testCreateSupportIntent_restrictA11yServicesFlagDisabled_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> AdvancedProtectionManager.createSupportIntent(
                        FEATURE_ID_RESTRICT_NON_TOOL_A11Y_SERVICES, SUPPORT_DIALOG_TYPE_INVALID));
    }

    @Test
    @EnableFlags(Flags.FLAG_EXTEND_AAPM_TO_A11Y_SERVICES)
    public void featureIdToString_restrictA11yServicesFlagEnabled_returnsCorrectString() {
        assertEquals("DISALLOW_NON_TOOL_ACCESSIBILITY_SERVICES",
                AdvancedProtectionManager.featureIdToString(
                        FEATURE_ID_RESTRICT_NON_TOOL_A11Y_SERVICES));
    }
}
