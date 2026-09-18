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

package com.android.server.input.data

import android.hardware.input.InputDeviceIdentifier
import android.platform.test.annotations.Presubmit
import android.util.Xml
import com.android.modules.utils.TypedXmlPullParser
import com.android.modules.utils.TypedXmlSerializer
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Tests for {@link InputDeviceRemappingPersistedData}.
 *
 * Build/Install/Run: atest InputTests:InputDeviceRemappingPersistedDataTests
 */
@Presubmit
class InputDeviceRemappingPersistedDataTest {

    private lateinit var persistedData: InputDeviceRemappingPersistedData

    @Before
    fun setUp() {
        persistedData = InputDeviceRemappingPersistedData()
    }

    @Test
    fun writeListToXml_writesCorrectXml() {
        val remappingDataList =
            listOf(
                InputDeviceRemappingData(
                    InputDeviceIdentifier("descriptor", 1, 2),
                    mapOf(1 to 2, 3 to 4, 11 to 12),
                    mapOf(5 to 6, 13 to 14),
                    mapOf(7 to 8, 15 to 16),
                ),
                InputDeviceRemappingData(
                    InputDeviceIdentifier("descriptor2", 101, 102),
                    mapOf(10 to 20),
                    mapOf(50 to 60),
                    mapOf(70 to 80),
                ),
            )
        val outputStream = ByteArrayOutputStream()
        val serializer: TypedXmlSerializer = Xml.newFastSerializer()
        serializer.setOutput(outputStream, StandardCharsets.UTF_8.name())

        persistedData.writeListToXml(serializer, remappingDataList)

        val xmlString = outputStream.toString(StandardCharsets.UTF_8.name())
        val expectedXml =
            """
            <?xml version='1.0' encoding='utf-8' standalone='yes' ?>
            <input-device-remappings>
            <device-remapping descriptor="descriptor" vendorId="1" productId="2">
            <button-remappings>
            <remap from="1" to="2" />
            <remap from="3" to="4" />
            <remap from="11" to="12" />
            </button-remappings>
            <button-to-axis-remappings>
            <remap from="5" to="6" />
            <remap from="13" to="14" />
            </button-to-axis-remappings>
            <axis-remappings>
            <remap from="7" to="8" />
            <remap from="15" to="16" />
            </axis-remappings>
            </device-remapping>
            <device-remapping descriptor="descriptor2" vendorId="101" productId="102">
            <button-remappings>
            <remap from="10" to="20" />
            </button-remappings>
            <button-to-axis-remappings>
            <remap from="50" to="60" />
            </button-to-axis-remappings>
            <axis-remappings>
            <remap from="70" to="80" />
            </axis-remappings>
            </device-remapping>
            </input-device-remappings>

            """
                .trimIndent()
        assertEquals(expectedXml, xmlString)
    }

    @Test
    fun readListFromXml_readsCorrectXml() {
        val xmlString =
            """
            <?xml version='1.0' encoding='utf-8' standalone='yes' ?>
            <input-device-remappings>
                <device-remapping descriptor="descriptor" vendorId="1" productId="2">
                    <button-remappings>
                        <remap from="1" to="2" />
                        <remap from="3" to="4" />
                        <remap from="11" to="12" />
                    </button-remappings>
                    <button-to-axis-remappings>
                        <remap from="5" to="6" />
                        <remap from="13" to="14" />
                    </button-to-axis-remappings>
                    <axis-remappings>
                        <remap from="7" to="8" />
                        <remap from="15" to="16" />
                    </axis-remappings>
                </device-remapping>
                <device-remapping descriptor="descriptor2" vendorId="101" productId="102">
                    <button-remappings>
                        <remap from="10" to="20" />
                    </button-remappings>
                    <button-to-axis-remappings>
                        <remap from="50" to="60" />
                    </button-to-axis-remappings>
                    <axis-remappings>
                        <remap from="70" to="80" />
                    </axis-remappings>
                </device-remapping>
            </input-device-remappings>
            """
                .trimIndent()

        val inputStream = ByteArrayInputStream(xmlString.toByteArray(StandardCharsets.UTF_8))
        val parser: TypedXmlPullParser = Xml.newFastPullParser()
        parser.setInput(inputStream, StandardCharsets.UTF_8.name())
        val remappingDataList = persistedData.readListFromXml(parser)

        val expectedRemappingDataList =
            listOf(
                InputDeviceRemappingData(
                    InputDeviceIdentifier("descriptor", 1, 2),
                    /* buttonRemappings= */ mapOf(1 to 2, 3 to 4, 11 to 12),
                    /* buttonToAxisRemappings= */ mapOf(5 to 6, 13 to 14),
                    /* axisRemappings= */ mapOf(7 to 8, 15 to 16),
                ),
                InputDeviceRemappingData(
                    InputDeviceIdentifier("descriptor2", 101, 102),
                    /* buttonRemappings= */ mapOf(10 to 20),
                    /* buttonToAxisRemappings= */ mapOf(50 to 60),
                    /* axisRemappings= */ mapOf(70 to 80),
                ),
            )

        assertEquals(expectedRemappingDataList, remappingDataList)
    }
}
