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

import android.app.admin.BooleanPolicyValue
import android.app.admin.IntegerPolicyValue
import android.app.admin.PackageSetPolicyValue
import android.app.admin.PolicyValue
import android.content.ComponentName
import android.os.UserHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ResolutionMechanismTest {
    private class ResolutionMechanismTestImpl : ResolutionMechanism<Int>() {
        override fun resolve(adminPolicies: java.util.LinkedHashMap<EnforcingAdmin?,
                PolicyValue<Int?>?>?): ResolvedPolicy<Int?>? = null
        override fun getParcelableResolutionMechanism(): android.app.admin
            .ResolutionMechanism<Int?>? = null
    }

    @Test
    fun resolve_flagUnion() {
        val adminPolicies: LinkedHashMap<EnforcingAdmin, PolicyValue<Int>> = LinkedHashMap()
        adminPolicies.put(SYSTEM_ADMIN, INT_POLICY_A as PolicyValue<Int>)
        adminPolicies.put(DEVICE_OWNER_ADMIN, INT_POLICY_B as PolicyValue<Int>)

        val resolvedPolicy = FlagUnion().resolve(adminPolicies)

        assertThat(resolvedPolicy).isNotNull()
        assertThat(resolvedPolicy?.resolvedPolicyValue).isEqualTo(INT_POLICY_AB)
        assertThat(resolvedPolicy?.contributingAdmins)
            .containsExactly(SYSTEM_ADMIN, DEVICE_OWNER_ADMIN)
    }

    @Test
    fun resolve_mostRecent() {
        val adminPolicies: LinkedHashMap<EnforcingAdmin, PolicyValue<Integer>> = LinkedHashMap()
        adminPolicies.put(SYSTEM_ADMIN, INT_POLICY_A as PolicyValue<Integer>)
        adminPolicies.put(DEVICE_OWNER_ADMIN, INT_POLICY_B as PolicyValue<Integer>)

        val resolvedPolicy = MostRecent<Integer>().resolve(adminPolicies)

        assertThat(resolvedPolicy).isNotNull()
        assertThat(resolvedPolicy?.resolvedPolicyValue).isEqualTo(INT_POLICY_B)
        assertThat(resolvedPolicy?.contributingAdmins).containsExactly(DEVICE_OWNER_ADMIN)
    }

    @Test
    fun resolve_mostRestrictive() {
        val adminPolicies: LinkedHashMap<EnforcingAdmin, PolicyValue<Boolean>> = LinkedHashMap()
        adminPolicies.put(SYSTEM_ADMIN, BooleanPolicyValue(false) as PolicyValue<Boolean>)
        adminPolicies.put(DEVICE_OWNER_ADMIN, BooleanPolicyValue(true) as PolicyValue<Boolean>)

        val resolvedPolicy =
            MostRestrictive<Boolean>(listOf(BooleanPolicyValue(false), BooleanPolicyValue(true)))
                .resolve(adminPolicies)

        assertThat(resolvedPolicy).isNotNull()
        resolvedPolicy?.resolvedPolicyValue?.value?.let { assertFalse(it) }
        assertThat(resolvedPolicy?.contributingAdmins).containsExactly(SYSTEM_ADMIN)
    }

    @Test
    fun resolve_mostRestrictive_multipleContributingAdmins() {
        val adminPolicies: LinkedHashMap<EnforcingAdmin, PolicyValue<Boolean>> = LinkedHashMap()
        adminPolicies.put(SYSTEM_ADMIN, BooleanPolicyValue(false) as PolicyValue<Boolean>)
        adminPolicies.put(DEVICE_OWNER_ADMIN, BooleanPolicyValue(true) as PolicyValue<Boolean>)
        adminPolicies.put(DEVICE_ADMIN, BooleanPolicyValue(false) as PolicyValue<Boolean>)

        val resolvedPolicy =
            MostRestrictive(listOf(BooleanPolicyValue(false), BooleanPolicyValue(true)))
                .resolve(adminPolicies)

        assertThat(resolvedPolicy).isNotNull()
        resolvedPolicy?.resolvedPolicyValue?.value?.let { assertFalse(it) }
        assertThat(resolvedPolicy?.contributingAdmins)
            .containsExactly(SYSTEM_ADMIN, DEVICE_ADMIN)
    }

    @Test
    fun resolve_stringSetIntersection() {
        val adminPolicies: LinkedHashMap<EnforcingAdmin, PolicyValue<Set<String>>> = LinkedHashMap()
        adminPolicies.put(
            SYSTEM_ADMIN,
            PackageSetPolicyValue(setOf("package1", "package2")) as PolicyValue<Set<String>>,
        )
        adminPolicies.put(
            DEVICE_OWNER_ADMIN,
            PackageSetPolicyValue(setOf("package1")) as PolicyValue<Set<String>>,
        )

        val resolvedPolicy = StringSetIntersection().resolve(adminPolicies)

        assertThat(resolvedPolicy).isNotNull()
        assertThat(resolvedPolicy?.resolvedPolicyValue?.value).containsExactly("package1")
        assertThat(resolvedPolicy?.contributingAdmins)
            .containsExactly(SYSTEM_ADMIN, DEVICE_OWNER_ADMIN)
    }

    @Test
    fun resolve_packageSetUnion() {
        val adminPolicies: LinkedHashMap<EnforcingAdmin, PolicyValue<Set<String>>> = LinkedHashMap()
        adminPolicies.put(
            SYSTEM_ADMIN,
            PackageSetPolicyValue(setOf("package1", "package2")) as PolicyValue<Set<String>>,
        )
        adminPolicies.put(
            DEVICE_OWNER_ADMIN,
            PackageSetPolicyValue(setOf("package1", "package3")) as PolicyValue<Set<String>>,
        )

        val resolvedPolicy = PackageSetUnion().resolve(adminPolicies)

        assertThat(resolvedPolicy).isNotNull()
        assertThat(resolvedPolicy?.resolvedPolicyValue?.value)
            .containsExactly("package1", "package2", "package3")
        assertThat(resolvedPolicy?.contributingAdmins)
            .containsExactly(SYSTEM_ADMIN, DEVICE_OWNER_ADMIN)
    }

    @Test
    fun resolve_topPriority() {
        val adminPolicies: LinkedHashMap<EnforcingAdmin, PolicyValue<Integer>> = LinkedHashMap()
        adminPolicies.put(SYSTEM_ADMIN, INT_POLICY_A as PolicyValue<Integer>)
        adminPolicies.put(DEVICE_OWNER_ADMIN, INT_POLICY_B as PolicyValue<Integer>)

        val resolvedPolicy =
            TopPriority<Integer>(listOf(EnforcingAdmin.DPC_AUTHORITY)).resolve(adminPolicies)

        assertThat(resolvedPolicy).isNotNull()
        assertThat(resolvedPolicy?.resolvedPolicyValue).isEqualTo(INT_POLICY_B)
        assertThat(resolvedPolicy?.contributingAdmins).containsExactly(DEVICE_OWNER_ADMIN)
    }

    @Test
    fun isPolicyApplied_defaultImplementation_sameValues_returnsTrue() {
        val resolutionMechanism = ResolutionMechanismTestImpl()

        assertTrue {
            resolutionMechanism.isPolicyApplied(INT_POLICY_A, INT_POLICY_A)
        }
    }

    @Test
    fun isPolicyApplied_defaultImplementation_differentValues_returnsFalse() {
        val resolutionMechanism = ResolutionMechanismTestImpl()

        assertFalse {
            resolutionMechanism.isPolicyApplied(INT_POLICY_A, INT_POLICY_AB)
        }
    }

    @Test
    fun isPolicyApplied_flagUnion_flagSet_returnTrue() {
        val resolutionMechanism = FlagUnion()

        assertTrue { resolutionMechanism.isPolicyApplied(INT_POLICY_A,
            INT_POLICY_AB) }
    }

    @Test
    fun isPolicyApplied_flagUnion_flagNotSet_returnFalse() {
        val resolutionMechanism = FlagUnion()

        assertFalse { resolutionMechanism.isPolicyApplied(INT_POLICY_C,
            INT_POLICY_AB) }
    }

    @Test
    fun isPolicyApplied_flagUnion_someFlagsNotSet_returnFalse() {
        val resolutionMechanism = FlagUnion()

        assertFalse { resolutionMechanism.isPolicyApplied(INT_POLICY_AB,
            INT_POLICY_A) }
    }

    @Test
    fun isPolicyApplied_packageSetUnion_setIncluded_returnsTrue() {
        val resolutionMechanism = PackageSetUnion()

        assertTrue {
            resolutionMechanism.isPolicyApplied(PackageSetPolicyValue(setOf("package1")),
                PackageSetPolicyValue(setOf("package1", "package2")))
        }
    }

    @Test
    fun isPolicyApplied_packageSetUnion_setDoesNotIntersect_returnsFalse() {
        val resolutionMechanism = PackageSetUnion()

        assertFalse {
            resolutionMechanism.isPolicyApplied(PackageSetPolicyValue(setOf("package3")),
                PackageSetPolicyValue(setOf("package1", "package2")))
        }
    }

    @Test
    fun isPolicyApplied_packageSetUnion_setPartiallyIncluded_returnsFalse() {
        val resolutionMechanism = PackageSetUnion()

        assertFalse {
            resolutionMechanism.isPolicyApplied(
                PackageSetPolicyValue(setOf("package1", "package3")),
                PackageSetPolicyValue(setOf("package1", "package2")))
        }
    }

    @Test
    fun isPolicyApplied_mostRecent_sameValues_returnsTrue() {
        val resolutionMechanism = MostRecent<Int>()

        assertTrue {
            resolutionMechanism.isPolicyApplied(INT_POLICY_A, INT_POLICY_A)
        }
    }

    @Test
    fun isPolicyApplied_mostRecent_differentValues_returnsFalse() {
        val resolutionMechanism = MostRecent<Int>()

        assertFalse {
            resolutionMechanism.isPolicyApplied(INT_POLICY_A, INT_POLICY_AB)
        }
    }

    @Test
    fun isPolicyApplied_mostRestrictive_sameValues_returnsTrue() {
        val resolutionMechanism = MostRestrictive<Int>(listOf())

        assertTrue {
            resolutionMechanism.isPolicyApplied(INT_POLICY_A, INT_POLICY_A)
        }
    }

    @Test
    fun isPolicyApplied_mostRestrictive_differentValues_returnsFalse() {
        val resolutionMechanism = MostRestrictive<Int>(listOf())

        assertFalse {
            resolutionMechanism.isPolicyApplied(INT_POLICY_A, INT_POLICY_AB )
        }
    }

    @Test
    fun isPolicyApplied_stringSetIntersection_sameValues_returnsTrue() {
        val resolutionMechanism = StringSetIntersection()
        val policyA = PackageSetPolicyValue(setOf("package1", "package2"))
        val policyB = PackageSetPolicyValue(setOf("package2", "package1"))

        assertTrue {
            resolutionMechanism.isPolicyApplied(policyA, policyB)
        }
    }

    @Test
    fun isPolicyApplied_stringSetIntersection_differentValues_returnsFalse() {
        val resolutionMechanism = StringSetIntersection()
        val policyA = PackageSetPolicyValue(setOf("package1"))
        val policyB = PackageSetPolicyValue(setOf("package2"))

        assertFalse {
            resolutionMechanism.isPolicyApplied(policyA, policyB)
        }
    }

    @Test
    fun isPolicyApplied_stringSetIntersection_policyIsSubsetOfResolvedPolicy_returnsFalse() {
        val resolutionMechanism = StringSetIntersection()
        val policyA = PackageSetPolicyValue(setOf("package1"))
        val policyB = PackageSetPolicyValue(setOf("package1", "package2"))

        assertFalse {
            resolutionMechanism.isPolicyApplied(policyA, policyB)
        }
    }

    @Test
    fun isPolicyApplied_stringSetIntersection_resolvedPolicyIsSubsetOfPolicy_returnsFalse() {
        val resolutionMechanism = StringSetIntersection()
        val policyA = PackageSetPolicyValue(setOf("package1", "package2"))
        val policyB = PackageSetPolicyValue(setOf("package1"))

        assertFalse {
            resolutionMechanism.isPolicyApplied(policyA, policyB)
        }
    }

    @Test
    fun isPolicyApplied_topPriority_sameValues_returnsTrue() {
        val resolutionMechanism = TopPriority<Int>(listOf())

        assertTrue {
            resolutionMechanism.isPolicyApplied(INT_POLICY_A, INT_POLICY_A)
        }
    }

    @Test
    fun isPolicyApplied_topPriority_differentValues_returnsFalse() {
        val resolutionMechanism = TopPriority<Int>(listOf())

        assertFalse {
            resolutionMechanism.isPolicyApplied(INT_POLICY_A, INT_POLICY_AB)
        }
    }


    companion object {
        private const val SYSTEM_USER_ID = UserHandle.USER_SYSTEM
        private val SYSTEM_ADMIN = EnforcingAdmin.createSystemEnforcingAdmin("system_entity")
        private val DEVICE_OWNER_ADMIN =
            EnforcingAdmin.createEnterpriseEnforcingAdmin(
                ComponentName("packagename", "classname"),
                SYSTEM_USER_ID,
            )
        private val DEVICE_ADMIN =
            EnforcingAdmin.createDeviceAdminEnforcingAdmin(
                ComponentName("packagename", "classname"),
                SYSTEM_USER_ID,
            )

        private val INT_POLICY_A: PolicyValue<Int> = IntegerPolicyValue(1 shl 7)
        private val INT_POLICY_B: PolicyValue<Int> = IntegerPolicyValue(1 shl 8)
        private val INT_POLICY_C: PolicyValue<Int> = IntegerPolicyValue(1 shl 9)
        private val INT_POLICY_AB: PolicyValue<Int> = IntegerPolicyValue((1 shl 7) or (1 shl 8))
    }
}
