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

package com.android.server.devicepolicy

import android.util.IndentingPrintWriter
import androidx.test.filters.SmallTest
import androidx.test.runner.AndroidJUnit4
import com.android.internal.util.JournaledFile
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.io.StringWriter
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for the DevicePolicyData class.
 *
 * Run this test with: `atest
 * FrameworksServicesTests:com.android.server.devicepolicy.DevicePolicyDataTest`
 */
@SmallTest
@RunWith(AndroidJUnit4::class)
class DevicePolicyDataTest {
    @Test
    fun testStoresAndLoadsAdminRecords() {
        val realFile = File.createTempFile("device-policy-data", "xml")
        val tempFile = File.createTempFile("device-policy-data", "xml.tmp")
        val journaledFile = JournaledFile(realFile, tempFile)
        try {
            val originalData = DevicePolicyData(0)
            originalData.mAdminRecordMap["com.android.test1"] =
                AdminRecord("com.android.test1").apply {
                    mOrganizationId = "org-id-1"
                    mEnrollmentSpecificId = "enrollment-id-1"
                }
            originalData.mAdminRecordMap["com.android.test2"] =
                AdminRecord("com.android.test2").apply { mOrganizationId = "org-id-2" }
            originalData.mAdminRecordMap["com.android.test3"] = AdminRecord("com.android.test3")

            DevicePolicyData.store(originalData, journaledFile)
            val loadedData = DevicePolicyData(0)
            DevicePolicyData.load(loadedData, journaledFile, { null }, null)

            assertThat(loadedData.mAdminRecordMap).hasSize(3)
            val loadedRecord1 = loadedData.mAdminRecordMap["com.android.test1"]
            assertThat(loadedRecord1!!.mOrganizationId).isEqualTo("org-id-1")
            assertThat(loadedRecord1!!.mEnrollmentSpecificId).isEqualTo("enrollment-id-1")
            val loadedRecord2 = loadedData.mAdminRecordMap["com.android.test2"]
            assertThat(loadedRecord2!!.mOrganizationId).isEqualTo("org-id-2")
            assertThat(loadedRecord2!!.mEnrollmentSpecificId).isNull()
            val loadedRecord3 = loadedData.mAdminRecordMap["com.android.test3"]
            assertThat(loadedRecord3!!.mOrganizationId).isNull()
            assertThat(loadedRecord3!!.mEnrollmentSpecificId).isNull()
        } finally {
            realFile.delete()
            tempFile.delete()
        }
    }

    @Test
    fun testDump_dumpsAdminRecords() {
        val data = DevicePolicyData(10)
        val record1 = AdminRecord("com.android.test1")
        data.mAdminRecordMap[record1.mPackageName] = record1
        val record2 = AdminRecord("com.android.test2").apply { mOrganizationId = "org-id-2" }
        data.mAdminRecordMap[record2.mPackageName] = record2
        val record3 =
            AdminRecord("com.android.test3").apply {
                mOrganizationId = "org-id-3"
                mEnrollmentSpecificId = "enrollment-id-3"
            }
        data.mAdminRecordMap[record3.mPackageName] = record3
        val stringWriter = StringWriter()
        val pw = IndentingPrintWriter(stringWriter, "  ")

        data.dump(pw)

        val output = stringWriter.toString()
        assertThat(output).contains("Enabled Device Admins (User 10, provisioningState: 0):")
        assertThat(output).contains("AdminRecord (package: com.android.test1):")
        assertThat(output)
            .contains(
                "AdminRecord (package: com.android.test2):\n" + "    mOrganizationId=org-id-2\n"
            )
        assertThat(output)
            .contains(
                "AdminRecord (package: com.android.test3):\n" +
                    "    mOrganizationId=org-id-3\n" +
                    "    mEnrollmentSpecificId=enrollment-id-3\n"
            )
    }
}
