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

package com.android.server.devicepolicy.handlers

import android.app.admin.DevicePolicyManager.NOT_A_DPC
import android.app.admin.PackageIdentifier
import android.app.admin.PackagePolicyValue
import android.app.admin.PolicyIdentifier
import android.app.admin.PolicyValueTransport
import android.app.admin.metadata.PackagePolicyMetadata
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.server.devicepolicy.IPermissionChecker
import com.android.server.devicepolicy.PolicyDefinition
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFailsWith
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

@RunWith(AndroidJUnit4::class)
class PackagePolicyHandlerTest {

    val mockDelegate: PolicyHandler.Delegate =
        mock<PolicyHandler.Delegate> {
            on { getDpcType(any()) } doReturn NOT_A_DPC
            on { getPermissionChecker() } doReturn mock<IPermissionChecker> {}
        }

    fun createHandler(
        key: PolicyIdentifier<PackageIdentifier> = PackagePolicy.key,
        metadata: PackagePolicyMetadata = PackagePolicy.metadata,
        definition: PolicyDefinition<PackageIdentifier> = PackagePolicy.definition,
        delegate: PolicyHandler.Delegate = this.mockDelegate,
    ) = PolicyHandler<PackageIdentifier>(key, metadata, definition, delegate)

    @Test
    fun setPolicyUnchecked_shouldAcceptNull() {
        val definition = PackagePolicy.definition
        val handler = createHandler(definition = definition)

        handler.setPolicyUnchecked(anyCaller, anyScope, null)

        verify(mockDelegate).clearPolicy(anyCaller, definition, anyScope)
    }

    @Test
    fun setPolicyUnchecked_shouldAcceptPackageIdentifier() {
        val definition = PackagePolicy.definition
        val packageIdentifier = PackageIdentifier("com.example.app")
        val handler = createHandler()

        handler.setPolicyUnchecked(
            anyCaller,
            anyScope,
            PolicyValueTransport.packageField(packageIdentifier.createTransport()),
        )

        verify(mockDelegate)
            .storePolicy(
                anyCaller,
                PackagePolicy.definition,
                anyScope,
                PackagePolicyValue(packageIdentifier),
            )
    }

    @Test
    fun setPolicyUnchecked_shouldRejectEmptyPackageName() {
        val handler = createHandler()
        val packageIdentifier = PackageIdentifier("")

        val exception =
            assertFailsWith<IllegalArgumentException> {
                handler.setPolicyUnchecked(
                    anyCaller,
                    anyScope,
                    PolicyValueTransport.packageField(packageIdentifier.createTransport()),
                )
            }

        assertThat(exception.message).contains("Package name is empty")
    }

    @Test
    fun setPolicyUnchecked_shouldRejectPackageNameTooLong() {
        val handler = createHandler()
        val packageNameTooLong = "com.example.app." + "a".repeat(256)
        val packageIdentifier = PackageIdentifier(packageNameTooLong)

        val exception =
            assertFailsWith<IllegalArgumentException> {
                handler.setPolicyUnchecked(
                    anyCaller,
                    anyScope,
                    PolicyValueTransport.packageField(packageIdentifier.createTransport()),
                )
            }

        assertThat(exception.message).contains("Package name too long")
    }

    @Test
    fun setPolicyUnchecked_shouldRejectInvalidPackageNameContainsInvalidCharacters() {
        val handler = createHandler()
        val packageIdentifier = PackageIdentifier("invalid.app.package&name")

        val exception =
            assertFailsWith<IllegalArgumentException> {
                handler.setPolicyUnchecked(
                    anyCaller,
                    anyScope,
                    PolicyValueTransport.packageField(
                        packageIdentifier.createTransport(),
                    ),
                )
            }

        assertThat(exception.message).contains(
            "Package name invalid.app.package&name is not valid",
        )
    }

    @Test
    fun setPolicyUnchecked_shouldRejectPackageNameWithSingleSegment() {
        val handler = createHandler()
        val packageIdentifier = PackageIdentifier("invalidapppackage")

        val exception =
            assertFailsWith<IllegalArgumentException> {
                handler.setPolicyUnchecked(
                    anyCaller,
                    anyScope,
                    PolicyValueTransport.packageField(
                        packageIdentifier.createTransport(),
                    ),
                )
            }

        assertThat(exception.message).contains(
            "Package name invalidapppackage is not valid for policy",
        )
    }

    @Test
    fun setPolicyUnchecked_shouldRejectPackageNameStartingWithNumber() {
        val handler = createHandler()
        val packageIdentifier = PackageIdentifier("1invalid.app.package")

        val exception =
            assertFailsWith<IllegalArgumentException> {
                handler.setPolicyUnchecked(
                    anyCaller,
                    anyScope,
                    PolicyValueTransport.packageField(
                        packageIdentifier.createTransport(),
                    ),
                )
            }

        assertThat(exception.message).contains(
            "Package name 1invalid.app.package is not valid for policy",
        )
    }
}
