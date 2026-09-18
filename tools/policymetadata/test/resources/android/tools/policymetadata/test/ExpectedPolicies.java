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

package android.app.admin.metadata;

import static android.app.admin.PolicyIdentifier.FLAGGED_POLICY;
import static android.app.admin.PolicyIdentifier.MOST_RESTRICTIVE_ENUM_POLICY;
import static android.app.admin.PolicyIdentifier.NOT_COEXISTANT_ENUM_POLICY;
import static android.app.admin.PolicyIdentifier.SIMPLE_BOOLEAN_POLICY;
import static android.app.admin.PolicyIdentifier.SIMPLE_ENUM_POLICY;
import static android.app.admin.PolicyIdentifier.SIMPLE_INTEGER_POLICY;
import static android.app.admin.PolicyIdentifier.SIMPLE_INTEGER_POLICY_WITH_RANGE;
import static android.app.admin.PolicyIdentifier.SIMPLE_LONG_POLICY;
import static android.app.admin.PolicyIdentifier.SIMPLE_LONG_POLICY_WITH_RANGE;
import static android.app.admin.PolicyIdentifier.SIMPLE_PACKAGE_LIST_POLICY;
import static android.app.admin.PolicyIdentifier.SIMPLE_PACKAGE_POLICY;
import static android.app.admin.PolicyIdentifier.SIMPLE_STRING_LIST_POLICY;
import static android.app.admin.PolicyIdentifier.SIMPLE_STRING_POLICY;
import static android.app.admin.PolicyIdentifier.STRING_POLICY_WITH_MAX_LENGTH;
import static android.app.admin.PolicyIdentifier.TEST_AFFILIATED_PROFILE_OWNER_ON_USER_ALLOWED;
import static android.app.admin.PolicyIdentifier.TEST_AFFILIATED_PROFILE_OWNER_ON_USER_SAME_AS_UNAFFILIATED;
import static android.app.admin.PolicyIdentifier.TEST_AFFILIATED_PROFILE_OWNER_ON_USER_SAME_AS_UNAFFILIATED_DISALLOWED;
import static android.app.admin.PolicyIdentifier.TEST_DEFAULT_DEVICE_OWNER_ALLOWED;
import static android.app.admin.PolicyIdentifier.TEST_FINANCED_DEVICE_OWNER_ALLOWED;
import static android.app.admin.PolicyIdentifier.TEST_MULTIPLE_DPC_TYPES_ALLOWED;
import static android.app.admin.PolicyIdentifier.TEST_PROFILE_OWNER_ALLOWED;
import static android.app.admin.PolicyIdentifier.TEST_PROFILE_OWNER_OF_ORGANIZATION_OWNED_DEVICE_ALLOWED;
import static android.app.admin.PolicyIdentifier.TEST_PROFILE_OWNER_ON_USER0_ALLOWED;
import static android.app.admin.PolicyIdentifier.TEST_PROFILE_OWNER_ON_USER_ALLOWED;

import android.app.admin.PackageIdentifier;
import android.app.admin.PolicyIdentifier;
import java.lang.Integer;
import java.lang.String;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Generated class that contains metadata on all known policies.
 *
 * @hide
 */
public class Policies {
    /**
     * Generated method that returns a list of all policy metadata
     */
    public static List<PolicyMetadata<?>> loadPolicyMetadata() {
        List<PolicyMetadata<?>> policies = new ArrayList<PolicyMetadata<?>>();
        policies.add(new BooleanPolicyMetadata(
            /* id= */ SIMPLE_BOOLEAN_POLICY,
            /* allowedScopes= */ Set.of(
                1,
                2
            ),
            /* affectedResource= */ 2,
            /* requiredPermission= */ "android.permission.MANAGE_POLICY_SIMPLE_BOOLEAN",
            /* requiredCrossUserPermission= */ "android.permission.MANAGE_DEVICE_POLICY_ACROSS_USERS_FULL",
            /* allowedDpcTypes= */ Set.of()
        ));
        policies.add(new EnumPolicyMetadata(
            /* id= */ SIMPLE_ENUM_POLICY,
            /* allowedScopes= */ Set.of(
                2,
                3
            ),
            /* affectedResource= */ 1,
            /* requiredPermission= */ "android.permission.MANAGE_POLICY_SIMPLE_ENUM",
            /* requiredCrossUserPermission= */ "android.permission.MANAGE_DEVICE_POLICY_ACROSS_USERS",
            /* allowedDpcTypes= */ Set.of(),
            /* resolutionMechanism= */ null,
            /* allowedValues= */ Set.of(
                0,
                1,
                2
            )
        ));
        policies.add(new EnumPolicyMetadata(
            /* id= */ MOST_RESTRICTIVE_ENUM_POLICY,
            /* allowedScopes= */ Set.of(
                2,
                3
            ),
            /* affectedResource= */ 1,
            /* requiredPermission= */ "android.permission.MANAGE_POLICY_SIMPLE_ENUM",
            /* requiredCrossUserPermission= */ "android.permission.MANAGE_DEVICE_POLICY_ACROSS_USERS",
            /* allowedDpcTypes= */ Set.of(),
            /* resolutionMechanism= */ new ResolutionMechanismMetadata.MostRestrictive<Integer>(
                List.of(
                    new Integer(0),
                    new Integer(1),
                    new Integer(2)
                )
            ),
            /* allowedValues= */ Set.of(
                0,
                1,
                2
            )
        ));
        policies.add(new IntegerPolicyMetadata(
            /* id= */ SIMPLE_INTEGER_POLICY,
            /* allowedScopes= */ Set.of(
                1
            ),
            /* affectedResource= */ 1,
            /* requiredPermission= */ null,
            /* requiredCrossUserPermission= */ null,
            /* allowedDpcTypes= */ Set.of(),
            /* resolutionMechanism= */ null,
            /* minValue= */ Integer.MIN_VALUE,
            /* maxValue= */ Integer.MAX_VALUE
        ));
        policies.add(new IntegerPolicyMetadata(
            /* id= */ SIMPLE_INTEGER_POLICY_WITH_RANGE,
            /* allowedScopes= */ Set.of(
                1
            ),
            /* affectedResource= */ 1,
            /* requiredPermission= */ null,
            /* requiredCrossUserPermission= */ null,
            /* allowedDpcTypes= */ Set.of(),
            /* resolutionMechanism= */ null,
            /* minValue= */ -100,
            /* maxValue= */ 100
        ));
        policies.add(new IntegerPolicyMetadata(
            /* id= */ TEST_DEFAULT_DEVICE_OWNER_ALLOWED,
            /* allowedScopes= */ Set.of(
                1
            ),
            /* affectedResource= */ 1,
            /* requiredPermission= */ null,
            /* requiredCrossUserPermission= */ null,
            /* allowedDpcTypes= */ Set.of(
                1  // DEVICE_OWNER
            ),
            /* resolutionMechanism= */ null,
            /* minValue= */ Integer.MIN_VALUE,
            /* maxValue= */ Integer.MAX_VALUE
        ));
        policies.add(new IntegerPolicyMetadata(
            /* id= */ TEST_FINANCED_DEVICE_OWNER_ALLOWED,
            /* allowedScopes= */ Set.of(
                1
            ),
            /* affectedResource= */ 1,
            /* requiredPermission= */ null,
            /* requiredCrossUserPermission= */ null,
            /* allowedDpcTypes= */ Set.of(
                2  // FINANCED_DEVICE_OWNER
            ),
            /* resolutionMechanism= */ null,
            /* minValue= */ Integer.MIN_VALUE,
            /* maxValue= */ Integer.MAX_VALUE
        ));
        policies.add(new IntegerPolicyMetadata(
            /* id= */ TEST_PROFILE_OWNER_OF_ORGANIZATION_OWNED_DEVICE_ALLOWED,
            /* allowedScopes= */ Set.of(
                1
            ),
            /* affectedResource= */ 1,
            /* requiredPermission= */ null,
            /* requiredCrossUserPermission= */ null,
            /* allowedDpcTypes= */ Set.of(
                3  // MANAGED_PROFILE_OWNER_OF_ORGANIZATION_OWNED_DEVICE
            ),
            /* resolutionMechanism= */ null,
            /* minValue= */ Integer.MIN_VALUE,
            /* maxValue= */ Integer.MAX_VALUE
        ));
        policies.add(new IntegerPolicyMetadata(
            /* id= */ TEST_PROFILE_OWNER_ON_USER0_ALLOWED,
            /* allowedScopes= */ Set.of(
                1
            ),
            /* affectedResource= */ 1,
            /* requiredPermission= */ null,
            /* requiredCrossUserPermission= */ null,
            /* allowedDpcTypes= */ Set.of(
                4  // PROFILE_OWNER_ON_USER0
            ),
            /* resolutionMechanism= */ null,
            /* minValue= */ Integer.MIN_VALUE,
            /* maxValue= */ Integer.MAX_VALUE
        ));
        policies.add(new IntegerPolicyMetadata(
            /* id= */ TEST_PROFILE_OWNER_ALLOWED,
            /* allowedScopes= */ Set.of(
                1
            ),
            /* affectedResource= */ 1,
            /* requiredPermission= */ null,
            /* requiredCrossUserPermission= */ null,
            /* allowedDpcTypes= */ Set.of(
                5  // MANAGED_PROFILE_OWNER_OF_PERSONAL_OWNED_DEVICE
            ),
            /* resolutionMechanism= */ null,
            /* minValue= */ Integer.MIN_VALUE,
            /* maxValue= */ Integer.MAX_VALUE
        ));
        policies.add(new IntegerPolicyMetadata(
            /* id= */ TEST_PROFILE_OWNER_ON_USER_ALLOWED,
            /* allowedScopes= */ Set.of(
                1
            ),
            /* affectedResource= */ 1,
            /* requiredPermission= */ null,
            /* requiredCrossUserPermission= */ null,
            /* allowedDpcTypes= */ Set.of(
                6, // UNAFFILIATED_FULL_USER_PROFILE_OWNER
                7  // AFFILIATED_FULL_USER_PROFILE_OWNER
            ),
            /* resolutionMechanism= */ null,
            /* minValue= */ Integer.MIN_VALUE,
            /* maxValue= */ Integer.MAX_VALUE
        ));
        policies.add(new IntegerPolicyMetadata(
            /* id= */ TEST_AFFILIATED_PROFILE_OWNER_ON_USER_ALLOWED,
            /* allowedScopes= */ Set.of(
                1
            ),
            /* affectedResource= */ 1,
            /* requiredPermission= */ null,
            /* requiredCrossUserPermission= */ null,
            /* allowedDpcTypes= */ Set.of(
                7  // AFFILIATED_FULL_USER_PROFILE_OWNER
            ),
            /* resolutionMechanism= */ null,
            /* minValue= */ Integer.MIN_VALUE,
            /* maxValue= */ Integer.MAX_VALUE
        ));
        policies.add(new IntegerPolicyMetadata(
            /* id= */ TEST_AFFILIATED_PROFILE_OWNER_ON_USER_SAME_AS_UNAFFILIATED,
            /* allowedScopes= */ Set.of(
                1
            ),
            /* affectedResource= */ 1,
            /* requiredPermission= */ null,
            /* requiredCrossUserPermission= */ null,
            /* allowedDpcTypes= */ Set.of(
                6, // UNAFFILIATED_FULL_USER_PROFILE_OWNER
                7  // AFFILIATED_FULL_USER_PROFILE_OWNER
            ),
            /* resolutionMechanism= */ null,
            /* minValue= */ Integer.MIN_VALUE,
            /* maxValue= */ Integer.MAX_VALUE
        ));
        policies.add(new IntegerPolicyMetadata(
            /* id= */ TEST_AFFILIATED_PROFILE_OWNER_ON_USER_SAME_AS_UNAFFILIATED_DISALLOWED,
            /* allowedScopes= */ Set.of(
                1
            ),
            /* affectedResource= */ 1,
            /* requiredPermission= */ null,
            /* requiredCrossUserPermission= */ null,
            /* allowedDpcTypes= */ Set.of(),
            /* resolutionMechanism= */ null,
            /* minValue= */ Integer.MIN_VALUE,
            /* maxValue= */ Integer.MAX_VALUE
        ));
        policies.add(new IntegerPolicyMetadata(
            /* id= */ TEST_MULTIPLE_DPC_TYPES_ALLOWED,
            /* allowedScopes= */ Set.of(
                1
            ),
            /* affectedResource= */ 1,
            /* requiredPermission= */ null,
            /* requiredCrossUserPermission= */ null,
            /* allowedDpcTypes= */ Set.of(
                1, // DEVICE_OWNER
                3, // MANAGED_PROFILE_OWNER_OF_ORGANIZATION_OWNED_DEVICE
                5, // MANAGED_PROFILE_OWNER_OF_PERSONAL_OWNED_DEVICE
                7  // AFFILIATED_FULL_USER_PROFILE_OWNER
            ),
            /* resolutionMechanism= */ null,
            /* minValue= */ Integer.MIN_VALUE,
            /* maxValue= */ Integer.MAX_VALUE
        ));
        policies.add(new IntegerPolicyMetadata(
            /* id= */ FLAGGED_POLICY,
            /* allowedScopes= */ Set.of(
                1
            ),
            /* affectedResource= */ 1,
            /* requiredPermission= */ null,
            /* requiredCrossUserPermission= */ null,
            /* allowedDpcTypes= */ Set.of(),
            /* resolutionMechanism= */ null,
            /* minValue= */ Integer.MIN_VALUE,
            /* maxValue= */ Integer.MAX_VALUE
        ));
        policies.add(new LongPolicyMetadata(
            /* id= */ SIMPLE_LONG_POLICY,
            /* allowedScopes= */ Set.of(
                1
            ),
            /* affectedResource= */ 1,
            /* requiredPermission= */ null,
            /* requiredCrossUserPermission= */ null,
            /* allowedDpcTypes= */ Set.of(),
            /* resolutionMechanism= */ null,
            /* minValue= */ Long.MIN_VALUE,
            /* maxValue= */ Long.MAX_VALUE
        ));
        policies.add(new LongPolicyMetadata(
            /* id= */ SIMPLE_LONG_POLICY_WITH_RANGE,
            /* allowedScopes= */ Set.of(
                1
            ),
            /* affectedResource= */ 1,
            /* requiredPermission= */ null,
            /* requiredCrossUserPermission= */ null,
            /* allowedDpcTypes= */ Set.of(),
            /* resolutionMechanism= */ null,
            /* minValue= */ 10L,
            /* maxValue= */ 100L
        ));
        policies.add(new StringPolicyMetadata(
            /* id= */ SIMPLE_STRING_POLICY,
            /* allowedScopes= */ Set.of(
                1
            ),
            /* affectedResource= */ 1,
            /* requiredPermission= */ null,
            /* requiredCrossUserPermission= */ null,
            /* allowedDpcTypes= */ Set.of(),
            /* emptyStringAllowed= */ false,
            /* unprintableCharactersAllowed= */ true,
            /* maxLength= */ Integer.MAX_VALUE
        ));
        policies.add(new ListPolicyMetadata<String>(
            /* id= */ SIMPLE_STRING_LIST_POLICY,
            /* elementMetadata= */ new StringPolicyMetadata(
                /* id= */ new PolicyIdentifier<String>(SIMPLE_STRING_LIST_POLICY.getId() + "#elements"),
                /* allowedScopes= */ Set.of(
                    1
                ),
                /* affectedResource= */ 1,
                /* requiredPermission= */ null,
                /* requiredCrossUserPermission= */ null,
                /* allowedDpcTypes= */ Set.of(),
                /* emptyStringAllowed= */ true,
                /* unprintableCharactersAllowed= */ false,
                /* maxLength= */ Integer.MAX_VALUE
            ),
            /* resolutionMechanism= */ null,
            /* emptyListAllowed= */ false
        ));
        policies.add(new StringPolicyMetadata(
            /* id= */ STRING_POLICY_WITH_MAX_LENGTH,
            /* allowedScopes= */ Set.of(
                1
            ),
            /* affectedResource= */ 1,
            /* requiredPermission= */ null,
            /* requiredCrossUserPermission= */ null,
            /* allowedDpcTypes= */ Set.of(),
            /* emptyStringAllowed= */ false,
            /* unprintableCharactersAllowed= */ false,
            /* maxLength= */ 10
        ));
        policies.add(new PackagePolicyMetadata(
            /* id= */ SIMPLE_PACKAGE_POLICY,
            /* allowedScopes= */ Set.of(
                1
            ),
            /* affectedResource= */ 1,
            /* requiredPermission= */ null,
            /* requiredCrossUserPermission= */ null,
            /* allowedDpcTypes= */ Set.of()
        ));
        policies.add(new EnumPolicyMetadata(
            /* id= */ NOT_COEXISTANT_ENUM_POLICY,
            /* allowedScopes= */ Set.of(
                2,
                3
            ),
            /* affectedResource= */ 1,
            /* requiredPermission= */ "android.permission.MANAGE_POLICY_SIMPLE_ENUM",
            /* requiredCrossUserPermission= */ "android.permission.MANAGE_DEVICE_POLICY_ACROSS_USERS",
            /* allowedDpcTypes= */ Set.of(),
            /* resolutionMechanism= */ new ResolutionMechanismMetadata.NotCoexistable(),
            /* allowedValues= */ Set.of(
                0,
                1,
                2
            )
        ));
        policies.add(new ListPolicyMetadata<PackageIdentifier>(
            /* id= */ SIMPLE_PACKAGE_LIST_POLICY,
            /* elementMetadata= */ new PackagePolicyMetadata(
                /* id= */ new PolicyIdentifier<PackageIdentifier>(SIMPLE_PACKAGE_LIST_POLICY.getId() + "#elements"),
                /* allowedScopes= */ Set.of(
                    1
                ),
                /* affectedResource= */ 1,
                /* requiredPermission= */ null,
                /* requiredCrossUserPermission= */ null,
                /* allowedDpcTypes= */ Set.of()
            ),
            /* resolutionMechanism= */ null,
            /* emptyListAllowed= */ false
        ));
        return policies;
    }
}
