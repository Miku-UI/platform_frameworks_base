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

package com.android.server.pm;

import static android.content.pm.UserInfo.FLAG_DEMO;
import static android.content.pm.UserInfo.FLAG_EPHEMERAL;
import static android.content.pm.UserInfo.FLAG_FULL;
import static android.content.pm.UserInfo.FLAG_GUEST;
import static android.content.pm.UserInfo.FLAG_MANAGED_PROFILE;
import static android.content.pm.UserInfo.FLAG_PROFILE;
import static android.content.pm.UserInfo.FLAG_RESTRICTED;
import static android.content.pm.UserInfo.FLAG_SYSTEM;
import static android.os.UserManager.USER_TYPE_SYSTEM_HEADLESS;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeTrue;
import static org.testng.Assert.assertThrows;

import android.content.pm.UserInfo;
import android.content.pm.UserProperties;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.os.Bundle;
import android.os.UserManager;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.Presubmit;
import android.platform.test.flag.junit.SetFlagsRule;
import android.util.ArrayMap;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.frameworks.servicestests.R;

import com.google.common.truth.Expect;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

/**
 * Tests for {@link UserTypeDetails} and {@link UserTypeFactory}.
 *
 * <p>Run with: atest UserManagerServiceUserTypeTest
 */
@Presubmit
@RunWith(AndroidJUnit4.class)
@MediumTest
public class UserManagerServiceUserTypeTest {

    @Rule
    public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    @Rule
    public final Expect expect = Expect.create();

    private Resources mResources;

    @Before
    public void setup() {
        mResources = InstrumentationRegistry.getTargetContext().getResources();
    }

    @Test
    @EnableFlags(android.multiuser.Flags.FLAG_HSU_APP_MANAGEMENT)
    public void testUserTypeBuilder_systemHeadless_hsuAppManagementEnabled() throws Exception {
        assumeTrue("Feature supported only on Headless System User Mode devices",
                UserManager.isHeadlessSystemUserMode());

        UserTypeDetails type = UserTypeFactory.getUserTypes().get(USER_TYPE_SYSTEM_HEADLESS);

        expect.withMessage("hasBadge()").that(type.hasBadge()).isTrue();
        expect.withMessage("getIconBadge()").that(type.getIconBadge())
                .isEqualTo(com.android.internal.R.drawable.ic_hsu_icon_badge);
        expect.withMessage("getBadgePlain()").that(type.getBadgePlain())
                .isEqualTo(com.android.internal.R.drawable.ic_hsu_badge);
        expect.withMessage("getBadgeNoBackground()").that(type.getBadgeNoBackground())
                .isEqualTo(com.android.internal.R.drawable.ic_hsu_badge_no_background);
        expect.withMessage("getBadgeLabel(0)").that(type.getBadgeLabel(0))
                .isEqualTo(com.android.internal.R.string.hsu_label_badge);
    }

    @Test
    @DisableFlags(android.multiuser.Flags.FLAG_HSU_APP_MANAGEMENT)
    public void testUserTypeBuilder_systemHeadless_hsuAppManagementDisabled_flagOff()
            throws Exception {
        assumeTrue("Feature supported only on Headless System User Mode devices",
                UserManager.isHeadlessSystemUserMode());

        UserTypeDetails type = UserTypeFactory.getUserTypes().get(USER_TYPE_SYSTEM_HEADLESS);

        expect.that(type.hasBadge()).isFalse();
    }

    @Test
    @EnableFlags(android.multiuser.Flags.FLAG_HSU_ALLOWLIST_ACTIVITIES)
    public void testUserTypeBuilder_systemHeadless_hsuActivitiesAllowlistResourceAndMode()
            throws Exception {
        assumeTrue("Feature supported only on Headless System User Mode devices",
                UserManager.isHeadlessSystemUserMode());

        UserTypeDetails type = UserTypeFactory.getUserTypes().get(USER_TYPE_SYSTEM_HEADLESS);

        expect.withMessage("getActivitiesAllowlist()").that(type.getActivitiesAllowlist())
                .isEqualTo(com.android.internal.R.array.hsu_allowlist_activities);
        expect.withMessage("getActivitiesAllowlistMode()").that(type.getActivitiesAllowlistMode())
                .isEqualTo(Resources.getSystem().getInteger(
                        com.android.internal.R.integer.config_hsuActivitiesAllowlistMode));
    }

    @Test
    public void testUserTypeBuilder_createUserType() {
        final Bundle restrictions = makeRestrictionsBundle("r1", "r2");
        final Bundle systemSettings = makeSettingsBundle("s1", "s2");
        final Bundle secureSettings = makeSettingsBundle("secure_s1", "secure_s2");
        final List<DefaultCrossProfileIntentFilter> filters = List.of(
                new DefaultCrossProfileIntentFilter.Builder(
                DefaultCrossProfileIntentFilter.Direction.TO_PARENT,
                /* flags= */0,
                /* letsPersonalDataIntoProfile= */false).build());
        final UserProperties.Builder userProps = new UserProperties.Builder()
                .setShowInLauncher(17)
                .setUseParentsContacts(true)
                .setCrossProfileIntentFilterAccessControl(10)
                .setCrossProfileIntentResolutionStrategy(1)
                .setMediaSharedWithParent(true)
                .setCredentialShareableWithParent(false)
                .setAuthAlwaysRequiredToDisableQuietMode(true)
                .setAllowStoppingUserWithDelayedLocking(true)
                .setShowInSettings(900)
                .setShowInSharingSurfaces(20)
                .setShowInQuietMode(30)
                .setInheritDevicePolicy(340)
                .setDeleteAppWithParent(true)
                .setAlwaysVisible(true)
                .setCrossProfileContentSharingStrategy(1)
                .setProfileApiVisibility(34)
                .setItemsRestrictedOnHomeScreen(true);


        final int activitiesAllowlistResId = 42;
        final UserTypeDetails type = new UserTypeDetails.Builder()
                .setName("a.name")
                .setEnabled(1)
                .setMaxAllowed(21)
                .setBaseType(FLAG_PROFILE)
                .setDefaultUserInfoPropertyFlags(FLAG_EPHEMERAL)
                .setProfileParentRequired(true)
                .setBadgeLabels(23, 24, 25)
                .setBadgeColors(26, 27)
                .setIconBadge(28)
                .setBadgePlain(29)
                .setBadgeNoBackground(30)
                .setMaxAllowedPerParent(32)
                .setStatusBarIcon(33)
                .setLabels(34, 35, 36)
                .setDefaultRestrictions(restrictions)
                .setDefaultSystemSettings(systemSettings)
                .setDefaultSecureSettings(secureSettings)
                .setDefaultCrossProfileIntentFilters(filters)
                .setDefaultUserProperties(userProps)
                .setActivitiesAllowlist(activitiesAllowlistResId)
                .createUserTypeDetails();

        expect.withMessage("getName()").that(type.getName()).isEqualTo("a.name");
        expect.withMessage("isEnabled()").that(type.isEnabled()).isTrue();
        expect.withMessage("getMaxAllowed()").that(type.getMaxAllowed()).isEqualTo(21);
        expect.withMessage("getDefaultUserInfoFlags()").that(type.getDefaultUserInfoFlags())
                .isEqualTo(FLAG_PROFILE | FLAG_EPHEMERAL);
        expect.withMessage("isProfileParentRequired()")
                .that(type.isProfileParentRequired()).isTrue();
        expect.withMessage("getIconBadge()").that(type.getIconBadge()).isEqualTo(28);
        expect.withMessage("getBadgePlain()").that(type.getBadgePlain()).isEqualTo(29);
        expect.withMessage("getBadgeNoBackground()")
                .that(type.getBadgeNoBackground()).isEqualTo(30);
        expect.withMessage("getMaxAllowedPerParent()")
                .that(type.getMaxAllowedPerParent()).isEqualTo(32);
        expect.withMessage("getStatusBarIcon()").that(type.getStatusBarIcon()).isEqualTo(33);
        expect.withMessage("getLabel(0)").that(type.getLabel(0)).isEqualTo(34);
        expect.withMessage("getLabel(1)").that(type.getLabel(1)).isEqualTo(35);
        expect.withMessage("getLabel(2)").that(type.getLabel(2)).isEqualTo(36);

        expect.withMessage("getDefaultRestrictions() equality")
                .that(UserRestrictionsUtils.areEqual(restrictions, type.getDefaultRestrictions()))
                .isTrue();
        expect.withMessage("getDefaultRestrictions() identity")
                .that(type.getDefaultRestrictions()).isNotSameInstanceAs(restrictions);

        expect.withMessage("getDefaultSystemSettings() identity")
                .that(type.getDefaultSystemSettings()).isNotSameInstanceAs(systemSettings);
        expect.withMessage("getDefaultSystemSettings() size")
                .that(type.getDefaultSystemSettings().size()).isEqualTo(systemSettings.size());
        for (String key : systemSettings.keySet()) {
            expect.withMessage("getDefaultSystemSettings() key " + key)
                    .that(type.getDefaultSystemSettings().getString(key))
                    .isEqualTo(systemSettings.getString(key));
        }

        expect.withMessage("getDefaultSecureSettings() identity")
                .that(type.getDefaultSecureSettings()).isNotSameInstanceAs(secureSettings);
        expect.withMessage("getDefaultSecureSettings() size")
                .that(type.getDefaultSecureSettings().size()).isEqualTo(secureSettings.size());
        for (String key : secureSettings.keySet()) {
            expect.withMessage("getDefaultSecureSettings() key " + key)
                    .that(type.getDefaultSecureSettings().getString(key))
                    .isEqualTo(secureSettings.getString(key));
        }

        expect.withMessage("getDefaultCrossProfileIntentFilters() identity")
                .that(type.getDefaultCrossProfileIntentFilters()).isNotSameInstanceAs(filters);
        expect.withMessage("getDefaultCrossProfileIntentFilters() size")
                .that(type.getDefaultCrossProfileIntentFilters().size()).isEqualTo(filters.size());
        for (int i = 0; i < filters.size(); i++) {
            expect.withMessage("getDefaultCrossProfileIntentFilters() index " + i)
                    .that(type.getDefaultCrossProfileIntentFilters().get(i))
                    .isEqualTo(filters.get(i));
        }

        expect.withMessage("getShowInLauncher()")
                .that(type.getDefaultUserPropertiesReference().getShowInLauncher()).isEqualTo(17);
        expect.withMessage("getUseParentsContacts()")
                .that(type.getDefaultUserPropertiesReference().getUseParentsContacts()).isTrue();
        expect.withMessage("getCrossProfileIntentFilterAccessControl()")
                .that(type.getDefaultUserPropertiesReference()
                .getCrossProfileIntentFilterAccessControl()).isEqualTo(10);
        expect.withMessage("getCrossProfileIntentResolutionStrategy()")
                .that(type.getDefaultUserPropertiesReference()
                .getCrossProfileIntentResolutionStrategy()).isEqualTo(1);
        expect.withMessage("isMediaSharedWithParent()")
                .that(type.getDefaultUserPropertiesReference().isMediaSharedWithParent()).isTrue();
        expect.withMessage("isCredentialShareableWithParent()")
                .that(type.getDefaultUserPropertiesReference().isCredentialShareableWithParent())
                .isFalse();
        expect.withMessage("isAuthAlwaysRequiredToDisableQuietMode()")
                .that(type.getDefaultUserPropertiesReference()
                .isAuthAlwaysRequiredToDisableQuietMode()).isTrue();
        expect.withMessage("getAllowStoppingUserWithDelayedLocking()")
                .that(type.getDefaultUserPropertiesReference()
                .getAllowStoppingUserWithDelayedLocking()).isTrue();
        expect.withMessage("getShowInSettings()")
                .that(type.getDefaultUserPropertiesReference().getShowInSettings()).isEqualTo(900);
        expect.withMessage("getShowInSharingSurfaces()")
                .that(type.getDefaultUserPropertiesReference().getShowInSharingSurfaces())
                .isEqualTo(20);
        expect.withMessage("getShowInQuietMode()")
                .that(type.getDefaultUserPropertiesReference().getShowInQuietMode()).isEqualTo(30);
        expect.withMessage("getInheritDevicePolicy()").that(type.getDefaultUserPropertiesReference()
                .getInheritDevicePolicy()).isEqualTo(340);
        expect.withMessage("getDeleteAppWithParent()")
                .that(type.getDefaultUserPropertiesReference().getDeleteAppWithParent()).isTrue();
        expect.withMessage("getAlwaysVisible()")
                .that(type.getDefaultUserPropertiesReference().getAlwaysVisible()).isTrue();
        expect.withMessage("getCrossProfileContentSharingStrategy()")
                .that(type.getDefaultUserPropertiesReference()
                .getCrossProfileContentSharingStrategy()).isEqualTo(1);
        expect.withMessage("getProfileApiVisibility()")
                .that(type.getDefaultUserPropertiesReference().getProfileApiVisibility())
                .isEqualTo(34);
        expect.withMessage("areItemsRestrictedOnHomeScreen()")
                .that(type.getDefaultUserPropertiesReference().areItemsRestrictedOnHomeScreen())
                .isTrue();

        expect.withMessage("getBadgeLabel(0)").that(type.getBadgeLabel(0)).isEqualTo(23);
        expect.withMessage("getBadgeLabel(1)").that(type.getBadgeLabel(1)).isEqualTo(24);
        expect.withMessage("getBadgeLabel(2)").that(type.getBadgeLabel(2)).isEqualTo(25);
        expect.withMessage("getBadgeLabel(3)").that(type.getBadgeLabel(3)).isEqualTo(25);
        expect.withMessage("getBadgeLabel(4)").that(type.getBadgeLabel(4)).isEqualTo(25);
        expect.withMessage("getBadgeLabel(-1)")
                .that(type.getBadgeLabel(-1)).isEqualTo(Resources.ID_NULL);

        expect.withMessage("getBadgeColor(0)").that(type.getBadgeColor(0)).isEqualTo(26);
        expect.withMessage("getBadgeColor(1)").that(type.getBadgeColor(1)).isEqualTo(27);
        expect.withMessage("getBadgeColor(2)").that(type.getBadgeColor(2)).isEqualTo(27);
        expect.withMessage("getBadgeColor(3)").that(type.getBadgeColor(3)).isEqualTo(27);
        expect.withMessage("getBadgeColor(-100)")
                .that(type.getBadgeColor(-100)).isEqualTo(Resources.ID_NULL);

        expect.withMessage("hasBadge()").that(type.hasBadge()).isTrue();

        expect.withMessage("getActivitiesAllowlist()")
                .that(type.getActivitiesAllowlist()).isEqualTo(activitiesAllowlistResId);
    }

    @Test
    public void testUserTypeBuilder_defaults() {
        UserTypeDetails type = getMinimalBuilder().createUserTypeDetails();

        expect.withMessage("isEnabled()").that(type.isEnabled()).isTrue();
        expect.withMessage("getMaxAllowed()").that(type.getMaxAllowed()).isEqualTo(0);
        expect.withMessage("getMaxAllowedPerParent()")
                .that(type.getMaxAllowedPerParent()).isEqualTo(0);
        expect.withMessage("getDefaultUserInfoFlags()")
                .that(type.getDefaultUserInfoFlags()).isEqualTo(FLAG_FULL);
        expect.withMessage("getIconBadge()")
                .that(type.getIconBadge()).isEqualTo(Resources.ID_NULL);
        expect.withMessage("getBadgePlain()")
                .that(type.getBadgePlain()).isEqualTo(Resources.ID_NULL);
        expect.withMessage("getBadgeNoBackground()")
                .that(type.getBadgeNoBackground()).isEqualTo(Resources.ID_NULL);
        expect.withMessage("getStatusBarIcon()")
                .that(type.getStatusBarIcon()).isEqualTo(Resources.ID_NULL);
        expect.withMessage("getBadgeLabel(0)")
                .that(type.getBadgeLabel(0)).isEqualTo(Resources.ID_NULL);
        expect.withMessage("getBadgeColor(0)")
                .that(type.getBadgeColor(0)).isEqualTo(Resources.ID_NULL);
        expect.withMessage("getLabel(0)").that(type.getLabel(0)).isEqualTo(Resources.ID_NULL);
        expect.withMessage("getDefaultRestrictions() is empty")
                .that(type.getDefaultRestrictions().isEmpty()).isTrue();
        expect.withMessage("getDefaultSystemSettings() is empty")
                .that(type.getDefaultSystemSettings().isEmpty()).isTrue();
        expect.withMessage("getDefaultSecureSettings() is empty")
                .that(type.getDefaultSecureSettings().isEmpty()).isTrue();
        expect.withMessage("getDefaultCrossProfileIntentFilters() is empty")
                .that(type.getDefaultCrossProfileIntentFilters()).isEmpty();

        final UserProperties props = type.getDefaultUserPropertiesReference();
        assertNotNull(props);
        expect.withMessage("getStartWithParent()").that(props.getStartWithParent()).isFalse();
        expect.withMessage("getUseParentsContacts()").that(props.getUseParentsContacts()).isFalse();
        expect.withMessage("getCrossProfileIntentFilterAccessControl()")
                .that(props.getCrossProfileIntentFilterAccessControl())
                .isEqualTo(UserProperties.CROSS_PROFILE_INTENT_FILTER_ACCESS_LEVEL_ALL);
        expect.withMessage("getShowInLauncher()").that(props.getShowInLauncher())
                .isEqualTo(UserProperties.SHOW_IN_LAUNCHER_WITH_PARENT);
        expect.withMessage("getCrossProfileIntentResolutionStrategy()")
                .that(props.getCrossProfileIntentResolutionStrategy())
                .isEqualTo(UserProperties.CROSS_PROFILE_INTENT_RESOLUTION_STRATEGY_DEFAULT);
        expect.withMessage("isMediaSharedWithParent()")
                .that(props.isMediaSharedWithParent()).isFalse();
        expect.withMessage("isCredentialShareableWithParent()")
                .that(props.isCredentialShareableWithParent()).isFalse();
        expect.withMessage("getDeleteAppWithParent()")
                .that(props.getDeleteAppWithParent()).isFalse();
        expect.withMessage("getAlwaysVisible()").that(props.getAlwaysVisible()).isFalse();
        expect.withMessage("getShowInSharingSurfaces()").that(props.getShowInSharingSurfaces())
                .isEqualTo(UserProperties.SHOW_IN_LAUNCHER_SEPARATE);
        expect.withMessage("getShowInQuietMode()").that(props.getShowInQuietMode())
                .isEqualTo(UserProperties.SHOW_IN_QUIET_MODE_PAUSED);
        expect.withMessage("getCrossProfileContentSharingStrategy()")
                .that(props.getCrossProfileContentSharingStrategy())
                .isEqualTo(UserProperties.CROSS_PROFILE_CONTENT_SHARING_NO_DELEGATION);
        expect.withMessage("getProfileApiVisibility()")
                .that(props.getProfileApiVisibility())
                .isEqualTo(UserProperties.PROFILE_API_VISIBILITY_VISIBLE);

        expect.withMessage("hasBadge()").that(type.hasBadge()).isFalse();
        expect.withMessage("getActivitiesAllowlist()")
                .that(type.getActivitiesAllowlist()).isEqualTo(Resources.ID_NULL);
    }

    @Test
    public void testUserTypeBuilder_nameIsRequired() {
        assertThrows(IllegalArgumentException.class,
                () -> new UserTypeDetails.Builder()
                        .setMaxAllowed(21)
                        .setBaseType(FLAG_FULL)
                        .createUserTypeDetails());
    }

    @Test
    public void testUserTypeBuilder_baseTypeIsRequired() {
        assertThrows(IllegalArgumentException.class,
                () -> new UserTypeDetails.Builder()
                        .setName("name")
                        .createUserTypeDetails());
    }

    @Test
    public void testUserTypeBuilder_colorIsRequiredIfBadged() {
        assertThrows(IllegalArgumentException.class,
                () -> getMinimalBuilder()
                        .setIconBadge(1)
                        .setBadgeLabels(2)
                        .createUserTypeDetails());
    }

    @Test
    public void testUserTypeBuilder_badgeLabelIsRequiredIfBadged() {
        assertThrows(IllegalArgumentException.class,
                () -> getMinimalBuilder()
                        .setIconBadge(1)
                        .setBadgeColors(2)
                        .createUserTypeDetails());
    }

    @Test
    public void testCheckUserTypeConsistency() {
        expect.withMessage("FLAG_GUEST")
                .that(UserManagerService.checkUserTypeConsistency(FLAG_GUEST)).isTrue();
        expect.withMessage("FLAG_GUEST | FLAG_EPHEMERAL")
                .that(UserManagerService.checkUserTypeConsistency(FLAG_GUEST | FLAG_EPHEMERAL))
                .isTrue();
        expect.withMessage("FLAG_PROFILE")
                .that(UserManagerService.checkUserTypeConsistency(FLAG_PROFILE)).isTrue();

        expect.withMessage("FLAG_DEMO | FLAG_RESTRICTED")
                .that(UserManagerService.checkUserTypeConsistency(FLAG_DEMO | FLAG_RESTRICTED))
                .isFalse();
        expect.withMessage("FLAG_PROFILE | FLAG_SYSTEM")
                .that(UserManagerService.checkUserTypeConsistency(FLAG_PROFILE | FLAG_SYSTEM))
                .isFalse();
        expect.withMessage("FLAG_PROFILE | FLAG_FULL")
                .that(UserManagerService.checkUserTypeConsistency(FLAG_PROFILE | FLAG_FULL))
                .isFalse();
    }

    @Test
    public void testGetDefaultUserType() {
        // Simple example.
        expect.withMessage("USER_TYPE_FULL_RESTRICTED")
                .that(UserInfo.getDefaultUserType(FLAG_RESTRICTED))
                .isEqualTo(UserManager.USER_TYPE_FULL_RESTRICTED);

        // Type plus a non-type flag.
        expect.withMessage("USER_TYPE_FULL_GUEST")
                .that(UserInfo.getDefaultUserType(FLAG_GUEST | FLAG_EPHEMERAL))
                .isEqualTo(UserManager.USER_TYPE_FULL_GUEST);

        // Two types, which is illegal.
        assertThrows(IllegalArgumentException.class,
                () -> UserInfo.getDefaultUserType(FLAG_MANAGED_PROFILE | FLAG_GUEST));

        // No type, which defaults to {@link UserManager#USER_TYPE_FULL_SECONDARY}.
        expect.withMessage("USER_TYPE_FULL_SECONDARY")
                .that(UserInfo.getDefaultUserType(FLAG_EPHEMERAL))
                .isEqualTo(UserManager.USER_TYPE_FULL_SECONDARY);
    }

    /** Tests {@link UserTypeFactory#customizeBuilders} for a reasonable xml file. */
    @Test
    public void testUserTypeFactoryCustomize_profile() throws Exception {
        final String userTypeAosp1 = "android.test.1"; // Profile user that is not customized
        final String userTypeAosp2 = "android.test.2"; // Profile user that is customized
        final String userTypeOem1 = "custom.test.1"; // Custom-defined profile

        // Mock some "AOSP defaults".
        final Bundle restrictions = makeRestrictionsBundle("no_config_vpn", "no_config_tethering");
        final UserProperties.Builder props = new UserProperties.Builder()
                .setShowInLauncher(19)
                .setStartWithParent(true)
                .setUseParentsContacts(true)
                .setCrossProfileIntentFilterAccessControl(10)
                .setCrossProfileIntentResolutionStrategy(1)
                .setMediaSharedWithParent(false)
                .setCredentialShareableWithParent(true)
                .setAuthAlwaysRequiredToDisableQuietMode(false)
                .setAllowStoppingUserWithDelayedLocking(false)
                .setShowInSettings(20)
                .setInheritDevicePolicy(21)
                .setShowInSharingSurfaces(22)
                .setShowInQuietMode(24)
                .setDeleteAppWithParent(true)
                .setAlwaysVisible(false)
                .setCrossProfileContentSharingStrategy(1)
                .setProfileApiVisibility(36)
                .setItemsRestrictedOnHomeScreen(false);

        final ArrayMap<String, UserTypeDetails.Builder> builders = new ArrayMap<>();
        builders.put(userTypeAosp1, new UserTypeDetails.Builder()
                .setName(userTypeAosp1)
                .setBaseType(FLAG_PROFILE)
                .setMaxAllowedPerParent(31)
                .setDefaultRestrictions(restrictions)
                .setDefaultUserProperties(props));
        builders.put(userTypeAosp2, new UserTypeDetails.Builder()
                .setName(userTypeAosp1)
                .setBaseType(FLAG_PROFILE)
                .setMaxAllowedPerParent(32)
                .setIconBadge(401)
                .setBadgeColors(402, 403, 404)
                .setDefaultRestrictions(restrictions)
                .setDefaultUserProperties(props));

        final XmlResourceParser parser = mResources.getXml(R.xml.usertypes_test_profile);
        UserTypeFactory.customizeBuilders(builders, parser);

        // userTypeAosp1 should not be modified.
        UserTypeDetails aospType = builders.get(userTypeAosp1).createUserTypeDetails();
        expect.withMessage("getMaxAllowedPerParent")
                .that(aospType.getMaxAllowedPerParent()).isEqualTo(31);
        expect.withMessage("getIconBadge")
                .that(aospType.getIconBadge()).isEqualTo(Resources.ID_NULL);
        expect.withMessage("restrictions")
                .that(UserRestrictionsUtils
                        .areEqual(restrictions, aospType.getDefaultRestrictions()))
                .isTrue();
        expect.withMessage("getShowInLauncher")
                .that(aospType.getDefaultUserPropertiesReference().getShowInLauncher())
                .isEqualTo(19);
        expect.withMessage("getCrossProfileIntentFilterAccessControl")
                .that(aospType.getDefaultUserPropertiesReference()
                .getCrossProfileIntentFilterAccessControl()).isEqualTo(10);
        expect.withMessage("getCrossProfileIntentResolutionStrategy")
                .that(aospType.getDefaultUserPropertiesReference()
                .getCrossProfileIntentResolutionStrategy()).isEqualTo(1);
        expect.withMessage("getStartWithParent")
                .that(aospType.getDefaultUserPropertiesReference().getStartWithParent()).isTrue();
        expect.withMessage("getUseParentsContacts")
                .that(aospType.getDefaultUserPropertiesReference().getUseParentsContacts())
                .isTrue();
        expect.withMessage("isMediaSharedWithParent")
                .that(aospType.getDefaultUserPropertiesReference().isMediaSharedWithParent())
                .isFalse();
        expect.withMessage("isCredentialShareableWithParent")
                .that(aospType.getDefaultUserPropertiesReference()
                        .isCredentialShareableWithParent())
                .isTrue();
        expect.withMessage("isAuthAlwaysRequiredToDisableQuietMode")
        .that(aospType.getDefaultUserPropertiesReference()
                .isAuthAlwaysRequiredToDisableQuietMode()).isFalse();
        expect.withMessage("getAllowStoppingUserWithDelayedLocking")
                .that(aospType.getDefaultUserPropertiesReference()
                .getAllowStoppingUserWithDelayedLocking()).isFalse();
        expect.withMessage("getShowInSettings")
                .that(aospType.getDefaultUserPropertiesReference().getShowInSettings())
                .isEqualTo(20);
        expect.withMessage("getInheritDevicePolicy")
                .that(aospType.getDefaultUserPropertiesReference()
                .getInheritDevicePolicy()).isEqualTo(21);
        expect.withMessage("getShowInSharingSurfaces")
                .that(aospType.getDefaultUserPropertiesReference().getShowInSharingSurfaces())
                .isEqualTo(22);
        expect.withMessage("getShowInQuietMode")
                .that(aospType.getDefaultUserPropertiesReference().getShowInQuietMode())
                .isEqualTo(24);
        expect.withMessage("getDeleteAppWithParent")
                .that(aospType.getDefaultUserPropertiesReference().getDeleteAppWithParent())
                .isTrue();
        expect.withMessage("getAlwaysVisible")
                .that(aospType.getDefaultUserPropertiesReference().getAlwaysVisible()).isFalse();
        expect.withMessage("getCrossProfileContentSharingStrategy")
                .that(aospType.getDefaultUserPropertiesReference()
                        .getCrossProfileContentSharingStrategy())
                .isEqualTo(1);
        expect.withMessage("getProfileApiVisibility")
                .that(aospType.getDefaultUserPropertiesReference().getProfileApiVisibility())
                .isEqualTo(36);
        expect.withMessage("areItemsRestrictedOnHomeScreen")
                .that(aospType.getDefaultUserPropertiesReference()
                        .areItemsRestrictedOnHomeScreen())
                .isFalse();

        // userTypeAosp2 should be modified.
        aospType = builders.get(userTypeAosp2).createUserTypeDetails();
        expect.withMessage("getMaxAllowedPerParent")
                .that(aospType.getMaxAllowedPerParent()).isEqualTo(12);
        expect.withMessage("getIconBadge")
                .that(aospType.getIconBadge())
                .isEqualTo(com.android.internal.R.drawable.ic_corp_icon_badge_case);
        expect.withMessage("getBadgePlain")
                .that(aospType.getBadgePlain())
                .isEqualTo(Resources.ID_NULL); // No resId for 'garbage'
        expect.withMessage("getBadgeNoBackground")
                .that(aospType.getBadgeNoBackground())
                .isEqualTo(com.android.internal.R.drawable.ic_corp_badge_no_background);
        expect.withMessage("getStatusBarIcon")
                .that(aospType.getStatusBarIcon())
                .isEqualTo(com.android.internal.R.drawable.ic_test_badge_experiment);
        expect.withMessage("getBadgeLabel(0)")
                .that(aospType.getBadgeLabel(0))
                .isEqualTo(com.android.internal.R.string.managed_profile_label_badge);
        expect.withMessage("getBadgeLabel(1)")
                .that(aospType.getBadgeLabel(1))
                .isEqualTo(com.android.internal.R.string.managed_profile_label_badge_2);
        expect.withMessage("getBadgeLabel(2)")
                .that(aospType.getBadgeLabel(2))
                .isEqualTo(com.android.internal.R.string.managed_profile_label_badge_2);
        expect.withMessage("getBadgeLabel(3)")
                .that(aospType.getBadgeLabel(3))
                .isEqualTo(com.android.internal.R.string.managed_profile_label_badge_2);
        expect.withMessage("getBadgeColor(0)")
                .that(aospType.getBadgeColor(0))
                .isEqualTo(com.android.internal.R.color.profile_badge_1);
        expect.withMessage("getBadgeColor(1)")
                .that(aospType.getBadgeColor(1))
                .isEqualTo(com.android.internal.R.color.profile_badge_2);
        expect.withMessage("getBadgeColor(2)")
                .that(aospType.getBadgeColor(2))
                .isEqualTo(com.android.internal.R.color.profile_badge_2);
        expect.withMessage("getBadgeColor(3)")
                .that(aospType.getBadgeColor(3))
                .isEqualTo(com.android.internal.R.color.profile_badge_2);
        expect.withMessage("restrictions")
                .that(UserRestrictionsUtils.areEqual(
                        makeRestrictionsBundle("no_remove_user", "no_bluetooth"),
                        aospType.getDefaultRestrictions()))
                .isTrue();
        expect.withMessage("getShowInLauncher")
                .that(aospType.getDefaultUserPropertiesReference().getShowInLauncher())
                .isEqualTo(2020);
        expect.withMessage("getCrossProfileIntentFilterAccessControl")
                .that(aospType.getDefaultUserPropertiesReference()
                        .getCrossProfileIntentFilterAccessControl())
                .isEqualTo(20);
        expect.withMessage("getCrossProfileIntentResolutionStrategy")
                .that(aospType.getDefaultUserPropertiesReference()
                        .getCrossProfileIntentResolutionStrategy())
                .isEqualTo(0);
        expect.withMessage("getStartWithParent")
                .that(aospType.getDefaultUserPropertiesReference().getStartWithParent())
                .isFalse();
        expect.withMessage("getUseParentsContacts")
                .that(aospType.getDefaultUserPropertiesReference().getUseParentsContacts())
                .isFalse();
        expect.withMessage("isMediaSharedWithParent")
                .that(aospType.getDefaultUserPropertiesReference().isMediaSharedWithParent())
                .isTrue();
        expect.withMessage("isCredentialShareableWithParent")
                .that(aospType.getDefaultUserPropertiesReference()
                        .isCredentialShareableWithParent())
                 .isFalse();
        expect.withMessage("isAuthAlwaysRequiredToDisableQuietMode")
                .that(aospType.getDefaultUserPropertiesReference()
                        .isAuthAlwaysRequiredToDisableQuietMode())
                .isTrue();
        expect.withMessage("getAllowStoppingUserWithDelayedLocking")
                .that(aospType.getDefaultUserPropertiesReference()
                        .getAllowStoppingUserWithDelayedLocking())
                .isTrue();
        expect.withMessage("getShowInSettings")
                .that(aospType.getDefaultUserPropertiesReference().getShowInSettings())
                .isEqualTo(23);
        expect.withMessage("getShowInSharingSurfaces")
                .that(aospType.getDefaultUserPropertiesReference().getShowInSharingSurfaces())
                .isEqualTo(22);
        expect.withMessage("getShowInQuietMode")
                .that(aospType.getDefaultUserPropertiesReference().getShowInQuietMode())
                .isEqualTo(24);
        expect.withMessage("getInheritDevicePolicy")
                .that(aospType.getDefaultUserPropertiesReference().getInheritDevicePolicy())
                .isEqualTo(450);
        expect.withMessage("getDeleteAppWithParent")
                .that(aospType.getDefaultUserPropertiesReference().getDeleteAppWithParent())
                .isFalse();
        expect.withMessage("getAlwaysVisible")
                .that(aospType.getDefaultUserPropertiesReference().getAlwaysVisible())
                .isTrue();
        expect.withMessage("getCrossProfileContentSharingStrategy")
                .that(aospType.getDefaultUserPropertiesReference()
                        .getCrossProfileContentSharingStrategy())
                .isEqualTo(0);
        expect.withMessage("getProfileApiVisibility")
                .that(aospType.getDefaultUserPropertiesReference().getProfileApiVisibility())
                .isEqualTo(36);
        expect.withMessage("areItemsRestrictedOnHomeScreen")
                .that(aospType.getDefaultUserPropertiesReference().areItemsRestrictedOnHomeScreen())
                .isTrue();

        // userTypeOem1 should be created.
        assertNotNull(builders.get(userTypeOem1));
        UserTypeDetails customType = builders.get(userTypeOem1).createUserTypeDetails();
        expect.withMessage("customType getMaxAllowedPerParent")
                .that(customType.getMaxAllowedPerParent()).isEqualTo(14);
        expect.withMessage("customType isProfileParentRequired")
                .that(customType.isProfileParentRequired()).isTrue();
    }

    /** Tests {@link UserTypeFactory#customizeBuilders} for customizing a FULL user. */
    @Test
    public void testUserTypeFactoryCustomize_full() throws Exception {
        final String userTypeFull = "android.test.1";

        // Mock "AOSP default".
        final Bundle restrictions = makeRestrictionsBundle("no_config_vpn", "no_config_tethering");
        final ArrayMap<String, UserTypeDetails.Builder> builders = new ArrayMap<>();
        builders.put(userTypeFull, new UserTypeDetails.Builder()
                .setName(userTypeFull)
                .setBaseType(FLAG_FULL)
                .setEnabled(0)
                .setDefaultRestrictions(restrictions));

        final XmlResourceParser parser = mResources.getXml(R.xml.usertypes_test_full);
        UserTypeFactory.customizeBuilders(builders, parser);

        UserTypeDetails details = builders.get(userTypeFull).createUserTypeDetails();
        expect.withMessage("getMaxAllowedPerParent")
                .that(details.getMaxAllowedPerParent()).isEqualTo(0);
        expect.withMessage("isEnabled").that(details.isEnabled()).isFalse();
        expect.withMessage("getMaxAllowed").that(details.getMaxAllowed()).isEqualTo(17);
        expect.withMessage("restrictions")
                .that(UserRestrictionsUtils.areEqual(
                        makeRestrictionsBundle("no_remove_user", "no_bluetooth"),
                        details.getDefaultRestrictions()))
                .isTrue();
        expect.withMessage("getBadgeColor(0)")
                .that(details.getBadgeColor(0)).isEqualTo(Resources.ID_NULL);
    }

    /**
     * Tests {@link UserTypeFactory#customizeBuilders} when custom user type deletes the
     * badge-colors and restrictions.
     */
    @Test
    public void testUserTypeFactoryCustomize_eraseArray() throws Exception {
        final String typeName = "android.test";

        final ArrayMap<String, UserTypeDetails.Builder> builders = new ArrayMap<>();
        builders.put(typeName, new UserTypeDetails.Builder()
                .setName(typeName)
                .setBaseType(FLAG_PROFILE)
                .setMaxAllowedPerParent(1)
                .setBadgeColors(501, 502)
                .setDefaultRestrictions(makeRestrictionsBundle("r1")));

        final XmlResourceParser parser = mResources.getXml(R.xml.usertypes_test_eraseArray);
        UserTypeFactory.customizeBuilders(builders, parser);

        UserTypeDetails typeDetails =  builders.get(typeName).createUserTypeDetails();
        expect.withMessage("getMaxAllowedPerParent")
                .that(typeDetails.getMaxAllowedPerParent()).isEqualTo(2);
        expect.withMessage("getBadgeColor(0)")
                .that(typeDetails.getBadgeColor(0)).isEqualTo(Resources.ID_NULL);
        expect.withMessage("getBadgeColor(1)")
                .that(typeDetails.getBadgeColor(1)).isEqualTo(Resources.ID_NULL);
        expect.withMessage("getDefaultRestrictions")
                .that(typeDetails.getDefaultRestrictions().isEmpty()).isTrue();
    }

    /** Tests {@link UserTypeFactory#customizeBuilders} when custom user type has illegal name. */
    @Test
    public void testUserTypeFactoryCustomize_illegalOemName() throws Exception {
        final String userTypeAosp = "android.aosp.legal";
        final String userTypeOem = "android.oem.illegal.name"; // Custom-defined profile

        final ArrayMap<String, UserTypeDetails.Builder> builders = new ArrayMap<>();
        builders.put(userTypeAosp, new UserTypeDetails.Builder()
                .setName(userTypeAosp)
                .setBaseType(FLAG_PROFILE)
                .setMaxAllowedPerParent(21));

        final XmlResourceParser parser = mResources.getXml(R.xml.usertypes_test_illegalOemName);

        // parser is illegal because non-AOSP user types cannot be prefixed with "android.".
        assertThrows(IllegalArgumentException.class,
                () -> UserTypeFactory.customizeBuilders(builders, parser));
    }

    /**
     * Tests {@link UserTypeFactory#customizeBuilders} when illegally customizing a non-profile as
     * a profile.
     */
    @Test
    public void testUserTypeFactoryCustomize_illegalUserBaseType() throws Exception {
        final String userTypeFull = "android.test";

        final ArrayMap<String, UserTypeDetails.Builder> builders = new ArrayMap<>();
        builders.put(userTypeFull, new UserTypeDetails.Builder()
                .setName(userTypeFull)
                .setBaseType(FLAG_FULL)
                .setMaxAllowedPerParent(21));

        XmlResourceParser parser = mResources.getXml(R.xml.usertypes_test_illegalUserBaseType);

        // parser is illegal because userTypeFull is FULL but the tag is for profile-type.
        assertThrows(IllegalArgumentException.class,
                () -> UserTypeFactory.customizeBuilders(builders, parser));
    }

    @Test
    public void testUserTypeFactoryVersion_versionMissing() {
        final XmlResourceParser parser = mResources.getXml(R.xml.usertypes_test_eraseArray);
        expect.withMessage("getUserTypeVersion")
                .that(UserTypeFactory.getUserTypeVersion(parser)).isEqualTo(0);
    }

    @Test
    public void testUserTypeFactoryVersion_versionPresent() {
        final XmlResourceParser parser = mResources.getXml(R.xml.usertypes_test_profile);
        expect.withMessage("getUserTypeVersion")
                .that(UserTypeFactory.getUserTypeVersion(parser)).isEqualTo(1234);
    }

    @Test
    public void testUserTypeFactoryUpgrades_validUpgrades() {
        final ArrayMap<String, UserTypeDetails.Builder> builders = new ArrayMap<>();
        builders.put("name", getMinimalBuilder());

        final XmlResourceParser parser = mResources.getXml(R.xml.usertypes_test_profile);
        List<UserTypeFactory.UserTypeUpgrade> upgrades =
                UserTypeFactory.parseUserUpgrades(builders, parser);

        expect.withMessage("upgrades not empty").that(upgrades).isNotEmpty();
        UserTypeFactory.UserTypeUpgrade upgrade = upgrades.get(0);
        expect.withMessage("getFromType").that(upgrade.getFromType()).isEqualTo("android.test.1");
        expect.withMessage("getToType").that(upgrade.getToType()).isEqualTo("android.test.2");
        expect.withMessage("getUpToVersion").that(upgrade.getUpToVersion()).isEqualTo(1233);
    }

    @Test
    public void testUserTypeFactoryUpgrades_illegalBaseTypeUpgrade() {
        final String userTypeFull = "android.test.1";
        final ArrayMap<String, UserTypeDetails.Builder> builders = new ArrayMap<>();
        builders.put(userTypeFull, new UserTypeDetails.Builder()
                .setName(userTypeFull)
                .setBaseType(FLAG_FULL));

        final XmlResourceParser parser = mResources.getXml(R.xml.usertypes_test_full);

        // parser is illegal because the "to" upgrade type is not a profile, but a full user
        assertThrows(IllegalArgumentException.class,
                () -> UserTypeFactory.parseUserUpgrades(builders, parser));
    }

    /** Returns a minimal {@link UserTypeDetails.Builder} that can legitimately be created. */
    private UserTypeDetails.Builder getMinimalBuilder() {
        return new UserTypeDetails.Builder().setName("name").setBaseType(FLAG_FULL);
    }

    /** Creates a Bundle of the given String restrictions, each set to true. */
    public static Bundle makeRestrictionsBundle(String ... restrictions) {
        final Bundle bundle = new Bundle();
        for (String restriction : restrictions) {
            bundle.putBoolean(restriction, true);
        }
        return bundle;
    }

    /** Creates a Bundle of the given settings keys and puts true for the value. */
    private static Bundle makeSettingsBundle(String ... settings) {
        final Bundle bundle = new Bundle();
        for (String setting : settings) {
            bundle.putBoolean(setting, true);
        }
        return bundle;
    }
}
