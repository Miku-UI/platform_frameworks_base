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

public class DisplayMode {
    private static final String XML_TAG_RESOLUTION_WIDTH = "resolution-width";
    private static final String XML_TAG_RESOLUTION_HEIGHT = "resolution-height";
    private static final String XML_TAG_REFRESH_RATE = "refresh-rate";
    static final XmlProcessor<DisplayMode> XML_PROCESSOR = new XmlProcessor<>() {
        @Override
        public DisplayMode loadFromXml(TypedXmlPullParser parser)
                throws XmlPullParserException, IOException {
            int width = 0, height = 0;
            float refreshRate = 0;
            final int outerDepth = parser.getDepth();
            while (XmlUtils.nextElementWithin(parser, outerDepth)) {
                try {
                    switch (parser.getName()) {
                        case XML_TAG_RESOLUTION_WIDTH -> width = Integer.parseInt(
                                parser.nextText());
                        case XML_TAG_RESOLUTION_HEIGHT -> height = Integer.parseInt(
                                parser.nextText());
                        case XML_TAG_REFRESH_RATE -> refreshRate = Float.parseFloat(
                                parser.nextText());
                    }
                } catch (NumberFormatException e) {
                    throw new XmlPullParserException(
                            "Failed to parse display mode: " + e.getLocalizedMessage());
                }
            }
            return new DisplayMode(width, height, refreshRate);
        }

        @Override
        public void saveToXml(TypedXmlSerializer serializer, DisplayMode value) throws IOException {
            serializer.startTag(null, XML_TAG_RESOLUTION_WIDTH);
            serializer.text(Integer.toString(value.mWidth));
            serializer.endTag(null, XML_TAG_RESOLUTION_WIDTH);

            serializer.startTag(null, XML_TAG_RESOLUTION_HEIGHT);
            serializer.text(Integer.toString(value.mHeight));
            serializer.endTag(null, XML_TAG_RESOLUTION_HEIGHT);

            serializer.startTag(null, XML_TAG_REFRESH_RATE);
            serializer.text(Float.toString(value.getRefreshRate()));
            serializer.endTag(null, XML_TAG_REFRESH_RATE);
        }
    };

    private final int mWidth, mHeight;
    private final float mRefreshRate;

    public DisplayMode(int width, int height, float refreshRate) {
        mWidth = width;
        mHeight = height;
        mRefreshRate = refreshRate;
    }

    public Point getResolution() {
        return new Point(mWidth, mHeight);
    }

    public float getRefreshRate() {
        return mRefreshRate;
    }

    @Override
    public String toString() {
        return "DisplayMode{"
                + "mWidth=" + mWidth
                + ", mHeight=" + mHeight
                + ", mRefreshRate=" + mRefreshRate
                + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DisplayMode that)) return false;
        return mWidth == that.mWidth && mHeight == that.mHeight && Float.compare(mRefreshRate,
                that.mRefreshRate) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mWidth, mHeight, mRefreshRate);
    }
}
