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
import android.tools.policymetadata.Generator
import org.junit.Test

class LongGeneratorTest {

    private fun longTestPolicy(name: String): PolicyMetadata.Builder =
        PolicyMetadata.newBuilder().apply {
            identifier = simpleNameToFieldName(name)
            typeSpecificMetadataBuilder.longMetadataBuilder.resolutionMechanismBuilder.custom = true
        }

    @Test
    fun test_outputMatches() {
        val javaFile =
            Generator.generate(
                longTestPolicy("test.package.PolicyContainer.MY_TEST_LONG_POLICY").apply {
                    addAllowedScopes(PolicyMetadata.PolicyScope.POLICY_SCOPE_DEVICE)
                    affectedResource = PolicyMetadata.ResourceType.RESOURCE_DEVICE_WIDE
                }
            )

        javaFile.assertContainsPolicy(
            staticImports = listOf("test.package.PolicyContainer.MY_TEST_LONG_POLICY"),
            code =
                """
                  policies.add(new LongPolicyMetadata(
                      /* id= */ MY_TEST_LONG_POLICY,
                      /* allowedScopes= */ Set.of(
                          2
                      ),
                      /* affectedResource= */ 1,
                      /* requiredPermission= */ null,
                      /* requiredCrossUserPermission= */ null,
                      /* allowedDpcTypes= */ Set.of(),
                      /* resolutionMechanism= */ null,
                      /* minValue= */ Long.MIN_VALUE,
                      /* maxValue= */ Long.MAX_VALUE
                  ));
                """,
        )
    }

    @Test
    fun test_withMinMax_outputMatches() {
        val javaFile =
            Generator.generate(
                longTestPolicy("test.package.PolicyContainer.LONG_POLICY").apply {
                    typeSpecificMetadataBuilder.longMetadataBuilder.apply {
                        minValue = 10L
                        maxValue = 100L
                    }
                }
            )

        javaFile.assertContainsPolicy(
            staticImports = listOf("test.package.PolicyContainer.LONG_POLICY"),
            code =
                """
                  policies.add(new LongPolicyMetadata(
                      /* id= */ LONG_POLICY,
                      /* allowedScopes= */ Set.of(),
                      /* affectedResource= */ 0,
                      /* requiredPermission= */ null,
                      /* requiredCrossUserPermission= */ null,
                      /* allowedDpcTypes= */ Set.of(),
                      /* resolutionMechanism= */ null,
                      /* minValue= */ 10L,
                      /* maxValue= */ 100L
                  ));
                """,
        )
    }

    @Test
    fun test_resolutionMechanismNotCoexistable_outputMatches() {
        val javaFile =
            Generator.generate(
                longTestPolicy("test.package.MY_TEST_LONG_POLICY").apply {
                    typeSpecificMetadataBuilder.longMetadataBuilder.resolutionMechanismBuilder
                        .notCoexistable = true
                }
            )

        javaFile.assertContainsPolicy(
            staticImports = listOf("test.package.MY_TEST_LONG_POLICY"),
            code =
                """
                  policies.add(new LongPolicyMetadata(
                      /* id= */ MY_TEST_LONG_POLICY,
                      /* allowedScopes= */ Set.of(),
                      /* affectedResource= */ 0,
                      /* requiredPermission= */ null,
                      /* requiredCrossUserPermission= */ null,
                      /* allowedDpcTypes= */ Set.of(),
                      /* resolutionMechanism= */ new ResolutionMechanismMetadata.NotCoexistable(),
                      /* minValue= */ Long.MIN_VALUE,
                      /* maxValue= */ Long.MAX_VALUE
                  ));
                """,
        )
    }
}
