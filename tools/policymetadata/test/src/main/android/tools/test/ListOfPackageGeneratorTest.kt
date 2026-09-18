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
import android.processor.devicepolicy.protos.TypeSpecificPolicyMetadata.ListPolicyMetadata
import android.tools.policymetadata.Generator
import org.junit.Test

class ListOfPackageGeneratorTest {

    private fun listOfPackageTestPolicy(
        name: String,
        resolutionMechanism: ListPolicyMetadata.ResolutionMechanism =
            ListPolicyMetadata.ResolutionMechanism.newBuilder().setCustom(true).build(),
    ): PolicyMetadata.Builder =
        PolicyMetadata.newBuilder()
            .setIdentifier(simpleNameToFieldName(name))
            .setTypeSpecificMetadata(
                TypeSpecificPolicyMetadata.newBuilder()
                    .setListMetadata(
                        TypeSpecificPolicyMetadata.ListPolicyMetadata.newBuilder()
                            .setPackageMetadata(
                                TypeSpecificPolicyMetadata.PackagePolicyMetadata.newBuilder()
                            )
                            .setResolutionMechanism(resolutionMechanism)
                    )
            )

    @Test
    fun test_outputMatches() {
        val javaFile =
            Generator.generate(
                listOfPackageTestPolicy("test.package.PolicyContainer.MY_TEST_PACKAGE_LIST_POLICY")
                    .addAllAllowedScopes(listOf(PolicyMetadata.PolicyScope.POLICY_SCOPE_DEVICE))
                    .setAffectedResource(PolicyMetadata.ResourceType.RESOURCE_DEVICE_WIDE)
            )

        javaFile.assertContainsPolicy(
            includes =
                listOf("android.app.admin.PolicyIdentifier", "android.app.admin.PackageIdentifier"),
            staticImports = listOf("test.package.PolicyContainer.MY_TEST_PACKAGE_LIST_POLICY"),
            code =
                """
                    policies.add(new ListPolicyMetadata<PackageIdentifier>(
                        /* id= */ MY_TEST_PACKAGE_LIST_POLICY,
                        /* elementMetadata= */ new PackagePolicyMetadata(
                            /* id= */ new PolicyIdentifier<PackageIdentifier>(MY_TEST_PACKAGE_LIST_POLICY.getId() + "#elements"),
                            /* allowedScopes= */ Set.of(
                                2
                            ),
                            /* affectedResource= */ 1,
                            /* requiredPermission= */ null,
                            /* requiredCrossUserPermission= */ null,
                            /* allowedDpcTypes= */ Set.of()
                        ),
                        /* resolutionMechanism= */ null,
                        /* emptyListAllowed= */ false
                    ));
                """,
        )
    }
}
