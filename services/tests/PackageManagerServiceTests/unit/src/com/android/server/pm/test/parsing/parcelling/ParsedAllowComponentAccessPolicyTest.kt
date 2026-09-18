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

package com.android.server.pm.test.parsing.parcelling

import android.content.pm.SignedPackage
import android.os.Parcel
import com.android.internal.pm.pkg.component.ParsedAllowComponentAccessPolicy
import com.android.internal.pm.pkg.component.ParsedAllowComponentAccessPolicyImpl
import com.google.common.truth.Truth.assertThat
import kotlin.contracts.ExperimentalContracts
import kotlin.reflect.KFunction1
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@OptIn(ExperimentalContracts::class)
@RunWith(Parameterized::class)
class ParsedAllowComponentAccessPolicyTest : ParcelableComponentTest(
    ParsedAllowComponentAccessPolicy::class, ParsedAllowComponentAccessPolicyImpl::class
) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "")
        fun parameters() = arrayOf(arrayOf<Any>())
    }

    override val defaultImpl = ParsedAllowComponentAccessPolicyImpl(emptyList())
    override val creator = ParsedAllowComponentAccessPolicyImpl.CREATOR

    override val baseParams: Collection<KFunction1<*, Any?>> = emptyList()

    override val excludedMethods: Collection<String> = emptyList()

    override fun extraParams(): Collection<Param?> {
        val testRules = listOf(
            SignedPackage("com.test.extra", "EXTRA_CERT".toByteArray()),
            SignedPackage("com.test.another", null)
        )

        return listOf(
            getSetByValue(
                getFunction = ParsedAllowComponentAccessPolicyImpl::getParsedAllowlistedSignedPackages,
                setFunction = ParsedAllowComponentAccessPolicyImpl::setParsedAllowlistedSignedPackages,
                value = testRules,
                compare = { list1, list2 ->
                    if (list1 === list2) return@getSetByValue true
                    if (list1.size != list2.size) return@getSetByValue false

                    list1.zip(list2).all { (a, b) ->
                        a.packageName == b.packageName
                                && (!a.hasCertificateDigest() && !b.hasCertificateDigest() ||
                                a.certificateDigest.contentEquals(b.certificateDigest))
                    }
                })
        )
    }

    override fun initialObject(): ParsedAllowComponentAccessPolicyImpl {
        val testRules = listOf(
            SignedPackage("com.test.app1", "EXTRA_CERT".toByteArray()),
            SignedPackage("com.test.app2", null)
        )
        return ParsedAllowComponentAccessPolicyImpl(testRules)
    }

    @Test
    fun testPolicyWithNoRules() {
        val emptyPolicy = ParsedAllowComponentAccessPolicyImpl(emptyList())
        val emptyResult = parcelAndUnparcel(emptyPolicy)
        assertThat(emptyResult.parsedAllowlistedSignedPackages).isNotNull()
        assertThat(emptyResult.parsedAllowlistedSignedPackages).isEmpty()
    }

    private fun parcelAndUnparcel(original: ParsedAllowComponentAccessPolicyImpl): ParsedAllowComponentAccessPolicyImpl {
        val parcel = Parcel.obtain()
        try {
            original.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            return ParsedAllowComponentAccessPolicyImpl.CREATOR.createFromParcel(parcel)
        } finally {
            parcel.recycle()
        }
    }
}