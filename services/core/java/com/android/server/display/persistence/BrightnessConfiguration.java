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

import android.util.TimeUtils;

import com.android.modules.utils.TypedXmlPullParser;
import com.android.modules.utils.TypedXmlSerializer;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.Objects;

public class BrightnessConfiguration {
    static final String XML_TAG = "brightness-configuration";
    private static final String XML_ATTR_PACKAGE_NAME = "package-name";
    private static final String XML_ATTR_TIMESTAMP = "timestamp";
    static final XmlProcessor<BrightnessConfiguration> XML_PROCESSOR = new XmlProcessor<>() {
        @Override
        public BrightnessConfiguration loadFromXml(TypedXmlPullParser parser)
                throws XmlPullParserException, IOException {
            String packageName = parser.getAttributeValue(null, XML_ATTR_PACKAGE_NAME);
            long timestamp = parser.getAttributeLong(null, XML_ATTR_TIMESTAMP, -1);
            android.hardware.display.BrightnessConfiguration config =
                    android.hardware.display.BrightnessConfiguration.loadFromXml(parser);
            return new BrightnessConfiguration(config, timestamp, packageName);
        }

        @Override
        public void saveToXml(TypedXmlSerializer serializer, BrightnessConfiguration value)
                throws IOException {
            serializer.attribute(null, XML_ATTR_PACKAGE_NAME, value.mPackageName);
            serializer.attributeLong(null, XML_ATTR_TIMESTAMP, value.mTimestamp);
            value.getConfiguration().saveToXml(serializer);
        }
    };

    private final android.hardware.display.BrightnessConfiguration mConfiguration;

    // Timestamp of time the configuration was set.
    private final long mTimestamp;

    // Package that set the configuration.
    private final String mPackageName;

    public BrightnessConfiguration(android.hardware.display.BrightnessConfiguration configuration,
            long timestamp, String packageName) {
        mConfiguration = configuration;
        mTimestamp = timestamp;
        mPackageName = packageName;
    }

    public android.hardware.display.BrightnessConfiguration getConfiguration() {
        return mConfiguration;
    }

    long getTimestamp() {
        return mTimestamp;
    }

    String getPackageName() {
        return mPackageName;
    }

    @Override
    public String toString() {
        return "BrightnessConfiguration{"
                + "mConfiguration=" + mConfiguration
                + ", mTimestamp=" + TimeUtils.formatForLogging(mTimestamp)
                + ", mPackageName='" + mPackageName + '\''
                + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof BrightnessConfiguration that)) return false;
        return mTimestamp == that.mTimestamp && Objects.equals(mConfiguration,
                that.mConfiguration) && Objects.equals(mPackageName, that.mPackageName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mConfiguration, mTimestamp, mPackageName);
    }
}
