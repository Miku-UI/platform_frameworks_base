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
import android.util.Xml
import androidx.test.filters.SmallTest
import androidx.test.runner.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayOutputStream
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

/**
 * Tests for the AdminRecord class.
 *
 * Run this test with: `atest
 * FrameworksServicesTests:com.android.server.devicepolicy.AdminRecordTest`
 */
@SmallTest
@RunWith(AndroidJUnit4::class)
class AdminRecordTest {
    @Test
    fun testWriteToXml() {
        val record = AdminRecord("com.android.test")
        record.mOrganizationId = "org-id-123"
        record.mEnrollmentSpecificId = "enrollment-id-456"
        val stream = ByteArrayOutputStream()
        val serializer = Xml.newFastSerializer()
        serializer.setOutput(stream, StandardCharsets.UTF_8.name())
        serializer.startDocument(null, true)
        serializer.startTag(null, "admin-record")

        record.writeToXml(serializer)

        serializer.endTag(null, "admin-record")
        serializer.endDocument()
        val xml = stream.toString()
        assertThat(xml)
            .isEqualTo(
                "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n" +
                    "<admin-record>\n" +
                    "<organization-id>org-id-123</organization-id>\n" +
                    "<enrollment-specific-id>enrollment-id-456</enrollment-specific-id>\n" +
                    "</admin-record>\n"
            )
    }

    @Test
    fun testWriteToXml_oneField() {
        val record = AdminRecord("com.android.test")
        record.mOrganizationId = "org-id-123"
        val stream = ByteArrayOutputStream()
        val serializer = Xml.newFastSerializer()
        serializer.setOutput(stream, StandardCharsets.UTF_8.name())
        serializer.startDocument(null, true)
        serializer.startTag(null, "admin-record")

        record.writeToXml(serializer)

        serializer.endTag(null, "admin-record")
        serializer.endDocument()
        val xml = stream.toString()
        assertThat(xml)
            .isEqualTo(
                "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n" +
                    "<admin-record>\n" +
                    "<organization-id>org-id-123</organization-id>\n" +
                    "</admin-record>\n"
            )
    }

    @Test
    fun testWriteToXml_emptyFields() {
        val record = AdminRecord("com.android.test")
        val stream = ByteArrayOutputStream()
        val serializer = Xml.newFastSerializer()
        serializer.setOutput(stream, StandardCharsets.UTF_8.name())
        serializer.startDocument(null, true)
        serializer.startTag(null, "admin-record")

        record.writeToXml(serializer)

        serializer.endTag(null, "admin-record")
        serializer.endDocument()
        val xml = stream.toString()
        assertThat(xml)
            .isEqualTo(
                "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n" + "<admin-record />\n"
            )
    }

    @Test
    fun testReadFromXml() {
        val xml =
            "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n" +
                "<admin-record>\n" +
                "    <organization-id>org-id-123</organization-id>\n" +
                "    <enrollment-specific-id>enrollment-id-456</enrollment-specific-id>\n" +
                "</admin-record>\n"
        val parser = Xml.newFastPullParser()
        parser.setInput(xml.byteInputStream(), StandardCharsets.UTF_8.name())
        while (parser.eventType != XmlPullParser.START_TAG) {
            parser.next()
        }
        val record = AdminRecord("com.android.test")

        record.readFromXml(parser)

        assertThat(record.mPackageName).isEqualTo("com.android.test")
        assertThat(record.mOrganizationId).isEqualTo("org-id-123")
        assertThat(record.mEnrollmentSpecificId).isEqualTo("enrollment-id-456")
    }

    @Test
    fun testReadFromXml_oneField() {
        val xml =
            "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n" +
                "<admin-record>\n" +
                "    <organization-id>org-id-123</organization-id>\n" +
                "</admin-record>\n"
        val parser = Xml.newFastPullParser()
        parser.setInput(xml.byteInputStream(), StandardCharsets.UTF_8.name())
        while (parser.eventType != XmlPullParser.START_TAG) {
            parser.next()
        }
        val record = AdminRecord("com.android.test")

        record.readFromXml(parser)

        assertThat(record.mPackageName).isEqualTo("com.android.test")
        assertThat(record.mOrganizationId).isEqualTo("org-id-123")
        assertThat(record.mEnrollmentSpecificId).isNull()
    }

    @Test
    fun testReadWriteXml_emptyFields() {
        val xml =
            "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n" +
                "<admin-record>\n" +
                "</admin-record>\n"
        val parser = Xml.newFastPullParser()
        parser.setInput(xml.byteInputStream(), StandardCharsets.UTF_8.name())
        while (parser.eventType != XmlPullParser.START_TAG) {
            parser.next()
        }
        val record = AdminRecord("com.android.test")

        record.readFromXml(parser)

        assertThat(record.mPackageName).isEqualTo("com.android.test")
        assertThat(record.mOrganizationId).isNull()
        assertThat(record.mEnrollmentSpecificId).isNull()
    }

    @Test
    fun testDump() {
        val record = AdminRecord("com.android.test.dumper")
        record.mOrganizationId = "org-id-dump"
        record.mEnrollmentSpecificId = "enrollment-id-dump"
        val stringWriter = StringWriter()
        val pw = IndentingPrintWriter(stringWriter, "  ")

        record.dump(pw)

        assertThat(stringWriter.toString())
            .isEqualTo(
                "\n" +
                    "AdminRecord (package: com.android.test.dumper):\n" +
                    "  mOrganizationId=org-id-dump\n" +
                    "  mEnrollmentSpecificId=enrollment-id-dump\n"
            )
    }

    companion object {
        private const val TAG = "AdminRecordTest"
    }
}
