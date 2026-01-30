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

package com.android.server.appfunctions

import android.content.Context
import android.content.pm.Signature
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.nio.charset.StandardCharsets
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class AppFunctionAgentAllowlistStorageTest {

    private lateinit var context: Context
    private lateinit var testDir: File
    private lateinit var storage: AppFunctionAgentAllowlistStorage
    private lateinit var allowlistFile: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Use a temporary directory for testing
        testDir = File(context.cacheDir, "appfunctions_test")
        testDir.mkdirs()
        val allowlistDir = File(testDir, "system/appfunctions")
        allowlistDir.mkdirs() // Ensure the parent directory exists
        allowlistFile = File(allowlistDir, "agent_allowlist.txt")
        storage = AppFunctionAgentAllowlistStorage(allowlistFile)
    }

    @After
    fun tearDown() {
        testDir.deleteRecursively()
    }

    @Test
    fun readPreviousValidAllowlist_fileDoesNotExist_returnsNull() {
        val allowlist = storage.readPreviousValidAllowlist()
        assertThat(allowlist).isNull()
    }

    @Test
    fun writeCurrentValidAllowlist_readsBackCorrectly() {
        val allowlistString =
            "$TEST_PACKAGE_NAME_1:$TEST_CERTIFICATE_STRING_1;" +
                "$TEST_PACKAGE_NAME_2:$TEST_CERTIFICATE_STRING_2"
        storage.writeCurrentAllowlist(allowlistString)

        val readAllowlist = storage.readPreviousValidAllowlist()

        assertThat(readAllowlist).isNotNull()
        assertThat(readAllowlist).hasSize(2)
        assertThat(readAllowlist!![0].packageName).isEqualTo(TEST_PACKAGE_NAME_1)
        assertThat(readAllowlist[0].certificateDigest).isEqualTo(TEST_CERTIFICATE_DIGEST_1)
        assertThat(readAllowlist[1].packageName).isEqualTo(TEST_PACKAGE_NAME_2)
        assertThat(readAllowlist[1].certificateDigest).isEqualTo(TEST_CERTIFICATE_DIGEST_2)
    }

    @Test
    fun readPreviousValidAllowlist_invalidFileContent_returnsNullAndDeletesFile() {
        val invalidAllowlistString = "$TEST_PACKAGE_NAME_1:$INVALID_CERTIFICATE_STRING"

        storage.writeCurrentAllowlist(invalidAllowlistString)
        assertThat(allowlistFile.exists()).isTrue()

        val allowlist = storage.readPreviousValidAllowlist()

        assertThat(allowlist).isNull()
        assertThat(allowlistFile.exists()).isFalse()
    }

    @Test
    fun writeCurrentValidAllowlist_emptyString_createsEmptyFile() {
        val allowlistString = ""
        storage.writeCurrentAllowlist(allowlistString)

        val readAllowlist = storage.readPreviousValidAllowlist()

        assertThat(readAllowlist).isNotNull()
        assertThat(readAllowlist).isEmpty()
        assertThat(allowlistFile.exists()).isTrue()
        assertThat(allowlistFile.readText(StandardCharsets.UTF_8)).isEmpty()
    }

    @Test
    fun writeCurrentAllowlist_writeTwice_readsBackCorrectlyTheSecondValue() {
        val allowlistString1 = "$TEST_PACKAGE_NAME_1:$TEST_CERTIFICATE_STRING_1;"
        storage.writeCurrentAllowlist(allowlistString1)
        val allowlistString2 = "$TEST_PACKAGE_NAME_2:$TEST_CERTIFICATE_STRING_2"
        storage.writeCurrentAllowlist(allowlistString2)

        val readAllowlist = storage.readPreviousValidAllowlist()

        assertThat(readAllowlist).isNotNull()
        assertThat(readAllowlist).hasSize(1)
        assertThat(readAllowlist!![0].packageName).isEqualTo(TEST_PACKAGE_NAME_2)
        assertThat(readAllowlist[0].certificateDigest).isEqualTo(TEST_CERTIFICATE_DIGEST_2)
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
