/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.server.appfunctions

import android.content.pm.Signature
import com.google.common.truth.Truth.assertThat
import kotlin.test.assertFailsWith
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class SignedPackageParserTest {

    @Test
    fun parseList_emptyInput_returnsEmptyList() {
        val input = ""

        val result = SignedPackageParser.parseList(input)

        assertThat(result).isEmpty()
    }

    @Test
    fun parseList_singlePackageName_returnsCorrectList() {
        val input = TEST_PACKAGE_NAME_1

        val result = SignedPackageParser.parseList(input)

        assertThat(result).hasSize(1)
        assertThat(result[0].packageName).isEqualTo(TEST_PACKAGE_NAME_1)
        assertThat(result[0].certificateDigest).isEmpty()
        assertThat(result[0].certificateDigestOrNull).isNull()
    }

    @Test
    fun parseList_singlePackageNameWithCertificate_returnsCorrectList() {
        val input = "$TEST_PACKAGE_NAME_1:$TEST_CERTIFICATE_STRING_1"

        val result = SignedPackageParser.parseList(input)

        assertThat(result).hasSize(1)
        assertThat(result[0].packageName).isEqualTo(TEST_PACKAGE_NAME_1)
        assertThat(result[0].certificateDigest).isEqualTo(TEST_CERTIFICATE_DIGEST_1)
    }

    @Test
    fun parseList_multiplePackages_returnsCorrectList() {
        val input = "$TEST_PACKAGE_NAME_1:$TEST_CERTIFICATE_STRING_1;$TEST_PACKAGE_NAME_2"

        val result = SignedPackageParser.parseList(input)

        assertThat(result).hasSize(2)
        assertThat(result[0].packageName).isEqualTo(TEST_PACKAGE_NAME_1)
        assertThat(result[0].certificateDigest).isEqualTo(TEST_CERTIFICATE_DIGEST_1)
        assertThat(result[1].packageName).isEqualTo(TEST_PACKAGE_NAME_2)
        assertThat(result[1].certificateDigest).isEmpty()
        assertThat(result[1].certificateDigestOrNull).isNull()
    }

    @Test
    fun parseList_multiplePackagesWithCertificates_returnsCorrectList() {
        val input =
            "$TEST_PACKAGE_NAME_1:$TEST_CERTIFICATE_STRING_1;" +
                "$TEST_PACKAGE_NAME_2:$TEST_CERTIFICATE_STRING_2"

        val result = SignedPackageParser.parseList(input)

        assertThat(result).hasSize(2)
        assertThat(result[0].packageName).isEqualTo(TEST_PACKAGE_NAME_1)
        assertThat(result[0].certificateDigest).isEqualTo(TEST_CERTIFICATE_DIGEST_1)
        assertThat(result[1].packageName).isEqualTo(TEST_PACKAGE_NAME_2)
        assertThat(result[1].certificateDigest).isEqualTo(TEST_CERTIFICATE_DIGEST_2)
    }

    @Test
    fun parseList_invalidCertificate_throwsException() {
        val input =
            "$TEST_PACKAGE_NAME_1:$INVALID_CERTIFICATE_STRING;" +
                "$TEST_PACKAGE_NAME_2:$TEST_CERTIFICATE_STRING_2"

        assertFailsWith<IllegalArgumentException> { SignedPackageParser.parseList(input) }
    }

    @Test
    fun parseList_trailingSeparator_handlesPotentialEmptyLastSegment() {
        val input = "$TEST_PACKAGE_NAME_1:$TEST_CERTIFICATE_STRING_1;"

        val result = SignedPackageParser.parseList(input)

        assertThat(result).hasSize(1)
        assertThat(result[0].packageName).isEqualTo(TEST_PACKAGE_NAME_1)
        assertThat(result[0].certificateDigest).isEqualTo(TEST_CERTIFICATE_DIGEST_1)
    }

    @Test
    fun parseList_onlySeparators_returnsEmptyList() {
        val input = ";;"

        val result = SignedPackageParser.parseList(input)

        assertThat(result).isEmpty()
    }

    private companion object {
        const val TEST_PACKAGE_NAME_1 = "com.example.test1"
        const val TEST_PACKAGE_NAME_2 = "com.example.test2"
        const val TEST_CERTIFICATE_STRING_1 = "abcdef0123456789"
        val TEST_CERTIFICATE_DIGEST_1 = Signature(TEST_CERTIFICATE_STRING_1).toByteArray()
        const val TEST_CERTIFICATE_STRING_2 = "9876543210fedcba"
        val TEST_CERTIFICATE_DIGEST_2 = Signature(TEST_CERTIFICATE_STRING_2).toByteArray()

        const val INVALID_CERTIFICATE_STRING = "invalid_certificate_string"
    }
}
