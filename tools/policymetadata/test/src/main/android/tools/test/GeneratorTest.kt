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

package android.processor.devicepolicy.test

import android.processor.devicepolicy.protos.PolicyMetadata
import android.processor.devicepolicy.protos.PolicyMetadataList
import android.processor.devicepolicy.protos.TypeSpecificPolicyMetadata
import android.processor.devicepolicy.protos.TypeSpecificPolicyMetadata.EnumPolicyMetadata
import android.processor.devicepolicy.protos.TypeSpecificPolicyMetadata.ListPolicyMetadata
import android.tools.policymetadata.Generator
import org.junit.Test

// Contains tests that apply to the policy metadata generator for all policy types.
// Type specific tests should go in the `<Type>GeneratorTest.kt` files.
class GeneratorTest {

    private fun boolTestPolicy(name: String): PolicyMetadata.Builder =
        PolicyMetadata.newBuilder()
            .setIdentifier(simpleNameToFieldName(name))
            .setTypeSpecificMetadata(
                TypeSpecificPolicyMetadata.newBuilder()
                    .setBooleanMetadata(
                        TypeSpecificPolicyMetadata.BooleanPolicyMetadata.newBuilder()
                    )
            )

    @Test
    fun test_twoPolicies_outputMatches() {
        val policyList =
            PolicyMetadataList.newBuilder()
                .addPolicyMetadata(
                    boolTestPolicy("test.package.PolicyContainer.MY_TEST_BOOL_POLICY")
                        .addAllAllowedScopes(
                            listOf(
                                PolicyMetadata.PolicyScope.POLICY_SCOPE_USER,
                                PolicyMetadata.PolicyScope.POLICY_SCOPE_PARENT_USER,
                            )
                        )
                        .setAffectedResource(PolicyMetadata.ResourceType.RESOURCE_DEVICE_WIDE)
                )
                .addPolicyMetadata(
                    boolTestPolicy("test.package.PolicyContainer.MY_SECOND_TEST_BOOL_POLICY")
                        .addAllAllowedScopes(
                            listOf(
                                PolicyMetadata.PolicyScope.POLICY_SCOPE_DEVICE,
                                PolicyMetadata.PolicyScope.POLICY_SCOPE_PARENT_USER,
                            )
                        )
                        .setAffectedResource(PolicyMetadata.ResourceType.RESOURCE_PER_USER)
                )
                .build()

        val javaFile = Generator.generate(policyList)

        javaFile.assertContainsPolicy(
            staticImports =
                listOf(
                    "test.package.PolicyContainer.MY_TEST_BOOL_POLICY",
                    "test.package.PolicyContainer.MY_SECOND_TEST_BOOL_POLICY",
                ),
            code =
                """
                  policies.add(new BooleanPolicyMetadata(
                      /* id= */ MY_TEST_BOOL_POLICY,
                      /* allowedScopes= */ Set.of(
                          1,
                          3
                      ),
                      /* affectedResource= */ 1,
                      /* requiredPermission= */ null,
                      /* requiredCrossUserPermission= */ null,
                      /* allowedDpcTypes= */ Set.of()
                  ));
                  policies.add(new BooleanPolicyMetadata(
                      /* id= */ MY_SECOND_TEST_BOOL_POLICY,
                      /* allowedScopes= */ Set.of(
                          2,
                          3
                      ),
                      /* affectedResource= */ 2,
                      /* requiredPermission= */ null,
                      /* requiredCrossUserPermission= */ null,
                      /* allowedDpcTypes= */ Set.of()
                  ));
                """,
        )
    }

    private fun listOfEnumTestPolicy(name: String, enumValues: Set<Int>): PolicyMetadata.Builder =
        PolicyMetadata.newBuilder()
            .setIdentifier(simpleNameToFieldName(name))
            .setTypeSpecificMetadata(
                TypeSpecificPolicyMetadata.newBuilder()
                    .setListMetadata(
                        TypeSpecificPolicyMetadata.ListPolicyMetadata.newBuilder()
                            .setEnumMetadata(
                                EnumPolicyMetadata.newBuilder()
                                    .addAllValues(
                                        enumValues.map {
                                            EnumPolicyMetadata.EnumValue.newBuilder()
                                                .setIntValue(it)
                                                .build()
                                        }
                                    )
                                    .setResolutionMechanism(
                                        EnumPolicyMetadata.ResolutionMechanism.newBuilder()
                                            .setCustom(true)
                                            .build()
                                    )
                            )
                            .setResolutionMechanism(
                                ListPolicyMetadata.ResolutionMechanism.newBuilder()
                                    .setCustom(true)
                                    .build()
                            )
                    )
            )

    @Test
    fun test_listOfEnumPolicy_outputMatches() {
        val javaFile =
            Generator.generate(
                listOfEnumTestPolicy(
                        "test.package.PolicyContainer.MY_TEST_ENUM_LIST_POLICY",
                        enumValues = setOf(1, 9, 17),
                    )
                    .addAllAllowedScopes(listOf(PolicyMetadata.PolicyScope.POLICY_SCOPE_DEVICE))
                    .setAffectedResource(PolicyMetadata.ResourceType.RESOURCE_DEVICE_WIDE)
            )

        javaFile.assertContainsPolicy(
            includes = listOf("android.app.admin.PolicyIdentifier", "java.lang.Integer"),
            staticImports = listOf("test.package.PolicyContainer.MY_TEST_ENUM_LIST_POLICY"),
            code =
                """
                  policies.add(new ListPolicyMetadata<Integer>(
                      /* id= */ MY_TEST_ENUM_LIST_POLICY,
                      /* elementMetadata= */ new EnumPolicyMetadata(
                          /* id= */ new PolicyIdentifier<Integer>(MY_TEST_ENUM_LIST_POLICY.getId() + "#elements"),
                          /* allowedScopes= */ Set.of(
                              2
                          ),
                          /* affectedResource= */ 1,
                          /* requiredPermission= */ null,
                          /* requiredCrossUserPermission= */ null,
                          /* allowedDpcTypes= */ Set.of(),
                          /* resolutionMechanism= */ null,
                          /* allowedValues= */ Set.of(
                            1,
                            9,
                            17
                          )
                      ),
                      /* resolutionMechanism= */ null,
                      /* emptyListAllowed= */ false
                  ));
                """,
        )
    }

    private fun listOfPackageTestPolicy(name: String): PolicyMetadata.Builder =
        PolicyMetadata.newBuilder()
            .setIdentifier(simpleNameToFieldName(name))
            .setTypeSpecificMetadata(
                TypeSpecificPolicyMetadata.newBuilder()
                    .setListMetadata(
                        TypeSpecificPolicyMetadata.ListPolicyMetadata.newBuilder()
                            .setPackageMetadata(
                                TypeSpecificPolicyMetadata.PackagePolicyMetadata.newBuilder()
                            )
                            .setEmptyListAllowed(true)
                            .setResolutionMechanism(
                                ListPolicyMetadata.ResolutionMechanism.newBuilder()
                                    .setCustom(true)
                                    .build()
                            )
                    )
            )

    @Test
    fun test_permissions_outputMatches() {
        val javaFile =
            Generator.generate(
                boolTestPolicy("test.package.PolicyContainer.MY_TEST_BOOL_POLICY")
                    .addAllAllowedScopes(
                        listOf(
                            PolicyMetadata.PolicyScope.POLICY_SCOPE_USER,
                            PolicyMetadata.PolicyScope.POLICY_SCOPE_PARENT_USER,
                        )
                    )
                    .setAffectedResource(PolicyMetadata.ResourceType.RESOURCE_DEVICE_WIDE)
                    .setRequiredPermission("test_permission")
                    .setRequiredCrossUserPermission("test_cross_permission")
            )

        javaFile.assertContainsPolicy(
            staticImports = listOf("test.package.PolicyContainer.MY_TEST_BOOL_POLICY"),
            code =
                """
                  policies.add(new BooleanPolicyMetadata(
                      /* id= */ MY_TEST_BOOL_POLICY,
                      /* allowedScopes= */ Set.of(
                          1,
                          3
                      ),
                      /* affectedResource= */ 1,
                      /* requiredPermission= */ "test_permission",
                      /* requiredCrossUserPermission= */ "test_cross_permission",
                      /* allowedDpcTypes= */ Set.of()
                  ));
                """,
        )
    }

    @Test
    fun test_allowedDpcTypes_outputMatches() {
        val javaFile =
            Generator.generate(
                boolTestPolicy("test.package.MY_TEST_POLICY")
                    .addAllAllowedScopes(listOf(PolicyMetadata.PolicyScope.POLICY_SCOPE_USER))
                    .setAffectedResource(PolicyMetadata.ResourceType.RESOURCE_DEVICE_WIDE)
                    .addAllAllowedDpcTypes(
                        listOf(
                            PolicyMetadata.DpcType.DPC_TYPE_DEVICE_OWNER,
                            PolicyMetadata.DpcType
                                .DPC_TYPE_MANAGED_PROFILE_OWNER_OF_PERSONAL_OWNED_DEVICE,
                        )
                    )
            )

        javaFile.assertContainsPolicy(
            staticImports = listOf("test.package.MY_TEST_POLICY"),
            code =
                """
                  policies.add(new BooleanPolicyMetadata(
                      /* id= */ MY_TEST_POLICY,
                      /* allowedScopes= */ Set.of(
                          1
                      ),
                      /* affectedResource= */ 1,
                      /* requiredPermission= */ null,
                      /* requiredCrossUserPermission= */ null,
                      /* allowedDpcTypes= */ Set.of(
                          1, // DEVICE_OWNER
                          5  // MANAGED_PROFILE_OWNER_OF_PERSONAL_OWNED_DEVICE
                      )
                  ));
                """,
        )
    }

    @Test
    fun test_allAllowedDpcTypes_outputMatches() {
        val javaFile =
            Generator.generate(
                boolTestPolicy("test.package.MY_TEST_POLICY")
                    .addAllAllowedScopes(listOf(PolicyMetadata.PolicyScope.POLICY_SCOPE_USER))
                    .setAffectedResource(PolicyMetadata.ResourceType.RESOURCE_DEVICE_WIDE)
                    .addAllAllowedDpcTypes(
                        listOf(
                            PolicyMetadata.DpcType.DPC_TYPE_DEVICE_OWNER,
                            PolicyMetadata.DpcType.DPC_TYPE_FINANCED_DEVICE_OWNER,
                            PolicyMetadata.DpcType
                                .DPC_TYPE_MANAGED_PROFILE_OWNER_OF_ORGANIZATION_OWNED_DEVICE,
                            PolicyMetadata.DpcType.DPC_TYPE_PROFILE_OWNER_ON_USER0,
                            PolicyMetadata.DpcType
                                .DPC_TYPE_MANAGED_PROFILE_OWNER_OF_PERSONAL_OWNED_DEVICE,
                            PolicyMetadata.DpcType.DPC_TYPE_UNAFFILIATED_FULL_USER_PROFILE_OWNER,
                            PolicyMetadata.DpcType.DPC_TYPE_AFFILIATED_FULL_USER_PROFILE_OWNER,
                        )
                    )
            )

        javaFile.assertContainsPolicy(
            staticImports = listOf("test.package.MY_TEST_POLICY"),
            code =
                """
                  policies.add(new BooleanPolicyMetadata(
                      /* id= */ MY_TEST_POLICY,
                      /* allowedScopes= */ Set.of(
                          1
                      ),
                      /* affectedResource= */ 1,
                      /* requiredPermission= */ null,
                      /* requiredCrossUserPermission= */ null,
                      /* allowedDpcTypes= */ Set.of(
                          1, // DEVICE_OWNER
                          2, // FINANCED_DEVICE_OWNER
                          3, // MANAGED_PROFILE_OWNER_OF_ORGANIZATION_OWNED_DEVICE
                          4, // PROFILE_OWNER_ON_USER0
                          5, // MANAGED_PROFILE_OWNER_OF_PERSONAL_OWNED_DEVICE
                          6, // UNAFFILIATED_FULL_USER_PROFILE_OWNER
                          7  // AFFILIATED_FULL_USER_PROFILE_OWNER
                      )
                  ));
                """,
        )
    }
}
