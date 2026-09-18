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

import android.app.admin.DevicePolicyManager.POLICY_SCOPE_DEVICE
import android.app.admin.DevicePolicyManager.POLICY_SCOPE_PARENT_USER
import android.app.admin.DevicePolicyManager.POLICY_SCOPE_USER
import android.app.admin.DevicePolicyManager.RESOURCE_PER_USER
import android.app.admin.NoArgsPolicyKey
import android.app.admin.PackageIdentifier
import android.app.admin.PolicyIdentifier
import android.app.admin.PolicyValueTransport
import android.app.admin.metadata.EnumPolicyMetadata
import android.app.admin.metadata.IntegerPolicyMetadata
import android.app.admin.metadata.ListPolicyMetadata
import android.app.admin.metadata.LongPolicyMetadata
import android.app.admin.metadata.PackagePolicyMetadata
import android.app.admin.metadata.ResolutionMechanismMetadata
import android.app.admin.metadata.StringPolicyMetadata
import com.android.server.devicepolicy.CallerIdentity
import com.android.server.devicepolicy.IntegerPolicySerializer
import com.android.server.devicepolicy.ListOfStringPolicySerializer
import com.android.server.devicepolicy.LongPolicySerializer
import com.android.server.devicepolicy.MostRecent
import com.android.server.devicepolicy.PackagePolicySerializer
import com.android.server.devicepolicy.PolicyDefinition
import com.android.server.devicepolicy.PolicyEnforcerCallbacks
import com.android.server.devicepolicy.StringPolicySerializer

/**
 * A collection of sample policies that can be used in the tests.
 *
 * <p>
 * This file also contains `copy` methods for the metadata classes, and some high-level defines.
 */
val anyCaller = CallerIdentity(111, 222, "callerPackage", null)
val anyUid = 100
const val anyScope = POLICY_SCOPE_USER
val allScopes = setOf(POLICY_SCOPE_USER, POLICY_SCOPE_DEVICE, POLICY_SCOPE_PARENT_USER)

// A sample enum policy that can be used in the tests.
object EnumPolicy {
    val name = "theEnumPolicy"
    const val VALUE_1 = 1
    const val VALUE_2 = 2
    val key = PolicyIdentifier<Int>(name)
    val metadata =
        EnumPolicyMetadata(
            key,
            /*allowedScopes=*/ setOf(POLICY_SCOPE_USER, POLICY_SCOPE_DEVICE),
            /*affectedResource=*/ RESOURCE_PER_USER,
            /*requiredPermission=*/ "testPermission",
            /*requiredCrossUserPermission=*/ "testCrossUserPermission",
            /*allowedDpcTypes=*/ setOf(),
            /*resolutionMechanism=*/ null,
            /*allowedValues=*/ setOf(VALUE_1, VALUE_2),
        )
    val anyTransportValue: PolicyValueTransport = PolicyValueTransport.integerField(VALUE_1)

    // The policy definition used for storing the policy value in DevicePolicyEngine.
    val definition =
        PolicyDefinition<Int>(
            NoArgsPolicyKey(name),
            MostRecent(),
            PolicyEnforcerCallbacks::noOp,
            IntegerPolicySerializer(),
        )
}

fun EnumPolicyMetadata.copy(
    id: PolicyIdentifier<Int>? = null,
    allowedScopes: Set<Int>? = null,
    affectedResource: Int? = null,
    requiredPermission: String? = null,
    requiredCrossUserPermission: String? = null,
    allowedDpcTypes: Set<Int>? = null,
    allowedValues: Set<Int>? = null,
    resolutionMechanism: ResolutionMechanismMetadata<Int>? = null,
) =
    EnumPolicyMetadata(
        id ?: this.id,
        allowedScopes ?: this.allowedScopes,
        affectedResource ?: this.affectedResource,
        requiredPermission ?: this.requiredPermission,
        requiredCrossUserPermission ?: this.requiredCrossUserPermission,
        allowedDpcTypes ?: this.allowedDpcTypes,
        resolutionMechanism ?: this.resolutionMechanism,
        allowedValues ?: this.allowedValues,
    )

object StringPolicy {
    val name = "theStringPolicy"
    val key = PolicyIdentifier<String>(name)
    val metadata =
        StringPolicyMetadata(
            key,
            /*allowedScopes=*/ setOf(POLICY_SCOPE_USER, POLICY_SCOPE_DEVICE),
            /*affectedResource=*/ RESOURCE_PER_USER,
            /*requiredPermission=*/ "testPermission",
            /*requiredCrossUserPermission=*/ "testCrossUserPermission",
            /*allowedDpcTypes=*/ setOf(),
            /*resolutionMechanism=*/ ResolutionMechanismMetadata.MostRestrictive<String>(),
            /*emptyStringAllowed=*/ false,
            /*unprintableCharactersAllowed=*/ false,
            /*maxLength=*/ Integer.MAX_VALUE,
        )
    val anyTransportValue: PolicyValueTransport = PolicyValueTransport.stringField("a string value")

    val definition =
        PolicyDefinition<String>(
            NoArgsPolicyKey(name),
            MostRecent(),
            PolicyEnforcerCallbacks::noOp,
            StringPolicySerializer(),
        )
}

fun StringPolicyMetadata.copy(
    id: PolicyIdentifier<String>? = null,
    allowedScopes: Set<Int>? = null,
    affectedResource: Int? = null,
    requiredPermission: String? = null,
    requiredCrossUserPermission: String? = null,
    allowedDpcTypes: Set<Int>? = null,
    emptyStringAllowed: Boolean? = null,
    unprintableCharactersAllowed: Boolean? = null,
    maxLength: Int? = null,
) =
    StringPolicyMetadata(
        id ?: this.id,
        allowedScopes ?: this.allowedScopes,
        affectedResource ?: this.affectedResource,
        requiredPermission ?: this.requiredPermission,
        requiredCrossUserPermission ?: this.requiredCrossUserPermission,
        allowedDpcTypes ?: this.allowedDpcTypes,
        this.resolutionMechanism,
        emptyStringAllowed ?: this.isEmptyStringAllowed,
        unprintableCharactersAllowed ?: this.isUnprintableCharactersAllowed,
        maxLength ?: this.maxLength,
    )

object ListOfStringPolicy {
    val name = "theStringListPolicy"
    val id = PolicyIdentifier<List<String>>(name)
    val metadata =
        ListPolicyMetadata(
            /* id= */ id,
            /* elementMetadata= */ StringPolicyMetadata(
                /* id= */ PolicyIdentifier<String>(name + "#elements"),
                /* allowedScopes= */ setOf(POLICY_SCOPE_USER, POLICY_SCOPE_DEVICE),
                /* affectedResource= */ RESOURCE_PER_USER,
                /*requiredPermission=*/ "testPermission",
                /*requiredCrossUserPermission=*/ "testCrossUserPermission",
                /* allowedDpcTypes= */ setOf(),
                /* emptyStringAllowed= */ false,
                /* unprintableCharactersAllowed= */ false,
                /* maxLength= */ Integer.MAX_VALUE,
            ),
            /* resolutionMechanism= */ null,
            /* emptyListAllowed= */ false,
        )
    val anyTransportValue: PolicyValueTransport =
        PolicyValueTransport.listOfStringField(listOf("anyValue"))

    val definition =
        PolicyDefinition<List<String>>(
            NoArgsPolicyKey(name),
            MostRecent(),
            PolicyEnforcerCallbacks::noOp,
            ListOfStringPolicySerializer(),
        )
}

fun ListPolicyMetadata<String>.copy(
    id: PolicyIdentifier<List<String>>? = null,
    allowedScopes: Set<Int>? = null,
    affectedResource: Int? = null,
    requiredPermission: String? = null,
    requiredCrossUserPermission: String? = null,
    allowedDpcTypes: Set<Int>? = null,
    emptyStringAllowed: Boolean? = null,
    unprintableCharactersAllowed: Boolean? = null,
    maxLength: Int? = null,
    emptyListAllowed: Boolean? = null,
    resolutionMechanism: ResolutionMechanismMetadata<List<String>>? = null,
) =
    ListPolicyMetadata(
        /* id= */ id ?: this.id,
        /* elementMetadata= */ StringPolicyMetadata(
            /* id= */ id?.run { PolicyIdentifier<String>("$id#elements") }
                ?: PolicyIdentifier<String>("${this.id.id}#elements"),
            /* allowedScopes= */ allowedScopes ?: this.elementMetadata.allowedScopes,
            /* affectedResource= */ affectedResource ?: this.elementMetadata.affectedResource,
            /* requiredPermission= */ requiredPermission ?: this.elementMetadata.requiredPermission,
            /* requiredCrossUserPermission= */ requiredCrossUserPermission
                ?: this.elementMetadata.requiredCrossUserPermission,
            /* allowedDpcTypes= */ allowedDpcTypes ?: this.elementMetadata.allowedDpcTypes,
            /* emptyStringAllowed= */ emptyStringAllowed
                ?: (this.elementMetadata as StringPolicyMetadata).isEmptyStringAllowed,
            /* unprintableCharactersAllowed= */ unprintableCharactersAllowed
                ?: (this.elementMetadata as StringPolicyMetadata).isUnprintableCharactersAllowed,
            /* maxLength= */ maxLength
                ?: (this.elementMetadata as StringPolicyMetadata).maxLength,
        ),
        /* resolutionMechanism= */ resolutionMechanism ?: this.resolutionMechanism,
        /* emptyListAllowed= */ emptyListAllowed ?: this.isEmptyListAllowed,
    )

// A sample int policy that can be used in the tests.
object IntegerPolicy {
    val name = "integerPolicy"
    val key = PolicyIdentifier<Int>(name)
    const val MIN = -100
    const val MAX = 100

    val metadata =
        IntegerPolicyMetadata(
            key,
            /*allowedScopes=*/ setOf(POLICY_SCOPE_USER, POLICY_SCOPE_DEVICE),
            /*affectedResource=*/ RESOURCE_PER_USER,
            /*requiredPermission=*/ "testPermission",
            /*requiredCrossUserPermission=*/ "testCrossUserPermission",
            /*allowedDpcTypes=*/ setOf(),
            /*resolutionMechanism=*/ null,
            MIN,
            MAX,
        )

    val definition =
        PolicyDefinition<Int>(
            NoArgsPolicyKey(name),
            MostRecent(),
            PolicyEnforcerCallbacks::noOp,
            IntegerPolicySerializer(),
        )
}

fun IntegerPolicyMetadata.copy(minValue: Int? = null, maxValue: Int? = null) =
    IntegerPolicyMetadata(
        this.id,
        this.allowedScopes,
        this.affectedResource,
        this.requiredPermission,
        this.requiredCrossUserPermission,
        this.allowedDpcTypes,
        this.resolutionMechanism,
        minValue ?: this.minValue,
        maxValue ?: this.maxValue,
    )

// A sample long policy that can be used in the tests.
object LongPolicy {
    val name = "theLongPolicy"
    val key = PolicyIdentifier<Long>(name)
    val metadata =
        LongPolicyMetadata(
            key,
            /*allowedScopes=*/ setOf(POLICY_SCOPE_USER, POLICY_SCOPE_DEVICE),
            /*affectedResource=*/ RESOURCE_PER_USER,
            /*requiredPermission=*/ "testPermission",
            /*requiredCrossUserPermission=*/ "testCrossUserPermission",
            /*allowedDpcTypes=*/ setOf(),
            /*resolutionMechanism=*/ null,
            /*minValue=*/ 10L,
            /*maxValue=*/ 20L,
        )
    val anyTransportValue: PolicyValueTransport = PolicyValueTransport.longField(15L)

    val definition =
        PolicyDefinition<Long>(
            NoArgsPolicyKey(name),
            MostRecent(),
            PolicyEnforcerCallbacks::noOp,
            LongPolicySerializer(),
        )
}

fun LongPolicyMetadata.copy(minValue: Long? = null, maxValue: Long? = null) =
    LongPolicyMetadata(
        this.id,
        this.allowedScopes,
        this.affectedResource,
        this.requiredPermission,
        this.requiredCrossUserPermission,
        this.allowedDpcTypes,
        this.resolutionMechanism,
        minValue ?: this.minValue,
        maxValue ?: this.maxValue,
    )

object PackagePolicy {
    val name = "thePackagePolicy"
    val key = PolicyIdentifier<PackageIdentifier>(name)
    val metadata =
        PackagePolicyMetadata(
            key,
            /*allowedScopes=*/ setOf(POLICY_SCOPE_USER, POLICY_SCOPE_DEVICE),
            /*affectedResource=*/ RESOURCE_PER_USER,
            /*requiredPermission=*/ "testPermission",
            /*requiredCrossUserPermission=*/ "testCrossUserPermission",
            /*allowedDpcTypes=*/ setOf(),
        )
    val anyTransportValue: PolicyValueTransport =
        PolicyValueTransport.packageField(PackageIdentifier("com.example.app").createTransport())

    val definition =
        PolicyDefinition<PackageIdentifier>(
            NoArgsPolicyKey(name),
            MostRecent(),
            PolicyEnforcerCallbacks::noOp,
            PackagePolicySerializer(),
        )
}
