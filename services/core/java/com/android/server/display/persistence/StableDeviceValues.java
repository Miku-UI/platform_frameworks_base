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

import android.graphics.Point;

import com.android.internal.util.XmlUtils;
import com.android.modules.utils.TypedXmlPullParser;
import com.android.modules.utils.TypedXmlSerializer;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.Objects;

public class StableDeviceValues {
    private static final String XML_TAG_STABLE_DISPLAY_HEIGHT = "stable-display-height";
    private static final String XML_TAG_STABLE_DISPLAY_WIDTH = "stable-display-width";
    static final XmlProcessor<StableDeviceValues> XML_PROCESSOR = new XmlProcessor<>() {
        @Override
        public StableDeviceValues loadFromXml(TypedXmlPullParser parser)
                throws XmlPullParserException, IOException {
            int width = 0, height = 0;
            final int outerDepth = parser.getDepth();
            while (XmlUtils.nextElementWithin(parser, outerDepth)) {
                try {
                    switch (parser.getName()) {
                        case XML_TAG_STABLE_DISPLAY_WIDTH -> width = Integer.parseInt(
                                parser.nextText());
                        case XML_TAG_STABLE_DISPLAY_HEIGHT -> height = Integer.parseInt(
                                parser.nextText());
                    }
                } catch (NumberFormatException e) {
                    throw new XmlPullParserException(
                            "Failed to parse stable device values: " + e.getLocalizedMessage());
                }
            }
            return new StableDeviceValues(new Point(width, height));
        }

        @Override
        public void saveToXml(TypedXmlSerializer serializer, StableDeviceValues value)
                throws IOException {
            serializer.startTag(null, XML_TAG_STABLE_DISPLAY_WIDTH);
            serializer.text(Integer.toString(value.mWidth));
            serializer.endTag(null, XML_TAG_STABLE_DISPLAY_WIDTH);
            serializer.startTag(null, XML_TAG_STABLE_DISPLAY_HEIGHT);
            serializer.text(Integer.toString(value.mHeight));
            serializer.endTag(null, XML_TAG_STABLE_DISPLAY_HEIGHT);
        }
    };

    private final int mWidth;
    private final int mHeight;

    public StableDeviceValues(Point size) {
        mWidth = size.x;
        mHeight = size.y;
    }

    public Point getDisplaySize() {
        return new Point(mWidth, mHeight);
    }

    @Override
    public String toString() {
        return "StableDeviceValues{"
                + "mWidth=" + mWidth
                + ", mHeight=" + mHeight
                + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof StableDeviceValues that)) return false;
        return mWidth == that.mWidth && mHeight == that.mHeight;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mWidth, mHeight);
    }
}
