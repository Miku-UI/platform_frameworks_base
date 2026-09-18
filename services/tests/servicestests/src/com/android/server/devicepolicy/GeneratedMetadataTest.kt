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

package com.android.server.devicepolicy

import android.app.admin.DevicePolicyManager.POLICY_SCOPE_DEVICE
import android.app.admin.DevicePolicyManager.POLICY_SCOPE_USER
import android.app.admin.DevicePolicyManager.RESOURCE_PER_USER
import android.app.admin.PolicyIdentifier
import android.app.admin.PolicyIdentifier.SCREEN_CAPTURE
import android.app.admin.PolicyIdentifier.SCREEN_CAPTURE_ALLOWED
import android.app.admin.PolicyIdentifier.SCREEN_CAPTURE_DISALLOWED
import android.app.admin.metadata.EnumPolicyMetadata
import android.app.admin.metadata.IntegerPolicyMetadata
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.app.admin.metadata.GeneratedPolicyMetadata
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertFailsWith

@RunWith(AndroidJUnit4::class)
class GeneratedMetadataTest {
    @Test
    fun policyMetadata_containsScreenCapture() {
        // Make sure that we generate at least one policy correctly.
        val metadata: EnumPolicyMetadata =
            GeneratedPolicyMetadata.getPolicyMetadata(SCREEN_CAPTURE) as EnumPolicyMetadata

        assertThat(metadata.id).isEqualTo(SCREEN_CAPTURE)
        assertThat(metadata.allowedScopes).isEqualTo(setOf(POLICY_SCOPE_USER, POLICY_SCOPE_DEVICE))
        assertThat(metadata.affectedResource).isEqualTo(RESOURCE_PER_USER)
        assertThat(metadata.allowedValues).isEqualTo(
            setOf(
                SCREEN_CAPTURE_ALLOWED,
                SCREEN_CAPTURE_DISALLOWED
            )
        )
    }

    @Test
    fun policyMetadata_failsOnInvalidId() {
        assertFailsWith<IllegalArgumentException> {
            GeneratedPolicyMetadata.getPolicyMetadata(PolicyIdentifier<Integer>("invalid_policy_id"))
        }
    }

    @Test
    fun policyMetadata_failsOnInvalidMetadataType() {
        assertFailsWith<ClassCastException> {
            GeneratedPolicyMetadata.getPolicyMetadata(SCREEN_CAPTURE) as IntegerPolicyMetadata
        }
    }
}
