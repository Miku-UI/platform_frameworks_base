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
import android.processor.devicepolicy.protos.TypeSpecificPolicyMetadata.StringPolicyMetadata
import android.tools.policymetadata.Generator
import org.junit.Test

class StringGeneratorTest {

    private fun stringTestPolicy(name: String): PolicyMetadata.Builder =
        PolicyMetadata.newBuilder()
            .setIdentifier(simpleNameToFieldName(name))
            .setTypeSpecificMetadata(
                TypeSpecificPolicyMetadata.newBuilder()
                    .setStringMetadata(TypeSpecificPolicyMetadata.StringPolicyMetadata.newBuilder())
            )

    @Test
    fun test_outputMatches() {
        val javaFile =
            Generator.generate(
                stringTestPolicy("test.package.PolicyContainer.MY_TEST_STRING_POLICY")
                    .addAllAllowedScopes(listOf(PolicyMetadata.PolicyScope.POLICY_SCOPE_DEVICE))
                    .setAffectedResource(PolicyMetadata.ResourceType.RESOURCE_DEVICE_WIDE)
            )

        javaFile.assertContainsPolicy(
            staticImports = listOf("test.package.PolicyContainer.MY_TEST_STRING_POLICY"),
            code =
                """
                  policies.add(new StringPolicyMetadata(
                      /* id= */ MY_TEST_STRING_POLICY,
                      /* allowedScopes= */ Set.of(
                          2
                      ),
                      /* affectedResource= */ 1,
                      /* requiredPermission= */ null,
                      /* requiredCrossUserPermission= */ null,
                      /* allowedDpcTypes= */ Set.of(),
                      /* emptyStringAllowed= */ false,
                      /* unprintableCharactersAllowed= */ false,
                      /* maxLength= */ Integer.MAX_VALUE
                  ));
                """,
        )
    }

    @Test
    fun test_withMaxLength_outputMatches() {
        val javaFile =
            Generator.generate(
                stringTestPolicy("test.package.PolicyContainer.MY_TEST_STRING_POLICY")
                    .setTypeSpecificMetadata(
                        TypeSpecificPolicyMetadata.newBuilder()
                            .setStringMetadata(
                                TypeSpecificPolicyMetadata.StringPolicyMetadata.newBuilder()
                                    .setMaxLength(10)
                            )
                    )
                    .addAllAllowedScopes(listOf(PolicyMetadata.PolicyScope.POLICY_SCOPE_DEVICE))
                    .setAffectedResource(PolicyMetadata.ResourceType.RESOURCE_DEVICE_WIDE)
            )

        javaFile.assertContainsPolicy(
            staticImports = listOf("test.package.PolicyContainer.MY_TEST_STRING_POLICY"),
            code =
                """
                    policies.add(new StringPolicyMetadata(
                        /* id= */ MY_TEST_STRING_POLICY,
                        /* allowedScopes= */ Set.of(
                            2
                        ),
                        /* affectedResource= */ 1,
                        /* requiredPermission= */ null,
                        /* requiredCrossUserPermission= */ null,
                        /* allowedDpcTypes= */ Set.of(),
                        /* emptyStringAllowed= */ false,
                        /* unprintableCharactersAllowed= */ false,
                        /* maxLength= */ 10
                    ));
                """,
        )
    }
}
