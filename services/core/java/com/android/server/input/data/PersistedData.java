/*
 * Copyright 2025 The Android Open Source Project
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

import android.util.Xml;

import com.android.modules.utils.TypedXmlPullParser;
import com.android.modules.utils.TypedXmlSerializer;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Abstract base class for creating and managing persistent data.
 */
abstract class PersistedData<T> {
    private final String mFileName;

    PersistedData(String fileName) {
        mFileName = fileName;
    }

    public String getFileName() {
        return mFileName;
    }

    /**
     * Reads data from the given input stream.
     *
     * @param stream      The input stream to read from.
     * @param utf8Encoded Whether the stream is UTF-8 encoded.
     * @return A list of data objects.
     * @throws XmlPullParserException If there is an issue parsing the XML.
     * @throws IOException            If there is an issue reading from the stream.
     */
    List<T> readData(InputStream stream, boolean utf8Encoded)
            throws XmlPullParserException, IOException {
        TypedXmlPullParser parser;
        if (utf8Encoded) {
            parser = Xml.newFastPullParser();
            parser.setInput(stream, StandardCharsets.UTF_8.name());
        } else {
            parser = Xml.resolvePullParser(stream);
        }
        return readListFromXml(parser);
    }

    /**
     * Writes data to the given output stream.
     *
     * @param stream      The output stream to write to.
     * @param utf8Encoded Whether to encode the stream in UTF-8.
     * @param dataList    The list of data objects to write.
     * @throws IOException If there is an issue writing to the stream.
     */
    void writeData(OutputStream stream, boolean utf8Encoded, List<T> dataList) throws IOException {
        final TypedXmlSerializer serializer;
        if (utf8Encoded) {
            serializer = Xml.newFastSerializer();
            serializer.setOutput(stream, StandardCharsets.UTF_8.name());
        } else {
            serializer = Xml.resolveSerializer(stream);
        }
        writeListToXml(serializer, dataList);
    }

    protected abstract List<T> readListFromXml(TypedXmlPullParser parser)
            throws XmlPullParserException, IOException;

    protected abstract void writeListToXml(TypedXmlSerializer serializer, List<T> dataList)
            throws IOException;
}
