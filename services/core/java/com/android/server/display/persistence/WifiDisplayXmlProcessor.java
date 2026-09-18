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

package com.android.server.display.persistence;

import static com.android.server.display.persistence.PersistentDataStore.XmlProcessor;

import android.hardware.display.WifiDisplay;

import com.android.modules.utils.TypedXmlPullParser;
import com.android.modules.utils.TypedXmlSerializer;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

public class WifiDisplayXmlProcessor implements XmlProcessor<WifiDisplay> {
    private static final String XML_ATTR_DEVICE_ADDRESS = "deviceAddress";
    private static final String XML_ATTR_DEVICE_NAME = "deviceName";
    private static final String XML_ATTR_DEVICE_ALIAS = "deviceAlias";

    @Override
    public WifiDisplay loadFromXml(TypedXmlPullParser parser)
            throws XmlPullParserException {
        String deviceAddress = parser.getAttributeValue(null, XML_ATTR_DEVICE_ADDRESS);
        String deviceName = parser.getAttributeValue(null, XML_ATTR_DEVICE_NAME);
        String deviceAlias = parser.getAttributeValue(null, XML_ATTR_DEVICE_ALIAS);
        if (deviceAddress == null || deviceName == null) {
            throw new XmlPullParserException(
                    "Missing deviceAddress or deviceName attribute on wifi-display.");
        }
        return new WifiDisplay(deviceAddress, deviceName, deviceAlias, /* available= */ false,
                /* canConnect= */ false, /* remembered= */ false);
    }

    @Override
    public void saveToXml(TypedXmlSerializer serializer, WifiDisplay value) throws IOException {
        serializer.attribute(null, XML_ATTR_DEVICE_ADDRESS, value.getDeviceAddress());
        serializer.attribute(null, XML_ATTR_DEVICE_NAME, value.getDeviceName());
        if (value.getDeviceAlias() != null) {
            serializer.attribute(null, XML_ATTR_DEVICE_ALIAS, value.getDeviceAlias());
        }
    }
}
