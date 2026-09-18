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

package com.android.server.input.data;

import android.hardware.input.InputDeviceIdentifier;
import android.util.ArrayMap;
import android.util.Slog;

import com.android.modules.utils.TypedXmlPullParser;
import com.android.modules.utils.TypedXmlSerializer;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@link PersistedData} implementation for InputDeviceRemappingData.
 *
 * @hide
 */
final class InputDeviceRemappingPersistedData extends PersistedData<InputDeviceRemappingData> {

    private static final String TAG = "InputDeviceRemappingPersistedData";

    private static final String TAG_ROOT = "input-device-remappings";
    private static final String TAG_DEVICE_REMAP = "device-remapping";
    private static final String TAG_BUTTON_REMAPS = "button-remappings";
    private static final String TAG_BUTTON_TO_AXIS_REMAPS = "button-to-axis-remappings";
    private static final String TAG_AXIS_REMAPS = "axis-remappings";
    private static final String TAG_REMAP = "remap";

    private static final String ATTR_DESCRIPTOR = "descriptor";
    private static final String ATTR_VENDOR_ID = "vendorId";
    private static final String ATTR_PRODUCT_ID = "productId";
    private static final String ATTR_FROM = "from";
    private static final String ATTR_TO = "to";

    InputDeviceRemappingPersistedData() {
        super("input_device_remappings");
    }

    @Override
    public List<InputDeviceRemappingData> readListFromXml(TypedXmlPullParser parser)
            throws XmlPullParserException, IOException {
        List<InputDeviceRemappingData> remappingDataList = new ArrayList<>();
        int type;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT) {
            if (type != XmlPullParser.START_TAG) {
                continue;
            }
            if (TAG_ROOT.equals(parser.getName())) {
                readDeviceRemappingListFromXml(parser, remappingDataList);
            }
        }
        return remappingDataList;
    }

    @Override
    public void writeListToXml(TypedXmlSerializer serializer,
            List<InputDeviceRemappingData> remappingDataList) throws IOException {
        serializer.startDocument(null, true);
        serializer.startTag(null, TAG_ROOT);
        for (InputDeviceRemappingData data : remappingDataList) {
            writeDeviceRemappingToXml(serializer, data);
        }
        serializer.endTag(null, TAG_ROOT);
        serializer.endDocument();
    }

    private void readDeviceRemappingListFromXml(TypedXmlPullParser parser,
            List<InputDeviceRemappingData> remappingDataList)
            throws XmlPullParserException, IOException {
        final int outerDepth = parser.getDepth();
        int type;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
            if (type != XmlPullParser.START_TAG) {
                continue;
            }
            if (TAG_DEVICE_REMAP.equals(parser.getName())) {
                try {
                    remappingDataList.add(readDeviceRemappingFromXml(parser));
                } catch (IllegalArgumentException e) {
                    Slog.w(TAG, "Invalid parameters for device remapping: ", e);
                }
            }
        }
    }

    private InputDeviceRemappingData readDeviceRemappingFromXml(TypedXmlPullParser parser)
            throws XmlPullParserException, IOException {
        String descriptor = parser.getAttributeValue(null, ATTR_DESCRIPTOR);
        int vendorId = parser.getAttributeInt(null, ATTR_VENDOR_ID);
        int productId = parser.getAttributeInt(null, ATTR_PRODUCT_ID);
        InputDeviceIdentifier identifier = new InputDeviceIdentifier(descriptor, vendorId,
                productId);

        Map<Integer, Integer> buttonRemapping = new ArrayMap<>();
        Map<Integer, Integer> buttonToAxisRemapping = new ArrayMap<>();
        Map<Integer, Integer> axisRemapping = new ArrayMap<>();

        final int outerDepth = parser.getDepth();
        int type;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
            if (type != XmlPullParser.START_TAG) {
                continue;
            }
            String tagName = parser.getName();
            if (TAG_BUTTON_REMAPS.equals(tagName)) {
                readRemapMapFromXml(parser, buttonRemapping);
            } else if (TAG_BUTTON_TO_AXIS_REMAPS.equals(tagName)) {
                readRemapMapFromXml(parser, buttonToAxisRemapping);
            } else if (TAG_AXIS_REMAPS.equals(tagName)) {
                readRemapMapFromXml(parser, axisRemapping);
            }
        }
        return new InputDeviceRemappingData(
                identifier, buttonRemapping, buttonToAxisRemapping, axisRemapping);
    }

    private void readRemapMapFromXml(TypedXmlPullParser parser, Map<Integer, Integer> remapMap)
            throws XmlPullParserException, IOException {
        final int outerDepth = parser.getDepth();
        int type;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
            if (type != XmlPullParser.START_TAG) {
                continue;
            }
            if (TAG_REMAP.equals(parser.getName())) {
                int from = parser.getAttributeInt(null, ATTR_FROM);
                int to = parser.getAttributeInt(null, ATTR_TO);
                remapMap.put(from, to);
            }
        }
    }

    private void writeDeviceRemappingToXml(TypedXmlSerializer serializer,
            InputDeviceRemappingData data) throws IOException {
        serializer.startTag(null, TAG_DEVICE_REMAP);
        InputDeviceIdentifier identifier = data.deviceIdentifier();
        serializer.attribute(null, ATTR_DESCRIPTOR, identifier.getDescriptor());
        serializer.attributeInt(null, ATTR_VENDOR_ID, identifier.getVendorId());
        serializer.attributeInt(null, ATTR_PRODUCT_ID, identifier.getProductId());

        if (!data.buttonRemappingMap().isEmpty()) {
            serializer.startTag(null, TAG_BUTTON_REMAPS);
            for (Map.Entry<Integer, Integer> entry : data.buttonRemappingMap().entrySet()) {
                writeRemapTag(serializer, entry.getKey(), entry.getValue());
            }
            serializer.endTag(null, TAG_BUTTON_REMAPS);
        }

        if (!data.buttonToAxisRemappingMap().isEmpty()) {
            serializer.startTag(null, TAG_BUTTON_TO_AXIS_REMAPS);
            for (Map.Entry<Integer, Integer> entry : data.buttonToAxisRemappingMap().entrySet()) {
                writeRemapTag(serializer, entry.getKey(), entry.getValue());
            }
            serializer.endTag(null, TAG_BUTTON_TO_AXIS_REMAPS);
        }

        if (!data.axisRemappingMap().isEmpty()) {
            serializer.startTag(null, TAG_AXIS_REMAPS);
            for (Map.Entry<Integer, Integer> entry : data.axisRemappingMap().entrySet()) {
                writeRemapTag(serializer, entry.getKey(), entry.getValue());
            }
            serializer.endTag(null, TAG_AXIS_REMAPS);
        }

        serializer.endTag(null, TAG_DEVICE_REMAP);
    }

    private void writeRemapTag(TypedXmlSerializer serializer, int from, int to) throws IOException {
        serializer.startTag(null, TAG_REMAP);
        serializer.attributeInt(null, ATTR_FROM, from);
        serializer.attributeInt(null, ATTR_TO, to);
        serializer.endTag(null, TAG_REMAP);
    }
}
