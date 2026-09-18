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

package android.app.admin;

import static android.processor.devicepolicy.AllowedDpcTypes.ALLOWED;
import static android.processor.devicepolicy.AllowedDpcTypes.DISALLOWED;
import static android.processor.devicepolicy.AllowedDpcTypes.SAME_AS_UNAFFILIATED;

import android.annotation.FlaggedApi;
import android.annotation.IntDef;
import android.processor.devicepolicy.AllowedDpcTypes;
import android.processor.devicepolicy.BooleanPolicyDefinition;
import android.processor.devicepolicy.EnumPolicyDefinition;
import android.processor.devicepolicy.EnumResolutionMechanism;
import android.processor.devicepolicy.IntegerPolicyDefinition;
import android.processor.devicepolicy.IntegerResolutionMechanism;
import android.processor.devicepolicy.ListOfPackagePolicyDefinition;
import android.processor.devicepolicy.ListOfStringPolicyDefinition;
import android.processor.devicepolicy.ListResolutionMechanism;
import android.processor.devicepolicy.LongPolicyDefinition;
import android.processor.devicepolicy.LongResolutionMechanism;
import android.processor.devicepolicy.PackagePolicyDefinition;
import android.processor.devicepolicy.PolicyDefinition;
import android.processor.devicepolicy.StringPolicyDefinition;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;

/** This is a test version of PolicyIdentifier which is used to verify the annotation processor. */
public final class PolicyIdentifier<T> {
    private final String mId;

    public PolicyIdentifier(String id) {
        mId = id;
    }

    public String getId() {
        return mId;
    }

    /**
     * Test policy 1
     *
     * <p>Second line
     */
    @BooleanPolicyDefinition(
            base =
                    @PolicyDefinition(
                            allowedScopes = {
                                1, // POLICY_SCOPE_USER
                                2 // POLICY_SCOPE_DEVICE
                            },
                            affectedResource = 2, // RESOURCE_DEVICE_PER_USER
                            requiredPermission = "android.permission.MANAGE_POLICY_SIMPLE_BOOLEAN",
                            requiredCrossUserPermission =
                                    "android.permission.MANAGE_DEVICE_POLICY_ACROSS_USERS_FULL",
                            allowedDpcTypes =
                                    @AllowedDpcTypes(
                                            deviceOwner = DISALLOWED,
                                            managedProfileOwnerOfOrganizationOwnedDevice =
                                                    DISALLOWED,
                                            managedProfileOwnerOfPersonalOwnedDevice = DISALLOWED,
                                            unaffiliatedFullUserProfileOwner = DISALLOWED)))
    public static final PolicyIdentifier<Boolean> SIMPLE_BOOLEAN_POLICY =
            new PolicyIdentifier<>("SIMPLE_BOOLEAN_POLICY");

    /** First entry */
    public static final int ENUM_ENTRY_1 = 1;

    /** Second entry */
    public static final int ENUM_ENTRY_2 = 2;

    /** Third entry */
    public static final int ENUM_ENTRY_3 = 3;

    /**
     * Enum for {@link SIMPLE_ENUM_POLICY}
     *
     * @hide
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(
            prefix = {"ENUM_ENTRY_"},
            value = {ENUM_ENTRY_1, ENUM_ENTRY_2, ENUM_ENTRY_3})
    public @interface SimpleEnumPolicyEnum {}

    /** Test policy 2 */
    @EnumPolicyDefinition(
            base =
                    @PolicyDefinition(
                            allowedScopes = {
                                2, // POLICY_SCOPE_DEVICE
                                3 // POLICY_SCOPE_PARENT_USER
                            },
                            affectedResource = 1, // RESOURCE_DEVICE_WIDE
                            requiredPermission = "android.permission.MANAGE_POLICY_SIMPLE_ENUM",
                            requiredCrossUserPermission =
                                    "android.permission.MANAGE_DEVICE_POLICY_ACROSS_USERS",
                            allowedDpcTypes =
                                    @AllowedDpcTypes(
                                            deviceOwner = DISALLOWED,
                                            managedProfileOwnerOfOrganizationOwnedDevice =
                                                    DISALLOWED,
                                            managedProfileOwnerOfPersonalOwnedDevice = DISALLOWED,
                                            unaffiliatedFullUserProfileOwner = DISALLOWED)),
            defaultValue = ENUM_ENTRY_2,
            intDef = SimpleEnumPolicyEnum.class,
            resolutionMechanism = @EnumResolutionMechanism(custom = true))
    public static final PolicyIdentifier<Integer> SIMPLE_ENUM_POLICY =
            new PolicyIdentifier<>("SIMPLE_ENUM_POLICY");

    /** Test policy 3 */
    @IntegerPolicyDefinition(
            base =
                    @PolicyDefinition(
                            allowedScopes = {
                                1 // POLICY_SCOPE_USER
                            },
                            affectedResource = 1, // RESOURCE_DEVICE_WIDE
                            // requiredPermission and requiredCrossUserPermission using the default
                            // values.
                            allowedDpcTypes =
                                    @AllowedDpcTypes(
                                            deviceOwner = DISALLOWED,
                                            managedProfileOwnerOfOrganizationOwnedDevice =
                                                    DISALLOWED,
                                            managedProfileOwnerOfPersonalOwnedDevice = DISALLOWED,
                                            unaffiliatedFullUserProfileOwner = DISALLOWED)),
            resolutionMechanism = @IntegerResolutionMechanism(custom = true))
    public static final PolicyIdentifier<Integer> SIMPLE_INTEGER_POLICY =
            new PolicyIdentifier<>("SIMPLE_INTEGER_POLICY");

    /** Test policy for Long */
    @LongPolicyDefinition(
            base =
                    @PolicyDefinition(
                            allowedScopes = {
                                1 // POLICY_SCOPE_USER
                            },
                            affectedResource = 1, // RESOURCE_DEVICE_WIDE
                            // requiredPermission and requiredCrossUserPermission using the default
                            // values.
                            allowedDpcTypes =
                                    @AllowedDpcTypes(
                                            deviceOwner = DISALLOWED,
                                            managedProfileOwnerOfOrganizationOwnedDevice =
                                                    DISALLOWED,
                                            managedProfileOwnerOfPersonalOwnedDevice = DISALLOWED,
                                            unaffiliatedFullUserProfileOwner = DISALLOWED)),
            resolutionMechanism = @LongResolutionMechanism(custom = true))
    public static final PolicyIdentifier<Long> SIMPLE_LONG_POLICY =
            new PolicyIdentifier<>("SIMPLE_LONG_POLICY");

    /** Test policy with an integer range. */
    @IntegerPolicyDefinition(
            base =
                    @PolicyDefinition(
                            allowedScopes = {
                                1 // POLICY_SCOPE_USER
                            },
                            affectedResource = 1, // RESOURCE_DEVICE_WIDE
                            // requiredPermission and requiredCrossUserPermission using the default
                            // values.
                            allowedDpcTypes =
                                    @AllowedDpcTypes(
                                            deviceOwner = DISALLOWED,
                                            managedProfileOwnerOfOrganizationOwnedDevice =
                                                    DISALLOWED,
                                            managedProfileOwnerOfPersonalOwnedDevice = DISALLOWED,
                                            unaffiliatedFullUserProfileOwner = DISALLOWED)),
            minValue = -100,
            maxValue = 100,
            resolutionMechanism = @IntegerResolutionMechanism(custom = true))
    public static final PolicyIdentifier<Integer> SIMPLE_INTEGER_POLICY_WITH_RANGE =
            new PolicyIdentifier<>("SIMPLE_INTEGER_POLICY_WITH_RANGE");

    /** Test long policy with min and max values. */
    @LongPolicyDefinition(
            base =
                    @PolicyDefinition(
                            allowedScopes = {
                                1 // POLICY_SCOPE_USER
                            },
                            affectedResource = 1, // RESOURCE_DEVICE_WIDE
                            // requiredPermission and requiredCrossUserPermission using the default
                            // values.
                            allowedDpcTypes =
                                    @AllowedDpcTypes(
                                            deviceOwner = DISALLOWED,
                                            managedProfileOwnerOfOrganizationOwnedDevice =
                                                    DISALLOWED,
                                            managedProfileOwnerOfPersonalOwnedDevice = DISALLOWED,
                                            unaffiliatedFullUserProfileOwner = DISALLOWED)),
            minValue = 10,
            maxValue = 100,
            resolutionMechanism = @LongResolutionMechanism(custom = true))
    public static final PolicyIdentifier<Long> SIMPLE_LONG_POLICY_WITH_RANGE =
            new PolicyIdentifier<>("SIMPLE_LONG_POLICY_WITH_RANGE");

    /** Test policy 4 */
    @StringPolicyDefinition(
            base =
                    @PolicyDefinition(
                            allowedScopes = {
                                1 // POLICY_SCOPE_USER
                            },
                            affectedResource = 1, // RESOURCE_DEVICE_WIDE
                            // requiredPermission and requiredCrossUserPermission using the default
                            // values.
                            allowedDpcTypes =
                                    @AllowedDpcTypes(
                                            deviceOwner = DISALLOWED,
                                            managedProfileOwnerOfOrganizationOwnedDevice =
                                                    DISALLOWED,
                                            managedProfileOwnerOfPersonalOwnedDevice = DISALLOWED,
                                            unaffiliatedFullUserProfileOwner = DISALLOWED)))
    public static final PolicyIdentifier<String> SIMPLE_STRING_POLICY =
            new PolicyIdentifier<>("SIMPLE_STRING_POLICY");

    /** Test policy verifying a string policy with unprintable characters allowed. */
    @StringPolicyDefinition(
            base =
                    @PolicyDefinition(
                            allowedScopes = {
                                1 // POLICY_SCOPE_USER
                            },
                            affectedResource = 1, // RESOURCE_DEVICE_WIDE
                            // requiredPermission and requiredCrossUserPermission using the default
                            // values.
                            allowedDpcTypes =
                                    @AllowedDpcTypes(
                                            deviceOwner = DISALLOWED,
                                            managedProfileOwnerOfOrganizationOwnedDevice =
                                                    DISALLOWED,
                                            managedProfileOwnerOfPersonalOwnedDevice = DISALLOWED,
                                            unaffiliatedFullUserProfileOwner = DISALLOWED)),
            unprintableCharactersAllowed = true)
    public static final PolicyIdentifier<String>
            SIMPLE_STRING_POLICY_WITH_UNPRINTABLE_CHARACTERS_ALLOWED =
                    new PolicyIdentifier<>(
                            "SIMPLE_STRING_POLICY_WITH_UNPRINTABLE_CHARACTERS_ALLOWED");

    /** Test policy verifying a string policy with max length. */
    @StringPolicyDefinition(
            base =
                    @PolicyDefinition(
                            allowedScopes = {
                                1 // POLICY_SCOPE_USER
                            },
                            affectedResource = 1, // RESOURCE_DEVICE_WIDE
                            // requiredPermission and requiredCrossUserPermission using the default
                            // values.
                            allowedDpcTypes =
                                    @AllowedDpcTypes(
                                            deviceOwner = DISALLOWED,
                                            managedProfileOwnerOfOrganizationOwnedDevice =
                                                    DISALLOWED,
                                            managedProfileOwnerOfPersonalOwnedDevice = DISALLOWED,
                                            unaffiliatedFullUserProfileOwner = DISALLOWED)),
            maxLength = 10)
    public static final PolicyIdentifier<String> STRING_POLICY_WITH_MAX_LENGTH =
            new PolicyIdentifier<>("STRING_POLICY_WITH_MAX_LENGTH");

    /** Test policy 5 */
    @ListOfStringPolicyDefinition(
            base =
                    @PolicyDefinition(
                            allowedScopes = {
                                1 // POLICY_SCOPE_USER
                            },
                            affectedResource = 1, // RESOURCE_DEVICE_WIDE
                            // requiredPermission and requiredCrossUserPermission
                            // using the default
                            // values.
                            allowedDpcTypes =
                                    @AllowedDpcTypes(
                                            deviceOwner = DISALLOWED,
                                            managedProfileOwnerOfOrganizationOwnedDevice =
                                                    DISALLOWED,
                                            managedProfileOwnerOfPersonalOwnedDevice = DISALLOWED,
                                            unaffiliatedFullUserProfileOwner = DISALLOWED)),
            emptyStringAllowed = true,
            resolutionMechanism = @ListResolutionMechanism(custom = true))
    public static final PolicyIdentifier<List<String>> SIMPLE_STRING_LIST_POLICY =
            new PolicyIdentifier<>("SIMPLE_STRING_LIST_POLICY");

    /** Test policy verifying a string list policy with unprintable characters allowed. */
    @ListOfStringPolicyDefinition(
            base =
                    @PolicyDefinition(
                            allowedScopes = {
                                1 // POLICY_SCOPE_USER
                            },
                            affectedResource = 1, // RESOURCE_DEVICE_WIDE
                            // requiredPermission and requiredCrossUserPermission
                            // using the default
                            // values.
                            allowedDpcTypes =
                                    @AllowedDpcTypes(
                                            deviceOwner = DISALLOWED,
                                            managedProfileOwnerOfOrganizationOwnedDevice =
                                                    DISALLOWED,
                                            managedProfileOwnerOfPersonalOwnedDevice = DISALLOWED,
                                            unaffiliatedFullUserProfileOwner = DISALLOWED)),
            emptyStringAllowed = true,
            unprintableCharactersAllowed = true,
            resolutionMechanism = @ListResolutionMechanism(custom = true))
    public static final PolicyIdentifier<List<String>>
            SIMPLE_STRING_LIST_POLICY_WITH_UNPRINTABLE_CHARACTERS_ALLOWED =
                    new PolicyIdentifier<>(
                            "SIMPLE_STRING_LIST_POLICY_WITH_UNPRINTABLE_CHARACTERS_ALLOWED");

    /** Test policy for Package */
    @PackagePolicyDefinition(
            base =
                    @PolicyDefinition(
                            allowedScopes = {
                                1 // POLICY_SCOPE_USER
                            },
                            affectedResource = 1, // RESOURCE_DEVICE_WIDE
                            // requiredPermission and requiredCrossUserPermission using the default
                            // values.
                            allowedDpcTypes =
                                    @AllowedDpcTypes(
                                            deviceOwner = DISALLOWED,
                                            managedProfileOwnerOfOrganizationOwnedDevice =
                                                    DISALLOWED,
                                            managedProfileOwnerOfPersonalOwnedDevice = DISALLOWED,
                                            unaffiliatedFullUserProfileOwner = DISALLOWED)))
    public static final PolicyIdentifier<PackageIdentifier> SIMPLE_PACKAGE_POLICY =
            new PolicyIdentifier<>("SIMPLE_PACKAGE_POLICY");

    /** Test policy for Package List */
    @ListOfPackagePolicyDefinition(
            base = @PolicyDefinition(
                    allowedScopes = {
                             1 // POLICY_SCOPE_USER
                    },
                    affectedResource = 1, // RESOURCE_DEVICE_WIDE
                    // requiredPermission and requiredCrossUserPermission using the default values.
                    allowedDpcTypes = @AllowedDpcTypes(
                            deviceOwner = DISALLOWED,
                            managedProfileOwnerOfOrganizationOwnedDevice = DISALLOWED,
                            managedProfileOwnerOfPersonalOwnedDevice = DISALLOWED,
                            unaffiliatedFullUserProfileOwner = DISALLOWED)),
            emptyListAllowed = false,
            resolutionMechanism = @ListResolutionMechanism(custom = true))
    public static final PolicyIdentifier<List<PackageIdentifier>> SIMPLE_PACKAGE_LIST_POLICY =
            new PolicyIdentifier<>("SIMPLE_PACKAGE_LIST_POLICY");

    /** Test policy verifying processing of DEFAULT_DEVICE_OWNER allowed. */
    @IntegerPolicyDefinition(
            base =
                    @PolicyDefinition(
                            allowedScopes = {
                                1 // POLICY_SCOPE_USER
                            },
                            affectedResource = 1, // RESOURCE_DEVICE_WIDE
                            // requiredPermission and requiredCrossUserPermission using the default
                            // values.
                            allowedDpcTypes =
                                    @AllowedDpcTypes(
                                            deviceOwner = ALLOWED,
                                            managedProfileOwnerOfOrganizationOwnedDevice =
                                                    DISALLOWED,
                                            managedProfileOwnerOfPersonalOwnedDevice = DISALLOWED,
                                            unaffiliatedFullUserProfileOwner = DISALLOWED)),
            resolutionMechanism = @IntegerResolutionMechanism(custom = true))
    public static final PolicyIdentifier<Integer> TEST_DEFAULT_DEVICE_OWNER_ALLOWED =
            new PolicyIdentifier<>("TEST_DEFAULT_DEVICE_OWNER_ALLOWED");

    /** Test policy verifying processing of FINANCED_DEVICE_OWNER allowed. */
    @IntegerPolicyDefinition(
            base =
                    @PolicyDefinition(
                            allowedScopes = {
                                1 // POLICY_SCOPE_USER
                            },
                            affectedResource = 1, // RESOURCE_DEVICE_WIDE
                            // requiredPermission and requiredCrossUserPermission using the default
                            // values.
                            allowedDpcTypes =
                                    @AllowedDpcTypes(
                                            deviceOwner = DISALLOWED,
                                            financedDeviceOwner = ALLOWED,
                                            managedProfileOwnerOfOrganizationOwnedDevice =
                                                    DISALLOWED,
                                            managedProfileOwnerOfPersonalOwnedDevice = DISALLOWED,
                                            unaffiliatedFullUserProfileOwner = DISALLOWED)),
            resolutionMechanism = @IntegerResolutionMechanism(custom = true))
    public static final PolicyIdentifier<Integer> TEST_FINANCED_DEVICE_OWNER_ALLOWED =
            new PolicyIdentifier<>("TEST_FINANCED_DEVICE_OWNER_ALLOWED");

    /** Test policy verifying processing of PROFILE_OWNER_OF_ORGANIZATION_OWNED_DEVICE allowed. */
    @IntegerPolicyDefinition(
            base =
                    @PolicyDefinition(
                            allowedScopes = {
                                1 // POLICY_SCOPE_USER
                            },
                            affectedResource = 1, // RESOURCE_DEVICE_WIDE
                            // requiredPermission and requiredCrossUserPermission using the default
                            // values.
                            allowedDpcTypes =
                                    @AllowedDpcTypes(
                                            deviceOwner = DISALLOWED,
                                            managedProfileOwnerOfOrganizationOwnedDevice = ALLOWED,
                                            managedProfileOwnerOfPersonalOwnedDevice = DISALLOWED,
                                            unaffiliatedFullUserProfileOwner = DISALLOWED)),
            resolutionMechanism = @IntegerResolutionMechanism(custom = true))
    public static final PolicyIdentifier<Integer>
            TEST_PROFILE_OWNER_OF_ORGANIZATION_OWNED_DEVICE_ALLOWED =
                    new PolicyIdentifier<>(
                            "TEST_PROFILE_OWNER_OF_ORGANIZATION_OWNED_DEVICE_ALLOWED");

    /** Test policy verifying processing of PROFILE_OWNER_ON_USER0 allowed. */
    @IntegerPolicyDefinition(
            base =
                    @PolicyDefinition(
                            allowedScopes = {
                                1 // POLICY_SCOPE_USER
                            },
                            affectedResource = 1, // RESOURCE_DEVICE_WIDE
                            // requiredPermission and requiredCrossUserPermission using the default
                            // values.
                            allowedDpcTypes =
                                    @AllowedDpcTypes(
                                            deviceOwner = DISALLOWED,
                                            managedProfileOwnerOfOrganizationOwnedDevice =
                                                    DISALLOWED,
                                            profileOwnerOnUser0 = ALLOWED,
                                            managedProfileOwnerOfPersonalOwnedDevice = DISALLOWED,
                                            unaffiliatedFullUserProfileOwner = DISALLOWED)),
            resolutionMechanism = @IntegerResolutionMechanism(custom = true))
    public static final PolicyIdentifier<Integer> TEST_PROFILE_OWNER_ON_USER0_ALLOWED =
            new PolicyIdentifier<>("TEST_PROFILE_OWNER_ON_USER0_ALLOWED");

    /** Test policy verifying processing of PROFILE_OWNER allowed. */
    @IntegerPolicyDefinition(
            base =
                    @PolicyDefinition(
                            allowedScopes = {
                                1 // POLICY_SCOPE_USER
                            },
                            affectedResource = 1, // RESOURCE_DEVICE_WIDE
                            // requiredPermission and requiredCrossUserPermission using the default
                            // values.
                            allowedDpcTypes =
                                    @AllowedDpcTypes(
                                            deviceOwner = DISALLOWED,
                                            managedProfileOwnerOfOrganizationOwnedDevice =
                                                    DISALLOWED,
                                            managedProfileOwnerOfPersonalOwnedDevice = ALLOWED,
                                            unaffiliatedFullUserProfileOwner = DISALLOWED)),
            resolutionMechanism = @IntegerResolutionMechanism(custom = true))
    public static final PolicyIdentifier<Integer> TEST_PROFILE_OWNER_ALLOWED =
            new PolicyIdentifier<>("TEST_PROFILE_OWNER_ALLOWED");

    /** Test policy verifying processing of PROFILE_OWNER_ON_USER allowed. */
    @IntegerPolicyDefinition(
            base =
                    @PolicyDefinition(
                            allowedScopes = {
                                1 // POLICY_SCOPE_USER
                            },
                            affectedResource = 1, // RESOURCE_DEVICE_WIDE
                            // requiredPermission and requiredCrossUserPermission using the default
                            // values.
                            allowedDpcTypes =
                                    @AllowedDpcTypes(
                                            deviceOwner = DISALLOWED,
                                            managedProfileOwnerOfOrganizationOwnedDevice =
                                                    DISALLOWED,
                                            managedProfileOwnerOfPersonalOwnedDevice = DISALLOWED,
                                            unaffiliatedFullUserProfileOwner = ALLOWED)),
            resolutionMechanism = @IntegerResolutionMechanism(custom = true))
    public static final PolicyIdentifier<Integer> TEST_PROFILE_OWNER_ON_USER_ALLOWED =
            new PolicyIdentifier<>("TEST_PROFILE_OWNER_ON_USER_ALLOWED");

    /** Test policy verifying processing of AFFILIATED_PROFILE_OWNER_ON_USER allowed. */
    @IntegerPolicyDefinition(
            base =
                    @PolicyDefinition(
                            allowedScopes = {
                                1 // POLICY_SCOPE_USER
                            },
                            affectedResource = 1, // RESOURCE_DEVICE_WIDE
                            // requiredPermission and requiredCrossUserPermission using the default
                            // values.
                            allowedDpcTypes =
                                    @AllowedDpcTypes(
                                            deviceOwner = DISALLOWED,
                                            managedProfileOwnerOfOrganizationOwnedDevice =
                                                    DISALLOWED,
                                            managedProfileOwnerOfPersonalOwnedDevice = DISALLOWED,
                                            unaffiliatedFullUserProfileOwner = DISALLOWED,
                                            affiliatedFullUserProfileOwner = ALLOWED)),
            resolutionMechanism = @IntegerResolutionMechanism(custom = true))
    public static final PolicyIdentifier<Integer> TEST_AFFILIATED_PROFILE_OWNER_ON_USER_ALLOWED =
            new PolicyIdentifier<>("TEST_AFFILIATED_PROFILE_OWNER_ON_USER_ALLOWED");

    /**
     * Test policy verifying processing of AFFILIATED_PROFILE_OWNER_ON_USER_SAME_AS_UNAFFILIATED.
     */
    @IntegerPolicyDefinition(
            base =
                    @PolicyDefinition(
                            allowedScopes = {
                                1 // POLICY_SCOPE_USER
                            },
                            affectedResource = 1, // RESOURCE_DEVICE_WIDE
                            // requiredPermission and requiredCrossUserPermission using the default
                            // values.
                            allowedDpcTypes =
                                    @AllowedDpcTypes(
                                            deviceOwner = DISALLOWED,
                                            managedProfileOwnerOfOrganizationOwnedDevice =
                                                    DISALLOWED,
                                            managedProfileOwnerOfPersonalOwnedDevice = DISALLOWED,
                                            unaffiliatedFullUserProfileOwner = ALLOWED,
                                            affiliatedFullUserProfileOwner = SAME_AS_UNAFFILIATED)),
            resolutionMechanism = @IntegerResolutionMechanism(custom = true))
    public static final PolicyIdentifier<Integer>
            TEST_AFFILIATED_PROFILE_OWNER_ON_USER_SAME_AS_UNAFFILIATED =
                    new PolicyIdentifier<>(
                            "TEST_AFFILIATED_PROFILE_OWNER_ON_USER_SAME_AS_UNAFFILIATED");

    /**
     * Test policy verifying processing of
     * AFFILIATED_PROFILE_OWNER_ON_USER_SAME_AS_UNAFFILIATED_DISALLOWED.
     */
    @IntegerPolicyDefinition(
            base =
                    @PolicyDefinition(
                            allowedScopes = {
                                1 // POLICY_SCOPE_USER
                            },
                            affectedResource = 1, // RESOURCE_DEVICE_WIDE
                            // requiredPermission and requiredCrossUserPermission using the default
                            // values.
                            allowedDpcTypes =
                                    @AllowedDpcTypes(
                                            deviceOwner = DISALLOWED,
                                            managedProfileOwnerOfOrganizationOwnedDevice =
                                                    DISALLOWED,
                                            managedProfileOwnerOfPersonalOwnedDevice = DISALLOWED,
                                            unaffiliatedFullUserProfileOwner = DISALLOWED,
                                            affiliatedFullUserProfileOwner = SAME_AS_UNAFFILIATED)),
            resolutionMechanism = @IntegerResolutionMechanism(custom = true))
    public static final PolicyIdentifier<Integer>
            TEST_AFFILIATED_PROFILE_OWNER_ON_USER_SAME_AS_UNAFFILIATED_DISALLOWED =
                    new PolicyIdentifier<>(
                            "TEST_AFFILIATED_PROFILE_OWNER_ON_USER_SAME_AS_UNAFFILIATED_DISALLOWED");

    /** Test policy verifying processing of multiple allowed DPC types. */
    @IntegerPolicyDefinition(
            base =
                    @PolicyDefinition(
                            allowedScopes = {
                                1 // POLICY_SCOPE_USER
                            },
                            affectedResource = 1, // RESOURCE_DEVICE_WIDE
                            // requiredPermission and requiredCrossUserPermission using the default
                            // values.
                            allowedDpcTypes =
                                    @AllowedDpcTypes(
                                            deviceOwner = ALLOWED,
                                            managedProfileOwnerOfOrganizationOwnedDevice = ALLOWED,
                                            managedProfileOwnerOfPersonalOwnedDevice = ALLOWED,
                                            unaffiliatedFullUserProfileOwner = DISALLOWED,
                                            affiliatedFullUserProfileOwner = ALLOWED)),
            resolutionMechanism = @IntegerResolutionMechanism(custom = true))
    public static final PolicyIdentifier<Integer> TEST_MULTIPLE_DPC_TYPES_ALLOWED =
            new PolicyIdentifier<>("TEST_MULTIPLE_DPC_TYPES_ALLOWED");

    /** Enum policy with mostRestrictive resolution mechanism. */
    @EnumPolicyDefinition(
            base =
                    @PolicyDefinition(
                            allowedScopes = {
                                2, // POLICY_SCOPE_DEVICE
                                3 // POLICY_SCOPE_PARENT_USER
                            },
                            affectedResource = 1, // RESOURCE_DEVICE_WIDE
                            requiredPermission = "android.permission.MANAGE_POLICY_SIMPLE_ENUM",
                            requiredCrossUserPermission =
                                    "android.permission.MANAGE_DEVICE_POLICY_ACROSS_USERS",
                            allowedDpcTypes =
                                    @AllowedDpcTypes(
                                            deviceOwner = DISALLOWED,
                                            managedProfileOwnerOfOrganizationOwnedDevice =
                                                    DISALLOWED,
                                            managedProfileOwnerOfPersonalOwnedDevice = DISALLOWED,
                                            unaffiliatedFullUserProfileOwner = DISALLOWED)),
            defaultValue = ENUM_ENTRY_2,
            intDef = SimpleEnumPolicyEnum.class,
            resolutionMechanism =
                    @EnumResolutionMechanism(
                            mostRestrictive = {ENUM_ENTRY_1, ENUM_ENTRY_2, ENUM_ENTRY_3}))
    public static final PolicyIdentifier<Integer> MOST_RESTRICTIVE_ENUM_POLICY =
            new PolicyIdentifier<>("MOST_RESTRICTIVE_ENUM_POLICY");

    /** Test policy that is flagged. */
    @FlaggedApi("my.flagged.api")
    @IntegerPolicyDefinition(
            base =
                    @PolicyDefinition(
                            allowedScopes = {
                                1 // POLICY_SCOPE_USER
                            },
                            affectedResource = 1, // RESOURCE_DEVICE_WIDE
                            allowedDpcTypes =
                                    @AllowedDpcTypes(
                                            deviceOwner = DISALLOWED,
                                            managedProfileOwnerOfOrganizationOwnedDevice =
                                                    DISALLOWED,
                                            managedProfileOwnerOfPersonalOwnedDevice = DISALLOWED,
                                            unaffiliatedFullUserProfileOwner = DISALLOWED)),
            resolutionMechanism = @IntegerResolutionMechanism(custom = true))
    public static final PolicyIdentifier<Integer> FLAGGED_POLICY =
            new PolicyIdentifier<>("FLAGGED_POLICY");

    /** Enum policy with notCoexistent resolution mechanism. */
    @EnumPolicyDefinition(
            base =
                    @PolicyDefinition(
                            allowedScopes = {
                                2, // POLICY_SCOPE_DEVICE
                                3 // POLICY_SCOPE_PARENT_USER
                            },
                            affectedResource = 1, // RESOURCE_DEVICE_WIDE
                            requiredPermission = "android.permission.MANAGE_POLICY_SIMPLE_ENUM",
                            requiredCrossUserPermission =
                                    "android.permission.MANAGE_DEVICE_POLICY_ACROSS_USERS",
                            allowedDpcTypes =
                                    @AllowedDpcTypes(
                                            deviceOwner = DISALLOWED,
                                            managedProfileOwnerOfOrganizationOwnedDevice =
                                                    DISALLOWED,
                                            managedProfileOwnerOfPersonalOwnedDevice = DISALLOWED,
                                            unaffiliatedFullUserProfileOwner = DISALLOWED)),
            defaultValue = ENUM_ENTRY_2,
            intDef = SimpleEnumPolicyEnum.class,
            resolutionMechanism = @EnumResolutionMechanism(notCoexistable = true))
    public static final PolicyIdentifier<Integer> NOT_COEXISTANT_ENUM_POLICY =
            new PolicyIdentifier<>("NOT_COEXISTANT_ENUM_POLICY");
}
