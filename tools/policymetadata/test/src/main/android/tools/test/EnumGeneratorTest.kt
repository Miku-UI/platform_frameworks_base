/*
 * Copyright (C) 2026 The Android Open Source Project
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
import android.processor.devicepolicy.protos.TypeSpecificPolicyMetadata
import android.processor.devicepolicy.protos.TypeSpecificPolicyMetadata.EnumPolicyMetadata
import android.tools.policymetadata.Generator
import org.junit.Test

class EnumGeneratorTest {

    private fun enumTestPolicy(
        name: String,
        allowedValues: Set<Int>,
        resolutionMechanism: EnumPolicyMetadata.ResolutionMechanism =
            EnumPolicyMetadata.ResolutionMechanism.newBuilder().setCustom(true).build(),
    ): PolicyMetadata.Builder =
        PolicyMetadata.newBuilder()
            .setIdentifier(simpleNameToFieldName(name))
            .setTypeSpecificMetadata(
                TypeSpecificPolicyMetadata.newBuilder()
                    .setEnumMetadata(
                        EnumPolicyMetadata.newBuilder()
                            .addAllValues(
                                allowedValues.map {
                                    EnumPolicyMetadata.EnumValue.newBuilder()
                                        .setIntValue(it)
                                        .build()
                                }
                            )
                            .setResolutionMechanism(resolutionMechanism)
                    )
            )

    @Test
    fun test_outputMatches() {
        val javaFile =
            Generator.generate(
                enumTestPolicy("test.package.PolicyContainer.MY_TEST_ENUM_POLICY", setOf(1, 5, 7))
                    .addAllAllowedScopes(listOf(PolicyMetadata.PolicyScope.POLICY_SCOPE_DEVICE))
                    .setAffectedResource(PolicyMetadata.ResourceType.RESOURCE_DEVICE_WIDE)
            )

        javaFile.assertContainsPolicy(
            staticImports = listOf("test.package.PolicyContainer.MY_TEST_ENUM_POLICY"),
            code =
                """
                  policies.add(new EnumPolicyMetadata(
                      /* id= */ MY_TEST_ENUM_POLICY,
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
                          5,
                          7
                      )
                  ));
                """,
        )
    }

    @Test
    fun test_mostRestrictive_outputMatches() {
        val javaFile =
            Generator.generate(
                enumTestPolicy(
                        "test.package.PolicyContainer.MY_TEST_ENUM_POLICY",
                        setOf(1, 5, 7),
                        resolutionMechanism =
                            EnumPolicyMetadata.ResolutionMechanism.newBuilder()
                                .setMostRestrictive(
                                    EnumPolicyMetadata.ResolutionMechanism.MostRestrictive
                                        .newBuilder()
                                        .addAllMostToLeastRestrictive(listOf(1, 7, 5))
                                )
                                .build(),
                    )
                    .addAllAllowedScopes(listOf(PolicyMetadata.PolicyScope.POLICY_SCOPE_DEVICE))
                    .setAffectedResource(PolicyMetadata.ResourceType.RESOURCE_DEVICE_WIDE)
            )

        javaFile.assertContainsPolicy(
            staticImports = listOf("test.package.PolicyContainer.MY_TEST_ENUM_POLICY"),
            includes = listOf("java.lang.Integer"),
            code =
                """
                  policies.add(new EnumPolicyMetadata(
                      /* id= */ MY_TEST_ENUM_POLICY,
                      /* allowedScopes= */ Set.of(
                          2
                      ),
                      /* affectedResource= */ 1,
                      /* requiredPermission= */ null,
                      /* requiredCrossUserPermission= */ null,
                      /* allowedDpcTypes= */ Set.of(),
                      /* resolutionMechanism= */ new ResolutionMechanismMetadata.MostRestrictive<Integer>(
                          List.of(
                              new Integer(1),
                              new Integer(7),
                              new Integer(5)
                          )
                      ),
                      /* allowedValues= */ Set.of(
                          1,
                          5,
                          7
                      )
                  ));
                """,
        )
    }
}
